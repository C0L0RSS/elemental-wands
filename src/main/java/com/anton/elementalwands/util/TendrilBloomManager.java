package com.anton.elementalwands.util;

import com.anton.elementalwands.party.WandAllies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.anton.elementalwands.registry.ModParticles;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Tendril Bloom: flowers supply individual tendrils; a seedless cast grows a three-branch knot.
 * Every trail and bramble patch belongs to a breakable source and ends with that source.
 */
public final class TendrilBloomManager {

    private static final int TENDRIL_MAX_TICKS = 24;
    private static final double TENDRIL_SPEED_PER_TICK = 0.625;
    private static final int TENDRIL_GROWTH_LIFESPAN = 260;

    private static final int BLOOM_LIFESPAN = 260;
    private static final int BLOOM_GROWTH_INTERVAL = 20;
    private static final int BLOOM_AMBIENT_INTERVAL = 15;
    private static final int BLOOM_MAX_RADIUS = 3;

    public static final int SOURCE_LIFETIME = TENDRIL_MAX_TICKS + BLOOM_LIFESPAN;
    private static final class Source {
        final UUID caster, seedling;
        final BlockPos anchor;
        final int expiry;
        final TemporaryBlockManager.TemporaryPlacement knot;
        final Map<BlockPos, TemporaryBlockManager.TemporaryPlacement> trails = new HashMap<>();
        boolean active = true;
        Source(UUID caster, UUID seedling, BlockPos anchor, int expiry, TemporaryBlockManager.TemporaryPlacement knot) {
            this.caster = caster; this.seedling = seedling; this.anchor = anchor.toImmutable();
            this.expiry = expiry; this.knot = knot;
        }
    }
    private static final Map<RegistryKey<World>, List<Source>> SOURCES = new HashMap<>();

    private static final class Tendril {
        final Source source;
        final UUID casterUuid;
        final UUID targetUuid;
        Vec3d currentHead;
        Vec3d lastKnownTarget;
        final int startTick;
        final Set<BlockPos> placedPositions = new HashSet<>();
        final Map<BlockPos, TemporaryBlockManager.TemporaryPlacement> placements = new HashMap<>();

        Tendril(Source source, UUID casterUuid, UUID targetUuid, Vec3d start, Vec3d lastKnown, int startTick) {
            this.source = source;
            this.casterUuid = casterUuid;
            this.targetUuid = targetUuid;
            this.currentHead = start;
            this.lastKnownTarget = lastKnown;
            this.startTick = startTick;
        }
    }

    private static final class Bloom {
        final Source source;
        final UUID casterUuid;
        final BlockPos center;
        final Set<BlockPos> crushed = new HashSet<>();
        final int startTick;
        int currentRadius;
        int lastGrowthTick;
        final Set<BlockPos> placedPositions = new HashSet<>();
        final Map<BlockPos, TemporaryBlockManager.TemporaryPlacement> placements = new HashMap<>();

        Bloom(Source source, UUID casterUuid, BlockPos center, int startTick) {
            this.casterUuid = casterUuid;
            this.source = source;
            this.center = center;
            this.startTick = startTick;
            this.currentRadius = 0;
            this.lastGrowthTick = startTick;
        }
    }

    private static final Map<RegistryKey<World>, List<Tendril>> TENDRILS = new HashMap<>();
    private static final Map<RegistryKey<World>, List<Bloom>> BLOOMS = new HashMap<>();

