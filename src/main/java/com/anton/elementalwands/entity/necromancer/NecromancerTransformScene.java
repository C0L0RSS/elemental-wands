package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.SoulGlow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

/**
 * The cinematic of the Necromancer's transformation into the colossus. He drops out of the dark
 * onto his staff, which snaps; the souls trapped in it break loose into a storm, two of them floor
 * him and the rest pour into his face. On his back, his fingers stretch into claws and each
 * convulsion is a growth spurt that rips his robe further, until inside a burst of smoke he is the
 * giant: it sits up, its eyes ignite, it crawls at the party and howls, and the braziers flare back.
 * <p>
 * The server runs the timeline on the transformation's clock, holds every watcher still and
 * unhurt, and plays the sounds, smoke and ghosts; each watcher's client flies its camera along the
 * same clock ({@link NecromancerTransformTrack}). Everyone can skip; the scene ends early only once
 * all of them have. {@link NecromancerCombat} owns the boss's side: the clip, the hitbox and the
 * hand-over to the colossus fight.
 */
public final class NecromancerTransformScene {
    /** A unanimous skip ends the scene no sooner than this, so a key still held from before can't. */
    public static final int SKIP_AFTER = 20;
    /** The braziers the souls' storm puts out, and the howl relights. */
    static final double BRAZIER_REACH = 44;

    private static final Map<UUID, NecromancerTransformScene> WATCHING = new HashMap<>();

    private final NecromancerEntity boss;
    private final ServerWorld world;
    private final Vec3d centre;
    private final float yaw;
    private final long start;
    private final Map<ServerPlayerEntity, Vec3d> watchers = new LinkedHashMap<>();
    private final Set<UUID> skipped = new HashSet<>();
    /** Lit braziers the storm put out; the howl, a skip or a cancel relights just these. */
    private final List<BlockPos> doused = new ArrayList<>();
    private final Map<Integer, TransformSoulEntity> souls = new HashMap<>();
    private final NecromancerTransformTrack track = NecromancerTransformTrack.get();
    private int clock = -1;

    /** Turns the boss to face the party and holds everyone watching where they stand. */
    NecromancerTransformScene(NecromancerEntity boss, ServerWorld world, List<ServerPlayerEntity> players, long start) {
        this.boss = boss;
        this.world = world;
        this.start = start;
        centre = boss.getEntityPos();
        Vec3d party = Vec3d.ZERO;
        for (ServerPlayerEntity player : players) party = party.add(player.getEntityPos());
        // The scene's front, toward the party: it crawls at them and howls in their direction.
        yaw = players.isEmpty() ? boss.getYaw() : facing(centre, party.multiply(1.0 / players.size()));
        for (ServerPlayerEntity player : players) {
            NecromancerTransformScene previous = WATCHING.put(player.getUuid(), this);
            if (previous != null && previous != this) previous.watchers.remove(player);
            player.removeStatusEffect(StatusEffects.BLINDNESS);
            Vec3d at = player.getEntityPos();
            // Everyone faces the boss, so the camera's return lands on a view of the colossus.
            player.teleport(world, at.x, at.y, at.z, Set.of(), facing(at, centre), 0, false);
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0;
            watchers.put(player, at);
        }
        hold();
        for (ServerPlayerEntity player : watchers.keySet())
            if (ServerPlayNetworking.canSend(player, ModNetworking.NecromancerTransformPayload.ID))
                ServerPlayNetworking.send(player, payload(true));
    }

