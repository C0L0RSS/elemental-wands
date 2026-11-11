package com.anton.elementalwands.hall;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.arena.GuardianArenaJournal.Point;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import com.anton.elementalwands.registry.ModEntities;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Burned Throne Hall before its fight exists: operators can lay out a slot, walk it and stand
 * the Pyre King in it. Slots are laid out from the builder's tiles one per tick the first time they
 * are used and reused afterwards; a changed layout moves to fresh ground. Return points are saved
 * before anyone moves. A player who leaves the fight floor is set back at the arrival, nothing drops
 * if one dies here, and nobody but a Creative player edits the hall.
 */
public final class PyreHallManager {
    private PyreHallManager() {}

    private static final Logger LOG = LoggerFactory.getLogger("ElementalWands/PyreHall");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    static final class State {
        int version = 1;
        /** Bumped when the layout changes, which moves every slot to untouched ground. */
        int generation;
        String layout = "";
        List<Integer> built = new ArrayList<>();
        /** Where each player entered from; leaving always sends them back here. */
        Map<String, Point> returns = new LinkedHashMap<>();
    }

    private static final class Build {
        final int slot;
        final BlockPos centre;
        final List<Identifier> tiles = new ArrayList<>();
        final List<BlockPos> origins = new ArrayList<>();
        final Set<UUID> waiting = new LinkedHashSet<>();
        int next;

        Build(int slot, BlockPos centre) { this.slot = slot; this.centre = centre; }
    }

