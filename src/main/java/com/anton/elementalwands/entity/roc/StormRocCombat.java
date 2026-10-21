package com.anton.elementalwands.entity.roc;

import static com.anton.elementalwands.entity.roc.StormRocRules.*;

import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.summit.StormSummit;
import com.anton.elementalwands.summit.StormSummitLayout;
import com.anton.elementalwands.util.SpellCombat;
import com.anton.elementalwands.util.TemporaryBlockManager;
import java.util.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
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
    /** The excess of a stacked hit, already mitigated: it skips armor, enchantments, effects and difficulty. */
    private static final RegistryKey<DamageType> EXCESS = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of("elementalwands", "roc_storm_excess"));
    private static final Identifier SLOW = Identifier.of("elementalwands", "roc_tornado_drag");
    private static final double GOLDEN_ANGLE = 2.3999632297;
    /** Chain-smash circles stay this close to the centre: a full circle and a margin inside the nearest rim. */
    private static final double SMASH_AREA = Arrays.stream(StormSummitLayout.RIM).min().orElse(54) - SMASH_RADIUS - 5;
    private static final List<String> FEET = List.of("left_foot", "right_foot");

    /** Straight-ahead distances from its feet that a head attack's bill can reach. */
    private record Reach(double min, double max) {
        boolean has(double distance) { return distance >= min && distance <= max; }
    }

    private static final class Reaches {
        // Measured from the shipped sockets, so a retimed or reposed clip keeps the choices honest.
        // A one-block margin leaves room for a target drifting through the windup.
        /** The bite closes this far ahead of its feet, standing or after a leap that carries it up to BITE_LUNGE further. */
        static final double BITE_AT = flat(StormRocPose.socket(Action.BITE.pose, Action.BITE.impact, "beak")).length();
        static final double BITE_LEAP_AT = flat(StormRocPose.socket("bite_leap", BITE_LEAP_IMPACT, "beak")).length();
        static final Reach BITE = new Reach(BITE_AT - (BITE_RADIUS - 1), Math.max(BITE_AT, BITE_LEAP_AT) + BITE_LUNGE + BITE_RADIUS - 1);
        static final Reach SWING = reach(Action.SWING_LEFT.pose, Action.SWING_LEFT.impact, Action.SWING_LEFT.impact + SWING_SWEEP, SWING_RADIUS - 1);
        static final double SCREAM_START = flat(StormRocPose.socket(Action.SCREAM.pose, Action.SCREAM.impact, "beak")).length();
    }

    private static final class Strike {
        final RocEffectEntity effect;
        /** Aimed strikes belong to the air stretch; random ones may keep falling after an impact. */
        final boolean aimed;
        int bolts;
        Strike(RocEffectEntity effect, boolean aimed) { this.effect = effect; this.aimed = aimed; }
    }

    private static final class Tornado {
        final RocEffectEntity effect;
        double heading;
        /** After turning off the rim or a neighbour it wanders a moment before it hunts again. */
        long detour;
        Tornado(RocEffectEntity effect, double heading) { this.effect = effect; this.heading = heading; }
    }

    /** angle: where around the funnel the player was caught, which the whirl starts from. */
    private record Caught(Tornado tornado, long at, double angle) {}

    private static final class Ring {
        final RocEffectEntity effect;
        /**
         * Where its front starts, the half-angle of the sector it covers around the effect's yaw
         * (180: all round), what it costs a grounded player, and how hard it throws them away.
         */
        final double start, half, push;
        final float damage;
        final Set<UUID> passed = new HashSet<>();
        Ring(RocEffectEntity effect, double start, double half, float damage, double push) {
            this.effect = effect; this.start = start; this.half = half; this.damage = damage; this.push = push;
        }
    }

    /** The costliest Roc hit a player took in the current stacking window, after mitigation, and when the window opened. */
    private record Recent(long at, float cost) {}

    private record Threat(double amount, long at) {
        double decayed(long time) { return amount * Math.pow(.5, (time - at) / (double) THREAT_HALF_LIFE); }
    }

    private final StormRocEntity boss;
    private final Set<UUID> roster = new LinkedHashSet<>();
    private final Map<UUID, Threat> threat = new HashMap<>();
    private final Map<UUID, Vec3d> previous = new HashMap<>(), motion = new HashMap<>();
    private final Map<UUID, Long> immunity = new HashMap<>(), footHurt = new HashMap<>(), cawAt = new HashMap<>();
    /** Players a tornado has thrown, until they come down: the throw's own hit is their fall. */
    private final Map<UUID, Long> thrown = new HashMap<>();
    private final Map<UUID, Recent> recent = new HashMap<>();
    private final Map<UUID, Caught> caught = new HashMap<>();
    private final Set<UUID> slowed = new HashSet<>();
    private final List<Strike> strikes = new ArrayList<>();
    private final List<Tornado> tornadoes = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<RocEffectEntity> fields = new ArrayList<>();
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
    private float floor, startYaw, landingYaw;
    private int perch, round, selection, sinceMove, smash;
    /** Ticks it has spent in its perched idle, which resumes on each spire so every visit shows more of it. */
    private long idle;
    /** Ticks it has spent in its brawl guard, which resumes after each blow so every pause shows a different beat. */
    private long guard;
    private long smashAt;
    /**
     * The Thunder Charge in progress: the tick its skid began (-1 while it runs), whether it has
     * locked its line and how far it still runs once it has, whether it wants to skid at the next
     * stride boundary, and where and which way the skid slides.
     */
    private int stampedeStop = -1;
    private double stampedeLeft;
    private boolean stampedeLocked, stampedeEnding;
    private Vec3d skidFrom = Vec3d.ZERO, skidHeading = Vec3d.ZERO;
    private boolean review, phasePending, two, defeated, marked;
    private RocEffectEntity marker;
    /** The storm's presence at the summit's centre, which every challenger's client tracks. */
    private RocEffectEntity storm;

    StormRocCombat(StormRocEntity boss) { this.boss = boss; }

    Stage stage() { return stage; }
    boolean phaseTwo() { return two; }
    boolean defeated() { return defeated; }
    boolean active() { return stage != Stage.IDLE && stage != Stage.COMPLETE; }
    boolean participant(UUID id) { return roster.contains(id); }
    void removeParticipant(UUID id) { roster.remove(id); caught.remove(id); thrown.remove(id); immunity.remove(id); clearSlow(id); }
    float healthFloor() { return Math.max(phaseHealth(), floor); }

    String status() {
        return "Storm Roc phase " + (two ? 2 : 1) + ": " + stage.name().toLowerCase(Locale.ROOT) + (action == null ? "" : " / " + action.name().toLowerCase(Locale.ROOT))
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
        two = false;
        defeated = false;
        boss.charged(false);
        round = 0;
        perch = 0;
        selection = 0;
        sinceMove = 0;
        last = null;
        boss.setPosition(perch(0));
        boss.face(center());
        air(false);
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
        if (storm != null) storm.discard();
        storm = null;
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
        fields.clear();
        caught.clear();
        thrown.clear();
        for (UUID id : new HashSet<>(slowed)) clearSlow(id);
    }

    /** The arena goes quiet before the storm: everything but the permanent tornadoes clears. */
    private void calm() {
        for (var s : strikes) s.effect.discard();
        for (var r : rings) r.effect.discard();
        for (var f : fields) f.discard();
        if (marker != null) marker.discard();
        strikes.clear();
        rings.clear();
        fields.clear();
        marker = null;
    }

    void damaged(ServerPlayerEntity player, float amount) {
        long now = now();
        Threat before = threat.get(player.getUuid());
        threat.put(player.getUuid(), new Threat((before == null ? 0 : before.decayed(now)) + amount, now));
        if (horizontal(player.getEntityPos(), boss.getEntityPos()) > RANGED_DISTANCE) {
            rangedBy = player.getUuid();
            rangedAt = now;
        }
        if (!two && atPhaseGate()) phasePending = true;
    }

    void tick(ServerWorld world) {
        if (!active()) return;
        List<ServerPlayerEntity> players = players();
        if (players.isEmpty()) { cancel(); return; }
        // Phase two's last gate is its death, earned by the hit that reached it: it resolves
        // before any hazard this tick could still wipe the challengers.
        if (two && atPhaseGate()) { defeat(); return; }
        trackPlayers(players);
        if (boss.poseName().equals("perched")) idle++;
        if (boss.poseName().equals("hunched")) guard++;
        // Distant cosmetic bolts can be outside entity-ticking chunks; their owner still
        // expires them on schedule instead of letting an unticked flash repeat forever.
        effects.removeIf(e -> {
            if (!e.isRemoved() && e.effectTicks(0) > e.duration()) e.discard();
            return e.isRemoved();
        });
        if (!review && !two && boss.getHealth() <= boss.getMaxHealth() * .75f && tornadoes.size() < 2) tornado(center().add(19, 0, -12));
        tickStrikes(players);
        tickFields(players);
        tickTornadoes(players);
        tickRings(players);
        caw(players);
        if (stage == Stage.STUCK || stage == Stage.BRAWL) groundSpells();
        // Phase two's sky keeps striking at random wherever the Roc is; aimed strikes stay in the air.
        if (two && !review && (stage == Stage.STUCK || stage == Stage.BRAWL || stage == Stage.TAKEOFF || stage == Stage.SMASH && smash > 0)) sky();
        // Phase one's gate is the last landing's floor, so the brawl's own check takes off there:
        // chipping it from the air never skips that landing.
        long t = now() - since;
        switch (stage) {
            case AIR -> tickAir(players, t);
            case DIVE -> tickDive(players, t);
            case SMASH -> tickSmash(players, t);
            case STUCK -> {
                stomps(players, true);
                if (t >= STUCK_TICKS) {
                    enter(Stage.BRAWL, "hunched");
                    nextAction = now() + 12;
                }
            }
            case BRAWL -> {
                if (boss.getHealth() <= floor + .01f) { takeoff(); break; }
                if (action != null) tickAction(players);
                else if (now() >= nextAction && !review) chooseAction(players);
                stomps(players, false);
            }
            case TAKEOFF -> tickTakeoff(players, t);
            case CHARGE -> tickCharge(t);
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
        if (pose.equals("hunched")) guard();
        else boss.pose(pose);
        Arrays.fill(lastFeet, null);
    }

    /** landing: it arrives on its spire from a flight, rather than starting the fight there. */
    private void air(boolean landing) {
        enter(Stage.AIR, "land");
        if (!landing) perched();
        nextSmall = now() + 25;
        nextLarge = now() + LARGE_EVERY;
        nextRandom = now() + 35;
        nextDistant = now() + 100;
    }

    private int airDuration() {
        Difficulty difficulty = world().getDifficulty();
        if (two) return difficulty == Difficulty.HARD ? AIR_TICKS_TWO_HARD : difficulty == Difficulty.EASY ? AIR_TICKS_TWO_EASY : AIR_TICKS_TWO;
        return difficulty == Difficulty.HARD ? AIR_TICKS_HARD : difficulty == Difficulty.EASY ? AIR_TICKS_EASY : AIR_TICKS;
    }

    /** On each spire it lands, rears up and calls a large volley down on everyone, then flies on. */
    private void tickAir(List<ServerPlayerEntity> players, long t) {
        storm(players);
        int cycle = (int) (t % AIR_CYCLE);
        if (cycle == FLY_AT) {
            from = boss.getEntityPos();
            perch = (perch + 1) % StormSummitLayout.PERCHES.length;
            to = perch(perch);
            boss.pose("flight");
        }
        if (cycle >= FLY_AT) fly(Math.min(1, (cycle - FLY_AT) / (double) (AIR_CYCLE - FLY_AT - 1)));
        if (cycle == 0 && t > 0) {
            boss.setPosition(perch(perch));
            boss.face(center());
            boss.pose("land");
        }
        if (cycle == LAND_TICKS && t > 0) perched();
        if (cycle == CALL_AT) {
            boss.pose("call");
            sound(boss.getEntityPos(), SoundEvents.ENTITY_PHANTOM_AMBIENT, 4, .5f);
        }
        if (cycle == CALL_AT + CALL_VOLLEY) call(players);
        if (cycle == CALL_AT + CALL_TICKS) perched();
        // It dives only from its spire, once its call has landed: never mid-call or mid-flight.
        boolean calling = cycle >= CALL_AT && cycle < CALL_AT + CALL_TICKS;
        if (t >= airDuration() && !calling && cycle < FLY_AT) {
            if (two) chain();
            else dive(chooseLanding());
        }
    }

    private void perched() { boss.pose("perched", now() - idle); }

    private void guard() { boss.pose("hunched", now() - guard); }

    /** It calls the lightning: a bolt from its raised bill into the clouds, and a large strike at everyone. */
    private void call(List<ServerPlayerEntity> players) {
        effect(RocEffectEntity.Kind.CALL_BOLT, boss.socket("beak"), 0, 0, 12);
        thunder(boss.getEntityPos(), .5f, 1.1f);
        aimed(players, true);
        nextLarge = now() + LARGE_EVERY;
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
        phasePending |= !two && atPhaseGate();
        from = boss.getEntityPos();
        perch = (perch + 1) % StormSummitLayout.PERCHES.length;
        to = perch(perch);
        enter(Stage.TAKEOFF, "takeoff");
    }

    private void tickTakeoff(List<ServerPlayerEntity> players, long t) {
        // The huge wing spread blasts everyone away without damage, then it flies up.
        if (t == 24) for (var p : players)
            if (horizontal(p.getEntityPos(), boss.getEntityPos()) < 24) pushSafe(p, p.getEntityPos().subtract(boss.getEntityPos()), 1.8, .38);
        if (t >= 24) fly(Math.clamp((t - 24) / 26.0, 0, 1));
        if (t < TAKEOFF_TICKS) return;
        if (!two && (phasePending || atPhaseGate())) charge();
        else {
            floor = Math.max(phaseHealth(), floor - boss.getMaxHealth() * (float) LANDING_SHARE);
            round++;
            air(true);
        }
    }

    // ------------------------------------------------------------------ phase change and defeat

    /**
     * A stand-in for the phase-change cinematic. It climbs high above the summit and the arena
     * goes quiet, the storm rolls in, a huge bolt charges it, then it lands on the tallest spire.
     */
    private void charge() {
        calm();
        phasePending = false;
        review = false;
        from = boss.getEntityPos();
        enter(Stage.CHARGE, "flight");
        sound(from, SoundEvents.ENTITY_PHANTOM_AMBIENT, 4, .4f);
    }

    private void tickCharge(long t) {
        // Well below the storm's scud, where the whole summit can watch the bolt come down into it.
        Vec3d high = center().add(0, StormSummit.STORM_CEILING - StormSummit.SURFACE_Y - 40, 0);
        if (t <= 50) {
            boss.setPosition(from.lerp(high, smooth(t / 50.0)));
            if (horizontal(boss.getEntityPos(), high) > 2) turnToward(high, FLIGHT_TURN_RATE);
        } else if (t < 120) {
            boss.setPosition(high.add(0, Math.sin((t - 50) * .09) * 1.2, 0));
        }
        // The client darkens the sky and rolls in the storm clouds once it is charged.
        if (t == 40) {
            boss.charged(true);
            if (storm == null || storm.isRemoved()) storm = RocEffectEntity.spawn(boss, RocEffectEntity.Kind.STORM, center(), 0, 0, Integer.MAX_VALUE);
        }
        if (t == 40 || t == 64) thunder(high, 1.2f, .55f);
        if (t == 90) {
            effect(RocEffectEntity.Kind.CHARGE_BOLT, boss.getEntityPos(), 3, 0, 26);
            sound(boss.getEntityPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 6, .5f);
            thunder(boss.getEntityPos(), 2, .45f);
        }
        if (t == 120) {
            from = boss.getEntityPos();
            perch = tallestPerch();
            to = perch(perch);
        }
        if (t >= 120) fly(Math.min(1, (t - 120) / (double) (CHARGE_TICKS - 120)));
        if (t >= CHARGE_TICKS) beginPhaseTwo();
    }

    private void beginPhaseTwo() {
        two = true;
        boss.charged(true);
        if (storm == null || storm.isRemoved()) storm = RocEffectEntity.spawn(boss, RocEffectEntity.Kind.STORM, center(), 0, 0, Integer.MAX_VALUE);
        floor = boss.getMaxHealth() * (float) (PHASE_GATE - LANDING_SHARE);
        round++;
        // The tornadoes swell, and a third joins them.
        for (var t : tornadoes) t.effect.resize(TORNADO_RADIUS_TWO);
        for (Vec3d seed : List.of(new Vec3d(-19, 0, 10), new Vec3d(19, 0, -12), new Vec3d(4, 0, 26)))
            if (tornadoes.size() < TORNADOES_TWO) tornado(center().add(seed));
        boss.setPosition(perch(perch));
        boss.face(center());
        air(true);
    }

    /** The death and the shard reward are still to come; the encounter ends here for now. */
    private void defeat() {
        Vec3d at = boss.getEntityPos();
        clearHazards();
        bar.clearPlayers();
        if (storm != null) storm.discard();
        storm = null;
        action = null;
        defeated = true;
        stage = Stage.COMPLETE;
        thunder(at, 2, .5f);
        sound(at, SoundEvents.ENTITY_PHANTOM_DEATH, 4, .35f);
    }

    /** Operators can skip straight to the phase change for playtests. */
    boolean skipToPhaseTwo() {
        if (!active() || two || stage == Stage.CHARGE) return false;
        boss.setHealth(phaseHealth());
        action = null;
        charge();
        return true;
    }

    private int tallestPerch() {
        int best = 0;
        for (int i = 1; i < StormSummitLayout.PERCHES.length; i++)
            if (StormSummitLayout.PERCHES[i][1] > StormSummitLayout.PERCHES[best][1]) best = i;
        return best;
    }

    // ------------------------------------------------------------------ lightning

    // Markers persist through every full-strength repeat; nothing authorizes a hit client-side.
    private void storm(List<ServerPlayerEntity> players) {
        if (now() >= nextSmall) { aimed(players, false); nextSmall = now() + SMALL_EVERY; }
        if (stage != Stage.AIR && now() >= nextLarge) { aimed(players, true); nextLarge = now() + LARGE_EVERY; }
        sky();
    }

    /** Random strikes across the summit, and the harmless distant bolts. */
    private void sky() {
        if (now() >= nextRandom) {
            double a = boss.getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(boss.getRandom().nextDouble()) * RANDOM_RADIUS;
            strike(center().add(Math.cos(a) * r, 0, Math.sin(a) * r), boss.getRandom().nextInt(4) == 0, false);
            nextRandom = now() + (two ? RANDOM_EVERY_TWO : RANDOM_EVERY);
        }
        if (now() >= nextDistant) {
            double a = boss.getRandom().nextDouble() * Math.PI * 2;
            if (two) {
                // Phase two's bolts fall from the storm ceiling into the cloud sea, with thunder.
                double r = 75 + boss.getRandom().nextDouble() * 90;
                Vec3d at = center().add(Math.cos(a) * r, StormSummitLayout.CLOUD_TOP - 1, Math.sin(a) * r);
                effect(RocEffectEntity.Kind.STORM_BOLT, at, StormSummit.STORM_CEILING - at.y, 0, 8);
                thunder(at, 1, .7f + boss.getRandom().nextFloat() * .3f);
            } else {
                // Phase one's fall from the cap cloud, out past the rim into the cloud sea below.
                Vec3d at = center().add(Math.cos(a) * (70 + boss.getRandom().nextDouble() * 35), -30, Math.sin(a) * (70 + boss.getRandom().nextDouble() * 35));
                effect(RocEffectEntity.Kind.DISTANT_BOLT, at, StormSummit.CAP_Y + 2 - at.y, 0, 6);
            }
            nextDistant = now() + (two ? DISTANT_EVERY_TWO : DISTANT_EVERY);
        }
    }

    /** Thunder carries a couple of hundred blocks, not into the other summits' fights. */
    private void thunder(Vec3d at, float loudness, float pitch) {
        world().playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 14 * loudness, pitch);
    }

    private void aimed(List<ServerPlayerEntity> players, boolean large) {
        int warning = large ? LARGE_WARNING : SMALL_WARNING;
        for (int i = 0; i < Math.min(AIMED_CAP, players.size()); i++) {
            var p = players.get(Math.floorMod(selection++, players.size()));
            if (i == 0) boss.lookAt(p);
            Vec3d spot = p.getEntityPos();
            // Half land where the player is running, so a straight line doesn't dodge them.
            if ((selection + round) % 2 == 0) spot = spot.add(motion.getOrDefault(p.getUuid(), Vec3d.ZERO).multiply(warning * .7));
            strike(spot, large, true);
        }
    }

    RocEffectEntity strike(Vec3d point, boolean large) { return strike(point, large, true); }

    private RocEffectEntity strike(Vec3d point, boolean large, boolean aimed) {
        if (!active()) return null;
        double radius = large ? (two ? LARGE_RADIUS_TWO : LARGE_RADIUS) : (two ? SMALL_RADIUS_TWO : SMALL_RADIUS);
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
            strikes.add(new Strike(e, aimed));
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
            float damage = large ? (two ? LARGE_DAMAGE_TWO : LARGE_DAMAGE) : (two ? SMALL_DAMAGE_TWO : SMALL_DAMAGE);
            for (var p : players)
                if (horizontal(p.getEntityPos(), e.getEntityPos()) <= e.radius() + p.getWidth() * .5
                        && p.getY() >= center().y - 4 && p.getY() < center().y + 110)
                    hurt(p, damage);
            // In phase two a large strike's last bolt leaves its circle live for a while.
            if (two && large && s.bolts == LARGE_BOLTS) fields.add(effect(RocEffectEntity.Kind.LIVE_GROUND, e.getEntityPos(), e.radius(), 0, LIVE_TICKS));
        }
    }

    /** Live ground hurts anyone standing on it; jumping over the pulses avoids them. */
    private void tickFields(List<ServerPlayerEntity> players) {
        for (var it = fields.iterator(); it.hasNext(); ) {
            var field = it.next();
            if (field.isRemoved()) { it.remove(); continue; }
            int age = (int) field.effectTicks(0);
            if (age <= 0 || age > LIVE_TICKS || age % LIVE_EVERY != 0) continue;
            for (var p : players)
                if (horizontal(p.getEntityPos(), field.getEntityPos()) <= field.radius() + p.getWidth() * .5
                        && grounded(p, field.getY()) && p.getY() < field.getY() + 6)
                    hurt(p, LIVE_DAMAGE);
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
        for (int i = 0; i < 64 && nearTornado(spot, tornadoSpacing()); i++) {
            double a = i * GOLDEN_ANGLE;
            spot = center().add(Math.cos(a) * 27, 0, Math.sin(a) * 27);
        }
        var e = effect(RocEffectEntity.Kind.TORNADO, spot, tornadoRadius(), 0, Integer.MAX_VALUE);
        tornadoes.add(new Tornado(e, boss.getRandom().nextDouble() * Math.PI * 2));
        return e;
    }

    private double tornadoRadius() { return two ? TORNADO_RADIUS_TWO : TORNADO_RADIUS; }

    /** Tornadoes keep this far apart, so their winds never merge. */
    private double tornadoSpacing() { return tornadoRadius() * 2 + 5; }

    private boolean nearTornado(Vec3d spot, double distance) {
        for (var t : tornadoes) if (horizontal(spot, t.effect.getEntityPos()) < distance) return true;
        return false;
    }

    private void tickTornadoes(List<ServerPlayerEntity> players) {
        Set<UUID> inside = new HashSet<>();
        for (var t : tornadoes) {
            if (t.effect.isRemoved()) continue;
            drift(t, players);
            Vec3d core = t.effect.getEntityPos();
            double radius = t.effect.radius(), reach = radius * TORNADO_REACH;
            if (now() % 30 == t.effect.getId() % 30) sound(core.add(0, 3, 0), SoundEvents.ENTITY_BREEZE_WHIRL, 2.2f, .55f);
            for (var p : players) {
                double distance = horizontal(p.getEntityPos(), core);
                if (distance > reach || p.getY() > center().y + 20 || caught.containsKey(p.getUuid())) continue;
                boolean immune = now() < immunity.getOrDefault(p.getUuid(), 0L);
                if (distance <= radius) {
                    inside.add(p.getUuid());
                    slow(p, Math.clamp(.1 + .35 * (1 - distance / radius), .1, .45));
                    if (distance < radius * TORNADO_CATCH && !immune) {
                        caught.put(p.getUuid(), new Caught(t, now(), Math.atan2(p.getZ() - core.z, p.getX() - core.x)));
                        sound(p.getEntityPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 1.6f, .6f);
                        continue;
                    }
                    if (now() % TORNADO_CHIP_EVERY == 0) hurt(p, TORNADO_CHIP);
                }
                if (immune) continue;
                // Its winds drag everyone near it inward and around it, harder the closer they are,
                // and much harder in the air, which punishes constant jumping. A walk still escapes
                // the edge of them; deep inside, only a sprint does.
                Vec3d inward = distance < 1e-3 ? Vec3d.ZERO : flat(core.subtract(p.getEntityPos())).multiply(1 / distance);
                double close = 1 - distance / reach;
                double pull = p.isOnGround() ? .018 + .05 * close : .065, swirl = p.isOnGround() ? .03 * close : .04;
                p.addVelocity(inward.x * pull + inward.z * swirl, 0, inward.z * pull - inward.x * swirl);
                p.velocityModified = true;
            }
        }
        for (UUID id : new HashSet<>(slowed)) if (!inside.contains(id)) clearSlow(id);
        tickCaught();
        // Thrown players come down without a fall: the throw's own hit already counted it.
        thrown.entrySet().removeIf(e -> {
            var p = player(e.getKey());
            if (p == null || !boss.eligible(p)) return true;
            p.fallDistance = 0;
            return now() >= e.getValue();
        });
    }

    /**
     * A quarter of a walk. In the air stretch it turns slowly toward the nearest challenger on the
     * rock; otherwise, or after turning off the rim or a neighbour, it wanders.
     */
    private void drift(Tornado t, List<ServerPlayerEntity> players) {
        Vec3d at = t.effect.getEntityPos();
        boolean hunting = stage == Stage.AIR || stage == Stage.DIVE || stage == Stage.SMASH;
        ServerPlayerEntity prey = null;
        double nearest = Double.MAX_VALUE;
        if (hunting && now() >= t.detour)
            for (var p : players) {
                if (caught.containsKey(p.getUuid()) || p.getY() > center().y + 6) continue;
                double d = horizontal(p.getEntityPos(), at);
                if (d < nearest) { nearest = d; prey = p; }
            }
        if (prey != null && nearest > 1) {
            double wanted = Math.atan2(prey.getZ() - at.z, prey.getX() - at.x);
            t.heading += MathHelper.clamp(MathHelper.wrapDegrees(Math.toDegrees(wanted - t.heading)), -1.2f, 1.2f) * Math.PI / 180;
        } else t.heading += Math.sin(now() * .03 + t.effect.getId()) * .012;
        Vec3d next = at.add(Math.cos(t.heading) * TORNADO_SPEED, 0, Math.sin(t.heading) * TORNADO_SPEED);
        boolean blocked = horizontal(next, center()) > SAFE_RADIUS;
        // Only moves that close in on a neighbour are blocked, so tornadoes that swelled into
        // each other's space at the phase change drift apart instead of freezing.
        for (var other : tornadoes) {
            if (other == t) continue;
            double after = horizontal(next, other.effect.getEntityPos());
            if (after < tornadoSpacing() - 1 && after < horizontal(t.effect.getEntityPos(), other.effect.getEntityPos())) blocked = true;
        }
        if (blocked) {
            t.heading += 1.1;
            t.detour = now() + 30;
        } else t.effect.setPosition(next);
    }

    private void tickCaught() {
        for (var it = caught.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            var p = player(entry.getKey());
            var hold = entry.getValue();
            Vec3d core = hold.tornado.effect.getEntityPos();
            if (p == null || !boss.eligible(p) || horizontal(p.getEntityPos(), core) > hold.tornado.effect.radius() * 1.5) {
                immunity.put(entry.getKey(), now() + TORNADO_IMMUNITY);
                it.remove();
                continue;
            }
            long age = now() - hold.at;
            if (age >= TORNADO_HOLD) {
                // Leave the map first: a lethal throw eliminates the player, which edits it.
                it.remove();
                immunity.put(entry.getKey(), now() + TORNADO_IMMUNITY);
                // Flung out of the top of the whirl, always toward the middle of the summit.
                hurt(p, TORNADO_DAMAGE);
                if (boss.eligible(p)) {
                    pushSafe(p, center().subtract(p.getEntityPos()), 2.2, .55);
                    thrown.put(entry.getKey(), now() + 60);
                }
                sound(p.getEntityPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST.value(), 2, .8f);
                continue;
            }
            // Whirled up the funnel ever faster, widening with it as it climbs.
            double k = hold.tornado.effect.radius() / TORNADO_RADIUS, f = Math.min(1, age / (TORNADO_HOLD * .7));
            double lift = TORNADO_LIFT * k * smooth(f), angle = hold.angle + age * (.25 + .2 * f), r = (1 + .2 * k) * (1 + lift * .08);
            Vec3d orbit = core.add(Math.cos(angle) * r, .5 + lift, Math.sin(angle) * r);
            Vec3d v = orbit.subtract(p.getEntityPos()).multiply(.5);
            if (v.lengthSquared() > 2.25) v = v.normalize().multiply(1.5);
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

    /**
     * A player who flies up to a spire is met with a caw that hurts and throws them toward the
     * summit, whatever the Roc is doing, so a spire is never a safe platform to shoot from.
     */
    private void caw(List<ServerPlayerEntity> players) {
        if (stage == Stage.CHARGE) return;
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
        rings.add(new Ring(e, 0, 180, RING_DAMAGE, 0));
    }

    /** The Storm Sweep's crackle wave, rolling out across the front it swept. */
    private void sweepWave() {
        var e = effect(RocEffectEntity.Kind.SWEEP_WAVE, onFloor(boss.getEntityPos()), SWEEP_WAVE_REACH, 0,
                (int) Math.ceil((SWEEP_WAVE_REACH - SWEEP_WAVE_START) / SHOCK_SPEED) + 3);
        e.setYaw(boss.getYaw());
        rings.add(new Ring(e, SWEEP_WAVE_START, SWEEP_WAVE_HALF, RING_DAMAGE, 0));
    }

    private void tickRings(List<ServerPlayerEntity> players) {
        for (var it = rings.iterator(); it.hasNext(); ) {
            var ring = it.next();
            if (ring.effect.isRemoved()) { it.remove(); continue; }
            double radius = Math.min(ring.effect.radius(), ring.start + ring.effect.effectTicks(0) * SHOCK_SPEED), inner = Math.max(0, radius - SHOCK_SPEED);
            Vec3d ahead = Vec3d.fromPolar(0, ring.effect.getYaw());
            // It stops where it is drawn to stop; its last ticks only fade.
            if (inner >= ring.effect.radius()) continue;
            double floorY = ring.effect.getY();
            for (var p : players) {
                if (ring.passed.contains(p.getUuid())) continue;
                double distance = horizontal(p.getEntityPos(), ring.effect.getEntityPos());
                if (distance + p.getWidth() * .5 < inner - .6 || distance - p.getWidth() * .5 > radius + .6) continue;
                Vec3d off = flat(p.getEntityPos().subtract(ring.effect.getEntityPos()));
                if (ring.half < 180 && off.lengthSquared() > 1e-6
                        && Math.toDegrees(Math.acos(MathHelper.clamp(off.normalize().dotProduct(ahead), -1, 1))) > ring.half) continue;
                // One contact decides, so a player who jumped it is not hit by its trailing smoke.
                ring.passed.add(p.getUuid());
                if (grounded(p, floorY) && hurt(p, ring.damage) && ring.push > 0)
                    pushSafe(p, p.getEntityPos().subtract(ring.effect.getEntityPos()), ring.push, .3);
            }
        }
    }

    /** Rings and live ground roll over low rocks: standing on one doesn't clear them, only being in the air does. */
    private static boolean grounded(ServerPlayerEntity p, double floorY) {
        return (p.isOnGround() || p.getY() < floorY + SHOCK_HEIGHT) && p.getY() + p.getHeight() > floorY - .4;
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
            if (nearest > DIVE_RADIUS + tornadoRadius() + 2) return point;
            if (nearest > bestClear) { best = point; bestClear = nearest; }
        }
        // Two spaced tornadoes always leave a clear patch; otherwise take the clearest point found.
        return best;
    }

    void dive(Vec3d point) {
        mark = clamp(point, 24);
        if (nearTornado(mark, DIVE_RADIUS + tornadoRadius() + 2)) mark = chooseLanding();
        from = boss.getEntityPos();
        boss.face(mark);
        enter(Stage.DIVE, "dive");
        to = landing(mark, boss.getYaw());
        marker = effect(RocEffectEntity.Kind.DIVE_MARKER, mark, DIVE_RADIUS, DIVE_TICKS, DIVE_TICKS + 2);
        sound(mark, SoundEvents.ENTITY_PHANTOM_AMBIENT, 3, .45f);
    }

    /** Where its feet go so that the pinned bill, not the body, lands on the mark. */
    private Vec3d landing(Vec3d mark, float yaw) {
        Vec3d bill = StormRocPose.socket("stuck", 0, "beak");
        double a = Math.toRadians(yaw), c = Math.cos(a), s = Math.sin(a);
        return new Vec3d(mark.x - (bill.x * c + bill.z * s), center().y, mark.z - (bill.x * s - bill.z * c));
    }

    // ------------------------------------------------------------------ chain smash

    /** Phase two's heavy attack: beak slams in quick succession, each turned on a player, never far from the last. */
    void chain() {
        from = boss.getEntityPos();
        smash = 0;
        marked = false;
        marker = null;
        enter(Stage.SMASH, "dive");
        sound(from, SoundEvents.ENTITY_PHANTOM_AMBIENT, 3, .4f);
    }

    private void tickSmash(List<ServerPlayerEntity> players, long t) {
        if (smash == 0) {
            // It launches straight up, picks its first player, then falls on them like the dive.
            if (!review) storm(players);
            int launch = DIVE_TICKS - FIRST_SMASH_WARNING;
            if (t < launch) {
                boss.setPosition(from.add(0, 14 * smooth(t / (double) launch), 0));
                turnToward(center(), FLIGHT_TURN_RATE);
                return;
            }
            if (!marked) {
                from = boss.getEntityPos();
                markSmash(players, null);
            }
            double f = Math.clamp((t - launch) / (double) FIRST_SMASH_WARNING, 0, 1);
            Vec3d apex = mark.add(0, 42, 0);
            boss.setPosition(f < .5 ? from.lerp(apex, smooth(f / .5)) : apex.lerp(to, smooth((f - .5) / .5)));
            boss.yaw(MathHelper.lerpAngleDegrees((float) smooth(Math.min(1, f * 2.5)), startYaw, landingYaw));
            if (f >= 1) slam(players, DIVE_DAMAGE);
            return;
        }
        // Buried for a moment, then it rips free and hops onto the next player's circle.
        long u = now() - smashAt;
        if (u < SMASH_PAUSE) return;
        if (!marked) {
            from = boss.getEntityPos();
            markSmash(players, mark);
            boss.pose("hop");
        }
        double f = Math.clamp((u - SMASH_PAUSE) / (double) SMASH_WARNING, 0, 1);
        boss.setPosition(from.lerp(to, smooth(f)).add(0, Math.sin(f * Math.PI) * (5 + horizontal(from, to) * .3), 0));
        boss.yaw(MathHelper.lerpAngleDegrees((float) smooth(Math.min(1, f * 1.6)), startYaw, landingYaw));
        if (f >= 1) slam(players, HOP_DAMAGE);
    }

    /** Each smash turns to the next player in turn, and its circle stays put once marked. */
    private void markSmash(List<ServerPlayerEntity> players, Vec3d previous) {
        var p = players.get(Math.floorMod(selection++, players.size()));
        boss.lookAt(p);
        mark = smashSpot(p.getEntityPos(), previous, smash == SMASHES - 1);
        // It turns to face its circle from where it stands. The bill is about 13 blocks ahead of its
        // feet, and facing the mark keeps the body's own hop no longer than the circle's, so it
        // never seems to teleport. Only a landing too near the rim turns it a little further.
        Vec3d body = boss.getEntityPos(), approach = flat(mark.subtract(body));
        float facing = approach.lengthSquared() < 1e-4 ? boss.getYaw() : (float) Math.toDegrees(Math.atan2(-approach.x, approach.z));
        landingYaw = facing;
        to = landing(mark, facing);
        for (int k = 1; k <= 18 && StormSummit.beyondRim(centre, to) > -6; k++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                Vec3d at = landing(mark, facing + sign * k * 10);
                if (StormSummit.beyondRim(centre, at) > -6 || previous != null && horizontal(at, body) > SMASH_HOP) continue;
                landingYaw = facing + sign * k * 10;
                to = at;
                break;
            }
        }
        startYaw = boss.getYaw();
        marked = true;
        int warning = previous == null ? FIRST_SMASH_WARNING : SMASH_WARNING;
        marker = effect(RocEffectEntity.Kind.DIVE_MARKER, mark, SMASH_RADIUS, warning, warning + 2);
        sound(mark, SoundEvents.ENTITY_PHANTOM_AMBIENT, 2.5f, .5f);
    }

    /** At a player if it can, but inside the summit, off the tornadoes and within a hop of the last circle. */
    private Vec3d smashSpot(Vec3d at, Vec3d previous, boolean last) {
        Vec3d wanted = reachable(at, previous, last), best = wanted;
        double clear = SMASH_RADIUS + tornadoRadius() + 1, most = room(wanted);
        // Outward from the player's spot across everything it can reach: the nearest clear spot,
        // or failing that the one with the most room, as the dive does.
        for (int i = 1; i < 260 && most < clear; i++) {
            double a = i * GOLDEN_ANGLE, r = 2.4 * Math.sqrt(i);
            Vec3d spot = reachable(wanted.add(Math.cos(a) * r, 0, Math.sin(a) * r), previous, last);
            double room = room(spot);
            if (room > most) { best = spot; most = room; }
        }
        return best;
    }

    /** How far a spot is from the nearest tornado. */
    private double room(Vec3d spot) {
        double nearest = Double.MAX_VALUE;
        for (var t : tornadoes) nearest = Math.min(nearest, horizontal(spot, t.effect.getEntityPos()));
        return nearest;
    }

    /**
     * The nearest point to a player that the chain can smash: within the summit's smashing area,
     * near enough the middle that the rest of the chain can still reach the last circle's spot,
     * and within a hop of the last circle, so it never seems to teleport. The last circle lands
     * where neither the wing wave nor the takeoff gust can push anyone off.
     */
    private Vec3d reachable(Vec3d point, Vec3d previous, boolean last) {
        double area = last ? 24 : Math.min(SMASH_AREA, 24 + (SMASHES - 1 - smash) * SMASH_HOP - 1);
        Vec3d p = onFloor(point);
        return previous == null ? clamp(p, area) : onFloor(lens(p, center(), area, previous, SMASH_HOP));
    }

    /** The nearest point to p inside both circles: around a with radius ra, and around b with rb. */
    private static Vec3d lens(Vec3d p, Vec3d a, double ra, Vec3d b, double rb) {
        Vec3d pa = clampTo(p, a, ra);
        if (horizontal(pa, b) <= rb + 1e-6) return pa;
        Vec3d pb = clampTo(p, b, rb);
        double d = horizontal(a, b);
        if (horizontal(pb, a) <= ra + 1e-6 || d > ra + rb || d < 1e-6) return pb;
        // Otherwise the nearest point is one of the two corners where the circles cross.
        double x = (d * d + ra * ra - rb * rb) / (2 * d), h = Math.sqrt(Math.max(0, ra * ra - x * x));
        Vec3d u = flat(b.subtract(a)).multiply(1 / d), n = new Vec3d(-u.z, 0, u.x), mid = a.add(u.multiply(x));
        Vec3d c1 = mid.add(n.multiply(h)), c2 = mid.subtract(n.multiply(h));
        return horizontal(p, c1) <= horizontal(p, c2) ? c1 : c2;
    }

    private static Vec3d clampTo(Vec3d p, Vec3d centre, double radius) {
        Vec3d d = flat(p.subtract(centre));
        return d.lengthSquared() > radius * radius ? centre.add(d.normalize().multiply(radius)) : centre.add(d);
    }

    private void slam(List<ServerPlayerEntity> players, float damage) {
        boss.setPosition(to);
        boss.yaw(landingYaw);
        if (marker != null) marker.discard();
        for (var p : players)
            if (horizontal(p.getEntityPos(), mark) <= SMASH_RADIUS + p.getWidth() * .5 && p.getY() < mark.y + 26) hurt(p, damage);
        ring(mark, SMASH_RING);
        sound(mark, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 3, .55f);
        if (smash == 0) {
            // The air stretch's aimed strikes stop at the first impact; the random sky keeps going.
            for (var s : strikes) if (s.aimed) s.effect.discard();
            strikes.removeIf(s -> s.aimed);
        }
        smash++;
        smashAt = now();
        marked = false;
        if (smash >= SMASHES) enter(Stage.STUCK, "stuck");
        else boss.pose("stuck");
    }

    // ------------------------------------------------------------------ on the ground

    /**
     * Authored/no-clip movement skips vanilla stepping callbacks. Preserve owned ground
     * spell contact explicitly at the actual feet, without allowing ambient world damage.
     */
    private void groundSpells() {
        if (action == Action.POUNCE && now() - actionAt >= POUNCE_LAUNCH && now() - actionAt < action.impact) return;
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
        boolean charging = action == Action.POUNCE && t >= POUNCE_LAUNCH && t <= action.impact + 1 || action == Action.STAMPEDE && t >= action.impact && (stampedeStop < 0 || t <= stampedeStop)
                || action == Action.BITE && t > BITE_LEAP && t <= BITE_LAND + 1
                || action == Action.WING && t > WING_LIFT && t <= action.impact + 1;
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
            if (Reaches.BITE.has(distance)) options.add(Action.BITE);
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
        boss.lookAt(p);
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
            stampedeStop = -1;
            stampedeLocked = stampedeEnding = false;
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
            case BITE -> bite(players, t);
            case SWING_LEFT, SWING_RIGHT -> swing(players, t);
            case SCREAM -> {
                // A deep, rising inhale as it draws breath: a tell apart from the bite, even off-screen.
                if (t == 4) sound(boss.socket("head"), SoundEvents.ENTITY_BREEZE_INHALE, 4, .45f);
                if (t == action.impact) scream(players);
            }
            case WING -> thunderclap(players, t);
            case POUNCE -> pounce(players, t);
            case STAMPEDE -> stampede(players, t);
        }
        // The Thunder Bite's leap takes longer than its standing bite; the Thunder Charge lasts as
        // long as its run.
        int end = action == Action.BITE && boss.poseName().equals("bite_leap") ? BITE_LEAP_DURATION
                : action == Action.STAMPEDE && stampedeStop >= 0 ? stampedeStop + STAMPEDE_AFTER : action.duration;
        if (t >= end) {
            last = action;
            action = null;
            guard();
            int party = Math.min(AIMED_CAP, players.size());
            Difficulty difficulty = world().getDifficulty();
            int pause = 18 - (party - 1) * 2 + (difficulty == Difficulty.EASY ? 8 : difficulty == Difficulty.HARD ? -5 : 0);
            nextAction = now() + Math.max(6, pause);
        }
    }

    // Resolve the swept arc across several frames, once per victim, along the actual bill.
    /**
     * Storm Sweep: its bill scythes its whole front at chest height, throwing everyone within
     * SWING_RADIUS of it far to the side; as it ends, its crackle wave rolls out over the front.
     */
    private void swing(List<ServerPlayerEntity> players, int t) {
        if (t == action.impact + SWING_SWEEP - 1) sweepWave();
        if (t < action.impact || t > action.impact + SWING_SWEEP) return;
        Vec3d bill = boss.socket("beak");
        for (var victim : players) {
            if (actionHits.contains(victim.getUuid()) || horizontal(victim.getEntityPos(), bill) >= SWING_RADIUS) continue;
            if (victim.getY() >= bill.y + 2.5 || victim.getY() + victim.getHeight() <= bill.y - 2.5) continue;
            if (!hurt(victim, SWING_DAMAGE)) continue;
            actionHits.add(victim.getUuid());
            pushSafe(victim, Vec3d.fromPolar(0, boss.getYaw() + (action == Action.SWING_LEFT ? 90 : -90)), SWING_PUSH, .35);
        }
    }

    /**
     * Storm Scream: a straight line ahead from the bill, crackling, too wide to sidestep casually and
     * safe behind it. Everyone in it is hit and shoved back along it.
     */
    private void scream(List<ServerPlayerEntity> players) {
        Vec3d origin = boss.socket("beak"), forward = forward();
        var e = effect(RocEffectEntity.Kind.SCREAM, new Vec3d(origin.x, center().y + 1, origin.z), SCREAM_HALF_WIDTH, 0, 14);
        e.setYaw(boss.getYaw());
        for (var victim : players) {
            Vec3d d = victim.getEntityPos().subtract(origin);
            double along = d.dotProduct(forward), side = Math.abs(d.x * forward.z - d.z * forward.x);
            // The blast is drawn as a tube on the ground, and it can't be jumped, but it passes under
            // anyone lifted well above it.
            if (along >= -1 && along <= 110 && side <= SCREAM_HALF_WIDTH + victim.getWidth() * .5 && victim.getY() < e.getY() + SCREAM_HALF_WIDTH + .5
                    && hurt(victim, SCREAM_DAMAGE)) pushSafe(victim, forward, SCREAM_PUSH, .25);
        }
        sound(origin, SoundEvents.ENTITY_PHANTOM_DEATH, 3, .4f);
    }

    /**
     * Thunderclap's leap: its legs and one huge downbeat throw it off the rock (a heavy whoosh and
     * dust blown out over the rock, tells for players close under it); it rises fast and slows to
     * its apex, hangs there a moment, then smashes down, gathering speed, and lands on its impact.
     */
    private void thunderclap(List<ServerPlayerEntity> players, int t) {
        if (t == WING_LIFT) {
            from = boss.getEntityPos();
            sound(boss.getEntityPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 4, .5f);
            downwash();
        }
        if (t == WING_APEX + 1) sound(boss.getEntityPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 3, .65f);
        if (t > WING_LIFT && t <= action.impact) {
            double h;
            if (t <= WING_APEX) h = WING_HEIGHT * (1 - Math.pow(1 - (t - WING_LIFT) / (double) (WING_APEX - WING_LIFT), 2));
            else if (t <= WING_DROP) h = WING_HEIGHT + .15 * Math.sin((t - WING_APEX) * .5);
            else h = WING_HEIGHT * (1 - Math.pow((t - WING_DROP) / (double) (action.impact - WING_DROP), 2));
            boss.setPosition(from.add(0, Math.max(0, h), 0));
        }
        if (t == action.impact) wing(players);
    }

    /** Dust blown out over the rock beneath it by a downbeat. */
    private void downwash() {
        Vec3d at = onFloor(from);
        for (int i = 0; i < 36; i++) {
            double a = boss.getRandom().nextDouble() * Math.PI * 2, r = 2 + boss.getRandom().nextDouble() * 7;
            world().spawnParticles(ParticleTypes.CLOUD, at.x + Math.cos(a) * r, at.y + .2, at.z + Math.sin(a) * r, 0,
                    Math.cos(a) * .45, .04, Math.sin(a) * .45, 1);
        }
    }

    /**
     * Thunderclap's landing: anyone under it is crushed, and a ring of crackles and dust rolls out to
     * WING_REACH. Anyone on the ground the ring reaches is hit and thrown away from the Roc; a jump clears it.
     */
    private void wing(List<ServerPlayerEntity> players) {
        hitCircle(players, onFloor(boss.getEntityPos()), WING_CRUSH, LARGE_DAMAGE, 6);
        var e = effect(RocEffectEntity.Kind.CLAP_WAVE, onFloor(boss.getEntityPos()), WING_REACH, 0, (int) Math.ceil(WING_REACH / SHOCK_SPEED) + 3);
        rings.add(new Ring(e, 0, 180, WING_DAMAGE, WING_PUSH));
        sparks(boss.getEntityPos().add(0, 2, 0), 50, 4);
        sound(boss.getEntityPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 3, .8f);
    }

    /**
     * Raptor Strike: a low stalk with its gaze on its prey, a coil once its aim locks, then a flat,
     * fast leap on one huge downbeat, talons first onto where its prey stood. Nothing marks the
     * spot: its locked head and coil are the warning.
     */
    private void pounce(List<ServerPlayerEntity> players, int t) {
        if (t == POUNCE_LAUNCH) {
            boss.face(to);
            sound(boss.getEntityPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 4, .55f);
            from = boss.getEntityPos();
            downwash();
        }
        if (t >= POUNCE_LAUNCH && t <= action.impact) {
            double f = (t - POUNCE_LAUNCH) / (double) (action.impact - POUNCE_LAUNCH);
            // It springs up off the push and glides down onto its prey.
            boss.setPosition(from.lerp(to, f).add(0, Math.sin(Math.PI * Math.pow(f, .7)) * POUNCE_HEIGHT, 0));
        }
        // A swoop as it comes in, and a screech as it flares to strike.
        if (t == POUNCE_LAUNCH + 6) sound(boss.getEntityPos(), SoundEvents.ENTITY_PHANTOM_SWOOP, 4, .5f);
        if (t == action.impact - 3) sound(boss.getEntityPos(), SoundEvents.ENTITY_PHANTOM_AMBIENT, 4, .45f);
        if (t == action.impact) {
            boss.setPosition(to);
            hitCircle(players, to, 5, POUNCE_DAMAGE, 10);
            ring(to, 26);
            sparks(to.add(0, 1, 0), 40, 2.5);
            sound(to, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 2, .65f);
        }
    }

    /**
     * Thunder Charge: three crackling rakes of its talons, then a head-down charge too heavy to
     * stop in time, screeching with its bill gaping and spitting sparks, lightning cracking at
     * every footfall. It curves after its prey until it is close, then holds its line, runs on
     * past where its prey stood and skids on gouging talons, its head whipping round.
     */
    private void stampede(List<ServerPlayerEntity> players, int t) {
        // Its three slow rakes drag its right talons back across the rock in a spray of sparks.
        if (t == 13 || t == 25 || t == 37) {
            sparks(boss.socket("right_foot"), 14, .6);
            sound(boss.socket("right_foot"), SoundEvents.BLOCK_GRINDSTONE_USE, 2.5f, .5f);
        }
        if (t == action.impact) boss.face(to);
        if (t >= action.impact && stampedeStop < 0) {
            Vec3d old = boss.getEntityPos();
            // It curves after its prey until it is close, then holds its line and runs on past.
            var p = player(target);
            if (!stampedeLocked) {
                if (p != null && boss.eligible(p)) aim = p.getEntityPos();
                double close = horizontal(old, aim);
                if (close <= STAMPEDE_COMMIT) {
                    stampedeLocked = true;
                    stampedeLeft = close + STAMPEDE_PAST;
                } else turnToward(aim, STAMPEDE_TURN);
            }
            // It gathers speed over its first strides, then keeps its stride's pace.
            double speed = STAMPEDE_SPEED * Math.min(1, (t - action.impact + 1) / 5.0);
            Vec3d ahead = old.add(forward().multiply(speed)), inside = interior(ahead, 9);
            boss.setPosition(inside);
            if (stampedeLocked) stampedeLeft -= speed;
            if (stampedeLocked && stampedeLeft <= 0 || inside.squaredDistanceTo(ahead) > 1e-6 || t - action.impact >= STAMPEDE_MAX_RUN) stampedeEnding = true;
            // It screeches the whole way, bill gaping and spitting sparks.
            if ((t - action.impact) % 16 == 0) sound(boss.socket("head"), SoundEvents.ENTITY_PHANTOM_AMBIENT, 4, t == action.impact ? .5f : .45f);
            if (t % 2 == 0) sparks(boss.socket("beak"), 6, .5);
            String clip = boss.poseName();
            float k = boss.poseTicks(0);
            for (String side : FEET)
                if (k >= 1 && StormRocPose.socket(clip, k - 1, side).y > .45 && StormRocPose.socket(clip, k, side).y <= .45) {
                    Vec3d foot = boss.socket(side);
                    sparks(foot, 16, .8);
                    sound(foot, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 1.6f, 1.3f);
                }
            for (var victim : players) {
                if (actionHits.contains(victim.getUuid()) || victim.getY() >= center().y + 10) continue;
                if (distanceToSegment(victim.getEntityPos(), old, boss.getEntityPos()) >= STAMPEDE_HALF_WIDTH || !hurt(victim, STAMPEDE_DAMAGE)) continue;
                actionHits.add(victim.getUuid());
                // Tossed aside, off whichever side of its path they stood on.
                double side = flat(victim.getEntityPos().subtract(old)).crossProduct(forward()).y;
                pushSafe(victim, Vec3d.fromPolar(0, boss.getYaw() + (side > 0 ? 90 : -90)), .8, .2);
            }
            // Its clips only hand over on a stride boundary: the run loop starts one stride in,
            // and once it means to stop it skids at the next boundary (the windup's end is one).
            boolean boundary = t >= STAMPEDE_RUN_AT && (t - STAMPEDE_RUN_AT) % STAMPEDE_STRIDE == 0;
            if (stampedeEnding && boundary) {
                stampedeStop = t;
                skidFrom = boss.getEntityPos();
                skidHeading = forward();
                boss.pose("stampede_skid");
            } else if (t == STAMPEDE_RUN_AT) boss.pose("stampede_run");
        }
        if (stampedeStop >= 0 && t > stampedeStop) {
            double f = Math.min(1, (t - stampedeStop) / (double) STAMPEDE_SKID_TICKS);
            boss.setPosition(interior(skidFrom.add(skidHeading.multiply(STAMPEDE_SKID * (1 - (1 - f) * (1 - f)))), 9));
            // Its talons gouge the rock as it skids.
            if (t == stampedeStop + 1) sound(boss.getEntityPos(), SoundEvents.BLOCK_GRINDSTONE_USE, 4, .35f);
            if (f < 1) for (String side : FEET) sparks(boss.socket(side), 4, .5);
            // Its head whips round to the player it missed with a huff, its bill shut.
            if (t == stampedeStop + STAMPEDE_SKID_TICKS + 2) sound(boss.socket("head"), SoundEvents.ENTITY_HORSE_BREATHE, 3, .5f);
        }
    }

    /**
     * Thunder Bite: it rears up slowly with its bill crackling, turning to follow its prey until its
     * aim locks at tick 24. If its prey is then out of reach it switches to its leap: a deep crouch,
     * a push off the rock and a jump of up to BITE_LUNGE that lands it right in front of where its
     * prey stood. Either way it snaps its bill shut in the air there; lightning bursts from the bill:
     * everyone within BITE_RADIUS of it, jumping or not, is hit hard and thrown away from the Roc.
     */
    private void bite(List<ServerPlayerEntity> players, int t) {
        boolean leaping = boss.poseName().equals("bite_leap");
        int impact = leaping ? BITE_LEAP_IMPACT : action.impact;
        if (t < impact && t % 2 == 0) sparks(boss.socket("beak"), 3, .35);
        if (t == action.lock) {
            double distance = horizontal(aim, boss.getEntityPos());
            from = to = boss.getEntityPos();
            if (distance - Reaches.BITE_AT >= BITE_LEAP_MIN) {
                to = interior(from.add(forward().multiply(MathHelper.clamp(distance - Reaches.BITE_LEAP_AT, 0, BITE_LUNGE))), 10);
                boss.pose("bite_leap", actionAt);
            }
        }
        // In the air from push-off to landing, carried in an arc so its feet clear the rock.
        if (t > BITE_LEAP && t <= BITE_LAND && leaping) {
            double f = smooth((t - BITE_LEAP) / (double) (BITE_LAND - BITE_LEAP));
            boss.setPosition(from.lerp(to, f).add(0, Math.sin(f * Math.PI) * Math.min(3.5, 1.5 + from.distanceTo(to) / 4), 0));
        }
        if (t != impact) return;
        Vec3d bill = boss.socket("beak");
        for (var victim : players) {
            if (horizontal(victim.getEntityPos(), bill) >= BITE_RADIUS) continue;
            if (victim.getY() >= bill.y + BITE_RADIUS || victim.getY() + victim.getHeight() <= bill.y - BITE_RADIUS) continue;
            if (hurt(victim, BITE_DAMAGE)) pushSafe(victim, victim.getEntityPos().subtract(boss.getEntityPos()), BITE_PUSH, .35);
        }
        sparks(bill, 40, 2);
        sound(bill, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 3, .7f);
        sound(bill, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.6f);
    }

    private void sparks(Vec3d at, int count, double spread) {
        world().spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, count, spread, spread, spread, .4);
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
     * shorter window a hit only adds its excess over the costliest recent one, so hazards never
     * stack. Costs are compared after armor: a smaller hit first never discounts a larger one.
     */
    private boolean hurt(ServerPlayerEntity p, float raw) {
        if (!boss.eligible(p)) return false;
        long now = now();
        var source = new DamageSource(damageType(DAMAGE), boss);
        float cost = mitigated(p, source, raw);
        Recent prior = recent.get(p.getUuid());
        if (prior == null || now - prior.at >= STACK_TICKS) {
            float before = p.getHealth() + p.getAbsorptionAmount();
            if (!p.damage(world(), source, raw)) return false;
            // A hit that breaks Protection armor costs more than its estimate: count what it took.
            float taken = before - p.getHealth() - p.getAbsorptionAmount();
            recent.put(p.getUuid(), new Recent(now, Math.max(cost, taken)));
            return true;
        }
        float extra = cost - prior.cost;
        if (extra <= 0) return false;
        if (!p.damage(world(), new DamageSource(damageType(EXCESS), boss), extra)) return false;
        recent.put(p.getUuid(), new Recent(prior.at, cost));
        return true;
    }

    /** What a raw hit costs this player, as vanilla works it out: difficulty, armor, Resistance, Protection. */
    private float mitigated(ServerPlayerEntity p, DamageSource source, float raw) {
        float amount = switch (world().getDifficulty()) {
            case PEACEFUL -> 0;
            case EASY -> Math.min(raw / 2 + 1, raw);
            case HARD -> raw * 1.5f;
            default -> raw;
        };
        amount = DamageUtil.getDamageLeft(p, amount, source, p.getArmor(), (float) p.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));
        var resistance = p.getStatusEffect(StatusEffects.RESISTANCE);
        if (resistance != null) amount = Math.max(amount * (25 - (resistance.getAmplifier() + 1) * 5) / 25f, 0);
        float protection = EnchantmentHelper.getProtectionAmount(world(), p, source);
        return protection > 0 ? DamageUtil.getInflictedDamage(amount, protection) : amount;
    }

    private net.minecraft.registry.entry.RegistryEntry<DamageType> damageType(RegistryKey<DamageType> key) {
        return world().getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(key);
    }

    private ServerWorld world() { return (ServerWorld) boss.getEntityWorld(); }
    private long now() { return boss.getEntityWorld().getTime(); }
    /** Phase one ends at half health; phase two's last gate is its defeat, one point above death. */
    private float phaseHealth() { return two ? 1 : boss.getMaxHealth() * (float) PHASE_GATE; }
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
