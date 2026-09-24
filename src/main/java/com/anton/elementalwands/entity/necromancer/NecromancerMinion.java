package com.anton.elementalwands.entity.necromancer;

import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

/**
 * A summon bound to one Necromancer. It rises out of the floor, never saves, never drops loot,
 * and dissolves when its caster is gone or the encounter stops.
 */
public interface NecromancerMinion {
    State minionState();

    final class State {
        UUID boss;
        int riseTick = -1;
        double riseTargetY;
        int orphanTicks;
    }

    static void bind(MobEntity mob, NecromancerEntity boss, double groundY) {
        State state = ((NecromancerMinion)mob).minionState();
        state.boss = boss.getUuid();
        state.riseTick = 0;
        state.riseTargetY = groundY;
        mob.setPosition(mob.getX(), groundY - NecromancerRules.RISE_DEPTH, mob.getZ());
        mob.noClip = true;
        mob.setNoGravity(true);
        mob.setAiDisabled(true);
        mob.setPersistent();
        mob.setCanPickUpLoot(false);
        for (var slot : net.minecraft.entity.EquipmentSlot.values()) mob.setEquipmentDropChance(slot, 0);
    }

    static boolean rising(MobEntity mob) { return ((NecromancerMinion)mob).minionState().riseTick >= 0; }

    static boolean belongsTo(Entity entity, NecromancerEntity boss) {
        return entity instanceof NecromancerMinion minion && boss.getUuid().equals(minion.minionState().boss);
    }

    /** Minions and their caster are one team for targeting, projectiles and spell protection. */
    static boolean sameSide(MobEntity mob, Entity other) {
        UUID boss = ((NecromancerMinion)mob).minionState().boss;
        if (boss == null) return false;
        if (other instanceof NecromancerEntity) return boss.equals(other.getUuid());
        return other instanceof NecromancerMinion minion && boss.equals(minion.minionState().boss);
    }

    static boolean ignores(MobEntity mob, DamageSource source) {
        return rising(mob) && (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.FALL));
    }

    /** Server tick: finish rising, then dissolve if orphaned. */
    static void tick(MobEntity mob) {
        if (!(mob.getEntityWorld() instanceof ServerWorld world)) return;
        State state = ((NecromancerMinion)mob).minionState();
        if (state.boss == null) { mob.discard(); return; }
        if (state.riseTick >= 0) {
            int tick = ++state.riseTick;
            double start = state.riseTargetY - NecromancerRules.RISE_DEPTH;
            double y = start + NecromancerRules.RISE_DEPTH * Math.min(1, tick / (double)NecromancerRules.RISE_TICKS);
            mob.setPosition(mob.getX(), y, mob.getZ());
            mob.setVelocity(0, 0, 0);
            BlockPos floor = BlockPos.ofFloored(mob.getX(), state.riseTargetY - .5, mob.getZ());
            BlockState ground = world.getBlockState(floor);
            if (tick % 2 == 0 && !ground.isAir())
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground),
                        mob.getX(), state.riseTargetY + .05, mob.getZ(), 6, .35, .02, .35, .05);
            if (tick % 3 == 0)
                world.spawnParticles(ParticleTypes.SOUL, mob.getX(), state.riseTargetY + .2, mob.getZ(), 2, .3, .1, .3, .02);
            if (tick == 1)
                world.playSound(null, mob.getBlockPos(), SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 1.2f, .7f);
            if (tick >= NecromancerRules.RISE_TICKS) {
                state.riseTick = -1;
                mob.noClip = false;
                mob.setNoGravity(false);
                // Blocks may have been placed over the grave during the rise.
                if (!world.isSpaceEmpty(mob)) { dissolve(mob, world); return; }
                mob.setAiDisabled(false);
            }
            return;
        }
        Entity boss = world.getEntity(state.boss);
        // Stopping the encounter dissolves the army directly; this only catches lost casters.
        boolean bound = boss instanceof NecromancerEntity necromancer && necromancer.isAlive()
                && mob.squaredDistanceTo(necromancer) <= 64 * 64;
        state.orphanTicks = bound ? 0 : state.orphanTicks + 1;
        if (state.orphanTicks >= 40) dissolve(mob, world);
    }

    static void dissolve(MobEntity mob, ServerWorld world) {
        world.spawnParticles(ParticleTypes.SOUL, mob.getX(), mob.getBodyY(.5), mob.getZ(), 10, .3, .5, .3, .03);
        mob.discard();
    }
}