    private static State state;
    private static Path path;
    private static String layout = "";
    private static final Map<Integer, Build> builds = new HashMap<>();
    /** Players who died here; the respawned copy gets their inventory and experience back. */
    private static final Set<UUID> kept = new HashSet<>();
    /** Players sliding off a pillar: their fall is forgiven until they stand on the floor. */
    private static final Set<UUID> slipping = new HashSet<>();
    private static boolean internalTeleport;

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(PyreHallManager::load);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> layout = fingerprint(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { state = null; path = null; builds.clear(); kept.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(PyreHallManager::tick);
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            // The set is lost on a restart; the body still lying in the hall is what survives a quit from the death screen.
            if (alive || !kept.remove(oldPlayer.getUuid()) && !inRealm(oldPlayer)) return;
            newPlayer.getInventory().clone(oldPlayer.getInventory());
            newPlayer.experienceLevel = oldPlayer.experienceLevel;
            newPlayer.totalExperience = oldPlayer.totalExperience;
            newPlayer.experienceProgress = oldPlayer.experienceProgress;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, block, entity) -> !restricted(world, player));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Item item = player.getStackInHand(hand).getItem();
            return restricted(world, player) && (item instanceof BlockItem || item instanceof BucketItem || item instanceof FlintAndSteelItem
                    || item instanceof FireChargeItem || item instanceof BoneMealItem) ? ActionResult.FAIL : ActionResult.PASS;
        });
    }

    // ------------------------------------------------------------------ rules other systems ask

    public static boolean inRealm(Entity entity) { return entity.getEntityWorld().getRegistryKey() == PyreHall.WORLD; }

    private static boolean restricted(World world, PlayerEntity player) {
        return world.getRegistryKey() == PyreHall.WORLD && !player.isCreative();
    }

    /** Nothing drops here; the respawned player gets their inventory and experience back. */
    public static boolean keepsBelongings(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity) || !inRealm(player)) return false;
        kept.add(player.getUuid());
        return true;
    }

    /**
     * Every player teleport (spells, pearls, commands, portals) stays on the walled floor and never
     * crosses into or out of the hall; only the hall's own moves (enter, leave, the edge rescue) do.
     * Creative and spectating players are free.
     */
    public static boolean canTeleport(PlayerEntity player, ServerWorld destination, Vec3d target) {
        if (internalTeleport) return true;
        if (player.isCreative() || player.isSpectator()) return true;
        boolean from = inRealm(player), to = destination.getRegistryKey() == PyreHall.WORLD;
        if (!from && !to) return true;
        return from && to && PyreHall.inPlay(PyreHall.nearestCentre(player.getEntityPos()), target, .4);
    }

    // ------------------------------------------------------------------ commands

    public static String enter(ServerPlayerEntity player, int slot) {
        ServerWorld realm = realm(player.getEntityWorld().getServer());
        if (realm == null) return "The Burned Throne Hall dimension is not loaded.";
        if (state == null) return "Burned Throne Hall storage is unavailable; see the server log.";
        if (!ready(slot) && !building(slot) && installedTiles(realm) == 0)
            return "The Burned Throne Hall's layout is not installed (no structure/pyre_hall tiles).";
        if (!inRealm(player)) {
            state.returns.put(player.getUuidAsString(), point(player));
            if (!persist()) return "Could not record your return point, so you were not moved.";
        }
        if (ready(slot)) {
            arrive(player, realm, slot);
            return "Entered hall slot " + slot + ". /ew hall summon stands the Pyre King; /ew hall leave takes you back.";
        }
        Build build = build(realm, slot);
        build.waiting.add(player.getUuid());
        return "Laying out hall slot " + slot + " (" + build.tiles.size() + " sections); you will be taken in when it is ready.";
    }

    public static String leave(ServerPlayerEntity player) {
        if (!inRealm(player)) return "You are not in the Burned Throne Hall.";
        if (state == null) return "Burned Throne Hall storage is unavailable; see the server log.";
        return sendHome(player) ? "Returned to where you entered." : "No return point was recorded; sent to world spawn.";
    }

    public static String status() {
        if (state == null) return "Burned Throne Hall storage is unavailable.";
        StringBuilder out = new StringBuilder("Burned Throne Hall: layout generation ").append(state.generation)
                .append(state.layout.equals(layout) ? "" : " (layout changed; slots move to fresh ground on next entry)")
                .append(", built slots ").append(state.built).append(", ").append(state.returns.size()).append(" return point(s) held.");
        builds.values().forEach(b -> out.append(" Slot ").append(b.slot).append(" building ").append(b.next).append('/').append(b.tiles.size()).append('.'));
        return out.toString();
    }

    /** Removes any Pyre King in the slot and stands a new one on his mark in his guard. He has no fight. */
    public static String summon(ServerPlayerEntity player) {
        if (!inRealm(player)) return "Enter the hall first with /ew hall enter.";
        ServerWorld realm = (ServerWorld) player.getEntityWorld();
        BlockPos centre = PyreHall.nearestCentre(player.getEntityPos());
        List<PyreKingEntity> old = realm.getEntitiesByClass(PyreKingEntity.class, PyreHall.footprint(centre), e -> true);
        old.forEach(Entity::discard);
        PyreKingEntity king = ModEntities.PYRE_KING.create(realm, SpawnReason.COMMAND);
        if (king == null) return "Could not create the Pyre King.";
        Vec3d at = PyreHall.king(centre);
        king.refreshPositionAndAngles(at.x, at.y, at.z, PyreHall.KING_YAW, 0);
        king.setBodyYaw(PyreHall.KING_YAW);
        king.setHeadYaw(PyreHall.KING_YAW);
        realm.spawnEntity(king);
        return "The Pyre King stands before his throne" + (old.isEmpty() ? "" : " (replaced " + old.size() + ")")
                + ". He fights anyone in Survival or Adventure who comes near; /ew pyreking calm stops him.";
    }

    /** Clears the slot's entities (not players) and lays its tiles out again. */
    public static String reset(ServerPlayerEntity player) {
        if (!inRealm(player)) return "Stand in the hall slot you want to reset.";
        if (state == null) return "Burned Throne Hall storage is unavailable; see the server log.";
        ServerWorld realm = (ServerWorld) player.getEntityWorld();
        BlockPos centre = PyreHall.nearestCentre(player.getEntityPos());
        List<Entity> leftovers = realm.getOtherEntities(null, PyreHall.footprint(centre), e -> !(e instanceof PlayerEntity));
        leftovers.forEach(Entity::discard);
        int slot = slotAt(centre);
        if (slot < 0) return "Cleared " + leftovers.size() + " entities. This slot is from an older layout, so it was not laid out again.";
        Build build = build(realm, slot);   // a slot already being laid out just carries on
        return "Cleared " + leftovers.size() + " entities; laying out " + build.tiles.size() + " sections of slot " + slot + " again.";
    }

    // ------------------------------------------------------------------ moving players

    private static void arrive(ServerPlayerEntity player, ServerWorld realm, int slot) {
        transfer(player, realm, PyreHall.arrival(PyreHall.centre(state.generation, slot)), PyreHall.ARRIVAL_YAW, 0);
    }

    /** Back to the recorded entry point, or world spawn. */
    private static boolean sendHome(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        String id = player.getUuidAsString();
        Point point = state.returns.get(id);
        ServerWorld world = point == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(point.dimension())));
        boolean recorded = world != null && world.getRegistryKey() != PyreHall.WORLD;
        if (recorded) transfer(player, world, new Vec3d(point.x(), point.y(), point.z()), point.yaw(), point.pitch());
        else {
            ServerWorld spawn = server.getSpawnWorld();
            BlockPos pos = server.getSpawnPoint().getPos();
            spawn.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            int y = spawn.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
            transfer(player, spawn, new Vec3d(pos.getX() + .5, y, pos.getZ() + .5), server.getSpawnPoint().yaw(), 0);
        }
        // Save where they now stand before retiring the record: a crash in between repeats the trip
        // home instead of leaving them in the hall with no way back.
        server.getPlayerManager().saveAllPlayerData();
        state.returns.remove(id);
        persist();
        return recorded;
    }

    private static void transfer(ServerPlayerEntity player, ServerWorld world, Vec3d pos, float yaw, float pitch) {
        player.stopRiding();
        internalTeleport = true;
        try { player.teleport(world, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, true); }
        finally { internalTeleport = false; }
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        player.velocityModified = true;
    }

    // ------------------------------------------------------------------ building

    private static boolean ready(int slot) {
        refreshGeneration();
        return state.built.contains(slot) && !builds.containsKey(slot);
    }

    private static boolean building(int slot) { return builds.containsKey(slot); }

    /** For fixtures: the slot is laid out in the current generation and nothing is building it. */
    public static boolean slotReady(int slot) { return state != null && ready(slot); }

    /** A changed layout never overwrites an old one in place: it moves to untouched ground. */
    private static void refreshGeneration() {
        if (state.layout.equals(layout)) return;
        if (!state.layout.isEmpty()) state.generation++;
        state.layout = layout;
        state.built.clear();
        persist();
    }

    private static int installedTiles(ServerWorld realm) {
        int count = 0;
        var templates = realm.getStructureTemplateManager();
        for (int i = 0; i < PyreHall.TILE_COUNT; i++)
            for (int j = 0; j < PyreHall.TILE_COUNT; j++)
                if (templates.getTemplate(PyreHall.tile(i, j)).isPresent()) count++;
        return count;
    }

    private static Build build(ServerWorld realm, int slot) {
        refreshGeneration();
        Build existing = builds.get(slot);
        if (existing != null) return existing;
        Build build = new Build(slot, PyreHall.centre(state.generation, slot));
        var templates = realm.getStructureTemplateManager();
        // Missing tiles are empty ground; the builder leaves them out.
        for (int i = 0; i < PyreHall.TILE_COUNT; i++)
            for (int j = 0; j < PyreHall.TILE_COUNT; j++) {
                Identifier id = PyreHall.tile(i, j);
                if (templates.getTemplate(id).isPresent()) {
                    build.tiles.add(id);
                    build.origins.add(PyreHall.tileOrigin(build.centre, i, j));
                }
            }
        state.built.remove(Integer.valueOf(slot));
        persist();
        builds.put(slot, build);
        return build;
    }

    private static void tick(MinecraftServer server) {
        ServerWorld realm = realm(server);
        if (realm == null || state == null) return;
        // One 48x48 section per tick lays out a fresh slot in about a second without a long stall.
        for (var it = builds.values().iterator(); it.hasNext(); ) {
            Build build = it.next();
            if (build.next < build.tiles.size()) {
                place(realm, build.tiles.get(build.next), build.origins.get(build.next));
                build.next++;
                continue;
            }
            it.remove();
            if (!state.built.contains(build.slot)) state.built.add(build.slot);
            persist();
            for (UUID id : build.waiting) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player == null || !player.isAlive()) continue;
                arrive(player, realm, build.slot);
                player.sendMessage(Text.literal("You enter the Burned Throne Hall."), true);
            }
        }
        // Anyone off the walled floor (over the wall, under the floor, into the lava) is set back at the arrival.
        for (ServerPlayerEntity player : realm.getPlayers()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
            BlockPos centre = PyreHall.nearestCentre(player.getEntityPos());
            if (PyreHall.inPlay(centre, player.getEntityPos(), 0)) continue;
            transfer(player, realm, PyreHall.arrival(centre), PyreHall.ARRIVAL_YAW, 0);
            player.extinguish();
            player.sendMessage(Text.literal("The hall will not let you go that way."), true);
        }
        // The pillars are scenery (the developer, Nov 11): nobody fights from the top of one. Whoever lands on a pillar
        // slides off it, away from its middle, and the drop that follows does not hurt.
        slipping.removeIf(id -> realm.getPlayerByUuid(id) == null);
        for (ServerPlayerEntity player : realm.getPlayers()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) { slipping.remove(player.getUuid()); continue; }
            Vec3d at = player.getEntityPos();
            boolean perched = PyreHall.perched(at);
            if (perched && player.isOnGround()) {
                Vec3d away = at.subtract(PyreHall.nearestPillar(PyreHall.nearestCentre(at), at)).multiply(1, 0, 1);
                away = away.lengthSquared() < 1e-3 ? new Vec3d(1, 0, 0) : away.normalize();
                player.setVelocity(away.x * .6, .34, away.z * .6);
                player.velocityModified = true;
                if (slipping.add(player.getUuid())) player.sendMessage(Text.literal("The broken stone gives way under you."), true);
            }
            if (slipping.contains(player.getUuid())) {
                player.fallDistance = 0;
                if (!perched && player.isOnGround()) slipping.remove(player.getUuid());
            }
        }
    }

    private static void place(ServerWorld realm, Identifier id, BlockPos origin) {
        StructureTemplate template = realm.getStructureTemplateManager().getTemplate(id).orElse(null);
        if (template == null) return;
        template.place(realm, origin, origin, new StructurePlacementData().setIgnoreEntities(true).setUpdateNeighbors(false),
                realm.getRandom(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }

    private static int slotAt(BlockPos centre) {
        for (int slot = 0; slot < PyreHall.SLOTS; slot++)
            if (PyreHall.centre(state.generation, slot).equals(centre)) return slot;
        return -1;
    }

    // ------------------------------------------------------------------ storage

    private static ServerWorld realm(MinecraftServer server) { return server.getWorld(PyreHall.WORLD); }

    private static Point point(ServerPlayerEntity player) {
        Vec3d pos = player.getEntityPos();
        return new Point(player.getEntityWorld().getRegistryKey().getValue().toString(), pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
    }

    /** Identifies the installed layout so a changed builder output never mixes with an old slot. */
    private static String fingerprint(MinecraftServer server) {
        CRC32 crc = new CRC32();
        for (int i = 0; i < PyreHall.TILE_COUNT; i++)
            for (int j = 0; j < PyreHall.TILE_COUNT; j++) {
                var resource = server.getResourceManager().getResource(
                        Identifier.of(ElementalWandsMod.MOD_ID, "structure/pyre_hall/hall_" + i + "_" + j + ".nbt"));
                if (resource.isEmpty()) continue;
                try (var in = resource.get().getInputStream()) {
                    crc.update(i * 31 + j);
                    crc.update(in.readAllBytes());
                } catch (IOException e) { LOG.error("Could not read Burned Throne Hall tile {} {}", i, j, e); }
            }
        return Long.toHexString(crc.getValue());
    }

    private static void load(MinecraftServer server) {
        builds.clear();
        kept.clear();
        layout = fingerprint(server);
        path = server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands").resolve("pyre-hall.json");
        try {
            state = Files.exists(path) ? JSON.fromJson(Files.readString(path), State.class) : new State();
            if (state == null || state.version != 1 || state.built == null || state.returns == null || state.layout == null)
                throw new IOException("Invalid Burned Throne Hall record");
            persist();
        } catch (Exception e) {
            state = null;
            LOG.error("Burned Throne Hall storage needs attention; the hall is closed", e);
        }
    }

    private static boolean persist() {
        if (state == null || path == null) return false;
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, JSON.toJson(state), StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOG.error("Could not save the Burned Throne Hall record", e);
            return false;
        }
    }
}
