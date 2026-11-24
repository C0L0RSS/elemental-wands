package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.spell.FlameSpell;
import com.anton.elementalwands.entity.firstwizard.spell.HollowPurpleSpell;
import com.anton.elementalwands.entity.firstwizard.spell.LeapSpell;
import com.anton.elementalwands.entity.firstwizard.spell.PhaseChange;
import com.anton.elementalwands.entity.firstwizard.spell.SlamSpell;
import com.anton.elementalwands.entity.firstwizard.spell.SweepSpell;
import com.anton.elementalwands.entity.firstwizard.spell.Tendrils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The phase-two cases of {@link FirstWizardFightSmoke}: the change and each ink move, its hit and its dodge.
 *
 * <ul>
 *   <li>{@code phase-change}: a Fireball is in hand when a blow takes it below half: it is held at half, the Fireball
 *       finishes first, then the change plays; a blow during it does nothing; the full sweep strikes the standing
 *       player once (medium); phase two follows, with the raised box.</li>
 *   <li>{@code phase-jump}: the same change, the player jumping the full sweep with a real client jump: untouched.</li>
 *   <li>{@code phase-body}: phase two standing, then fighting at range: it moves in strides and its body stays on the floor.</li>
 *   <li>{@code phase-death}: it breathes fire and leaves its cloud, then dies: everything it left is gone.</li>
 *   <li>{@code slam-hit} and {@code slam-dodge}: on the line, one heavy hit; a step aside after the lock, nothing.</li>
 *   <li>{@code sweep-stand} and {@code sweep-jump}: standing, one medium hit; a timed jump, nothing.</li>
 *   <li>{@code leap-hit} and {@code leap-dodge}: in the landing circle, one heavy hit; out of it after the mark, nothing.</li>
 *   <li>{@code flame-hit}: in the cone: burned every half second, each burn the flame's, no more than the breath allows.</li>
 *   <li>{@code flame-behind}: the combination: a Leap, the player dodges round behind it, then the Flame (cast as the
 *       follow-up is, as the Leap frees it): untouched.</li>
 *   <li>{@code flame-bottle}: the cloud the flame leaves: standing in it hurts nothing, and a glass bottle used there
 *       gives dragon's breath.</li>
 *   <li>{@code purple-gate}: fighting at range in phase two above a quarter health, it never casts Hollow Purple;
 *       below, it does.</li>
 *   <li>{@code purple-hit} and {@code purple-dodge}: in the lane, one Hollow Purple hit; out of it after the lock, nothing.</li>
 *   <li>{@code phase-fireball}: a spell from the human hand in phase two (the tentacles flare round it and recoil as it
 *       is thrown): one heavy hit, then the player is moved clear of its burning ground.</li>
 * </ul>
 *
 * <p>{@code -PwizardView=<side>} ({@code wizard.view}: left, behind, right, front, front-left, behind-left; her sides,
 * she faces -z) films every phase-two case but {@code purple-gate} and {@code phase-body} from that side instead, so a
 * move's motion can be reviewed from all round.</p>
 */
final class FirstWizardPhaseTwoCases {
    private FirstWizardPhaseTwoCases() {}

    static final List<String> NAMES = List.of("phase-look", "phase-drift", "phase-change", "phase-jump", "phase-body", "phase-death", "slam-hit", "slam-dodge",
            "sweep-stand", "sweep-jump", "leap-hit", "leap-dodge", "flame-hit", "flame-behind", "flame-bottle", "purple-gate",
            "purple-hit", "purple-dodge", "purple-interrupt", "phase-fireball");

    static boolean mine(String name) { return NAMES.contains(name); }
    /** The cases recorded for the developer to watch: lit by day so the ink reads against the floor and sky. */
    static boolean lit(String name) {
        return List.of("phase-look", "phase-drift", "phase-change", "slam-hit", "sweep-stand", "leap-hit", "flame-hit", "purple-hit", "phase-fireball").contains(name);
    }
    /** The two that play the change start from a fresh copy in phase one; the rest are put in phase two. */
    static boolean fromPhaseOne(String name) { return name.equals("phase-change") || name.equals("phase-jump"); }

    interface Placer { void place(double x, double z); }

    /** Ticks before the tendril reaches the player that a jump is asked for (the client takes it a tick later). */
    private static final int JUMP_LEAD = 4;

