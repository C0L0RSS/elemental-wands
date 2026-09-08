package com.anton.elementalwands.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;
import static com.anton.elementalwands.entity.GuardianCombatRules.*;

/** Server-authoritative encounter: one committed action, group-aware selection, real recovery. */
final class GuardianBossCombat {
    private final FracturedGuardianEntity guardian;
    private final ServerBossBar bar = new ServerBossBar(Text.literal("Fractured Guardian"), BossBar.Color.BLUE, BossBar.Style.PROGRESS);
    private final Map<Attack, Long> ready = new EnumMap<>(Attack.class);
    private final Map<UUID, Long> lastTargeted = new HashMap<>();
    private final GuardianLeapAttack leap;
    private final GuardianMotionSample motion = new GuardianMotionSample();
    private final Set<UUID> waveHit = new HashSet<>();
    private final List<GuardianRockEntity> rocks = new ArrayList<>();
    private Vec3d home, anchor, lockedAim;
    private UUID target, movementTarget;
    private Attack active, last;
    private long started, nextAction, emptySince = -1, nextMove;
    private boolean engaged, frozen, previousNoAi;
    private boolean reviewing;
    private int waking;
    private float attackYaw;
    private GuardianRockEntity heldRock;

    GuardianBossCombat(FracturedGuardianEntity guardian) { this.guardian = guardian; this.leap = new GuardianLeapAttack(guardian,this); }

    static boolean canDamage(FracturedGuardianEntity guardian, ServerPlayerEntity player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.getEntityWorld() == guardian.getEntityWorld() && !guardian.isTeammate(player);
    }

