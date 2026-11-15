package com.anton.elementalwands.entity;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

/**
 * Shared encounter decisions and geometry, independent of a running world. The Guardian is a
 * hand-written rule system, like the Pyre King: where its target stands decides which attacks it
 * weighs, what the target just did tilts them, and chance picks among them. It is the slow one. It
 * cannot catch a player, so every hit is an area, the leap is how it closes distance, and the Scar
 * takes away the ground a player runs on.
 */
public final class GuardianCombatRules {
    public enum Attack {
        /** Stones burst round a player who has been launched. Plays the {@code fan} clip; see {@link GuardianAirburstRules}. */
        AIRBURST(48),
        /** Not chosen in a fight since the Scar took its place; it can still be rehearsed. */
        BEAM(72), THROW(72),
        /** Fists in front of it and a ring wave outward, at any range the wave reaches. */
        SLAM(56),
        /** A backhand through its sides and back, without turning first; see {@link GuardianSweepRules}. */
        SWEEP(GuardianSweepRules.DURATION), LEAP(GuardianLeapRules.LAND + GuardianLeapRules.RECOVERY),
        /** Two walls of spikes that pen its target; see {@link GuardianScarRules}. */
        SCAR(GuardianScarRules.DURATION),
        /** Phase two only: see {@link GuardianPulseRules}. */
        PULSE(GuardianPulseRules.DURATION);
        /** Ticks one plain, unheld use lasts. */
        public final int duration;
        Attack(int duration) { this.duration = duration; }
    }
    /**
     * A player the Guardian could attack. Threat is the damage they dealt it recently (decaying);
     * still means they have stood in place for a moment, usually to cast.
     */
    public record Candidate(UUID id, double distance, boolean visible, float threat, boolean still) {
        public Candidate(UUID id, double distance, boolean visible) { this(id, distance, visible, 0, false); }
    }
    public static final int THROW_LOCK = 40, THROW_RELEASE = 44, SLAM_IMPACT = 26;
    public static final double ROCK_GRAVITY = .035, ROCK_RADIUS = .7;
    public static final double WAVE_SPEED = .85, WAVE_RANGE = 32, WAVE_HEIGHT = .75;
    /**
     * Raw damage before armor and before vanilla's difficulty scaling (Easy roughly halves it, Hard
     * adds half). Sized against full iron on Normal. Nothing is a light hit: the edge of an area
     * (a wave, a rock's splash, an Airburst) takes about 30% of health, a direct hit about 45%.
     */
    public static final float WAVE_DAMAGE = 10, FIST_DAMAGE = 14, ROCK_DAMAGE = 13, ROCK_SPLASH_DAMAGE = 10;
    /** The slam's fists strike everything in front of it within this reach, on top of the ground wave. */
    public static final double FIST_RADIUS = 5;
    /** Nobody goes this long without being attacked, whatever the threat says. */
    public static final int NEGLECT = 200;
    /** Targeting weights: threat is in damage points, neglect in priority per second unattacked. */
    public static final float STILL_PRIORITY = 15, NEGLECT_PRIORITY = 4;
    /** Pressure (rest, lockouts, back stones) stops growing at this many players; health keeps scaling. */
    public static final int PRESSURE_CAP = 4;
    /** Threat fades with this time constant (ticks); a player counts as still after this many slow ticks. */
    public static final int THREAT_DECAY = 100, STILL_TICKS = 15;
    public static final double STILL_SPEED = .05;
    /**
     * Ticks it walks at its target after a string before it picks again, solo on Normal: 1.25 s, the
     * developer's limit for an opening. Every clip's own recovery plays out before this starts.
     */
    public static final int REST = 25;
    /** Distance bands. Inside CLOSE its fists reach; out to MID it walks in; out to FAR it leaps. */
    public static final double CLOSE = FIST_RADIUS, MID = 12, FAR = 30;
    public static final double THROW_MIN = 6, THROW_MAX = 48, BEAM_MIN = 5;
    /** A player who has stayed beyond MID this long is keeping their distance, and draws more leaps. */
    public static final int KEPT_AWAY = 100;
    /** The core pulse and the Airburst cannot follow themselves sooner than this (ticks, scaled). */
    public static final int PULSE_COOLDOWN = 320, AIRBURST_LOCKOUT = 40;

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

