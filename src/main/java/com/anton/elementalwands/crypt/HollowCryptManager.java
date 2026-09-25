package com.anton.elementalwands.crypt;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.arena.GuardianArenaJournal.Point;
import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
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
 * The Hollow Crypt: lays out the clearing in a slot, moves players in and out with journaled
 * return points and keeps fighters inside the clearing. The overworld graveyard's headstone
 * takes the nearby group in and the boss rises; its death sends everyone home. The offering,
 * sealed party, spectating and rewards are not built yet.
 */
public final class HollowCryptManager {
    private static final Logger LOG = LoggerFactory.getLogger("elementalwands-crypt");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    static final class State {
        int version = 1;
        /** Bumped when the layout changes, which moves every slot to untouched ground. */
        int generation;
        String layout = "";
        List<Integer> built = new ArrayList<>();
        Map<String, Point> returns = new LinkedHashMap<>();
    }

    private static final class Build {
        final int slot;
        final BlockPos centre;
        final List<Identifier> tiles = new ArrayList<>();
        final List<BlockPos> origins = new ArrayList<>();
        final Set<UUID> waiting = new LinkedHashSet<>();
        boolean ritual;
        int next;
        Build(int slot, BlockPos centre) { this.slot = slot; this.centre = centre; }
    }

    private static Path path;
    private static State state;
    private static String layout = "";
    private static final Map<Integer, Build> builds = new HashMap<>();
    private static boolean internalTeleport;
    /** Server tick at which a ritual slot's boss rises, and at which a won slot sends players home. */
    private static final Map<Integer, Integer> risings = new HashMap<>(), releases = new HashMap<>();
    private static final int RISE_DELAY = 60, RELEASE_DELAY = 200, RITUAL_RADIUS = 16;

