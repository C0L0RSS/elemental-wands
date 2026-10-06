package com.anton.elementalwands.entity;

import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModEntities;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The cinematic that opens a Guardian fight. A zombie shuffles across the nave, stops, turns at a
 * rumble overhead and looks up; the Guardian arcs down out of the vaults and hammers it flat with
 * both fists. Then, slow and heavy, it hauls the zombie up by the head, looks at it, takes its feet,
 * rips it in two, flings the legs away and hurls the rest over the players' heads, then points at them.
 * <p>
 * The server runs the timeline, holds everyone still and unhurt, and plays the sounds and dust;
 * each watcher's client flies its camera along the same clock ({@link GuardianIntroTrack}), so a
 * party sees it together. Everyone can skip; the fight starts early only once all of them have.
 */
public final class GuardianIntro {
    /**
     * Timeline, in ticks from the first frame. Both actors' clips and the camera track share this
     * clock (art/fractured_guardian/intro/build_intro.py authors them from the same beats).
     */
    public static final int LENGTH = 476, STOP = 64, TURN = 70, LOOK = 88, FALL = 100, POV = 116, IMPACT = 156, RISE = 190,
            GRIP = 208, SHOW = 232, GRAB = 260, TEAR = 288, TOSS = 312, HURL = 344, PLAYERS = 348, POINT = 366,
            TITLE = 382, TITLE_END = 458, RETURN = 458;
    /** A unanimous skip ends the scene no sooner than this, so a key still held from before can't. */
    public static final int SKIP_AFTER = 20;
    /** How far the smash's ring rolls out across the floor; past the players at the arrival line. */
    static final double RING_REACH = 34;

    private static final Map<UUID, GuardianIntro> WATCHING = new HashMap<>();

    private final FracturedGuardianEntity guardian;
    private final ServerWorld world;
    private final Vec3d centre;
    private final float yaw;
    private final Map<ServerPlayerEntity, Vec3d> watchers = new LinkedHashMap<>();
    private final Set<UUID> skipped = new HashSet<>();
    private final GuardianIntroTrack track = GuardianIntroTrack.get();
    private GuardianIntroZombieEntity zombie;
    private long start;
    private int clock = -1;

    GuardianIntro(FracturedGuardianEntity guardian, ServerWorld world, List<ServerPlayerEntity> players, long start) {
        this.guardian = guardian;
        this.world = world;
        this.start = start;
        centre = guardian.getEntityPos();
        yaw = guardian.getYaw();
        for (ServerPlayerEntity player : players) {
            GuardianIntro previous = WATCHING.put(player.getUuid(), this);
            if (previous != null && previous != this) previous.watchers.remove(player);
            // The ritual's blindness would black out the whole scene; the client fades in from black instead.
            player.removeStatusEffect(StatusEffects.BLINDNESS);
            Vec3d at = player.getEntityPos();
            // Everyone faces the Guardian, so the camera's return lands on a view of it.
            player.teleport(world, at.x, at.y, at.z, Set.of(), facing(at, centre), 0, false);
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0;
            watchers.put(player, at);
        }
        hold(guardian);
        // The zombie arrives a tick before the scene, so every client has it when the clips start.
        GuardianIntroZombieEntity made = ModEntities.GUARDIAN_INTRO_ZOMBIE.create(world, SpawnReason.EVENT);
        if (made != null) {
            made.script(centre, yaw, start);
            world.spawnEntity(made);
            zombie = made;
        }
    }

