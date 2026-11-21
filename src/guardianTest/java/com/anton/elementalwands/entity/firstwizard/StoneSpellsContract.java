package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Move;
import com.anton.elementalwands.entity.firstwizard.spell.BossSpells;
import com.anton.elementalwands.entity.firstwizard.spell.FaultlineSpell;
import com.anton.elementalwands.entity.firstwizard.spell.FireballSpell;
import com.anton.elementalwands.entity.firstwizard.spell.GroundZone;
import com.anton.elementalwands.entity.firstwizard.spell.StoneWallSpell;
import com.anton.elementalwands.entity.firstwizard.spell.Timing;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.util.math.Vec3d;

/**
 * The First Wizard's Stone spells as pure rules, part of {@link FirstWizardContractTest}: Stone Wall's timing, size and
 * line (behind the target, across its way out, on the floor, two blocks thick and watertight), the shove that keeps a
 * player on its line from being entombed, and Faultline's timing, shape, speed, throw and follow-up.
 */
final class StoneSpellsContract {
    private StoneSpellsContract() {}

    static void run() {
        require(BossSpells.available().contains(Move.STONE_WALL) && BossSpells.available().contains(Move.FAULTLINE), "Stone Wall and Faultline are built");
        wall();
        faultline();
        followUp();
    }

