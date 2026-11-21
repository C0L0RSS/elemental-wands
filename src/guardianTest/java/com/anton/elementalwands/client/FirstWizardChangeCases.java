package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.firstwizard.FirstWizardChangeScene;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.spell.FireballSpell;
import com.anton.elementalwands.entity.firstwizard.spell.PhaseChange;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

/**
 * The change's cinematic in {@link FirstWizardFightSmoke} (the developer's request, Nov 19): the fight pauses for everyone
 * in it while the arm falls off and the tentacles burst out of the swelling, then the tell and the full sweep follow.
 *
 * <ul>
 *   <li>{@code change-cinematic}: a Fireball has left burning ground when a blow takes the copy past half; when the change
 *       begins, everything phase one left is gone. Through the cinematic the player is held (a teleport aside is undone),
 *       their camera is the scene's, they cannot be hurt (the copy's blow, a plain one) and cannot hurt the copy (a blow
 *       that would, an attack refused at the gate); the wand is in the copy's right hand. Control returns at its end, and
 *       the tell and the full sweep strike the standing player once (medium), starting on the copy's left. Recorded by
 *       day as the player sees it, letterbox included.</li>
 *   <li>{@code change-stop}: the fight stopped mid-cinematic: the copy is in phase two at once, nothing of the change is
 *       left, and the player is let go (server and client) and can be hurt again.</li>
 *   <li>{@code change-leave}: the only player leaves the fight mid-cinematic (made a spectator: the path a disconnect, a
 *       death or a fall also takes, the player no longer among the fighting): the copy, fighting, is in phase two at once
 *       and the player is let go.</li>
 *   <li>{@code change-late}: a player who joins the fight mid-cinematic (Creative, then Survival) is taken in where it has
 *       got to, let go at its end, and struck by the full sweep.</li>
 *   <li>{@code change-skip}: holding Sneak skips it: the change's clock jumps to the end of the cinematic, the player is
 *       let go, and the tell and the full sweep still follow and strike once.</li>
 *   <li>{@code change-look}: the change with nobody watching (the player Creative, far off), filmed close by day from the
 *       fixture's camera at her front left: a still at each beat, for the developer.</li>
 * </ul>
 */
final class FirstWizardChangeCases {
    private FirstWizardChangeCases() {}

    static final List<String> NAMES = List.of("change-cinematic", "change-stop", "change-leave", "change-late", "change-skip", "change-look");

    static boolean mine(String name) { return NAMES.contains(name); }
    /** The ones made to look at: lit by day. */
    static boolean lit(String name) { return name.equals("change-cinematic") || name.equals("change-look") || name.equals("change-skip"); }

    interface Placer { void place(double x, double z); }

    // Client side (written on the client thread, read on the server's).
    private static volatile boolean clientWatching, clientCinematic, clientSkipped, sneak, everCinematic;
    // Server side, for the case in hand.
    private static int changedAt = -1, endedAt = -1, stopAt = -1, joinAt = -1, joinedAt = -1, sneakFrom = -1, ringSide = 0;
    private static boolean leftoversBefore, leftoversCleared, watchingDuring, notWatchingAfter, copyHurt, playerHurt, gateFailed, heldBack, rightHanded,
            phaseTwoAtOnce, letGo, letGoClient, hurtAgain, clientDuring, clientAfter, watchingBeforeJoin, ringSeen, skipJumped;
    private static int woundsAtChange = -1;
    private static Vec3d lock;

    private static void reset() {
        clientWatching = clientCinematic = clientSkipped = sneak = everCinematic = false;
        changedAt = endedAt = stopAt = joinAt = joinedAt = sneakFrom = -1; ringSide = 0;
        leftoversBefore = leftoversCleared = watchingDuring = notWatchingAfter = copyHurt = playerHurt = gateFailed = heldBack = rightHanded
                = phaseTwoAtOnce = letGo = letGoClient = hurtAgain = clientDuring = clientAfter = watchingBeforeJoin = ringSeen = skipJumped = false;
        woundsAtChange = -1;
        lock = null;
    }

