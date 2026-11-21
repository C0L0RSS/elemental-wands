package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Move;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Read;
import com.anton.elementalwands.entity.firstwizard.spell.BlinkRiftSpell;
import com.anton.elementalwands.entity.firstwizard.spell.BossSpell;
import com.anton.elementalwands.entity.firstwizard.spell.BossSpells;
import com.anton.elementalwands.entity.firstwizard.spell.FireballSpell;
import com.anton.elementalwands.entity.firstwizard.spell.GroundZone;
import com.anton.elementalwands.entity.firstwizard.spell.Timing;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Pure rule checks for the First Wizard's fight; run by the Gradle check task without launching Minecraft. Every
 * implemented spell warns before it lands and its timings are in order; the armored damage sums hold; the chooser never
 * casts a spell three times running and never one that is not built; the opener and the follow-ups are the design's;
 * and everything it aims at stays on its floor.
 */
public final class FirstWizardContractTest {
    public static void main(String[] args) {
        require(FirstWizardRules.health(1) == 1000 && FirstWizardRules.health(2) == 1600 && FirstWizardRules.health(3) == 2200, "Party health scaling moved");
        damage();
        table();
        timings();
        chooser();
        openers();
        followUps();
        gaps();
        geometry();
        meteorAndWell();
        // Sky Shear (its jump arithmetic) and Waylay Dash (its line, hit and wake).
        WindSpellsContract.run();
        // Stone Wall (its line, cells and shove) and Faultline (its shape, lanes and throw).
        StoneSpellsContract.run();
        // Thornbite (its warning, reach, dodge and heal) and Sentry Bloom (its seeds, store, health and spot).
        NatureSpellsContract.run();
        // Phase two: the change, the five ink moves, the jump of the low sweeps, the finisher's rarity.
        InkMovesContract.run();
        System.out.println("First Wizard contract passed: " + BossSpells.ALL.size() + " spells built " + BossSpells.available());
    }

    /** Sized like the Pyre King's for plain diamond to netherite armor (20 armor; toughness 8 and 12) and 20 health. */
    private static void damage() {
        for (double toughness : new double[]{8, 12}) {
            double heavy = armored(FirstWizardRules.HEAVY, 20, toughness), medium = armored(FirstWizardRules.MEDIUM, 20, toughness);
            double ground = armored(FirstWizardRules.GROUND, 20, toughness);
            require(heavy >= 7 && heavy <= 9, "A heavy hit should cost about 8 of 20 health in diamond or netherite: " + heavy);
            require(heavy * 2 < 20 && heavy * 3 > 20, "Two heavy hits should leave a player alive and a third kill: " + heavy);
            require(medium >= 4.8 && medium <= 6.2, "A medium hit should cost about 5 to 6: " + medium);
            require(ground >= 1.5 && ground <= 2.5, "A second on lingering ground should cost about 2: " + ground);
        }
        require(FirstWizardRules.GROUND_EVERY == 20, "Lingering ground should burn once a second");
    }

    private static void table() {
        require(Move.values().length == 15 && java.util.Arrays.stream(Move.values()).filter(Move::spell).count() == 10, "The move table lists the ten spells and the five ink moves");
        for (Move move : Move.values()) {
            require(move.near < move.far && move.weight > 0, move + " has no range or weight");
            require(Move.byId(move.id) == move, move + "'s id does not round-trip");
        }
        Set<Move> built = EnumSet.noneOf(Move.class);
        for (BossSpell spell : BossSpells.ALL) require(built.add(spell.move()), "Two spells for " + spell.move());
        require(built.equals(BossSpells.available()), "Available moves are exactly the built ones");
        require(built.contains(Move.FIREBALL) && built.contains(Move.BLINK_RIFT), "Fireball and Blink Rift are built");
    }

