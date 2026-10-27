package com.anton.elementalwands.summit;

import com.anton.elementalwands.ElementalWandsMod;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Storm Summit before its fight exists: operators can raise a slot and walk it. Slots are laid
 * out from the builder's tiles one per tick and reused; a changed layout moves to fresh ground. A
 * player who falls off is caught and set back on the summit, nothing drops if one dies here, and
 * nobody but a Creative player edits the mountain.
 */
public final class StormSummitManager {
    private StormSummitManager() {}

    private static final Logger LOG = LoggerFactory.getLogger("ElementalWands/StormSummit");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    /** A saved place to send a player home to. */
    record Point(String dimension, double x, double y, double z, float yaw, float pitch) {}

    static final class State {
        int version = 1;
        int generation;
        String layout = "";
        List<Integer> built = new ArrayList<>();
        Map<String, Point> returns = new LinkedHashMap<>();
        Map<String, String> modes = new LinkedHashMap<>();
        /** Players an aerie gong took up; a login with no fight or call holding them goes back to the aerie. */
        List<String> called = new ArrayList<>();
        /** Aeries that have been won, by {@link StormAerie} site key. */
        Map<String, Site> sites = new LinkedHashMap<>();
    }

    static final class Site {
        /** The reward chest cells, placed again after a victory if broken or blocked. */
        List<Long> chests = new ArrayList<>();
        /** Cells whose chest has been filled; a chest placed there again stays empty. */
        List<Long> filled = new ArrayList<>();
        /** Everyone who has won here; each may claim one spell book from the chests. */
        List<String> victors = new ArrayList<>();
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
    private static boolean internalTeleport;

    public static void init() {
        StormRocEncounter.init();
        StormAerie.init();
        ServerLifecycleEvents.SERVER_STARTED.register(StormSummitManager::load);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> layout = fingerprint(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { state = null; path = null; builds.clear(); kept.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(StormSummitManager::tick);
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (alive || !kept.remove(oldPlayer.getUuid()) && !inRealm(oldPlayer)) return;
            newPlayer.getInventory().clone(oldPlayer.getInventory());
            newPlayer.experienceLevel = oldPlayer.experienceLevel;
            newPlayer.totalExperience = oldPlayer.totalExperience;
            newPlayer.experienceProgress = oldPlayer.experienceProgress;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, blockState, entity) -> !restricted(player));
        UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                restricted(player) && player.getStackInHand(hand).getItem() instanceof BlockItem ? ActionResult.FAIL : ActionResult.PASS);
    }

    public static boolean inRealm(PlayerEntity player) { return player.getEntityWorld().getRegistryKey() == StormSummit.WORLD; }

    private static boolean restricted(PlayerEntity player) { return inRealm(player) && !player.isCreative(); }