    static boolean clearLine(ServerWorld world, FracturedGuardianEntity guardian, Vec3d from, Vec3d to) {
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, guardian)).getType() == HitResult.Type.MISS;
    }

    void cancel() {
        leap.cancel();
        guardian.cancelCombatBeam();
        guardian.setHoldingRock(false);
        guardian.clearWave();
        unfreeze();
        active = null; waking = 0; engaged = false;
        target = movementTarget = null;
        ready.clear(); lastTargeted.clear(); waveHit.clear(); last = null;
        for (GuardianRockEntity rock : rocks) rock.discard();
        rocks.clear(); heldRock = null;
        bar.clearPlayers();
        guardian.getNavigation().stop();
        guardian.stopTriggeredAnim("guardian", null);
    }

    String leapStatus() { return leap.status(); }

    void start() {
        cancel();
        home = guardian.getEntityPos();
        nextAction = guardian.getEntityWorld().getTime() + 20;
        guardian.setAiDisabled(false);
        guardian.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(.16);
    }

    void testAttack(ServerPlayerEntity player, Attack attack) {
        begin((ServerWorld)guardian.getEntityWorld(), attack, player, guardian.getEntityWorld().getTime());
        reviewing = true;
    }

    void tickReview(ServerWorld world) {
        rocks.removeIf(GuardianRockEntity::isRemoved);
        if (active != null) tickAttack(world, world.getTime());
    }

    void tick(ServerWorld world) {
        if (!guardian.isAlive() || world.getDifficulty() == Difficulty.PEACEFUL) { cancel(); return; }
        if (home == null) home = guardian.getEntityPos();
        long now = world.getTime();
        List<ServerPlayerEntity> players = world.getPlayers(p -> canDamage(guardian, p)
                && p.squaredDistanceTo(home) <= 48*48 && guardian.squaredDistanceTo(p) <= 48*48);
        rocks.removeIf(GuardianRockEntity::isRemoved);
        if (!engaged) {
            if (players.stream().noneMatch(p -> guardian.squaredDistanceTo(p) <= 24*24 && guardian.canSee(p))) return;
            engaged = true; waking = 84; emptySince = -1;
            guardian.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(.16);
            anchor = guardian.getEntityPos();
            freeze();
            guardian.triggerAnim("guardian", "awaken");
        }
        updateBar(players);
        if (players.isEmpty() || guardian.squaredDistanceTo(home) > 48*48) {
            interruptAction();
            if (emptySince < 0) emptySince = now;
            if (now - emptySince >= 200) {
                cancel();
                guardian.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(200);
                guardian.setHealth(200);
                // Displaced encounters settle at their new position; there is no surprise teleport.
                home = guardian.getEntityPos();
            }
            return;
        }
        emptySince = -1;
        int health = healthForParty(players.size());
        if (health > guardian.getMaxHealth()) {
            float extra = health - guardian.getMaxHealth();
            guardian.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health);
            guardian.setHealth(guardian.getHealth() + extra); // Preserve damage already dealt; never shrink mid-fight.
        }
        if (waking > 0) {
            guardian.setVelocity(0, guardian.getVelocity().y, 0);
            if (--waking == 51) impactSound(world);
            if (waking == 0) { unfreeze(); nextAction = now + 20; }
            return;
        }
        if (active != null) { tickAttack(world, now); return; }
        if (now < nextAction) return;
        List<Candidate> candidates = players.stream().map(p -> new Candidate(p.getUuid(),
                guardian.distanceTo(p), guardian.canSee(p))).toList();
        Attack choice = choose(candidates, ready, now, last);
        if (choice != null && guardian.isOnGround()) {
            Candidate selected = target(choice, candidates, lastTargeted);
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(selected.id());
            begin(world, choice, player, now);
        } else {
            // Hold a useful firing position. Reposition only if the group is out of reach or behind cover.
            if (candidates.stream().anyMatch(p -> p.visible() && p.distance() <= 24)) {
                guardian.getNavigation().stop(); return;
            }
            ServerPlayerEntity destination = players.stream().filter(p -> p.getUuid().equals(movementTarget)).findFirst()
                    .orElseGet(() -> players.stream().min(java.util.Comparator.comparingDouble(guardian::squaredDistanceTo)).orElseThrow());
            movementTarget = destination.getUuid();
            if (now >= nextMove) {
                nextMove = now + 20;
                Vec3d delta = destination.getEntityPos().subtract(home);
                Vec3d point = delta.length() > 14 ? home.add(delta.normalize().multiply(14)) : destination.getEntityPos();
                guardian.getNavigation().startMovingTo(point.x, point.y, point.z, 1);
            }
        }
    }

    private void updateBar(List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity previous : new ArrayList<>(bar.getPlayers())) if (!players.contains(previous)) bar.removePlayer(previous);
        for (ServerPlayerEntity player : players) bar.addPlayer(player);
        bar.setPercent(MathHelper.clamp(guardian.getHealth()/Math.max(1, guardian.getMaxHealth()), 0, 1));
    }

    private void begin(ServerWorld world, Attack attack, ServerPlayerEntity player, long now) {
        reviewing = false;
        active = attack; last = attack; target = player.getUuid(); started = now;
        anchor = guardian.getEntityPos(); waveHit.clear();
        motion.reset(player.getEntityPos());
        lastTargeted.put(target, now);
        ready.put(attack, now + attack.duration + attack.cooldown);
        guardian.getNavigation().stop();
        face(player.getEntityPos(), true);
        if (attack == Attack.LEAP) {
            freeze();
            if (!leap.begin(world,player,home == null ? anchor : home)) {
                unfreeze(); active = null; nextAction = now+10;
                ready.put(Attack.LEAP,now+60);
            }
            return;
        }
        if (attack == Attack.BEAM) { guardian.beginCombatBeam(player); return; }
        freeze();
        if (attack == Attack.SHOCKWAVE) guardian.startWave(now);
        guardian.triggerAnim("guardian", attack == Attack.THROW ? "throw" : "slam");
        world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, .8f, .55f);
        lockedAim = player.getBoundingBox().getCenter();
    }

    private void tickAttack(ServerWorld world, long now) {
        if (active == Attack.LEAP) {
            if (leap.tick(world)) { unfreeze(); active = null; nextAction = now+RECOVERY_GAP; }
            return;
        }
        int tick = (int)(now - started);
        if (guardian.getEntityPos().squaredDistanceTo(anchor) > .75*.75) { interruptAction(); nextAction = now + 30; return; }
        if (tick >= active.duration) {
            unfreeze(); active = null; heldRock = null;
            guardian.clearWave();
            guardian.setHoldingRock(false);
            guardian.getNavigation().stop();
            nextAction = now + RECOVERY_GAP;
            return;
        }
        if (active == Attack.BEAM) return;
        guardian.setVelocity(0, guardian.getVelocity().y, 0);
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(target);
        int lock = active == Attack.THROW ? THROW_LOCK : 14;
        if (tick < lock) {
            if (player == null || !player.isAlive() || player.isSpectator() || player.getEntityWorld() != world
                    || (!reviewing && !canDamage(guardian, player)) || guardian.squaredDistanceTo(player) > 48*48) {
                interruptAction(); nextAction = now + 30; return;
            }
            face(player.getEntityPos(), false);
            Vec3d velocity = motion.observe(player.getEntityPos());
            lockedAim = active == Attack.THROW
                    ? throwAim(GuardianThrowSocket.worldPosition(guardian, THROW_RELEASE),
                            player.getBoundingBox().getCenter(), velocity, THROW_RELEASE-tick)
                    : player.getBoundingBox().getCenter();
        }
        guardian.setYaw(attackYaw); guardian.setBodyYaw(attackYaw); guardian.setHeadYaw(attackYaw);
        if (active == Attack.THROW) {
            if (tick == 12) {
                heldRock = new GuardianRockEntity(com.anton.elementalwands.registry.ModEntities.GUARDIAN_ROCK, world);
                heldRock.setOwner(guardian);
                heldRock.setPosition(GuardianThrowSocket.worldPosition(guardian, tick));
                world.spawnEntity(heldRock); rocks.add(heldRock);
                guardian.setHoldingRock(true);
            }
            if (heldRock != null && tick <= THROW_RELEASE) heldRock.setPosition(GuardianThrowSocket.worldPosition(guardian, tick));
            if (tick == THROW_LOCK) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    lockedAim.x, lockedAim.y, lockedAim.z, 15, .4, .5, .4, .02);
            if (tick == THROW_RELEASE && heldRock != null) {
                guardian.setHoldingRock(false);
                heldRock.release(lockedAim);
                heldRock = null;
                world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.2f, .7f);
            }
        } else {
            if (tick < SLAM_IMPACT && tick % 3 == 0) warning(world, active == Attack.SHOCKWAVE ? 4 : 3);
            if (tick == SLAM_IMPACT) {
                impactSound(world);
                if (active == Attack.SLAM) slam(world);
            }
            if (active == Attack.SHOCKWAVE && tick >= SLAM_IMPACT) wave(world, tick-SLAM_IMPACT);
        }
    }

    private void slam(ServerWorld world) {
        Vec3d forward = Vec3d.fromPolar(0, attackYaw);
        for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(guardian, p))) {
            Vec3d delta = player.getEntityPos().subtract(anchor);
            if (delta.horizontalLength() > 4.5 || Math.abs(delta.y) > 3) continue;
            if (delta.horizontalLength() > .5 && forward.dotProduct(new Vec3d(delta.x, 0, delta.z).normalize()) < .25) continue;
            if (clearLine(world, guardian, anchor.add(0, 1, 0), player.getBoundingBox().getCenter())
                    && player.damage(world, world.getDamageSources().mobAttack(guardian), 6)) player.takeKnockback(.8, -delta.x, -delta.z);
        }
    }

    /** Sample collision surfaces, rather than assuming grass blocks or a flat arena. */
    private Vec3d ground(ServerWorld world, double x, double z) {
        return GuardianWaveSurface.ground(world, guardian, anchor, x, z);
    }

    private void wave(ServerWorld world, int tick) { wave(world,tick,anchor,waveHit); }

    void leapWave(ServerWorld world, int tick, Vec3d center, Set<UUID> hitPlayers) {
        wave(world,tick,center,hitPlayers);
    }

    void leapLanded(ServerWorld world, Set<UUID> impactVictims) {
        Vec3d center = guardian.getEntityPos();
        impactSound(world);
        world.playSound(null,guardian.getBlockPos(),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.HOSTILE,1.6f,.65f);
        for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(guardian,p))) {
            float damage = GuardianLeapRules.damage(center,player.getBoundingBox());
            if (damage <= 0 || !clearLine(world,guardian,center.add(0,1,0),player.getBoundingBox().getCenter())) continue;
            impactVictims.add(player.getUuid());
            if (player.damage(world,world.getDamageSources().mobAttack(guardian),damage)) {
                Vec3d away = player.getEntityPos().subtract(center);
                player.takeKnockback(1.1,-away.x,-away.z);
            }
        }
    }

    private void wave(ServerWorld world, int tick, Vec3d center, Set<UUID> hitPlayers) {
        double previous = tick * WAVE_SPEED, radius = Math.min(WAVE_RANGE, (tick+1)*WAVE_SPEED);
        if (previous > WAVE_RANGE) return;
        int samples = Math.max(16, (int)(radius*10));
        for (int i=0; i<samples; i++) {
            double angle = Math.PI*2*i/samples;
            Vec3d p = GuardianWaveSurface.ground(world, guardian, center, center.x+Math.cos(angle)*radius, center.z+Math.sin(angle)*radius);
            if (p == null || !clearLine(world, guardian, center.add(0,.7,0), p.add(0,.7,0))) continue;
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    p.x, p.y+.12, p.z, 3, .12,.22,.12,.045);
            if (i%2 == 0) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x,p.y+.18,p.z,1,0,.05,0,0);
        }
        for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(guardian, p) && !hitPlayers.contains(p.getUuid()))) {
            Vec3d p = GuardianWaveSurface.ground(world, guardian, center, player.getX(), player.getZ());
            if (p == null || !waveContact(center, player.getBoundingBox(), previous, radius, p.y)) continue;
            if (!clearLine(world, guardian, center.add(0,.7,0), p.add(0,.7,0))) continue;
            hitPlayers.add(player.getUuid());
            if (player.damage(world, world.getDamageSources().mobAttack(guardian), 6)) {
                Vec3d delta = player.getEntityPos().subtract(center);
                player.takeKnockback(.7, -delta.x, -delta.z);
            }
        }
    }

    private void warning(ServerWorld world, double radius) {
        for (int i=0; i<24; i++) {
            double angle = active == Attack.SLAM
                    ? Math.toRadians(attackYaw) + Math.PI/2 + (i/23.0-.5)*2.6
                    : Math.PI*2*i/24;
            Vec3d p = ground(world, anchor.x+Math.cos(angle)*radius, anchor.z+Math.sin(angle)*radius);
            if (p != null) {
                if (active == Attack.SLAM) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK,
                        Blocks.STONE.getDefaultState()), p.x,p.y+.12,p.z,2,.05,.1,.05,.02);
                else world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x,p.y+.12,p.z,1,0,0,0,0);
            }
        }
    }

    private void face(Vec3d point, boolean immediate) {
        Vec3d delta = point.subtract(guardian.getEntityPos());
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        attackYaw = immediate ? yaw : MathHelper.stepUnwrappedAngleTowards(attackYaw, yaw, 6);
        guardian.setYaw(attackYaw); guardian.setBodyYaw(attackYaw); guardian.setHeadYaw(attackYaw);
    }

    private void freeze() {
        if (!frozen) { previousNoAi = guardian.isAiDisabled(); frozen = true; }
        guardian.getNavigation().stop(); guardian.setAiDisabled(true);
        guardian.setVelocity(0, guardian.getVelocity().y, 0);
    }
    private void unfreeze() { if (frozen) { guardian.setAiDisabled(previousNoAi); frozen = false; } }
    private void interruptAction() {
        leap.cancel();
        guardian.cancelCombatBeam();
        guardian.setHoldingRock(false);
        guardian.clearWave(); unfreeze(); active = null; waking = 0;
        if (heldRock != null) { heldRock.discard(); heldRock = null; }
        for (GuardianRockEntity rock : rocks) rock.discard();
        rocks.clear();
        guardian.getNavigation().stop(); guardian.stopTriggeredAnim("guardian", null);
    }
    private void impactSound(ServerWorld world) {
        world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.5f, .45f);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                guardian.getX(), guardian.getY()+.2, guardian.getZ(), 35, 2,.15,2,.08);
    }
}
