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
        require(NecromancerRules.wave(1, 2).equals(new Wave(6, 0, 0)) && NecromancerRules.wave(2, 2).equals(new Wave(5, 2, 0))
                && NecromancerRules.wave(3, 2).equals(new Wave(5, 2, 1)) && NecromancerRules.wave(4, 2).equals(new Wave(6, 2, 2)), "Duo waves moved");
        require(NecromancerRules.wave(1, 1).equals(new Wave(4, 0, 0)) && NecromancerRules.wave(2, 1).equals(new Wave(4, 1, 0))
                && NecromancerRules.wave(3, 1).equals(new Wave(4, 1, 1)) && NecromancerRules.wave(4, 1).equals(new Wave(5, 2, 1)), "Solo waves moved");
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
        // Reinforcements: two solo, one more per player, capped; they arrive while a wave still stands.
        require(NecromancerRules.reinforcements(1) == 2 && NecromancerRules.reinforcements(2) == 3 && NecromancerRules.reinforcements(8) == 5, "Reinforcements moved");
        require(NecromancerRules.REINFORCE_MIN > NecromancerRules.WAVE_CLEAR && NecromancerRules.WAVE_BREATHER <= 40 && NecromancerRules.WAVE_TIMEOUT <= 600,
                "Waves rest too long or reinforcements claw out on top of players");
        // Siege bodies are quickened, crawlers most; a quickened crawler's walk stays inside the 2x playback clamp.
        require(NecromancerRules.quickening(NecromancerRules.Undead.CRAWLER) > NecromancerRules.quickening(NecromancerRules.Undead.BRUTE)
                && NecromancerRules.quickening(NecromancerRules.Undead.BRUTE) > NecromancerRules.quickening(NecromancerRules.Undead.ARCHER)
                && NecromancerRules.quickening(NecromancerRules.Undead.ARCHER) > 0, "Siege quickening out of order");
        double crawler = 2.159 * Math.pow(.19 * (1 + NecromancerRules.quickening(NecromancerRules.Undead.CRAWLER)), 2) * 20;
        require(crawler > 2.3 && crawler < 4.3 && crawler / com.anton.elementalwands.entity.undead.HollowUndeadClips.CRAWLER_STRIDE <= 2,
                "Quickened crawlers are not faster than a zombie, outrun a walking player or overrun their walk clip: " + crawler);
        // Soul Fire Rain: a marker on every player plus spares, faster in the second siege, escapable on foot, heavy.
        require(NecromancerRules.rainMarkers(1, Stage.SIEGE_1) == 2 && NecromancerRules.rainMarkers(1, Stage.SIEGE_2) == 3
                && NecromancerRules.rainMarkers(2, Stage.SIEGE_1) == 3 && NecromancerRules.rainMarkers(12, Stage.SIEGE_2) == 8, "Rain markers moved");
        require(NecromancerRules.rainEvery(Stage.SIEGE_2) < NecromancerRules.rainEvery(Stage.SIEGE_1)
                && NecromancerRules.rainEvery(Stage.SIEGE_2) > NecromancerRules.RAIN_WARNING, "Rain volleys overlap or do not escalate");
        require((NecromancerRules.RAIN_WARNING - 10) * .2 > NecromancerRules.RAIN_RADIUS + .3 && NecromancerRules.RAIN_DAMAGE >= 10,
                "A fireball marker cannot be walked out of, or its blast is chip damage");
        // Soul light: real block light levels; a rain marker brightens to full in at most four light
        // changes; a glow outlives a tick's refresh (block ticks run before entities) but not much more.
        for (int level : new int[] {NecromancerRules.GLOW_FIREBALL, NecromancerRules.GLOW_BOLT, NecromancerRules.GLOW_HARVEST,
                NecromancerRules.GLOW_SOUL, NecromancerRules.GLOW_IMPACT}) require(level >= 1 && level <= 15, "Soul light level out of range: " + level);
        int glowSteps = 0;
        for (int age = 0, last = 0; age <= NecromancerRules.RAIN_WARNING; age++) {
            int glow = NecromancerRules.rainGlow(age);
            require(glow >= last, "A rain marker dimmed as its fireball closed in");
            if (glow != last) glowSteps++;
            last = glow;
        }
        require(glowSteps <= 4 && NecromancerRules.rainGlow(NecromancerRules.RAIN_WARNING) == 15 && NecromancerRules.GLOW_IMPACT == 15,
                "Rain marker light rebuilds too often or never reaches full: " + glowSteps);
        require(NecromancerRules.GLOW_LINGER >= 2 && NecromancerRules.GLOW_LINGER <= 5 && NecromancerRules.GLOW_IMPACT_TICKS <= 20,
                "Soul light flickers between refreshes or lingers behind its spell");

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
        // Drain only starts on a close player, and a sprint (5.6 blocks a second) through its windup escapes the break range.
        require(!NecromancerRules.canTarget(Action.DRAIN, new Candidate(a, 12, true)) && NecromancerRules.canTarget(Action.DRAIN, new Candidate(a, 9, true)),
                "Drain range moved");
        require(Action.DRAIN.impact >= 30 && NecromancerRules.DRAIN_BREAK_RANGE - NecromancerRules.DRAIN_RANGE < 5.6 * Action.DRAIN.impact / 20.0,
                "Drain windup too short to step out of");
        require((Action.DRAIN.duration - Action.DRAIN.impact) / NecromancerRules.DRAIN_INTERVAL >= 5, "Drain lost its pulses");
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
        var far = List.of(new Candidate(a, 12, true));
        require(NecromancerRules.choose(near, ready, 0, null, 0, 2) == Action.BLINK, "Robed form lost its blink");
        require(NecromancerRules.chooseColossus(near, ready, 0, null, false) == Action.SWIPE, "Close player not swiped");
        require(NecromancerRules.chooseColossus(near, ready, 0, Action.SWIPE, false) == Action.GRAB, "Swipe and grab do not alternate");
        require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 3, true), new Candidate(b, 12, true)), ready, 0, null, false) != Action.BLINK, "Colossus blinked");
        // Standing off draws the Grave Dive first, sight or not; the rush follows it.
        require(NecromancerRules.chooseColossus(far, ready, 0, null, false) == Action.DIVE, "Distant player not dived after");
        require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 30, false)), ready, 0, null, false) == Action.DIVE, "A hidden kiter escaped the dive");
        require(!NecromancerRules.canTarget(Action.DIVE, new Candidate(a, 5, true)), "Dived at a player already within reach");
        ready.put(Action.SWIPE, 1000L); ready.put(Action.GRAB, 1000L);
        require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 5, true), new Candidate(b, 12, true)), ready, 0, null, false) == Action.RUSH,
                "Dived while a player stood within reach");
        ready.clear(); ready.put(Action.DIVE, 1000L);
        require(NecromancerRules.chooseColossus(far, ready, 0, null, false) == Action.RUSH, "Distant player not rushed");
        require(NecromancerRules.chooseColossus(far, ready, 0, Action.RUSH, false) == Action.HARVEST, "Harvest missing from the rotation");
        for (Action action : Action.values()) if (action.robedOnly())
            require(NecromancerRules.chooseColossus(far, ready, 0, Action.RUSH, false) != action, "Colossus used " + action);
        require(NecromancerRules.chooseColossus(List.of(new Candidate(a, 60, false)), ready, 0, null, false) == null, "Colossus cast beyond the encounter");
        // With its soul out the body only fights: no bolts, hands, drain or harvest.
        ready.clear();
        for (double distance : new double[]{3, 5, 12, 20, 30})
            for (Action previous : new Action[]{null, Action.SWIPE, Action.DIVE, Action.RUSH}) {
                Action chosen = NecromancerRules.chooseColossus(List.of(new Candidate(a, distance, true)), ready, 0, previous, true);
                require(chosen == null || !chosen.soulSpell(), "The body cast " + chosen + " while its soul was out");
            }
        ready.put(Action.DIVE, 1000L); ready.put(Action.RUSH, 1000L);
        require(NecromancerRules.chooseColossus(far, ready, 0, null, true) == null, "The body cast a spell while its soul was out");
        require(Action.BOLT.soulSpell() && Action.HANDS.soulSpell() && Action.DRAIN.soulSpell() && Action.HARVEST.soulSpell()
                && !Action.SWIPE.soulSpell() && !Action.DIVE.soulSpell(), "Soul spells moved");
        // The colossus keeps pace with a walking player (4.3 blocks/s) but not a sprint (5.6).
        double colossusPace = 2.159 * NecromancerRules.COLOSSUS_SPEED * NecromancerRules.COLOSSUS_SPEED * 20;
        require(colossusPace >= 4.3 && colossusPace < 5.6, "Colossus pace moved: " + colossusPace);
        // Grave Dive: tunnels faster than a sprint, a player already moving walks out of the crack ring,
        // the eruption hits hard, and a miss leaves a long opening. The whole dive fits its cast.
        require(NecromancerRules.DIVE_SPEED > .28 && NecromancerRules.DIVE_WARNING * .216 > NecromancerRules.DIVE_RADIUS + .3,
                "The dive cannot catch a sprinter, or its ring cannot be walked out of");
        require(NecromancerRules.DIVE_DAMAGE >= 14 && NecromancerRules.DIVE_STUCK >= 60 && NecromancerRules.DIVE_UNDER < NecromancerRules.DIVE_SINK,
                "Dive damage, opening or sink moved");
        require(Action.DIVE.duration >= NecromancerRules.DIVE_SINK + NecromancerRules.DIVE_TUNNEL + NecromancerRules.DIVE_WARNING
                + NecromancerRules.DIVE_ERUPT + NecromancerRules.DIVE_STUCK + NecromancerRules.DIVE_HAUL, "Dive timeline longer than its cast");
        // Soul Harvest: enough time to shoot the souls down, and never more than a tenth healed at once.
        require(NecromancerRules.harvestSouls(1) == 3 && NecromancerRules.harvestSouls(2) == 4 && NecromancerRules.harvestSouls(8) == 6, "Harvest souls moved");
        require((NecromancerRules.HARVEST_MIN - NecromancerRules.HARVEST_ABSORB) / NecromancerRules.HARVEST_SPEED >= 120,
                "Harvested souls reach the ribcage too fast to shoot down");
        for (int players = 1; players <= 8; players++)
            require(NecromancerRules.HARVEST_HEAL * NecromancerRules.harvestSouls(players) <= .18f, "A full harvest heals too much");
        // The call reads in order: scream, a second of glowing spots, then the souls, all inside the cast.
        require(Action.HARVEST.impact == NecromancerRules.HARVEST_SCREAM && NecromancerRules.HARVEST_GLOW >= 20
                && Action.HARVEST.duration > NecromancerRules.HARVEST_SCREAM + NecromancerRules.HARVEST_GLOW, "Harvest call out of order");
        // The caster inside: a knockdown within reach, a real collapse, and a soul out of melee but not out of range.
        require(NecromancerRules.soulKnockdown(800) == 30 && NecromancerRules.soulKnockdown(1300) == 78 && NecromancerRules.soulKnockdown(300) == 30,
                "Soul knockdown moved");
        require(NecromancerRules.SPLIT_RELEASE < NecromancerRules.SPLIT_TELL && NecromancerRules.COLLAPSE_TICKS >= 80, "Split clock out of order");
        // The blind body's marked arc leads its turn (at most ~9 ticks for a half turn) and lands after the turn.
        require(180 / NecromancerRules.FLAIL_TURN < Action.SWIPE.impact && NecromancerRules.FLAIL_GAP_MIN >= 10
                && NecromancerRules.FLAIL_GAP_MAX > NecromancerRules.FLAIL_GAP_MIN, "Blind swipes land before they turn or never rest");
        require(NecromancerRules.SOUL_NEAR > NecromancerRules.SOUL_THREAT && NecromancerRules.SOUL_LOW >= 2.5 && NecromancerRules.SOUL_HIGH <= 6
                && NecromancerRules.SOUL_FAR < NecromancerRules.BOLT_RANGE, "The soul hovers out of reach or on top of players");
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
        // Rush: a windup of random length (its rhythm cannot be counted) that locks its aim, then a hard bite.
        require(NecromancerRules.RUSH_WARNING_MIN >= 24 && NecromancerRules.RUSH_WARNING_MAX - NecromancerRules.RUSH_WARNING_MIN >= 12
                && NecromancerRules.RUSH_LOCK > 0 && NecromancerRules.RUSH_CROUCH + NecromancerRules.RUSH_LOCK < NecromancerRules.RUSH_WARNING_MIN, "Rush lacks a readable windup");
        var random = new java.util.Random(7);
        int shortest = Integer.MAX_VALUE, longest = 0;
        for (int i = 0; i < 500; i++) { int w = NecromancerRules.rushWarning(random); shortest = Math.min(shortest, w); longest = Math.max(longest, w); }
        require(shortest == NecromancerRules.RUSH_WARNING_MIN && longest == NecromancerRules.RUSH_WARNING_MAX, "Rush warning range not covered");
        require(NecromancerRules.RUSH_COMBO_WARNING >= 10 && NecromancerRules.RUSH_DAMAGE >= 14 && NecromancerRules.RUSH_TURN <= 1.5, "Rush combo, damage or steering moved");
        require(NecromancerRules.RUSH_SPEED * (NecromancerRules.ROOT_TICKS - NecromancerRules.RUSH_COMBO_WARNING) + NecromancerRules.RUSH_REACH >= NecromancerRules.HANDS_RANGE,
                "Hands root expires before a combo rush can reach its farthest target");
        require(NecromancerRules.RUSH_BITE < NecromancerRules.RUSH_THROW && NecromancerRules.RUSH_THROW < NecromancerRules.RUSH_RECOVER,
                "Rush bite/throw/recovery out of order");
        require(Action.RUSH.duration >= NecromancerRules.RUSH_WARNING_MAX + NecromancerRules.RUSH_TRAVEL / 2, "Rush cast ends before it can run");
        require(!NecromancerRules.canTarget(Action.RUSH, new Candidate(a, 12, false)), "Rush targeted through cover");
        require(NecromancerRules.boltCount(true) > NecromancerRules.boltCount(false) && NecromancerRules.handsRadius(true) > NecromancerRules.handsRadius(false), "Colossus spells are not stronger");
        require(Action.BOLT.impact + (NecromancerRules.boltCount(true) - 1) * NecromancerRules.boltInterval(true) < Action.BOLT.duration, "Colossus volley truncated");
        // Intro cinematic: the shots follow the plan's order, the soul lands in the staff on time, and everyone may skip.
        int[] beats = {0, NecromancerIntro.WALK_END, NecromancerIntro.ARCH, NecromancerIntro.PULL, NecromancerIntro.FREE, NecromancerIntro.CRUMBLE,
                NecromancerIntro.REVEAL, NecromancerIntro.SOUL_ARRIVE, NecromancerIntro.EYES, NecromancerIntro.TURN, NecromancerIntro.HERO_END,
                NecromancerIntro.TURN_END, NecromancerIntro.LEVEL, NecromancerIntro.SLAM, NecromancerIntro.RING_END, NecromancerIntro.RETURN, NecromancerIntro.LENGTH};
        for (int i = 1; i < beats.length; i++) require(beats[i - 1] < beats[i], "Intro beats out of order at " + i);
        require(NecromancerIntro.LENGTH <= 20 * 20 && NecromancerIntro.SKIP_AFTER < NecromancerIntro.WALK_END, "Intro too long or unskippable");
        require(NecromancerIntro.TITLE > NecromancerIntro.SLAM && NecromancerIntro.TITLE_END <= NecromancerIntro.RETURN,
                "Title must finish before the camera hands back");
        require(NecromancerIntro.TITLE_END - NecromancerIntro.TITLE >= 20 * 3
                && NecromancerIntro.LEVEL_RELEASE - NecromancerIntro.LEVEL_HOLD >= 20,
                "The ending needs a readable title and a patient pointing hold");
        var staff = NecromancerIntro.STAFF_HEAD;
        var chest = NecromancerIntro.VICTIM_TO.add(0, NecromancerIntro.VICTIM_CHEST, 0);
        require(NecromancerIntro.tornAt(chest, staff, NecromancerIntro.SOUL_ARRIVE - NecromancerIntro.PULL).distanceTo(staff) < .01, "Torn soul misses the staff");
        require(NecromancerIntro.tornAt(chest, staff, 0).distanceTo(chest) < .1, "Torn soul does not start in the chest");
        require(NecromancerIntro.victimAt(NecromancerIntro.WALK_END).distanceTo(NecromancerIntro.VICTIM_TO) < .01, "Zombie does not stop at its mark");
        require(Math.abs(NecromancerIntro.facing(NecromancerIntro.PULL) - NecromancerIntro.PULL_FACING) < .01 && NecromancerIntro.PULL_FACING > 90
                && Math.abs(NecromancerIntro.facing(NecromancerIntro.TURN_END)) < .01, "He must face the zombie while hauling and the players before the slam");
        require(NecromancerIntro.ringProgress(NecromancerIntro.SLAM - 1) == 0
                && NecromancerIntro.ringProgress(NecromancerIntro.SLAM) == 0
                && NecromancerIntro.ringProgress(NecromancerIntro.RING_END) == 1
                && NecromancerIntro.ringProgress(NecromancerIntro.RING_END + 1) == 1,
                "Arena lighting must reveal with the fire front and settle at normal brightness");
        System.out.println("Necromancer checks passed: intro beats, torn soul path and skip window; scaling, stages and gates, solo/duo/party waves, reinforcements, quickening, soul fire rain, soul light, drain cap, drain windup and range, blink/shift/ambush priority, vision, target rotation, telegraph timing, hands buff and follow-up, sidestep window, siege timing, colossus priorities without an army, dive/rush/harvest choice, soul-split restrictions, colossus pace, grave dive, harvest, soul knockdown, one blind-swinging split, transformation clock, grab caps, jumpable swipe, random rush windup, timing and cover.");
    }

    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
