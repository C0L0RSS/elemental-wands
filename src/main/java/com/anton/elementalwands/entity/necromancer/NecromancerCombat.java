package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.entity.AstralDoubleEntity;
import com.anton.elementalwands.entity.undead.HollowArcherEntity;
import com.anton.elementalwands.entity.undead.HollowBruteEntity;
import com.anton.elementalwands.entity.undead.HollowCrawlerEntity;
import com.anton.elementalwands.registry.ModEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
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
 * keeps its distance; at half health it transforms into the crawling colossus, which closes in.
 */
final class NecromancerCombat {
    static final RegistryKey<DamageType> GRASP = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "necromancer_grasp"));
    static final RegistryKey<DamageType> DRAIN = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "necromancer_drain"));
    private static final Set<PositionFlag> KEEP_VIEW = Set.of(PositionFlag.X_ROT, PositionFlag.Y_ROT);

    private record Grasp(Vec3d center, double radius, long detonate) {}
    private record Curse(Vec3d center, long until) {}

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
        bar.clearPlayers();
    }

    String status() {
        prune();
        String form = boss.isTransforming() ? "transforming" : boss.isColossus() ? "colossus" : boss.phasePending() ? "robed, transformation pending" : "robed";
        return "Hollow Necromancer (" + form + "): " + (active == null ? "idle" : active.name().toLowerCase())
                + ", health " + Math.round(boss.getHealth()) + "/" + Math.round(boss.getMaxHealth())
                + ", minions " + minions.size() + ", " + (boss.isBossAggressive() ? engaged ? "fighting" : "waiting for players" : "passive");
    }

    void testAction(ServerPlayerEntity player, Action action) {
        home = boss.getEntityPos();
        begin((ServerWorld)boss.getEntityWorld(), action, player, boss.getEntityWorld().getTime());
        reviewing = true;
    }

    /** Called with the health actually lost; hits on the colossus during a grab loosen its grip. */
    void damaged(float lost) {
        if (active != Action.GRAB || held == null || lost <= 0) return;
        gripDamage += lost;
        if (gripDamage >= grabEscape(boss.getMaxHealth()) && boss.getEntityWorld() instanceof ServerWorld world) {
            release(world, true);
            staggerUntil = world.getTime() + 30;
            world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SKELETON_HURT, SoundCategory.HOSTILE, 2f, .4f);
            if (active == Action.GRAB) finish(world.getTime());
        }
    }

    void tickReview(ServerWorld world) {
        long now = world.getTime();
        tickEffects(world, now);
        if (tickPhase(world, now)) return;
        if (active != null) tickAction(world, now);
    }

    void tick(ServerWorld world) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) { cancel(); return; }
        if (home == null) home = boss.getEntityPos();
        long now = world.getTime();
        List<ServerPlayerEntity> players = world.getPlayers(p -> canDamage(boss, p)
                && p.squaredDistanceTo(home) <= ENCOUNTER_RANGE * ENCOUNTER_RANGE
                && boss.squaredDistanceTo(p) <= ENCOUNTER_RANGE * ENCOUNTER_RANGE);
        tickEffects(world, now);
        if (!engaged) {
            if (players.stream().noneMatch(p -> boss.squaredDistanceTo(p) <= ENGAGE_RANGE * ENGAGE_RANGE && boss.canSee(p))) return;
            engaged = true; emptySince = -1; nextAction = now + 20;
            world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.4f, .6f);
        }
        updateBar(players);
        if (players.isEmpty() && !boss.isTransforming()) {
            interrupt();
            if (emptySince < 0) emptySince = now;
            // A wiped or abandoned fight resets only in the robed form; the colossus keeps its earned phase.
            if (now - emptySince >= 200 && !boss.isColossus()) {
                cancel();
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
        directMinions(players);
        if (now < staggerUntil) { halt(); return; }
        if (active != null) { tickAction(world, now); return; }
        if (now < nextAction) { move(players, now); return; }
        List<Candidate> candidates = players.stream()
                .map(p -> new Candidate(p.getUuid(), boss.distanceTo(p), boss.canSee(p))).toList();
        boolean colossus = boss.isColossus();
        int cap = minionCap(colossus, players.size());
        Action choice = colossus ? chooseColossus(candidates, ready, now, last, minions.size(), cap)
                : choose(candidates, ready, now, last, minions.size(), cap);
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

    // ── Actions ──────────────────────────────────────────────────────────────

    private void begin(ServerWorld world, Action action, ServerPlayerEntity player, long now) {
        interrupt();
        boolean colossus = boss.isColossus();
        active = action; last = action; started = now; target = player.getUuid();
        boltsFired = 0; drained = 0; rushCaught = -1; handsRush = false;
        lastTargeted.put(target, now);
        ready.put(action, now + action.duration + action.cooldown);
        face(player.getEntityPos());
        lockedYaw = boss.getYaw();
        boss.triggerAnim(NecromancerEntity.CONTROLLER, switch (action) {
            case BOLT -> colossus ? "roar" : "bolt";
            case HANDS -> colossus ? "roar" : "hands";
            case DRAIN -> colossus ? "roar" : "drain";
            case RAISE -> colossus ? "roar" : "raise";
            case BLINK -> "blink";
            case SWIPE -> "swipe";
            case GRAB -> "grab";
            case RUSH -> "rush";
        });
        switch (action) {
            case BOLT -> world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1f, colossus ? .7f : 1.3f);
            case HANDS -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.3f, .7f);
                List<ServerPlayerEntity> victims = new ArrayList<>(List.of(player));
                world.getPlayers(p -> p != player && canDamage(boss, p) && boss.distanceTo(p) <= HANDS_RANGE).stream()
                        .sorted(Comparator.comparingDouble(boss::squaredDistanceTo))
                        .limit(handsTargets(colossus) - 1).forEach(victims::add);
                for (ServerPlayerEntity victim : victims) {
                    Vec3d center = ground(world, victim.getEntityPos());
                    grasps.add(new Grasp(center, handsRadius(colossus), now + action.impact));
                    for (int i = 0; i < 6; i++) {
                        var hand = new GraspingHandEntity(ModEntities.GRASPING_HAND, world);
                        double angle = i * Math.PI / 3 + .15, radius = handsRadius(colossus) * .7;
                        hand.setup(boss, center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius),
                                (float)Math.toDegrees(angle) + 90, i % 3, colossus ? 1.18f : 1f, i % 2 == 1);
                        if (world.spawnEntity(hand)) handVisuals.add(hand);
                    }
                }
            }
            case DRAIN -> {
                boss.setDrainTarget(player.getId());
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1.5f, .8f);
            }
            case RAISE -> world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.4f, .8f);
            case BLINK -> {
                blinkTo = blinkDestination(world, player);
                if (blinkTo == null) {
                    // Cornered: skip the escape briefly rather than teleporting into a wall.
                    active = null; ready.put(Action.BLINK, now + 40); nextAction = now + 4;
                    boss.stopTriggeredAnim(NecromancerEntity.CONTROLLER, null);
                }
            }
            case SWIPE -> world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 2f, .45f);
            case GRAB -> world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_SKELETON_AMBIENT, SoundCategory.HOSTILE, 2f, .35f);
            case RUSH -> world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ATTACK, SoundCategory.HOSTILE, 2f, .5f);
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
            case BLINK -> false;
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
            }
            case DRAIN -> {
                if (tick < active.impact) break;
                if (!valid || player.squaredDistanceTo(boss) > DRAIN_BREAK_RANGE * DRAIN_BREAK_RANGE
                        || (tick % 5 == 0 && !clear(world, castOrigin(), player.getBoundingBox().getCenter()))) {
                    world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.HOSTILE, .8f, 1.4f);
                    finish(now); return;
                }
                if ((tick - active.impact) % DRAIN_INTERVAL == 0 && player.damage(world, source(world, DRAIN, null), DRAIN_DAMAGE)) {
                    float heal = drainHeal(DRAIN_DAMAGE, boss.getMaxHealth(), drained);
                    if (heal > 0) { boss.heal(heal); drained += heal; }
                    world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getBodyY(.6), player.getZ(), 2, .2, .2, .2, .05);
                }
            }
            case RAISE -> { if (tick == active.impact) raise(world, player); }
            case BLINK -> { if (tick == active.impact) blink(world); }
            case HANDS -> {}
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
        active = null; blinkTo = null; rushCaught = -1; handsRush = false;
        boss.setDrainTarget(-1);
        halt();
        boss.stopTriggeredAnim(NecromancerEntity.CONTROLLER, null);
    }

    // ── Colossus attacks ─────────────────────────────────────────────────────

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
            if (victim.damage(world, world.getDamageSources().mobAttack(boss), SWIPE_DAMAGE)) {
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
        float damage = grabDamage(victim.getMaxHealth());
        victim.damage(world, world.getDamageSources().mobAttack(boss), damage);
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

    /** Grounded pursuit, then a separate contact-triggered grab/bite/throw animation. */
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
                    victim.damage(world, world.getDamageSources().mobAttack(boss), RUSH_DAMAGE);
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
        int warning = handsRush ? 0 : RUSH_WARNING;
        if (elapsed < warning) { halt(); face(victim.getEntityPos()); lockedYaw = boss.getYaw(); return; }
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
        if (boss.isColossus() && !boss.isTransforming() && active == Action.HANDS && !caught.isEmpty()) {
            ServerPlayerEntity nearest = caught.stream().min(Comparator.comparingDouble(boss::squaredDistanceTo)).orElseThrow();
            ready.put(Action.HANDS, now + Action.HANDS.cooldown);
            begin(world, Action.RUSH, nearest, now);
            handsRush = true;
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
            if (victim.damage(world, source(world, GRASP, null), HANDS_DAMAGE) && victim instanceof ServerPlayerEntity player) {
                // Same zero-speed root the Nature wand uses; short, visible and unsaved beyond its duration.
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ROOT_TICKS, 6, false, true, true), boss);
                caught.add(player);
            }
        }
    }

    private void raise(ServerWorld world, ServerPlayerEntity player) {
        boolean colossus = boss.isColossus();
        int players = Math.max(1, world.getPlayers(p -> canDamage(boss, p) && boss.squaredDistanceTo(p) <= ENCOUNTER_RANGE * ENCOUNTER_RANGE).size());
        int count = Math.min(raisePerCast(colossus) + (players > 2 ? 1 : 0), minionCap(colossus, players) - minions.size());
        Vec3d anchor = player != null ? boss.getEntityPos().lerp(player.getEntityPos(), .5) : boss.getEntityPos();
        for (int i = 0, attempts = 0; i < count && attempts < 24; attempts++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2, distance = 1.5 + boss.getRandom().nextDouble() * 3;
            Vec3d spot = standable(world, anchor.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), .6, 1.99);
            // Never rise inside or directly under a player, or inside the colossus.
            if (spot == null || !world.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(spot) < 2 * 2).isEmpty()
                    || boss.getBoundingBox().expand(.5).contains(spot.add(0, .5, 0))) continue;
            MobEntity minion = switch (raiseKind(minions.size(), count(HollowArcherEntity.class), count(HollowBruteEntity.class),
                    colossus, boss.getRandom().nextDouble())) {
                case CRAWLER -> new HollowCrawlerEntity(ModEntities.HOLLOW_CRAWLER, world);
                case ARCHER -> new HollowArcherEntity(ModEntities.HOLLOW_ARCHER, world);
                case BRUTE -> new HollowBruteEntity(ModEntities.HOLLOW_BRUTE, world);
            };
            minion.refreshPositionAndAngles(spot.x, spot.y, spot.z, boss.getRandom().nextFloat() * 360, 0);
            NecromancerMinion.bind(minion, boss);
            if (world.spawnEntity(minion)) { minions.add(minion); i++; }
        }
    }

    private int count(Class<? extends MobEntity> kind) {
        return (int)minions.stream().filter(kind::isInstance).count();
    }

    private void blink(ServerWorld world) {
        if (blinkTo == null) return;
        curses.add(new Curse(ground(world, boss.getEntityPos()), world.getTime() + CURSE_TICKS));
        teleport(world, blinkTo);
        blinkTo = null;
    }

    private void teleport(ServerWorld world, Vec3d to) {
        Vec3d from = boss.getEntityPos();
        world.spawnParticles(ParticleTypes.SOUL, from.x, from.y + .8, from.z, 24, .3, .6, .3, .08);
        world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1f, .6f);
        boss.requestTeleport(to.x, to.y, to.z);
        world.spawnParticles(ParticleTypes.SOUL, to.x, to.y + .8, to.z, 24, .3, .6, .3, .08);
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
            if (home != null && spot.subtract(home).horizontalLength() > MOVEMENT_RANGE + 4) continue;
            Vec3d standing = standable(world, spot, boss.getWidth(), boss.getHeight());
            if (standing != null) return standing;
        }
        return null;
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

    private void directMinions(List<ServerPlayerEntity> players) {
        for (MobEntity minion : minions) {
            if (NecromancerMinion.rising(minion)) continue;
            if (minion.getTarget() instanceof ServerPlayerEntity current && players.contains(current)) continue;
            players.stream().min(Comparator.comparingDouble(minion::squaredDistanceTo)).ifPresent(minion::setTarget);
        }
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
