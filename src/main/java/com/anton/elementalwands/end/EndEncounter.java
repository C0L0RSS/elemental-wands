package com.anton.elementalwands.end;

import com.anton.elementalwands.arena.GuardianArenaJournal.Point;
import com.anton.elementalwands.party.PartyManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

/**
 * The First Wizard's fight on the End's main island: one at a time, started by the first Survival or
 * Adventure player to arrive while the boss is undefeated. Members of that player's party who arrive
 * later join it; everyone else waits at the portal ({@link EndGate}). A fighter who falls keeps their
 * belongings, wakes at their way back and is out of that fight. When nobody is left standing the boss
 * is stopped and removed. The win opens the way home and the gateways, and the End is ordinary from then.
 *
 * <p>A stop, crash, reload, relog or a switch to Peaceful ends a fight without a result. While the boss
 * is undefeated, a Survival or Adventure player in the arena's footprint whom no fight holds (found
 * there at login, or come by another way than the portal: an old return gateway, a command) is sent to
 * their way back, or to world spawn if none is recorded, within a second. So nobody is stranded on the
 * island, and nobody stands on its floor without the fight. The rest of the End is left alone.
 */
public final class EndEncounter {
    private EndEncounter() {}

    /** Vanilla entities carrying this command tag are left alone by the End's rules (an intro's dragon, say). */
    public static final String ACTOR_TAG = "elementalwands.end_actor";
    /** Ticks: the boss is placed once the floor's middle is ticking; past this the start gives up. */
    private static final int GIVE_UP = 600;
    /** Ticks after the win before a boss still standing is put away. */
    private static final int DEPART = 200;
    private static final int MOB_SWEEP = 5;
    /** Ticks between looks for players on the island whom no fight holds. */
    private static final int PLAYER_SWEEP = 20;

    private static Function<ServerWorld, EndFighter> factory;

    private static final class Fight {
        final UUID skinOwner;
        final int opened;
        final Set<UUID> roster = new LinkedHashSet<>(), standing = new LinkedHashSet<>(), fallen = new HashSet<>();
        /** Null until the floor is loaded and the boss is placed. */
        EndFighter boss;
        /** The intro, from the boss's placing until the fight starts; null otherwise. */
        EndIntro intro;
        /** Which intro an operator asked for ({@code /ew end intro}); null plays the one the saved dragon calls for. */
        Boolean forceFull;
        Fight(UUID skinOwner, int opened) { this.skinOwner = skinOwner; this.opened = opened; }
    }

    private static Fight fight;
    /** Taken onto the island by {@code /ew end enter}: left there, without a fight, until they leave the End or log out. */
    private static final Set<UUID> visitors = new HashSet<>();
    /** Arrived through the portal this tick; handled at the end of the server tick. */
    private static final Set<UUID> arrivals = new LinkedHashSet<>();
    private static final Set<UUID> joined = new LinkedHashSet<>(), respawns = new LinkedHashSet<>();
    /** Players who died in the arena; the respawned copy gets their inventory and experience back. */
    private static final Set<UUID> kept = new HashSet<>();
    private static final List<Entity> discards = new ArrayList<>();
    private static EndFighter departing;
    private static int departAt;
    private static boolean stopping;

