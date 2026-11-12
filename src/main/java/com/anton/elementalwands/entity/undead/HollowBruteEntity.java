package com.anton.elementalwands.entity.undead;

import static com.anton.elementalwands.entity.undead.HollowUndeadClips.*;

import com.anton.elementalwands.registry.ModSounds;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Rare hunched heavy with a charging slam: it stomps in while heaving the spiked maul overhead and
 * turning to track its target, crouches and commits, then bounds forward and brings the maul down as
 * it lands, hitting everything around the maul head on the impact frame. The server replays the
 * clip's travel, stretching the airborne bound so the maul lands where the target stood at the commit.
 */
public class HollowBruteEntity extends HollowUndeadEntity {
    /** Gap to the target's body when the slam starts, and the maul head's reach on impact. */
    static final double REACH = 4.5, MAUL_RADIUS = 1.3;
    /** The bound covers at most this far, however far the target has got. */
    static final double BOUND_MAX = 3;
    /** The attack tick the bound lands on: the body stops there. */
    static final int LAND = BRUTE_CARRY.length - 1;
    /** Stays planted through most of the recovery. */
    private static final int BUSY = 55;
    /** A change this big (blocks/tick, squared) in its velocity between its own moves came from outside. */
    private static final double KNOCK = .01;

    /** Forward distance the strides may still cover before the maul head would pass the target. */
    private double room;
    /** Set at the commit: the locked direction and how much the clip's bound is stretched. */
    private Vec3d bound;
    private double boundScale;
    /** Velocity as its own physics left it last tick, and whether something knocked it out of the charge. */
    private Vec3d settled = Vec3d.ZERO;
    private boolean knocked;

