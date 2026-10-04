package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.entity.BossFightLog;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * {@code /ew necromancer log on}: the shared {@link BossFightLog}, also counting hits from the
 * Necromancer's army; its stages are the duels, sieges and colossus phases.
 */
public final class NecromancerFightLog extends BossFightLog {
    private static boolean enabled;

    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    NecromancerFightLog() { super("Necromancer", "necromancer", "duel A", NecromancerFightLog::enabled); }

    /** Records successful hits on players; lost hits are reported by the boss's own attacks. */
    public static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!enabled || !(entity instanceof ServerPlayerEntity player) || !(player.getEntityWorld() instanceof ServerWorld world)) return;
            NecromancerEntity boss = owner(world, source);
            if (boss != null) boss.combat().log().playerHit(world.getTime(), player, source, base, taken);
        });
    }

    private static NecromancerEntity owner(ServerWorld world, DamageSource source) {
        Entity attacker = source.getAttacker();
        if (attacker instanceof NecromancerEntity boss) return boss;
        if (attacker instanceof NecromancerMinion minion && minion.minionState().boss != null
                && world.getEntity(minion.minionState().boss) instanceof NecromancerEntity boss) return boss;
        return null;
    }
}
