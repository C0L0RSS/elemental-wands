package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Move;
import com.anton.elementalwands.entity.firstwizard.spell.BossSpells;
import com.anton.elementalwands.entity.firstwizard.spell.SentryBloomSpell;
import com.anton.elementalwands.entity.firstwizard.spell.ThornbiteSpell;
import com.anton.elementalwands.entity.firstwizard.spell.Timing;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The Nature spells' sums, for {@link FirstWizardContractTest}: Thornbite's warning, reach, dodge, bite and heal, and
 * Sentry Bloom's warning, seeds, store, health, spot and cooldown. Pure: no Minecraft is started.
 */
final class NatureSpellsContract {
    private NatureSpellsContract() {}

    static void run() {
        require(BossSpells.get(Move.THORNBITE) != null && BossSpells.get(Move.SENTRY_BLOOM) != null, "Thornbite and Sentry Bloom are built");
        thornbite();
        sentryBloom();
    }

    private static void thornbite() {
        Timing t = ThornbiteSpell.TIMING;
        require(t.release() >= 14 && t.release() <= 18, "The flytrap forms for about 0.8 s: " + t.release());
        require(t.release() - t.aimLock() >= 3 && t.release() - t.aimLock() <= 5, "Thornbite's aim locks about a fifth of a second before the lunge");
        require(ThornbiteSpell.MOUTH_RADIUS * 2 >= 2.5 && ThornbiteSpell.MOUTH_RADIUS * 2 <= 3.5, "The mouth is about 3 blocks across");
        require(ThornbiteSpell.REACH < ThornbiteSpell.MOUTH_RADIUS, "A body is bitten only inside the drawn jaws");
        require(ThornbiteSpell.LENGTH >= 14 && ThornbiteSpell.LENGTH <= 18, "It lunges about 16 blocks: " + ThornbiteSpell.LENGTH);
        require(ThornbiteSpell.LUNGE >= 5 && ThornbiteSpell.LUNGE <= 9, "The lunge takes about a third of a second: " + ThornbiteSpell.LUNGE);
        require(t.release() + ThornbiteSpell.LUNGE + ThornbiteSpell.HOLD + ThornbiteSpell.RETRACT <= t.free(), "It has drawn back before the copy is free");
        require(t.release() + ThornbiteSpell.LUNGE + ThornbiteSpell.PULSE < t.free(), "The heal reaches the copy within its cast");
        float heal = ThornbiteSpell.HEAL * FirstWizardRules.health(1);
        require(heal >= 20 && heal <= 60, "A bite heals a few percent of the copy's health: " + heal);
        require(Math.abs(ThornbiteSpell.height(0, 4) - ThornbiteSpell.FORM_HEIGHT) < 1e-9 && Math.abs(ThornbiteSpell.height(4, 4) - ThornbiteSpell.BITE_HEIGHT) < 1e-9,
                "The mouth leaves from over the copy's head and drops to body height");
        // Walking (4.3 a second) from the lock, a player 5 blocks away (where Blink Rift lands behind them) clears the
        // mouth before it reaches them; sprinting (5.6), one 3 blocks away does.
        require(sidestep(4.3, 5) > ThornbiteSpell.REACH + .3, "A walking sidestep after the lock should dodge a bite from 5 blocks: " + sidestep(4.3, 5));
        require(sidestep(5.6, 3) > ThornbiteSpell.REACH + .3, "A sprinting sidestep after the lock should dodge a bite from 3 blocks: " + sidestep(5.6, 3));

        // The lunge, stepped as the spell steps it: a player standing on the locked line is bitten, one 1.5 aside is not.
        Arena arena = new Arena(new Vec3d(.5, 64, .5), FirstWizardRules.FLOOR_RADIUS);
        Random random = new Random(5);
        for (int i = 0; i < 2000; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * (arena.radius() - 4);
            Vec3d boss = new Vec3d(arena.centre().x + Math.cos(a) * r, arena.floor(), arena.centre().z + Math.sin(a) * r);
            double b = random.nextDouble() * Math.PI * 2, distance = 1.5 + random.nextDouble() * 13;
            Vec3d dir = new Vec3d(Math.cos(b), 0, Math.sin(b));
            Vec3d feet = boss.add(dir.multiply(distance));
            if (!arena.inside(feet, 1)) continue;
            Vec3d launch = boss.add(dir.multiply(ThornbiteSpell.FORM_AHEAD)).add(0, ThornbiteSpell.FORM_HEIGHT, 0);
            double drop = ThornbiteSpell.drop(Math.hypot(feet.x - launch.x, feet.z - launch.z)), reach = ThornbiteSpell.reach(arena, launch, dir);
            Vec3d end = ThornbiteSpell.mouth(launch, dir, reach, drop, arena.floor());
            require(arena.inside(end, 0), "Thornbite reached off the floor: " + end);
            require(bitten(launch, dir, drop, reach, body(feet)), "A player standing on the locked line was not bitten at " + distance);
            Vec3d aside = feet.add(new Vec3d(-dir.z, 0, dir.x).multiply(1.5));
            require(!bitten(launch, dir, drop, reach, body(aside)), "A player 1.5 aside of the locked line was bitten at " + distance);
        }
    }

    /** How far a player moving sideways at this speed gets from the lock to the mouth reaching them. */
    private static double sidestep(double speed, double distance) {
        double ticks = (ThornbiteSpell.TIMING.release() - ThornbiteSpell.TIMING.aimLock()) + distance / ThornbiteSpell.SPEED;
        return speed * ticks / 20;
    }

