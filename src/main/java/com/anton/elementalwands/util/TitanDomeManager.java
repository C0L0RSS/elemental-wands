package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class TitanDomeManager {

    private static final class Dome {
        private final BlockPos center;
        private final int radius;
        private final UUID casterUuid;
        private final int expiryTick;
        private int nextRepairTick;
        private final Long2ObjectMap<BlockState> originalByPos;

        private Dome(BlockPos center, int radius, UUID casterUuid, int expiryTick, int nextRepairTick,
                Long2ObjectMap<BlockState> originalByPos) {
            this.center = center;
            this.radius = radius;
            this.casterUuid = casterUuid;
            this.expiryTick = expiryTick;
            this.nextRepairTick = nextRepairTick;
            this.originalByPos = originalByPos;
        }
    }

    private static final Map<RegistryKey<World>, List<Dome>> DOMES = new HashMap<>();

    private static final int DURATION_TICKS = 240;
    private static final int RADIUS = 6;
    private static final int REPAIR_INTERVAL_TICKS = 10;

    private static final BlockState DOME_STATE = Blocks.POLISHED_DEEPSLATE.getDefaultState();

    private static final String NBT_TITAN_BLADE = "ew_titan_blade";

    private TitanDomeManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(TitanDomeManager::tickWorld);
    }

    public static void startDome(ServerWorld world, PlayerEntity caster) {
        int now = world.getServer().getTicks();

        BlockPos center = caster.getBlockPos();
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
                .add(new Dome(center, RADIUS, caster.getUuid(), now + DURATION_TICKS, now + REPAIR_INTERVAL_TICKS,
                        originalByPos));

        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, DURATION_TICKS + 20, 1, false, true, true));
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, DURATION_TICKS + 20, 1, false, true, true));
        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, DURATION_TICKS + 20, 0, false, true, true));

        giveTitanBlade(world, caster);

        world.playSound(null, center, SoundEvents.BLOCK_DEEPSLATE_PLACE, SoundCategory.PLAYERS, 1.1f, 0.7f);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, center.getX() + 0.5, center.getY() + 1.0,
                center.getZ() + 0.5, 40, 2.0, 0.8, 2.0, 0.02);
    }

    private static void tickWorld(ServerWorld world) {
        List<Dome> domes = DOMES.get(world.getRegistryKey());
        int now = world.getServer().getTicks();
        if (domes == null || domes.isEmpty()) {
            if (now % 20 == 0) {
                cleanupTitanBlades(world, Set.of());
            }
            return;
        }

        Set<UUID> activeCasters = new HashSet<>();

        Iterator<Dome> it = domes.iterator();
        while (it.hasNext()) {
            Dome dome = it.next();
            activeCasters.add(dome.casterUuid);

            if (now >= dome.expiryTick) {
                endDome(world, dome);
                it.remove();
                continue;
            }

            if (now >= dome.nextRepairTick) {
                repairDome(world, dome);
                dome.nextRepairTick = now + REPAIR_INTERVAL_TICKS;
            }
        }

        if (domes.isEmpty()) {
            DOMES.remove(world.getRegistryKey());
        }

        if (now % 20 == 0) {
            cleanupTitanBlades(world, activeCasters);
        }
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
            removeTitanBlade(caster);

            Box box = new Box(dome.center).expand(dome.radius + 4.0);
            for (ItemEntity itemEntity : world.getEntitiesByClass(ItemEntity.class, box, e -> isTitanBlade(e.getStack()))) {
                itemEntity.discard();
            }
        }

        world.playSound(null, dome.center, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.PLAYERS, 1.1f, 0.8f);
    }

    private static boolean canReplace(BlockState state) {
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) return false;
        return state.isReplaceable();
    }

    private static void giveTitanBlade(ServerWorld world, PlayerEntity caster) {
        if (hasTitanBlade(caster)) return;

        ItemStack blade = new ItemStack(Items.NETHERITE_SWORD);
        blade.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Titan Blade"));
        blade.addEnchantment(world.getRegistryManager().getEntryOrThrow(Enchantments.SHARPNESS), 5);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, blade, data -> data.putBoolean(NBT_TITAN_BLADE, true));

        if (!caster.getInventory().insertStack(blade)) {
            caster.dropItem(blade, false);
        }
    }

    private static void cleanupTitanBlades(ServerWorld world, Set<UUID> activeCasters) {
        for (PlayerEntity player : world.getPlayers()) {
            if (activeCasters.contains(player.getUuid())) continue;
            removeTitanBlade(player);
        }
    }

    private static void removeTitanBlade(PlayerEntity caster) {
        for (int i = 0; i < caster.getInventory().size(); i++) {
            ItemStack stack = caster.getInventory().getStack(i);
            if (!isTitanBlade(stack)) continue;
            caster.getInventory().setStack(i, ItemStack.EMPTY);
        }
    }

    private static boolean hasTitanBlade(PlayerEntity caster) {
        for (int i = 0; i < caster.getInventory().size(); i++) {
            if (isTitanBlade(caster.getInventory().getStack(i))) return true;
        }
        return false;
    }

    private static boolean isTitanBlade(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .copyNbt()
                .getBoolean(NBT_TITAN_BLADE, false);
    }
}
