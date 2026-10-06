package com.anton.elementalwands.entity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * {@code /ew guardian log on}: the shared {@link BossFightLog} for the Fractured Guardian. Its
 * stages are the shell (phase one), the break and the open core (phase two); players who fall to
 * it are noted.
 */
public final class GuardianFightLog extends BossFightLog {
    private static boolean enabled;

    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    GuardianFightLog() { super("Guardian", "guardian", "shell", GuardianFightLog::enabled); }

    /** Records successful hits and deaths; lost hits are reported by the Guardian's own attacks. */
    public static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (enabled && entity instanceof ServerPlayerEntity player && player.getEntityWorld() instanceof ServerWorld world
                    && source.getAttacker() instanceof FracturedGuardianEntity guardian)
                guardian.combat().log().playerHit(world.getTime(), player, source, base, taken);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (enabled && entity instanceof ServerPlayerEntity player && player.getEntityWorld() instanceof ServerWorld world
                    && source.getAttacker() instanceof FracturedGuardianEntity guardian)
                guardian.combat().log().note(world.getTime(), player.getName().getString() + " fell to " + source.getName());
        });
    }
}
