package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Move;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules.Read;
import com.anton.elementalwands.entity.firstwizard.spell.BossSpell;
import com.anton.elementalwands.entity.firstwizard.spell.BossSpells;
import com.anton.elementalwands.entity.firstwizard.spell.Cast;
import com.anton.elementalwands.entity.firstwizard.spell.Casting;
import com.anton.elementalwands.entity.firstwizard.spell.Lingering;
import com.anton.elementalwands.entity.firstwizard.spell.PhaseChange;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.consume.UseAction;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import static com.anton.elementalwands.entity.firstwizard.FirstWizardRules.*;

/**
 * The fight loop, on the server: one spell at a time, chosen by {@link FirstWizardRules} and carried out by its
 * {@link BossSpell}. Between spells the copy holds a middle distance from its target, turning at a limited rate, and
 * strafes. Everything a spell leaves (effect entities, lingering effects, temporary blocks) is kept here, so stopping
 * the fight, its death, its removal and the server stopping clear all of it. The loop never names a spell.
 */
final class FirstWizardCombat {
    static final RegistryKey<DamageType> SPELL = damage("first_wizard_spell"), GROUND = damage("first_wizard_ground");
    private static final int HISTORY = 12;

    /** What it has seen of one player lately. */
    private static final class Seen {
        final ArrayDeque<Vec3d> path = new ArrayDeque<>();     // newest first, one per tick
        int still;
    }

    private final FirstWizardEntity boss;
    private final ServerBossBar bar = new ServerBossBar(Text.translatable("entity.elementalwands.first_wizard"), BossBar.Color.PURPLE, BossBar.Style.PROGRESS);
    private final Map<UUID, Seen> seen = new HashMap<>();
    private Arena arena;
    /** Fighting for the End's encounter: only its roster, on its floor. */
    private boolean encounter;
    private final Set<UUID> roster = new LinkedHashSet<>();
    private boolean engaged, reviewing, opened;
    /** Half health was reached in phase one: the change starts once the spell in hand has finished. */
    private boolean changePending;
    /** The change is the cast in hand ({@link PhaseChange}). */
    private boolean changing;
    /** When the last Hollow Purple began, for its wait. */
    private long lastPurple = Long.MIN_VALUE / 2;
    /** Vanilla entities its moves left (the flame's cloud), cleared with everything else. */
    private final List<Entity> adopted = new ArrayList<>();
    /** The cast in hand's clocks and name (a move's, or the change's). */
    private com.anton.elementalwands.entity.firstwizard.spell.Timing timing;
    private String castId;
    /** Passive for Peaceful: engages again when the difficulty rises; a kill meanwhile is not the roster's win. */
    private boolean peaceful;
    private UUID target, lastTarget;
    private long nextPick, emptySince = -1;
    private List<ServerPlayerEntity> current = List.of();
    // The spell in hand.
    private BossSpell spell;
    private Casting casting;
    private CastImpl cast;
    private Move last, before, queued;
    private Vec3d lastAim;
    // What spells left behind.
    private final List<Lingering> lingering = new ArrayList<>(), incoming = new ArrayList<>();
    private final List<FirstWizardEffectEntity> effects = new ArrayList<>();
    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private int strafe = 1;
    private long strafeUntil;

    FirstWizardCombat(FirstWizardEntity boss) { this.boss = boss; }

