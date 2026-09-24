package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.entity.AstralDoubleEntity;
import com.anton.elementalwands.registry.ModEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
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

/** Server-authoritative caster encounter: one committed spell at a time, telegraphed and dodgeable. */
final class NecromancerCombat {
    static final RegistryKey<DamageType> GRASP = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "necromancer_grasp"));
    static final RegistryKey<DamageType> DRAIN = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "necromancer_drain"));

    private record Grasp(Vec3d center, long detonate) {}
    private record Curse(Vec3d center, long until) {}

    private final NecromancerEntity boss;
    private final ServerBossBar bar = new ServerBossBar(Text.translatable("entity.elementalwands.hollow_necromancer"),
            BossBar.Color.PURPLE, BossBar.Style.PROGRESS);
    private final Map<Action, Long> ready = new EnumMap<>(Action.class);
    private final Map<UUID, Long> lastTargeted = new HashMap<>();
    private final List<MobEntity> minions = new ArrayList<>();
    private final List<SoulBoltEntity> bolts = new ArrayList<>();
    private final List<Grasp> grasps = new ArrayList<>();
    private final List<Curse> curses = new ArrayList<>();
    private Vec3d home, blinkTo;
    private Action active, last;
    private UUID target;
    private long started, nextAction, emptySince = -1, nextMove;
    private boolean engaged, reviewing;
    private int boltsFired;
    private float drained;

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
        for (MobEntity minion : minions) if (!minion.isRemoved() && minion.getEntityWorld() instanceof ServerWorld world)
            NecromancerMinion.dissolve(minion, world);
        minions.clear(); grasps.clear(); curses.clear();
        for (SoulBoltEntity bolt : bolts) bolt.discard();
        bolts.clear();
        ready.clear(); lastTargeted.clear(); last = null;
        engaged = false; reviewing = false; emptySince = -1;
        bar.clearPlayers();
    }

    String status() {
        prune();
        return "Hollow Necromancer: " + (active == null ? "idle" : active.name().toLowerCase())
                + ", health " + Math.round(boss.getHealth()) + "/" + Math.round(boss.getMaxHealth())
                + ", minions " + minions.size() + ", " + (boss.isBossAggressive() ? engaged ? "fighting" : "waiting for players" : "passive");
    }

    void testAction(ServerPlayerEntity player, Action action) {
        home = boss.getEntityPos();
        begin((ServerWorld)boss.getEntityWorld(), action, player, boss.getEntityWorld().getTime());
        reviewing = true;
    }

    void tickReview(ServerWorld world) {
        long now = world.getTime();
        tickEffects(world, now);
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
        if (players.isEmpty()) {
            interrupt();
            if (emptySince < 0) emptySince = now;
            if (now - emptySince >= 200) {
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
        directMinions(players);
        if (active != null) { tickAction(world, now); return; }
        if (now < nextAction) { move(players, now); return; }
        List<Candidate> candidates = players.stream()
                .map(p -> new Candidate(p.getUuid(), boss.distanceTo(p), boss.canSee(p))).toList();
        Action choice = choose(candidates, ready, now, last, minions.size(), minionCap(false, players.size()));
        Candidate selected = choice == null ? null : target(choice, candidates, lastTargeted);
        if (selected == null) { move(players, now); return; }
        begin(world, choice, world.getServer().getPlayerManager().getPlayer(selected.id()), now);
    }

    private void begin(ServerWorld world, Action action, ServerPlayerEntity player, long now) {
        interrupt();
        active = action; last = action; started = now; target = player.getUuid();
        boltsFired = 0; drained = 0;
        lastTargeted.put(target, now);
        ready.put(action, now + action.duration + action.cooldown);
        face(player.getEntityPos());
        boss.triggerAnim(NecromancerEntity.CONTROLLER, switch (action) {
            case BOLT -> "bolt"; case HANDS -> "hands"; case DRAIN -> "drain"; case RAISE -> "raise"; case BLINK -> "blink";
        });
        switch (action) {
            case BOLT -> world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1f, 1.3f);
            case HANDS -> {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.3f, .7f);
                List<ServerPlayerEntity> victims = new ArrayList<>(List.of(player));
                world.getPlayers(p -> p != player && canDamage(boss, p) && boss.distanceTo(p) <= HANDS_RANGE).stream()
                        .sorted(Comparator.comparingDouble(boss::squaredDistanceTo))
                        .limit(HANDS_MAX_TARGETS - 1).forEach(victims::add);
                for (ServerPlayerEntity victim : victims) grasps.add(new Grasp(ground(world, victim.getEntityPos()), now + action.impact));
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
        }
    }

    private void tickAction(ServerWorld world, long now) {
        int tick = (int)(now - started);
        boss.getNavigation().stop();
        boss.setVelocity(0, Math.min(0, boss.getVelocity().y), 0);
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(target);
        boolean valid = player != null && (reviewing ? player.isAlive() && !player.isSpectator() && player.getEntityWorld() == world
                : canDamage(boss, player));
        if (valid && active != Action.BLINK) face(player.getEntityPos());
        switch (active) {
            case BOLT -> {
                if (tick >= active.impact && (tick - active.impact) % BOLT_INTERVAL == 0 && boltsFired < BOLT_COUNT) {
                    if (!valid) { finish(now); return; }
                    boltsFired++;
                    SoulBoltEntity bolt = new SoulBoltEntity(ModEntities.SOUL_BOLT, world);
                    bolt.launch(boss, player, staffTip());
                    if (world.spawnEntity(bolt)) bolts.add(bolt);
                    world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, .7f, .5f);
                }
            }
            case DRAIN -> {
                if (tick < active.impact) break;
                if (!valid || player.squaredDistanceTo(boss) > DRAIN_BREAK_RANGE * DRAIN_BREAK_RANGE
                        || (tick % 5 == 0 && !clear(world, staffTip(), player.getBoundingBox().getCenter()))) {
                    world.playSound(null, boss.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.HOSTILE, .8f, 1.4f);
                    finish(now); return;
                }
                if (tick % 2 == 0) tether(world, player);
                if ((tick - active.impact) % DRAIN_INTERVAL == 0 && player.damage(world, source(world, DRAIN, null), DRAIN_DAMAGE)) {
                    float heal = drainHeal(DRAIN_DAMAGE, boss.getMaxHealth(), drained);
                    if (heal > 0) { boss.heal(heal); drained += heal; }
                    world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getBodyY(.6), player.getZ(), 2, .2, .2, .2, .05);
                }
            }
            case RAISE -> { if (tick == active.impact) raise(world, player); }
            case BLINK -> { if (tick == active.impact) blink(world); }
            case HANDS -> {}
        }
        if (tick >= active.duration) finish(now);
    }

    private void finish(long now) {
        // The cooldown counts from the end of the cast, so a long channel is not also a short cooldown.
        ready.put(active, now + active.cooldown);
        boss.setDrainTarget(-1);
        active = null;
        nextAction = now + RECOVERY_GAP;
    }

    private void interrupt() {
        active = null; blinkTo = null;
        boss.setDrainTarget(-1);
        boss.getNavigation().stop();
        boss.stopTriggeredAnim(NecromancerEntity.CONTROLLER, null);
    }

    /** Hands, curses and the army run on their own clocks, even while the boss casts something else. */
    private void tickEffects(ServerWorld world, long now) {
        prune();
        grasps.removeIf(grasp -> {
            if (now >= grasp.detonate()) { detonate(world, grasp.center()); return true; }
            if (now % 2 == 0) ring(world, grasp.center(), HANDS_RADIUS, ParticleTypes.SOUL_FIRE_FLAME, 14);
            if (now % 6 == 0) world.spawnParticles(ParticleTypes.SOUL, grasp.center().x, grasp.center().y + .1, grasp.center().z, 2, .6, 0, .6, .01);
            return false;
        });
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

    private void detonate(ServerWorld world, Vec3d center) {
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 1.2f, .6f);
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI / 3, r = HANDS_RADIUS * .55;
            world.spawnParticles(ParticleTypes.SOUL, center.x + Math.cos(angle) * r, center.y + .4, center.z + Math.sin(angle) * r, 5, .08, .45, .08, .02);
        }
        world.spawnParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + .3, center.z, 10, .7, .2, .7, .04);
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(HANDS_RADIUS + 1, 2, HANDS_RADIUS + 1),
                e -> (e instanceof ServerPlayerEntity p ? canDamage(boss, p) : e instanceof AstralDoubleEntity) && inside(e, center, HANDS_RADIUS))) {
            if (victim.damage(world, source(world, GRASP, null), HANDS_DAMAGE) && victim instanceof ServerPlayerEntity)
                // Same zero-speed root the Nature wand uses; short, visible and unsaved beyond its duration.
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ROOT_TICKS, 6, false, true, true), boss);
        }
    }

    private void raise(ServerWorld world, ServerPlayerEntity player) {
        int players = Math.max(1, world.getPlayers(p -> canDamage(boss, p) && boss.squaredDistanceTo(p) <= ENCOUNTER_RANGE * ENCOUNTER_RANGE).size());
        int count = Math.min(RAISE_PER_CAST + (players > 2 ? 1 : 0), minionCap(false, players) - minions.size());
        Vec3d anchor = player != null ? boss.getEntityPos().lerp(player.getEntityPos(), .5) : boss.getEntityPos();
        for (int i = 0, attempts = 0; i < count && attempts < 24; attempts++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2, distance = 1.5 + boss.getRandom().nextDouble() * 3;
            Vec3d spot = standable(world, anchor.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance), .6, 1.99);
            // Never rise inside or directly under a player.
            if (spot == null || !world.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(spot) < 2 * 2).isEmpty()) continue;
            MobEntity minion = boss.getRandom().nextBoolean()
                    ? new SpectralZombieEntity(ModEntities.SPECTRAL_ZOMBIE, world)
                    : new SpectralSkeletonEntity(ModEntities.SPECTRAL_SKELETON, world);
            minion.refreshPositionAndAngles(spot.x, spot.y, spot.z, boss.getRandom().nextFloat() * 360, 0);
            NecromancerMinion.bind(minion, boss, spot.y);
            if (world.spawnEntity(minion)) { minions.add(minion); i++; }
        }
    }

    private void blink(ServerWorld world) {
        if (blinkTo == null) return;
        Vec3d from = boss.getEntityPos();
        curses.add(new Curse(ground(world, from), world.getTime() + CURSE_TICKS));
        world.spawnParticles(ParticleTypes.SOUL, from.x, from.y + .8, from.z, 24, .3, .6, .3, .08);
        world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1f, .6f);
        boss.requestTeleport(blinkTo.x, blinkTo.y, blinkTo.z);
        world.spawnParticles(ParticleTypes.SOUL, blinkTo.x, blinkTo.y + .8, blinkTo.z, 24, .3, .6, .3, .08);
        world.playSound(null, blinkTo.x, blinkTo.y, blinkTo.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1f, .8f);
        blinkTo = null;
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
            if (world.isSpaceEmpty(box) && !world.containsFluid(box)) return spot;
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
        nextMove = now + 8;
        double distance = boss.distanceTo(nearest);
        Vec3d destination;
        if (distance < NEAR) destination = boss.getEntityPos().add(boss.getEntityPos().subtract(nearest.getEntityPos()).multiply(1, 0, 1).normalize().multiply(5));
        else if (distance > FAR) destination = nearest.getEntityPos().add(boss.getEntityPos().subtract(nearest.getEntityPos()).multiply(1, 0, 1).normalize().multiply(10));
        else { boss.getNavigation().stop(); face(nearest.getEntityPos()); return; }
        Vec3d offset = destination.subtract(home).multiply(1, 0, 1);
        if (offset.length() > MOVEMENT_RANGE) destination = home.add(offset.normalize().multiply(MOVEMENT_RANGE)).add(0, destination.y - home.y, 0);
        boss.getNavigation().startMovingTo(destination.x, destination.y, destination.z, distance < NEAR ? 1.25 : 1);
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

    private void tether(ServerWorld world, ServerPlayerEntity player) {
        Vec3d from = player.getBoundingBox().getCenter(), to = staffTip();
        for (int i = 0; i < 6; i++) {
            Vec3d point = from.lerp(to, (i + boss.getRandom().nextDouble()) / 6);
            world.spawnParticles(ParticleTypes.SOUL, point.x, point.y, point.z, 1, .03, .03, .03, 0);
        }
    }

    private void ring(ServerWorld world, Vec3d center, double radius, net.minecraft.particle.ParticleEffect effect, int points) {
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

    private boolean clear(ServerWorld world, Vec3d from, Vec3d to) {
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, boss)).getType() == HitResult.Type.MISS;
    }

    Vec3d staffTip() {
        float yaw = boss.getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
        Vec3d forward = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
        Vec3d right = new Vec3d(-MathHelper.cos(yaw), 0, -MathHelper.sin(yaw));
        return boss.getEntityPos().add(forward.multiply(.35)).add(right.multiply(.35)).add(0, 1.55, 0);
    }

    private void face(Vec3d point) {
        Vec3d delta = point.subtract(boss.getEntityPos());
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        boss.setYaw(yaw); boss.setBodyYaw(yaw); boss.setHeadYaw(yaw);
    }

    private DamageSource source(ServerWorld world, RegistryKey<DamageType> type, net.minecraft.entity.Entity direct) {
        return new DamageSource(world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(type), direct == null ? boss : direct, boss);
    }
}
