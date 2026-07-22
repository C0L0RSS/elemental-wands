package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModParticles;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Tracks the Nature wand's planted seedlings. A seed projectile sprouts a flowering anchor; the
 * seedling then periodically pulses verdant growth (moss carpet on land, lily pads over water)
 * outward in rings, slowing, entangling and thorn-damaging any enemy caught in the thicket.
 * Seedlings inside an {@link OvergrowthManager} cloud spread faster and farther.
 */
public final class SeedlingManager {

    public static final int SEEDLING_LIFESPAN_TICKS = 600;

    private static final int MAX_SEEDLINGS_PER_CASTER = 5;
    private static final int NORMAL_PULSE_INTERVAL = 30;
    private static final int AMPLIFIED_PULSE_INTERVAL = 10;
    private static final int NORMAL_MAX_RADIUS = 3;
    private static final int AMPLIFIED_MAX_RADIUS = 6;

    private static final int CONSUMPTION_TAIL_TICKS = 310;

    private static final int ZONE_ENTANGLE_INTERVAL = 20;
    private static final float THORN_DAMAGE = 1.5f;

    static final class Seedling {
        final UUID seedlingId;
        final UUID casterUuid;
        final BlockPos anchorPos;
        final BlockPos floorPos;
        final RegistryKey<World> worldKey;
        final int plantTick;
        int expiryTick;
        int lastPulseTick;
        int currentRadius;
        boolean amplifiedByOvergrowth;
        boolean active;
        final Set<BlockPos> placedPositions = new HashSet<>();
        final Map<BlockPos, BlockState> originals = new HashMap<>();
        final Map<UUID, Integer> lastEntangleTickByEntity = new HashMap<>();

        Seedling(UUID seedlingId, UUID casterUuid, BlockPos anchorPos, BlockPos floorPos,
              RegistryKey<World> worldKey, int plantTick, int lifespanTicks) {
            this.seedlingId = seedlingId;
            this.casterUuid = casterUuid;
            this.anchorPos = anchorPos;
            this.floorPos = floorPos;
            this.worldKey = worldKey;
            this.plantTick = plantTick;
            this.expiryTick = plantTick + lifespanTicks;
            this.lastPulseTick = plantTick;
            this.currentRadius = 0;
            this.amplifiedByOvergrowth = false;
            this.active = true;
        }
    }

    public record SeedlingSnapshot(UUID seedlingId, BlockPos anchorPos, int plantTick) {
    }

    public record PlacementResult(Set<BlockPos> placed, Map<BlockPos, BlockState> originals) {
    }

    private static final Map<RegistryKey<World>, List<Seedling>> ACTIVE = new HashMap<>();

