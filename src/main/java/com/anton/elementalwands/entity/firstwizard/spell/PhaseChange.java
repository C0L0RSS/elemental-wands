package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.FirstWizardChangeScene;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * The change to phase two at half health: the fight's one set piece, played every time the fight reaches it. It is not
 * a move the chooser picks; the fight starts it when the spell in hand has finished, clears everything phase one left
 * running, and the copy cannot be hurt while it plays. It opens with a cinematic (the developer's request, Nov 19) and
 * ends with the full sweep, which is gameplay. Every beat has room; the clocks are public, since clients draw the body
 * from them.
 *
 * <ol>
 *   <li>The cinematic, to {@link #CINEMATIC} ({@link FirstWizardChangeScene}): the fight stops for everyone in it, their
 *       cameras taken and their bodies held, unhurt. The copy is thrown back by the blow until {@link #STAGGER}; its
 *       left arm is wrenched back and out and twitches from {@link #WRENCH}, comes away from the shoulder on strands of
 *       ink from {@link #TEAR}, drops at {@link #SNAP}, tumbles down by her feet ({@link #LANDS}) and melts into the
 *       floor from {@link #MELT_FROM} to {@link #MELTED}. Her left flank swells in slow pulses from {@link #BULGE_FROM}
 *       while her head bends the other way, to her right, from {@link #BEND_FROM} to {@link #BENT}. The swelling bursts
 *       at {@link #BURST} ({@link #GROW_FROM}: the tentacles erupt out of it and have grown by {@link #GROW_TO}), and she
 *       lifts off the floor from {@link #LIFT_FROM} to {@link #LIFTED}; the weapon rises at {@link #RISE}. The camera
 *       heads back to the players at {@link #RETURN}. The camera and the body are baked on this clock by
 *       art/first_wizard/change/build_change.py ({@link com.anton.elementalwands.entity.firstwizard.FirstWizardChangeTrack}).</li>
 *   <li>{@link #TELL}, once control has returned: three tendrils wind into a low whip for the opening sweep.</li>
 *   <li>{@link #SWEEP_FROM}: it sweeps a full circle low across the floor round the copy in {@link #SWEEP} ticks: a
 *       medium hit to every body whose feet are below {@link Tendrils#CLEAR} as it passes (jump it), from under its
 *       arch out to {@link Tendrils#REACH}.</li>
 *   <li>At {@link Timing#free()} phase two begins.</li>
 * </ol>
 *
 * The wound is always on its left ({@link SweepSpell#woundSide}), whichever hand the skin's owner uses. Once every
 * watcher has skipped the cinematic, the clock moves on to {@link #CINEMATIC} and the tell follows. Cut short (the
 * fight stopped, the copy removed, everyone gone), the fight puts it in phase two at once: never between.
 */
public final class PhaseChange implements Casting {
    public static final String RING_KIND = "phase_ring";
    /** The cinematic's beats (in step with build_change.py; the contract check holds the baked track to them). */
    public static final int STAGGER = 28, WRENCH = 18, TEAR = 92, SNAP = 108, LANDS = 120, MELT_FROM = 156, MELTED = 192;
    public static final int BULGE_FROM = 184, BEND_FROM = 194, BENT = 256, BURST = 266, LIFT_FROM = 284, LIFTED = 318;
    /** The camera starts back to the players at RETURN; control returns at CINEMATIC. */
    public static final int RETURN = 320, CINEMATIC = 340;
    /**
     * The tentacles: they burst out of the swelling at GROW_FROM (the burst) and have grown by GROW_TO (PLANT, kept for
     * the drawers, is a little before); the weapon rises at RISE.
     */
    public static final int GROW_FROM = BURST, GROW_TO = 306, PLANT = 300, RISE = 312;
    /** The tell, after control has returned. */
    public static final int TELL = 344;
    /** The tell's ripple runs from the shoulder to the tip between these ticks. */
    public static final int RIPPLE_FROM = 348, RIPPLE_TO = 360;
    public static final int SWEEP_FROM = 360, SWEEP = 30;
    public static final Timing TIMING = new Timing(0, SWEEP_FROM - 4, SWEEP_FROM, SWEEP_FROM + SWEEP + 10);
    /** Its left flank swells in a heartbeat: the first surge, and the beat between surges (the track's pulses). */
    public static final int HEARTBEAT_FROM = 198, HEARTBEAT = 15;

    private final Cast cast;
    private final double side = SweepSpell.woundSide();
    private final Set<UUID> struck = new HashSet<>();
    private double start = Double.NaN;
    private FirstWizardEffectEntity ring;
    private FirstWizardChangeScene scene;
    /** The scene's front: the way the copy faces through the cinematic. */
    private float frame;

    public PhaseChange(Cast cast) { this.cast = cast; }

    /** The full circle's angle on tick t, starting on the wound's side and turning toward the front. */
    public static double angleAt(double t, double start, double side) {
        double k = Math.clamp((t - SWEEP_FROM) / SWEEP, 0, 1);
        k = k * k * (3 - 2 * k) * .25 + k * .75;      // a little eased at either end
        return start + side * 2 * Math.PI * k;
    }

    @Override public void tick(int t) {
        if (t < CINEMATIC) {
            if (t == 0) begin();
            hold();
            if (scene != null) {
                scene.tick(t, cast.players());
                if (scene.everyoneSkipped()) { skip(t); return; }
            }
            beats(t);
            return;
        }
        if (t == CINEMATIC) end();
        Vec3d feet = cast.boss().getEntityPos();
        ServerPlayerEntity target = cast.target();
        if (t < TELL && target != null) cast.face(target.getEntityPos(), 4);
        if (t == TELL) {
            double facing = Tendrils.angle(cast.boss().getRotationVector().multiply(1, 0, 1).normalize());
            start = facing - side * Math.PI / 2;
            cast.reach(feet.add(Tendrils.dir(start).multiply(Tendrils.REACH)));
            ring = cast.effect(RING_KIND, feet, SWEEP_FROM - TELL, TIMING.free() - TELL + 6).size(Tendrils.REACH).dir(Tendrils.dir(start)).aux(new Vec3d(side, 0, 0));
            sound(feet, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 2f, .8f);
        }
        if (t == SWEEP_FROM && ring != null) {
            ring.stage(1);
            sound(feet, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 2.5f, .45f);
            sound(feet, SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, 2f, .6f);
        }
        if (t > SWEEP_FROM && t <= SWEEP_FROM + SWEEP && !Double.isNaN(start)) {
            double a0 = angleAt(t - 1, start, side), a1 = angleAt(t, start, side);
            for (ServerPlayerEntity player : cast.players()) {
                if (struck.contains(player.getUuid()) || !Tendrils.sweeps(player.getBoundingBox(), cast.arena().floor(), feet, a0, a1, Tendrils.REACH)) continue;
                struck.add(player.getUuid());
                cast.hit(player, FirstWizardRules.MEDIUM, Cast.Hit.SPELL, null);
            }
            cast.reach(feet.add(Tendrils.dir(a1).multiply(Tendrils.REACH)));
            if ((t - SWEEP_FROM) % 8 == 0) sound(feet.add(Tendrils.dir(a1).multiply(5)), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, .5f);
        }
        if (t == SWEEP_FROM + SWEEP + 4) cast.reach(null);
    }

    /** The cinematic starts: the copy turns to face the fight, and everyone in it watches. */
    private void begin() {
        FirstWizardEntity boss = cast.boss();
        Vec3d feet = boss.getEntityPos();
        List<ServerPlayerEntity> players = cast.players();
        Vec3d middle = Vec3d.ZERO;
        for (ServerPlayerEntity player : players) middle = middle.add(player.getEntityPos());
        Vec3d to = players.isEmpty() ? Vec3d.ZERO : middle.multiply(1.0 / players.size()).subtract(feet);
        frame = to.horizontalLengthSquared() < 1e-4 ? boss.getBodyYaw() : (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        hold();
        scene = new FirstWizardChangeScene(boss, cast.world(), cast.started(), frame, feet);
    }

    /** Through the cinematic it stands where it was, facing the fight, its head level (the track frames it so). */
    private void hold() {
        FirstWizardEntity boss = cast.boss();
        boss.setVelocity(0, Math.min(0, boss.getVelocity().y), 0);
        boss.setYaw(frame); boss.setBodyYaw(frame); boss.setHeadYaw(frame); boss.setPitch(0);
    }

    /** Control returns: everyone is let go. */
    private void end() { if (scene != null) scene.finish(); }

    /** Everyone skipped: the clock moves on to the end of the cinematic; the tell follows as if it had played out. */
    private void skip(int t) {
        cast.advance(CINEMATIC - t);
        end();
    }

    @Override public void cancel() { end(); }

    /** The cinematic's placeholder sounds and the burst's ink (sounds are parked for this boss). */
    private void beats(int t) {
        FirstWizardEntity boss = cast.boss();
        Vec3d feet = boss.getEntityPos(), shoulder = flank(1.38, .32), flank = flank(1.2, .3);
        if (t == 0) {
            sound(feet, SoundEvents.ENTITY_WARDEN_HURT, 1.8f, .7f);
            sound(feet, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.6f, .6f);
        }
        if (t == WRENCH + 13 || t == WRENCH + 32 || t == WRENCH + 47 || t == WRENCH + 62) {
            sound(shoulder, SoundEvents.BLOCK_BONE_BLOCK_BREAK, 1.4f, .55f + (t - WRENCH) / 300f);
            sound(shoulder, SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 1f, .6f);
        }
        if (t == TEAR) {
            sound(shoulder, SoundEvents.ENTITY_SLIME_SQUISH, 1.6f, .5f);
            sound(shoulder, SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, 1.6f, .55f);
        }
        if (t == SNAP) sound(shoulder, SoundEvents.BLOCK_SLIME_BLOCK_BREAK, 1.8f, .5f);
        if (t == LANDS + 4) {
            sound(feet, SoundEvents.BLOCK_MUD_BREAK, 1.6f, .6f);
            sound(feet, SoundEvents.ENTITY_SLIME_SQUISH_SMALL, 1.4f, .5f);
        }
        if (t == MELT_FROM) sound(feet, SoundEvents.BLOCK_SCULK_SPREAD, 1.6f, .5f);
        if (t == MELT_FROM + 18) sound(feet, SoundEvents.BLOCK_SCULK_SPREAD, 1.2f, .45f);
        if (t >= HEARTBEAT_FROM && t < BURST && (t - HEARTBEAT_FROM) % HEARTBEAT == 0)
            sound(flank, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 2f, .6f + (t - HEARTBEAT_FROM) / 400f);
        if (t == BEND_FROM + 42 || t == BEND_FROM + 55) sound(feet.add(0, 1.7, 0), SoundEvents.BLOCK_BONE_BLOCK_HIT, 1.6f, .45f);
        if (t == BURST) {
            // The swelling bursts: ink thrown from the flank, and the tentacles come out.
            cast.world().spawnParticles(ParticleTypes.SQUID_INK, true, false, flank.x, flank.y, flank.z, 30, .2, .25, .2, .06);
            sound(feet, SoundEvents.ENTITY_WARDEN_HURT, 2f, .6f);
            sound(feet, SoundEvents.ENTITY_SLIME_SQUISH, 2f, .5f);
            sound(feet, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), .8f, 1.4f);
            sound(feet, SoundEvents.ENTITY_WARDEN_EMERGE, 1.8f, .8f);
        }
        if (t == RISE) sound(feet, SoundEvents.ENTITY_WARDEN_ROAR, 1.6f, .9f);
    }

    /** A point on the wound's side of the copy: this high over its feet and this far out. */
    private Vec3d flank(double up, double out) {
        double yaw = Math.toRadians(cast.boss().getBodyYaw());
        Vec3d left = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        return cast.boss().getEntityPos().add(left.multiply(side * out)).add(0, up, 0);
    }

    private void sound(Vec3d at, SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
