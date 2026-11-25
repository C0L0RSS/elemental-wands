package com.anton.elementalwands.entity.roc;

import com.anton.elementalwands.entity.BossFightLog;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * {@code /ew summit log on}: the shared {@link BossFightLog} for the Storm Roc. Its stages are its
 * own (air, dive, stuck, brawl, takeoff, and phase two's); each brawl attack is recorded with what
 * the Roc read of its target and whether it hit anybody, each call with its pattern, and players
 * who fall to it are noted.
 */
public final class StormRocFightLog extends BossFightLog {
    private static boolean enabled;

    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    StormRocFightLog() { super("Storm Roc", "storm-roc", "idle", StormRocFightLog::enabled); }

    /** Records successful hits and deaths; the Roc's combat records its own choices. */
    public static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (enabled && entity instanceof ServerPlayerEntity player && player.getEntityWorld() instanceof ServerWorld world
                    && source.getAttacker() instanceof StormRocEntity roc)
                roc.combat().log().playerHit(world.getTime(), player, source, base, taken);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (enabled && entity instanceof ServerPlayerEntity player && player.getEntityWorld() instanceof ServerWorld world
                    && source.getAttacker() instanceof StormRocEntity roc)
                roc.combat().log().note(world.getTime(), player.getName().getString() + " fell to " + source.getName());
        });
    }
}
