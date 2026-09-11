package com.anton.elementalwands.entity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Shared encounter decisions and geometry, independent of a running world. */
public final class GuardianCombatRules {
    public enum Attack {
        FAN(58, 120), BEAM(72, 100), THROW(72, 80), SHOCKWAVE(56, 100), SLAM(56, 60), LEAP(GuardianLeapRules.LAND + GuardianLeapRules.RECOVERY, 180);
        public final int duration, cooldown;
        Attack(int duration, int cooldown) { this.duration = duration; this.cooldown = cooldown; }
    }
    public record Candidate(UUID id, double distance, boolean visible) {}
    public static final int THROW_LOCK = 40, THROW_RELEASE = 44, SLAM_IMPACT = 26, RECOVERY_GAP = 12;
    public static final double ROCK_GRAVITY = .035, ROCK_RADIUS = .7;
    public static final double WAVE_SPEED = .85, WAVE_RANGE = 32, WAVE_HEIGHT = .75;

    private GuardianCombatRules() {}

    public static int healthForParty(int players) { return GuardianGuardRules.health(players); }

    public static boolean eligible(Attack attack, Candidate player) {
        return eligible(attack, player, false);
    }

    public static boolean eligible(Attack attack, Candidate player, boolean unstable) {
        if (!player.visible()) return false;
        return switch (attack) {
            case FAN -> player.distance() >= 4 && player.distance() <= GuardianFanRules.RANGE;
            case BEAM -> player.distance() >= 5 && player.distance() <= 24;
            case THROW -> player.distance() >= 6 && player.distance() <= 48;
            case SHOCKWAVE -> player.distance() <= GuardianPhaseRules.waveRange(unstable);
            case LEAP -> player.distance() >= GuardianLeapRules.MIN_RANGE && player.distance() <= GuardianLeapRules.MAX_RANGE;
            case SLAM -> player.distance() <= 4.5;
        };
    }

    /** Least recently targeted eligible player wins; distance breaks initial ties. */
    public static Candidate target(Attack attack, List<Candidate> players, Map<UUID, Long> lastTargeted) {
        return target(attack, players, lastTargeted, false);
    }
    public static Candidate target(Attack attack, List<Candidate> players, Map<UUID, Long> lastTargeted, boolean unstable) {
        return players.stream().filter(p -> eligible(attack, p, unstable))
                .min(Comparator.<Candidate>comparingLong(p -> lastTargeted.getOrDefault(p.id(), Long.MIN_VALUE))
                        .thenComparingDouble(Candidate::distance).thenComparing(p -> p.id().toString()))
                .orElse(null);
    }

    public static Attack choose(List<Candidate> players, Map<Attack, Long> ready, long now, Attack last) {
        return choose(players, ready, now, last, false);
    }
    public static Attack choose(List<Candidate> players, Map<Attack, Long> ready, long now, Attack last, boolean unstable) {
        long nearby = players.stream().filter(p -> p.visible() && p.distance() <= 8).count();
        if (last != Attack.LEAP && now >= ready.getOrDefault(Attack.LEAP,0L)
                && players.stream().anyMatch(p -> eligible(Attack.LEAP,p))) return Attack.LEAP;
        // Cluster control has priority, but no attack can repeat immediately.
        Attack[] order = nearby >= 2 || last == Attack.THROW
                ? new Attack[]{Attack.SHOCKWAVE, Attack.SLAM, Attack.THROW, Attack.BEAM}
                : last == Attack.SHOCKWAVE
                ? new Attack[]{Attack.SLAM, Attack.BEAM, Attack.THROW, Attack.SHOCKWAVE}
                : new Attack[]{Attack.SLAM, Attack.THROW, Attack.BEAM, Attack.SHOCKWAVE};
        for (Attack attack : order) {
            if (attack != last && now >= ready.getOrDefault(attack, 0L)
                    && players.stream().anyMatch(p -> eligible(attack, p, unstable))) return attack;
        }
        // A distant solo player may only qualify for throws. Do not deadlock after one throw.
        if (last != null && now >= ready.getOrDefault(last, 0L)
                && players.stream().anyMatch(p -> eligible(last, p, unstable))) return last;
        return null;
    }

    /** Constant-gravity arc reaches the locked position after a readable flight time. */
    public static Vec3d launchVelocity(Vec3d origin, Vec3d aim) {
        int ticks = flightTicks(origin, aim);
        Vec3d delta = aim.subtract(origin);
        return new Vec3d(delta.x / ticks, delta.y / ticks + ROCK_GRAVITY * (ticks - 1) / 2, delta.z / ticks);
    }

    public static int flightTicks(Vec3d origin, Vec3d aim) {
        return Math.clamp((int)Math.ceil(origin.distanceTo(aim) / 1.5), 8, 36);
    }

    /** Predict steady horizontal travel; cap prediction so dashes/teleports cannot fling aim away. */
    public static Vec3d lead(Vec3d position, Vec3d velocity, int ticks) {
        Vec3d offset = new Vec3d(velocity.x, 0, velocity.z).multiply(Math.max(0, ticks));
        if (offset.length() > 6) offset = offset.normalize().multiply(6);
        return position.add(offset);
    }

    public static Vec3d throwAim(Vec3d origin, Vec3d position, Vec3d velocity, int commitmentTicks) {
        Vec3d aim = position;
        for (int i=0; i<4; i++) aim = lead(position, velocity, commitmentTicks + flightTicks(origin, aim));
        return aim;
    }

    public static boolean waveContact(Vec3d center, Box victim, double previousRadius, double radius, double groundY) {
        double x = Math.clamp(center.x, victim.minX, victim.maxX);
        double z = Math.clamp(center.z, victim.minZ, victim.maxZ);
        double near = Math.hypot(x-center.x, z-center.z);
        double far = Math.hypot(Math.max(Math.abs(victim.minX-center.x), Math.abs(victim.maxX-center.x)),
                Math.max(Math.abs(victim.minZ-center.z), Math.abs(victim.maxZ-center.z)));
        // A low traveling band: jumping above it works, standing anywhere inside does not re-hit.
        return near <= radius + .3 && far >= previousRadius - .3
                && victim.minY <= groundY + WAVE_HEIGHT && victim.maxY >= groundY;
    }
}
