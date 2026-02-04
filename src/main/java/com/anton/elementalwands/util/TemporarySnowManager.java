package com.anton.elementalwands.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public final class TemporarySnowManager {

    private record SnowField(BlockPos center, int radius, LongSet placed, int expiryTick) {}

    private static final Map<RegistryKey<World>, List<SnowField>> FIELDS = new HashMap<>();

    private TemporarySnowManager() {}

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(TemporarySnowManager::tickWorld);
    }

    public static void createSnowField(ServerWorld world, BlockPos center, int radius, int durationTicks) {
        int expiryTick = world.getServer().getTicks() + durationTicks;
        LongOpenHashSet placed = new LongOpenHashSet();

        BlockState snowState = Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 1);
        BlockPos.Mutable base = new BlockPos.Mutable();
        BlockPos.Mutable place = new BlockPos.Mutable();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                base.set(center.getX() + dx, center.getY(), center.getZ() + dz);

                int down = 0;
                while (down < 6 && world.getBlockState(base).isAir() && base.getY() > world.getBottomY()) {
                    base.move(0, -1, 0);
                    down++;
                }

                if (world.getBlockState(base).isAir()) continue;
                if (!world.getBlockState(base).isSolidBlock(world, base)) continue;

                place.set(base.getX(), base.getY() + 1, base.getZ());
                if (!world.getBlockState(place).isAir()) continue;
                if (!snowState.canPlaceAt(world, place)) continue;

                world.setBlockState(place, snowState);
                placed.add(place.asLong());
            }
        }

        if (placed.isEmpty()) return;

        RegistryKey<World> key = world.getRegistryKey();
        FIELDS.computeIfAbsent(key, _k -> new ArrayList<>())
                .add(new SnowField(center, radius, placed, expiryTick));
    }

    private static void tickWorld(ServerWorld world) {
        List<SnowField> fields = FIELDS.get(world.getRegistryKey());
        if (fields == null || fields.isEmpty()) return;

        int tick = world.getServer().getTicks();

        Iterator<SnowField> it = fields.iterator();
        while (it.hasNext()) {
            SnowField field = it.next();

            if (tick >= field.expiryTick) {
                removeSnow(world, field);
                it.remove();
                continue;
            }

            applySlow(world, field);
        }

        if (fields.isEmpty()) {
            FIELDS.remove(world.getRegistryKey());
        }
    }

    private static void applySlow(ServerWorld world, SnowField field) {
        Box box = new Box(field.center).expand(field.radius + 1.5, 2.0, field.radius + 1.5);

        for (LivingEntity living : world.getEntitiesByClass(LivingEntity.class, box, _e -> true)) {
            BlockPos feet = living.getBlockPos();
            if (!field.placed.contains(feet.asLong())) continue;
            if (!world.getBlockState(feet).isOf(Blocks.SNOW)) continue;

            living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 0, false, true, true));
        }
    }

    private static void removeSnow(ServerWorld world, SnowField field) {
        for (long posLong : field.placed) {
            BlockPos p = BlockPos.fromLong(posLong);
            if (world.getBlockState(p).isOf(Blocks.SNOW)) {
                world.setBlockState(p, Blocks.AIR.getDefaultState());
            }
        }
    }
}

