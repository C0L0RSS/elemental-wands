package com.anton.elementalwands.item;

import com.anton.elementalwands.util.CycloneManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class WindWandItem extends AbstractWandItem {

    private static final float WIND_SLASH_DAMAGE = 4.0f;
    private static final double DASH_SPEED = 1.7;

    public WindWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, DEFAULT_PRIMARY_COOLDOWN_TICKS)) return;

        HitResult hit = raycast(world, caster, DEFAULT_RANGE);
        Vec3d start = caster.getEyePos();
        Vec3d end = hit.getPos();

        spawnParticleLine(world, start, end, ParticleTypes.CLOUD);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.2f);

        if (hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            applyDamage(world, caster, entity, WIND_SLASH_DAMAGE);

            if (entity instanceof LivingEntity living) {
                living.takeKnockback(1.8, caster.getX() - living.getX(), caster.getZ() - living.getZ());
                living.velocityModified = true;
            }
        }
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, DEFAULT_SECONDARY_COOLDOWN_TICKS)) return;

        Vec3d look = caster.getRotationVec(1.0f).normalize();
        caster.addVelocity(look.x * DASH_SPEED, look.y * DASH_SPEED, look.z * DASH_SPEED);
        caster.setOnGround(false);
        caster.fallDistance = 0;

        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, false, false, true));

        if (caster instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
            serverPlayer.networkHandler
                    .sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(caster));
        }
        caster.velocityModified = true;

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS, 1.0f,
                1.1f);
        world.spawnParticles(ParticleTypes.CLOUD, caster.getX(), caster.getY(), caster.getZ(), 25, 0.4, 0.2, 0.4, 0.03);
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, DEFAULT_ULTIMATE_COOLDOWN_TICKS)) return;

        HitResult hit = raycast(world, caster, DEFAULT_RANGE);
        Vec3d center = hit.getType() == HitResult.Type.MISS ? caster.getEntityPos() : hit.getPos();

        CycloneManager.startCyclone(world, caster, center);
        world.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), SoundCategory.PLAYERS, 1.2f,
                0.8f);
    }
}