    private static void wall() {
        Timing t = StoneWallSpell.TIMING;
        require(t.release() >= 16 && t.release() <= 24, "Ink runs along the wall's line for about a second: " + t.release());
        require(t.aimLock() <= 2, "The wall's line is fixed as the ink appears, so the ink is the warning");
        require(t.free() - t.release() <= 8, "The copy is free soon after the wall rises");
        require(StoneWallSpell.HEIGHT == 5 && StoneWallSpell.HALF_THICK == 1.0, "The wall is about 5 tall and 2 thick");
        require(StoneWallSpell.LENGTH >= 24 && StoneWallSpell.LENGTH <= 30, "The wall is about 27 long");
        require(StoneWallSpell.risen() - t.release() <= 5, "It rises quickly: " + (StoneWallSpell.risen() - t.release()) + " ticks");
        require(StoneWallSpell.STAND >= 100 && StoneWallSpell.STAND <= 140, "It stands about 6 s");
        require(StoneWallSpell.gone() + 20 < com.anton.elementalwands.block.FirstWizardWallBlock.LIFE + t.release(),
                "A wall block's own clearing must come well after the spell takes it down");

        Arena arena = new Arena(new Vec3d(.5, 64, .5), FirstWizardRules.FLOOR_RADIUS);
        Random random = new Random(23);
        int cast = 0;
        for (int i = 0; i < 4000; i++) {
            Vec3d boss = arena.onFloor(spot(arena, random, 1.05), FirstWizardRules.EDGE);
            Vec3d target = arena.onFloor(spot(arena, random, 1), 1);
            double apart = Math.hypot(target.x - boss.x, target.z - boss.z);
            if (apart < Move.STONE_WALL.near || apart > Move.STONE_WALL.far) continue;
            Vec3d[] line = StoneWallSpell.line(arena, boss, target);
            if (line == null) continue;
            cast++;
            Vec3d a = line[0], b = line[1], mid = a.lerp(b, .5);
            require(arena.inside(a, StoneWallSpell.MARGIN - 1e-6) && arena.inside(b, StoneWallSpell.MARGIN - 1e-6), "A wall off the floor: " + a + " " + b);
            require(a.distanceTo(b) <= StoneWallSpell.LENGTH + 1e-6 && a.distanceTo(b) >= StoneWallSpell.SHORTEST, "A wall of the wrong length: " + a.distanceTo(b));
            // Behind the target: further from the copy, across the line between them.
            Vec3d away = new Vec3d(target.x - boss.x, 0, target.z - boss.z).normalize();
            double behind = (mid.x - target.x) * away.x + (mid.z - target.z) * away.z;
            require(behind >= StoneWallSpell.BEHIND_LEAST - 1.2 && behind <= StoneWallSpell.BEHIND + 1.2, "The wall should run a few blocks behind the target: " + behind);
            double square = Math.abs((b.x - a.x) * away.x + (b.z - a.z) * away.z) / a.distanceTo(b);
            require(square < 1e-6, "The wall should cross the line from the copy to the target");
            // The copy stands well clear of it, so the Fireball that follows has an open lane.
            require(GroundZone.segment(boss.x, boss.z, a.x, a.z, b.x, b.z) > StoneWallSpell.HALF_THICK + 3, "The wall rises too near the copy");
            List<long[]> cells = StoneWallSpell.cells(arena, a, b);
            require(!cells.isEmpty(), "A wall with no cells");
            Set<Long> filled = new HashSet<>();
            for (long[] cell : cells) {
                require(arena.inside(new Vec3d(cell[0] + .5, 64, cell[1] + .5), .5 - 1e-9), "A wall cell off the floor");
                filled.add(cell[0] * 100000 + cell[1]);
            }
            // Watertight: walking straight across its middle third meets a cell, at every angle.
            Vec3d along = b.subtract(a).normalize(), across = new Vec3d(-along.z, 0, along.x);
            for (double u = -.3; u <= .3; u += .05) {
                Vec3d on = mid.add(b.subtract(a).multiply(u));
                boolean met = false;
                for (double v = -2; v <= 2; v += .1) {
                    Vec3d p = on.add(across.multiply(v));
                    if (filled.contains((long) Math.floor(p.x) * 100000 + (long) Math.floor(p.z))) { met = true; break; }
                }
                require(met, "A gap through the wall at " + on);
                // A player-sized body squeezing through where two cells meet at a corner: never clear.
                for (double v = -1.5; v <= 1.5; v += .1) {
                    Vec3d p = on.add(across.multiply(v));
                    boolean blocked = false;
                    for (double dx : new double[]{-.3, .3}) for (double dz : new double[]{-.3, .3})
                        if (filled.contains((long) Math.floor(p.x + dx) * 100000 + (long) Math.floor(p.z + dz))) blocked = true;
                    if (!blocked && Math.abs(v) < .6) throw new AssertionError("A body fits inside the wall at " + p);
                }
            }
            // Anyone on the line is set down clear of every cell, on the floor.
            for (int k = 0; k < 6; k++) {
                Vec3d feet = mid.add(along.multiply((random.nextDouble() - .5) * a.distanceTo(b) * .8)).add(across.multiply((random.nextDouble() - .5) * 2));
                Vec3d to = StoneWallSpell.shove(arena, a, b, feet, .3);
                require(arena.inside(to, .5 - 1e-6), "A shove off the floor");
                for (double dx : new double[]{-.3, .3}) for (double dz : new double[]{-.3, .3})
                    require(!filled.contains((long) Math.floor(to.x + dx) * 100000 + (long) Math.floor(to.z + dz)) || !arena.inside(to, 2.5),
                            "A shove left a body in the wall at " + to);
                double sideBefore = (feet.x - a.x) * across.x + (feet.z - a.z) * across.z, sideAfter = (to.x - a.x) * across.x + (to.z - a.z) * across.z;
                require(Math.signum(sideBefore) == Math.signum(sideAfter) || Math.abs(sideBefore) < 1e-9, "A shove should go to the nearer side");
            }
        }
        require(cast > 1000, "Most walls fit: " + cast);
    }

