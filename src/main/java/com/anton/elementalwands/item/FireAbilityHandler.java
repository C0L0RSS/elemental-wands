package com.anton.elementalwands.item;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import com.anton.elementalwands.entity.InfernoWaveEntity;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.MeteorManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FireAbilityHandler {

    // Secondary: Dragon's Pyre
    private static final int PYRE_CONE_LENGTH = 40;
    private static final int PYRE_GROUND_DURATION = 100; // 5 seconds
    private static final int SECONDARY_COOLDOWN_TICKS = 200; // 10 seconds
    private static final String NBT_LAST_PYRE_CAST = "LastPyreCast";

    // Ultimate: Maximum Meteor
    private static final int METEOR_SPAWN_HEIGHT = 35;
    private static final float METEOR_EXPLOSION_POWER = 15.0f; // Increased from 10.0

    private FireAbilityHandler() {}

    public static int getPrimaryCooldownTicks() {
        return AbstractWandItem.DEFAULT_PRIMARY_COOLDOWN_TICKS;
    }

    public static int getSecondaryCooldownTicks() {
        return SECONDARY_COOLDOWN_TICKS;
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
                    if (groundState.isOf(ModSpellBlocks.INFERNO_FLAME)
                            || groundState.isOf(ModSpellBlocks.PYRE_COALS)) {
                        player.addStatusEffect(
                                new StatusEffectInstance(StatusEffects.REGENERATION, 20, 0, false, false, true)); // Regen
                                                                                                                  // I
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

        // Record cast time (used by self-buff in inventoryTick)
        NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound data = nbtComponent.copyNbt();
        data.putLong(NBT_LAST_PYRE_CAST, world.getServer().getTicks());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));

        // Snapshot origin + facing at cast time and hand off to scheduler;
        // wave propagates 1 block/tick over 40 ticks instead of all-at-once.
        Vec3d origin = caster.getEyePos();
        Vec3d forward = caster.getRotationVec(1.0f).normalize();

        PyreSchedulerEntity scheduler = new PyreSchedulerEntity(world, caster, origin, forward);
        world.spawnEntity(scheduler);

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

    // Scheduler for Dragon's Pyre — wave propagates 1 block/tick across 40 ticks.
    // Modeled after StoneAbilityHandler.TectonicSchedulerEntity.
    public static class PyreSchedulerEntity extends net.minecraft.entity.decoration.ArmorStandEntity {
        private static final int WAVE_LENGTH = 40;
        private static final double WAVE_HALF_WIDTH = 2.5;
        private static final double WAVE_FRONT_DEPTH = 0.5;
        private static final int MAX_VISUAL_DELAY = 4;

        private final PlayerEntity caster;
        private final Vec3d origin;
        private final Vec3d forward;
        private final Vec3d right;
        private final Map<Integer, Map<BlockPos, Integer>> pendingFlames = new HashMap<>();
        private int tickCounter = 0;
        private final Set<UUID> hitTargets = new HashSet<>();

        public PyreSchedulerEntity(ServerWorld world, PlayerEntity caster, Vec3d origin, Vec3d forward) {
            super(net.minecraft.entity.EntityType.ARMOR_STAND, world);
            this.caster = caster;
            this.origin = origin;
            this.forward = forward;

            Vec3d up = new Vec3d(0, 1, 0);
            Vec3d r = forward.crossProduct(up).normalize();
            if (r.lengthSquared() < 0.001) {
                r = new Vec3d(1, 0, 0);
            }
            this.right = r;

            this.setPosition(caster.getX(), caster.getY(), caster.getZ());
            this.setInvisible(true);
            this.setNoGravity(true);
            this.setInvulnerable(true);
        }

        @Override
        public void tick() {
            super.tick();
            if (!(getEntityWorld() instanceof ServerWorld sw))
                return;
            if (tickCounter >= WAVE_LENGTH + MAX_VISUAL_DELAY) {
                discard();
                return;
            }

            if (tickCounter < WAVE_LENGTH) {
                int currentDistance = tickCounter + 1;
                Vec3d frontCenter = origin.add(forward.multiply(currentDistance));

                Set<BlockPos> sliceBlocks = new HashSet<>();
                Map<BlockPos, Integer> flameDelayByPos = new HashMap<>();
                for (double w = -2.0; w <= 2.0; w += 0.5) {
                    Vec3d target = frontCenter.add(right.multiply(w));
                    BlockPos targetPos = BlockPos.ofFloored(target);
                    for (int yOffset = 0; yOffset >= -3; yOffset--) {
                        BlockPos p = targetPos.add(0, yOffset, 0);
                        if (sw.getBlockState(p).isSolidBlock(sw, p)) {
                            sliceBlocks.add(p);
                            net.minecraft.block.BlockState groundState = sw.getBlockState(p);
                            if (!groundState.hasBlockEntity()
                                    && !groundState.isOf(Blocks.OBSIDIAN)
                                    && !groundState.isOf(Blocks.BEDROCK)) {
                                int delay = Math.min(MAX_VISUAL_DELAY,
                                        (int) Math.round(Math.abs(w) * 2.0));
                                flameDelayByPos.merge(p.up(), delay, Math::min);
                            }
                            break;
                        }
                    }
                }

                TemporaryBlockManager.placeTemporaryBlocks(sw, sliceBlocks,
                        ModSpellBlocks.PYRE_COALS.getDefaultState(),
                        PYRE_GROUND_DURATION,
                        state -> !state.hasBlockEntity() && !state.isOf(Blocks.OBSIDIAN)
                                && !state.isOf(Blocks.BEDROCK));

                for (Map.Entry<BlockPos, Integer> entry : flameDelayByPos.entrySet()) {
                    int delay = entry.getValue();
                    pendingFlames.computeIfAbsent(tickCounter + delay, _tick -> new HashMap<>())
                            .merge(entry.getKey(), PYRE_GROUND_DURATION - delay, Math::max);
                }

                // Damage remains authoritative at the original full-width front;
                // only the fire model fan-out is delayed.
                List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class,
                        caster.getBoundingBox().expand(WAVE_LENGTH),
                        e -> e != caster && e.isAlive());
                for (LivingEntity target : targets) {
                    if (hitTargets.contains(target.getUuid()))
                        continue;

                    Vec3d toTarget = target.getEntityPos().subtract(origin);
                    double distForward = toTarget.dotProduct(forward);
                    double distRight = Math.abs(toTarget.dotProduct(right));

                    if (distForward >= currentDistance - WAVE_FRONT_DEPTH
                            && distForward <= currentDistance + WAVE_FRONT_DEPTH
                            && distRight <= WAVE_HALF_WIDTH) {
                        boolean damaged = target.damage(sw, sw.getDamageSources().playerAttack(caster), 8.0f);
                        if (damaged) {
                            AbstractWandItem.onWandDamageDealt(caster, 8.0f);
                        }
                        target.setFireTicks(100);
                        hitTargets.add(target.getUuid());
                    }
                }
            }

            igniteScheduledFlames(sw);
            tickCounter++;
        }

        private void igniteScheduledFlames(ServerWorld world) {
            Map<BlockPos, Integer> due = pendingFlames.remove(tickCounter);
            if (due == null || due.isEmpty()) {
                return;
            }

            for (Map.Entry<BlockPos, Integer> entry : due.entrySet()) {
                BlockPos pos = entry.getKey();
                int placed = TemporaryBlockManager.placeTemporaryBlocks(
                        world,
                        List.of(pos),
                        ModSpellBlocks.PYRE_FLAME.getDefaultState(),
                        entry.getValue(),
                        state -> state.isAir()
                                || (state.isReplaceable()
                                        && !state.isOf(ModSpellBlocks.INFERNO_FLAME)
                                        && !state.isOf(ModSpellBlocks.PYRE_FLAME)));
                if (placed > 0) {
                    world.spawnParticles(ModParticles.FIRE_INFERNO_FLAME,
                            pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5,
                            1, 0.0, 0.05, 0.0, 0.01);
                }
            }
        }
    }
}