    private static RegistryKey<DamageType> damage(String name) {
        return RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, name));
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Fights again after a calm or a rehearsal. A summoned copy: on its floor round where it stands, anyone Survival or
     * Adventure nearby, once one comes near. A copy fighting for the End: its roster, at once.
     */
    void start() {
        cancel();
        if (arena == null) arena = floorHere();
        engaged = encounter;
        nextPick = now() + OPEN_DELAY;
    }

    /** The End's fight: its floor and roster, from now. */
    void startEncounter(Arena arena, List<ServerPlayerEntity> players) {
        cancel();
        this.arena = arena;
        encounter = true;
        roster.clear();
        for (ServerPlayerEntity player : players) roster.add(player.getUuid());
        boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health(roster.size()));
        boss.setHealth(boss.getMaxHealth());
        phaseOne();
        engaged = true;
        nextPick = now() + OPEN_DELAY;
    }

    void forgetEncounter() { encounter = false; roster.clear(); }
    boolean inEncounter() { return encounter; }
    void restoreArena(Arena arena) { this.arena = arena; }
    Arena arena() { return arena; }

    void addParticipant(ServerPlayerEntity player) {
        if (roster.add(player.getUuid())) grow(roster.size());
    }

    void removeParticipant(UUID player) {
        roster.remove(player);
        if (player.equals(target)) target = null;
    }

    /** A late arrival grows it to the larger party; it keeps its share of health, so nobody gains by starting short. */
    private void grow(int players) {
        float wanted = health(players), max = boss.getMaxHealth();
        if (wanted <= max) return;
        float share = boss.getHealth() / Math.max(1, max);
        boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(wanted);
        boss.setHealth(share * wanted);
    }

    boolean eligible(ServerPlayerEntity player) { return !encounter || roster.contains(player.getUuid()); }

    /** Ends the fight without a result: the spell in hand, everything it left, and the boss bar go. */
    void cancel() {
        cancelCast();
        clearLeftovers();
        engaged = reviewing = opened = false;
        target = lastTarget = null;
        last = before = queued = null;
        lastAim = null;
        emptySince = -1;
        seen.clear();
        current = List.of();
        bar.clearPlayers();
        halt();
    }

    /** Everything spells left goes at once: effect entities, lingering effects, temporary blocks, adopted entities. */
    private void clearLeftovers() {
        ServerWorld world = boss.getEntityWorld() instanceof ServerWorld w ? w : null;
        List<Lingering> running = new ArrayList<>(lingering);
        running.addAll(incoming);
        lingering.clear(); incoming.clear();
        if (world != null) for (Lingering each : running) each.cancel(world);
        for (FirstWizardEffectEntity effect : effects) if (!effect.isRemoved()) effect.discard();
        effects.clear();
        if (world != null) for (Map.Entry<BlockPos, BlockState> block : blocks.entrySet()) world.setBlockState(block.getKey(), block.getValue(), Block.NOTIFY_ALL);
        blocks.clear();
        for (Entity entity : adopted) if (!entity.isRemoved()) entity.discard();
        adopted.clear();
    }

    /** Passive: drops the spell in hand; what it already cast burns out. */
    void calm() {
        cancelCast();
        engaged = reviewing = false;
        bar.clearPlayers();
        halt();
    }

    private void cancelCast() {
        if (casting != null) casting.cancel();
        casting = null; spell = null; cast = null; timing = null; castId = null;
        boss.showCast("", 0, 0, 0);
        boss.showFocus(null);
        boss.showReach(null);
        // A change cut short ends torn: phase two at once, never between.
        if (changing) phaseTwoNow();
    }

    // ── The phase change ─────────────────────────────────────────────────────

    /** Half health in phase one: held there until the change begins, once the spell in hand has finished. */
    void enterPhaseTwo() { if (boss.phase() == FirstWizardEntity.PHASE_ONE) changePending = true; }
    boolean changePending() { return changePending; }

    /** /ew wizard phase two: down to half health if above, and the change as soon as the move in hand allows. */
    boolean requestPhaseTwo() {
        if (boss.phase() != FirstWizardEntity.PHASE_ONE || changePending) return false;
        float half = boss.getMaxHealth() * PHASE_TWO_AT;
        if (boss.getHealth() > half) boss.setHealth(half);
        changePending = true;
        return true;
    }

    /** Phase two without the set piece: a change cut short, a reload, a fixture. */
    void phaseTwoNow() {
        changePending = changing = false;
        boss.setPhase(FirstWizardEntity.PHASE_TWO);
    }

    private void phaseOne() {
        changePending = changing = false;
        lastPurple = Long.MIN_VALUE / 2;
        boss.setPhase(FirstWizardEntity.PHASE_ONE);
    }

    /**
     * The change starts now: it is the cast in hand until it is free. Nothing of phase one runs on into it (the fight
     * pauses for its cinematic): what spells left is cleared, and a follow-up that was coming is dropped.
     */
    private void beginChange() {
        changePending = false;
        halt();
        clearLeftovers();
        queued = rehearsedFollow = null;
        CastImpl next = new CastImpl(now(), target, lastAim);
        cast = next;
        spell = null;
        timing = PhaseChange.TIMING;
        castId = "phase_change";
        changing = true;
        boss.setPhase(FirstWizardEntity.CHANGING);
        casting = new PhaseChange(next);
        boss.showCast(castId, next.started, timing.release(), timing.free());
        casting.tick(0);
    }

    /** One spell at this player, then passive. */
    boolean testMove(ServerPlayerEntity player, Move move) {
        if (BossSpells.get(move) == null) return false;
        if (move.phase > 1 && !boss.inPhaseTwo()) return false;
        cancelCast();
        if (arena == null) arena = floorHere();
        reviewing = true;
        target = player.getUuid();
        current = List.of(player);
        observe(current);
        face(player.getEntityPos(), 180);
        rehearsedFollow = null;
        return begin(move, player);
    }

    /** A rehearsal's scripted follow-up, cast as the fight casts one (no turn toward the player first). */
    private Move rehearsedFollow;

    /** One move at this player and then, as in the fight, its scripted follow-up if it has one (always), then passive. */
    boolean testCombination(ServerPlayerEntity player, Move move) {
        if (!testMove(player, move)) return false;
        rehearsedFollow = FirstWizardRules.followUp(move, BossSpells.available(), () -> 0);
        return true;
    }

    /** It died: true when it died fighting (for the encounter, to its roster). */
    boolean died(ServerWorld world) {
        boolean fighting = !peaceful && (engaged || encounter);
        world.spawnParticles(ParticleTypes.SQUID_INK, true, false, boss.getX(), boss.getBodyY(.5), boss.getZ(), 160, .4, .9, .4, .08);
        world.spawnParticles(ParticleTypes.PORTAL, true, false, boss.getX(), boss.getBodyY(.5), boss.getZ(), 120, .4, .9, .4, .4);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ENTITY_ENDERMAN_DEATH, SoundCategory.HOSTILE, 2.5f, .5f);
        for (ServerPlayerEntity player : bar.getPlayers())
            player.sendMessage(Text.translatable("message.elementalwands.first_wizard.defeated").formatted(Formatting.DARK_PURPLE), false);
        cancel();
        return fighting;
    }

    String status() {
        String phase = switch (boss.phase()) {
            case FirstWizardEntity.CHANGING -> "changing to phase two";
            case FirstWizardEntity.PHASE_TWO -> "phase two";
            default -> changePending ? "phase one, held at half health for the change" : "phase one";
        };
        return "First Wizard (" + phase + "): " + moveName()
                + ", health " + Math.round(boss.getHealth()) + "/" + Math.round(boss.getMaxHealth())
                + ", lingering " + lingeringCount() + ", " + (encounter ? "End fight, roster " + roster.size() : "summoned")
                + ", " + (boss.isBossAggressive() ? engaged ? "fighting" : "waiting for players" : "passive")
                + ", spells built " + BossSpells.available().stream().map(m -> m.id).toList();
    }

    String moveName() { return castId == null ? "free" : castId; }
    int lingeringCount() { return lingering.size() + incoming.size(); }

    private long now() { return boss.getEntityWorld().getTime(); }

    /** A summoned copy's floor: round where it stands, as wide as the End's. */
    private Arena floorHere() { return new Arena(boss.getEntityPos(), FLOOR_RADIUS); }

    // ── Fight loop ───────────────────────────────────────────────────────────

    /** Passive: a rehearsed spell plays out and what it left runs on. */
    void tickPassive(ServerWorld world) {
        long now = world.getTime();
        // Whoever is on the floor can still be hurt by what it already cast, and is read for a rehearsed spell's aim.
        current = arena == null ? List.of() : players(world);
        observe(current);
        tickLeftovers(world, now);
        if (casting != null) { tickCast(world, now); return; }
        // /ew wizard phase two on a passive copy: it plays the change, then stands passive in phase two.
        if (changePending) { if (arena == null) arena = floorHere(); beginChange(); return; }
        // A rehearsed combination's follow-up, after the same short gap the fight leaves.
        if (reviewing && queued != null) {
            if (now < nextPick) { halt(); return; }
            Move follow = queued;
            queued = null;
            ServerPlayerEntity player = null;
            for (ServerPlayerEntity each : current) if (each.getUuid().equals(target)) player = each;
            if (player == null || !begin(follow, player)) reviewing = false;
            return;
        }
        halt();
    }

    void tick(ServerWorld world) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) { if (engaged) { cancel(); peaceful = true; } return; }
        if (peaceful) { peaceful = false; if (encounter) { engaged = true; nextPick = world.getTime() + OPEN_DELAY; } }
        if (arena == null) arena = floorHere();
        long now = world.getTime();
        current = players(world);
        observe(current);
        tickLeftovers(world, now);
        if (!engaged) {
            if (encounter) return;
            ServerPlayerEntity near = nearest(current, ENGAGE_RANGE);
            if (near == null) return;
            engaged = true; emptySince = -1; nextPick = now + OPEN_DELAY;
            if (!boss.wearsSomeone()) boss.wear(near);
            world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 1.5f, 1.4f);
        }
        updateBar();
        keepOnFloor(world);
        if (current.isEmpty()) {
            cancelCast();
            halt();
            if (emptySince < 0) emptySince = now;
            if (!encounter && now - emptySince >= RESET_AFTER) reset();
            return;
        }
        emptySince = -1;
        if (!encounter) grow(current.size());
        if (casting != null) { tickCast(world, now); return; }
        ServerPlayerEntity player = target();
        // Half health was reached: the change, before anything else.
        if (changePending) { beginChange(); return; }
        if (now >= nextPick) {
            boolean two = boss.inPhaseTwo();
            Move choice = queued != null ? queued : !opened ? opener()
                    : FirstWizardRules.choose(read(player), last, before, usable(now), two, boss.getRandom()::nextDouble);
            queued = null;
            if (choice != null && begin(choice, player)) return;
            nextPick = now + 5;     // nothing fits from here: it moves and looks again shortly
        }
        // A scripted follow-up is coming: it holds still and keeps its heading (a player behind it stays behind it).
        if (queued != null) { halt(); return; }
        approach(player);
    }

    /** The moves the chooser may use now: the phase's, and Hollow Purple only when its health is low and it has waited. */
    private java.util.Set<Move> usable(long now) {
        return FirstWizardRules.usable(BossSpells.available(), boss.inPhaseTwo(), boss.getHealth() / Math.max(1, boss.getMaxHealth()), now - lastPurple);
    }

    private Move opener() { return FirstWizardRules.opener(boss.affinity(), FirstWizardRules.usable(BossSpells.available(), false, 1, 0)); }

    /** A wiped or abandoned summoned fight starts over: full health, waiting for players. */
    private void reset() {
        cancel();
        boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health(1));
        boss.setHealth(boss.getMaxHealth());
        phaseOne();
    }

    private List<ServerPlayerEntity> players(ServerWorld world) {
        double reach = arena.radius() + ENCOUNTER_MARGIN;
        return world.getPlayers(p -> canDamage(boss, p) && arena.fromCentre(p.getEntityPos()) <= reach && Math.abs(p.getY() - arena.floor()) < 48);
    }

    static boolean canDamage(FirstWizardEntity boss, ServerPlayerEntity player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.getEntityWorld() == boss.getEntityWorld() && !boss.isTeammate(player) && boss.eligible(player);
    }

    private ServerPlayerEntity nearest(List<ServerPlayerEntity> players, double within) {
        ServerPlayerEntity best = null;
        double most = within * within;
        for (ServerPlayerEntity player : players) {
            double d = boss.squaredDistanceTo(player);
            if (d <= most) { most = d; best = player; }
        }
        return best;
    }

    private void updateBar() {
        for (ServerPlayerEntity previous : new ArrayList<>(bar.getPlayers())) if (!current.contains(previous)) bar.removePlayer(previous);
        for (ServerPlayerEntity player : current) bar.addPlayer(player);
        bar.setPercent(MathHelper.clamp(boss.getHealth() / Math.max(1, boss.getMaxHealth()), 0, 1));
    }

    /** Lingering effects, then any that started this tick; finished effect entities are forgotten. */
    private void tickLeftovers(ServerWorld world, long now) {
        lingering.addAll(incoming);
        incoming.clear();
        lingering.removeIf(each -> !each.tick(world, now));
        lingering.addAll(incoming);
        incoming.clear();
        effects.removeIf(Entity::isRemoved);
    }

    /** It never leaves its floor: a shove or a bad step is set straight back inside. */
    private void keepOnFloor(ServerWorld world) {
        Vec3d at = boss.getEntityPos();
        if (at.y < arena.floor() - 6) {
            boss.refreshPositionAndAngles(arena.centre().x, arena.floor(), arena.centre().z, boss.getYaw(), 0);
            return;
        }
        if (!arena.inside(at, EDGE * .5)) {
            Vec3d in = arena.clamp(at, EDGE);
            boss.refreshPositionAndAngles(in.x, at.y, in.z, boss.getYaw(), boss.getPitch());
        }
    }

    // ── The spell in hand ────────────────────────────────────────────────────

    private boolean begin(Move move, ServerPlayerEntity player) {
        BossSpell chosen = BossSpells.get(move);
        if (chosen == null) return false;
        halt();
        if (player != null) target = player.getUuid();
        CastImpl next = new CastImpl(now(), target, lastAim);
        cast = next;
        spell = chosen;
        Casting started = chosen.begin(next);
        if (started == null) { cast = null; spell = null; boss.showFocus(null); boss.showReach(null); return false; }
        casting = started;
        timing = chosen.timing();
        castId = move.id;
        opened = true;
        if (move == Move.HOLLOW_PURPLE) lastPurple = next.started;
        boss.showCast(move.id, next.started, chosen.timing().release(), chosen.timing().free());
        casting.tick(0);
        return true;
    }

    private void tickCast(ServerWorld world, long now) {
        int t = (int) (now - cast.started);
        if (t > 0) casting.tick(Math.min(t, timing.free()));
        if (casting == null) return;        // the spell ended the fight (a removal)
        if (t < timing.free()) return;
        boss.showCast("", 0, 0, 0);
        boss.showFocus(null);
        boss.showReach(null);
        if (changing) {
            // The set piece is over: phase two begins, after a gap.
            casting = null; cast = null; timing = null; castId = null;
            phaseTwoNow();
            nextPick = now + gap(current.size(), boss.getRandom()::nextDouble, true);
            return;
        }
        // Free: the next spell, a follow-up straight away or a gap first; and the next target.
        Move done = spell.move();
        before = last; last = done;
        lastAim = cast.aim;
        casting = null; spell = null; cast = null; timing = null; castId = null;
        if (reviewing) {
            if (rehearsedFollow != null) { queued = rehearsedFollow; rehearsedFollow = null; nextPick = now + FOLLOW_GAP; return; }
            reviewing = false;
            return;
        }
        queued = FirstWizardRules.followUp(done, usable(now), boss.getRandom()::nextDouble);
        nextPick = now + (queued != null ? FOLLOW_GAP : gap(current.size(), boss.getRandom()::nextDouble, boss.inPhaseTwo()));
        if (current.size() > 1 && target != null) { lastTarget = target; target = null; }
    }

    // ── Targets ──────────────────────────────────────────────────────────────

    /**
     * Its target: kept until a spell ends; then, in co-op, another player, never the one it just cast at. Nearer
     * players are likelier. A new target is marked with a puff of ink over their head.
     */
    private ServerPlayerEntity target() {
        for (ServerPlayerEntity player : current) if (player.getUuid().equals(target)) return player;
        List<ServerPlayerEntity> pool = current.size() > 1 ? current.stream().filter(p -> !p.getUuid().equals(lastTarget)).toList() : current;
        if (pool.isEmpty()) pool = current;
        double total = 0;
        double[] weights = new double[pool.size()];
        for (int i = 0; i < pool.size(); i++) total += weights[i] = 1 / (1 + Math.sqrt(boss.squaredDistanceTo(pool.get(i))) / 8);
        double roll = boss.getRandom().nextDouble() * total;
        ServerPlayerEntity next = pool.getLast();
        for (int i = 0; i < pool.size(); i++) if ((roll -= weights[i]) < 0) { next = pool.get(i); break; }
        target = next.getUuid();
        if (current.size() > 1 && boss.getEntityWorld() instanceof ServerWorld world)
            world.spawnParticles(ParticleTypes.SQUID_INK, true, false, next.getX(), next.getY() + 2.4, next.getZ(), 14, .2, .2, .2, .02);
        return next;
    }

    private void observe(List<ServerPlayerEntity> players) {
        seen.keySet().removeIf(id -> players.stream().noneMatch(p -> p.getUuid().equals(id)));
        for (ServerPlayerEntity player : players) {
            Seen s = seen.computeIfAbsent(player.getUuid(), id -> new Seen());
            Vec3d at = player.getEntityPos();
            if (!s.path.isEmpty() && s.path.peekFirst().squaredDistanceTo(at) > 36) s.path.clear();     // a teleport clears what it read
            Vec3d previous = s.path.peekFirst();
            s.still = previous != null && previous.squaredDistanceTo(at) < 1e-4 ? s.still + 1 : 0;
            s.path.addFirst(at);
            while (s.path.size() > HISTORY) s.path.removeLast();
        }
    }

    private Read read(ServerPlayerEntity player) {
        Seen s = seen.get(player.getUuid());
        Vec3d to = player.getEntityPos().subtract(boss.getEntityPos()).multiply(1, 0, 1);
        double distance = to.length();
        boolean closing = false, fleeing = false;
        if (s != null && s.path.size() >= 10 && distance > .2) {
            Vec3d moved = s.path.peekFirst().subtract(s.path.peekLast()).multiply(1, 0, 1);
            double along = moved.dotProduct(to.normalize());
            closing = along < -2; fleeing = along > 2;
        }
        boolean crowded = current.stream().anyMatch(p -> p.squaredDistanceTo(boss) < CROWD * CROWD);
        UseAction use = player.isUsingItem() ? player.getActiveItem().getUseAction() : UseAction.NONE;
        return new Read(distance, crowded, closing, fleeing, s != null && s.still >= STILL_TICKS,
                !player.isOnGround() && player.getY() - arena.floor() > .6, use == UseAction.EAT || use == UseAction.DRINK);
    }

    private Vec3d velocity(ServerPlayerEntity player) {
        Seen s = seen.get(player.getUuid());
        if (s == null || s.path.size() < 2) return Vec3d.ZERO;
        int span = Math.min(5, s.path.size() - 1);
        Vec3d newest = s.path.peekFirst(), older = s.path.stream().skip(span).findFirst().orElse(newest);
        return newest.subtract(older).multiply(1.0 / span).multiply(1, 0, 1);
    }

    // ── Free: holding a middle distance ──────────────────────────────────────

    private void approach(ServerPlayerEntity player) {
        Vec3d at = boss.getEntityPos();
        Vec3d to = player.getEntityPos().subtract(at).multiply(1, 0, 1);
        double distance = to.length();
        face(player.getEntityPos(), TURN);
        if (distance < 1e-3) { halt(); return; }
        Vec3d toward = to.multiply(1 / distance), side = new Vec3d(-toward.z, 0, toward.x);
        long now = now();
        if (now >= strafeUntil) { strafe = boss.getRandom().nextBoolean() ? 1 : -1; strafeUntil = now + 40 + boss.getRandom().nextInt(40); }
        Vec3d heading;
        double speed;
        // Phase two: it floats, closer in and a little faster than its walk, easing into and out of each drift.
        boolean two = boss.inPhaseTwo();
        double near = two ? HOLD_NEAR_TWO : HOLD_NEAR, far = two ? HOLD_FAR_TWO : HOLD_FAR;
        if (distance < near) { heading = toward.multiply(-1).add(side.multiply(strafe * .4)).normalize(); speed = two ? BACK_TWO : BACK; }
        else if (distance > far) { heading = toward.add(side.multiply(strafe * .25)).normalize(); speed = two ? DRIFT : WALK; }
        else { heading = side.multiply(strafe); speed = two ? STRAFE_TWO : STRAFE; }
        double step = speed / 20;
        if (!arena.inside(at.add(heading.multiply(step * 6)), EDGE)) {
            // At the edge: the other way round, or in toward the middle.
            strafe = -strafe;
            strafeUntil = now + 40;
            Vec3d inward = arena.centre().subtract(at).multiply(1, 0, 1).normalize();
            Vec3d round = side.multiply(strafe);
            heading = arena.inside(at.add(round.multiply(step * 6)), EDGE) ? round.add(inward.multiply(.5)).normalize() : inward;
        }
        if (two) {
            // Floating: it eases toward the way it wants to go, so it drifts rather than steps.
            Vec3d was = boss.getVelocity();
            double x = was.x + (heading.x * step - was.x) * DRIFT_EASE, z = was.z + (heading.z * step - was.z) * DRIFT_EASE;
            boss.setVelocity(x, Math.min(0, was.y), z);
            return;
        }
        boss.setVelocity(heading.x * step, Math.min(0, boss.getVelocity().y), heading.z * step);
    }

    private void halt() { boss.setVelocity(0, Math.min(0, boss.getVelocity().y), 0); }

    /** Turns it toward a point by at most `limit` degrees, head and body together; returns how far it turned. */
    private float face(Vec3d point, float limit) {
        Vec3d delta = point.subtract(boss.getEntityPos());
        if (delta.x * delta.x + delta.z * delta.z < 1e-4) return 0;
        float wanted = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float turn = MathHelper.clamp(MathHelper.wrapDegrees(wanted - boss.getBodyYaw()), -limit, limit);
        float yaw = boss.getBodyYaw() + turn;
        boss.setYaw(yaw); boss.setBodyYaw(yaw); boss.setHeadYaw(yaw);
        double flat = Math.hypot(delta.x, delta.z);
        boss.setPitch((float) MathHelper.clamp(-Math.toDegrees(Math.atan2(delta.y - 1.5, flat)), -40, 40));
        return turn;
    }

    private DamageSource source(ServerWorld world, RegistryKey<DamageType> type, Entity source) {
        return new DamageSource(world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(type), source == null ? boss : source, boss);
    }

    // ── What a spell is handed ───────────────────────────────────────────────

    private final class CastImpl implements Cast {
        /** The tick it began: moved back by {@link #advance}. */
        long started;
        final UUID targetId;
        final Vec3d previous;
        Vec3d aim;

        CastImpl(long started, UUID targetId, Vec3d previous) { this.started = started; this.targetId = targetId; this.previous = previous; }

        @Override public FirstWizardEntity boss() { return boss; }
        @Override public ServerWorld world() { return (ServerWorld) boss.getEntityWorld(); }
        @Override public Arena arena() { return arena; }
        @Override public long now() { return boss.getEntityWorld().getTime(); }
        @Override public long started() { return started; }
        @Override public Random random() { return boss.getRandom(); }

        @Override public ServerPlayerEntity target() {
            if (targetId == null) return null;
            for (ServerPlayerEntity player : current) if (player.getUuid().equals(targetId)) return player;
            return null;
        }
        @Override public List<ServerPlayerEntity> players() { return current; }
        @Override public boolean crowded() { return current.stream().anyMatch(p -> p.squaredDistanceTo(boss) < CROWD * CROWD); }

        @Override public Vec3d velocity(ServerPlayerEntity player) { return FirstWizardCombat.this.velocity(player); }
        @Override public Vec3d lead(ServerPlayerEntity player, double seconds) {
            Vec3d ahead = velocity(player).multiply(seconds * 20);
            if (ahead.length() > LEAD_MAX) ahead = ahead.normalize().multiply(LEAD_MAX);
            return arena.onFloor(player.getEntityPos().add(ahead), EDGE);
        }

        @Override public float face(Vec3d point, float limitDegrees) { return FirstWizardCombat.this.face(point, limitDegrees); }
        @Override public void focus(Vec3d point) { if (cast == this) boss.showFocus(point); }
        @Override public void aim(Vec3d point) { aim = point; }
        @Override public Vec3d previousAim() { return previous; }
        @Override public void teleport(Vec3d feet) {
            boss.blinkTo(arena.onFloor(feet, EDGE * .5));
        }
        @Override public void step(Vec3d feet) {
            boss.setVelocity(Vec3d.ZERO);
            boss.setPosition(arena.onFloor(feet, EDGE * .5));
        }
        @Override public void step(Vec3d feet, double height) {
            boss.setVelocity(Vec3d.ZERO);
            boss.setPosition(arena.onFloor(feet, EDGE * .5).add(0, Math.max(0, height), 0));
        }
        @Override public void reach(Vec3d point) { if (cast == this) boss.showReach(point); }
        @Override public void advance(int ticks) {
            if (ticks <= 0 || cast != this) return;
            started -= ticks;
            boss.showCast(castId, started, timing.release(), timing.free());
            if (changing) boss.shiftPhase(ticks);
        }

        @Override public <T extends Entity> T adopt(T entity) {
            // The End's encounter removes vanilla entities it did not make; this one is the boss's.
            entity.addCommandTag(com.anton.elementalwands.end.EndEncounter.ACTOR_TAG);
            world().spawnEntity(entity);
            adopted.removeIf(Entity::isRemoved);
            adopted.add(entity);
            return entity;
        }

        @Override public FirstWizardEffectEntity effect(String kind, Vec3d at, int form, int life) {
            FirstWizardEffectEntity effect = FirstWizardEffectEntity.spawn(boss, kind, at, form, life);
            effects.add(effect);
            return effect;
        }
        @Override public void linger(Lingering effect) { incoming.add(effect); }

        @Override public boolean hit(ServerPlayerEntity player, float amount, Hit kind, Entity source) {
            if (!canDamage(boss, player)) return false;
            ServerWorld world = world();
            boolean hurt = player.damage(world, FirstWizardCombat.this.source(world, kind == Hit.GROUND ? GROUND : SPELL, source), amount);
            if (hurt && kind == Hit.SPELL) {
                Vec3d from = source == null ? boss.getEntityPos() : source.getEntityPos();
                Vec3d away = player.getEntityPos().subtract(from).multiply(1, 0, 1);
                if (away.lengthSquared() > 1e-4) {
                    away = away.normalize();
                    player.takeKnockback(.6, -away.x, -away.z);
                    player.velocityModified = true;
                }
            }
            return hurt;
        }

        @Override public boolean placeBlock(BlockPos pos, BlockState state) {
            ServerWorld world = world();
            BlockState there = world.getBlockState(pos);
            if (!blocks.containsKey(pos)) {
                if (world.getBlockEntity(pos) != null || there.getHardness(world, pos) < 0 && !there.isAir()) return false;
                blocks.put(pos.toImmutable(), there);
            }
            return world.setBlockState(pos, state, Block.NOTIFY_ALL);
        }
        @Override public void restoreBlock(BlockPos pos) {
            BlockState original = blocks.remove(pos);
            if (original != null) world().setBlockState(pos, original, Block.NOTIFY_ALL);
        }
    }

    /** For status and fixtures. */
    @Override public String toString() { return status().toLowerCase(Locale.ROOT); }
}