    private HollowCryptManager() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(HollowCryptManager::load);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> layout = fingerprint(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { state = null; path = null; builds.clear(); risings.clear(); releases.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(HollowCryptManager::tick);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // Death in the realm sends the player to their usual spawn; the return point is spent.
            if (!alive && state != null && !inRealm(newPlayer) && state.returns.remove(newPlayer.getUuidAsString()) != null) persist();
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof NecromancerEntity) || !inRealm(entity) || state == null) return;
            BlockPos centre = HollowCryptRealm.nearestCentre(entity.getEntityPos());
            int slot = slotOf(centre);
            if (slot < 0) return;
            releases.put(slot, entity.getEntityWorld().getServer().getTicks() + RELEASE_DELAY);
            tell((ServerWorld) entity.getEntityWorld(), centre, "The Hollow Necromancer is destroyed. The crypt releases you in 10 seconds.");
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || !(world instanceof ServerWorld) || !(player instanceof ServerPlayerEntity caller)
                    || world.getRegistryKey() == HollowCryptRealm.WORLD) return ActionResult.PASS;
            BlockPos skull = headstone(world, hit.getBlockPos());
            if (skull == null) return ActionResult.PASS;
            caller.sendMessage(Text.literal(ritual(caller, skull)), true);
            return ActionResult.SUCCESS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, block, entity) -> !restricted(world, player));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Item item = player.getStackInHand(hand).getItem();
            return restricted(world, player) && (item instanceof BlockItem || item instanceof BucketItem || item instanceof FlintAndSteelItem
                    || item instanceof FireChargeItem || item instanceof BoneMealItem) ? ActionResult.FAIL : ActionResult.PASS;
        });
    }

    // ------------------------------------------------------------------ rules other systems ask

    public static boolean inRealm(Entity entity) { return entity.getEntityWorld().getRegistryKey() == HollowCryptRealm.WORLD; }

    private static boolean restricted(World world, PlayerEntity player) {
        return world.getRegistryKey() == HollowCryptRealm.WORLD && !player.isCreative();
    }

    /**
     * Every player teleport (spells, pearls, commands, portals) stays inside the clearing and
     * never crosses into or out of the realm; only the crypt's own entry and exit do that.
     */
    public static boolean canTeleport(PlayerEntity player, ServerWorld destination, Vec3d target) {
        if (internalTeleport || player.isCreative() || player.isSpectator()) return true;
        boolean from = inRealm(player), to = destination.getRegistryKey() == HollowCryptRealm.WORLD;
        if (!from && !to) return true;
        return from && to && HollowCryptRealm.inPlay(HollowCryptRealm.nearestCentre(player.getEntityPos()), target, .4);
    }

    // ------------------------------------------------------------------ graveyard ritual

    /**
     * The graveyard's headstone altar, recognised by its blocks so natural, /place'd and rebuilt
     * copies all work in any rotation: a skeleton skull on two carved deepslate blocks, the lower
     * one flanked by more carving, on a polished deepslate plinth. Returns the skull, or null.
     */
    static BlockPos headstone(World world, BlockPos clicked) {
        BlockState state = world.getBlockState(clicked);
        if (!state.isOf(Blocks.CHISELED_DEEPSLATE) && !state.isOf(Blocks.SKELETON_SKULL) && !state.isOf(Blocks.POLISHED_DEEPSLATE)) return null;
        for (BlockPos skull : BlockPos.iterate(clicked.add(-1, 0, -1), clicked.add(1, 2, 1))) {
            if (!world.getBlockState(skull).isOf(Blocks.SKELETON_SKULL)) continue;
            BlockPos upper = skull.down(), lower = skull.down(2);
            if (world.getBlockState(upper).isOf(Blocks.CHISELED_DEEPSLATE) && world.getBlockState(lower).isOf(Blocks.CHISELED_DEEPSLATE)
                    && (carved(world, lower.east()) && carved(world, lower.west()) || carved(world, lower.north()) && carved(world, lower.south()))
                    && world.getBlockState(skull.down(3)).isOf(Blocks.POLISHED_DEEPSLATE)) return skull.toImmutable();
        }
        return null;
    }

    private static boolean carved(World world, BlockPos pos) { return world.getBlockState(pos).isOf(Blocks.CHISELED_DEEPSLATE); }

    /** The headstone pulls every living player near it into a free slot; the boss rises shortly after. */
    static String ritual(ServerPlayerEntity caller, BlockPos headstone) {
        ServerWorld here = (ServerWorld) caller.getEntityWorld();
        ServerWorld realm = realm(here.getServer());
        if (realm == null || state == null) return "The grave is silent. (The crypt is unavailable; see the server log.)";
        refreshGeneration();
        int slot = freeSlot(realm);
        if (slot < 0) return "The crypt is full. Try again when a fight ends.";
        List<ServerPlayerEntity> group = here.getPlayers(p -> p.isAlive() && !p.isSpectator()
                && p.squaredDistanceTo(Vec3d.ofCenter(headstone)) <= RITUAL_RADIUS * RITUAL_RADIUS);
        for (ServerPlayerEntity p : group) state.returns.put(p.getUuidAsString(), point(p));
        if (!persist()) return "The grave is silent. (Could not record return points.)";
        here.playSound(null, headstone, SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 1.4f, .6f);
        here.playSound(null, headstone, SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 2f, .7f);
        here.spawnParticles(ParticleTypes.SOUL, headstone.getX() + .5, headstone.getY() + .5, headstone.getZ() + 1.5, 60, 1.5, .6, 1.5, .03);
        for (ServerPlayerEntity p : group) p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0, false, false));
        if (ready(slot)) {
            group.forEach(p -> arrive(p, realm, slot));
            risings.put(slot, here.getServer().getTicks() + RISE_DELAY);
        } else {
            Build build = build(realm, slot);
            build.ritual = true;
            group.forEach(p -> build.waiting.add(p.getUuid()));
        }
        return "The grave drags " + (group.size() == 1 ? "you" : group.size() + " of you") + " under...";
    }

    /** A slot with nobody fighting in it, nothing building and no pending rise or release. */
    private static int freeSlot(ServerWorld realm) {
        for (int slot = 0; slot < HollowCryptRealm.SLOTS; slot++) {
            if (builds.containsKey(slot) || risings.containsKey(slot) || releases.containsKey(slot)) continue;
            var area = HollowCryptRealm.footprint(HollowCryptRealm.centre(state.generation, slot));
            if (realm.getPlayers(p -> !p.isSpectator() && area.contains(p.getEntityPos())).isEmpty()) return slot;
        }
        return -1;
    }

    private static void tell(ServerWorld realm, BlockPos centre, String message) {
        var area = HollowCryptRealm.footprint(centre);
        for (ServerPlayerEntity p : realm.getPlayers(p -> area.contains(p.getEntityPos()))) p.sendMessage(Text.literal(message), false);
    }

    /** Clears leftovers from any earlier fight and raises a fighting boss on the circle. */
    private static NecromancerEntity raise(ServerWorld realm, BlockPos centre) {
        clearEntities(realm, centre);
        NecromancerEntity boss = ModEntities.HOLLOW_NECROMANCER.create(realm, SpawnReason.COMMAND);
        if (boss == null) return null;
        Vec3d at = HollowCryptRealm.summonPoint(centre);
        boss.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        boss.setHeadYaw(0);
        realm.spawnEntity(boss);
        boss.startFight();
        realm.spawnParticles(ParticleTypes.SOUL, at.x, at.y + .5, at.z, 80, 1.2, .4, 1.2, .04);
        realm.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 3f, .5f);
        return boss;
    }

    // ------------------------------------------------------------------ operator commands

    public static String enter(ServerPlayerEntity player, int slot) {
        ServerWorld realm = realm(player.getEntityWorld().getServer());
        if (realm == null) return "The Hollow Crypt dimension is not loaded.";
        if (state == null) return "Hollow Crypt storage is unavailable; see the server log.";
        if (!inRealm(player)) {
            state.returns.put(player.getUuidAsString(), point(player));
            if (!persist()) return "Could not record your return point, so you were not moved.";
        }
        if (ready(slot)) {
            arrive(player, realm, slot);
            return "Entered crypt slot " + slot + ". /ew crypt summon raises the boss; /ew crypt leave takes you back.";
        }
        Build build = build(realm, slot);
        build.waiting.add(player.getUuid());
        return "Raising crypt slot " + slot + " (" + build.tiles.size() + " sections); you will be taken in when it is ready.";
    }

    public static String leave(ServerPlayerEntity player) {
        if (!inRealm(player)) return "You are not in the Hollow Crypt.";
        MinecraftServer server = player.getEntityWorld().getServer();
        Point point = state == null ? null : state.returns.get(player.getUuidAsString());
        ServerWorld world = point == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(point.dimension())));
        if (world != null && world.getRegistryKey() != HollowCryptRealm.WORLD) {
            transfer(player, world, new Vec3d(point.x(), point.y(), point.z()), point.yaw(), point.pitch());
        } else {
            ServerWorld spawn = server.getSpawnWorld();
            BlockPos pos = server.getSpawnPoint().getPos();
            spawn.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            int y = spawn.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
            transfer(player, spawn, new Vec3d(pos.getX() + .5, y, pos.getZ() + .5), server.getSpawnPoint().yaw(), 0);
        }
        if (state != null && state.returns.remove(player.getUuidAsString()) != null) persist();
        return world != null ? "Returned to where you entered." : "No return point was recorded; sent to world spawn.";
    }

    public static String summon(ServerPlayerEntity player) {
        if (!inRealm(player)) return "Enter the crypt first with /ew crypt enter.";
        ServerWorld realm = (ServerWorld) player.getEntityWorld();
        BlockPos centre = HollowCryptRealm.nearestCentre(player.getEntityPos());
        return raise(realm, centre) == null ? "Could not create the Hollow Necromancer." : "The Hollow Necromancer rises.";
    }

    public static String reset(ServerPlayerEntity player) {
        if (!inRealm(player)) return "Stand in the crypt slot you want to reset.";
        ServerWorld realm = (ServerWorld) player.getEntityWorld();
        BlockPos centre = HollowCryptRealm.nearestCentre(player.getEntityPos());
        int slot = slotOf(centre);
        int cleared = clearEntities(realm, centre);
        if (slot < 0) return "Cleared " + cleared + " entities. This slot is from an older layout, so it was not rebuilt.";
        Build build = build(realm, slot);
        return "Cleared " + cleared + " entities; restoring " + build.tiles.size() + " sections of slot " + slot + ".";
    }

    public static String status() {
        if (state == null) return "Hollow Crypt storage is unavailable.";
        StringBuilder out = new StringBuilder("Hollow Crypt: layout generation " + state.generation
                + (state.layout.equals(layout) ? "" : " (layout changed; slots rebuild on next entry)")
                + ", built slots " + state.built + ", " + state.returns.size() + " return point(s) held.");
        builds.values().forEach(b -> out.append(" Slot ").append(b.slot).append(" building ").append(b.next).append('/').append(b.tiles.size()).append('.'));
        return out.toString();
    }

    // ------------------------------------------------------------------ building

    private static boolean ready(int slot) {
        refreshGeneration();
        return state.built.contains(slot) && !builds.containsKey(slot);
    }

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
        Build build = new Build(slot, HollowCryptRealm.centre(state.generation, slot));
        var templates = realm.getStructureTemplateManager();
        for (int i = 0; i < HollowCryptRealm.TILE_COUNT; i++)
            for (int j = 0; j < HollowCryptRealm.TILE_COUNT; j++) {
                Identifier id = HollowCryptRealm.tile(i, j);
                if (templates.getTemplate(id).isPresent()) {
                    build.tiles.add(id);
                    build.origins.add(HollowCryptRealm.tileOrigin(build.centre, i, j));
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
        // One 48x48 section per tick keeps a fresh slot to a couple of seconds without a long stall.
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
                if (player != null && player.isAlive()) {
                    arrive(player, realm, build.slot);
                    player.sendMessage(Text.literal("The Hollow Crypt opens."), true);
                }
            }
            if (build.ritual) risings.put(build.slot, server.getTicks() + RISE_DELAY);
        }
        int now = server.getTicks();
        risings.entrySet().removeIf(rise -> {
            if (now < rise.getValue()) return false;
            BlockPos centre = HollowCryptRealm.centre(state.generation, rise.getKey());
            if (raise(realm, centre) != null) tell(realm, centre, "The Hollow Necromancer rises from the grave.");
            return true;
        });
        releases.entrySet().removeIf(release -> {
            if (now < release.getValue()) return false;
            var area = HollowCryptRealm.footprint(HollowCryptRealm.centre(state.generation, release.getKey()));
            for (ServerPlayerEntity p : realm.getPlayers(p -> area.contains(p.getEntityPos()))) leave(p);
            return true;
        });
        for (ServerPlayerEntity player : realm.getPlayers()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
            BlockPos centre = HollowCryptRealm.nearestCentre(player.getEntityPos());
            if (!HollowCryptRealm.inPlay(centre, player.getEntityPos(), 0)) {
                Vec3d inside = HollowCryptRealm.clampToPlay(centre, player.getEntityPos());
                player.networkHandler.requestTeleport(inside.x, inside.y, inside.z, player.getYaw(), player.getPitch());
                player.setVelocity(Vec3d.ZERO);
                player.velocityModified = true;
                player.fallDistance = 0;
            }
        }
    }

    private static void place(ServerWorld realm, Identifier id, BlockPos origin) {
        StructureTemplate template = realm.getStructureTemplateManager().getTemplate(id).orElse(null);
        if (template == null) return;
        template.place(realm, origin, origin, new StructurePlacementData().setIgnoreEntities(true).setUpdateNeighbors(false),
                realm.getRandom(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }

    private static int clearEntities(ServerWorld realm, BlockPos centre) {
        List<Entity> leftovers = realm.getOtherEntities(null, HollowCryptRealm.footprint(centre), e -> !(e instanceof PlayerEntity));
        leftovers.forEach(Entity::discard);
        return leftovers.size();
    }

    private static int slotOf(BlockPos centre) {
        for (int slot : state.built)
            if (HollowCryptRealm.centre(state.generation, slot).equals(centre)) return slot;
        for (Build build : builds.values()) if (build.centre.equals(centre)) return build.slot;
        return -1;
    }

    private static void arrive(ServerPlayerEntity player, ServerWorld realm, int slot) {
        transfer(player, realm, HollowCryptRealm.arrival(HollowCryptRealm.centre(state.generation, slot)), HollowCryptRealm.ARRIVAL_YAW, 0);
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

    // ------------------------------------------------------------------ storage

    private static ServerWorld realm(MinecraftServer server) { return server.getWorld(HollowCryptRealm.WORLD); }

    private static Point point(ServerPlayerEntity player) {
        Vec3d pos = player.getEntityPos();
        return new Point(player.getEntityWorld().getRegistryKey().getValue().toString(), pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
    }

    /** Identifies the installed layout so a changed builder output never mixes with an old slot. */
    private static String fingerprint(MinecraftServer server) {
        CRC32 crc = new CRC32();
        for (int i = 0; i < HollowCryptRealm.TILE_COUNT; i++)
            for (int j = 0; j < HollowCryptRealm.TILE_COUNT; j++) {
                var resource = server.getResourceManager().getResource(
                        Identifier.of(ElementalWandsMod.MOD_ID, "structure/hollow_crypt/realm_" + i + "_" + j + ".nbt"));
                if (resource.isEmpty()) continue;
                try (var in = resource.get().getInputStream()) {
                    crc.update(i * 31 + j);
                    crc.update(in.readAllBytes());
                } catch (IOException e) { LOG.error("Could not read Hollow Crypt tile {} {}", i, j, e); }
            }
        return Long.toHexString(crc.getValue());
    }

    private static void load(MinecraftServer server) {
        builds.clear();
        layout = fingerprint(server);
        path = server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands/hollow-crypt.json");
        try {
            state = Files.exists(path) ? JSON.fromJson(Files.readString(path), State.class) : new State();
            if (state == null || state.version != 1 || state.built == null || state.returns == null || state.layout == null)
                throw new IOException("Invalid Hollow Crypt record");
        } catch (Exception e) {
            state = null;
            LOG.error("Hollow Crypt storage needs attention; crypt commands are disabled", e);
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
            LOG.error("Could not save the Hollow Crypt record", e);
            return false;
        }
    }
}