    /** formFrom &lt; aimLock &lt;= release &lt; free for every built spell, with at least half a second of forming in the open. */
    private static void timings() {
        for (BossSpell spell : BossSpells.ALL) {
            Timing t = spell.timing();
            require(t.consistent(), spell.move() + "'s timing is out of order: " + t);
            require(t.warning() >= Timing.MIN_WARNING, spell.move() + " forms for only " + t.warning() + " ticks before it is released");
        }
        Timing ball = FireballSpell.TIMING;
        require(ball.release() >= 20 && ball.release() <= 30, "The Fireball forms for about 1.2 s: " + ball.release());
        require(ball.release() - ball.aimLock() >= 3 && ball.release() - ball.aimLock() <= 8, "The Fireball's aim locks about a quarter of a second before release");
        require(FireballSpell.RADIUS * 2 >= 2.5 && FireballSpell.RADIUS * 2 <= 3.5, "The Fireball is about 3 blocks across");
        require(FireballSpell.SPEED * 20 <= 16.01, "Bigger means slower: the Fireball flies no faster than 16 blocks a second");
        require(FireballSpell.REACH < FireballSpell.RADIUS, "A body is struck only inside the drawn ball");
        require(FireballSpell.BURST < 4, "A four-block sidestep after the aim lock clears the burst");
        require(FireballSpell.GROUND_LIFE >= 80 && FireballSpell.GROUND_LIFE <= 120, "The burning ground lasts about 5 s");
        // A body standing still in the line is struck no sooner than the ball, thrown at release, reaches it.
        require(FireballSpell.formingRadius(0) > 0 && FireballSpell.formingRadius(1) == FireballSpell.RADIUS, "The ball grows to full size as it forms");
        Timing blink = BlinkRiftSpell.TIMING;
        require(blink.release() >= 8 && blink.release() <= 14, "Ink pools for about half a second before the Blink: " + blink.release());
    }

    private static void chooser() {
        Random random = new Random(7);
        List<Move> moves = List.of(Move.values());
        for (int run = 0; run < 400; run++) {
            // A random set of built spells, always including what is built today.
            Set<Move> available = EnumSet.copyOf(BossSpells.available());
            for (Move move : moves) if (random.nextDouble() < .4) available.add(move);
            Move last = null, before = null;
            for (int step = 0; step < 200; step++) {
                Read read = new Read(random.nextDouble() * 50, random.nextDouble() < .2, random.nextDouble() < .2, random.nextDouble() < .2,
                        random.nextDouble() < .2, random.nextDouble() < .2, random.nextDouble() < .1);
                var weights = FirstWizardRules.weights(read, last, before, available);
                for (Move move : weights.keySet()) {
                    require(available.contains(move), "The chooser weighed " + move + ", which is not built");
                    require(read.distance() >= move.near && read.distance() <= move.far, move + " weighed outside its range at " + read.distance());
                }
                Move next = FirstWizardRules.choose(read, last, before, available, random::nextDouble);
                if (next == null) continue;
                require(!(next == last && last == before), "The chooser cast " + next + " three times running");
                before = last; last = next;
                // A scripted follow-up comes between: it is a different spell, so it never makes three.
                Move follow = FirstWizardRules.followUp(next, available, random::nextDouble);
                if (follow != null) {
                    require(follow != next, "A follow-up repeats its spell");
                    require(available.contains(follow), "A follow-up that is not built");
                    before = last; last = follow;
                }
            }
        }
        // Crowded, it gets away (phase one: the ten spells).
        Set<Move> all = FirstWizardRules.usable(EnumSet.allOf(Move.class), false, 1, 0);
        var crowded = FirstWizardRules.weights(new Read(3, true, false, false, false, false, false), null, null, all);
        require(crowded.get(Move.BLINK_RIFT) > crowded.values().stream().mapToDouble(Double::doubleValue).sum() * .5, "Crowded, Blink Rift should be likeliest");
        // Today, at range, the Fireball is most of what it has.
        var far = FirstWizardRules.weights(Read.at(16), null, null, BossSpells.available());
        require(far.containsKey(Move.FIREBALL), "At 16 blocks the Fireball is weighed");
    }