    private static void faultline() {
        Timing t = FaultlineSpell.TIMING;
        require(t.release() >= 16 && t.release() <= 20, "Ink runs out for about 0.9 s before the wave: " + t.release());
        require(t.release() - t.aimLock() >= 4 && t.release() - t.aimLock() <= 6, "Faultline's aim locks about a quarter of a second before release");
        require(t.free() - t.release() <= 8, "The copy is free soon after the wave, to hit the thrown player again");
        require(FaultlineSpell.SPEED * 20 <= 30.01 && FaultlineSpell.SPEED * 20 >= 25, "The wave runs at about 30 blocks a second");
        require(Math.abs(FaultlineSpell.half(0) * 2 - 5) < .6, "About 5 wide at the copy");
        require(Math.abs(FaultlineSpell.half(50) * 2 - 12) < 1, "About 12 wide by 50 blocks out");
        require(FaultlineSpell.REACH_UP > 1.25 + .1, "A jump does not clear the spikes");
        // About 7 blocks up, with vanilla's gravity and drag.
        double y = 0, v = FaultlineSpell.THROW, top = 0;
        for (int i = 0; i < 80 && y >= 0; i++) { y += v; v = (v - .08) * .98; top = Math.max(top, y); }
        require(top >= 6 && top <= 8, "The throw should reach about 7 blocks: " + top);
        // A sidestep after the lock, at a sprint, clears the wave's edge before it arrives, from where it is meant to be used.
        for (double d = Move.FAULTLINE.near; d <= Move.FAULTLINE.far; d += 1) {
            double seconds = (t.release() - t.aimLock()) / 20.0 + d / (FaultlineSpell.SPEED * 20);
            require(5.6 * seconds >= FaultlineSpell.half(d) + .3 - 1e-9, "A sprint from the middle should clear the wave at " + d + " blocks: " + 5.6 * seconds);
        }
        Arena arena = new Arena(new Vec3d(.5, 64, .5), FirstWizardRules.FLOOR_RADIUS);
        Random random = new Random(29);
        for (int i = 0; i < 2000; i++) {
            Vec3d origin = arena.onFloor(spot(arena, random, 1.05), FirstWizardRules.EDGE);
            double angle = random.nextDouble() * Math.PI * 2;
            Vec3d dir = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
            double length = FaultlineSpell.length(arena, origin, dir);
            require(arena.inside(origin.add(dir.multiply(length)), FaultlineSpell.MARGIN - 1e-6), "The wave runs off the floor");
            require(length > arena.radius() - 4 || !arena.inside(origin.add(dir.multiply(length + 1)), FaultlineSpell.MARGIN), "The wave should run to the floor's edge");
            for (int lane = 0; lane < FaultlineSpell.LANES; lane++) {
                double end = FaultlineSpell.laneEnd(pos -> false, origin, dir, lane, length, at -> arena.inside(at, FaultlineSpell.MARGIN));
                require(arena.inside(FaultlineSpell.lanePoint(origin, dir, lane, Math.max(0, end)), FaultlineSpell.MARGIN - 1e-6) || end <= .5, "A lane off the floor");
            }
        }
        Vec3d o = new Vec3d(0, 64, 0), dir = new Vec3d(0, 0, -1);
        require(FaultlineSpell.inShape(o, dir, new Vec3d(0, 64, -14), .3), "A player straight down the line is in it");
        require(!FaultlineSpell.inShape(o, dir, new Vec3d(5, 64, -14), .3), "A player 5 aside at 14 blocks is clear");
        require(!FaultlineSpell.inShape(o, dir, new Vec3d(0, 64, 3), .3), "Nothing behind the copy");
    }

    private static void followUp() {
        Set<Move> all = EnumSet.allOf(Move.class);
        require(FirstWizardRules.followUp(Move.FAULTLINE, all, () -> 0) == Move.FIREBALL, "Faultline, then a Fireball at the thrown player");
        require(FirstWizardRules.followUp(Move.FAULTLINE, all, () -> .99) == null, "Not every time");
        // The Fireball's throw comes after the thrown player has peaked and before they could walk far from the landing.
        int fireballRelease = FaultlineSpell.TIMING.free() - FaultlineSpell.TIMING.release() + FirstWizardRules.FOLLOW_GAP + FireballSpell.TIMING.release();
        require(fireballRelease >= 20 && fireballRelease <= 50, "The follow-up's Fireball leaves " + fireballRelease + " ticks after the wave");
    }

    private static Vec3d spot(Arena arena, Random random, double reach) {
        double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * arena.radius() * reach;
        return new Vec3d(arena.centre().x + Math.cos(a) * r, arena.floor(), arena.centre().z + Math.sin(a) * r);
    }

    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
