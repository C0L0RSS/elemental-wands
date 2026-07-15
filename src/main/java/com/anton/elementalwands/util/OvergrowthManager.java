package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.anton.elementalwands.entity.AwakenedTreeEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.TemporaryBlockManager.TemporaryPlacement;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Nature ultimate manager. The public name is kept so existing seedling amplification hooks can
 * continue to ask whether a position is inside the active Nature ultimate.
 */
public final class OvergrowthManager {

    private static final int TREE_DURATION_TICKS = 300;
    private static final double TREE_RADIUS = 10.0;
    private static final double TREE_RADIUS_SQ = TREE_RADIUS * TREE_RADIUS;
    private static final double TREE_Y_BELOW = 4.0;
    private static final double TREE_Y_ABOVE = 8.0;
    private static final float BASE_CRUSH_DAMAGE = 14.0f;
    private static final float MAX_CRUSH_DAMAGE = 18.0f;
    private static final int ROOT_CRUSH_SLOW_TICKS = 30;
    private static final int ROOT_CRUSH_SLOW_AMPLIFIER = 6;

    private static final class AwakenedTree {
        final int entityId;
        final UUID casterUuid;
        final BlockPos center;
        final int expiryTick;
        final List<TemporaryPlacement> blockPlacements;

        AwakenedTree(int entityId, UUID casterUuid, BlockPos center, int expiryTick,
                List<TemporaryPlacement> blockPlacements) {
            this.entityId = entityId;
            this.casterUuid = casterUuid;
            this.center = center;
            this.expiryTick = expiryTick;
            this.blockPlacements = List.copyOf(blockPlacements);
        }
    }

    private static final Map<RegistryKey<World>, List<AwakenedTree>> ACTIVE = new HashMap<>();

