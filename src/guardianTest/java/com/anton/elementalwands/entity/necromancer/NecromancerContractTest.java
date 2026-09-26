package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.entity.necromancer.NecromancerRules.Action;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Candidate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure rule checks; run by the Gradle check task without launching Minecraft. */
public final class NecromancerContractTest {
    public static void main(String[] args) {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        Map<Action, Long> ready = new EnumMap<>(Action.class);

        require(NecromancerRules.health(1) == 600 && NecromancerRules.health(3) == 1400, "Party health scaling moved");
        require(NecromancerRules.threshold(300, 600) && !NecromancerRules.threshold(301, 600), "Half-health boundary moved");
        require(NecromancerRules.minionCap(false, 1) == 3 && NecromancerRules.minionCap(false, 9) == 5, "Phase-one army cap moved");
        require(NecromancerRules.minionCap(true, 1) == 5 && NecromancerRules.minionCap(true, 9) == 7, "Colossus army cap moved");
        // The army starts as crawlers; archers join a standing army, a brute only a grown or colossus army.
        for (double roll = 0; roll < 1; roll += .05)
            require(NecromancerRules.raiseKind(0, 0, 0, false, roll) == NecromancerRules.Undead.CRAWLER, "First raised body was not a crawler");
        require(NecromancerRules.raiseKind(1, 0, 0, false, .1) == NecromancerRules.Undead.ARCHER, "Archers never joined");
        require(NecromancerRules.raiseKind(1, 2, 0, false, .1) == NecromancerRules.Undead.CRAWLER, "More than two archers");
        require(NecromancerRules.raiseKind(2, 0, 0, false, .1) != NecromancerRules.Undead.BRUTE, "Brute joined a small army");
        require(NecromancerRules.raiseKind(3, 0, 0, false, .1) == NecromancerRules.Undead.BRUTE, "Brute never joined a grown army");
        require(NecromancerRules.raiseKind(0, 0, 0, true, .1) == NecromancerRules.Undead.BRUTE, "Colossus army had no brute");
        require(NecromancerRules.raiseKind(5, 1, 1, true, .1) != NecromancerRules.Undead.BRUTE, "Second brute joined");
        require(NecromancerRules.raiseKind(5, 1, 1, true, .9) == NecromancerRules.Undead.CRAWLER, "Army was not mostly crawlers");

        // Drain healing stops at its per-channel share however long the channel runs.
        float healed = 0;
        for (int i = 0; i < 40; i++) healed += NecromancerRules.drainHeal(NecromancerRules.DRAIN_DAMAGE, 600, healed);
        require(Math.abs(healed - 600 * NecromancerRules.DRAIN_HEAL_SHARE) < 1e-3, "Drain heal exceeded its cap: " + healed);

        // A close player triggers the escape before anything else.
        var close = List.of(new Candidate(a, 3, true), new Candidate(b, 12, true));
        require(NecromancerRules.choose(close, ready, 0, null, 3, 3) == Action.BLINK, "Close player did not trigger blink");
        // An emptied army is refilled first.
        var spread = List.of(new Candidate(a, 10, true), new Candidate(b, 14, true));
        require(NecromancerRules.choose(spread, ready, 0, null, 0, 3) == Action.RAISE, "Empty army was not raised");
        // A full army and recent drain move on to the next spell; no spell repeats while another is ready.
        require(NecromancerRules.choose(spread, ready, 0, Action.DRAIN, 3, 3) == Action.HANDS, "Spell rotation repeated");
        require(NecromancerRules.choose(spread, ready, 0, null, 3, 3) == Action.DRAIN, "Drain lost priority");
        // Out of sight: drain and bolt need vision, hands do not.
        var hidden = List.of(new Candidate(a, 10, false));
        ready.put(Action.HANDS, 100L);
        require(NecromancerRules.choose(hidden, ready, 0, null, 3, 3) == null, "Cast a sight-based spell without vision");
        require(NecromancerRules.choose(hidden, ready, 100, null, 3, 3) == Action.HANDS, "Hands need no vision");
        // Bolts remain the fallback even directly after a bolt.
        ready.clear();
        for (Action action : Action.values()) if (action != Action.BOLT) ready.put(action, 1000L);
        require(NecromancerRules.choose(spread, ready, 0, Action.BOLT, 3, 3) == Action.BOLT, "No fallback spell");
        require(NecromancerRules.choose(List.of(new Candidate(a, 40, true)), ready, 0, null, 3, 3) == null, "Bolt fired beyond range");

        // Target rotation: the least recently targeted eligible player, nearest breaking ties.
        Map<UUID, Long> last = new HashMap<>();
        var three = List.of(new Candidate(a, 9, true), new Candidate(b, 5, true), new Candidate(c, 7, true));
        require(NecromancerRules.target(Action.BOLT, three, last).id().equals(b), "Tie did not prefer nearest");
        last.put(b, 10L); last.put(c, 5L);
        require(NecromancerRules.target(Action.BOLT, three, last).id().equals(a), "Rotation skipped the untargeted player");
        last.put(a, 20L);
        require(NecromancerRules.target(Action.BOLT, three, last).id().equals(c), "Rotation ignored oldest target");

        // Every cast lands inside its own duration; bolts all leave before the cast ends.
        for (Action action : Action.values()) require(action.impact < action.duration, action + " impact after cast end");
        require(Action.BOLT.impact + (NecromancerRules.BOLT_COUNT - 1) * NecromancerRules.BOLT_INTERVAL < Action.BOLT.duration, "Bolt volley truncated");
        // After a half-second reaction, walking (0.2 blocks/tick) leaves a centred ring before it bites.
        require((Action.HANDS.impact - 10) * .2 > NecromancerRules.HANDS_RADIUS + .3, "Grasping hands cannot be escaped on foot");
        // Homing tracks at range, but a late sprint-strafe inside three blocks outturns it.
        require(NecromancerRules.BOLT_TURN < Math.atan2(.28, 3), "Soul bolts cannot be sidestepped");

        // Colossus: robed spells never pick melee; the colossus never blinks.
        ready.clear();
        var near = List.of(new Candidate(a, 3, true));
        require(NecromancerRules.choose(near, ready, 0, null, 3, 3) == Action.BLINK, "Robed form lost its blink");
        require(NecromancerRules.chooseColossus(near, ready, 0, null, 5, 5) == Action.SWIPE, "Close player not swiped");
        require(NecromancerRules.chooseColossus(near, ready, 0, Action.SWIPE, 5, 5) == Action.GRAB, "Swipe and grab do not alternate");
        require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 3, true), new Candidate(b, 12, true)), ready, 0, null, 5, 5) != Action.BLINK, "Colossus blinked");
        require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 12, true)), ready, 0, null, 5, 5) == Action.RUSH, "Distant player not lunged at");
        require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 12, true)), ready, 0, null, 0, 5) == Action.RAISE, "Colossus did not refill its army");
        require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 30, false)), ready, 0, null, 5, 5) == null, "Colossus cast without reach or vision");
        // Transformation clock: the body grows before the roar, and both finish inside the cinematic.
        require(0 < NecromancerRules.TRANSFORM_GROW && NecromancerRules.TRANSFORM_GROW < NecromancerRules.TRANSFORM_ROAR
                && NecromancerRules.TRANSFORM_ROAR < NecromancerRules.TRANSFORM_TICKS, "Transformation clock out of order");
        require(NecromancerRules.COLOSSUS_CLEARANCE >= NecromancerRules.COLOSSUS_HEIGHT, "Clearance lower than the body");
        // Grab: never a one-shot, and the team can break it.
        require(NecromancerRules.grabDamage(20) <= 12 && NecromancerRules.grabDamage(20) < 20 && NecromancerRules.grabDamage(10) < 10, "Grab slam can one-shot");
        require(NecromancerRules.grabEscape(600) <= 30 && NecromancerRules.grabEscape(1400) <= 45, "Grab escape too hard");
        require(Action.GRAB.impact < NecromancerRules.GRAB_LIFT_END && NecromancerRules.GRAB_LIFT_END < NecromancerRules.GRAB_SLAM
                && NecromancerRules.GRAB_SLAM < Action.GRAB.duration, "Grab timeline out of order");
        // Swipe: a front arc, jumpable, never behind the skeleton.
        require(NecromancerRules.swipeHits(0, 4, 0, 0), "Swipe missed straight ahead");
        require(!NecromancerRules.swipeHits(0, -4, 0, 0), "Swipe hit behind");
        require(!NecromancerRules.swipeHits(0, 4, 1.2, 0), "A vanilla jump (1.25 blocks) cannot clear the swipe");
        require(!NecromancerRules.swipeHits(0, 7, 0, 0), "Swipe reached beyond its radius");
        require(NecromancerRules.swipeHits(4, 0, 0, Math.toRadians(-90)), "Swipe ignored facing");
        require(NecromancerRules.RUSH_WARNING >= 10, "Rush lacks a sidestep warning");
        require(NecromancerRules.RUSH_SPEED * NecromancerRules.ROOT_TICKS + NecromancerRules.RUSH_REACH >= NecromancerRules.HANDS_RANGE,
                "Hands root expires before a direct rush can reach its farthest target");
        require(NecromancerRules.RUSH_BITE < NecromancerRules.RUSH_THROW && NecromancerRules.RUSH_THROW < NecromancerRules.RUSH_RECOVER,
                "Rush bite/throw/recovery out of order");
        require(!NecromancerRules.canTarget(Action.RUSH, new Candidate(a, 12, false)), "Rush targeted through cover");
        require(NecromancerRules.boltCount(true) > NecromancerRules.boltCount(false) && NecromancerRules.handsRadius(true) > NecromancerRules.handsRadius(false), "Colossus spells are not stronger");
        require(Action.BOLT.impact + (NecromancerRules.boltCount(true) - 1) * NecromancerRules.boltInterval(true) < Action.BOLT.duration, "Colossus volley truncated");
        System.out.println("Necromancer checks passed: scaling, threshold, army caps, drain cap, spell priority, vision, target rotation, telegraph timing, sidestep window, colossus priorities, transformation clock, grab caps, jumpable swipe, rush timing and cover.");
    }

    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
