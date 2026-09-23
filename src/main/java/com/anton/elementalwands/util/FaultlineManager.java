package com.anton.elementalwands.util;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.data.*;
import com.anton.elementalwands.entity.*;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** A widening, surface-following wave. No world blocks are replaced or left behind. */
public final class FaultlineManager {
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final class Wave {
        final ServerPlayerEntity owner;
        final ServerWorld world;
        final Vec3d origin, forward, right;
        final Vec3d[] previous = new Vec3d[9];
        final Set<UUID> hits = new HashSet<>();
        final List<FaultlineSpikeEntity> visuals = new ArrayList<>();
        int age;
        Wave(ServerPlayerEntity player) {
            owner = player; world = player.getEntityWorld(); origin = player.getEntityPos();
            double angle = Math.toRadians(player.getYaw());
            forward = new Vec3d(-Math.sin(angle), 0, Math.cos(angle)); right = new Vec3d(forward.z, 0, -forward.x);
            Arrays.fill(previous, origin);
        }
    }
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> WAVES.removeIf(w -> !tick(w)));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { WAVES.forEach(FaultlineManager::clear); WAVES.clear(); });
    }
    public static void cast(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() || EWAttachments.getAffinity(player)!=WizardAffinity.STONE
                || !(player.getMainHandStack().getItem() instanceof AbstractWandItem)
                || !WandProgression.owns(player,WandSpells.find(StoneTechniqueRules.FAULTLINE))
                || !WandLoadouts.get(player).contains(StoneTechniqueRules.FAULTLINE)
                || !GuardianArenaManager.canCast(player) || StoneChargeManager.active(player)
                || HollowPurpleChargeManager.isCharging(player.getEntityWorld(),player)) return;
        if (!AbstractWandItem.tryStartCooldown(player.getEntityWorld(), player, player.getMainHandStack(),
                StoneTechniqueRules.FAULTLINE, StoneTechniqueRules.FAULT_COOLDOWN)) return;
        WAVES.add(new Wave(player));
        player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.PLAYERS, 1, .65f);
    }
    /** A small vertical search accepts leaf canopies and water tops, not tall walls or distant cave floors. */
    public static Vec3d surface(ServerWorld world, ServerPlayerEntity owner, Vec3d previous, double x, double z) {
        Vec3d top = new Vec3d(x, previous.y + 1.05, z);
        if (!world.isChunkLoaded(BlockPos.ofFloored(top))) return null;
        var hit = world.raycast(new RaycastContext(top, top.add(0, -2.6, 0), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.ANY, owner));
        if (hit.getType() == HitResult.Type.MISS || hit.getSide() != Direction.UP) return null;
        Vec3d at = hit.getPos();
        if (!world.getFluidState(BlockPos.ofFloored(at.add(0, -.05, 0))).isEmpty()
                && !world.getFluidState(BlockPos.ofFloored(at.add(0, -.05, 0))).isIn(net.minecraft.registry.tag.FluidTags.WATER)) return null;
        // Check overhead crossing at the higher surface, then the endpoint's clearance.
        double height = Math.max(previous.y, at.y) + .65;
        if (world.raycast(new RaycastContext(new Vec3d(previous.x,height,previous.z), new Vec3d(at.x,height,at.z),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner)).getType() != HitResult.Type.MISS) return null;
        if (!world.isSpaceEmpty(owner, new Box(at.add(-.18,.05,-.18), at.add(.18,.8,.18)))) return null;
        return at;
    }
    private static boolean tick(Wave wave) {
        var owner = wave.owner;
        if (!owner.isAlive() || owner.isDisconnected() || owner.isSpectator() || owner.getEntityWorld() != wave.world
                || !GuardianArenaManager.canCast(owner)) { clear(wave); return false; }
        int age = wave.age++;
        if (age < StoneTechniqueRules.WINDUP) {
            for (int i = 1; i <= 5; i++) {
                Vec3d at = wave.origin.add(wave.forward.multiply(i*1.7));
                wave.world.spawnParticles(ModParticles.STONE_FAULT, at.x, at.y+.08, at.z, 1, i*.15, .02, .1, 0);
            }
            return true;
        }
        int firstRow = (age - StoneTechniqueRules.WINDUP) * StoneTechniqueRules.FAULT_ROWS_PER_TICK;
        if (firstRow >= StoneTechniqueRules.FAULT_ROWS) {
            int travelTicks = (StoneTechniqueRules.FAULT_ROWS + StoneTechniqueRules.FAULT_ROWS_PER_TICK - 1)
                    / StoneTechniqueRules.FAULT_ROWS_PER_TICK;
            return age < StoneTechniqueRules.WINDUP + travelTicks + FaultlineSpikeEntity.LIFE;
        }
        // Visit every intermediate surface even when several rows erupt in the same tick.
        for (int row = firstRow; row < Math.min(firstRow + StoneTechniqueRules.FAULT_ROWS_PER_TICK,
                StoneTechniqueRules.FAULT_ROWS); row++) eruptRow(wave, row);
        return true;
    }
    private static void eruptRow(Wave wave, int row) {
        var owner = wave.owner;
        double depth = row + 1, halfWidth = 1 + 3 * depth / StoneTechniqueRules.FAULT_ROWS;
        Vec3d lastVisual = null;
        for (int lane = 0; lane < 9; lane++) {
            Vec3d prior = wave.previous[lane];
            if (prior == null) continue;
            Vec3d horizontal = wave.origin.add(wave.forward.multiply(depth)).add(wave.right.multiply((lane-4)/4.0*halfWidth));
            Vec3d at = surface(wave.world, owner, prior, horizontal.x, horizontal.z);
            wave.previous[lane] = at; // A blocked lane cannot reappear behind a wall or across a gap.
            if (at == null || !GuardianArenaManager.canTeleport(owner, wave.world, at.add(0,.05,0))) continue;
            if (lastVisual == null || at.subtract(lastVisual).horizontalLength() >= .85) {
                var spike = new FaultlineSpikeEntity(ModEntities.FAULTLINE_SPIKE, wave.world);
                spike.refreshPositionAndAngles(at.x, at.y, at.z, lane*37+row*19, 0); spike.begin();
                wave.world.spawnEntity(spike); wave.visuals.add(spike); lastVisual = at;
            }
            wave.world.spawnParticles(ModParticles.STONE_DUST, at.x, at.y+.12, at.z, 3, .3,.08,.3,.025);
            for (LivingEntity target : wave.world.getEntitiesByClass(LivingEntity.class,
                    new Box(at.add(-.55,-.15,-.55), at.add(.55,1.25,.55)),
                    e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity) && !WandAllies.protectedFrom(owner,e))) {
                if (wave.hits.contains(target.getUuid())) continue;
                if (wave.world.raycast(new RaycastContext(at.add(0,.5,0),target.getEntityPos().add(0,.5,0),
                        RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,owner)).getType()!=HitResult.Type.MISS) continue;
                wave.hits.add(target.getUuid());
                if (SpellCombat.damage(target,wave.world,owner.getDamageSources().playerAttack(owner),4,owner,WizardAffinity.STONE)) {
                    AbstractWandItem.onWandDamageDealt(owner,4,WizardAffinity.STONE);
                    if (!(target instanceof FracturedGuardianEntity)) StoneChargeManager.interrupt(target);
                }
            }
        }
        if (row%3==0) wave.world.playSound(null,BlockPos.ofFloored(wave.origin.add(wave.forward.multiply(depth))),
                SoundEvents.BLOCK_DEEPSLATE_BREAK,SoundCategory.PLAYERS,.65f,.75f);
    }
    private static void clear(Wave wave) { wave.visuals.forEach(FaultlineSpikeEntity::discard); }
    private FaultlineManager() {}
}