    private static boolean bitten(Vec3d launch, Vec3d dir, double drop, double reach, Box body) {
        for (int tick = 1; tick <= ThornbiteSpell.LUNGE; tick++) {
            double from = Math.min(reach, ThornbiteSpell.SPEED * (tick - 1)), to = Math.min(reach, ThornbiteSpell.SPEED * tick);
            for (int step = 1; step <= 6; step++)
                if (ThornbiteSpell.bites(body, ThornbiteSpell.mouth(launch, dir, from + (to - from) * step / 6.0, drop, launch.y - ThornbiteSpell.FORM_HEIGHT))) return true;
        }
        return false;
    }

    private static Box body(Vec3d feet) { return new Box(feet.x - .3, feet.y, feet.z - .3, feet.x + .3, feet.y + 1.8, feet.z + .3); }

    private static void sentryBloom() {
        Timing t = SentryBloomSpell.TIMING;
        require(t.release() >= 14 && t.release() <= 18, "The bud forms for about 0.8 s: " + t.release());
        require(SentryBloomSpell.UNFURL >= 25 && SentryBloomSpell.UNFURL <= 35, "The flower unfurls over about 1.5 s");
        require(SentryBloomSpell.SHOT_EVERY >= 25 && SentryBloomSpell.SHOT_EVERY <= 35, "A seed every 1.5 s or so");
        require(SentryBloomSpell.STANDS >= 220 && SentryBloomSpell.STANDS <= 260, "It delivers about 12 s after it opens");
        require(SentryBloomSpell.HEIGHT >= 5 && SentryBloomSpell.HEIGHT <= 7, "The flower is about 6 blocks tall");
        // Slow enough to sidestep: from 12 blocks a seed takes most of a second, and a seed is narrow.
        require(SentryBloomSpell.SEED_SPEED * 20 <= 14, "Seeds fly no faster than 14 blocks a second");
        require(12 / SentryBloomSpell.SEED_SPEED / 20 * 4.3 > SentryBloomSpell.SEED_REACH + .3 + .5, "A walking sidestep clears a seed from 12 blocks");
        double seed = armored(FirstWizardRules.GROUND, 20, 8);
        require(seed >= 1.5 && seed <= 2.5, "A seed is a light blow, about the lingering ground's: " + seed);
        float max = FirstWizardRules.health(1), perHit = SentryBloomSpell.STORE_PER_HIT * max, cap = SentryBloomSpell.STORE_CAP * max;
        require(perHit >= 15 && perHit <= 30, "A seed's hit stores a couple of percent: " + perHit);
        require(cap >= 80 && cap <= 120, "The store caps near a tenth: " + cap);
        require(SentryBloomSpell.hitsToFill() == Math.round(cap / perHit), "Hits to fill the store");
        int shots = (SentryBloomSpell.STANDS - SentryBloomSpell.FIRST_SHOT) / SentryBloomSpell.SHOT_EVERY + 1;
        require(shots >= SentryBloomSpell.hitsToFill() + 2, "It shoots enough to fill its store with some misses: " + shots);
        require(SentryBloomSpell.health(1) == 50 && SentryBloomSpell.health(2) > SentryBloomSpell.health(1), "The flower has 50 health alone, more in co-op");
        require(SentryBloomSpell.COOLDOWN >= 200, "Not again for a while after one ends");
        Vec3d launch = new Vec3d(0, 66.9, 0), spot = new Vec3d(10, 64, 4);
        require(SentryBloomSpell.lob(launch, spot, 1).distanceTo(spot) < 1 && SentryBloomSpell.lob(launch, spot, .5).y > launch.y, "The bud is lobbed in an arc to its spot");

        // It is planted on the floor, off to one side, away from the players.
        Arena arena = new Arena(new Vec3d(.5, 64, .5), FirstWizardRules.FLOOR_RADIUS);
        Random random = new Random(13);
        int near = 0, cases = 0;
        for (int i = 0; i < 3000; i++) {
            Vec3d boss = arena.onFloor(spot(arena, random), FirstWizardRules.EDGE);
            List<Vec3d> players = new ArrayList<>();
            for (int p = 0; p < 1 + random.nextInt(3); p++) players.add(arena.onFloor(spot(arena, random), 0));
            List<Vec3d> spots = SentryBloomSpell.spots(arena, boss, players.getFirst(), players, random::nextDouble);
            require(!spots.isEmpty(), "Sentry Bloom found nowhere to grow");
            for (Vec3d s : spots) require(arena.inside(s, FirstWizardRules.EDGE + SentryBloomSpell.POOL_RADIUS - 1e-6) && s.y == arena.floor(), "A flower off the floor: " + s);
            if (players.size() == 1 && boss.distanceTo(players.getFirst()) >= 6) {
                cases++;
                Vec3d best = spots.getFirst();
                if (Math.hypot(best.x - players.getFirst().x, best.z - players.getFirst().z) < 7) near++;
            }
        }
        require(near < cases * .03, "Alone, the flower should grow at least 7 blocks from the player nearly always: " + near + " of " + cases);
    }

    private static Vec3d spot(Arena arena, Random random) {
        double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * arena.radius();
        return new Vec3d(arena.centre().x + Math.cos(a) * r, arena.floor(), arena.centre().z + Math.sin(a) * r);
    }

    private static double armored(double damage, double armor, double toughness) {
        double held = Math.clamp(armor - 4 * damage / (toughness + 8), armor / 5, 20);
        return damage * (1 - held / 25);
    }

    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
