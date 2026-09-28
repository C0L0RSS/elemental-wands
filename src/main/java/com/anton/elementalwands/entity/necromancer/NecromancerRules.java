package com.anton.elementalwands.entity.necromancer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure encounter tuning: no world access, so the contract checks can exercise every rule. */
public final class NecromancerRules {
    private NecromancerRules() {}

    /** Duration is the whole cast; impact is the tick its effect lands; cooldown starts after the cast. */
    public enum Action {
        /** Escape from a close player; a flare marks where it lands. */
        BLINK(16, 7, 110),
        /** Reposition anywhere in the clearing every few casts; a flare marks the arrival point. */
        SHIFT(20, 12, 0),
        /** A whisper behind the target, then he appears there and bursts after a short windup. */
        AMBUSH(46, 26, 180),
        DRAIN(84, 14, 200),
        HANDS(48, 32, 110),
        BOLT(40, 12, 40),
        // Colossus-only: the giant skeleton trades the blinks for its long arms and rush.
        SWIPE(36, 18, 70),
        GRAB(64, 16, 200),
        RUSH(100, 12, 160);

        public final int duration, impact, cooldown;
        Action(int duration, int impact, int cooldown) { this.duration = duration; this.impact = impact; this.cooldown = cooldown; }
        public boolean colossusOnly() { return this == SWIPE || this == GRAB || this == RUSH; }
        public boolean robedOnly() { return this == BLINK || this == SHIFT || this == AMBUSH; }
    }

    /**
     * The robed fight alternates duels and sieges. Each duel ends at its health gate; each siege
     * is a run of waves while the caster stands out of reach. DONE hands over to the colossus.
     */
    public enum Stage {
        DUEL_A, SIEGE_1, DUEL_B, SIEGE_2, DONE;

        public boolean siege() { return this == SIEGE_1 || this == SIEGE_2; }
        /** First and last absolute wave numbers of a siege. */
        public int firstWave() { return this == SIEGE_2 ? 3 : 1; }
        public int lastWave() { return this == SIEGE_2 ? 4 : 2; }
        public Stage next() { return values()[Math.min(ordinal() + 1, DONE.ordinal())]; }
    }

    public record Candidate(UUID id, double distance, boolean visible) {}

    public static final int RECOVERY_GAP = 18;
    public static final int ENGAGE_RANGE = 24;
    public static final int ENCOUNTER_RANGE = 48;
    public static final int MOVEMENT_RANGE = 16;
    /** Blinks, shifts and ambushes may land this far from home; walking keeps to MOVEMENT_RANGE. */
    public static final int BLINK_LEASH = 30;
    /** A caster keeps its distance: it backs away inside the near band and closes beyond the far one. */
    public static final double NEAR = 6, FAR = 15;

    public static final double BLINK_TRIGGER = 4.5, BLINK_MIN = 8, BLINK_MAX = 14;
    public static final int CURSE_TICKS = 100;
    public static final double CURSE_RADIUS = 2.5;
    /** Casts between repositioning shifts: two or three, chosen after each blink. */
    public static final int SHIFT_EVERY_MIN = 2, SHIFT_EVERY_MAX = 3;
    public static final double SHIFT_MIN = 10, SHIFT_MAX = 24, SHIFT_CLEAR = 8;

    public static final int AMBUSH_APPEAR = 12;
    public static final double AMBUSH_BEHIND = 2.5, AMBUSH_RADIUS = 4.5, AMBUSH_MIN = 6;
    public static final float AMBUSH_DAMAGE = 14;

    public static final int BOLT_COUNT = 3, BOLT_INTERVAL = 8, BOLT_LIFE = 100;
    public static final double BOLT_SPEED = .38, BOLT_TURN = Math.toRadians(4.5), BOLT_RADIUS = .3, BOLT_RANGE = 28;
    public static final float BOLT_DAMAGE = 5;

    public static final double HANDS_RADIUS = 4, HANDS_RANGE = 24;
    public static final int HANDS_MAX_TARGETS = 3, ROOT_TICKS = 40;
    public static final float HANDS_DAMAGE = 10;

    public static final double DRAIN_RANGE = 18, DRAIN_BREAK_RANGE = 22;
    public static final int DRAIN_INTERVAL = 10;
    public static final float DRAIN_DAMAGE = 2, DRAIN_HEAL_SHARE = .03f;

    /** Hollow undead a siege wave can call up. */
    public enum Undead { CRAWLER, ARCHER, BRUTE }

    /** One siege wave's bodies. */
    public record Wave(int crawlers, int archers, int brutes) {
        public int total() { return crawlers + archers + brutes; }
    }