    // Client side.
    private static volatile boolean jumpWanted, reporting;
    private static volatile int jumps;
    // Server side, for the case in hand.
    private static int changedAt = -1, heldChecked = -1, hurtDuring = -1, cloudAt = -1, purpleAt = -1, lastCast = -1;
    private static boolean held, heldPhaseOne, heldInHand, stepped, bottled, cloudGone, effectsGone, offFloor, strode, purpleEarly, purpleSeen, placedBehind;
    private static int woundsAtChange = -1, woundsAtCloud = -1, jumpAsked = -1;
    private static double highest, travelled, boxHeight;
    private static Vec3d lastAt, landing;
    private static final List<String> moves = new ArrayList<>();

    private static void reset() {
        jumpWanted = false; jumps = 0;
        changedAt = heldChecked = hurtDuring = cloudAt = purpleAt = lastCast = -1;
        held = heldPhaseOne = heldInHand = stepped = bottled = cloudGone = effectsGone = offFloor = strode = purpleEarly = purpleSeen = placedBehind = false;
        woundsAtChange = woundsAtCloud = jumpAsked = -1;
        highest = travelled = boxHeight = 0;
        lastAt = landing = null;
        moves.clear();
    }

    // ── Client thread ────────────────────────────────────────────────────────

    static void client(MinecraftClient c) {
        if (c.player == null) return;
        if (jumpWanted && c.player.isOnGround()) { jumpWanted = false; jumps++; c.player.jump(); }
        // The camera is not the player, so vanilla does not report their movement: report it, so the server sees the jump.
        if (reporting && c.getCameraEntity() != c.player && c.player.networkHandler != null)
            c.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(
                    c.player.getX(), c.player.getY(), c.player.getZ(), c.player.isOnGround(), c.player.horizontalCollision));
    }

    /** Where the camera stands for a case, and what it looks at; null for the fixture's own framing. */
    static Vec3d[] camera(String name, Vec3d home) {
        Vec3d eye, at;
        String view = System.getProperty("wizard.view", "");
        if (!view.isEmpty() && !view.equals("showcase") && !name.equals("purple-gate") && !name.equals("phase-body")) return view(name, view, home);
        switch (name) {
            // Close, from in front and to the wound's side (a right-handed copy facing -z: its left is -x... seen from -x, -z).
            case "phase-change", "phase-jump" -> { eye = home.add(-2.5, 2.4, -3.3); at = home.add(-.1, 1.6, -.3); }
            case "phase-body" -> { eye = home.add(-7, 3.2, -8); at = home.add(0, 1.2, -4); }
            // The tentacles reach some five blocks out from her left side, so these stand clear of them: she is large in
            // frame, the three tentacles spread beside her, and the whole arc, line or flight still shows.
            // High behind her right shoulder, the arc before her.
            case "sweep-stand", "sweep-jump" -> { eye = home.add(3.5, 7.5, 5); at = home.add(0, .8, -2.5); }
            // Raised, before her left side: the striker rears over her and comes down along the line.
            case "slam-hit", "slam-dodge" -> { eye = home.add(-6, 5.5, -7.5); at = home.add(-.5, 2, -4); }
            // Off her right side, level with the middle of the leap: the windup, the flight and the landing.
            case "leap-hit", "leap-dodge" -> { eye = home.add(8.5, 5, -6); at = home.add(0, 2.3, -6); }
            // Off her left, a little behind her: both anchors splayed fore and aft, the tail arched over her and the breath.
            case "flame-hit" -> { eye = home.add(-8.5, 4.2, 1.8); at = home.add(-1.2, 2.4, -.8); }
            // Off her left, a little behind her: both anchors, the feeding tentacle and the whole orb over her.
            case "purple-hit" -> { eye = home.add(-7.5, 4.8, 2); at = home.add(-1, 3.6, -.6); }
            case "purple-dodge" -> { eye = home.add(-12, 7, 4); at = home.add(0, 2, -12); }
            // Close before her left: the spell forming over her head with the tentacles flared round it.
            case "phase-fireball" -> { eye = home.add(-5.5, 3, -6.5); at = home.add(-.3, 2.6, -.5); }
            case "purple-gate" -> { return null; }
            default -> { eye = home.add(-9, 5, -5); at = home.add(0, 1, -6); }
        }
        return new Vec3d[]{eye, at.subtract(eye)};
    }

    /** A case filmed from one side of her (she faces -z, her left is -x), far enough back to hold the move. */
    private static Vec3d[] view(String name, String side, Vec3d home) {
        Vec3d centre = name.startsWith("leap") ? home.add(0, 2.6, -6) : name.startsWith("purple") ? home.add(0, 3.5, -1) : home.add(0, 2.4, -.3);
        double far = name.startsWith("leap") ? 13 : name.startsWith("purple") ? 11 : 8.5;
        Vec3d dir = switch (side) {
            case "left" -> new Vec3d(-1, 0, 0);
            case "behind" -> new Vec3d(0, 0, 1);
            case "right" -> new Vec3d(1, 0, 0);
            case "front" -> new Vec3d(0, 0, -1);
            case "front-left" -> new Vec3d(-1, 0, -1).normalize();
            case "behind-left" -> new Vec3d(-1, 0, 1).normalize();
            default -> throw new IllegalArgumentException("wizard.view: no side " + side);
        };
        Vec3d eye = centre.add(dir.multiply(far)).add(0, far * .3, 0);
        return new Vec3d[]{eye, centre.subtract(eye)};
    }

    // ── Server thread ────────────────────────────────────────────────────────

    /** As the case starts (its first tick): what the fixture's testMove does not. */
    static void start(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, String name) {
        reset();
        reporting = name.equals("phase-jump") || name.equals("sweep-jump");
        var w = server.getOverworld();
        switch (name) {
            case "phase-change" -> {
                // A Fireball in hand, then the blow that takes it past half.
                k.testMove(p, FirstWizardRules.Move.FIREBALL);
            }
            case "phase-jump" -> k.damage(w, w.getDamageSources().playerAttack(p), k.getMaxHealth() * .6f);
            case "purple-gate" -> { k.setHealth(k.getMaxHealth() * .6f); k.fight(); }
            // The Leap, then the Flame as its scripted follow-up, cast as the fight casts it.
            case "flame-behind" -> k.testCombination(p, FirstWizardRules.Move.LEAP);
            default -> {}
        }
    }

    static void during(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, List<Float> wounds, String name, int t, Vec3d home, Placer place) {
        var w = server.getOverworld();
        double floor = home.y;
        Vec3d at = k.getEntityPos();
        double age = k.castAge(0);
        String move = k.moveName();
        if (!move.equals("free") && (moves.isEmpty() || !moves.getLast().equals(move))) moves.add(move);
        // Bound vertical travel during airborne moves and require ground contact again as soon as the cast ends.
        double ceiling = switch (move) {
            case "leap" -> LeapSpell.HEIGHT;
            case "fireball" -> 1.35;
            case "hollow_purple" -> 1.85;
            default -> 0;
        };
        if (at.y < floor - .05 || at.y > floor + ceiling + .05) offFloor = true;
        if (k.arena() != null && k.arena().fromCentre(at) > k.arena().radius()) offFloor = true;
        highest = Math.max(highest, at.y - floor);
        if (changedAt < 0 && k.phase() == FirstWizardEntity.CHANGING) { changedAt = t; woundsAtChange = wounds.size(); }
        switch (name) {
            case "phase-change", "phase-jump" -> {
                if (name.equals("phase-change")) {
                    if (t == 5) k.damage(w, w.getDamageSources().playerAttack(p), k.getMaxHealth() * .6f);
                    if (t == 8) { held = Math.abs(k.getHealth() - k.getMaxHealth() / 2) < .01f; heldPhaseOne = k.phase() == FirstWizardEntity.PHASE_ONE; heldInHand = move.equals("fireball"); }
                    // Struck by the Fireball: stepped clear of its burning ground, still within the sweep's reach.
                    if (!stepped && !wounds.isEmpty()) { stepped = true; place.place(2, 5.5); }
                }
                if (changedAt >= 0 && t == changedAt + 40) {
                    float before = k.getHealth();
                    k.damage(w, w.getDamageSources().playerAttack(p), 120);
                    hurtDuring = k.getHealth() < before - .01f ? 1 : 0;
                }
                if (name.equals("phase-jump") && changedAt >= 0) jumpFor(p, k, w, floor, k.phaseAge(0), true);
                if (k.inPhaseTwo()) boxHeight = k.getBoundingBox().getLengthY();
            }
            case "sweep-jump" -> jumpFor(p, k, w, floor, age, false);
            case "slam-dodge" -> { if (!stepped && age >= SlamSpell.TIMING.aimLock() + 1) { stepped = true; place.place(2.6, -6); } }
            case "leap-dodge" -> { if (!stepped && age >= LeapSpell.TIMING.aimLock() + 1) { stepped = true; place.place(5.5, -12); } }
            case "flame-behind" -> {
                // Round behind it once its landing is marked (it faces the way it flew), then the Flame as the follow-up comes.
                FirstWizardEffectEntity mark = effect(w, home, LeapSpell.KIND);
                if (mark != null && mark.stage() >= 1 && landing == null) landing = mark.getEntityPos();
                if (landing != null && !placedBehind && age >= LeapSpell.TIMING.aimLock() + 1) {
                    placedBehind = true;
                    Vec3d back = home.subtract(landing).multiply(1, 0, 1).normalize().multiply(LeapSpell.RADIUS + 1.2);
                    Vec3d spot = landing.add(back);
                    place.place(spot.x - home.x, spot.z - home.z);
                }
                if (move.equals("flame") && lastCast < 0) lastCast = t;
            }
            case "flame-bottle" -> {
                List<AreaEffectCloudEntity> clouds = w.getEntitiesByClass(AreaEffectCloudEntity.class, new Box(home, home).expand(40), e -> e.isAlive());
                if (!clouds.isEmpty() && cloudAt < 0) {
                    cloudAt = t;
                    woundsAtCloud = wounds.size();
                    Vec3d c = clouds.getFirst().getEntityPos();
                    place.place(c.x - home.x, c.z - home.z);
                }
                if (cloudAt >= 0 && t == cloudAt + 20) {
                    // A glass bottle used in the cloud, as the server handles a player's use.
                    p.getInventory().clear();
                    p.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE, 2));
                    p.interactionManager.interactItem(p, w, p.getStackInHand(Hand.MAIN_HAND), Hand.MAIN_HAND);
                    bottled = p.getInventory().count(Items.DRAGON_BREATH) > 0;
                }
            }
            case "phase-body" -> {
                if (t == 30) k.fight();
                if (t > 30) {
                    // Held at range, circling: it comes after them in strides.
                    if (lastAt != null) travelled += at.subtract(lastAt).horizontalLength();
                    if (t % 30 == 0) {
                        Vec3d off = p.getEntityPos().subtract(at).multiply(1, 0, 1);
                        double a = Math.atan2(off.z, off.x) + .5;
                        place.place(at.x - home.x + Math.cos(a) * 13, at.z - home.z + Math.sin(a) * 13);
                    }
                }
                lastAt = at;
            }
            case "phase-death" -> {
                List<AreaEffectCloudEntity> clouds = w.getEntitiesByClass(AreaEffectCloudEntity.class, new Box(home, home).expand(40), e -> e.isAlive());
                if (!clouds.isEmpty() && cloudAt < 0) cloudAt = t;
                if (cloudAt >= 0 && t == cloudAt + 5) k.damage(w, w.getDamageSources().playerAttack(p), 100000);
                if (cloudAt >= 0 && t == cloudAt + 12) {
                    cloudGone = w.getEntitiesByClass(AreaEffectCloudEntity.class, new Box(home, home).expand(60), e -> e.isAlive()).isEmpty();
                    effectsGone = w.getEntitiesByClass(FirstWizardEffectEntity.class, new Box(home, home).expand(80), e -> e.isAlive()).isEmpty();
                }
            }
            case "purple-gate" -> {
                if (t == 320) k.setHealth(k.getMaxHealth() * .2f);
                if (move.equals("hollow_purple")) { if (t < 320) purpleEarly = true; else if (!purpleSeen) { purpleSeen = true; purpleAt = t; } }
                if (purpleSeen && t == purpleAt + 130) k.calm();
                // Kept at range, so the finisher is in its range.
                if (t % 40 == 0 && !move.equals("hollow_purple")) {
                    Vec3d off = p.getEntityPos().subtract(at).multiply(1, 0, 1);
                    Vec3d dir = off.lengthSquared() < 1e-4 ? new Vec3d(0, 0, -1) : off.normalize();
                    Vec3d spot = k.arena() == null ? at.add(dir.multiply(20)) : k.arena().onFloor(at.add(dir.multiply(20)), 3);
                    place.place(spot.x - home.x, spot.z - home.z);
                }
            }
            case "purple-dodge" -> { if (!stepped && age >= HollowPurpleSpell.TIMING.aimLock() + 1) { stepped = true; place.place(9, -20); } }
            // Struck, then well clear of the burning ground, so the hit stands alone.
            case "phase-fireball" -> { if (!stepped && !wounds.isEmpty()) { stepped = true; place.place(9, -12); } }
            default -> {}
        }
    }

    /** Asks for a jump when the sweeping tendril is JUMP_LEAD ticks from the player, as a player would read it. */
    private static void jumpFor(ServerPlayerEntity p, FirstWizardEntity k, net.minecraft.server.world.ServerWorld w, double floor, double age, boolean change) {
        if (jumpAsked >= 0) return;
        FirstWizardEffectEntity fx = effect(w, k.getEntityPos(), change ? PhaseChange.RING_KIND : SweepSpell.KIND);
        if (fx == null || (change ? false : fx.stage() < 1)) return;
        double a = Tendrils.angle(fx.dir()), s = fx.auxPoint().x;
        Box box = p.getBoundingBox();
        Box standing = box.offset(0, floor - box.minY, 0);
        int now = (int) Math.floor(age);
        for (int n = now + 1; n < now + 60; n++) {
            double a0 = change ? PhaseChange.angleAt(n - 1, a, s) : SweepSpell.angleAt(n - 1, a, s);
            double a1 = change ? PhaseChange.angleAt(n, a, s) : SweepSpell.angleAt(n, a, s);
            if (Tendrils.sweeps(standing, floor, k.getEntityPos(), a0, a1, Tendrils.REACH)) {
                if (n - now <= JUMP_LEAD) { jumpWanted = true; jumpAsked = n - now; }
                return;
            }
        }
    }

    static void verify(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, String name, List<Float> hurt, Vec3d home, List<String> notes, Consumer<String> fail) {
        var w = server.getOverworld();
        long heavy = count(hurt, FirstWizardRules.HEAVY), medium = count(hurt, FirstWizardRules.MEDIUM);
        notes.add("moves " + moves + ", wounds " + hurt + String.format(Locale.ROOT, ", highest %.2f", highest));
        if (offFloor) fail.accept("its body left the arena or exceeded the height allowed for the current move");
        switch (name) {
            case "phase-change", "phase-jump" -> {
                notes.add(String.format(Locale.ROOT, "change began at t%d, hurt during it %d, box %.2f tall, jumps %d (asked %d ahead)", changedAt, hurtDuring, boxHeight, jumps, jumpAsked));
                if (changedAt < 0) fail.accept("the change never began");
                if (hurtDuring != 0) fail.accept("it should not be hurt during the change");
                if (!k.inPhaseTwo()) fail.accept("phase two should follow the change");
                if (Math.abs(boxHeight - (1.8 + FirstWizardRules.LIFT)) > .05) fail.accept("phase two's box should cover the raised body: " + boxHeight);
                List<Float> after = woundsAtChange < 0 ? List.of() : hurt.subList(Math.min(hurt.size(), woundsAtChange), hurt.size());
                if (name.equals("phase-change")) {
                    if (!held || !heldPhaseOne || !heldInHand) fail.accept("a blow past half should hold it at half, in phase one, while its Fireball finishes: held " + held + ", phase one " + heldPhaseOne + ", fireball " + heldInHand);
                    if (changedAt < com.anton.elementalwands.entity.firstwizard.spell.FireballSpell.TIMING.free() - 1) fail.accept("the change should wait for the Fireball to finish; began at " + changedAt);
                    if (count(after, FirstWizardRules.MEDIUM) != 1 || after.size() != 1) fail.accept("the full sweep should strike a standing player once; after the change began " + after);
                } else {
                    if (jumps < 1) fail.accept("the player should have jumped the sweep");
                    if (!after.isEmpty()) fail.accept("a timed jump should clear the full sweep; took " + after);
                }
            }
            case "phase-body" -> {
                notes.add(String.format(Locale.ROOT, "travelled %.1f", travelled));
                if (travelled < 4) fail.accept("fighting, phase two should move after its target; it moved " + travelled);
            }
            case "phase-death" -> {
                notes.add("cloud at t" + cloudAt + ", cloud gone " + cloudGone + ", effects gone " + effectsGone);
                if (cloudAt < 0) fail.accept("the flame should leave its cloud");
                if (!cloudGone || !effectsGone) fail.accept("everything it left should be cleared when it dies: cloud gone " + cloudGone + ", effects gone " + effectsGone);
                if (k.isAlive()) fail.accept("it should have died");
            }
            case "slam-hit" -> { if (heavy != 1 || hurt.size() != 1) fail.accept("on the Slam's line: one heavy hit; took " + hurt); }
            case "slam-dodge" -> { if (!stepped || !hurt.isEmpty()) fail.accept("a step aside after the lock should dodge the Slam; took " + hurt); }
            case "sweep-stand" -> { if (medium != 1 || hurt.size() != 1) fail.accept("standing in the Sweep: one medium hit; took " + hurt); }
            case "sweep-jump" -> {
                notes.add("jumps " + jumps + " asked " + jumpAsked + " ahead");
                if (jumps < 1) fail.accept("the player should have jumped the Sweep");
                if (!hurt.isEmpty()) fail.accept("a timed jump should clear the Sweep; took " + hurt);
            }
            case "leap-hit" -> { if (heavy != 1 || hurt.size() != 1) fail.accept("in the landing circle: one heavy hit; took " + hurt); }
            case "leap-dodge" -> { if (!stepped || !hurt.isEmpty()) fail.accept("out of the circle after the mark: nothing; took " + hurt); }
            case "flame-hit" -> {
                long burns = count(hurt, FirstWizardRules.FLAME);
                if (burns < 3) fail.accept("standing in the cone should burn about twice a second; burned " + burns + ": " + hurt);
                if (burns != hurt.size()) fail.accept("only the flame should hurt: " + hurt);
                if (burns > FlameSpell.BREATH / FirstWizardRules.FLAME_EVERY + 1) fail.accept("no more burns than the breath allows: " + burns);
            }
            case "flame-behind" -> {
                notes.add("landing " + (landing == null ? "none" : String.format(Locale.ROOT, "(%.1f, %.1f)", landing.x, landing.z)) + ", flame cast at t" + lastCast);
                if (!placedBehind) fail.accept("the player never got behind it");
                if (lastCast < 0 || !moves.contains("flame")) fail.accept("the Flame should follow the Leap");
                if (!hurt.isEmpty()) fail.accept("a player who dodged the landing and got behind it is untouched; took " + hurt);
            }
            case "flame-bottle" -> {
                List<Float> inCloud = woundsAtCloud < 0 ? List.of() : hurt.subList(Math.min(hurt.size(), woundsAtCloud), hurt.size());
                notes.add("cloud at t" + cloudAt + ", bottled " + bottled + ", wounds in the cloud " + inCloud);
                if (cloudAt < 0) fail.accept("the flame should leave a cloud");
                if (!bottled) fail.accept("a glass bottle used in the cloud should give dragon's breath");
                if (!inCloud.isEmpty()) fail.accept("the cloud itself should not hurt: " + inCloud);
            }
            case "purple-gate" -> {
                notes.add("purple early " + purpleEarly + ", below a quarter at t" + purpleAt);
                if (purpleEarly) fail.accept("Hollow Purple came above a quarter health");
                if (!purpleSeen) fail.accept("below a quarter health Hollow Purple should come");
            }
            case "purple-hit" -> { if (count(hurt, FirstWizardRules.PURPLE) != 1 || hurt.size() != 1) fail.accept("in the lane: one Hollow Purple hit; took " + hurt); }
            case "purple-dodge" -> { if (!stepped || !hurt.isEmpty()) fail.accept("out of the lane after the lock: nothing; took " + hurt); }
            case "phase-fireball" -> {
                if (!moves.contains("fireball")) fail.accept("in phase two it should still cast the Fireball from its hand; moves " + moves);
                if (heavy != 1 || hurt.size() != 1) fail.accept("a Fireball in phase two: one heavy hit; took " + hurt);
            }
            default -> {}
        }
        p.getInventory().clear();
    }

    private static long count(List<Float> hurt, float amount) { return hurt.stream().filter(w -> Math.abs(w - amount) < .01f).count(); }

    private static FirstWizardEffectEntity effect(net.minecraft.server.world.ServerWorld w, Vec3d near, String kind) {
        var found = w.getEntitiesByClass(FirstWizardEffectEntity.class, new Box(near, near).expand(70), e -> e.kind().equals(kind) && e.isAlive());
        return found.isEmpty() ? null : found.getFirst();
    }
}