    private static void openers() {
        Set<Move> all = EnumSet.allOf(Move.class);
        require(FirstWizardRules.opener(WizardAffinity.FIRE, all) == Move.FIREBALL, "Fire opens with the Fireball");
        require(FirstWizardRules.opener(WizardAffinity.WIND, all) == Move.SKY_SHEAR, "Wind opens with Sky Shear");
        require(FirstWizardRules.opener(WizardAffinity.STONE, all) == Move.FAULTLINE, "Stone opens with Faultline");
        require(FirstWizardRules.opener(WizardAffinity.NATURE, all) == Move.THORNBITE, "Nature opens with Thornbite");
        require(FirstWizardRules.opener(WizardAffinity.SPACE, all) == Move.GRAVITY_WELL, "Space opens with Gravity Well");
        require(FirstWizardRules.opener(WizardAffinity.NONE, all) == Move.FIREBALL, "No element opens with the Fireball");
        // Falling back: the same element's other attack first, then the nearest in the table; never a spell that only moves.
        require(FirstWizardRules.opener(WizardAffinity.WIND, EnumSet.of(Move.WAYLAY_DASH, Move.FIREBALL)) == Move.WAYLAY_DASH, "Wind falls back to Waylay Dash");
        require(FirstWizardRules.opener(WizardAffinity.SPACE, EnumSet.of(Move.BLINK_RIFT, Move.FIREBALL)) == Move.FIREBALL, "Blink Rift is never an opener");
        require(FirstWizardRules.opener(WizardAffinity.STONE, EnumSet.of(Move.METEOR, Move.THORNBITE)) == Move.THORNBITE, "Stone falls back to the nearest attack");
        for (WizardAffinity affinity : WizardAffinity.values()) {
            Move opener = FirstWizardRules.opener(affinity, BossSpells.available());
            require(opener != null && BossSpells.available().contains(opener) && opener.attack, "Today " + affinity + " opens with a built attack: " + opener);
        }
    }

    private static void followUps() {
        Set<Move> all = EnumSet.allOf(Move.class);
        require(FirstWizardRules.FOLLOW_UPS.size() >= 3, "The design's three combinations");
        require(firstFollow(Move.STONE_WALL, all) == Move.FIREBALL, "Stone Wall, then the Fireball down the open lane");
        require(firstFollow(Move.GRAVITY_WELL, all) == Move.METEOR, "A Gravity Well, then a Meteor falling on it");
        require(firstFollow(Move.BLINK_RIFT, all) == Move.THORNBITE, "Blink Rift behind, then Thornbite");
        require(FirstWizardRules.followUp(Move.BLINK_RIFT, EnumSet.of(Move.BLINK_RIFT, Move.FIREBALL), () -> 0) == null, "No follow-up that is not built");
        require(BlinkRiftSpell.mode(false, true) == BlinkRiftSpell.Mode.FLANK && BlinkRiftSpell.mode(true, true) == BlinkRiftSpell.Mode.ESCAPE
                && BlinkRiftSpell.mode(false, false) == BlinkRiftSpell.Mode.AROUND, "Blink Rift's three uses");
    }

    private static Move firstFollow(Move move, Set<Move> available) { return FirstWizardRules.followUp(move, available, () -> 0); }

