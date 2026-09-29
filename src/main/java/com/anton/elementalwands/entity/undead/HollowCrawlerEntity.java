package com.anton.elementalwands.entity.undead;

import static com.anton.elementalwands.entity.undead.HollowUndeadClips.*;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Legless common fodder: drags itself along and rears up to rake with a claw. */
public class HollowCrawlerEntity extends HollowUndeadEntity {
    /** Gap to the target's body when the lunge starts, and the claw's reach on the hit frame. */
    static final double REACH = 1.1, CLAW_RADIUS = .9;
    /** The rake begins here; a small hop carries the claw into the target. */
    private static final int LUNGE_TICK = 9;

    public HollowCrawlerEntity(EntityType<? extends HollowCrawlerEntity> type, World world) {
        super(type, world, "crawler");
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 14)
                .add(EntityAttributes.ATTACK_DAMAGE, 3)
                .add(EntityAttributes.MOVEMENT_SPEED, .19)
                .add(EntityAttributes.FOLLOW_RANGE, 32);
    }

    @Override protected int riseTicks() { return CRAWLER_RISE; }
    @Override protected int deathTicks() { return CRAWLER_DEATH; }
    @Override protected double stride() { return CRAWLER_STRIDE; }
    @Override protected StrikeGoal strikeGoal() { return new StrikeGoal(1, CRAWLER_HIT, CRAWLER_ATTACK, 12); }

    @Override
    protected boolean canBegin(LivingEntity target, boolean seen) {
        return reachTo(target) <= REACH && Math.abs(target.getY() - getY()) < 1.5;
    }

    @Override
    protected void aim(LivingEntity target, int tick) {
        if (tick < CRAWLER_HIT) face(target, 0, 20);
        if (tick == LUNGE_TICK && isOnGround()) {
            Vec3d toward = new Vec3d(target.getX() - getX(), 0, target.getZ() - getZ());
            if (toward.lengthSquared() > 1e-4) addVelocity(toward.normalize().multiply(.3).add(0, .1, 0));
        }
    }

    @Override
    protected void onBegin(ServerWorld world, LivingEntity target) {
        playSound(SoundEvents.ENTITY_SKELETON_AMBIENT, 1, 1.3f);
    }

    @Override
    protected void strike(ServerWorld world, LivingEntity target) {
        if (target == null) return;
        Vec3d claw = socket(CRAWLER_SOCKET);
        if (distance(target.getBoundingBox(), claw) <= CLAW_RADIUS) tryAttack(world, target);
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_SKELETON_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_SKELETON_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_SKELETON_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.ENTITY_SKELETON_STEP, .08f, .7f); }
}
