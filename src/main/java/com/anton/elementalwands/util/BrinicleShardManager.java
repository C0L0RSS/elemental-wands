package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class BrinicleShardManager {

    public static final int SHARD_LIFESPAN_TICKS = 600;

    private static final int MAX_SHARDS_PER_CASTER = 5;
    private static final int NORMAL_PULSE_INTERVAL = 30;
    private static final int AMPLIFIED_PULSE_INTERVAL = 10;
    private static final int NORMAL_MAX_RADIUS = 3;
    private static final int AMPLIFIED_MAX_RADIUS = 6;

    private static final int CONSUMPTION_TAIL_TICKS = 310;

    private static final int ZONE_FROST_STACK_INTERVAL = 20;

    static final class Shard {
        final UUID shardId;
        final UUID casterUuid;
        final BlockPos anchorPos;
        final BlockPos floorPos;
        final RegistryKey<World> worldKey;
        final int plantTick;
        int expiryTick;
        int lastPulseTick;
        int currentRadius;
        boolean amplifiedByFog;
        boolean active;
        final Set<BlockPos> placedPositions = new HashSet<>();
        final Map<BlockPos, BlockState> originals = new HashMap<>();
        final Map<UUID, Integer> lastFrostTickByEntity = new HashMap<>();

        Shard(UUID shardId, UUID casterUuid, BlockPos anchorPos, BlockPos floorPos,
              RegistryKey<World> worldKey, int plantTick, int lifespanTicks) {
            this.shardId = shardId;
            this.casterUuid = casterUuid;
            this.anchorPos = anchorPos;
            this.floorPos = floorPos;
            this.worldKey = worldKey;
            this.plantTick = plantTick;
            this.expiryTick = plantTick + lifespanTicks;
            this.lastPulseTick = plantTick;
            this.currentRadius = 0;
            this.amplifiedByFog = false;
            this.active = true;
        }
    }

    public record ShardSnapshot(UUID shardId, BlockPos anchorPos, int plantTick) {
    }

    public record PlacementResult(Set<BlockPos> placed, Map<BlockPos, BlockState> originals) {
    }

    private static final Map<RegistryKey<World>, List<Shard>> ACTIVE = new HashMap<>();

    private BrinicleShardManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(BrinicleShardManager::tickWorld);
    }

    public static boolean tryPlantShard(ServerWorld world, PlayerEntity caster, BlockHitResult hit) {
        BlockPos hitPos = hit.getBlockPos();
        Direction face = hit.getSide();

        BlockPos floorPos = findFloorNearY(world, hitPos.getX(), hitPos.getY(), hitPos.getZ());
        if (floorPos == null) {
            return dudAt(world, hit.getPos());
        }

        BlockPos anchorPos = floorPos.up();
        BlockState atAnchor = world.getBlockState(anchorPos);
        BlockState anchorOriginal = atAnchor;
        if (!atAnchor.isAir() && !atAnchor.isReplaceable()) {
            return dudAt(world, hit.getPos());
        }

        List<Shard> forCaster = activeForCaster(world, caster.getUuid());
        if (forCaster.size() >= MAX_SHARDS_PER_CASTER) {
            Shard oldest = forCaster.stream().min(Comparator.comparingInt(s -> s.plantTick)).orElse(null);
            if (oldest != null) cleanupShard(world, oldest);
        }

        int placed = TemporaryBlockManager.placeTemporaryBlocks(world,
                List.of(anchorPos),
                Blocks.PACKED_ICE.getDefaultState(),
                SHARD_LIFESPAN_TICKS,
                s -> s.isAir() || s.isReplaceable());
        if (placed == 0) {
            return dudAt(world, hit.getPos());
        }

        int now = world.getServer().getTicks();
        Shard shard = new Shard(UUID.randomUUID(), caster.getUuid(), anchorPos, floorPos,
                world.getRegistryKey(), now, SHARD_LIFESPAN_TICKS);
        shard.placedPositions.add(anchorPos);
        shard.originals.put(anchorPos, anchorOriginal);

        ACTIVE.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>()).add(shard);

        if (face != Direction.UP) {
            spawnVerticalFlourish(world, hit.getPos(), floorPos);
        }

        world.playSound(null, anchorPos, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 0.6f, 1.6f);
        world.spawnParticles(ParticleTypes.SNOWFLAKE,
                anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5,
                12, 0.3, 0.3, 0.3, 0.02);
        return true;
    }

    public static List<ShardSnapshot> getActiveShardsForCaster(ServerWorld world, UUID casterUuid) {
        List<Shard> shards = activeForCaster(world, casterUuid);
        List<ShardSnapshot> out = new ArrayList<>(shards.size());
        for (Shard s : shards) {
            out.add(new ShardSnapshot(s.shardId, s.anchorPos, s.plantTick));
        }
        return out;
    }

    public static void markShardsForConsumption(ServerWorld world, UUID casterUuid, int castTick) {
        int cap = castTick + CONSUMPTION_TAIL_TICKS;
        for (Shard s : activeForCaster(world, casterUuid)) {
            if (s.expiryTick > cap) s.expiryTick = cap;
        }
    }

    public static boolean isShardAlive(ServerWorld world, UUID shardId) {
        List<Shard> list = ACTIVE.get(world.getRegistryKey());
        if (list == null) return false;
        for (Shard s : list) {
            if (s.shardId.equals(shardId) && s.active) return true;
        }
        return false;
    }

    public static boolean destroyShardAtAnchor(ServerWorld world, BlockPos anchorPos) {
        List<Shard> list = ACTIVE.get(world.getRegistryKey());
        if (list == null) return false;
        for (Shard s : list) {
            if (s.active && s.anchorPos.equals(anchorPos)) {
                cleanupShardInternal(world, s);
                return true;
            }
        }
        return false;
    }

    public static PlacementResult placeTerrainSnow(ServerWorld world,
                                                    Iterable<BlockPos> columnAnchors,
                                                    Set<BlockPos> alreadyPlaced,
                                                    int lifespanTicks) {
        List<BlockPos> snowTargets = new ArrayList<>();
        List<BlockPos> iceTargets = new ArrayList<>();
        Map<BlockPos, BlockState> originalsOut = new HashMap<>();

        for (BlockPos col : columnAnchors) {
            int surfaceY = findSurfaceY(world, col.getX(), col.getY(), col.getZ());
            if (surfaceY == Integer.MIN_VALUE) continue;

            BlockPos surfacePos = new BlockPos(col.getX(), surfaceY, col.getZ());
            BlockState surfaceState = world.getBlockState(surfacePos);

            if (surfaceState.getFluidState().isOf(Fluids.LAVA)
                    || surfaceState.getFluidState().isOf(Fluids.FLOWING_LAVA)) {
                continue;
            }

            if (surfaceState.getFluidState().isOf(Fluids.WATER)) {
                if (alreadyPlaced.contains(surfacePos)) continue;
                if (world.getBlockState(surfacePos).isOf(Blocks.PACKED_ICE)) continue;
                iceTargets.add(surfacePos);
                originalsOut.put(surfacePos, surfaceState);
                continue;
            }

            BlockPos snowPos = surfacePos.up();
            BlockState aboveState = world.getBlockState(snowPos);
            if (aboveState.isOf(Blocks.SNOW)) continue;
            if (!aboveState.isAir() && !aboveState.isReplaceable()) continue;
            if (alreadyPlaced.contains(snowPos)) continue;

            snowTargets.add(snowPos);
            originalsOut.put(snowPos, aboveState);
        }

        Set<BlockPos> actuallyPlaced = new HashSet<>();

        if (!snowTargets.isEmpty()) {
            BlockState snowState = Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, 1);
            TemporaryBlockManager.placeTemporaryBlocks(world, snowTargets, snowState, lifespanTicks,
                    s -> s.isAir() || s.isReplaceable());
            for (BlockPos p : snowTargets) {
                if (world.getBlockState(p).isOf(Blocks.SNOW)) {
                    actuallyPlaced.add(p);
                }
            }
        }

        if (!iceTargets.isEmpty()) {
            BlockState iceState = Blocks.PACKED_ICE.getDefaultState();
            TemporaryBlockManager.placeTemporaryBlocks(world, iceTargets, iceState, lifespanTicks,
                    s -> s.isAir() || s.isReplaceable() || !s.getFluidState().isEmpty());
            for (BlockPos p : iceTargets) {
                if (world.getBlockState(p).isOf(Blocks.PACKED_ICE)) {
                    actuallyPlaced.add(p);
                }
            }
        }

        Map<BlockPos, BlockState> finalOriginals = new HashMap<>();
        for (BlockPos p : actuallyPlaced) {
            BlockState orig = originalsOut.get(p);
            if (orig != null) finalOriginals.put(p, orig);
        }
        return new PlacementResult(actuallyPlaced, finalOriginals);
    }

    public static void restoreBlocks(ServerWorld world, Map<BlockPos, BlockState> originals) {
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState current = world.getBlockState(pos);
            if (current.isOf(Blocks.SNOW) || current.isOf(Blocks.PACKED_ICE)) {
                world.setBlockState(pos, entry.getValue(), 3);
            }
        }
    }

    private static List<Shard> activeForCaster(ServerWorld world, UUID casterUuid) {
        List<Shard> list = ACTIVE.get(world.getRegistryKey());
        if (list == null) return new ArrayList<>();
        List<Shard> out = new ArrayList<>();
        for (Shard s : list) {
            if (s.active && s.casterUuid.equals(casterUuid)) out.add(s);
        }
        return out;
    }

    private static boolean dudAt(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 6, 0.1, 0.1, 0.1, 0.01);
        world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_SNOWBALL_THROW,
                SoundCategory.PLAYERS, 0.4f, 0.6f);
        return false;
    }

    private static void spawnVerticalFlourish(ServerWorld world, Vec3d hitPos, BlockPos floorPos) {
        double floorTopY = floorPos.getY() + 1.0;
        int steps = Math.max(4, (int) Math.ceil(Math.abs(hitPos.y - floorTopY) * 2.0));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double y = hitPos.y + (floorTopY - hitPos.y) * t;
            world.spawnParticles(ParticleTypes.SNOWFLAKE, hitPos.x, y, hitPos.z, 1, 0.04, 0.04, 0.04, 0.0);
        }
    }

    private static BlockPos findFloorNearY(ServerWorld world, int x, int yCenter, int z) {
        for (int offset = 0; offset <= 4; offset++) {
            int[] dirs = (offset == 0) ? new int[]{0} : new int[]{-1, 1};
            for (int dir : dirs) {
                int y = yCenter + dir * offset;
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = world.getBlockState(pos);
                if (!state.isSolidBlock(world, pos)) continue;
                BlockState above = world.getBlockState(pos.up());
                if (above.isAir() || above.isReplaceable()) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static int findSurfaceY(ServerWorld world, int x, int yCenter, int z) {
        for (int offset = 0; offset <= 4; offset++) {
            int[] dirs = (offset == 0) ? new int[]{0} : new int[]{-1, 1};
            for (int dir : dirs) {
                int y = yCenter + dir * offset;
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = world.getBlockState(pos);
                if (!state.getFluidState().isEmpty()) {
                    return y;
                }
                if (!state.isSolidBlock(world, pos)) continue;
                BlockState above = world.getBlockState(pos.up());
                if (above.isAir() || above.isReplaceable()) {
                    return y;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private static void tickWorld(ServerWorld world) {
        List<Shard> list = ACTIVE.get(world.getRegistryKey());
        if (list == null || list.isEmpty()) return;

        int now = world.getServer().getTicks();

        Iterator<Shard> it = list.iterator();
        while (it.hasNext()) {
            Shard shard = it.next();
            if (!shard.active) {
                it.remove();
                continue;
            }

            shard.amplifiedByFog = WhiteoutManager.isInFog(world, shard.anchorPos);

            if (now >= shard.expiryTick) {
                cleanupShardInternal(world, shard);
                it.remove();
                continue;
            }

            if (!world.getBlockState(shard.anchorPos).isOf(Blocks.PACKED_ICE)) {
                cleanupShardInternal(world, shard);
                it.remove();
                continue;
            }

            if (anyProjectileAt(world, shard.anchorPos)) {
                cleanupShardInternal(world, shard);
                it.remove();
                continue;
            }

            pulseIfDue(world, shard, now);
            applyZoneEffects(world, shard, now);
        }

        if (list.isEmpty()) {
            ACTIVE.remove(world.getRegistryKey());
        }
    }

    private static boolean anyProjectileAt(ServerWorld world, BlockPos pos) {
        Box box = new Box(pos).expand(0.02);
        List<ProjectileEntity> list = world.getEntitiesByClass(ProjectileEntity.class, box, p -> !p.isRemoved());
        return !list.isEmpty();
    }

    private static void pulseIfDue(ServerWorld world, Shard shard, int now) {
        int interval = shard.amplifiedByFog ? AMPLIFIED_PULSE_INTERVAL : NORMAL_PULSE_INTERVAL;
        int maxRadius = shard.amplifiedByFog ? AMPLIFIED_MAX_RADIUS : NORMAL_MAX_RADIUS;

        if (now - shard.lastPulseTick < interval) return;
        if (shard.currentRadius >= maxRadius) return;

        shard.currentRadius++;
        shard.lastPulseTick = now;

        List<BlockPos> ringCols = chebyshevRingColumns(shard.floorPos, shard.currentRadius);
        int remainingLife = Math.max(20, shard.expiryTick - now);
        PlacementResult result = placeTerrainSnow(world, ringCols, shard.placedPositions, remainingLife);
        shard.placedPositions.addAll(result.placed());
        for (Map.Entry<BlockPos, BlockState> e : result.originals().entrySet()) {
            shard.originals.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    static List<BlockPos> chebyshevRingColumns(BlockPos center, int radius) {
        List<BlockPos> out = new ArrayList<>();
        if (radius <= 0) {
            out.add(center);
            return out;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                out.add(new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz));
            }
        }
        return out;
    }

    private static void applyZoneEffects(ServerWorld world, Shard shard, int now) {
        if (shard.placedPositions.isEmpty()) return;

        int maxRadius = shard.amplifiedByFog ? AMPLIFIED_MAX_RADIUS : NORMAL_MAX_RADIUS;
        int r = Math.max(shard.currentRadius, maxRadius) + 1;
        Box box = new Box(
                shard.floorPos.getX() - r, shard.floorPos.getY() - 0.5, shard.floorPos.getZ() - r,
                shard.floorPos.getX() + r + 1, shard.floorPos.getY() + 3.5, shard.floorPos.getZ() + r + 1);

        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator());

        for (LivingEntity e : entities) {
            BlockPos feet = e.getBlockPos();
            boolean inZone = shard.placedPositions.contains(feet) || shard.placedPositions.contains(feet.down());
            if (!inZone) continue;

            if (e.getUuid().equals(shard.casterUuid)) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 0, false, false, true));
                continue;
            }

            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3, false, false, true));

            Integer lastFrost = shard.lastFrostTickByEntity.get(e.getUuid());
            if (lastFrost == null || now - lastFrost >= ZONE_FROST_STACK_INTERVAL) {
                ChillTracker.addStack(world, e);
                shard.lastFrostTickByEntity.put(e.getUuid(), now);
            }
        }
    }

    private static void cleanupShard(ServerWorld world, Shard shard) {
        if (!shard.active) return;
        cleanupShardInternal(world, shard);
        List<Shard> list = ACTIVE.get(world.getRegistryKey());
        if (list != null) list.remove(shard);
    }

    private static void cleanupShardInternal(ServerWorld world, Shard shard) {
        if (!shard.active) return;
        shard.active = false;

        restoreBlocks(world, shard.originals);

        world.spawnParticles(ParticleTypes.SNOWFLAKE,
                shard.anchorPos.getX() + 0.5, shard.anchorPos.getY() + 0.5, shard.anchorPos.getZ() + 0.5,
                20, 0.6, 0.6, 0.6, 0.05);
        world.playSound(null, shard.anchorPos, SoundEvents.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 0.9f, 0.7f);
    }
}
