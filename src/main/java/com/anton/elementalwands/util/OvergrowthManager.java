package com.anton.elementalwands.util;

import com.anton.elementalwands.party.WandAllies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.anton.elementalwands.entity.AwakenedTreeEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.util.TemporaryBlockManager.TemporaryPlacement;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
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
 * Nature ultimate manager. A thrown acorn grows a staged Fairy Heart Tree whose roots crush the
 * ten-block circle once and whose flowering canopy heals the caster's side until it expires.
 * The tree is scenery: its blocks have no collision and its heart cannot be attacked, so it never
 * blocks movement, shots or a boss, and nothing in an encounter needs a rule for it.
 */
public final class OvergrowthManager {

    private static final int TREE_DURATION_TICKS = 300;
    private static final double TREE_RADIUS = 10.0;
    private static final double TREE_RADIUS_SQ = TREE_RADIUS * TREE_RADIUS;
    private static final double TREE_Y_BELOW = 4.0;
    private static final double TREE_Y_ABOVE = 8.0;
    private static final float ROOT_CRUSH_DAMAGE = 6.0f;
    private static final int ROOT_CRUSH_SLOW_TICKS = 30;
    private static final int ROOT_CRUSH_SLOW_AMPLIFIER = 6;

    private static final int HEART_WAKE_TICK = 12;
    private static final int TRUNK_STAGE_TICK = 7;
    private static final int HEART_STAGE_TICK = 14;
    private static final int CANOPY_STAGE_TICK = 22;
    private static final int TREE_AMBIENT_INTERVAL = 6;

    private static final class AwakenedTree {
        final int entityId;
        final UUID casterUuid;
        final BlockPos center;
        final int startTick;
        final int expiryTick;
        final List<TemporaryPlacement> blockPlacements = new ArrayList<>();
        int builtStage = -1;

        AwakenedTree(int entityId, UUID casterUuid, BlockPos center, int startTick, int expiryTick) {
            this.entityId = entityId;
            this.casterUuid = casterUuid;
            this.center = center;
            this.startTick = startTick;
            this.expiryTick = expiryTick;
        }
    }

    private static final Map<RegistryKey<World>, List<AwakenedTree>> ACTIVE = new HashMap<>();

