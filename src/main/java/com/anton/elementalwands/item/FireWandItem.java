package com.anton.elementalwands.item;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.anton.elementalwands.entity.InfernoWaveEntity;
import com.anton.elementalwands.util.MeteorManager;
import com.anton.elementalwands.util.TemporaryBlockManager;

import java.util.HashSet;
import java.util.Set;

public class FireWandItem extends AbstractWandItem {

    // Primary: Inferno Wave
    private static final double INFERNO_WAVE_SPEED = 1.5;

    // Secondary: Magma Surf
    private static final int MAGMA_SURF_DURATION_TICKS = 60; // 3 seconds
    private static final int MAGMA_SURF_FIRE_TRAIL_DURATION_TICKS = 40; // 2 seconds

    // Ultimate: Maximum Meteor
    private static final int METEOR_SPAWN_HEIGHT = 35;
    private static final float METEOR_EXPLOSION_POWER = 15.0f; // Increased from 10.0

    public FireWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, world, entity, slot);
        if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
                && entity instanceof LivingEntity living) {
            // Permanent Fire Resistance while holding
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 2, 0, false, false, true));
        }

        // Handle Magma Surf fire trail
        if (entity instanceof ServerPlayerEntity player && player.hasStatusEffect(StatusEffects.SPEED)) {
            // Check if this is our Magma Surf buff (we set it with amplifier 1 = Speed II)
            StatusEffectInstance speedEffect = player.getStatusEffect(StatusEffects.SPEED);
            if (speedEffect != null && speedEffect.getAmplifier() == 1) {
                // Leave fire trail behind player
                BlockPos playerPos = player.getBlockPos();
                BlockPos groundPos = playerPos.down();

                if (world.getBlockState(playerPos).isAir() &&
                        world.getBlockState(groundPos).isSolidBlock(world, groundPos)) {

                    Set<BlockPos> positions = new HashSet<>();
                    positions.add(playerPos);

                    TemporaryBlockManager.placeTemporaryBlocks(
                            world,
                            positions,
                            Blocks.FIRE.getDefaultState(),
                            MAGMA_SURF_FIRE_TRAIL_DURATION_TICKS,
                            state -> state.isAir());
                }

                // Spawn magma/fire particles around player
                world.spawnParticles(
                        ParticleTypes.FLAME,
                        player.getX(), player.getY() + 0.1, player.getZ(),
                        8, 0.5, 0.1, 0.5, 0.02);
                world.spawnParticles(
                        ParticleTypes.LAVA,
                        player.getX(), player.getY() + 0.1, player.getZ(),
                        2, 0.3, 0.1, 0.3, 0.01);
            }
        }
    }

    @Override
    public void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.PRIMARY, getPrimaryCooldownTicks()))
            return;

        // Spawn Inferno Wave projectile
        InfernoWaveEntity infernoWave = new InfernoWaveEntity(world, caster);
        world.spawnEntity(infernoWave);

        // Sound effects
        world.playSound(null, caster.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 0.7f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.8f, 0.9f);
    }

    @Override
    public void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.SECONDARY, getSecondaryCooldownTicks()))
            return;

        // Magma Surf: Apply Speed II for 3 seconds (1.5x speed multiplier)
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, MAGMA_SURF_DURATION_TICKS, 1)); // Amplifier
                                                                                                             // 1 =
                                                                                                             // Speed II
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, MAGMA_SURF_DURATION_TICKS, 0));

        // Sound and particles for activation
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(
                ParticleTypes.FLAME,
                caster.getX(), caster.getY() + 0.5, caster.getZ(),
                30, 0.5, 0.5, 0.5, 0.1);
        world.spawnParticles(
                ParticleTypes.LAVA,
                caster.getX(), caster.getY() + 0.5, caster.getZ(),
                10, 0.3, 0.3, 0.3, 0.05);
    }

    @Override
    public void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!tryStartCooldown(world, caster, stack, Ability.ULTIMATE, getUltimateCooldownTicks()))
            return;

        HitResult hit = raycast(world, caster, DEFAULT_RANGE);
        Vec3d target = hit.getPos();

        MeteorManager.spawnMeteor(world, caster, target, METEOR_SPAWN_HEIGHT, METEOR_EXPLOSION_POWER);
    }
}
