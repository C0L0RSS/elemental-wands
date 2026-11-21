package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.Arena;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.InkBloomEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Sentry Bloom: one towering flower that stores healing for the copy until it is broken. The example of a spell that
 * leaves something players must answer.
 *
 * <ul>
 *   <li>A bud forms over the copy's head for 0.8 s; its spot is fixed at 0.5 s (ink pools there at once) and it is
 *       lobbed there: off to one side of the copy, well away from the players, on the floor.</li>
 *   <li>A flower about 6 blocks tall ({@link InkBloomEntity}) unfurls there over 1.5 s, then spits a seed every
 *       1.5 s at a player it has a clear line to, taking turns in co-op. Seeds fly straight at 12 blocks a second and
 *       lead a moving target, so a sidestep dodges them.</li>
 *   <li>A seed that hits is a light blow ({@link FirstWizardRules#GROUND}) and stores {@link #STORE_PER_HIT} of the
 *       copy's maximum health in the flower, up to {@link #STORE_CAP}.</li>
 *   <li>The flower has health of its own ({@link #health}) and takes the players' spells and weapons like a hostile
 *       mob; breaking it wastes everything it stored.</li>
 *   <li>12 s after it has opened, a flower still standing delivers: what it stored flows to the copy and heals it,
 *       and the flower wilts.</li>
 *   <li>One flower at a time per copy, and not again for {@link #COOLDOWN} ticks after one ends.</li>
 * </ul>
 *
 * <p>Effect kinds: {@link #BUD_KIND} (stage 0 forming over the head, 1 lobbed from {@code aux} to the point in
 * {@code dir}); {@link #GROUND_KIND} at the spot (stage 0 ink pooling, 1 standing, 2 delivering from the head in
 * {@code aux}, 3 wilted, 4 broken; {@code size} is the share it held); {@link #SEED_KIND} (stage 0 flying from {@code
 * aux} at the velocity in {@code dir}, 1 burst where it stopped).</p>
 */
public final class SentryBloomSpell implements BossSpell {
    public static final String BUD_KIND = "bloom_bud", GROUND_KIND = "bloom_ground", SEED_KIND = "bloom_seed";
    /** The bud forms from tick 0, its spot is fixed at 0.5 s, it is lobbed at 0.8 s and the copy is free 0.3 s later. */
    public static final Timing TIMING = new Timing(0, 10, 16, 22);
    /** Ticks: the lob's flight, the unfurling, the first seed after it opens, between seeds, how long it stands open. */
    public static final int LOB = 22, UNFURL = 30, FIRST_SHOT = 16, SHOT_EVERY = 30, STANDS = 240;
    /** Ticks: the flow to the copy, the wilt after it, the break, and how long before another flower after one ends. */
    public static final int DELIVER = 20, WILT = 30, BREAK = 30, COOLDOWN = 400;
    /** The bud's radius once formed, and its centre over the copy's feet. */
    public static final double BUD_RADIUS = .75, BUD_HEIGHT = 1.95 + .95;
    /** The flower: its head's pivot over the floor, the head's scale against the player's, its whole height. */
    public static final double HEAD_PIVOT = 3.9, HEAD_SCALE = 3.4, HEIGHT = 6.1;
    /** The ink pool under it. */
    public static final double POOL_RADIUS = 2.2;
    /** Seeds: blocks per tick (12 a second), a body this close to one is struck, drawn this big, flying at most this long. */
    public static final double SEED_SPEED = .6, SEED_REACH = .5, SEED_RADIUS = .35;
    public static final int SEED_FLIGHT = 80;
    /** It shoots only players this close to its head with a clear line to them. */
    public static final double RANGE = 40;
    /** A hit stores this share of the copy's maximum health, up to this share in all. */
    public static final float STORE_PER_HIT = .02f, STORE_CAP = .10f;
    /** Where it is planted: this far from the copy, and at least this far from every player when the floor allows. */
    public static final double RING_NEAR = 9, RING_FAR = 13, CLEAR = 9;
    public static final int POOLING = 0, STANDING = 1, DELIVERING = 2, WILTED = 3, BROKEN = 4;

    /** Each copy's flower, while one stands, and when another may be cast. Keyed by the copy. */
    private static final Map<UUID, Bloom> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.SENTRY_BLOOM; }
    @Override public Timing timing() { return TIMING; }

    /** The flower's health: 50 alone, 30 more for each player after the first (a first guess). */
    public static float health(int players) { return 50 + 30 * (Math.max(1, players) - 1); }

    /** Seed hits that fill the store. */
    public static int hitsToFill() { return Math.round(STORE_CAP / STORE_PER_HIT); }

    /** The bud's radius while it forms. */
    public static double formingRadius(double formed) { return BUD_RADIUS * (.15 + .85 * Math.pow(Math.clamp(formed, 0, 1), .7)); }

    /** Where the lobbed bud is after a share k of its flight from launch to the spot: a high arc. */
    public static Vec3d lob(Vec3d launch, Vec3d spot, double k) {
        k = Math.clamp(k, 0, 1);
        double apex = 3 + Math.hypot(spot.x - launch.x, spot.z - launch.z) * .2;
        Vec3d line = launch.lerp(spot.add(0, BUD_RADIUS * .6, 0), k);
        return line.add(0, 4 * apex * k * (1 - k), 0);
    }

    /** Whether a copy may plant a flower now: none standing, and the last one ended long enough ago. */
    public static boolean ready(UUID copy, long now) { return !ACTIVE.containsKey(copy) && now >= NEXT.getOrDefault(copy, Long.MIN_VALUE); }

    /**
     * Spots for the flower, best first, all on the floor with room for its pool: on a ring round the copy, off to one
     * side of the line to its target, as far from every player as it can be. Pure, so the contract check can hold them
     * to the floor.
     */
    public static List<Vec3d> spots(Arena arena, Vec3d boss, Vec3d target, List<Vec3d> players, DoubleSupplier random) {
        double margin = FirstWizardRules.EDGE + POOL_RADIUS;
        Vec3d toward = new Vec3d(target.x - boss.x, 0, target.z - boss.z);
        toward = toward.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : toward.normalize();
        List<double[]> scored = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            double side = i % 2 == 0 ? 1 : -1;
            double angle = Math.toRadians(side * (60 + random.getAsDouble() * 70)), ring = RING_NEAR + random.getAsDouble() * (RING_FAR - RING_NEAR);
            Vec3d dir = toward.rotateY((float) angle);
            Vec3d spot = arena.onFloor(new Vec3d(boss.x + dir.x * ring, arena.floor(), boss.z + dir.z * ring), margin);
            double clear = players.stream().mapToDouble(p -> Math.hypot(p.x - spot.x, p.z - spot.z)).min().orElse(99);
            scored.add(new double[]{spot.x, spot.z, Math.min(clear, CLEAR + 6) + random.getAsDouble() * 2});
        }
        scored.sort(Comparator.comparingDouble(s -> -s[2]));
        List<Vec3d> spots = new ArrayList<>();
        for (double[] s : scored) spots.add(new Vec3d(s[0], arena.floor(), s[1]));
        return spots;
    }

    @Override public Casting begin(Cast cast) {
        if (cast.target() == null || !ready(cast.boss().getUuid(), cast.now())) return null;
        return new Lob(cast);
    }

    /** The bud forming over the copy's head and its throw. */
    private static final class Lob implements Casting {
        private final Cast cast;
        private final FirstWizardEffectEntity bud;
        private FirstWizardEffectEntity ground;
        private Vec3d spot;

        Lob(Cast cast) {
            this.cast = cast;
            Vec3d over = new Vec3d(0, BUD_HEIGHT, 0);
            bud = cast.effect(BUD_KIND, cast.boss().getEntityPos().add(over), TIMING.release(), TIMING.release() + LOB + 6)
                    .size(BUD_RADIUS).anchor(over);
            cast.focus(cast.boss().getEntityPos().add(over));
            sound(cast, cast.boss().getEntityPos(), SoundEvents.BLOCK_AZALEA_LEAVES_PLACE, 1.6f, .5f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            Vec3d launch = cast.boss().getEntityPos().add(0, BUD_HEIGHT, 0);
            if (t < TIMING.release()) cast.focus(launch);
            if (t < TIMING.aimLock()) {
                if (target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
            } else if (t == TIMING.aimLock()) {
                Vec3d aimAt = target != null ? target.getEntityPos() : cast.boss().getEntityPos().add(cast.boss().getRotationVector().multiply(10));
                List<Vec3d> feet = cast.players().stream().map(ServerPlayerEntity::getEntityPos).toList();
                spot = spots(cast.arena(), cast.boss().getEntityPos(), aimAt, feet, cast.random()::nextDouble).getFirst();
                cast.aim(spot);
                cast.face(spot, 180);
                // Ink pools where it will grow from the moment its spot is fixed: the warning.
                ground = cast.effect(GROUND_KIND, spot, TIMING.release() + LOB - TIMING.aimLock(), 3000).size(0);
            }
            if (t == TIMING.release()) {
                bud.unanchor().aux(launch).dir(spot).stage(1);
                cast.focus(null);
                Bloom bloom = new Bloom(cast, bud, ground, spot);
                ACTIVE.put(cast.boss().getUuid(), bloom);
                cast.linger(bloom);
                sound(cast, launch, SoundEvents.ENTITY_SNOW_GOLEM_SHOOT, 1.6f, .5f);
            }
        }
    }

    /** The bud's flight, then the flower, from unfurling to its delivery or its break. */
    private static final class Bloom implements Lingering {
        private final Cast cast;
        private final UUID copy;
        private final FirstWizardEffectEntity bud, ground;
        private final Vec3d spot;
        private final long landAt;
        private InkBloomEntity flower;
        private int phase = -1;     // -1 in flight, then the ground's stages
        private long phaseAt, nextShot, openUntil;
        private float stored;
        private UUID lastTarget;
        /** Ended: it waits out the cooldown as one of the fight's leftovers, so stopping the fight clears it. */
        private boolean over;
        private long coolUntil;

        Bloom(Cast cast, FirstWizardEffectEntity bud, FirstWizardEffectEntity ground, Vec3d spot) {
            this.cast = cast; this.bud = bud; this.ground = ground; this.spot = spot;
            copy = cast.boss().getUuid();
            landAt = cast.now() + LOB;
        }

        private float cap() { return STORE_CAP * cast.boss().getMaxHealth(); }

        @Override public boolean tick(ServerWorld world, long now) {
            if (over) {
                if (now < coolUntil) return true;
                NEXT.remove(copy, coolUntil);
                return false;
            }
            switch (phase) {
                case -1 -> { if (now >= landAt) land(world, now); }
                case STANDING -> stand(world, now);
                case DELIVERING -> {
                    if (broken()) { breakUp(world, now); break; }
                    if (now >= phaseAt + DELIVER) {
                        FirstWizardEntity boss = cast.boss();
                        if (boss.isAlive() && stored > 0) boss.heal(stored);
                        flower.stage(InkBloomEntity.WILTING);
                        ground.stage(WILTED);
                        to(WILTED, now);
                        sound(cast, boss.getEntityPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.6f, .7f);
                        sound(cast, flower.getEntityPos(), SoundEvents.BLOCK_BIG_DRIPLEAF_TILT_DOWN, 1.4f, .5f);
                    }
                }
                case WILTED -> { if (now >= phaseAt + WILT) end(now); }
                case BROKEN -> { if (now >= phaseAt + BREAK) end(now); }
                default -> {}
            }
            return true;
        }

        private void land(ServerWorld world, long now) {
            bud.discard();
            flower = InkBloomEntity.plant(cast.boss(), spot, health(cast.players().size()));
            ground.stage(STANDING);
            to(STANDING, now);
            openUntil = now + UNFURL + STANDS;
            nextShot = now + UNFURL + FIRST_SHOT;
            sound(cast, spot, SoundEvents.BLOCK_BIG_DRIPLEAF_PLACE, 2f, .5f);
            sound(cast, spot, SoundEvents.BLOCK_AZALEA_LEAVES_BREAK, 1.6f, .6f);
        }

        private boolean broken() { return flower == null || flower.isRemoved() || !flower.isAlive(); }

        private void stand(ServerWorld world, long now) {
            if (broken()) { breakUp(world, now); return; }
            if (now >= openUntil) {
                flower.stage(InkBloomEntity.DELIVERING);
                ground.aux(flower.head()).size(stored / cap()).stage(DELIVERING);
                to(DELIVERING, now);
                sound(cast, flower.head(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 2f, .6f);
                return;
            }
            boolean open = now >= phaseAt + UNFURL;
            if (!open || now < nextShot && (now - phaseAt) % 5 != 0) return;
            ServerPlayerEntity target = pick(world);
            flower.aim(target);
            if (now < nextShot) return;
            if (target == null) { nextShot = now + 5; return; }
            shoot(target);
            nextShot = now + SHOT_EVERY;
        }

        /**
         * Whom it shoots: a player within range with a clear line from its head, taking turns in co-op (the next one
         * after the last it shot at, by a fixed order).
         */
        private ServerPlayerEntity pick(ServerWorld world) {
            Vec3d head = flower.head();
            List<ServerPlayerEntity> seen = new ArrayList<>();
            for (ServerPlayerEntity player : cast.players()) {
                Vec3d body = player.getBoundingBox().getCenter();
                if (body.squaredDistanceTo(head) > RANGE * RANGE) continue;
                var line = world.raycast(new RaycastContext(head, body, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, flower));
                if (line.getType() == HitResult.Type.MISS) seen.add(player);
            }
            if (seen.isEmpty()) return null;
            seen.sort(Comparator.comparing(ServerPlayerEntity::getUuid));
            if (lastTarget != null && seen.size() > 1)
                for (int i = 0; i < seen.size(); i++) if (seen.get(i).getUuid().equals(lastTarget)) return seen.get((i + 1) % seen.size());
            return seen.getFirst();
        }

        /** A seed at where the target's body will be when it arrives; it flies straight. */
        private void shoot(ServerPlayerEntity target) {
            Vec3d head = flower.head(), body = target.getBoundingBox().getCenter(), aim = body;
            Vec3d velocity = cast.velocity(target);
            for (int pass = 0; pass < 2; pass++) {
                Vec3d lead = velocity.multiply(head.distanceTo(aim) / SEED_SPEED);
                if (lead.length() > FirstWizardRules.LEAD_MAX) lead = lead.normalize().multiply(FirstWizardRules.LEAD_MAX);
                aim = body.add(lead);
            }
            Vec3d dir = aim.subtract(head).normalize();
            Vec3d muzzle = head.add(dir.multiply(.53 * HEAD_SCALE));
            Vec3d step = dir.multiply(SEED_SPEED);
            FirstWizardEffectEntity seed = cast.effect(SEED_KIND, muzzle, 0, SEED_FLIGHT + 12).size(SEED_RADIUS).aux(muzzle).dir(step);
            cast.linger(new Seed(cast, this, seed, muzzle, step));
            lastTarget = target.getUuid();
            flower.fired();
            sound(cast, muzzle, SoundEvents.ENTITY_LLAMA_SPIT, 1.6f, .5f);
        }

        /** A seed struck: a little more healing stored, up to the cap, while the flower stands open. */
        void stored() {
            if (phase != STANDING || broken()) return;
            stored = Math.min(cap(), stored + STORE_PER_HIT * cast.boss().getMaxHealth());
            flower.store(stored / cap());
        }

        private void breakUp(ServerWorld world, long now) {
            Vec3d at = flower != null ? flower.getEntityPos() : spot;
            if (flower != null && !flower.isRemoved()) flower.discard();
            // Everything it stored is wasted.
            ground.aux(at.add(0, HEAD_PIVOT, 0)).size(stored / cap()).stage(BROKEN);
            stored = 0;
            to(BROKEN, now);
            sound(cast, at, SoundEvents.BLOCK_AZALEA_LEAVES_BREAK, 2f, .5f);
            sound(cast, at, SoundEvents.BLOCK_BIG_DRIPLEAF_BREAK, 2f, .6f);
        }

        private void to(int next, long now) { phase = next; phaseAt = now; }

        private void end(long now) {
            over = true;
            if (flower != null && !flower.isRemoved()) flower.discard();
            ground.discard();
            ACTIVE.remove(copy, this);
            coolUntil = now + COOLDOWN;
            NEXT.put(copy, coolUntil);
        }

        @Override public void cancel(ServerWorld world) {
            if (flower != null && !flower.isRemoved()) flower.discard();
            bud.discard();
            ground.discard();
            // The fight is over: the next one starts without a wait.
            ACTIVE.remove(copy, this);
            if (over) NEXT.remove(copy, coolUntil);
            else NEXT.remove(copy);
            over = true;
            coolUntil = 0;
        }

        /** Fixtures: what it holds. */
        float held() { return stored; }
    }

    /** A seed in flight: the first player it reaches, a block, the floor or the floor's edge ends it. */
    private static final class Seed implements Lingering {
        private final Cast cast;
        private final Bloom bloom;
        private final FirstWizardEffectEntity seed;
        private final Vec3d from, step;
        private int ticks;
        private boolean done;

        Seed(Cast cast, Bloom bloom, FirstWizardEffectEntity seed, Vec3d from, Vec3d step) {
            this.cast = cast; this.bloom = bloom; this.seed = seed; this.from = from; this.step = step;
        }

        @Override public boolean tick(ServerWorld world, long now) {
            if (done) return false;
            Vec3d a = from.add(step.multiply(ticks));
            ticks++;
            Vec3d b = from.add(step.multiply(ticks));
            for (int i = 1; i <= 4; i++) {
                Vec3d at = a.lerp(b, i / 4.0), before = a.lerp(b, (i - 1) / 4.0);
                for (ServerPlayerEntity player : cast.players()) {
                    if (player.getBoundingBox().squaredMagnitude(at) > SEED_REACH * SEED_REACH) continue;
                    if (cast.hit(player, FirstWizardRules.GROUND, Cast.Hit.SPELL, seed)) bloom.stored();
                    burst(at);
                    return false;
                }
                var wall = world.raycast(new RaycastContext(before, at, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, seed));
                if (wall.getType() != HitResult.Type.MISS) { burst(wall.getPos()); return false; }
                if (at.y <= cast.arena().floor() + .05 || !cast.arena().inside(at, -1) || ticks >= SEED_FLIGHT) { burst(at); return false; }
            }
            seed.setPosition(b);
            return true;
        }

        private void burst(Vec3d at) {
            done = true;
            seed.setPosition(at);
            seed.aux(at).stage(1).life((int) (cast.now() - seed.start()) + 10);
            sound(cast, at, SoundEvents.BLOCK_AZALEA_LEAVES_BREAK, 1f, .8f);
        }

        @Override public void cancel(ServerWorld world) { seed.discard(); }
    }

    /** Fixtures: what the copy's standing flower holds, in health, or -1 when none stands. */
    public static float held(UUID copy) {
        Bloom bloom = ACTIVE.get(copy);
        return bloom == null ? -1 : bloom.held();
    }

    private static void sound(Cast cast, Vec3d at, SoundEvent event, float volume, float pitch) {
        cast.world().playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }
}