    private OvergrowthManager() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(OvergrowthManager::tickWorld);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE.clear());
    }

    /** Grows a tree at a known anchor; commands and fixtures use this instead of the throw. */
    public static void startOvergrowth(ServerWorld world, PlayerEntity caster, BlockPos anchor) {
        startOvergrowthInternal(world, caster, anchor, TREE_DURATION_TICKS);
    }

    public static boolean startThrownOvergrowth(ServerWorld world, PlayerEntity caster, BlockPos anchor) {
        if (!OvergrowthThrowRules.validGround(world, anchor)) return false;
        startOvergrowthInternal(world, caster, anchor, TREE_DURATION_TICKS);
        return true;
    }

    public static int remainingTicks(ServerWorld world, UUID caster, BlockPos center) {
        return ACTIVE.getOrDefault(world.getRegistryKey(), List.of()).stream()
                .filter(tree -> tree.casterUuid.equals(caster) && tree.center.equals(center))
                .mapToInt(tree -> Math.max(0, tree.expiryTick - world.getServer().getTicks())).max().orElse(0);
    }

    private static void startOvergrowthInternal(ServerWorld world, PlayerEntity caster,
            BlockPos anchor, int duration) {
        int now = world.getServer().getTicks();
        BlockPos center = anchor.toImmutable();

        AwakenedTreeEntity treeEntity = new AwakenedTreeEntity(ModEntities.AWAKENED_TREE, world);
        treeEntity.initialize(caster.getUuid());
        treeEntity.refreshPositionAndAngles(center.getX() + 0.5, center.getY(), center.getZ() + 0.5,
                caster.getYaw(), 0.0f);
        world.spawnEntity(treeEntity);

        AwakenedTree tree = new AwakenedTree(treeEntity.getId(), caster.getUuid(), center,
                now, now + duration);
        ACTIVE.computeIfAbsent(world.getRegistryKey(), _key -> new ArrayList<>()).add(tree);

        advanceTreeGrowth(world, tree, 0, now);
        applyRootCrush(world, caster, center, ROOT_CRUSH_DAMAGE);
        spawnRootAwakening(world, center);

        world.playSound(null, center, SoundEvents.BLOCK_AZALEA_PLACE,
                SoundCategory.PLAYERS, 1.4f, 0.55f);
        world.playSound(null, center, SoundEvents.ENTITY_RAVAGER_ROAR,
                SoundCategory.PLAYERS, 0.9f, 0.75f);
    }

    public static void destroyTree(ServerWorld world, AwakenedTreeEntity treeEntity) {
        List<AwakenedTree> trees = ACTIVE.get(world.getRegistryKey());
        if (trees == null || trees.isEmpty()) return;

        Iterator<AwakenedTree> iterator = trees.iterator();
        while (iterator.hasNext()) {
            AwakenedTree tree = iterator.next();
            if (tree.entityId != treeEntity.getId()) continue;

            cleanupTree(world, tree, treeEntity, true);
            iterator.remove();
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
        Iterator<AwakenedTree> iterator = trees.iterator();
        while (iterator.hasNext()) {
            AwakenedTree tree = iterator.next();
            AwakenedTreeEntity entity = world.getEntityById(tree.entityId) instanceof AwakenedTreeEntity awakened
                    ? awakened
                    : null;

            if (entity == null || entity.isRemoved() || !entity.isAlive()) {
                cleanupTree(world, tree, entity, true);
                iterator.remove();
                continue;
            }
            if (now >= tree.expiryTick) {
                cleanupTree(world, tree, entity, false);
                iterator.remove();
                continue;
            }

            int age = now - tree.startTick;
            if (age == HEART_WAKE_TICK) wakeHeart(world, tree);

            int targetStage = age >= CANOPY_STAGE_TICK ? 3
                    : age >= HEART_STAGE_TICK ? 2
                    : age >= TRUNK_STAGE_TICK ? 1 : 0;
            advanceTreeGrowth(world, tree, targetStage, now);

            tickHealingBeacon(world, tree, now);
            if (now % TREE_AMBIENT_INTERVAL == 0) {
                spawnAmbientTreeParticles(world, tree, entity, now);
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

    private static void applyRootCrush(ServerWorld world, PlayerEntity caster,
            BlockPos center, float damage) {
        Vec3d centerVec = Vec3d.ofCenter(center);
        Box box = new Box(
                centerVec.x - TREE_RADIUS, centerVec.y - TREE_Y_BELOW, centerVec.z - TREE_RADIUS,
                centerVec.x + TREE_RADIUS, centerVec.y + TREE_Y_ABOVE, centerVec.z + TREE_RADIUS);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                entity -> entity.isAlive() && !entity.isSpectator()
                        && !WandAllies.protectedFrom(caster, entity)
                        && !(entity instanceof AwakenedTreeEntity));

        for (LivingEntity target : targets) {
            double dx = target.getX() - centerVec.x;
            double dz = target.getZ() - centerVec.z;
            if (dx * dx + dz * dz > TREE_RADIUS_SQ) continue;

            boolean damaged = com.anton.elementalwands.util.SpellCombat.damage(target, world, world.getDamageSources().playerAttack(caster), damage, caster, com.anton.elementalwands.data.WizardAffinity.NATURE);
            if (damaged) {
                AbstractWandItem.onWandDamageDealt(caster, damage, 0, com.anton.elementalwands.data.WizardAffinity.NATURE);
            }

            // The roots pin ordinary enemies for a moment. A boss takes the same damage and is not held.
            EntangleTracker.applyNatureSlow(target, ROOT_CRUSH_SLOW_TICKS, ROOT_CRUSH_SLOW_AMPLIFIER);
            if (!(target instanceof com.anton.elementalwands.entity.WandBoss)) {
                EntangleTracker.syncUltimateRoot(world,target,ROOT_CRUSH_SLOW_TICKS);
                target.addVelocity(0.0, -0.65, 0.0);
                target.velocityModified = true;
                target.fallDistance = 0.0f;
            }

            world.spawnParticles(ModParticles.NATURE_VINE,
                    target.getX(), target.getY() + 0.16, target.getZ(),
                    16, 0.48, 0.08, 0.48, 0.016);
            world.spawnParticles(ModParticles.NATURE_BLOOM,
                    target.getX(), target.getY() + 0.2, target.getZ(),
                    3, 0.34, 0.05, 0.34, 0.0);
            world.spawnParticles(ModParticles.NATURE_POLLEN,
                    target.getX(), target.getBodyY(0.45), target.getZ(),
                    12, 0.35, 0.4, 0.35, 0.02);
        }
    }

    private static void tickHealingBeacon(ServerWorld world, AwakenedTree tree, int now) {
        PlayerEntity caster = world.getPlayerByUuid(tree.casterUuid);
        if (caster == null || !caster.isAlive()) return;
        for(PlayerEntity ally:world.getPlayers()) {
            if(ally.isAlive() && !ally.isSpectator() && (ally==caster || com.anton.elementalwands.party.WandAllies.protectedFrom(caster,ally))
                    && containsPos(tree,ally.getX(),ally.getY()+.1,ally.getZ()))
                SpellBuffs.regeneration(ally,caster,com.anton.elementalwands.data.WizardAffinity.NATURE,1);
        }
        if (!containsPos(tree, caster.getX(), caster.getY() + 0.1, caster.getZ())) return;
        if (now % 20 == 0) {
            world.spawnParticles(ModParticles.NATURE_HEART,
                    caster.getX(), caster.getBodyY(0.52), caster.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(ModParticles.NATURE_POLLEN,
                    caster.getX(), caster.getBodyY(0.5), caster.getZ(),
                    8, 0.35, 0.45, 0.35, 0.02);
        }
    }

    private static void wakeHeart(ServerWorld world, AwakenedTree tree) {
        Vec3d heart = Vec3d.ofCenter(tree.center).add(0.0, 2.0, 0.0);
        world.spawnParticles(ModParticles.NATURE_HEART,
                heart.x, heart.y, heart.z, 9, 0.42, 0.5, 0.42, 0.035);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                heart.x, heart.y, heart.z, 28, 0.7, 0.9, 0.7, 0.045);
        world.playSound(null, tree.center, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                SoundCategory.PLAYERS, 0.72f, 1.62f);
    }

    private static void advanceTreeGrowth(ServerWorld world, AwakenedTree tree,
            int targetStage, int now) {
        while (tree.builtStage < targetStage) {
            tree.builtStage++;
            int remainingLife = Math.max(20, tree.expiryTick - now);
            tree.blockPlacements.addAll(placeTreeStage(world, tree.center,
                    tree.builtStage, remainingLife));
            spawnGrowthStageEffects(world, tree.center, tree.builtStage);
        }
    }

    private static List<TemporaryPlacement> placeTreeStage(ServerWorld world,
            BlockPos center, int stage, int durationTicks) {
        List<TemporaryPlacement> placements = new ArrayList<>();
        Map<BlockState,List<BlockPos>> groups = new java.util.LinkedHashMap<>();
        for (NatureTreeLayout.Cell cell : NatureTreeLayout.cells()) {
            if (cell.growthStage() != stage) continue;
            BlockPos pos = center.add(cell.x(),cell.y(),cell.z());
            if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos) || !world.isChunkLoaded(pos)) continue;
            // The trunk is solid: never grow it through someone standing there (it would lift or
            // trap them). That piece of trunk is simply missing. Leaves have no collision.
            if (!cell.state().getCollisionShape(world, pos).isEmpty()
                    && !world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, new net.minecraft.util.math.Box(pos),
                            e -> e.isAlive() && !(e instanceof AwakenedTreeEntity) && !e.isSpectator()).isEmpty()) continue;
            groups.computeIfAbsent(cell.state(), ignored -> new ArrayList<>()).add(pos);
        }
        for (var group : groups.entrySet()) addPlacement(placements,
                TemporaryBlockManager.placeTrackedTemporaryBlocks(world,group.getValue(),group.getKey(),
                        durationTicks,OvergrowthManager::canReplaceTreeBlock));
        return placements;
    }

    private static void addPlacement(List<TemporaryPlacement> placements,
            TemporaryPlacement placement) {
        if (placement != null && !placement.isEmpty()) {
            placements.add(placement);
        }
    }

    private static boolean canReplaceTreeBlock(BlockState state) {
        if (state.isOf(com.anton.elementalwands.registry.ModSpellBlocks.NATURE_SEEDLING)
                || state.isOf(com.anton.elementalwands.registry.ModSpellBlocks.NATURE_ROOT_KNOT)) return false;
        return state.isAir() || state.isReplaceable()
                || com.anton.elementalwands.registry.ModSpellBlocks.isNatureGrowth(state);
    }

    private static void spawnRootAwakening(ServerWorld world, BlockPos center) {
        Vec3d c = Vec3d.ofCenter(center);
        NatureVfx.rootSpokes(world, center, TREE_RADIUS);
        world.spawnParticles(ModParticles.NATURE_LEAF,
                c.x, c.y + 0.45, c.z, 36, 1.8, 0.55, 1.8, 0.055);
        world.spawnParticles(ModParticles.NATURE_POLLEN,
                c.x, c.y + 0.72, c.z,
                30, 2.2, 0.9, 2.2, 0.05);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK,
                        Blocks.OAK_LOG.getDefaultState()),
                c.x, c.y + 0.2, c.z, 48, 1.6, 0.28, 1.6, 0.16);
    }

    private static void spawnGrowthStageEffects(ServerWorld world, BlockPos center, int stage) {
        Vec3d c = Vec3d.ofCenter(center);
        double y = switch (stage) {
            case 0 -> 0.35;
            case 1 -> 2.25;
            case 2 -> 4.6;
            default -> 7.2;
        };
        int leaves = switch (stage) {
            case 0 -> 16;
            case 1 -> 24;
            case 2 -> 34;
            default -> 64;
        };
        world.spawnParticles(ModParticles.NATURE_LEAF,
                c.x, c.y + y, c.z, leaves,
                0.65 + stage * 0.55, 0.55 + stage * 0.35,
                0.65 + stage * 0.55, 0.045);
        world.spawnParticles(stage >= 2 ? ModParticles.NATURE_PETAL : ModParticles.NATURE_POLLEN,
                c.x, c.y + y + 0.45, c.z, 14 + stage * 11,
                0.6 + stage * 0.62, 0.55 + stage * 0.38,
                0.6 + stage * 0.62, 0.035);
        if (stage >= 2) {
            world.spawnParticles(ModParticles.NATURE_HEART,
                    c.x, c.y + 2.0, c.z, stage == 2 ? 4 : 2,
                    0.22, 0.3, 0.22, 0.0);
        }
        if (stage == 3) {
            NatureVfx.ring(world, ModParticles.NATURE_BLOOM,
                    c.add(0.0, 6.8, 0.0), 3.8, 16, 0.0, Math.PI / 16.0);
        }
        world.playSound(null, center,
                stage == 3 ? SoundEvents.BLOCK_AZALEA_LEAVES_BREAK
                        : SoundEvents.BLOCK_AZALEA_PLACE,
                SoundCategory.PLAYERS, 0.72f + stage * 0.14f, 0.72f + stage * 0.12f);
    }

    private static void spawnAmbientTreeParticles(ServerWorld world, AwakenedTree tree,
            AwakenedTreeEntity entity, int now) {
        Vec3d c = Vec3d.ofCenter(tree.center);
        float healthRatio = entity.getHealth() / entity.getMaxHealth();
        Vec3d heart = c.add(0.0, 2.0, 0.0);
        NatureVfx.treeHeart(world, heart, now, healthRatio);

        if (tree.builtStage >= 3) {
            NatureVfx.floweringCanopy(world, c, now, healthRatio);
        }

        int orbiters = healthRatio > 0.34f ? 8 : 5;
        double angle = now * (healthRatio > 0.34f ? 0.15 : 0.09);
        for (int i = 0; i < orbiters; i++) {
            double a = angle + i * (Math.PI * 2.0 / orbiters);
            double radius = 1.35 + (i % 3) * 0.52;
            double x = c.x + Math.cos(a) * radius;
            double z = c.z + Math.sin(a) * radius;
            double y = c.y + 1.0 + (i % 4) * 0.92;
            world.spawnParticles(i % 3 == 0 ? ModParticles.NATURE_BLOOM : ModParticles.NATURE_POLLEN,
                    x, y, z, 1, 0.04, 0.04, 0.04, 0.0);
        }
    }

    private static void cleanupTree(ServerWorld world, AwakenedTree tree,
            AwakenedTreeEntity entity, boolean destroyed) {
        for (int i = tree.blockPlacements.size() - 1; i >= 0; i--) {
            TemporaryBlockManager.restoreTemporaryBlocks(world, tree.blockPlacements.get(i));
        }

        Vec3d c = Vec3d.ofCenter(tree.center);
        if (destroyed) {
            world.spawnParticles(ModParticles.NATURE_HEART,
                    c.x, c.y + 2.0, c.z, 22, 1.2, 1.5, 1.2, 0.085);
            world.spawnParticles(ModParticles.NATURE_PETAL,
                    c.x, c.y + 6.2, c.z, 95, 3.3, 2.4, 3.3, 0.12);
            world.spawnParticles(ModParticles.NATURE_LEAF,
                    c.x, c.y + 5.0, c.z, 80, 2.8, 2.5, 2.8, 0.14);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK,
                            Blocks.OAK_LOG.getDefaultState()),
                    c.x, c.y + 3.0, c.z, 80, 1.9, 2.2, 1.9, 0.16);
            NatureVfx.ring(world, ModParticles.NATURE_BLOOM,
                    c, 5.6, 24, 0.05, 0.0);
            world.playSound(null, tree.center, SoundEvents.BLOCK_WOOD_BREAK,
                    SoundCategory.PLAYERS, 1.25f, 0.66f);
        } else {
            // Natural expiry lifts the tree away as a calm helix of petals and pollen.
            for (int layer = 0; layer < 8; layer++) {
                double radius = Math.max(0.35, 3.5 - layer * 0.38);
                NatureVfx.ring(world,
                        layer % 2 == 0 ? ModParticles.NATURE_PETAL : ModParticles.NATURE_POLLEN,
                        c, radius, 8, 0.6 + layer * 0.86, layer * 0.58);
            }
            world.spawnParticles(ModParticles.NATURE_HEART,
                    c.x, c.y + 6.8, c.z, 5, 0.32, 0.55, 0.32, 0.025);
            world.playSound(null, tree.center, SoundEvents.BLOCK_GRASS_BREAK,
                    SoundCategory.PLAYERS, 0.95f, 1.18f);
        }

        if (entity != null && !entity.isRemoved()) {
            entity.discard();
        }
    }
}
