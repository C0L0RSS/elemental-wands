package com.anton.elementalwands.entity.undead;

import static com.anton.elementalwands.entity.undead.HollowUndeadClips.*;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Hooded bowman: keeps its distance, draws for more than two seconds and fires a real arrow
 * from its bow hand on the clip's release frame. The model draws side-on, shooting to its left,
 * so the body turns its left shoulder to the target while drawing.
 */
public class HollowArcherEntity extends HollowUndeadEntity {
    static final double RANGE = 15, HOLD_RANGE = 12;
    /** Degrees clockwise of the target the body faces: the authored shot leaves 7° ahead of the left shoulder. */
    static final float DRAW_TURN = 83;
    /** Stays planted while lowering the bow after the shot. */
    private static final int BUSY = 72;

    public HollowArcherEntity(EntityType<? extends HollowArcherEntity> type, World world) {
        super(type, world, "archer");
        // Unseen (the model carries its own bow) but gives the arrow its enchantments and a vanilla-style bow drop.
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.MAX_HEALTH, 18)
                .add(EntityAttributes.MOVEMENT_SPEED, .21)
                .add(EntityAttributes.FOLLOW_RANGE, 32);
    }

    @Override protected int riseTicks() { return ARCHER_RISE; }
    @Override protected int deathTicks() { return ARCHER_DEATH; }
    @Override protected double stride() { return ARCHER_STRIDE; }
    @Override protected StrikeGoal strikeGoal() { return new StrikeGoal(1, ARCHER_HIT, BUSY, 20); }

    @Override
    protected boolean canBegin(LivingEntity target, boolean seen) {
        return seen && squaredDistanceTo(target) <= RANGE * RANGE;
    }

    @Override
    protected boolean wantsCloser(LivingEntity target, boolean seen) {
        return !seen || squaredDistanceTo(target) > HOLD_RANGE * HOLD_RANGE;
    }

    @Override
    protected void aim(LivingEntity target, int tick) {
        if (tick <= ARCHER_HIT) face(target, DRAW_TURN, 15);
    }

    @Override
    protected void onBegin(ServerWorld world, LivingEntity target) {
        playSound(SoundEvents.ITEM_CROSSBOW_LOADING_START.value(), .6f, .6f);
    }

    @Override
    protected void strike(ServerWorld world, LivingEntity target) {
        if (target == null) return;
        ItemStack bow = getMainHandStack().isOf(Items.BOW) ? getMainHandStack() : new ItemStack(Items.BOW);
        ItemStack ammo = getProjectileType(bow);
        PersistentProjectileEntity arrow = ProjectileUtil.createArrowProjectile(this, ammo, 1, bow);
        Vec3d from = socket(ARCHER_SOCKET);
        arrow.setPosition(from);
        double dx = target.getX() - from.x, dy = target.getBodyY(1 / 3d) - from.y, dz = target.getZ() - from.z;
        ProjectileEntity.spawnWithVelocity(arrow, world, ammo, dx, dy + Math.sqrt(dx * dx + dz * dz) * .2, dz,
                1.6f, 14 - world.getDifficulty().getId() * 4);
        playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1, 1 / (getRandom().nextFloat() * .4f + .8f));
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_SKELETON_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_SKELETON_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_SKELETON_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { playSound(SoundEvents.ENTITY_SKELETON_STEP, .15f, 1); }
}
