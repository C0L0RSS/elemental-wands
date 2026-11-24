package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.particle.DragonBreathParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Flame (phase two; the developer's combination: "a kind of leap to do heavy damage then a flame thrower ability"):
 * the copy bends its upper tendril into a nozzle and breathes a cone of its own fire,
 * turning slowly after its target. While it breathes it cannot move, so a player who dodged the landing and got
 * behind it has a clear opening.
 *
 * <ul>
 *   <li>Only at a target within {@link #START_REACH}: whoever is still close. Usually it follows a Leap.</li>
 *   <li>Fire gathers in the nozzle for half a second; it then breathes for {@link #BREATH} ticks. From the start of the
 *       cast to its end it turns at most {@link #TURN} degrees a tick, so a player behind it stays behind it and one
 *       who sprints round it outruns the cone.</li>
 *   <li>The cone reaches {@link #REACH} blocks and {@link #HALF_ANGLE} each side. Standing in it costs
 *       {@link FirstWizardRules#FLAME} every {@link FirstWizardRules#FLAME_EVERY} ticks (ignoring shields, like burning
 *       ground): clearly more than burning ground, and in diamond or netherite not a kill over the whole breath.</li>
 *   <li>It leaves a cloud of cooled breath where the cone was, {@link #CLOUD_LIFE} ticks: a vanilla area effect cloud
 *       owned by the copy, with no effect, which a glass bottle collects as dragon's breath (no dragon can be fought
 *       any more; see {@code mixin/InkBreathBottleMixin}).</li>
 * </ul>
 */
public final class FlameSpell implements BossSpell {
    public static final String KIND = "flame";
    /** Ticks it breathes. */
    public static final int BREATH = 50;
    /** Fire gathers from 0; its first heading is set at 0.5 s; it breathes from 0.6 s for 2.5 s; free 0.3 s after. */
    public static final Timing TIMING = new Timing(0, 10, 12, 12 + BREATH + 6);
    /** The cone's reach and half angle (degrees), and the farthest target it starts at. */
    public static final double REACH = 9, HALF_ANGLE = 24, START_REACH = 10;
    /** Degrees a tick it turns after its target, from the start of the cast to its end. */
    public static final float TURN = 2f;
    /** Feet this high over the floor are still burned. */
    public static final double REACH_UP = 2.5;
    /** The cloud it leaves: its radius, its life, and how far out along the cone it sits. */
    public static final float CLOUD_RADIUS = 3;
    public static final int CLOUD_LIFE = 300;
    public static final double CLOUD_OUT = 5;

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.FLAME; }
    @Override public Timing timing() { return TIMING; }

    /** The tick of the cast its breath ends on. */
    public static int ends() { return TIMING.release() + BREATH; }

    @Override public Casting begin(Cast cast) {
        ServerPlayerEntity target = cast.target();
        if (target == null || target.getEntityPos().subtract(cast.boss().getEntityPos()).horizontalLength() > START_REACH) return null;
        return new Breath(cast);
    }

    private static final class Breath implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity cone;
        private final Map<UUID, Long> next = new HashMap<>();

        Breath(Cast cast) {
            this.cast = cast;
            Vec3d feet = cast.boss().getEntityPos();
            cone = cast.effect(KIND, feet, TIMING.release(), ends() + 14).size(REACH).dir(cast.boss().getRotationVector().multiply(1, 0, 1).normalize());
            cast.focus(null);
            sound(cast, feet, SoundEvents.ENTITY_WARDEN_DIG, 1.4f, .8f);
            sound(cast, feet, SoundEvents.ITEM_FIRECHARGE_USE, 1.2f, .5f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            Vec3d feet = cast.boss().getEntityPos();
            if (t <= ends() && target != null) cast.face(target.getEntityPos(), TURN);
            Vec3d dir = cast.boss().getRotationVector().multiply(1, 0, 1);
            dir = dir.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : dir.normalize();
            cone.dir(dir);
            if (t == TIMING.aimLock()) cast.aim(feet.add(dir.multiply(REACH)));
            if (t == TIMING.release()) {
                cone.stage(1);
                sound(cast, feet, SoundEvents.ENTITY_BLAZE_SHOOT, 2f, .45f);
            }
            if (t >= TIMING.release() && t < ends()) {
                if ((t - TIMING.release()) % 12 == 0) sound(cast, feet.add(dir.multiply(3)), SoundEvents.ITEM_FIRECHARGE_USE, 1.5f, .6f);
                long now = cast.now();
                for (ServerPlayerEntity player : cast.players()) {
                    if (now < next.getOrDefault(player.getUuid(), Long.MIN_VALUE)
                            || !Tendrils.inCone(player.getBoundingBox(), cast.arena().floor(), feet, dir, REACH, Math.toRadians(HALF_ANGLE), REACH_UP)) continue;
                    boolean burned = cast.hit(player, FirstWizardRules.FLAME, Cast.Hit.GROUND, cone);
                    next.put(player.getUuid(), now + (burned ? FirstWizardRules.FLAME_EVERY : 3));
                }
            }
            if (t == ends()) {
                cone.stage(2);
                leaveCloud(feet, dir);
                sound(cast, feet, SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.2f, .6f);
            }
        }

        /** The cooled breath, for a glass bottle: no effect, so it hurts nobody. */
        private void leaveCloud(Vec3d feet, Vec3d dir) {
            Vec3d at = cast.arena().onFloor(feet.add(dir.multiply(CLOUD_OUT)), FirstWizardRules.EDGE);
            AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(cast.world(), at.x, at.y, at.z);
            cloud.setOwner(cast.boss());
            cloud.setParticleType(DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1f));
            cloud.setRadius(CLOUD_RADIUS);
            cloud.setDuration(CLOUD_LIFE);
            cloud.setWaitTime(0);
            cloud.setRadiusGrowth(-CLOUD_RADIUS * .5f / CLOUD_LIFE);
            cast.adopt(cloud);
            cast.linger(new Cloud(cloud));
        }
    }

    /** The cloud, until it is bottled away or runs out; discarded with everything else if the fight ends first. */
    private record Cloud(AreaEffectCloudEntity cloud) implements Lingering {
        @Override public boolean tick(ServerWorld world, long now) { return !cloud.isRemoved(); }
        @Override public void cancel(ServerWorld world) { cloud.discard(); }
    }

    private static void sound(Cast cast, Vec3d at, SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