    public static void register() {
        // Watchers can't be hurt while their camera is somewhere else; only /kill and the void still reach them.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof PlayerEntity
                && WATCHING.containsKey(entity.getUuid()) && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHING.clear());
        // A leaver is let go at once: an unloaded Guardian can no longer tick them out, and they must not rejoin still held.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GuardianIntro intro = WATCHING.remove(handler.getPlayer().getUuid());
            if (intro != null) intro.watchers.remove(handler.getPlayer());
        });
    }

    /** A player held in an intro: frozen, unhurt and unable to cast. */
    public static boolean watching(PlayerEntity player) { return WATCHING.containsKey(player.getUuid()); }

    /** A watcher asked to skip; the scene ends early only once every watcher has. */
    public static void skip(ServerPlayerEntity player) {
        GuardianIntro intro = WATCHING.get(player.getUuid());
        if (intro != null) intro.skipped.add(player.getUuid());
    }

    // ------------------------------------------------------------------ the shared frame

    /** A point in a frame at {@code origin} turned to {@code yaw}: +Z ahead, +X to the left. */
    public static Vec3d place(Vec3d origin, float yaw, Vec3d local) {
        double r = Math.toRadians(yaw), c = Math.cos(r), s = Math.sin(r);
        return origin.add(local.x * c - local.z * s, local.y, local.x * s + local.z * c);
    }

    /** The yaw that looks from {@code from} toward {@code to}. */
    public static float facing(Vec3d from, Vec3d to) { return (float)Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z)); }

    public static double smooth(double s) {
        s = MathHelper.clamp(s, 0, 1);
        return s * s * (3 - 2 * s);
    }

    // ------------------------------------------------------------------ running the scene

    /** Runs one tick; false once the scene has handed over to the fight. */
    boolean tick(ServerWorld world) {
        int t = ++clock;
        if (t == 0) begin(world);
        hold(guardian);
        holdWatchers(false);
        if (t >= LENGTH || t >= SKIP_AFTER && everyoneSkipped()) {
            finish(t < LENGTH);
            return false;
        }
        stage(world, t);
        return true;
    }

    private void begin(ServerWorld world) {
        start = world.getTime();
        guardian.syncIntroStart(start);
        // Both actors start drawing, and so start their clips, on this tick.
        if (zombie != null) zombie.syncStart(start);
        for (ServerPlayerEntity player : watchers.keySet())
            if (ServerPlayNetworking.canSend(player, ModNetworking.GuardianIntroPayload.ID))
                ServerPlayNetworking.send(player, payload(true));
    }

    private ModNetworking.GuardianIntroPayload payload(boolean active) {
        return new ModNetworking.GuardianIntroPayload(guardian.getId(), start, yaw, centre, active);
    }

    /** The Guardian keeps its place and facing, whatever pushes on it. */
    private void hold(FracturedGuardianEntity guardian) {
        guardian.setYaw(yaw); guardian.setBodyYaw(yaw); guardian.setHeadYaw(yaw);
        guardian.setVelocity(0, Math.min(0, guardian.getVelocity().y), 0);
    }

    /** Keeps every watcher where the scene found them, or with {@code release} lets them all go. Leavers stop counting toward the skip. */
    private void holdWatchers(boolean release) {
        for (var it = watchers.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            ServerPlayerEntity player = entry.getKey();
            if (release || player.isRemoved() || !player.isAlive() || player.getEntityWorld() != world) {
                WATCHING.remove(player.getUuid(), this);
                player.fallDistance = 0;
                // Released, killed, or gone to another dimension: its client must let go of the camera too.
                if (!player.isRemoved() && ServerPlayNetworking.canSend(player, ModNetworking.GuardianIntroPayload.ID))
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

    private boolean everyoneSkipped() {
        for (ServerPlayerEntity player : watchers.keySet()) if (!skipped.contains(player.getUuid())) return false;
        return true;
    }

    private Vec3d local(Vec3d frame) { return place(centre, yaw, frame); }
    private Vec3d local(double x, double y, double z) { return local(new Vec3d(x, y, z)); }
    private Vec3d upper(int t) { return local(track.upper(t)); }
    private Vec3d lower(int t) { return local(track.lower(t)); }
    private Vec3d fists(int t) { return local(track.fists(t)); }

    private void stage(ServerWorld world, int t) {
        // A zombie shuffling out of the dark, groaning.
        if (t < STOP - 4 && t % 12 == 4) sound(world, lower(t), SoundEvents.ENTITY_ZOMBIE_STEP, .7f, .9f);
        if (t == 14 || t == 50 || t == TURN + 4) sound(world, upper(t), SoundEvents.ENTITY_ZOMBIE_AMBIENT, 1.2f, .9f + t / 400f);
        // Something heavy moving far overhead: thunder in the vaults and grit sifting down on it.
        if (t == 44 || t == 76) sound(world, local(0, 40, -20), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, .9f, .45f);
        if (t >= 44 && t < IMPACT && t % 2 == 0) grit(world, t);
        if (t == LOOK + 8) sound(world, upper(t), SoundEvents.ENTITY_ZOMBIE_AMBIENT, 1.2f, .7f);
        // The drop: a roar out of the dark and the wind of its fall.
        if (t == FALL + 6) sound(world, fists(t), SoundEvents.ENTITY_RAVAGER_ROAR, 3f, .5f);
        for (int gust : new int[]{POV + 8, POV + 22, IMPACT - 8})
            if (t == gust) sound(world, fists(t), SoundEvents.ENTITY_BREEZE_WIND_BURST, 1.2f + (t - POV) / 30f, .6f);
        if (t == IMPACT) smash(world);
        if (t > IMPACT && t <= IMPACT + 18) ring(world, t);
        if (t == IMPACT + 40) guardian.clearWave(0);
        if (t == IMPACT + 16) sound(world, local(0, 4, 0), SoundEvents.ENTITY_RAVAGER_ROAR, 2.5f, .55f);
        // It takes the zombie by the head and hauls it up to look at it; the zombie claws and groans.
        if (t == GRIP) { sound(world, upper(t), SoundEvents.ENTITY_ZOMBIE_HURT, 1.2f, 1f); dust(world, upper(t).add(0, -.3, 0), 14); }
        if (t == GRIP + 4) sound(world, local(0, 3, 0), SoundEvents.BLOCK_GRINDSTONE_USE, 1.4f, .4f);
        if (t == SHOW + 4 || t == SHOW + 16) sound(world, upper(t), SoundEvents.ENTITY_ZOMBIE_AMBIENT, 1.3f, 1.05f);
        if (t == GRAB) sound(world, lower(t), SoundEvents.ENTITY_ZOMBIE_HURT, 1.2f, .9f);
        if (t == TEAR - 12) sound(world, local(0, 3.5, 0), SoundEvents.BLOCK_GRINDSTONE_USE, 1.5f, .35f);
        if (t == TEAR + 3) tear(world, t);
        // The legs are flung away, and the rest thrown hard over the players' heads.
        if (t == TOSS) sound(world, lower(t), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, .55f);
        if (t == HURL - 12) sound(world, local(0, 4, 0), SoundEvents.BLOCK_GRINDSTONE_USE, 1.4f, .45f);
        if (t == HURL) {
            sound(world, upper(t), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.8f, .5f);
            sound(world, upper(t), SoundEvents.ENTITY_BREEZE_WIND_BURST, 2f, .7f);
        }
        if (t == track.event("over_players") - 2) sound(world, upper(t), SoundEvents.ENTITY_BREEZE_WIND_BURST, 2f, 1.1f);
        if (t == POINT + 12) sound(world, local(0, 4, 0), SoundEvents.ENTITY_RAVAGER_AMBIENT, 1.8f, .5f);
    }

    /** Grit sifting down around the zombie as the Guardian comes: more and more of it. */
    private void grit(ServerWorld world, int t) {
        var random = world.getRandom();
        BlockState stone = floor(world, centre);
        int count = 1 + (int)(3 * smooth((t - 44.0) / (IMPACT - 44)));
        Vec3d at = upper(t);
        for (int i = 0; i < count; i++)
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, stone), true, false,
                    at.x + (random.nextDouble() - .5) * 7, at.y + 5 + random.nextDouble() * 5, at.z + (random.nextDouble() - .5) * 7, 1, 0, 0, 0, 0);
    }

    /** Both fists land: a thunderclap, a burst of stone and a ring that rolls out across the floor. */
    private void smash(ServerWorld world) {
        Vec3d fists = fists(IMPACT);
        sound(world, fists, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 3f, .55f);
        sound(world, fists, SoundEvents.ENTITY_GENERIC_EXPLODE, 2.5f, .6f);
        sound(world, fists, SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, 2f, .5f);
        sound(world, fists, SoundEvents.ENTITY_ZOMBIE_HURT, 1.6f, .8f);
        particles(world, ParticleTypes.CLOUD, fists.add(0, .6, 0), 18, 1.2, .06);
        dust(world, fists, 60);
        dust(world, centre, 30);
        // The fight's own shockwave, as a look only (the fight's waves do the damage, not this ridge).
        guardian.startWaveAt(world.getTime() - GuardianCombatRules.SLAM_IMPACT, new Vec3d(fists.x, centre.y, fists.z), 0);
    }

    /** The smash's ring: dust racing out over the floor. */
    private void ring(ServerWorld world, int t) {
        double s = (t - IMPACT) / 18.0, radius = 1.5 + (RING_REACH - 1.5) * (1 - (1 - s) * (1 - s));
        Vec3d from = fists(IMPACT);
        int points = Math.min(180, (int)(radius * 4));
        BlockState floor = floor(world, centre);
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points + t * .05;
            double x = from.x + Math.cos(angle) * radius, z = from.z + Math.sin(angle) * radius;
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floor), true, false, x, centre.y + .1, z, 1, .1, .05, .1, .05);
            if (i % 4 == 0) world.spawnParticles(ParticleTypes.POOF, true, false, x, centre.y + .2, z, 1, .05, .05, .05, .01);
        }
    }

    /** The zombie comes apart at the waist: a crack, a groan and a puff, nothing more. */
    private void tear(ServerWorld world, int t) {
        Vec3d waist = upper(t).lerp(lower(t), .5);
        sound(world, waist, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.6f, .8f);
        sound(world, waist, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, .6f);
        sound(world, waist, SoundEvents.ENTITY_ZOMBIE_DEATH, 1.4f, 1f);
        particles(world, ParticleTypes.POOF, waist, 14, .25, .05);
        particles(world, ParticleTypes.CRIT, waist, 12, .3, .3);
    }

    private void dust(ServerWorld world, Vec3d at, int count) {
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floor(world, at)), true, false, at.x, at.y + .15, at.z, count, .8, .1, .8, .12);
    }

    private void finish(boolean early) {
        clear();
        holdWatchers(true);
        guardian.endIntro(early);
    }

    /** The Guardian is gone or an operator took over mid-scene: tidy up and let everyone go without starting a fight. */
    void cancel() {
        clear();
        holdWatchers(true);
    }

    private void clear() {
        if (zombie != null) zombie.discard();
        zombie = null;
    }

    /** Watchers still held by this scene, for tests and status. */
    int watcherCount() { return watchers.size(); }
    boolean zombiePresent() { return zombie != null && !zombie.isRemoved(); }

    private static BlockState floor(ServerWorld world, Vec3d at) {
        BlockState state = world.getBlockState(BlockPos.ofFloored(at.x, at.y - .5, at.z));
        return state.isAir() ? Blocks.STONE.getDefaultState() : state;
    }

    private static void particles(ServerWorld world, ParticleEffect type, Vec3d at, int count, double spread, double speed) {
        world.spawnParticles(type, true, false, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    private static void sound(ServerWorld world, Vec3d at, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.HOSTILE, volume, pitch);
    }

    private static void sound(ServerWorld world, Vec3d at, RegistryEntry<SoundEvent> sound, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.HOSTILE, volume, pitch, world.getRandom().nextLong());
    }
}