    private static final int[][] SOLO_WAVES = {{3, 0, 0}, {3, 1, 0}, {3, 1, 1}, {3, 2, 1}};
    private static final int[][] DUO_WAVES = {{5, 0, 0}, {4, 2, 0}, {4, 2, 1}, {5, 2, 2}};
    public static final int WAVE_ALIVE_CAP = 12;
    /** Spawns spread over this long; a cleared wave rests, a stalled one is joined by the next. */
    public static final int WAVE_STAGGER = 40, WAVE_BREATHER = 60, WAVE_TIMEOUT = 900;
    public static final double WAVE_MIN = 8, WAVE_MAX = 32, WAVE_CLEAR = 6;
    /** A flare marks the perch or landing point this long before the caster moves. */
    public static final int SIEGE_FLARE = 12, SIEGE_FIRST_WAVE = 20;
    /** Outside the crypt there are no boughs: the caster hovers this far above home. */
    public static final double SIEGE_HOVER = 10;
    /** A player this far above the floor for this long draws a soul bolt from the perch. */
    public static final double HOVER_HEIGHT = 4;
    public static final int HOVER_TICKS = 60, SNIPE_COOLDOWN = 60;
    public static final double CRASH_DROP = 5;
    /** After the first siege the caster lies exposed and takes extra damage. */
    public static final int EXPOSED_TICKS = 120;
    public static final float EXPOSED_MULTIPLIER = 1.5f;

    /** Eight-second emergence: hood opens, hands plant, body pulls free, robe burns, skeleton rises. */
    public static final int TRANSFORM_TICKS = 160, TRANSFORM_GROW = 90, TRANSFORM_ROAR = 142, RELOCATE_TIMEOUT = 200;
    public static final float COLOSSUS_WIDTH = 3.6f, COLOSSUS_HEIGHT = 4.5f;
    /** The crawl is low, but rearing needs headroom: clearance is checked above the hitbox. */
    public static final double COLOSSUS_CLEARANCE = 5.5;
    public static final double REACH = 5.5;

    public static final double SWIPE_RADIUS = 6.5, SWIPE_ARC = Math.toRadians(150), SWIPE_JUMP_CLEAR = 1.0;
    public static final float SWIPE_DAMAGE = 13;

    public static final double GRAB_REACH = 6.5, GRAB_CONE = Math.toRadians(70);
    public static final int GRAB_LIFT_END = 36, GRAB_SLAM = 44, GRAB_REGRAB = 300;

    public static final double RUSH_MIN = 7, RUSH_MAX = 24, RUSH_SPEED = .82, RUSH_REACH = 3.6;
    /** The skeleton rears and scrapes while a lane marks its path; the aim locks for the last ticks. */
    public static final int RUSH_WARNING = 30, RUSH_LOCK = 8, RUSH_COMBO_WARNING = 10, RUSH_TRAVEL = 40;
    public static final int RUSH_BITE = 18, RUSH_THROW = 28, RUSH_RECOVER = 44;
    public static final float RUSH_DAMAGE = 14;
    public static final float RUSH_TURN = .75f;

    public static int boltCount(boolean colossus) { return colossus ? 4 : BOLT_COUNT; }
    public static int boltInterval(boolean colossus) { return colossus ? 6 : BOLT_INTERVAL; }
    public static double boltSpeed(boolean colossus) { return colossus ? .48 : BOLT_SPEED; }
    public static double handsRadius(boolean colossus) { return colossus ? 5 : HANDS_RADIUS; }
    public static int handsTargets(boolean colossus) { return colossus ? 4 : HANDS_MAX_TARGETS; }
    /** Enough hand models to read as a ring: about one per 1.5 blocks of its inner circle. */
    public static int handsModels(double radius) { return Math.max(6, (int)Math.round(radius * .7 * Math.PI * 2 / 1.5)); }

    /**
     * Siege wave {@code wave} (1–4) for a party. Two players meet the authored duo wave, one the
     * lighter solo wave; each player beyond two adds 40%, and the whole wave stays under the cap.
     */
    public static Wave wave(int wave, int players) {
        int index = Math.clamp(wave, 1, 4) - 1;
        if (players <= 1) return new Wave(SOLO_WAVES[index][0], SOLO_WAVES[index][1], SOLO_WAVES[index][2]);
        double scale = 1 + .4 * (players - 2);
        int[] duo = DUO_WAVES[index];
        int archers = Math.min(4, (int)Math.round(duo[1] * scale)), brutes = Math.min(3, (int)Math.round(duo[2] * scale));
        int crawlers = Math.min((int)Math.round(duo[0] * scale), WAVE_ALIVE_CAP - archers - brutes);
        return new Wave(crawlers, archers, brutes);
    }

    /** Health share a duel may not cross: reaching it starts the next siege (or the transformation). */
    public static float gate(Stage stage) { return stage == Stage.DUEL_A || stage == Stage.SIEGE_1 ? .75f : .5f; }

