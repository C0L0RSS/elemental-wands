package com.anton.elementalwands.entity.roc;

import static com.anton.elementalwands.entity.roc.StormRocRules.*;

import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.summit.StormSummit;
import com.anton.elementalwands.summit.StormSummitLayout;
import com.anton.elementalwands.util.SpellCombat;
import com.anton.elementalwands.util.TemporaryBlockManager;
import java.util.*;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

/** Telegraph -> committed hit -> opening. All encounter decisions and damage stay on the server. */
final class StormRocCombat {
    private static final RegistryKey<DamageType> DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("elementalwands", "roc_storm"));
    private static final Identifier SLOW = Identifier.of("elementalwands", "roc_tornado_drag");
    private static final double GOLDEN_ANGLE = 2.3999632297;
    private static final List<String> FEET = List.of("left_foot", "right_foot");

    /** Straight-ahead distances from its feet that a head attack's bill can reach. */
    private record Reach(double min, double max) {
        boolean has(double distance) { return distance >= min && distance <= max; }
    }

    private static final class Reaches {
        // Measured from the shipped sockets, so a retimed or reposed clip keeps the choices honest.
        // A one-block margin leaves room for a target drifting through the windup.
        static final Reach PECK = reach(Action.PECK.pose, Action.PECK.impact, Action.PECK.impact, PECK_RADIUS - 1);
        static final Reach SWING = reach(Action.SWING_LEFT.pose, Action.SWING_LEFT.impact, Action.SWING_LEFT.impact + 7, SWING_RADIUS - 1);
        static final double SCREAM_START = flat(StormRocPose.socket(Action.SCREAM.pose, Action.SCREAM.impact, "beak")).length();
    }

    private static final class Strike {
        final RocEffectEntity effect;
        int bolts;
        Strike(RocEffectEntity effect) { this.effect = effect; }
    }

    private static final class Tornado {
        final RocEffectEntity effect;
        double heading;
        Tornado(RocEffectEntity effect, double heading) { this.effect = effect; this.heading = heading; }
    }

    private record Caught(Tornado tornado, long at) {}

    private static final class Ring {
        final RocEffectEntity effect;
        final Set<UUID> passed = new HashSet<>();
        Ring(RocEffectEntity effect) { this.effect = effect; }
    }

    /** The largest Roc hit a player took in the current stacking window, and when the window opened. */
    private record Recent(long at, float amount) {}

    private record Threat(double amount, long at) {
        double decayed(long time) { return amount * Math.pow(.5, (time - at) / (double) THREAT_HALF_LIFE); }
    }

    private final StormRocEntity boss;
    private final Set<UUID> roster = new LinkedHashSet<>();
    private final Map<UUID, Threat> threat = new HashMap<>();
    private final Map<UUID, Vec3d> previous = new HashMap<>(), motion = new HashMap<>();
    private final Map<UUID, Long> immunity = new HashMap<>(), footHurt = new HashMap<>(), cawAt = new HashMap<>();
    private final Map<UUID, Recent> recent = new HashMap<>();
    private final Map<UUID, Caught> caught = new HashMap<>();
    private final Set<UUID> slowed = new HashSet<>();
    private final List<Strike> strikes = new ArrayList<>();
    private final List<Tornado> tornadoes = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<RocEffectEntity> effects = new ArrayList<>();
    private final Set<UUID> actionHits = new HashSet<>();
    private final Vec3d[] lastFeet = new Vec3d[FEET.size()];
    private final ServerBossBar bar = new ServerBossBar(Text.translatable("entity.elementalwands.storm_roc"), BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
    private BlockPos centre = BlockPos.ORIGIN;
    private Stage stage = Stage.IDLE;
    private Action action, last;
    private long since, nextAction, actionAt, nextSmall, nextLarge, nextRandom, nextDistant, nextStampede, rangedAt;
    private Vec3d from = Vec3d.ZERO, to = Vec3d.ZERO, mark = Vec3d.ZERO, aim = Vec3d.ZERO;
    private UUID target, rangedBy;
    private float floor;
    private int perch, round, selection, sinceMove;
    private boolean review, phasePending;
    private RocEffectEntity marker;

    StormRocCombat(StormRocEntity boss) { this.boss = boss; }

    Stage stage() { return stage; }
    boolean active() { return stage != Stage.IDLE && stage != Stage.COMPLETE; }
    boolean participant(UUID id) { return roster.contains(id); }
    void removeParticipant(UUID id) { roster.remove(id); caught.remove(id); immunity.remove(id); clearSlow(id); }
    float healthFloor() { return Math.max(phaseHealth(), floor); }

    String status() {
        return "Storm Roc: " + stage.name().toLowerCase(Locale.ROOT) + (action == null ? "" : " / " + action.name().toLowerCase(Locale.ROOT))
                + ", health " + Math.round(boss.getHealth()) + "/" + Math.round(boss.getMaxHealth())
                + ", tornadoes " + tornadoes.size() + ", fighters " + roster.size();
    }

    void start(BlockPos centre, List<ServerPlayerEntity> players) {
        cancel();
        this.centre = centre.toImmutable();
        for (var p : players) roster.add(p.getUuid());
        int health = health(Math.max(1, players.size()));
        boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health);
        boss.setHealth(health);
        floor = health * (float) (1 - LANDING_SHARE);
        phasePending = false;
        review = false;
        round = 0;
        perch = 0;
        selection = 0;
        sinceMove = 0;
        last = null;
        boss.setPosition(perch(0));
        boss.face(center());
        air();
        tornado(center().add(-19, 0, 10));
    }

    void cancel() {
        clearHazards();
        footHurt.clear();
        cawAt.clear();
        recent.clear();
        roster.clear();
        threat.clear();
        previous.clear();
        motion.clear();
        immunity.clear();
        rangedBy = null;
        bar.clearPlayers();
        stage = Stage.IDLE;
        action = null;
        marker = null;
        review = false;
    }

    private void clearHazards() {
        for (var e : effects) e.discard();
        effects.clear();
        strikes.clear();
        tornadoes.clear();
        rings.clear();
        caught.clear();
        for (UUID id : new HashSet<>(slowed)) clearSlow(id);
    }

    void damaged(ServerPlayerEntity player, float amount) {
        long now = now();
        Threat before = threat.get(player.getUuid());
        threat.put(player.getUuid(), new Threat((before == null ? 0 : before.decayed(now)) + amount, now));
        if (horizontal(player.getEntityPos(), boss.getEntityPos()) > RANGED_DISTANCE) {
            rangedBy = player.getUuid();
            rangedAt = now;
        }
        if (atPhaseGate()) phasePending = true;
    }

    void tick(ServerWorld world) {
        if (!active()) return;
        List<ServerPlayerEntity> players = players();
        if (players.isEmpty()) { cancel(); return; }
        trackPlayers(players);
        // Distant cosmetic bolts can be outside entity-ticking chunks; their owner still
        // expires them on schedule instead of letting an unticked flash repeat forever.
        effects.removeIf(e -> {
            if (!e.isRemoved() && e.effectTicks(0) > e.duration()) e.discard();
            return e.isRemoved();
        });
        if (!review && boss.getHealth() <= boss.getMaxHealth() * .75f && tornadoes.size() < 2) tornado(center().add(19, 0, -12));
        tickStrikes(players);
        tickTornadoes(players);
        tickRings(players);
        caw(players);
        if (stage == Stage.STUCK || stage == Stage.BRAWL) groundSpells();
        if (atPhaseGate() && stage != Stage.TAKEOFF) takeoff();
        long t = now() - since;
        switch (stage) {
            case AIR -> tickAir(players, t);
            case DIVE -> tickDive(players, t);
            case STUCK -> {
                stomps(players, true);
                if (t >= STUCK_TICKS) {
                    enter(Stage.BRAWL, "hunched");
                    nextAction = now() + 12;
                }
            }
            case BRAWL -> {
                if (boss.getHealth() <= floor + .01f || atPhaseGate()) { takeoff(); break; }
                if (action != null) tickAction(players);
                else if (now() >= nextAction && !review) chooseAction(players);
                stomps(players, false);
            }
            case TAKEOFF -> tickTakeoff(players, t);
            default -> {}
        }
    }

    private void trackPlayers(List<ServerPlayerEntity> players) {
        for (var p : new HashSet<>(bar.getPlayers())) if (!players.contains(p)) bar.removePlayer(p);
        for (var p : players) {
            bar.addPlayer(p);
            Vec3d before = previous.put(p.getUuid(), p.getEntityPos());
            Vec3d delta = before == null ? Vec3d.ZERO : flat(p.getEntityPos().subtract(before));
            // Leads follow running, not a blink or a throw.
            motion.put(p.getUuid(), delta.lengthSquared() > .36 ? Vec3d.ZERO : delta);
        }
        bar.setPercent(Math.clamp(boss.getHealth() / boss.getMaxHealth(), 0, 1));
    }

    // ------------------------------------------------------------------ stages

    private void enter(Stage stage, String pose) {
        this.stage = stage;
        since = now();
        action = null;
        boss.pose(pose);
        Arrays.fill(lastFeet, null);
    }

    private void air() {
        enter(Stage.AIR, "perched");
        nextSmall = now() + 25;
        nextLarge = now() + 80;
        nextRandom = now() + 35;
        nextDistant = now() + 100;
    }

    private int airDuration() {
        Difficulty difficulty = world().getDifficulty();
        return difficulty == Difficulty.HARD ? AIR_TICKS_HARD : difficulty == Difficulty.EASY ? AIR_TICKS_EASY : AIR_TICKS;
    }

    /** Perched for most of each six-second cycle, then a flight to the next spire. */
    private void tickAir(List<ServerPlayerEntity> players, long t) {
        storm(players);
        int cycle = (int) (t % 120);
        if (cycle == 75) {
            from = boss.getEntityPos();
            perch = (perch + 1) % StormSummitLayout.PERCHES.length;
            to = perch(perch);
            boss.pose("flight");
        }
        if (cycle >= 75) fly(Math.min(1, (cycle - 75) / 44.0));
        if (cycle == 0) {
            boss.setPosition(perch(perch));
            boss.face(center());
            boss.pose("perched");
        }
        if (t >= airDuration()) dive(chooseLanding());
    }

    /** It banks toward the next spire, then turns to face the summit before it lands. */
    private void fly(double f) {
        boss.setPosition(from.lerp(to, smooth(f)).add(0, Math.sin(f * Math.PI) * 12, 0));
        turnToward(f < .65 ? to : center(), FLIGHT_TURN_RATE);
    }

    private void tickDive(List<ServerPlayerEntity> players, long t) {
        if (!review) storm(players);
        double f = Math.clamp(t / (double) DIVE_TICKS, 0, 1);
        Vec3d apex = mark.add(0, 64, 0);
        boss.setPosition(f < .6 ? from.lerp(apex, smooth(f / .6)) : apex.lerp(to, smooth((f - .6) / .4)));
        if (t < DIVE_TICKS) return;
        boss.setPosition(to);
        if (marker != null) marker.discard();
        for (var p : players)
            if (horizontal(p.getEntityPos(), mark) <= DIVE_RADIUS + p.getWidth() * .5 && p.getY() < mark.y + 26) hurt(p, DIVE_DAMAGE);
        ring(mark, 70);
        sound(mark, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 3, .55f);
        // Phase one's sky stops striking at impact; permanent tornadoes remain.
        for (var s : strikes) s.effect.discard();
        strikes.clear();
        enter(Stage.STUCK, "stuck");
    }

    private void takeoff() {
        phasePending |= atPhaseGate();
        from = boss.getEntityPos();
        perch = (perch + 1) % StormSummitLayout.PERCHES.length;
        to = perch(perch);
        enter(Stage.TAKEOFF, "takeoff");
    }

    private void tickTakeoff(List<ServerPlayerEntity> players, long t) {
        // The huge wing spread blasts everyone away without damage, then it flies up.
        if (t == 24) for (var p : players)
            if (horizontal(p.getEntityPos(), boss.getEntityPos()) < 35) pushSafe(p, p.getEntityPos().subtract(boss.getEntityPos()), 1.8, .38);
        if (t >= 24) fly(Math.clamp((t - 24) / 26.0, 0, 1));
        if (t < TAKEOFF_TICKS) return;
        if (phasePending || atPhaseGate()) {
            clearHazards();
            bar.clearPlayers();
            stage = Stage.COMPLETE;
            boss.pose("perched");
            boss.face(center());
        } else {
            floor = Math.max(phaseHealth(), floor - boss.getMaxHealth() * (float) LANDING_SHARE);
            round++;
            air();
        }
    }

    // ------------------------------------------------------------------ lightning

    // Markers persist through every full-strength repeat; nothing authorizes a hit client-side.
    private void storm(List<ServerPlayerEntity> players) {
        if (now() >= nextSmall) { aimed(players, false); nextSmall = now() + SMALL_EVERY; }
        if (now() >= nextLarge) { aimed(players, true); nextLarge = now() + LARGE_EVERY; }
        if (now() >= nextRandom) {
            double a = boss.getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(boss.getRandom().nextDouble()) * RANDOM_RADIUS;
            strike(center().add(Math.cos(a) * r, 0, Math.sin(a) * r), boss.getRandom().nextInt(4) == 0);
            nextRandom = now() + RANDOM_EVERY;
        }
        if (now() >= nextDistant) {
            double a = boss.getRandom().nextDouble() * Math.PI * 2;
            effect(RocEffectEntity.Kind.DISTANT_BOLT, center().add(Math.cos(a) * 140, -30, Math.sin(a) * 140), 3, 0, 6);
            nextDistant = now() + DISTANT_EVERY;
        }
    }

    private void aimed(List<ServerPlayerEntity> players, boolean large) {
        int warning = large ? LARGE_WARNING : SMALL_WARNING;
        for (int i = 0; i < Math.min(AIMED_CAP, players.size()); i++) {
            var p = players.get(Math.floorMod(selection++, players.size()));
            Vec3d spot = p.getEntityPos();
            // Half land where the player is running, so a straight line doesn't dodge them.
            if ((selection + round) % 2 == 0) spot = spot.add(motion.getOrDefault(p.getUuid(), Vec3d.ZERO).multiply(warning * .7));
            strike(spot, large);
        }
    }

    RocEffectEntity strike(Vec3d point, boolean large) {
        if (!active()) return null;
        double radius = large ? LARGE_RADIUS : SMALL_RADIUS;
        int warning = large ? LARGE_WARNING : SMALL_WARNING, bolts = large ? LARGE_BOLTS : SMALL_BOLTS;
        Vec3d candidate = onFloor(point);
        // A relocated centre must be outside every live circle; edges may still partly overlap.
        for (int attempt = 0; attempt < 160; attempt++) {
            if (attempt > 0) {
                double a = attempt * GOLDEN_ANGLE, r = .8 * Math.sqrt(attempt);
                candidate = onFloor(point.add(Math.cos(a) * r, 0, Math.sin(a) * r));
            }
            if (StormSummit.beyondRim(centre, candidate) > -1 || insideLiveCircle(candidate, radius)) continue;
            var kind = large ? RocEffectEntity.Kind.LARGE_STRIKE : RocEffectEntity.Kind.SMALL_STRIKE;
            var e = effect(kind, candidate, radius, warning, warning + (bolts - 1) * BOLT_GAP + 6);
            strikes.add(new Strike(e));
            sound(candidate, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.7f, large ? .55f : 1.1f);
            return e;
        }
        return null; // Never stack an unavoidable strike when the bounded search fills up.
    }

    private boolean insideLiveCircle(Vec3d candidate, double radius) {
        for (var s : strikes)
            if (!s.effect.isRemoved() && horizontal(candidate, s.effect.getEntityPos()) < Math.max(radius, s.effect.radius()) + .05) return true;
        return false;
    }

    private void tickStrikes(List<ServerPlayerEntity> players) {
        for (var it = strikes.iterator(); it.hasNext(); ) {
            var s = it.next();
            var e = s.effect;
            if (e.isRemoved()) { it.remove(); continue; }
            boolean large = e.kind() == RocEffectEntity.Kind.LARGE_STRIKE;
            if (s.bolts >= (large ? LARGE_BOLTS : SMALL_BOLTS) || e.effectTicks(0) < e.warningTicks() + s.bolts * BOLT_GAP) continue;
            s.bolts++;
            sound(e.getEntityPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, large ? 2.3f : 1.4f, large ? .65f : 1.15f);
            for (var p : players)
                if (horizontal(p.getEntityPos(), e.getEntityPos()) <= e.radius() + p.getWidth() * .5
                        && p.getY() >= center().y - 4 && p.getY() < center().y + 110)
                    hurt(p, large ? LARGE_DAMAGE : SMALL_DAMAGE);
        }
    }

    private RocEffectEntity effect(RocEffectEntity.Kind kind, Vec3d position, double radius, int warning, int duration) {
        var e = RocEffectEntity.spawn(boss, kind, position, radius, warning, duration);
        effects.add(e);
        return e;
    }

    // ------------------------------------------------------------------ tornadoes

    // Persistent drifting obstacles, never homing missiles. Immunity after a throw prevents juggling.
    RocEffectEntity tornado(Vec3d point) {
        Vec3d spot = clamp(point, SAFE_RADIUS);
        for (int i = 0; i < 64 && nearTornado(spot, 15); i++) {
            double a = i * GOLDEN_ANGLE;
            spot = center().add(Math.cos(a) * 27, 0, Math.sin(a) * 27);
        }
        var e = effect(RocEffectEntity.Kind.TORNADO, spot, TORNADO_RADIUS, 0, Integer.MAX_VALUE);
        tornadoes.add(new Tornado(e, boss.getRandom().nextDouble() * Math.PI * 2));
        return e;
    }

    private boolean nearTornado(Vec3d spot, double distance) {
        for (var t : tornadoes) if (horizontal(spot, t.effect.getEntityPos()) < distance) return true;
        return false;
    }

    private void tickTornadoes(List<ServerPlayerEntity> players) {
        Set<UUID> inside = new HashSet<>();
        for (var t : tornadoes) {
            if (t.effect.isRemoved()) continue;
            drift(t);
            for (var p : players) {
                double distance = horizontal(p.getEntityPos(), t.effect.getEntityPos());
                if (distance > TORNADO_RADIUS || p.getY() > center().y + 20) continue;
                inside.add(p.getUuid());
                slow(p, Math.clamp(.15 + .45 * (1 - distance / TORNADO_RADIUS), .15, .6));
                if (caught.containsKey(p.getUuid())) continue;
                if (distance < TORNADO_CORE && now() >= immunity.getOrDefault(p.getUuid(), 0L)) {
                    caught.put(p.getUuid(), new Caught(t, now()));
                    continue;
                }
                // The pull is much stronger in the air, which punishes constant jumping.
                Vec3d inward = flat(t.effect.getEntityPos().subtract(p.getEntityPos())).normalize();
                double pull = p.isOnGround() ? .012 : .065;
                p.addVelocity(inward.x * pull, 0, inward.z * pull);
                p.velocityModified = true;
                if (now() % TORNADO_CHIP_EVERY == 0) hurt(p, TORNADO_CHIP);
            }
        }
        for (UUID id : new HashSet<>(slowed)) if (!inside.contains(id)) clearSlow(id);
        tickCaught();
    }

    /** Slower than walking; it turns away from the edge and from other tornadoes. */
    private void drift(Tornado t) {
        Vec3d next = t.effect.getEntityPos().add(Math.cos(t.heading) * .026, 0, Math.sin(t.heading) * .026);
        boolean blocked = horizontal(next, center()) > SAFE_RADIUS;
        for (var other : tornadoes) if (other != t && horizontal(next, other.effect.getEntityPos()) < 14) blocked = true;
        if (blocked) t.heading += 1.1;
        else t.effect.setPosition(next);
    }

    private void tickCaught() {
        for (var it = caught.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            var p = player(entry.getKey());
            var hold = entry.getValue();
            Vec3d core = hold.tornado.effect.getEntityPos();
            if (p == null || !boss.eligible(p) || horizontal(p.getEntityPos(), core) > TORNADO_RADIUS * 1.5) {
                immunity.put(entry.getKey(), now() + TORNADO_IMMUNITY);
                it.remove();
                continue;
            }
            long age = now() - hold.at;
            if (age >= TORNADO_HOLD) {
                // Thrown out, always toward the middle of the summit.
                hurt(p, TORNADO_DAMAGE);
                pushSafe(p, center().subtract(p.getEntityPos()), 2.2, .55);
                immunity.put(entry.getKey(), now() + TORNADO_IMMUNITY);
                it.remove();
                continue;
            }
            Vec3d d = flat(p.getEntityPos().subtract(core)).normalize();
            Vec3d orbit = core.add(d.x * .8 - d.z * .45, Math.min(5, 1 + age * .18), d.z * .8 + d.x * .45);
            Vec3d v = orbit.subtract(p.getEntityPos()).multiply(.32);
            if (v.lengthSquared() > 1) v = v.normalize();
            p.setVelocity(v);
            p.velocityModified = true;
            p.fallDistance = 0;
        }
    }

    private void slow(ServerPlayerEntity p, double amount) {
        var attribute = p.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        attribute.removeModifier(SLOW);
        attribute.addTemporaryModifier(new EntityAttributeModifier(SLOW, -amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        slowed.add(p.getUuid());
    }

    private void clearSlow(UUID id) {
        var p = player(id);
        var attribute = p == null ? null : p.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(SLOW);
        slowed.remove(id);
    }

    // ------------------------------------------------------------------ spires, edge and shockwaves

    /** A player who flies up to a spire is met with a caw that hurts and throws them toward the summit. */
    private void caw(List<ServerPlayerEntity> players) {
        if (stage != Stage.AIR) return;
        for (var p : players) {
            if (p.getY() < center().y + 8 || now() < cawAt.getOrDefault(p.getUuid(), 0L)) continue;
            for (int i = 0; i < StormSummitLayout.PERCHES.length; i++) {
                if (horizontal(p.getEntityPos(), perch(i)) >= 17) continue;
                hurt(p, CAW_DAMAGE);
                Vec3d v = flat(center().subtract(p.getEntityPos())).normalize().multiply(2.8);
                p.setVelocity(v.x, .55, v.z);
                p.velocityModified = true;
                cawAt.put(p.getUuid(), now() + 40);
                sound(p.getEntityPos(), SoundEvents.ENTITY_PHANTOM_SWOOP, 2, .5f);
                break;
            }
        }
    }

    // Every authored shove stops inside an interior margin. Voluntary movement can still fall.
    private void pushSafe(ServerPlayerEntity p, Vec3d direction, double strength, double up) {
        Vec3d d = flat(direction).normalize();
        Vec3d projected = p.getEntityPos().add(d.multiply(strength * 12));
        double radius = StormSummit.rim(projected.x - center().x, projected.z - center().z) - 9;
        Vec3d impulse = clamp(projected, radius).subtract(p.getEntityPos()).multiply(1 / 12.0, 0, 1 / 12.0);
        // A player already near the edge can only receive an inward impulse.
        if (StormSummit.beyondRim(centre, p.getEntityPos()) > -10)
            impulse = flat(center().subtract(p.getEntityPos())).normalize().multiply(Math.min(strength, 1.4));
        p.setVelocity(impulse.x, up, impulse.z);
        p.velocityModified = true;
        p.fallDistance = 0;
    }

    private void ring(Vec3d mark, double radius) {
        var e = effect(RocEffectEntity.Kind.SHOCKWAVE, mark, radius, 0, (int) Math.ceil(radius / SHOCK_SPEED) + 2);
        rings.add(new Ring(e));
    }

    private void tickRings(List<ServerPlayerEntity> players) {
        for (var it = rings.iterator(); it.hasNext(); ) {
            var ring = it.next();
            if (ring.effect.isRemoved()) { it.remove(); continue; }
            double radius = ring.effect.effectTicks(0) * SHOCK_SPEED, inner = Math.max(0, radius - SHOCK_SPEED);
            double floorY = ring.effect.getY();
            for (var p : players) {
                if (ring.passed.contains(p.getUuid())) continue;
                double distance = horizontal(p.getEntityPos(), ring.effect.getEntityPos());
                if (distance + p.getWidth() * .5 < inner - .6 || distance - p.getWidth() * .5 > radius + .6) continue;
                // One contact decides, so a player who jumped it is not hit by its trailing smoke.
                ring.passed.add(p.getUuid());
                // It rolls over low rocks: standing on one doesn't clear it, only being in the air does.
                boolean grounded = p.isOnGround() || p.getY() < floorY + SHOCK_HEIGHT;
                if (grounded && p.getY() + p.getHeight() > floorY - .4) hurt(p, RING_DAMAGE);
            }
        }
    }

    // ------------------------------------------------------------------ dive

    // The fixed dive area never tracks a player or covers a tornado.
    private Vec3d chooseLanding() {
        Vec3d best = center();
        double bestClear = -1;
        for (int i = 0; i < 80; i++) {
            double a = i * GOLDEN_ANGLE, r = Math.sqrt(i / 80.0) * 24;
            Vec3d point = center().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            double nearest = 999;
            for (var t : tornadoes) nearest = Math.min(nearest, horizontal(point, t.effect.getEntityPos()));
            if (nearest > DIVE_RADIUS + TORNADO_RADIUS + 2) return point;
            if (nearest > bestClear) { best = point; bestClear = nearest; }
        }
        // Two spaced tornadoes always leave a clear patch; otherwise take the clearest point found.
        return best;
    }

    void dive(Vec3d point) {
        mark = clamp(point, 24);
        if (nearTornado(mark, DIVE_RADIUS + TORNADO_RADIUS + 2)) mark = chooseLanding();
        from = boss.getEntityPos();
        boss.face(mark);
        enter(Stage.DIVE, "dive");
        // Land so the pinned bill, not the feet, ends on the mark.
        Vec3d bill = boss.modelToWorld(StormRocPose.socket("stuck", 0, "beak")).subtract(boss.getEntityPos());
        to = new Vec3d(mark.x - bill.x, center().y, mark.z - bill.z);
        marker = effect(RocEffectEntity.Kind.DIVE_MARKER, mark, DIVE_RADIUS, DIVE_TICKS, DIVE_TICKS + 2);
        sound(mark, SoundEvents.ENTITY_PHANTOM_AMBIENT, 3, .45f);
    }

    // ------------------------------------------------------------------ on the ground

    /**
     * Authored/no-clip movement skips vanilla stepping callbacks. Preserve owned ground
     * spell contact explicitly at the actual feet, without allowing ambient world damage.
     */
    private void groundSpells() {
        if (action == Action.POUNCE && now() - actionAt >= 24 && now() - actionAt < 44) return;
        for (String side : FEET) {
            Vec3d foot = boss.socket(side);
            if (foot.y > center().y + 1.8) continue;
            BlockPos air = BlockPos.ofFloored(foot.x, center().y, foot.z);
            for (BlockPos pos : List.of(air, air.down())) {
                var state = world().getBlockState(pos);
                boolean flame = state.isOf(ModSpellBlocks.INFERNO_FLAME), coals = state.isOf(ModSpellBlocks.PYRE_COALS);
                if (!flame && !coals) continue;
                UUID owner = TemporaryBlockManager.casterAt(world(), pos);
                var player = player(owner);
                if (player == null || !boss.eligible(player)) continue;
                var source = flame ? world().getDamageSources().inFire() : world().getDamageSources().hotFloor();
                SpellCombat.damage(boss, world(), source, 1, player, WizardAffinity.FIRE);
                if (flame) SpellCombat.ignite(boss, player, 8);
            }
        }
    }

    /**
     * While stuck its scrambling legs hurt anyone close. In the brawl only a moving foot does,
     * except during the pounce's flight and the stampede's charge, which have their own hits.
     */
    private void stomps(List<ServerPlayerEntity> players, boolean scrambling) {
        long t = action == null ? -1 : now() - actionAt;
        boolean charging = action == Action.POUNCE && t >= 24 && t <= 44 || action == Action.STAMPEDE && t >= 24 && t <= 54;
        for (int i = 0; i < FEET.size(); i++) {
            Vec3d foot = boss.socket(FEET.get(i)), before = lastFeet[i];
            lastFeet[i] = foot;
            if (!scrambling && (charging || before == null || before.distanceTo(foot) < STOMP_MOTION)) continue;
            for (var p : players) {
                if (now() < footHurt.getOrDefault(p.getUuid(), 0L)) continue;
                if (horizontal(p.getEntityPos(), foot) < FOOT_RADIUS && p.getY() < foot.y + 4 && p.getY() + p.getHeight() > foot.y - 1) {
                    hurt(p, FOOT_DAMAGE);
                    footHurt.put(p.getUuid(), now() + FOOT_COOLDOWN);
                }
            }
        }
    }

    // Ground brawl: where its target stands decides the answer, and every aim locks before the hit.
    private void chooseAction(List<ServerPlayerEntity> players) {
        ServerPlayerEntity ranged = rangedAttacker();
        if (ranged != null && now() >= nextStampede) { begin(ranged, Action.STAMPEDE, false); return; }
        var p = pickTarget(players);
        Vec3d relative = flat(p.getEntityPos().subtract(boss.getEntityPos()));
        double distance = relative.length();
        if (last != Action.POUNCE && (distance > POUNCE_FAR || sinceMove >= ACTIONS_BEFORE_POUNCE)) {
            begin(p, Action.POUNCE, false);
            return;
        }
        double facing = distance < .01 ? 1 : relative.normalize().dotProduct(forward());
        var options = answers(distance, facing, relative.crossProduct(forward()).y > 0, last);
        begin(p, options.get(boss.getRandom().nextInt(options.size())), false);
    }

    /**
     * Every place to stand has an answer. facing is 1 straight ahead and -1 behind; the last
     * action is never repeated. Head attacks are offered only where their bill can reach.
     */
    static List<Action> answers(double distance, double facing, boolean rightSide, Action last) {
        boolean close = distance < WING_REACH - 2;
        List<Action> options = new ArrayList<>();
        if (facing < .2 && close) options.add(Action.WING); // Beside or behind it, where the head can't reach.
        else {
            if (Reaches.PECK.has(distance)) options.add(Action.PECK);
            if (Reaches.SWING.has(distance)) options.add(rightSide ? Action.SWING_LEFT : Action.SWING_RIGHT);
            if (distance >= Reaches.SCREAM_START) options.add(Action.SCREAM);
            // Under its neck and chest no head attack reaches, so the wings answer.
            if (options.isEmpty() && close) options.add(Action.WING);
        }
        options.removeIf(a -> a == last || a.swing() && last != null && last.swing());
        if (!options.isEmpty()) return options;
        if (close && last != Action.WING) return List.of(Action.WING);
        return List.of(last != Action.POUNCE ? Action.POUNCE : Action.SCREAM);
    }

    /** The player who last hit it from range, while they are still out there. */
    private ServerPlayerEntity rangedAttacker() {
        if (rangedBy == null || now() - rangedAt > RANGED_MEMORY) return null;
        var p = player(rangedBy);
        return p != null && boss.eligible(p) && StormSummit.nearestCentre(p.getEntityPos()).equals(centre)
                && horizontal(p.getEntityPos(), boss.getEntityPos()) > RANGED_DISTANCE - 1 ? p : null;
    }

    /** Weighted by recent damage: the main damage dealer draws most attacks, but not all of them. */
    private ServerPlayerEntity pickTarget(List<ServerPlayerEntity> players) {
        double[] weights = new double[players.size()];
        double total = 0;
        for (int i = 0; i < weights.length; i++) {
            Threat t = threat.get(players.get(i).getUuid());
            weights[i] = THREAT_FLOOR + (t == null ? 0 : t.decayed(now()));
            total += weights[i];
        }
        double roll = boss.getRandom().nextDouble() * total;
        for (int i = 0; i < weights.length; i++) if ((roll -= weights[i]) < 0) return players.get(i);
        return players.getLast();
    }

    private void begin(ServerPlayerEntity p, Action chosen, boolean snap) {
        action = chosen;
        actionAt = now();
        target = p.getUuid();
        actionHits.clear();
        aim = p.getEntityPos();
        if (snap) boss.face(aim);
        boss.pose(chosen.pose);
        from = boss.getEntityPos();
        to = interior(aim, 10);
        sinceMove = chosen == Action.POUNCE || chosen == Action.STAMPEDE ? 0 : sinceMove + 1;
        if (chosen == Action.STAMPEDE) {
            nextStampede = now() + STAMPEDE_COOLDOWN;
            rangedBy = null;
            if (flat(to.subtract(from)).lengthSquared() < 16) to = interior(from.add(forward().multiply(20)), 10);
        }
    }

    private void tickAction(List<ServerPlayerEntity> players) {
        int t = (int) (now() - actionAt);
        var p = player(target);
        if (t < action.lock) {
            // Swings sweep where the target stood when chosen; the rest follow it until their aim locks.
            if (p != null && boss.eligible(p) && !action.swing()) {
                aim = p.getEntityPos();
                if (action == Action.POUNCE || action == Action.STAMPEDE) to = interior(aim, 10);
            }
            turnToward(aim, TURN_RATE);
        }
        switch (action) {
            case PECK -> { if (t == action.impact) hitCircle(players, boss.socket("beak"), PECK_RADIUS, PECK_DAMAGE, 9); }
            case SWING_LEFT, SWING_RIGHT -> swing(players, t);
            case SCREAM -> { if (t == action.impact) scream(players); }
            case WING -> { if (t == action.impact) wing(players); }
            case POUNCE -> pounce(players, t);
            case STAMPEDE -> stampede(players, t);
        }
        if (t >= action.duration) {
            last = action;
            action = null;
            boss.pose("hunched");
            int party = Math.min(AIMED_CAP, players.size());
            Difficulty difficulty = world().getDifficulty();
            int pause = 18 - (party - 1) * 2 + (difficulty == Difficulty.EASY ? 8 : difficulty == Difficulty.HARD ? -5 : 0);
            nextAction = now() + Math.max(6, pause);
        }
    }

    // Resolve the swept arc across several frames, once per victim, along the actual bill.
    private void swing(List<ServerPlayerEntity> players, int t) {
        if (t < action.impact || t > action.impact + 7) return;
        Vec3d bill = boss.socket("beak");
        for (var victim : players) {
            if (actionHits.contains(victim.getUuid()) || horizontal(victim.getEntityPos(), bill) >= SWING_RADIUS) continue;
            if (victim.getY() >= bill.y + 3 || victim.getY() + victim.getHeight() <= bill.y - 3) continue;
            if (!hurt(victim, PECK_DAMAGE)) continue;
            actionHits.add(victim.getUuid());
            pushSafe(victim, Vec3d.fromPolar(0, boss.getYaw() + (action == Action.SWING_LEFT ? 90 : -90)), .65, .16);
        }
    }

    /** A straight line ahead from the bill: too wide to sidestep casually, safe behind it. */
    private void scream(List<ServerPlayerEntity> players) {
        Vec3d origin = boss.socket("beak"), forward = forward();
        var e = effect(RocEffectEntity.Kind.SCREAM, new Vec3d(origin.x, center().y + 1, origin.z), SCREAM_HALF_WIDTH, 0, 10);
        e.setYaw(boss.getYaw());
        for (var victim : players) {
            Vec3d d = victim.getEntityPos().subtract(origin);
            double along = d.dotProduct(forward), side = Math.abs(d.x * forward.z - d.z * forward.x);
            if (along >= -1 && along <= 110 && side <= SCREAM_HALF_WIDTH + victim.getWidth() * .5 && victim.getY() < center().y + 12)
                hurt(victim, SCREAM_DAMAGE);
        }
        sound(origin, SoundEvents.ENTITY_PHANTOM_DEATH, 3, .4f);
    }

    private void wing(List<ServerPlayerEntity> players) {
        effect(RocEffectEntity.Kind.SHOCKWAVE, boss.getEntityPos(), WING_REACH, 0, 22);
        for (var victim : players) {
            if (horizontal(victim.getEntityPos(), boss.getEntityPos()) >= WING_REACH) continue;
            if (victim.getY() >= center().y + 18 || victim.getY() + victim.getHeight() <= center().y) continue;
            hurt(victim, WING_DAMAGE);
            pushSafe(victim, victim.getEntityPos().subtract(boss.getEntityPos()), 1.4, .3);
        }
    }

    /** Cat wiggle, then one huge flap onto a circle marked at launch; its aim locked 0.4 s before. */
    private void pounce(List<ServerPlayerEntity> players, int t) {
        if (t == 24) {
            boss.face(to);
            marker = effect(RocEffectEntity.Kind.DIVE_MARKER, to, 6, 20, 22);
        }
        if (t >= 24 && t <= 44) {
            double f = (t - 24) / 20.0;
            boss.setPosition(from.lerp(to, f).add(0, Math.sin(f * Math.PI) * 14, 0));
        }
        if (t == 44) {
            boss.setPosition(to);
            hitCircle(players, to, 6, POUNCE_DAMAGE, 10);
            ring(to, 26);
            sound(to, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 2, .65f);
        }
    }

    /** Two bull scrapes, a straight charge that can't stop in time, then a long skid. */
    private void stampede(List<ServerPlayerEntity> players, int t) {
        if (t == action.impact) boss.face(to);
        if (t >= 24 && t <= 54) {
            Vec3d old = boss.getEntityPos();
            boss.setPosition(from.lerp(to, (t - 24) / 30.0));
            for (var victim : players) {
                if (actionHits.contains(victim.getUuid()) || victim.getY() >= center().y + 10) continue;
                if (distanceToSegment(victim.getEntityPos(), old, boss.getEntityPos()) >= 3.3 || !hurt(victim, STAMPEDE_DAMAGE)) continue;
                actionHits.add(victim.getUuid());
                // Tossed aside, off whichever side of its path they stood on.
                double side = flat(victim.getEntityPos().subtract(old)).crossProduct(forward()).y;
                pushSafe(victim, Vec3d.fromPolar(0, boss.getYaw() + (side > 0 ? 90 : -90)), .8, .2);
            }
        }
        if (t > 54) {
            double f = Math.min(1, (t - 54) / 30.0);
            Vec3d slide = flat(to.subtract(from)).normalize().multiply(4 * (1 - (1 - f) * (1 - f)));
            boss.setPosition(interior(to.add(slide), 9));
        }
    }

    private void hitCircle(List<ServerPlayerEntity> players, Vec3d point, double radius, float damage, double height) {
        for (var p : players) if (horizontal(p.getEntityPos(), point) <= radius && p.getY() < center().y + height) hurt(p, damage);
    }

    void rehearse(ServerPlayerEntity player, Action action) {
        if (!active()) start(StormSummit.nearestCentre(player.getEntityPos()), List.of(player));
        clearHazards();
        review = true;
        boss.setPosition(center());
        enter(Stage.BRAWL, "hunched");
        floor = Math.max(phaseHealth(), boss.getHealth() - boss.getMaxHealth() * (float) LANDING_SHARE);
        begin(player, action, true);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Roc damage skips vanilla's hurt cooldown so repeat bolts land in full. Inside its own
     * shorter window a hit only adds its excess over the largest recent one, so hazards never stack.
     */
    private boolean hurt(ServerPlayerEntity p, float raw) {
        if (!boss.eligible(p)) return false;
        long now = now();
        Recent prior = recent.get(p.getUuid());
        boolean fresh = prior == null || now - prior.at >= STACK_TICKS;
        float amount = fresh ? raw : raw - prior.amount;
        if (amount <= 0) return false;
        var type = world().getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(DAMAGE);
        if (!p.damage(world(), new DamageSource(type, boss), amount)) return false;
        recent.put(p.getUuid(), new Recent(fresh ? now : prior.at, raw));
        return true;
    }

    private ServerWorld world() { return (ServerWorld) boss.getEntityWorld(); }
    private long now() { return boss.getEntityWorld().getTime(); }
    private float phaseHealth() { return boss.getMaxHealth() * (float) PHASE_GATE; }
    private boolean atPhaseGate() { return boss.getHealth() <= phaseHealth() + .01f; }
    private Vec3d center() { return new Vec3d(centre.getX() + .5, StormSummit.SURFACE_Y + 1.03, centre.getZ() + .5); }
    private Vec3d forward() { return Vec3d.fromPolar(0, boss.getYaw()); }
    private ServerPlayerEntity player(UUID id) { return id == null ? null : world().getServer().getPlayerManager().getPlayer(id); }

    private List<ServerPlayerEntity> players() {
        return world().getPlayers(p -> boss.eligible(p) && StormSummit.nearestCentre(p.getEntityPos()).equals(centre));
    }

    private Vec3d perch(int n) {
        int[] p = StormSummitLayout.PERCHES[n];
        return new Vec3d(centre.getX() + p[0] + .5, StormSummit.SURFACE_Y + p[1], centre.getZ() + p[2] + .5);
    }

    private void turnToward(Vec3d point, float rate) {
        Vec3d d = flat(point.subtract(boss.getEntityPos()));
        if (d.lengthSquared() < 1e-4) return;
        float wanted = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        boss.yaw(boss.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(wanted - boss.getYaw()), -rate, rate));
    }

    private void sound(Vec3d pos, SoundEvent sound, float volume, float pitch) {
        world().playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.HOSTILE, volume, pitch);
    }

    private Vec3d onFloor(Vec3d v) { return new Vec3d(v.x, center().y, v.z); }

    private Vec3d clamp(Vec3d v, double radius) {
        Vec3d d = flat(v.subtract(center()));
        return center().add(d.lengthSquared() > radius * radius ? d.normalize().multiply(radius) : d);
    }

    private Vec3d interior(Vec3d point, double margin) {
        Vec3d d = point.subtract(center());
        return clamp(point, Math.max(1, StormSummit.rim(d.x, d.z) - margin));
    }

    private static Vec3d flat(Vec3d v) { return new Vec3d(v.x, 0, v.z); }
    private static double smooth(double f) { return f * f * (3 - 2 * f); }
    private static double horizontal(Vec3d a, Vec3d b) { return flat(a.subtract(b)).length(); }

    private static double distanceToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d d = flat(b.subtract(a));
        double l = d.lengthSquared(), t = l == 0 ? 0 : Math.clamp(p.subtract(a).dotProduct(d) / l, 0, 1);
        return horizontal(p, a.add(d.multiply(t)));
    }

    /** Model space faces -Z, so a target straight ahead at distance d sits at (0, -d). */
    private static Reach reach(String pose, int from, int to, double radius) {
        double min = Double.MAX_VALUE, max = -1;
        for (double d = 0; d <= 60; d += .25) {
            for (int t = from; t <= to; t++) {
                Vec3d bill = StormRocPose.socket(pose, t, "beak");
                if (Math.hypot(bill.x, bill.z + d) > radius) continue;
                min = Math.min(min, d);
                max = Math.max(max, d);
                break;
            }
        }
        return new Reach(min, max);
    }
}
