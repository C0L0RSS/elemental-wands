package com.anton.elementalwands.entity.undead;

import static com.anton.elementalwands.entity.undead.HollowUndeadClips.*;

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
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Rare hunched heavy: a long, readable windup with the spiked maul, then one crushing swing
 * that hits everything around the maul head on the impact frame.
 */
public class HollowBruteEntity extends HollowUndeadEntity {
    /** Gap to the target's body when the windup starts, and the maul head's reach on impact. */
    static final double REACH = 2, MAUL_RADIUS = 1.3;
    /** Tracks the target through the windup, then commits to the swing. */
    static final int COMMIT_TICK = 30;
    /** Stays planted through most of the recovery. */
    private static final int BUSY = 70;

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
        return reachTo(target) <= REACH && Math.abs(target.getY() - getY()) < 2;
    }

    @Override
    protected void aim(LivingEntity target, int tick) {
        if (tick < COMMIT_TICK) face(target, 0, 8);
    }

    @Override
    protected void onBegin(ServerWorld world, LivingEntity target) {
        playSound(SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, 1.2f, .55f);
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
        world.playSound(null, head.x, head.y, head.z, SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 1, .6f);
    }

    @Override public float getSoundPitch() { return super.getSoundPitch() * .8f; }
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_WITHER_SKELETON_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_WITHER_SKELETON_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.ENTITY_WITHER_SKELETON_STEP, .4f, .8f); }
}
