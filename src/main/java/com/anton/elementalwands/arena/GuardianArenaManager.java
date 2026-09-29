package com.anton.elementalwands.arena;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.arena.GuardianArenaJournal.Point;
import com.anton.elementalwands.church.GuardianChurchManager;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.registry.ModEntities;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.anton.elementalwands.arena.GuardianArenaRules.*;

/**
 * The Fractured Guardian's arena, fought in the Shattered Nave. A church ritual seals the gathered
 * group into a free slot of the endless hall; the Guardian drops onto the effigy seat and the
 * fight begins. The fallen watch as spectators, a wipe sends everyone back to the church with
 * their belongings, and a victory restores the church. Fighters and their teleports stay inside
 * the walled floor, and nothing damages the hall's blocks.
 */
public final class GuardianArenaManager {
    private static final Logger LOG = LoggerFactory.getLogger("elementalwands-arena");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    static final class State {
        int version = 1;
        /** Bumped when the layout changes, which moves every slot to untouched ground. */
        int generation;
        String layout = "";
        List<Integer> built = new ArrayList<>();
        /** Where each player entered from; the nave always sends them back here. */
        Map<String, Point> returns = new LinkedHashMap<>();
        /** Game modes to restore for players made spectators by a fight. */
        Map<String, String> modes = new LinkedHashMap<>();
        /** Fights in progress, by slot. A restart cancels them and sends everyone home. */
        Map<String, Fight> fights = new LinkedHashMap<>();
    }

    static final class Fight {
        /** The church whose ritual started it; null for an operator summon. */
        String site;
        /** Sealed when the fight begins. */
        List<String> roster = new ArrayList<>();
        List<String> standing = new ArrayList<>();
        /** A party fight wipes when nobody is left standing; an empty operator summon never does. */
        boolean party;
        String boss;
        /** Who offered the heart; they hold it out in the intro. Null for an operator summon from the console. */
        String caller;
        /** The Guardian has woken and the fight is on; until then nobody casts or takes damage. */
        boolean fighting;
        boolean won;
    }

    private static final class Build {
        final int slot;
        final BlockPos centre;
        final List<Identifier> tiles = new ArrayList<>();
        final List<BlockPos> origins = new ArrayList<>();
        final Set<UUID> waiting = new LinkedHashSet<>();
        boolean ritual;
        int next;
        Build(int slot, BlockPos centre) { this.slot = slot; this.centre = centre; }
    }

    /** The Guardian kneeling on the seat through its intro, until the fight starts. */
    private record Arrival(FracturedGuardianEntity guardian) {}

    private static Path path;
    private static State state;
    private static String layout = "";
    private static final Map<Integer, Build> builds = new HashMap<>();
    private static final Map<Integer, Arrival> arrivals = new HashMap<>();
    private static boolean internalTeleport, internalMutation;
    /** Server tick at which a slot's Guardian wakes, and at which an ended fight sends its players home. */
    private static final Map<Integer, Integer> risings = new HashMap<>(), endings = new HashMap<>();
    /** Players whose respawn the nave handles (spectate or go home), and whose belongings it carries over. */
    private static final Set<UUID> respawns = new LinkedHashSet<>(), joins = new LinkedHashSet<>(), kept = new HashSet<>();
    /** Players the retired sky arena still owed a trip home when this world was upgraded. */
    private static final Set<String> legacy = new HashSet<>();