    /** Where the camera stands, and what it looks at (her front is -Z; her left is -X, facing so). */
    static Vec3d[] camera(String name, Vec3d home) {
        Vec3d eye, at;
        if (name.equals("change-look")) { eye = home.add(-2.7, 1.75, -3.1); at = home.add(-.35, 1.05, 0); }
        else { eye = home.add(-5.5, 3.2, -7); at = home.add(0, 1.2, -2.5); }
        return new Vec3d[]{eye, at.subtract(eye)};
    }

    // ── Client thread ────────────────────────────────────────────────────────

    static void client(MinecraftClient c) {
        clientWatching = FirstWizardChangeClient.watching();
        clientCinematic = BossIntroCamera.cinematic();
        clientSkipped = FirstWizardChangeClient.skipped();
        if (clientCinematic) everCinematic = true;
        // The letterbox is drawn in place of the HUD: show it while the scene owns the view, as a player sees it.
        c.options.hudHidden = !clientCinematic;
        c.options.sneakKey.setPressed(sneak);
    }

    // ── Server thread ────────────────────────────────────────────────────────

    static void start(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, String name) {
        reset();
        switch (name) {
            // A Fireball in hand (it leaves burning ground), then the blow that takes it past half.
            case "change-cinematic" -> k.testMove(p, FirstWizardRules.Move.FIREBALL);
            case "change-leave" -> { k.fight(); k.requestPhaseTwo(); }
            case "change-late", "change-look" -> { p.changeGameMode(GameMode.CREATIVE); k.requestPhaseTwo(); }
            default -> k.requestPhaseTwo();
        }
    }

    static void during(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, List<Float> wounds, String name, int t, Vec3d home, Placer place) {
        ServerWorld w = server.getOverworld();
        if (changedAt < 0 && k.phase() == FirstWizardEntity.CHANGING) {
            changedAt = t;
            woundsAtChange = wounds.size();
            rightHanded = k.getMainArm() == Arm.RIGHT;
        }
        int age = changedAt < 0 ? -1 : t - changedAt;
        boolean watching = FirstWizardChangeScene.watching(p);
        FirstWizardEffectEntity ring = effect(w, home, PhaseChange.RING_KIND);
        if (ring != null && !ringSeen) { ringSeen = true; ringSide = (int) Math.signum(ring.auxPoint().x); }
        switch (name) {
            case "change-cinematic" -> {
                if (t == 4) k.damage(w, w.getDamageSources().playerAttack(p), k.getMaxHealth() * .6f);
                // Struck by the Fireball: a step clear of its burning ground, still in the sweep's reach.
                if (changedAt < 0 && !wounds.isEmpty() && lock == null) { lock = Vec3d.ZERO; place.place(2.5, -6); }
                if (changedAt < 0) leftoversBefore |= k.lingering() > 0;
                if (age == 1) leftoversCleared = k.lingering() == 0 && effect(w, home, FireballSpell.GROUND_KIND) == null && effect(w, home, FireballSpell.KIND) == null;
                if (age == 5) watchingDuring = watching;
                if (age == 60) {
                    float before = k.getHealth();
                    k.damage(w, w.getDamageSources().playerAttack(p), 120);
                    copyHurt = k.getHealth() < before - .01f;
                    gateFailed = AttackEntityCallback.EVENT.invoker().interact(p, w, Hand.MAIN_HAND, k, null) == ActionResult.FAIL;
                }
                if (age == 70) {
                    int was = wounds.size();
                    boolean a = p.damage(w, w.getDamageSources().mobAttack(k), 10), b = p.damage(w, w.getDamageSources().generic(), 10);
                    playerHurt = a || b || p.getHealth() < p.getMaxHealth() || wounds.size() != was;
                }
                if (age == 100) { lock = p.getEntityPos(); place.place(6, -9); }
                if (age == 106) heldBack = lock != null && p.getEntityPos().squaredDistanceTo(lock) < .3 * .3;
                if (age == 150) clientDuring = clientCinematic && clientWatching;
                if (age == PhaseChange.CINEMATIC + 2) notWatchingAfter = !watching;
                if (age == PhaseChange.CINEMATIC + 12) clientAfter = !clientWatching && !clientCinematic;
            }
            case "change-stop" -> {
                if (age == 5) watchingDuring = watching;
                if (age == 100) { stopAt = t; k.stop(); }
                if (stopAt >= 0 && t == stopAt + 2) {
                    phaseTwoAtOnce = k.inPhaseTwo() && effect(w, home, PhaseChange.RING_KIND) == null;
                    letGo = !watching;
                    int was = wounds.size();
                    hurtAgain = p.damage(w, w.getDamageSources().generic(), 5) || wounds.size() != was;
                }
                if (stopAt >= 0 && t == stopAt + 12) letGoClient = !clientWatching && !clientCinematic;
            }
            case "change-leave" -> {
                if (age == 5) watchingDuring = watching;
                if (age == 120) { stopAt = t; p.changeGameMode(GameMode.SPECTATOR); }
                if (stopAt >= 0 && t == stopAt + 2) { phaseTwoAtOnce = k.inPhaseTwo(); letGo = !watching; }
                if (stopAt >= 0 && t == stopAt + 12) letGoClient = !clientWatching && !clientCinematic;
                if (stopAt >= 0 && t == stopAt + 14) p.changeGameMode(GameMode.SURVIVAL);
            }
            case "change-late" -> {
                if (age >= 1 && age < 80 && watching) watchingBeforeJoin = true;
                if (age == 80) { joinAt = t; p.changeGameMode(GameMode.SURVIVAL); }
                if (joinAt >= 0 && joinedAt < 0 && watching) joinedAt = t;
                if (age == 140) clientDuring = clientCinematic && clientWatching;
                if (age == PhaseChange.CINEMATIC + 2) notWatchingAfter = !watching;
                if (age == PhaseChange.CINEMATIC + 12) clientAfter = !clientWatching && !clientCinematic;
            }
            case "change-skip" -> {
                if (age == 5) watchingDuring = watching;
                if (age == 30) sneak = true;
                if (sneak && clientSkipped && sneakFrom < 0) { sneakFrom = t; sneak = false; }
                if (age == 70) { sneak = false; skipJumped = k.phaseAge(0) >= PhaseChange.CINEMATIC; notWatchingAfter = !watching; }
                if (age == 80) clientAfter = !clientWatching && !clientCinematic;
            }
            default -> {}
        }
        if (k.inPhaseTwo() && endedAt < 0) endedAt = t;
    }