    /**
     * Meteor and Gravity Well: their timing, their damage zones, the pull (walking outward loses ground, sprinting
     * outward wins), their combination's timing (the meteor lands while the well still pulls, before its burst), and
     * that every mark and landing is on the floor.
     */
    private static void meteorAndWell() {
        Timing meteor = com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.TIMING;
        Timing well = com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.TIMING;
        double M_RADIUS = com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.RADIUS;
        double M_INNER = com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.INNER;
        int fall = com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.FALL;
        require(meteor.aimLock() >= 16 && meteor.aimLock() <= 24, "The copy raises its wand about a second before the Meteor's mark appears: " + meteor.aimLock());
        require(fall >= 50 && fall <= 70, "The Meteor falls for about 3 s: " + fall);
        require(meteor.free() - meteor.release() <= 10, "The copy is free shortly after the Meteor drops");
        require(M_RADIUS >= 8 && M_RADIUS <= 10 && M_INNER < M_RADIUS, "The Meteor's circle is about 9 blocks in radius");
        double ball = com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.BALL_RADIUS * 2;
        require(ball >= 16 && ball <= 24, "The Meteor is two to three times the player's 8 blocks: " + ball);
        // From the mark appearing to the impact, a player in the middle sprinting (5.6 a second) has a second to spare.
        double warning = (meteor.release() - meteor.aimLock() + fall) / 20.0;
        require(warning * 5.6 >= M_RADIUS + 5.6, "Leaving the Meteor's circle from its middle at a sprint should have a second to spare: " + warning + " s");
        Box body = new Box(-.3, 64, -.3, .3, 65.8, .3);
        Vec3d mid = new Vec3d(0, 64, 0);
        require(com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.damage(body, mid) == FirstWizardRules.HEAVY, "The Meteor's middle takes a heavy hit");
        require(com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.damage(body.offset(7, 0, 0), mid) == FirstWizardRules.MEDIUM, "Near its rim, a medium hit");
        require(com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.damage(body.offset(M_RADIUS + .5, 0, 0), mid) == 0, "Out of the circle, nothing");
        require(com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.damage(body.offset(0, 2, 0), mid) == FirstWizardRules.HEAVY, "A jump does not clear the Meteor");

        require(well.release() >= 15 && well.release() <= 22, "The Gravity Well's orb forms for about 0.9 s: " + well.release());
        require(well.aimLock() <= well.release() && well.release() - well.aimLock() <= 2, "The well lands where the target stood as it is thrown");
        int flight = com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.FLIGHT;
        int open = com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.OPEN;
        int buildup = com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.BUILDUP;
        require(flight >= 16 && flight <= 24 && open >= 70 && open <= 90 && buildup >= 10, "The orb flies about a second, the well pulls about 4 s, then a readable buildup");
        double pull = com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.PULL;
        // Between vanilla's ground acceleration walking (0.1, times 0.98) and sprinting (0.13, times 0.98): walking out
        // loses ground and sprinting out wins (the fixture measures by how much).
        require(pull > .098 + .005, "Walking out of the well should lose ground: pull " + pull);
        require(pull < .1274 - .01, "Sprinting out of the well should win: pull " + pull);
        double pullRadius = com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.PULL_RADIUS;
        double burst = com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.BURST;
        require(pullRadius >= 10 && pullRadius <= 12 && burst >= 4 && burst <= 6, "The pull reaches about 11 blocks and the burst about 5");
        Vec3d centre = new Vec3d(0, 64, 0);
        require(com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.pull(centre, new Vec3d(6, 64, 0)).x < 0, "The well pulls toward its middle");
        require(com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.pull(centre, new Vec3d(pullRadius + .5, 64, 0)).lengthSquared() == 0, "Nothing beyond its reach");
        // The combination: cast as a follow-up, the meteor lands while the well still pulls and before it bursts.
        int meteorStart = well.free() + FirstWizardRules.FOLLOW_GAP, impact = meteorStart + meteor.release() + fall;
        int wellOpens = well.release() + flight, wellBursts = wellOpens + open + buildup;
        require(meteor.aimLock() + meteorStart > well.aimLock(), "The Meteor's mark is set after the well's landing is known");
        require(impact > wellOpens && impact < wellBursts, "After a Gravity Well the Meteor should land while it pulls: impact " + impact + ", well " + wellOpens + " to " + wellBursts);

        Arena arena = new Arena(new Vec3d(.5, 64, .5), FirstWizardRules.FLOOR_RADIUS);
        Random random = new Random(5);
        for (int i = 0; i < 2000; i++) {
            Vec3d feet = arena.onFloor(spot(arena, random, 1.05), 0);
            Vec3d mark = com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell.centre(arena, feet, random.nextBoolean() ? null : feet);
            Vec3d landing = com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell.landing(arena, feet);
            require(arena.inside(mark, FirstWizardRules.EDGE) && mark.y == arena.floor(), "A Meteor marked off the floor: " + mark);
            require(arena.inside(landing, FirstWizardRules.EDGE) && landing.y == arena.floor(), "A Gravity Well off the floor: " + landing);
        }
    }