    private GuardianArenaManager() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(GuardianArenaManager::load);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> layout = fingerprint(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            state = null; path = null; builds.clear(); arrivals.clear(); risings.clear(); endings.clear();
            respawns.clear(); joins.clear(); kept.clear(); legacy.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(GuardianArenaManager::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (state == null) return;
            if (entity instanceof FracturedGuardianEntity guardian) victory(guardian);
            else if (entity instanceof ServerPlayerEntity player) {
                abandon(player);
                if (fightOf(player) != null) eliminate(player, "You fell. You can watch the rest of the fight.");
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(GuardianArenaManager::allowDamage);
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            // The set is lost on a restart; the body still lying in the nave is what survives a quit from the death screen.
            if (alive || !kept.remove(oldPlayer.getUuid()) && !inRealm(oldPlayer)) return;
            newPlayer.getInventory().clone(oldPlayer.getInventory());
            newPlayer.experienceLevel = oldPlayer.experienceLevel;
            newPlayer.totalExperience = oldPlayer.totalExperience;
            newPlayer.experienceProgress = oldPlayer.experienceProgress;
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // Only a death in the nave or in a fight; wait a tick for the connection to reference the new player.
            if (!alive && state != null && (rosterOf(newPlayer) != null || inRealm(oldPlayer)))
                respawns.add(newPlayer.getUuid());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (state == null) return;
            abandon(handler.getPlayer());
            if (fightOf(handler.getPlayer()) != null) eliminate(handler.getPlayer(), null);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            if (state != null) joins.add(handler.getPlayer().getUuid());
        }));
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, block, entity) -> !restricted(world, player));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Item item = player.getStackInHand(hand).getItem();
            return restricted(world, player) && (item instanceof BlockItem || item instanceof BucketItem || item instanceof FlintAndSteelItem
                    || item instanceof FireChargeItem || item instanceof BoneMealItem) ? ActionResult.FAIL : ActionResult.PASS;
        });
    }

    // ------------------------------------------------------------------ rules other systems ask

    public static boolean inRealm(Entity entity) { return entity.getEntityWorld().getRegistryKey() == ShatteredNave.WORLD; }

    private static boolean restricted(World world, PlayerEntity player) {
        return world.getRegistryKey() == ShatteredNave.WORLD && !player.isCreative();
    }

    /** Nothing drops in the nave: the respawned player gets their inventory and experience back. */
    public static boolean keepsBelongings(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity) || !inRealm(player)) return false;
        kept.add(player.getUuid());
        return true;
    }

    /**
     * Every player teleport (spells, pearls, commands, portals, spectator jumps) stays inside the
     * walled floor and never crosses into or out of the nave; only the nave's own moves do that.
     * Fallen players watching a fight may roam the slot but not leave it. The Hollow Crypt applies
     * its own rules first.
     */
    public static boolean canTeleport(PlayerEntity player, ServerWorld destination, Vec3d target) {
        if (!com.anton.elementalwands.crypt.HollowCryptManager.canTeleport(player, destination, target)) return false;
        if (internalTeleport) return true;
        boolean from = inRealm(player), to = destination.getRegistryKey() == ShatteredNave.WORLD;
        if (state != null && state.modes.containsKey(player.getUuidAsString()) && from)
            return to && ShatteredNave.footprint(ShatteredNave.nearestCentre(player.getEntityPos())).contains(target);
        if (player.isCreative() || player.isSpectator()) return true;
        if (!from && !to) return true;
        return from && to && ShatteredNave.inPlay(ShatteredNave.nearestCentre(player.getEntityPos()), target, .4);
    }

    /** Sealed fighters cast only once the Guardian has woken, and only while still standing. */
    public static boolean canCast(PlayerEntity player) {
        if (player.isSpectator()) return false;
        Fight fight = rosterOf(player);
        return fight == null || fight.fighting && fight.standing.contains(player.getUuidAsString());
    }

    public static boolean isParticipant(PlayerEntity player) { return rosterOf(player) != null; }

    /** A Guardian this arena raised in the nave for a fight that has not ended. */
    public static boolean owns(FracturedGuardianEntity guardian) { return fightOf(guardian) != null; }

    public static boolean isFighting(FracturedGuardianEntity guardian) {
        Fight fight = fightOf(guardian);
        return fight != null && fight.fighting && !fight.won;
    }

    public static List<String> enrolledPlayers(FracturedGuardianEntity guardian) {
        Fight fight = fightOf(guardian);
        return fight == null ? List.of() : List.copyOf(fight.roster);
    }

    /** An arena Guardian only fights the sealed players still standing; any other Guardian fights anyone. */
    public static boolean eligible(FracturedGuardianEntity guardian, ServerPlayerEntity player) {
        Fight fight = fightOf(guardian);
        return fight == null || fight.fighting && fight.standing.contains(player.getUuidAsString());
    }

    public static double encounterRange(FracturedGuardianEntity guardian) { return owns(guardian) ? 192 : 48; }
    public static double movementRange(FracturedGuardianEntity guardian) { return owns(guardian) ? ShatteredNave.HALF - 6 : 14; }

    public static boolean validLanding(FracturedGuardianEntity guardian, Vec3d home, Vec3d target) {
        if (!owns(guardian)) return target.squaredDistanceTo(home) <= 36 * 36;
        BlockPos centre = ShatteredNave.nearestCentre(guardian.getEntityPos());
        return ShatteredNave.inPlay(centre, target, 4) && target.y >= ShatteredNave.SURFACE_Y + .8
                && target.y <= ShatteredNave.SURFACE_Y + 1 + ShatteredNave.CEILING - guardian.getHeight();
    }

    /** Fallen fighters see the Guardian's bar while they watch its fight. */
    public static boolean spectatorViewer(FracturedGuardianEntity guardian, ServerPlayerEntity player) {
        Fight fight = fightOf(guardian);
        return fight != null && player.isAlive() && player.isSpectator() && inRealm(player)
                && fight.roster.contains(player.getUuidAsString()) && !fight.standing.contains(player.getUuidAsString());
    }

    private record SpellWrite(World world, BlockPos pos, BlockState state) {}
    private static SpellWrite spellWrite;

    /**
     * Temporary spell blocks write through here. In the nave they may cover and then restore the
     * fight floor, but never open a hole in it or reach into the rest of the hall; everywhere else
     * this is an ordinary block write.
     */
    public static boolean setTemporarySpellBlock(ServerWorld world, BlockPos pos, BlockState state) {
        if (!ShatteredNave.keepsTerrain(world)) return world.setBlockState(pos, state, Block.NOTIFY_ALL);
        if (protectedBlock(world, pos) && (state.isAir() || !fightFloor(pos))) return false;
        SpellWrite previous = spellWrite;
        spellWrite = new SpellWrite(world, pos.toImmutable(), state);
        try { return world.setBlockState(pos, state, Block.NOTIFY_ALL); }
        finally { spellWrite = previous; }
    }

    public static boolean authorizedSpellWrite(World world, BlockPos pos, BlockState state) {
        return spellWrite != null && spellWrite.world == world && spellWrite.pos.equals(pos) && spellWrite.state.equals(state);
    }

    /**
     * The hall itself: the floor and everything outside the walled fight volume. Spells may still
     * place and clear their own blocks above the floor.
     */
    public static boolean protectedBlock(World world, BlockPos pos) {
        if (internalMutation || world.getRegistryKey() != ShatteredNave.WORLD) return false;
        if (pos.getY() <= ShatteredNave.SURFACE_Y) return true;
        BlockPos centre = ShatteredNave.nearestCentre(Vec3d.ofCenter(pos));
        int dx = pos.getX() - centre.getX(), dz = pos.getZ() - centre.getZ();
        return dx < -ShatteredNave.HALF || dx >= ShatteredNave.HALF || dz < -ShatteredNave.HALF || dz >= ShatteredNave.HALF
                || pos.getY() > ShatteredNave.SURFACE_Y + ShatteredNave.CEILING;
    }

    /** The walkable top layer of a slot's fight floor. */
    private static boolean fightFloor(BlockPos pos) {
        BlockPos centre = ShatteredNave.nearestCentre(Vec3d.ofCenter(pos));
        int dx = pos.getX() - centre.getX(), dz = pos.getZ() - centre.getZ();
        return pos.getY() == ShatteredNave.SURFACE_Y && dx >= -ShatteredNave.HALF && dx < ShatteredNave.HALF
                && dz >= -ShatteredNave.HALF && dz < ShatteredNave.HALF;
    }

    /** Until the Guardian wakes nobody in its fight takes damage; afterwards only its fighters may hurt it. */
    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (state == null || source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        if (entity instanceof ServerPlayerEntity player && inRealm(player)) {
            Fight fight = rosterOf(player);
            if (fight != null && !fight.fighting) return false;
        }
        if (entity instanceof FracturedGuardianEntity guardian) {
            Fight fight = fightOf(guardian);
            if (fight == null) return true;
            if (!fight.fighting) return false;
            if (source.getAttacker() instanceof ServerPlayerEntity player) return fight.standing.contains(player.getUuidAsString());
        }
        return true;
    }

    // ------------------------------------------------------------------ church ritual

    /** Whether a church's ritual currently has a fight in the nave. */
    public static boolean hosts(String site) {
        return state != null && site != null && state.fights.values().stream().anyMatch(f -> site.equals(f.site));
    }

    /**
     * The church ritual seals every living player gathered around the socket into a fight in a
     * free slot; shortly after they arrive the Guardian's intro wakes it on its seat. {@code accepted}
     * consumes the heart once the fight is recorded. Anyone already sealed into a fight or
     * waiting for a slot to open stays with it.
     */
    public static String ritual(ServerPlayerEntity caller, String site, BlockPos socket, Runnable accepted) {
        ServerWorld here = (ServerWorld) caller.getEntityWorld();
        ServerWorld realm = realm(here.getServer());
        if (realm == null || state == null) return "The heart is silent. (The nave is unavailable; see the server log.)";
        if (here.getDifficulty() == Difficulty.PEACEFUL) return "Switch out of Peaceful before awakening the keeper.";
        // A held right-click repeats while the slot builds; a second fight would share the first one's party.
        if (rosterOf(caller) != null || pending(caller.getUuid())) return "The heart is already carrying you away...";
        refreshGeneration();
        int slot = freeSlot(realm);
        if (slot < 0) return "Every nave is occupied. Try again when a fight ends.";
        List<ServerPlayerEntity> group = here.getPlayers(p -> p.isAlive() && !p.isSpectator()
                && p.squaredDistanceTo(Vec3d.ofCenter(socket)) <= GATHER_RADIUS * GATHER_RADIUS
                && rosterOf(p) == null && !pending(p.getUuid()));
        Fight fight = new Fight();
        fight.site = site;
        fight.caller = caller.getUuidAsString();
        for (ServerPlayerEntity p : group) {
            state.returns.put(p.getUuidAsString(), point(p));
            if (!p.isCreative()) fight.roster.add(p.getUuidAsString());
        }
        fight.standing.addAll(fight.roster);
        fight.party = !fight.roster.isEmpty();
        state.fights.put(String.valueOf(slot), fight);
        if (!persist()) { state.fights.remove(String.valueOf(slot)); return "The ritual could not be saved. Your heart was not consumed."; }
        accepted.run();
        here.playSound(null, socket, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.5f, .5f);
        here.playSound(null, socket, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.4f, .7f);
        here.spawnParticles(ParticleTypes.END_ROD, socket.getX() + .5, socket.getY() + 1, socket.getZ() + .5, 80, 1.2, 1.5, 1.2, .06);
        for (ServerPlayerEntity p : group) {
            cancelEncounterEffects(p);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0, false, false));
        }
        if (ready(slot)) {
            arriveAll(group, realm, slot);
            risings.put(slot, here.getServer().getTicks() + RISE_DELAY);
        } else {
            Build build = build(realm, slot);
            build.ritual = true;
            group.forEach(p -> build.waiting.add(p.getUuid()));
        }
        return "The heart pulls " + (group.size() == 1 ? "you" : group.size() + " of you") + " into the nave...";
    }

    /** A slot with no fight, nothing building or pending, and nobody standing in it. */
    private static int freeSlot(ServerWorld realm) {
        for (int slot = 0; slot < ShatteredNave.SLOTS; slot++) {
            if (state.fights.containsKey(String.valueOf(slot)) || builds.containsKey(slot) || risings.containsKey(slot)
                    || endings.containsKey(slot) || arrivals.containsKey(slot)) continue;
            var area = ShatteredNave.footprint(ShatteredNave.centre(state.generation, slot));
            if (realm.getPlayers(p -> !p.isSpectator() && area.contains(p.getEntityPos())).isEmpty()) return slot;
        }
        return -1;
    }

    /** Waiting to be taken into a slot that is still building. */
    private static boolean pending(UUID id) {
        for (Build build : builds.values()) if (build.waiting.contains(id)) return true;
        return false;
    }

    /**
     * A player who dies or leaves before their slot opens never went in: they drop out of the
     * waiting fight, and a return point recorded outside the nave is forgotten.
     */
    private static void abandon(ServerPlayerEntity player) {
        boolean waited = false;
        for (Build build : builds.values()) {
            if (!build.waiting.remove(player.getUuid())) continue;
            waited = true;
            Fight fight = build.ritual ? state.fights.get(String.valueOf(build.slot)) : null;
            if (fight != null) {
                fight.roster.remove(player.getUuidAsString());
                fight.standing.remove(player.getUuidAsString());
            }
        }
        if (!waited) return;
        if (!inRealm(player)) state.returns.remove(player.getUuidAsString());
        persist();
    }

    private static void cancelEncounterEffects(ServerPlayerEntity player) {
        com.anton.elementalwands.util.ZephyrStrikeManager.cancel(player);
        com.anton.elementalwands.util.TitanDomeManager.cancelForEncounter(player);
        com.anton.elementalwands.util.HollowPurpleChargeManager.cancel(player);
    }

    // ------------------------------------------------------------------ fights

    private static Integer slotOf(Fight fight) {
        for (var entry : state.fights.entrySet()) if (entry.getValue() == fight) return Integer.valueOf(entry.getKey());
        return null;
    }

    /** The fight a player is still standing in, if any. */
    private static Fight fightOf(PlayerEntity player) {
        if (state == null) return null;
        for (Fight fight : state.fights.values()) if (fight.standing.contains(player.getUuidAsString())) return fight;
        return null;
    }

    /** The fight a Guardian was raised for, if it has not ended. */
    private static Fight fightOf(FracturedGuardianEntity guardian) {
        if (state == null || !inRealm(guardian)) return null;
        for (var entry : state.fights.entrySet())
            if (guardian.getUuidAsString().equals(entry.getValue().boss) && !endings.containsKey(Integer.valueOf(entry.getKey()))) return entry.getValue();
        return null;
    }

    /** The fight a player was sealed into, standing or fallen, if it has not ended. */
    private static Fight rosterOf(PlayerEntity player) {
        if (state == null) return null;
        for (var entry : state.fights.entrySet())
            if (entry.getValue().roster.contains(player.getUuidAsString()) && !endings.containsKey(Integer.valueOf(entry.getKey()))) return entry.getValue();
        return null;
    }

    private static void eliminate(ServerPlayerEntity player, String message) {
        Fight fight = fightOf(player);
        if (fight == null) return;
        fight.standing.remove(player.getUuidAsString());
        cancelEncounterEffects(player);
        persist();
        // The last to fall hears about the wipe instead.
        if (message != null && !fight.standing.isEmpty()) player.sendMessage(Text.literal(message), false);
    }

    private static void victory(FracturedGuardianEntity guardian) {
        for (var entry : state.fights.entrySet()) {
            Fight fight = entry.getValue();
            if (fight.won || !fight.fighting || !guardian.getUuidAsString().equals(fight.boss)) continue;
            int slot = Integer.parseInt(entry.getKey());
            fight.won = true;
            MinecraftServer server = guardian.getEntityWorld().getServer();
            // Commit the victory before the church changes a block or a reward inventory.
            persist();
            String reward = fight.site == null ? "" : GuardianChurchManager.naveVictory(server, fight.site, fight.roster);
            endings.put(slot, server.getTicks() + VICTORY_DELAY);
            tell(fight, server, "The Fractured Guardian falls. The nave releases you in 10 seconds." + reward);
            return;
        }
    }

    /** Each tick: wake the Guardian, drop the fallen, detect a wipe or a lost boss, and finish ended fights. */
    private static void tickFights(MinecraftServer server, ServerWorld realm) {
        int now = server.getTicks();
        risings.entrySet().removeIf(rise -> {
            if (now < rise.getValue()) return false;
            Fight fight = state.fights.get(String.valueOf(rise.getKey()));
            if (fight != null) {
                FracturedGuardianEntity guardian = raise(realm, rise.getKey(), fight);
                if (guardian != null) {
                    fight.boss = guardian.getUuidAsString();
                    persist();
                }
            }
            return true;
        });
        arrivals.entrySet().removeIf(entry -> wake(server, realm, entry.getKey(), entry.getValue()));
        for (var entry : List.copyOf(state.fights.entrySet())) {
            int slot = Integer.parseInt(entry.getKey());
            Fight fight = entry.getValue();
            if (fight.boss == null || fight.won || endings.containsKey(slot)) continue;
            BlockPos centre = ShatteredNave.centre(state.generation, slot);
            var area = ShatteredNave.footprint(centre);
            for (String id : List.copyOf(fight.standing)) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(UUID.fromString(id));
                if (p == null || !p.isAlive() || p.getEntityWorld() != realm || !area.contains(p.getEntityPos()) || p.isSpectator()) {
                    fight.standing.remove(id);
                    if (p != null) cancelEncounterEffects(p);
                    persist();
                }
            }
            Entity boss = realm.getEntity(UUID.fromString(fight.boss));
            if (fight.party && fight.standing.isEmpty()) {
                // A wipe: the Guardian vanishes and everyone goes back with their belongings.
                clearEntities(realm, slot);
                endings.put(slot, now + WIPE_DELAY);
                tell(fight, server, "The nave swallows the Guardian. You are cast back to the church.");
            } else if (boss == null && !arrivals.containsKey(slot) || boss != null && !boss.isAlive()
                    || realm.getDifficulty() == Difficulty.PEACEFUL) {
                clearEntities(realm, slot);
                endings.put(slot, now + WIPE_DELAY);
                tell(fight, server, "The fight is over. The nave releases you.");
            } else if (boss instanceof FracturedGuardianEntity guardian && fight.fighting && strayed(centre, guardian)) {
                // Preserve the high leap; only a horizontal escape or a fall beneath the floor is corrected.
                guardian.stopReview();
                guardian.setPosition(ShatteredNave.seat(centre));
                guardian.startFight();
            }
        }
        endings.entrySet().removeIf(ending -> {
            if (now < ending.getValue()) return false;
            finish(server, realm, ending.getKey());
            return true;
        });
    }

    private static boolean strayed(BlockPos centre, FracturedGuardianEntity guardian) {
        return Math.abs(guardian.getX() - centre.getX() - .5) > ShatteredNave.HALF - 3
                || Math.abs(guardian.getZ() - centre.getZ() - .5) > ShatteredNave.HALF - 3 || guardian.getY() < ShatteredNave.SURFACE_Y;
    }

    /** Sends everyone in the fight home and retires it; a lost ritual lets the church offer its heart again. */
    private static void finish(MinecraftServer server, ServerWorld realm, int slot) {
        Fight fight = state.fights.get(String.valueOf(slot));
        if (fight == null) return;
        Set<ServerPlayerEntity> members = members(fight, slot, server, realm);
        state.fights.remove(String.valueOf(slot));
        persist();
        if (!fight.won && fight.site != null) GuardianChurchManager.naveFinished(fight.site);
        var area = ShatteredNave.footprint(ShatteredNave.centre(state.generation, slot));
        for (ServerPlayerEntity p : members)
            // Offline or still on the death screen: handled on join or respawn. A wiped party is home
            // before its fight ends and may already be in a new one in another slot; that one keeps them.
            if (p.isAlive() && p.networkHandler.player == p && p.getEntityWorld() == realm && area.contains(p.getEntityPos())) leave(p);
    }

    /**
     * The sealed roster plus anyone else the nave brought into the slot, such as Creative players
     * the ritual took along; a fight's end releases them all. Without a realm, only the roster.
     */
    private static Set<ServerPlayerEntity> members(Fight fight, int slot, MinecraftServer server, ServerWorld realm) {
        Set<ServerPlayerEntity> members = new LinkedHashSet<>();
        for (String id : fight.roster) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(UUID.fromString(id));
            if (p != null) members.add(p);
        }
        if (realm != null && slot >= 0) {
            var area = ShatteredNave.footprint(ShatteredNave.centre(state.generation, slot));
            // Only those the nave brought in (it recorded where they came from), not an operator who /tp'd in.
            members.addAll(realm.getPlayers(p -> area.contains(p.getEntityPos()) && state.returns.containsKey(p.getUuidAsString())));
        }
        return members;
    }

    /** Fallen fighters watch from above the floor until the fight ends. */
    private static void spectate(ServerPlayerEntity player, int slot, ServerWorld realm) {
        if (!state.modes.containsKey(player.getUuidAsString()) && !player.isSpectator()) {
            state.modes.put(player.getUuidAsString(), player.interactionManager.getGameMode().getId());
            persist();
        }
        cancelEncounterEffects(player);
        player.changeGameMode(GameMode.SPECTATOR);
        player.setCameraEntity(player);
        transfer(player, realm, ShatteredNave.gallery(ShatteredNave.centre(state.generation, slot)), 180, 25);
        player.sendMessage(Text.literal("You are watching the fight. You will return with your belongings when it ends."), false);
    }

    /**
     * Respawned players go back into their fight as spectators, or to where they entered. Rejoining
     * players who are still in the nave do the same; a rejoin elsewhere is left alone.
     */
    private static void tickReturns(MinecraftServer server, ServerWorld realm) {
        for (Set<UUID> queue : List.of(respawns, joins)) for (UUID id : List.copyOf(queue)) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) { queue.remove(id); continue; }
            if (!p.isAlive() || p.networkHandler.player != p) continue;
            queue.remove(id);
            if (queue == joins && legacy.remove(id.toString())) { sendHome(p); continue; }
            if (queue == joins && !inRealm(p)) continue;
            Fight fight = rosterOf(p);
            Integer slot = fight == null ? null : slotOf(fight);
            if (slot != null) { fight.standing.remove(p.getUuidAsString()); spectate(p, slot, realm); }
            else if (state.modes.containsKey(id.toString()) || !p.isCreative() && state.returns.containsKey(id.toString())) sendHome(p);
        }
        // Watchers stay above their own floor.
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!state.modes.containsKey(p.getUuidAsString()) || !p.isAlive()) continue;
            Fight fight = rosterOf(p);
            Integer slot = fight == null ? null : slotOf(fight);
            if (slot == null) continue;
            var area = ShatteredNave.footprint(ShatteredNave.centre(state.generation, slot));
            if (p.getEntityWorld() != realm || !area.contains(p.getEntityPos())) spectate(p, slot, realm);
        }
    }

    // ------------------------------------------------------------------ the Guardian's arrival

    /**
     * Clears leftovers from any earlier fight and kneels the Guardian on the effigy seat as a
     * statue; its intro cinematic plays for everyone in the slot, with the caller holding out the
     * heart that wakes it. It stays untouchable until the intro hands over to the fight.
     */
    private static FracturedGuardianEntity raise(ServerWorld realm, int slot, Fight fight) {
        clearEntities(realm, slot);
        BlockPos centre = ShatteredNave.centre(state.generation, slot);
        Vec3d seat = ShatteredNave.seat(centre);
        FracturedGuardianEntity guardian = ModEntities.FRACTURED_GUARDIAN.create(realm, net.minecraft.entity.SpawnReason.COMMAND);
        if (guardian == null) return null;
        // Facing south, toward the players at the arrival point.
        guardian.refreshPositionAndAngles(seat.x, seat.y, seat.z, 0, 0);
        guardian.setHeadYaw(0);
        guardian.setBodyYaw(0);
        guardian.stopReview();
        guardian.setInvulnerable(true);
        realm.spawnEntity(guardian);
        var area = ShatteredNave.footprint(centre);
        List<ServerPlayerEntity> watchers = realm.getPlayers(p -> p.isAlive() && !p.isSpectator() && area.contains(p.getEntityPos()));
        ServerPlayerEntity caller = fight.caller == null ? null : realm.getServer().getPlayerManager().getPlayer(UUID.fromString(fight.caller));
        guardian.beginIntro(watchers, caller);
        arrivals.put(slot, new Arrival(guardian));
        return guardian;
    }

    /** Starts the fight once the Guardian's intro has handed over. Returns true when done. */
    private static boolean wake(MinecraftServer server, ServerWorld realm, int slot, Arrival arrival) {
        Fight fight = state.fights.get(String.valueOf(slot));
        FracturedGuardianEntity guardian = arrival.guardian();
        if (fight == null || endings.containsKey(slot) || !guardian.isAlive() || guardian.isRemoved()) return true;
        if (guardian.inIntro()) return false;
        guardian.setInvulnerable(false);
        // The intro starts the fight itself; one an operator interrupted is started here.
        if (!guardian.isBossAggressive()) guardian.startFight();
        fight.fighting = true;
        persist();
        tell(fight, server, "The keeper awakens.");
        return true;
    }

    private static void sound(ServerWorld world, Vec3d at, SoundEvent event, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, event, SoundCategory.HOSTILE, volume, pitch);
    }

    // ------------------------------------------------------------------ moving players

    /** Back to the recorded entry point (or world spawn), restoring any game mode the nave changed. */
    private static boolean sendHome(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        String id = player.getUuidAsString();
        Point point = state.returns.get(id);
        ServerWorld world = point == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(point.dimension())));
        if (world != null && world.getRegistryKey() != ShatteredNave.WORLD) {
            transfer(player, world, new Vec3d(point.x(), point.y(), point.z()), point.yaw(), point.pitch());
        } else {
            world = null;
            ServerWorld spawn = server.getSpawnWorld();
            BlockPos pos = server.getSpawnPoint().getPos();
            spawn.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            int y = spawn.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
            transfer(player, spawn, new Vec3d(pos.getX() + .5, y, pos.getZ() + .5), server.getSpawnPoint().yaw(), 0);
        }
        String mode = state.modes.remove(id);
        if (mode != null) {
            player.setCameraEntity(player);
            player.changeGameMode(GameMode.byId(mode, GameMode.SURVIVAL));
        }
        state.returns.remove(id);
        persist();
        return world != null;
    }

    private static void arriveAll(List<ServerPlayerEntity> group, ServerWorld realm, int slot) {
        for (int i = 0; i < group.size(); i++) arrive(group.get(i), realm, slot, i - (group.size() - 1) / 2.0);
    }

    /** Players arrive side by side, two blocks apart, facing the seat. */
    private static void arrive(ServerPlayerEntity player, ServerWorld realm, int slot, double place) {
        Vec3d at = ShatteredNave.arrival(ShatteredNave.centre(state.generation, slot)).add(Math.clamp(place, -8, 8) * 2, 0, 0);
        transfer(player, realm, at, ShatteredNave.ARRIVAL_YAW, 0);
    }

    private static void transfer(ServerPlayerEntity player, ServerWorld world, Vec3d pos, float yaw, float pitch) {
        player.stopRiding();
        internalTeleport = true;
        try { player.teleport(world, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, true); }
        finally { internalTeleport = false; }
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        player.velocityModified = true;
    }

    // ------------------------------------------------------------------ commands

    public static String enter(ServerPlayerEntity player, int slot) {
        ServerWorld realm = realm(player.getEntityWorld().getServer());
        if (realm == null) return "The Shattered Nave dimension is not loaded.";
        if (state == null) return "Shattered Nave storage is unavailable; see the server log.";
        if (!inRealm(player)) {
            state.returns.put(player.getUuidAsString(), point(player));
            if (!persist()) return "Could not record your return point, so you were not moved.";
        }
        if (ready(slot)) {
            arrive(player, realm, slot, 0);
            return "Entered nave slot " + slot + ". /ew nave summon wakes the Guardian; /ew nave leave takes you back.";
        }
        Build build = build(realm, slot);
        build.waiting.add(player.getUuid());
        return "Raising nave slot " + slot + " (" + build.tiles.size() + " sections); you will be taken in when it is ready.";
    }

    public static String leave(ServerPlayerEntity player) {
        if (!inRealm(player)) return "You are not in the Shattered Nave.";
        if (state == null) return "Shattered Nave storage is unavailable; see the server log.";
        Fight fight = rosterOf(player);
        if (fight != null) {
            fight.standing.remove(player.getUuidAsString());
            fight.roster.remove(player.getUuidAsString());
            persist();
        }
        cancelEncounterEffects(player);
        return sendHome(player) ? "Returned to where you entered." : "No return point was recorded; sent to world spawn.";
    }

    /** Operator summon: the survival players already in the slot become the fight's sealed party. */
    public static String summon(ServerPlayerEntity player) {
        if (!inRealm(player)) return "Enter the nave first with /ew nave enter.";
        ServerWorld realm = (ServerWorld) player.getEntityWorld();
        BlockPos centre = ShatteredNave.nearestCentre(player.getEntityPos());
        int slot = slotAt(centre);
        if (slot < 0) return "This slot is from an older layout; /ew nave enter a current one.";
        abort(realm, slot);
        Fight fight = new Fight();
        fight.caller = player.getUuidAsString();
        var area = ShatteredNave.footprint(centre);
        for (ServerPlayerEntity p : realm.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && area.contains(p.getEntityPos())))
            fight.roster.add(p.getUuidAsString());
        fight.standing.addAll(fight.roster);
        fight.party = !fight.roster.isEmpty();
        state.fights.put(String.valueOf(slot), fight);
        persist();
        risings.put(slot, realm.getServer().getTicks());
        return "The Guardian wakes on its seat" + (fight.roster.isEmpty() ? " (no survival players here, so it waits until reset)." : ".");
    }

    public static String reset(ServerPlayerEntity player) {
        if (!inRealm(player)) return "Stand in the nave slot you want to reset.";
        ServerWorld realm = (ServerWorld) player.getEntityWorld();
        BlockPos centre = ShatteredNave.nearestCentre(player.getEntityPos());
        int slot = slotAt(centre);
        if (slot < 0) return "This slot is from an older layout, so it was not reset.";
        int cleared = clearEntities(realm, slot);
        abort(realm, slot);
        Build build = build(realm, slot);
        return "Cleared " + cleared + " entities; restoring " + build.tiles.size() + " sections of slot " + slot + ".";
    }

    /**
     * Ends a slot's fight without sending anyone home; watchers get their game mode back on the
     * floor. An offline watcher keeps the record, so rejoining sends them home with their game mode.
     */
    private static void abort(ServerWorld realm, int slot) {
        risings.remove(slot);
        endings.remove(slot);
        arrivals.remove(slot);
        Fight fight = state.fights.remove(String.valueOf(slot));
        if (fight == null) return;
        if (!fight.won && fight.site != null) GuardianChurchManager.naveFinished(fight.site);
        for (String id : fight.roster) {
            ServerPlayerEntity p = realm.getServer().getPlayerManager().getPlayer(UUID.fromString(id));
            if (p == null) continue;
            String mode = state.modes.remove(id);
            if (mode != null) {
                p.changeGameMode(GameMode.byId(mode, GameMode.SURVIVAL));
                arrive(p, realm, slot, 0);
            }
        }
        persist();
    }

    public static String status() {
        if (state == null) return "Shattered Nave storage is unavailable.";
        StringBuilder out = new StringBuilder("Shattered Nave: layout generation " + state.generation
                + (state.layout.equals(layout) ? "" : " (layout changed; slots rebuild on next entry)")
                + ", built slots " + state.built + ", " + state.returns.size() + " return point(s) held.");
        builds.values().forEach(b -> out.append(" Slot ").append(b.slot).append(" building ").append(b.next).append('/').append(b.tiles.size()).append('.'));
        state.fights.forEach((slot, f) -> out.append(" Slot ").append(slot).append(" fight: ").append(f.standing.size()).append('/')
                .append(f.roster.size()).append(" standing").append(f.fighting ? "" : ", Guardian waking").append(f.won ? ", won" : "")
                .append(f.site == null ? ", operator summon" : "").append('.'));
        return out.toString();
    }

    // ------------------------------------------------------------------ building

    private static boolean ready(int slot) {
        refreshGeneration();
        return state.built.contains(slot) && !builds.containsKey(slot);
    }

    /** A changed layout never overwrites an old one in place: it moves to untouched ground. */
    private static void refreshGeneration() {
        if (state.layout.equals(layout)) return;
        if (!state.layout.isEmpty()) state.generation++;
        state.layout = layout;
        state.built.clear();
        persist();
    }

    private static Build build(ServerWorld realm, int slot) {
        refreshGeneration();
        Build existing = builds.get(slot);
        if (existing != null) return existing;
        Build build = new Build(slot, ShatteredNave.centre(state.generation, slot));
        var templates = realm.getStructureTemplateManager();
        for (int i = 0; i < ShatteredNave.TILE_COUNT; i++)
            for (int j = 0; j < ShatteredNave.TILE_COUNT; j++) {
                Identifier id = ShatteredNave.tile(i, j);
                if (templates.getTemplate(id).isPresent()) {
                    build.tiles.add(id);
                    build.origins.add(ShatteredNave.tileOrigin(build.centre, i, j));
                }
            }
        state.built.remove(Integer.valueOf(slot));
        persist();
        builds.put(slot, build);
        return build;
    }

    private static void tick(MinecraftServer server) {
        ServerWorld realm = realm(server);
        if (realm == null || state == null) return;
        // One 48x48 section per tick lays out a fresh slot in about six seconds without a long stall.
        for (var it = builds.values().iterator(); it.hasNext(); ) {
            Build build = it.next();
            if (build.next < build.tiles.size()) {
                place(realm, build.tiles.get(build.next), build.origins.get(build.next));
                build.next++;
                continue;
            }
            it.remove();
            if (!state.built.contains(build.slot)) state.built.add(build.slot);
            persist();
            List<ServerPlayerEntity> arriving = new ArrayList<>();
            for (UUID id : build.waiting) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player != null && player.isAlive()) arriving.add(player);
            }
            arriveAll(arriving, realm, build.slot);
            arriving.forEach(p -> p.sendMessage(Text.literal("The Shattered Nave opens."), true));
            if (build.ritual) risings.put(build.slot, server.getTicks() + RISE_DELAY);
        }
        tickReturns(server, realm);
        tickFights(server, realm);
        for (ServerPlayerEntity player : realm.getPlayers()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
            BlockPos centre = ShatteredNave.nearestCentre(player.getEntityPos());
            if (!ShatteredNave.inPlay(centre, player.getEntityPos(), 0)) {
                Vec3d inside = ShatteredNave.clampToPlay(centre, player.getEntityPos());
                player.networkHandler.requestTeleport(inside.x, inside.y, inside.z, player.getYaw(), player.getPitch());
                player.setVelocity(Vec3d.ZERO);
                player.velocityModified = true;
                player.fallDistance = 0;
            }
        }
    }

    private static void place(ServerWorld realm, Identifier id, BlockPos origin) {
        StructureTemplate template = realm.getStructureTemplateManager().getTemplate(id).orElse(null);
        if (template == null) return;
        internalMutation = true;
        try {
            template.place(realm, origin, origin, new StructurePlacementData().setIgnoreEntities(true).setUpdateNeighbors(false),
                    realm.getRandom(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        } finally { internalMutation = false; }
    }

    private static int clearEntities(ServerWorld realm, int slot) {
        List<Entity> leftovers = realm.getOtherEntities(null, ShatteredNave.footprint(ShatteredNave.centre(state.generation, slot)),
                e -> !(e instanceof PlayerEntity));
        leftovers.forEach(Entity::discard);
        return leftovers.size();
    }

    private static int slotAt(BlockPos centre) {
        for (int slot = 0; slot < ShatteredNave.SLOTS; slot++)
            if (ShatteredNave.centre(state.generation, slot).equals(centre)) return slot;
        return -1;
    }

    private static void tell(Fight fight, MinecraftServer server, String message) {
        Integer slot = slotOf(fight);
        for (ServerPlayerEntity p : members(fight, slot == null ? -1 : slot, server, slot == null ? null : realm(server)))
            p.sendMessage(Text.literal(message), false);
    }

    // ------------------------------------------------------------------ storage

    private static ServerWorld realm(MinecraftServer server) { return server.getWorld(ShatteredNave.WORLD); }

    private static Point point(ServerPlayerEntity player) {
        Vec3d pos = player.getEntityPos();
        return new Point(player.getEntityWorld().getRegistryKey().getValue().toString(), pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
    }

    /** Identifies the installed layout so a changed builder output never mixes with an old slot. */
    private static String fingerprint(MinecraftServer server) {
        CRC32 crc = new CRC32();
        for (int i = 0; i < ShatteredNave.TILE_COUNT; i++)
            for (int j = 0; j < ShatteredNave.TILE_COUNT; j++) {
                var resource = server.getResourceManager().getResource(
                        Identifier.of(ElementalWandsMod.MOD_ID, "structure/shattered_nave/nave_" + i + "_" + j + ".nbt"));
                if (resource.isEmpty()) continue;
                try (var in = resource.get().getInputStream()) {
                    crc.update(i * 31 + j);
                    crc.update(in.readAllBytes());
                } catch (IOException e) { LOG.error("Could not read Shattered Nave tile {} {}", i, j, e); }
            }
        return Long.toHexString(crc.getValue());
    }

    private static void load(MinecraftServer server) {
        builds.clear(); arrivals.clear(); risings.clear(); endings.clear(); respawns.clear(); joins.clear(); kept.clear(); legacy.clear();
        layout = fingerprint(server);
        Path root = server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands");
        path = root.resolve("guardian-nave.json");
        try {
            state = Files.exists(path) ? JSON.fromJson(Files.readString(path), State.class) : new State();
            if (state == null || state.version != 1 || state.built == null || state.returns == null || state.layout == null
                    || state.modes == null || state.fights == null)
                throw new IOException("Invalid Shattered Nave record");
            if (!state.fights.isEmpty()) {
                // A restart never resumes a fight: everyone goes home on join, and the church offers its heart again.
                LOG.info("Cancelling {} interrupted Guardian fight(s)", state.fights.size());
                state.fights.clear();
            }
            retireSkyArena(root.resolve("guardian-arena.json"));
            persist();
        } catch (Exception e) {
            state = null;
            LOG.error("Shattered Nave storage needs attention; Guardian rituals are disabled", e);
        }
    }

    /**
     * The Guardian used to be fought on a floor raised into the sky. Players that arena still owed a
     * trip home (or a game mode) when the world was upgraded are sent home when they next join.
     */
    private static void retireSkyArena(Path old) throws IOException {
        if (!Files.exists(old)) return;
        JsonObject record = JSON.fromJson(Files.readString(old), JsonObject.class);
        if (record != null && record.has("returns")) for (var entry : record.getAsJsonObject("returns").entrySet()) {
            state.returns.putIfAbsent(entry.getKey(), JSON.fromJson(entry.getValue(), Point.class));
            legacy.add(entry.getKey());
        }
        if (record != null && record.has("gameModes")) for (var entry : record.getAsJsonObject("gameModes").entrySet())
            state.modes.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
        if (record != null && record.has("arena") && !record.get("arena").isJsonNull())
            LOG.warn("The retired sky arena was interrupted; its players are sent home when they join");
        Files.move(old, old.resolveSibling("guardian-arena.retired.json"), StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean persist() {
        if (state == null || path == null) return false;
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, JSON.toJson(state), StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOG.error("Could not save the Shattered Nave record", e);
            return false;
        }
    }
}
