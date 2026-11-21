package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.entity.necromancer.NecromancerRules.Action;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Candidate;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Read;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Seen;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Self;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Stage;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Tell;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Temper;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Wave;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Pure rule checks; run by the Gradle check task without launching Minecraft. */
public final class NecromancerContractTest {
    public static void main(String[] args) {
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        Predicate<Action> all = action -> true;
        Self calm = Self.CALM, wounded = new Self(true, false, false, true, false), hounded = new Self(false, true, false, true, false),
                stale = new Self(false, false, true, true, false), crowded = new Self(false, false, false, false, false),
                frenzy = new Self(true, false, false, true, true);

        require(NecromancerRules.health(1) == 800 && NecromancerRules.health(2) == 1300 && NecromancerRules.health(3) == 1800, "Party health scaling moved");
        require(NecromancerRules.threshold(300, 600) && !NecromancerRules.threshold(301, 600), "Half-health boundary moved");

        // Stages: duel, siege, duel, siege, then the colossus; the first duel holds at 75%, the second at half.
        require(Stage.DUEL_A.next() == Stage.SIEGE_1 && Stage.SIEGE_1.next() == Stage.DUEL_B && Stage.DUEL_B.next() == Stage.SIEGE_2
                && Stage.SIEGE_2.next() == Stage.DONE && Stage.DONE.next() == Stage.DONE, "Stage order moved");
        require(NecromancerRules.gate(Stage.DUEL_A) == .75f && NecromancerRules.gate(Stage.DUEL_B) == .5f && NecromancerRules.gate(Stage.DONE) == .5f, "Health gates moved");
        require(Stage.SIEGE_1.siege() && Stage.SIEGE_2.siege() && !Stage.DUEL_A.siege() && !Stage.DUEL_B.siege() && !Stage.DONE.siege(), "Siege stages wrong");
        require(Stage.SIEGE_1.firstWave() == 1 && Stage.SIEGE_1.lastWave() == 2 && Stage.SIEGE_2.firstWave() == 3 && Stage.SIEGE_2.lastWave() == 4, "Siege waves wrong");

        // Waves: the authored duo and solo hordes, heavier each wave, scaled for bigger parties under the cap.
        require(NecromancerRules.wave(1, 2).equals(new Wave(14, 0, 0)) && NecromancerRules.wave(2, 2).equals(new Wave(14, 3, 0))
                && NecromancerRules.wave(3, 2).equals(new Wave(15, 3, 2)) && NecromancerRules.wave(4, 2).equals(new Wave(17, 4, 3)), "Duo waves moved");
        require(NecromancerRules.wave(1, 1).equals(new Wave(10, 0, 0)) && NecromancerRules.wave(2, 1).equals(new Wave(10, 2, 0))
                && NecromancerRules.wave(3, 1).equals(new Wave(11, 2, 1)) && NecromancerRules.wave(4, 1).equals(new Wave(13, 3, 2)), "Solo waves moved");
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
        // Reinforcements: four solo, one more per player, capped; they arrive while a wave still stands.
        require(NecromancerRules.reinforcements(1) == 4 && NecromancerRules.reinforcements(2) == 5 && NecromancerRules.reinforcements(8) == 8, "Reinforcements moved");
        require(NecromancerRules.REINFORCE_MIN > NecromancerRules.WAVE_CLEAR && NecromancerRules.WAVE_BREATHER <= 40 && NecromancerRules.WAVE_TIMEOUT <= 600,
                "Waves rest too long or reinforcements claw out on top of players");
        // Siege bodies are quickened, crawlers most; a quickened crawler's walk stays inside the playback clamp.
        require(NecromancerRules.quickening(NecromancerRules.Undead.CRAWLER) > NecromancerRules.quickening(NecromancerRules.Undead.BRUTE)
                && NecromancerRules.quickening(NecromancerRules.Undead.BRUTE) > NecromancerRules.quickening(NecromancerRules.Undead.ARCHER)
                && NecromancerRules.quickening(NecromancerRules.Undead.ARCHER) > 0, "Siege quickening out of order");
        double crawler = 2.159 * Math.pow(.19 * (1 + NecromancerRules.quickening(NecromancerRules.Undead.CRAWLER)), 2) * 20;
        // ...and hit harder than their wild kin, by a share: a buff, not a new monster. The horde's claws gain the most.
        for (var kind : NecromancerRules.Undead.values())
            require(NecromancerRules.empowerment(kind) > 0 && NecromancerRules.empowerment(kind) <= .5, "Siege damage buff out of range for " + kind);
        require(NecromancerRules.empowerment(NecromancerRules.Undead.CRAWLER) > NecromancerRules.empowerment(NecromancerRules.Undead.BRUTE), "Siege damage buff out of order");
        require(crawler > 4.32 && crawler < 5.6 && crawler / com.anton.elementalwands.entity.undead.HollowUndeadClips.CRAWLER_STRIDE
                <= com.anton.elementalwands.entity.undead.HollowUndeadEntity.MAX_WALK_PLAYBACK,
                "Quickened crawlers can be walked away from, outrun a sprint or overrun their walk clip: " + crawler);
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
                NecromancerRules.GLOW_IMPACT}) require(level >= 1 && level <= 15, "Soul light level out of range: " + level);
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

        // On top of him: he blinks away, and a player he has just fled gets the burst. A memory, not a roll;
        // each falls back on the other when it is not ready.
        require(only(NecromancerRules.robedWeights(Read.at(3), calm, null, null, all)) == Action.BLINK, "Close player did not trigger blink");
        require(only(NecromancerRules.robedWeights(Read.at(3, Tell.PRESSING), calm, null, null, all)) == Action.BURST, "A player who chased him down was not burst");
        require(only(NecromancerRules.robedWeights(Read.at(3), calm, null, null, x -> x != Action.BLINK)) == Action.BURST, "No answer to a close player with blink spent");
        require(only(NecromancerRules.robedWeights(Read.at(3, Tell.PRESSING), calm, null, null, x -> x != Action.BURST)) == Action.BLINK, "No answer to a pressing player with burst spent");
        require(!NecromancerRules.robedWeights(Read.at(3), calm, null, null, x -> x != Action.BLINK && x != Action.BURST).isEmpty(), "Stood idle beside a player with both escapes spent");
        // The slam is only chosen for a player inside it, so it cannot be baited from a step outside; and a player he
        // has already fled who hangs there is fought like anyone at that range, not fled again.
        var hanging = NecromancerRules.robedWeights(Read.at(4.2, Tell.PRESSING), calm, null, null, all);
        require(!hanging.containsKey(Action.BURST) && !hanging.containsKey(Action.BLINK) && hanging.size() > 1, "A player just outside the slam drew it, or another blink");
        require(only(NecromancerRules.robedWeights(Read.at(4.2), calm, null, null, all)) == Action.BLINK, "A player at the blink's edge was not blinked from");
        require(!NecromancerRules.robedWeights(Read.at(4.2), calm, null, null, x -> x != Action.BLINK).containsKey(Action.BURST), "The slam was chosen for a player it cannot reach");
        // The burst is smaller than the ambush's: a player a block from him who reacts in a quarter second sprints out.
        require((Action.BURST.impact - 5) * .28 > NecromancerRules.BURST_RADIUS - 1 && NecromancerRules.BURST_RADIUS < NecromancerRules.AMBUSH_RADIUS
                && Action.BURST.duration - Action.BURST.impact >= 20, "The burst cannot be stepped out of, or leaves no opening");
        // Range decides the family: drain only up close, bolts the staple at range, and no move without a reason.
        var mid = NecromancerRules.robedWeights(Read.at(8), calm, null, null, all);
        var ranged = NecromancerRules.robedWeights(Read.at(16), calm, null, null, all);
        require(mid.keySet().equals(java.util.Set.of(Action.BOLT, Action.HANDS, Action.AMBUSH, Action.DRAIN)), "Mid-range kit wrong: " + mid);
        require(ranged.keySet().equals(java.util.Set.of(Action.BOLT, Action.HANDS, Action.AMBUSH)) && heaviest(ranged) == Action.BOLT, "Ranged kit wrong: " + ranged);
        // What the player does tilts it. Standing still or eating draws the ambush, and eating cuts his rest short.
        require(share(NecromancerRules.robedWeights(Read.at(12, Tell.STILL), calm, null, null, all), Action.AMBUSH) > .5, "A still player was not ambushed");
        require(share(NecromancerRules.robedWeights(Read.at(12, Tell.HEALING), calm, null, null, all), Action.AMBUSH) > .5, "An eating player was not ambushed");
        require(NecromancerRules.punishes(Read.at(12, Tell.HEALING)) && !NecromancerRules.punishes(Read.at(12, Tell.STILL)), "Only healing cuts the rest short");
        // Hiding: only what needs no sight, and once they have stayed hidden he comes round the cover.
        var lost = NecromancerRules.robedWeights(Read.unseen(12), calm, null, null, all);
        var hiding = NecromancerRules.robedWeights(Read.unseen(12, Tell.HIDDEN), calm, null, null, all);
        require(lost.keySet().equals(java.util.Set.of(Action.HANDS, Action.AMBUSH)), "Cast a sight-based spell without vision: " + lost);
        require(hiding.keySet().equals(java.util.Set.of(Action.HANDS, Action.AMBUSH, Action.SHIFT)), "A hidden player did not draw him round: " + hiding);
        require(heaviest(NecromancerRules.robedWeights(Read.at(12, Tell.BUNCHED), calm, null, null, all)) == Action.HANDS, "Players standing together did not draw the hands");
        // A hovering player: nothing from the ground, only skulls.
        require(only(NecromancerRules.robedWeights(Read.at(12, Tell.AIRBORNE), calm, null, null, all)) == Action.BOLT, "Cast a ground spell at a hovering player");
        // He drains when wounded, and shifts for a reason: heavy fire, a stale spot, a player out of reach. Never straight after a move.
        require(heaviest(NecromancerRules.robedWeights(Read.at(8), wounded, null, null, all)) == Action.DRAIN
                && share(mid, Action.DRAIN) < .2, "Drain does not follow his wounds");
        require(heaviest(NecromancerRules.robedWeights(Read.at(12), hounded, null, null, all)) == Action.SHIFT, "Heavy fire did not move him");
        require(NecromancerRules.robedWeights(Read.at(12), stale, null, null, all).containsKey(Action.SHIFT)
                && share(NecromancerRules.robedWeights(Read.at(12), stale, null, null, all), Action.SHIFT) < .25, "A stale spot should only nudge him to move");
        require(NecromancerRules.robedWeights(Read.at(26), calm, null, null, all).containsKey(Action.SHIFT), "He did not close on a player beyond his hands");
        for (Action move : new Action[]{Action.BLINK, Action.SHIFT})
            require(!NecromancerRules.robedWeights(Read.at(12), hounded, move, null, all).containsKey(Action.SHIFT), "Two moves in a row after " + move);
        // Never the same cast three times running, seldom twice; the bolt remains when it is all he has.
        require(!NecromancerRules.robedWeights(Read.at(16), calm, Action.BOLT, Action.BOLT, all).containsKey(Action.BOLT), "A third bolt volley in a row");
        require(share(NecromancerRules.robedWeights(Read.at(16), calm, Action.BOLT, null, all), Action.BOLT) < share(ranged, Action.BOLT), "A repeat is not made less likely");
        require(only(NecromancerRules.robedWeights(Read.at(16), calm, Action.BOLT, Action.BOLT, x -> x == Action.BOLT)) == Action.BOLT, "No fallback spell");
        require(NecromancerRules.robedWeights(Read.unseen(60), calm, null, null, all).isEmpty(), "Cast beyond the encounter");
        for (double distance : new double[]{3, 8, 16, 26, 40}) for (Self self : new Self[]{calm, wounded, hounded, stale})
            for (Action action : NecromancerRules.robedWeights(Read.at(distance, Tell.STILL, Tell.BUNCHED), self, null, null, all).keySet())
                require(!action.colossusOnly(), "The robed caster used " + action);
        // Drain only starts on a close player, and a sprint (5.6 blocks a second) through its windup escapes the break range.
        require(!NecromancerRules.canTarget(Action.DRAIN, new Candidate(a, 12, true)) && NecromancerRules.canTarget(Action.DRAIN, new Candidate(a, 9, true)),
                "Drain range moved");
        require(Action.DRAIN.impact >= 30 && NecromancerRules.DRAIN_BREAK_RANGE - NecromancerRules.DRAIN_RANGE < 5.6 * Action.DRAIN.impact / 20.0,
                "Drain windup too short to step out of");
        require((Action.DRAIN.duration - Action.DRAIN.impact) / NecromancerRules.DRAIN_INTERVAL >= 5, "Drain lost its pulses");
        require(!NecromancerRules.canTarget(Action.AMBUSH, new Candidate(a, 5, true)), "Ambushed a player already beside it");
        require(!NecromancerRules.canTarget(Action.BOLT, new Candidate(a, 40, true)), "Bolt fired beyond range");

        // Who it turns on: whoever it has left alone longest, nearest breaking ties; eating and standing still move a
        // player up, and inside a string it turns on someone else.
        Map<UUID, Long> last = new HashMap<>();
        var three = List.of(new Seen(a, Read.at(9)), new Seen(b, Read.at(5)), new Seen(c, Read.at(7)));
        require(NecromancerRules.focus(three, last, 400, null).id().equals(b), "Tie did not prefer nearest");
        last.put(b, 390L); last.put(c, 380L);
        require(NecromancerRules.focus(three, last, 400, null).id().equals(a), "Rotation skipped the untargeted player");
        last.put(a, 395L);
        require(NecromancerRules.focus(three, last, 400, null).id().equals(c), "Rotation ignored oldest target");
        require(NecromancerRules.focus(three, last, 400, c).id().equals(b), "A string did not turn on someone else");
        require(NecromancerRules.focus(List.of(new Seen(a, Read.at(9))), last, 400, a).id().equals(a), "A lone player cannot be avoided");
        var eater = List.of(new Seen(a, Read.at(9, Tell.HEALING)), new Seen(b, Read.at(5)), new Seen(c, Read.at(7)));
        require(NecromancerRules.focus(eater, last, 400, null).id().equals(a), "An eating player was not picked out");
        last.put(a, 400L); last.put(c, 400L - NecromancerRules.NEGLECT);
        require(NecromancerRules.focus(eater, last, 400, null).id().equals(c), "A player left alone for ten seconds must come first");
        var idler = List.of(new Seen(a, Read.at(9, Tell.HEALING, Tell.STILL)), new Seen(b, Read.at(5)), new Seen(c, Read.at(7)));
        require(NecromancerRules.focus(idler, last, 400, null).id().equals(c), "Ten seconds alone must outrank a player both eating and standing still");
        last.put(a, 400L - NecromancerRules.NEGLECT + 1);
        require(NecromancerRules.focus(idler, last, 400, null).id().equals(c) && NecromancerRules.overdue(c, last, 400) && !NecromancerRules.overdue(a, last, 400),
                "An overdue player lost their turn to one who is nearly overdue and eating");
        // The frenzy's casts come back sooner; nothing else's cooldown moves.
        for (Action action : Action.values()) {
            require(NecromancerRules.recast(action, Temper.COLOSSUS) == action.duration + action.cooldown && NecromancerRules.recast(action, Temper.SECOND_DUEL) == action.duration + action.cooldown,
                    action + " changed its cooldown outside the frenzy");
            require(NecromancerRules.cooldown(action, Temper.COLOSSUS) == action.cooldown && NecromancerRules.cooldown(action, Temper.FRENZY) < action.cooldown
                    && NecromancerRules.recast(action, Temper.FRENZY) == action.duration + NecromancerRules.cooldown(action, Temper.FRENZY), action + " keeps its full cooldown in the frenzy");
            require(NecromancerRules.recast(action, Temper.FRENZY) < NecromancerRules.recast(action, Temper.COLOSSUS) && NecromancerRules.recast(action, Temper.FRENZY) > action.duration + action.cooldown / 2,
                    action + " does not come back sooner, or comes back too soon, in the frenzy");
        }

        // Strings and rests. A move always goes straight into a cast; his openings never chain; the second duel and
        // every extra player press harder, and no rest shrinks to nothing.
        require(NecromancerRules.temper(Stage.DUEL_A, false, false) == Temper.FIRST_DUEL && NecromancerRules.temper(Stage.DUEL_B, false, false) == Temper.SECOND_DUEL
                && NecromancerRules.temper(Stage.DONE, true, false) == Temper.COLOSSUS && NecromancerRules.temper(Stage.DONE, true, true) == Temper.FRENZY, "Tempers moved");
        for (Temper temper : Temper.values()) {
            require(NecromancerRules.chainChance(temper, Action.BLINK, 1) == 1 && NecromancerRules.chainChance(temper, Action.SHIFT, 1) == 1, "A move was not followed at once");
            for (Action opening : new Action[]{Action.AMBUSH, Action.BURST, Action.DRAIN, Action.DIVE, Action.HARVEST})
                require(NecromancerRules.chainChance(temper, opening, 4) == 0, opening + " leaves no opening in " + temper);
            for (int players = 1; players <= 8; players++) {
                require(NecromancerRules.chainChance(temper, Action.BOLT, players) <= .9, "Strings never end with " + players + " players");
                require(NecromancerRules.rest(temper, players) >= 6 && NecromancerRules.rest(temper, players) <= NecromancerRules.RECOVERY_GAP, "Rest out of range");
                if (players > 1) require(NecromancerRules.rest(temper, players) <= NecromancerRules.rest(temper, players - 1)
                        && NecromancerRules.chainChance(temper, Action.BOLT, players) >= NecromancerRules.chainChance(temper, Action.BOLT, players - 1), "A bigger party got an easier pace");
            }
            require(temper.limit >= 2 && temper.limit <= 3, "String length moved");
        }
        require(NecromancerRules.chainChance(Temper.SECOND_DUEL, Action.BOLT, 1) > NecromancerRules.chainChance(Temper.FIRST_DUEL, Action.BOLT, 1)
                && NecromancerRules.chainChance(Temper.FRENZY, Action.SWIPE, 1) > NecromancerRules.chainChance(Temper.COLOSSUS, Action.SWIPE, 1)
                && NecromancerRules.rest(Temper.FRENZY, 1) < NecromancerRules.rest(Temper.COLOSSUS, 1)
                && NecromancerRules.rest(Temper.FIRST_DUEL, 2) < NecromancerRules.rest(Temper.FIRST_DUEL, 1), "The fight does not get meaner");
        // The roll covers every weight and nothing else.
        var two = new java.util.EnumMap<Action, Double>(Action.class);
        two.put(Action.BOLT, 1.0); two.put(Action.HANDS, 3.0);
        require(NecromancerRules.pick(two, () -> 0) == Action.HANDS && NecromancerRules.pick(two, () -> .74) == Action.HANDS
                && NecromancerRules.pick(two, () -> .76) == Action.BOLT && NecromancerRules.pick(two, () -> .999) == Action.BOLT
                && NecromancerRules.pick(new java.util.EnumMap<Action, Double>(Action.class), () -> .5) == null, "Weighted pick wrong");

        // Every cast lands inside its own duration; bolts all leave before the cast ends.
        for (Action action : Action.values()) require(action.impact < action.duration, action + " impact after cast end");
        require(Action.BOLT.impact + (NecromancerRules.BOLT_COUNT - 1) * NecromancerRules.BOLT_INTERVAL < Action.BOLT.duration, "Bolt volley truncated");
        // The extra skull for a noticed Astral Double also leaves in time, in both forms, well within the double's life.
        for (boolean colossus : new boolean[]{false, true})
            require(Action.BOLT.impact + NecromancerRules.boltCount(colossus) * NecromancerRules.boltInterval(colossus) < Action.BOLT.duration, "Double skull truncated");
        require(NecromancerRules.DOUBLE_NOTICE > 0 && NecromancerRules.DOUBLE_NOTICE < com.anton.elementalwands.util.AstralDoubleManager.LIFETIME / 2,
                "A double is hunted at once or never");
        // Skulls arrive far enough apart to read; each one counts (necromancer_soul bypasses hit immunity).
        require(NecromancerRules.BOLT_INTERVAL >= 8, "Soul bolts bunch into one blur");
        // After a half-second reaction, walking (0.2 blocks/tick) leaves a centred ring before it bites.
        require((Action.HANDS.impact - 10) * .2 > NecromancerRules.HANDS_RADIUS + .3, "Grasping hands cannot be escaped on foot");
        require(NecromancerRules.HANDS_RADIUS >= 4 && NecromancerRules.HANDS_DAMAGE >= 10, "Grasping hands lost their buff");
        // A ring rises ahead of a moving player. Whether they walk (0.216 blocks a tick) or sprint (0.28), it starts
        // with them outside it, so stopping or turning is safe, and running on does not get through before it closes.
        for (boolean giant : new boolean[]{false, true}) for (double pace : new double[]{.216, .28}) {
            double radius = NecromancerRules.handsRadius(giant), lead = Math.min(NecromancerRules.handsLead(giant) * pace, NecromancerRules.handsLeadMax(giant));
            require(lead >= radius, "A led ring rises on top of a moving player");
            require(lead + radius > Action.HANDS.impact * pace, "A player can run straight through a led ring");
        }
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

        // Colossus: no robed moves, no army. Within its arms it swipes or grabs; a player who jumped its last swipe
        // gets the hand, unless the hand had them lately.
        var reach = NecromancerRules.colossusWeights(Read.at(4), crowded, null, null, all);
        require(heaviest(reach) == Action.SWIPE && reach.containsKey(Action.GRAB) && !reach.containsKey(Action.RUSH) && !reach.containsKey(Action.DIVE), "Arms' reach kit wrong: " + reach);
        require(only(NecromancerRules.colossusWeights(Read.at(4, Tell.JUMPED), crowded, null, null, all)) == Action.GRAB, "A jumped swipe was not answered with the hand");
        require(!NecromancerRules.colossusWeights(Read.at(4, Tell.JUMPED, Tell.HELD), crowded, null, null, all).containsKey(Action.GRAB), "Grabbed the same player again too soon");
        require(heaviest(NecromancerRules.colossusWeights(Read.at(4), crowded, Action.SWIPE, Action.SWIPE, all)) != Action.SWIPE, "A third swipe in a row");
        // Beyond its arms: the rush at mid range, the dive at range, and the dive only when nobody is in reach.
        var mids = NecromancerRules.colossusWeights(Read.at(10), calm, null, null, all);
        var fars = NecromancerRules.colossusWeights(Read.at(20), calm, null, null, all);
        require(heaviest(mids) == Action.RUSH && heaviest(fars) == Action.DIVE && !fars.containsKey(Action.SWIPE), "Gap closers out of order: " + mids + " " + fars);
        require(!NecromancerRules.colossusWeights(Read.at(20), crowded, null, null, all).containsKey(Action.DIVE), "Dived while a player stood within reach");
        require(!NecromancerRules.canTarget(Action.DIVE, new Candidate(a, 5, true)), "Dived at a player already within reach");
        // Reads: backing away draws the rush; standing still, eating or hiding draws the dive.
        require(share(NecromancerRules.colossusWeights(Read.at(12, Tell.RETREATING), calm, null, null, all), Action.RUSH) > share(mids, Action.RUSH), "A retreating player was not rushed");
        require(heaviest(NecromancerRules.colossusWeights(Read.at(12, Tell.STILL), calm, null, null, all)) == Action.DIVE
                && heaviest(NecromancerRules.colossusWeights(Read.at(12, Tell.HEALING), calm, null, null, all)) == Action.DIVE, "A still player was not dived under");
        var hide = NecromancerRules.colossusWeights(Read.unseen(30), calm, null, null, all);
        require(heaviest(hide) == Action.DIVE && !hide.containsKey(Action.RUSH) && !hide.containsKey(Action.BOLT), "A hidden kiter escaped the dive: " + hide);
        require(!NecromancerRules.canTarget(Action.RUSH, new Candidate(a, 12, false)), "Rush targeted through cover");
        // A hovering player: skulls, or the hand where it reaches; never a swing at the air.
        require(only(NecromancerRules.colossusWeights(Read.at(12, Tell.AIRBORNE), calm, null, null, all)) == Action.BOLT, "Ground attack at a hovering player");
        require(NecromancerRules.colossusWeights(Read.at(5, Tell.AIRBORNE), crowded, null, null, all).keySet().equals(java.util.Set.of(Action.BOLT, Action.GRAB)), "Hovering in reach is not grabbed");
        // It harvests when wounded, not on a clock; in its frenzy it does not feed at all.
        require(!mids.containsKey(Action.HARVEST) && NecromancerRules.colossusWeights(Read.at(10), wounded, null, null, all).containsKey(Action.HARVEST), "Harvest does not follow its wounds");
        for (double distance : new double[]{3, 8, 12, 20, 30}) {
            var mad = NecromancerRules.colossusWeights(Read.at(distance), frenzy, null, null, all);
            require(!mad.isEmpty() && mad.keySet().stream().noneMatch(Action::feeds), "The frenzied colossus fed or stood idle at " + distance + ": " + mad);
            for (Self self : new Self[]{calm, wounded, crowded, frenzy})
                for (Action action : NecromancerRules.colossusWeights(Read.at(distance, Tell.STILL), self, null, null, all).keySet())
                    require(!action.robedOnly(), "Colossus used " + action);
        }
        require(Action.DRAIN.feeds() && Action.HARVEST.feeds() && !Action.BOLT.feeds() && Action.BURST.robedOnly() && Action.BLINK.repositions()
                && Action.SHIFT.repositions() && !Action.AMBUSH.repositions(), "Action families moved");
        require(NecromancerRules.colossusWeights(Read.unseen(60), calm, null, null, all).isEmpty(), "Colossus cast beyond the encounter");
        // The colossus keeps pace with a walking player (4.3 blocks/s) but not a sprint (5.6); its frenzy is faster and still short of one.
        double colossusPace = 2.159 * NecromancerRules.COLOSSUS_SPEED * NecromancerRules.COLOSSUS_SPEED * 20;
        double frenzyPace = 2.159 * NecromancerRules.FRENZY_SPEED * NecromancerRules.FRENZY_SPEED * 20;
        require(colossusPace >= 4.3 && colossusPace < 5.6, "Colossus pace moved: " + colossusPace);
        require(frenzyPace > colossusPace + .4 && frenzyPace < 5.6, "Frenzy pace moved: " + frenzyPace);
        require(NecromancerRules.FRENZY_GATE == .25f && 0 < NecromancerRules.FRENZY_HOWL && NecromancerRules.FRENZY_HOWL < NecromancerRules.FRENZY_HOWL_END
                && NecromancerRules.FRENZY_HOWL_END < NecromancerRules.FRENZY_TELL, "Frenzy clock out of order");
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
        // Transformation clock: the body grows before the roar, and both finish inside the cinematic.
        require(0 < NecromancerRules.TRANSFORM_GROW && NecromancerRules.TRANSFORM_GROW < NecromancerRules.TRANSFORM_ROAR
                && NecromancerRules.TRANSFORM_ROAR < NecromancerRules.TRANSFORM_TICKS, "Transformation clock out of order");
        // ...on the cinematic's own track: it is the colossus at the swap, howls at the roar and hands back at its end.
        var track = NecromancerTransformTrack.get();
        require(NecromancerRules.TRANSFORM_TICKS == track.length() && NecromancerRules.TRANSFORM_GROW == track.beat("swap")
                && NecromancerRules.TRANSFORM_ROAR == track.beat("roar"), "Transformation clock drifted from its cinematic track");
        require(track.souls().size() == 40 && track.souls().stream().allMatch(s -> s.first() > 0 && s.last() < track.beat("writhe")
                && s.arrive() <= s.last() + 1), "A ghost of the storm flies outside the scene");
        require(track.beat("ret") < track.length() && NecromancerTransformScene.SKIP_AFTER < track.beat("impact"), "Transformation hand-back out of order");
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
        System.out.println("Necromancer checks passed: intro beats, torn soul path and skip window; scaling, stages and gates, solo/duo/party waves, reinforcements, quickening and siege damage, soul fire rain, soul light, drain cap, drain windup and range; the robed chooser (blink or burst by memory, range families, still/eating/hidden/bunched/hovering reads, drain when wounded, shifts for a reason, no third repeat); who it turns on; strings and rests by temper and party; telegraph timing, hands buff, led rings and follow-up, sidestep window, siege timing; the colossus chooser (arms, the hand after a jumped swipe, rush and dive bands and reads, harvest when wounded, a frenzy that never feeds); colossus and frenzy pace, grave dive, harvest, transformation clock, grab caps, jumpable swipe, random rush windup, timing and cover.");
    }

    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }

    /** The one cast a set of weights allows, or null when it allows none or several. */
    private static Action only(Map<Action, Double> weights) { return weights.size() == 1 ? weights.keySet().iterator().next() : null; }

    private static Action heaviest(Map<Action, Double> weights) {
        return weights.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static double share(Map<Action, Double> weights, Action action) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        return total <= 0 ? 0 : weights.getOrDefault(action, 0.0) / total;
    }
}
