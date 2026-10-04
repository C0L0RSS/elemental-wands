package com.anton.elementalwands.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

/** Health, the shell break, the core pulse, back volleys and difficulty pacing, without a world. */
public final class GuardianShellContractTest {
    public static void run() {
        require(GuardianShellRules.health(1) == 1400 && GuardianShellRules.health(2) == 2400
                && GuardianShellRules.health(4) == 4400, "Party health changed");
        for (int party = 1; party <= 8; party++) {
            float maximum = GuardianShellRules.health(party);
            // One meteor volley from every player stays a modest bite, and the gate holds phase one anyway.
            require(party * GuardianShellRules.impact(211) < maximum * .4f, "One meteor volley skips the fight for party " + party);
        }
        require(GuardianShellRules.impact(7) == 7, "Ordinary attacks were softened");
        require(GuardianShellRules.impact(211) > GuardianShellRules.impact(60), "Larger ultimates lost their advantage");
        require(GuardianShellRules.gate(1400) == 700, "The break no longer sits at half health");
        require(GuardianShellRules.cracks(1400, 1400, false) == 0 && GuardianShellRules.cracks(1226, 1400, false) == 0
                && GuardianShellRules.cracks(1225, 1400, false) == 1 && GuardianShellRules.cracks(1050, 1400, false) == 2
                && GuardianShellRules.cracks(875, 1400, false) == 3 && GuardianShellRules.cracks(700, 1400, false) == 3
                && GuardianShellRules.cracks(1400, 1400, true) == 3, "Crack stages no longer track the way to the break");
        require(GuardianShellRules.openness(-1, false) == 0 && GuardianShellRules.openness(40, false) == 0,
                "Ribs opened before the break");
        require(GuardianShellRules.openness(0, true) == 0 && GuardianShellRules.openness(GuardianPhaseRules.BURST, true) == 0,
                "Ribs opened before the burst");
        require(GuardianShellRules.openness(GuardianPhaseRules.BURST + GuardianShellRules.RIB_OPEN_TICKS, true) == 1
                && GuardianShellRules.openness(-1, true) == 1, "Ribs did not stay open after the break");
        require(GuardianPhaseRules.BURST + GuardianShellRules.RIB_OPEN_TICKS <= GuardianPhaseRules.TRANSITION_TICKS,
                "Ribs still opening when the fight resumes");
        require(GuardianPhaseRules.BURST >= 30, "The break's warning is shorter than a second and a half");

        // The pull, integrated like vanilla ground movement (input + pull, then 0.546 friction).
        double edge = GuardianPulseRules.BLAST_RADIUS;
        require(pulled(10, 0) <= edge - .5, "Standing still at ten blocks was not dragged into the blast");
        require(pulled(edge - .5, .1) > edge && pulled(edge - .5, .1) - (edge - .5) < 2,
                "Walking away from the blast edge should barely escape");
        require(pulled(3, .13) > edge, "Sprinting from close in did not escape");
        require(pulled(GuardianPulseRules.PULL_RADIUS + 1, 0) == GuardianPulseRules.PULL_RADIUS + 1, "The pull reached past its radius");
        require(GuardianPulseRules.inBlast(Vec3d.ZERO, new Box(5.5, 0, -.3, 5.9, 1.8, .3))
                && !GuardianPulseRules.inBlast(Vec3d.ZERO, new Box(6.2, 0, -.3, 6.8, 1.8, .3)), "Blast edge moved");
        require(GuardianPulseRules.charge(-1) == 0 && GuardianPulseRules.charge(GuardianPulseRules.BLAST) == 1
                && GuardianPulseRules.charge(GuardianPulseRules.PULL_START) < GuardianPulseRules.charge(GuardianPulseRules.BLAST - 1)
                && GuardianPulseRules.charge(GuardianPulseRules.DURATION) == 0, "Core charge no longer builds to the blast");
        require(GuardianPulseRules.BLAST - GuardianPulseRules.PULL_START >= 30, "Pull too short to read and escape");

        // Back volleys lift off behind the Guardian, one separate cluster per target.
        Vec3d feet = Vec3d.ZERO, forward = Vec3d.fromPolar(0, 30);
        for (int cluster = 0; cluster < 3; cluster++) for (int i = 0; i < GuardianVolleyRules.COUNT; i++) {
            Vec3d low = GuardianVolleyRules.socket(feet, 30, cluster, 3, i, 0), high = GuardianVolleyRules.socket(feet, 30, cluster, 3, i, 1);
            require(forward.dotProduct(new Vec3d(low.x, 0, low.z)) < 0, "Volley stone formed in front of the Guardian");
            require(low.y > 4.5 && high.y > low.y + 1, "Volley stones did not rise off its back");
        }
        require(GuardianVolleyRules.socket(feet, 30, 0, 3, 1, 1).distanceTo(GuardianVolleyRules.socket(feet, 30, 1, 3, 1, 1)) >= 2,
                "Volley clusters overlap");

        // Pressure grows with the party up to four players and with difficulty; health keeps scaling past four.
        require(GuardianCombatRules.restScale(1, Difficulty.NORMAL) == 1 && GuardianCombatRules.cooldownScale(1, Difficulty.NORMAL) == 1,
                "Solo Normal pacing drifted");
        for (int party = 2; party <= 4; party++)
            require(GuardianCombatRules.restScale(party, Difficulty.NORMAL) < GuardianCombatRules.restScale(party - 1, Difficulty.NORMAL)
                    && GuardianCombatRules.cooldownScale(party, Difficulty.NORMAL) < GuardianCombatRules.cooldownScale(party - 1, Difficulty.NORMAL),
                    "Pressure did not grow with player " + party);
        require(GuardianCombatRules.restScale(8, Difficulty.NORMAL) == GuardianCombatRules.restScale(4, Difficulty.NORMAL)
                && GuardianShellRules.health(8) > GuardianShellRules.health(4), "Pressure must cap at four while health grows");
        require(GuardianCombatRules.restScale(1, Difficulty.HARD) < 1 && GuardianCombatRules.restScale(1, Difficulty.EASY) > 1,
                "Difficulty no longer changes rest");
        require(GuardianCombatRules.delayChance(Difficulty.EASY) < GuardianCombatRules.delayChance(Difficulty.NORMAL)
                && GuardianCombatRules.delayChance(Difficulty.NORMAL) < GuardianCombatRules.delayChance(Difficulty.HARD)
                && GuardianCombatRules.delayChance(Difficulty.HARD) < 1, "Delayed slam chance out of order");
        require(GuardianCombatRules.scaled(12, .45) >= 1 && GuardianCombatRules.scaled(1, .1) == 1, "Rest collapsed to zero");

        // Delayed slams pause at the apex: later impact, same follow-through; fast combo slams never hold.
        for (int hold : GuardianPhaseRules.SLAM_HOLDS) {
            require(GuardianPhaseRules.slamImpact(false, hold) == GuardianPhaseRules.slamImpact(false) + hold
                    && GuardianPhaseRules.slamDuration(false, hold) - GuardianPhaseRules.slamImpact(false, hold)
                    == GuardianPhaseRules.slamDuration(false) - GuardianPhaseRules.slamImpact(false), "Held slam lost its follow-through");
            require(GuardianPhaseRules.slamClip(false, hold).equals("slam_hold_" + hold), "Held slam clip name drifted");
            require(GuardianPhaseRules.SLAM_APEX * 20 < GuardianPhaseRules.slamImpact(false), "Hold starts after the impact");
        }
        require(GuardianPhaseRules.slamImpact(true, 16) == GuardianPhaseRules.slamImpact(true)
                && GuardianPhaseRules.slamClip(true, 16).equals("slam_fast"), "A fast combo slam paused");

        // The fists cover its front and its feet, not the space behind it.
        Box body = new Box(-.3, 0, -.3, .3, 1.8, .3);
        Vec3d ahead = Vec3d.fromPolar(0, 0);
        require(GuardianCombatRules.fistContact(Vec3d.ZERO, 0, body.offset(ahead.multiply(4))), "Fists missed a player in front");
        require(!GuardianCombatRules.fistContact(Vec3d.ZERO, 0, body.offset(ahead.multiply(-4))), "Fists hit a player behind it");
        require(GuardianCombatRules.fistContact(Vec3d.ZERO, 0, body.offset(ahead.multiply(-1))), "Fists missed a player at its feet");
        require(!GuardianCombatRules.fistContact(Vec3d.ZERO, 0, body.offset(ahead.multiply(6))), "Fists reached too far");
        require(!GuardianCombatRules.fistContact(Vec3d.ZERO, 0, body.offset(ahead.multiply(3)).offset(0, 4, 0)), "Fists hit a player high above");
        System.out.println("Guardian shell checks passed: party health, burst envelope, half-health gate, crack stages, rib opening, "
                + "pull escape by walking/sprinting, blast edge, volley sockets, pressure cap and difficulty, delayed slams, fist reach.");
    }

    /** Final distance from the core after the pull, for a player starting {@code distance} away pushing outward with {@code input}. */
    private static double pulled(double distance, double input) {
        Vec3d core = Vec3d.ZERO;
        double x = distance, velocity = 0;
        for (int tick = GuardianPulseRules.PULL_START; tick < GuardianPulseRules.BLAST; tick++) {
            velocity += input + GuardianPulseRules.pull(core, new Vec3d(x, 0, 0)).x;
            x += velocity;
            velocity *= .546;
        }
        return x;
    }
    private static void require(boolean ok, String why) { if(!ok) throw new AssertionError(why); }
}
