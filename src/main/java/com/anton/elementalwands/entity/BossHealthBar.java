package com.anton.elementalwands.entity;

import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/** Share encounter health with the fallen without adding them to targeting or party scaling. */
public final class BossHealthBar {
    private BossHealthBar() {}

    public static void update(ServerBossBar bar, LivingEntity boss, List<ServerPlayerEntity> fighters,
                              Predicate<ServerPlayerEntity> fellInFight) {
        var viewers = new HashSet<>(fighters);
        if (boss.getEntityWorld() instanceof ServerWorld world)
            viewers.addAll(world.getPlayers(p -> p.isAlive() && p.isSpectator() && fellInFight.test(p)));
        // A same-tick respawn reuses the dead entity's numeric ID, so the two player objects compare equal.
        for (var previous : List.copyOf(bar.getPlayers()))
            if (previous.isRemoved() || !viewers.contains(previous)) bar.removePlayer(previous);
        viewers.forEach(bar::addPlayer);
        bar.setPercent(Math.clamp(boss.getHealth() / Math.max(1, boss.getMaxHealth()), 0, 1));
    }
}
