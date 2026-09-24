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
        BOLT(40, 12, 50);

        public final int duration, impact, cooldown;
        Action(int duration, int impact, int cooldown) { this.duration = duration; this.impact = impact; this.cooldown = cooldown; }
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

    public static int health(int players) { return 600 + 400 * (Math.max(1, players) - 1); }

    /** Phase two begins at half health; the transformation itself arrives with the colossus form. */
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

    /** Rotates pressure through the group: the least recently targeted eligible player, nearest first. */
    public static Candidate target(Action action, List<Candidate> players, Map<UUID, Long> lastTargeted) {
        return players.stream().filter(p -> canTarget(action, p))
                .min(Comparator.<Candidate>comparingLong(p -> lastTargeted.getOrDefault(p.id(), Long.MIN_VALUE))
                        .thenComparingDouble(Candidate::distance))
                .orElse(null);
    }
}
