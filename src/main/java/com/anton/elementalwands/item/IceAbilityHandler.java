package com.anton.elementalwands.item;

import java.util.List;
import java.util.UUID;

import com.anton.elementalwands.entity.BrinicleShardProjectileEntity;
import com.anton.elementalwands.util.BrinicleShardManager;
import com.anton.elementalwands.util.TendrilBloomManager;
import com.anton.elementalwands.util.WhiteoutManager;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class IceAbilityHandler {

    private static final int PRIMARY_COOLDOWN_TICKS = 25;
    private static final int SECONDARY_COOLDOWN_TICKS = 300;
    private static final double TENDRIL_TARGET_RANGE = 15.0;

    private IceAbilityHandler() {
    }

    public static int getPrimaryCooldownTicks() {
        return PRIMARY_COOLDOWN_TICKS;
    }

    public static int getSecondaryCooldownTicks() {
        return SECONDARY_COOLDOWN_TICKS;
    }

    public static void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) return;
        if (!(entity instanceof PlayerEntity player) || player.isSpectator()) return;

        BlockPos base = player.getBlockPos().down();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos pos = base.add(dx, 0, dz);
                BlockState state = world.getBlockState(pos);
                if (!state.getFluidState().isOf(Fluids.WATER)) continue;
                BlockState frosted = Blocks.FROSTED_ICE.getDefaultState();
                if (!frosted.canPlaceAt(world, pos)) continue;
                world.setBlockState(pos, frosted, 3);
            }
        }
    }

    public static void castPrimary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.tryStartCooldown(world, caster, stack,
                AbstractWandItem.Ability.PRIMARY, getPrimaryCooldownTicks())) {
            return;
        }

        BrinicleShardProjectileEntity shard = new BrinicleShardProjectileEntity(world, caster);
        world.spawnEntity(shard);

        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_SNOWBALL_THROW,
                SoundCategory.PLAYERS, 0.7f, 0.6f);
    }

    public static void castSecondary(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        UUID casterUuid = caster.getUuid();
        List<BrinicleShardManager.ShardSnapshot> shards = BrinicleShardManager.getActiveShardsForCaster(world, casterUuid);
        if (shards.isEmpty()) {
            caster.sendMessage(Text.literal("No active shards."), true);
            return;
        }

        if (!AbstractWandItem.tryStartCooldown(world, caster, stack,
                AbstractWandItem.Ability.SECONDARY, getSecondaryCooldownTicks())) {
            return;
        }

        int now = world.getServer().getTicks();

        for (BrinicleShardManager.ShardSnapshot s : shards) {
            LivingEntity target = findNearestTarget(world, caster, s.anchorPos());
            if (target != null) {
                Vec3d anchorVec = Vec3d.ofCenter(s.anchorPos());
                TendrilBloomManager.startTendril(world, caster, s.shardId(), anchorVec, target);
            }
        }

        BrinicleShardManager.markShardsForConsumption(world, casterUuid, now);

        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK,
                SoundCategory.PLAYERS, 0.6f, 1.4f);
    }

    public static void castUltimate(ServerWorld world, PlayerEntity caster, ItemStack stack) {
        if (!AbstractWandItem.trySpendUltimateCharge(world, caster, stack)) {
            return;
        }
        WhiteoutManager.startWhiteout(world, caster);
    }

    private static LivingEntity findNearestTarget(ServerWorld world, PlayerEntity caster, BlockPos anchor) {
        Vec3d anchorCenter = Vec3d.ofCenter(anchor);
        double rangeSq = TENDRIL_TARGET_RANGE * TENDRIL_TARGET_RANGE;

        Box box = new Box(
                anchorCenter.x - TENDRIL_TARGET_RANGE, anchorCenter.y - TENDRIL_TARGET_RANGE, anchorCenter.z - TENDRIL_TARGET_RANGE,
                anchorCenter.x + TENDRIL_TARGET_RANGE, anchorCenter.y + TENDRIL_TARGET_RANGE, anchorCenter.z + TENDRIL_TARGET_RANGE);

        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && !e.isSpectator() && !e.getUuid().equals(caster.getUuid()));

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double d = e.getEntityPos().squaredDistanceTo(anchorCenter);
            if (d > rangeSq) continue;
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }
}
