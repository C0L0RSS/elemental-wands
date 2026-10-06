package com.anton.elementalwands.entity;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

/** Shared encounter decisions and geometry, independent of a running world. */
public final class GuardianCombatRules {
    public enum Attack {
        FAN(48, 120), BEAM(72, 100), THROW(72, 80), SHOCKWAVE(56, 100), SLAM(56, 60), LEAP(GuardianLeapRules.LAND + GuardianLeapRules.RECOVERY, 180),
        /** Phase two only: see {@link GuardianPulseRules}. */
        PULSE(GuardianPulseRules.DURATION, 400);
        public final int duration, cooldown;
        Attack(int duration, int cooldown) { this.duration = duration; this.cooldown = cooldown; }
    }
    /**
     * A player the Guardian could attack. Threat is the damage they dealt it recently (decaying);
     * still means they have stood in place for a moment, usually to cast.
     */
    public record Candidate(UUID id, double distance, boolean visible, float threat, boolean still) {
        public Candidate(UUID id, double distance, boolean visible) { this(id, distance, visible, 0, false); }
    }
    public static final int THROW_LOCK = 40, THROW_RELEASE = 44, SLAM_IMPACT = 26, RECOVERY_GAP = 12;
    public static final double ROCK_GRAVITY = .035, ROCK_RADIUS = .7;
    public static final double WAVE_SPEED = .85, WAVE_RANGE = 32, WAVE_HEIGHT = .75;
    /**
     * Raw damage before armor and before vanilla's difficulty scaling (Easy roughly halves it, Hard
     * adds half). Sized against full iron on Normal: light hits about 15% of health, heavy about 45%.
     */
    public static final float WAVE_DAMAGE = 6, FIST_DAMAGE = 14, ROCK_DAMAGE = 13, ROCK_SPLASH_DAMAGE = 6, SHARD_DAMAGE = 7;
    /** The slam's fists strike everything in front of it within this reach, on top of the ground wave. */
    public static final double FIST_RADIUS = 5;
    /** Nobody goes this long without being attacked, whatever the threat says. */
    public static final int NEGLECT = 200;
    /** Targeting weights: threat is in damage points, neglect in priority per second unattacked. */
    public static final float STILL_PRIORITY = 15, NEGLECT_PRIORITY = 4;
    /** Pressure (rest, cooldowns, volleys) stops growing at this many players; health keeps scaling. */
    public static final int PRESSURE_CAP = 4;
    /** Threat fades with this time constant (ticks); a player counts as still after this many slow ticks. */
    public static final int THREAT_DECAY = 100, STILL_TICKS = 15;
    public static final double STILL_SPEED = .05;

    private GuardianCombatRules() {}

    /** A victim's body within {@code radius} of the centre, from just below its feet to {@code above} over them. */
    public static boolean radial(Vec3d center, Box victim, double radius, double above) {
        if (victim.minY > center.y + above || victim.maxY < center.y - 1) return false;
        double x = Math.clamp(center.x, victim.minX, victim.maxX), z = Math.clamp(center.z, victim.minZ, victim.maxZ);
        return Math.hypot(x - center.x, z - center.z) <= radius;
    }
    /** The slam's fists: anywhere under its body, or in the front half within {@link #FIST_RADIUS}. */
    public static boolean fistContact(Vec3d anchor, float yaw, Box victim) {
        if (!radial(anchor, victim, FIST_RADIUS, 3)) return false;
        double x = Math.clamp(anchor.x, victim.minX, victim.maxX), z = Math.clamp(anchor.z, victim.minZ, victim.maxZ);
        Vec3d flat = new Vec3d(x - anchor.x, 0, z - anchor.z);
        return flat.length() < 1.6 || Vec3d.fromPolar(0, yaw).dotProduct(flat.normalize()) >= -.1;
    }

    public static int healthForParty(int players) { return GuardianShellRules.health(players); }

