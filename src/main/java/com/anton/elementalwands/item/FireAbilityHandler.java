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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import com.anton.elementalwands.entity.InfernoWaveEntity;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.MeteorManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;

import java.util.HashSet;
import java.util.Set;

public final class FireAbilityHandler {

    // Secondary: Dragon's Pyre
    private static final int PYRE_CONE_LENGTH = 40;
    private static final int PYRE_GROUND_DURATION = 100; // 5 seconds
    private static final String NBT_LAST_PYRE_CAST = "LastPyreCast";

    // Ultimate: Maximum Meteor
    private static final int METEOR_SPAWN_HEIGHT = 35;
    private static final float METEOR_EXPLOSION_POWER = 15.0f; // Increased from 10.0

    private FireAbilityHandler() {}

    public static int getPrimaryCooldownTicks() {
        return AbstractWandItem.DEFAULT_PRIMARY_COOLDOWN_TICKS;
    }

    public static int getSecondaryCooldownTicks() {
        return AbstractWandItem.DEFAULT_SECONDARY_COOLDOWN_TICKS;
    }

    public static void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
                && entity instanceof LivingEntity living) {
            // Permanent Fire Resistance while holding
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 2, 0, false, false, true));
        }

        if (!world.isClient() && entity instanceof PlayerEntity player
                && (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)) {
            NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
            NbtCompound data = nbtComponent.copyNbt();
            if (data.contains(NBT_LAST_PYRE_CAST)) {
                long lastCast = data.getLong(NBT_LAST_PYRE_CAST).orElse(0L);
                if (world.getServer().getTicks() - lastCast <= PYRE_GROUND_DURATION) {
                    BlockPos groundPos = player.getBlockPos().down();
                    net.minecraft.block.BlockState groundState = world.getBlockState(groundPos);
                    if (groundState.isOf(Blocks.FIRE) || groundState.isOf(Blocks.MAGMA_BLOCK)) {
                        player.addStatusEffect(
                                new StatusEffectInstance(StatusEffects.REGENERATION, 20, 1, false, false, true)); // Regen
                                                                                                                  // II
                        player.addStatusEffect(
                                new StatusEffectInstance(StatusEffects.SPEED, 20, 2, false, false, true)); // Speed III
                    }
                }
            }
        }
    }

    public static void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.PRIMARY, getPrimaryCooldownTicks()))
            return;

        // Spawn Inferno Wave projectile
        InfernoWaveEntity infernoWave = new InfernoWaveEntity(world, caster);
        world.spawnEntity(infernoWave);

        // Sound effects
        world.playSound(null, caster.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0f, 0.7f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.8f, 0.9f);
    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.SECONDARY, getSecondaryCooldownTicks()))
            return;

        // Record cast time
        NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound data = nbtComponent.copyNbt();
        data.putLong(NBT_LAST_PYRE_CAST, world.getServer().getTicks());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));

        // Compute 40-block runway
        Vec3d origin = caster.getEyePos();
        Vec3d forward = caster.getRotationVec(1.0f).normalize();

        // Define 'right' vector for width
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = forward.crossProduct(up).normalize();
        if (right.lengthSquared() < 0.001) { // Fallback if looking straight up/down
            right = new Vec3d(1, 0, 0);
        }

        Set<BlockPos> groundBlocks = new HashSet<>();

        // Map out runway points: 40 blocks long, from -2 to +2 wide
        for (double d = 1.0; d <= PYRE_CONE_LENGTH; d += 0.5) {
            Vec3d center = origin.add(forward.multiply(d));

            for (double w = -2.0; w <= 2.0; w += 0.5) {
                Vec3d target = center.add(right.multiply(w));

                world.spawnParticles(ParticleTypes.FLAME, target.x, target.y, target.z, 1, 0.1, 0.1, 0.1, 0.05);
                if (world.getRandom().nextFloat() < 0.1f) {
                    world.spawnParticles(ParticleTypes.LAVA, target.x, target.y, target.z, 1, 0.0, 0.0, 0.0, 0.0);
                }

                BlockPos targetPos = BlockPos.ofFloored(target);
                for (int yOffset = 0; yOffset >= -3; yOffset--) {
                    BlockPos p = targetPos.add(0, yOffset, 0);
                    if (world.getBlockState(p).isSolidBlock(world, p)) {
                        groundBlocks.add(p);
                        break;
                    }
                }
            }
        }

        // Deal damage
        java.util.List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class,
                caster.getBoundingBox().expand(PYRE_CONE_LENGTH), e -> e != caster && e.isAlive());

        // Calculate runway bounds for damage
        Vec3d rightVec = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        if (rightVec.lengthSquared() < 0.001)
            rightVec = new Vec3d(1, 0, 0);

        for (LivingEntity target : targets) {
            Vec3d toTarget = target.getEntityPos().subtract(caster.getEntityPos());
            double distForward = toTarget.dotProduct(forward);
            double distRight = Math.abs(toTarget.dotProduct(rightVec));

            if (distForward > 0 && distForward <= PYRE_CONE_LENGTH && distRight <= 2.5) {
                target.damage(world, world.getDamageSources().onFire(), 5.0f);
                target.setFireTicks(100);
            }
        }

        // Place magma/fire
        Set<BlockPos> validGround = new HashSet<>();
        Set<BlockPos> validAbove = new HashSet<>();

        for (BlockPos p : groundBlocks) {
            validGround.add(p);
            if (world.getBlockState(p.up()).isAir() || world.getBlockState(p.up()).isReplaceable()) {
                validAbove.add(p.up());
            }
        }

        TemporaryBlockManager.placeTemporaryBlocks(world, validGround, Blocks.MAGMA_BLOCK.getDefaultState(),
                PYRE_GROUND_DURATION,
                state -> !state.hasBlockEntity() && !state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK));
        TemporaryBlockManager.placeTemporaryBlocks(world, validAbove, Blocks.FIRE.getDefaultState(),
                PYRE_GROUND_DURATION, state -> state.isAir() || state.isReplaceable());

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_SHOOT, SoundCategory.PLAYERS, 1.0f,
                0.8f);
    }

    public static void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack))
            return;

        HitResult hit = AbstractWandItem.raycast(world, caster, AbstractWandItem.DEFAULT_RANGE);
        Vec3d target = hit.getPos();

        MeteorManager.spawnMeteor(world, caster, target, METEOR_SPAWN_HEIGHT, METEOR_EXPLOSION_POWER);
    }
}
