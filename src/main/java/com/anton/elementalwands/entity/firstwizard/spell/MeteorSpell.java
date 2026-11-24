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
 * Meteor: the copy flings a knot above its target and a meteor twice the player's size forms there.
 * Cinders drift through the threatened air during the fall.
 *
 * <ul>
 *   <li>A knot of ink gathers over its raised wand for a second. At the aim lock the area ({@link #RADIUS}) is
 *       fixed where the target will be half a second on, and the ink is flung up to form the meteor over it.</li>
 *   <li>After a Gravity Well (the design's combination) the circle is centred on the well instead, the previous
 *       cast's aim, so the meteor falls on everyone the well is pulling in.</li>
 *   <li>The meteor drops at the release and falls for {@link #FALL} ticks; the copy is free soon after.</li>
 *   <li>Impact: a heavy hit within {@link #INNER} of the middle, a medium one out to the rim, then burning ground
 *       over the inner part (a {@link GroundZone}) for {@link #GROUND_LIFE} ticks.</li>
 * </ul>
 */
public final class MeteorSpell implements BossSpell {
    public static final String KIND = "meteor", MARK_KIND = "meteor_mark", GROUND_KIND = "meteor_ground";
    /** The wand is raised from tick 0; the mark is set at 1 s; the meteor drops at 1.5 s; free 0.3 s later. */
    public static final Timing TIMING = new Timing(0, 20, 30, 36);
    /** Ticks from the release to the impact: three seconds. */
    public static final int FALL = 60;
    /** The marked circle: a medium hit anywhere in it, a heavy one in its inner part. */
    public static final double RADIUS = 9, INNER = 4.5;
    /** The meteor's radius once formed: 16 blocks across, twice the player's. */
    public static final double BALL_RADIUS = 8;
    /** Its centre's height over the floor as it drops. */
    public static final double HEIGHT = 44;
    /** Where the knot of ink gathers over the copy's feet while its wand is raised. */
    public static final double KNOT = 3.4;
    /** The mark leads a moving target by this many seconds. */
    public static final double LEAD = .5;
    /** The middle of the mark stays this far inside the floor's edge. */
    public static final double MARGIN = 4;
    /** A body is struck only with its feet this close over the floor (a jump does not clear it). */
    public static final double REACH_UP = 4;
    /** The burning ground over the inner part, and how long it burns. */
    public static final double GROUND_RADIUS = INNER;
    public static final int GROUND_LIFE = 80;
    /** Ticks the impact is drawn after it lands. */
    public static final int IMPACT_LIFE = 36;

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.METEOR; }
    @Override public Timing timing() { return TIMING; }

    /**
     * The mark's middle: on a well this copy has open where the cast before aimed ({@code previousAim}), otherwise
     * where the target will be. Kept on the floor, inside its margin.
     */
    public static Vec3d centre(Arena arena, Vec3d led, Vec3d well) { return arena.onFloor(well != null ? well : led, MARGIN); }

    /** The raw damage a body takes at the impact: heavy inside, medium out to the rim, none beyond or high in the air. */
    public static float damage(Box body, Vec3d centre) {
        if (body.minY > centre.y + REACH_UP || body.maxY < centre.y - .5) return 0;
        double reach = GroundZone.reach(body, centre, centre);
        return reach <= INNER ? FirstWizardRules.HEAVY : reach <= RADIUS ? FirstWizardRules.MEDIUM : 0;
    }

    @Override public Casting begin(Cast cast) {
        if (cast.target() == null) return null;
        return new Call(cast);
    }

    private static final class Call implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity ball;
        private FirstWizardEffectEntity mark;
        private Vec3d centre;
        private boolean dropped;

        Call(Cast cast) {
            this.cast = cast;
            Vec3d over = new Vec3d(0, KNOT, 0);
            ball = cast.effect(KIND, cast.boss().getEntityPos().add(over), TIMING.aimLock(), TIMING.release() + FALL + IMPACT_LIFE)
                    .size(BALL_RADIUS).anchor(over);
            cast.focus(cast.boss().getEntityPos().add(over));
            sound(cast, cast.boss().getEntityPos(), SoundEvents.ITEM_FIRECHARGE_USE, 1.4f, .45f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            Vec3d knot = cast.boss().getEntityPos().add(0, KNOT, 0);
            if (t < TIMING.aimLock()) {
                cast.focus(knot);
                if (target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
            } else if (t == TIMING.aimLock()) {
                Vec3d previous = cast.previousAim();
                Vec3d well = GravityWellSpell.wellAt(cast.boss(), previous) ? previous : null;
                Vec3d led = target != null ? cast.lead(target, LEAD) : cast.boss().getEntityPos().add(cast.boss().getRotationVector().multiply(12));
                centre = centre(cast.arena(), led, well);
                cast.aim(centre);
                cast.face(centre, 180);
                // The knot is flung up; the meteor forms over the mark, drawn from the ball's clocks round aux.
                ball.unanchor().aux(centre).dir(knot).stage(1);
                ball.setPosition(centre);
                mark = cast.effect(MARK_KIND, centre, TIMING.release() - TIMING.aimLock() + FALL, TIMING.release() - TIMING.aimLock() + FALL + 2)
                        .size(RADIUS);
                cast.focus(centre.add(0, HEIGHT, 0));
                sound(cast, centre, SoundEvents.ENTITY_BLAZE_SHOOT, 2f, .4f);
            }
            if (t == TIMING.release()) {
                dropped = true;
                ball.stage(2);
                cast.focus(null);
                cast.linger(new Fall(cast, ball, mark, centre));
                sound(cast, centre.add(0, 12, 0), SoundEvents.ENTITY_GHAST_SHOOT, 3f, .4f);
            }
        }

        @Override public void cancel() {
            if (dropped) return;
            ball.discard();
            if (mark != null) mark.discard();
        }
    }

    /** The fall, then the impact and its burning ground. */
    private static final class Fall implements Lingering {
        private final Cast cast;
        private final FirstWizardEffectEntity ball, mark;
        private final Vec3d centre;
        private final long impactAt;

        Fall(Cast cast, FirstWizardEffectEntity ball, FirstWizardEffectEntity mark, Vec3d centre) {
            this.cast = cast; this.ball = ball; this.mark = mark; this.centre = centre;
            impactAt = cast.now() + FALL;
        }

        @Override public boolean tick(ServerWorld world, long now) {
            if (now == impactAt - 20) sound(cast, centre.add(0, 8, 0), SoundEvents.ENTITY_BLAZE_AMBIENT, 3f, .4f);
            if (now < impactAt) return true;
            Set<UUID> hurt = new HashSet<>();
            for (ServerPlayerEntity player : cast.players()) {
                float amount = damage(player.getBoundingBox(), centre);
                if (amount > 0 && hurt.add(player.getUuid())) cast.hit(player, amount, Cast.Hit.SPELL, ball);
            }
            ball.stage(3).life((int) (now - ball.start()) + IMPACT_LIFE);
            mark.discard();
            var ground = cast.effect(GROUND_KIND, centre, FirstWizardRules.GROUND_ARM, GROUND_LIFE).size(GROUND_RADIUS);
            cast.linger(GroundZone.disc(cast, centre, GROUND_RADIUS, GROUND_LIFE, FirstWizardRules.GROUND, ground));
            sound(cast, centre, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 4f, .5f);
            sound(cast, centre, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, 3f, .6f);
            return false;
        }

        @Override public void cancel(ServerWorld world) { ball.discard(); mark.discard(); }
    }

    private static void sound(Cast cast, Vec3d at, SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
