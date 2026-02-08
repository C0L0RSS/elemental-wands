package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.anton.elementalwands.registry.ModItems;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class TitanDomeManager {

    private static final class Aegis {
        private final UUID casterUuid;
        private final int expiryTick;

        private Aegis(UUID casterUuid, int expiryTick) {
            this.casterUuid = casterUuid;
            this.expiryTick = expiryTick;
        }
    }

    private static final class Dome {
        private final BlockPos center;
        private final int radius;
        private final UUID casterUuid;
        private final int expiryTick;
        private int nextRepairTick;
        private final Long2ObjectMap<BlockState> originalByPos;
        private final Set<UUID> confinedEntityUuids;
        private final NbtCompound originalArmorNbt;
        private final ItemStack originalMainHand;
        private int nextBuffRefreshTick;

        private Dome(BlockPos center, int radius, UUID casterUuid, int expiryTick, int nextRepairTick,
                Long2ObjectMap<BlockState> originalByPos, Set<UUID> confinedEntityUuids, NbtCompound originalArmorNbt,
                ItemStack originalMainHand, int nextBuffRefreshTick) {
            this.center = center;
            this.radius = radius;
            this.casterUuid = casterUuid;
            this.expiryTick = expiryTick;
            this.nextRepairTick = nextRepairTick;
            this.originalByPos = originalByPos;
            this.confinedEntityUuids = confinedEntityUuids;
            this.originalArmorNbt = originalArmorNbt;
            this.originalMainHand = originalMainHand;
            this.nextBuffRefreshTick = nextBuffRefreshTick;
        }
    }

    private static final Map<RegistryKey<World>, List<Dome>> DOMES = new HashMap<>();
    private static final Map<RegistryKey<World>, List<Aegis>> AEGISES = new HashMap<>();

    private static final int DURATION_TICKS = 240;
    private static final int RADIUS = 16;
    private static final int REPAIR_INTERVAL_TICKS = 10;
    private static final int BUFF_REFRESH_INTERVAL_TICKS = 20;
    private static final int RESISTANCE_REFRESH_DURATION_TICKS = 40;
    private static final int AEGIS_DURATION_TICKS = 80;
    private static final int AEGIS_DISTANCE_AHEAD = 4;
    private static final int AEGIS_HEIGHT = 3;
    private static final int AEGIS_HALF_WIDTH = 1;
    private static final int AEGIS_BLOCK_DURATION_TICKS = 1;
    private static final double DOMAIN_PULL_SPEED = 1.5;

    private static final BlockState DOME_STATE = Blocks.POLISHED_DEEPSLATE.getDefaultState();

    private static final String NBT_TITAN_GEAR = "ew_titan_gear";
    private static final String NBT_TITAN_ARMOR = "ew_titan_armor";
    private static final String NBT_TITAN_SWORD = "ew_titan_sword";
    private static final String NBT_ORIGINAL_ARMOR = "ew_original_armor";
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private static final Identifier EARTHEN_DOMAIN_KNOCKBACK_RESISTANCE_ID = Identifier.of(
            "elementalwands",
            "earthen_domain_knockback_resistance");
    private static final EntityAttributeModifier EARTHEN_DOMAIN_KNOCKBACK_RESISTANCE_MODIFIER = new EntityAttributeModifier(
            EARTHEN_DOMAIN_KNOCKBACK_RESISTANCE_ID,
            1.0,
            EntityAttributeModifier.Operation.ADD_VALUE);

    private TitanDomeManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(TitanDomeManager::tickWorld);
    }

    public static void startDome(ServerWorld world, PlayerEntity caster) {
        int now = world.getServer().getTicks();

        BlockPos center = caster.getBlockPos();
        NbtCompound originalArmorNbt = serializeArmor(caster);
        ItemStack originalMainHand = caster.getMainHandStack().copy();
        Long2ObjectOpenHashMap<BlockState> originalByPos = new Long2ObjectOpenHashMap<>();

        int r2 = RADIUS * RADIUS;
        int rInner2 = (RADIUS - 1) * (RADIUS - 1);

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r2 || d2 < rInner2) continue;

                    BlockPos pos = center.add(dx, dy, dz);

                    BlockState existing = world.getBlockState(pos);
                    if (!canReplace(existing)) continue;

                    originalByPos.put(pos.asLong(), existing);
                    world.setBlockState(pos, DOME_STATE, 3);
                }
            }
        }

        DOMES.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>())
                .add(new Dome(
                        center,
                        RADIUS,
                        caster.getUuid(),
                        now + DURATION_TICKS,
                        now + REPAIR_INTERVAL_TICKS,
                        originalByPos,
                        new HashSet<>(),
                        originalArmorNbt,
                        originalMainHand,
                        now + BUFF_REFRESH_INTERVAL_TICKS));

        applyCasterBuffs(caster);
        equipTitanJuggernaut(caster, originalArmorNbt);

        world.playSound(null, center, SoundEvents.BLOCK_DEEPSLATE_PLACE, SoundCategory.PLAYERS, 1.1f, 0.7f);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, center.getX() + 0.5, center.getY() + 1.0,
                center.getZ() + 0.5, 40, 2.0, 0.8, 2.0, 0.02);
    }

    public static void startAegis(ServerWorld world, PlayerEntity caster) {
        int now = world.getServer().getTicks();
        List<Aegis> aegises = AEGISES.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>());
        aegises.removeIf(aegis -> aegis.casterUuid.equals(caster.getUuid()));
        aegises.add(new Aegis(caster.getUuid(), now + AEGIS_DURATION_TICKS));
    }

    private static void tickWorld(ServerWorld world) {
        RegistryKey<World> key = world.getRegistryKey();
        List<Aegis> aegises = AEGISES.get(key);
        List<Dome> domes = DOMES.get(world.getRegistryKey());
        int now = world.getServer().getTicks();

        if (aegises != null && !aegises.isEmpty()) {
            tickAegises(world, aegises, now);
            if (aegises.isEmpty()) {
                AEGISES.remove(key);
            }
        }

        Set<UUID> activeCasters = new HashSet<>();

        if (domes != null && !domes.isEmpty()) {
            Iterator<Dome> it = domes.iterator();
            while (it.hasNext()) {
                Dome dome = it.next();
                activeCasters.add(dome.casterUuid);

                if (now >= dome.expiryTick) {
                    endDome(world, dome);
                    it.remove();
                    continue;
                }

                if (now >= dome.nextBuffRefreshTick) {
                    refreshCasterBuffs(world, dome);
                    dome.nextBuffRefreshTick = now + BUFF_REFRESH_INTERVAL_TICKS;
                }

                tickInescapableDomain(world, dome);

                if (now >= dome.nextRepairTick) {
                    repairDome(world, dome);
                    dome.nextRepairTick = now + REPAIR_INTERVAL_TICKS;
                }
            }
        }

        if (domes != null && domes.isEmpty()) {
            DOMES.remove(world.getRegistryKey());
        }

        if (now % 20 == 0) {
            cleanupKnockbackResistance(world, activeCasters);
            cleanupTitanGear(world, activeCasters);
        }
    }

    private static void tickAegises(ServerWorld world, List<Aegis> aegises, int now) {
        Iterator<Aegis> it = aegises.iterator();
        while (it.hasNext()) {
            Aegis aegis = it.next();
            if (now >= aegis.expiryTick) {
                it.remove();
                continue;
            }

            PlayerEntity caster = world.getPlayerByUuid(aegis.casterUuid);
            if (caster == null || !caster.isAlive() || caster.isSpectator()) {
                it.remove();
                continue;
            }

            Vec3d forward = horizontalForward(caster);
            List<BlockPos> positions = buildAegisWall(caster, forward);
            TemporaryBlockManager.placeTemporaryBlocks(
                    world,
                    positions,
                    Blocks.GLASS.getDefaultState(),
                    AEGIS_BLOCK_DURATION_TICKS,
                    state -> (state.isAir() || state.isReplaceable()) && state.getFluidState().isEmpty());
        }
    }

    private static List<BlockPos> buildAegisWall(PlayerEntity caster, Vec3d forward) {
        Vec3d left = new Vec3d(-forward.z, 0.0, forward.x);
        double centerX = caster.getX() + forward.x * AEGIS_DISTANCE_AHEAD;
        double centerZ = caster.getZ() + forward.z * AEGIS_DISTANCE_AHEAD;
        int baseY = caster.getBlockY();

        Set<BlockPos> dedupe = new LinkedHashSet<>();
        for (int lateral = -AEGIS_HALF_WIDTH; lateral <= AEGIS_HALF_WIDTH; lateral++) {
            double x = centerX + left.x * lateral;
            double z = centerZ + left.z * lateral;
            for (int y = 0; y < AEGIS_HEIGHT; y++) {
                dedupe.add(BlockPos.ofFloored(x, baseY + y, z));
            }
        }
        return new ArrayList<>(dedupe);
    }

    private static Vec3d horizontalForward(PlayerEntity caster) {
        Vec3d look = caster.getRotationVec(1.0f);
        Vec3d horizontal = new Vec3d(look.x, 0.0, look.z);
        if (horizontal.lengthSquared() > 0.0001) return horizontal.normalize();
        float yawRad = caster.getYaw() * (float) (Math.PI / 180.0);
        return new Vec3d(-MathHelper.sin(yawRad), 0.0, MathHelper.cos(yawRad)).normalize();
    }

    private static void repairDome(ServerWorld world, Dome dome) {
        for (Long2ObjectMap.Entry<BlockState> entry : dome.originalByPos.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            BlockState current = world.getBlockState(pos);

            if (current.isAir()) {
                world.setBlockState(pos, DOME_STATE, 3);
            }
        }
    }

    private static void refreshCasterBuffs(ServerWorld world, Dome dome) {
        PlayerEntity caster = world.getPlayerByUuid(dome.casterUuid);
        if (caster == null || !caster.isAlive() || caster.isSpectator()) return;
        applyCasterBuffs(caster);
    }

    private static void applyCasterBuffs(PlayerEntity caster) {
        caster.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE,
                RESISTANCE_REFRESH_DURATION_TICKS,
                1,
                false,
                true,
                true));

        EntityAttributeInstance knockbackResistance = caster.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE);
        if (knockbackResistance == null) return;
        if (!knockbackResistance.hasModifier(EARTHEN_DOMAIN_KNOCKBACK_RESISTANCE_ID)) {
            knockbackResistance.addTemporaryModifier(EARTHEN_DOMAIN_KNOCKBACK_RESISTANCE_MODIFIER);
        }
    }

    private static void tickInescapableDomain(ServerWorld world, Dome dome) {
        Vec3d center = dome.center.toCenterPos();
        double radiusSq = dome.radius * dome.radius;

        Box trackingBox = new Box(dome.center).expand(dome.radius + 1.0);
        List<LivingEntity> inside = world.getEntitiesByClass(LivingEntity.class, trackingBox,
                e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(dome.casterUuid));
        for (LivingEntity living : inside) {
            if (living.getEntityPos().squaredDistanceTo(center) <= radiusSq) {
                dome.confinedEntityUuids.add(living.getUuid());
            }
        }

        Iterator<UUID> trackedIt = dome.confinedEntityUuids.iterator();
        while (trackedIt.hasNext()) {
            UUID trackedUuid = trackedIt.next();
            Entity tracked = world.getEntity(trackedUuid);
            if (!(tracked instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()) {
                trackedIt.remove();
                continue;
            }

            Vec3d offsetFromCenter = living.getEntityPos().subtract(center);
            double distSq = offsetFromCenter.lengthSquared();
            if (distSq <= radiusSq || distSq < 0.0001) continue;

            Vec3d pullBack = offsetFromCenter.normalize().multiply(-DOMAIN_PULL_SPEED);
            living.setVelocity(pullBack);
            living.velocityModified = true;
            living.fallDistance = 0.0f;
        }
    }

    private static void endDome(ServerWorld world, Dome dome) {
        for (Long2ObjectMap.Entry<BlockState> entry : dome.originalByPos.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            BlockState current = world.getBlockState(pos);

            if (current.isOf(DOME_STATE.getBlock())) {
                world.setBlockState(pos, entry.getValue(), 3);
            }
        }

        PlayerEntity caster = world.getPlayerByUuid(dome.casterUuid);
        if (caster != null) {
            removeKnockbackResistance(caster);
            restoreJuggernautLoadout(world, caster, dome);
            removeMarkedTitanGear(caster);
        }

        world.playSound(null, dome.center, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.PLAYERS, 1.1f, 0.8f);
    }

    private static boolean canReplace(BlockState state) {
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) return false;
        return state.isReplaceable();
    }

    private static void cleanupKnockbackResistance(ServerWorld world, Set<UUID> activeCasters) {
        for (PlayerEntity player : world.getPlayers()) {
            if (activeCasters.contains(player.getUuid())) continue;
            removeKnockbackResistance(player);
        }
    }

    private static void cleanupTitanGear(ServerWorld world, Set<UUID> activeCasters) {
        for (PlayerEntity player : world.getPlayers()) {
            if (activeCasters.contains(player.getUuid())) continue;
            removeMarkedTitanGear(player);
        }
    }

    private static void removeKnockbackResistance(PlayerEntity player) {
        EntityAttributeInstance knockbackResistance = player.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE);
        if (knockbackResistance == null) return;
        knockbackResistance.removeModifier(EARTHEN_DOMAIN_KNOCKBACK_RESISTANCE_ID);
    }

    private static NbtCompound serializeArmor(PlayerEntity player) {
        NbtCompound armorNbt = new NbtCompound();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            armorNbt.put(slot.getName(), ItemStack.OPTIONAL_CODEC, player.getEquippedStack(slot).copy());
        }
        return armorNbt;
    }

    private static void equipTitanJuggernaut(PlayerEntity player, NbtCompound originalArmorNbt) {
        player.equipStack(EquipmentSlot.HEAD, createTitanArmorPiece(Items.NETHERITE_HELMET));
        player.equipStack(EquipmentSlot.CHEST, createTitanArmorPiece(Items.NETHERITE_CHESTPLATE));
        player.equipStack(EquipmentSlot.LEGS, createTitanArmorPiece(Items.NETHERITE_LEGGINGS));
        player.equipStack(EquipmentSlot.FEET, createTitanArmorPiece(Items.NETHERITE_BOOTS));

        ItemStack titanSword = new ItemStack(ModItems.TITAN_SWORD);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, titanSword, data -> {
            data.putBoolean(NBT_TITAN_GEAR, true);
            data.putBoolean(NBT_TITAN_SWORD, true);
            data.put(NBT_ORIGINAL_ARMOR, originalArmorNbt.copy());
        });
        player.setStackInHand(Hand.MAIN_HAND, titanSword);
    }

    private static ItemStack createTitanArmorPiece(net.minecraft.item.Item item) {
        ItemStack stack = new ItemStack(item);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> {
            data.putBoolean(NBT_TITAN_GEAR, true);
            data.putBoolean(NBT_TITAN_ARMOR, true);
        });
        return stack;
    }

    private static void restoreJuggernautLoadout(ServerWorld world, PlayerEntity player, Dome dome) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack equipped = player.getEquippedStack(slot);
            if (!equipped.isEmpty() && !isMarkedTitanArmor(equipped)) {
                player.equipStack(slot, ItemStack.EMPTY);
                moveToInventoryOrDrop(player, equipped.copy());
            } else if (isMarkedTitanArmor(equipped)) {
                player.equipStack(slot, ItemStack.EMPTY);
            }

            ItemStack original = dome.originalArmorNbt.get(slot.getName(), ItemStack.OPTIONAL_CODEC)
                    .map(ItemStack::copy)
                    .orElse(ItemStack.EMPTY);
            player.equipStack(slot, original);
        }

        ItemStack currentMain = player.getMainHandStack();
        if (!currentMain.isEmpty() && !isMarkedTitanSword(currentMain)) {
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            moveToInventoryOrDrop(player, currentMain.copy());
        } else if (isMarkedTitanSword(currentMain)) {
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }

        player.setStackInHand(Hand.MAIN_HAND, dome.originalMainHand.copy());
    }

    private static void moveToInventoryOrDrop(PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().insertStack(stack)) {
            player.dropItem(stack, false);
        }
    }

    private static void removeMarkedTitanGear(PlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isMarkedTitanGear(stack)) {
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack equipped = player.getEquippedStack(slot);
            if (isMarkedTitanGear(equipped)) {
                player.equipStack(slot, ItemStack.EMPTY);
            }
        }

        if (isMarkedTitanGear(player.getMainHandStack())) {
            player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        }
        if (isMarkedTitanGear(player.getOffHandStack())) {
            player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static boolean isMarkedTitanGear(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                        .copyNbt()
                        .getBoolean(NBT_TITAN_GEAR, false);
    }

    private static boolean isMarkedTitanArmor(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                        .copyNbt()
                        .getBoolean(NBT_TITAN_ARMOR, false);
    }

    private static boolean isMarkedTitanSword(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                        .copyNbt()
                        .getBoolean(NBT_TITAN_SWORD, false);
    }
}
