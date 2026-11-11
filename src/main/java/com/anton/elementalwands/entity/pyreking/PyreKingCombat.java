package com.anton.elementalwands.entity.pyreking;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.registry.ModParticles;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.RaycastContext;
import static com.anton.elementalwands.entity.pyreking.PyreKingRules.*;

/**
 * Server-authoritative duel: one committed, telegraphed move at a time. Between moves he runs at
 * his target and weighs what to do next; a move is a clip, and the blade and chain it swings are
 * swept for hits from the clip's own tracks ({@link PyreKingTracks}), so a blow lands where and
 * when it is drawn. At half health Unbound tears his chains loose and the fight quickens.
 */
final class PyreKingCombat {
    static final RegistryKey<DamageType> BLADE = damage("pyre_king_blade"), CHAINS = damage("pyre_king_chain"), FLAMES = damage("pyre_king_fire");
    private static final Set<PositionFlag> KEEP_VIEW = Set.of(PositionFlag.X_ROT, PositionFlag.Y_ROT);
    private static final int HISTORY = 12;

    /** What he has seen of one player lately. */
    private static final class Seen {
        final ArrayDeque<Vec3d> path = new ArrayDeque<>();     // newest first, one per tick
        long jumped = Long.MIN_VALUE / 2, struck = Long.MIN_VALUE / 2, burnAt;
        float struckFor;
        int behind, aloft, hidden, combo = -1;
        double ground;
    }

    /** Fire left on the floor: a line (or, with both ends the same, a disc) that arms and then burns. */
    private record Fire(Vec3d from, Vec3d to, double half, double height, long armed, long until) {}

    /** A mark on the floor that only warns: the line a throw will fly along, or the ring where he will land. */
    private record Mark(Vec3d from, Vec3d to, double radius, long until) {}

    private final PyreKingEntity boss;
    private final ServerBossBar bar = new ServerBossBar(Text.translatable("entity.elementalwands.pyre_king"), BossBar.Color.RED, BossBar.Style.PROGRESS);
    private final Map<UUID, Seen> seen = new HashMap<>();
    private final List<Fire> fires = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();
    private Vec3d home;
    private boolean engaged, reviewing, phasePending;
    /** A way round a pillar: the move it opens with, and where that move's sword is thrown. */
    private record Flank(Move opener, Vec3d sword) {}
    /** The flank he has chosen and is turning onto the line of. */
    private Flank pending;
    private long pendingUntil;
    /** He is on his way round a pillar: from a flank's first move until its last ends. */
    private boolean flanking;
    private Move lastFlank;
    private long emptySince = -1, nextPick, flankRetry, lastDeflect, lastBlow = Long.MIN_VALUE / 2;
    private UUID target, lastTarget;
    private long targetSince, lastBegan;
    private int steerSide = 1;
    private Move move, last, before;
    private int chained, combo;
    // The move in hand.
    private long started;
    private double swept;
    private int cue;
    private boolean aimFrozen, turned, branched, decided, zipNext, doubled, landed, blowLanded;
    private float retracked;
    /** The move was called off part-way: he recovers in his guard until this moment of it, and nothing more of it happens. */
    private double calledOff = -1;
    private double aimRight, aimUp, aimAhead;
    private Vec3d aimPoint, landing;
    /** A flank: where this move's sword is thrown instead of at his target. */
    private Vec3d flank;
    private final Map<UUID, Integer> groups = new HashMap<>();
    private UUID caught;
    private Vec3d caughtAt;

    PyreKingCombat(PyreKingEntity boss) { this.boss = boss; }

