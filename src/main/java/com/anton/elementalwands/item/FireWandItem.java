package com.anton.elementalwands.item;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.anton.elementalwands.util.MeteorManager;

public class FireWandItem extends AbstractWandItem {

    private static final float FIREBOLT_DAMAGE = 6.0f;
    private static final int FIREBOLT_BURN_SECONDS = 3;
    private static final float FIREBOLT_EXPLOSION_POWER = 2.5f;

    private static final float FLAME_BURST_DAMAGE = 3.0f;
    private static final double FLAME_BURST_RANGE = 4.5;
    private static final double FLAME_BURST_CONE_DOT = 0.55;
    private static final int FLAME_BURST_BURN_SECONDS = 2;

    private static final int METEOR_SPAWN_HEIGHT = 35;
    private static final float METEOR_EXPLOSION_POWER = 6.0f;

    public FireWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, DEFAULT_PRIMARY_COOLDOWN_TICKS)) return;

        HitResult hit = raycast(world, caster, DEFAULT_RANGE);
        Vec3d start = caster.getEyePos();
        Vec3d end = hit.getPos();

        spawnParticleLine(world, start, end, ParticleTypes.FLAME);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.9f, 1.1f);

        if (hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            applyDamage(world, caster, entity, FIREBOLT_DAMAGE);
            entity.setOnFireFor(FIREBOLT_BURN_SECONDS);
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3d pos = hit.getPos();
            world.createExplosion(caster, pos.x, pos.y, pos.z, FIREBOLT_EXPLOSION_POWER, true, World.ExplosionSourceType.MOB);
        }
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, DEFAULT_SECONDARY_COOLDOWN_TICKS)) return;

        Vec3d look = caster.getRotationVec(1.0f).normalize();
        Box box = caster.getBoundingBox().expand(FLAME_BURST_RANGE, 2.0, FLAME_BURST_RANGE);

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.FLAME,
                caster.getX() + look.x * 1.3,
                caster.getBodyY(0.6),
                caster.getZ() + look.z * 1.3,
                60, 1.0, 0.5, 1.0, 0.04);

        for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && e != caster)) {
            Vec3d to = living.getEntityPos().subtract(caster.getEntityPos());
            double dist = to.length();
            if (dist > FLAME_BURST_RANGE || dist < 0.001) continue;

            Vec3d dir = to.normalize();
            if (look.dotProduct(dir) < FLAME_BURST_CONE_DOT) continue;

            applyDamage(world, caster, living, FLAME_BURST_DAMAGE);
            living.setOnFireFor(FLAME_BURST_BURN_SECONDS);

            living.takeKnockback(1.8, caster.getX() - living.getX(), caster.getZ() - living.getZ());
            living.addVelocity(0.0, 0.20, 0.0);
            living.velocityModified = true;
        }
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, DEFAULT_ULTIMATE_COOLDOWN_TICKS)) return;

        HitResult hit = raycast(world, caster, DEFAULT_RANGE);
        Vec3d target = hit.getPos();

        MeteorManager.spawnMeteor(world, caster, target, METEOR_SPAWN_HEIGHT, METEOR_EXPLOSION_POWER);
    }
}