    /** Pause between attacks and the walk before the next: shorter with each player and on Hard. */
    public static double restScale(int players, Difficulty difficulty) {
        double[] party = {1, .7, .55, .45};
        return party[Math.clamp(players, 1, PRESSURE_CAP) - 1] * difficultyScale(difficulty, 1.4, .75);
    }
    public static double cooldownScale(int players, Difficulty difficulty) {
        double[] party = {1, .85, .75, .7};
        return party[Math.clamp(players, 1, PRESSURE_CAP) - 1] * difficultyScale(difficulty, 1.15, .9);
    }
    /** Chance an ordinary slam pauses at the top of its swing. */
    public static double delayChance(Difficulty difficulty) { return .35 * difficultyScale(difficulty, .6, 1.6); }
    public static int scaled(int ticks, double scale) { return Math.max(1, (int)Math.round(ticks * scale)); }
    private static double difficultyScale(Difficulty difficulty, double easy, double hard) {
        return difficulty == Difficulty.EASY ? easy : difficulty == Difficulty.HARD ? hard : 1;
    }

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
            case PULSE -> unstable && player.distance() <= GuardianPulseRules.PULL_RADIUS;
        };
    }

    public static Candidate target(Attack attack, List<Candidate> players, Map<UUID, Long> lastTargeted, long now) {
        return target(attack, players, lastTargeted, now, false, null);
    }
    /**
     * Anyone left alone for {@link #NEGLECT} ticks is attacked first. Otherwise the highest priority
     * wins: recent damage dealt, standing still, and time since last attacked, with distance
     * breaking ties, so equal threat still rotates through the party. {@code avoid} (the previous
     * target of a combo) is skipped when someone else qualifies.
     */
    public static Candidate target(Attack attack, List<Candidate> players, Map<UUID, Long> lastTargeted,
            long now, boolean unstable, UUID avoid) {
        List<Candidate> eligible = players.stream().filter(p -> eligible(attack, p, unstable)).toList();
        if (avoid != null && eligible.stream().anyMatch(p -> !p.id().equals(avoid)))
            eligible = eligible.stream().filter(p -> !p.id().equals(avoid)).toList();
        Comparator<Candidate> ties = Comparator.<Candidate>comparingDouble(Candidate::distance).thenComparing(p -> p.id().toString());
        var neglected = eligible.stream().filter(p -> unattacked(p, lastTargeted, now) >= NEGLECT)
                .min(Comparator.<Candidate>comparingLong(p -> -unattacked(p, lastTargeted, now)).thenComparing(ties));
        if (neglected.isPresent()) return neglected.get();
        return eligible.stream().min(Comparator.<Candidate>comparingDouble(p -> -priority(p, lastTargeted, now)).thenComparing(ties)).orElse(null);
    }
    public static double priority(Candidate player, Map<UUID, Long> lastTargeted, long now) {
        return player.threat() + (player.still() ? STILL_PRIORITY : 0)
                + NEGLECT_PRIORITY * Math.min(NEGLECT, unattacked(player, lastTargeted, now)) / 20.0;
    }
    private static long unattacked(Candidate player, Map<UUID, Long> lastTargeted, long now) {
        Long at = lastTargeted.get(player.id());
        return at == null ? Long.MAX_VALUE : Math.max(0, now - at);
    }

    public static Attack choose(List<Candidate> players, Map<Attack, Long> ready, long now, Attack last) {
        return choose(players, ready, now, last, false);
    }
    public static Attack choose(List<Candidate> players, Map<Attack, Long> ready, long now, Attack last, boolean unstable) {
        return choose(players, ready, now, last, unstable, 0);
    }

    public static boolean beamDue(List<Candidate> players, Map<Attack, Long> ready, long now,
            Attack last, int attacksSinceBeam) {
        return attacksSinceBeam >= 2 && last != Attack.BEAM && now >= ready.getOrDefault(Attack.BEAM, 0L)
                && players.stream().anyMatch(p -> eligible(Attack.BEAM, p));
    }

    public static Attack choose(List<Candidate> players, Map<Attack, Long> ready, long now,
            Attack last, boolean unstable, int attacksSinceBeam) {
        if (beamDue(players, ready, now, last, attacksSinceBeam)) return Attack.BEAM;
        if (unstable && last != Attack.PULSE && now >= ready.getOrDefault(Attack.PULSE, 0L)
                && players.stream().anyMatch(p -> eligible(Attack.PULSE, p, true))) return Attack.PULSE;
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
