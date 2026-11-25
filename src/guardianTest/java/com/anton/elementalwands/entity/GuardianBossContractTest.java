package com.anton.elementalwands.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import static com.anton.elementalwands.entity.GuardianCombatRules.*;

/** Regression cases for cooperative targeting, the chooser, strings, jump windows, Airburst aim and rock trajectories. */
final class GuardianBossContractTest {
    private static void require(boolean pass, String message) { if (!pass) throw new AssertionError(message); }

    /** A target on the ground doing nothing in particular, in phase one. */
    private static Read at(double distance) { return new Read(distance, false, false, false, false, false, false); }
    /** The same, but they have got beside or behind it. */
    private static Read behind(double distance) { return new Read(distance, false, false, false, false, false, false, true, false, false); }
    /** The same, but they stand between the Scar's walls. */
    private static Read penned(double distance) { return new Read(distance, false, false, false, false, false, false, false, false, true); }
    private static Attack heaviest(Map<Attack, Double> weights) {
        return weights.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    }

    static void run() {
        UUID a = new UUID(0,1), b = new UUID(0,2), c = new UUID(0,3);
        var players = List.of(new Candidate(a, 10, true), new Candidate(b, 12, true), new Candidate(c, 14, true));
        var history = new HashMap<UUID, Long>();
        // With equal threat the selection still rotates through the whole party.
        require(target(Attack.THROW, players, history, 0).id().equals(a), "Initial throw selection failed");
        history.put(a, 10L);
        require(target(Attack.BEAM, players, history, 15).id().equals(b), "Beam tunneled on the prior throw target");
        history.put(b, 20L);
        require(target(Attack.THROW, players, history, 25).id().equals(c), "Third co-op player was starved");
        history.put(c, 30L);
        require(target(Attack.BEAM, players, history, 40).id().equals(a), "Target rotation did not wrap");
        var obscured = List.of(new Candidate(a, 10, false), new Candidate(b, 12, true));
        require(target(Attack.THROW, obscured, new HashMap<>(), 0).id().equals(b), "Obstructed target was selected");
        require(target(Attack.THROW, List.of(), history, 40) == null, "Empty party produced a target");
        require(target(Attack.BEAM, List.of(new Candidate(a, 30, true)), history, 40) == null, "Beam exceeded range");
        // Threat: whoever is hurting it draws attacks, standing still to cast does too, and nobody is ignored for long.
        var recent = new HashMap<UUID, Long>(Map.of(a, 100L, b, 100L));
        require(target(Attack.THROW, List.of(new Candidate(a, 10, true, 0, false), new Candidate(b, 14, true, 30, false)),
                recent, 140).id().equals(b), "The player dealing damage did not draw the attack");
        require(target(Attack.THROW, List.of(new Candidate(a, 14, true, 0, true), new Candidate(b, 10, true, 0, false)),
                recent, 140).id().equals(a), "Standing still to cast drew no attention");
        var neglect = new HashMap<UUID, Long>(Map.of(a, 0L, b, 190L));
        require(target(Attack.THROW, List.of(new Candidate(a, 10, true, 0, false), new Candidate(b, 10, true, 500, false)),
                neglect, NEGLECT).id().equals(a), "A player was ignored past the neglect limit");
        // Inside a string it turns on someone else when one is in reach, and stays put when nobody is.
        require(!target(Attack.THROW, players, recent, 140, false, a).id().equals(a), "A string stayed on its previous target");
        require(!focus(players, recent, 140, a).id().equals(a), "A string's next pick stayed on its previous target");
        require(focus(List.of(new Candidate(a, 3, true)), recent, 140, a).id().equals(a), "A solo string lost its only target");
        require(target(Attack.THROW, List.of(new Candidate(a, 10, true), new Candidate(b, 60, true)), recent, 140, false, a).id().equals(a),
                "A string with nobody else in reach lost its target");
        require(healthForParty(1)==1400 && healthForParty(3)==3400 && healthForParty(5)==5400 && healthForParty(8)==8400, "Party scaling bounds changed");

        // The chooser. Distance decides the family.
        var upClose = weights(at(3), null, null);
        require(upClose.keySet().equals(Set.of(Attack.SLAM, Attack.SWEEP)) && heaviest(upClose) == Attack.SLAM, "Up close it did not use its fists");
        var walkingIn = weights(at(8), null, null);
        require(walkingIn.keySet().equals(Set.of(Attack.SLAM, Attack.SCAR, Attack.THROW)), "Walking in offered " + walkingIn.keySet());
        require(!weights(at(5.5), null, null).containsKey(Attack.THROW), "A rock was thrown at a player under its hands");
        var ranged = weights(at(20), null, null);
        require(heaviest(ranged) == Attack.LEAP, "At range the leap is not what it does most");
        require(ranged.keySet().containsAll(Set.of(Attack.THROW, Attack.SCAR, Attack.SLAM)), "At range it has only the leap");
        for (double d : new double[]{0, 3, 8, 20, 40}) for (boolean unstable : new boolean[]{false, true})
            require(!weights(new Read(d, false, false, false, true, unstable, false, true, true, true), null, null).containsKey(Attack.BEAM),
                    "The beam was chosen in a fight at " + d);
        require(weights(new Read(20, false, false, false, true, false, false), null, null).get(Attack.LEAP) > ranged.get(Attack.LEAP),
                "Keeping away drew no more leaps");
        var distant = weights(at(40), null, null);
        require(heaviest(distant) == Attack.THROW && !distant.containsKey(Attack.LEAP) && !distant.containsKey(Attack.SLAM),
                "Beyond the leap it did not throw: " + distant.keySet());
        require(weights(new Read(40, false, false, false, false, true, false), null, null).containsKey(Attack.SLAM),
                "Phase two's wider wave was not used at its range");
        require(weights(at(60), null, null).isEmpty(), "It attacked a player beyond every reach instead of walking");
        for (double d : new double[]{0, 3, 5, 5.5, 8, 12, 12.5, 20, 30, 31, 44, 48})
            require(!weights(at(d), null, null).isEmpty(), "Nothing to do against a visible player " + d + " blocks away");
        for (double d : new double[]{1, 5, 9, 15, 24, 29, 35, 47}) for (var entry : weights(at(d), null, null).entrySet())
            require(eligible(entry.getKey(), new Candidate(a, d, true)), entry.getKey() + " was offered out of its reach at " + d);

        // What the target is doing tilts it.
        require(weights(new Read(20, true, false, false, false, false, false), Attack.THROW, null).keySet().equals(Set.of(Attack.AIRBURST)),
                "A launched player did not draw the Airburst");
        require(!weights(new Read(45, true, false, false, false, false, false), null, null).containsKey(Attack.AIRBURST)
                && !weights(new Read(2, true, false, false, false, false, false), null, null).containsKey(Attack.AIRBURST),
                "The Airburst was used out of its reach");
        require(weights(new Read(8, false, false, false, false, true, true), Attack.SLAM, null).keySet().equals(Set.of(Attack.PULSE)),
                "A ready core pulse was skipped");
        require(!weights(new Read(8, false, false, false, false, true, true), Attack.PULSE, null).containsKey(Attack.PULSE),
                "The core pulse followed itself");
        require(heaviest(weights(new Read(20, false, true, false, false, false, false), null, null)) == Attack.THROW,
                "Standing still at range did not draw the rock");
        require(heaviest(weights(new Read(20, false, false, true, false, false, false), null, null)) == Attack.THROW,
                "Eating at range did not draw the rock");
        require(punishes(new Read(20, true, false, false, false, false, false)) && punishes(new Read(3, false, false, true, false, false, false))
                && punishes(new Read(20, false, true, false, false, false, false)), "A launched, eating or standing player was let off");
        require(!punishes(new Read(3, false, true, false, false, false, false)), "Standing beside it in its opening was punished");
        require(!punishes(at(20)), "An ordinary player cut its walk short");

        // The Sweep is for whoever got behind it; in front of it the arm never arrives, so there it is rare.
        require(heaviest(weights(behind(3), null, null)) == Attack.SWEEP, "A player behind it did not draw the Sweep");
        require(weights(behind(3), null, null).containsKey(Attack.SLAM), "Behind it, the slam was taken away entirely");
        require(weights(behind(6.5), null, null).containsKey(Attack.SWEEP) && !weights(behind(7.5), null, null).containsKey(Attack.SWEEP),
                "The Sweep's reach is not seven blocks");
        require(weights(at(6.5), null, null).get(Attack.SWEEP) < weights(behind(6.5), null, null).get(Attack.SWEEP),
                "The Sweep was as likely at a player in front of it");
        var flanked = weights(new Read(20, false, false, false, false, false, false, false, true, false), null, null);
        require(flanked.containsKey(Attack.SWEEP) && heaviest(flanked) == Attack.LEAP, "Somebody behind it while it looked elsewhere drew no Sweep: " + flanked);
        require(!ranged.containsKey(Attack.SWEEP), "It swept at nobody");
        Vec3d feet = new Vec3d(0, 64, 0);
        // Yaw 0 faces +Z. In front is safe at any distance; beside, behind and underneath are not.
        Box front = new Box(-.3, 64, 3.7, .3, 65.8, 4.3), side = new Box(3.7, 64, -.3, 4.3, 65.8, .3), back = new Box(-.3, 64, -6.3, .3, 65.8, -5.7);
        require(!GuardianSweepRules.contact(feet, 0, front), "The Sweep struck a player in front of it");
        require(GuardianSweepRules.contact(feet, 0, side) && GuardianSweepRules.contact(feet, 0, back), "The Sweep missed a player beside or behind it");
        require(GuardianSweepRules.contact(feet, 0, new Box(-.3, 64, .5, .3, 65.8, 1.1)), "Standing under it was safe from the Sweep");
        require(!GuardianSweepRules.contact(feet, 0, new Box(-.3, 64, -8.3, .3, 65.8, -7.7)), "The Sweep reached past seven blocks");
        require(!GuardianSweepRules.contact(feet, 0, back.offset(0, 5, 0)), "The Sweep struck a player far above it");
        require(GuardianSweepRules.contact(feet, 180, front) && !GuardianSweepRules.contact(feet, 180, back), "The Sweep ignored which way it faces");
        require(!GuardianSweepRules.behind(feet, 0, new Vec3d(2, 64, 4)) && GuardianSweepRules.behind(feet, 0, new Vec3d(4, 64, 2)),
                "The front wedge is not sixty degrees either side");
        require(fistContact(feet, 0, front) && !fistContact(feet, 0, back.offset(0, 0, 2)), "The slam and the Sweep no longer cover opposite sides");

        // The Scar pens its target: two walls either side of them, open toward it and away from it.
        var aimed = GuardianScarRules.pen(feet, new Vec3d(0, 64, 15));
        require(aimed.contains(new Vec3d(0, 64, 15)) && aimed.contains(new Vec3d(3, 64, 22)) && aimed.contains(new Vec3d(-3, 64, 5)),
                "The pen does not hold its target");
        require(!aimed.contains(new Vec3d(5, 64, 15)) && !aimed.contains(new Vec3d(0, 64, 26)) && !aimed.contains(new Vec3d(0, 64, 2)),
                "The pen holds a player outside its walls or past its ends");
        GuardianScarRules.Wall left = aimed.wall(true), right = aimed.wall(false);
        require(Math.abs(left.distance(new Vec3d(0, 64, 15)) - GuardianScarRules.HALF_WIDTH) < 1e-6
                && Math.abs(right.distance(new Vec3d(0, 64, 15)) - GuardianScarRules.HALF_WIDTH) < 1e-6
                && Math.abs(left.from().x + right.from().x) < 1e-6 && left.from().x != right.from().x, "The walls are not either side of its target");
        require(Math.abs(left.length() - (15 + GuardianScarRules.AHEAD - GuardianScarRules.START)) < 1e-6, "A wall is the wrong length: " + left.length());
        require(GuardianScarRules.pen(feet, new Vec3d(0, 64, 36)).wall(true).length() <= GuardianScarRules.MAX_LENGTH - GuardianScarRules.START + 1e-6,
                "A wall ran past its longest");
        require(GuardianScarRules.pen(feet, new Vec3d(0, 64, 2)) == null, "It scored a pen round a player under its hands");
        var columns = left.columns();
        require(columns.size() == (int)left.length() + 1, "A wall has gaps or doubled spikes: " + columns.size());
        for (int i = 1; i < columns.size(); i++)
            require(columns.get(i).distanceTo(columns.get(i - 1)) <= GuardianScarRules.SPACING + 1e-6
                    && GuardianScarRules.column(columns.get(i)).intersects(GuardianScarRules.column(columns.get(i - 1))), "A player could slip between two spikes");
        // Diagonal walls are as tight as straight ones.
        var slanted = GuardianScarRules.pen(feet, new Vec3d(11, 64, 9)).wall(true).columns();
        for (int i = 1; i < slanted.size(); i++)
            require(GuardianScarRules.column(slanted.get(i)).intersects(GuardianScarRules.column(slanted.get(i - 1))), "A slanted wall has a gap");
        require(GuardianScarRules.HEIGHT > 3 && GuardianScarRules.HALF_WIDTH * 2 < GuardianLeapRules.IMPACT_RADIUS * 2,
                "The walls can be jumped, or the landing ring no longer fills the pen from side to side");
        // A sprinting player (about .28 blocks a tick) cannot run out sideways between the crack and the wall.
        require(.28 * GuardianScarRules.COMMIT < GuardianScarRules.HALF_WIDTH - GuardianScarRules.WIDTH, "A sprint escapes the pen before it closes");
        require(GuardianScarRules.ripple(0, 30) == 0 && GuardianScarRules.ripple(29, 30) == GuardianScarRules.RIPPLE && GuardianScarRules.ripple(0, 1) == 0,
                "The eruption does not run outward along the wall");
        require(GuardianScarRules.ERUPT + GuardianScarRules.RIPPLE < GuardianScarRules.DURATION, "The walls are still coming up when its next attack starts");
        // The follow-up lands while the walls stand: the Scar's recovery, then a whole leap.
        require(GuardianScarRules.DURATION - GuardianScarRules.ERUPT + GuardianLeapRules.LAND < GuardianScarRules.LIFE - GuardianScarRules.FADING,
                "The walls fall before the leap into them lands");
        // Co-op: another player gets one wall, ahead of where they are heading and clear of the pen.
        var runner = GuardianScarRules.flank(aimed, new Vec3d(15, 64, 0), new Vec3d(0, 0, -.25));
        require(runner != null && runner.distance(new Vec3d(15, 64, -.25 * GuardianScarRules.COMMIT - GuardianScarRules.FLANK_LEAD)) < 1e-6,
                "A running player's wall is not ahead of them");
        var standing = GuardianScarRules.flank(aimed, new Vec3d(15, 64, 0), Vec3d.ZERO);
        require(standing != null && standing.to().z < 0, "A standing player's wall is on the pen's side of them");
        require(GuardianScarRules.flank(aimed, new Vec3d(1, 64, 18), Vec3d.ZERO) == null, "A wall was cut across the pen");
        require(GuardianScarRules.flank(aimed, new Vec3d(.5, 64, .5), Vec3d.ZERO) == null, "A wall was scored for a player under its hands");
        // What it does with a penned player: the leap at range, the fists up close, and never another Scar or a Sweep.
        require(weights(penned(20), null, null).keySet().equals(Set.of(Attack.LEAP)) && weights(penned(20), Attack.LEAP, Attack.LEAP).keySet().equals(Set.of(Attack.LEAP)),
                "A penned player at range did not draw the leap");
        require(weights(penned(6), null, null).keySet().equals(Set.of(Attack.SLAM)), "A penned player up close did not draw the slam");
        require(weights(penned(31), null, null).keySet().equals(Set.of(Attack.SLAM)), "A penned player past the leap's reach did not draw the slam");
        require(!weights(penned(40), null, null).containsKey(Attack.SLAM) && weights(new Read(40, false, false, false, false, true, false, false, false, true), null, null)
                .keySet().equals(Set.of(Attack.SLAM)), "A penned player was slammed from beyond its wave, or not slammed inside phase two's");
        require(weights(new Read(20, false, false, false, false, false, false, false, true, true), null, null).keySet().equals(Set.of(Attack.LEAP))
                && weights(new Read(4, false, false, false, false, false, false, true, true, true), null, null).keySet().equals(Set.of(Attack.SLAM)),
                "Somebody behind it drew it off the player it had penned");
        require(weights(new Read(20, true, false, false, false, false, false, false, false, true), null, null).keySet().equals(Set.of(Attack.AIRBURST)),
                "A player launched out of the pen did not draw the Airburst");
        require(weights(new Read(20, false, false, false, true, false, false), null, null).get(Attack.SCAR) > ranged.get(Attack.SCAR),
                "Keeping away drew no more Scars");
        require(eligible(Attack.SCAR, new Candidate(a, 7, true)) && eligible(Attack.SCAR, new Candidate(a, 30, true))
                && !eligible(Attack.SCAR, new Candidate(a, 6, true)) && !eligible(Attack.SCAR, new Candidate(a, 31, true)), "The Scar's range changed");

        // Never the same attack three times running, and seldom twice; a lone option still happens.
        require(!weights(at(20), Attack.LEAP, Attack.LEAP).containsKey(Attack.LEAP), "The same attack came three times running");
        require(weights(at(20), Attack.LEAP, Attack.THROW).get(Attack.LEAP) < ranged.get(Attack.LEAP), "A repeat was as likely as a fresh attack");
        require(weights(at(3), Attack.SLAM, Attack.SLAM).keySet().equals(Set.of(Attack.SWEEP)), "Up close it slammed a third time running");
        require(weights(at(40), Attack.THROW, Attack.THROW).keySet().equals(Set.of(Attack.THROW)), "Its only option was taken away");
        require(pick(ranged, () -> 0) != null && pick(ranged, () -> .999999) != null && pick(Map.of(), () -> .5) == null, "Pick lost an end of its range");
        for (boolean unstable : new boolean[]{false, true}) {
            Random random = new Random(7);
            Attack last = null, before = null;
            var seenAttacks = new HashSet<Attack>();
            for (int i = 0; i < 3000; i++) {
                double distance = 13 + random.nextDouble() * 11;
                Attack next = choose(new Read(distance, false, false, false, false, unstable, false), last, before, random::nextDouble);
                require(next != null, "A player at range stalled the fight");
                require(!(next == last && last == before), "Three " + next + " in a row");
                seenAttacks.add(next); before = last; last = next;
            }
            require(seenAttacks.equals(Set.of(Attack.LEAP, Attack.THROW, Attack.SCAR, Attack.SLAM)), "At range it used only " + seenAttacks);
        }

        // Strings: phase two strings more and longer; a landed leap nearly always goes on; the pulse never does.
        require(chainChance(false, Attack.SLAM, Difficulty.NORMAL) < chainChance(true, Attack.SLAM, Difficulty.NORMAL), "Phase two does not string more");
        require(chainChance(true, Attack.LEAP, Difficulty.NORMAL) == 1 && chainChance(false, Attack.LEAP, Difficulty.NORMAL) > chainChance(false, Attack.SLAM, Difficulty.NORMAL),
                "A landed leap is not followed up");
        require(chainChance(true, Attack.PULSE, Difficulty.HARD) == 0, "Something was chained onto the core pulse");
        require(chainChance(false, Attack.SCAR, Difficulty.EASY) == 1, "The Scar was left without its follow-up");
        require(chainChance(false, Attack.SLAM, Difficulty.EASY) < chainChance(false, Attack.SLAM, Difficulty.NORMAL)
                && chainChance(false, Attack.SLAM, Difficulty.NORMAL) < chainChance(false, Attack.SLAM, Difficulty.HARD)
                && chainChance(true, Attack.SLAM, Difficulty.HARD) <= 1, "String chance out of order across difficulties");
        require(chainLimit(false) == 2 && chainLimit(true) == 3, "String length changed");
        require(scaled(REST, restScale(1, Difficulty.NORMAL)) == 25, "The solo opening is not 1.25 seconds");
        require(scaled(REST, restScale(2, Difficulty.NORMAL)) < 25 && scaled(REST, restScale(4, Difficulty.HARD)) >= 5, "Co-op openings out of bounds");

        // Nothing it lands is a light hit.
        require(WAVE_DAMAGE >= 9 && ROCK_SPLASH_DAMAGE >= 9 && GuardianAirburstRules.DAMAGE >= 9, "An area's edge went back to chip damage");
        require(FIST_DAMAGE > WAVE_DAMAGE && ROCK_DAMAGE > ROCK_SPLASH_DAMAGE && GuardianLeapRules.DAMAGE > FIST_DAMAGE, "Damage tiers out of order");

        // The Airburst: a jump is not a launch; its stones meet a body that keeps going and miss one that turns.
        require(!GuardianAirburstRules.launched(1.3, false, 20), "An ordinary jump counted as launched");
        require(!GuardianAirburstRules.launched(6, true, 20), "A player standing on a ledge counted as launched");
        require(!GuardianAirburstRules.launched(6, false, GuardianAirburstRules.AIRBORNE_TICKS - 1)
                && GuardianAirburstRules.launched(6, false, GuardianAirburstRules.AIRBORNE_TICKS), "Launch recognition moved");
        Vec3d socket = new Vec3d(0, 6.2, 0), body = new Vec3d(0, 9, 16);
        for (Vec3d velocity : List.of(new Vec3d(.25, 0, 0), new Vec3d(-.2, .1, .15), Vec3d.ZERO)) {
            int wait = GuardianFanRules.RELEASE - GuardianFanRules.LOCK;
            Vec3d aim = GuardianAirburstRules.aim(socket, body, velocity, 0, wait);
            int arrival = wait + GuardianAirburstRules.flightTicks(socket, aim);
            require(aim.distanceTo(body.add(velocity.multiply(arrival))) < 1, "A stone missed a body that kept going");
            if (velocity.lengthSquared() > 0)
                require(aim.distanceTo(body.add(velocity.multiply(-arrival))) > GuardianAirburstRules.BLAST + 1,
                        "Turning round in the air did not beat the burst");
        }
        Vec3d falling = GuardianAirburstRules.lead(body, new Vec3d(0, -.3, 0), -.08, 10);
        require(Math.abs(falling.y - (body.y - 3 - 4)) < 1e-9, "A falling body was not led as a fall");
        require(GuardianAirburstRules.lead(body, new Vec3d(0, .3, 0), .2, 10).y == body.y + 3, "A body was led as falling upward");
        require(GuardianAirburstRules.lead(body, new Vec3d(30, 10, 0), 0, 40).distanceTo(body) <= GuardianAirburstRules.LEAD_MAX + 1e-9,
                "A dash generated runaway aim");
        Box person = new Box(-.3, 0, -.3, .3, 1.8, .3);
        require(GuardianAirburstRules.inBlast(new Vec3d(4.2, .9, 0), person) && !GuardianAirburstRules.inBlast(new Vec3d(4.4, .9, 0), person),
                "The blast's edge moved");
        require(GuardianAirburstRules.inBlast(new Vec3d(0, 5.7, 0), person) && !GuardianAirburstRules.inBlast(new Vec3d(0, 6, 0), person),
                "The blast is not a sphere");
        // On the ground the ring is marked long enough to run out of, but not to stroll out of.
        require(GuardianAirburstRules.FUSE * .28 > GuardianAirburstRules.BLAST + .3 && GuardianAirburstRules.FUSE * .1 < GuardianAirburstRules.BLAST,
                "The ground ring's warning no longer asks for a sprint");
        for(int age=0,releases=0;age<GuardianFanRules.duration();age++) {
            if(GuardianFanRules.localTime(age)==GuardianFanRules.RELEASE) {
                require(age==18+releases*8,"Unexpected burst release cadence");releases++;
            }
            if(age==GuardianFanRules.duration()-1)require(releases==3,"Missing burst");
        }
        require(Attack.AIRBURST.duration == GuardianFanRules.duration(), "The Airburst outlasts or cuts off its clip");

        Vec3d center = Vec3d.ZERO;
        Box grounded = new Box(5.7,0,-.3,6.3,1.8,.3);
        require(waveContact(center, grounded, 5.5, 6, 0), "Wave missed standing player");
        require(!waveContact(center, grounded.offset(0,1,0), 5.5, 6, 0), "Successful jump was hit");
        require(waveCrossing(center, grounded.offset(0,1,0), 5.5, 6), "A jump over the wave was not seen");
        require(!waveCrossing(center, grounded.offset(0,1,0), 8, 8.45), "A jump was credited after the wave had gone by");
        require(!waveContact(center, grounded, 8, 8.45, 0), "Wave damaged a player behind the front");
        require(!waveContact(center, grounded, 2, 2.45, 0), "Wave hit ahead of its visible front");
        require(!waveContact(center, grounded.offset(0,-3,0), 5.5, 6, 0), "Wave hit through the floor");
        for (Vec3d aim : List.of(new Vec3d(0,1,10),new Vec3d(20,3,20),new Vec3d(0,-3,30))) {
            Vec3d pos = new Vec3d(2,3,0), velocity = launchVelocity(pos, aim);
            int ticks = flightTicks(pos, aim);
            for (int i=0;i<ticks;i++) { pos=pos.add(velocity); velocity=velocity.add(0,-ROCK_GRAVITY,0); }
            require(pos.distanceTo(aim)<1e-7, "Rock did not reach committed position with runtime integration");
        }
        Vec3d muzzle = new Vec3d(0,3,0), walking = new Vec3d(.215,0,0), walker = new Vec3d(0,.9,14);
        Vec3d predicted = throwAim(muzzle,walker,walking,THROW_RELEASE-THROW_LOCK);
        int arrival = flightTicks(muzzle,predicted) + THROW_RELEASE-THROW_LOCK;
        require(predicted.distanceTo(walker.add(walking.multiply(arrival))) < .25,
                "Steady sideways walking still evades the predicted landing point");
        require(predicted.distanceTo(walker.add(walking.multiply(-arrival))) > 3,
                "Reversing after commitment should beat prediction");
        require(lead(walker,new Vec3d(100,40,0),40).distanceTo(walker) <= 6.001,
                "A dash/teleport generated runaway aim");
        GuardianMotionSample sample = new GuardianMotionSample();
        sample.reset(walker);
        Vec3d sampled = Vec3d.ZERO;
        for (int i=1;i<=12;i++) sampled = sample.observe(walker.add(walking.multiply(i)));
        require(sampled.distanceTo(walking) < .001, "Server-position sampling missed steady player motion");
        require(sample.observe(walker.add(100,0,0)).lengthSquared() == 0, "Teleport was treated as walking velocity");
        require(THROW_RELEASE-THROW_LOCK>=4, "Throw dodge window shrank");
        require(Attack.THROW.duration-THROW_RELEASE>=20, "Throw lost recovery");
        require(GuardianPhaseRules.waveTicks(false)==38 && GuardianPhaseRules.waveTicks(true)==47, "Independent wave lifetime changed");
        System.out.println("Guardian boss checks passed: 3-player rotation, visibility/range, distance bands, reads, no triple repeats, "
                + "strings, damage tiers, Airburst aim and blast, the Sweep's arc, the Scar's pen and walls, jumps, ballistic arcs.");
    }
}