    private OvergrowthManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(OvergrowthManager::tickWorld);
    }

    public static void startOvergrowth(ServerWorld world, PlayerEntity caster, BlockPos seedlingPos,
            int consumedSeedlings) {
        int now = world.getServer().getTicks();
        BlockPos center = seedlingPos.toImmutable();

        AwakenedTreeEntity tree = new AwakenedTreeEntity(ModEntities.AWAKENED_TREE, world);
        tree.initialize(caster.getUuid());
        tree.refreshPositionAndAngles(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, caster.getYaw(), 0.0f);
        world.spawnEntity(tree);

        List<TemporaryPlacement> blockPlacements = placeTreeBlocks(world, center);
        ACTIVE.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>())
                .add(new AwakenedTree(tree.getId(), caster.getUuid(), center, now + TREE_DURATION_TICKS,
                        blockPlacements));

        float damage = Math.min(MAX_CRUSH_DAMAGE, BASE_CRUSH_DAMAGE + Math.max(0, consumedSeedlings - 1));
        applyRootCrush(world, caster, center, damage);
        spawnTreeBirthEffects(world, center);

        world.playSound(null, center, SoundEvents.BLOCK_AZALEA_PLACE, SoundCategory.PLAYERS, 1.4f, 0.55f);
        world.playSound(null, center, SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.9f, 0.75f);
    }

    public static boolean isInOvergrowth(ServerWorld world, BlockPos pos) {
        List<AwakenedTree> trees = ACTIVE.get(world.getRegistryKey());
        if (trees == null || trees.isEmpty()) return false;

        for (AwakenedTree tree : trees) {
            if (containsPos(tree, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                return true;
            }
        }
        return false;
    }

    public static void onTreeDamaged(ServerWorld world, AwakenedTreeEntity treeEntity) {
        Vec3d pos = treeEntity.getEntityPos().add(0.0, treeEntity.getHeight() * 0.5, 0.0);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_LOG.getDefaultState()),
                pos.x, pos.y, pos.z, 18, 0.9, 1.2, 0.9, 0.08);
        world.playSound(null, treeEntity.getBlockPos(), SoundEvents.BLOCK_WOOD_HIT, SoundCategory.PLAYERS, 1.0f, 0.75f);
    }

    public static void destroyTree(ServerWorld world, AwakenedTreeEntity treeEntity) {
        List<AwakenedTree> trees = ACTIVE.get(world.getRegistryKey());
        if (trees == null || trees.isEmpty()) return;

        Iterator<AwakenedTree> it = trees.iterator();
        while (it.hasNext()) {
            AwakenedTree tree = it.next();
            if (tree.entityId != treeEntity.getId()) continue;

            cleanupTree(world, tree, treeEntity, true);
            it.remove();
            break;
        }

        if (trees.isEmpty()) {
            ACTIVE.remove(world.getRegistryKey());
        }
    }

    private static void tickWorld(ServerWorld world) {
        List<AwakenedTree> trees = ACTIVE.get(world.getRegistryKey());
        if (trees == null || trees.isEmpty()) return;

        int now = world.getServer().getTicks();
        Iterator<AwakenedTree> it = trees.iterator();
        while (it.hasNext()) {
            AwakenedTree tree = it.next();
            AwakenedTreeEntity entity = world.getEntityById(tree.entityId) instanceof AwakenedTreeEntity awakened
                    ? awakened
                    : null;

            if (entity == null || entity.isRemoved() || !entity.isAlive() || now >= tree.expiryTick) {
                cleanupTree(world, tree, entity, false);
                it.remove();
                continue;
            }

            tickHealingBeacon(world, tree, now);
            if (now % 5 == 0) {
                spawnAmbientTreeParticles(world, tree, now);
            }
        }

        if (trees.isEmpty()) {
            ACTIVE.remove(world.getRegistryKey());
        }
    }

    private static boolean containsPos(AwakenedTree tree, double x, double y, double z) {
        double dx = x - (tree.center.getX() + 0.5);
        double dz = z - (tree.center.getZ() + 0.5);
        if (dx * dx + dz * dz > TREE_RADIUS_SQ) return false;

        double dy = y - tree.center.getY();
        return dy >= -TREE_Y_BELOW && dy <= TREE_Y_ABOVE;
    }

    private static void applyRootCrush(ServerWorld world, PlayerEntity caster, BlockPos center, float damage) {
        Vec3d centerVec = Vec3d.ofCenter(center);
        Box box = new Box(
                centerVec.x - TREE_RADIUS, centerVec.y - TREE_Y_BELOW, centerVec.z - TREE_RADIUS,
                centerVec.x + TREE_RADIUS, centerVec.y + TREE_Y_ABOVE, centerVec.z + TREE_RADIUS);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(caster.getUuid())
                        && !(e instanceof AwakenedTreeEntity));

        for (LivingEntity target : targets) {
            double dx = target.getX() - centerVec.x;
            double dz = target.getZ() - centerVec.z;
            if (dx * dx + dz * dz > TREE_RADIUS_SQ) continue;

            boolean damaged = target.damage(world, world.getDamageSources().playerAttack(caster), damage);
            if (damaged) {
                AbstractWandItem.onWandDamageDealt(caster, damage);
            }

            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                    ROOT_CRUSH_SLOW_TICKS, ROOT_CRUSH_SLOW_AMPLIFIER, false, true, true));
            target.addVelocity(0.0, -0.65, 0.0);
            target.velocityModified = true;
            target.fallDistance = 0.0f;

            world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                    target.getX(), target.getY() + 0.2, target.getZ(), 18, 0.45, 0.08, 0.45, 0.02);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    target.getX(), target.getBodyY(0.45), target.getZ(), 12, 0.35, 0.4, 0.35, 0.02);
        }
    }

    private static void tickHealingBeacon(ServerWorld world, AwakenedTree tree, int now) {
        PlayerEntity caster = world.getPlayerByUuid(tree.casterUuid);
        if (caster == null || !caster.isAlive()) return;
        if (!containsPos(tree, caster.getX(), caster.getY() + 0.1, caster.getZ())) return;

        caster.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, false, false, true));
        if (now % 20 == 0) {
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    caster.getX(), caster.getBodyY(0.5), caster.getZ(), 8, 0.35, 0.45, 0.35, 0.02);
        }
    }

    private static List<TemporaryPlacement> placeTreeBlocks(ServerWorld world, BlockPos center) {
        List<TemporaryPlacement> placements = new ArrayList<>();

        Set<BlockPos> trunk = new HashSet<>();
        for (int y = 0; y <= 5; y++) {
            trunk.add(center.up(y));
        }
        trunk.add(center.north());
        trunk.add(center.south());
        trunk.add(center.east());
        trunk.add(center.west());
        placements.add(TemporaryBlockManager.placeTrackedTemporaryBlocks(world, trunk,
                Blocks.OAK_LOG.getDefaultState(), TREE_DURATION_TICKS, OvergrowthManager::canReplaceTreeBlock));

        Set<BlockPos> leaves = new HashSet<>();
        for (int y = 3; y <= 7; y++) {
            int radius = y == 7 ? 1 : (y <= 4 ? 2 : 3);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > radius + 1) continue;
                    if (dx == 0 && dz == 0 && y <= 5) continue;
                    leaves.add(center.add(dx, y, dz));
                }
            }
        }
        placements.add(TemporaryBlockManager.placeTrackedTemporaryBlocks(world, leaves,
                Blocks.OAK_LEAVES.getDefaultState(), TREE_DURATION_TICKS, OvergrowthManager::canReplaceTreeBlock));

        Set<BlockPos> moss = new HashSet<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz > 16) continue;
                BlockPos p = center.add(dx, 0, dz);
                BlockState below = world.getBlockState(p.down());
                BlockState at = world.getBlockState(p);
                if ((at.isAir() || at.isReplaceable()) && below.isSolidBlock(world, p.down())) {
                    moss.add(p);
                }
            }
        }
        placements.add(TemporaryBlockManager.placeTrackedTemporaryBlocks(world, moss,
                Blocks.MOSS_CARPET.getDefaultState(), TREE_DURATION_TICKS, state -> state.isAir() || state.isReplaceable()));

        return placements;
    }

    private static boolean canReplaceTreeBlock(BlockState state) {
        return state.isAir()
                || state.isReplaceable()
                || state.isOf(Blocks.MOSS_BLOCK)
                || state.isOf(Blocks.MOSS_CARPET)
                || state.isOf(Blocks.OAK_LOG)
                || state.isOf(Blocks.OAK_LEAVES);
    }

    private static void spawnTreeBirthEffects(ServerWorld world, BlockPos center) {
        Vec3d c = Vec3d.ofCenter(center);
        world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 1.5, c.z, 80, 2.5, 1.6, 2.5, 0.05);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 2.5, c.z, 60, 2.0, 2.4, 2.0, 0.08);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_LOG.getDefaultState()),
                c.x, c.y + 0.5, c.z, 60, 1.5, 0.8, 1.5, 0.18);
    }

    private static void spawnAmbientTreeParticles(ServerWorld world, AwakenedTree tree, int now) {
        Vec3d c = Vec3d.ofCenter(tree.center);
        double angle = now * 0.18;
        for (int i = 0; i < 8; i++) {
            double a = angle + i * (Math.PI * 2.0 / 8.0);
            double r = 1.4 + (i % 3) * 0.5;
            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            double y = c.y + 1.0 + (i % 4) * 0.9;
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private static void cleanupTree(ServerWorld world, AwakenedTree tree, AwakenedTreeEntity entity, boolean destroyed) {
        for (TemporaryPlacement placement : tree.blockPlacements) {
            TemporaryBlockManager.restoreTemporaryBlocks(world, placement);
        }

        Vec3d c = Vec3d.ofCenter(tree.center);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.getDefaultState()),
                c.x, c.y + 3.0, c.z, destroyed ? 70 : 35, 2.0, 2.0, 2.0, 0.12);
        world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, c.x, c.y + 1.0, c.z, destroyed ? 35 : 18,
                1.2, 0.7, 1.2, 0.03);
        world.playSound(null, tree.center, destroyed ? SoundEvents.BLOCK_WOOD_BREAK : SoundEvents.BLOCK_GRASS_BREAK,
                SoundCategory.PLAYERS, 1.0f, destroyed ? 0.75f : 1.05f);

        if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
    }
}
