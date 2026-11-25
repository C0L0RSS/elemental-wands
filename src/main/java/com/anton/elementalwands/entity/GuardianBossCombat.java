package com.anton.elementalwands.entity;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModSounds;
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
import net.minecraft.item.consume.UseAction;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;
import static com.anton.elementalwands.entity.GuardianCombatRules.*;

/**
 * Server-authoritative encounter: one committed action at a time, threat-aware targeting, real
 * recovery. {@link GuardianCombatRules} holds the chooser; this reads the players for it and carries
 * out what it picks. Co-op adds pressure up to four players (shorter walks between strings, stones off
 * its back and spike walls for the players it is not attacking); the world's difficulty sets the intensity.
 */
final class GuardianBossCombat {
    private static final BlockStateParticleEffect STONE = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState());
    private static final DustParticleEffect PULSE_RING = new DustParticleEffect(0x3CE1FF, 1.6f);
    /** Where something is about to land: the leap, or stones bursting on the floor. */
    private static final DustParticleEffect IMPACT_RING = new DustParticleEffect(0xFFA238, 1.8f);
    private final FracturedGuardianEntity guardian;
    private final ServerBossBar bar = new ServerBossBar(Text.literal("Fractured Guardian"), BossBar.Color.BLUE, BossBar.Style.PROGRESS);
    private final Map<Attack, Long> ready = new EnumMap<>(Attack.class);
    private final Map<UUID, Long> lastTargeted = new HashMap<>();
    private final GuardianLeapAttack leap;
    private final GuardianMotionSample motion = new GuardianMotionSample();
    private final GuardianFightLog log = new GuardianFightLog();
    private final List<GuardianRockEntity> rocks = new ArrayList<>();
    private Vec3d home, anchor, lockedAim;
    private UUID target;
    private Attack active, last, before;
    private long started, nextAction, emptySince = -1, nextMove, nextVolley;
    private boolean engaged, frozen, previousNoAi;
    private boolean reviewing;
    private int waking;
    private float attackYaw;
    private GuardianRockEntity heldRock;
    /** Attacks begun in the current string; 0 while it walks between strings. */
    private int chain;
    /**
     * The wait before its next pick is the walk after a string, which a punished player cuts short.
     * Waking, the shell break and an interrupted attack are followed by a plain pause that nothing does.
     */
    private boolean walking;
    /** Ticks the current slam pauses at the top of its swing (0 for an ordinary slam). */
    private int slamHold;
    /** Phase two: a slam chained inside a string comes down faster. */
    private boolean fastSlam;
    private float fanPitch;
    /** Players the current Airburst cast has already hurt, and whether it has run out of players in the air. */
    private Set<UUID> burstHits = new HashSet<>();
    private boolean burstSpent;
    /** Party size and difficulty as of the last fight tick; pressure scales with both. */
    private int party = 1;
    private Difficulty difficulty = Difficulty.NORMAL;
    /** Ticks each player has been off the ground and well clear of the floor, and how far clear. */
    private final Map<UUID,Integer> airborne = new HashMap<>();
    private final Map<UUID,Double> clearance = new HashMap<>();
    /** Ticks each player has stayed beyond {@link GuardianCombatRules#MID}. */
    private final Map<UUID,Integer> farTicks = new HashMap<>();
    /** Whether each player jumped the last wave that reached them; it answers that with a held slam. */
    private final Map<UUID,Boolean> jumped = new HashMap<>();
    /** Recent damage each player dealt it, fading with {@link GuardianCombatRules#THREAT_DECAY}. */
    private record Threat(float amount, long at) {
        float current(long now) { return amount * (float)Math.exp(-(now - at) / (double)THREAT_DECAY); }
    }
    private final Map<UUID, Threat> threat = new HashMap<>();
    private final Map<UUID, Vec3d> lastSeen = new HashMap<>(), drift = new HashMap<>();
    private final Map<UUID, Integer> stillTicks = new HashMap<>();
    /** Each player's movement in all three axes, smoothed, and how quickly they are falling faster: for aiming into the air. */
    private final Map<UUID, Vec3d> flight = new HashMap<>(), lastStep = new HashMap<>();
    private final Map<UUID, Double> fall = new HashMap<>();
    private record TravelingWave(long start, Vec3d origin, Set<UUID> hits, boolean unstable, int slot, GuardianWallImpact walls) {}
    private final List<TravelingWave> waves = new ArrayList<>();
    private record Volley(UUID target, List<GuardianRockEntity> stones, int cluster, int clusters, long started) {}
    private final List<Volley> volleys = new ArrayList<>();
    /**
     * A ring marked on the floor where something is about to land. With a {@code gap}, the wedge that
     * many degrees either side of {@code yaw} is left open: the Sweep's arc.
     */
    private record Ring(Vec3d center, double radius, long until, boolean leap, float yaw, double gap) {
        Ring(Vec3d center, double radius, long until, boolean leap) { this(center, radius, until, leap, 0, 0); }
    }
    private final List<Ring> rings = new ArrayList<>();
    /** Players beside or behind it and within reach of its arm, as of the last pick. */
    private Set<UUID> arc = Set.of();
    /** The Scar. Cracks scored on the floor and waiting to erupt: one list of footings for each wall. */
    private List<List<Vec3d>> cracks = List.of();
    /** A spike about to come up. */
    private record Eruption(Vec3d base, float yaw, long at, int life) {}
    private final List<Eruption> eruptions = new ArrayList<>();
    private final List<GuardianSpikeEntity> spikes = new ArrayList<>();
    /** The pen its walls make: scored but not yet up, and standing until {@link #penUntil}. */
    private GuardianScarRules.Pen scoredPen, pen;
    private long penUntil;
    /** Players the current Scar's eruption has already hurt, and when a spike last hurt each player. */
    private Set<UUID> scarHits = new HashSet<>();
    private final Map<UUID, Long> spikeTouch = new HashMap<>();

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
        airborne.clear(); clearance.clear(); farTicks.clear(); jumped.clear();
        leap.cancel();
        guardian.cancelCombatBeam();
        guardian.clearFan(); guardian.clearPulse();
        guardian.setHoldingRock(false);
        guardian.clearWave(); waves.clear(); volleys.clear(); rings.clear(); clearScar();
        unfreeze();
        active = null; waking = 0; engaged = false;
        guardian.finishPhase();
        target = null;
        reviewing = false;
        ready.clear(); lastTargeted.clear(); last = null; before = null; chain = 0; walking = false; nextVolley = 0;
        threat.clear(); lastSeen.clear(); drift.clear(); stillTicks.clear(); flight.clear(); lastStep.clear(); fall.clear();
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
        if (active == null) return;
        // The same reading of the players a fight takes, so a rehearsed Airburst leads a moving target.
        trackPlayers(world, world.getPlayers(p -> p.isAlive() && !p.isSpectator() && guardian.squaredDistanceTo(p) <= 64*64));
        tickAttack(world, world.getTime());
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
            world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_SHELL_BREAK_WINDUP,
                    SoundCategory.HOSTILE, 2, .65f);
            log.stage(now, "shell break");
            return;
        }
        if (active != null) { tickAttack(world, now); return; }
        boolean resting = now < nextAction;
        if (decide(world, players, now, resting)) return;
        if (!resting && chain > 0) {
            // The string broke off with nothing in reach: it walks again.
            chain = 0; walking = true; nextAction = now + scaled(REST, restScale(party, difficulty));
        }
        pursue(players, now);
    }

    /**
     * Picks who to turn on and what to do to them. While it is still walking off its last string,
     * only a player it punishes (launched, eating, or standing still at range) makes it start early.
     */
    private boolean decide(ServerWorld world, List<ServerPlayerEntity> players, long now, boolean resting) {
        if (!guardian.isOnGround() || resting && !walking) return false;
        Map<UUID, ServerPlayerEntity> byId = new HashMap<>();
        for (ServerPlayerEntity player : players) byId.put(player.getUuid(), player);
        List<Candidate> all = candidates(players, now);
        List<Candidate> seen = all.stream().filter(Candidate::visible).toList();
        arc = new HashSet<>();
        for (ServerPlayerEntity player : players)
            if (GuardianSweepRules.inArc(guardian.getEntityPos(), guardian.getBodyYaw(), player.getEntityPos())) arc.add(player.getUuid());
        boolean pulse = guardian.isUnstable() && now >= ready.getOrDefault(Attack.PULSE, 0L)
                && all.stream().anyMatch(p -> eligible(Attack.PULSE, p, true));
        List<Candidate> punished = seen.stream().filter(c -> punishes(read(c, byId.get(c.id()), now, false))).toList();
        if (punished.isEmpty() && resting) return false;
        // A player in the air comes first: the Airburst has the shortest window.
        List<Candidate> aloft = punished.stream().filter(c -> read(c, byId.get(c.id()), now, false).airborne()).toList();
        // The Scar is followed into the pen it made, whoever is in it.
        List<Candidate> held = chain > 0 && last == Attack.SCAR
                ? seen.stream().filter(c -> read(c, byId.get(c.id()), now, false).penned()).toList() : List.<Candidate>of();
        // Otherwise, inside a string, it turns on someone else when it can.
        UUID avoid = chain > 0 && party > 1 && held.isEmpty() ? target : null;
        // Cutting its walk short, it turns on whoever earned that. Otherwise everyone in sight is weighed, so a player
        // who stands still or eats all fight long cannot keep it off the rest of the party.
        List<Candidate> pool = new ArrayList<>(!aloft.isEmpty() ? aloft : !held.isEmpty() ? held : resting ? punished : seen);
        Candidate first = null;
        // Whoever matters most comes first, but a player beyond every reach does not keep it off the ones it can hit.
        while (!pool.isEmpty()) {
            Candidate pick = focus(pool, lastTargeted, now, avoid);
            if (first == null) first = pick;
            Read read = read(pick, byId.get(pick.id()), now, pulse);
            Map<Attack, Double> weights = weights(read, last, before);
            weights.keySet().removeIf(attack -> now < ready.getOrDefault(attack, 0L));
            // No landing in the pen: its wave goes down it instead.
            if (weights.isEmpty() && read.penned() && now >= ready.getOrDefault(Attack.SLAM, 0L)) weights.put(Attack.SLAM, 1.0);
            Attack choice = pick(weights, guardian.getRandom()::nextDouble);
            if (choice != null) {
                begin(world, choice, byId.get(pick.id()), now, false);
                if (active != null) return true;
                // A leap with no landing: the same player is weighed again without it.
                continue;
            }
            pool.remove(pick);
        }
        // Nothing it can do to anyone: it walks at whoever matters most, in sight or not.
        if (first == null) first = focus(all, lastTargeted, now, null);
        if (first != null && !resting) target = first.id();
        return false;
    }

    private boolean aloft(UUID id) { return airborne.getOrDefault(id, 0) >= GuardianAirburstRules.AIRBORNE_TICKS; }

    private Read read(Candidate candidate, ServerPlayerEntity player, long now, boolean pulse) {
        UUID id = candidate.id();
        UseAction use = player != null && player.isUsingItem() ? player.getActiveItem().getUseAction() : UseAction.NONE;
        boolean behind = player != null && GuardianSweepRules.behind(guardian.getEntityPos(), guardian.getBodyYaw(), player.getEntityPos());
        boolean flanked = arc.stream().anyMatch(other -> !other.equals(id));
        return new Read(candidate.distance(), aloft(id) && now >= ready.getOrDefault(Attack.AIRBURST, 0L), candidate.still(),
                use == UseAction.EAT || use == UseAction.DRINK, farTicks.getOrDefault(id, 0) >= KEPT_AWAY, guardian.isUnstable(), pulse,
                behind, flanked, player != null && penned(player.getEntityPos(), now));
    }

    /** Between the Scar's walls while they still have a while to stand. */
    private boolean penned(Vec3d point, long now) {
        return pen != null && now < penUntil - GuardianScarRules.FADING && pen.contains(point);
    }

    /** What it reads off each player: time in the air, smoothed movement for aim leads, standing still, keeping away. */
    private void trackPlayers(ServerWorld world, List<ServerPlayerEntity> players) {
        Set<UUID> present = new HashSet<>();
        for (ServerPlayerEntity player : players) {
            UUID id = player.getUuid();
            present.add(id);
            var floor=world.raycast(new RaycastContext(player.getEntityPos(),player.getEntityPos().add(0,-16,0),
                    RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,guardian));
            double clear=floor.getType()==HitResult.Type.MISS?16:player.getY()-floor.getPos().y;
            clearance.put(id,clear);
            airborne.compute(id,(key,count) -> !player.isOnGround() && clear>GuardianAirburstRules.CLEARANCE
                    ? Math.min(60,(count==null?0:count)+1) : 0);
            farTicks.compute(id,(key,count) -> guardian.distanceTo(player)>MID ? Math.min(400,(count==null?0:count)+1) : 0);
            Vec3d position = player.getEntityPos(), previous = lastSeen.put(id, position);
            // A teleport or dash is not walking; it never inflates an aim lead.
            Vec3d step = previous == null || previous.squaredDistanceTo(position) > 1 ? Vec3d.ZERO : position.subtract(previous);
            drift.merge(id, step, (old, latest) -> old.multiply(.6).add(latest.multiply(.4)));
            // The same reading with its height kept, and how quickly that is changing: a fall is led as a fall.
            Vec3d moved = previous == null || previous.squaredDistanceTo(position) > 16 ? Vec3d.ZERO : position.subtract(previous);
            Vec3d earlier = lastStep.put(id, moved);
            flight.merge(id, moved, (old, latest) -> old.multiply(.5).add(latest.multiply(.5)));
            fall.merge(id, earlier == null ? 0 : moved.y - earlier.y, (old, latest) -> old * .5 + latest * .5);
            boolean slow = step.horizontalLengthSquared() < STILL_SPEED * STILL_SPEED;
            stillTicks.merge(id, slow ? 1 : 0, (old, add) -> add == 0 ? 0 : Math.min(100, old + 1));
        }
        for (Map<UUID, ?> map : List.of(airborne, clearance, farTicks, jumped, lastSeen, drift, stillTicks, flight, lastStep, fall))
            map.keySet().retainAll(present);
    }

    private List<Candidate> candidates(List<ServerPlayerEntity> players, long now) {
        return players.stream().map(p -> new Candidate(p.getUuid(), guardian.distanceTo(p), guardian.canSee(p),
                threat(p.getUuid(), now), stillTicks.getOrDefault(p.getUuid(), 0) >= STILL_TICKS)).toList();
    }

    /** It walks at its target, not at whoever is nearest, and stops once its fists could reach them. */
    private void pursue(List<ServerPlayerEntity> players,long now) {
        ServerPlayerEntity destination=players.stream().filter(p -> p.getUuid().equals(target)).findFirst()
                .orElseGet(() -> players.stream().min(Comparator.comparingDouble(guardian::squaredDistanceTo)).orElse(null));
        if(destination==null || guardian.squaredDistanceTo(destination)<4*4) { guardian.getNavigation().stop(); return; }
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
        BossHealthBar.update(bar, guardian, players, p -> GuardianArenaManager.spectatorViewer(guardian, p));
    }

    private void begin(ServerWorld world, Attack attack, ServerPlayerEntity player, long now, boolean rehearsal) {
        Attack priorLast = last, priorBefore = before;
        reviewing = rehearsal;
        if (rehearsal) chain = 0; else chain++;
        walking = false;
        active = attack; before = last; last = attack; target = player.getUuid(); started = now; slamHold = 0; fastSlam = false;
        anchor = guardian.getEntityPos();
        motion.reset(player.getEntityPos());
        lastTargeted.put(target, now);
        guardian.getNavigation().stop();
        if (attack == Attack.SWEEP) {
            // It does not turn first: whoever got behind it is still behind it when the arm comes round.
            attackYaw = guardian.getBodyYaw();
            guardian.setYaw(attackYaw); guardian.setBodyYaw(attackYaw); guardian.setHeadYaw(attackYaw);
        } else face(player.getEntityPos(), true);
        String name = player.getName().getString();
        switch (attack) {
            case SWEEP -> {
                freeze();
                guardian.stopTriggeredAnim("guardian", null);
                guardian.triggerAnim("guardian", "sweep");
                world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_ATTACK_WINDUP, SoundCategory.HOSTILE, .8f, .45f);
                rings.add(new Ring(anchor, GuardianSweepRules.REACH, now + GuardianSweepRules.IMPACT, false,
                        attackYaw, GuardianSweepRules.FRONT_DEGREES));
                log.cast(now, "sweep", name);
            }
            case SCAR -> {
                freeze(); cracks = List.of(); scoredPen = null; scarHits = new HashSet<>();
                // Not again until these walls have stood and fallen.
                ready.put(Attack.SCAR, now + GuardianScarRules.ERUPT + GuardianScarRules.LIFE
                        + scaled(GuardianScarRules.LOCKOUT, cooldownScale(party, difficulty)));
                guardian.stopTriggeredAnim("guardian", null);
                guardian.triggerAnim("guardian", "scar");
                world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_ATTACK_WINDUP, SoundCategory.HOSTILE, .9f, .4f);
                log.cast(now, "scar", name);
            }
            case LEAP -> {
                freeze();
                if (!leap.begin(world,player,home == null ? anchor : home)) {
                    // No clear landing: it picks something else, and tries the leap again later.
                    unfreeze(); active = null; last = priorLast; before = priorBefore; chain = Math.max(0, chain - 1);
                    ready.put(Attack.LEAP,now+60);
                    return;
                }
                log.cast(now, "leap", name);
            }
            case BEAM -> {
                guardian.beginCombatBeam(player);
                log.cast(now, "beam", name);
                backStones(world, now);
            }
            case AIRBURST -> {
                freeze(); burstHits = new HashSet<>(); burstSpent = false;
                ready.put(Attack.AIRBURST, now + attack.duration + AIRBURST_LOCKOUT);
                lockedAim=airAim(player,GuardianFanRules.socket(guardian.getEntityPos(),attackYaw,0,1),GuardianFanRules.RELEASE);
                fanPitch=aimPitch(lockedAim); guardian.syncFan(now,fanPitch); guardian.triggerAnim("guardian","fan");
                log.cast(now, "airburst", name);
            }
            case PULSE -> {
                freeze();
                ready.put(Attack.PULSE, now + attack.duration + PULSE_COOLDOWN);
                guardian.syncPulse(now);
                guardian.stopTriggeredAnim("guardian", null);
                guardian.triggerAnim("guardian", "core_pulse");
                world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_PULSE_CHARGE, SoundCategory.HOSTILE, 2.5f, .55f);
                log.cast(now, "core pulse", null);
            }
            case SLAM, THROW -> {
                freeze();
                if (attack == Attack.SLAM) rollHold();
                triggerPhysicalAnimation();
                world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_ATTACK_WINDUP, SoundCategory.HOSTILE, .8f, .55f);
                lockedAim = player.getBoundingBox().getCenter();
                log.cast(now, attack == Attack.THROW ? "throw" : slamHold > 0 ? "held slam (" + slamHold / 20.0 + "s hold)"
                        : fastSlam ? "fast slam" : "slam", name);
                backStones(world, now);
            }
        }
    }

    /**
     * It holds its fists overhead against a player who jumped its last wave, so jumping in rhythm stops
     * working. In phase two a slam chained inside a string comes down faster instead.
     */
    private void rollHold() {
        int[] holds = GuardianPhaseRules.SLAM_HOLDS;
        boolean remembered = !reviewing && jumped.getOrDefault(target, false) && guardian.getRandom().nextDouble() < holdChance(difficulty);
        slamHold = remembered ? holds[guardian.getRandom().nextInt(holds.length)] : 0;
        fastSlam = !reviewing && guardian.isUnstable() && chain > 1 && slamHold == 0;
    }
    private void triggerPhysicalAnimation() {
        guardian.stopTriggeredAnim("guardian", null);
        guardian.triggerAnim("guardian", active == Attack.THROW
                ? (guardian.isUnstable() ? "throw_fast" : "throw") : GuardianPhaseRules.slamClip(fastSlam, slamHold));
    }
    private int physicalDuration() {
        return active == Attack.THROW ? GuardianPhaseRules.throwDuration(guardian.isUnstable())
                : GuardianPhaseRules.slamDuration(fastSlam, slamHold);
    }
    /** The attack's whole clip has played. It goes straight into another (a string), or walks at its target for a while. */
    private void finishAction(long now) {
        Attack finished = active;
        double lockout = cooldownScale(party, difficulty);
        if (finished == Attack.PULSE) ready.put(Attack.PULSE, now + scaled(PULSE_COOLDOWN, lockout));
        if (finished == Attack.AIRBURST) ready.put(Attack.AIRBURST, now + scaled(AIRBURST_LOCKOUT, lockout));
        guardian.clearFan(); guardian.clearPulse();
        unfreeze(); active = null; heldRock = null;
        guardian.setHoldingRock(false);
        guardian.getNavigation().stop();
        boolean more = !reviewing && (finished == Attack.SCAR || chain < chainLimit(guardian.isUnstable()))
                && guardian.getRandom().nextDouble() < chainChance(guardian.isUnstable(), finished, difficulty);
        if (more) { nextAction = now; return; }
        chain = 0; walking = !reviewing;
        nextAction = now + scaled(REST, restScale(party, difficulty));
    }
    private void tickAttack(ServerWorld world, long now) {
        if (active == Attack.AIRBURST) { tickAirburst(world,now); return; }
        if (active == Attack.PULSE) { tickPulse(world,now); return; }
        if (active == Attack.SWEEP) { tickSweep(world,now); return; }
        if (active == Attack.SCAR) { tickScar(world,now); return; }
        if (active == Attack.LEAP) {
            if (leap.tick(world)) finishAction(now);
            return;
        }
        int tick = (int)(now - started);
        if (guardian.getEntityPos().squaredDistanceTo(anchor) > .75*.75) { interruptAction(); nextAction = now + 30; return; }
        if (tick >= (active == Attack.BEAM ? active.duration : physicalDuration())) { finishAction(now); return; }
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
                world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_THROW_LAUNCH, SoundCategory.HOSTILE, 1.2f, .7f);
            }
        } else if (tick == GuardianPhaseRules.slamImpact(fastSlam, slamHold)) {
            impactSound(world);
            breakDoubles(world, anchor, 4.5);
            emitWave(world, anchor, fists(world));
        }
    }

    /** The Sweep: one blow through everything beside and behind it, where the marked arc showed. */
    private void tickSweep(ServerWorld world, long now) {
        int tick = (int)(now - started);
        if (guardian.getEntityPos().squaredDistanceTo(anchor) > .75*.75) { interruptAction(); nextAction = now + 30; return; }
        if (tick >= GuardianSweepRules.DURATION) { finishAction(now); return; }
        guardian.setVelocity(0, Math.min(0, guardian.getVelocity().y), 0);
        guardian.setYaw(attackYaw); guardian.setBodyYaw(attackYaw); guardian.setHeadYaw(attackYaw);
        if (tick != GuardianSweepRules.IMPACT) return;
        world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_GROUND_IMPACT, SoundCategory.HOSTILE, 1.3f, .7f);
        double gap = GuardianSweepRules.FRONT_DEGREES;
        for (int i = 0; i <= 30; i++) {
            // Dust along the path of the arm.
            Vec3d out = Vec3d.fromPolar(0, attackYaw + (float)(gap + (360 - 2 * gap) * i / 30)).multiply(GuardianSweepRules.REACH * .7);
            world.spawnParticles(STONE, anchor.x + out.x, anchor.y + 1.4, anchor.z + out.z, 4, .3, .5, .3, .08);
        }
        Vec3d shoulder = anchor.add(0, 1.5, 0);
        for (AstralDoubleEntity clone : world.getEntitiesByClass(AstralDoubleEntity.class,
                new Box(anchor, anchor).expand(GuardianSweepRules.REACH + 1, 4, GuardianSweepRules.REACH + 1), e -> !e.isRemoved())) {
            if (GuardianSweepRules.contact(anchor, attackYaw, clone.getBoundingBox())
                    && GuardianWaveSurface.clearLine(world, guardian, shoulder, clone.getBoundingBox().getCenter()))
                clone.damage(world, world.getDamageSources().mobAttack(guardian), 1);
        }
        var walls = new GuardianWallImpact();
        for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(guardian, p))) {
            if (!GuardianSweepRules.contact(anchor, attackYaw, player.getBoundingBox())
                    || !walls.clear(world, guardian, shoulder, player.getBoundingBox().getCenter(), false)) continue;
            if (strike(world, player, world.getDamageSources().mobAttack(guardian), GuardianSweepRules.DAMAGE, "sweep"))
                knockAway(player, anchor, 1.3);
        }
    }

    /**
     * The Scar: it follows its target until its hands hit the floor, scores the cracks where they will be
     * a moment later, and the walls erupt along them. The pen they make is what its next attack goes into.
     */
    private void tickScar(ServerWorld world, long now) {
        int tick = (int)(now - started);
        if (guardian.getEntityPos().squaredDistanceTo(anchor) > .75*.75) { interruptAction(); nextAction = now + 30; return; }
        if (tick >= GuardianScarRules.DURATION) { finishAction(now); return; }
        guardian.setVelocity(0, Math.min(0, guardian.getVelocity().y), 0);
        if (tick <= GuardianScarRules.PLANT) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(target);
            if (player == null || !player.isAlive() || player.isSpectator() || player.getEntityWorld() != world
                    || (!reviewing && !canDamage(guardian, player))) { interruptAction(); nextAction = now + 20; return; }
            if (tick < GuardianScarRules.PLANT) face(player.getEntityPos(), false);
            else {
                score(world, player, now);
                // Its target is under its hands, or there is no floor for the walls: there is nothing to pen.
                if (cracks.isEmpty()) { interruptAction(); nextAction = now + 20; return; }
            }
        }
        guardian.setYaw(attackYaw); guardian.setBodyYaw(attackYaw); guardian.setHeadYaw(attackYaw);
        if (tick > GuardianScarRules.PLANT && tick < GuardianScarRules.ERUPT && tick % 2 == 0) {
            // The cracks race outward from its hands.
            double run = Math.clamp((tick - GuardianScarRules.PLANT) / (GuardianScarRules.COMMIT - 2.0), 0, 1);
            for (List<Vec3d> crack : cracks) {
                int shown = (int)Math.ceil(crack.size() * run);
                for (int i = 0; i < shown; i++) {
                    Vec3d point = crack.get(i);
                    world.spawnParticles(IMPACT_RING, point.x, point.y + .15, point.z, 2, .25, 0, .25, 0);
                }
                Vec3d tip = crack.get(Math.max(0, shown - 1));
                world.spawnParticles(STONE, tip.x, tip.y + .2, tip.z, 6, .3, .2, .3, .06);
            }
        }
        if (tick == GuardianScarRules.ERUPT) erupt(world, now);
    }

    /** Commits the Scar: the pen round where its target will be, and in co-op a wall for each of the others. */
    private void score(ServerWorld world, ServerPlayerEntity player, long now) {
        impactSound(world);
        Vec3d aim = lead(player.getEntityPos(), drift.getOrDefault(player.getUuid(), Vec3d.ZERO), GuardianScarRules.COMMIT);
        GuardianScarRules.Pen made = GuardianScarRules.pen(guardian.getEntityPos(), aim);
        if (made == null) return;
        List<GuardianScarRules.Wall> walls = new ArrayList<>(List.of(made.wall(true), made.wall(false)));
        if (!reviewing && party > 1) {
            candidates(world.getPlayers(p -> canDamage(guardian, p) && !p.getUuid().equals(target)), now).stream()
                    .filter(c -> c.visible() && c.distance() <= GuardianScarRules.MAX_RANGE + 6)
                    .sorted(Comparator.comparingDouble(c -> -priority(c, lastTargeted, now)))
                    .limit(GuardianScarRules.MAX_FLANKS).forEach(c -> {
                        Vec3d at = lastSeen.get(c.id());
                        GuardianScarRules.Wall wall = at == null ? null
                                : GuardianScarRules.flank(made, at, drift.getOrDefault(c.id(), Vec3d.ZERO));
                        if (wall != null) walls.add(wall);
                    });
        }
        List<List<Vec3d>> scored = new ArrayList<>();
        // It is only a pen if both of its walls stand, unbroken enough, to past where its target will be.
        double reach = Math.hypot(aim.x - made.origin().x, aim.z - made.origin().z);
        boolean holds = true;
        int sides = 0;
        for (int i = 0; i < walls.size(); i++) {
            List<Vec3d> footing = footing(world, walls.get(i));
            if (i < 2) {
                Vec3d end = footing.isEmpty() ? made.origin() : footing.get(footing.size() - 1);
                double covered = new Vec3d(end.x - made.origin().x, 0, end.z - made.origin().z).dotProduct(made.axis());
                holds &= covered >= reach + 2 && footing.size() >= .8 * (reach + 2 - made.near());
                if (!footing.isEmpty()) sides++;
            }
            if (!footing.isEmpty()) scored.add(footing);
        }
        // With no floor for either wall there is nothing to score. With part of them, the walls still come up, as obstacles.
        cracks = sides == 0 ? List.of() : scored; scoredPen = holds ? made : null;
    }

    /** The floor under each spike of a wall. A gap or a pillar is stepped over; the edge of the floor ends the wall. */
    private List<Vec3d> footing(ServerWorld world, GuardianScarRules.Wall wall) {
        List<Vec3d> bases = new ArrayList<>();
        double level = guardian.getY();
        int missing = 0;
        for (Vec3d point : wall.columns()) {
            Vec3d ground = GuardianWaveSurface.ground(world, guardian, new Vec3d(point.x, level, point.z), point.x, point.z);
            BlockPos above = ground == null ? null : BlockPos.ofFloored(ground.x, ground.y + .5, ground.z);
            if (ground == null || !world.getBlockState(above).getCollisionShape(world, above).isEmpty()) {
                if (++missing >= 3) break;
                continue;
            }
            missing = 0; level = ground.y; bases.add(ground);
        }
        return bases;
    }

    /** The walls come up along the cracks, from its hands outward. */
    private void erupt(ServerWorld world, long now) {
        for (List<Vec3d> crack : cracks) {
            for (int i = 0; i < crack.size(); i++) {
                Vec3d before = crack.get(Math.max(0, i - 1)), after = crack.get(Math.min(crack.size() - 1, i + 1));
                // Each spike's teeth line up along its wall (see GuardianSpikeRenderer).
                float yaw = (float)Math.toDegrees(Math.atan2(-(after.z - before.z), after.x - before.x));
                int delay = GuardianScarRules.ripple(i, crack.size());
                eruptions.add(new Eruption(crack.get(i), yaw, now + delay, GuardianScarRules.LIFE - delay));
            }
        }
        cracks = List.of();
        pen = scoredPen; scoredPen = null; penUntil = now + GuardianScarRules.LIFE;
        world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_SHELL_CRACK, SoundCategory.HOSTILE, 2f, .55f);
        world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_GROUND_IMPACT, SoundCategory.HOSTILE, 1.6f, .6f);
    }

    /** Spikes coming up hurt whoever stands on the crack; standing spikes hurt whoever touches them. */
    private void tickSpikes(ServerWorld world, long now) {
        eruptions.removeIf(eruption -> {
            if (now < eruption.at()) return false;
            Vec3d base = eruption.base();
            var spike = new GuardianSpikeEntity(ModEntities.GUARDIAN_SPIKE, world);
            spike.setPosition(base); spike.setYaw(eruption.yaw()); spike.begin(eruption.life());
            world.spawnEntity(spike); spikes.add(spike);
            world.spawnParticles(STONE, base.x, base.y + .5, base.z, 8, .3, .7, .3, .12);
            Box body = GuardianScarRules.column(base);
            for (AstralDoubleEntity clone : world.getEntitiesByClass(AstralDoubleEntity.class, body, e -> !e.isRemoved()))
                clone.damage(world, world.getDamageSources().mobAttack(guardian), 1);
            for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(guardian, p) && !scarHits.contains(p.getUuid()))) {
                if (!body.intersects(player.getBoundingBox())) continue;
                scarHits.add(player.getUuid()); spikeTouch.put(player.getUuid(), now);
                if (strike(world, player, world.getDamageSources().mobAttack(guardian), GuardianScarRules.ERUPT_DAMAGE, "scar eruption"))
                    knockAway(player, base, 1.0);
            }
            return true;
        });
        spikes.removeIf(Entity::isRemoved);
        if (pen != null && now >= penUntil) pen = null;
        if (spikes.isEmpty()) return;
        for (ServerPlayerEntity player : world.getPlayers(p -> canDamage(guardian, p))) {
            UUID id = player.getUuid();
            if (now - spikeTouch.getOrDefault(id, -1000L) < GuardianScarRules.TOUCH_INTERVAL) continue;
            Box body = player.getBoundingBox().expand(.12);
            for (GuardianSpikeEntity spike : spikes) {
                if (!spike.standing() || spike.elapsed(0) < GuardianSpikeEntity.RISE || !spike.getBoundingBox().intersects(body)) continue;
                spikeTouch.put(id, now);
                if (strike(world, player, world.getDamageSources().mobAttack(guardian), GuardianScarRules.TOUCH_DAMAGE, "spikes"))
                    knockAway(player, spike.getEntityPos(), .9);
                break;
            }
        }
    }

    private void clearScar() {
        for (GuardianSpikeEntity spike : spikes) spike.discard();
        spikes.clear(); eruptions.clear(); cracks = List.of(); pen = null; scoredPen = null; spikeTouch.clear();
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

    /** Where a stone leaving {@code origin} after {@code wait} ticks meets this player, in the air or on the ground. */
    private Vec3d airAim(ServerPlayerEntity player, Vec3d origin, int wait) {
        UUID id = player.getUuid();
        Vec3d center = player.getBoundingBox().getCenter(), velocity = flight.getOrDefault(id, Vec3d.ZERO);
        double falling = fall.getOrDefault(id, 0.0);
        if (player.isOnGround()) { velocity = new Vec3d(velocity.x, 0, velocity.z); falling = 0; }
        Vec3d aim = GuardianAirburstRules.aim(origin, center, velocity, falling, wait);
        // Never under the floor they would land on.
        double floor = player.getY() - clearance.getOrDefault(id, 0.0) + player.getHeight() / 2;
        return aim.y < floor ? new Vec3d(aim.x, floor, aim.z) : aim;
    }

    /** Three bursts of three stones. Each commits its aim, flies to where its target will be and bursts there. */
    private void tickAirburst(ServerWorld world,long now) {
        int age=(int)(now-started);
        if (age>=GuardianFanRules.duration()) { finishAction(now); return; }
        if (guardian.getEntityPos().squaredDistanceTo(anchor)>.75*.75) { interruptAction(); nextAction=now+20; return; }
        guardian.setYaw(attackYaw);guardian.setBodyYaw(attackYaw);guardian.setHeadYaw(attackYaw);
        guardian.setVelocity(0,Math.min(0,guardian.getVelocity().y),0);
        if (burstSpent) return;
        int burst = GuardianFanRules.burst(age);
        int tick = (int)GuardianFanRules.localTime(age);
        if (burst > 0 && burst != GuardianFanRules.burst(age - 1)) {
            var players=world.getPlayers(p -> reviewing ? p.isAlive() && !p.isSpectator() : canDamage(guardian,p));
            List<Candidate> pool=candidates(players,now), aloft=pool.stream().filter(c -> aloft(c.id())).toList();
            // Later bursts go to whoever is still in the air, spread across the party where they can.
            Candidate next=aloft.isEmpty() && !reviewing ? null
                    : target(Attack.AIRBURST,aloft.isEmpty()?pool:aloft,lastTargeted,now,guardian.isUnstable(),target);
            // Nobody left up there: no more stones form, and the clip plays out.
            if(next==null) { burstSpent=true; guardian.clearFan(); return; }
            target=next.id(); lastTargeted.put(target,now);
        }
        if (tick<GuardianFanRules.LOCK) {
            ServerPlayerEntity player=world.getServer().getPlayerManager().getPlayer(target);
            if(player==null || !player.isAlive() || player.isSpectator() || player.getEntityWorld()!=world
                    || (!reviewing && !canDamage(guardian,player)) || guardian.distanceTo(player)>48) { interruptAction();nextAction=now+20;return; }
            face(player.getEntityPos(),false);
            lockedAim=airAim(player,GuardianFanRules.socket(guardian.getEntityPos(),attackYaw,fanPitch,1),GuardianFanRules.RELEASE-tick);
            fanPitch=aimPitch(lockedAim);
            guardian.setYaw(attackYaw);guardian.setBodyYaw(attackYaw);guardian.setHeadYaw(attackYaw);
        }
        guardian.syncFan(started,fanPitch);
        if (tick==8) world.playSound(null,guardian.getBlockPos(),ModSounds.ENTITY_GUARDIAN_FAN_CHARGE,SoundCategory.HOSTILE,1.6f,.6f);
        if (tick == GuardianFanRules.LOCK) {
            for (int i = 0; i < GuardianFanRules.COUNT; i++) {
                Vec3d socket = GuardianFanRules.socket(guardian.getEntityPos(), attackYaw, fanPitch, i);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, socket.x, socket.y, socket.z,
                        6, .2, .2, .2, .03);
            }
        }
        if (tick==GuardianFanRules.RELEASE) {
            var walls=new GuardianWallImpact();
            for(int i=0;i<GuardianFanRules.COUNT;i++) {
                Vec3d socket=GuardianFanRules.socket(guardian.getEntityPos(),attackYaw,fanPitch,i);
                Vec3d source=guardian.getEntityPos().add(0,3.3,0);
                // Floating stones cannot materialize on the far side of solid cover.
                if (walls.clip(world,guardian,source,socket,false,true).squaredDistanceTo(socket)>1e-10) continue;
                Vec3d aim=socket.add(GuardianFanRules.direction(socket,lockedAim,i).multiply(socket.distanceTo(lockedAim)));
                var stone=new GuardianRockEntity(ModEntities.GUARDIAN_ROCK,world);
                stone.setOwner(guardian);stone.setPosition(socket);
                stone.releaseBurst(aim,GuardianAirburstRules.flightTicks(socket,aim),burstHits,walls);
                world.spawnEntity(stone);rocks.add(stone);
            }
            world.playSound(null,guardian.getBlockPos(),ModSounds.ENTITY_GUARDIAN_FAN_LAUNCH,SoundCategory.HOSTILE,1.6f,1.1f);
        }
    }

    /** The break: grinding and sparks as a warning, the shell bursting at {@link GuardianPhaseRules#BURST}, then a roar. */
    private void tickBreak(ServerWorld world, int time) {
        Vec3d feet = guardian.getEntityPos();
        if (time < GuardianPhaseRules.BURST) {
            if (time % 8 == 0)
                world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_SHELL_BREAK_LOOP, SoundCategory.HOSTILE, 2f, .45f + time * .01f);
            if (time % 2 == 0) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + 3.3, feet.z, 3 + time / 4, 1.1, 1.3, 1.1, .05);
                world.spawnParticles(STONE, feet.x, feet.y + 3, feet.z, 2 + time / 8, 1, 1.2, 1, .04);
            }
        } else if (time == GuardianPhaseRules.BURST) {
            burst(world, feet);
        } else if (time == GuardianPhaseRules.BURST + 8) {
            world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_VOICE_ROAR, SoundCategory.HOSTILE, 2.5f, .45f);
        }
    }

    private void burst(ServerWorld world, Vec3d feet) {
        Vec3d core = feet.add(0, GuardianShellRules.CORE_HEIGHT, 0);
        world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_SHELL_BREAK_BURST_BOOM, SoundCategory.HOSTILE, 1.8f, .8f);
        world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_SHELL_BREAK_BURST_CRACK, SoundCategory.HOSTILE, 2f, .5f);
        world.spawnParticles(STONE, core.x, core.y, core.z, 140, 2.2, 1.6, 2.2, .35);
        world.spawnParticles(ParticleTypes.EXPLOSION, core.x, core.y, core.z, 3, .8, .6, .8, 0);
        breakDoubles(world, feet, GuardianPhaseRules.BURST_RADIUS);
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
            world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_PULSE_LOOP, SoundCategory.HOSTILE, 2.5f, .6f + tick * .012f);
    }

    private void blast(ServerWorld world, Vec3d feet, Vec3d core, List<ServerPlayerEntity> players) {
        world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_PULSE_BLAST_SONIC, SoundCategory.HOSTILE, 3f, .7f);
        world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_PULSE_BLAST_BOOM, SoundCategory.HOSTILE, 2f, .6f);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, core.x, core.y, core.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.EXPLOSION, core.x, core.y, core.z, 6, 2, 1, 2, 0);
        world.spawnParticles(STONE, feet.x, feet.y + .5, feet.z, 90, 3, .4, 3, .2);
        breakDoubles(world, feet, GuardianPulseRules.BLAST_RADIUS);
        var walls = new GuardianWallImpact();
        for (ServerPlayerEntity player : players) {
            if (!GuardianPulseRules.inBlast(feet, player.getBoundingBox())
                    || !walls.clear(world, guardian, core, player.getBoundingBox().getCenter(), false)) continue;
            if (strike(world, player, world.getDamageSources().mobAttack(guardian), GuardianPulseRules.DAMAGE, "core pulse"))
                knockAway(player, feet, 1.8);
        }
    }

    /** Co-op pressure: while it commits to one player, stones lift off its back for the others. */
    private void backStones(ServerWorld world, long now) {
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
            log.cast(now, "back stones", victim == null ? "?" : victim.getName().getString());
        }
        world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_VOLLEY_CHARGE, SoundCategory.HOSTILE, 1.6f, .8f);
    }

    /**
     * Back stones rise with a spark, then go. At a player on the ground they burst on a ring marked
     * where that player is heading; at a player in the air they fly straight to them.
     */
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
            Vec3d body = guardian.getEntityPos().add(0, GuardianShellRules.CORE_HEIGHT, 0);
            boolean inAir = aloft(player.getUuid());
            Vec3d mark = null;
            if (!inAir) {
                Vec3d led = lead(player.getEntityPos(), drift.getOrDefault(player.getUuid(), Vec3d.ZERO), GuardianAirburstRules.FUSE);
                Vec3d ground = GuardianWaveSurface.ground(world, guardian, player.getEntityPos(), led.x, led.z);
                mark = ground == null ? led : ground;
            }
            boolean released = false;
            for (int i = 0; i < volley.stones().size(); i++) {
                var stone = volley.stones().get(i);
                if (stone.isRemoved()) continue;
                Vec3d socket = stone.getEntityPos();
                // Stones cannot launch from the far side of solid cover.
                if (walls.clip(world, guardian, body, socket, false, true).squaredDistanceTo(socket) > 1e-10) { stone.discard(); continue; }
                Vec3d aim = inAir ? airAim(player, socket, 0) : mark.add((i - 1) * .5, .45, 0);
                stone.releaseBurst(aim, inAir ? GuardianAirburstRules.flightTicks(socket, aim) : GuardianAirburstRules.FUSE, hits, walls);
                released = true;
            }
            if (released && mark != null) rings.add(new Ring(mark, GuardianAirburstRules.BLAST, now + GuardianAirburstRules.FUSE, false));
            world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_VOLLEY_LAUNCH, SoundCategory.HOSTILE, 1.3f, 1.2f);
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
    /** The leap marks its landing once it commits to it. */
    void markRing(Vec3d center, double radius, long until) { rings.add(new Ring(center, radius, until, true)); }
    void clearLeapRing() { rings.removeIf(Ring::leap); }

    void tickEffects(ServerWorld world) {
        expireWaves(world);
        long time = world.getTime();
        tickSpikes(world, time);
        rings.removeIf(ring -> time > ring.until());
        if (time % 2 == 0) for (Ring ring : rings) {
            int points = (int)Math.ceil(ring.radius() * 9);
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2 * i / points;
                Vec3d rim = ring.center().add(Math.cos(angle) * ring.radius(), 0, Math.sin(angle) * ring.radius());
                if (ring.gap() > 0 && !GuardianSweepRules.behind(ring.center(), ring.yaw(), rim)) continue;
                mark(world, ring.center(), rim);
            }
            // The two edges of the open wedge, so the safe ground in front of it can be read.
            if (ring.gap() > 0) for (int side = -1; side <= 1; side += 2) {
                Vec3d edge = Vec3d.fromPolar(0, ring.yaw() + side * (float)ring.gap());
                for (double out = 2; out < ring.radius(); out += .7) mark(world, ring.center(), ring.center().add(edge.multiply(out)));
            }
        }
        for (TravelingWave w : waves)
            wave(world, (int)(world.getTime()-w.start()), w.origin(), w.hits(), w.unstable(), w.walls());
        if (guardian.isUnstable() && !guardian.isArenaHidden() && (engaged || reviewing) && world.getTime()%4 == 0) {
            int count = guardian.isBreaking() ? 7 : 2;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, guardian.getX(),guardian.getY()+3.3,guardian.getZ(),count,1.1,.8,1.1,.04);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, guardian.getX(),guardian.getY()+3,guardian.getZ(),count,1.5,1.5,1.5,.14);
        }
    }

    private void mark(ServerWorld world, Vec3d origin, Vec3d at) {
        Vec3d point = GuardianWaveSurface.ground(world, guardian, origin, at.x, at.z);
        if (point != null) world.spawnParticles(IMPACT_RING, point.x, point.y + .15, point.z, 1, .05, 0, .05, 0);
    }

    /** A slam's blast pops any Astral Double it reaches. */
    void breakDoubles(ServerWorld world, Vec3d center, double radius) {
        for (AstralDoubleEntity clone : world.getEntitiesByClass(AstralDoubleEntity.class,
                new net.minecraft.util.math.Box(center,center).expand(radius+1,3,radius+1),e->!e.isRemoved())) {
            Vec3d point = Vec3d.ofCenter(clone.getBlockPos()), delta = point.subtract(center);
            if (delta.horizontalLength() <= radius && Math.abs(delta.y) <= 2
                    && GuardianWaveSurface.clearLine(world,guardian,center.add(0,1,0),point.add(0,.6,0)))
                clone.damage(world,world.getDamageSources().mobAttack(guardian),1);
        }
    }

    void leapLanded(ServerWorld world, Set<UUID> impactVictims) {
        Vec3d center = guardian.getEntityPos();
        breakDoubles(world, center, 6);
        GuardianWallImpact walls=new GuardianWallImpact();
        for(BlockPos pos:com.anton.elementalwands.item.StoneAbilityHandler.guardianWallBlocks(world)) {
            if(GuardianLeapRules.damage(center,new net.minecraft.util.math.Box(pos))>0)
                walls.clip(world,guardian,center.add(0,1,0),pos.toCenterPos(),false,true);
        }
        impactSound(world);
        world.playSound(null,guardian.getBlockPos(),ModSounds.ENTITY_GUARDIAN_LEAP_LAND_BOOM,SoundCategory.HOSTILE,1.6f,.65f);
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
        for (AstralDoubleEntity clone : world.getEntitiesByClass(AstralDoubleEntity.class,
                new net.minecraft.util.math.Box(center,center).expand(radius+2,6,radius+2),e->!e.isRemoved())) {
            Vec3d floor=GuardianWaveSurface.ground(world,guardian,center,clone.getX(),clone.getZ());
            if(floor!=null && waveContact(center,clone.getBoundingBox(),previous,radius,floor.y)
                    && walls.clear(world,guardian,center.add(0,.7,0),floor.add(0,.7,0),true))
                clone.damage(world,world.getDamageSources().mobAttack(guardian),1);
        }
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
            if (p == null) continue;
            Box body = player.getBoundingBox();
            if (!waveContact(center, body, previous, radius, p.y)) {
                // Over it as it passed underneath: it remembers, and answers the next one with a held slam.
                if (waveCrossing(center, body, previous, radius) && !player.isOnGround()
                        && body.minY > p.y + WAVE_HEIGHT && body.minY < p.y + 3
                        && walls.clear(world,guardian,center.add(0,.7,0),p.add(0,.7,0),true)) jumped.put(player.getUuid(), true);
                continue;
            }
            if (!walls.clear(world,guardian,center.add(0,.7,0),p.add(0,.7,0),true)) continue;
            hitPlayers.add(player.getUuid());
            jumped.put(player.getUuid(), false);
            if (guardian.strikeGroundWave(world, player, center, WAVE_DAMAGE)) {
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
        guardian.clearFan(); guardian.clearPulse(); chain = 0; walking = false;
        guardian.clearWave(); waves.clear(); volleys.clear(); rings.clear(); clearScar(); unfreeze(); active = null; waking = 0;
        if (heldRock != null) { heldRock.discard(); heldRock = null; }
        for (GuardianRockEntity rock : rocks) rock.discard();
        rocks.clear();
        guardian.getNavigation().stop(); guardian.stopTriggeredAnim("guardian", null);
    }
    private void impactSound(ServerWorld world) {
        world.playSound(null, guardian.getBlockPos(), ModSounds.ENTITY_GUARDIAN_GROUND_IMPACT, SoundCategory.HOSTILE, 1.5f, .45f);
        world.spawnParticles(STONE, guardian.getX(), guardian.getY()+.2, guardian.getZ(), 35, 2,.15,2,.08);
    }
}