    /** The boss agent registers how the First Wizard is made. Without it a player may stand in the End, but no fight starts. */
    public static void setBossFactory(Function<ServerWorld, EndFighter> bossFactory) { factory = bossFactory; }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(EndEncounter::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player && fight != null && fight.standing.contains(player.getUuid()))
                out(player.getUuid(), player, "You fell. You will wake beside the End portal.");
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // Wait a tick: the connection still points at the dead player at this moment.
            if (!alive && EndArenaManager.guarded() && EndArena.inFootprint(oldPlayer.getEntityWorld(), oldPlayer.getEntityPos()))
                respawns.add(newPlayer.getUuid());
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            // The set is lost on a restart; the body still lying in the arena is what survives a quit from the death screen.
            if (alive || !kept.remove(oldPlayer.getUuid())
                    && !(EndArenaManager.guarded() && EndArena.inFootprint(oldPlayer.getEntityWorld(), oldPlayer.getEntityPos()))) return;
            newPlayer.getInventory().clone(oldPlayer.getInventory());
            newPlayer.experienceLevel = oldPlayer.experienceLevel;
            newPlayer.totalExperience = oldPlayer.totalExperience;
            newPlayer.experienceProgress = oldPlayer.experienceProgress;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> loggedIn(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUuid();
            visitors.remove(id); arrivals.remove(id);
            if (fight != null && fight.standing.contains(id)) out(id, null, null);
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (world.getRegistryKey() != EndArena.WORLD || entity.getCommandTags().contains(ACTOR_TAG)) return;
            // No dragon fight here: a dragon saved from before the takeover goes. Crystals are the old fight's too.
            if (entity instanceof EnderDragonEntity
                    || entity instanceof EndCrystalEntity && EndArenaManager.guarded() && EndArena.radius(entity.getEntityPos()) <= EndArena.CLEAR_RADIUS + 8)
                discards.add(entity);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (fight != null) end(server, "The First Wizard's fight was reset by a reload. The portal will take you again.", true);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(EndEncounter::interrupt);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            fight = null; departing = null;
            visitors.clear(); arrivals.clear(); joined.clear(); respawns.clear(); kept.clear(); discards.clear();
        });
    }

    /**
     * The server is stopping: any fight ends without a result and its fighters stay where they stand,
     * to be sent to their way back when they next log in. The fixture calls it directly, since it cannot restart.
     */
    static void interrupt(MinecraftServer server) {
        stopping = true;
        try { if (fight != null) end(server, "The First Wizard's fight ended: the server is stopping.", false); }
        finally { stopping = false; }
        if (departing != null) { putAway(departing); departing = null; }
    }

    /** A player logged in; next tick, one left on the island before the win with no fight holding them is sent back. */
    static void loggedIn(ServerPlayerEntity player) { joined.add(player.getUuid()); }

    // ------------------------------------------------------------------ rules other systems ask

    public static boolean running() { return fight != null; }

    /** A fight runs and its boss is placed (in its intro or fighting). */
    public static boolean fightPlaced() { return fight != null && fight.boss != null; }

    /** Standing in the running fight. */
    public static boolean fighting(PlayerEntity player) { return fight != null && fight.standing.contains(player.getUuid()); }

    /** Nothing drops in the arena while the boss is undefeated; the respawned player gets their inventory and experience back. */
    public static boolean keepsBelongings(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity) || !EndArenaManager.guarded() || !EndArena.inFootprint(player.getEntityWorld(), player.getEntityPos()))
            return false;
        kept.add(player.getUuid());
        return true;
    }

    /**
     * While a fight runs its fighters' teleports (spells, pearls, commands) end on the floor and never
     * leave the End, and nobody else is moved into the arena except by the portal. Creative and
     * spectating players are free, as are the arena's own moves.
     */
    public static boolean canTeleport(PlayerEntity player, ServerWorld destination, Vec3d target) {
        if (fight == null || EndArenaManager.internalTeleport() || player.isCreative() || player.isSpectator()) return true;
        boolean toArena = destination.getRegistryKey() == EndArena.WORLD;
        if (fight.standing.contains(player.getUuid())) return toArena && EndArena.teleportOnFloor(target);
        if (toArena && EndArena.radius(target) <= EndArena.FOOTPRINT_RADIUS)
            return player instanceof ServerPlayerEntity p && EndGate.hasPass(p);
        return true;
    }

    /** Vanilla mobs do not spawn into the arena while a fight runs. */
    public static boolean blocksSpawn(World world, Entity mob) {
        return fight != null && EndArena.inFootprint(world, mob.getEntityPos()) && sweepable(mob);
    }

    /** A vanilla mob nobody keeps: not the boss or an actor, and not persistent, named, leashed, ridden, riding or tamed. */
    private static boolean sweepable(Entity entity) {
        if (entity instanceof EndFighter || entity.getCommandTags().contains(ACTOR_TAG)
                || !Registries.ENTITY_TYPE.getId(entity.getType()).getNamespace().equals("minecraft")) return false;
        if (entity.hasCustomName() || entity.hasPassengers() || entity.hasVehicle()) return false;
        if (entity instanceof MobEntity mob && (mob.isPersistent() || mob.isLeashed())) return false;
        return !(entity instanceof Tameable tame && tame.getOwnerReference() != null)
                && !(entity instanceof TameableEntity tamed && tamed.isTamed())
                && !(entity instanceof AbstractHorseEntity horse && horse.isTame());
    }

    /** Why the portal refuses this Survival or Adventure player now, or null to let them through. */
    static String refusal(ServerPlayerEntity player) {
        if (fight == null) return null;
        UUID id = player.getUuid();
        if (fight.fallen.contains(id)) return "You fell in this fight. The End portal opens for you again when it ends.";
        if (fight.standing.contains(id) || party(player)) return null;
        return "Another party is fighting the First Wizard. The End portal opens when their fight ends.";
    }

    /** In the same party as someone on the running fight's roster. */
    private static boolean party(ServerPlayerEntity player) {
        PartyManager parties = PartyManager.get(player.getEntityWorld().getServer());
        for (UUID member : fight.roster) if (parties.allied(member, player.getUuid())) return true;
        return false;
    }

    /** Came through the portal into the End. */
    static void arrived(ServerPlayerEntity player) { arrivals.add(player.getUuid()); }

    // ------------------------------------------------------------------ the tick

    private static void tick(MinecraftServer server) {
        ServerWorld end = server.getWorld(EndArena.WORLD);
        if (!discards.isEmpty()) { List.copyOf(discards).forEach(e -> { if (!e.isRemoved()) e.discard(); }); discards.clear(); }
        if (end == null || !EndArenaManager.available()) return;
        int now = server.getTicks();
        if (departing != null && now >= departAt) { putAway(departing); departing = null; }
        for (UUID id : List.copyOf(arrivals)) {
            arrivals.remove(id);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) admit(server, end, p);
        }
        for (UUID id : List.copyOf(joined)) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) { joined.remove(id); continue; }
            if (p.networkHandler.player != p) continue;
            joined.remove(id);
            if (stranded(p)) home(p, "The island lets you go. You stand beside the End portal.");
        }
        for (UUID id : List.copyOf(respawns)) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) { respawns.remove(id); continue; }
            if (!p.isAlive() || p.networkHandler.player != p) continue;
            respawns.remove(id);
            home(p, "You wake beside the End portal.");
        }
        if (fight != null) tickFight(server, end, now);
        if (now % PLAYER_SWEEP == 0) sweepPlayers(end);
    }

    /** Whoever no fight holds on the island before the win goes to their way back (see the class comment). */
    private static void sweepPlayers(ServerWorld end) {
        visitors.removeIf(id -> {
            ServerPlayerEntity p = end.getServer().getPlayerManager().getPlayer(id);
            return p == null || p.getEntityWorld() != end;
        });
        for (ServerPlayerEntity p : List.copyOf(end.getPlayers())) {
            if (visitors.contains(p.getUuid()) || arrivals.contains(p.getUuid()) || !stranded(p)) continue;
            home(p, "The First Wizard holds this island until he is beaten, and only the End portal leads into his fight. The island sends you back.");
        }
    }

    /** A Survival or Adventure player is on the island: they start a fight, join it, or are sent back. */
    private static void admit(MinecraftServer server, ServerWorld end, ServerPlayerEntity p) {
        if (!p.isAlive() || p.isCreative() || p.isSpectator() || p.getEntityWorld() != end || EndArenaManager.defeated()) return;
        if (fight == null) { begin(server, end, p); return; }
        if (fight.standing.contains(p.getUuid())) return;
        String refusal = refusal(p);
        if (refusal != null) { home(p, refusal); return; }
        join(p);
    }

    /** Nobody may stay on the island (the arena's footprint) before the win unless a fight holds them. The rest of the End is free. */
    private static boolean stranded(ServerPlayerEntity p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && EndArena.inFootprint(p.getEntityWorld(), p.getEntityPos())
                && !EndArenaManager.defeated() && !fighting(p);
    }

    // ------------------------------------------------------------------ the fight

    private static void begin(MinecraftServer server, ServerWorld end, ServerPlayerEntity first) {
        if (factory == null) {
            home(first, "The First Wizard is not here yet: no boss is registered, so no fight starts. The island sends you back.");
            return;
        }
        fight = new Fight(first.getUuid(), server.getTicks());
        fight.roster.add(first.getUuid());
        fight.standing.add(first.getUuid());
        EndArenaManager.hold(end);
        // Whoever else is on the island joins if they are of the first player's party, and goes back otherwise.
        for (ServerPlayerEntity p : List.copyOf(end.getPlayers())) {   // a copy: sending someone back changes the list
            if (p == first || !p.isAlive() || p.isCreative() || p.isSpectator() || !EndArena.inFootprint(end, p.getEntityPos())) continue;
            if (party(p)) { fight.roster.add(p.getUuid()); fight.standing.add(p.getUuid()); }
            else home(p, "Another party's fight with the First Wizard begins. The island sends you back.");
        }
        announce(server, "The ink over the island stirs.");
    }

    private static void join(ServerPlayerEntity p) {
        fight.roster.add(p.getUuid());
        fight.standing.add(p.getUuid());
        fight.fallen.remove(p.getUuid());
        // During the intro they watch from where it has got to; the fight takes them in when it starts.
        if (fight.intro != null) fight.intro.addWatcher(p);
        else if (fight.boss != null) fight.boss.addParticipant(p);
        announce(p.getEntityWorld().getServer(), p.getName().getString() + " joins the fight.");
    }

    /** A fighter who fell, left or logged out: out of this fight, and refused at the portal until it ends. */
    private static void out(UUID id, ServerPlayerEntity player, String message) {
        if (fight == null || !fight.standing.remove(id)) return;
        fight.fallen.add(id);
        if (fight.boss != null) fight.boss.removeParticipant(id);
        if (player != null && message != null) player.sendMessage(Text.literal(message), false);
    }

    private static void tickFight(MinecraftServer server, ServerWorld end, int now) {
        for (UUID id : List.copyOf(fight.standing)) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null || !p.isAlive() || p.isCreative() || p.isSpectator() || p.getEntityWorld() != end) out(id, p, null);
        }
        // The boss does not fight on Peaceful: a fight running when the difficulty drops ends without a result.
        if (end.getDifficulty() == Difficulty.PEACEFUL) { end(server, "The First Wizard does not fight on Peaceful. The fight is over without a result.", true); return; }
        if (fight.boss == null) {
            if (fight.standing.isEmpty()) { end(server, null, true); return; }
            if (end.shouldTickEntityAt(EndArena.CENTRE) && end.shouldTickEntityAt(BlockPos.ofFloored(EndArena.arrival()))) placeBoss(server, end);
            else if (now - fight.opened > GIVE_UP) end(server, "The island did not wake. The portal will take you again.", true);
            return;
        }
        Entity body = fight.boss.asEntity();
        if (fight.boss.fightComplete()) { victory(server, end); return; }
        if (body.isRemoved() || !body.isAlive()) { end(server, "The First Wizard is gone. The fight is over without a result.", true); return; }
        if (fight.standing.isEmpty()) { end(server, null, true); return; }
        if (fight.intro != null && !fight.intro.tick()) { fight.intro = null; startBoss(server); if (fight == null) return; }
        // A fighter off the floor (over the edge, below it) is set back on its nearest edge, facing in, the fall forgiven.
        for (UUID id : fight.standing) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null || EndArena.onFloor(p.getEntityPos(), EndArena.EDGE_SLACK)) continue;
            Vec3d spot = EndArena.edgeSpot(p.getEntityPos());
            EndArenaManager.transfer(p, end, spot, EndArena.yawToCentre(spot), 0);
            p.sendMessage(Text.literal("The island will not let you go that way."), true);
        }
        if (now % MOB_SWEEP == 0)
            end.getEntitiesByClass(MobEntity.class, EndArena.footprintBox(),
                    e -> sweepable(e) && EndArena.radius(e.getEntityPos()) <= EndArena.FOOTPRINT_RADIUS).forEach(Entity::discard);
    }

    private static void placeBoss(MinecraftServer server, ServerWorld end) {
        EndFighter boss;
        try { boss = factory.apply(end); }
        catch (RuntimeException failure) { boss = null; }
        if (boss == null) { end(server, "The First Wizard could not be made; see the server log. The fight is over without a result.", true); return; }
        Entity body = boss.asEntity();
        putAwayLeftovers(end, body);
        Vec3d at = EndArena.bossSpot();
        body.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        fight.boss = boss;
        if (!end.spawnEntity(body)) { fight.boss = null; end(server, "The First Wizard could not be placed; see the server log. The fight is over without a result.", true); return; }
        // The First Wizard opens with its intro; the fight starts when the scene ends.
        if (body instanceof com.anton.elementalwands.entity.firstwizard.FirstWizardEntity copy) {
            boolean full = fight.forceFull != null ? fight.forceFull : !EndArenaManager.dragonDead(server);
            fight.intro = EndIntro.begin(end, copy, standing(server), server.getPlayerManager().getPlayer(fight.skinOwner), full);
            return;
        }
        startBoss(server);
    }

    private static List<ServerPlayerEntity> standing(MinecraftServer server) {
        List<ServerPlayerEntity> roster = new ArrayList<>();
        for (UUID id : fight.standing) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) roster.add(p);
        }
        return roster;
    }

    /** The placed boss begins its fight with whoever stands in it. */
    private static void startBoss(MinecraftServer server) {
        try { fight.boss.startFight(EndArena.CENTRE, EndArena.FLOOR_RADIUS, standing(server), fight.skinOwner); }
        catch (RuntimeException failure) {
            end(server, "The First Wizard could not begin: " + failure.getClass().getSimpleName() + ". The fight is over without a result.", true);
        }
    }

    /** Any boss already standing in the arena (left by a crash, or summoned) goes before a new one is placed. */
    private static void putAwayLeftovers(ServerWorld end, Entity keep) {
        for (Entity e : end.getEntitiesByClass(Entity.class, EndArena.footprintBox(), e -> e instanceof EndFighter && e != keep))
            putAway((EndFighter) e);
    }

    private static void putAway(EndFighter boss) {
        Entity body = boss.asEntity();
        if (body.isRemoved()) return;
        try { boss.stopFight(); } finally { body.discard(); }
    }

    private static void victory(MinecraftServer server, ServerWorld end) {
        EndArenaManager.setDefeated(server, true);
        EndArenaManager.openWayOut(end);
        departing = fight.boss;
        departAt = server.getTicks() + DEPART;
        announce(server, "The First Wizard is beaten. The way home has opened at the island's centre, and gateways to the outer islands stand at the four compass points.");
        fight = null;
        EndArenaManager.hold(end);
    }

    /**
     * Ends the fight without a result: the boss stops and goes, the arena is let go, and (unless the
     * server is stopping, when they go on their next login) whoever still stands on the island is sent
     * to their way back. {@code home} false leaves them standing in the End.
     */
    private static void end(MinecraftServer server, String message, boolean home) {
        Fight ended = fight;
        fight = null;
        ServerWorld end = server.getWorld(EndArena.WORLD);
        if (end != null) EndArenaManager.hold(end);
        // Cut short mid-scene: everyone it held is let go before the boss goes.
        if (ended.intro != null) ended.intro.cancel();
        if (ended.boss != null) putAway(ended.boss);
        for (UUID id : ended.roster) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) continue;
            if (message != null) p.sendMessage(Text.literal(message), false);
            if (home && !stopping && ended.standing.contains(id) && stranded(p)) home(p, "You stand beside the End portal.");
        }
    }

    private static void home(ServerPlayerEntity player, String message) {
        boolean recorded = EndArenaManager.sendHome(player);
        player.sendMessage(Text.literal(recorded ? message : "No way back was recorded; sent to world spawn."), false);
    }

    private static void announce(MinecraftServer server, String message) {
        if (fight == null) return;
        for (UUID id : fight.roster) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) p.sendMessage(Text.literal(message), false);
        }
    }

    // ------------------------------------------------------------------ commands

    public static String status() {
        StringBuilder out = new StringBuilder(EndArenaManager.status());
        out.append(factory == null ? " No boss is registered." : "");
        if (fight == null) out.append(" No fight is running.");
        else out.append(" Fight: ").append(fight.boss == null ? "starting" : fight.intro != null ? (fight.intro.full() ? "in the full intro" : "in the short intro")
                        + " (tick " + fight.intro.clock() + " of " + fight.intro.length() + ")" : fight.boss.fightComplete() ? "won" : "running")
                .append(", ").append(fight.standing.size()).append('/').append(fight.roster.size()).append(" standing, ")
                .append(fight.fallen.size()).append(" fallen.");
        return out.toString();
    }

    /** To the arrival spot; no fight starts, and the island lets them stay until they leave the End. */
    public static String enter(ServerPlayerEntity player) { return bring(player, false, "Entered the End's arena. No fight starts; /ew end leave takes you back."); }

    /** As if the player had arrived through the portal: to the arrival spot, and the fight starts for them. */
    public static String begin(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator()) return "Switch to Survival or Adventure first: the fight is for them.";
        if (EndArenaManager.defeated()) return "The First Wizard is beaten in this world. /ew end reset makes him undefeated again.";
        if (fight != null) return "A fight is already running.";
        if (factory == null) return "No boss is registered, so no fight can start.";
        if (player.getEntityWorld().getDifficulty() == Difficulty.PEACEFUL) return "The First Wizard does not fight on Peaceful. Raise the difficulty first.";
        return bring(player, true, "Arrived in the End; the fight begins.");
    }

    /** Nothing is recorded or moved until the arena is ready: the operator tries again in a moment. */
    private static String bring(ServerPlayerEntity player, boolean fights, String done) {
        MinecraftServer server = player.getEntityWorld().getServer();
        ServerWorld end = server.getWorld(EndArena.WORLD);
        if (end == null) return "The End is not loaded.";
        if (!EndArenaManager.available()) return "End arena storage is unavailable; see the server log.";
        if (!EndArenaManager.ready()) {
            String problem = EndArenaManager.raise(end);
            return problem != null ? problem : "The End's arena is being laid out; try again in a moment (/ew end status shows how far it is).";
        }
        if (player.getEntityWorld() != end) {
            Vec3d pos = player.getEntityPos();
            if (!EndArenaManager.recordReturn(player, new Point(player.getEntityWorld().getRegistryKey().getValue().toString(),
                    pos.x, pos.y, pos.z, player.getYaw(), player.getPitch()))) return "Could not record your way back, so you were not moved.";
        }
        EndArenaManager.arrive(player, end);
        if (fights) admit(server, end, player);
        else visitors.add(player.getUuid());
        return done;
    }

    /**
     * {@code /ew end intro [full]}: the intro again, then the fight. During a fight with the First Wizard its fighters
     * watch the scene where they stand and the fight starts afresh after it; with no fight it is {@code /ew end begin}
     * for the caller. {@code full} plays the scene with the dragon whatever the saved state; otherwise the short one.
     */
    public static String replayIntro(ServerPlayerEntity player, boolean full) {
        if (fight == null) {
            String message = begin(player);
            if (fight == null) return message;
            fight.forceFull = full;
            return (full ? "The full intro" : "The short intro") + " plays once the island wakes; the fight follows.";
        }
        if (fight.boss == null) { fight.forceFull = full; return "The fight is starting; it opens with the " + (full ? "full" : "short") + " intro."; }
        if (!(fight.boss instanceof com.anton.elementalwands.entity.firstwizard.FirstWizardEntity copy)) return "This fight's boss has no intro.";
        MinecraftServer server = player.getEntityWorld().getServer();
        ServerWorld end = server.getWorld(EndArena.WORLD);
        if (end == null || copy.isRemoved()) return "The First Wizard is not on the island.";
        if (fight.intro != null) { fight.intro.cancel(); fight.intro = null; }
        copy.stopFight();
        fight.intro = EndIntro.begin(end, copy, standing(server), server.getPlayerManager().getPlayer(fight.skinOwner), full);
        return "Replaying the " + (full ? "full" : "short") + " intro for " + fight.standing.size() + " fighter(s); the fight starts afresh after it.";
    }

    /** Ends any fight, makes the boss undefeated and closes the way home and the gateways this mod opened. */
    public static String reset(MinecraftServer server) {
        ServerWorld end = server.getWorld(EndArena.WORLD);
        if (end == null || !EndArenaManager.available()) return "The End or its record is unavailable.";
        if (fight != null) end(server, "An operator reset the End. The fight is over without a result.", true);
        if (departing != null) { putAway(departing); departing = null; }
        putAwayLeftovers(end, null);
        EndArenaManager.setDefeated(server, false);
        EndArenaManager.closeWayOut(end);
        return "The End is reset: the First Wizard is undefeated and the way home and gateways are closed.";
    }

    /** Anyone in the End goes to their way back. */
    public static String leave(ServerPlayerEntity player) {
        if (player.getEntityWorld().getRegistryKey() != EndArena.WORLD) return "You are not in the End.";
        if (!EndArenaManager.available()) return "End arena storage is unavailable; see the server log.";
        out(player.getUuid(), null, null);
        visitors.remove(player.getUuid());
        return EndArenaManager.sendHome(player) ? "Returned to your way back." : "No way back was recorded; sent to world spawn.";
    }

    /**
     * Lays the arena out again. Without {@code force} it first checks the island and stops if block
     * entities stand in the way, and it refuses after the win, when the island is the players'.
     */
    public static String layout(MinecraftServer server, boolean force) {
        ServerWorld end = server.getWorld(EndArena.WORLD);
        if (end == null) return "The End is not loaded.";
        if (fight != null) return "A fight is running; reset it first.";
        if (EndArenaManager.building()) return "The arena is already being laid out.";
        if (EndArenaManager.defeated() && !force)
            return "The First Wizard is beaten here, so the island is the players' and is not laid out again. /ew end layout force would"
                    + " clear everything above the floor within 64 blocks of the centre and lay the arena's tiles again: every chest, shulker box,"
                    + " sign and other block entity in the way is broken as a player would break it (containers drop their contents),"
                    + " dragon eggs are set down beside the arrival, and the way home stays open.";
        String problem = EndArenaManager.layOut(end, force);
        if (problem != null) return problem;
        return force ? "Laying the End's arena out, breaking whatever stands in its way (containers drop their contents)."
                : "Checking the island, then laying the End's arena out. If players' things stand in the way it stops there; /ew end status lists them.";
    }

    // ------------------------------------------------------------------ for fixtures

    /** The running fight's intro, or null. */
    public static EndIntro intro() { return fight == null ? null : fight.intro; }

    /** The running fight's boss, or null. */
    public static EndFighter boss() { return fight == null ? null : fight.boss; }

    public static boolean fell(PlayerEntity player) { return fight != null && fight.fallen.contains(player.getUuid()); }
}
