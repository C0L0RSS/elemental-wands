package com.anton.elementalwands.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.anton.elementalwands.block.StoneWallBlock;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.TemporaryBlockManager.TemporaryPlacement;
import com.anton.elementalwands.util.TitanDomeManager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class StoneAbilityHandler {

    private static final class WallData {
        private final BlockPos center;
        private final RegistryKey<World> worldKey;
        private final int creationTick;
        private final List<BlockPos> blockPositions;
        private final Vec3d forward;
        private final TemporaryPlacement placement;
        private boolean settled;
        private int nextStressFxTick;

        private WallData(BlockPos center, RegistryKey<World> worldKey, int tick,
                List<BlockPos> blockPositions, Vec3d forward, TemporaryPlacement placement) {
            this.center = center;
            this.worldKey = worldKey;
            this.creationTick = tick;
            this.blockPositions = List.copyOf(blockPositions);
            this.forward = forward;
            this.placement = placement;
            this.nextStressFxTick = tick + WALL_SETTLE_TICKS;
        }
    }

    private static final Map<UUID, WallData> ACTIVE_WALLS = new HashMap<>();

    private static final int WALL_DURATION = 70;
    private static final int WALL_SETTLE_TICKS = 6;
    private static final int WALL_STRESS_FX_INTERVAL = 8;
    private static final int WALL_GROUND_SEARCH_MAX = 16;

    private static final double SHATTER_TRIGGER_DISTANCE = 2.0;
    private static final double SHATTER_BLAST_DEPTH = 5.0;
    private static final double SHATTER_LATERAL_PADDING = 1.5;
    private static final double SHATTER_VERTICAL_PADDING = 1.0;
    private static final float SHATTER_DAMAGE = 7.0f;
    private static final double SHATTER_HORIZONTAL_KNOCKBACK = 1.2;
    private static final double SHATTER_VERTICAL_KNOCKBACK = 0.35;

    private StoneAbilityHandler() {
    }

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(StoneAbilityHandler::tickWalls);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE_WALLS.clear());
    }

    public static int getPrimaryCooldownTicks() {
        return 30;
    }

    public static int getSecondaryCooldownTicks() {
        return AbstractWandItem.DEFAULT_SECONDARY_COOLDOWN_TICKS;
    }

    public static void inventoryTick(ItemStack stack, ServerWorld world, Entity entity,
            net.minecraft.entity.EquipmentSlot slot) {
        if (world.isClient() || !(entity instanceof PlayerEntity player)
                || (slot != net.minecraft.entity.EquipmentSlot.MAINHAND
                        && slot != net.minecraft.entity.EquipmentSlot.OFFHAND)) {
            return;
        }

        WallData wall = ACTIVE_WALLS.get(player.getUuid());
        if (wall == null) return;

        if (!wall.worldKey.equals(world.getRegistryKey()) || !isWallVisible(world, wall)) return;

        double dist = player.getEntityPos().distanceTo(Vec3d.ofCenter(wall.center));
        if (dist <= 3.5) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.RESISTANCE, 20, 2, false, false, true));
        }
    }

    public static void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        com.anton.elementalwands.util.StoneClusterManager.cast(world, caster, stack);
    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        int now = world.getServer().getTicks();
        WallData existing = ACTIVE_WALLS.get(caster.getUuid());
        if (existing != null) {
            if (!existing.worldKey.equals(world.getRegistryKey())
                    || now - existing.creationTick >= WALL_DURATION
                    || !isWallVisible(world, existing)) {
                ACTIVE_WALLS.remove(caster.getUuid());
            } else {
                double nearest = nearestWallDistance(caster.getEntityPos(), existing.blockPositions);
                if (nearest <= SHATTER_TRIGGER_DISTANCE) {
                    executeShatter(world, caster, stack, existing);
                }
                return;
            }
        }

        if (!AbstractWandItem.tryStartCooldown(
                world, caster, stack, AbstractWandItem.Ability.SECONDARY, getSecondaryCooldownTicks())) {
            return;
        }

        Vec3d forward = horizontalForward(caster);
        int centerX = MathHelper.floor(caster.getX() + forward.x * 2.0);
        int centerZ = MathHelper.floor(caster.getZ() + forward.z * 2.0);
        int bottomY = resolveWallBottomY(
                world, centerX, centerZ, MathHelper.floor(caster.getY()), WALL_GROUND_SEARCH_MAX);
        BlockPos center = new BlockPos(centerX, bottomY + 1, centerZ);

        BlockPos wallStep = discreteWallRight(forward);
        List<BlockPos> requestedBlocks = new ArrayList<>();
        for (int lateral = -1; lateral <= 2; lateral++) {
            for (int vertical = -1; vertical <= 1; vertical++) {
                requestedBlocks.add(center.add(
                        wallStep.getX() * lateral, vertical, wallStep.getZ() * lateral));
            }
        }

        BlockState wallState = ModSpellBlocks.STONE_WALL.getDefaultState()
                .with(StoneWallBlock.SHATTER_READY, false);
        TemporaryPlacement placement = TemporaryBlockManager.placeTrackedTemporaryBlocks(
                world,
                requestedBlocks,
                wallState,
                WALL_DURATION,
                state -> (state.isAir() || state.isReplaceable()) && state.getFluidState().isEmpty());
        if (placement.isEmpty()) return;

        // The receipt excludes another caster's wall where requested cells overlap.
        List<BlockPos> placedBlocks = TemporaryBlockManager.placementPositions(world, placement);
        WallData wall = new WallData(center, world.getRegistryKey(), now,
                placedBlocks, forward, placement);
        ACTIVE_WALLS.put(caster.getUuid(), wall);

        Vec3d wallCenter = averageCenter(placedBlocks, center.toCenterPos());
        world.spawnParticles(ModParticles.STONE_SHOCKWAVE,
                wallCenter.x, bottomY + 0.25, wallCenter.z,
                2, 0.22, 0.04, 0.22, 0.0);
        for (BlockPos pos : placedBlocks) {
            world.spawnParticles(ModParticles.STONE_DUST,
                    pos.getX() + 0.5, pos.getY() + 0.18, pos.getZ() + 0.5,
                    5, 0.4, 0.12, 0.4, 0.07);
            world.spawnParticles(ModParticles.STONE_SHARD,
                    pos.getX() + 0.5, pos.getY() + 0.35, pos.getZ() + 0.5,
                    2, 0.28, 0.25, 0.28, 0.09);
        }
        world.playSound(null, center, SoundEvents.BLOCK_DEEPSLATE_BRICKS_PLACE,
                SoundCategory.PLAYERS, 1.25f, 0.64f);
        world.playSound(null, center, SoundEvents.ENTITY_IRON_GOLEM_REPAIR,
                SoundCategory.PLAYERS, 0.55f, 0.52f);
    }

    private static void executeShatter(ServerWorld world, PlayerEntity caster, ItemStack stack, WallData wall) {
        Vec3d forward = wall.forward;
        Vec3d right = new Vec3d(-forward.z, 0.0, forward.x);
        Vec3d wallCenter = averageCenter(wall.blockPositions, wall.center.toCenterPos());

        double halfRight = 0.0;
        double halfUp = 0.0;
        for (BlockPos pos : wall.blockPositions) {
            Vec3d rel = pos.toCenterPos().subtract(wallCenter);
            halfRight = Math.max(halfRight, Math.abs(rel.dotProduct(right)) + 0.5);
            halfUp = Math.max(halfUp, Math.abs(rel.y) + 0.5);
        }
        double blastHalfRight = halfRight + SHATTER_LATERAL_PADDING;
        double blastHalfUp = halfUp + SHATTER_VERTICAL_PADDING;

        Box searchBox = new Box(wallCenter, wallCenter)
                .expand(SHATTER_BLAST_DEPTH + blastHalfRight + 1.0,
                        blastHalfUp + 1.0,
                        SHATTER_BLAST_DEPTH + blastHalfRight + 1.0);
        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, searchBox,
                target -> target.isAlive() && !target.isSpectator() && target != caster);

        for (LivingEntity target : candidates) {
            Vec3d targetCenter = target.getEntityPos().add(0.0, target.getHeight() * 0.5, 0.0);
            Vec3d delta = targetCenter.subtract(wallCenter);
            double distanceForward = delta.dotProduct(forward);
            double distanceSide = delta.dotProduct(right);
            double distanceVertical = delta.y;

            if (distanceForward < 0.0 || distanceForward > SHATTER_BLAST_DEPTH) continue;
            if (Math.abs(distanceSide) > blastHalfRight) continue;
            if (Math.abs(distanceVertical) > blastHalfUp) continue;

            boolean damaged = target.damage(
                    world, world.getDamageSources().playerAttack(caster), SHATTER_DAMAGE);
            if (damaged) {
                AbstractWandItem.onWandDamageDealt(caster, SHATTER_DAMAGE);
            }
            target.addVelocity(
                    forward.x * SHATTER_HORIZONTAL_KNOCKBACK,
                    SHATTER_VERTICAL_KNOCKBACK,
                    forward.z * SHATTER_HORIZONTAL_KNOCKBACK);
            target.velocityModified = true;
        }

        ACTIVE_WALLS.remove(caster.getUuid());
        TemporaryBlockManager.restoreTemporaryBlocks(world, wall.placement);

        long cooldownTick = world.getTime();
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, data -> {
            data.putLong("ew_last_secondary", cooldownTick);
            data.putLong("ew_last_global", cooldownTick);
        });

        net.minecraft.util.math.random.Random random = world.getRandom();
        for (BlockPos pos : wall.blockPositions) {
            Vec3d origin = pos.toCenterPos();
            for (int index = 0; index < 9; index++) {
                double speed = 0.32 + random.nextDouble() * 0.52;
                Vec3d velocity = forward.multiply(speed)
                        .add(right.multiply((random.nextDouble() - 0.5) * 0.42))
                        .add(0.0, 0.08 + random.nextDouble() * 0.46, 0.0);
                spawnMovingParticle(world, ModParticles.STONE_SHARD, origin, velocity);
            }
            world.spawnParticles(ModParticles.STONE_DUST,
                    origin.x, origin.y, origin.z, 7, 0.38, 0.42, 0.38, 0.08);
            world.spawnParticles(ModParticles.STONE_FAULT,
                    origin.x, origin.y, origin.z, 1, 0.12, 0.16, 0.12, 0.0);
        }

        // A widening, ground-heavy wedge makes the recast direction unambiguous.
        for (double depth = 0.7; depth <= SHATTER_BLAST_DEPTH; depth += 0.65) {
            Vec3d front = wallCenter.add(forward.multiply(depth));
            double wedgeHalfWidth = halfRight + depth * 0.42;
            int lanes = Math.max(3, (int) Math.ceil(wedgeHalfWidth * 2.0));
            for (int lane = 0; lane <= lanes; lane++) {
                double lateral = MathHelper.lerp(lane / (double) lanes, -wedgeHalfWidth, wedgeHalfWidth);
                Vec3d point = front.add(right.multiply(lateral)).add(0.0, -halfUp + 0.25, 0.0);
                world.spawnParticles(ModParticles.STONE_DUST,
                        point.x, point.y, point.z, 2, 0.18, 0.06, 0.18, 0.045);
                if ((lane + MathHelper.floor(depth * 10.0)) % 3 == 0) {
                    spawnMovingParticle(world, ModParticles.STONE_SHARD, point,
                            forward.multiply(0.35 + depth * 0.055).add(0.0, 0.22, 0.0));
                }
            }
            world.spawnParticles(ModParticles.STONE_SHOCKWAVE,
                    front.x, wallCenter.y - halfUp + 0.3, front.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        world.playSound(null, wall.center, SoundEvents.BLOCK_DEEPSLATE_BRICKS_BREAK,
                SoundCategory.PLAYERS, 1.45f, 0.58f);
        world.playSound(null, wall.center, SoundEvents.ENTITY_RAVAGER_ROAR,
                SoundCategory.PLAYERS, 0.95f, 0.55f);
        world.playSound(null, wall.center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.PLAYERS, 0.9f, 0.78f);
    }

    public static void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (TitanDomeManager.hasActiveDome(caster)) return;
        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack)) return;
        TitanDomeManager.startDome(world, caster);
    }

    /** Only tracked player walls are breakable; restore their original terrain, never drop loot. */
    public static List<BlockPos> breakWallFromGuardian(ServerWorld world, BlockPos contact) {
        if(!world.getBlockState(contact).isOf(ModSpellBlocks.STONE_WALL))return List.of();
        var iterator=ACTIVE_WALLS.entrySet().iterator();
        while(iterator.hasNext()) {
            WallData wall=iterator.next().getValue();
            if(!wall.worldKey.equals(world.getRegistryKey()) || !wall.blockPositions.contains(contact))continue;
            List<BlockPos> standing=wall.blockPositions.stream()
                    .filter(pos -> world.getBlockState(pos).isOf(ModSpellBlocks.STONE_WALL)).toList();
            iterator.remove();
            TemporaryBlockManager.restoreTemporaryBlocks(world,wall.placement);
            for(BlockPos pos:standing) {
                Vec3d point=pos.toCenterPos();
                world.spawnParticles(ModParticles.STONE_SHARD,point.x,point.y,point.z,5,.3,.35,.3,.1);
                world.spawnParticles(ModParticles.STONE_DUST,point.x,point.y,point.z,3,.3,.3,.3,.05);
            }
            world.playSound(null,contact,SoundEvents.BLOCK_DEEPSLATE_BRICKS_BREAK,SoundCategory.HOSTILE,1.4f,.65f);
            return standing;
        }
        return List.of();
    }

    public static List<BlockPos> guardianWallBlocks(ServerWorld world) {
        return ACTIVE_WALLS.values().stream().filter(w -> w.worldKey.equals(world.getRegistryKey()))
                .flatMap(w -> w.blockPositions.stream())
                .filter(pos -> world.getBlockState(pos).isOf(ModSpellBlocks.STONE_WALL)).toList();
    }

    private static void markWallSettled(ServerWorld world, WallData wall) {
        for (BlockPos pos : wall.blockPositions) {
            BlockState state = world.getBlockState(pos);
            if (state.isOf(ModSpellBlocks.STONE_WALL)) {
                world.setBlockState(pos, state.with(StoneWallBlock.SHATTER_READY, true), 3);
            }
        }
    }

    private static void tickWalls(ServerWorld world) {
        int now = world.getServer().getTicks();
        Iterator<Map.Entry<UUID, WallData>> iterator = ACTIVE_WALLS.entrySet().iterator();
        while (iterator.hasNext()) {
            WallData wall = iterator.next().getValue();
            if (!wall.worldKey.equals(world.getRegistryKey())) continue;

            int age = now - wall.creationTick;
            if (age >= WALL_DURATION || !isWallVisible(world, wall)) {
                iterator.remove();
                continue;
            }
            if (!wall.settled && age >= WALL_SETTLE_TICKS) {
                markWallSettled(world, wall);
                wall.settled = true;
                world.playSound(null, wall.center, SoundEvents.BLOCK_DEEPSLATE_BRICKS_PLACE,
                        SoundCategory.PLAYERS, 0.8f, 0.62f);
            }
            if (wall.settled && now >= wall.nextStressFxTick) {
                spawnWallStressFx(world, wall, WALL_DURATION - age <= 16);
                wall.nextStressFxTick = now + WALL_STRESS_FX_INTERVAL;
            }
        }
    }

    private static void spawnWallStressFx(ServerWorld world, WallData wall, boolean urgent) {
        if (wall.blockPositions.isEmpty()) return;
        int samples = urgent ? 5 : 3;
        for (int index = 0; index < samples; index++) {
            BlockPos pos = wall.blockPositions.get(world.getRandom().nextInt(wall.blockPositions.size()));
            if (!world.getBlockState(pos).isOf(ModSpellBlocks.STONE_WALL)) continue;
            Vec3d center = pos.toCenterPos();
            world.spawnParticles(ModParticles.STONE_FAULT,
                    center.x, center.y, center.z, 1, 0.18, 0.2, 0.18, 0.0);
            world.spawnParticles(ModParticles.STONE_DUST,
                    center.x, center.y - 0.35, center.z, urgent ? 3 : 1, 0.24, 0.08, 0.24, 0.025);
        }
        world.playSound(null, wall.center, SoundEvents.BLOCK_DEEPSLATE_HIT,
                SoundCategory.PLAYERS, urgent ? 0.65f : 0.38f, urgent ? 0.48f : 0.64f);
    }

    private static boolean isWallVisible(ServerWorld world, WallData wall) {
        for (BlockPos pos : wall.blockPositions) {
            if (world.getBlockState(pos).isOf(ModSpellBlocks.STONE_WALL)) return true;
        }
        return false;
    }

    private static double nearestWallDistance(Vec3d point, List<BlockPos> blocks) {
        double nearest = Double.POSITIVE_INFINITY;
        for (BlockPos pos : blocks) {
            double dx = Math.max(0.0, Math.max(pos.getX() - point.x, point.x - (pos.getX() + 1.0)));
            double dy = Math.max(0.0, Math.max(pos.getY() - point.y, point.y - (pos.getY() + 1.0)));
            double dz = Math.max(0.0, Math.max(pos.getZ() - point.z, point.z - (pos.getZ() + 1.0)));
            nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return nearest;
    }

    private static Vec3d averageCenter(List<BlockPos> blocks, Vec3d fallback) {
        if (blocks.isEmpty()) return fallback;
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (BlockPos pos : blocks) {
            x += pos.getX() + 0.5;
            y += pos.getY() + 0.5;
            z += pos.getZ() + 0.5;
        }
        double count = blocks.size();
        return new Vec3d(x / count, y / count, z / count);
    }

    private static int resolveWallBottomY(ServerWorld world, int x, int z, int startY, int maxDepth) {
        for (int y = startY; y >= startY - maxDepth; y--) {
            BlockState state = world.getBlockState(new BlockPos(x, y - 1, z));
            if (!state.isAir() && !state.isReplaceable()) return y;
        }
        return startY;
    }

    private static Vec3d horizontalForward(PlayerEntity caster) {
        Vec3d look = caster.getRotationVec(1.0f);
        Vec3d horizontal = new Vec3d(look.x, 0.0, look.z);
        if (horizontal.lengthSquared() > 0.0001) return horizontal.normalize();

        float yawRadians = caster.getYaw() * (float) (Math.PI / 180.0);
        return new Vec3d(-MathHelper.sin(yawRadians), 0.0, MathHelper.cos(yawRadians)).normalize();
    }

    private static BlockPos discreteWallRight(Vec3d forward) {
        if (Math.abs(forward.x) >= Math.abs(forward.z)) {
            return new BlockPos(0, 0, forward.x >= 0.0 ? 1 : -1);
        }
        return new BlockPos(forward.z >= 0.0 ? -1 : 1, 0, 0);
    }

    private static void spawnMovingParticle(ServerWorld world, SimpleParticleType type, Vec3d pos, Vec3d velocity) {
        world.spawnParticles(type, pos.x, pos.y, pos.z,
                0, velocity.x, velocity.y, velocity.z, 1.0);
    }

}
