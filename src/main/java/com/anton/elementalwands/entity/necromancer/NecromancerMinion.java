package com.anton.elementalwands.entity.necromancer;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

/**
 * A Hollow undead bound to one Necromancer. It claws out of the floor with its own rise clip,
 * never saves, never drops loot, and dissolves when its caster is gone or the encounter stops.
 * Unbound (naturally spawned) copies share the class but skip every rule here.
 */
public interface NecromancerMinion {
    State minionState();

    /** True while the rise clip plays: the body cannot act or be pushed. */
    boolean isRising();

    /** Starts the rise clip in place; the body stands on the floor throughout. */
    void startRise();

    final class State {
        UUID boss;
        int orphanTicks;

        public boolean bound() { return boss != null; }
    }

    static void bind(MobEntity mob, NecromancerEntity boss) {
        ((NecromancerMinion)mob).minionState().boss = boss.getUuid();
        mob.setPersistent();
        mob.setCanPickUpLoot(false);
        for (var slot : net.minecraft.entity.EquipmentSlot.values()) mob.setEquipmentDropChance(slot, 0);
        ((NecromancerMinion)mob).startRise();
    }

    static boolean rising(MobEntity mob) { return ((NecromancerMinion)mob).isRising(); }

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

    /** Server tick for a bound minion: dissolve once its caster is lost. */
    static void tick(MobEntity mob) {
        State state = ((NecromancerMinion)mob).minionState();
        if (state.boss == null || rising(mob) || !(mob.getEntityWorld() instanceof ServerWorld world)) return;
        Entity boss = world.getEntity(state.boss);
        // Stopping the encounter dissolves the army directly; this only catches lost casters.
        // Measured from the encounter's home, so a caster on a far siege perch keeps its whole army.
        boolean bound = boss instanceof NecromancerEntity necromancer && necromancer.isAlive()
                && mob.squaredDistanceTo(necromancer.anchor()) <= 64 * 64;
        state.orphanTicks = bound ? 0 : state.orphanTicks + 1;
        if (state.orphanTicks >= 40) dissolve(mob, world);
    }

    /** The rise clip ended: a bound minion also respects the emergence cinematic. */
    static void finishRise(MobEntity mob, ServerWorld world) {
        State state = ((NecromancerMinion)mob).minionState();
        if (state.boss == null) return;
        // Blocks may have been placed over the grave during the rise.
        if (!world.isSpaceEmpty(mob)) { dissolve(mob, world); return; }
        Entity caster = world.getEntity(state.boss);
        mob.setAiDisabled(caster instanceof NecromancerEntity necromancer && necromancer.isTransforming());
    }

    static void dissolve(MobEntity mob, ServerWorld world) {
        world.spawnParticles(ParticleTypes.SOUL, mob.getX(), mob.getBodyY(.5), mob.getZ(), 10, .3, .5, .3, .03);
        mob.discard();
    }
}
