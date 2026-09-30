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
import net.minecraft.util.Arm;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The cinematic that opens a Guardian fight, "the effigy wakes". The camera finds the caller's
 * outstretched hand with the heart in it; the heart shakes itself loose and flies the length of the
 * nave into the chest of the Guardian kneeling on its seat. Lightning crawls over the stone as it
 * wakes; it lifts its head, rises and slams its fists together, and the fight begins. The server
 * runs the timeline and holds everyone still and unhurt; each watcher's client flies its camera
 * along the same clock, so a party sees it together. Everyone can skip; the fight starts early
 * only once all of them have.
 */
public final class GuardianIntro {
    /**
     * Timeline, in ticks from the first frame. The Guardian's intro clip shares this clock
     * (art/fractured_guardian/intro/build_intro.py authors it from the same beats).
     */
    public static final int LENGTH = 322, LIFT = 64, LAUNCH = 76, IMPACT = 108, WAKE = 124, HEAD = 140, RISE = 152,
            STAND = 186, WIND = 196, CLAP = 216, RELEASE = 236, TITLE = 222, TITLE_END = 298, RETURN = 298;
    /** A unanimous skip ends the scene no sooner than this, so a key still held from before can't. */
    public static final int SKIP_AFTER = 20;
    /**
     * Points on the Guardian measured from the intro clip (intro-points.json), in blocks in its own
     * frame: +Z straight ahead of it, +Y up from its feet.
     */
    public static final Vec3d CORE = new Vec3d(0, 2.8532, .7196), FISTS = new Vec3d(0, 2.958, 2.1311),
            HEAD_KNEELING = new Vec3d(0, 3.172, 1.273), HEAD_STANDING = new Vec3d(0, 4.4682, .5289);
    /**
     * Where the heart rests on the caller's outstretched arm, from their feet in their own frame:
     * +X toward their off hand, +Z ahead. The main arm is held out a little below level, as the
     * player model draws it ({@code ARM_PITCH}).
     */
    public static final Vec3d HAND = new Vec3d(-.375, 1.38, .55);
    /** The caller's arm pitch while holding the heart out, in the player model's radians. */
    public static final float ARM_PITCH = -1.28f;
    /** The heart floats this far above the hand before it flies. */
    static final double HOVER = .4;
    /** How far the fist slam's ring rolls out across the floor; past the players at the arrival line. */
    static final double RING_REACH = 34;

    private static final Map<UUID, GuardianIntro> WATCHING = new HashMap<>();

    private final FracturedGuardianEntity guardian;
    private final ServerWorld world;
    private final Vec3d centre, core;
    private final float yaw;
    private final Map<ServerPlayerEntity, Vec3d> watchers = new LinkedHashMap<>();
    private final Set<UUID> skipped = new HashSet<>();
    private final ServerPlayerEntity caller;
    private final Vec3d hand;
    private IntroHeartEntity heart;
    private long start;
    private int clock = -1;

