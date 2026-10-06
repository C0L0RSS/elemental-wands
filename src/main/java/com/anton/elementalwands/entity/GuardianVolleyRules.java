package com.anton.elementalwands.entity;

import net.minecraft.util.math.Vec3d;

/**
 * Co-op pressure: while the Guardian commits an attack to one player, stones peel off its back
 * and fly at the others. Each volley hits a player at most once, for {@link GuardianCombatRules#SHARD_DAMAGE}.
 */
public final class GuardianVolleyRules {
    /** Stones per target; ticks they rise with a spark before release; ticks between volleys. */
    public static final int COUNT = 3, RISE = 16, COOLDOWN = 100;
    /** Pressure stops growing at four players: the main target plus up to three volleys. */
    public static final int MAX_TARGETS = 3;
    public static final double RANGE = 36;
    private GuardianVolleyRules() {}

    /** Stones lift off its back above the shoulders, one cluster per target, rising as they charge. */
    public static Vec3d socket(Vec3d feet, float yaw, int cluster, int clusters, int index, float rise) {
        Vec3d forward = Vec3d.fromPolar(0, yaw), right = new Vec3d(forward.z, 0, -forward.x);
        double spread = (cluster - (clusters - 1) / 2.0) * 2.4 + (index - 1) * .7;
        double lift = Math.clamp(rise, 0, 1);
        return feet.add(forward.multiply(-1.4)).add(right.multiply(spread))
                .add(0, 4.8 + lift * lift * (3 - 2 * lift) * 1.6 + (index == 1 ? .35 : 0), 0);
    }
}