    /** Nothing drops here; the respawned player gets their inventory and experience back. */
    public static boolean keepsBelongings(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity) || !inRealm(player)) return false;
        kept.add(player.getUuid());
        return true;
    }

    // ------------------------------------------------------------------ commands

    public static String enter(ServerPlayerEntity player, int slot) {
        if (StormRocEncounter.enrolled(player)) return "Leave your current fight before entering another summit.";
        ServerWorld realm = realm(player.getEntityWorld().getServer());
        if (realm == null) return "The Storm Summit dimension is not loaded.";
        if (state == null) return "Storm Summit storage is unavailable; see the server log.";
        if (!inRealm(player)) {
            state.returns.put(player.getUuidAsString(), point(player));
            if (!persist()) return "Could not record your return point, so you were not moved.";
        }
        if (StormRocEncounter.activeAt(StormSummit.centre(state.generation, slot))) return "That summit has a sealed fight in progress.";
        if (ready(slot)) {
            arrive(player, realm, slot);
            return "Entered summit slot " + slot + ". /ew summit leave takes you back.";
        }
        Build build = build(realm, slot);
        build.waiting.add(player.getUuid());
        return "Raising summit slot " + slot + " (" + build.tiles.size() + " sections); you will be taken up when it is ready.";
    }

    public static String leave(ServerPlayerEntity player) {
        if (!inRealm(player)) return "You are not on the Storm Summit.";
        if (state == null) return "Storm Summit storage is unavailable; see the server log.";
        StormRocEncounter.leave(player);
        return sendHome(player) ? "Returned to where you entered." : "No return point was recorded; sent to world spawn.";
    }

    public static String status() {
        if (state == null) return "Storm Summit storage is unavailable.";
        StringBuilder out = new StringBuilder("Storm Summit generation ").append(state.generation)
                .append(", slots built: ").append(state.built).append('.');
        builds.values().forEach(b -> out.append(" Slot ").append(b.slot).append(" building ").append(b.next).append('/').append(b.tiles.size()).append('.'));
        return out.append(StormRocEncounter.status()).append(StormAerie.status()).toString();
    }

    public static String start(ServerPlayerEntity player) {
        if (!inRealm(player) || state == null) return "Stand on a ready Storm Summit first.";
        BlockPos centre = StormSummit.nearestCentre(player.getEntityPos());
        int slot = centre.getX() / StormSummit.SLOT_SPACING - state.generation * StormSummit.SLOTS;
        if (slot < 0 || slot >= StormSummit.SLOTS || !ready(slot)) return "This summit slot is not ready.";
        return StormRocEncounter.start(player.getEntityWorld().getServer(), centre);
    }

    public static String skip(ServerPlayerEntity player) {
        if (!inRealm(player)) return "Stand on the Storm Summit to skip its fight ahead.";
        return StormRocEncounter.skip(player.getEntityWorld().getServer(), StormSummit.nearestCentre(player.getEntityPos()));
    }

    public static String reset(ServerPlayerEntity player) {
        if (!inRealm(player)) return "Stand on the Storm Summit to reset its fight.";
        return StormRocEncounter.reset(player.getEntityWorld().getServer(), StormSummit.nearestCentre(player.getEntityPos()));
    }

    static Map<String, String> savedModes() { return state == null ? null : state.modes; }
    static Map<String, Point> savedReturns() { return state == null ? null : state.returns; }
    static List<String> savedCalls() { return state == null ? null : state.called; }
    static Map<String, Site> savedSites() { return state == null ? null : state.sites; }

    // ------------------------------------------------------------------ moving players

    private static void arrive(ServerPlayerEntity player, ServerWorld realm, int slot) {
        transfer(player, realm, StormSummit.arrival(StormSummit.centre(state.generation, slot)), StormSummit.ARRIVAL_YAW, 0);
    }

    /** Back to the recorded entry point (an aerie's for a called player), or world spawn. */
    static boolean sendHome(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        String id = player.getUuidAsString();
        Point point = state.returns.get(id);
        ServerWorld world = point == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(point.dimension())));
        boolean recorded = world != null && world.getRegistryKey() != StormSummit.WORLD;
        if (recorded) transfer(player, world, new Vec3d(point.x(), point.y(), point.z()), point.yaw(), point.pitch());
        else {
            ServerWorld spawn = server.getSpawnWorld();
            BlockPos pos = server.getSpawnPoint().getPos();
            spawn.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            int y = spawn.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
            transfer(player, spawn, new Vec3d(pos.getX() + .5, y, pos.getZ() + .5), server.getSpawnPoint().yaw(), 0);
        }
        // Save where they now stand before retiring the record: a crash in between repeats the trip
        // home instead of leaving them on the summit with no way back.
        server.getPlayerManager().saveAllPlayerData();
        state.returns.remove(id);
        state.called.remove(id);
        persist();
        return recorded;
    }

    static void transfer(ServerPlayerEntity player, ServerWorld world, Vec3d pos, float yaw, float pitch) {
        player.stopRiding();
        internalTeleport = true;
        try { player.teleport(world, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, true); }
        finally { internalTeleport = false; }
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        player.velocityModified = true;
    }

    /** True while the summit itself is moving a player, for teleport rules added with the fight. */
    public static boolean internalTeleport() { return internalTeleport; }

    // ------------------------------------------------------------------ building

    static boolean ready(int slot) {
        refreshGeneration();
        return state.built.contains(slot) && !builds.containsKey(slot);
    }

    static boolean building(int slot) { return builds.containsKey(slot); }

    /** A slot's centre in the current generation. */
    static BlockPos slotCentre(int slot) {
        refreshGeneration();
        return StormSummit.centre(state.generation, slot);
    }

    /** Starts laying out a slot for an aerie call, which moves its players itself once it is ready. */
    static void raise(ServerWorld realm, int slot) { if (!ready(slot)) build(realm, slot); }

    /** A changed layout never overwrites an old one in place: it moves to untouched ground. */
    private static void refreshGeneration() {
        if (state.layout.equals(layout)) return;
        if (!state.layout.isEmpty()) state.generation++;
        state.layout = layout;
        state.built.clear();
        persist();
    }

    private static Build build(ServerWorld realm, int slot) {
        refreshGeneration();
        Build existing = builds.get(slot);
        if (existing != null) return existing;
        Build build = new Build(slot, StormSummit.centre(state.generation, slot));
        var templates = realm.getStructureTemplateManager();
        for (int i = 0; i < StormSummitLayout.TILE_COUNT; i++)
            for (int j = 0; j < StormSummitLayout.TILE_COUNT; j++) {
                Identifier id = StormSummit.tile(i, j);
                if (templates.getTemplate(id).isPresent()) {
                    build.tiles.add(id);
                    build.origins.add(StormSummit.tileOrigin(build.centre, i, j));
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
        // One 48x48 section per tick lays out a fresh slot in a few seconds without a long stall.
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
                player.sendMessage(Text.literal("The wind carries you up to the Storm Summit."), true);
            }
        }
        // Until the fight decides what a fall means, the wind sets a falling visitor back on the summit.
        for (ServerPlayerEntity player : realm.getPlayers()) {
            if (StormRocEncounter.handlesFall(player)) continue;
            if (player.isSpectator() || !player.isAlive()) continue;
            BlockPos centre = StormSummit.nearestCentre(player.getEntityPos());
            if (!StormSummit.fallen(centre, player.getEntityPos())) continue;
            transfer(player, realm, StormSummit.arrival(centre), StormSummit.ARRIVAL_YAW, 0);
            player.sendMessage(Text.literal("The wind throws you back onto the summit."), true);
        }
    }

    private static void place(ServerWorld realm, Identifier id, BlockPos origin) {
        StructureTemplate template = realm.getStructureTemplateManager().getTemplate(id).orElse(null);
        if (template == null) return;
        template.place(realm, origin, origin, new StructurePlacementData().setIgnoreEntities(true).setUpdateNeighbors(false),
                realm.getRandom(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }

    // ------------------------------------------------------------------ storage

    private static ServerWorld realm(MinecraftServer server) { return server.getWorld(StormSummit.WORLD); }

    private static Point point(ServerPlayerEntity player) {
        Vec3d pos = player.getEntityPos();
        return new Point(player.getEntityWorld().getRegistryKey().getValue().toString(), pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
    }

    /** Identifies the installed layout so a changed builder output never mixes with an old slot. */
    private static String fingerprint(MinecraftServer server) {
        CRC32 crc = new CRC32();
        for (int i = 0; i < StormSummitLayout.TILE_COUNT; i++)
            for (int j = 0; j < StormSummitLayout.TILE_COUNT; j++) {
                var resource = server.getResourceManager().getResource(
                        Identifier.of(ElementalWandsMod.MOD_ID, "structure/storm_summit/summit_" + i + "_" + j + ".nbt"));
                if (resource.isEmpty()) continue;
                try (var in = resource.get().getInputStream()) {
                    crc.update(i * 31 + j);
                    crc.update(in.readAllBytes());
                } catch (IOException e) { LOG.error("Could not read Storm Summit tile {} {}", i, j, e); }
            }
        return Long.toHexString(crc.getValue());
    }

    private static void load(MinecraftServer server) {
        builds.clear();
        kept.clear();
        layout = fingerprint(server);
        path = server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands").resolve("storm-summit.json");
        try {
            state = Files.exists(path) ? JSON.fromJson(Files.readString(path), State.class) : new State();
            if (state == null || state.version != 1 || state.built == null || state.returns == null || state.layout == null)
                throw new IOException("Invalid Storm Summit record");
            // Records from before the aerie lack these.
            if (state.modes == null) state.modes = new LinkedHashMap<>();
            if (state.called == null) state.called = new ArrayList<>();
            if (state.sites == null) state.sites = new LinkedHashMap<>();
            persist();
        } catch (Exception e) {
            state = null;
            LOG.error("Storm Summit storage needs attention; the summit is closed", e);
        }
    }

    static boolean persist() {
        if (state == null || path == null) return false;
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, JSON.toJson(state), StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOG.error("Could not save the Storm Summit record", e);
            return false;
        }
    }
}
