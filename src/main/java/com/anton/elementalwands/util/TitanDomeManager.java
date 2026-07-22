package com.anton.elementalwands.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.registry.ModSpellBlocks;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
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
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
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

    private record ShellCandidate(BlockPos pos, int revealTick, boolean rib) {
    }

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
        private final int startTick;
        private final int expiryTick;
        private int nextRepairTick;
        private final Long2ObjectMap<BlockState> originalByPos;
        private final List<ShellCandidate> shellCandidates;
        private final Set<UUID> confinedEntityUuids;
        private final NbtCompound originalArmorNbt;
        private final ItemStack originalMainHand;
        private int nextBuffRefreshTick;
        private int nextAmbientFxTick;
        private int formationIndex;
        private boolean formationCompleted;
        private boolean collapsing;
        private int collapseStartedTick;
        private List<Long> collapsePositions = List.of();
        private int collapseIndex;

        private Dome(BlockPos center, int radius, UUID casterUuid, int startTick, int expiryTick, int nextRepairTick,
                Long2ObjectMap<BlockState> originalByPos, List<ShellCandidate> shellCandidates,
                Set<UUID> confinedEntityUuids, NbtCompound originalArmorNbt,
                ItemStack originalMainHand, int nextBuffRefreshTick, int nextAmbientFxTick) {
            this.center = center;
            this.radius = radius;
            this.casterUuid = casterUuid;
            this.startTick = startTick;
            this.expiryTick = expiryTick;
            this.nextRepairTick = nextRepairTick;
            this.originalByPos = originalByPos;
            this.shellCandidates = shellCandidates;
            this.confinedEntityUuids = confinedEntityUuids;
            this.originalArmorNbt = originalArmorNbt;
            this.originalMainHand = originalMainHand;
            this.nextBuffRefreshTick = nextBuffRefreshTick;
            this.nextAmbientFxTick = nextAmbientFxTick;
        }
    }

    private static final Map<RegistryKey<World>, List<Dome>> DOMES = new HashMap<>();
    private static final Map<RegistryKey<World>, List<Aegis>> AEGISES = new HashMap<>();

    private static final int DURATION_TICKS = 240;
    private static final int RADIUS = 16;
    private static final int DOME_FORMATION_TICKS = 30;
    private static final int DOME_RIB_COUNT = 12;
    private static final double DOME_RIB_ANGLE_HALF_WIDTH = 0.09;
    private static final int DOME_COLLAPSE_TICKS = 14;
    private static final int AMBIENT_FX_INTERVAL_TICKS = 4;
    private static final int REPAIR_INTERVAL_TICKS = 10;
    private static final int BUFF_REFRESH_INTERVAL_TICKS = 20;
    private static final int RESISTANCE_REFRESH_DURATION_TICKS = 40;
    private static final int AEGIS_DURATION_TICKS = 80;
    private static final int AEGIS_DISTANCE_AHEAD = 4;
    private static final int AEGIS_HEIGHT = 3;
    private static final int AEGIS_HALF_WIDTH = 1;
    private static final int AEGIS_BLOCK_DURATION_TICKS = 1;
    private static final double DOMAIN_PULL_SPEED = 1.5;

    private static final BlockState DOME_STATE = ModSpellBlocks.TITAN_DOME.getDefaultState();

    private static final RegistryKey<EquipmentAsset> TITAN_ARMOR_ASSET = RegistryKey.of(
            EquipmentAssetKeys.REGISTRY_KEY,
            Identifier.of("elementalwands", "titan_armor"));

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
        ServerPlayerEvents.LEAVE.register(TitanDomeManager::finishForPlayer);
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                finishForPlayer(player));
        ServerLifecycleEvents.SERVER_STOPPING.register(TitanDomeManager::finishAll);
    }

    public static void startDome(ServerWorld world, PlayerEntity caster) {
        int now = world.getServer().getTicks();

        BlockPos center = caster.getBlockPos();
        NbtCompound originalArmorNbt = serializeArmor(caster);
        ItemStack originalMainHand = caster.getMainHandStack().copy();
        Long2ObjectOpenHashMap<BlockState> originalByPos = new Long2ObjectOpenHashMap<>();
        List<ShellCandidate> shellCandidates = buildFormationCandidates(world, center, RADIUS);

        DOMES.computeIfAbsent(world.getRegistryKey(), _k -> new ArrayList<>())
                .add(new Dome(
                        center,
                        RADIUS,
                        caster.getUuid(),
                        now,
                        now + DOME_FORMATION_TICKS + DURATION_TICKS,
                        now + REPAIR_INTERVAL_TICKS,
                        originalByPos,
                        shellCandidates,
                        new HashSet<>(),
                        originalArmorNbt,
                        originalMainHand,
                        now + BUFF_REFRESH_INTERVAL_TICKS,
                        now));

        applyCasterBuffs(caster);
        equipTitanJuggernaut(caster, originalArmorNbt);

        spawnOpeningFaultRing(world, center, RADIUS);
        spawnJuggernautAssembly(world, caster);
        world.playSound(null, center, SoundEvents.BLOCK_DEEPSLATE_PLACE,
                SoundCategory.PLAYERS, 1.35f, 0.54f);
        world.playSound(null, center, SoundEvents.ENTITY_IRON_GOLEM_REPAIR,
                SoundCategory.PLAYERS, 0.9f, 0.48f);
    }

    private static List<ShellCandidate> buildFormationCandidates(ServerWorld world, BlockPos center, int radius) {
        List<ShellCandidate> candidates = new ArrayList<>();
        int radiusSquared = radius * radius;
        int innerRadiusSquared = (radius - 1) * (radius - 1);
        double spokeAngle = Math.PI * 2.0 / DOME_RIB_COUNT;

        // Two foundation layers anchor the shell without filling subterranean caves.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared > radiusSquared || distanceSquared < innerRadiusSquared) continue;

                    BlockPos pos = center.add(dx, dy, dz);
                    if (!canReplace(world.getBlockState(pos))) continue;

                    double angle = Math.atan2(dz, dx);
                    double nearestSpoke = Math.rint(angle / spokeAngle) * spokeAngle;
                    double angleDelta = Math.abs(wrapRadians(angle - nearestSpoke));
                    boolean rib = dx == 0 && dz == 0 || angleDelta <= DOME_RIB_ANGLE_HALF_WIDTH;
                    double heightProgress = MathHelper.clamp(Math.max(0, dy) / (double) radius, 0.0, 1.0);
                    int revealTick = 1 + (int) Math.round(heightProgress * 22.0) + (rib ? 0 : 6);
                    candidates.add(new ShellCandidate(pos, Math.min(DOME_FORMATION_TICKS - 1, revealTick), rib));
                }
            }
        }

        candidates.sort(Comparator
                .comparingInt(ShellCandidate::revealTick)
                .thenComparing(candidate -> !candidate.rib())
                .thenComparingInt(candidate -> candidate.pos().getY()));
        return candidates;
    }

    private static double wrapRadians(double angle) {
        while (angle > Math.PI) angle -= Math.PI * 2.0;
        while (angle < -Math.PI) angle += Math.PI * 2.0;
        return angle;
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

                if (dome.collapsing) {
                    if (tickDomeCollapse(world, dome, now)) {
                        it.remove();
                    }
                    continue;
                }

                if (now >= dome.expiryTick) {
                    beginDomeCollapse(world, dome, now);
                    continue;
                }

                activeCasters.add(dome.casterUuid);

                if (!dome.formationCompleted) {
                    tickDomeFormation(world, dome, now);
                } else if (now >= dome.nextAmbientFxTick) {
                    spawnActiveDomeFx(world, dome);
                    dome.nextAmbientFxTick = now + AMBIENT_FX_INTERVAL_TICKS;
                }

                if (now >= dome.nextBuffRefreshTick) {
                    refreshCasterBuffs(world, dome);
                    dome.nextBuffRefreshTick = now + BUFF_REFRESH_INTERVAL_TICKS;
                }

                if (dome.formationCompleted) {
                    tickInescapableDomain(world, dome, now);
                }

                if (dome.formationCompleted && now >= dome.nextRepairTick) {
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

    private static void tickDomeFormation(ServerWorld world, Dome dome, int now) {
        int elapsed = now - dome.startTick;
        int placedThisTick = 0;
        int ribBlocksThisTick = 0;

        while (dome.formationIndex < dome.shellCandidates.size()) {
            ShellCandidate candidate = dome.shellCandidates.get(dome.formationIndex);
            if (candidate.revealTick() > elapsed) break;
            dome.formationIndex++;

            BlockPos pos = candidate.pos();
            BlockState current = world.getBlockState(pos);
            if (!canReplace(current)) continue;

            dome.originalByPos.put(pos.asLong(), current);
            world.setBlockState(pos, DOME_STATE, 3);
            placedThisTick++;
            if (candidate.rib()) ribBlocksThisTick++;

            if (placedThisTick <= 16 && (candidate.rib() || world.getRandom().nextFloat() < 0.18f)) {
                Vec3d center = pos.toCenterPos();
                world.spawnParticles(ModParticles.STONE_SHARD,
                        center.x, center.y, center.z, candidate.rib() ? 3 : 1,
                        0.34, 0.3, 0.34, 0.075);
                world.spawnParticles(ModParticles.STONE_DUST,
                        center.x, center.y - 0.3, center.z, candidate.rib() ? 5 : 2,
                        0.45, 0.18, 0.45, 0.055);
            }
        }

        if (placedThisTick > 0 && elapsed % 4 == 0) {
            float pitch = 0.48f + Math.min(0.3f, elapsed / (float) DOME_FORMATION_TICKS * 0.3f);
            world.playSound(null, dome.center, SoundEvents.BLOCK_DEEPSLATE_PLACE,
                    SoundCategory.PLAYERS, ribBlocksThisTick > 0 ? 1.05f : 0.72f, pitch);
        }

        if (elapsed == 8 || elapsed == 16 || elapsed == 24) {
            Vec3d hero = dome.center.toCenterPos().add(0.0, 2.2 + elapsed * 0.06, 0.0);
            world.spawnParticles(ModParticles.STONE_TITAN,
                    hero.x, hero.y, hero.z, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(ModParticles.STONE_SHOCKWAVE,
                    dome.center.getX() + 0.5, dome.center.getY() + 0.2, dome.center.getZ() + 0.5,
                    3, 0.45, 0.08, 0.45, 0.0);
        }

        if (elapsed >= DOME_FORMATION_TICKS) {
            dome.formationCompleted = true;
            dome.nextAmbientFxTick = now;
            world.playSound(null, dome.center, SoundEvents.ENTITY_IRON_GOLEM_REPAIR,
                    SoundCategory.PLAYERS, 1.05f, 0.56f);
            world.playSound(null, dome.center, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                    SoundCategory.PLAYERS, 0.7f, 0.62f);
        }
    }

    private static void spawnActiveDomeFx(ServerWorld world, Dome dome) {
        if (dome.shellCandidates.isEmpty()) return;
        int samples = 5;
        for (int index = 0; index < samples; index++) {
            ShellCandidate candidate = dome.shellCandidates.get(
                    world.getRandom().nextInt(dome.shellCandidates.size()));
            BlockPos pos = candidate.pos();
            if (!world.getBlockState(pos).isOf(DOME_STATE.getBlock())) continue;

            Vec3d center = pos.toCenterPos();
            world.spawnParticles(ModParticles.STONE_DUST,
                    center.x, center.y - 0.2, center.z,
                    candidate.rib() ? 2 : 1, 0.22, 0.12, 0.22, 0.018);
            if (candidate.rib() && world.getRandom().nextFloat() < 0.42f) {
                world.spawnParticles(ModParticles.STONE_FAULT,
                        center.x, center.y, center.z, 1, 0.08, 0.1, 0.08, 0.0);
            }
        }

        PlayerEntity caster = world.getPlayerByUuid(dome.casterUuid);
        if (caster != null && world.getServer().getTicks() % 20 == 0) {
            Vec3d center = caster.getEntityPos().add(0.0, 1.05, 0.0);
            world.spawnParticles(ModParticles.STONE_DUST,
                    center.x, center.y, center.z, 5, 0.7, 0.9, 0.7, 0.025);
            world.spawnParticles(ModParticles.STONE_SHARD,
                    center.x, center.y, center.z, 3, 0.55, 0.65, 0.55, 0.035);
        }
    }

    private static void spawnOpeningFaultRing(ServerWorld world, BlockPos center, int radius) {
        int points = 72;
        for (int index = 0; index < points; index++) {
            double angle = Math.PI * 2.0 * index / points;
            double x = center.getX() + 0.5 + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
            double y = center.getY() + 0.12;
            world.spawnParticles(ModParticles.STONE_FAULT,
                    x, y, z, 1, 0.04, 0.02, 0.04, 0.0);
            if ((index & 1) == 0) {
                world.spawnParticles(ModParticles.STONE_DUST,
                        x, y, z, 2, 0.28, 0.05, 0.28, 0.045);
            }
        }
        world.spawnParticles(ModParticles.STONE_SHOCKWAVE,
                center.getX() + 0.5, center.getY() + 0.22, center.getZ() + 0.5,
                5, 0.8, 0.08, 0.8, 0.0);
    }

    private static void spawnJuggernautAssembly(ServerWorld world, PlayerEntity caster) {
        Vec3d center = caster.getEntityPos().add(0.0, 1.05, 0.0);
        world.spawnParticles(ModParticles.STONE_TITAN,
                center.x, center.y + 0.65, center.z, 2, 0.16, 0.25, 0.16, 0.0);
        world.spawnParticles(ModParticles.STONE_SHARD,
                center.x, center.y, center.z, 34, 0.85, 1.0, 0.85, 0.14);
        world.spawnParticles(ModParticles.STONE_DUST,
                center.x, center.y - 0.65, center.z, 28, 1.15, 0.24, 1.15, 0.085);
        for (int ring = 0; ring < 3; ring++) {
            int points = 12 + ring * 4;
            double radius = 0.7 + ring * 0.32;
            double y = center.y - 0.55 + ring * 0.65;
            for (int index = 0; index < points; index++) {
                double angle = Math.PI * 2.0 * index / points + ring * 0.35;
                Vec3d point = new Vec3d(
                        center.x + Math.cos(angle) * radius,
                        y,
                        center.z + Math.sin(angle) * radius);
                Vec3d inward = center.subtract(point).normalize().multiply(0.09).add(0.0, 0.05, 0.0);
                world.spawnParticles(ModParticles.STONE_SHARD,
                        point.x, point.y, point.z, 0, inward.x, inward.y, inward.z, 1.0);
            }
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
        int repaired = 0;
        for (Long2ObjectMap.Entry<BlockState> entry : dome.originalByPos.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            BlockState current = world.getBlockState(pos);

            if (current.isAir()) {
                world.setBlockState(pos, DOME_STATE, 3);
                repaired++;
                if (repaired <= 16) {
                    Vec3d center = pos.toCenterPos();
                    world.spawnParticles(ModParticles.STONE_SHARD,
                            center.x, center.y, center.z, 4, 0.28, 0.28, 0.28, 0.075);
                    world.spawnParticles(ModParticles.STONE_DUST,
                            center.x, center.y - 0.28, center.z, 3, 0.35, 0.1, 0.35, 0.045);
                    world.spawnParticles(ModParticles.STONE_FAULT,
                            center.x, center.y, center.z, 1, 0.08, 0.08, 0.08, 0.0);
                }
            }
        }

        if (repaired > 0) {
            world.playSound(null, dome.center, SoundEvents.ENTITY_IRON_GOLEM_REPAIR,
                    SoundCategory.PLAYERS, Math.min(1.2f, 0.45f + repaired * 0.025f), 0.64f);
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

    private static void tickInescapableDomain(ServerWorld world, Dome dome, int now) {
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

            if (now % 4 == 0) {
                Vec3d waist = living.getEntityPos().add(0.0, living.getHeight() * 0.45, 0.0);
                world.spawnParticles(ModParticles.STONE_FAULT,
                        waist.x, waist.y, waist.z, 1, 0.12, 0.2, 0.12, 0.0);
                world.spawnParticles(ModParticles.STONE_DUST,
                        waist.x, waist.y, waist.z, 5, 0.32, 0.32, 0.32, 0.07);
                world.spawnParticles(ModParticles.STONE_SHOCKWAVE,
                        waist.x, waist.y, waist.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void beginDomeCollapse(ServerWorld world, Dome dome, int now) {
        dome.collapsing = true;
        dome.collapseStartedTick = now;
        dome.collapseIndex = 0;
        dome.collapsePositions = new ArrayList<>();
        for (long packedPos : dome.originalByPos.keySet()) {
            dome.collapsePositions.add(packedPos);
        }
        dome.collapsePositions.sort(Comparator
                .comparingInt((Long packed) -> BlockPos.fromLong(packed).getY())
                .thenComparingLong(Long::longValue));

        PlayerEntity caster = world.getPlayerByUuid(dome.casterUuid);
        if (caster != null) {
            removeKnockbackResistance(caster);
            restoreJuggernautLoadout(world, caster, dome);
            removeMarkedTitanGear(caster);
        }

        // Crack reads across the entire shell before any slab is restored.
        int samples = Math.min(96, dome.collapsePositions.size());
        for (int index = 0; index < samples; index++) {
            int sampleIndex = index * dome.collapsePositions.size() / Math.max(1, samples);
            BlockPos pos = BlockPos.fromLong(dome.collapsePositions.get(sampleIndex));
            Vec3d center = pos.toCenterPos();
            world.spawnParticles(ModParticles.STONE_FAULT,
                    center.x, center.y, center.z, 1, 0.12, 0.16, 0.12, 0.0);
        }
        world.spawnParticles(ModParticles.STONE_TITAN,
                dome.center.getX() + 0.5, dome.center.getY() + 4.0, dome.center.getZ() + 0.5,
                3, 0.45, 0.6, 0.45, 0.0);
        world.playSound(null, dome.center, SoundEvents.BLOCK_DEEPSLATE_BREAK,
                SoundCategory.PLAYERS, 1.4f, 0.48f);
        world.playSound(null, dome.center, SoundEvents.ENTITY_WARDEN_ROAR,
                SoundCategory.PLAYERS, 0.72f, 0.48f);
    }

    /** Restores transient gear before vanilla death inventory handling runs. */
    public static void onPlayerDeath(ServerPlayerEntity player) {
        finishForPlayer(player);
    }

    private static void finishForPlayer(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        for (ServerWorld world : server.getWorlds()) {
            RegistryKey<World> key = world.getRegistryKey();
            List<Dome> domes = DOMES.get(key);
            if (domes != null) {
                Iterator<Dome> iterator = domes.iterator();
                while (iterator.hasNext()) {
                    Dome dome = iterator.next();
                    if (!dome.casterUuid.equals(player.getUuid())) continue;
                    finishDomeImmediately(world, dome, player);
                    iterator.remove();
                }
                if (domes.isEmpty()) DOMES.remove(key);
            }

            List<Aegis> aegises = AEGISES.get(key);
            if (aegises != null) {
                aegises.removeIf(aegis -> aegis.casterUuid.equals(player.getUuid()));
                if (aegises.isEmpty()) AEGISES.remove(key);
            }
        }
    }

    private static void finishAll(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            List<Dome> domes = DOMES.remove(world.getRegistryKey());
            if (domes == null) continue;
            for (Dome dome : domes) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(dome.casterUuid);
                finishDomeImmediately(world, dome, player);
            }
        }
        DOMES.clear();
        AEGISES.clear();
    }

    private static void finishDomeImmediately(ServerWorld world, Dome dome,
            ServerPlayerEntity player) {
        for (Long2ObjectMap.Entry<BlockState> entry : dome.originalByPos.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            if (world.getBlockState(pos).isOf(DOME_STATE.getBlock())) {
                world.setBlockState(pos, entry.getValue(), 3);
            }
        }
        if (player != null) {
            removeKnockbackResistance(player);
            restoreJuggernautLoadout(world, player, dome);
            removeMarkedTitanGear(player);
        }
    }

    private static boolean tickDomeCollapse(ServerWorld world, Dome dome, int now) {
        int total = dome.collapsePositions.size();
        if (total == 0) return true;

        int elapsed = now - dome.collapseStartedTick;
        int targetIndex = Math.min(total,
                MathHelper.ceil(total * Math.min(1.0, (elapsed + 1) / (double) DOME_COLLAPSE_TICKS)));
        int particleBudget = 18;
        while (dome.collapseIndex < targetIndex) {
            long packedPos = dome.collapsePositions.get(dome.collapseIndex++);
            BlockPos pos = BlockPos.fromLong(packedPos);
            BlockState current = world.getBlockState(pos);
            if (!current.isOf(DOME_STATE.getBlock())) continue;

            BlockState original = dome.originalByPos.get(packedPos);
            if (original != null) {
                world.setBlockState(pos, original, 3);
            }

            if (particleBudget-- > 0) {
                Vec3d center = pos.toCenterPos();
                world.spawnParticles(ModParticles.STONE_SHARD,
                        center.x, center.y, center.z, 5, 0.38, 0.34, 0.38, 0.09);
                world.spawnParticles(ModParticles.STONE_DUST,
                        center.x, center.y - 0.32, center.z, 7, 0.52, 0.16, 0.52, 0.075);
            }
        }

        if (elapsed % 3 == 0) {
            double radius = Math.max(2.0, dome.radius * (1.0 - elapsed / (double) DOME_COLLAPSE_TICKS));
            int points = 36;
            for (int index = 0; index < points; index++) {
                double angle = Math.PI * 2.0 * index / points;
                double x = dome.center.getX() + 0.5 + Math.cos(angle) * radius;
                double z = dome.center.getZ() + 0.5 + Math.sin(angle) * radius;
                world.spawnParticles(ModParticles.STONE_DUST,
                        x, dome.center.getY() + 0.2, z, 2, 0.36, 0.08, 0.36, 0.085);
            }
            world.spawnParticles(ModParticles.STONE_SHOCKWAVE,
                    dome.center.getX() + 0.5, dome.center.getY() + 0.24, dome.center.getZ() + 0.5,
                    4, Math.max(0.25, radius * 0.05), 0.04, Math.max(0.25, radius * 0.05), 0.0);
        }

        if (dome.collapseIndex < total && elapsed < DOME_COLLAPSE_TICKS + 2) return false;

        // Guarantee restoration even if a very large shell exceeded the staged target.
        for (Long2ObjectMap.Entry<BlockState> entry : dome.originalByPos.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.fromLong(entry.getLongKey());
            if (world.getBlockState(pos).isOf(DOME_STATE.getBlock())) {
                world.setBlockState(pos, entry.getValue(), 3);
            }
        }
        world.spawnParticles(ModParticles.STONE_SHOCKWAVE,
                dome.center.getX() + 0.5, dome.center.getY() + 0.28, dome.center.getZ() + 0.5,
                8, 1.6, 0.1, 1.6, 0.0);
        world.playSound(null, dome.center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.PLAYERS, 1.15f, 0.62f);
        return true;
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
        player.equipStack(EquipmentSlot.HEAD, createTitanArmorPiece(Items.NETHERITE_HELMET, EquipmentSlot.HEAD));
        player.equipStack(EquipmentSlot.CHEST, createTitanArmorPiece(Items.NETHERITE_CHESTPLATE, EquipmentSlot.CHEST));
        player.equipStack(EquipmentSlot.LEGS, createTitanArmorPiece(Items.NETHERITE_LEGGINGS, EquipmentSlot.LEGS));
        player.equipStack(EquipmentSlot.FEET, createTitanArmorPiece(Items.NETHERITE_BOOTS, EquipmentSlot.FEET));

        ItemStack titanSword = new ItemStack(ModItems.TITAN_SWORD);
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, titanSword, data -> {
            data.putBoolean(NBT_TITAN_GEAR, true);
            data.putBoolean(NBT_TITAN_SWORD, true);
            data.put(NBT_ORIGINAL_ARMOR, originalArmorNbt.copy());
        });
        player.setStackInHand(Hand.MAIN_HAND, titanSword);
    }

    private static ItemStack createTitanArmorPiece(net.minecraft.item.Item item, EquipmentSlot slot) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.EQUIPPABLE,
                EquippableComponent.builder(slot)
                        .model(TITAN_ARMOR_ASSET)
                        .dispensable(false)
                        .swappable(false)
                        .damageOnHurt(false)
                        .equipOnInteract(false)
                        .build());
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