    private SeedlingManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(SeedlingManager::tickWorld);
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                syncActiveSeedlings(player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE.clear());
    }

    public static boolean tryPlantSeedling(ServerWorld world, PlayerEntity caster, BlockHitResult hit) {
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

        List<Seedling> forCaster = activeForCaster(world, caster.getUuid());
        if (forCaster.size() >= MAX_SEEDLINGS_PER_CASTER) {
            Seedling oldest = forCaster.stream().min(Comparator.comparingInt(s -> s.plantTick)).orElse(null);
            if (oldest != null) cleanupSeedling(world, oldest);
        }

        int placed = TemporaryBlockManager.placeTemporaryBlocks(world,
                List.of(anchorPos),
                Blocks.FLOWERING_AZALEA.getDefaultState(),
                SEEDLING_LIFESPAN_TICKS,
                s -> s.isAir() || s.isReplaceable());
        if (placed == 0) {
            return dudAt(world, hit.getPos());
        }

        int now = world.getServer().getTicks();
        Seedling seedling = new Seedling(UUID.randomUUID(), caster.getUuid(), anchorPos, floorPos,
                world.getRegistryKey(), now, SEEDLING_LIFESPAN_TICKS);
        seedling.placedPositions.add(anchorPos);
        seedling.originals.put(anchorPos, anchorOriginal);

        ACTIVE.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>()).add(seedling);
        syncActiveSeedlings(world, caster.getUuid());

        if (face != Direction.UP) {
            spawnVerticalFlourish(world, hit.getPos(), floorPos);
        }

        world.playSound(null, anchorPos, SoundEvents.BLOCK_AZALEA_PLACE, SoundCategory.PLAYERS, 0.7f, 1.1f);
        world.spawnParticles(ModParticles.NATURE_BLOOM,
                anchorPos.getX() + 0.5, anchorPos.getY() + 0.82, anchorPos.getZ() + 0.5,
                1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.NATURE_LEAF,
                anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5,
                10, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                anchorPos.getX() + 0.5, anchorPos.getY() + 0.7, anchorPos.getZ() + 0.5,
                14, 0.32, 0.24, 0.32, 0.018);
        return true;
    }

    public static List<SeedlingSnapshot> getActiveSeedlingsForCaster(ServerWorld world, UUID casterUuid) {
        List<Seedling> seedlings = activeForCaster(world, casterUuid);
        List<SeedlingSnapshot> out = new ArrayList<>(seedlings.size());
        for (Seedling s : seedlings) {
            out.add(new SeedlingSnapshot(s.seedlingId, s.anchorPos, s.plantTick));
        }
        return out;
    }

    public static List<BlockPos> getActiveSeedlingPositionsForCaster(ServerWorld world, UUID casterUuid) {
        List<Seedling> seedlings = activeForCaster(world, casterUuid);
        List<BlockPos> out = new ArrayList<>(seedlings.size());
        for (Seedling s : seedlings) {
            out.add(s.anchorPos.toImmutable());
        }
        return out;
    }

    public static Optional<SeedlingSnapshot> findTargetedSeedling(ServerWorld world, PlayerEntity caster, double range) {
        List<Seedling> seedlings = activeForCaster(world, caster.getUuid());
        if (seedlings.isEmpty()) return Optional.empty();

        Vec3d start = caster.getEyePos();
        Vec3d direction = caster.getRotationVec(1.0f).normalize();
        Vec3d end = start.add(direction.multiply(range));

        BlockHitResult blockHit = world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                caster));
        double blockLimitSq = blockHit.getType() == HitResult.Type.MISS
                ? range * range
                : start.squaredDistanceTo(blockHit.getPos()) + 0.25;

        Seedling best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Seedling seedling : seedlings) {
            Optional<Vec3d> hit = new Box(seedling.anchorPos).expand(0.2).raycast(start, end);
            if (hit.isEmpty()) continue;

            double distSq = start.squaredDistanceTo(hit.get());
            if (distSq > blockLimitSq || distSq >= bestDistSq) continue;

            best = seedling;
            bestDistSq = distSq;
        }

        return best == null
                ? Optional.empty()
                : Optional.of(new SeedlingSnapshot(best.seedlingId, best.anchorPos, best.plantTick));
    }

    public static int consumeAllSeedlingsForCaster(ServerWorld world, UUID casterUuid) {
        List<Seedling> seedlings = activeForCaster(world, casterUuid);
        if (seedlings.isEmpty()) {
            syncActiveSeedlings(world, casterUuid);
            return 0;
        }

        for (Seedling seedling : seedlings) {
            cleanupSeedlingInternal(world, seedling);
        }

        List<Seedling> list = ACTIVE.get(world.getRegistryKey());
        if (list != null) {
            list.removeIf(s -> s.casterUuid.equals(casterUuid));
            if (list.isEmpty()) {
                ACTIVE.remove(world.getRegistryKey());
            }
        }

        syncActiveSeedlings(world, casterUuid);
        return seedlings.size();
    }

    public static void syncActiveSeedlings(ServerPlayerEntity player) {
        if (player.getEntityWorld() instanceof ServerWorld world) {
            ModNetworking.syncNatureSeedlings(player, getActiveSeedlingPositionsForCaster(world, player.getUuid()));
        }
    }

    private static void syncActiveSeedlings(ServerWorld world, UUID casterUuid) {
        if (world.getPlayerByUuid(casterUuid) instanceof ServerPlayerEntity player) {
            ModNetworking.syncNatureSeedlings(player, getActiveSeedlingPositionsForCaster(world, casterUuid));
        }
    }

    public static void markSeedlingsForConsumption(ServerWorld world, UUID casterUuid, int castTick) {
        int cap = castTick + CONSUMPTION_TAIL_TICKS;
        for (Seedling s : activeForCaster(world, casterUuid)) {
            if (s.expiryTick > cap) s.expiryTick = cap;
        }
    }

    public static boolean isSeedlingAlive(ServerWorld world, UUID seedlingId) {
        List<Seedling> list = ACTIVE.get(world.getRegistryKey());
        if (list == null) return false;
        for (Seedling s : list) {
            if (s.seedlingId.equals(seedlingId) && s.active) return true;
        }
        return false;
    }

    public static boolean destroySeedlingAtAnchor(ServerWorld world, BlockPos anchorPos) {
        List<Seedling> list = ACTIVE.get(world.getRegistryKey());
        if (list == null) return false;
        for (Seedling s : list) {
            if (s.active && s.anchorPos.equals(anchorPos)) {
                cleanupSeedlingInternal(world, s);
                syncActiveSeedlings(world, s.casterUuid);
                return true;
            }
        }
        return false;
    }

    public static PlacementResult placeVerdantGrowth(ServerWorld world,
                                                    Iterable<BlockPos> columnAnchors,
                                                    Set<BlockPos> alreadyPlaced,
                                                    int lifespanTicks) {
        List<BlockPos> mossTargets = new ArrayList<>();
        List<BlockPos> lilyTargets = new ArrayList<>();
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
                // Float a lily pad on the air directly above the water surface.
                BlockPos padPos = surfacePos.up();
                BlockState padState = world.getBlockState(padPos);
                if (padState.isOf(Blocks.LILY_PAD)) continue;
                if (!padState.isAir() && !padState.isReplaceable()) continue;
                if (alreadyPlaced.contains(padPos)) continue;
                lilyTargets.add(padPos);
                originalsOut.put(padPos, padState);
                continue;
            }

            BlockPos mossPos = surfacePos.up();
            BlockState aboveState = world.getBlockState(mossPos);
            if (aboveState.isOf(Blocks.MOSS_CARPET)) continue;
            if (!aboveState.isAir() && !aboveState.isReplaceable()) continue;
            if (alreadyPlaced.contains(mossPos)) continue;

            mossTargets.add(mossPos);
            originalsOut.put(mossPos, aboveState);
        }

        Set<BlockPos> actuallyPlaced = new HashSet<>();

        if (!mossTargets.isEmpty()) {
            BlockState mossState = Blocks.MOSS_CARPET.getDefaultState();
            TemporaryBlockManager.placeTemporaryBlocks(world, mossTargets, mossState, lifespanTicks,
                    s -> s.isAir() || s.isReplaceable());
            for (BlockPos p : mossTargets) {
                if (world.getBlockState(p).isOf(Blocks.MOSS_CARPET)) {
                    actuallyPlaced.add(p);
                }
            }
        }

        if (!lilyTargets.isEmpty()) {
            BlockState lilyState = Blocks.LILY_PAD.getDefaultState();
            TemporaryBlockManager.placeTemporaryBlocks(world, lilyTargets, lilyState, lifespanTicks,
                    s -> s.isAir() || s.isReplaceable());
            for (BlockPos p : lilyTargets) {
                if (world.getBlockState(p).isOf(Blocks.LILY_PAD)) {
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
            if (current.isOf(Blocks.MOSS_CARPET) || current.isOf(Blocks.LILY_PAD)
                    || current.isOf(Blocks.FLOWERING_AZALEA)) {
                world.setBlockState(pos, entry.getValue(), 3);
            }
        }
    }

    private static List<Seedling> activeForCaster(ServerWorld world, UUID casterUuid) {
        List<Seedling> list = ACTIVE.get(world.getRegistryKey());
        if (list == null) return new ArrayList<>();
        List<Seedling> out = new ArrayList<>();
        for (Seedling s : list) {
            if (s.active && s.casterUuid.equals(casterUuid)) out.add(s);
        }
        return out;
    }

    private static boolean dudAt(ServerWorld world, Vec3d pos) {
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                pos.x, pos.y, pos.z, 6, 0.1, 0.1, 0.1, 0.01);
        world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_EGG_THROW,
                SoundCategory.PLAYERS, 0.4f, 0.8f);
        return false;
    }

    private static void spawnVerticalFlourish(ServerWorld world, Vec3d hitPos, BlockPos floorPos) {
        double floorTopY = floorPos.getY() + 1.0;
        int steps = Math.max(4, (int) Math.ceil(Math.abs(hitPos.y - floorTopY) * 2.0));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double y = hitPos.y + (floorTopY - hitPos.y) * t;
            world.spawnParticles(i % 2 == 0 ? ModParticles.NATURE_VINE : ModParticles.NATURE_POLLEN,
                    hitPos.x, y, hitPos.z, 1, 0.04, 0.04, 0.04, 0.0);
        }
    }

    private static BlockPos findFloorNearY(ServerWorld world, int x, int yCenter, int z) {
        for (int offset = 0; offset <= 4; offset++) {
            int[] dirs = (offset == 0) ? new int[]{0} : new int[]{-1, 1};
            for (int dir : dirs) {
                int y = yCenter + dir * offset;
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = world.getBlockState(pos);
                if (!hasFloorTop(world, pos, state)) continue;
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
                if (!hasFloorTop(world, pos, state)) continue;
                BlockState above = world.getBlockState(pos.up());
                if (above.isAir() || above.isReplaceable()) {
                    return y;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Treats blocks with a flat solid top surface (dirt path, farmland, slabs, carpets, etc.)
     * as a valid floor in addition to ordinary full opaque cubes. {@code isSolidBlock} alone
     * rejects partial-collision blocks like dirt path, which made the seedling fail to plant on them.
     */
    private static boolean hasFloorTop(ServerWorld world, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.isSolidBlock(world, pos)) return true;
        return state.isSideSolidFullSquare(world, pos, Direction.UP);
    }

    private static void tickWorld(ServerWorld world) {
        List<Seedling> list = ACTIVE.get(world.getRegistryKey());
        if (list == null || list.isEmpty()) return;

        int now = world.getServer().getTicks();
        Set<UUID> changedCasters = new HashSet<>();

        Iterator<Seedling> it = list.iterator();
        while (it.hasNext()) {
            Seedling seedling = it.next();
            if (!seedling.active) {
                changedCasters.add(seedling.casterUuid);
                it.remove();
                continue;
            }

            seedling.amplifiedByOvergrowth = OvergrowthManager.isInOvergrowth(world, seedling.anchorPos);

            if (now >= seedling.expiryTick) {
                cleanupSeedlingInternal(world, seedling);
                changedCasters.add(seedling.casterUuid);
                it.remove();
                continue;
            }

            if (!world.getBlockState(seedling.anchorPos).isOf(Blocks.FLOWERING_AZALEA)) {
                cleanupSeedlingInternal(world, seedling);
                changedCasters.add(seedling.casterUuid);
                it.remove();
                continue;
            }

            if (anyProjectileAt(world, seedling.anchorPos)) {
                cleanupSeedlingInternal(world, seedling);
                changedCasters.add(seedling.casterUuid);
                it.remove();
                continue;
            }

            pulseIfDue(world, seedling, now);
            applyZoneEffects(world, seedling, now);

            // The physical azalea is intentionally vanilla-scale; the pulsing particle crown
            // conveys growth level, amplification, and the fully-active state at a glance.
            int phase = Math.floorMod(seedling.anchorPos.getX() * 3
                    + seedling.anchorPos.getZ() * 5, 6);
            if ((now + phase) % 6 == 0) {
                NatureVfx.seedlingPulse(world, seedling.anchorPos,
                        Math.max(1, seedling.currentRadius), seedling.amplifiedByOvergrowth, now);
            }
        }

        if (list.isEmpty()) {
            ACTIVE.remove(world.getRegistryKey());
        }

        for (UUID casterUuid : changedCasters) {
            syncActiveSeedlings(world, casterUuid);
        }
    }

    private static boolean anyProjectileAt(ServerWorld world, BlockPos pos) {
        Box box = new Box(pos).expand(0.02);
        List<ProjectileEntity> list = world.getEntitiesByClass(ProjectileEntity.class, box, p -> !p.isRemoved());
        return !list.isEmpty();
    }

    private static void pulseIfDue(ServerWorld world, Seedling seedling, int now) {
        int interval = seedling.amplifiedByOvergrowth ? AMPLIFIED_PULSE_INTERVAL : NORMAL_PULSE_INTERVAL;
        int maxRadius = seedling.amplifiedByOvergrowth ? AMPLIFIED_MAX_RADIUS : NORMAL_MAX_RADIUS;

        if (now - seedling.lastPulseTick < interval) return;
        if (seedling.currentRadius >= maxRadius) return;

        seedling.currentRadius++;
        seedling.lastPulseTick = now;

        List<BlockPos> ringCols = chebyshevRingColumns(seedling.floorPos, seedling.currentRadius);
        int remainingLife = Math.max(20, seedling.expiryTick - now);
        PlacementResult result = placeVerdantGrowth(world, ringCols, seedling.placedPositions, remainingLife);
        seedling.placedPositions.addAll(result.placed());
        for (Map.Entry<BlockPos, BlockState> e : result.originals().entrySet()) {
            seedling.originals.putIfAbsent(e.getKey(), e.getValue());
        }

        NatureVfx.growthRing(world, seedling.floorPos, seedling.currentRadius, now);
        NatureVfx.seedlingPulse(world, seedling.anchorPos, seedling.currentRadius,
                seedling.amplifiedByOvergrowth, now);
        world.playSound(null, seedling.anchorPos, SoundEvents.ITEM_BONE_MEAL_USE,
                SoundCategory.PLAYERS, 0.32f,
                1.1f + Math.min(0.35f, seedling.currentRadius * 0.05f));
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

    private static void applyZoneEffects(ServerWorld world, Seedling seedling, int now) {
        if (seedling.placedPositions.isEmpty()) return;

        int maxRadius = seedling.amplifiedByOvergrowth ? AMPLIFIED_MAX_RADIUS : NORMAL_MAX_RADIUS;
        int r = Math.max(seedling.currentRadius, maxRadius) + 1;
        Box box = new Box(
                seedling.floorPos.getX() - r, seedling.floorPos.getY() - 0.5, seedling.floorPos.getZ() - r,
                seedling.floorPos.getX() + r + 1, seedling.floorPos.getY() + 3.5, seedling.floorPos.getZ() + r + 1);

        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator());

        for (LivingEntity e : entities) {
            BlockPos feet = e.getBlockPos();
            boolean inZone = seedling.placedPositions.contains(feet) || seedling.placedPositions.contains(feet.down());
            if (!inZone) continue;

            if (e.getUuid().equals(seedling.casterUuid)) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 0, false, false, true));
                continue;
            }

            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3, false, false, true));

            Integer lastTick = seedling.lastEntangleTickByEntity.get(e.getUuid());
            if (lastTick == null || now - lastTick >= ZONE_ENTANGLE_INTERVAL) {
                EntangleTracker.addStack(world, e);
                applyThorns(world, e, seedling.casterUuid);
                seedling.lastEntangleTickByEntity.put(e.getUuid(), now);
            }
        }
    }

    /** Periodic bramble damage from standing in the thicket, credited to the caster's wand. */
    static void applyThorns(ServerWorld world, LivingEntity target, UUID casterUuid) {
        boolean hurt = target.damage(world, world.getDamageSources().sweetBerryBush(), THORN_DAMAGE);
        if (hurt) {
            PlayerEntity caster = world.getPlayerByUuid(casterUuid);
            if (caster != null) {
                AbstractWandItem.onWandDamageDealt(caster, THORN_DAMAGE);
            }
        }
    }

    private static void cleanupSeedling(ServerWorld world, Seedling seedling) {
        if (!seedling.active) return;
        cleanupSeedlingInternal(world, seedling);
        List<Seedling> list = ACTIVE.get(world.getRegistryKey());
        if (list != null) list.remove(seedling);
        syncActiveSeedlings(world, seedling.casterUuid);
    }

    private static void cleanupSeedlingInternal(ServerWorld world, Seedling seedling) {
        if (!seedling.active) return;
        seedling.active = false;

        restoreBlocks(world, seedling.originals);

        world.spawnParticles(ModParticles.NATURE_PETAL,
                seedling.anchorPos.getX() + 0.5, seedling.anchorPos.getY() + 0.5, seedling.anchorPos.getZ() + 0.5,
                16, 0.6, 0.6, 0.6, 0.04);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                seedling.anchorPos.getX() + 0.5, seedling.anchorPos.getY() + 0.68, seedling.anchorPos.getZ() + 0.5,
                10, 0.42, 0.42, 0.42, 0.025);
        world.playSound(null, seedling.anchorPos, SoundEvents.BLOCK_GRASS_BREAK,
                SoundCategory.PLAYERS, 0.9f, 0.8f);
    }
}
