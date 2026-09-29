package com.anton.elementalwands.crypt;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.arena.GuardianArenaJournal.Point;
import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.SpellBooks;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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

/**
 * The Hollow Crypt. Lays out the clearing in a slot, moves players in and out with journaled
 * return points and keeps fighters inside the clearing. A graveyard headstone seals the nearby
 * group into a fight: the fallen watch as spectators, a wipe makes the boss vanish and sends
 * everyone back with their belongings, and a victory fills the graveyard's reward chests.
 */
public final class HollowCryptManager {
    private static final Logger LOG = LoggerFactory.getLogger("elementalwands-crypt");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    static final class State {
        int version = 1;
        /** Bumped when the layout changes, which moves every slot to untouched ground. */
        int generation;
        String layout = "";
        List<Integer> built = new ArrayList<>();
        /** Where each player entered from; the crypt always sends them back here. */
        Map<String, Point> returns = new LinkedHashMap<>();
        /** Game modes to restore for players made spectators by a fight. */
        Map<String, String> modes = new LinkedHashMap<>();
        /** Fights in progress, by slot. A restart cancels them and sends everyone home. */
        Map<String, Fight> fights = new LinkedHashMap<>();
        /** Graveyards that have been won, by {@link #siteKey}. */
        Map<String, Site> sites = new LinkedHashMap<>();
    }

    static final class Fight {
        /** The graveyard whose headstone started it; null for an operator summon. */
        String site;
        /** Sealed when the fight begins. */
        List<String> roster = new ArrayList<>();
        List<String> standing = new ArrayList<>();
        /** A party fight wipes when nobody is left standing; an empty operator summon never does. */
        boolean party;
        String boss;
        boolean won;
    }

    static final class Site {
        List<Long> chests = new ArrayList<>();
        /** Everyone who has won here; each may claim one spell book from the chests. */
        List<String> victors = new ArrayList<>();
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

    private static Path path;
    private static State state;
    private static String layout = "";
    private static final Map<Integer, Build> builds = new HashMap<>();
    private static boolean internalTeleport;
    /** Server tick at which a ritual slot's boss rises, and at which an ended fight sends its players home. */
    private static final Map<Integer, Integer> risings = new HashMap<>(), endings = new HashMap<>();
    /** Players whose respawn the crypt handles (spectate or go home), and whose belongings it carries over. */
    private static final Set<UUID> respawns = new LinkedHashSet<>(), joins = new LinkedHashSet<>(), kept = new HashSet<>();
    private static final int RISE_DELAY = 40, VICTORY_DELAY = 200, WIPE_DELAY = 60, RITUAL_RADIUS = 16;