    GuardianIntro(FracturedGuardianEntity guardian, ServerWorld world, List<ServerPlayerEntity> players, ServerPlayerEntity preferred) {
        this.guardian = guardian;
        this.world = world;
        centre = guardian.getEntityPos();
        yaw = guardian.getYaw();
        core = place(centre, yaw, CORE);
        ServerPlayerEntity chosen = null;
        for (ServerPlayerEntity player : players) {
            GuardianIntro previous = WATCHING.put(player.getUuid(), this);
            if (previous != null && previous != this) previous.watchers.remove(player);
            // The ritual's blindness would black out the whole scene; the client fades in from black instead.
            player.removeStatusEffect(StatusEffects.BLINDNESS);
            Vec3d at = player.getEntityPos();
            // Everyone faces the Guardian, so the caller holds the heart out toward it and the
            // camera's return lands on a view of it.
            player.teleport(world, at.x, at.y, at.z, Set.of(), facing(at, centre), 0, false);
            player.setVelocity(Vec3d.ZERO);
            player.fallDistance = 0;
            watchers.put(player, at);
            if (chosen == null) chosen = player;
        }
        caller = preferred != null && watchers.containsKey(preferred) ? preferred : chosen;
        hand = caller == null ? place(centre, yaw, new Vec3d(0, HAND.y, 26)) : handOf(caller.getEntityPos(), facing(caller.getEntityPos(), centre), caller.getMainArm());
        hold(guardian);
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

    // ------------------------------------------------------------------ the shared script

    /** A point in a frame at {@code origin} turned to {@code yaw}: +Z ahead, +X to the left. */
    public static Vec3d place(Vec3d origin, float yaw, Vec3d local) {
        double r = Math.toRadians(yaw), c = Math.cos(r), s = Math.sin(r);
        return origin.add(local.x * c - local.z * s, local.y, local.x * s + local.z * c);
    }

    /** The yaw that looks from {@code from} toward {@code to}. */
    public static float facing(Vec3d from, Vec3d to) { return (float)Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z)); }

    /** Where the heart rests on a player standing at {@code feet} facing {@code yaw}. */
    public static Vec3d handOf(Vec3d feet, float yaw, Arm arm) {
        return place(feet, yaw, arm == Arm.RIGHT ? HAND : new Vec3d(-HAND.x, HAND.y, HAND.z));
    }

    public static double smooth(double s) {
        s = MathHelper.clamp(s, 0, 1);
        return s * s * (3 - 2 * s);
    }

    /** 0 while the heart lies still in the hand, rising to 1 as it shakes itself loose. */
    public static double vibration(double t) { return smooth((t - 16) / (LIFT - 16)); }

    /**
     * The heart {@code t} ticks into the scene: it lies in the hand and trembles harder and harder,
     * floats up off the palm, then flies faster and faster in a shallow arc into the core.
     */
    public static Vec3d heartAt(Vec3d hand, Vec3d core, double t) {
        Vec3d hover = hand.add(0, HOVER, 0);
        if (t < LIFT) return hand.add(jitter(t, vibration(t)));
        if (t < LAUNCH) return hand.lerp(hover, smooth((t - LIFT) / (LAUNCH - LIFT - 3))).add(jitter(t, 1.4));
        double flown = Math.pow(MathHelper.clamp((t - LAUNCH) / (IMPACT - LAUNCH), 0, 1), 1.7);
        return hover.lerp(core, flown).add(0, 1.3 * 4 * flown * (1 - flown), 0);
    }

    private static Vec3d jitter(double t, double amount) {
        double a = .032 * amount;
        return new Vec3d(Math.sin(t * 2.9) * a, Math.sin(t * 3.7 + 1) * a * .7, Math.sin(t * 3.3 + 2) * a);
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
        for (ServerPlayerEntity player : watchers.keySet())
            if (ServerPlayNetworking.canSend(player, ModNetworking.GuardianIntroPayload.ID))
                ServerPlayNetworking.send(player, payload(true));
        IntroHeartEntity made = ModEntities.INTRO_HEART.create(world, SpawnReason.EVENT);
        if (made == null) return;
        made.script(hand, core, start);
        world.spawnEntity(made);
        heart = made;
    }

    private ModNetworking.GuardianIntroPayload payload(boolean active) {
        return new ModNetworking.GuardianIntroPayload(guardian.getId(), caller == null ? -1 : caller.getId(), start, yaw, centre, hand,
                caller != null && caller.getMainArm() == Arm.LEFT, active);
    }

    /** The statue keeps its place and facing, whatever pushes on it. */
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
                if (release && !player.isRemoved() && ServerPlayNetworking.canSend(player, ModNetworking.GuardianIntroPayload.ID))
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

    private Vec3d local(double x, double y, double z) { return place(centre, yaw, new Vec3d(x, y, z)); }

    private void stage(ServerWorld world, int t) {
        Vec3d held = heartAt(hand, core, t);
        if (t == 0) sound(world, hand, SoundEvents.BLOCK_CONDUIT_AMBIENT, 1.2f, .8f);
        // The heart's beat quickens in the hand.
        for (int beat : new int[]{8, 26, 40, 50, 57, 62, 66, 70, 73})
            if (t == beat) sound(world, hand, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1f + t / 70f, .9f + t / 180f);
        if (t >= 30 && t < LAUNCH && t % 3 == 0)
            particles(world, ParticleTypes.ELECTRIC_SPARK, held, 1 + (int)(3 * vibration(t)), .12, .01);
        if (t == LIFT) sound(world, hand, SoundEvents.BLOCK_CONDUIT_ACTIVATE, 1.6f, 1f);
        if (t == LAUNCH) {
            sound(world, held, SoundEvents.ENTITY_BREEZE_WIND_BURST, 1.6f, .7f);
            sound(world, held, SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 1.4f, .8f);
            particles(world, ParticleTypes.END_ROD, held, 16, .15, .06);
        }
        if (t == LAUNCH + 12) sound(world, held, SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 1.6f, 1.3f);
        if (t == IMPACT) impact(world);
        if (t == IMPACT + 6) sound(world, core, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 2.5f, .7f);
        // Lightning crawls over the waking stone.
        for (int crack : new int[]{WAKE, WAKE + 7, WAKE + 15, WAKE + 26, WAKE + 34, RISE + 8, RISE + 21})
            if (t == crack) {
                sound(world, core, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 1.6f, .8f + world.getRandom().nextFloat() * .4f);
                sound(world, core, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, .7f, 1.5f + world.getRandom().nextFloat() * .3f);
            }
        if (t >= IMPACT && t < RISE + 24 && t % 2 == 0) {
            var random = world.getRandom();
            double height = t < RISE ? 2.2 : 3;
            for (int i = 0; i < 4; i++)
                particles(world, ParticleTypes.ELECTRIC_SPARK, local((random.nextDouble() - .5) * 3.6, .3 + random.nextDouble() * height * 1.4,
                        (random.nextDouble() - .5) * 2.6 + .4), 2, .1, .08);
        }
        if (t == HEAD) sound(world, local(0, 3, 1), SoundEvents.BLOCK_GRINDSTONE_USE, 1.6f, .45f);
        if (t == RISE) sound(world, centre, SoundEvents.ENTITY_WARDEN_EMERGE, 1.8f, 1.1f);
        if (t == RISE + 4) for (int side : new int[]{-1, 1}) dust(world, local(side * 1.9, 0, 1.5), 18);
        if (t == RISE + 16) { dust(world, local(.7, 0, -.6), 22); sound(world, centre, SoundEvents.BLOCK_DEEPSLATE_BREAK, 1.6f, .5f); }
        if (t == RISE + 26 || t == STAND) { sound(world, centre, SoundEvents.ENTITY_IRON_GOLEM_STEP, 2f, .5f); dust(world, centre, 12); }
        if (t == WIND) {
            sound(world, local(0, 3, 0), SoundEvents.BLOCK_BEACON_POWER_SELECT, 2f, .5f);
            sound(world, local(0, 3, 0), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, .5f);
        }
        if (t > WIND && t < CLAP && t % 2 == 0)
            for (int side : new int[]{-1, 1}) particles(world, ParticleTypes.ELECTRIC_SPARK, local(side * 3.1, 3.4, .2), 3, .25, .05);
        if (t == CLAP) clap(world);
        if (t > CLAP && t <= CLAP + 18) ring(world, t);
    }

    /** The heart strikes the core: a flash of light, a blast of sparks and the first heartbeat of stone. */
    private void impact(ServerWorld world) {
        if (heart != null) heart.discard();
        heart = null;
        sound(world, core, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 3f, .6f);
        sound(world, core, SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 2f, .6f);
        sound(world, core, SoundEvents.BLOCK_BEACON_ACTIVATE, 2.5f, .6f);
        sound(world, core, SoundEvents.ITEM_TRIDENT_THUNDER, 1.4f, 1.4f);
        particles(world, ParticleTypes.END_ROD, core, 40, .2, .18);
        particles(world, ParticleTypes.ELECTRIC_SPARK, core, 60, .5, .4);
        particles(world, ParticleTypes.GLOW, core, 24, .6, .1);
    }

    /** Both fists meet: a thunderclap and a ring that rolls out across the whole floor. */
    private void clap(ServerWorld world) {
        Vec3d fists = place(centre, yaw, FISTS);
        sound(world, fists, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 3f, .6f);
        sound(world, fists, SoundEvents.ENTITY_GENERIC_EXPLODE, 2.5f, .6f);
        sound(world, fists, SoundEvents.ITEM_TRIDENT_THUNDER, 2.5f, .9f);
        sound(world, fists, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 2f, .7f);
        particles(world, ParticleTypes.ELECTRIC_SPARK, fists, 90, .4, .6);
        particles(world, ParticleTypes.END_ROD, fists, 40, .3, .25);
        dust(world, centre, 40);
    }

    /** The slam's ring: sparks and dust racing out over the floor. */
    private void ring(ServerWorld world, int t) {
        double s = (t - CLAP) / 18.0, radius = 1.5 + (RING_REACH - 1.5) * (1 - (1 - s) * (1 - s));
        int points = Math.min(180, (int)(radius * 4));
        BlockState floor = floor(world, centre);
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points + t * .05;
            double x = centre.x + Math.cos(angle) * radius, z = centre.z + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, false, x, centre.y + .2, z, 1, 0, .08, 0, .02);
            if (i % 3 == 0) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floor), true, false, x, centre.y + .1, z, 1, .1, .05, .1, .05);
        }
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
        if (heart != null) heart.discard();
        heart = null;
    }

    /** Watchers still held by this scene, for tests and status. */
    int watcherCount() { return watchers.size(); }
    boolean heartFlying() { return heart != null && !heart.isRemoved(); }
    ServerPlayerEntity caller() { return caller; }

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