    static void verify(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, String name, List<Float> hurt, Vec3d home, List<String> notes, Consumer<String> fail) {
        sneak = false;
        p.changeGameMode(GameMode.SURVIVAL);
        List<Float> after = woundsAtChange < 0 ? List.of() : hurt.subList(Math.min(hurt.size(), woundsAtChange), hurt.size());
        long medium = after.stream().filter(x -> Math.abs(x - FirstWizardRules.MEDIUM) < .01f).count();
        notes.add(String.format(Locale.ROOT, "change at t%d, phase two at t%d (%s), wounds after the change %s, ring side %d, client cinematic seen %s",
                changedAt, endedAt, k.inPhaseTwo() ? "phase two" : "not phase two", after, ringSide, everCinematic));
        if (changedAt < 0) { fail.accept("the change never began"); return; }
        if (!k.inPhaseTwo()) fail.accept("the copy should end in phase two");
        if (!rightHanded) fail.accept("from the change on the wand is in its right hand");
        switch (name) {
            case "change-cinematic" -> {
                notes.add("leftovers before " + leftoversBefore + " cleared " + leftoversCleared + ", watching " + watchingDuring + ", copy hurt " + copyHurt
                        + ", player hurt " + playerHurt + ", attack refused " + gateFailed + ", held " + heldBack + ", client during " + clientDuring
                        + ", let go " + notWatchingAfter + "/" + clientAfter);
                if (!leftoversBefore) fail.accept("the Fireball should have left burning ground before the change (the case is not testing the clearing)");
                if (!leftoversCleared) fail.accept("nothing of phase one should run on into the change");
                if (!watchingDuring || !clientDuring) fail.accept("the player should watch the cinematic: server " + watchingDuring + ", client " + clientDuring);
                if (copyHurt) fail.accept("the copy should not be hurt during the cinematic");
                if (!gateFailed) fail.accept("a watcher's attack should be refused");
                if (playerHurt) fail.accept("a watcher should not be hurt during the cinematic");
                if (!heldBack) fail.accept("a watcher should be held where they stood");
                if (!notWatchingAfter || !clientAfter) fail.accept("control should return at the cinematic's end: server " + notWatchingAfter + ", client " + clientAfter);
                if (medium != 1 || after.size() != 1) fail.accept("the full sweep should strike the standing player once after control returns; took " + after);
                if (ringSide != 1) fail.accept("the full sweep should start on the copy's left (side +1); was " + ringSide);
            }
            case "change-stop" -> {
                notes.add("watching " + watchingDuring + ", phase two at once " + phaseTwoAtOnce + ", let go " + letGo + "/" + letGoClient + ", hurt again " + hurtAgain);
                if (!watchingDuring) fail.accept("the player should watch the cinematic");
                if (!phaseTwoAtOnce) fail.accept("stopped mid-cinematic, the copy should be in phase two at once, its ring gone");
                if (!letGo || !letGoClient) fail.accept("stopped, the player should be let go: server " + letGo + ", client " + letGoClient);
                if (!hurtAgain) fail.accept("let go, the player can be hurt again");
            }
            case "change-leave" -> {
                notes.add("watching " + watchingDuring + ", phase two at once " + phaseTwoAtOnce + ", let go " + letGo + "/" + letGoClient);
                if (!watchingDuring) fail.accept("the player should watch the cinematic");
                if (!phaseTwoAtOnce) fail.accept("with nobody left in the fight, the copy should be in phase two at once");
                if (!letGo || !letGoClient) fail.accept("a player who leaves the fight should be let go: server " + letGo + ", client " + letGoClient);
            }
            case "change-late" -> {
                notes.add("watching before joining " + watchingBeforeJoin + ", joined t" + joinAt + " watching from t" + joinedAt + ", client during " + clientDuring
                        + ", let go " + notWatchingAfter + "/" + clientAfter);
                if (watchingBeforeJoin) fail.accept("a player not in the fight should not watch");
                if (joinedAt < 0 || joinedAt > joinAt + 2) fail.accept("a player who joins mid-cinematic should be taken in at once; joined t" + joinAt + ", watching from t" + joinedAt);
                if (!clientDuring) fail.accept("their client should show the cinematic from where it has got to");
                if (!notWatchingAfter || !clientAfter) fail.accept("control should return at the end: server " + notWatchingAfter + ", client " + clientAfter);
                if (medium != 1 || after.size() != 1) fail.accept("the full sweep should strike them once; took " + after);
            }
            case "change-skip" -> {
                notes.add("watching " + watchingDuring + ", skipped by the client at t" + sneakFrom + ", the clock jumped " + skipJumped + ", let go " + notWatchingAfter + "/" + clientAfter);
                if (!watchingDuring) fail.accept("the player should watch the cinematic");
                if (sneakFrom < 0 || !skipJumped) fail.accept("holding Sneak should skip the cinematic: skipped t" + sneakFrom + ", the clock jumped " + skipJumped);
                if (!notWatchingAfter || !clientAfter) fail.accept("skipped, the player should be let go: server " + notWatchingAfter + ", client " + clientAfter);
                if (medium != 1 || after.size() != 1) fail.accept("after a skip the full sweep still strikes once; took " + after);
            }
            case "change-look" -> {
                if (watchingDuring) fail.accept("nobody in the fight: nobody should watch");
            }
            default -> {}
        }
    }

    private static FirstWizardEffectEntity effect(ServerWorld w, Vec3d near, String kind) {
        var found = w.getEntitiesByClass(FirstWizardEffectEntity.class, new Box(near, near).expand(70), e -> e.kind().equals(kind) && e.isAlive());
        return found.isEmpty() ? null : found.getFirst();
    }
}