    /** The walk between strings: shorter with each player and on Hard. */
    public static double restScale(int players, Difficulty difficulty) {
        double[] party = {1, .7, .55, .45};
        return party[Math.clamp(players, 1, PRESSURE_CAP) - 1] * difficultyScale(difficulty, 1.4, .75);
    }
    public static double cooldownScale(int players, Difficulty difficulty) {
        double[] party = {1, .85, .75, .7};
        return party[Math.clamp(players, 1, PRESSURE_CAP) - 1] * difficultyScale(difficulty, 1.15, .9);
    }
    /**
     * How surely it holds its fists overhead against a player who jumped its last wave. It never
     * holds against one who did not: the hold is a memory, not a roll.
     */
    public static double holdChance(Difficulty difficulty) { return Math.min(1, .8 * difficultyScale(difficulty, .6, 1.25)); }
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
            case AIRBURST -> player.distance() >= GuardianAirburstRules.MIN_RANGE && player.distance() <= GuardianAirburstRules.RANGE;
            case BEAM -> player.distance() >= BEAM_MIN && player.distance() <= GuardianBeamTiming.RANGE;
            case THROW -> player.distance() >= THROW_MIN && player.distance() <= THROW_MAX;
            case LEAP -> player.distance() >= GuardianLeapRules.MIN_RANGE && player.distance() <= GuardianLeapRules.MAX_RANGE;
            case SLAM -> player.distance() <= GuardianPhaseRules.waveRange(unstable);
            case SWEEP -> player.distance() <= GuardianSweepRules.REACH;
            case SCAR -> player.distance() >= GuardianScarRules.MIN_RANGE && player.distance() <= GuardianScarRules.MAX_RANGE;
            case PULSE -> unstable && player.distance() <= GuardianPulseRules.PULL_RADIUS;
        };
    }

    public static Candidate target(Attack attack, List<Candidate> players, Map<UUID, Long> lastTargeted, long now) {
        return target(attack, players, lastTargeted, now, false, null);
    }
    /** Whoever {@link #focus} would pick among the players this attack can reach. */
    public static Candidate target(Attack attack, List<Candidate> players, Map<UUID, Long> lastTargeted,
            long now, boolean unstable, UUID avoid) {
        return focus(players.stream().filter(p -> eligible(attack, p, unstable)).toList(), lastTargeted, now, avoid);
    }
    /**
     * Who it turns on. Anyone left alone for {@link #NEGLECT} ticks comes first. Otherwise the highest
     * priority wins: recent damage dealt, standing still, and time since last attacked, with distance
     * breaking ties, so equal threat still rotates through the party. {@code avoid} (the player it
     * just attacked, inside a string) is skipped when someone else qualifies.
     */
    public static Candidate focus(List<Candidate> players, Map<UUID, Long> lastTargeted, long now, UUID avoid) {
        List<Candidate> pool = players;
        if (avoid != null && pool.stream().anyMatch(p -> !p.id().equals(avoid)))
            pool = pool.stream().filter(p -> !p.id().equals(avoid)).toList();
        Comparator<Candidate> ties = Comparator.<Candidate>comparingDouble(Candidate::distance).thenComparing(p -> p.id().toString());
        var neglected = pool.stream().filter(p -> unattacked(p, lastTargeted, now) >= NEGLECT)
                .min(Comparator.<Candidate>comparingLong(p -> -unattacked(p, lastTargeted, now)).thenComparing(ties));
        if (neglected.isPresent()) return neglected.get();
        return pool.stream().min(Comparator.<Candidate>comparingDouble(p -> -priority(p, lastTargeted, now)).thenComparing(ties)).orElse(null);
    }
    public static double priority(Candidate player, Map<UUID, Long> lastTargeted, long now) {
        return player.threat() + (player.still() ? STILL_PRIORITY : 0)
                + NEGLECT_PRIORITY * Math.min(NEGLECT, unattacked(player, lastTargeted, now)) / 20.0;
    }
    private static long unattacked(Candidate player, Map<UUID, Long> lastTargeted, long now) {
        Long at = lastTargeted.get(player.id());
        return at == null ? Long.MAX_VALUE : Math.max(0, now - at);
    }

    // ── The chooser ──────────────────────────────────────────────────────────

    /**
     * What it knows about its target when it picks. Distance is in blocks.
     *
     * @param airborne they have been launched and an Airburst is free to answer it
     * @param still they have stood in place for a moment, usually to cast
     * @param healing they are eating or drinking
     * @param keptAway they have stayed beyond {@link #MID} for {@link #KEPT_AWAY} ticks
     * @param pulse phase two's core pulse is ready and somebody stands inside its pull
     * @param behind they stand beside or behind it, outside {@link GuardianSweepRules#FRONT_DEGREES}
     * @param flanked somebody else stands beside or behind it, within reach of its arm
     * @param penned they stand between the Scar's walls
     */
    public record Read(double distance, boolean airborne, boolean still, boolean healing, boolean keptAway,
                       boolean unstable, boolean pulse, boolean behind, boolean flanked, boolean penned) {
        /** A target in front of it, with nobody behind it and no walls up. */
        public Read(double distance, boolean airborne, boolean still, boolean healing, boolean keptAway,
                    boolean unstable, boolean pulse) {
            this(distance, airborne, still, healing, keptAway, unstable, pulse, false, false, false);
        }
    }

    /**
     * The weights of every attack it could start now. Distance decides the family: fists up close, a
     * wave or the Scar as it walks in, the leap at range and the rock beyond that. What the target is
     * doing tilts it: a launched player draws the Airburst, one standing still or eating draws a rock,
     * one keeping their distance draws the leap and the Scar, one who has got behind it draws the
     * Sweep, and one its walls have penned draws the leap or the slam. Chance picks within that, so
     * the same spot does not always draw the same attack.
     */
    public static Map<Attack, Double> weights(Read read, Attack last, Attack before) {
        Map<Attack, Double> w = new EnumMap<>(Attack.class);
        double d = read.distance();
        if (read.airborne() && d >= GuardianAirburstRules.MIN_RANGE && d <= GuardianAirburstRules.RANGE) {
            w.put(Attack.AIRBURST, 1.0);
            return w;
        }
        if (read.pulse() && last != Attack.PULSE) {
            w.put(Attack.PULSE, 1.0);
            return w;
        }
        boolean penned = read.penned() && d <= GuardianPhaseRules.waveRange(read.unstable());
        if (penned) {
            // Boxed in by its spikes: the landing is the hit the walls set up, or the slam where the leap cannot reach.
            w.put(d >= GuardianLeapRules.MIN_RANGE && d <= GuardianLeapRules.MAX_RANGE ? Attack.LEAP : Attack.SLAM, 1.0);
        } else if (d <= CLOSE) {
            w.put(Attack.SLAM, read.behind() ? 3.0 : 6.0);
        } else if (d <= MID) {
            w.put(Attack.SLAM, 4.0);
            if (d >= GuardianScarRules.MIN_RANGE) w.put(Attack.SCAR, 4.0);
            if (d >= THROW_MIN) w.put(Attack.THROW, 2.0);
        } else if (d <= FAR) {
            w.put(Attack.LEAP, read.keptAway() ? 10.0 : 6.0);
            w.put(Attack.SCAR, read.keptAway() ? 6.0 : 3.0);
            w.put(Attack.THROW, 3.0);
            w.put(Attack.SLAM, 2.0);
        } else {
            if (d <= THROW_MAX) w.put(Attack.THROW, 5.0);
            if (d <= GuardianPhaseRules.waveRange(read.unstable())) w.put(Attack.SLAM, 2.0);
        }
        // Within its arm's reach the backhand is always possible, and likely against whoever got behind it. In front
        // of it the arm never arrives, so there it is a mix-up: stepping aside, the answer to a slam, walks into it.
        if (!penned && d <= GuardianSweepRules.REACH) w.put(Attack.SWEEP, read.behind() ? 8.0 : 2.0);
        // Somebody else is behind it while it looks at this player. Not while it has a player penned: the pen comes first.
        if (!penned && read.flanked()) w.merge(Attack.SWEEP, 4.0, Math::max);
        // Standing still to cast, or stopping to eat, is what the rock is for.
        if (read.still() || read.healing()) w.computeIfPresent(Attack.THROW, (attack, weight) -> weight + 8);
        // Never the same attack three times running, and seldom twice.
        if (last != null && w.size() > 1) {
            if (last == before) w.remove(last);
            else w.computeIfPresent(last, (attack, weight) -> weight * .45);
        }
        return w;
    }

    public static Attack choose(Read read, Attack last, Attack before, DoubleSupplier random) {
        return pick(weights(read, last, before), random);
    }

    /**
     * These cut its walk short: it answers a launched or eating player at once, and one standing still
     * where a rock can reach them. Standing still beside it is left alone: that is the opening.
     */
    public static boolean punishes(Read read) {
        return read.airborne() || read.healing()
                || read.still() && read.distance() >= THROW_MIN && read.distance() <= THROW_MAX;
    }

    public static Attack pick(Map<Attack, Double> weights, DoubleSupplier random) {
        double total = 0;
        for (double weight : weights.values()) total += weight;
        if (total <= 0) return null;
        double roll = random.getAsDouble() * total;
        Attack chosen = null;
        for (Map.Entry<Attack, Double> entry : weights.entrySet()) {
            chosen = entry.getKey();
            roll -= entry.getValue();
            if (roll < 0) break;
        }
        return chosen;
    }

    /**
     * A string is a run of attacks with no walk between them; each still plays its whole recovery. This
     * is how likely it is to go straight into another. A leap that has just landed nearly always does,
     * the Scar always does, and phase two strings far more often: that, not tripled attacks, is what
     * makes it meaner.
     */
    public static double chainChance(boolean unstable, Attack finished, Difficulty difficulty) {
        if (finished == Attack.PULSE) return 0;
        // The Scar is only the set-up: it always goes on, into whoever it penned.
        if (finished == Attack.SCAR) return 1;
        double base = finished == Attack.LEAP ? (unstable ? 1 : .5) : unstable ? .7 : .35;
        return Math.min(1, base * difficultyScale(difficulty, .6, 1.3));
    }
    /** The most attacks in one string. */
    public static int chainLimit(boolean unstable) { return unstable ? 3 : 2; }

    // ── Rocks and waves ──────────────────────────────────────────────────────

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

    /** The wave's front is crossing this body's footprint now, whatever height the body is at. */
    public static boolean waveCrossing(Vec3d center, Box victim, double previousRadius, double radius) {
        Box flat = new Box(victim.minX, 0, victim.minZ, victim.maxX, 1, victim.maxZ);
        return waveContact(new Vec3d(center.x, 0, center.z), flat, previousRadius, radius, 0);
    }
}