    /** Tight but real alone; co-op shortens them up to four players, and no further. */
    private static void gaps() {
        for (double r : new double[]{0, .5, .999}) {
            int solo = FirstWizardRules.gap(1, () -> r), duo = FirstWizardRules.gap(2, () -> r), four = FirstWizardRules.gap(4, () -> r), six = FirstWizardRules.gap(6, () -> r);
            require(solo >= 9 && solo <= 18, "A solo gap is 0.45 to 0.9 s: " + solo);
            require(duo <= solo && four < solo && four >= 4, "Co-op shortens the gap: " + solo + " " + duo + " " + four);
            require(six == four, "Pressure stops growing at four players");
        }
        require(FirstWizardRules.FOLLOW_GAP < FirstWizardRules.gap(4, () -> 0), "A follow-up comes faster than any ordinary gap");
    }

    /** Everything it aims at or moves to is on its floor. */
    private static void geometry() {
        Arena arena = new Arena(new Vec3d(.5, 64, .5), FirstWizardRules.FLOOR_RADIUS);
        Random random = new Random(11);
        for (int i = 0; i < 4000; i++) {
            Vec3d boss = arena.onFloor(spot(arena, random, 1.1), FirstWizardRules.EDGE);
            Vec3d feet = arena.onFloor(spot(arena, random, 1.0), 0);
            Vec3d velocity = new Vec3d(random.nextGaussian() * .3, 0, random.nextGaussian() * .3);
            Vec3d aim = FireballSpell.aimPoint(arena, boss.add(0, FireballSpell.HEIGHT, 0), feet, velocity);
            require(arena.inside(aim, FirstWizardRules.EDGE - 1e-6), "A Fireball aimed off the floor: " + aim);
            require(Math.abs(aim.y - arena.floor() - .3) < 1e-9, "A Fireball aims just over the floor");
            Vec3d ground = arena.onFloor(new Vec3d(feet.x * 1.2, 64.5, feet.z * 1.2), FirstWizardRules.EDGE);
            require(arena.inside(ground, FirstWizardRules.EDGE - 1e-6), "Burning ground off the floor");
            List<Vec3d> players = new ArrayList<>();
            for (int p = 0; p < 1 + random.nextInt(4); p++) players.add(arena.onFloor(spot(arena, random, 1), 0));
            for (BlinkRiftSpell.Mode mode : BlinkRiftSpell.Mode.values()) {
                List<Vec3d> spots = BlinkRiftSpell.spots(mode, arena, boss, players.getFirst(), players, random::nextDouble);
                require(!spots.isEmpty(), "Blink Rift found nowhere to go");
                for (Vec3d spot : spots) require(arena.inside(spot, FirstWizardRules.EDGE) && spot.y == arena.floor(), "A Blink Rift off the floor: " + mode + " " + spot);
                if (mode == BlinkRiftSpell.Mode.ESCAPE) {
                    Vec3d best = spots.getFirst();
                    double clear = players.stream().mapToDouble(p -> Math.hypot(p.x - best.x, p.z - best.z)).min().orElse(0);
                    require(clear >= 10 || players.size() > 2, "An escape should land well clear of the players: " + clear);
                }
            }
        }
        require(arena.exit(new Vec3d(0, 64, 0), new Vec3d(100, 64, 0), 0) < .46, "A run leaves the floor where the floor ends");
        require(GroundZone.segment(3, 4, 0, 0, 0, 0) == 5, "Distance to a point");
        Box body = new Box(2.2, 64, -.3, 2.8, 65.8, .3);
        require(GroundZone.reach(body, new Vec3d(0, 64, 0), new Vec3d(0, 64, 0)) < FireballSpell.GROUND_RADIUS, "A body 2.2 out stands in the burning ground");
        require(GroundZone.reach(body.offset(1, 0, 0), new Vec3d(0, 64, 0), new Vec3d(0, 64, 0)) > FireballSpell.GROUND_RADIUS, "A body 3.2 out is clear of it");
    }

    private static Vec3d spot(Arena arena, Random random, double reach) {
        double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * arena.radius() * reach;
        return new Vec3d(arena.centre().x + Math.cos(a) * r, arena.floor(), arena.centre().z + Math.sin(a) * r);
    }

    /** Vanilla's armor sum: what is left of a blow after armor and its toughness. */
    private static double armored(double damage, double armor, double toughness) {
        double held = Math.clamp(armor - 4 * damage / (toughness + 8), armor / 5, 20);
        return damage * (1 - held / 25);
    }

    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