    public HollowBruteEntity(EntityType<? extends HollowBruteEntity> type, World world) {
        super(type, world, "brute");
        experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 40)
                .add(EntityAttributes.ATTACK_DAMAGE, 10)
                .add(EntityAttributes.ATTACK_KNOCKBACK, 1.2)
                .add(EntityAttributes.ARMOR, 4)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, .6)
                .add(EntityAttributes.MOVEMENT_SPEED, .2)
                .add(EntityAttributes.FOLLOW_RANGE, 32);
    }

    @Override protected int riseTicks() { return BRUTE_RISE; }
    @Override protected int deathTicks() { return BRUTE_DEATH; }
    @Override protected double stride() { return BRUTE_STRIDE; }
    @Override protected StrikeGoal strikeGoal() { return new StrikeGoal(1, BRUTE_HIT, BUSY, 25); }

    @Override
    protected boolean canBegin(LivingEntity target, boolean seen) {
        return reachTo(target) <= REACH && Math.abs(target.getY() - getY()) < 2 && clearCharge(target);
    }

    /**
     * Floor under the body (at most a block's drop) and room for it (a normal step up) every half
     * block of the way the charge would carry it; otherwise it keeps walking instead of charging
     * off a ledge or into a wall.
     */
    private boolean clearCharge(LivingEntity target) {
        Vec3d to = new Vec3d(target.getX() - getX(), 0, target.getZ() - getZ());
        double travel = Math.min(to.length() - BRUTE_SOCKET[2], BRUTE_CARRY[BRUTE_COMMIT] + BOUND_MAX);
        if (travel <= 0) return true;
        Vec3d way = to.normalize();
        Box body = getBoundingBox();
        World world = getEntityWorld();
        for (double d = .5; d < travel + .5; d += .5) {
            Vec3d at = way.multiply(Math.min(d, travel));
            Box moved = body.offset(at.x, 0, at.z);
            if (!world.isBlockSpaceEmpty(this, new Box(moved.minX, moved.minY + getStepHeight(), moved.minZ, moved.maxX, moved.maxY, moved.maxZ))
                    || world.isBlockSpaceEmpty(this, new Box(moved.minX, moved.minY - 1.05, moved.minZ, moved.maxX, moved.minY, moved.maxZ)))
                return false;
        }
        return true;
    }

    @Override
    protected void aim(LivingEntity target, int tick) {
        if (tick < BRUTE_COMMIT) {
            // Stomping in, it turns fast enough to come round from facing away.
            face(target, 0, 18);
            room = ahead(target, facing()) - BRUTE_SOCKET[2];
            return;
        }
        if (bound != null || tick >= LAND) return;
        // Commits: the bound carries the maul head to where the target stands now, so a late
        // sidestep or a step back still beats it.
        bound = facing();
        double left = BRUTE_CARRY[LAND] - BRUTE_CARRY[tick];
        boundScale = left > 0 ? MathHelper.clamp(ahead(target, bound) - BRUTE_SOCKET[2], 0, BOUND_MAX) / left : 0;
    }

    @Override
    protected void drive(int clip) {
        if (clip > LAND || knocked) return;
        // A hit, a push or a spell knocks it out of the charge: physics takes over and the maul
        // comes down wherever it ends up.
        if (getVelocity().subtract(settled).lengthSquared() > KNOCK) { knocked = true; return; }
        // The right foot lands: the body stops where the bound put it instead of sliding on.
        if (clip == LAND) { setVelocity(0, getVelocity().y, 0); return; }
        double step = BRUTE_CARRY[clip + 1] - BRUTE_CARRY[clip];
        Vec3d way;
        if (clip < BRUTE_COMMIT) {
            // The strides follow the turning body but never carry the maul head past the target.
            way = facing();
            step = Math.min(step, Math.max(0, room));
            room -= step;
        } else {
            // With no target to commit to, the bound keeps its own length.
            if (bound == null) { bound = facing(); boundScale = 1; }
            way = bound;
            step *= boundScale;
        }
        setVelocity(way.x * step, getVelocity().y, way.z * step);
    }

    @Override
    public void travel(Vec3d input) {
        super.travel(input);
        settled = getVelocity();
    }

    private Vec3d facing() {
        double yaw = Math.toRadians(bodyYaw);
        return new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    private double ahead(LivingEntity target, Vec3d way) {
        return (target.getX() - getX()) * way.x + (target.getZ() - getZ()) * way.z;
    }

    @Override
    protected void onBegin(ServerWorld world, LivingEntity target) {
        room = Double.MAX_VALUE;
        bound = null;
        boundScale = 1;
        knocked = false;
        playSound(ModSounds.ENTITY_HOLLOW_BRUTE_SLAM_WINDUP, 1.2f, .55f);
    }

    @Override
    protected void strike(ServerWorld world, LivingEntity target) {
        Vec3d head = socket(BRUTE_SOCKET);
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, new Box(head, head).expand(MAUL_RADIUS + 1),
                e -> hittable(e) && distance(e.getBoundingBox(), head) <= MAUL_RADIUS))
            tryAttack(world, victim);
        BlockState ground = world.getBlockState(BlockPos.ofFloored(head.x, getY() - .5, head.z));
        if (!ground.isAir())
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), head.x, getY() + .1, head.z, 24, .6, .05, .6, .15);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, head.x, head.y, head.z, 1, 0, 0, 0, 0);
        world.playSound(null, head.x, head.y, head.z, ModSounds.ENTITY_HOLLOW_BRUTE_SLAM_IMPACT, SoundCategory.HOSTILE, 1, .6f);
    }

    @Override public float getSoundPitch() { return super.getSoundPitch() * .8f; }
    @Override protected SoundEvent getAmbientSound() { return ModSounds.ENTITY_HOLLOW_BRUTE_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.ENTITY_HOLLOW_BRUTE_HURT; }
    @Override protected SoundEvent getDeathSound() { return ModSounds.ENTITY_HOLLOW_BRUTE_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(ModSounds.ENTITY_HOLLOW_BRUTE_STEP, .4f, .8f); }
}