    public static void register() {
        // Watchers can't be hurt while their camera is somewhere else; only /kill and the void still reach them.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof PlayerEntity
                && WATCHING.containsKey(entity.getUuid()) && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHING.clear());
        // A leaver is let go at once, and must not rejoin still held.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            NecromancerTransformScene scene = WATCHING.remove(handler.getPlayer().getUuid());
            if (scene != null) scene.watchers.remove(handler.getPlayer());
        });
    }

    /** A player held in the transformation: frozen, unhurt and unable to cast. */
    public static boolean watching(PlayerEntity player) { return WATCHING.containsKey(player.getUuid()); }

    /** A watcher asked to skip; the scene ends early only once every watcher has. */
    public static void skip(ServerPlayerEntity player) {
        NecromancerTransformScene scene = WATCHING.get(player.getUuid());
        if (scene != null) scene.skipped.add(player.getUuid());
    }

    /** A point of the scene's frame, turned into the world: +Z ahead of the boss, +X on his left. */
    public static Vec3d place(Vec3d centre, float yaw, Vec3d local) {
        double r = Math.toRadians(yaw), c = Math.cos(r), s = Math.sin(r);
        return centre.add(local.x * c - local.z * s, local.y, local.x * s + local.z * c);
    }

    private Vec3d place(Vec3d local) { return place(centre, yaw, local); }

    static float facing(Vec3d from, Vec3d to) { return (float)Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z)); }

    float yaw() { return yaw; }

    /** Every watcher has skipped, and the scene has run long enough for that to be deliberate. */
    boolean everyoneSkipped() {
        if (clock < SKIP_AFTER || watchers.isEmpty()) return false;
        for (ServerPlayerEntity player : watchers.keySet()) if (!skipped.contains(player.getUuid())) return false;
        return true;
    }

    /** Runs scene tick {@code t} of the transformation's clock. */
    void tick(int t) {
        hold();
        holdWatchers();
        for (int k = clock + 1; k <= t; k++) stage(k); // A lagging server still plays every beat once.
        clock = Math.max(clock, t);
    }

    private ModNetworking.NecromancerTransformPayload payload(boolean active) {
        return new ModNetworking.NecromancerTransformPayload(boss.getId(), start, yaw, centre, active);
    }

    /** The boss keeps its spot and its facing, whatever pushes on it. */
    private void hold() {
        boss.setYaw(yaw); boss.setBodyYaw(yaw); boss.setHeadYaw(yaw);
        boss.setVelocity(0, Math.min(0, boss.getVelocity().y), 0);
    }

    /** Keeps every watcher where the scene found them; leavers stop counting toward the skip. */
    private void holdWatchers() {
        for (var it = watchers.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            ServerPlayerEntity player = entry.getKey();
            if (player.isRemoved() || !player.isAlive() || player.getEntityWorld() != world) {
                WATCHING.remove(player.getUuid(), this);
                player.fallDistance = 0;
                // Killed or gone to another dimension: its client must let go of the camera too.
                if (!player.isRemoved() && ServerPlayNetworking.canSend(player, ModNetworking.NecromancerTransformPayload.ID))
                    ServerPlayNetworking.send(player, payload(false));
                it.remove();
                continue;
            }
            Vec3d lock = entry.getValue();
            if (player.getEntityPos().squaredDistanceTo(lock) > .01)
                player.teleport(world, lock.x, lock.y, lock.z, Set.of(), player.getYaw(), player.getPitch(), false);
            player.setVelocity(Vec3d.ZERO);
            // A watcher caught mid-jump keeps sinking toward the ground and being put back; none of that is a fall.
            player.fallDistance = 0;
        }
    }

    private void stage(int t) {
        for (NecromancerTransformTrack.Cue cue : track.cues()) if (cue.tick() == t) cue(cue.cue(), t);
        for (NecromancerTransformTrack.Smoke smoke : track.smoke()) if (smoke.tick() == t) smoke(smoke);
        for (NecromancerTransformTrack.Effect effect : track.effects()) if (effect.tick() == t) effect(effect);
        // The souls' real light: at the cracks, in the storm, in him, then in the colossus.
        float glow = track.glow(t);
        if (glow > .05f && t % SoulGlow.CHECK == 0)
            SoulGlow.light(world, place(track.glowAt(t)), Math.round(4 + 10 * glow), 6);
        // The trapped souls seep out of the broken ends before they burst free.
        if (t >= track.beat("shake") && t < track.beat("burst") && t % 3 == 0) {
            Vec3d cracks = place(track.glowAt(t));
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, cracks.x, cracks.y, cracks.z, 2, .08, .08, .08, .01);
        }
        if (t > 0 && track.braziers(t - 1) && !track.braziers(t)) douse();
        if (t > 0 && !track.braziers(t - 1) && track.braziers(t)) relight(true);
        for (NecromancerTransformTrack.Soul soul : track.souls()) {
            // Each ghost arrives a tick before it shows, so every client has it in time.
            if (t == soul.first() - 1) spawn(soul);
            if (t > soul.last() + 1) {
                TransformSoulEntity entity = souls.remove(soul.index());
                if (entity != null) entity.discard();
            }
        }
    }

    private void spawn(NecromancerTransformTrack.Soul soul) {
        TransformSoulEntity entity = ModEntities.TRANSFORM_SOUL.create(world, SpawnReason.EVENT);
        if (entity == null) return;
        entity.script(soul.index(), start, centre, yaw);
        world.spawnEntity(entity);
        souls.put(soul.index(), entity);
    }

    // ------------------------------------------------------------------ sound

    private void cue(String cue, int t) {
        Vec3d at = centre.add(0, 1, 0);
        switch (cue) {
            case "wind rush" -> sound(at.add(0, 6, 0), SoundEvents.ENTITY_PHANTOM_SWOOP, 2.5f, .5f);
            case "crash" -> {
                sound(at, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 3f, .55f);
                sound(at, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, .5f);
                dust(centre, 40);
            }
            case "staff snaps" -> {
                sound(at, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 2.5f, 1.3f);
                sound(at, SoundEvents.BLOCK_WOOD_BREAK, 2.5f, .6f);
            }
            case "braziers gutter out" -> sound(at, SoundEvents.BLOCK_FIRE_EXTINGUISH, 3f, .5f);
            case "heartbeat" -> sound(at, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 3f, .55f);
            case "the halves rattle" -> sound(at, SoundEvents.BLOCK_WOOD_HIT, 2f, .5f);
            case "whispers" -> sound(at, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 3f, .7f);
            case "souls shriek free" -> {
                sound(at, SoundEvents.ENTITY_GHAST_SCREAM, 2.5f, .8f);
                sound(at, SoundEvents.ENTITY_VEX_CHARGE, 2.5f, .6f);
                sound(at, SoundEvents.PARTICLE_SOUL_ESCAPE, 3f, 1f);
            }
            case "wailing storm" -> {
                sound(at.add(0, 3, 0), SoundEvents.ENTITY_VEX_AMBIENT, 2.5f, .6f);
                sound(at.add(0, 3, 0), SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 3f, 1.2f);
            }
            case "a soul strikes" -> {
                sound(at, SoundEvents.ENTITY_PHANTOM_BITE, 2.5f, .6f);
                sound(at, SoundEvents.ENTITY_VEX_CHARGE, 2.5f, .5f);
            }
            case "thud" -> {
                sound(centre, SoundEvents.ENTITY_GENERIC_BIG_FALL, 2.5f, .6f);
                dust(centre, 24);
            }
            case "souls rush in" -> {
                sound(at, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.5f, .6f);
                sound(at, SoundEvents.PARTICLE_SOUL_ESCAPE, 3f, .6f);
            }
            case "the eye light gutters" -> sound(at, SoundEvents.BLOCK_CANDLE_EXTINGUISH, 2.5f, .5f);
            case "eyes go dark" -> sound(at, SoundEvents.BLOCK_BEACON_DEACTIVATE, 2.5f, .5f);
            case "choking" -> sound(at, SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, 2.5f, .55f);
            case "bones crack", "finger bones crack" -> sound(at, SoundEvents.BLOCK_BONE_BLOCK_BREAK, 2.5f, cue.startsWith("finger") ? 1.3f : .7f);
            case "hiss of smoke" -> sound(at, SoundEvents.BLOCK_FIRE_EXTINGUISH, 2f, .4f);
            case "a hand jerks up" -> sound(at, SoundEvents.ENTITY_PHANTOM_FLAP, 2f, .8f);
            case "the hand slams down" -> sound(at, SoundEvents.ITEM_MACE_SMASH_GROUND, 2.5f, .8f);
            case "bones grind and stretch" -> {
                sound(at, SoundEvents.BLOCK_GRINDSTONE_USE, 2.5f, .4f);
                sound(at, SoundEvents.BLOCK_BONE_BLOCK_BREAK, 2.5f, .5f);
            }
            case "a seam rips", "cloth tears" -> {
                sound(at, SoundEvents.ENTITY_SHEEP_SHEAR, 2.5f, .55f);
                sound(at, SoundEvents.BLOCK_WOOL_BREAK, 2.5f, .7f);
            }
            case "the robe tears apart" -> {
                sound(at, SoundEvents.ENTITY_GENERIC_EXPLODE, 2f, .45f);
                sound(at, SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 2.5f, .5f);
                sound(at, SoundEvents.BLOCK_WOOL_BREAK, 3f, .5f);
            }
            case "a spasm" -> sound(at, SoundEvents.ENTITY_SKELETON_HURT, 2.5f, .45f);
            case "rags slap the stone" -> sound(centre, SoundEvents.BLOCK_WOOL_FALL, 2.5f, .6f);
            case "it sits up" -> sound(at, SoundEvents.ENTITY_SKELETON_AMBIENT, 2.5f, .4f);
            case "bone creaks" -> sound(at.add(0, 2, 0), SoundEvents.ENTITY_SKELETON_AMBIENT, 2.5f, .3f);
            case "soul fire ignites" -> {
                sound(at.add(0, 3, 0), SoundEvents.ITEM_FIRECHARGE_USE, 2.5f, .5f);
                sound(at.add(0, 3, 0), SoundEvents.BLOCK_BEACON_ACTIVATE, 2.5f, .5f);
            }
            case "a claw strikes stone" -> sound(centre, SoundEvents.ENTITY_IRON_GOLEM_STEP, 2.5f, .5f);
            case "bone scraping stone", "claws grind into the stone" -> sound(centre, SoundEvents.BLOCK_GRINDSTONE_USE, 2.5f, cue.startsWith("claws") ? .35f : .5f);
            case "a howl" -> {
                sound(at.add(0, 3, 0), SoundEvents.ENTITY_WARDEN_ROAR, 3f, .6f);
                sound(at.add(0, 3, 0), SoundEvents.ENTITY_RAVAGER_ROAR, 3f, .45f);
                Vec3d skull = at.add(0, 3, 0);
                world.spawnParticles(ParticleTypes.SCULK_SOUL, true, false, skull.x, skull.y, skull.z, 30, 1.5, 1.2, 1.5, .08);
            }
            case "braziers flare" -> sound(at, SoundEvents.ITEM_FIRECHARGE_USE, 3f, .6f);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ smoke, chips and embers

    /** The preview's smoke, as particles: wisps from his face, the spurts' puffs and the burst that hides the swap. */
    private void smoke(NecromancerTransformTrack.Smoke smoke) {
        Vec3d at = place(smoke.at());
        double spread = smoke.spread();
        switch (smoke.kind()) {
            case "wisp" -> {
                particles(ParticleTypes.SMOKE, at, 4, .06, .06, .02);
                particles(ParticleTypes.LARGE_SMOKE, at, 1, .05, .05, .01);
            }
            case "puff" -> particles(ParticleTypes.LARGE_SMOKE, at, smoke.count() * 5, spread * .5, .25, .03);
            case "burst" -> {
                // Thick enough to hide the robed body becoming the giant.
                particles(ParticleTypes.LARGE_SMOKE, at, 110, spread * .55, .9, .05);
                particles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 14, spread * .5, .6, .01);
                particles(ParticleTypes.SQUID_INK, at, 30, spread * .4, .6, .04);
                particles(ParticleTypes.SOUL_FIRE_FLAME, at, 24, spread * .4, .5, .06);
            }
            case "ring" -> {
                for (int i = 0; i < smoke.count() * 3; i++) {
                    double angle = i * Math.PI * 2 / (smoke.count() * 3);
                    Vec3d out = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
                    world.spawnParticles(ParticleTypes.POOF, true, false, at.x + out.x * .6, at.y, at.z + out.z * .6, 0, out.x, .02, out.z, .25);
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, true, false, at.x + out.x, at.y, at.z + out.z, 0, out.x, .01, out.z, .12);
                }
            }
            case "cloak" -> particles(ParticleTypes.LARGE_SMOKE, at, smoke.count() * 5, spread * .45, .12, .01);
            case "dust" -> dust(at, smoke.count() * 5);
            default -> {}
        }
    }

    private void effect(NecromancerTransformTrack.Effect effect) {
        Vec3d at = place(effect.at());
        if (effect.kind().equals("chip")) {
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floor(at)), true, false, at.x, at.y + .05, at.z,
                    effect.count() * 2, .2, .05, .2, .15);
        } else {
            particles(ParticleTypes.SOUL_FIRE_FLAME, at, effect.count(), .35, .3, .03);
            particles(ParticleTypes.SOUL, at, effect.count() / 2, .4, .3, .03);
        }
    }

    private void dust(Vec3d at, int count) {
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floor(at)), true, false, at.x, at.y + .15, at.z, count, .7, .1, .7, .1);
    }

    // ------------------------------------------------------------------ braziers

    /**
     * The storm smothers every lit soul campfire in reach. In the crypt these are the circle's
     * braziers; elsewhere (the operator command plays anywhere) it only borrows ones that burn.
     */
    private void douse() {
        int reach = (int)BRAZIER_REACH + 1;
        BlockPos c = BlockPos.ofFloored(centre);
        for (int cx = ChunkSectionPos.getSectionCoord(c.getX() - reach); cx <= ChunkSectionPos.getSectionCoord(c.getX() + reach); cx++)
            for (int cz = ChunkSectionPos.getSectionCoord(c.getZ() - reach); cz <= ChunkSectionPos.getSectionCoord(c.getZ() + reach); cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (BlockPos pos : BlockPos.iterate(Math.max(cx << 4, c.getX() - reach), c.getY() - 2, Math.max(cz << 4, c.getZ() - reach),
                        Math.min((cx << 4) + 15, c.getX() + reach), c.getY() + 5, Math.min((cz << 4) + 15, c.getZ() + reach))) {
                    BlockState state = world.getBlockState(pos);
                    if (!state.isOf(Blocks.SOUL_CAMPFIRE) || !state.get(CampfireBlock.LIT)) continue;
                    doused.add(pos.toImmutable());
                    world.setBlockState(pos, state.with(CampfireBlock.LIT, false));
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, true, false, pos.getX() + .5, pos.getY() + .7, pos.getZ() + .5, 6, .2, .2, .2, .02);
                }
            }
    }

    /**
     * A transformation saved mid-scene resumes as the colossus, but its storm may have left the
     * braziers out with their chunks. In the crypt every soul campfire about the circle is one of
     * them, lit since the intro: relight those that are loaded.
     */
    static void relightCrypt(ServerWorld world, Vec3d centre) {
        if (world.getRegistryKey() != com.anton.elementalwands.crypt.HollowCryptRealm.WORLD) return;
        int reach = (int)BRAZIER_REACH + 1;
        BlockPos c = BlockPos.ofFloored(centre);
        for (int cx = ChunkSectionPos.getSectionCoord(c.getX() - reach); cx <= ChunkSectionPos.getSectionCoord(c.getX() + reach); cx++)
            for (int cz = ChunkSectionPos.getSectionCoord(c.getZ() - reach); cz <= ChunkSectionPos.getSectionCoord(c.getZ() + reach); cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                for (BlockPos pos : BlockPos.iterate(Math.max(cx << 4, c.getX() - reach), c.getY() - 2, Math.max(cz << 4, c.getZ() - reach),
                        Math.min((cx << 4) + 15, c.getX() + reach), c.getY() + 5, Math.min((cz << 4) + 15, c.getZ() + reach))) {
                    BlockState state = world.getBlockState(pos);
                    if (state.isOf(Blocks.SOUL_CAMPFIRE) && !state.get(CampfireBlock.LIT)) world.setBlockState(pos, state.with(CampfireBlock.LIT, true));
                }
            }
    }

    /** Relights what the storm put out; a cancel never loads a chunk back to do it. */
    private void relight(boolean flare) {
        for (BlockPos pos : doused) {
            if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()))) continue;
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(Blocks.SOUL_CAMPFIRE)) continue;
            world.setBlockState(pos, state.with(CampfireBlock.LIT, true));
            if (flare) world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, pos.getX() + .5, pos.getY() + .8, pos.getZ() + .5, 18, .2, .35, .2, .06);
        }
        doused.clear();
    }

    // ------------------------------------------------------------------ the end

    /** The scene is over, played out or skipped: the ghosts go, the braziers burn and everyone is let go. */
    void finish() {
        clear();
        relight(false);
        release();
    }

    /** The boss is gone or an operator took over mid-scene: tidy up and let everyone go. */
    void cancel() { finish(); }

    private void clear() {
        souls.values().forEach(TransformSoulEntity::discard);
        souls.clear();
    }

    private void release() {
        for (ServerPlayerEntity player : watchers.keySet()) {
            WATCHING.remove(player.getUuid(), this);
            player.fallDistance = 0;
            if (!player.isRemoved() && ServerPlayNetworking.canSend(player, ModNetworking.NecromancerTransformPayload.ID))
                ServerPlayNetworking.send(player, payload(false));
        }
        watchers.clear();
    }

    /** Watchers still held by this scene, for tests and status. */
    int watcherCount() { return watchers.size(); }
    int soulCount() { return souls.size(); }

    private BlockState floor(Vec3d at) {
        BlockState state = world.getBlockState(BlockPos.ofFloored(at.x, centre.y - .5, at.z));
        return state.isAir() ? Blocks.SOUL_SOIL.getDefaultState() : state;
    }

    private void particles(ParticleEffect type, Vec3d at, int count, double spread, double rise, double speed) {
        world.spawnParticles(type, true, false, at.x, at.y + rise * .5, at.z, count, spread, Math.max(.05, rise * .5), spread, speed);
    }

    private void sound(Vec3d at, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.HOSTILE, volume, pitch);
    }

    private void sound(Vec3d at, RegistryEntry<SoundEvent> sound, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.HOSTILE, volume, pitch, world.getRandom().nextLong());
    }
}