    /** The slam hurts but never one-shots: at most 45% of a player's health and never above 14. */
    public static float grabDamage(float playerMaxHealth) { return Math.min(14, playerMaxHealth * .45f); }
    /** Team damage that breaks a grab's grip, or interrupts an ambush windup, and staggers the boss. */
    public static float grabEscape(float bossMaxHealth) { return Math.max(20, bossMaxHealth * .03f); }

    /** A sweep low enough to jump: feet above this height over the skeleton's floor clear it. */
    public static boolean swipeHits(double dx, double dz, double feetAboveFloor, double facingYawRadians) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > SWIPE_RADIUS || feetAboveFloor > SWIPE_JUMP_CLEAR) return false;
        if (distance < 1.2) return true;
        double forwardX = -Math.sin(facingYawRadians), forwardZ = Math.cos(facingYawRadians);
        double cos = (dx * forwardX + dz * forwardZ) / distance;
        return Math.acos(Math.clamp(cos, -1, 1)) <= SWIPE_ARC / 2;
    }

    public static int health(int players) { return 800 + 500 * (Math.max(1, players) - 1); }

    /** Phase two begins at half health: the robed caster transforms into the colossus. */
    public static boolean threshold(float health, float maxHealth) { return health <= maxHealth * .5f; }

    /** Healing per drain tick, capped so one channel never restores more than a small share. */
    public static float drainHeal(float damage, float maxHealth, float healedThisChannel) {
        return Math.max(0, Math.min(damage * 2, maxHealth * DRAIN_HEAL_SHARE - healedThisChannel));
    }

    public static boolean canTarget(Action action, Candidate candidate) {
        return switch (action) {
            case BOLT -> candidate.visible() && candidate.distance() <= BOLT_RANGE;
            case DRAIN -> candidate.visible() && candidate.distance() <= DRAIN_RANGE;
            case HANDS -> candidate.distance() <= HANDS_RANGE;
            case BLINK -> candidate.distance() <= BLINK_TRIGGER;
            case SHIFT -> candidate.distance() <= ENCOUNTER_RANGE;
            case AMBUSH -> candidate.distance() >= AMBUSH_MIN && candidate.distance() <= ENCOUNTER_RANGE;
            case SWIPE -> candidate.distance() <= REACH + 1;
            case GRAB -> candidate.visible() && candidate.distance() <= GRAB_REACH;
            case RUSH -> candidate.visible() && candidate.distance() >= RUSH_MIN && candidate.distance() <= RUSH_MAX;
        };
    }

    /**
     * Duel choice. A close player makes it blink away; after two or three casts it shifts to a new
     * spot; otherwise the ready spell that differs from the last cast wins, bolts as the fallback.
     * The Hands follow-up (an ambush on a rooted player) is started by the controller directly.
     */
    public static Action choose(List<Candidate> players, Map<Action, Long> ready, long now, Action last, int castsSinceBlink, int shiftEvery) {
        if (players.isEmpty()) return null;
        java.util.function.Predicate<Action> available = action -> now >= ready.getOrDefault(action, 0L)
                && players.stream().anyMatch(p -> canTarget(action, p));
        if (available.test(Action.BLINK)) return Action.BLINK;
        if (castsSinceBlink >= shiftEvery && available.test(Action.SHIFT)) return Action.SHIFT;
        for (Action action : new Action[]{Action.DRAIN, Action.HANDS, Action.AMBUSH, Action.BOLT})
            if (action != last && available.test(action)) return action;
        return available.test(Action.BOLT) ? Action.BOLT : null;
    }

    /**
     * The colossus answers close players with its arms, alternating swipe and grab, rushes distant
     * players, and otherwise keeps casting its larger spells. It raises no army.
     */
    public static Action chooseColossus(List<Candidate> players, Map<Action, Long> ready, long now, Action last) {
        if (players.isEmpty()) return null;
        java.util.function.Predicate<Action> available = action -> now >= ready.getOrDefault(action, 0L)
                && players.stream().anyMatch(p -> canTarget(action, p));
        for (Action melee : last == Action.SWIPE ? new Action[]{Action.GRAB, Action.SWIPE} : new Action[]{Action.SWIPE, Action.GRAB})
            if (available.test(melee)) return melee;
        for (Action action : new Action[]{Action.RUSH, Action.HANDS, Action.DRAIN, Action.BOLT})
            if (action != last && available.test(action)) return action;
        return available.test(Action.BOLT) ? Action.BOLT : null;
    }

    /** Rotates pressure through the group: the least recently targeted eligible player, nearest first. */
    public static Candidate target(Action action, List<Candidate> players, Map<UUID, Long> lastTargeted) {
        return players.stream().filter(p -> canTarget(action, p))
                .min(Comparator.<Candidate>comparingLong(p -> lastTargeted.getOrDefault(p.id(), Long.MIN_VALUE))
                        .thenComparingDouble(Candidate::distance))
                .orElse(null);
    }
}