    private TendrilBloomManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(TendrilBloomManager::tickWorld);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SOURCES.clear();
            TENDRILS.clear();
            BLOOMS.clear();
        });
    }

    public static void startTendril(ServerWorld world, PlayerEntity caster, UUID seedlingId,
                                     Vec3d seedlingAnchor, LivingEntity target) {
        var source = new Source(caster.getUuid(), seedlingId, BlockPos.ofFloored(seedlingAnchor),
                world.getServer().getTicks() + SOURCE_LIFETIME, null);
        SOURCES.computeIfAbsent(world.getRegistryKey(), k -> new ArrayList<>()).add(source);
        launch(world, source, seedlingAnchor, target, target.getEntityPos().add(0, target.getHeight() * .5, 0));
    }

    private static void launch(ServerWorld world, Source source, Vec3d origin, LivingEntity target, Vec3d destination) {
        int now = world.getServer().getTicks();
        Tendril t = new Tendril(source, source.caster, target == null ? null : target.getUuid(), origin, destination, now);
        TENDRILS.computeIfAbsent(world.getRegistryKey(), k -> new ArrayList<>()).add(t);
        Vec3d seedlingAnchor = origin;
        world.spawnParticles(ModParticles.NATURE_LEAF,
                seedlingAnchor.x, seedlingAnchor.y + 0.52, seedlingAnchor.z,
                1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                seedlingAnchor.x, seedlingAnchor.y + 0.42, seedlingAnchor.z,
                10, 0.3, 0.26, 0.3, 0.018);

        world.playSound(null, BlockPos.ofFloored(seedlingAnchor), SoundEvents.ITEM_BONE_MEAL_USE,
                SoundCategory.PLAYERS, 0.8f, 0.9f);
    }

    private static void tickWorld(ServerWorld world) {
        int now = world.getServer().getTicks();

        var sources = SOURCES.get(world.getRegistryKey());
        if (sources != null) {
            for (Source source : sources) {
                var caster = world.getPlayerByUuid(source.caster);
                boolean valid = caster != null && caster.isAlive() && !caster.isSpectator()
                        && com.anton.elementalwands.data.EWAttachments.getAffinity(caster) == com.anton.elementalwands.data.WizardAffinity.NATURE
                        && WandLoadouts.get(caster).contains("tendril_bloom");
                if (!source.active || !valid || now >= source.expiry
                        || !world.isChunkLoaded(source.anchor)
                        || (source.seedling != null && !SeedlingManager.isSeedlingAlive(world, source.seedling))
                        || (source.knot != null && (!world.getBlockState(source.anchor).isOf(com.anton.elementalwands.registry.ModSpellBlocks.NATURE_ROOT_KNOT)
                            || !world.getBlockState(source.anchor.down()).isSideSolidFullSquare(world, source.anchor.down(), net.minecraft.util.math.Direction.UP)))) {
                    endSource(world, source);
                }
            }
        }
        List<Tendril> tendrils = TENDRILS.get(world.getRegistryKey());
        if (tendrils != null && !tendrils.isEmpty()) {
            Iterator<Tendril> it = tendrils.iterator();
            while (it.hasNext()) {
                Tendril t = it.next();
                boolean done = tickTendril(world, t, now);
                if (done) it.remove();
            }
            if (tendrils.isEmpty()) TENDRILS.remove(world.getRegistryKey());
        }

        List<Bloom> blooms = BLOOMS.get(world.getRegistryKey());
        if (blooms != null && !blooms.isEmpty()) {
            Iterator<Bloom> it = blooms.iterator();
            while (it.hasNext()) {
                Bloom b = it.next();
                boolean done = tickBloom(world, b, now);
                if (done) it.remove();
            }
            if (blooms.isEmpty()) BLOOMS.remove(world.getRegistryKey());
        }
        if (sources != null) {
            sources.removeIf(source -> !source.active);
            if (sources.isEmpty()) SOURCES.remove(world.getRegistryKey());
        }
    }

    private static boolean tickTendril(ServerWorld world, Tendril t, int now) {
        if (!t.source.active) return true;

        int age = now - t.startTick;

        Vec3d targetPos = t.lastKnownTarget;
        Entity target = t.targetUuid == null ? null : world.getEntity(t.targetUuid);
        if (target != null && WandAllies.protectedFrom(world, t.casterUuid, target)) return true;
        if (target instanceof LivingEntity living && living.isAlive()) {
            targetPos = living.getEntityPos().add(0, living.getHeight() * 0.5, 0);
            t.lastKnownTarget = targetPos;
        }

        Vec3d toTarget = targetPos.subtract(t.currentHead);
        double dist = toTarget.length();

        if (dist <= TENDRIL_SPEED_PER_TICK || age >= TENDRIL_MAX_TICKS) {
            Vec3d arrival = dist <= TENDRIL_SPEED_PER_TICK && clearTravel(world, t, t.currentHead, targetPos)
                    ? targetPos : t.currentHead;
            spawnBloom(world, t.source, BlockPos.ofFloored(arrival), now);
            return true;
        }

        Vec3d prevHead = t.currentHead;
        Vec3d step = toTarget.normalize().multiply(TENDRIL_SPEED_PER_TICK);
        t.currentHead = t.currentHead.add(step);
        if (!clearTravel(world, t, prevHead, t.currentHead)) {
            spawnBloom(world, t.source, BlockPos.ofFloored(prevHead), now);
            return true;
        }

        Set<BlockPos> newColumns = new HashSet<>();
        int substeps = Math.max(1, (int) Math.ceil(TENDRIL_SPEED_PER_TICK / 0.25));
        for (int i = 1; i <= substeps; i++) {
            double frac = i / (double) substeps;
            Vec3d p = prevHead.lerp(t.currentHead, frac);
            newColumns.add(BlockPos.ofFloored(p));
        }

        SeedlingManager.PlacementResult result = SeedlingManager.placeVerdantGrowth(
                world, newColumns, t.placedPositions, TENDRIL_GROWTH_LIFESPAN);
        t.placedPositions.addAll(result.placed());
        for (Map.Entry<BlockPos, TemporaryBlockManager.TemporaryPlacement> e : result.placements().entrySet()) {
            t.placements.putIfAbsent(e.getKey(), e.getValue());
            t.source.trails.putIfAbsent(e.getKey(), e.getValue());
        }

        NatureVfx.pairedTendril(world, prevHead, t.currentHead, age);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                t.currentHead.x, t.currentHead.y + 0.14, t.currentHead.z,
                2, 0.13, 0.08, 0.13, 0.01);
        return false;
    }

    private static boolean clearTravel(ServerWorld world, Tendril t, Vec3d from, Vec3d to) {
        return world.raycast(new net.minecraft.world.RaycastContext(from, to,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, world.getPlayerByUuid(t.casterUuid)) {
            @Override public net.minecraft.util.shape.VoxelShape getBlockShape(net.minecraft.block.BlockState state,
                    net.minecraft.world.BlockView view, BlockPos pos) {
                return com.anton.elementalwands.registry.ModSpellBlocks.isNatureGrowth(state)
                        ? net.minecraft.util.shape.VoxelShapes.empty() : super.getBlockShape(state, view, pos);
            }
        }).getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    private static void spawnBloom(ServerWorld world, Source source, BlockPos center, int now) {
        BlockPos floor = SeedlingManager.findFloorNearY(world, center.getX(), center.getY(), center.getZ());
        if (floor == null) return;
        center = floor;
        Bloom b = new Bloom(source, source.caster, center, now);
        b.currentRadius = 1;
        b.lastGrowthTick = now;

        List<BlockPos> diskCols = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                diskCols.add(new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz));
            }
        }

        SeedlingManager.PlacementResult result = SeedlingManager.placeVerdantGrowth(
                world, diskCols, b.placedPositions, BLOOM_LIFESPAN);
        b.placedPositions.addAll(result.placed());
        b.placements.putAll(result.placements());

        BLOOMS.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>()).add(b);

        world.spawnParticles(ModParticles.NATURE_LEAF,
                center.getX() + 0.5, center.getY() + 0.55, center.getZ() + 0.5,
                3, 0.35, 0.08, 0.35, 0.0);
        world.spawnParticles(ModParticles.NATURE_LEAF,
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                26, 0.8, 0.4, 0.8, 0.05);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                center.getX() + 0.5, center.getY() + 0.65, center.getZ() + 0.5,
                30, 0.8, 0.52, 0.8, 0.04);
        NatureVfx.growthRing(world, center, 1, now);
        world.playSound(null, center, SoundEvents.ITEM_BONE_MEAL_USE,
                SoundCategory.PLAYERS, 1.0f, 1.1f);
    }

    private static boolean tickBloom(ServerWorld world, Bloom b, int now) {
        int age = now - b.startTick;
        if (!b.source.active || age >= BLOOM_LIFESPAN) {
            SeedlingManager.restoreBlocks(world, b.placements);
            Vec3d center = Vec3d.ofCenter(b.center);
            world.spawnParticles(ModParticles.NATURE_LEAF,
                    center.x, center.y + 0.4, center.z,
                    18, b.currentRadius * 0.5, 0.24, b.currentRadius * 0.5, 0.025);
            return true;
        }

        if (b.currentRadius < BLOOM_MAX_RADIUS && now >= b.lastGrowthTick + BLOOM_GROWTH_INTERVAL) {
            b.currentRadius++;
            b.lastGrowthTick = now;

            List<BlockPos> ringCols = SeedlingManager.chebyshevRingColumns(b.center, b.currentRadius);
            int remainingLife = Math.max(20, BLOOM_LIFESPAN - age);
            SeedlingManager.PlacementResult result = SeedlingManager.placeVerdantGrowth(
                    world, ringCols.stream().filter(p -> !b.crushed.contains(p.up())).toList(), b.placedPositions, remainingLife);
            b.placedPositions.addAll(result.placed());
            for (Map.Entry<BlockPos, TemporaryBlockManager.TemporaryPlacement> e : result.placements().entrySet()) {
                b.placements.putIfAbsent(e.getKey(), e.getValue());
            }

            NatureVfx.growthRing(world, b.center, b.currentRadius, now);
            world.playSound(null, b.center, SoundEvents.BLOCK_FLOWERING_AZALEA_BREAK,
                    SoundCategory.PLAYERS, 0.38f, 1.25f + b.currentRadius * 0.05f);
        }

        if ((now + b.center.getX() * 3 + b.center.getZ() * 5)
                % BLOOM_AMBIENT_INTERVAL == 0) {
            Vec3d center = Vec3d.ofCenter(b.center);
            double radius = Math.max(1.0, b.currentRadius - 0.15);
            NatureVfx.ring(world, ModParticles.NATURE_VINE, center,
                    radius, 6, -0.36, now * 0.025);
            NatureVfx.ring(world, ModParticles.NATURE_LEAF, center,
                    radius * 0.78, 4, -0.24, now * -0.035);
            world.spawnParticles(ModParticles.NATURE_POLLEN,
                    center.x, center.y + 0.12, center.z,
                    5, radius * 0.45, 0.18, radius * 0.45, 0.008);
        }

        applyBloomEffects(world, b, now);
        return false;
    }

    public static void crushGrowth(ServerWorld world, java.util.function.Predicate<BlockPos> hit) {
        for (Source source : SOURCES.getOrDefault(world.getRegistryKey(), List.of())) {
            if (hit.test(source.anchor)) endSource(world, source);
        }
        List<Bloom> blooms = BLOOMS.get(world.getRegistryKey());
        if (blooms != null) blooms.removeIf(b -> {
            if (hit.test(b.center.up())) {
                SeedlingManager.restoreBlocks(world, b.placements);
                return true;
            }
            SeedlingManager.crushPositions(world, b.placedPositions, b.placements, b.crushed, hit);
            return false;
        });
        List<Tendril> tendrils = TENDRILS.get(world.getRegistryKey());
        if (tendrils != null) tendrils.removeIf(t -> {
            if (!hit.test(BlockPos.ofFloored(t.currentHead))) return false;
            SeedlingManager.restoreBlocks(world, t.placements);
            return true;
        });
    }

    private static void endSource(ServerWorld world, Source source) {
        if (!source.active) return;
        source.active = false;
        SeedlingManager.restoreBlocks(world, source.trails);
        for (Bloom bloom : BLOOMS.getOrDefault(world.getRegistryKey(), List.of())) {
            if (bloom.source == source) SeedlingManager.restoreBlocks(world, bloom.placements);
        }
        if (source.knot != null) {
            TemporaryBlockManager.restoreTemporaryBlocks(world, source.knot);
            world.spawnParticles(ModParticles.NATURE_LEAF, source.anchor.getX()+.5, source.anchor.getY()+.5,
                    source.anchor.getZ()+.5, 20, .4, .3, .4, .04);
            world.playSound(null, source.anchor, SoundEvents.BLOCK_ROOTED_DIRT_BREAK, SoundCategory.PLAYERS, .8f, .7f);
        }
    }

    public static void invalidateSeedling(ServerWorld world, UUID seedlingId) {
        for (Source source : SOURCES.getOrDefault(world.getRegistryKey(), List.of()))
            if (seedlingId.equals(source.seedling)) endSource(world, source);
    }

    public static boolean hasKnot(ServerWorld world, BlockPos pos) {
        return SOURCES.getOrDefault(world.getRegistryKey(), List.of()).stream()
                .anyMatch(source -> source.active && source.knot != null && source.anchor.equals(pos));
    }

    public static BlockPos findKnotPosition(ServerWorld world, PlayerEntity caster) {
        BlockPos feet = caster.getBlockPos();
        // Prefer the casting position, then its immediate neighbours. Never overwrite plants/terrain.
        for (int radius = 0; radius <= 1; radius++) for (int dx=-radius; dx<=radius; dx++) for (int dz=-radius; dz<=radius; dz++) {
            for (int dy=0; dy>=-2; dy--) {
                BlockPos pos = feet.add(dx, dy, dz);
                if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos) || !world.isChunkLoaded(pos)) continue;
                if (!world.getBlockState(pos).isAir()) continue;
                if (world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), net.minecraft.util.math.Direction.UP)) return pos;
            }
        }
        return null;
    }

    public static List<LivingEntity> findTargets(ServerWorld world, PlayerEntity caster, Vec3d origin) {
        return world.getEntitiesByClass(LivingEntity.class, new Box(origin, origin).expand(15),
                e -> e.isAlive() && !e.isSpectator() && !WandAllies.protectedFrom(caster, e)
                        && !(e instanceof com.anton.elementalwands.entity.AwakenedTreeEntity)
                        && !(e instanceof net.minecraft.entity.passive.PassiveEntity)
                        && !(e instanceof net.minecraft.entity.decoration.ArmorStandEntity)
                        && e.getEntityPos().squaredDistanceTo(origin) <= 225)
                .stream().sorted(java.util.Comparator.comparingDouble(e -> e.getEntityPos().squaredDistanceTo(origin))).toList();
    }

    public static boolean startKnot(ServerWorld world, PlayerEntity caster, BlockPos pos) {
        var placement = TemporaryBlockManager.placeTrackedTemporaryBlocks(world, List.of(pos),
                com.anton.elementalwands.registry.ModSpellBlocks.NATURE_ROOT_KNOT.getDefaultState(), SOURCE_LIFETIME,
                state -> state.isAir(), caster.getUuid());
        if (placement.isEmpty()) return false;
        // At most one live knot per caster, including commands or later cooldown tuning.
        for (Source old : SOURCES.getOrDefault(world.getRegistryKey(), List.of()))
            if (old.knot != null && old.caster.equals(caster.getUuid())) endSource(world, old);
        var source = new Source(caster.getUuid(), null, pos, world.getServer().getTicks() + SOURCE_LIFETIME, placement);
        SOURCES.computeIfAbsent(world.getRegistryKey(), k -> new ArrayList<>()).add(source);
        Vec3d origin = Vec3d.ofCenter(pos);
        var targets = findTargets(world, caster, origin);
        NatureVfx.pairedTendril(world, caster.getEntityPos().add(0, 1, 0), origin, 0);
        for (int i=0; i<3; i++) {
            double angle = Math.toRadians(i * 120);
            Vec3d branch = origin.add(Math.cos(angle)*.3, .15, Math.sin(angle)*.3);
            LivingEntity target = targets.isEmpty() ? null : targets.get(i % targets.size());
            Vec3d destination = target == null ? origin.add(Vec3d.fromPolar(0, caster.getYaw() + (i-1)*35).multiply(7))
                    : target.getEntityPos().add((i-targets.indexOf(target))*.12, target.getHeight()*.5, 0);
            launch(world, source, branch, target, destination);
        }
        return true;
    }

    /** Basic input can attack a nearby knot despite the wand intercepting vanilla block mining. */
    public static boolean tryBreakKnotAimed(net.minecraft.server.network.ServerPlayerEntity player) {
        var hit = player.raycast(3.5, 1, false);
        if (!(hit instanceof net.minecraft.util.hit.BlockHitResult block)
                || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return false;
        var world = player.getEntityWorld();
        for (Source source : SOURCES.getOrDefault(world.getRegistryKey(), List.of())) {
            if (!source.active || source.knot == null || !source.anchor.equals(block.getBlockPos())) continue;
            if (WandAllies.protectedFrom(world, source.caster, player)) return false;
            endSource(world, source); return true;
        }
        return false;
    }

    // Compact observation hooks for the disposable real-server regression fixture.
    public static int activeTendrils(ServerWorld world, UUID caster) {
        return (int)TENDRILS.getOrDefault(world.getRegistryKey(), List.of()).stream()
                .filter(t -> t.source.active && t.casterUuid.equals(caster)).count();
    }
    public static int activeBlooms(ServerWorld world, UUID caster) {
        return (int)BLOOMS.getOrDefault(world.getRegistryKey(), List.of()).stream()
                .filter(b -> b.source.active && b.casterUuid.equals(caster)).count();
    }

    private static void applyBloomEffects(ServerWorld world, Bloom b, int now) {
        // Broken/replaced cells must stop hurting entities, even before source expiry.
        b.placedPositions.removeIf(pos -> !com.anton.elementalwands.registry.ModSpellBlocks.isNatureGrowth(world.getBlockState(pos)));
        if (b.placedPositions.isEmpty() || !b.source.active) return;

        int r = BLOOM_MAX_RADIUS + 1;
        Box box = new Box(
                b.center.getX() - r, b.center.getY() - 0.5, b.center.getZ() - r,
                b.center.getX() + r + 1, b.center.getY() + 3.5, b.center.getZ() + r + 1);

        List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator());

        for (LivingEntity e : entities) {
            BlockPos feet = e.getBlockPos();
            boolean inZone = b.placedPositions.contains(feet) || b.placedPositions.contains(feet.down());
            if (!inZone) continue;

            if (WandAllies.protectedFrom(world, b.casterUuid, e)) continue;

            EntangleTracker.applyNatureSlow(e, 40, 3);

            SeedlingManager.applyThorns(world, e, b.casterUuid);
        }
    }
}
