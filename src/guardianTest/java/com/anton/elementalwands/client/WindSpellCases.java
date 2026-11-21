package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.spell.SkyShearSpell;
import com.anton.elementalwands.entity.firstwizard.spell.WaylayDashSpell;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The First Wizard fight fixture's cases for its two Wind spells, kept out of {@link FirstWizardFightSmoke}.
 *
 * <ul>
 *   <li>{@code shear-stand}: a player 16 out who never jumps takes all three crescents, a medium hit each.</li>
 *   <li>{@code shear-jump}: the same player jumps each crescent with a real client jump, timed off the crescent's
 *       distance (as a player would read it), and takes nothing.</li>
 *   <li>{@code shear-mistimed}: jumps the first too early (landing before it arrives) and is struck, jumps the second
 *       in time and is not, and jumps the third too late and is struck.</li>
 *   <li>{@code dash-hit}: a player on its line takes one medium hit and is moved out of the wake.</li>
 *   <li>{@code dash-beside}: a player who steps 2.5 off the line during the tell takes nothing from the dash or wake.</li>
 *   <li>{@code dash-wake}: a player who steps aside, then stands in the wake once it has armed, burns about once a
 *       second until it ends, and never after.</li>
 * </ul>
 * Every crescent and the copy stay on the floor. The server-side parts run on the server thread; {@link #client} on
 * the client's.
 */
final class WindSpellCases {
    /** Where the player stands for each kind of case, from the copy's mark. */
    static final double SHEAR_Z = -16, DASH_Z = -12;
    /** Blocks between a crescent's front and the player's body when a jump is asked for: timed, early, late. */
    private static final double TIMED = 7, EARLY = 15.5, LATE = .4;

    /** Moves the player to an offset from the copy's mark (the fixture's own place). */
    interface Placer { void place(double x, double z); }

    private volatile boolean jumpWanted, reporting;
    private volatile int jumps;
    private final Set<Integer> crescents = new HashSet<>(), asked = new HashSet<>();
    private volatile double furthest;
    private volatile boolean stepped, inWake;
    private volatile int wakeEndedAt = -1, lateBurns, wakeEnteredAt = -1;
    private volatile Vec3d dashEnd;

    static boolean mine(String name) { return name.startsWith("shear") || name.startsWith("dash"); }

    /** Server, as a case starts. */
    void reset() {
        jumpWanted = false; jumps = 0; crescents.clear(); asked.clear(); furthest = 0;
        stepped = inWake = false; wakeEndedAt = lateBurns = wakeEnteredAt = -1; lateBurns = 0; dashEnd = null;
    }

    /** Client: a jump the server asked for, taken the first tick the player stands on the floor (as holding jump would). */
    void client(MinecraftClient c) {
        if (c.player == null) return;
        if (jumpWanted && c.player.isOnGround()) {
            jumpWanted = false;
            jumps++;
            c.player.jump();
        }
        // The camera is an armor stand, and vanilla only reports the player's movement while the player is the camera:
        // report it here, so the server sees the client's own jump as it happens.
        if (reporting && c.getCameraEntity() != c.player && c.player.networkHandler != null)
            c.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(
                    c.player.getX(), c.player.getY(), c.player.getZ(), c.player.isOnGround(), c.player.horizontalCollision));
    }

    /** Where the camera stands for a case and what it looks at, or null for the fixture's own framing. */
    static Vec3d[] camera(String name, Vec3d home) {
        if (name.equals("shear-stand") || name.equals("shear-jump")) {
            // Behind and above the player, as they would see the crescents come.
            Vec3d eye = home.add(4.5, 3.4, SHEAR_Z - 6.5);
            return new Vec3d[]{eye, home.add(0, .6, -3).subtract(eye)};
        }
        return null;
    }

    /** Server, each tick of a case after the first. wounded: the fixture just counted and healed a wound. */
    void during(ServerWorld world, ServerPlayerEntity p, FirstWizardEntity k, String name, int t, boolean wounded, Placer place) {
        if (t == 1) reset();
        Box around = new Box(k.arena().centre(), k.arena().centre()).expand(70);
        reporting = name.startsWith("shear");
        if (name.startsWith("shear")) {
            if (wounded) place.place(0, SHEAR_Z);       // set back on the mark after a hit's knockback
            List<FirstWizardEffectEntity> flying = new ArrayList<>(world.getEntitiesByClass(FirstWizardEffectEntity.class, around,
                    e -> e.isAlive() && e.kind().equals(SkyShearSpell.KIND)));
            flying.sort(Comparator.comparingLong(FirstWizardEffectEntity::start));
            for (FirstWizardEffectEntity e : flying) {
                crescents.add(e.getId());
                furthest = Math.max(furthest, k.arena().fromCentre(e.getEntityPos()));
            }
            List<Integer> order = new ArrayList<>(crescents);
            order.sort(Integer::compare);
            for (FirstWizardEffectEntity e : flying) {
                int index = order.indexOf(e.getId());
                double lead = switch (name) {
                    case "shear-jump" -> TIMED;
                    case "shear-mistimed" -> index == 0 ? EARLY : index == 1 ? TIMED : LATE;
                    default -> -1;
                };
                if (lead < 0 || asked.contains(e.getId())) continue;
                Vec3d dir = e.dir().normalize();
                double gap = p.getEntityPos().subtract(e.getEntityPos()).dotProduct(dir) - .3;
                if (gap <= lead && gap > -1) { asked.add(e.getId()); jumpWanted = true; }
            }
        } else if (name.startsWith("dash")) {
            FirstWizardEffectEntity line = world.getEntitiesByClass(FirstWizardEffectEntity.class, around,
                    e -> e.isAlive() && e.kind().equals(WaylayDashSpell.KIND)).stream().findFirst().orElse(null);
            if (line != null) dashEnd = line.auxPoint();
            double age = k.castAge(0);
            switch (name) {
                case "dash-hit" -> { if (wounded && !stepped) { stepped = true; place.place(6, DASH_Z); } }
                case "dash-beside", "dash-wake" -> {
                    // Off the line once it is fixed, during the tell.
                    if (!stepped && age >= 3 && age < WaylayDashSpell.TIMING.release() - 2) { stepped = true; place.place(name.equals("dash-beside") ? 2.5 : 3, DASH_Z); }
                    if (name.equals("dash-wake")) {
                        if (line != null && line.stage() == 1 && !inWake && line.stageAge(0) >= FirstWizardRules.GROUND_ARM + 2) {
                            inWake = true; wakeEnteredAt = t;
                            place.place(0, -7);
                        }
                        if (inWake && line == null && wakeEndedAt < 0) wakeEndedAt = t;
                        if (wakeEndedAt >= 0 && t > wakeEndedAt + 2 && wounded) lateBurns++;
                    }
                }
                default -> {}
            }
        }
    }

    /** Server, when a case ends: its judgement, into notes and failures. */
    void verify(String name, List<Float> hurt, FirstWizardEntity k, ServerPlayerEntity p, double floor, List<String> notes, Consumer<String> fail) {
        long medium = hurt.stream().filter(w -> Math.abs(w - FirstWizardRules.MEDIUM) < .01f).count();
        if (name.startsWith("shear")) {
            notes.add(String.format(Locale.ROOT, "crescents %d, jumps %d, wounds %s, furthest crescent %.1f from the centre", crescents.size(), jumps, hurt, furthest));
            if (crescents.size() != SkyShearSpell.COUNT) fail.accept("three crescents should leave; saw " + crescents.size());
            if (furthest > k.arena().radius() + .01) fail.accept("a crescent ran off the floor: " + furthest);
            if (hurt.stream().anyMatch(w -> Math.abs(w - FirstWizardRules.MEDIUM) > .01f)) fail.accept("every crescent hits for " + FirstWizardRules.MEDIUM + ": " + hurt);
            switch (name) {
                case "shear-stand" -> { if (medium != 3) fail.accept("a player who never jumps should take all three crescents; took " + hurt); }
                case "shear-jump" -> {
                    if (jumps < 3) fail.accept("the player should have jumped three times; jumped " + jumps);
                    if (!hurt.isEmpty()) fail.accept("a timed jump should clear every crescent; took " + hurt);
                }
                case "shear-mistimed" -> {
                    if (jumps < 3) fail.accept("the player should have jumped three times; jumped " + jumps);
                    if (medium != 2) fail.accept("an early and a late jump should each be struck and the timed one between clear; took " + hurt);
                }
                default -> {}
            }
            return;
        }
        Vec3d at = k.getEntityPos();
        double out = k.arena().fromCentre(at);
        Vec3d look = k.getRotationVector().multiply(1, 0, 1), toPlayer = p.getEntityPos().subtract(at).multiply(1, 0, 1);
        double facing = look.lengthSquared() < 1e-6 || toPlayer.lengthSquared() < 1e-6 ? 1 : look.normalize().dotProduct(toPlayer.normalize());
        notes.add(String.format(Locale.ROOT, "line to %s; it ended at (%.1f, %.1f), %.1f from the centre, facing the player %.2f; wounds %s",
                dashEnd == null ? "none" : String.format(Locale.ROOT, "(%.1f, %.1f)", dashEnd.x, dashEnd.z), at.x, at.z, out, facing, hurt));
        if (dashEnd == null) fail.accept("ink should run along the line during the tell");
        else if (at.subtract(dashEnd).horizontalLength() > .6) fail.accept("it should end where its line ends; line " + dashEnd + ", at " + at);
        if (Math.abs(at.y - floor) > .05) fail.accept("it should end on the floor; y " + at.y);
        if (out > k.arena().radius() - FirstWizardRules.EDGE + .01) fail.accept("it should end inside the floor; " + out + " from the centre");
        if (facing < .7) fail.accept("it should end facing back toward its target; facing " + facing);
        switch (name) {
            case "dash-hit" -> {
                if (medium != 1 || hurt.size() != 1) fail.accept("a player on its line should take one medium hit; took " + hurt);
                if (at.z > DASH_Z - 5) fail.accept("it should pass through the player and on; it ended at z " + at.z);
            }
            case "dash-beside" -> { if (!hurt.isEmpty()) fail.accept("a player 2.5 beside its line should take nothing; took " + hurt); }
            case "dash-wake" -> {
                long burns = hurt.stream().filter(w -> Math.abs(w - FirstWizardRules.GROUND) < .01f).count();
                notes.add("in the wake from tick " + wakeEnteredAt + "; it ended at tick " + wakeEndedAt + "; burns after " + lateBurns + "; lingering " + k.lingering());
                if (!inWake) fail.accept("the player was never put in the wake");
                if (burns < 2) fail.accept("standing in the wake should burn about once a second for its 3 s; burned " + burns + ": " + hurt);
                if (hurt.size() != burns) fail.accept("only the wake should hurt (the dash missed): " + hurt);
                if (wakeEndedAt < 0) fail.accept("the wake should end after about 3 s");
                if (lateBurns > 0) fail.accept("nothing should burn after the wake has ended");
                if (k.lingering() > 0) fail.accept("nothing of the dash should still be running");
            }
            default -> {}
        }
    }
}
