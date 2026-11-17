package com.anton.elementalwands.item;

import com.anton.elementalwands.party.WandAllies;

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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import com.anton.elementalwands.entity.InfernoWaveEntity;
import com.anton.elementalwands.registry.ModSounds;
import com.anton.elementalwands.util.BlockFireEvents;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.MeteorManager;
import com.anton.elementalwands.util.PyreWaveRules;
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
    private static final int PYRE_GROUND_DURATION = 100; // 5 seconds
    private static final int SECONDARY_COOLDOWN_TICKS = 200; // 10 seconds
    private static final String NBT_LAST_PYRE_CAST = "LastPyreCast";

    // Ultimate: Maximum Meteor
    private static final int METEOR_SPAWN_HEIGHT = 35;
    private static final float METEOR_EXPLOSION_POWER = 5.0f; // Ten-block blast radius; damage is independently capped by MeteorManager.

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
                long sincePyre = world.getTime() - lastCast;
                if (sincePyre >= 0 && sincePyre <= PYRE_GROUND_DURATION) {
                    BlockPos groundPos = player.getBlockPos().down();
                    net.minecraft.block.BlockState groundState = world.getBlockState(groundPos);
                    if (groundState.isOf(ModSpellBlocks.INFERNO_FLAME)
                            || groundState.isOf(ModSpellBlocks.PYRE_COALS)
                            || groundState.isOf(ModSpellBlocks.PYRE_FLAME)
                            || world.getBlockState(player.getBlockPos()).isOf(ModSpellBlocks.INFERNO_FLAME)
                            || world.getBlockState(player.getBlockPos()).isOf(ModSpellBlocks.PYRE_FLAME)) {
                        com.anton.elementalwands.util.SpellBuffs.regeneration(player);
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

        // A compressed ignition just past the wand tip establishes the source of the fireball.
        Vec3d ignition = com.anton.elementalwands.util.SpellCastVisuals.burstOrigin(caster);
        BlockFireEvents.send(world, BlockFireEvents.INFERNO_CAST, ignition, caster.getRotationVec(1.0f).normalize());

        // Sound effects
        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_FIRE_INFERNO_WAVE_CAST, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack, AbstractWandItem.Ability.SECONDARY, getSecondaryCooldownTicks()))
            return;

        // Record cast time (used by self-buff in inventoryTick)
        NbtComponent nbtComponent = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound data = nbtComponent.copyNbt();
        data.putLong(NBT_LAST_PYRE_CAST, world.getTime());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));

        // The wall runs along the ground from the caster's feet in the direction they face, whatever their pitch;
        // the scheduler lights one slice a tick over 40 ticks. Clients trace the same path from these two vectors.
        Vec3d origin = caster.getEntityPos();
        Vec3d forward = PyreWaveRules.heading(caster.getYaw());

        PyreSchedulerEntity scheduler = new PyreSchedulerEntity(world, caster, origin, forward);
        world.spawnEntity(scheduler);
        BlockFireEvents.send(world, 128, BlockFireEvents.PYRE_CAST, caster.getId(), origin, forward, Vec3d.ZERO, 0);

        world.playSound(null, caster.getBlockPos(), ModSounds.SPELL_FIRE_DRAGONS_PYRE_CAST, SoundCategory.PLAYERS, 1.0f,
                1.0f);
    }

    public static void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack))
            return;

        HitResult hit = AbstractWandItem.raycast(world, caster, AbstractWandItem.DEFAULT_RANGE);
        Vec3d target = hit.getPos();

        MeteorManager.spawnMeteor(world, caster, target, METEOR_SPAWN_HEIGHT, METEOR_EXPLOSION_POWER);
    }

    /**
     * Scheduler for Dragon's Pyre: lights one slice of the wall's ground path a tick across 40 ticks. Each slice is
     * traced as the front reaches it ({@link PyreWaveRules}), the same way clients trace it to draw the wall.
     */
    public static class PyreSchedulerEntity extends net.minecraft.entity.decoration.ArmorStandEntity {
        private static final int WAVE_LENGTH = PyreWaveRules.LENGTH;
        private static final double WAVE_HALF_WIDTH = 2.5;
        private static final double WAVE_FRONT_DEPTH = 0.5;
        private static final int MAX_VISUAL_DELAY = 4;

        private final PlayerEntity caster;
        /** The caster's feet, and their facing with the pitch removed. */
        private final Vec3d origin;
        private final Vec3d forward;
        private final Vec3d right;
        private final PyreWaveRules.Tracer path;
        private final Map<Integer, Map<BlockPos, Integer>> pendingFlames = new HashMap<>();
        private int tickCounter = 0;
        private com.anton.elementalwands.entity.PyreFrontEntity visualFront;
        private final Set<UUID> hitTargets = new HashSet<>();

        public PyreSchedulerEntity(ServerWorld world, PlayerEntity caster, Vec3d origin, Vec3d forward) {
            super(net.minecraft.entity.EntityType.ARMOR_STAND, world);
            this.caster = caster;
            this.origin = origin;
            this.forward = forward;
            this.right = PyreWaveRules.right(forward);
            this.path = new PyreWaveRules.Tracer(PyreWaveRules.Ground.of(world), origin, forward);

            this.setPosition(caster.getX(), caster.getY(), caster.getZ());
            this.setInvisible(true);
            this.setNoGravity(true);
            this.setInvulnerable(true);
            visualFront = new com.anton.elementalwands.entity.PyreFrontEntity(com.anton.elementalwands.registry.ModEntities.PYRE_FRONT, world);
            visualFront.setPosition(caster.getEntityPos());
            visualFront.setYaw((float)Math.toDegrees(Math.atan2(-forward.x, forward.z)));
            visualFront.setInvisible(true);
            world.spawnEntity(visualFront);
        }

        /** Lives 44 ticks; must never be serialized or it would reload as a permanent invisible armor stand. */
        @Override
        public boolean shouldSave() {
            return false;
        }

        @Override
        public void tick() {
            super.tick();
            if (!(getEntityWorld() instanceof ServerWorld sw))
                return;
            if (tickCounter >= WAVE_LENGTH && visualFront != null) {
                visualFront.discard(); visualFront = null;
            }
            if (tickCounter >= WAVE_LENGTH + MAX_VISUAL_DELAY) {
                discard();
                return;
            }

            if (tickCounter < WAVE_LENGTH) {
                int currentDistance = tickCounter + 1;
                path.advance(currentDistance);

                Set<BlockPos> sliceBlocks = new HashSet<>();
                Map<BlockPos, Integer> flameDelayByPos = new HashMap<>();
                Vec3d centerSurface = null;
                for (int lane = 0; lane < PyreWaveRules.LANES; lane++) {
                    int y = path.ground(currentDistance, lane);
                    if (y == PyreWaveRules.NONE) continue;
                    BlockPos p = PyreWaveRules.column(origin, forward, currentDistance, lane).withY(y);
                    net.minecraft.block.BlockState groundState = sw.getBlockState(p);
                    // Coals only replace full blocks; a slab or path keeps its shape and just burns on top.
                    if (groundState.isSolidBlock(sw, p)) sliceBlocks.add(p);
                    if (!groundState.hasBlockEntity()
                            && !groundState.isOf(Blocks.OBSIDIAN)
                            && !groundState.isOf(Blocks.BEDROCK)) {
                        int delay = Math.min(MAX_VISUAL_DELAY,
                                (int) Math.round(Math.abs(PyreWaveRules.lateral(lane)) * 2.0));
                        flameDelayByPos.merge(p.up(), delay, Math::min);
                    }
                    if (lane == PyreWaveRules.LANES / 2) {
                        centerSurface = new Vec3d(p.getX() + 0.5, p.getY() + 1.22, p.getZ() + 0.5);
                    }
                }

                TemporaryBlockManager.placeTrackedTemporaryBlocks(sw, sliceBlocks,
                        ModSpellBlocks.PYRE_COALS.getDefaultState(),
                        PYRE_GROUND_DURATION,
                        state -> !state.hasBlockEntity() && !state.isOf(Blocks.OBSIDIAN)
                                && !state.isOf(Blocks.BEDROCK), caster.getUuid());

                for (Map.Entry<BlockPos, Integer> entry : flameDelayByPos.entrySet()) {
                    int delay = entry.getValue();
                    pendingFlames.computeIfAbsent(tickCounter + delay, _tick -> new HashMap<>())
                            .merge(entry.getKey(), PYRE_GROUND_DURATION - delay, Math::max);
                }

                if (visualFront != null) {
                    visualFront.setInvisible(centerSurface == null);
                    if (centerSurface != null) visualFront.setPosition(centerSurface.add(0, -.22, 0));
                }

                // Damage is authoritative at the full-width front; only the fire model fan-out is delayed.
                // It measures along the ground, at any height, and only where the fire is lit: a wall stops it.
                List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class,
                        new net.minecraft.util.math.Box(origin, origin).expand(WAVE_LENGTH),
                        e -> !WandAllies.protectedFrom(caster, e) && e.isAlive() && !e.isSpectator());
                for (LivingEntity target : targets) {
                    if (hitTargets.contains(target.getUuid()))
                        continue;

                    Vec3d toTarget = target.getEntityPos().subtract(origin);
                    double distForward = toTarget.dotProduct(forward);
                    double side = toTarget.dotProduct(right);

                    if (distForward >= currentDistance - WAVE_FRONT_DEPTH
                            && distForward <= currentDistance + WAVE_FRONT_DEPTH
                            && Math.abs(side) <= WAVE_HALF_WIDTH
                            && path.covers(currentDistance, target.getEntityPos(), side)) {
                        boolean damaged = com.anton.elementalwands.util.SpellCombat.damage(target, sw, sw.getDamageSources().playerAttack(caster), 6.0f, caster, com.anton.elementalwands.data.WizardAffinity.FIRE);
                        if (damaged) {
                            AbstractWandItem.onWandDamageDealt(caster, 6.0f, com.anton.elementalwands.data.WizardAffinity.FIRE);
                        }
                        com.anton.elementalwands.util.SpellCombat.ignite(target,caster,5);
                        hitTargets.add(target.getUuid());
                        BlockFireEvents.send(sw, BlockFireEvents.PYRE_HIT,
                                new Vec3d(target.getX(), target.getBodyY(0.55), target.getZ()), forward);
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
                TemporaryBlockManager.placeTemporaryBlocks(
                        world,
                        List.of(pos),
                        ModSpellBlocks.PYRE_FLAME.getDefaultState(),
                        entry.getValue(),
                        state -> state.isAir()
                                || (state.isReplaceable()
                                        && !state.isOf(ModSpellBlocks.INFERNO_FLAME)
                                        && !state.isOf(ModSpellBlocks.PYRE_FLAME)));
            }
        }
    }
}
