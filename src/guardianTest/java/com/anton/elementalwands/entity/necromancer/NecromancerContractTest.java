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
        System.out.println("Necromancer checks passed: scaling, threshold, army caps, drain cap, spell priority, vision, target rotation, telegraph timing, sidestep window.");
    }

    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
