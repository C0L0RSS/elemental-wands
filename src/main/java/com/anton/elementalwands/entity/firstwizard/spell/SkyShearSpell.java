package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.Arena;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Sky Shear: three pale crescents form stacked over the copy's head, then leave one at a time along the floor, each
 * about 14 blocks wide and low enough to jump. The answer is a jump, not a sidestep; the rhythm of three is the test.
 *
 * <ul>
 *   <li>Forms for 0.9 s: three crescents of ink over its head, taking Wind's colour as they grow.</li>
 *   <li>They leave {@link #GAP} ticks apart, each aimed at where its target stands as it leaves. A crescent drops to
 *       the floor in front of the copy and widens to {@link #HALF} each side over its first {@link #GROW} blocks.</li>
 *   <li>Each travels at 20 blocks a second for {@link #RANGE} blocks or to the floor's edge, then falls apart into ink.</li>
 *   <li>It strikes a body whose feet are lower than {@link #CLEAR} over the floor as its band passes: a medium hit,
 *       once per crescent per player. Nothing shelters anyone: every player is judged on their own.</li>
 * </ul>
 *
 * <p>The gap is set by vanilla's jump, not by taste: a jump started 1 to 6 ticks before a crescent arrives clears it,
 * and a jump lasts 12 ticks. Crescents 14 ticks apart can each be cleared by its own jump at the same timing, a player
 * who jumped one too late lands in time to clear the next, and the 10-tick hurt cooldown swallows none of them. Under
 * half a second apart they could not each be jumped. The contract check holds this.</p>
 */
public final class SkyShearSpell implements BossSpell {
    public static final String FORM_KIND = "sky_shear_form", KIND = "sky_shear";
    /** Crescents per cast, and ticks between them leaving. */
    public static final int COUNT = 3, GAP = 14;
    /** Forms from tick 0; the first leaves (aimed then) at 0.9 s, the others 0.7 s apart; free 0.2 s after the last. */
    public static final Timing TIMING = new Timing(0, 18, 18, 18 + GAP * (COUNT - 1) + 4);
    /** Blocks per tick: 20 a second. */
    public static final double SPEED = 1;
    /** How far a crescent travels at most, from where it leaves. */
    public static final double RANGE = 40;
    /** Half its width once grown (14 blocks across), and the blocks of travel over which it widens to that. */
    public static final double HALF = 7, GROW = 5;
    /** How far behind its middle its tips trail, and the depth of the band that strikes. */
    public static final double SWEEP = 2.4, THICK = .9;
    /** Feet at least this far over the floor are clear of it (the Pyre King's low sweep uses the same). */
    public static final double CLEAR = .6;
    /** Its drawn height at the middle: low enough to read as a thing to jump. */
    public static final double HEIGHT = .85;
    /** It leaves this far ahead of the copy's feet. */
    public static final double START = 1.2;
    /** Over the head while forming: the lowest crescent's height over the copy's feet, and the step between them. */
    public static final double STACK = 2.35, STACK_STEP = .5;
    /** Ticks a crescent takes to fall apart into ink at the end of its run. */
    public static final int DISSOLVE = 10;

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.SKY_SHEAR; }
    @Override public Timing timing() { return TIMING; }

    /** The tick of the cast on which crescent i (0, 1, 2) leaves. */
    public static int leaves(int i) { return TIMING.release() + i * GAP; }

    /** Half its width after travelling d blocks: narrow as it leaves the copy, full from GROW on. */
    public static double halfWidth(double d) { return HALF * Math.clamp(.3 + .7 * d / GROW, .3, 1); }

    /** How far behind its middle the crescent's front is at this sideways offset (its tips trail). */
    public static double lag(double sideways, double half) {
        double k = Math.clamp(Math.abs(sideways) / half, 0, 1);
        return SWEEP * k * k;
    }

    /** How far a crescent leaving here along dir may travel: RANGE, or to the floor's edge. */
    public static double reach(Arena arena, Vec3d origin, Vec3d dir) {
        return RANGE * arena.exit(origin, origin.add(dir.multiply(RANGE)), .5);
    }

    /**
     * Whether a crescent sweeping from d0 to d1 blocks along dir from origin strikes a body: its feet low enough, its
     * footprint within the crescent's width, and the band (THICK deep behind the front) passing over it this tick.
     * Pure, and judged for each body alone, so a body in front shelters nobody behind it.
     */
    public static boolean strikes(Box box, double floor, Vec3d origin, Vec3d dir, double d0, double d1) {
        if (box.minY - floor >= CLEAR - 1e-6 || box.maxY < floor - .5) return false;
        Vec3d f = new Vec3d(dir.x, 0, dir.z).normalize(), side = new Vec3d(-f.z, 0, f.x);
        double cx = (box.minX + box.maxX) / 2 - origin.x, cz = (box.minZ + box.maxZ) / 2 - origin.z;
        double u = cx * f.x + cz * f.z, v = cx * side.x + cz * side.z;
        double r = Math.max(box.maxX - box.minX, box.maxZ - box.minZ) / 2;
        double half = halfWidth(d1), near = Math.max(0, Math.abs(v) - r);
        if (near > half) return false;
        double lag = lag(near, half);
        return u + r >= d0 - lag - THICK && u - r <= d1 - lag;
    }

    @Override public Casting begin(Cast cast) {
        if (cast.target() == null) return null;
        return new Shear(cast);
    }

    private static final class Shear implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity form;

        Shear(Cast cast) {
            this.cast = cast;
            Vec3d over = new Vec3d(0, STACK, 0);
            form = cast.effect(FORM_KIND, cast.boss().getEntityPos().add(over), TIMING.release(), leaves(COUNT - 1) + 2).anchor(over);
            cast.focus(cast.boss().getEntityPos().add(0, STACK + STACK_STEP, 0));
            sound(cast, cast.boss().getEntityPos(), SoundEvents.ENTITY_BREEZE_CHARGE, 1.4f, .6f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            Vec3d feet = cast.boss().getEntityPos();
            int left = Math.max(0, Math.min(COUNT, (t - TIMING.release()) / GAP + 1));
            if (t < TIMING.release()) left = 0;
            cast.focus(left < COUNT ? feet.add(0, STACK + STACK_STEP * Math.max(1, left), 0) : null);
            if (target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
            for (int i = 0; i < COUNT; i++) if (t == leaves(i)) release(i, target, feet);
        }

        /** Crescent i leaves now, aimed at where its target stands. */
        private void release(int i, ServerPlayerEntity target, Vec3d feet) {
            Vec3d aim = target != null ? target.getEntityPos() : feet.add(cast.boss().getRotationVector().multiply(12));
            Vec3d dir = new Vec3d(aim.x - feet.x, 0, aim.z - feet.z);
            if (dir.lengthSquared() < 1e-4) dir = cast.boss().getRotationVector().multiply(1, 0, 1);
            if (dir.lengthSquared() < 1e-4) dir = new Vec3d(0, 0, 1);
            dir = dir.normalize();
            cast.face(aim, 180);
            if (i == 0) cast.aim(cast.arena().onFloor(aim, FirstWizardRules.EDGE));
            Vec3d origin = cast.arena().onFloor(feet.add(dir.multiply(START)), FirstWizardRules.EDGE * .5);
            double length = reach(cast.arena(), origin, dir);
            int life = (int) Math.ceil(length / SPEED) + DISSOLVE + 2;
            var crescent = cast.effect(KIND, origin, 0, life).aux(origin).dir(dir.multiply(SPEED)).size(length);
            form.stage(i + 1);
            cast.linger(new Flight(cast, crescent, origin, dir, length));
            sound(cast, origin, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.8f, .55f);
            sound(cast, origin, SoundEvents.ENTITY_BREEZE_SHOOT, 1.6f, .5f + i * .08f);
        }
    }

    /** One crescent along the floor: it strikes as it passes, then falls apart into ink at the end of its run. */
    private static final class Flight implements Lingering {
        private final Cast cast;
        private final FirstWizardEffectEntity crescent;
        private final Vec3d origin, dir;
        private final double length;
        private final Set<UUID> struck = new HashSet<>();
        private int ticks;

        Flight(Cast cast, FirstWizardEffectEntity crescent, Vec3d origin, Vec3d dir, double length) {
            this.cast = cast; this.crescent = crescent; this.origin = origin; this.dir = dir; this.length = length;
        }

        @Override public boolean tick(ServerWorld world, long now) {
            double d0 = Math.min(length, ticks * SPEED);
            ticks++;
            double d1 = Math.min(length, ticks * SPEED);
            if (d0 >= length) {
                // Spent: it falls apart into ink on the client; nothing it leaves hurts.
                if (ticks * SPEED >= length + DISSOLVE * SPEED) { crescent.discard(); return false; }
                return true;
            }
            for (ServerPlayerEntity player : cast.players()) {
                if (struck.contains(player.getUuid()) || !strikes(player.getBoundingBox(), cast.arena().floor(), origin, dir, d0, d1)) continue;
                // Once per crescent, whether the blow lands or a shield takes it.
                struck.add(player.getUuid());
                cast.hit(player, FirstWizardRules.MEDIUM, Cast.Hit.SPELL, crescent);
            }
            crescent.setPosition(origin.add(dir.multiply(d1)));
            return true;
        }

        @Override public void cancel(ServerWorld world) { crescent.discard(); }
    }

    private static void sound(Cast cast, Vec3d at, SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
