package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.crypt.HollowCryptRealm;
import com.anton.elementalwands.entity.AstralDoubleEntity;
import com.anton.elementalwands.entity.undead.HollowArcherEntity;
import com.anton.elementalwands.entity.undead.HollowBruteEntity;
import com.anton.elementalwands.entity.undead.HollowCrawlerEntity;
import com.anton.elementalwands.registry.ModEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;
import static com.anton.elementalwands.entity.necromancer.NecromancerRules.*;

/**
 * Server-authoritative encounter: one committed, telegraphed action at a time. The robed caster
 * duels alone, blinking about the clearing, and at 75% and half health retreats out of reach to
 * raise siege waves; after the second siege it crashes down and transforms into the colossus.
 */
final class NecromancerCombat {
    static final RegistryKey<DamageType> GRASP = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "necromancer_grasp"));
    static final RegistryKey<DamageType> DRAIN = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "necromancer_drain"));
    static final RegistryKey<DamageType> BURST = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "necromancer_burst"));
    private static final Set<PositionFlag> KEEP_VIEW = Set.of(PositionFlag.X_ROT, PositionFlag.Y_ROT);

    private record Grasp(Vec3d center, double radius, long detonate) {}
    private record Curse(Vec3d center, long until) {}
    /** Siege steps: the flare before the perch, the waves, then the flare and drop to the ground. */
    private enum Step { RISING, WAVES, FALLING }

    private final NecromancerEntity boss;
    private final ServerBossBar bar = new ServerBossBar(Text.translatable("entity.elementalwands.hollow_necromancer"),
            BossBar.Color.PURPLE, BossBar.Style.PROGRESS);
    private final Map<Action, Long> ready = new EnumMap<>(Action.class);
    private final Map<UUID, Long> lastTargeted = new HashMap<>();
    private final Map<UUID, Long> grabbedAt = new HashMap<>();
    private final List<MobEntity> minions = new ArrayList<>();
    private final List<SoulBoltEntity> bolts = new ArrayList<>();
    private final List<Grasp> grasps = new ArrayList<>();
    private final List<Curse> curses = new ArrayList<>();
    private final NecromancerFightLog log = new NecromancerFightLog();
    private Vec3d home, blinkTo;
    private Action active, last;
    private UUID target, held;
    private long started, nextAction, emptySince = -1, nextMove, pendingSince = -1, nextRelocate, staggerUntil;
    private boolean engaged, reviewing, minionsFrozen;
    private int boltsFired;
    private long rushCaught = -1;
    private boolean handsRush;
    private final List<GraspingHandEntity> handVisuals = new ArrayList<>();
    private float drained, gripDamage;
    private float lockedYaw;
    // Duel: repositioning cadence and the ambush.
    private int castsSinceBlink, shiftEvery = SHIFT_EVERY_MIN;
    private Vec3d ambushTo;
    private float ambushDamage;
    // Siege: perch, waves and the crash that ends it.
    private Step step;
    private boolean siegePending, dropped;
    private Vec3d perch, landing;
    private int lastPerch = -1, wave, spawnGap;
    private long stepAt, exposedUntil, nextSpawn, waveStarted, nextWave = -1, nextSnipe, lastDeflect;
    private final List<Undead> spawnQueue = new ArrayList<>();
    private final Map<UUID, Integer> hovering = new HashMap<>();

    NecromancerCombat(NecromancerEntity boss) { this.boss = boss; }

    static boolean canDamage(NecromancerEntity boss, ServerPlayerEntity player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.getEntityWorld() == boss.getEntityWorld() && !boss.isTeammate(player) && boss.eligible(player);
    }

    void start() {
        cancel();
        home = boss.getEntityPos();
        nextAction = boss.getEntityWorld().getTime() + 20;
        boss.setAiDisabled(false);
    }

    void cancel() {
        log.finish(boss, boss.isAlive() ? "stopped" : "defeated");
        interrupt();
        thawMinions();
        for (MobEntity minion : minions) if (!minion.isRemoved() && minion.getEntityWorld() instanceof ServerWorld world)
            NecromancerMinion.dissolve(minion, world);
        minions.clear(); grasps.clear(); curses.clear();
        handVisuals.forEach(GraspingHandEntity::discard); handVisuals.clear();
        for (SoulBoltEntity bolt : bolts) bolt.discard();
        bolts.clear();
        ready.clear(); lastTargeted.clear(); grabbedAt.clear(); last = null;
        engaged = false; reviewing = false; emptySince = -1; pendingSince = -1; staggerUntil = 0;
        // The stage survives: a resumed fight re-enters its siege from the first wave.
        endSiege();
        siegePending = false; exposedUntil = 0; castsSinceBlink = 0; hovering.clear();
        bar.clearPlayers();
    }

    String status() {
        prune();
        String form = boss.isTransforming() ? "transforming" : boss.isColossus() ? "colossus" : boss.phasePending() ? "robed, transformation pending"
                : "robed, " + boss.stage().name().toLowerCase().replace('_', ' ') + (step == null ? "" : " (" + step.name().toLowerCase()
                + (wave > 0 ? ", wave " + wave : "") + ")");
        return "Hollow Necromancer (" + form + "): " + (active == null ? "idle" : active.name().toLowerCase())
                + ", health " + Math.round(boss.getHealth()) + "/" + Math.round(boss.getMaxHealth())
                + ", minions " + minions.size() + ", " + (boss.isBossAggressive() ? engaged ? "fighting" : "waiting for players" : "passive");
    }

    NecromancerFightLog log() { return log; }
    Vec3d home() { return home; }
    void restoreHome(Vec3d home) { this.home = home; }
    Vec3d anchor() { return home != null ? home : boss.getEntityPos(); }

    void testAction(ServerPlayerEntity player, Action action) {
        home = boss.getEntityPos();
        begin((ServerWorld)boss.getEntityWorld(), action, player, boss.getEntityWorld().getTime());
        reviewing = true;
    }

    /** One-shot rehearsal: raises siege wave {@code number} for the nearby party around the boss, then stays passive. */
    int testWave(ServerPlayerEntity player, int number) {
        ServerWorld world = (ServerWorld)boss.getEntityWorld();
        home = boss.getEntityPos();
        reviewing = true;
        List<ServerPlayerEntity> party = world.getPlayers(p -> canDamage(boss, p) && boss.squaredDistanceTo(p) <= ENCOUNTER_RANGE * ENCOUNTER_RANGE);
        Wave bodies = NecromancerRules.wave(number, Math.max(1, party.size()));
        for (Undead kind : waveBodies(bodies)) spawnWaveBody(world, kind, party);
        return bodies.total();
    }

    // ── Hooks from the entity ────────────────────────────────────────────────

    /** Called with the health actually lost: team damage loosens a grab and interrupts an ambush windup. */
    void damaged(Entity attacker, float lost) {
        if (boss.getEntityWorld() instanceof ServerWorld world) log.bossHit(world.getTime(), attacker, lost);
        if (lost <= 0 || !(boss.getEntityWorld() instanceof ServerWorld world)) return;
        if (active == Action.GRAB && held != null) {
            gripDamage += lost;
            if (gripDamage >= grabEscape(boss.getMaxHealth())) {
                release(world, true);
                stagger(world, world.getTime());
                if (active == Action.GRAB) finish(world.getTime());
            }
        }
        long tick = world.getTime() - started;
        if (active == Action.AMBUSH && tick > AMBUSH_APPEAR && tick < Action.AMBUSH.impact) {
            ambushDamage += lost;
            if (ambushDamage >= grabEscape(boss.getMaxHealth())) {
                log.note(world.getTime(), "ambush interrupted");
                finish(world.getTime());
                stagger(world, world.getTime());
            }
        }
    }

    private void stagger(ServerWorld world, long now) {
        staggerUntil = now + 30;
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SKELETON_HURT, SoundCategory.HOSTILE, 2f, .4f);
    }

    /** Shielded on a siege perch and while dropping from it. */
    boolean immune() { return boss.stage().siege() && step != null; }

    /** A soul ward flashes where the shielded caster stands, at most a few times a second. */
    void deflect(ServerWorld world) {
        long now = world.getTime();
        if (now - lastDeflect < 5) return;
        lastDeflect = now;
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, boss.getX(), boss.getBodyY(.5), boss.getZ(), 14, .5, .8, .5, .04);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ITEM_SHIELD_BLOCK.value(), SoundCategory.HOSTILE, 1.4f, .6f);
    }

    float damageMultiplier(long now) { return now < exposedUntil ? EXPOSED_MULTIPLIER : 1; }

    /** The current duel's health gate was reached: the siege starts on the next tick. */
    void gateReached() { if (!boss.isColossus()) siegePending = true; }

    /** Operator rehearsal: the current duel's siege starts now. */
    boolean requestSiege() {
        if (boss.isColossus() || boss.isTransforming() || !(boss.stage() == Stage.DUEL_A || boss.stage() == Stage.DUEL_B)) return false;
        siegePending = true;
        return true;
    }

    /** Operator transform: the robed stages are skipped, and a perched caster comes down first. */
    void skipSieges() {
        if (boss.isColossus()) return;
        endSiege();
        siegePending = false;
        boss.setStage(Stage.DONE);
    }

    // ── Fight loop ───────────────────────────────────────────────────────────

    void tickReview(ServerWorld world) {
        long now = world.getTime();
        tickEffects(world, now);
        if (tickPhase(world, now)) return;
        if (now < staggerUntil) { halt(); return; }
        if (active != null) tickAction(world, now);
    }

    void tick(ServerWorld world) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) { cancel(); return; }
        if (home == null) home = boss.getEntityPos();
        long now = world.getTime();
        // During a siege the caster is far above the clearing: the encounter is measured from home.
        boolean siege = boss.stage().siege();
        List<ServerPlayerEntity> players = world.getPlayers(p -> canDamage(boss, p)
                && p.squaredDistanceTo(home) <= ENCOUNTER_RANGE * ENCOUNTER_RANGE
                && (siege || boss.squaredDistanceTo(p) <= ENCOUNTER_RANGE * ENCOUNTER_RANGE));
        tickEffects(world, now);
        if (!engaged) {
            if (siege ? players.isEmpty()
                    : players.stream().noneMatch(p -> boss.squaredDistanceTo(p) <= ENGAGE_RANGE * ENGAGE_RANGE && boss.canSee(p))) return;
            engaged = true; emptySince = -1; nextAction = now + 20;
            log.begin(now, players.size());
            world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.4f, .6f);
        }
        updateBar(players);
        if (players.isEmpty() && !boss.isTransforming()) {
            interrupt();
            if (emptySince < 0) emptySince = now;
            // A wiped or abandoned fight resets only in the robed form; the colossus keeps its earned phase.
            if (now - emptySince >= 200 && !boss.isColossus()) {
                log.finish(boss, "reset: nobody left");
                cancel();
                boss.setStage(Stage.DUEL_A);
                boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health(1));
                boss.setHealth(health(1));
                home = boss.getEntityPos(); // A displaced encounter settles where it stands.
            }
            return;
        }
        emptySince = -1;
        int health = health(players.size());
        if (health > boss.getMaxHealth()) {
            float extra = health - boss.getMaxHealth();
            boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health);
            boss.setHealth(boss.getHealth() + extra); // Damage already dealt is kept; health never shrinks mid-fight.
        }
        if (tickPhase(world, now)) return;
        tickHovering(world, players);
        directMinions(players);
        if (tickSiege(world, now, players)) return;
        if (now < staggerUntil) { halt(); return; }
        if (active != null) { tickAction(world, now); return; }
        if (now < nextAction) { move(players, now); return; }
        List<Candidate> candidates = players.stream()
                .map(p -> new Candidate(p.getUuid(), boss.distanceTo(p), boss.canSee(p))).toList();
        boolean colossus = boss.isColossus();
        Action choice = colossus ? chooseColossus(candidates, ready, now, last)
                : choose(candidates, ready, now, last, castsSinceBlink, shiftEvery);
        if (choice == Action.GRAB) {
            // A player grabbed recently is spared; fall back to the sweep.
            List<Candidate> grabbable = candidates.stream().filter(c -> now >= grabbedAt.getOrDefault(c.id(), Long.MIN_VALUE) + GRAB_REGRAB).toList();
            if (target(Action.GRAB, grabbable, lastTargeted) == null) choice = canTargetAny(Action.SWIPE, candidates) ? Action.SWIPE : null;
            else candidates = grabbable;
        }
        Candidate selected = choice == null ? null : target(choice, candidates, lastTargeted);
        if (selected == null) { move(players, now); return; }
        begin(world, choice, world.getServer().getPlayerManager().getPlayer(selected.id()), now);
    }

    private static boolean canTargetAny(Action action, List<Candidate> candidates) {
        return candidates.stream().anyMatch(c -> canTarget(action, c));
    }

    // ── Phase two ────────────────────────────────────────────────────────────

    /** Runs the pending latch and the transformation; returns true while they own the boss. */
    private boolean tickPhase(ServerWorld world, long now) {
        if (boss.isTransforming()) { tickTransform(world, now); return true; }
        if (!boss.phasePending() || boss.isColossus()) { pendingSince = -1; return false; }
        if (active != null) interrupt();
        halt();
        if (pendingSince < 0) pendingSince = now;
        if (now - pendingSince >= RELOCATE_TIMEOUT) { beginTransform(world); return true; } // Never stall phase two.
        if (room(world, boss.getEntityPos())) {
            // Enough headroom: only wait for footing (a fresh teleport briefly reports no ground).
            if (boss.isOnGround()) beginTransform(world);
            return true;
        }
        if (now >= nextRelocate) {
            nextRelocate = now + 20;
            Vec3d spot = colossusSpot(world);
            if (spot != null) teleport(world, spot);
        }
        return true;
    }

    private void beginTransform(ServerWorld world) {
        pendingSince = -1;
        boss.startTransform();
        boss.triggerAnim(NecromancerEntity.CONTROLLER, "transform");
        freezeMinions();
        log.stage(world.getTime(), "transformation");
        world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE, 2f, .55f);
    }

    private void tickTransform(ServerWorld world, long now) {
        halt();
        int t = (int)boss.getTransformTime(0);
        Vec3d hood = boss.getEntityPos().subtract(forward().multiply(.3)).add(0, 1.9, 0);
        if (t >= 18 && t < 65 && t % 4 == 0)
            world.spawnParticles(ParticleTypes.SOUL, hood.x, hood.y, hood.z, 2, .13, .13, .13, .01);
        if (t == 27) world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SKELETON_HURT, SoundCategory.HOSTILE, 1.6f, .35f);
        if (t == 49 || t == 56) {
            Vec3d hand = boss.getEntityPos().add(forward().multiply(1.5));
            world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_STEP, SoundCategory.HOSTILE, 1.2f, .6f);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floorState(world, hand)), hand.x, hand.y + .05, hand.z, 12, .7, .02, .4, .025);
        }
        if (t == 68) world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 2f, .65f);
        if (t >= 112 && t < 155 && t % 2 == 0) {
            // The empty robe lies behind the skeleton before it burns. Cosmetic particles only.
            Vec3d robe = boss.getEntityPos().subtract(forward().multiply(1.2)).add(0, .12, 0);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, robe.x, robe.y, robe.z, 5, .35, .08, .65, .015);
            world.spawnParticles(ParticleTypes.LARGE_SMOKE, robe.x, robe.y + .15, robe.z, 1, .3, .12, .5, .015);
        }
        if (t == TRANSFORM_GROW && !boss.isColossus()) {
            boss.setColossus(true);
            boss.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(.3);
            boss.getAttributeInstance(EntityAttributes.STEP_HEIGHT).setBaseValue(1.5);
            shove(world, .7, .25);
        }
        if (t == TRANSFORM_ROAR) {
            world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 3f, .5f);
            world.spawnParticles(ParticleTypes.SCULK_SOUL, boss.getX(), boss.getY() + 3, boss.getZ(), 30, 1.5, 1.2, 1.5, .08);
            shove(world, 1.1, .35);
        }
        if (t >= TRANSFORM_TICKS) {
            boss.finishTransform();
            boss.stopTriggeredAnim(NecromancerEntity.CONTROLLER, null);
            thawMinions();
            last = null;
            nextAction = now + 20;
            log.stage(now, "colossus");
        }
    }

    /** Pushes nearby players out of the growing body instead of trapping them inside it. */
    private void shove(ServerWorld world, double strength, double lift) {
        for (ServerPlayerEntity player : world.getPlayers(p -> p.isAlive() && !p.isSpectator() && boss.squaredDistanceTo(p) <= 7 * 7)) {
            Vec3d away = player.getEntityPos().subtract(boss.getEntityPos()).multiply(1, 0, 1);
            away = away.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : away.normalize();
            player.setVelocity(away.x * strength, lift, away.z * strength);
            player.velocityModified = true;
        }
    }

    private boolean room(ServerWorld world, Vec3d at) {
        double half = COLOSSUS_WIDTH / 2.0;
        Box box = new Box(at.x - half, at.y, at.z - half, at.x + half, at.y + COLOSSUS_CLEARANCE, at.z + half);
        return world.isSpaceEmpty(boss, box) && !world.containsFluid(box);
    }

    private Vec3d colossusSpot(ServerWorld world) {
        Vec3d origin = boss.getEntityPos();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2, distance = 3 + boss.getRandom().nextDouble() * 7;
            Vec3d point = origin.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            if (home != null && point.subtract(home).horizontalLength() > MOVEMENT_RANGE + 4) continue;
            Vec3d spot = standable(world, point, COLOSSUS_WIDTH, COLOSSUS_CLEARANCE);
            if (spot != null) return spot;
        }
        return null;
    }

    private void freezeMinions() {
        minionsFrozen = true;
        for (MobEntity minion : minions) if (!NecromancerMinion.rising(minion)) { minion.getNavigation().stop(); minion.setAiDisabled(true); }
    }

    private void thawMinions() {
        if (!minionsFrozen) return;
        minionsFrozen = false;
        for (MobEntity minion : minions) if (!NecromancerMinion.rising(minion)) minion.setAiDisabled(false);
    }

    // ── Sieges ───────────────────────────────────────────────────────────────

    /**
     * Runs a siege (flare, perch, waves, crash) and the exposed window after the first one;
     * returns true while they own the boss.
     */
    private boolean tickSiege(ServerWorld world, long now, List<ServerPlayerEntity> players) {
        if (boss.isColossus()) return false;
        Stage stage = boss.stage();
        if (siegePending && (stage == Stage.DUEL_A || stage == Stage.DUEL_B)) {
            siegePending = false;
            stage = stage.next();
            boss.setStage(stage);
        }
        if (stage.siege() && step == null) beginSiege(world, stage, players, now);
        if (!stage.siege()) {
            if (now >= exposedUntil) return false;
            halt(); hold(lockedYaw);
            if (now % 3 == 0) world.spawnParticles(ParticleTypes.SOUL, boss.getX(), boss.getBodyY(.6), boss.getZ(), 3, .35, .4, .35, .02);
            return true;
        }
        halt();
        switch (step) {
            case RISING -> {
                flare(world, perch, now);
                if (now >= stepAt) {
                    teleport(world, perch);
                    boss.setNoGravity(true);
                    boss.setVelocity(Vec3d.ZERO);
                    boss.triggerAnim(NecromancerEntity.CONTROLLER, "perch_channel");
                    step = Step.WAVES;
                    wave = stage.firstWave() - 1;
                    nextWave = now + SIEGE_FIRST_WAVE;
                }
            }
            case WAVES -> tickWaves(world, now, players, stage);
            case FALLING -> tickFall(world, now, stage);
        }
        return true;
    }

    private void beginSiege(ServerWorld world, Stage stage, List<ServerPlayerEntity> players, long now) {
        interrupt();
        exposedUntil = 0;
        perch = choosePerch(world, players);
        step = Step.RISING;
        stepAt = now + SIEGE_FLARE;
        wave = 0; nextWave = -1; spawnQueue.clear();
        log.stage(now, stage == Stage.SIEGE_1 ? "siege 1" : "siege 2");
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 2f, .5f);
        world.playSound(null, perch.x, perch.y, perch.z, SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 3f, .6f);
    }

    /**
     * In the crypt, the free bough nearest the party (never the last one used); elsewhere, hovering
     * above home. The crypt's boughs sit above the barrier lid, so players see but never reach them.
     */
    private Vec3d choosePerch(ServerWorld world, List<ServerPlayerEntity> players) {
        Vec3d focus = players.isEmpty() ? home : centroid(players);
        if (HollowCryptRealm.keepsTerrain(world)) {
            List<Vec3d> spots = HollowCryptRealm.perches(HollowCryptRealm.nearestCentre(home));
            int best = -1;
            for (int i = 0; i < spots.size(); i++) {
                if (i == lastPerch && spots.size() > 1) continue;
                if (!world.isSpaceEmpty(boss, boss.getDimensions(boss.getPose()).getBoxAt(spots.get(i)))) continue;
                if (best < 0 || spots.get(i).subtract(focus).horizontalLengthSquared() < spots.get(best).subtract(focus).horizontalLengthSquared()) best = i;
            }
            if (best >= 0) { lastPerch = best; return spots.get(best); }
        }
        for (double lift = SIEGE_HOVER; lift > 2; lift -= 2) {
            Vec3d hover = home.add(0, lift, 0);
            if (world.isSpaceEmpty(boss, boss.getDimensions(boss.getPose()).getBoxAt(hover))) return hover;
        }
        return home;
    }

    private void tickWaves(ServerWorld world, long now, List<ServerPlayerEntity> players, Stage stage) {
        if (!players.isEmpty()) face(centroid(players));
        if (now % 2 == 0) {
            // A ward and a soul-fire column down to the floor lead the eye up to the perch through the fog.
            ringForced(world, boss.getEntityPos().add(0, .1, 0), 1.1, ParticleTypes.SOUL_FIRE_FLAME, 8);
            world.spawnParticles(ParticleTypes.SOUL, true, false, boss.getX(), boss.getBodyY(.5), boss.getZ(), 2, .4, .8, .4, .02);
            for (double y = boss.getY() - 1; y > home.y; y -= 1.5)
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, boss.getX(), y, boss.getZ(), 1, .06, .4, .06, 0);
        }
        if (nextWave >= 0 && now >= nextWave) startWave(world, wave + 1, players, now);
        if (!spawnQueue.isEmpty() && now >= nextSpawn && minions.size() < WAVE_ALIVE_CAP) {
            spawnWaveBody(world, spawnQueue.removeFirst(), players);
            nextSpawn = now + spawnGap;
        }
        snipeHoverers(world, now, players);
        if (nextWave >= 0) return; // Resting between waves.
        if (spawnQueue.isEmpty() && minions.isEmpty()) {
            if (wave < stage.lastWave()) {
                nextWave = now + WAVE_BREATHER;
                log.note(now, "wave " + wave + " cleared after " + String.format("%.1fs", (now - waveStarted) / 20.0));
            } else {
                log.note(now, "wave " + wave + " cleared; siege over");
                beginFall(world, now, stage);
            }
        } else if (wave < stage.lastWave() && now - waveStarted >= WAVE_TIMEOUT) {
            log.note(now, "wave " + wave + " timed out; the next wave joins it");
            startWave(world, wave + 1, players, now);
        }
    }

    private void startWave(ServerWorld world, int number, List<ServerPlayerEntity> players, long now) {
        wave = number; nextWave = -1; waveStarted = now;
        Wave bodies = NecromancerRules.wave(number, Math.max(1, players.size()));
        List<Undead> queue = waveBodies(bodies);
        spawnQueue.addAll(queue);
        spawnGap = Math.max(3, WAVE_STAGGER / Math.max(1, queue.size()));
        nextSpawn = now;
        log.note(now, "wave " + number + ": " + bodies.crawlers() + " crawlers, " + bodies.archers() + " archers, " + bodies.brutes() + " brutes");
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 3f, .7f);
        for (ServerPlayerEntity player : players) player.sendMessage(Text.translatable("necromancer.elementalwands.wave", number), true);
    }

    private List<Undead> waveBodies(Wave bodies) {
        List<Undead> queue = new ArrayList<>();
        for (int i = 0; i < bodies.crawlers(); i++) queue.add(Undead.CRAWLER);
        for (int i = 0; i < bodies.archers(); i++) queue.add(Undead.ARCHER);
        for (int i = 0; i < bodies.brutes(); i++) queue.add(Undead.BRUTE);
        Collections.shuffle(queue, new java.util.Random(boss.getRandom().nextLong()));
        return queue;
    }

    /** Claws one body out of the floor somewhere in the clearing, away from the players. */
    private void spawnWaveBody(ServerWorld world, Undead kind, List<ServerPlayerEntity> players) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2, distance = WAVE_MIN + boss.getRandom().nextDouble() * (WAVE_MAX - WAVE_MIN);
            Vec3d spot = standable(world, home.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), .6, 1.99);
            if (spot == null || players.stream().anyMatch(p -> p.squaredDistanceTo(spot) < WAVE_CLEAR * WAVE_CLEAR)
                    || boss.getBoundingBox().expand(.5).contains(spot.add(0, .5, 0))) continue;
            MobEntity minion = switch (kind) {
                case CRAWLER -> new HollowCrawlerEntity(ModEntities.HOLLOW_CRAWLER, world);
                case ARCHER -> new HollowArcherEntity(ModEntities.HOLLOW_ARCHER, world);
                case BRUTE -> new HollowBruteEntity(ModEntities.HOLLOW_BRUTE, world);
            };
            minion.refreshPositionAndAngles(spot.x, spot.y, spot.z, boss.getRandom().nextFloat() * 360, 0);
            NecromancerMinion.bind(minion, boss);
            if (world.spawnEntity(minion)) {
                minions.add(minion);
                stream(world, castOrigin(), spot.add(0, .4, 0));
            }
            return;
        }
        log.note(world.getTime(), "no room for a " + kind.name().toLowerCase());
    }

    /** Counts how long each player has hovered above the floor, out of the army's reach. */
    private void tickHovering(ServerWorld world, List<ServerPlayerEntity> players) {
        if (!boss.stage().siege()) { hovering.clear(); return; }
        hovering.keySet().removeIf(id -> players.stream().noneMatch(p -> p.getUuid().equals(id)));
        for (ServerPlayerEntity player : players) {
            boolean up = heightAboveFloor(world, player) > HOVER_HEIGHT; // A jump peaks near 1.25 blocks.
            hovering.put(player.getUuid(), up ? hovering.getOrDefault(player.getUuid(), 0) + 1 : 0);
        }
    }

    /** A player who hovers too long draws a soul bolt from the perch: the siege is fought on the ground. */
    private void snipeHoverers(ServerWorld world, long now, List<ServerPlayerEntity> players) {
        if (now < nextSnipe) return;
        ServerPlayerEntity hoverer = players.stream().filter(p -> hovering.getOrDefault(p.getUuid(), 0) >= HOVER_TICKS).findFirst().orElse(null);
        if (hoverer == null) return;
        nextSnipe = now + SNIPE_COOLDOWN;
        Vec3d staff = castOrigin(), from = staff;
        // The crypt's boughs sit above an invisible lid: the skull forms just beneath it.
        if (HollowCryptRealm.keepsTerrain(world))
            from = new Vec3d(staff.x, Math.min(staff.y, HollowCryptRealm.SURFACE_Y + HollowCryptRealm.PLAY_CEILING - .5), staff.z);
        if (from != staff) stream(world, staff, from);
        SoulBoltEntity bolt = new SoulBoltEntity(ModEntities.SOUL_BOLT, world);
        bolt.launch(boss, hoverer, from, BOLT_SPEED * 1.3);
        if (world.spawnEntity(bolt)) bolts.add(bolt);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.5f, .5f);
        log.cast(now, "perch bolt", hoverer.getName().getString());
    }

    private double heightAboveFloor(ServerWorld world, ServerPlayerEntity player) {
        Vec3d feet = player.getEntityPos();
        var hit = world.raycast(new RaycastContext(feet, feet.add(0, -8, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, player));
        return hit.getType() == HitResult.Type.MISS ? 8 : feet.y - hit.getPos().y;
    }

    private void beginFall(ServerWorld world, long now, Stage stage) {
        landing = landingSpot(world, stage == Stage.SIEGE_2);
        step = Step.FALLING;
        stepAt = now + SIEGE_FLARE;
        dropped = false;
        world.playSound(null, landing.x, landing.y, landing.z, SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 3f, .5f);
    }

    /** Near home; before the transformation the spot must also fit the colossus. */
    private Vec3d landingSpot(ServerWorld world, boolean colossusRoom) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2, distance = attempt == 0 ? 0 : 2 + boss.getRandom().nextDouble() * 6;
            Vec3d point = home.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Vec3d spot = colossusRoom ? standable(world, point, COLOSSUS_WIDTH, COLOSSUS_CLEARANCE) : standable(world, point, boss.getWidth(), boss.getHeight());
            if (spot != null) return spot;
        }
        return home;
    }

    private void tickFall(ServerWorld world, long now, Stage stage) {
        if (!dropped) {
            flare(world, landing, now);
            if (now < stepAt) return;
            Vec3d drop = landing.add(0, CRASH_DROP, 0);
            if (!world.isSpaceEmpty(boss, boss.getDimensions(boss.getPose()).getBoxAt(drop).union(boss.getDimensions(boss.getPose()).getBoxAt(landing))))
                drop = landing;
            teleport(world, drop);
            boss.setNoGravity(false);
            dropped = true;
            stepAt = now;
            return;
        }
        // Gravity does the rest; a fresh teleport briefly reports no ground, so wait for real footing.
        if ((!boss.isOnGround() || now - stepAt < 2) && now - stepAt < 60) return;
        crash(world, now, stage);
    }

    private void crash(ServerWorld world, long now, Stage stage) {
        step = null; perch = null; landing = null; dropped = false; wave = 0;
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 2.5f, .5f);
        world.playSound(null, boss.getBlockPos(), SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 2f, .5f);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floorState(world, boss.getEntityPos())),
                boss.getX(), boss.getY() + .1, boss.getZ(), 40, 1.2, .1, 1.2, .12);
        world.spawnParticles(ParticleTypes.SCULK_SOUL, boss.getX(), boss.getY() + .5, boss.getZ(), 20, 1, .3, 1, .05);
        shove(world, .6, .25);
        if (stage == Stage.SIEGE_1) {
            boss.setStage(Stage.DUEL_B);
            exposedUntil = now + EXPOSED_TICKS;
            lockedYaw = boss.getYaw();
            boss.triggerAnim(NecromancerEntity.CONTROLLER, "crash"); // Kneels for the whole exposed window.
            last = null; castsSinceBlink = 0; nextAction = exposedUntil + 10;
            log.stage(now, "exposed, then duel B");
            for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(boss, p) && p.squaredDistanceTo(home) <= ENCOUNTER_RANGE * ENCOUNTER_RANGE))
                player.sendMessage(Text.translatable("necromancer.elementalwands.exposed"), true);
        } else {
            boss.setStage(Stage.DONE);
            boss.schedulePhaseTwo();
        }
    }

    /** Clears the siege; a caster left on a perch or hovering comes back down to home. */
    private void endSiege() {
        step = null; perch = null; landing = null; dropped = false; wave = 0; nextWave = -1;
        spawnQueue.clear();
        if (!boss.hasNoGravity()) return;
        boss.setNoGravity(false);
        if (!boss.isRemoved() && home != null && boss.getEntityWorld() instanceof ServerWorld) boss.requestTeleport(home.x, home.y, home.z);
    }

    /** A column of soul fire where the caster is about to appear. */
    private void flare(ServerWorld world, Vec3d at, long now) {
        if (at == null || now % 2 != 0) return;
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, at.x, at.y + 1.2, at.z, 6, .15, .9, .15, .01);
        world.spawnParticles(ParticleTypes.SOUL, true, false, at.x, at.y + .2, at.z, 2, .3, .05, .3, .02);
    }

    /** A thin soul stream from one point to another, seen from anywhere in the clearing. */
    private void stream(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        int steps = (int)Math.min(60, Math.ceil(delta.length() / 1.2));
        for (int i = 0; i <= steps; i++) {
            Vec3d p = from.add(delta.multiply(i / (double)Math.max(1, steps)));
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, p.x, p.y, p.z, 1, .03, .03, .03, 0);
        }
    }

    private static Vec3d centroid(List<ServerPlayerEntity> players) {
        Vec3d sum = Vec3d.ZERO;
        for (ServerPlayerEntity player : players) sum = sum.add(player.getEntityPos());
        return sum.multiply(1.0 / players.size());
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private void begin(ServerWorld world, Action action, ServerPlayerEntity player, long now) {
        interrupt();
        boolean colossus = boss.isColossus();
        active = action; last = action; started = now; target = player.getUuid();
        boltsFired = 0; drained = 0; rushCaught = -1; handsRush = false; ambushDamage = 0;
        lastTargeted.put(target, now);
        ready.put(action, now + action.duration + action.cooldown);
        if (action == Action.BLINK || action == Action.SHIFT || action == Action.AMBUSH) {
            castsSinceBlink = 0;
            shiftEvery = SHIFT_EVERY_MIN + boss.getRandom().nextInt(SHIFT_EVERY_MAX - SHIFT_EVERY_MIN + 1);
        } else castsSinceBlink++;
        log.cast(now, action.name().toLowerCase(), player.getName().getString());
        if (action != Action.SHIFT && action != Action.AMBUSH) face(player.getEntityPos());
        lockedYaw = boss.getYaw();
        String clip = switch (action) {
            case BOLT, HANDS, DRAIN -> (colossus ? "colossus_" : "") + action.name().toLowerCase();
            case BLINK -> "blink";
            case SHIFT, AMBUSH -> null; // The blink clip plays just before the teleport.
            case SWIPE -> "swipe";
            case GRAB -> "grab";
            case RUSH -> "rush_windup"; // Rears and scrapes through the warning; the run loop starts at launch.
        };
        if (clip != null) boss.triggerAnim(NecromancerEntity.CONTROLLER, clip);
        switch (action) {
            case BOLT -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1f, colossus ? .7f : 1.3f);
                // The colossus's jaws kindle before the volley: a distinct tell from its other roars.
                if (colossus) world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 1.6f, .5f);
            }
            case HANDS -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.3f, .7f);
                List<ServerPlayerEntity> victims = new ArrayList<>(List.of(player));
                world.getPlayers(p -> p != player && canDamage(boss, p) && boss.distanceTo(p) <= HANDS_RANGE).stream()
                        .sorted(Comparator.comparingDouble(boss::squaredDistanceTo))
                        .limit(handsTargets(colossus) - 1).forEach(victims::add);
                double radius = handsRadius(colossus);
                int models = handsModels(radius);
                for (ServerPlayerEntity victim : victims) {
                    Vec3d center = ground(world, victim.getEntityPos());
                    grasps.add(new Grasp(center, radius, now + action.impact));
                    for (int i = 0; i < models; i++) {
                        var hand = new GraspingHandEntity(ModEntities.GRASPING_HAND, world);
                        double angle = i * Math.PI * 2 / models + .15, ring = radius * .7;
                        hand.setup(boss, center.add(Math.cos(angle) * ring, 0, Math.sin(angle) * ring),
                                (float)Math.toDegrees(angle) + 90, i % 3, colossus ? 1.18f : 1f, i % 2 == 1);
                        if (world.spawnEntity(hand)) handVisuals.add(hand);
                    }
                }
            }
            case DRAIN -> {
                boss.setDrainTarget(player.getId());
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1.5f, .8f);
                if (colossus) world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WARDEN_SNIFF, SoundCategory.HOSTILE, 2f, .6f);
            }
            case BLINK -> {
                blinkTo = blinkDestination(world, player);
                if (blinkTo == null) {
                    // Cornered: skip the escape briefly rather than teleporting into a wall.
                    active = null; ready.put(Action.BLINK, now + 40); nextAction = now + 4;
                    boss.stopTriggeredAnim(NecromancerEntity.CONTROLLER, null);
                } else world.playSound(null, blinkTo.x, blinkTo.y, blinkTo.z, SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 1.5f, .8f);
            }
            case SHIFT -> {
                blinkTo = shiftDestination(world);
                if (blinkTo == null) { active = null; nextAction = now + 4; castsSinceBlink = 0; }
                else world.playSound(null, blinkTo.x, blinkTo.y, blinkTo.z, SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 2f, .7f);
            }
            case AMBUSH -> {
                ambushTo = behind(world, player);
                if (ambushTo == null) { active = null; ready.put(Action.AMBUSH, now + 40); nextAction = now + 4; }
                // The whisper plays where it will stand: behind the target, who hears it first.
                else world.playSound(null, ambushTo.x, ambushTo.y + 1, ambushTo.z, SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.HOSTILE, 2f, .5f);
            }
            case SWIPE -> world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 2f, .45f);
            case GRAB -> world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SKELETON_AMBIENT, SoundCategory.HOSTILE, 2f, .35f);
            case RUSH -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2f, .7f);
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ATTACK, SoundCategory.HOSTILE, 2f, .5f);
            }
        }
    }

    private void tickAction(ServerWorld world, long now) {
        int tick = (int)(now - started);
        boolean colossus = boss.isColossus();
        if (active == Action.RUSH) { tickRush(world, now); return; }
        halt();
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(target);
        boolean valid = player != null && (reviewing ? player.isAlive() && !player.isSpectator() && player.getEntityWorld() == world
                : canDamage(boss, player));
        boolean tracking = switch (active) {
            case BLINK, SHIFT -> false;
            case AMBUSH -> tick > AMBUSH_APPEAR && tick < active.impact - 4; // Turns on its victim, then commits.
            case SWIPE, GRAB -> tick < active.impact - 6; // Committed swings stop turning before they land.
            case RUSH -> false;
            default -> true;
        };
        if (valid && tracking) { face(player.getEntityPos()); lockedYaw = boss.getYaw(); }
        else hold(lockedYaw);
        switch (active) {
            case BOLT -> {
                if (tick >= active.impact && (tick - active.impact) % boltInterval(colossus) == 0 && boltsFired < boltCount(colossus)) {
                    if (!valid) { finish(now); return; }
                    boltsFired++;
                    SoulBoltEntity bolt = new SoulBoltEntity(ModEntities.SOUL_BOLT, world);
                    bolt.launch(boss, player, castOrigin(), boltSpeed(colossus));
                    if (world.spawnEntity(bolt)) bolts.add(bolt);
                    world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, .7f, .5f);
                }
                if (colossus && tick < active.impact && tick % 2 == 0) {
                    Vec3d jaw = castOrigin();
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, jaw.x, jaw.y, jaw.z, 3, .25, .15, .25, .01);
                }
            }
            case DRAIN -> {
                if (tick < active.impact) break;
                if (!valid || player.squaredDistanceTo(boss) > DRAIN_BREAK_RANGE * DRAIN_BREAK_RANGE
                        || (tick % 5 == 0 && !clear(world, castOrigin(), player.getBoundingBox().getCenter()))) {
                    world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.HOSTILE, .8f, 1.4f);
                    finish(now); return;
                }
                if ((tick - active.impact) % DRAIN_INTERVAL == 0 && hurt(world, player, source(world, DRAIN, null), DRAIN_DAMAGE, "drain")) {
                    float heal = drainHeal(DRAIN_DAMAGE, boss.getMaxHealth(), drained);
                    if (heal > 0) { boss.heal(heal); drained += heal; }
                    world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getBodyY(.6), player.getZ(), 2, .2, .2, .2, .05);
                }
            }
            case BLINK, SHIFT -> {
                flare(world, blinkTo, now);
                if (tick == active.impact - 7) boss.triggerAnim(NecromancerEntity.CONTROLLER, "blink");
                if (tick == active.impact) blink(world, active == Action.BLINK);
            }
            case AMBUSH -> tickAmbush(world, player, valid, tick, now);
            case HANDS -> { if (colossus && tick == CLAW_STRIKE) clawStrike(world); }
            case SWIPE -> {
                if (tick < active.impact && tick % 3 == 0) swipeTelegraph(world);
                if (tick == active.impact) swipe(world);
            }
            case GRAB -> tickGrab(world, player, valid, tick, now);
            case RUSH -> {}
        }
        if (active != null && tick >= active.duration) finish(now);
    }

    private void finish(long now) {
        if (active == null) return;
        // The cooldown counts from the end of the cast, so a long channel is not also a short cooldown.
        ready.put(active, now + active.cooldown);
        boss.setDrainTarget(-1);
        if (held != null && boss.getEntityWorld() instanceof ServerWorld world) release(world, false);
        if (active == Action.RUSH) { halt(); boss.stopTriggeredAnim(NecromancerEntity.CONTROLLER, null); }
        active = null;
        nextAction = now + RECOVERY_GAP;
    }

    private void interrupt() {
        if (held != null && boss.getEntityWorld() instanceof ServerWorld world) release(world, false);
        if (active == Action.RUSH) { halt(); boss.stopTriggeredAnim(NecromancerEntity.CONTROLLER, null); }
        active = null; blinkTo = null; ambushTo = null; rushCaught = -1; handsRush = false;
        boss.setDrainTarget(-1);
        halt();
        boss.stopTriggeredAnim(NecromancerEntity.CONTROLLER, null);
    }

    // ── Duel movement: blink, shift, ambush ──────────────────────────────────

    private void blink(ServerWorld world, boolean curse) {
        if (blinkTo == null) return;
        if (curse) curses.add(new Curse(ground(world, boss.getEntityPos()), world.getTime() + CURSE_TICKS));
        teleport(world, blinkTo);
        blinkTo = null;
    }

    /**
     * Whisper, appear, burst. The flare follows the target's back until he appears, so a player who
     * keeps moving leaves the burst behind; one standing still to cast is caught. After the burst he
     * stays put briefly, open to close-range spells.
     */
    private void tickAmbush(ServerWorld world, ServerPlayerEntity player, boolean valid, int tick, long now) {
        if (tick < AMBUSH_APPEAR) {
            if (!valid) { finish(now); return; }
            if (tick % 2 == 0) {
                Vec3d next = behind(world, player);
                if (next != null) ambushTo = next;
            }
            flare(world, ambushTo, now);
            if (tick == AMBUSH_APPEAR - 7) boss.triggerAnim(NecromancerEntity.CONTROLLER, "blink");
            return;
        }
        if (tick == AMBUSH_APPEAR) {
            teleport(world, ambushTo);
            ambushTo = null;
            if (valid) { face(player.getEntityPos()); lockedYaw = boss.getYaw(); }
            boss.triggerAnim(NecromancerEntity.CONTROLLER, "ambush_burst");
            world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.6f, .5f);
            return;
        }
        if (tick < Action.AMBUSH.impact) {
            if (tick % 2 == 0) ring(world, ground(world, boss.getEntityPos()), AMBUSH_RADIUS, ParticleTypes.SOUL_FIRE_FLAME, 28);
            return;
        }
        if (tick == Action.AMBUSH.impact) soulBurst(world);
    }

    private void soulBurst(ServerWorld world) {
        Vec3d center = boss.getEntityPos();
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 1.2f, 1.4f);
        world.spawnParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 1, center.z, 30, 1.6, .6, 1.6, .08);
        ring(world, ground(world, center), AMBUSH_RADIUS, ParticleTypes.SOUL, 32);
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, boss.getBoundingBox().expand(AMBUSH_RADIUS + 1, 2, AMBUSH_RADIUS + 1),
                e -> (e instanceof ServerPlayerEntity p ? canDamage(boss, p) : e instanceof AstralDoubleEntity) && inside(e, center, AMBUSH_RADIUS))) {
            if (hurt(world, victim, source(world, BURST, null), AMBUSH_DAMAGE, "soul burst")) {
                Vec3d away = victim.getEntityPos().subtract(center).multiply(1, 0, 1);
                away = away.lengthSquared() < 1e-4 ? forward() : away.normalize();
                victim.takeKnockback(1.2, -away.x, -away.z);
            }
        }
    }

    /** A standable spot about two and a half blocks behind where the player faces, inside the leash. */
    private Vec3d behind(ServerWorld world, ServerPlayerEntity player) {
        Vec3d look = player.getRotationVector().multiply(1, 0, 1);
        look = look.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : look.normalize();
        Vec3d side = new Vec3d(-look.z, 0, look.x);
        for (double back : new double[]{AMBUSH_BEHIND, AMBUSH_BEHIND + 1, AMBUSH_BEHIND - .8})
            for (double offset : new double[]{0, .9, -.9}) {
                Vec3d point = player.getEntityPos().subtract(look.multiply(back)).add(side.multiply(offset));
                if (home != null && point.subtract(home).horizontalLength() > BLINK_LEASH) continue;
                Vec3d spot = standable(world, point, boss.getWidth(), boss.getHeight());
                if (spot != null && Math.abs(spot.y - player.getY()) < 2.5) return spot;
            }
        return null;
    }

    /** Somewhere new in the clearing: away from everyone, not too near or far from the fight. */
    private Vec3d shiftDestination(ServerWorld world) {
        List<ServerPlayerEntity> players = world.getPlayers(p -> canDamage(boss, p) && p.squaredDistanceTo(home) <= ENCOUNTER_RANGE * ENCOUNTER_RANGE);
        Vec3d focus = players.isEmpty() ? home : centroid(players);
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2, distance = SHIFT_MIN + boss.getRandom().nextDouble() * (SHIFT_MAX - SHIFT_MIN);
            Vec3d point = focus.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            if (point.subtract(home).horizontalLength() > BLINK_LEASH || point.subtract(boss.getEntityPos()).horizontalLength() < 6) continue;
            if (players.stream().anyMatch(p -> p.getEntityPos().subtract(point).horizontalLength() < SHIFT_CLEAR)) continue;
            Vec3d spot = standable(world, point, boss.getWidth(), boss.getHeight());
            if (spot != null) return spot;
        }
        return null;
    }

    private void teleport(ServerWorld world, Vec3d to) {
        Vec3d from = boss.getEntityPos();
        world.spawnParticles(ParticleTypes.SOUL, true, false, from.x, from.y + .8, from.z, 24, .3, .6, .3, .08);
        world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1f, .6f);
        boss.requestTeleport(to.x, to.y, to.z);
        world.spawnParticles(ParticleTypes.SOUL, true, false, to.x, to.y + .8, to.z, 24, .3, .6, .3, .08);
        world.playSound(null, to.x, to.y, to.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1f, .8f);
    }

    /** Away from the threatening player, inside the leash, on supported ground with room to stand. */
    private Vec3d blinkDestination(ServerWorld world, ServerPlayerEntity threat) {
        Vec3d away = boss.getEntityPos().subtract(threat.getEntityPos()).multiply(1, 0, 1);
        double base = away.lengthSquared() < 1e-4 ? boss.getRandom().nextDouble() * Math.PI * 2 : Math.atan2(away.z, away.x);
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = base + (boss.getRandom().nextDouble() - .5) * Math.toRadians(attempt < 10 ? 140 : 300);
            double distance = BLINK_MIN + boss.getRandom().nextDouble() * (BLINK_MAX - BLINK_MIN);
            Vec3d spot = threat.getEntityPos().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            if (home != null && spot.subtract(home).horizontalLength() > BLINK_LEASH) continue;
            Vec3d standing = standable(world, spot, boss.getWidth(), boss.getHeight());
            if (standing != null) return standing;
        }
        return null;
    }

    // ── Colossus attacks ─────────────────────────────────────────────────────

    /** The tick the colossus Hands clip drives both claws into the floor. */
    private static final int CLAW_STRIKE = 5;

    /** Both claws strike the floor: the ground answers under every marked player. */
    private void clawStrike(ServerWorld world) {
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 2f, .5f);
        for (int side = -1; side <= 1; side += 2) {
            Vec3d claw = boss.getEntityPos().add(forward().multiply(2.2)).add(-forward().z * side * 1.4, 0, forward().x * side * 1.4);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floorState(world, claw)), claw.x, claw.y + .1, claw.z, 16, .5, .05, .5, .1);
        }
    }

    private void swipeTelegraph(ServerWorld world) {
        double yaw = Math.toRadians(lockedYaw);
        for (int i = 0; i <= 8; i++) {
            double angle = yaw - SWIPE_ARC / 2 + SWIPE_ARC * i / 8;
            Vec3d point = boss.getEntityPos().add(-Math.sin(angle) * (SWIPE_RADIUS - .5), .1, Math.cos(angle) * (SWIPE_RADIUS - .5));
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, point.x, point.y, point.z, 1, .05, 0, .05, 0);
        }
    }

    private void swipe(ServerWorld world) {
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2f, .5f);
        double yaw = Math.toRadians(lockedYaw);
        for (int i = 0; i <= 10; i++) {
            double angle = yaw - SWIPE_ARC / 2 + SWIPE_ARC * i / 10;
            Vec3d point = boss.getEntityPos().add(-Math.sin(angle) * 4, .5, Math.cos(angle) * 4);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, boss.getBoundingBox().expand(SWIPE_RADIUS, 2, SWIPE_RADIUS),
                e -> e instanceof ServerPlayerEntity p ? canDamage(boss, p) : e instanceof AstralDoubleEntity)) {
            double dx = victim.getX() - boss.getX(), dz = victim.getZ() - boss.getZ();
            if (!swipeHits(dx, dz, victim.getY() - boss.getY(), yaw)) continue;
            if (hurt(world, victim, world.getDamageSources().mobAttack(boss), SWIPE_DAMAGE, "swipe")) {
                // Swept sideways, in the direction the arm travels.
                Vec3d side = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
                victim.takeKnockback(1.1, -side.x, -side.z);
            }
        }
    }

    private void tickGrab(ServerWorld world, ServerPlayerEntity player, boolean valid, int tick, long now) {
        if (tick < active.impact) {
            if (valid && tick % 2 == 0) world.spawnParticles(ParticleTypes.SOUL, player.getX(), player.getY() + .1, player.getZ(), 2, .5, 0, .5, .01);
            return;
        }
        if (tick == active.impact) {
            if (valid && grabbable(world, player)) {
                held = player.getUuid(); gripDamage = 0;
                grabbedAt.put(held, now);
                boss.setGrabbed(player.getId());
                player.stopGliding();
                world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SKELETON_HURT, SoundCategory.HOSTILE, 1.6f, .5f);
            } else {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.HOSTILE, 1.5f, .5f);
                finish(now);
            }
            return;
        }
        if (held == null) return;
        ServerPlayerEntity victim = world.getServer().getPlayerManager().getPlayer(held);
        if (victim == null || victim.getEntityWorld() != world || !victim.isAlive() || victim.isSpectator() || victim.isCreative()
                || !boss.eligible(victim)) { release(world, false); return; }
        if (tick < GRAB_SLAM) {
            Vec3d hand = NecromancerGrabSocket.worldPosition(boss, tick);
            // The palm holds the upper torso. Keep feet above the floor on the downward stroke.
            Vec3d feet = new Vec3d(hand.x, Math.max(boss.getY() + .05, hand.y - .85), hand.z);
            pin(world, victim, feet);
            if (tick % 3 == 0) world.spawnParticles(ParticleTypes.SOUL, hand.x, hand.y, hand.z, 2, .2, .2, .2, .01);
            return;
        }
        Vec3d slam = ground(world, NecromancerGrabSocket.worldPosition(boss, GRAB_SLAM));
        pin(world, victim, slam);
        hurt(world, victim, world.getDamageSources().mobAttack(boss), grabDamage(victim.getMaxHealth()), "grab slam");
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floorState(world, slam)), slam.x, slam.y + .1, slam.z, 30, .8, .1, .8, .1);
        world.playSound(null, slam.x, slam.y, slam.z, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 2f, .5f);
        release(world, false);
    }

    private boolean grabbable(ServerWorld world, ServerPlayerEntity player) {
        Vec3d delta = player.getEntityPos().subtract(boss.getEntityPos()).multiply(1, 0, 1);
        if (delta.length() > GRAB_REACH) return false;
        if (delta.length() > 1.5) {
            double cos = delta.normalize().dotProduct(forward());
            if (Math.acos(Math.clamp(cos, -1, 1)) > GRAB_CONE / 2) return false;
        }
        return Math.abs(player.getY() - boss.getY()) < 4 && clear(world, boss.getEntityPos().add(0, 2, 0), player.getBoundingBox().getCenter());
    }

    /** Server-held position that leaves the player's camera free: no mount, no dismount exploit. */
    private void pin(ServerWorld world, ServerPlayerEntity player, Vec3d at) {
        player.teleport(world, at.x, at.y, at.z, KEEP_VIEW, 0, 0, false);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        player.velocityModified = true;
    }

    /** One exit for every grab ending: slam, escape, death, disconnect, removal or cancel. */
    private void release(ServerWorld world, boolean escaped) {
        UUID id = held;
        held = null; gripDamage = 0;
        boss.setGrabbed(-1);
        ServerPlayerEntity player = id == null ? null : world.getServer().getPlayerManager().getPlayer(id);
        if (player != null) {
            player.fallDistance = 0;
            if (escaped) {
                Vec3d away = forward().multiply(.6);
                player.setVelocity(away.x, .3, away.z);
                player.velocityModified = true;
            }
        }
    }

    /**
     * Rear and scrape while a soul-fire lane marks the path (the aim locks for the last few ticks),
     * then a grounded run with little steering; contact starts the grab, bite and throw.
     */
    private void tickRush(ServerWorld world, long now) {
        ServerPlayerEntity victim = world.getServer().getPlayerManager().getPlayer(target);
        if (victim == null || !canDamage(boss, victim)) { finish(now); return; }
        int elapsed = (int)(now - started);
        if (rushCaught >= 0) {
            halt(); hold(lockedYaw);
            int grip = (int)(now - rushCaught);
            if (grip < RUSH_THROW) {
                Vec3d wrist = NecromancerRushSocket.worldPosition(boss, grip);
                Vec3d feet = new Vec3d(wrist.x, Math.max(boss.getY() + .05, wrist.y - .85), wrist.z);
                if (!clear(world, victim.getBoundingBox().getCenter(), feet.add(0, .85, 0))) { finish(now); return; }
                pin(world, victim, feet);
                if (grip == RUSH_BITE) {
                    hurt(world, victim, world.getDamageSources().mobAttack(boss), RUSH_DAMAGE, "rush bite");
                    world.playSound(null, victim.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT.value(), SoundCategory.HOSTILE, 2f, .5f);
                    world.spawnParticles(ParticleTypes.SOUL, wrist.x, wrist.y, wrist.z, 14, .3, .3, .3, .04);
                }
            } else if (held != null) {
                release(world, false);
                victim.removeStatusEffect(StatusEffects.SLOWNESS);
                Vec3d throwVelocity = forward().multiply(1.25);
                victim.setVelocity(throwVelocity.x, .55, throwVelocity.z);
                victim.velocityModified = true;
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.8f, .65f);
            }
            if (grip >= RUSH_RECOVER) finish(now);
            return;
        }
        int warning = handsRush ? RUSH_COMBO_WARNING : RUSH_WARNING;
        if (elapsed < warning) {
            halt();
            if (elapsed < warning - RUSH_LOCK) { face(victim.getEntityPos()); lockedYaw = boss.getYaw(); }
            else hold(lockedYaw);
            if (elapsed % 2 == 0) rushLane(world);
            if (elapsed % 6 == 0) {
                Vec3d claws = boss.getEntityPos().add(forward().multiply(2));
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, floorState(world, claws)), claws.x, claws.y + .1, claws.z, 8, .8, .05, .8, .08);
                world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.HOSTILE, 1.6f, .5f);
            }
            return;
        }
        if (elapsed == warning) boss.triggerAnim(NecromancerEntity.CONTROLLER, "rush");
        if (elapsed >= warning + RUSH_TRAVEL || boss.squaredDistanceTo(home) > MOVEMENT_RANGE * MOVEMENT_RANGE * 4) { finish(now); return; }
        Vec3d delta = victim.getEntityPos().subtract(boss.getEntityPos());
        float wanted = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        lockedYaw += MathHelper.clamp(MathHelper.wrapDegrees(wanted - lockedYaw), -RUSH_TURN, RUSH_TURN);
        hold(lockedYaw);
        if (delta.horizontalLength() <= RUSH_REACH && Math.abs(delta.y) < 2.5
                && forward().dotProduct(delta.multiply(1, 0, 1).normalize()) > .65
                && clear(world, boss.getEntityPos().add(0, 1.5, 0), victim.getBoundingBox().getCenter())) {
            halt(); held = victim.getUuid(); boss.setGrabbed(victim.getId());
            victim.stopGliding(); rushCaught = now;
            boss.stopTriggeredAnim(NecromancerEntity.CONTROLLER, null);
            boss.triggerAnim(NecromancerEntity.CONTROLLER, "bite_throw");
            return;
        }
        // MovementType.SELF resolves block collisions; never teleport or turn through a wall.
        Vec3d before = boss.getEntityPos();
        boss.setVelocity(Vec3d.ZERO);
        Vec3d step = forward().multiply(RUSH_SPEED);
        boss.move(MovementType.SELF, new Vec3d(step.x, -.12, step.z));
        if (boss.getEntityPos().subtract(before).horizontalLength() < .1) { finish(now); return; }
        if (elapsed % 5 == 0) world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SKELETON_STEP, SoundCategory.HOSTILE, 1.8f, .55f);
    }

    /** Two soul-fire edges along the run's path, up to the first wall. */
    private void rushLane(ServerWorld world) {
        Vec3d forward = forward(), side = new Vec3d(-forward.z, 0, forward.x).multiply(RUSH_REACH / 2);
        Vec3d start = boss.getEntityPos().add(0, .6, 0);
        var hit = world.raycast(new RaycastContext(start, start.add(forward.multiply(RUSH_SPEED * RUSH_TRAVEL)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, boss));
        double length = hit.getType() == HitResult.Type.MISS ? RUSH_SPEED * RUSH_TRAVEL : start.distanceTo(hit.getPos());
        for (double d = 2.5; d < length; d += 1.4) {
            Vec3d mid = boss.getEntityPos().add(forward.multiply(d)).add(0, .1, 0);
            for (Vec3d edge : new Vec3d[]{mid.add(side), mid.subtract(side)})
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, edge.x, edge.y, edge.z, 1, .05, 0, .05, 0);
        }
    }

    // ── Shared spells and effects ────────────────────────────────────────────

    /** Hands, curses and the army run on their own clocks, even while the boss casts something else. */
    private void tickEffects(ServerWorld world, long now) {
        prune();
        List<ServerPlayerEntity> caught = new ArrayList<>();
        handVisuals.removeIf(GraspingHandEntity::isRemoved);
        grasps.removeIf(grasp -> {
            if (now >= grasp.detonate()) { detonate(world, grasp, caught); return true; }
            if (now % 2 == 0) ring(world, grasp.center(), grasp.radius(), ParticleTypes.SOUL_FIRE_FLAME, (int)Math.round(grasp.radius() * 8));
            if (now % 6 == 0) world.spawnParticles(ParticleTypes.SOUL, grasp.center().x, grasp.center().y + .1, grasp.center().z, 2, .6, 0, .6, .01);
            return false;
        });
        if (!boss.isTransforming() && active == Action.HANDS && !caught.isEmpty()) {
            ServerPlayerEntity nearest = caught.stream().min(Comparator.comparingDouble(boss::squaredDistanceTo)).orElseThrow();
            ready.put(Action.HANDS, now + Action.HANDS.cooldown);
            if (boss.isColossus()) {
                // The skeleton charges the nearest caught player at once.
                begin(world, Action.RUSH, nearest, now);
                handsRush = true;
            } else {
                // The caster follows a root with an ambush behind the caught player, cooldown or not;
                // with nowhere to stand behind them, the Hands cast simply ends.
                begin(world, Action.AMBUSH, nearest, now);
            }
        }
        curses.removeIf(curse -> {
            if (now >= curse.until()) return true;
            if (now % 3 == 0) {
                ring(world, curse.center(), CURSE_RADIUS, ParticleTypes.SMOKE, 10);
                world.spawnParticles(ParticleTypes.SCULK_SOUL, curse.center().x, curse.center().y + .15, curse.center().z, 1, 1.2, 0, 1.2, .005);
            }
            if (now % 10 == 0) for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(boss, p) && inside(p, curse.center(), CURSE_RADIUS))) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0, false, true, true), boss);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 0, false, true, true), boss);
            }
            return false;
        });
    }

    private void detonate(ServerWorld world, Grasp grasp, List<ServerPlayerEntity> caught) {
        Vec3d center = grasp.center();
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 1.2f, .6f);
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3, r = grasp.radius() * .55;
            world.spawnParticles(ParticleTypes.SOUL, center.x + Math.cos(angle) * r, center.y + .4, center.z + Math.sin(angle) * r, 5, .08, .45, .08, .02);
        }
        world.spawnParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + .3, center.z, 10, .7, .2, .7, .04);
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(grasp.radius() + 1, 2, grasp.radius() + 1),
                e -> (e instanceof ServerPlayerEntity p ? canDamage(boss, p) : e instanceof AstralDoubleEntity) && inside(e, center, grasp.radius()))) {
            if (hurt(world, victim, source(world, GRASP, null), HANDS_DAMAGE, "grasping hands") && victim instanceof ServerPlayerEntity player) {
                // Same zero-speed root the Nature wand uses; short, visible and unsaved beyond its duration.
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ROOT_TICKS, 6, false, true, true), boss);
                caught.add(player);
            }
        }
    }

    /** Deals boss damage and reports a hit swallowed by the victim's immunity to the fight log. */
    private boolean hurt(ServerWorld world, LivingEntity victim, DamageSource source, float amount, String what) {
        boolean dealt = victim.damage(world, source, amount);
        if (!dealt && victim instanceof ServerPlayerEntity player && victim.isAlive()) log.lostHit(world.getTime(), player, what, amount);
        return dealt;
    }

    /** Minions chase the nearest player; archers pick off anyone hovering out of the others' reach. */
    private void directMinions(List<ServerPlayerEntity> players) {
        List<ServerPlayerEntity> hoverers = players.stream().filter(p -> hovering.getOrDefault(p.getUuid(), 0) >= 20).toList();
        for (MobEntity minion : minions) {
            if (NecromancerMinion.rising(minion)) continue;
            if (minion instanceof HollowArcherEntity && !hoverers.isEmpty()) {
                if (!(minion.getTarget() instanceof ServerPlayerEntity current && hoverers.contains(current)))
                    hoverers.stream().min(Comparator.comparingDouble(minion::squaredDistanceTo)).ifPresent(minion::setTarget);
                continue;
            }
            if (minion.getTarget() instanceof ServerPlayerEntity current && players.contains(current)) continue;
            players.stream().min(Comparator.comparingDouble(minion::squaredDistanceTo)).ifPresent(minion::setTarget);
        }
    }

    /** Finds a supported, dry, empty column near the point, scanning a few blocks up and down. */
    private Vec3d standable(ServerWorld world, Vec3d point, double width, double height) {
        BlockPos column = BlockPos.ofFloored(point);
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos feet = column.up(dy);
            BlockPos below = feet.down();
            if (!world.isChunkLoaded(feet) || !world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) continue;
            Vec3d spot = new Vec3d(point.x, feet.getY(), point.z);
            Box box = Box.of(spot.add(0, height / 2, 0), width, height, width);
            if (world.isSpaceEmpty(boss, box) && !world.containsFluid(box)) return spot;
        }
        return null;
    }

    private void move(List<ServerPlayerEntity> players, long now) {
        ServerPlayerEntity nearest = players.stream().min(Comparator.comparingDouble(boss::squaredDistanceTo)).orElse(null);
        if (nearest == null || now < nextMove) return;
        nextMove = now + (boss.isColossus() ? 4 : 8);
        double distance = boss.distanceTo(nearest);
        if (boss.isColossus()) {
            // The skeleton closes on all fours; the rush accelerates this grounded gait.
            if (distance <= REACH - .5) { halt(); face(nearest.getEntityPos()); return; }
            Vec3d destination = leash(nearest.getEntityPos());
            boss.getMoveControl().moveTo(destination.x, destination.y, destination.z, 1);
            return;
        }
        Vec3d destination;
        if (distance < NEAR) destination = boss.getEntityPos().add(boss.getEntityPos().subtract(nearest.getEntityPos()).multiply(1, 0, 1).normalize().multiply(5));
        else if (distance > FAR) destination = nearest.getEntityPos().add(boss.getEntityPos().subtract(nearest.getEntityPos()).multiply(1, 0, 1).normalize().multiply(10));
        else { boss.getNavigation().stop(); face(nearest.getEntityPos()); return; }
        destination = leash(destination);
        boss.getNavigation().startMovingTo(destination.x, destination.y, destination.z, distance < NEAR ? 1.25 : 1);
    }

    private Vec3d leash(Vec3d destination) {
        Vec3d offset = destination.subtract(home).multiply(1, 0, 1);
        return offset.length() > MOVEMENT_RANGE ? home.add(offset.normalize().multiply(MOVEMENT_RANGE)).add(0, destination.y - home.y, 0) : destination;
    }

    /** Stops every movement source: pathing, steering and residual horizontal velocity. */
    private void halt() {
        boss.getNavigation().stop();
        boss.getMoveControl().moveTo(boss.getX(), boss.getY(), boss.getZ(), 0);
        boss.setForwardSpeed(0);
        boss.setVelocity(0, Math.min(0, boss.getVelocity().y), 0);
    }

    private void updateBar(List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity previous : new ArrayList<>(bar.getPlayers())) if (!players.contains(previous)) bar.removePlayer(previous);
        for (ServerPlayerEntity player : players) bar.addPlayer(player);
        bar.setPercent(MathHelper.clamp(boss.getHealth() / Math.max(1, boss.getMaxHealth()), 0, 1));
    }

    private void prune() {
        minions.removeIf(minion -> minion.isRemoved() || !minion.isAlive());
        bolts.removeIf(SoulBoltEntity::isRemoved);
    }

    private void ring(ServerWorld world, Vec3d center, double radius, ParticleEffect effect, int points) {
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            world.spawnParticles(effect, center.x + Math.cos(angle) * radius, center.y + .08, center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** A ring sent to every player in the clearing, not only those within the usual 32 blocks. */
    private void ringForced(ServerWorld world, Vec3d center, double radius, ParticleEffect effect, int points) {
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            world.spawnParticles(effect, true, false, center.x + Math.cos(angle) * radius, center.y + .08, center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    private static boolean inside(LivingEntity entity, Vec3d center, double radius) {
        double dx = entity.getX() - center.x, dz = entity.getZ() - center.z;
        return dx * dx + dz * dz <= radius * radius && Math.abs(entity.getY() - center.y) < 1.6;
    }

    /** Snaps a telegraph to the floor under a point, so rings sit on terrain rather than mid-air. */
    private Vec3d ground(ServerWorld world, Vec3d point) {
        var hit = world.raycast(new RaycastContext(point.add(0, .5, 0), point.add(0, -6, 0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, boss));
        return hit.getType() == HitResult.Type.MISS ? point : hit.getPos();
    }

    private static BlockState floorState(ServerWorld world, Vec3d at) {
        BlockState state = world.getBlockState(BlockPos.ofFloored(at.x, at.y - .5, at.z));
        return state.isAir() ? net.minecraft.block.Blocks.STONE.getDefaultState() : state;
    }

    private boolean clear(ServerWorld world, Vec3d from, Vec3d to) {
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, boss)).getType() == HitResult.Type.MISS;
    }

    private Vec3d forward() {
        float yaw = boss.getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    /** Staff tip for the robed caster; the skull's jaws for the colossus. */
    Vec3d castOrigin() {
        Vec3d forward = forward();
        if (boss.isColossus()) return boss.getEntityPos().add(forward.multiply(2.4)).add(0, 3.8, 0);
        Vec3d right = new Vec3d(-forward.z, 0, forward.x);
        return boss.getEntityPos().add(forward.multiply(.35)).add(right.multiply(.35)).add(0, 1.55, 0);
    }

    private void face(Vec3d point) {
        Vec3d delta = point.subtract(boss.getEntityPos());
        hold((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
    }

    private void hold(float yaw) { boss.setYaw(yaw); boss.setBodyYaw(yaw); boss.setHeadYaw(yaw); }

    private DamageSource source(ServerWorld world, RegistryKey<DamageType> type, net.minecraft.entity.Entity direct) {
        return new DamageSource(world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(type), direct == null ? boss : direct, boss);
    }
}