    private static RegistryKey<DamageType> damage(String name) {
        return RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, name));
    }

    static boolean canDamage(PyreKingEntity boss, ServerPlayerEntity player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.getEntityWorld() == boss.getEntityWorld() && !boss.isTeammate(player) && boss.eligible(player);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    void start() {
        cancel();
        home = boss.getEntityPos();
        nextPick = now() + 30;
    }

    void cancel() {
        release();
        // Unbound cut short (an operator's command, Peaceful) has still spent the half-health hold: he is in phase two.
        if (move == Move.UNBOUND) boss.setSecondPhase(true);
        move = null; last = before = lastFlank = null; target = lastTarget = null; flank = null; pending = null; flanking = false;
        engaged = reviewing = false; emptySince = -1; chained = 0;
        fires.clear(); marks.clear(); seen.clear(); groups.clear();
        phasePending = false;
        halt();
        bar.clearPlayers();
    }

    Vec3d home() { return home; }
    void restoreHome(Vec3d home) { this.home = home; }
    private long now() { return boss.getEntityWorld().getTime(); }

    String status() {
        return "Pyre King (" + (boss.isSecondPhase() ? "phase two" : phasePending ? "phase one, Unbound pending" : "phase one") + "): "
                + (move == null ? "free" : move.name().toLowerCase(Locale.ROOT)) + ", health " + Math.round(boss.getHealth()) + "/" + Math.round(boss.getMaxHealth())
                + ", fires " + fires.size() + ", " + (boss.isBossAggressive() ? engaged ? "fighting" : "waiting for players" : "passive");
    }

    boolean testMove(ServerPlayerEntity player, Move next) {
        ServerWorld world = (ServerWorld) boss.getEntityWorld();
        home = boss.getEntityPos();
        reviewing = true;
        if (next != Move.TURNING_CUT) face(player.getEntityPos(), 180);
        if (next == Move.ZIP) {     // A Zip follows a throw: stand the sword where the clip left it, straight ahead.
            begin(world, Move.CAST, player, world.getTime(), 3);
            zipNext = true;
            return true;
        }
        begin(world, next, player, world.getTime(), 3);
        return true;
    }

    boolean requestUnbound() {
        if (boss.unboundDone() || move == Move.UNBOUND) return false;
        phasePending = true;
        return true;
    }

    // ── Hooks from the entity ────────────────────────────────────────────────

    void damaged(Entity attacker, float lost) {}

    /** Half health reached: Unbound starts as soon as the move in hand allows. */
    void gateReached() { if (!boss.unboundDone()) phasePending = true; }

    boolean untouchable() { return move == Move.UNBOUND; }

    /** Sparks fly off his chains where a hit lands during Unbound, at most a few times a second. */
    void deflect(ServerWorld world) {
        long now = world.getTime();
        if (now - lastDeflect < 5) return;
        lastDeflect = now;
        world.spawnParticles(ParticleTypes.LAVA, true, false, boss.getX(), boss.getBodyY(.5), boss.getZ(), 8, .5, .8, .5, .04);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 1.2f, .5f);
    }

    void died(ServerWorld world) {
        world.spawnParticles(ParticleTypes.FLAME, true, false, boss.getX(), boss.getBodyY(.5), boss.getZ(), 220, .7, 1.8, .7, .12);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, true, false, boss.getX(), boss.getBodyY(.5), boss.getZ(), 80, .7, 1.8, .7, .04);
        world.spawnParticles(ParticleTypes.LAVA, true, false, boss.getX(), boss.getBodyY(.5), boss.getZ(), 40, .5, 1.2, .5, 0);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ENTITY_BLAZE_DEATH, SoundCategory.HOSTILE, 3f, .5f);
        world.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 3f, .6f);
        for (ServerPlayerEntity player : bar.getPlayers())
            player.sendMessage(Text.translatable("message.elementalwands.pyre_king.defeated").formatted(Formatting.GOLD), false);
        cancel();
    }

    // ── Fight loop ───────────────────────────────────────────────────────────

    /** Passive: a rehearsed move plays out, a rehearsed clip returns to his guard, and fire burns down. */
    void tickReview(ServerWorld world) {
        long now = world.getTime();
        List<ServerPlayerEntity> players = players(world);
        observe(players, now);
        tickFloor(world, now, players);
        if (move != null) { tickMove(world, now, players); return; }
        PyreKingClips.Clip clip = PyreKingClips.get(boss.clip());
        if (clip != null && !clip.loop() && boss.clipSeconds(0) >= clip.seconds()) boss.play("idle", 0);
    }

    void tick(ServerWorld world) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) { if (engaged) cancel(); return; }
        if (home == null) home = boss.getEntityPos();
        long now = world.getTime();
        List<ServerPlayerEntity> players = players(world);
        observe(players, now);
        tickFloor(world, now, players);
        // Held at half health with Unbound still to come: a reload or a cancelled move must not lose that.
        if (!boss.unboundDone() && boss.getHealth() <= boss.getMaxHealth() * PHASE_GATE + .01f) phasePending = true;
        if (!engaged) {
            if (players.stream().noneMatch(p -> boss.squaredDistanceTo(p) <= ENGAGE_RANGE * ENGAGE_RANGE)) return;
            engaged = true; emptySince = -1; nextPick = now + 30;
            sound(world, boss.getEntityPos(), SoundEvents.ENTITY_RAVAGER_ROAR, 3f, .55f);
            sound(world, boss.getEntityPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 2f, .5f);
        }
        updateBar(players);
        if (players.isEmpty()) {
            if (move != null && move != Move.UNBOUND) { release(); move = null; }
            if (move == null) { halt(); settle(); }
            if (emptySince < 0) emptySince = now;
            if (now - emptySince >= RESET_AFTER) reset(world);
            if (move != null) tickMove(world, now, players);
            return;
        }
        emptySince = -1;
        float health = health(players.size());
        if (health > boss.getMaxHealth()) {
            float extra = health - boss.getMaxHealth();
            boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health);
            boss.setHealth(boss.getHealth() + extra);       // Damage already dealt is kept; health never shrinks mid-fight.
        }
        if (move != null) { tickMove(world, now, players); return; }
        if (phasePending && !boss.unboundDone()) { begin(world, Move.UNBOUND, null, now, 3); return; }
        ServerPlayerEntity player = target(world, players);
        Read read = read(world, player, now);
        Seen watched = seen.get(player.getUuid());
        boolean hiding = !read.sight() && !read.elevated();
        if (pending != null && !hiding) pending = null;
        if (pending != null) {
            // A Haul or a Lunge is drawn from where it began: he turns onto the line of his throw, then goes.
            halt();
            settle();
            face(pending.sword(), FLANK_TURN);
            if (Math.abs(off(pending.sword())) <= 4) {
                Flank go = pending;
                pending = null;
                begin(world, go.opener(), player, now, 2);
                flank = go.sword();
                flanking = true;
                lastFlank = go.opener();
            } else if (now >= pendingUntil) pending = null;
            return;
        }
        // Hiding behind a pillar buys a moment, not a rest, and eating or drinking there buys less: he gets round it.
        if (hiding && now >= flankRetry && watched != null && (read.healing()
                ? watched.hidden >= FLANK_HEALING && now >= nextPick - 12
                : watched.hidden >= FLANK_AFTER && now >= nextPick)) {
            pending = flank(world, player, read);
            if (pending != null) { pendingUntil = now + 10; halt(); return; }
            flankRetry = now + 10;      // nowhere to throw it from here: he keeps coming round, and looks again shortly
        }
        if (now >= nextPick || punishes(read) && now >= nextPick - 12) {
            Move choice = choose(read, last, before, boss.getRandom()::nextDouble);
            if (choice != null && ready(choice, player)) { begin(world, choice, player, now, 3); return; }
        }
        approach(world, player, read);
    }

    /** A wiped or abandoned fight starts over: full health, phase one, back at his mark. */
    private void reset(ServerWorld world) {
        Vec3d back = home;
        cancel();
        boss.setUnboundDone(false);
        boss.setSecondPhase(false);
        boss.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(health(1));
        boss.setHealth(boss.getMaxHealth());
        if (back != null) {
            home = back;
            boss.refreshPositionAndAngles(back.x, back.y, back.z, boss.getYaw(), 0);
        }
        boss.play("idle", 0);
    }

    private List<ServerPlayerEntity> players(ServerWorld world) {
        Vec3d centre = home != null ? home : boss.getEntityPos();
        return world.getPlayers(p -> canDamage(boss, p) && p.squaredDistanceTo(centre) <= ENCOUNTER_RANGE * ENCOUNTER_RANGE);
    }

    private void updateBar(List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity previous : new ArrayList<>(bar.getPlayers())) if (!players.contains(previous)) bar.removePlayer(previous);
        for (ServerPlayerEntity player : players) bar.addPlayer(player);
        bar.setPercent(MathHelper.clamp(boss.getHealth() / Math.max(1, boss.getMaxHealth()), 0, 1));
    }

    // ── What he sees ─────────────────────────────────────────────────────────

    private void observe(List<ServerPlayerEntity> players, long now) {
        ServerWorld world = (ServerWorld) boss.getEntityWorld();
        seen.keySet().removeIf(id -> players.stream().noneMatch(p -> p.getUuid().equals(id)));
        Vec3d forward = forward(boss.getBodyYaw());
        for (ServerPlayerEntity player : players) {
            Seen s = seen.computeIfAbsent(player.getUuid(), id -> new Seen());
            Vec3d at = player.getEntityPos();
            if (!s.path.isEmpty() && s.path.peekFirst().squaredDistanceTo(at) > 36) s.path.clear();    // a teleport clears what he read
            s.path.addFirst(at);
            while (s.path.size() > HISTORY) s.path.removeLast();
            if (player.isOnGround()) s.ground = at.y;
            else if (at.y - s.ground > .35 && at.y - s.ground < 2.2 && player.getVelocity().y > -.6) s.jumped = now;
            Vec3d to = at.subtract(boss.getEntityPos()).multiply(1, 0, 1);
            double distance = to.length();
            boolean behind = distance <= 6.5 && distance > .2 && to.normalize().dotProduct(forward) < -.5;
            s.behind = behind ? s.behind + 1 : 0;
            s.aloft = at.y - boss.getY() > ELEVATED && (player.isOnGround() || player.getVelocity().y > -.3) ? s.aloft + 1 : 0;
            s.hidden = sight(world, player) ? 0 : s.hidden + 1;
        }
    }

    /** He never picks the same player twice running when another is there; alone, the one he has. */
    private ServerPlayerEntity target(ServerWorld world, List<ServerPlayerEntity> players) {
        ServerPlayerEntity current = target == null ? null : players.stream().filter(p -> p.getUuid().equals(target)).findFirst().orElse(null);
        // A target he has chased this long without a blow has had its turn: one player cannot hold him behind a pillar while another hits him.
        if (current != null && players.size() > 1 && now() - Math.max(targetSince, lastBegan) > PATIENCE) { lastTarget = target; current = null; }
        if (current != null && (chained > 0 || now() < nextPick)) return current;       // mid-chain, or still closing on them
        ServerPlayerEntity next = players.stream()
                .filter(p -> players.size() == 1 || !p.getUuid().equals(lastTarget))
                .min(Comparator.comparingDouble(boss::squaredDistanceTo)).orElse(players.get(0));
        if (!next.getUuid().equals(target)) {
            target = next.getUuid();
            targetSince = now();
            if (players.size() > 1) {       // the switch is announced: a flame over the player he has chosen
                world.spawnParticles(ParticleTypes.FLAME, true, false, next.getX(), next.getY() + 2.5, next.getZ(), 24, .25, .3, .25, .01);
                sound(world, next.getEntityPos(), SoundEvents.ITEM_FIRECHARGE_USE, 1.6f, 1.4f);
            }
        }
        return next;
    }

    private Read read(ServerWorld world, ServerPlayerEntity player, long now) {
        Seen s = seen.get(player.getUuid());
        Vec3d to = player.getEntityPos().subtract(boss.getEntityPos()).multiply(1, 0, 1);
        double distance = to.length();
        boolean backed = false;
        if (last != null && last.swordString() && s != null && s.path.size() >= 10) {
            Vec3d moved = s.path.peekFirst().subtract(s.path.peekLast()).multiply(1, 0, 1);
            backed = moved.length() > 2 && distance > .2 && moved.normalize().dotProduct(to.normalize()) > .8;
        }
        UseAction use = player.isUsingItem() ? player.getActiveItem().getUseAction() : UseAction.NONE;
        return new Read(distance, s != null && s.aloft >= ELEVATED_TICKS, s != null && s.behind >= BEHIND_TICKS, backed,
                s != null && now - s.jumped < 40, use == UseAction.EAT || use == UseAction.DRINK, sight(world, player), boss.isSecondPhase());
    }

    private boolean sight(ServerWorld world, ServerPlayerEntity player) {
        return clear(world, boss.getEntityPos().add(0, 3.6, 0), player.getEyePos());
    }

    private boolean clear(ServerWorld world, Vec3d from, Vec3d to) {
        return world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, boss))
                .getType() == HitResult.Type.MISS;
    }

    /** The clips that carry him are drawn from where they began, so he must already face his target. */
    private boolean ready(Move choice, ServerPlayerEntity player) {
        if (choice != Move.LUNGE && choice != Move.HOOK && choice != Move.CAST && choice != Move.DRAG_DOWN) return true;
        return Math.abs(off(player.getEntityPos())) <= (choice == Move.LUNGE ? 20 : 60);
    }

    // ── Free: closing on his target ──────────────────────────────────────────

    private void approach(ServerWorld world, ServerPlayerEntity player, Read read) {
        Vec3d to = player.getEntityPos().subtract(boss.getEntityPos()).multiply(1, 0, 1);
        double distance = to.length();
        face(player.getEntityPos(), TURN);
        double hold = read.elevated() ? DRAG_RANGE * .5 : HOLD;
        if (distance <= hold || Math.abs(off(player.getEntityPos())) > 75) { halt(); settle(); return; }
        Vec3d heading = steer(world, to.normalize());
        double speed = (boss.isSecondPhase() ? RUN_TWO : RUN) / 20;
        Vec3d next = boss.getEntityPos().add(heading.multiply(speed));
        if (home != null && next.subtract(home).multiply(1, 0, 1).length() > LEASH) { halt(); settle(); return; }
        boss.setVelocity(heading.x * speed, Math.min(0, boss.getVelocity().y), heading.z * speed);
        if (!boss.clip().equals("run")) boss.play("run", 3);
    }

    /** Round a pillar rather than into it: the first heading his whole width can take, keeping to the side he last chose. */
    private Vec3d steer(ServerWorld world, Vec3d wish) {
        if (passable(world, wish)) return wish;
        for (int side : new int[]{steerSide, -steerSide}) for (double turn : new double[]{35, 70, 100}) {
            Vec3d heading = wish.rotateY((float) Math.toRadians(turn * side));
            if (passable(world, heading)) { steerSide = side; return heading; }
        }
        return wish;
    }

    /** One of the ways round the pillar that are open to him from where he stands; null when there is none. */
    private Flank flank(ServerWorld world, ServerPlayerEntity player, Read read) {
        Map<Move, Vec3d> spots = new java.util.EnumMap<>(Move.class);
        for (Move opener : new Move[]{Move.HAUL, Move.LUNGE}) {
            Vec3d spot = flankSpot(world, player, opener);
            if (spot != null) spots.put(opener, spot);
        }
        Move opener = pick(flankWeights(spots.containsKey(Move.HAUL), spots.containsKey(Move.LUNGE), read.healing(), lastFlank), boss.getRandom()::nextDouble);
        return opener == null ? null : new Flank(opener, spots.get(opener));
    }

    /**
     * Where to throw his sword so that the chain sets him down in sight of a target who is hiding from him, by the
     * opener's rule. He flies there in a straight line, his whole width clear of the stone. Null when there is no
     * such spot.
     */
    private Vec3d flankSpot(ServerWorld world, ServerPlayerEntity player, Move opener) {
        FlankRule rule = flankRule(opener);
        Vec3d from = boss.getEntityPos(), target = player.getEntityPos(), eyes = player.getEyePos();
        // He lands this far short of where the blade stands.
        double shortOf = PyreKingAim.get(opener.clip).stick()[2] - PyreKingTracks.get(opener.clip).travelEnd();
        double across = boss.getWidth() / 2 + .15;
        Vec3d away = from.subtract(target).multiply(1, 0, 1);
        Vec3d best = null;
        double most = -Double.MAX_VALUE;
        for (double ring : rule.rings()) {
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI / 12;
                Vec3d out = new Vec3d(Math.cos(angle), 0, Math.sin(angle));
                Vec3d land = new Vec3d(target.x + out.x * ring, from.y, target.z + out.z * ring);
                Vec3d to = land.subtract(from);
                double run = to.length();
                if (run + shortOf < rule.near() || run + shortOf > rule.far()) continue;
                if (home != null && land.subtract(home).multiply(1, 0, 1).length() > LEASH) continue;
                Vec3d heading = to.multiply(1 / run), side = new Vec3d(-heading.z, 0, heading.x).multiply(across);
                Vec3d chest = from.add(0, 1.2, 0), sword = land.add(heading.multiply(shortOf));
                // His whole width flies there, the sword a little further; he fits where he lands, and sees them from it.
                if (!clear(world, chest, sword.add(0, 1.2, 0)) || !clear(world, chest.add(side), land.add(side).add(0, 1.2, 0))
                        || !clear(world, chest.subtract(side), land.subtract(side).add(0, 1.2, 0))) continue;
                if (!world.isSpaceEmpty(boss, boss.getBoundingBox().offset(to).contract(.02))) continue;
                Vec3d head = land.add(0, 3.6, 0);
                if (!clear(world, head, eyes)) continue;
                double score;
                if (rule.wide()) {
                    // Where they would have the furthest to go to be out of his sight again; then as far round the
                    // pillar from where he stands as he can fly, about a dozen blocks from them.
                    double round = away.lengthSquared() < 1e-4 ? 0 : Math.toDegrees(Math.acos(Math.clamp(away.normalize().dotProduct(out), -1.0, 1.0)));
                    score = cover(world, player, head) * 10 + round / 9 - Math.abs(ring - 12) * .5;
                } else {
                    // Beside them with nothing between, so the cut he lands in can reach; and the shortest flight there.
                    if (!clear(world, land.add(0, 2.5, 0), target.add(0, player.getHeight() * .5, 0))) continue;
                    score = -run;
                }
                if (score > most) { most = score; best = sword; }
            }
            if (best != null && !rule.wide()) return best;       // the nearest ring that works
        }
        return best;
    }

    /**
     * How far his target would have to go, from where they stand, to be out of sight of a head at this point: the
     * nearest of a few rings of spots round them that is hidden from it and has room to stand in.
     */
    private double cover(ServerWorld world, ServerPlayerEntity player, Vec3d head) {
        Vec3d eyes = player.getEyePos();
        for (double reach : coverRings()) for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            Vec3d step = new Vec3d(Math.cos(angle) * reach, 0, Math.sin(angle) * reach);
            if (!clear(world, head, eyes.add(step)) && world.isSpaceEmpty(player, player.getBoundingBox().offset(step).contract(.02))) return reach;
        }
        return COVER_NONE;
    }

    private boolean passable(ServerWorld world, Vec3d heading) {
        Vec3d chest = boss.getEntityPos().add(0, 1.2, 0), across = new Vec3d(-heading.z, 0, heading.x).multiply(boss.getWidth() / 2 + .15);
        Vec3d ahead = heading.multiply(3.2);
        return clear(world, chest, chest.add(ahead)) && clear(world, chest.add(across), chest.add(across).add(ahead))
                && clear(world, chest.subtract(across), chest.subtract(across).add(ahead));
    }

    /** Standing still: his guard, once whatever clip he was in has run out. */
    private void settle() {
        String clip = boss.clip();
        if (clip.equals("idle")) return;
        PyreKingClips.Clip playing = PyreKingClips.get(clip);
        if (playing.loop()) boss.play("idle", 3);
        else if (boss.clipCarriesHim()) boss.play("idle", 4);       // he may need to turn, and that clip is drawn from where it began
        else if (boss.clipSeconds(0) >= playing.seconds()) boss.play("idle", 0);
    }

    private void halt() { boss.setVelocity(0, Math.min(0, boss.getVelocity().y), 0); }

    // ── A move ───────────────────────────────────────────────────────────────

    private void begin(ServerWorld world, Move next, ServerPlayerEntity player, long now, int blend) {
        halt();
        release();
        move = next;
        lastBegan = now;
        if (player != null) target = player.getUuid();
        boss.play(next.clip, blend);
        started = now + blend;
        swept = 0; cue = 0; retracked = 0; calledOff = -1;
        aimFrozen = turned = branched = decided = zipNext = landed = blowLanded = false;
        aimRight = aimUp = aimAhead = 0;
        aimPoint = landing = flank = null;
        groups.clear();
        if (next == Move.UNBOUND) {     // from here he cannot be hurt until it ends, and the half-health hold is spent
            boss.setUnboundDone(true);
            phasePending = false;
            sound(world, boss.getEntityPos(), SoundEvents.ENTITY_RAVAGER_ROAR, 3f, .45f);
        }
    }

    private void finish(long now) {
        Move done = move;
        release();
        move = null;
        before = last; last = done;
        doubled = false;
        boolean flanked = flanking;
        flank = null;
        flanking = false;
        if (reviewing) return;
        if (flanked) {      // round the pillar: he presses at once, and whoever is behind the stone again already has earned only a short moment
            Seen s = target == null ? null : seen.get(target);
            if (s != null && s.hidden > 0) s.hidden = Math.max(s.hidden, FLANK_AFTER - FLANK_AGAIN);
            chained = 0;
            nextPick = now;
            return;
        }
        boolean second = boss.isSecondPhase();
        // Sometimes he goes straight into another string; otherwise he closes on his next target for a moment.
        if (done.swordString() && chained < chainLimit(second) - 1 && boss.getRandom().nextDouble() < chainChance(second)) {
            chained++;
            nextPick = now;
        } else {
            chained = 0;
            lastTarget = target;
            nextPick = now + gap(second, boss.getRandom()::nextDouble);
        }
    }

    private ServerPlayerEntity victim(List<ServerPlayerEntity> players) {
        if (target == null) return null;
        for (ServerPlayerEntity player : players) if (player.getUuid().equals(target)) return player;
        return null;
    }

    private void tickMove(ServerWorld world, long now, List<ServerPlayerEntity> players) {
        double t = (now - started) / 20.0;
        ServerPlayerEntity player = victim(players);
        if (player == null && reviewing) player = world.getPlayers(p -> p.getUuid().equals(target)).stream().findFirst().orElse(null);
        halt();
        if (calledOff >= 0) { if (t >= calledOff) finish(now); return; }
        // While clients blend into the clip he may still turn, unless the clip is one drawn from where it began.
        // A flank's throw is aimed at its spot beside the pillar, not at the player behind it. The Snare is thrown as
        // he whips round on them from wherever the Haul set him down.
        Vec3d aimAt = flank != null ? flank : player == null ? null : player.getEntityPos();
        float turn = move == Move.SNARE ? SNARE_TURN : AIM_TURN;
        if (t < 0) { if (aimAt != null && move.aimUntil > 0 && !boss.clipCarriesHim()) face(aimAt, turn); return; }

        // Aim: he tracks until the snap, then the direction is fixed; a follow-up may turn a little toward the dodge.
        if (aimAt != null && !aimFrozen && t < move.aimUntil && !boss.clipCarriesHim()) face(aimAt, turn);
        if (!aimFrozen && t >= move.aimUntil) { aimFrozen = true; freeze(world, player, t); }
        if (player != null) for (Retrack retrack : move.retracks) if (t >= retrack.from() && t < retrack.to()) {
            float room = retrack.degrees() - retracked;
            if (room > 0) retracked += Math.abs(face(player.getEntityPos(), Math.min(room, retrack.degrees() / (float) ((retrack.to() - retrack.from()) * 20))));
        }
        while (cue < move.cues.length && t >= move.cues[cue]) warn(world, cue++);

        // His own steps.
        for (Advance advance : move.advances) if (t >= advance.from() && t < advance.to()) {
            double step = advance.blocks() / Math.max(1, (advance.to() - advance.from()) * 20);
            if (player != null) step = Math.min(step, Math.max(0, player.getEntityPos().subtract(boss.getEntityPos()).multiply(1, 0, 1).length() - 2.6));
            Vec3d forward = forward(boss.getBodyYaw());
            if (home == null || boss.getEntityPos().add(forward.multiply(step)).subtract(home).multiply(1, 0, 1).length() <= LEASH + 2)
                boss.move(MovementType.SELF, forward.multiply(step));
        }
        carry(world, t);

        sweep(world, now, t, players);
        if (special(world, now, t, player, players)) return;        // it handed over to another move, or ended early
        swept = t;
        if (t >= move.free) finish(now);
    }

    /**
     * Calls the move off part-way. The clip would go on to show the rest of it (a blow that can no longer hit, a sword
     * thrown at nobody), so he eases back to his guard instead and is free at `until` seconds into the move.
     */
    private void callOff(double until) {
        calledOff = until;
        release();
        boss.play("idle", 5);
    }

    /** A warning before a blow: the chain snaps taut and embers ring his feet. */
    private void warn(ServerWorld world, int which) {
        if (move == Move.ZIP) return;
        Vec3d at = boss.getEntityPos();
        boolean chain = !move.swordString();
        sound(world, at, chain ? SoundEvents.BLOCK_CHAIN_BREAK : SoundEvents.ENTITY_BLAZE_SHOOT, chain ? 2.4f : 1.6f, chain ? .55f : .7f + which * .12f);
        ring(world, at.add(0, .1, 0), 1.6, ParticleTypes.FLAME, 20);
    }

    // ── Frames and aim ───────────────────────────────────────────────────────

    private static Vec3d forward(float yaw) {
        float radians = yaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(radians), 0, MathHelper.cos(radians));
    }

    private static Vec3d right(float yaw) {
        float radians = yaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.cos(radians), 0, -MathHelper.sin(radians));
    }

    /** The frame the clip's tracks are in: where the clip began for one that carries him, else where he stands now. */
    private Vec3d frameOrigin() { return boss.clipCarriesHim() ? boss.clipOrigin() : boss.getEntityPos(); }
    private float frameYaw() { return boss.clipCarriesHim() ? boss.clipYaw() : boss.getBodyYaw(); }

    private Vec3d world(double[] point) {
        float yaw = frameYaw();
        return frameOrigin().add(right(yaw).multiply(point[0])).add(0, point[1], 0).add(forward(yaw).multiply(point[2]));
    }

    private double[] frame(Vec3d world) {
        float yaw = frameYaw();
        Vec3d offset = world.subtract(frameOrigin());
        return new double[]{offset.dotProduct(right(yaw)), offset.y, offset.dotProduct(forward(yaw))};
    }

    /** Degrees his target is off his front: positive to his right. */
    private double off(Vec3d point) {
        Vec3d to = point.subtract(boss.getEntityPos());
        float yaw = boss.getBodyYaw();
        return Math.toDegrees(Math.atan2(to.dotProduct(right(yaw)), to.dotProduct(forward(yaw))));
    }

    /** Turns him toward a point by at most `limit` degrees; returns how far he turned. */
    private float face(Vec3d point, float limit) {
        Vec3d delta = point.subtract(boss.getEntityPos());
        if (delta.x * delta.x + delta.z * delta.z < 1e-4) return 0;
        float wanted = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float turn = MathHelper.clamp(MathHelper.wrapDegrees(wanted - boss.getBodyYaw()), -limit, limit);
        hold(boss.getBodyYaw() + turn);
        return turn;
    }

    private void hold(float yaw) { boss.setYaw(yaw); boss.setBodyYaw(yaw); boss.setHeadYaw(yaw); }

    /** Where a moving target will be in `ticks`: its recent velocity carried forward, never far. */
    private Vec3d lead(ServerPlayerEntity player, double ticks) { return lead(player, ticks, HISTORY); }

    /** The same, read from only the last `span` ticks of their path: what they are doing now, not what they were. */
    private Vec3d lead(ServerPlayerEntity player, double ticks, int span) {
        Seen s = seen.get(player.getUuid());
        Vec3d at = player.getEntityPos();
        if (s == null || s.path.size() < Math.min(6, span)) return at;
        Vec3d old = null;
        int count = 0;
        for (Vec3d point : s.path) { old = point; if (++count >= span) break; }
        Vec3d velocity = s.path.peekFirst().subtract(old).multiply(1.0 / (count - 1)).multiply(1, 0, 1);
        Vec3d ahead = velocity.multiply(ticks);
        if (ahead.length() > LEAD_MAX) ahead = ahead.normalize().multiply(LEAD_MAX);
        return at.add(ahead);
    }

    /** A point on his floor he can reach: inside his leash, and short of any pillar between him and it. */
    private Vec3d reachable(ServerWorld world, Vec3d point) {
        Vec3d floor = new Vec3d(point.x, boss.getY(), point.z);
        if (home != null) {
            Vec3d out = floor.subtract(home).multiply(1, 0, 1);
            if (out.length() > LEASH + 2) {
                Vec3d edge = home.add(out.normalize().multiply(LEASH + 2));
                floor = new Vec3d(edge.x, boss.getY(), edge.z);
            }
        }
        Vec3d from = boss.getEntityPos().add(0, 1.2, 0), to = floor.add(0, 1.2, 0);
        var hit = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, boss));
        if (hit.getType() == HitResult.Type.MISS) return floor;
        Vec3d back = from.subtract(hit.getPos()).multiply(1, 0, 1);
        double room = Math.min(1.8, Math.max(0, back.length() - 3.2));
        Vec3d short_ = hit.getPos().add(back.lengthSquared() < 1e-6 ? Vec3d.ZERO : back.normalize().multiply(room));
        return new Vec3d(short_.x, boss.getY(), short_.z);
    }

    /** How far straight ahead of him the floor is open, up to `reach`, less room for a sword to stand. */
    private double open(ServerWorld world, double reach) {
        Vec3d from = frameOrigin().add(0, 1.2, 0);
        var hit = world.raycast(new RaycastContext(from, from.add(forward(frameYaw()).multiply(reach + 1.8)), RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, boss));
        return hit.getType() == HitResult.Type.MISS ? reach : Math.max(0, hit.getPos().distanceTo(from) - 1.8);
    }

    /** The aim is fixed here: the thrown sword's real target, and the offset from where the clip throws it. */
    private void freeze(ServerWorld world, ServerPlayerEntity player, double t) {
        PyreKingAim.Throw thrown = PyreKingAim.get(move.clip);
        if (thrown == null || move == Move.ZIP) return;
        if (player == null) { aimPoint = world(thrown.stick()); return; }      // nobody to aim at: where the clip throws it
        double[] stick = thrown.stick();
        Vec3d point;
        switch (move) {
            case CAST -> point = reachable(world, lead(player, (1.26 - .47) * 20));
            case LUNGE, HAUL -> {
                if (flank != null) point = flank;
                else {
                    Vec3d wanted = lead(player, move == Move.HAUL ? 6 : (1.7 - .45) * 20 * .5);
                    Vec3d back = boss.getEntityPos().subtract(wanted).multiply(1, 0, 1);
                    Vec3d spot = wanted.add(back.lengthSquared() < 1e-6 ? Vec3d.ZERO : back.normalize().multiply(Math.min(LUNGE_SHORT, back.length())));
                    point = reachable(world, spot);
                }
            }
            case HOOK -> point = lead(player, 8).add(0, player.getHeight() * .55, 0);
            case SNARE -> {
                // Thrown past them: a target running from him runs onto the blade, not out from under its point.
                Vec3d at = lead(player, 8, 4), out = at.subtract(boss.getEntityPos()).multiply(1, 0, 1);
                point = at.add(out.lengthSquared() < 1e-6 ? Vec3d.ZERO : out.normalize().multiply(SNARE_PAST)).add(0, player.getHeight() * .55, 0);
            }
            case DRAG_DOWN -> point = lead(player, 6).add(0, player.getHeight() * .5, 0);
            default -> { return; }
        }
        double[] in = frame(point);
        if (move == Move.CAST || move == Move.LUNGE || move == Move.HAUL) {
            // The sword goes at least a little way out, but never past a pillar he has backed his target behind.
            in[1] = 0;
            double least = move == Move.LUNGE ? 4.5 : 6;
            if (in[2] < least) in[2] = Math.max(1.7, Math.min(least, Math.max(in[2], open(world, least))));
        }
        if (move.hooks()) in[2] = Math.max(in[2], 8);
        aimPoint = world(in);
        aimRight = in[0] - stick[0]; aimUp = in[1] - stick[1]; aimAhead = in[2] - stick[2];
        boss.setAim(aimRight, aimUp, aimAhead);
        if (move == Move.CAST || move.hooks())        // the line the throw will fly along, solid from the freeze
            marks.add(new Mark(boss.getEntityPos().add(0, .15, 0), new Vec3d(aimPoint.x, boss.getY() + .15, aimPoint.z), 0, started + (long) ((1.2 - move.hookLead()) * 20)));
        if (move == Move.LUNGE) land(world, thrown, started + (long) (1.75 * 20));
        if (move == Move.HAUL) land(world, thrown, started + (long) (.85 * 20));
    }

    /** Where the chain will set him down, ringed with embers until his cut lands there. */
    private void land(ServerWorld world, PyreKingAim.Throw thrown, long until) {
        PyreKingTracks.Track track = PyreKingTracks.get(move.clip);
        landing = boss.clipOrigin().add(right(boss.clipYaw()).multiply(aimRight)).add(forward(boss.clipYaw()).multiply(track.travelEnd() + aimAhead));
        marks.add(new Mark(landing.add(0, .15, 0), landing.add(0, .15, 0), LANDING_RING, until));
    }

    /** Zip, Lunge, Haul and the Turning Cut move or turn him inside the clip; this makes it real as the clip does it. */
    private void carry(ServerWorld world, double t) {
        if (!boss.clipCarriesHim()) return;
        PyreKingTracks.Track track = PyreKingTracks.get(move.clip);
        PyreKingAim.Throw thrown = PyreKingAim.get(move.clip);
        if (track != null && track.travelEnd() > 0) {
            double share = thrown.body(t);
            Vec3d at = boss.clipOrigin().add(right(boss.clipYaw()).multiply(share * aimRight))
                    .add(forward(boss.clipYaw()).multiply(track.travel(t) + share * aimAhead));
            // The landing was aimed short of any pillar; should something still stand in the way, he stops against it.
            Vec3d delta = new Vec3d(at.x - boss.getX(), 0, at.z - boss.getZ());
            if (world.isSpaceEmpty(boss, boss.getBoundingBox().offset(delta).contract(.02))) boss.setPosition(at.x, boss.getY(), at.z);
            if (share >= 1 && !landed) {
                landed = true;
                sound(world, at, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 2.4f, .5f);
                ring(world, at.add(0, .1, 0), 2.2, ParticleTypes.LAVA, 14);
                // The Haul scorches the line he flew: past a pillar, that is a wall across the way round it.
                if (move == Move.HAUL && !reviewing)
                    fire(world, boss.clipOrigin(), new Vec3d(at.x, boss.getY(), at.z), FIRE_HALF, boss.isSecondPhase() ? FIRE_TALL : FIRE_LOW, world.getTime(), HAUL_FIRE);
            }
        }
        PyreKingClips.Clip clip = PyreKingClips.get(move.clip);
        if (clip.endTurnDegrees() != 0 && !turned && t >= move.lastBlow()) {
            turned = true;
            hold(boss.clipYaw() - clip.endTurnDegrees());       // to his left: yaw falls
        }
    }

    // ── Hits ─────────────────────────────────────────────────────────────────

    /** One part's line at a moment of the clip, in his frame: joints from his hand outward, the throw's aim applied. */
    private double[][] shape(Part part, double t, int chain) {
        PyreKingTracks.Track track = PyreKingTracks.get(move.clip);
        if (part == Part.CHAINS) return new double[][]{track.chest(t), track.chainEnd(chain, t)};
        double[] pommel = track.pommel(t), tip = track.tip(t), wrist = track.wrist(t);
        PyreKingAim.Throw thrown = PyreKingAim.get(move.clip);
        if (thrown != null) {
            double[] body = thrown.bodyOffset(t, aimRight, aimAhead), sword = thrown.swordOffset(t, aimRight, aimUp, aimAhead);
            for (int i = 0; i < 3; i++) { wrist[i] += body[i]; pommel[i] += sword[i]; tip[i] += sword[i]; }
        }
        return part == Part.BLADE ? new double[][]{pommel, tip} : new double[][]{wrist, pommel, tip};
    }

    private void sweep(ServerWorld world, long now, double t, List<ServerPlayerEntity> players) {
        for (Window window : move.windows) {
            double from = Math.max(swept, window.from()), to = Math.min(t, window.to());
            if (to <= from) continue;
            if (now - lastBlow > COMBO_GAP) combo++;        // a new combo: everyone can be struck again
            lastBlow = now;
            if (from <= window.from()) swing(world, window);
            int steps = Math.max(1, (int) Math.ceil((to - from) * PyreKingTracks.RATE));
            int chains = window.part() == Part.CHAINS ? PyreKingTracks.get(move.clip).chains() : 1;
            for (int chain = 0; chain < chains; chain++) for (int i = 0; i < steps; i++) {
                double[][] a = shape(window.part(), from + (to - from) * i / steps, chain), b = shape(window.part(), from + (to - from) * (i + 1) / steps, chain);
                for (ServerPlayerEntity player : players) if (touches(world, window, a, b, player)) strike(world, now, window, player);
            }
            if (window.band() == Band.LOW && window.part() == Part.CHAIN) sparks(world, shape(Part.BLADE, to, 0));
        }
    }

    private boolean touches(ServerWorld world, Window window, double[][] a, double[][] b, ServerPlayerEntity player) {
        if ((groups.getOrDefault(player.getUuid(), 0) & 1 << window.group()) != 0) return false;
        double[] at = frame(player.getEntityPos());
        double reach = window.radius() + player.getWidth() / 2;
        for (int i = 0; i + 1 < a.length; i++) {
            double low = Math.min(Math.min(a[i][1], a[i + 1][1]), Math.min(b[i][1], b[i + 1][1])),
                    high = Math.max(Math.max(a[i][1], a[i + 1][1]), Math.max(b[i][1], b[i + 1][1]));
            if (!inBand(window.band(), at[1], at[1] + player.getHeight(), low, high)) continue;
            if (sweptDistanceSquared(at[0], at[2], a[i][0], a[i][2], a[i + 1][0], a[i + 1][2], b[i][0], b[i][2], b[i + 1][0], b[i + 1][2]) > reach * reach) continue;
            // Unbound's chains wrap round the pillars: whoever stands behind one is sheltered.
            if (window.part() == Part.CHAINS && !clear(world, world(a[0]), player.getEntityPos().add(0, .4, 0))) continue;
            // Nothing else of his passes through stone either: a pillar between him and a player shelters them from
            // his blade in hand, from the chain and whatever swings on it, and from the Hook. (A sword thrown to stick
            // is where it is: it was aimed short of the stone.)
            if ((window.part() == Part.CHAIN || window.band() == Band.SOLID || move.hooks())
                    && !clear(world, boss.getEntityPos().add(0, 2.5, 0), player.getEntityPos().add(0, player.getHeight() * .5, 0))) continue;
            return true;
        }
        return false;
    }

    private void strike(ServerWorld world, long now, Window window, ServerPlayerEntity player) {
        groups.merge(player.getUuid(), 1 << window.group(), (x, y) -> x | y);
        Seen s = seen.computeIfAbsent(player.getUuid(), id -> new Seen());
        boolean heavy = window.damage() >= CHAIN;
        if (heavy && s.combo == combo) return;       // one mistake, one hit: already struck in this combo
        DamageSource source = source(world, window.part() == Part.BLADE && move.swordString() ? BLADE : CHAINS);
        if (!player.damage(world, source, window.damage())) return;
        if (heavy) s.combo = combo;
        s.struck = now; s.struckFor = window.damage();
        blowLanded = true;
        Vec3d away = player.getEntityPos().subtract(boss.getEntityPos()).multiply(1, 0, 1);
        if (move.hooks() || move == Move.DRAG_DOWN) {
            caught = player.getUuid();
            caughtAt = player.getEntityPos();
            sound(world, player.getEntityPos(), SoundEvents.BLOCK_CHAIN_PLACE, 2f, .6f);
        } else if (away.lengthSquared() > 1e-4) {
            away = away.normalize();
            player.takeKnockback(heavy ? .9 : .4, -away.x, -away.z);
            player.velocityModified = true;
        }
        sound(world, player.getEntityPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.8f, .7f);
        world.spawnParticles(ParticleTypes.LAVA, player.getX(), player.getBodyY(.5), player.getZ(), 8, .3, .4, .3, 0);
    }

    /** The blow itself: the blade's rush, or the chain's whistle. */
    private void swing(ServerWorld world, Window window) {
        Vec3d at = boss.getEntityPos();
        if (window.part() == Part.BLADE) sound(world, at, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 2.2f, .5f);
        else sound(world, at, SoundEvents.ENTITY_PHANTOM_SWOOP, 2.6f, window.band() == Band.HIGH ? .9f : .6f);
        sound(world, at, SoundEvents.ITEM_FIRECHARGE_USE, 1.4f, .6f);
    }

    /** Sparks where the low Scythe's blade drags along the floor. */
    private void sparks(ServerWorld world, double[][] blade) {
        for (double f : new double[]{.2, .6, 1}) {
            Vec3d at = world(new double[]{blade[0][0] + (blade[1][0] - blade[0][0]) * f, .15, blade[0][2] + (blade[1][2] - blade[0][2]) * f});
            world.spawnParticles(ParticleTypes.LAVA, true, false, at.x, at.y, at.z, 1, .1, 0, .1, 0);
            world.spawnParticles(ParticleTypes.FLAME, true, false, at.x, at.y, at.z, 2, .15, .05, .15, .02);
        }
    }

    // ── What each move does beyond its blows ─────────────────────────────────

    /** Returns true when the move in hand is over: it handed over to another, or ended early. */
    private boolean special(ServerWorld world, long now, double t, ServerPlayerEntity player, List<ServerPlayerEntity> players) {
        boolean second = boss.isSecondPhase();
        switch (move) {
            case ROYAL_CUT, ROYAL_CUT_THREE -> {
                if (!branched && t >= ROYAL_BRANCH_AT && player != null && !reviewing) {
                    branched = true;
                    Move branch = royalBranch(player.getEntityPos().subtract(boss.getEntityPos()).multiply(1, 0, 1).length(),
                            off(player.getEntityPos()), second, boss.getRandom()::nextDouble);
                    if (branch != null) { begin(world, branch, player, now, 3); return true; }
                }
                if (move == Move.ROYAL_CUT_THREE) {
                    // The third blow is for whoever dodged the first two.
                    if (!decided && t >= ROYAL_THIRD_AT) { decided = true; if (blowLanded && !reviewing) { callOff(Move.ROYAL_CUT.free); return true; } }
                    if (crossed(t, 2.8)) {
                        Vec3d from = boss.getEntityPos().add(forward(boss.getBodyYaw()).multiply(2));
                        fire(world, from, from.add(forward(boss.getBodyYaw()).multiply(CUT_FIRE)), FIRE_HALF, FIRE_LOW, now, FIRE_SHORT);
                        sound(world, from, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 2f, .7f);
                    }
                }
            }
            case CAST -> {
                if (crossed(t, .87)) sound(world, boss.getEntityPos(), SoundEvents.ITEM_TRIDENT_THROW.value(), 2.4f, .5f);
                if (crossed(t, 1.26) && aimPoint != null) {
                    sound(world, aimPoint, SoundEvents.ITEM_TRIDENT_HIT_GROUND, 2.4f, .5f);
                    fire(world, aimPoint, aimPoint, CAST_FIRE, FIRE_LOW, now, FIRE_SHORT);
                }
                if (!decided && t >= CAST_DECIDE) {
                    decided = true;
                    double away = player == null ? 0 : player.getEntityPos().subtract(boss.getEntityPos()).multiply(1, 0, 1).length();
                    if (aimPoint != null && (away > ZIP_BEYOND || second && boss.getRandom().nextDouble() < .35)) zipNext = true;
                    if (zipNext && aimPoint == null) zipNext = false;
                }
                if (zipNext && t >= CAST_ZIP_AT) { zip(world, now); return true; }
                if (crossed(t, 3.5)) sound(world, boss.getEntityPos(), SoundEvents.ITEM_TRIDENT_RETURN, 2.4f, .6f);
                if (crossed(t, 3.8) && second && aimPoint != null)       // phase two: the path it came back along burns
                    fire(world, aimPoint, boss.getEntityPos().add(forward(boss.getBodyYaw()).multiply(2.5)), FIRE_HALF, FIRE_TALL, now, FIRE_LONG);
                if (second && !doubled && !reviewing && t >= CAST_AGAIN_AT && player != null) {
                    // The Double Cast: a second throw straight after the first comes back, at another player if there is one.
                    ServerPlayerEntity other = players.stream().filter(p -> p != player).min(Comparator.comparingDouble(boss::squaredDistanceTo)).orElse(player);
                    Move done = move;
                    begin(world, Move.CAST, other, now, 3);
                    doubled = true;
                    before = last; last = done;
                    return true;
                }
            }
            case ZIP -> {
                if (crossed(t, 1.64) && second && landing != null)       // phase two: the line he flew burns
                    fire(world, boss.clipOrigin(), landing, FIRE_HALF, FIRE_TALL, now, FIRE_LONG);
            }
            case LUNGE -> {
                if (crossed(t, .62)) sound(world, boss.getEntityPos(), SoundEvents.ITEM_TRIDENT_THROW.value(), 2.2f, .7f);
            }
            case HAUL -> {
                if (crossed(t, .3)) sound(world, boss.getEntityPos(), SoundEvents.ITEM_TRIDENT_THROW.value(), 2.4f, .8f);
                // Set down where he can see them, he throws again at once: the Snare.
                if (t >= move.free && !reviewing && player != null && player.getY() - boss.getY() <= ELEVATED && sight(world, player)) {
                    Move done = move;
                    begin(world, Move.SNARE, player, now, 2);
                    before = last; last = done;
                    return true;
                }
            }
            case HOOK, SNARE -> {
                double lead = move.hookLead();
                if (move == Move.SNARE && !reviewing) {
                    // Thrown only at what he can see: if they are behind the stone again before it leaves his hand, he keeps it.
                    if (t >= move.aimUntil && t < SNARE_THROWN && (player == null || !sight(world, player))) {
                        marks.removeIf(mark -> mark.radius() == 0);
                        callOff(t + SNARE_KEPT);
                        return true;
                    }
                    // And a throw that caught nobody is whipped straight back, not hauled in hand over hand.
                    if (t >= SNARE_MISSED && caught == null) { callOff(t + SNARE_RECALL); return true; }
                }
                if (crossed(t, 1.0 - lead)) sound(world, boss.getEntityPos(), SoundEvents.ITEM_TRIDENT_THROW.value(), 2.6f, .4f);
                if (caught != null) {
                    ServerPlayerEntity held = held(world, players);
                    if (held == null) caught = null;
                    else if (t < HOOK_DRAG_UNTIL - lead) {
                        // They come in on the sword's hilt as he hauls it, and stop at his feet.
                        double[] pommel = shape(Part.BLADE, t, 0)[0];
                        Vec3d to = world(new double[]{pommel[0] * .3, 0, Math.max(pommel[2] + .6, HOOK_HOLD)});
                        drag(world, held, new Vec3d(to.x, boss.getY(), to.z), true);
                    }
                }
                if (t >= HOOK_FOLLOW_AT - lead && caught != null && !reviewing) {
                    ServerPlayerEntity held = held(world, players);
                    release();
                    if (held != null) { begin(world, Move.OVERHEAD, held, now, 3); aimFrozen = true; return true; }
                }
            }
            case DRAG_DOWN -> {
                if (!decided && t >= move.aimUntil) {
                    decided = true;
                    // Landing during the tell cancels it.
                    if (player != null && !reviewing && player.getY() - boss.getY() < ELEVATED * .6) { callOff(move.aimUntil + .45); return true; }
                }
                if (crossed(t, .7)) sound(world, boss.getEntityPos(), SoundEvents.ITEM_TRIDENT_THROW.value(), 2.2f, .8f);
                if (caught != null) {
                    ServerPlayerEntity held = held(world, players);
                    if (held == null) caught = null;
                    else if (t < DRAG_LAND || held.getY() > boss.getY() + .05) {
                        // Down off their perch to the floor at his feet, where his next string is waiting.
                        Vec3d to = boss.getEntityPos().add(forward(boss.getBodyYaw()).multiply(HOOK_HOLD));
                        drag(world, held, to, false);
                    } else {
                        held.fallDistance = 0;
                        release();
                    }
                }
            }
            case UNBOUND -> {
                if (crossed(t, .64)) sound(world, boss.getEntityPos(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, 3f, .5f);
                if (crossed(t, 1.2)) {
                    sound(world, boss.getEntityPos(), SoundEvents.BLOCK_CHAIN_BREAK, 3f, .4f);
                    sound(world, boss.getEntityPos(), SoundEvents.ENTITY_WARDEN_ROAR, 3f, .7f);
                    world.spawnParticles(ParticleTypes.FLAME, true, false, boss.getX(), boss.getBodyY(.5), boss.getZ(), 120, .5, .8, .5, .25);
                    marks.add(new Mark(boss.getEntityPos().add(0, .15, 0), boss.getEntityPos().add(0, .15, 0), UNBOUND_REACH, started + (long) (1.95 * 20)));
                }
                if (crossed(t, UNBOUND_WALLS_AT)) {
                    PyreKingTracks.Track track = PyreKingTracks.get(move.clip);
                    Vec3d centre = boss.getEntityPos();
                    for (int chain = 0; chain < track.chains(); chain++) {
                        double[] end = track.chainEnd(chain, UNBOUND_WALLS_AT);
                        double length = Math.hypot(end[0], end[2]);
                        Vec3d out = world(new double[]{end[0], 0, end[2]}), in = world(new double[]{end[0] / length * 2.5, 0, end[2] / length * 2.5});
                        fire(world, new Vec3d(in.x, centre.y, in.z), new Vec3d(out.x, centre.y, out.z), FIRE_HALF, FIRE_TALL, now, FIRE_LONG);
                    }
                    sound(world, centre, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 2.5f, .6f);
                }
                if (t >= move.free - .05) {
                    boss.setSecondPhase(true);
                    for (ServerPlayerEntity each : bar.getPlayers())
                        each.sendMessage(Text.translatable("message.elementalwands.pyre_king.unbound").formatted(Formatting.RED), true);
                }
            }
            default -> {}
        }
        return false;
    }

    /** Whether the clip passed this moment during this tick. */
    private boolean crossed(double t, double moment) { return swept < moment && t >= moment; }

    /**
     * The Cast ends in a Zip instead of a Reel: the chain hauls him to his sword where it stands. The Zip's clip opens
     * on the Cast's wait, so it follows that with no blend.
     */
    private void zip(ServerWorld world, long now) {
        double right = aimRight, up = aimUp, ahead = aimAhead;
        Vec3d point = aimPoint;
        UUID keep = target;
        Move done = move;
        move = Move.ZIP;
        boss.play(Move.ZIP.clip, 0);
        started = now;
        swept = 0; cue = 0; retracked = 0; calledOff = -1;
        aimFrozen = true; turned = branched = decided = zipNext = landed = blowLanded = false;
        groups.clear();
        target = keep;
        aimRight = right; aimUp = up; aimAhead = ahead; aimPoint = point;
        boss.setAim(right, up, ahead);
        before = last; last = done;
        marks.removeIf(mark -> mark.radius() == 0);
        land(world, PyreKingAim.get(Move.ZIP.clip), started + (long) (1.5 * 20));
        sound(world, boss.getEntityPos(), SoundEvents.BLOCK_CHAIN_BREAK, 2.6f, .5f);
    }

    // ── A caught player ──────────────────────────────────────────────────────

    private ServerPlayerEntity held(ServerWorld world, List<ServerPlayerEntity> players) {
        for (ServerPlayerEntity player : players) if (player.getUuid().equals(caught)) return player;
        return null;
    }

    /**
     * Hauls a caught player to a point. A movement spell that carries them off the chain, or (for
     * the Hook) a pillar between them and where it pulls, snaps it and frees them.
     */
    private void drag(ServerWorld world, ServerPlayerEntity player, Vec3d to, boolean snaps) {
        Vec3d at = player.getEntityPos();
        boolean broke = caughtAt != null && at.squaredDistanceTo(caughtAt) > HOOK_BREAK * HOOK_BREAK;
        boolean blocked = snaps && !clear(world, at.add(0, 1, 0), to.add(0, 1, 0));
        if (broke || blocked) {
            sound(world, at, SoundEvents.BLOCK_CHAIN_BREAK, 2.4f, 1.3f);
            world.spawnParticles(ParticleTypes.LAVA, at.x, at.y + 1, at.z, 10, .3, .3, .3, 0);
            release();
            return;
        }
        Vec3d step = to.subtract(at);
        if (step.length() > 2.2) step = step.normalize().multiply(2.2);        // never a teleport: the chain pulls
        // Round stone, never through it: off a pillar's top sideways first, then down.
        if (!fits(world, player, step)) {
            Vec3d flat = new Vec3d(step.x, 0, step.z), down = new Vec3d(0, step.y, 0);
            if (flat.lengthSquared() > 1e-4 && fits(world, player, flat)) step = flat;
            else if (Math.abs(step.y) > 1e-3 && fits(world, player, down)) step = down;
            else { player.fallDistance = 0; release(); return; }
        }
        Vec3d next = at.add(step);
        // The realm may refuse a pull across its edge: then the chain lets go.
        if (!player.teleport(world, next.x, next.y, next.z, KEEP_VIEW, 0, 0, false)) { release(); return; }
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        player.velocityModified = true;
        caughtAt = player.getEntityPos();
    }

    private static boolean fits(ServerWorld world, ServerPlayerEntity player, Vec3d step) {
        return world.isSpaceEmpty(player, player.getBoundingBox().offset(step).contract(.02));
    }

    private void release() { caught = null; caughtAt = null; }

    // ── The floor: fire and warnings ─────────────────────────────────────────

    private void fire(ServerWorld world, Vec3d from, Vec3d to, double half, double height, long now, int ticks) {
        // A line of fire stops at a pillar: whoever stands behind one is out of it.
        if (from.squaredDistanceTo(to) > 1e-4) {
            var hit = world.raycast(new RaycastContext(from.add(0, .5, 0), to.add(0, .5, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, boss));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3d stop = hit.getPos().subtract(to.subtract(from).normalize().multiply(.3));
                to = new Vec3d(stop.x, to.y, stop.z);
                if (to.subtract(from).dotProduct(hit.getPos().subtract(from)) <= 0) return;      // it starts against the pillar
            }
        }
        fires.add(new Fire(from, to, half, height, now + FIRE_ARM, now + ticks));
        sound(world, from, SoundEvents.ITEM_FIRECHARGE_USE, 1.8f, .7f);
    }

    private void tickFloor(ServerWorld world, long now, List<ServerPlayerEntity> players) {
        marks.removeIf(mark -> now >= mark.until());
        fires.removeIf(fire -> now >= fire.until());
        if (now % 2 == 0) for (Mark mark : marks) {
            if (mark.radius() > 0) ring(world, mark.from(), mark.radius(), ParticleTypes.FLAME, (int) (mark.radius() * 9));
            else line(world, mark.from(), mark.to(), .7, ParticleTypes.SMALL_FLAME);
        }
        if (now % 3 == 0) for (Fire fire : fires) flames(world, fire, now);
        for (Fire fire : fires) {
            if (now < fire.armed()) continue;
            for (ServerPlayerEntity player : players) {
                Seen s = seen.computeIfAbsent(player.getUuid(), id -> new Seen());
                if (now < s.burnAt || !burning(fire, player)) continue;
                // Fire straight after a blow only adds what it exceeds the blow by. For the first half second the game
                // itself takes the blow off whatever follows it; after that the fire does its own sum.
                long since = now - s.struck;
                float amount = since >= 10 && since < FIRE_AFTER_WEAPON ? Math.max(0, FIRE - s.struckFor) : FIRE;
                boolean burned = amount > 0 && player.damage(world, source(world, FLAMES), amount);
                s.burnAt = now + (burned || since >= FIRE_AFTER_WEAPON ? FIRE_EVERY : 4);
            }
        }
    }

    private static boolean burning(Fire fire, ServerPlayerEntity player) {
        double up = player.getY() - fire.from().y;
        if (up >= fire.height() || up < -1) return false;
        double reach = fire.half() + player.getWidth() / 2;
        return segment(player.getX(), player.getZ(), fire.from().x, fire.from().z, fire.to().x, fire.to().z) <= reach * reach;
    }

    private void flames(ServerWorld world, Fire fire, long now) {
        boolean armed = now >= fire.armed();
        Vec3d along = fire.to().subtract(fire.from());
        double length = along.length();
        var random = boss.getRandom();
        if (length < 1e-3) {        // a disc
            int count = (int) Math.ceil(fire.half() * fire.half() * 2.2);
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * fire.half();
                flame(world, fire.from().add(Math.cos(angle) * r, 0, Math.sin(angle) * r), fire.height(), armed);
            }
            return;
        }
        int count = (int) Math.ceil(length / .7);
        for (int i = 0; i <= count; i++) {
            Vec3d at = fire.from().add(along.multiply((i + random.nextDouble() * .6 - .3) / count)).add((random.nextDouble() - .5) * fire.half(), 0, (random.nextDouble() - .5) * fire.half());
            flame(world, at, fire.height(), armed);
        }
    }

    private void flame(ServerWorld world, Vec3d at, double height, boolean armed) {
        var random = boss.getRandom();
        // Catching: a low lick of flame. Burning: the Fire spells' big block flames, stacked as tall as the fire is.
        if (!armed) { world.spawnParticles(ModParticles.FIRE_FLAME_RIBBON, true, false, at.x, at.y + .2, at.z, 1, .05, .05, .05, .01); return; }
        world.spawnParticles(ModParticles.FIRE_PYRE_FRONT, true, false, at.x, at.y + .55, at.z, 1, 0, 0, 0, 0);
        if (height > 1.5) world.spawnParticles(ModParticles.FIRE_PYRE_FRONT, true, false, at.x, at.y + 1.5 + random.nextDouble() * .3, at.z, 1, 0, 0, 0, 0);
        if (random.nextInt(6) == 0) world.spawnParticles(ParticleTypes.LAVA, true, false, at.x, at.y + .2, at.z, 1, 0, 0, 0, 0);
        if (random.nextInt(3) == 0) world.spawnParticles(ParticleTypes.FLAME, true, false, at.x, at.y + height + random.nextDouble() * .5, at.z, 1, .1, .1, .1, .03);
    }

    private void ring(ServerWorld world, Vec3d centre, double radius, ParticleEffect effect, int points) {
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            world.spawnParticles(effect, true, false, centre.x + Math.cos(angle) * radius, centre.y, centre.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    private void line(ServerWorld world, Vec3d from, Vec3d to, double spacing, ParticleEffect effect) {
        Vec3d along = to.subtract(from);
        int count = Math.max(1, (int) (along.length() / spacing));
        for (int i = 0; i <= count; i++) {
            Vec3d at = from.add(along.multiply(i / (double) count));
            world.spawnParticles(effect, true, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    private void sound(ServerWorld world, Vec3d at, SoundEvent event, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }

    private DamageSource source(ServerWorld world, RegistryKey<DamageType> type) {
        return new DamageSource(world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(type), boss, boss);
    }

    /** For the fixture: the blade's two ends now, in the world, as the sweep reads them. */
    Vec3d[] blade() {
        if (move == null) return null;
        double[][] shape = shape(Part.BLADE, Math.max(0, (now() - started) / 20.0), 0);
        return new Vec3d[]{world(shape[0]), world(shape[1])};
    }

    String moveName() { return move == null ? "free" : move.name().toLowerCase(Locale.ROOT); }
}
