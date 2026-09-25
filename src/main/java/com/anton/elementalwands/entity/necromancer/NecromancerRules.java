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
        BLINK(16, 7, 110),
        RAISE(44, 22, 300),
        DRAIN(84, 14, 240),
        HANDS(48, 32, 130),
        BOLT(40, 12, 50),
        // Colossus-only: the giant skeleton trades the blink for its long arms and rush.
        SWIPE(36, 18, 70),
        GRAB(64, 16, 200),
        RUSH(100, 12, 160);

        public final int duration, impact, cooldown;
        Action(int duration, int impact, int cooldown) { this.duration = duration; this.impact = impact; this.cooldown = cooldown; }
        public boolean colossusOnly() { return this == SWIPE || this == GRAB || this == RUSH; }
    }

    public record Candidate(UUID id, double distance, boolean visible) {}

    public static final int RECOVERY_GAP = 18;
    public static final int ENGAGE_RANGE = 24;
    public static final int ENCOUNTER_RANGE = 48;
    public static final int MOVEMENT_RANGE = 16;
    /** A caster keeps its distance: it backs away inside the near band and closes beyond the far one. */
    public static final double NEAR = 6, FAR = 15;

    public static final double BLINK_TRIGGER = 4.5, BLINK_MIN = 8, BLINK_MAX = 14;
    public static final int CURSE_TICKS = 100;
    public static final double CURSE_RADIUS = 2.5;

    public static final int BOLT_COUNT = 3, BOLT_INTERVAL = 6, BOLT_LIFE = 100;
    public static final double BOLT_SPEED = .38, BOLT_TURN = Math.toRadians(4.5), BOLT_RADIUS = .3, BOLT_RANGE = 28;
    public static final float BOLT_DAMAGE = 5;

    public static final double HANDS_RADIUS = 1.7, HANDS_RANGE = 24;
    public static final int HANDS_MAX_TARGETS = 3, ROOT_TICKS = 30;
    public static final float HANDS_DAMAGE = 7;

    public static final double DRAIN_RANGE = 18, DRAIN_BREAK_RANGE = 22;
    public static final int DRAIN_INTERVAL = 10;
    public static final float DRAIN_DAMAGE = 2, DRAIN_HEAL_SHARE = .03f;

    public static final int RISE_TICKS = 24, RAISE_PER_CAST = 2;
    public static final double RISE_DEPTH = 1.9;

    /** Eight-second emergence: hood opens, hands plant, body pulls free, robe burns, skeleton rises. */
    public static final int TRANSFORM_TICKS = 160, TRANSFORM_GROW = 90, TRANSFORM_ROAR = 142, RELOCATE_TIMEOUT = 200;
    public static final float COLOSSUS_WIDTH = 3.6f, COLOSSUS_HEIGHT = 4.5f;
    /** The crawl is low, but rearing needs headroom: clearance is checked above the hitbox. */
    public static final double COLOSSUS_CLEARANCE = 5.5;
    public static final double REACH = 5.5;

    public static final double SWIPE_RADIUS = 6.5, SWIPE_ARC = Math.toRadians(150), SWIPE_JUMP_CLEAR = 1.0;
    public static final float SWIPE_DAMAGE = 10;

    public static final double GRAB_REACH = 6.5, GRAB_CONE = Math.toRadians(70);
    public static final int GRAB_LIFT_END = 36, GRAB_SLAM = 44, GRAB_REGRAB = 300;

    public static final double RUSH_MIN = 7, RUSH_MAX = 24, RUSH_SPEED = .82, RUSH_REACH = 3.6;
    public static final int RUSH_WARNING = 12, RUSH_TRAVEL = 40;
    public static final int RUSH_BITE = 18, RUSH_THROW = 28, RUSH_RECOVER = 44;
    public static final float RUSH_DAMAGE = 8;
    public static final float RUSH_TURN = 3;

    public static int boltCount(boolean colossus) { return colossus ? 5 : BOLT_COUNT; }
    public static int boltInterval(boolean colossus) { return colossus ? 4 : BOLT_INTERVAL; }
    public static double boltSpeed(boolean colossus) { return colossus ? .48 : BOLT_SPEED; }
    public static double handsRadius(boolean colossus) { return colossus ? 2.4 : HANDS_RADIUS; }
    public static int handsTargets(boolean colossus) { return colossus ? 4 : HANDS_MAX_TARGETS; }
    public static int raisePerCast(boolean colossus) { return colossus ? 3 : RAISE_PER_CAST; }

    /** The slam hurts but never one-shots: at most a third of a player's health and never above 12. */
    public static float grabDamage(float playerMaxHealth) { return Math.min(12, playerMaxHealth * .35f); }
    /** Team damage during the hold that breaks the grip and staggers the skeleton. */
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

    public static int health(int players) { return 600 + 400 * (Math.max(1, players) - 1); }

    /** Phase two begins at half health: the robed caster transforms into the colossus. */
    public static boolean threshold(float health, float maxHealth) { return health <= maxHealth * .5f; }

    public static int minionCap(boolean colossus, int players) {
        int extra = Math.max(0, players - 1);
        return colossus ? Math.min(7, 5 + extra) : Math.min(5, 3 + extra);
    }

    /** Healing per drain tick, capped so one channel never restores more than a small share. */
    public static float drainHeal(float damage, float maxHealth, float healedThisChannel) {
        return Math.max(0, Math.min(damage * 2, maxHealth * DRAIN_HEAL_SHARE - healedThisChannel));
    }

    public static boolean canTarget(Action action, Candidate candidate) {
        return switch (action) {
            case BOLT -> candidate.visible() && candidate.distance() <= BOLT_RANGE;
            case DRAIN -> candidate.visible() && candidate.distance() <= DRAIN_RANGE;
            case HANDS -> candidate.distance() <= HANDS_RANGE;
            case RAISE -> candidate.distance() <= ENCOUNTER_RANGE;
            case BLINK -> candidate.distance() <= BLINK_TRIGGER;
            case SWIPE -> candidate.distance() <= REACH + 1;
            case GRAB -> candidate.visible() && candidate.distance() <= GRAB_REACH;
            case RUSH -> candidate.visible() && candidate.distance() >= RUSH_MIN && candidate.distance() <= RUSH_MAX;
        };
    }

    /**
     * Blink escapes a close player; raising refills an emptied army; otherwise the ready spell
     * that differs from the last cast wins, with bolts as the dependable fallback.
     */
    public static Action choose(List<Candidate> players, Map<Action, Long> ready, long now, Action last, int minions, int cap) {
        if (players.isEmpty()) return null;
        java.util.function.Predicate<Action> available = action -> now >= ready.getOrDefault(action, 0L)
                && players.stream().anyMatch(p -> canTarget(action, p));
        if (available.test(Action.BLINK)) return Action.BLINK;
        if (minions * 2 <= cap && available.test(Action.RAISE)) return Action.RAISE;
        for (Action action : new Action[]{Action.DRAIN, Action.HANDS, Action.RAISE, Action.BOLT})
            if (action != last && (action != Action.RAISE || minions < cap) && available.test(action)) return action;
        return available.test(Action.BOLT) ? Action.BOLT : null;
    }

    /**
     * The colossus answers close players with its arms, alternating swipe and grab, refills its army,
     * rushes distant players, and otherwise keeps casting its larger spells.
     */
    public static Action chooseColossus(List<Candidate> players, Map<Action, Long> ready, long now, Action last, int minions, int cap) {
        if (players.isEmpty()) return null;
        java.util.function.Predicate<Action> available = action -> now >= ready.getOrDefault(action, 0L)
                && players.stream().anyMatch(p -> canTarget(action, p));
        for (Action melee : last == Action.SWIPE ? new Action[]{Action.GRAB, Action.SWIPE} : new Action[]{Action.SWIPE, Action.GRAB})
            if (available.test(melee)) return melee;
        if (minions * 2 <= cap && available.test(Action.RAISE)) return Action.RAISE;
        for (Action action : new Action[]{Action.RUSH, Action.HANDS, Action.DRAIN, Action.RAISE, Action.BOLT})
            if (action != last && (action != Action.RAISE || minions < cap) && available.test(action)) return action;
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