    private HollowCryptManager() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(HollowCryptManager::load);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> layout = fingerprint(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            state = null; path = null; builds.clear(); risings.clear(); endings.clear(); respawns.clear(); joins.clear(); kept.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(HollowCryptManager::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (state == null) return;
            if (entity instanceof NecromancerEntity boss) victory(boss);
            else if (entity instanceof ServerPlayerEntity player && fightOf(player) != null)
                eliminate(player, "You fell. You can watch the rest of the fight.");
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (alive || !kept.remove(oldPlayer.getUuid())) return;
            newPlayer.getInventory().clone(oldPlayer.getInventory());
            newPlayer.experienceLevel = oldPlayer.experienceLevel;
            newPlayer.totalExperience = oldPlayer.totalExperience;
            newPlayer.experienceProgress = oldPlayer.experienceProgress;
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // Wait a tick: the connection still points at the dead player at this moment.
            if (!alive && state != null && (rosterOf(newPlayer) != null || state.returns.containsKey(newPlayer.getUuidAsString())))
                respawns.add(newPlayer.getUuid());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (state != null && fightOf(handler.getPlayer()) != null) eliminate(handler.getPlayer(), null);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            if (state != null) joins.add(handler.getPlayer().getUuid());
        }));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || !(world instanceof ServerWorld server) || !(player instanceof ServerPlayerEntity caller)
                    || world.getRegistryKey() == HollowCryptRealm.WORLD || state == null) return ActionResult.PASS;
            claimBook(caller, server, hit.getBlockPos());
            BlockPos skull = headstone(world, hit.getBlockPos());
            if (skull == null) return ActionResult.PASS;
            caller.sendMessage(Text.literal(ritual(caller, skull)), true);
            return ActionResult.SUCCESS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, block, entity) -> !restricted(world, player));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Item item = player.getStackInHand(hand).getItem();
            return restricted(world, player) && (item instanceof BlockItem || item instanceof BucketItem || item instanceof FlintAndSteelItem
                    || item instanceof FireChargeItem || item instanceof BoneMealItem) ? ActionResult.FAIL : ActionResult.PASS;
        });
    }

    // ------------------------------------------------------------------ rules other systems ask

    public static boolean inRealm(Entity entity) { return entity.getEntityWorld().getRegistryKey() == HollowCryptRealm.WORLD; }

    private static boolean restricted(World world, PlayerEntity player) {
        return world.getRegistryKey() == HollowCryptRealm.WORLD && !player.isCreative();
    }

    /** Nothing drops in the crypt: the respawned player gets their inventory and experience back. */
    public static boolean keepsBelongings(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity) || !inRealm(player)) return false;
        kept.add(player.getUuid());
        return true;
    }

    /**
     * Every player teleport (spells, pearls, commands, portals, spectator jumps) stays inside the
     * clearing and never crosses into or out of the realm; only the crypt's own moves do that.
     * Fallen players watching a fight may roam the slot but not leave it.
     */
    public static boolean canTeleport(PlayerEntity player, ServerWorld destination, Vec3d target) {
        if (internalTeleport) return true;
        boolean from = inRealm(player), to = destination.getRegistryKey() == HollowCryptRealm.WORLD;
        if (state != null && state.modes.containsKey(player.getUuidAsString()) && from)
            return to && HollowCryptRealm.footprint(HollowCryptRealm.nearestCentre(player.getEntityPos())).contains(target);
        if (player.isCreative() || player.isSpectator()) return true;
        if (!from && !to) return true;
        return from && to && HollowCryptRealm.inPlay(HollowCryptRealm.nearestCentre(player.getEntityPos()), target, .4);
    }

    // ------------------------------------------------------------------ graveyard ritual

    /**
     * The graveyard's headstone altar, recognised by its blocks so natural, /place'd and rebuilt
     * copies all work in any rotation: a skeleton skull on two carved deepslate blocks, the lower
     * one flanked by more carving, on a polished deepslate plinth. Returns the skull, or null.
     */
    static BlockPos headstone(World world, BlockPos clicked) {
        BlockState state = world.getBlockState(clicked);
        if (!state.isOf(Blocks.CHISELED_DEEPSLATE) && !state.isOf(Blocks.SKELETON_SKULL) && !state.isOf(Blocks.POLISHED_DEEPSLATE)) return null;
        for (BlockPos skull : BlockPos.iterate(clicked.add(-1, 0, -1), clicked.add(1, 2, 1))) {
            if (!world.getBlockState(skull).isOf(Blocks.SKELETON_SKULL)) continue;
            BlockPos upper = skull.down(), lower = skull.down(2);
            if (world.getBlockState(upper).isOf(Blocks.CHISELED_DEEPSLATE) && world.getBlockState(lower).isOf(Blocks.CHISELED_DEEPSLATE)
                    && (carved(world, lower.east()) && carved(world, lower.west()) || carved(world, lower.north()) && carved(world, lower.south()))
                    && world.getBlockState(skull.down(3)).isOf(Blocks.POLISHED_DEEPSLATE)) return skull.toImmutable();
        }
        return null;
    }

    private static boolean carved(World world, BlockPos pos) { return world.getBlockState(pos).isOf(Blocks.CHISELED_DEEPSLATE); }

    /** The headstone seals every living player near it into a fight in a free slot; the boss rises shortly after. */
    static String ritual(ServerPlayerEntity caller, BlockPos headstone) {
        ServerWorld here = (ServerWorld) caller.getEntityWorld();
        ServerWorld realm = realm(here.getServer());
        if (realm == null || state == null) return "The grave is silent. (The crypt is unavailable; see the server log.)";
        refreshGeneration();
        int slot = freeSlot(realm);
        if (slot < 0) return "The crypt is full. Try again when a fight ends.";
        List<ServerPlayerEntity> group = here.getPlayers(p -> p.isAlive() && !p.isSpectator()
                && p.squaredDistanceTo(Vec3d.ofCenter(headstone)) <= RITUAL_RADIUS * RITUAL_RADIUS);
        Fight fight = new Fight();
        fight.site = siteKey(here, headstone);
        for (ServerPlayerEntity p : group) {
            state.returns.put(p.getUuidAsString(), point(p));
            if (!p.isCreative()) fight.roster.add(p.getUuidAsString());
        }
        fight.standing.addAll(fight.roster);
        fight.party = !fight.roster.isEmpty();
        state.fights.put(String.valueOf(slot), fight);
        if (!persist()) { state.fights.remove(String.valueOf(slot)); return "The grave is silent. (Could not record return points.)"; }
        here.playSound(null, headstone, SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 1.4f, .6f);
        here.playSound(null, headstone, SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 2f, .7f);
        here.spawnParticles(ParticleTypes.SOUL, headstone.getX() + .5, headstone.getY() + .5, headstone.getZ() + 1.5, 60, 1.5, .6, 1.5, .03);
        for (ServerPlayerEntity p : group) p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0, false, false));
        if (ready(slot)) {
            group.forEach(p -> arrive(p, realm, slot));
            risings.put(slot, here.getServer().getTicks() + RISE_DELAY);
        } else {
            Build build = build(realm, slot);
            build.ritual = true;
            group.forEach(p -> build.waiting.add(p.getUuid()));
        }
        return "The grave drags " + (group.size() == 1 ? "you" : group.size() + " of you") + " under...";
    }

    /** A slot with no fight, nothing building or pending, and nobody standing in it. */
    private static int freeSlot(ServerWorld realm) {
        for (int slot = 0; slot < HollowCryptRealm.SLOTS; slot++) {
            if (state.fights.containsKey(String.valueOf(slot)) || builds.containsKey(slot) || risings.containsKey(slot) || endings.containsKey(slot)) continue;
            var area = HollowCryptRealm.footprint(HollowCryptRealm.centre(state.generation, slot));
            if (realm.getPlayers(p -> !p.isSpectator() && area.contains(p.getEntityPos())).isEmpty()) return slot;
        }
        return -1;
    }

    private static String siteKey(ServerWorld world, BlockPos skull) {
        return world.getRegistryKey().getValue() + "|" + skull.getX() + "|" + skull.getY() + "|" + skull.getZ();
    }

    // ------------------------------------------------------------------ fights

    private static Integer slotOf(Fight fight) {
        for (var entry : state.fights.entrySet()) if (entry.getValue() == fight) return Integer.valueOf(entry.getKey());
        return null;
    }

    /** The fight a player is still standing in, if any. */
    private static Fight fightOf(PlayerEntity player) {
        for (Fight fight : state.fights.values()) if (fight.standing.contains(player.getUuidAsString())) return fight;
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
        persist();
        // The last to fall hears about the wipe instead.
        if (message != null && !fight.standing.isEmpty()) player.sendMessage(Text.literal(message), false);
    }

    private static void victory(NecromancerEntity boss) {
        for (var entry : state.fights.entrySet()) {
            Fight fight = entry.getValue();
            if (fight.won || !boss.getUuidAsString().equals(fight.boss)) continue;
            int slot = Integer.parseInt(entry.getKey());
            fight.won = true;
            MinecraftServer server = boss.getEntityWorld().getServer();
            String reward = fight.site == null ? "" : reward(server, fight);
            persist();
            endings.put(slot, server.getTicks() + VICTORY_DELAY);
            tell(fight, server, "The Hollow Necromancer is destroyed. The crypt releases you in 10 seconds." + reward);
            return;
        }
    }

    /** Victory at a graveyard: fill its chests the first time, and let every victor claim a book there. */
    private static String reward(MinecraftServer server, Fight fight) {
        String[] parts = fight.site.split("\\|");
        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(parts[0])));
        if (world == null) return "";
        BlockPos skull = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        Site site = state.sites.computeIfAbsent(fight.site, key -> new Site());
        boolean first = site.chests.isEmpty();
        if (first) HollowCryptRewards.place(world, skull).forEach(pos -> site.chests.add(pos.asLong()));
        for (String id : fight.roster) if (!site.victors.contains(id)) site.victors.add(id);
        if (site.chests.isEmpty()) return "";
        return first ? " Reward chests wait beside the open grave." : " Open the grave's chests to claim your spell book.";
    }

    private static void claimBook(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof ChestBlockEntity)) return;
        for (var entry : state.sites.entrySet()) {
            Site site = entry.getValue();
            if (!site.chests.contains(pos.asLong()) || !entry.getKey().startsWith(world.getRegistryKey().getValue() + "|")) continue;
            if (site.victors.contains(player.getUuidAsString()))
                SpellBooks.claim(player, "graveyard:" + entry.getKey(), "Your Hollow Crypt spell book is in your inventory. Right-click it for a free Basic or Technique spell.");
            return;
        }
    }

    /** Each tick: rise, drop the fallen, detect a wipe or a lost boss, and finish ended fights. */
    private static void tickFights(MinecraftServer server, ServerWorld realm) {
        int now = server.getTicks();
        risings.entrySet().removeIf(rise -> {
            if (now < rise.getValue()) return false;
            Fight fight = state.fights.get(String.valueOf(rise.getKey()));
            BlockPos centre = HollowCryptRealm.centre(state.generation, rise.getKey());
            NecromancerEntity boss = raise(realm, centre);
            if (fight != null && boss != null) {
                fight.boss = boss.getUuidAsString();
                persist();
                tell(fight, server, "The Hollow Necromancer rises from the grave.");
            }
            return true;
        });
        for (var entry : List.copyOf(state.fights.entrySet())) {
            int slot = Integer.parseInt(entry.getKey());
            Fight fight = entry.getValue();
            if (fight.boss == null || fight.won || endings.containsKey(slot)) continue;
            BlockPos centre = HollowCryptRealm.centre(state.generation, slot);
            var area = HollowCryptRealm.footprint(centre);
            for (String id : List.copyOf(fight.standing)) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(UUID.fromString(id));
                if (p == null || !p.isAlive() || p.getEntityWorld() != realm || !area.contains(p.getEntityPos()) || p.isSpectator()) {
                    fight.standing.remove(id);
                    persist();
                }
            }
            Entity boss = realm.getEntity(UUID.fromString(fight.boss));
            if (fight.party && fight.standing.isEmpty()) {
                // A wipe: the boss vanishes, the clearing resets, and everyone goes back with their belongings.
                clearEntities(realm, centre);
                endings.put(slot, now + WIPE_DELAY);
                tell(fight, server, "The crypt swallows the Hollow Necromancer. You are cast back to the graveyard.");
            } else if (boss == null || !boss.isAlive() || realm.getDifficulty() == Difficulty.PEACEFUL) {
                clearEntities(realm, centre);
                endings.put(slot, now + WIPE_DELAY);
                tell(fight, server, "The fight is over. The crypt releases you.");
            }
        }
        endings.entrySet().removeIf(ending -> {
            if (now < ending.getValue()) return false;
            finish(server, realm, ending.getKey());
            return true;
        });
    }

    /** Sends everyone in the fight home and retires it; a lost fight also restores the clearing. */
    private static void finish(MinecraftServer server, ServerWorld realm, int slot) {
        Fight fight = state.fights.get(String.valueOf(slot));
        if (fight == null) return;
        Set<ServerPlayerEntity> members = members(fight, slot, server, realm);
        state.fights.remove(String.valueOf(slot));
        persist();
        for (ServerPlayerEntity p : members)
            // Offline or still on the death screen: handled on join or respawn.
            if (p.isAlive() && p.networkHandler.player == p && inRealm(p)) leave(p);
        if (!fight.won) build(realm, slot);
    }

    /**
     * The sealed roster plus anyone else in the slot, such as Creative players the headstone
     * brought along or players who entered by command; a fight's end releases them all. Without a
     * realm, only the roster.
     */
    private static Set<ServerPlayerEntity> members(Fight fight, int slot, MinecraftServer server, ServerWorld realm) {
        Set<ServerPlayerEntity> members = new LinkedHashSet<>();
        for (String id : fight.roster) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(UUID.fromString(id));
            if (p != null) members.add(p);
        }
        if (realm != null) {
            var area = HollowCryptRealm.footprint(HollowCryptRealm.centre(state.generation, slot));
            // Only those the crypt brought in (it recorded where they came from), not an operator who /tp'd in.
            members.addAll(realm.getPlayers(p -> area.contains(p.getEntityPos()) && state.returns.containsKey(p.getUuidAsString())));
        }
        return members;
    }

    /** Fallen fighters watch from above the clearing until the fight ends. */
    private static void spectate(ServerPlayerEntity player, int slot, ServerWorld realm) {
        if (!state.modes.containsKey(player.getUuidAsString()) && !player.isSpectator()) {
            state.modes.put(player.getUuidAsString(), player.interactionManager.getGameMode().getId());
            persist();
        }
        player.changeGameMode(GameMode.SPECTATOR);
        player.setCameraEntity(player);
        BlockPos centre = HollowCryptRealm.centre(state.generation, slot);
        transfer(player, realm, new Vec3d(centre.getX() + .5, HollowCryptRealm.SURFACE_Y + 14, centre.getZ() + 26.5), 180, 25);
        player.sendMessage(Text.literal("You are watching the fight. You will return with your belongings when it ends."), false);
    }

    /**
     * Respawned players go back into their fight as spectators, or to where they entered. Rejoining
     * players who are still in the realm do the same; a rejoin elsewhere is left alone.
     */
    private static void tickReturns(MinecraftServer server, ServerWorld realm) {
        for (Set<UUID> queue : List.of(respawns, joins)) for (UUID id : List.copyOf(queue)) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null) { queue.remove(id); continue; }
            if (!p.isAlive() || p.networkHandler.player != p) continue;
            queue.remove(id);
            if (queue == joins && !inRealm(p)) continue;
            Fight fight = rosterOf(p);
            Integer slot = fight == null ? null : slotOf(fight);
            if (slot != null) { fight.standing.remove(p.getUuidAsString()); spectate(p, slot, realm); }
            else if (state.modes.containsKey(id.toString()) || !p.isCreative() && state.returns.containsKey(id.toString())) sendHome(p);
        }
        // Watchers stay above their own clearing.
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!state.modes.containsKey(p.getUuidAsString()) || !p.isAlive()) continue;
            Fight fight = rosterOf(p);
            Integer slot = fight == null ? null : slotOf(fight);
            if (slot == null) continue;
            var area = HollowCryptRealm.footprint(HollowCryptRealm.centre(state.generation, slot));
            if (p.getEntityWorld() != realm || !area.contains(p.getEntityPos())) spectate(p, slot, realm);
        }
    }

    // ------------------------------------------------------------------ moving players

    /** Back to the recorded entry point (or world spawn), restoring any game mode the crypt changed. */
    private static boolean sendHome(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        String id = player.getUuidAsString();
        Point point = state.returns.get(id);
        ServerWorld world = point == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(point.dimension())));
        if (world != null && world.getRegistryKey() != HollowCryptRealm.WORLD) {
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

    // ------------------------------------------------------------------ commands

    public static String enter(ServerPlayerEntity player, int slot) {
        ServerWorld realm = realm(player.getEntityWorld().getServer());
        if (realm == null) return "The Hollow Crypt dimension is not loaded.";
        if (state == null) return "Hollow Crypt storage is unavailable; see the server log.";
        if (!inRealm(player)) {
            state.returns.put(player.getUuidAsString(), point(player));
            if (!persist()) return "Could not record your return point, so you were not moved.";
        }
        if (ready(slot)) {
            arrive(player, realm, slot);
            return "Entered crypt slot " + slot + ". /ew crypt summon raises the boss; /ew crypt leave takes you back.";
        }
        Build build = build(realm, slot);
        build.waiting.add(player.getUuid());
        return "Raising crypt slot " + slot + " (" + build.tiles.size() + " sections); you will be taken in when it is ready.";
    }

    public static String leave(ServerPlayerEntity player) {
        if (!inRealm(player)) return "You are not in the Hollow Crypt.";
        if (state == null) return "Hollow Crypt storage is unavailable; see the server log.";
        Fight fight = rosterOf(player);
        if (fight != null) {
            fight.standing.remove(player.getUuidAsString());
            fight.roster.remove(player.getUuidAsString());
            persist();
        }
        return sendHome(player) ? "Returned to where you entered." : "No return point was recorded; sent to world spawn.";
    }

    /** Operator summon: the survival players already in the slot become the fight's sealed party. */
    public static String summon(ServerPlayerEntity player) {
        if (!inRealm(player)) return "Enter the crypt first with /ew crypt enter.";
        ServerWorld realm = (ServerWorld) player.getEntityWorld();
        BlockPos centre = HollowCryptRealm.nearestCentre(player.getEntityPos());
        int slot = slotAt(centre);
        if (slot < 0) return "This slot is from an older layout; /ew crypt enter a current one.";
        abort(realm, slot);
        NecromancerEntity boss = raise(realm, centre);
        if (boss == null) return "Could not create the Hollow Necromancer.";
        Fight fight = new Fight();
        fight.boss = boss.getUuidAsString();
        var area = HollowCryptRealm.footprint(centre);
        for (ServerPlayerEntity p : realm.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && area.contains(p.getEntityPos())))
            fight.roster.add(p.getUuidAsString());
        fight.standing.addAll(fight.roster);
        fight.party = !fight.roster.isEmpty();
        state.fights.put(String.valueOf(slot), fight);
        persist();
        return "The Hollow Necromancer rises" + (fight.roster.isEmpty() ? " (no survival players here, so it fights until reset)." : ".");
    }

    public static String reset(ServerPlayerEntity player) {
        if (!inRealm(player)) return "Stand in the crypt slot you want to reset.";
        ServerWorld realm = (ServerWorld) player.getEntityWorld();
        BlockPos centre = HollowCryptRealm.nearestCentre(player.getEntityPos());
        int slot = slotAt(centre);
        int cleared = clearEntities(realm, centre);
        if (slot < 0) return "Cleared " + cleared + " entities. This slot is from an older layout, so it was not rebuilt.";
        abort(realm, slot);
        Build build = build(realm, slot);
        return "Cleared " + cleared + " entities; restoring " + build.tiles.size() + " sections of slot " + slot + ".";
    }

    /** Ends a slot's fight without sending anyone home; watchers get their game mode back at the rim. */
    private static void abort(ServerWorld realm, int slot) {
        risings.remove(slot);
        endings.remove(slot);
        Fight fight = state.fights.remove(String.valueOf(slot));
        if (fight == null) return;
        for (String id : fight.roster) {
            String mode = state.modes.remove(id);
            ServerPlayerEntity p = realm.getServer().getPlayerManager().getPlayer(UUID.fromString(id));
            if (mode != null && p != null) {
                p.changeGameMode(GameMode.byId(mode, GameMode.SURVIVAL));
                arrive(p, realm, slot);
            }
        }
        persist();
    }

    public static String status() {
        if (state == null) return "Hollow Crypt storage is unavailable.";
        StringBuilder out = new StringBuilder("Hollow Crypt: layout generation " + state.generation
                + (state.layout.equals(layout) ? "" : " (layout changed; slots rebuild on next entry)")
                + ", built slots " + state.built + ", " + state.returns.size() + " return point(s) held, " + state.sites.size() + " graveyard(s) won.");
        builds.values().forEach(b -> out.append(" Slot ").append(b.slot).append(" building ").append(b.next).append('/').append(b.tiles.size()).append('.'));
        state.fights.forEach((slot, f) -> out.append(" Slot ").append(slot).append(" fight: ").append(f.standing.size()).append('/')
                .append(f.roster.size()).append(" standing").append(f.won ? ", won" : "").append(f.site == null ? ", operator summon" : "").append('.'));
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
        Build build = new Build(slot, HollowCryptRealm.centre(state.generation, slot));
        var templates = realm.getStructureTemplateManager();
        for (int i = 0; i < HollowCryptRealm.TILE_COUNT; i++)
            for (int j = 0; j < HollowCryptRealm.TILE_COUNT; j++) {
                Identifier id = HollowCryptRealm.tile(i, j);
                if (templates.getTemplate(id).isPresent()) {
                    build.tiles.add(id);
                    build.origins.add(HollowCryptRealm.tileOrigin(build.centre, i, j));
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
        // One 48x48 section per tick keeps a fresh slot to a couple of seconds without a long stall.
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
            for (UUID id : build.waiting) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player != null && player.isAlive()) {
                    arrive(player, realm, build.slot);
                    player.sendMessage(Text.literal("The Hollow Crypt opens."), true);
                }
            }
            if (build.ritual) risings.put(build.slot, server.getTicks() + RISE_DELAY);
        }
        tickReturns(server, realm);
        tickFights(server, realm);
        for (ServerPlayerEntity player : realm.getPlayers()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()) continue;
            BlockPos centre = HollowCryptRealm.nearestCentre(player.getEntityPos());
            if (!HollowCryptRealm.inPlay(centre, player.getEntityPos(), 0)) {
                Vec3d inside = HollowCryptRealm.clampToPlay(centre, player.getEntityPos());
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
        template.place(realm, origin, origin, new StructurePlacementData().setIgnoreEntities(true).setUpdateNeighbors(false),
                realm.getRandom(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }

    /**
     * Clears leftovers from any earlier fight and stands the boss on the circle; its intro
     * cinematic plays for everyone in the clearing, then the fight starts.
     */
    private static NecromancerEntity raise(ServerWorld realm, BlockPos centre) {
        clearEntities(realm, centre);
        NecromancerEntity boss = ModEntities.HOLLOW_NECROMANCER.create(realm, SpawnReason.COMMAND);
        if (boss == null) return null;
        Vec3d at = HollowCryptRealm.summonPoint(centre);
        boss.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        boss.setHeadYaw(0);
        realm.spawnEntity(boss);
        var area = HollowCryptRealm.footprint(centre);
        boss.beginIntro(realm.getPlayers(p -> p.isAlive() && !p.isSpectator() && area.contains(p.getEntityPos())));
        return boss;
    }

    private static int clearEntities(ServerWorld realm, BlockPos centre) {
        List<Entity> leftovers = realm.getOtherEntities(null, HollowCryptRealm.footprint(centre), e -> !(e instanceof PlayerEntity));
        leftovers.forEach(Entity::discard);
        return leftovers.size();
    }

    private static int slotAt(BlockPos centre) {
        for (int slot = 0; slot < HollowCryptRealm.SLOTS; slot++)
            if (HollowCryptRealm.centre(state.generation, slot).equals(centre)) return slot;
        return -1;
    }

    private static void tell(Fight fight, MinecraftServer server, String message) {
        Integer slot = slotOf(fight);
        for (ServerPlayerEntity p : members(fight, slot == null ? -1 : slot, server, slot == null ? null : realm(server)))
            p.sendMessage(Text.literal(message), false);
    }

    private static void arrive(ServerPlayerEntity player, ServerWorld realm, int slot) {
        transfer(player, realm, HollowCryptRealm.arrival(HollowCryptRealm.centre(state.generation, slot)), HollowCryptRealm.ARRIVAL_YAW, 0);
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

    // ------------------------------------------------------------------ storage

    private static ServerWorld realm(MinecraftServer server) { return server.getWorld(HollowCryptRealm.WORLD); }

    private static Point point(ServerPlayerEntity player) {
        Vec3d pos = player.getEntityPos();
        return new Point(player.getEntityWorld().getRegistryKey().getValue().toString(), pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
    }

    /** Identifies the installed layout so a changed builder output never mixes with an old slot. */
    private static String fingerprint(MinecraftServer server) {
        CRC32 crc = new CRC32();
        for (int i = 0; i < HollowCryptRealm.TILE_COUNT; i++)
            for (int j = 0; j < HollowCryptRealm.TILE_COUNT; j++) {
                var resource = server.getResourceManager().getResource(
                        Identifier.of(ElementalWandsMod.MOD_ID, "structure/hollow_crypt/realm_" + i + "_" + j + ".nbt"));
                if (resource.isEmpty()) continue;
                try (var in = resource.get().getInputStream()) {
                    crc.update(i * 31 + j);
                    crc.update(in.readAllBytes());
                } catch (IOException e) { LOG.error("Could not read Hollow Crypt tile {} {}", i, j, e); }
            }
        return Long.toHexString(crc.getValue());
    }

    private static void load(MinecraftServer server) {
        builds.clear(); risings.clear(); endings.clear(); respawns.clear(); joins.clear(); kept.clear();
        layout = fingerprint(server);
        path = server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands/hollow-crypt.json");
        try {
            state = Files.exists(path) ? JSON.fromJson(Files.readString(path), State.class) : new State();
            if (state == null || state.version != 1 || state.built == null || state.returns == null || state.layout == null)
                throw new IOException("Invalid Hollow Crypt record");
            // Records from before fights existed lack these maps.
            if (state.modes == null) state.modes = new LinkedHashMap<>();
            if (state.fights == null) state.fights = new LinkedHashMap<>();
            if (state.sites == null) state.sites = new LinkedHashMap<>();
            if (!state.fights.isEmpty()) {
                // A restart never resumes a fight: its slots are rebuilt and everyone goes home on join.
                LOG.info("Cancelling {} interrupted Hollow Crypt fight(s)", state.fights.size());
                for (String slot : state.fights.keySet()) state.built.remove(Integer.valueOf(slot));
                state.fights.clear();
                persist();
            }
        } catch (Exception e) {
            state = null;
            LOG.error("Hollow Crypt storage needs attention; crypt commands are disabled", e);
        }
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
            LOG.error("Could not save the Hollow Crypt record", e);
            return false;
        }
    }
}
