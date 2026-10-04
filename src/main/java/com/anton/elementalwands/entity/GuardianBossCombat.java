package com.anton.elementalwands.entity;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
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

/**
 * Server-authoritative encounter: one committed action at a time, threat-aware targeting, real
 * recovery. Co-op adds pressure up to four players (shorter rests, back volleys at the players it
 * is not attacking); the world's difficulty sets the intensity.
 */
final class GuardianBossCombat {
    private static final BlockStateParticleEffect STONE = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState());
    private static final DustParticleEffect PULSE_RING = new DustParticleEffect(0x3CE1FF, 1.6f);
    private final FracturedGuardianEntity guardian;
    private final ServerBossBar bar = new ServerBossBar(Text.literal("Fractured Guardian"), BossBar.Color.BLUE, BossBar.Style.PROGRESS);
    private final Map<Attack, Long> ready = new EnumMap<>(Attack.class);
    private final Map<UUID, Long> lastTargeted = new HashMap<>();
    private final GuardianLeapAttack leap;
    private final GuardianNatureResponse nature = new GuardianNatureResponse();
    private final GuardianMotionSample motion = new GuardianMotionSample();
    private final GuardianFightLog log = new GuardianFightLog();
    private final List<GuardianRockEntity> rocks = new ArrayList<>();
    private Vec3d home, anchor, lockedAim;
    private UUID target;
    private Attack active, last;
    private long started, nextAction, emptySince = -1, nextMove, nextVolley;
    private boolean engaged, frozen, previousNoAi;
    private boolean reviewing;
    private int waking;
    private float attackYaw;
    private GuardianRockEntity heldRock;
    /** Ticks the current slam pauses at the top of its swing (0 for an ordinary slam). */
    private int comboIndex, comboCount = 1, slamHold;
    private int attacksSinceBeam;
    private boolean leapFollowup, pendingFan;
    private long approachUntil;
    private float fanPitch;
    /** Party size and difficulty as of the last fight tick; pressure scales with both. */
    private int party = 1;
    private Difficulty difficulty = Difficulty.NORMAL;
    private final Map<UUID,Integer> hovering = new HashMap<>();
    /** Recent damage each player dealt it, fading with {@link GuardianCombatRules#THREAT_DECAY}. */
    private record Threat(float amount, long at) {
        float current(long now) { return amount * (float)Math.exp(-(now - at) / (double)THREAT_DECAY); }
    }
    private final Map<UUID, Threat> threat = new HashMap<>();
    private final Map<UUID, Vec3d> lastSeen = new HashMap<>(), drift = new HashMap<>();
    private final Map<UUID, Integer> stillTicks = new HashMap<>();
    private record TravelingWave(long start, Vec3d origin, Set<UUID> hits, boolean unstable, int slot, GuardianWallImpact walls) {}
    private final List<TravelingWave> waves = new ArrayList<>();
    private record Volley(UUID target, List<GuardianRockEntity> stones, int cluster, int clusters, long started) {}
    private final List<Volley> volleys = new ArrayList<>();

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

    GuardianFightLog log() { return log; }
    /** Ends the tuning record, if one is running. Safe to call more than once. */
    void finishLog(String outcome) { log.finish(guardian, outcome); }

    /** Health the Guardian actually lost: it builds that player's threat and goes to the fight log. */
    void damaged(ServerWorld world, Entity attacker, float lost) {
        long now = world.getTime();
        log.bossHit(now, attacker, lost);
        if (attacker instanceof ServerPlayerEntity player)
            threat.merge(player.getUuid(), new Threat(lost, now), (old, added) -> new Threat(old.current(now) + lost, now));
    }
    private float threat(UUID id, long now) { Threat t = threat.get(id); return t == null ? 0 : t.current(now); }

    /** Every Guardian hit on a player lands here, so hits swallowed by hit immunity reach the log. */
    boolean strike(ServerWorld world, ServerPlayerEntity player, DamageSource source, float amount, String what) {
        boolean dealt = player.damage(world, source, amount);
        if (!dealt && player.isAlive()) log.lostHit(world.getTime(), player, what, amount);
        return dealt;
    }

    void cancel() {
        nature.reset(); guardian.setNatureOpening(-1); hovering.clear(); pendingFan=false; approachUntil=0;
        leap.cancel();
        guardian.cancelCombatBeam();
        guardian.clearFan(); guardian.clearPulse();
        guardian.setHoldingRock(false);
        guardian.clearWave(); waves.clear(); volleys.clear();
        unfreeze();
        active = null; waking = 0; engaged = false;
        guardian.finishPhase(); leapFollowup=false;
        target = null;
        reviewing = false;
        ready.clear(); lastTargeted.clear(); last = null; attacksSinceBeam = 0; nextVolley = 0;
        threat.clear(); lastSeen.clear(); drift.clear(); stillTicks.clear();
        for (GuardianRockEntity rock : rocks) rock.discard();
        rocks.clear(); heldRock = null;
        bar.clearPlayers();
        guardian.getNavigation().stop();
        guardian.stopTriggeredAnim("guardian", null);
    }

    String leapStatus() { return leap.status(); }

    void start() {
        finishLog("restarted");
        cancel();
        home = guardian.getEntityPos();
        nextAction = guardian.getEntityWorld().getTime() + 20;
        guardian.setAiDisabled(false);
        guardian.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(.16);
    }

    /** One rehearsed attack at its standard timing: no delayed slam and no back volley. */
    void testAttack(ServerPlayerEntity player, Attack attack) {
        difficulty = player.getEntityWorld().getDifficulty();
        begin((ServerWorld)guardian.getEntityWorld(), attack, player, guardian.getEntityWorld().getTime(), true);
    }

    void tickReview(ServerWorld world) {
        rocks.removeIf(GuardianRockEntity::isRemoved);
        if (active != null) tickAttack(world, world.getTime());
    }

    void tick(ServerWorld world) {
        if (!guardian.isAlive() || world.getDifficulty() == Difficulty.PEACEFUL) {
            finishLog(guardian.isAlive() ? "stopped: Peaceful" : "defeated");
            cancel();
            return;
        }
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
            engaged = true; waking = guardian.isBreaking() || guardian.isUnstable() ? 0 : 84; emptySince = -1;
            guardian.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(.16);
            anchor = guardian.getEntityPos();
            if (waking > 0 || guardian.isBreaking()) freeze();
            if (waking > 0) guardian.triggerAnim("guardian", "awaken");
            log.begin(now, players.size(), " on " + world.getDifficulty().asString() + ", "
                    + Math.max(healthForParty(players.size()), Math.round(guardian.getMaxHealth())) + " health");
        }
        updateBar(players);
        if (players.isEmpty() || guardian.squaredDistanceTo(home) > range*range) {
            interruptAction();
            if (emptySince < 0) emptySince = now;
            if (now - emptySince >= 200) {
                finishLog("reset: nobody left");
                cancel();
                guardian.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(healthForParty(1));
                guardian.setHealth(healthForParty(1));
                guardian.resetShell();
                // Displaced encounters settle at their new position; there is no surprise teleport.
                home = guardian.getEntityPos();
            }
            return;
        }
        emptySince = -1;
        party = players.size();
        difficulty = world.getDifficulty();
        int health = healthForParty(players.size());
        if (health > guardian.getMaxHealth()) {
            float extra = health - guardian.getMaxHealth();
            guardian.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health);
            guardian.setHealth(guardian.getHealth() + extra); // Preserve damage already dealt; never shrink mid-fight.
            guardian.updateCracks();
        }
        trackPlayers(world, players);
        if (GuardianPhaseRules.threshold(guardian.getHealth(), guardian.getMaxHealth())) guardian.requestPhase();
        guardian.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(guardian.isUnstable() ? .29 : .24);
        if (waking > 0) {
            guardian.setVelocity(0, Math.min(0,guardian.getVelocity().y), 0);
            if (--waking == 51) impactSound(world);
            if (waking == 0) { unfreeze(); nextAction = now + 20; }
            return;
        }
        tickVolleys(world, now);
        if (guardian.isBreaking()) {
            freeze();
            int time = (int)guardian.getPhaseTime(0);
            tickBreak(world, time);
            if (time >= GuardianPhaseRules.TRANSITION_TICKS) {
                guardian.finishPhase(); guardian.stopTriggeredAnim("guardian", null);
                unfreeze(); nextAction = now + 16;
                ready.put(Attack.PULSE, now + GuardianPulseRules.FIRST_DELAY);
                log.stage(now, "open core");
            }
            return;
        }
        if (guardian.phasePending() && active == null && guardian.isOnGround()) {
            interruptAction(); guardian.beginPhase(); freeze();
            guardian.triggerAnim("guardian", "phase_change");
            world.playSound(null, guardian.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundCategory.HOSTILE, 2, .65f);
            log.stage(now, "shell break");
            return;
        }
        if (active != null) { tickAttack(world, now); return; }
        if (now < nextAction) return;
        boolean unstable = guardian.isUnstable();
        List<Candidate> candidates = candidates(players, now);
        if (!pendingFan && now < approachUntil) { pursue(players,now); return; }
        boolean reservedBeam = beamDue(candidates, ready, now, last, attacksSinceBeam);
        Attack choice = choose(candidates, ready, now, last, unstable, attacksSinceBeam);
        Candidate aerial = target(Attack.FAN,candidates.stream()
                .filter(p -> hovering.getOrDefault(p.id(),0)>=GuardianFanRules.HOVER_TICKS).toList(),lastTargeted,now,unstable,null);
        if (!reservedBeam && choice != Attack.PULSE && now>=ready.getOrDefault(Attack.FAN,0L) && last!=Attack.FAN
                && target(Attack.FAN,candidates,lastTargeted,now,unstable,null)!=null
                && (pendingFan || aerial!=null || guardian.getRandom().nextInt(3)==0)) choice=Attack.FAN;
        Candidate clearingTarget = null;
        if (!reservedBeam && choice != Attack.PULSE && nature.wantsClear(now)) {
            for (Attack clearing : new Attack[]{Attack.SLAM, Attack.SHOCKWAVE}) {
                Candidate candidate = target(clearing, candidates, lastTargeted, now, unstable, null);
                // A local clearing slam still makes sense when players stand beyond its wave.
                if (candidate == null && clearing == Attack.SHOCKWAVE) candidate = candidates.stream()
                        .filter(p -> p.visible() && p.distance() <= 32)
                        .min(Comparator.comparingDouble(Candidate::distance)).orElse(null);
                if (clearing != last && now >= ready.getOrDefault(clearing, 0L) && candidate != null) {
                    choice = clearing; clearingTarget = candidate;
                    break;
                }
            }
        }
        if (choice != null && guardian.isOnGround()) {
            Candidate selected = clearingTarget != null ? clearingTarget
                    : choice==Attack.FAN && aerial!=null ? aerial : target(choice,candidates,lastTargeted,now,unstable,null);
            if (clearingTarget != null) nature.clearing(now);
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(selected.id());
            begin(world, choice, player, now, false);
            // Mix a single slam into a separately telegraphed fan; retain the full triple-slam pattern too.
            if (unstable && (choice==Attack.SLAM || choice==Attack.SHOCKWAVE)
                    && now>=ready.getOrDefault(Attack.FAN,0L) && guardian.getRandom().nextBoolean()) {
                comboCount=1; pendingFan=true;
            }
        } else {
            pursue(players,now);
        }
    }

    /** Hovering, a smoothed walking velocity for aim leads, and how long each player has stood still. */
    private void trackPlayers(ServerWorld world, List<ServerPlayerEntity> players) {
        Set<UUID> present = new HashSet<>();
        for (ServerPlayerEntity player : players) {
            UUID id = player.getUuid();
            present.add(id);
            var floor=world.raycast(new RaycastContext(player.getEntityPos(),player.getEntityPos().add(0,-16,0),
                    RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,guardian));
            double clearance=floor.getType()==HitResult.Type.MISS?16:player.getY()-floor.getPos().y;
            hovering.compute(id,(key,count) -> !player.isOnGround() && clearance>2.5
                    ? Math.min(60,(count==null?0:count)+1) : 0);
            Vec3d position = player.getEntityPos(), previous = lastSeen.put(id, position);
            // A teleport or dash is not walking; it never inflates an aim lead.
            Vec3d step = previous == null || previous.squaredDistanceTo(position) > 1 ? Vec3d.ZERO : position.subtract(previous);
            drift.merge(id, step, (old, latest) -> old.multiply(.6).add(latest.multiply(.4)));
            boolean slow = step.horizontalLengthSquared() < STILL_SPEED * STILL_SPEED;
            stillTicks.merge(id, slow ? 1 : 0, (old, add) -> add == 0 ? 0 : Math.min(100, old + 1));
        }
        hovering.keySet().retainAll(present); lastSeen.keySet().retainAll(present);
        drift.keySet().retainAll(present); stillTicks.keySet().retainAll(present);
    }

    private List<Candidate> candidates(List<ServerPlayerEntity> players, long now) {
        return players.stream().map(p -> new Candidate(p.getUuid(), guardian.distanceTo(p), guardian.canSee(p),
                threat(p.getUuid(), now), stillTicks.getOrDefault(p.getUuid(), 0) >= STILL_TICKS)).toList();
    }

    private void pursue(List<ServerPlayerEntity> players,long now) {
        ServerPlayerEntity destination=players.stream().min(Comparator.comparingDouble(guardian::squaredDistanceTo)).orElse(null);
        if(destination==null || guardian.squaredDistanceTo(destination)<6*6) { guardian.getNavigation().stop(); approachUntil=0; return; }
        if(now<nextMove)return;
        nextMove=now+8;
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

    private void begin(ServerWorld world, Attack attack, ServerPlayerEntity player, long now, boolean rehearsal) {
        reviewing = rehearsal; pendingFan=false; approachUntil=0;
        comboIndex = 0; comboCount = GuardianPhaseRules.repeats(attack, guardian.isUnstable());
        leapFollowup = attack == Attack.LEAP && guardian.isUnstable();
        active = attack; last = attack; target = player.getUuid(); started = now; slamHold = 0;
        attacksSinceBeam = attack == Attack.BEAM ? 0 : Math.min(2, attacksSinceBeam + 1);
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
                return;
            }
            log.cast(now, "leap", player.getName().getString());
            return;
        }
        if (attack == Attack.BEAM) {
            guardian.beginCombatBeam(player);
            log.cast(now, "beam", player.getName().getString());
            volley(world, now);
            return;
        }
        if (attack == Attack.FAN) {
            freeze(); lockedAim=player.getBoundingBox().getCenter();
            fanPitch=aimPitch(lockedAim); guardian.syncFan(now,fanPitch); guardian.triggerAnim("guardian","fan");
            log.cast(now, "fan", player.getName().getString());
            return;
        }
        if (attack == Attack.PULSE) {
            freeze();
            guardian.syncPulse(now);
            guardian.stopTriggeredAnim("guardian", null);
            guardian.triggerAnim("guardian", "core_pulse");
            world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.5f, .55f);
            log.cast(now, "core pulse", null);
            return;
        }
        freeze();
        rollHold();
        triggerPhysicalAnimation();
        world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, .8f, .55f);
        lockedAim = player.getBoundingBox().getCenter();
        logPhysical(now, player);
        volley(world, now);
    }

    private boolean fastSlam() {
        return guardian.isUnstable() && comboCount == 3 && comboIndex < 2;
    }
    /** Ordinary slams sometimes pause at the top of the swing, so jumping in rhythm stops working. */
    private void rollHold() {
        boolean slam = active == Attack.SLAM || active == Attack.SHOCKWAVE;
        int[] holds = GuardianPhaseRules.SLAM_HOLDS;
        slamHold = slam && !fastSlam() && !reviewing && guardian.getRandom().nextDouble() < delayChance(difficulty)
                ? holds[guardian.getRandom().nextInt(holds.length)] : 0;
    }
    private void triggerPhysicalAnimation() {
        guardian.stopTriggeredAnim("guardian", null);
        guardian.triggerAnim("guardian", active == Attack.THROW
                ? (guardian.isUnstable() ? "throw_fast" : "throw") : GuardianPhaseRules.slamClip(fastSlam(), slamHold));
    }
    private int physicalDuration() {
        return active == Attack.THROW ? GuardianPhaseRules.throwDuration(guardian.isUnstable())
                : GuardianPhaseRules.slamDuration(fastSlam(), slamHold);
    }
    private void logPhysical(long now, ServerPlayerEntity player) {
        String what = active == Attack.THROW ? "throw" : slamHold > 0 ? "delayed slam (" + slamHold / 20.0 + "s hold)" : "slam";
        log.cast(now, what, player == null ? "?" : player.getName().getString());
    }
    private void finishAction(long now) {
        // Cooldowns begin after the whole combo. The laser keeps its first-phase clock.
        int cooldown = active == Attack.BEAM ? active.cooldown : Math.round(active.cooldown * (guardian.isUnstable() ? .8f : 1));
        ready.put(active, now + scaled(cooldown, cooldownScale(party, difficulty)));
        double rest = restScale(party, difficulty);
        int gap = scaled(active == Attack.BEAM ? RECOVERY_GAP : GuardianPhaseRules.gap(guardian.isUnstable()), rest);
        guardian.clearFan(); guardian.clearPulse();
        unfreeze(); active = null; heldRock = null;
        guardian.setHoldingRock(false);
        guardian.getNavigation().stop();
        int extraRecovery = nature.finishAttack(now);
        guardian.setNatureOpening(extraRecovery>0?now+gap:-1);
        nextAction = now + gap + extraRecovery;
        approachUntil = reviewing || pendingFan ? 0 : nextAction + scaled(20, rest);
    }
    private void tickAttack(ServerWorld world, long now) {
        if (active == Attack.FAN) { tickFan(world,now); return; }
        if (active == Attack.PULSE) { tickPulse(world,now); return; }
        if (active == Attack.LEAP) {
            if (leap.tick(world)) {
                if (leapFollowup && leap.status().equals("Last leap completed.")) {
                    leapFollowup = false;
                    ready.put(Attack.LEAP, now + 144);
                    active = Attack.SHOCKWAVE; comboIndex = 0; comboCount = 1; started = now; slamHold = 0;
                    anchor = guardian.getEntityPos(); triggerPhysicalAnimation();
                } else finishAction(now);
            }
            return;
        }
        int tick = (int)(now - started);
        if (guardian.getEntityPos().squaredDistanceTo(anchor) > .75*.75) { interruptAction(); nextAction = now + 30; return; }
        if (tick >= (active == Attack.BEAM ? active.duration : physicalDuration())) {
            if (active != Attack.BEAM && ++comboIndex < comboCount) {
                // Each repeat turns to someone else when it can; a rock never bends after release.
                Candidate candidate = target(active, candidates(world.getPlayers(p -> canDamage(guardian,p)), now),
                        lastTargeted, now, guardian.isUnstable(), target);
                if (candidate != null) {
                    target = candidate.id(); lastTargeted.put(target,now);
                    motion.reset(world.getServer().getPlayerManager().getPlayer(target).getEntityPos());
                } else if (active == Attack.THROW) { finishAction(now); return; }
                started = now; tick = 0;
                rollHold();
                triggerPhysicalAnimation();
                logPhysical(now, world.getServer().getPlayerManager().getPlayer(target));
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
                heldRock = new GuardianRockEntity(ModEntities.GUARDIAN_ROCK, world);
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
        } else if (tick == GuardianPhaseRules.slamImpact(fastSlam(), slamHold)) {
            impactSound(world);
            crushGrowth(world, anchor, 4.5, false, 6);
            emitWave(world, anchor, fists(world));
        }
    }

    /** The slam's fists: a heavy hit in front of and under it. Those struck are spared its own wave. */
    private Set<UUID> fists(ServerWorld world) {
        Set<UUID> struck = new HashSet<>();
        var walls = new GuardianWallImpact();
        for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(guardian, p))) {
            if (!fistContact(anchor, attackYaw, player.getBoundingBox())
                    || !walls.clear(world, guardian, anchor.add(0,1,0), player.getBoundingBox().getCenter(), false)) continue;
            struck.add(player.getUuid());
            if (strike(world, player, world.getDamageSources().mobAttack(guardian), FIST_DAMAGE, "slam fists")) knockAway(player, anchor, 1.0);
        }
        return struck;
    }

    private float aimPitch(Vec3d aim) {
        Vec3d delta=aim.subtract(guardian.getEntityPos().add(0,4,0));
        return (float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalLength()));
    }

    private void tickFan(ServerWorld world,long now) {
        int age=(int)(now-started);
        if (age>=GuardianFanRules.duration(guardian.isUnstable())) { finishAction(now); return; }
        if (guardian.getEntityPos().squaredDistanceTo(anchor)>.75*.75) { interruptAction(); nextAction=now+20; return; }
        int burst = GuardianFanRules.burst(age);
        int tick = (int)GuardianFanRules.localTime(age);
        if (burst > 0 && burst != GuardianFanRules.burst(age - 1)) {
            var players=world.getPlayers(p -> reviewing ? p.isAlive() && !p.isSpectator() : canDamage(guardian,p));
            // Later bursts spread across the party where they can.
            Candidate next=target(Attack.FAN,candidates(players,now),lastTargeted,now,guardian.isUnstable(),target);
            if(next==null) { finishAction(now); return; }
            target=next.id(); lastTargeted.put(target,now);
            motion.reset(world.getServer().getPlayerManager().getPlayer(target).getEntityPos());

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
        if (tick == GuardianFanRules.LOCK) {
            for (int i = 0; i < GuardianFanRules.COUNT; i++) {
                Vec3d socket = GuardianFanRules.socket(guardian.getEntityPos(), attackYaw, fanPitch, i);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, socket.x, socket.y, socket.z,
                        6, .2, .2, .2, .03);
            }
        }
        if (tick==GuardianFanRules.RELEASE) {
            var hits=new HashSet<UUID>(); var walls=new GuardianWallImpact();
            for(int i=0;i<GuardianFanRules.COUNT;i++) {
                Vec3d socket=GuardianFanRules.socket(guardian.getEntityPos(),attackYaw,fanPitch,i);
                Vec3d source=guardian.getEntityPos().add(0,3.3,0);
                // Floating stones cannot materialize on the far side of solid cover.
                if (walls.clip(world,guardian,source,socket,false,true).squaredDistanceTo(socket)>1e-10) continue;
                var shard=new GuardianRockEntity(ModEntities.GUARDIAN_ROCK,world);
                shard.setOwner(guardian);shard.setPosition(socket);
                shard.releaseShard(GuardianFanRules.direction(socket,lockedAim,i),hits,walls);
                world.spawnEntity(shard);rocks.add(shard);
            }
            world.playSound(null,guardian.getBlockPos(),SoundEvents.ENTITY_IRON_GOLEM_ATTACK,SoundCategory.HOSTILE,1.6f,1.1f);
        }
    }

    /** The break: grinding and sparks as a warning, the shell bursting at {@link GuardianPhaseRules#BURST}, then a roar. */
    private void tickBreak(ServerWorld world, int time) {
        Vec3d feet = guardian.getEntityPos();
        if (time < GuardianPhaseRules.BURST) {
            if (time % 8 == 0)
                world.playSound(null, guardian.getBlockPos(), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.HOSTILE, 2f, .45f + time * .01f);
            if (time % 2 == 0) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + 3.3, feet.z, 3 + time / 4, 1.1, 1.3, 1.1, .05);
                world.spawnParticles(STONE, feet.x, feet.y + 3, feet.z, 2 + time / 8, 1, 1.2, 1, .04);
            }
        } else if (time == GuardianPhaseRules.BURST) {
            burst(world, feet);
        } else if (time == GuardianPhaseRules.BURST + 8) {
            world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.5f, .45f);
        }
    }

    private void burst(ServerWorld world, Vec3d feet) {
        Vec3d core = feet.add(0, GuardianShellRules.CORE_HEIGHT, 0);
        world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.8f, .8f);
        world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 2f, .5f);
        world.spawnParticles(STONE, core.x, core.y, core.z, 140, 2.2, 1.6, 2.2, .35);
        world.spawnParticles(ParticleTypes.EXPLOSION, core.x, core.y, core.z, 3, .8, .6, .8, 0);
        crushGrowth(world, feet, GuardianPhaseRules.BURST_RADIUS, false, 8);
        var walls = new GuardianWallImpact();
        for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(guardian, p))) {
            if (!radial(feet, player.getBoundingBox(), GuardianPhaseRules.BURST_RADIUS, 6)
                    || !walls.clear(world, guardian, core, player.getBoundingBox().getCenter(), false)) continue;
            if (strike(world, player, world.getDamageSources().mobAttack(guardian), GuardianPhaseRules.BURST_DAMAGE, "shell burst"))
                knockAway(player, feet, 1.4);
        }
    }

    /** Plant, pull everyone near toward the open core, then detonate. */
    private void tickPulse(ServerWorld world, long now) {
        int tick = (int)(now - started);
        if (tick >= GuardianPulseRules.DURATION) { finishAction(now); return; }
        guardian.setVelocity(0, Math.min(0, guardian.getVelocity().y), 0);
        Vec3d feet = guardian.getEntityPos(), core = feet.add(0, GuardianShellRules.CORE_HEIGHT, 0);
        var players = world.getPlayers(p -> reviewing ? p.isAlive() && !p.isSpectator() : canDamage(guardian, p));
        if (GuardianPulseRules.pulling(tick)) {
            for (ServerPlayerEntity player : players) {
                Vec3d pull = GuardianPulseRules.pull(feet, player.getEntityPos());
                // Solid cover between a player and the core anchors them.
                if (pull.lengthSquared() == 0 || Math.abs(player.getY() - feet.y) > 6
                        || !clearLine(world, guardian, core, player.getBoundingBox().getCenter())) continue;
                ServerPlayNetworking.send(player, new ModNetworking.GuardianPullPayload((float)pull.x, (float)pull.z));
            }
            pulseVisuals(world, tick, feet, core);
        }
        if (tick == GuardianPulseRules.BLAST) blast(world, feet, core, players);
    }

    private void pulseVisuals(ServerWorld world, int tick, Vec3d feet, Vec3d core) {
        var random = guardian.getRandom();
        if (tick % 2 == 0) for (int i = 0; i < 6; i++) {
            // Debris and sparks stream inward across the whole pull.
            double angle = random.nextDouble() * Math.PI * 2, radius = 3 + random.nextDouble() * (GuardianPulseRules.PULL_RADIUS - 3);
            Vec3d from = feet.add(Math.cos(angle) * radius, .4 + random.nextDouble() * 2, Math.sin(angle) * radius);
            Vec3d toward = core.subtract(from).multiply(.12);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 0, toward.x, toward.y, toward.z, 1);
            world.spawnParticles(STONE, from.x, from.y, from.z, 0, toward.x, toward.y + .15, toward.z, 1);
        }
        if (tick % 3 == 0) for (int i = 0; i < 36; i++) {
            // The blast's reach, traced on the ground for the whole pull.
            double angle = Math.PI * 2 * i / 36;
            Vec3d point = GuardianWaveSurface.ground(world, guardian, feet,
                    feet.x + Math.cos(angle) * GuardianPulseRules.BLAST_RADIUS, feet.z + Math.sin(angle) * GuardianPulseRules.BLAST_RADIUS);
            if (point != null) world.spawnParticles(PULSE_RING, point.x, point.y + .15, point.z, 1, .05, 0, .05, 0);
        }
        if (tick % 10 == 0)
            world.playSound(null, guardian.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 2.5f, .6f + tick * .012f);
    }

    private void blast(ServerWorld world, Vec3d feet, Vec3d core, List<ServerPlayerEntity> players) {
        world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3f, .7f);
        world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 2f, .6f);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, core.x, core.y, core.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.EXPLOSION, core.x, core.y, core.z, 6, 2, 1, 2, 0);
        world.spawnParticles(STONE, feet.x, feet.y + .5, feet.z, 90, 3, .4, 3, .2);
        crushGrowth(world, feet, GuardianPulseRules.BLAST_RADIUS, false, 10);
        var walls = new GuardianWallImpact();
        for (ServerPlayerEntity player : players) {
            if (!GuardianPulseRules.inBlast(feet, player.getBoundingBox())
                    || !walls.clear(world, guardian, core, player.getBoundingBox().getCenter(), false)) continue;
            if (strike(world, player, world.getDamageSources().mobAttack(guardian), GuardianPulseRules.DAMAGE, "core pulse"))
                knockAway(player, feet, 1.8);
        }
    }

    /** Co-op pressure: while it commits to one player, stones lift off its back for the others. */
    private void volley(ServerWorld world, long now) {
        if (!engaged || reviewing || party < 2 || now < nextVolley) return;
        var others = candidates(world.getPlayers(p -> canDamage(guardian, p) && !p.getUuid().equals(target)), now).stream()
                .filter(c -> c.visible() && c.distance() <= GuardianVolleyRules.RANGE)
                .sorted(Comparator.comparingDouble(c -> -priority(c, lastTargeted, now)))
                .limit(GuardianVolleyRules.MAX_TARGETS).toList();
        if (others.isEmpty()) return;
        nextVolley = now + scaled(GuardianVolleyRules.COOLDOWN, cooldownScale(party, difficulty));
        for (int cluster = 0; cluster < others.size(); cluster++) {
            List<GuardianRockEntity> stones = new ArrayList<>();
            for (int i = 0; i < GuardianVolleyRules.COUNT; i++) {
                var stone = new GuardianRockEntity(ModEntities.GUARDIAN_ROCK, world);
                stone.setOwner(guardian); stone.holdShard();
                stone.setPosition(GuardianVolleyRules.socket(guardian.getEntityPos(), guardian.getYaw(), cluster, others.size(), i, 0));
                world.spawnEntity(stone); rocks.add(stone); stones.add(stone);
            }
            volleys.add(new Volley(others.get(cluster).id(), stones, cluster, others.size(), now));
            ServerPlayerEntity victim = world.getServer().getPlayerManager().getPlayer(others.get(cluster).id());
            log.cast(now, "back volley", victim == null ? "?" : victim.getName().getString());
        }
        world.playSound(null, guardian.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.HOSTILE, 1.6f, .8f);
    }

    private void tickVolleys(ServerWorld world, long now) {
        volleys.removeIf(volley -> {
            if (volley.stones().stream().allMatch(GuardianRockEntity::isRemoved)) return true;
            int age = (int)(now - volley.started());
            if (age < GuardianVolleyRules.RISE) {
                for (int i = 0; i < volley.stones().size(); i++) {
                    Vec3d socket = GuardianVolleyRules.socket(guardian.getEntityPos(), guardian.getYaw(), volley.cluster(),
                            volley.clusters(), i, age / (float)GuardianVolleyRules.RISE);
                    volley.stones().get(i).setPosition(socket);
                    if (age % 4 == i) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, socket.x, socket.y, socket.z, 3, .15, .15, .15, .02);
                }
                return false;
            }
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(volley.target());
            if (player == null || !canDamage(guardian, player) || guardian.distanceTo(player) > GuardianVolleyRules.RANGE + 8) {
                volley.stones().forEach(GuardianRockEntity::discard);
                return true;
            }
            var hits = new HashSet<UUID>(); var walls = new GuardianWallImpact();
            Vec3d body = guardian.getEntityPos().add(0, GuardianShellRules.CORE_HEIGHT, 0), aim = player.getBoundingBox().getCenter();
            for (int i = 0; i < volley.stones().size(); i++) {
                var stone = volley.stones().get(i);
                if (stone.isRemoved()) continue;
                Vec3d socket = stone.getEntityPos();
                // Like the fan, stones cannot launch from the far side of solid cover.
                if (walls.clip(world, guardian, body, socket, false, true).squaredDistanceTo(socket) > 1e-10) { stone.discard(); continue; }
                Vec3d led = lead(aim, drift.getOrDefault(player.getUuid(), Vec3d.ZERO), (int)(socket.distanceTo(aim) / GuardianFanRules.SPEED));
                stone.releaseShard(GuardianFanRules.direction(socket, led, i), hits, walls);
            }
            world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.3f, 1.2f);
            return true;
        });
    }

    /** Waves finish independently of the next action; interruption cancels every pending hazard. */
    void emitWave(ServerWorld world, Vec3d center, Set<UUID> exempt) {
        expireWaves(world);
        final int preferred = waves.stream().anyMatch(w -> w.slot()==0) ? 1 : 0;
        int free = preferred;
        if (waves.stream().anyMatch(w -> w.slot()==preferred)) {
            // Both slots busy: retire the oldest wave instead of crashing the server tick.
            TravelingWave oldest = waves.stream().min(Comparator.comparingLong(TravelingWave::start)).orElseThrow();
            waves.remove(oldest); free = oldest.slot();
        }
        final int slot = free;
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
            int count = guardian.isBreaking() ? 7 : 2;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, guardian.getX(),guardian.getY()+3.3,guardian.getZ(),count,1.1,.8,1.1,.04);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, guardian.getX(),guardian.getY()+3,guardian.getZ(),count,1.5,1.5,1.5,.14);
        }
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
        for (AstralDoubleEntity clone : world.getEntitiesByClass(AstralDoubleEntity.class,
                new net.minecraft.util.math.Box(center,center).expand(radius+1,3,radius+1),e->!e.isRemoved()))
            if(hit.test(clone.getBlockPos()))clone.damage(world,world.getDamageSources().mobAttack(guardian),1);
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
            if (strike(world,player,world.getDamageSources().mobAttack(guardian),damage,"leap landing")) {
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
        for (AstralDoubleEntity clone : world.getEntitiesByClass(AstralDoubleEntity.class,
                new net.minecraft.util.math.Box(center,center).expand(radius+2,6,radius+2),e->!e.isRemoved())) {
            Vec3d floor=GuardianWaveSurface.ground(world,guardian,center,clone.getX(),clone.getZ());
            if(floor!=null && waveContact(center,clone.getBoundingBox(),previous,radius,floor.y)
                    && walls.clear(world,guardian,center.add(0,.7,0),floor.add(0,.7,0),true))
                clone.damage(world,world.getDamageSources().mobAttack(guardian),1);
        }
        com.anton.elementalwands.util.SeedlingManager.crushGrowth(world,contacted);
        com.anton.elementalwands.util.TendrilBloomManager.crushGrowth(world,contacted);
        // The continuous ridge is client-rendered. Keep server effects bounded as the radius grows.
        int samples = tick % 3 == 0 ? 24 : 0;
        for (int i=0; i<samples; i++) {
            double angle = Math.PI*2*i/samples;
            Vec3d p = GuardianWaveSurface.ground(world, guardian, center, center.x+Math.cos(angle)*radius, center.z+Math.sin(angle)*radius);
            if (p == null || !walls.clear(world,guardian,center.add(0,.7,0),p.add(0,.7,0),true)) continue;
            world.spawnParticles(STONE, p.x, p.y+.12, p.z, 3, .12,.22,.12,.045);
            if (i%2 == 0) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x,p.y+.18,p.z,1,0,.05,0,0);
        }
        for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(guardian, p) && !hitPlayers.contains(p.getUuid()))) {
            if (Math.hypot(player.getX()-center.x,player.getZ()-center.z) > radius+2) continue;
            Vec3d p = GuardianWaveSurface.ground(world, guardian, center, player.getX(), player.getZ());
            if (p == null || !waveContact(center, player.getBoundingBox(), previous, radius, p.y)) continue;
            if (!walls.clear(world,guardian,center.add(0,.7,0),p.add(0,.7,0),true)) continue;
            hitPlayers.add(player.getUuid());
            if (strike(world, player, world.getDamageSources().mobAttack(guardian), WAVE_DAMAGE, "wave")) {
                Vec3d delta = player.getEntityPos().subtract(center);
                player.takeKnockback(.7, -delta.x, -delta.z);
            }
        }
    }

    private static void knockAway(ServerPlayerEntity player, Vec3d from, double strength) {
        Vec3d away = player.getEntityPos().subtract(from);
        player.takeKnockback(strength, -away.x, -away.z);
        player.velocityModified = true;
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
        guardian.clearFan(); guardian.clearPulse(); pendingFan=false; approachUntil=0;
        guardian.clearWave(); waves.clear(); volleys.clear(); leapFollowup=false; unfreeze(); active = null; waking = 0;
        if (heldRock != null) { heldRock.discard(); heldRock = null; }
        for (GuardianRockEntity rock : rocks) rock.discard();
        rocks.clear();
        guardian.getNavigation().stop(); guardian.stopTriggeredAnim("guardian", null);
    }
    private void impactSound(ServerWorld world) {
        world.playSound(null, guardian.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.5f, .45f);
        world.spawnParticles(STONE, guardian.getX(), guardian.getY()+.2, guardian.getZ(), 35, 2,.15,2,.08);
    }
}
