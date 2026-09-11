package com.anton.elementalwands.entity;

import com.anton.elementalwands.arena.GuardianArenaManager;
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
    private final GuardianNatureResponse nature = new GuardianNatureResponse();
    private final GuardianMotionSample motion = new GuardianMotionSample();
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
    private int comboIndex, comboCount = 1;
    private boolean leapFollowup, pendingFan;
    private long approachUntil;
    private float fanPitch;
    private final Map<UUID,Integer> hovering = new HashMap<>();
    private record TravelingWave(long start, Vec3d origin, Set<UUID> hits, boolean unstable, int slot, GuardianWallImpact walls) {}
    private final List<TravelingWave> waves = new ArrayList<>();

    GuardianBossCombat(FracturedGuardianEntity guardian) { this.guardian = guardian; this.leap = new GuardianLeapAttack(guardian,this); }

    static boolean canDamage(FracturedGuardianEntity guardian, ServerPlayerEntity player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.getEntityWorld() == guardian.getEntityWorld() && !guardian.isTeammate(player)
                && GuardianArenaManager.eligible(guardian,player);
    }

    static boolean clearLine(ServerWorld world, FracturedGuardianEntity guardian, Vec3d from, Vec3d to) {
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, guardian)).getType() == HitResult.Type.MISS;
    }

    void entangle(int stacks) { nature.entangle(stacks, guardian.getEntityWorld().getTime()); }
    void thorn() { nature.thorn(guardian.getEntityWorld().getTime()); }

    void cancel() {
        nature.reset(); hovering.clear(); pendingFan=false; approachUntil=0;
        leap.cancel();
        guardian.cancelCombatBeam();
        guardian.clearFan();
        guardian.setHoldingRock(false);
        guardian.clearWave(); waves.clear();
        unfreeze();
        active = null; waking = 0; engaged = false;
        guardian.finishPhase(); leapFollowup=false;
        target = movementTarget = null;
        ready.clear(); lastTargeted.clear(); last = null;
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
        double range=GuardianArenaManager.encounterRange(guardian);
        if (GuardianArenaManager.owns(guardian))
            guardian.getAttributeInstance(EntityAttributes.FOLLOW_RANGE).setBaseValue(range);
        List<ServerPlayerEntity> players = world.getPlayers(p -> canDamage(guardian, p)
                && p.squaredDistanceTo(home) <= range*range && guardian.squaredDistanceTo(p) <= range*range);
        rocks.removeIf(GuardianRockEntity::isRemoved);
        if (!engaged) {
            if (players.stream().noneMatch(p -> (GuardianArenaManager.owns(guardian) || guardian.squaredDistanceTo(p) <= 24*24) && guardian.canSee(p))) return;
            engaged = true; waking = guardian.isGuardOpening() || guardian.getPhaseTime(0) >= 0 || guardian.isUnstable() ? 0 : 84; emptySince = -1;
            guardian.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(.16);
            anchor = guardian.getEntityPos();
            if (waking > 0 || guardian.isGuardOpening() || guardian.getPhaseTime(0)>=0) freeze();
            if (waking > 0) guardian.triggerAnim("guardian", "awaken");
        }
        updateBar(players);
        if (players.isEmpty() || guardian.squaredDistanceTo(home) > range*range) {
            interruptAction();
            if (emptySince < 0) emptySince = now;
            if (now - emptySince >= 200) {
                cancel();
                guardian.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(healthForParty(1));
                guardian.setHealth(healthForParty(1));
                guardian.resetGuard();
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
        guardian.scaleGuard(players.size());
        hovering.keySet().removeIf(id -> players.stream().noneMatch(p -> p.getUuid().equals(id)));
        for (ServerPlayerEntity player : players) {
            var floor=world.raycast(new RaycastContext(player.getEntityPos(),player.getEntityPos().add(0,-16,0),
                    RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,guardian));
            double clearance=floor.getType()==HitResult.Type.MISS?16:player.getY()-floor.getPos().y;
            hovering.compute(player.getUuid(),(id,count) -> !player.isOnGround() && clearance>2.5
                    ? Math.min(60,(count==null?0:count)+1) : 0);
        }
        if (GuardianPhaseRules.threshold(guardian.getHealth(), guardian.getMaxHealth())) guardian.requestPhase();
        guardian.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(guardian.isUnstable() ? .29 : .24);
        if (waking > 0) {
            guardian.setVelocity(0, Math.min(0,guardian.getVelocity().y), 0);
            if (--waking == 51) impactSound(world);
            if (waking == 0) { unfreeze(); nextAction = now + 20; }
            return;
        }
        // Finish an airborne leap before interrupting it; never strand the boss in the air.
        if (!guardian.isGuardOpening() && guardian.getGuard() <= 0 && active != Attack.LEAP) {
            interruptAction();
            guardian.finishPhase();
            guardian.openGuard();
            freeze();
            guardian.triggerAnim("guardian", "guard_break");
            world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 2, .55f);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    guardian.getX(), guardian.getY()+3, guardian.getZ(), 60, 1.5, 1.4, 1.5, .15);
        }
        if (guardian.isGuardOpening()) {
            freeze();
            float elapsed = guardian.getGuardTime(0);
            if (elapsed == GuardianGuardRules.CLOSE_START)
                world.playSound(null, guardian.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 1.5f, .6f);
            if (elapsed >= GuardianGuardRules.CYCLE_TICKS) {
                guardian.finishGuard();
                guardian.stopTriggeredAnim("guardian", null);
                unfreeze();
                nextAction = now + RECOVERY_GAP;
            }
            return;
        }
        if (guardian.getPhaseTime(0) >= 0) {
            freeze();
            if (guardian.getPhaseTime(0) >= GuardianPhaseRules.TRANSITION_TICKS) {
                guardian.finishPhase(); guardian.stopTriggeredAnim("guardian", null);
                unfreeze(); nextAction = now + 16;
            }
            return;
        }
        if (guardian.phasePending() && active == null && guardian.isOnGround()) {
            interruptAction(); guardian.beginPhase(); freeze();
            guardian.triggerAnim("guardian", "phase_change");
            world.playSound(null, guardian.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundCategory.HOSTILE, 2, .65f);
            return;
        }
        if (active != null) { tickAttack(world, now); return; }
        if (now < nextAction) return;
        List<Candidate> candidates = players.stream().map(p -> new Candidate(p.getUuid(),
                guardian.distanceTo(p), guardian.canSee(p))).toList();
        if (!pendingFan && now < approachUntil) { pursue(players,now); return; }
        Attack choice = choose(candidates, ready, now, last, guardian.isUnstable());
        Candidate aerial = target(Attack.FAN,candidates.stream()
                .filter(p -> hovering.getOrDefault(p.id(),0)>=GuardianFanRules.HOVER_TICKS).toList(),lastTargeted,guardian.isUnstable());
        if (now>=ready.getOrDefault(Attack.FAN,0L) && last!=Attack.FAN
                && target(Attack.FAN,candidates,lastTargeted,guardian.isUnstable())!=null
                && (pendingFan || aerial!=null || guardian.getRandom().nextInt(3)==0)) choice=Attack.FAN;
        Candidate clearingTarget = null;
        if (nature.wantsClear(now)) {
            for (Attack clearing : new Attack[]{Attack.SLAM, Attack.SHOCKWAVE}) {
                Candidate candidate = target(clearing, candidates, lastTargeted, guardian.isUnstable());
                // A local clearing slam still makes sense when players stand beyond its wave.
                if (candidate == null && clearing == Attack.SHOCKWAVE) candidate = candidates.stream()
                        .filter(p -> p.visible() && p.distance() <= 32)
                        .min(java.util.Comparator.comparingDouble(Candidate::distance)).orElse(null);
                if (clearing != last && now >= ready.getOrDefault(clearing, 0L) && candidate != null) {
                    choice = clearing; clearingTarget = candidate;
                    break;
                }
            }
        }
        if (choice != null && guardian.isOnGround()) {
            Candidate selected = clearingTarget != null ? clearingTarget
                    : choice==Attack.FAN && aerial!=null ? aerial : target(choice,candidates,lastTargeted,guardian.isUnstable());
            if (clearingTarget != null) nature.clearing(now);
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(selected.id());
            begin(world, choice, player, now);
            // Mix a single slam into a separately telegraphed fan; retain the full triple-slam pattern too.
            if (guardian.isUnstable() && (choice==Attack.SLAM || choice==Attack.SHOCKWAVE)
                    && now>=ready.getOrDefault(Attack.FAN,0L) && guardian.getRandom().nextBoolean()) {
                comboCount=1; pendingFan=true;
            }
        } else {
            pursue(players,now);
        }
    }

    private void pursue(List<ServerPlayerEntity> players,long now) {
        ServerPlayerEntity destination=players.stream().min(java.util.Comparator.comparingDouble(guardian::squaredDistanceTo)).orElse(null);
        if(destination==null || guardian.squaredDistanceTo(destination)<6*6) { guardian.getNavigation().stop(); approachUntil=0; return; }
        if(now<nextMove)return;
        nextMove=now+8; movementTarget=destination.getUuid();
        Vec3d delta=destination.getEntityPos().subtract(home);
        double reach=GuardianArenaManager.movementRange(guardian);
        Vec3d point=GuardianArenaManager.owns(guardian)
                ? new Vec3d(Math.clamp(destination.getX(),home.x-reach,home.x+reach),home.y,Math.clamp(destination.getZ(),home.z-reach,home.z+reach))
                : delta.length()>reach?home.add(delta.normalize().multiply(reach)):destination.getEntityPos();
        guardian.getNavigation().startMovingTo(point.x,point.y,point.z,1);
    }

    private void updateBar(List<ServerPlayerEntity> players) {
        var viewers=new ArrayList<>(players);
        if(guardian.getEntityWorld() instanceof ServerWorld world)
            viewers.addAll(world.getPlayers(p -> GuardianArenaManager.spectatorViewer(guardian,p)));
        for (ServerPlayerEntity previous : new ArrayList<>(bar.getPlayers())) if (!viewers.contains(previous)) bar.removePlayer(previous);
        for (ServerPlayerEntity player : viewers) bar.addPlayer(player);
        bar.setPercent(MathHelper.clamp(guardian.getHealth()/Math.max(1, guardian.getMaxHealth()), 0, 1));
    }

    private void begin(ServerWorld world, Attack attack, ServerPlayerEntity player, long now) {
        reviewing = false; pendingFan=false; approachUntil=0;
        comboIndex = 0; comboCount = GuardianPhaseRules.repeats(attack, guardian.isUnstable());
        leapFollowup = attack == Attack.LEAP && guardian.isUnstable();
        active = attack; last = attack; target = player.getUuid(); started = now;
        anchor = guardian.getEntityPos();
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
        if (attack == Attack.FAN) {
            freeze(); lockedAim=player.getBoundingBox().getCenter();
            fanPitch=aimPitch(lockedAim); guardian.syncFan(now,fanPitch); guardian.triggerAnim("guardian","fan");
            return;
        }
        freeze();
        triggerPhysicalAnimation();
        world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, .8f, .55f);
        lockedAim = player.getBoundingBox().getCenter();
    }

    private boolean fastSlam() {
        return guardian.isUnstable() && comboCount == 3 && comboIndex < 2;
    }
    private void triggerPhysicalAnimation() {
        guardian.stopTriggeredAnim("guardian", null);
        guardian.triggerAnim("guardian", active == Attack.THROW
                ? (guardian.isUnstable() ? "throw_fast" : "throw") : (fastSlam() ? "slam_fast" : "slam"));
    }
    private int physicalDuration() {
        return active == Attack.THROW ? GuardianPhaseRules.throwDuration(guardian.isUnstable())
                : GuardianPhaseRules.slamDuration(fastSlam());
    }
    private void finishAction(long now) {
        // Cooldowns begin after the whole combo. The laser retains its original clock.
        ready.put(active, now + (active == Attack.BEAM ? active.cooldown
                : Math.round(active.cooldown * (guardian.isUnstable() ? .8f : 1))));
        int gap = active == Attack.BEAM ? RECOVERY_GAP : GuardianPhaseRules.gap(guardian.isUnstable());
        guardian.clearFan();
        unfreeze(); active = null; heldRock = null;
        guardian.setHoldingRock(false);
        guardian.getNavigation().stop();
        nextAction = now + gap + nature.finishAttack(now);
        approachUntil = reviewing || pendingFan ? 0 : nextAction + 20;
    }
    private void tickAttack(ServerWorld world, long now) {
        if (active == Attack.FAN) { tickFan(world,now); return; }
        if (active == Attack.LEAP) {
            if (leap.tick(world)) {
                if (leapFollowup && leap.status().equals("Last leap completed.") && guardian.getGuard() > 0) {
                    leapFollowup = false;
                    ready.put(Attack.LEAP, now + 144);
                    active = Attack.SHOCKWAVE; comboIndex = 0; comboCount = 1; started = now;
                    anchor = guardian.getEntityPos(); triggerPhysicalAnimation();
                } else finishAction(now);
            }
            return;
        }
        int tick = (int)(now - started);
        if (guardian.getEntityPos().squaredDistanceTo(anchor) > .75*.75) { interruptAction(); nextAction = now + 30; return; }
        if (tick >= (active == Attack.BEAM ? active.duration : physicalDuration())) {
            if (active != Attack.BEAM && ++comboIndex < comboCount) {
                // Each rock acquires a fresh eligible target/aim, never bends after release.
                List<Candidate> candidates = world.getPlayers(p -> canDamage(guardian,p)).stream()
                        .map(p -> new Candidate(p.getUuid(),guardian.distanceTo(p),guardian.canSee(p))).toList();
                Candidate candidate = target(active,candidates,lastTargeted,guardian.isUnstable());
                if (candidate != null) {
                    target = candidate.id(); lastTargeted.put(target,now);
                    motion.reset(world.getServer().getPlayerManager().getPlayer(target).getEntityPos());
                } else if (active == Attack.THROW) { finishAction(now); return; }
                started = now; tick = 0;
                triggerPhysicalAnimation();
            } else { finishAction(now); return; }
        }
        if (active == Attack.BEAM) return;
        guardian.setVelocity(0, Math.min(0,guardian.getVelocity().y), 0);
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(target);
        int release = GuardianPhaseRules.throwRelease(guardian.isUnstable());
        int lock = active == Attack.THROW ? GuardianPhaseRules.throwLock(guardian.isUnstable()) : 14;
        double socketTick = active == Attack.THROW && guardian.isUnstable() ? tick * (44.0/29) : tick;
        if (tick < lock && player != null && canDamage(guardian, player) && guardian.squaredDistanceTo(player) <= 60*60) {
            face(player.getEntityPos(), false);
            Vec3d velocity = motion.observe(player.getEntityPos());
            lockedAim = active == Attack.THROW
                    ? throwAim(GuardianThrowSocket.worldPosition(guardian, THROW_RELEASE),
                            player.getBoundingBox().getCenter(), velocity, release-tick)
                    : player.getBoundingBox().getCenter();
        } else if (tick < lock && active == Attack.THROW && !reviewing) {
            interruptAction(); nextAction = now+20; return;
        }
        guardian.setYaw(attackYaw); guardian.setBodyYaw(attackYaw); guardian.setHeadYaw(attackYaw);
        if (active == Attack.THROW) {
            if (tick == (guardian.isUnstable() ? 8 : 12)) {
                heldRock = new GuardianRockEntity(com.anton.elementalwands.registry.ModEntities.GUARDIAN_ROCK, world);
                heldRock.setOwner(guardian);
                heldRock.setPosition(GuardianThrowSocket.worldPosition(guardian, (int)Math.round(socketTick)));
                world.spawnEntity(heldRock); rocks.add(heldRock);
                guardian.setHoldingRock(true);
            }
            if (heldRock != null && tick <= release) heldRock.setPosition(GuardianThrowSocket.worldPosition(guardian, (int)Math.round(Math.min(THROW_RELEASE,socketTick))));
            if (tick == release && heldRock != null) {
                guardian.setHoldingRock(false); heldRock.release(lockedAim); heldRock = null;
                world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.2f, .7f);
            }
        } else if (tick == GuardianPhaseRules.slamImpact(fastSlam())) {
            impactSound(world);
            crushGrowth(world, anchor, 4.5, false, 6);
            emitWave(world, anchor, Set.of());
        }
    }

    private float aimPitch(Vec3d aim) {
        Vec3d delta=aim.subtract(guardian.getEntityPos().add(0,4,0));
        return (float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalLength()));
    }

    private void tickFan(ServerWorld world,long now) {
        int age=(int)(now-started);
        if (age>=GuardianFanRules.duration(guardian.isUnstable())) { finishAction(now); return; }
        if (guardian.getEntityPos().squaredDistanceTo(anchor)>.75*.75) { interruptAction(); nextAction=now+20; return; }
        int tick=guardian.isUnstable() && age>=GuardianFanRules.REPEAT?age-GuardianFanRules.REPEAT:age;
        if (guardian.isUnstable() && age==GuardianFanRules.REPEAT) {
            var candidates=world.getPlayers(p -> reviewing ? p.isAlive() && !p.isSpectator() : canDamage(guardian,p)).stream()
                    .map(p -> new Candidate(p.getUuid(),guardian.distanceTo(p),guardian.canSee(p))).toList();
            Candidate next=target(Attack.FAN,candidates,lastTargeted,guardian.isUnstable());
            if(next==null) { finishAction(now); return; }
            target=next.id(); lastTargeted.put(target,now);
            motion.reset(world.getServer().getPlayerManager().getPlayer(target).getEntityPos());
            guardian.stopTriggeredAnim("guardian",null);guardian.triggerAnim("guardian","fan");
        }
        if (tick<GuardianFanRules.LOCK) {
            ServerPlayerEntity player=world.getServer().getPlayerManager().getPlayer(target);
            if(player==null || !player.isAlive() || player.isSpectator() || player.getEntityWorld()!=world
                    || (!reviewing && !canDamage(guardian,player)) || guardian.distanceTo(player)>48) { interruptAction();nextAction=now+20;return; }
            face(player.getEntityPos(),false);
            Vec3d velocity=motion.observe(player.getEntityPos());
            int leadTicks=GuardianFanRules.RELEASE-tick+(int)(guardian.distanceTo(player)/GuardianFanRules.SPEED);
            lockedAim=lead(player.getBoundingBox().getCenter(),velocity,leadTicks);
            fanPitch=aimPitch(lockedAim);
        }
        guardian.syncFan(started,fanPitch);
        guardian.setYaw(attackYaw);guardian.setBodyYaw(attackYaw);guardian.setHeadYaw(attackYaw);
        guardian.setVelocity(0,Math.min(0,guardian.getVelocity().y),0);
        if (tick==8) world.playSound(null,guardian.getBlockPos(),SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,SoundCategory.HOSTILE,1.6f,.6f);
        if (tick==GuardianFanRules.RELEASE) {
            var hits=new HashSet<UUID>(); var walls=new GuardianWallImpact();
            for(int i=0;i<GuardianFanRules.COUNT;i++) {
                Vec3d socket=GuardianFanRules.socket(guardian.getEntityPos(),attackYaw,fanPitch,i);
                Vec3d source=guardian.getEntityPos().add(0,3.3,0);
                // Floating stones cannot materialize on the far side of solid cover.
                if (walls.clip(world,guardian,source,socket,false,true).squaredDistanceTo(socket)>1e-10) continue;
                var shard=new GuardianRockEntity(com.anton.elementalwands.registry.ModEntities.GUARDIAN_ROCK,world);
                shard.setOwner(guardian);shard.setPosition(socket);
                shard.releaseShard(GuardianFanRules.direction(socket,lockedAim,i),hits,walls);
                world.spawnEntity(shard);rocks.add(shard);
            }
            world.playSound(null,guardian.getBlockPos(),SoundEvents.ENTITY_IRON_GOLEM_ATTACK,SoundCategory.HOSTILE,1.6f,1.1f);
        }
    }

    /** Waves finish independently of the next action; interruption cancels every pending hazard. */
    void emitWave(ServerWorld world, Vec3d center, Set<UUID> exempt) {
        expireWaves(world);
        int slot = waves.stream().anyMatch(w -> w.slot()==0) ? 1 : 0;
        if (waves.stream().anyMatch(w -> w.slot()==slot)) throw new IllegalStateException("Guardian wave slots exhausted");
        boolean unstable = guardian.isUnstable();
        waves.add(new TravelingWave(world.getTime(), center, new HashSet<>(exempt), unstable, slot, new GuardianWallImpact()));
        guardian.startWaveAt(world.getTime()-SLAM_IMPACT, center, slot);
    }

    private void expireWaves(ServerWorld world) {
        waves.removeIf(w -> {
            if (world.getTime()-w.start() < GuardianPhaseRules.waveTicks(w.unstable())) return false;
            guardian.clearWave(w.slot()); return true;
        });
    }
    void clearWaves() { waves.clear(); guardian.clearWave(); }

    void tickEffects(ServerWorld world) {
        expireWaves(world);
        for (TravelingWave w : waves)
            wave(world, (int)(world.getTime()-w.start()), w.origin(), w.hits(), w.unstable(), w.walls());
        if (guardian.isUnstable() && !guardian.isArenaHidden() && (engaged || reviewing) && world.getTime()%4 == 0) {
            int count = guardian.getPhaseTime(0) >= 0 ? 7 : 2;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, guardian.getX(),guardian.getY()+3.3,guardian.getZ(),count,1.1,.8,1.1,.04);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, guardian.getX(),guardian.getY()+3,guardian.getZ(),count,1.5,1.5,1.5,.14);
        }
        if (guardian.getPhaseTime(0) == 32 && !guardian.isArenaHidden())
            world.playSound(null,guardian.getBlockPos(),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.HOSTILE,1.8f,.8f);
    }

    void crushGrowth(ServerWorld world, Vec3d center, double radius, boolean frontal, float treeDamage) {
        Vec3d forward = Vec3d.fromPolar(0, attackYaw);
        java.util.function.Predicate<BlockPos> hit = pos -> {
            Vec3d point = Vec3d.ofCenter(pos), delta = point.subtract(center);
            return delta.horizontalLength() <= radius && Math.abs(delta.y) <= 2
                    && (!frontal || delta.horizontalLength() <= .5
                        || forward.dotProduct(new Vec3d(delta.x,0,delta.z).normalize()) >= .25)
                    && GuardianWaveSurface.clearLine(world,guardian,center.add(0,1,0),point.add(0,.6,0));
        };
        com.anton.elementalwands.util.SeedlingManager.crushGrowth(world, hit);
        com.anton.elementalwands.util.TendrilBloomManager.crushGrowth(world, hit);
        for (AwakenedTreeEntity tree : world.getEntitiesByClass(AwakenedTreeEntity.class,
                guardian.getBoundingBox().expand(radius,3,radius), e -> e.isAlive())) {
            Vec3d delta = tree.getEntityPos().subtract(center);
            if (delta.horizontalLength() > radius || Math.abs(delta.y) > 3
                    || (frontal && delta.horizontalLength() > .5 && forward.dotProduct(new Vec3d(delta.x,0,delta.z).normalize()) < .25)) continue;
            Vec3d from = center.add(0,1,0);
            var cover = world.raycast(new RaycastContext(from,tree.getBoundingBox().getCenter(),
                    RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,guardian));
            // The tree's own log shell is part of its damageable body, not external cover.
            if (cover.getType() == HitResult.Type.MISS || tree.getBoundingBox().expand(.1).contains(cover.getPos()))
                tree.damage(world, world.getDamageSources().mobAttack(guardian), treeDamage);
        }
    }

    void leapLanded(ServerWorld world, Set<UUID> impactVictims) {
        Vec3d center = guardian.getEntityPos();
        crushGrowth(world, center, 6, false, 10);
        GuardianWallImpact walls=new GuardianWallImpact();
        for(BlockPos pos:com.anton.elementalwands.item.StoneAbilityHandler.guardianWallBlocks(world)) {
            if(GuardianLeapRules.damage(center,new net.minecraft.util.math.Box(pos))>0)
                walls.clip(world,guardian,center.add(0,1,0),pos.toCenterPos(),false,true);
        }
        impactSound(world);
        world.playSound(null,guardian.getBlockPos(),SoundEvents.ENTITY_GENERIC_EXPLODE.value(),SoundCategory.HOSTILE,1.6f,.65f);
        for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(guardian,p))) {
            float damage = GuardianLeapRules.damage(center,player.getBoundingBox());
            if (damage <= 0 || !walls.clear(world,guardian,center.add(0,1,0),player.getBoundingBox().getCenter(),false)) continue;
            impactVictims.add(player.getUuid());
            if (player.damage(world,world.getDamageSources().mobAttack(guardian),damage)) {
                Vec3d away = player.getEntityPos().subtract(center);
                player.takeKnockback(1.1,-away.x,-away.z);
            }
        }
    }

    private void wave(ServerWorld world, int tick, Vec3d center, Set<UUID> hitPlayers, boolean unstable, GuardianWallImpact walls) {
        double speed = GuardianPhaseRules.waveSpeed(unstable), range = GuardianPhaseRules.waveRange(unstable);
        double previous = tick * speed, radius = Math.min(range, (tick+1)*speed);
        if (previous >= range) return;
        for(BlockPos pos:com.anton.elementalwands.item.StoneAbilityHandler.guardianWallBlocks(world)) {
            double distance=Math.hypot(pos.getX()+.5-center.x,pos.getZ()+.5-center.z);
            if(distance<previous-1.5 || distance>radius+1.5)continue;
            Vec3d floor=GuardianWaveSurface.groundUnderWall(world,guardian,center,pos);
            if(floor==null || !waveContact(center,new net.minecraft.util.math.Box(pos),previous,radius,floor.y))continue;
            walls.clip(world,guardian,center.add(0,.7,0),new Vec3d(pos.getX()+.5,floor.y+.7,pos.getZ()+.5),true,true);
        }
        // Crush only tracked Nature growth reached by this traveling band. Real
        // defensive cover still protects plants behind it; primary spells do not.
        java.util.function.Predicate<BlockPos> contacted = pos -> {
            double distance = Math.hypot(pos.getX()+.5-center.x,pos.getZ()+.5-center.z);
            if (distance < previous-1 || distance > radius+1) return false;
            Vec3d surface = GuardianWaveSurface.ground(world,guardian,center,pos.getX()+.5,pos.getZ()+.5);
            return surface != null && waveContact(center,new net.minecraft.util.math.Box(pos),previous,radius,surface.y)
                    && walls.clear(world,guardian,center.add(0,.7,0),surface.add(0,.7,0),true);
        };
        com.anton.elementalwands.util.SeedlingManager.crushGrowth(world,contacted);
        com.anton.elementalwands.util.TendrilBloomManager.crushGrowth(world,contacted);
        // The continuous ridge is client-rendered. Keep server effects bounded as the radius grows.
        int samples = tick % 3 == 0 ? 24 : 0;
        for (int i=0; i<samples; i++) {
            double angle = Math.PI*2*i/samples;
            Vec3d p = GuardianWaveSurface.ground(world, guardian, center, center.x+Math.cos(angle)*radius, center.z+Math.sin(angle)*radius);
            if (p == null || !walls.clear(world,guardian,center.add(0,.7,0),p.add(0,.7,0),true)) continue;
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    p.x, p.y+.12, p.z, 3, .12,.22,.12,.045);
            if (i%2 == 0) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x,p.y+.18,p.z,1,0,.05,0,0);
        }
        for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(guardian, p) && !hitPlayers.contains(p.getUuid()))) {
            if (Math.hypot(player.getX()-center.x,player.getZ()-center.z) > radius+2) continue;
            Vec3d p = GuardianWaveSurface.ground(world, guardian, center, player.getX(), player.getZ());
            if (p == null || !waveContact(center, player.getBoundingBox(), previous, radius, p.y)) continue;
            if (!walls.clear(world,guardian,center.add(0,.7,0),p.add(0,.7,0),true)) continue;
            hitPlayers.add(player.getUuid());
            if (player.damage(world, world.getDamageSources().mobAttack(guardian), 6)) {
                Vec3d delta = player.getEntityPos().subtract(center);
                player.takeKnockback(.7, -delta.x, -delta.z);
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
        guardian.setVelocity(0, Math.min(0,guardian.getVelocity().y), 0);
    }
    private void unfreeze() { if (frozen) { guardian.setAiDisabled(previousNoAi); frozen = false; } }
    private void interruptAction() {
        leap.cancel();
        guardian.cancelCombatBeam();
        guardian.setHoldingRock(false);
        guardian.clearFan(); pendingFan=false; approachUntil=0;
        guardian.clearWave(); waves.clear(); leapFollowup=false; unfreeze(); active = null; waking = 0;
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
