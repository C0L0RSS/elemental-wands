package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.entity.necromancer.NecromancerRules.Action;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Candidate;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Stage;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Wave;
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

        require(NecromancerRules.health(1) == 800 && NecromancerRules.health(2) == 1300 && NecromancerRules.health(3) == 1800, "Party health scaling moved");
        require(NecromancerRules.threshold(300, 600) && !NecromancerRules.threshold(301, 600), "Half-health boundary moved");

        // Stages: duel, siege, duel, siege, then the colossus; the first duel holds at 75%, the second at half.
        require(Stage.DUEL_A.next() == Stage.SIEGE_1 && Stage.SIEGE_1.next() == Stage.DUEL_B && Stage.DUEL_B.next() == Stage.SIEGE_2
                && Stage.SIEGE_2.next() == Stage.DONE && Stage.DONE.next() == Stage.DONE, "Stage order moved");
        require(NecromancerRules.gate(Stage.DUEL_A) == .75f && NecromancerRules.gate(Stage.DUEL_B) == .5f && NecromancerRules.gate(Stage.DONE) == .5f, "Health gates moved");
        require(Stage.SIEGE_1.siege() && Stage.SIEGE_2.siege() && !Stage.DUEL_A.siege() && !Stage.DUEL_B.siege() && !Stage.DONE.siege(), "Siege stages wrong");
        require(Stage.SIEGE_1.firstWave() == 1 && Stage.SIEGE_1.lastWave() == 2 && Stage.SIEGE_2.firstWave() == 3 && Stage.SIEGE_2.lastWave() == 4, "Siege waves wrong");

        // Waves: the authored duo and solo tables, heavier each wave, scaled for bigger parties under the cap.
        require(NecromancerRules.wave(1, 2).equals(new Wave(5, 0, 0)) && NecromancerRules.wave(2, 2).equals(new Wave(4, 2, 0))
                && NecromancerRules.wave(3, 2).equals(new Wave(4, 2, 1)) && NecromancerRules.wave(4, 2).equals(new Wave(5, 2, 2)), "Duo waves moved");
        require(NecromancerRules.wave(1, 1).equals(new Wave(3, 0, 0)) && NecromancerRules.wave(2, 1).equals(new Wave(3, 1, 0))
                && NecromancerRules.wave(3, 1).equals(new Wave(3, 1, 1)) && NecromancerRules.wave(4, 1).equals(new Wave(3, 2, 1)), "Solo waves moved");
        for (int players = 1; players <= 8; players++) {
            int previous = 0;
            for (int n = 1; n <= 4; n++) {
                Wave wave = NecromancerRules.wave(n, players);
                int weight = wave.crawlers() + 2 * wave.archers() + 4 * wave.brutes();
                require(wave.total() <= NecromancerRules.WAVE_ALIVE_CAP, "Wave " + n + " for " + players + " over the cap: " + wave);
                // Up to four players every wave is heavier; beyond that the later waves may both fill the cap.
                require(players <= 4 ? weight > previous : weight >= previous, "Wave " + n + " for " + players + " is not harder than the last: " + wave);
                if (players > 1) require(wave.total() >= NecromancerRules.wave(n, players - 1).total(), "A bigger party got a smaller wave " + n);
                previous = weight;
            }
        }
        require(NecromancerRules.wave(1, 2).brutes() == 0 && NecromancerRules.wave(2, 2).brutes() == 0 && NecromancerRules.wave(3, 2).brutes() > 0, "Brutes arrive before the second siege");

        // Drain healing stops at its per-channel share however long the channel runs.
        float healed = 0;
        for (int i = 0; i < 40; i++) healed += NecromancerRules.drainHeal(NecromancerRules.DRAIN_DAMAGE, 600, healed);
        require(Math.abs(healed - 600 * NecromancerRules.DRAIN_HEAL_SHARE) < 1e-3, "Drain heal exceeded its cap: " + healed);

        // A close player triggers the escape before anything else.
        var close = List.of(new Candidate(a, 3, true), new Candidate(b, 12, true));
        require(NecromancerRules.choose(close, ready, 0, null, 0, 2) == Action.BLINK, "Close player did not trigger blink");
        // Every two or three casts the caster shifts somewhere new.
        var spread = List.of(new Candidate(a, 10, true), new Candidate(b, 14, true));
        require(NecromancerRules.choose(spread, ready, 0, Action.DRAIN, 2, 2) == Action.SHIFT, "Caster did not reposition");
        require(NecromancerRules.choose(spread, ready, 0, Action.DRAIN, 1, 2) != Action.SHIFT, "Caster repositioned too early");
        // Otherwise spells rotate; no spell repeats while another is ready, and nothing raises an army in a duel.
        require(NecromancerRules.choose(spread, ready, 0, Action.DRAIN, 0, 2) == Action.HANDS, "Spell rotation repeated");
        require(NecromancerRules.choose(spread, ready, 0, null, 0, 2) == Action.DRAIN, "Drain lost priority");
        require(NecromancerRules.choose(spread, ready, 0, Action.HANDS, 0, 2) == Action.DRAIN, "Rotation skipped drain");
        ready.put(Action.DRAIN, 100L);
        require(NecromancerRules.choose(spread, ready, 0, Action.HANDS, 0, 2) == Action.AMBUSH, "Ambush missing from the rotation");
        ready.clear();
        // Ambush needs no vision but never lands on top of a close player; hands need no vision; drain and bolt do.
        var hidden = List.of(new Candidate(a, 10, false));
        ready.put(Action.HANDS, 100L); ready.put(Action.AMBUSH, 100L);
        require(NecromancerRules.choose(hidden, ready, 0, null, 0, 2) == null, "Cast a sight-based spell without vision");
        require(NecromancerRules.choose(hidden, ready, 100, null, 0, 2) == Action.HANDS, "Hands need no vision");
        require(NecromancerRules.choose(hidden, ready, 100, Action.HANDS, 0, 2) == Action.AMBUSH, "Ambush needs no vision");
        require(!NecromancerRules.canTarget(Action.AMBUSH, new Candidate(a, 5, true)), "Ambushed a player already beside it");
        // Bolts remain the fallback even directly after a bolt.
        ready.clear();
        for (Action action : Action.values()) if (action != Action.BOLT) ready.put(action, 1000L);
        require(NecromancerRules.choose(spread, ready, 0, Action.BOLT, 0, 2) == Action.BOLT, "No fallback spell");
        require(NecromancerRules.choose(List.of(new Candidate(a, 40, true)), ready, 0, null, 0, 2) == null, "Bolt fired beyond range");

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
        // Skulls arrive far enough apart to read; each one counts (necromancer_soul bypasses hit immunity).
        require(NecromancerRules.BOLT_INTERVAL >= 8, "Soul bolts bunch into one blur");
        // After a half-second reaction, walking (0.2 blocks/tick) leaves a centred ring before it bites.
        require((Action.HANDS.impact - 10) * .2 > NecromancerRules.HANDS_RADIUS + .3, "Grasping hands cannot be escaped on foot");
        require(NecromancerRules.HANDS_RADIUS >= 4 && NecromancerRules.HANDS_DAMAGE >= 10, "Grasping hands lost their buff");
        require(NecromancerRules.handsModels(NecromancerRules.HANDS_RADIUS) >= 10 && NecromancerRules.handsModels(1.7) == 6, "Hand models do not fill the ring");
        // Hands follow-up: the root lasts until the ambush that follows it has burst.
        require(NecromancerRules.ROOT_TICKS > Action.AMBUSH.impact, "A rooted player walks free before the follow-up ambush");
        // Ambush: a readable tell, a windup long enough to react to, and a heavy hit.
        require(NecromancerRules.AMBUSH_APPEAR >= 10 && Action.AMBUSH.impact - NecromancerRules.AMBUSH_APPEAR >= 12, "Ambush tell or windup too short");
        require(NecromancerRules.AMBUSH_DAMAGE >= 14 && Action.AMBUSH.cooldown >= 160, "Ambush damage or cooldown moved");
        require(NecromancerRules.AMBUSH_RADIUS > NecromancerRules.AMBUSH_BEHIND, "Ambush burst misses the player it appears behind");
        // Homing tracks at range, but a late sprint-strafe inside three blocks outturns it.
        require(NecromancerRules.BOLT_TURN < Math.atan2(.28, 3), "Soul bolts cannot be sidestepped");
        // Siege: waves need time to rise and fight, a stalled wave is joined by the next, the crash rewards.
        require(NecromancerRules.WAVE_BREATHER < NecromancerRules.WAVE_TIMEOUT && NecromancerRules.EXPOSED_MULTIPLIER > 1
                && NecromancerRules.EXPOSED_TICKS >= 100, "Siege timing out of order");
        require(NecromancerRules.BLINK_LEASH > NecromancerRules.MOVEMENT_RANGE && NecromancerRules.BLINK_LEASH < 44, "Blinks leave the crypt clearing");

        // Colossus: robed spells never pick melee; the colossus never blinks or raises an army.
        ready.clear();
        var near = List.of(new Candidate(a, 3, true));
        require(NecromancerRules.choose(near, ready, 0, null, 0, 2) == Action.BLINK, "Robed form lost its blink");
        require(NecromancerRules.chooseColossus(near, ready, 0, null) == Action.SWIPE, "Close player not swiped");
        require(NecromancerRules.chooseColossus(near, ready, 0, Action.SWIPE) == Action.GRAB, "Swipe and grab do not alternate");
        require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 3, true), new Candidate(b, 12, true)), ready, 0, null) != Action.BLINK, "Colossus blinked");
        require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 12, true)), ready, 0, null) == Action.RUSH, "Distant player not rushed");
        for (Action action : Action.values()) if (action.robedOnly())
            require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 12, true)), ready, 0, Action.RUSH) != action, "Colossus used " + action);
        require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 30, false)), ready, 0, null) == null, "Colossus cast without reach or vision");
        // Transformation clock: the body grows before the roar, and both finish inside the cinematic.
        require(0 < NecromancerRules.TRANSFORM_GROW && NecromancerRules.TRANSFORM_GROW < NecromancerRules.TRANSFORM_ROAR
                && NecromancerRules.TRANSFORM_ROAR < NecromancerRules.TRANSFORM_TICKS, "Transformation clock out of order");
        require(NecromancerRules.COLOSSUS_CLEARANCE >= NecromancerRules.COLOSSUS_HEIGHT, "Clearance lower than the body");
        // Grab: never a one-shot, and the team can break it.
        require(NecromancerRules.grabDamage(20) <= 14 && NecromancerRules.grabDamage(20) < 20 && NecromancerRules.grabDamage(10) < 10, "Grab slam can one-shot");
        require(NecromancerRules.grabEscape(800) <= 30 && NecromancerRules.grabEscape(1800) <= 60, "Grab escape too hard");
        require(Action.GRAB.impact < NecromancerRules.GRAB_LIFT_END && NecromancerRules.GRAB_LIFT_END < NecromancerRules.GRAB_SLAM
                && NecromancerRules.GRAB_SLAM < Action.GRAB.duration, "Grab timeline out of order");
        // Swipe: a front arc, jumpable, never behind the skeleton.
        require(NecromancerRules.swipeHits(0, 4, 0, 0), "Swipe missed straight ahead");
        require(!NecromancerRules.swipeHits(0, -4, 0, 0), "Swipe hit behind");
        require(!NecromancerRules.swipeHits(0, 4, 1.2, 0), "A vanilla jump (1.25 blocks) cannot clear the swipe");
        require(!NecromancerRules.swipeHits(0, 7, 0, 0), "Swipe reached beyond its radius");
        require(NecromancerRules.swipeHits(4, 0, 0, Math.toRadians(-90)), "Swipe ignored facing");
        // Rush: a long, readable windup that locks its aim, then a hard bite.
        require(NecromancerRules.RUSH_WARNING >= 30 && NecromancerRules.RUSH_LOCK > 0 && NecromancerRules.RUSH_LOCK < NecromancerRules.RUSH_WARNING, "Rush lacks a readable windup");
        require(NecromancerRules.RUSH_COMBO_WARNING >= 10 && NecromancerRules.RUSH_DAMAGE >= 14 && NecromancerRules.RUSH_TURN <= 1, "Rush combo, damage or steering moved");
        require(NecromancerRules.RUSH_SPEED * (NecromancerRules.ROOT_TICKS - NecromancerRules.RUSH_COMBO_WARNING) + NecromancerRules.RUSH_REACH >= NecromancerRules.HANDS_RANGE,
                "Hands root expires before a combo rush can reach its farthest target");
        require(NecromancerRules.RUSH_BITE < NecromancerRules.RUSH_THROW && NecromancerRules.RUSH_THROW < NecromancerRules.RUSH_RECOVER,
                "Rush bite/throw/recovery out of order");
        require(Action.RUSH.duration >= NecromancerRules.RUSH_WARNING + NecromancerRules.RUSH_TRAVEL / 2, "Rush cast ends before it can run");
        require(!NecromancerRules.canTarget(Action.RUSH, new Candidate(a, 12, false)), "Rush targeted through cover");
        require(NecromancerRules.boltCount(true) > NecromancerRules.boltCount(false) && NecromancerRules.handsRadius(true) > NecromancerRules.handsRadius(false), "Colossus spells are not stronger");
        require(Action.BOLT.impact + (NecromancerRules.boltCount(true) - 1) * NecromancerRules.boltInterval(true) < Action.BOLT.duration, "Colossus volley truncated");
        System.out.println("Necromancer checks passed: scaling, stages and gates, solo/duo/party waves, drain cap, blink/shift/ambush priority, vision, target rotation, telegraph timing, hands buff and follow-up, sidestep window, siege timing, colossus priorities without an army, transformation clock, grab caps, jumpable swipe, rush windup, timing and cover.");
    }

    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
