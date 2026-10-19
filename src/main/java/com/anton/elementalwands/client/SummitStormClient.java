package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.roc.RocEffectEntity;
import com.anton.elementalwands.entity.roc.StormRocEntity;
import com.anton.elementalwands.summit.StormSummit;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/**
 * Phase two's storm, as each client sees it. While a charged Roc or its storm is near, the summit's weather
 * eases into a thunderstorm: the mixins report its rain and thunder in place of the real
 * weather, so phase one's sky stays clear, and vanilla darkens the sky, light and fog and draws
 * the rain. The storm's own bolts flash the sky. Presentation only; the server's weather is untouched.
 */
public final class SummitStormClient {
    /** Seconds for the storm to roll in, and to clear after the fight. */
    private static final float ROLL_IN = 1 / 70f, CLEAR = 1 / 50f;
    private static final double NEAR = 400 * 400;
    private static float level, previous, cap, previousCap;
    private static Set<Integer> bolts = new HashSet<>();

    private SummitStormClient() {}

    /** 0 clear to 1 full storm, between client ticks. */
    public static float level(float tickProgress) { return MathHelper.lerp(tickProgress, previous, level); }

    /** 0 to 1: phase one's thin cap cloud over a summit where a Roc is fighting, gathered in and cleared like the storm. */
    public static float cap(float tickProgress) { return MathHelper.lerp(tickProgress, previousCap, cap); }

    public static void tick(MinecraftClient client) {
        previous = level;
        previousCap = cap;
        var world = client.world;
        if (world == null || client.player == null || world.getRegistryKey() != StormSummit.WORLD) {
            level = previous = cap = previousCap = 0;
            bolts.clear();
            return;
        }
        boolean storm = false, fight = false;
        Set<Integer> seen = new HashSet<>();
        for (Entity e : world.getEntities()) {
            if ((e instanceof StormRocEntity || e instanceof RocEffectEntity) && e.squaredDistanceTo(client.player) < NEAR) fight = true;
            if (e instanceof StormRocEntity roc && roc.charged() && roc.squaredDistanceTo(client.player) < NEAR) storm = true;
            // The storm's marker at the summit's centre keeps it going while the Roc is out of tracking range.
            if (e instanceof RocEffectEntity fx && fx.kind() == RocEffectEntity.Kind.STORM && fx.squaredDistanceTo(client.player) < NEAR) storm = true;
            if (e instanceof RocEffectEntity fx && flashes(fx.kind())) {
                seen.add(fx.getId());
                // Flash on the bolt's first tick, and the charging bolt's repeat strikes.
                float age = fx.effectTicks(0);
                boolean repeat = fx.kind() == RocEffectEntity.Kind.CHARGE_BOLT && age < 20 && (int) age % 6 == 0;
                if (!bolts.contains(fx.getId()) && age < 5 || repeat) world.setLightningTicksLeft(2);
            }
        }
        bolts = seen;
        level = storm ? Math.min(1, level + ROLL_IN) : Math.max(0, level - CLEAR);
        cap = fight ? Math.min(1, cap + ROLL_IN) : Math.max(0, cap - CLEAR);
    }

    private static boolean flashes(RocEffectEntity.Kind kind) {
        return kind == RocEffectEntity.Kind.CHARGE_BOLT || kind == RocEffectEntity.Kind.STORM_BOLT || kind == RocEffectEntity.Kind.CALL_BOLT;
    }
}
