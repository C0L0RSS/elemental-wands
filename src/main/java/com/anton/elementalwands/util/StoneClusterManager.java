package com.anton.elementalwands.util;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.entity.StoneClusterEntity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModParticles;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Player-owned, transient material reserve. Gathering never writes to the world. */
public final class StoneClusterManager {
    public static final Identifier WEIGHT = Identifier.of("elementalwands", "stone_cluster_weight");
    private static final Map<UUID, Reserve> RESERVES = new HashMap<>();
    private static final class Reserve {
        final ServerPlayerEntity player;
        final ServerWorld world;
        StoneClusterEntity cluster;
        int mass, duration = StoneClusterRules.SMALL_COOLDOWN;
        long lastShot = -1_000_000_000L, lastPull = -1_000_000_000L, lastChip = -1_000_000_000L;
        int lastSentMass = -1, lastSentRemaining = -1;
        Reserve(ServerPlayerEntity player, ServerWorld world) { this.player = player; this.world = world; }
        int remaining() {
            return StoneClusterRules.cooldownRemaining(world.getTime() - lastShot, duration,
                    EntangleTracker.getStacks(player) > 0);
        }
    }
    private StoneClusterManager() {}
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Reserve reserve : List.copyOf(RESERVES.values())) {
                var player = reserve.player;
                if (!player.isAlive() || player.isRemoved() || player.isSpectator()
                        || player.getEntityWorld() != reserve.world
                        || server.getPlayerManager().getPlayer(player.getUuid()) != player
                        || EWAttachments.getAffinity(player) != WizardAffinity.STONE) {
                    clear(player); continue;
                }
                if (reserve.cluster != null && reserve.cluster.isRemoved()) {
                    reserve.cluster = null; reserve.mass = 0; applyWeight(reserve);
                }
                sync(reserve);
            }
        });
        ServerPlayerEvents.LEAVE.register(StoneClusterManager::clear);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) clear(player);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Reserve reserve : List.copyOf(RESERVES.values())) clear(reserve.player);
        });
    }
    public static int mass(PlayerEntity player) {
        Reserve reserve = RESERVES.get(player.getUuid());
        return reserve == null ? 0 : reserve.mass;
    }
    public static int remaining(PlayerEntity player) {
        Reserve reserve = RESERVES.get(player.getUuid());
        return reserve == null ? 0 : reserve.remaining();
    }
    public static StoneClusterEntity held(PlayerEntity player) {
        Reserve reserve = RESERVES.get(player.getUuid());
        return reserve == null ? null : reserve.cluster;
    }
    public static void cast(ServerWorld world, PlayerEntity caster, ItemStack wand) {
        if (!(caster instanceof ServerPlayerEntity player) || !player.isAlive() || player.isSpectator()
                || EWAttachments.getAffinity(player) != WizardAffinity.STONE) return;
        Reserve reserve = RESERVES.get(player.getUuid());
        if (reserve != null && (reserve.world != world || reserve.player != player)) {
            clear(reserve.player); reserve = null;
        }
        if (reserve == null) {
            reserve = new Reserve(player, world); RESERVES.put(player.getUuid(), reserve);
        }
        if (reserve.remaining() > 0) {
            AbstractWandItem.sendCooldownActionbar(player, AbstractWandItem.Ability.PRIMARY, reserve.remaining());
            return;
        }
        BlockHitResult ground = gatheringTarget(world, player);
        if (ground != null) { gather(reserve, wand, ground); return; }
        // The previous attack's duration is authoritative, including after swapping wands.
        if (!AbstractWandItem.tryStartCooldown(world, player, wand,
                AbstractWandItem.Ability.PRIMARY, reserve.duration)) return;
        int mass = reserve.mass;
        StoneClusterEntity projectile = reserve.cluster;
        if (projectile == null || projectile.isRemoved()) {
            projectile = new StoneClusterEntity(ModEntities.STONE_CLUSTER, world);
            projectile.setOwner(player);
            projectile.setPosition(player.getEyePos().add(0, -.15, 0));
            projectile.release(player, 0);
            if (!world.spawnEntity(projectile)) return;
        } else projectile.release(player, mass);
        reserve.mass = 0; reserve.cluster = null;
        reserve.duration = mass == 0 ? StoneClusterRules.SMALL_COOLDOWN : StoneClusterRules.HEAVY_COOLDOWN;
        reserve.lastShot = world.getTime();
        applyWeight(reserve); sync(reserve);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK,
                SoundCategory.PLAYERS, mass == 0 ? .6f : 1.2f, mass == 0 ? 1.15f : .6f);
    }
    public static BlockHitResult gatheringTarget(ServerWorld world, PlayerEntity player) {
        Vec3d look = player.getRotationVec(1);
        if (look.y > -.35) return null;
        Vec3d start = player.getEyePos();
        BlockHitResult hit = world.raycast(new RaycastContext(start,
                start.add(look.multiply(StoneClusterRules.GATHER_RANGE)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getSide() != Direction.UP) return null;
        // A nearby enemy under the crosshair takes priority over the floor behind it.
        // Otherwise downward aiming in close combat would gather instead of firing.
        for (var target : world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,
                new net.minecraft.util.math.Box(start,hit.getPos()).expand(.1),
                target -> StoneClusterEntity.eligible(player,target))) {
            var box=target.getBoundingBox().expand(.05);
            if (box.contains(start) || box.raycast(start,hit.getPos()).isPresent()) return null;
        }
        var state = world.getBlockState(hit.getBlockPos());
        return state.isSolidBlock(world, hit.getBlockPos()) && !state.hasBlockEntity()
                && state.getFluidState().isEmpty() ? hit : null;
    }
    private static void gather(Reserve reserve, ItemStack wand, BlockHitResult ground) {
        long now = reserve.world.getTime();
        if (reserve.mass >= StoneClusterRules.MAX_MASS) return;
        if (StoneClusterRules.cooldownRemaining(now-reserve.lastPull, StoneClusterRules.PULL_TICKS,
                EntangleTracker.getStacks(reserve.player)>0) > 0) return;
        long global = wand.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .copyNbt().getLong("ew_last_global").orElse(-1_000_000_000L);
        if (now-global < 6) return;
        int before = reserve.mass;
        if (reserve.cluster == null || reserve.cluster.isRemoved()) {
            reserve.cluster = new StoneClusterEntity(ModEntities.STONE_CLUSTER, reserve.world);
            reserve.cluster.hold(reserve.player);
            if (!reserve.world.spawnEntity(reserve.cluster)) { reserve.cluster = null; return; }
        }
        reserve.mass = StoneClusterRules.mass(before + StoneClusterRules.PULL_MASS);
        reserve.cluster.gather(reserve.mass, before, ground.getBlockPos());
        reserve.lastPull = now;
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, wand, data -> data.putLong("ew_last_global", now));
        applyWeight(reserve); sync(reserve);
        var p = ground.getPos();
        reserve.world.spawnParticles(ModParticles.STONE_DUST,p.x,p.y+.1,p.z,14,.45,.1,.45,.035);
        reserve.world.playSound(null,ground.getBlockPos(),SoundEvents.BLOCK_DEEPSLATE_BREAK,
                SoundCategory.PLAYERS,.9f,.65f + reserve.mass*.002f);
    }
    public static void onDamage(ServerPlayerEntity player, float damage) {
        Reserve reserve = RESERVES.get(player.getUuid());
        if (reserve == null || reserve.mass <= 0 || reserve.cluster == null || damage <= 0) return;
        long now = reserve.world.getTime();
        if (now-reserve.lastChip < StoneClusterRules.CHIP_GRACE_TICKS) return;
        reserve.lastChip = now;
        reserve.mass = StoneClusterRules.afterDamage(reserve.mass, damage);
        reserve.cluster.chip(reserve.mass);
        Vec3d at = reserve.cluster.getEntityPos();
        reserve.world.spawnParticles(ModParticles.STONE_SHARD,at.x,at.y,at.z,14,.6,.4,.6,.16);
        applyWeight(reserve); sync(reserve);
    }
    private static void applyWeight(Reserve reserve) {
        var speed = reserve.player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(WEIGHT);
        if (reserve.mass > 0) speed.addTemporaryModifier(new EntityAttributeModifier(WEIGHT,
                StoneClusterRules.weight(reserve.mass), EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private static void sync(Reserve reserve) {
        int remaining = reserve.remaining();
        if (reserve.mass == reserve.lastSentMass && remaining == reserve.lastSentRemaining) return;
        reserve.lastSentMass = reserve.mass; reserve.lastSentRemaining = remaining;
        ServerPlayNetworking.send(reserve.player,
                new ModNetworking.SyncStoneClusterPayload(reserve.mass, remaining, reserve.duration));
    }
    public static void clear(ServerPlayerEntity player) {
        Reserve reserve = RESERVES.get(player.getUuid());
        if (reserve == null || reserve.player != player) return;
        RESERVES.remove(player.getUuid());
        if (reserve.cluster != null) reserve.cluster.discard();
        reserve.mass = 0; applyWeight(reserve);
        if (!player.isRemoved()) ServerPlayNetworking.send(player, new ModNetworking.SyncStoneClusterPayload(0,0,30));
    }
}
