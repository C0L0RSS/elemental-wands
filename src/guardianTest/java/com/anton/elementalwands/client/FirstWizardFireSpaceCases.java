package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.spell.GravityWellSpell;
import com.anton.elementalwands.entity.firstwizard.spell.MeteorSpell;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The Meteor and Gravity Well cases of {@link FirstWizardFightSmoke}. The player really moves on their own client for
 * the dodges (walking, sprinting, a dash out of the well; a sprint out of the Meteor's circle), with the pull applied
 * the way it is in play; everything else is staged on the server as the other cases are.
 *
 * <ul>
 *   <li>{@code meteor-hit}: standing in the middle: one heavy hit, then the burning ground burns about once a second
 *       and ends.</li>
 *   <li>{@code meteor-rim}: moved 7 blocks aside after the mark is set: one medium hit.</li>
 *   <li>{@code meteor-leave}: sprinting out once the mark is set: untouched, well out of the circle at the impact.</li>
 *   <li>{@code gravity-stand}: standing 6 out when it opens: dragged in, nothing while it pulls, a heavy hit at the burst.</li>
 *   <li>{@code gravity-walk}: walking outward from 6: loses ground.</li>
 *   <li>{@code gravity-sprint}: sprinting outward from 6: gains ground, untouched.</li>
 *   <li>{@code gravity-dash}: dragged toward the middle, then a dash outward (a Waylay Dash's 1.5 a tick) and a sprint:
 *       clear of the burst at once, out of the pull soon after, untouched.</li>
 *   <li>{@code gravity-meteor}: the combination: a Meteor cast as the well's follow-up is centred on the well and lands
 *       while it still pulls.</li>
 * </ul>
 */
final class FirstWizardFireSpaceCases {
    private FirstWizardFireSpaceCases() {}

    static final List<String> NAMES = List.of("meteor-hit", "meteor-rim", "meteor-leave", "gravity-stand", "gravity-walk",
            "gravity-sprint", "gravity-dash", "gravity-meteor");

    static boolean mine(String name) { return NAMES.contains(name); }

    private enum Run { STILL, WALK, SPRINT }

    // Set on the server thread, read by the client's.
    private static volatile Run run = Run.STILL;
    private static volatile Vec3d runFrom;
    private static volatile boolean dash;
    // The server's measurements for the case in hand.
    private static Vec3d mark, well, placedAt;
    private static int seenWounds, impactAge = -1, burstAge = -1, groundEndedAge = -1, lateBurns, lateCast = -1, dashedAge = -1;
    private static int wellStageAtImpact = -1, outOfPullAge = -1, clearedBurstAge = -1;
    private static double distanceAtOpen = -1, distanceBeforeBurst = -1, distanceAtImpact = -1, furthest;
    private static boolean placed, offFloor, followed;
    private static final List<String> woundsAt = new ArrayList<>();
    private static final List<Integer> woundTicks = new ArrayList<>();
    /** The client's own view of the player each half second while they dodge: distance out and speed. */
    private static final List<String> TRACE = java.util.Collections.synchronizedList(new ArrayList<>());

    // ── Client thread ─────────────────────────────────────────────────────────

    /** The player's own movement for the dodges: their yaw away from the danger, forward held, sprinting, a dash. */
    static void client(MinecraftClient c, String name, int t, int ticks) {
        var player = c.player;
        if (player == null) return;
        // The game sends a player's movement only while they are the camera, and the camera is the fixture's stand:
        // report where the pull and their own input took them, as the game would.
        if (t >= 2 && player.networkHandler != null)
            player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full(player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), player.getPitch(), player.isOnGround(), player.horizontalCollision));
        Run now = t >= ticks - 3 ? Run.STILL : run;
        Vec3d from = runFrom;
        if (from != null && t % 10 == 0) TRACE.add(String.format(Locale.ROOT, "t%d %s %.2f v%.3f%s", t, now, flat(player.getEntityPos(), from),
                player.getVelocity().horizontalLength(), player.isOnGround() ? "" : " air"));
        if (now == Run.STILL || from == null) {
            c.options.forwardKey.setPressed(false);
            if (player.isSprinting()) player.setSprinting(false);
            player.forwardSpeed = 0;
            return;
        }
        Vec3d away = player.getEntityPos().subtract(from).multiply(1, 0, 1);
        if (away.lengthSquared() < 1e-4) away = new Vec3d(1, 0, 0);
        away = away.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-away.x, away.z));
        player.setYaw(yaw); player.setHeadYaw(yaw); player.setBodyYaw(yaw);
        c.options.forwardKey.setPressed(true);
        // Not the camera, so the game does not copy the keys into its movement: it is set here, as the keys would.
        player.forwardSpeed = 1;
        player.setSprinting(now == Run.SPRINT);
        if (dash) {
            dash = false;
            player.setVelocity(away.x * 1.5, .3, away.z * 1.5);
        }
    }

    // ── Server thread ─────────────────────────────────────────────────────────

    static void during(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, ArmorStandEntity camera, List<Float> wounds,
            String name, int t, double age, Vec3d home) {
        if (t == 1) reset();
        var w = server.getOverworld();
        FirstWizardEffectEntity markFx = effect(server, home, MeteorSpell.MARK_KIND), wellFx = effect(server, home, GravityWellSpell.WELL_KIND);
        FirstWizardEffectEntity meteorGround = effect(server, home, MeteorSpell.GROUND_KIND);
        if (markFx != null && mark == null) mark = markFx.getEntityPos();
        if (wellFx != null && well == null) well = wellFx.getEntityPos();
        for (Vec3d at : new Vec3d[]{mark, well}) if (at != null && !k.arena().inside(at, FirstWizardRules.EDGE - 1e-6)) offFloor = true;
        // Every wound, with when it came.
        synchronized (wounds) {
            while (seenWounds < wounds.size()) { woundTicks.add(t); woundsAt.add(String.format(Locale.ROOT, "%.0f@t%d", wounds.get(seenWounds++), t)); }
        }
        if (meteorGround != null && impactAge < 0) { impactAge = t; wellStageAtImpact = wellFx == null ? -1 : wellFx.stage(); }
        if (wellFx != null && wellFx.stage() == 2 && burstAge < 0) burstAge = t;
        Vec3d feet = p.getEntityPos();
        boolean meteorCase = name.startsWith("meteor");
        Vec3d centre = meteorCase ? mark : well;
        if (centre != null) furthest = Math.max(furthest, flat(feet, centre));

        if (meteorCase) {
            if (mark != null) {
                frame(camera, mark, t >= 84 ? 20 : 33, t >= 84 ? 8 : 9, t >= 84 ? 1.5 : 18);
                if (impactAge < 0) distanceAtImpact = flat(feet, mark);
            }
            switch (name) {
                case "meteor-hit" -> {
                    // Stands in the middle: struck (and knocked back), then put back in the burning ground until it ends.
                    // Every blow knocks them back (vanilla knockback from the copy), so each one is followed by a step back in.
                    if (impactAge >= 0 && groundEndedAge < 0 && !woundTicks.isEmpty() && woundTicks.getLast() == t - 3) place(p, mark, mark.add(0, 0, 1));
                    if (impactAge >= 0 && meteorGround == null && groundEndedAge < 0) groundEndedAge = t;
                }
                case "meteor-rim" -> {
                    if (mark != null && !placed) { placed = true; place(p, mark.add(7, 0, 0), mark); }
                }
                case "meteor-leave" -> {
                    if (mark != null && impactAge < 0) { runFrom = mark; run = Run.SPRINT; }
                    if (impactAge >= 0) run = Run.STILL;
                }
                default -> {}
            }
            return;
        }

        if (well != null) frame(camera, well, 21, 11, .5);
        boolean open = wellFx != null && wellFx.stage() < 2;
        if (open && !placed) {
            placed = true;
            double out = switch (name) { case "gravity-dash" -> 3; case "gravity-meteor" -> 7; default -> 6; };
            placedAt = well.add(out, 0, 0);
            place(p, placedAt, well);
            runFrom = well;
            return;
        }
        if (!placed) return;
        double from = flat(feet, well);
        if (distanceAtOpen < 0 && t > 0 && open) distanceAtOpen = from;
        if (open && wellFx.stage() == 1 && wellFx.stageAge(0) >= GravityWellSpell.BUILDUP - 2 && distanceBeforeBurst < 0) distanceBeforeBurst = from;
        if (burstAge >= 0) run = Run.STILL;
        switch (name) {
            case "gravity-walk" -> { if (burstAge < 0) run = Run.WALK; }
            case "gravity-sprint" -> { if (burstAge < 0) run = Run.SPRINT; }
            case "gravity-dash" -> {
                // A second after it opens, a dash outward; then a sprint, as a player would.
                if (dashedAge < 0 && wellFx != null && wellFx.age(0) >= 20) { dashedAge = t; dash = true; run = Run.SPRINT; }
                if (dashedAge >= 0 && clearedBurstAge < 0 && from > GravityWellSpell.BURST + 1) clearedBurstAge = t;
                if (dashedAge >= 0 && outOfPullAge < 0 && from > GravityWellSpell.PULL_RADIUS) outOfPullAge = t;
                if (outOfPullAge >= 0) run = Run.STILL;
            }
            case "gravity-meteor" -> {
                // The well's follow-up: the Meteor, cast once the copy is free, as the fight would.
                if (!followed && k.moveName().equals("free")) {
                    followed = true; lateCast = t;
                    if (!k.testMove(p, FirstWizardRules.Move.METEOR)) woundsAt.add("the meteor would not cast");
                }
            }
            default -> {}
        }
    }

    static void verify(ServerPlayerEntity p, FirstWizardEntity k, String name, List<Float> hurt, List<String> notes, Consumer<String> fail) {
        run = Run.STILL; runFrom = null; dash = false;
        notes.add("wounds " + woundsAt);
        if (!TRACE.isEmpty()) notes.add("client " + TRACE);
        if (offFloor) fail.accept("a mark or a well off the floor: " + fmt(mark) + " " + fmt(well));
        switch (name) {
            case "meteor-hit" -> {
                if (groundEndedAge >= 0) lateBurns = (int) woundTicks.stream().filter(at -> at > groundEndedAge + 2).count();
                long burns = hurt.stream().skip(1).filter(w -> Math.abs(w - FirstWizardRules.GROUND) < .01f).count();
                notes.add("mark " + fmt(mark) + ", impact at t" + impactAge + ", ground ended at t" + groundEndedAge + ", burns after " + lateBurns);
                if (mark == null) fail.accept("no circle was marked");
                if (hurt.isEmpty() || Math.abs(hurt.getFirst() - FirstWizardRules.HEAVY) > .01f) fail.accept("standing in the middle should take a heavy hit (" + FirstWizardRules.HEAVY + ") first; took " + hurt);
                if (impactAge < 80 || impactAge > 100) fail.accept("the meteor should land about 4.5 s after the cast began (1.5 s raised, 3 s falling); landed at t" + impactAge);
                if (burns < 3) fail.accept("the burning ground should burn about once a second; burned " + burns + " times: " + hurt);
                if (hurt.stream().skip(1).anyMatch(w -> Math.abs(w - FirstWizardRules.GROUND) > .01f)) fail.accept("after the impact only ground burns: " + hurt);
                if (groundEndedAge < 0) fail.accept("the burning ground should end");
                if (lateBurns > 0) fail.accept("nothing should burn after the ground has ended");
                if (k.lingering() > 0) fail.accept("nothing of the Meteor should still be running");
            }
            case "meteor-rim" -> {
                if (hurt.size() != 1 || Math.abs(hurt.getFirst() - FirstWizardRules.MEDIUM) > .01f) fail.accept("7 blocks from the middle should take one medium hit (" + FirstWizardRules.MEDIUM + "); took " + hurt);
            }
            case "meteor-leave" -> {
                notes.add(String.format(Locale.ROOT, "%.1f from the middle at the impact", distanceAtImpact));
                if (!hurt.isEmpty()) fail.accept("sprinting out of the circle once it is marked should dodge it; took " + hurt);
                if (distanceAtImpact < MeteorSpell.RADIUS + 2) fail.accept("a sprint should be well out of the circle by the impact; " + distanceAtImpact);
            }
            case "gravity-stand", "gravity-walk", "gravity-sprint", "gravity-dash" -> {
                notes.add(String.format(Locale.ROOT, "well %s; %.1f out at the open, %.1f before the burst, furthest %.1f; burst at t%d%s",
                        fmt(well), distanceAtOpen, distanceBeforeBurst, furthest, burstAge, dashedAge >= 0 ? ", dashed t" + dashedAge + ", out of the pull t" + outOfPullAge : ""));
                if (well == null) { fail.accept("no well opened"); return; }
                if (burstAge < 0) fail.accept("the well never burst");
                if (Math.hypot(well.x - (placedAt.x - (name.equals("gravity-dash") ? 3 : 6)), well.z - placedAt.z) > .01) fail.accept("staging went wrong");
                switch (name) {
                    case "gravity-stand" -> {
                        if (distanceBeforeBurst > 3) fail.accept("standing still should be dragged into the middle; " + distanceBeforeBurst + " out before the burst");
                        if (hurt.size() != 1 || Math.abs(hurt.getFirst() - FirstWizardRules.HEAVY) > .01f) fail.accept("no damage while it pulls, one heavy hit at the burst; took " + woundsAt);
                    }
                    case "gravity-walk" -> {
                        if (!(distanceBeforeBurst < distanceAtOpen)) fail.accept("walking outward should lose ground: " + distanceAtOpen + " to " + distanceBeforeBurst);
                    }
                    case "gravity-sprint" -> {
                        if (!(distanceBeforeBurst > distanceAtOpen + 1)) fail.accept("sprinting outward should win: " + distanceAtOpen + " to " + distanceBeforeBurst);
                        if (!hurt.isEmpty()) fail.accept("sprinting out should clear the burst; took " + hurt);
                    }
                    case "gravity-dash" -> {
                        // From the middle a dash clears the burst at once, and with a sprint after it leaves the pull in about two seconds.
                        if (clearedBurstAge < 0 || clearedBurstAge - dashedAge > 12) fail.accept("a dash should clear the burst at once; clear at t" + clearedBurstAge + " after a dash at t" + dashedAge);
                        if (outOfPullAge < 0 || outOfPullAge - dashedAge > 50) fail.accept("a dash and a sprint should get out of the pull easily; out at t" + outOfPullAge + " after a dash at t" + dashedAge);
                        if (!hurt.isEmpty()) fail.accept("dashing out should clear the burst; took " + hurt);
                    }
                    default -> {}
                }
            }
            case "gravity-meteor" -> {
                double off = mark == null || well == null ? 99 : flat(mark, well);
                notes.add(String.format(Locale.ROOT, "well %s, meteor cast at t%d, marked %s (%.2f off), landed t%d with the well at stage %d, burst t%d",
                        fmt(well), lateCast, fmt(mark), off, impactAge, wellStageAtImpact, burstAge));
                if (mark == null) fail.accept("the follow-up Meteor never marked its circle");
                if (off > .5) fail.accept("after a Gravity Well the Meteor should be centred on the well; " + off + " off");
                if (impactAge < 0 || wellStageAtImpact < 0 || wellStageAtImpact > 1) fail.accept("the Meteor should land while the well still pulls; well stage " + wellStageAtImpact);
                if (hurt.isEmpty() || Math.abs(hurt.getFirst() - FirstWizardRules.HEAVY) > .01f) fail.accept("pulled into the middle, the Meteor's heavy hit comes first; took " + woundsAt);
            }
            default -> {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void reset() {
        run = Run.STILL; runFrom = null; dash = false;
        mark = well = placedAt = null;
        seenWounds = 0; impactAge = burstAge = groundEndedAge = lateCast = dashedAge = wellStageAtImpact = outOfPullAge = clearedBurstAge = -1;
        lateBurns = 0;
        distanceAtOpen = distanceBeforeBurst = distanceAtImpact = -1; furthest = 0;
        placed = offFloor = followed = false;
        woundsAt.clear(); woundTicks.clear(); TRACE.clear();
    }

    private static FirstWizardEffectEntity effect(MinecraftServer server, Vec3d home, String kind) {
        var found = server.getOverworld().getEntitiesByClass(FirstWizardEffectEntity.class, new Box(home, home).expand(70), e -> e.kind().equals(kind) && e.isAlive());
        return found.isEmpty() ? null : found.getFirst();
    }

    private static double flat(Vec3d a, Vec3d b) { return Math.hypot(a.x - b.x, a.z - b.z); }

    private static void place(ServerPlayerEntity p, Vec3d at, Vec3d facing) {
        Vec3d look = facing.subtract(at);
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0;
        p.networkHandler.requestTeleport(at.x, at.y, at.z, (float) Math.toDegrees(Math.atan2(-look.x, look.z)), 5);
    }

    /** The camera off to one side of a point, this far back and this high, looking at a point this high over it. */
    private static void frame(ArmorStandEntity camera, Vec3d at, double back, double high, double lookUp) {
        Vec3d eye = at.add(back * .62, high, back * .78), look = at.add(0, lookUp, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z)), pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        camera.refreshPositionAndAngles(eye.x, eye.y - camera.getStandingEyeHeight(), eye.z, yaw, pitch);
        camera.setHeadYaw(yaw); camera.setBodyYaw(yaw);
    }

    private static String fmt(Vec3d v) { return v == null ? "none" : String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z); }
}
