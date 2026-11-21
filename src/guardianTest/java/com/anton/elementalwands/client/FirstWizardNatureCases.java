package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.InkBloomEntity;
import com.anton.elementalwands.entity.firstwizard.spell.BlinkRiftSpell;
import com.anton.elementalwands.entity.firstwizard.spell.SentryBloomSpell;
import com.anton.elementalwands.entity.firstwizard.spell.ThornbiteSpell;
import com.anton.elementalwands.util.FlowerTurretManager;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The Thornbite and Sentry Bloom cases of {@link FirstWizardFightSmoke}, staged on the server like the others. The copy
 * stands at the mark facing -Z with 500 of its 1000 health, so a heal shows as a rise; the player stands in front of it.
 *
 * <ul>
 *   <li>{@code thorn-bite}: the player 9 blocks away takes one heavy hit and the copy is healed by
 *       {@link ThornbiteSpell#HEAL} of its maximum health.</li>
 *   <li>{@code thorn-sidestep}: moved 3.5 aside after the aim lock: untouched, and nothing healed.</li>
 *   <li>{@code thorn-blink}: the design's combination: Blink Rift lands behind the player, 5 to 7 blocks past them, and
 *       the Thornbite cast from there bites and heals.</li>
 *   <li>{@code thorn-blink-dodge}: the same, and the player steps aside after the bite's lock: it misses.</li>
 *   <li>{@code bloom-deliver}: the flower lands on the floor well away from the player and opens; every seed that
 *       strikes is a light blow and fills its store, up to the cap; a second flower cannot be cast while it stands;
 *       12 s after it opens it heals the copy by what it stored, wilts and is gone.</li>
 *   <li>{@code bloom-dodge}: the player steps 3 further aside from every seed: untouched, nothing stored. Then the fight is stopped
 *       and the flower is gone at once.</li>
 *   <li>{@code bloom-break}: once it holds something, the player breaks it (a hostile mob to their flowers and weapons):
 *       the store is wasted, the copy is not healed, and another flower cannot be cast straight after.</li>
 * </ul>
 */
final class FirstWizardNatureCases {
    private FirstWizardNatureCases() {}

    static final List<String> NAMES = List.of("thorn-bite", "thorn-sidestep", "thorn-blink", "thorn-blink-dodge", "bloom-deliver", "bloom-dodge", "bloom-break");
    /** The copy's health at the start of each case: half, so a heal shows. */
    static final float START = 500;

    // The server's measurements for the case in hand (server thread only).
    private static float healthBefore, heldMost, heldBeforeBreak = -1, heldAtDeliver = -1, healthAfterDeliver = -1;
    private static boolean stepped, secondRefused, secondTried, hostile, broke, flowerGone, stoppedClean, refusedAfter, gotDamage;
    private static Vec3d blinkFrom, blinkTo, playerAt, flowerAt;
    private static int biteCastAt = -1, seeds, grownAt = -1, side = 1;
    private static final Set<Integer> seen = new HashSet<>();

    static boolean mine(String name) { return NAMES.contains(name); }

    /** On the server, every tick of a case from t = 1. */
    static void during(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, ArmorStandEntity camera, List<Float> wounds,
                       String name, int t, Vec3d home, BiConsumer<Double, Double> place) {
        ServerWorld w = server.getOverworld();
        if (t == 1) {
            healthBefore = START; heldMost = 0; heldBeforeBreak = heldAtDeliver = healthAfterDeliver = -1;
            stepped = secondRefused = secondTried = hostile = broke = flowerGone = stoppedClean = refusedAfter = gotDamage = false;
            blinkFrom = k.getEntityPos(); blinkTo = flowerAt = null; playerAt = p.getEntityPos();
            biteCastAt = grownAt = -1; seeds = 0; side = 1; seen.clear();
            k.setHealth(START);
            if (name.startsWith("thorn-blink")) aim(camera, home.add(13, 5, -9), home.add(0, 1.5, -11));
            else if (name.startsWith("thorn")) aim(camera, home.add(8, 3.6, -3.5), home.add(0, 2, -5));
            else aim(camera, home.add(16, 9, 2), home.add(0, 1.5, -8));
        }
        double age = k.castAge(0);
        switch (name) {
            case "thorn-bite" -> {}
            case "thorn-sidestep" -> {
                if (!stepped && age >= ThornbiteSpell.TIMING.aimLock() + 1 && age < ThornbiteSpell.TIMING.release()) { stepped = true; place.accept(3.5, -9.0); }
            }
            case "thorn-blink", "thorn-blink-dodge" -> {
                // Blink Rift first (cast at t = 0); once it is free, the bite it sets up.
                if (biteCastAt < 0 && t > BlinkRiftSpell.TIMING.free() + 1 && k.moveName().equals("free")) {
                    blinkTo = k.getEntityPos();
                    biteCastAt = t;
                    if (!k.testMove(p, FirstWizardRules.Move.THORNBITE)) biteCastAt = -2;
                }
                if (name.equals("thorn-blink-dodge") && !stepped && k.moveName().equals("thornbite")
                        && age >= ThornbiteSpell.TIMING.aimLock() + 1 && age < ThornbiteSpell.TIMING.release()) {
                    stepped = true;
                    Vec3d at = p.getEntityPos(), line = at.subtract(k.getEntityPos()).multiply(1, 0, 1).normalize();
                    Vec3d to = at.add(-line.z * 3.5, 0, line.x * 3.5).subtract(home);
                    place.accept(to.x, to.z);
                }
            }
            case "bloom-deliver", "bloom-dodge", "bloom-break" -> bloom(w, p, k, camera, name, t, home, place);
            default -> {}
        }
    }

    private static void bloom(ServerWorld w, ServerPlayerEntity p, FirstWizardEntity k, ArmorStandEntity camera, String name, int t, Vec3d home,
                              BiConsumer<Double, Double> place) {
        InkBloomEntity flower = flower(w, home);
        float held = SentryBloomSpell.held(k.getUuid());
        heldMost = Math.max(heldMost, held);
        // Framed to hold the copy, the player and where the flower will grow, once its spot is fixed.
        FirstWizardEffectEntity ground = effect(w, home, SentryBloomSpell.GROUND_KIND);
        if (ground != null && flowerAt == null) {
            flowerAt = ground.getEntityPos();
            Vec3d middle = home.add(flowerAt).add(p.getEntityPos()).multiply(1 / 3.0);
            Vec3d across = flowerAt.subtract(p.getEntityPos()).multiply(1, 0, 1);
            Vec3d side = new Vec3d(-across.z, 0, across.x).normalize();
            if (side.dotProduct(middle.subtract(home)) > 0) side = side.multiply(-1);
            aim(camera, middle.add(side.multiply(12)).add(0, 6, 0), middle.add(0, 2.4, 0));
        }
        if (flower != null && grownAt < 0 && flower.grown(0) >= 1) grownAt = t;
        if (flower != null) hostile |= FlowerTurretManager.hostile(flower, p);
        switch (name) {
            case "bloom-deliver" -> {
                if (t == 100) { secondTried = true; secondRefused = !k.testMove(p, FirstWizardRules.Move.SENTRY_BLOOM); }
                if (flower != null && flower.bloomStage() == InkBloomEntity.DELIVERING && heldAtDeliver < 0) heldAtDeliver = held;
                if (flower != null && flower.bloomStage() == InkBloomEntity.WILTING && healthAfterDeliver < 0) healthAfterDeliver = k.getHealth();
                if (healthAfterDeliver >= 0 && flower == null) flowerGone = true;
            }
            case "bloom-dodge" -> {
                // Every new seed: a step of 3 further aside. Never back: a seed takes about as long to arrive as the
                // next takes to come, so stepping back would meet the one aimed there.
                for (FirstWizardEffectEntity seed : w.getEntitiesByClass(FirstWizardEffectEntity.class, new Box(home, home).expand(70),
                        e -> e.kind().equals(SentryBloomSpell.SEED_KIND) && e.isAlive() && e.stage() == 0)) {
                    if (!seen.add(seed.getId())) continue;
                    seeds++;
                    place.accept(3.0 * seeds * side, -14.0);
                }
                if (t == 150) {
                    k.stop();
                    stoppedClean = flower(w, home) == null && SentryBloomSpell.held(k.getUuid()) < 0 && k.lingering() == 0;
                }
            }
            case "bloom-break" -> {
                // Once it holds something, the player cuts it down: three blows, as a weapon or spell would land.
                if (flower != null && held > 0 && (t - grownAt) % 11 == 0) {
                    if (heldBeforeBreak < 0) heldBeforeBreak = held;
                    float before = flower.getHealth();
                    flower.damage(w, w.getDamageSources().playerAttack(p), 20);
                    gotDamage |= flower.getHealth() < before || flower.isDead();
                }
                if (heldBeforeBreak >= 0 && flower == null) broke = true;
                if (t == 190) refusedAfter = !k.testMove(p, FirstWizardRules.Move.SENTRY_BLOOM);
            }
            default -> {}
        }
    }

    static void verify(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, String name, List<Float> hurt, Vec3d home,
                       List<String> notes, Consumer<String> fail) {
        ServerWorld w = server.getOverworld();
        float healed = k.getHealth() - healthBefore, heal = ThornbiteSpell.HEAL * k.getMaxHealth();
        long heavy = hurt.stream().filter(x -> Math.abs(x - FirstWizardRules.HEAVY) < .01f).count();
        switch (name) {
            case "thorn-bite" -> {
                notes.add(String.format(Locale.ROOT, "wounds %s; healed %.1f", hurt, healed));
                if (heavy != 1 || hurt.size() != 1) fail.accept("a player standing on the line should take one heavy hit (" + FirstWizardRules.HEAVY + "); took " + hurt);
                if (Math.abs(healed - heal) > .01f) fail.accept("a bite should heal the copy by " + heal + "; healed " + healed);
            }
            case "thorn-sidestep" -> {
                notes.add(String.format(Locale.ROOT, "stepped %s; wounds %s; healed %.1f", stepped, hurt, healed));
                if (!stepped) fail.accept("the sidestep never happened");
                if (!hurt.isEmpty()) fail.accept("a sidestep after the aim lock should dodge the bite; took " + hurt);
                if (Math.abs(healed) > .01f) fail.accept("a miss should heal nothing; healed " + healed);
            }
            case "thorn-blink", "thorn-blink-dodge" -> {
                Vec3d to = blinkTo;
                double behind = to == null ? -1 : Math.hypot(to.x - playerAt.x, to.z - playerAt.z);
                boolean far = to != null && to.subtract(playerAt).multiply(1, 0, 1).dotProduct(playerAt.subtract(blinkFrom).multiply(1, 0, 1)) > 0;
                notes.add(String.format(Locale.ROOT, "blinked to %s, %.1f from the player, %s; bite cast at tick %d; wounds %s; healed %.1f",
                        fmt(to), behind, far ? "behind them" : "not behind them", biteCastAt, hurt, healed));
                if (to == null || biteCastAt < 0) fail.accept("the bite after the blink never cast (" + biteCastAt + ")");
                if (!far || behind < 4.5 || behind > 7.6) fail.accept("Blink Rift should land behind the player, 5 to 7 blocks past them; " + behind);
                if (name.equals("thorn-blink")) {
                    if (heavy != 1 || hurt.size() != 1) fail.accept("the bite from behind should land one heavy hit; took " + hurt);
                    if (Math.abs(healed - heal) > .01f) fail.accept("the bite should heal the copy by " + heal + "; healed " + healed);
                } else {
                    if (!stepped) fail.accept("the sidestep never happened");
                    if (!hurt.isEmpty()) fail.accept("from behind, a sidestep after the lock should still dodge it; took " + hurt);
                    if (Math.abs(healed) > .01f) fail.accept("a miss should heal nothing; healed " + healed);
                }
            }
            case "bloom-deliver" -> {
                long seeds = hurt.stream().filter(x -> Math.abs(x - FirstWizardRules.GROUND) < .01f).count();
                float perHit = SentryBloomSpell.STORE_PER_HIT * k.getMaxHealth(), cap = SentryBloomSpell.STORE_CAP * k.getMaxHealth();
                float expected = Math.min(cap, seeds * perHit);
                double fromPlayer = flowerAt == null ? -1 : Math.hypot(flowerAt.x - home.x, flowerAt.z - (home.z - 14));
                notes.add(String.format(Locale.ROOT, "flower at %s, %.1f from the player, open by tick %d; seeds struck %d %s; held %.0f at delivery; healed %.1f; second refused %s; gone %s",
                        fmt(flowerAt), fromPlayer, grownAt, seeds, hurt, heldAtDeliver, healed, secondRefused, flowerGone));
                if (flowerAt == null) fail.accept("ink should pool where the flower will grow");
                else {
                    if (Math.abs(flowerAt.y - home.y) > .05 || !k.arena().inside(flowerAt, FirstWizardRules.EDGE)) fail.accept("the flower should stand on the floor; " + fmt(flowerAt));
                    if (fromPlayer < 7) fail.accept("the flower should grow well away from the player; " + fromPlayer);
                }
                if (grownAt < 0) fail.accept("the flower should open");
                if (hurt.size() != seeds) fail.accept("every seed should be a light blow (" + FirstWizardRules.GROUND + "); took " + hurt);
                if (seeds < SentryBloomSpell.hitsToFill()) fail.accept("a player standing still should be struck at least " + SentryBloomSpell.hitsToFill() + " times; " + seeds);
                if (Math.abs(heldAtDeliver - expected) > .01f) fail.accept("its store should hold " + expected + " at delivery; held " + heldAtDeliver);
                if (Math.abs(healed - expected) > .01f) fail.accept("delivery should heal the copy by what it stored (" + expected + "); healed " + healed);
                if (!secondTried || !secondRefused) fail.accept("a second flower should not be cast while one stands");
                if (!flowerGone || flower(w, home) != null) fail.accept("the flower should wilt and be gone after it delivers");
                if (SentryBloomSpell.held(k.getUuid()) >= 0) fail.accept("the spell should have ended");
            }
            case "bloom-dodge" -> {
                notes.add(String.format(Locale.ROOT, "seeds dodged %d; wounds %s; most held %.0f; stopped clean %s", seeds, hurt, heldMost, stoppedClean));
                if (seeds < 2) fail.accept("it should have shot at least twice; " + seeds);
                if (!hurt.isEmpty()) fail.accept("a sidestep should dodge every seed; took " + hurt);
                if (heldMost > 0) fail.accept("a dodged seed should store nothing; held " + heldMost);
                if (!stoppedClean) fail.accept("stopping the fight should clear the flower and its spell at once");
            }
            case "bloom-break" -> {
                notes.add(String.format(Locale.ROOT, "hostile to the player's flowers %s; damaged %s; held %.0f when struck; broken %s; healed %.1f; another refused straight after %s",
                        hostile, gotDamage, heldBeforeBreak, broke, healed, refusedAfter));
                if (!hostile) fail.accept("the player's own Sentry Bloom flowers should treat it as an enemy");
                if (!gotDamage) fail.accept("the player's blows should hurt it");
                if (heldBeforeBreak <= 0) fail.accept("it should have stored something before it was broken");
                if (!broke) fail.accept("it should break");
                if (Math.abs(healed) > .01f) fail.accept("breaking it should waste its store; the copy healed " + healed);
                if (flower(w, home) != null) fail.accept("a broken flower should be gone");
                if (!refusedAfter) fail.accept("another flower should not be cast straight after one ends");
            }
            default -> {}
        }
    }

    private static InkBloomEntity flower(ServerWorld w, Vec3d home) {
        var found = w.getEntitiesByClass(InkBloomEntity.class, new Box(home, home).expand(70), e -> !e.isRemoved());
        return found.isEmpty() ? null : found.getFirst();
    }

    private static FirstWizardEffectEntity effect(ServerWorld w, Vec3d home, String kind) {
        var found = w.getEntitiesByClass(FirstWizardEffectEntity.class, new Box(home, home).expand(70), e -> e.kind().equals(kind) && e.isAlive());
        return found.isEmpty() ? null : found.getFirst();
    }

    private static void aim(ArmorStandEntity stand, Vec3d eye, Vec3d at) {
        Vec3d look = at.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z)), pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        stand.refreshPositionAndAngles(eye.x, eye.y - stand.getStandingEyeHeight(), eye.z, yaw, pitch);
        stand.setHeadYaw(yaw); stand.setBodyYaw(yaw);
    }

    private static String fmt(Vec3d v) { return v == null ? "none" : String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z); }
}
