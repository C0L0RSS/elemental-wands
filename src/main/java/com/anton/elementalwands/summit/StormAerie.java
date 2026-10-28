package com.anton.elementalwands.summit;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.church.GuardianChurchLoot;
import com.anton.elementalwands.crypt.HollowCryptManager;
import com.anton.elementalwands.party.PartyManager;
import com.anton.elementalwands.registry.ModBlocks;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.util.SpellBooks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Storm Roc's way in: an abandoned Windcaller summoning gong on an Overworld peak. Striking it
 * twice calls the Roc for the striker's nearby party: its intro ({@link StormRocIntro}) snatches each
 * of them and carries them up to a free summit slot, and the fight starts once all have landed; its end
 * sends them back to the gong, and a win fills the aerie's reward chest, where each victor claims
 * one Ultimate spell book. Return points and who was called are saved with the summit, so a crash,
 * stop or relog on the summit still ends at the aerie.
 */
public final class StormAerie {
    private StormAerie() {}

    /**
     * Ticks: a second strike confirms within CONFIRM; a held right-click repeats faster than REPEAT and
     * never counts as one. Victors wait HOMECOMING on the summit after it falls.
     */
    private static final int CONFIRM = 100, REPEAT = 10, HOMECOMING = 200;
    /** Party members this close to the gong's anchor go with the striker. */
    private static final int RADIUS = 24;
    /** A part finds its anchor in its own plane, as far below it and to either side as the plate's cells reach. */
    private static final int PART_DOWN = Arrays.stream(StormAerieLayout.GONG_CELLS).mapToInt(c -> c[1]).max().orElse(0),
            PART_SIDE = Arrays.stream(StormAerieLayout.GONG_CELLS).mapToInt(c -> Math.abs(c[0])).max().orElse(0);
    private static final String BOOK_MESSAGE =
            "Your Storm Roc spell book is in your inventory. Right-click it for a free Basic, Technique or Ultimate spell.";

    /** A gong: its anchor block, and the side players strike it from. */
    public record Gong(BlockPos anchor, Direction facing) {
        /** The standing cell under the anchor, where {@link StormAerieLayout}'s offsets start. */
        public BlockPos foot() { return anchor.down(StormAerieLayout.GONG_UP); }
    }

    private static final class Ring {
        final String site;
        final int first;
        int last;
        Ring(String site, int now) { this.site = site; first = last = now; }
    }

    /** A party on its way: in the intro at the aerie, then on the summit until all have landed. */
    private static final class Call {
        final String site;
        final RegistryKey<World> world;
        final Gong gong;
        final int slot;
        final BlockPos centre;
        final int committed;
        final Set<UUID> members = new LinkedHashSet<>();
        /** Server tick the last of the party was carried up to the summit, or -1 while any is still at the aerie. */
        int arrived = -1;
        Call(String site, RegistryKey<World> world, Gong gong, int slot, BlockPos centre, int committed) {
            this.site = site; this.world = world; this.gong = gong; this.slot = slot; this.centre = centre; this.committed = committed;
        }
    }

    /** Victors waiting on an empty summit, by slot centre, until the tick they are sent home. */
    private record Homecoming(Set<UUID> players, int due) {}

    private static final Map<UUID, Ring> rings = new HashMap<>();
    private static final List<Call> calls = new ArrayList<>();
    private static final Map<BlockPos, Homecoming> homecomings = new HashMap<>();
    /**
     * Players the storm passed by because their client never covered its screen, until the tick a
     * call may take them again: otherwise withholding the cloud would chain the scene's protection.
     */
    private static final Map<UUID, Integer> passedBy = new HashMap<>();

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(StormAerie::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) drop(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            rings.remove(handler.getPlayer().getUuid());
            drop(handler.getPlayer());
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> cancelAll(server, true));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> cancelAll(server, false));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { rings.clear(); calls.clear(); homecomings.clear(); passedBy.clear(); });
        // Claim before the chest opens; the chest itself opens as usual.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand == Hand.MAIN_HAND && world instanceof ServerWorld server && player instanceof ServerPlayerEntity opener)
                claimBook(opener, server, hit.getBlockPos());
            return ActionResult.PASS;
        });
    }

    // ------------------------------------------------------------------ the gong

    /**
     * The gong a clicked block belongs to: the anchor itself, or for a part the nearest anchor in the
     * part's plane with the same facing, as far below it and to either side as the plate reaches
     * (three and two blocks). Null if the block is neither, or a part has lost its anchor.
     */
    public static Gong gong(World world, BlockPos clicked) {
        BlockState state = world.getBlockState(clicked);
        if (state.isOf(ModBlocks.STORM_GONG)) return new Gong(clicked.toImmutable(), state.get(StormGongBlock.FACING));
        if (!state.isOf(ModBlocks.STORM_GONG_PART)) return null;
        Direction facing = state.get(StormGongBlock.FACING), along = facing.rotateYClockwise();
        BlockPos anchor = null;
        for (int down = 0; down <= PART_DOWN; down++)
            for (int side = -PART_SIDE; side <= PART_SIDE; side++) {
                BlockPos pos = clicked.down(down).offset(along, side);
                BlockState there = world.getBlockState(pos);
                if (there.isOf(ModBlocks.STORM_GONG) && there.get(StormGongBlock.FACING) == facing
                        && (anchor == null || clicked.getSquaredDistance(pos) < clicked.getSquaredDistance(anchor))) anchor = pos.toImmutable();
            }
        return anchor == null ? null : new Gong(anchor, facing);
    }

    /** The gong whose anchor this is, or null if it is gone. */
    static Gong find(World world, BlockPos anchor) {
        BlockState state = world.getBlockState(anchor);
        return state.isOf(ModBlocks.STORM_GONG) ? new Gong(anchor.toImmutable(), state.get(StormGongBlock.FACING)) : null;
    }

    /** An aerie's identity: its world and its gong's anchor. Keys rewards and the fight it called. */
    public static String siteKey(World world, BlockPos anchor) {
        return world.getRegistryKey().getValue() + "|" + anchor.getX() + "|" + anchor.getY() + "|" + anchor.getZ();
    }

    /** A cell given in the gong's frame ({forward, right, up} from its foot, see {@link StormAerieLayout}). */
    public static BlockPos cell(Gong gong, int[] frame) {
        return gong.foot().offset(gong.facing(), frame[0]).offset(gong.facing().rotateYClockwise(), frame[1]).up(frame[2]);
    }

    /** Where a called player comes back to: the aerie's stand point, facing the gong. */
    static StormSummitManager.Point stand(World world, Gong gong) {
        Vec3d feet = Vec3d.ofBottomCenter(cell(gong, StormAerieLayout.STAND));
        Vec3d to = Vec3d.ofBottomCenter(gong.foot()).subtract(feet);
        // Minecraft yaw: 0 faces south (+z), 90 faces west (-x).
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        return new StormSummitManager.Point(world.getRegistryKey().getValue().toString(), feet.x, feet.y, feet.z, yaw, 0);
    }

    // ------------------------------------------------------------------ ringing

    /**
     * A player struck the gong the clicked block belongs to. The first strike names who would go; a
     * second by the same player at the same gong within five seconds calls the Storm Roc. Returns
     * the striker's reply, or null when nothing should be said (a held click, or not a gong).
     */
    public static String ring(ServerPlayerEntity ringer, BlockPos clicked) {
        ServerWorld world = ringer.getEntityWorld();
        Gong gong = gong(world, clicked);
        if (gong == null) return null;
        int now = world.getServer().getTicks();
        String site = siteKey(world, gong.anchor());
        Ring last = rings.get(ringer.getUuid());
        // A held right-click repeats every few ticks: it keeps the first ring alive but never confirms it.
        if (last != null && last.site.equals(site) && now - last.last < REPEAT) { last.last = now; return null; }
        String refusal = refusal(ringer, world);
        if (refusal != null) { rings.remove(ringer.getUuid()); return refusal; }
        List<ServerPlayerEntity> group = group(ringer, world, gong.anchor());
        if (last != null && last.site.equals(site) && now - last.first <= CONFIRM) {
            rings.remove(ringer.getUuid());
            return call(ringer, world, gong, site, group);
        }
        rings.put(ringer.getUuid(), new Ring(site, now));
        toll(world, gong);
        return "Strike the gong again within 5 seconds to call the Storm Roc for: " + names(group) + ".";
    }

    private static String refusal(ServerPlayerEntity ringer, ServerWorld world) {
        if (StormSummitManager.savedCalls() == null || world.getServer().getWorld(StormSummit.WORLD) == null)
            return "The gong is silent. (The Storm Summit is unavailable; see the server log.)";
        if (world.getRegistryKey() == StormSummit.WORLD) return "The gong is silent up here.";
        if (!ringer.isAlive() || ringer.isCreative() || ringer.isSpectator()) return "Only a Survival or Adventure player can call the Storm Roc.";
        if (StormRocEncounter.enrolled(ringer) || pending(ringer.getUuid())) return "The storm is already answering your call.";
        if (passedBy(ringer.getUuid(), world.getServer().getTicks())) return "The storm won't answer you again yet.";
        if (elsewhere(ringer)) return "Finish your current fight first.";
        return null;
    }

    /** Sealed into, or inside, another boss's realm. */
    private static boolean elsewhere(ServerPlayerEntity player) {
        return GuardianArenaManager.isParticipant(player) || GuardianArenaManager.inRealm(player) || HollowCryptManager.inRealm(player);
    }

    /**
     * Who goes: the striker, plus members of the striker's party in this world within 24 blocks of the
     * gong's anchor, alive in Survival or Adventure and not already held by a fight or another call.
     */
    public static List<ServerPlayerEntity> group(ServerPlayerEntity ringer, ServerWorld world, BlockPos anchor) {
        List<ServerPlayerEntity> group = new ArrayList<>(List.of(ringer));
        var party = PartyManager.get(world.getServer()).party(ringer.getUuid());
        if (party == null) return group;
        Vec3d at = Vec3d.ofCenter(anchor);
        for (UUID id : party.members().keySet()) {
            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(id);
            if (p == null || p == ringer || p.getEntityWorld() != world || p.squaredDistanceTo(at) > RADIUS * RADIUS) continue;
            if (p.isAlive() && !p.isCreative() && !p.isSpectator() && !StormRocEncounter.enrolled(p) && !pending(id) && !elsewhere(p)
                    && !passedBy(id, world.getServer().getTicks())) group.add(p);
        }
        return group;
    }

    private static boolean passedBy(UUID id, int now) {
        Integer until = passedBy.get(id);
        if (until != null && now >= until) passedBy.remove(id);
        return until != null && now < until;
    }

    private static String names(List<ServerPlayerEntity> group) {
        return String.join(", ", group.stream().map(p -> p.getName().getString()).toList());
    }

    /**
     * The confirmed strike: reserves a free slot (raising it if needed) and records every member's way
     * home before anyone moves. The call itself plays out in {@link #tick}.
     */
    private static String call(ServerPlayerEntity ringer, ServerWorld world, Gong gong, String site, List<ServerPlayerEntity> group) {
        MinecraftServer server = world.getServer();
        ServerWorld realm = server.getWorld(StormSummit.WORLD);
        int slot = freeSlot(realm);
        if (slot < 0) return "The Storm Roc is fighting elsewhere. Try again soon.";
        var returns = StormSummitManager.savedReturns();
        var called = StormSummitManager.savedCalls();
        StormSummitManager.Point home = stand(world, gong);
        for (ServerPlayerEntity p : group) {
            returns.put(p.getUuidAsString(), home);
            if (!called.contains(p.getUuidAsString())) called.add(p.getUuidAsString());
        }
        if (!StormSummitManager.persist()) {
            group.forEach(p -> { returns.remove(p.getUuidAsString()); called.remove(p.getUuidAsString()); });
            return "The gong is silent. (Could not record return points.)";
        }
        Call call = new Call(site, world.getRegistryKey(), gong, slot, StormSummitManager.slotCentre(slot), server.getTicks());
        group.forEach(p -> call.members.add(p.getUuid()));
        calls.add(call);
        StormSummitManager.raise(realm, slot);
        StormRocIntro.begin(world, gong, call.centre, group);
        for (ServerPlayerEntity p : group)
            if (p != ringer) p.sendMessage(Text.literal(ringer.getName().getString() + " struck the storm gong. The Storm Roc answers."), false);
        return "The storm gong calls the Storm Roc for: " + names(group) + ".";
    }

    /**
     * A slot with no fight, call or victors' wait, not being raised for someone else and with no
     * living player standing in it. Ready slots first; else one to raise. -1 if every slot is busy.
     */
    private static int freeSlot(ServerWorld realm) {
        int unbuilt = -1;
        for (int slot = 0; slot < StormSummit.SLOTS; slot++) {
            BlockPos centre = StormSummitManager.slotCentre(slot);
            if (StormRocEncounter.activeAt(centre) || homecomings.containsKey(centre) || StormSummitManager.building(slot)
                    || calls.stream().anyMatch(c -> c.centre.equals(centre))) continue;
            var area = StormSummit.footprint(centre);
            if (!realm.getPlayers(p -> p.isAlive() && !p.isSpectator() && area.contains(p.getEntityPos())).isEmpty()) continue;
            if (StormSummitManager.ready(slot)) return slot;
            if (unbuilt < 0) unbuilt = slot;
        }
        return unbuilt;
    }

    /** One deep stroke of the gong: its sound from the plate's centre, and its swing on every client near it. */
    private static void toll(ServerWorld world, Gong gong) {
        Vec3d at = Vec3d.ofCenter(gong.anchor()).add(0, 1.5, 0);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 4f, .5f);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 2f, .5f);
        if (world.getBlockEntity(gong.anchor()) instanceof StormGongBlockEntity anchor) anchor.strike();
    }

    // ------------------------------------------------------------------ calls and homecomings

    /** Held by a call that has not started its fight, or by a won fight's wait on the summit. */
    public static boolean pending(UUID id) {
        for (Call call : calls) if (call.members.contains(id)) return true;
        for (Homecoming home : homecomings.values()) if (home.players().contains(id)) return true;
        return false;
    }

    private static void tick(MinecraftServer server) {
        ServerWorld realm = server.getWorld(StormSummit.WORLD);
        if (realm == null || StormSummitManager.savedCalls() == null) return;
        int now = server.getTicks();
        for (Call call : List.copyOf(calls)) {
            if (call.arrived < 0) tickCarrying(server, realm, call, now);
            else tickWaiting(server, realm, call, now);
        }
        homecomings.entrySet().removeIf(entry -> {
            if (now < entry.getValue().due()) return false;
            for (UUID id : entry.getValue().players()) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p != null && p.isAlive() && onSlot(p, entry.getKey())) home(p);
            }
            return true;
        });
        StormRocIntro.hold(server);
        recover(server);
    }

    /**
     * The intro: drop whoever left, and carry each of the rest up once their client reports its
     * screen inside the storm cloud (or they skip), and their slot is ready; until then their scene
     * waits in the cloud.
     */
    private static void tickCarrying(MinecraftServer server, ServerWorld realm, Call call, int now) {
        ServerWorld world = server.getWorld(call.world);
        for (UUID id : List.copyOf(call.members)) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            boolean up = StormRocIntro.carried(id);
            if (p == null || !p.isAlive() || p.isCreative() || p.isSpectator() || (up ? !onSlot(p, call.centre) : p.getEntityWorld() != world)) {
                call.members.remove(id);
                if (p != null) StormRocIntro.end(p); else StormRocIntro.forget(id);
                if (!up) forget(id.toString());
            }
        }
        // Nobody left: the slot is free again.
        if (call.members.isEmpty() || world == null) { calls.remove(call); return; }
        if (!StormSummitManager.ready(call.slot)) {
            StormSummitManager.raise(realm, call.slot);
            return;
        }
        boolean late = now - call.committed >= StormRocIntro.CLOUD_LATE;
        for (UUID id : List.copyOf(call.members)) {
            if (StormRocIntro.carried(id)) continue;
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (StormRocIntro.readyToCarry(id)) StormRocIntro.carryUp(p, realm, call.centre);
            else if (late) {
                call.members.remove(id);
                StormRocIntro.end(p);
                forget(id.toString());
                passedBy.put(id, now + StormRocIntro.CLOUD_LATE);
                p.sendMessage(Text.literal("The storm passes you by. Strike the gong again in a little while."), false);
            }
        }
        if (call.members.isEmpty()) { calls.remove(call); return; }
        if (call.members.stream().allMatch(StormRocIntro::carried)) call.arrived = now;
    }

    /**
     * On the summit: those who died, left or disconnected drop out (a relog sends them home), and
     * once every scene has ended (each has landed or skipped; or a scene has run far too long) the
     * fight starts with exactly the called players still there.
     */
    private static void tickWaiting(MinecraftServer server, ServerWorld realm, Call call, int now) {
        for (UUID id : List.copyOf(call.members)) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p == null || !p.isAlive() || !onSlot(p, call.centre)) {
                call.members.remove(id);
                if (p != null) StormRocIntro.end(p); else StormRocIntro.forget(id);
            }
        }
        if (call.members.isEmpty()) { calls.remove(call); return; }
        if (!call.members.stream().allMatch(StormRocIntro::finished)) {
            if (now - call.committed < StormRocIntro.LENGTH + StormRocIntro.GRACE) return;
            // A scene that never reported back (a stuck or lost client) doesn't hold up the others,
            // and its player isn't dropped into the fight blind: they go back to the aerie.
            for (UUID id : List.copyOf(call.members)) {
                if (StormRocIntro.finished(id)) continue;
                call.members.remove(id);
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p != null) { StormRocIntro.end(p); home(p); }
            }
            if (call.members.isEmpty()) { calls.remove(call); return; }
        }
        calls.remove(call);
        List<ServerPlayerEntity> roster = new ArrayList<>();
        for (UUID id : call.members) roster.add(server.getPlayerManager().getPlayer(id));
        roster.forEach(StormRocIntro::end);
        String result = StormRocEncounter.start(server, call.centre, roster, call.site);
        boolean started = roster.stream().anyMatch(StormRocEncounter::enrolled);
        // Anyone the fight did not take, or everyone if it could not begin, goes back to the aerie;
        // a fight whose Roc failed to spawn has already sent its party home, and a second trip would find no return point.
        for (ServerPlayerEntity p : roster) {
            if (StormRocEncounter.enrolled(p) || !p.isAlive() || !onSlot(p, call.centre)) continue;
            p.sendMessage(Text.literal(started ? "The Storm Roc attacked without you; the wind sets you down at the aerie."
                    : "The storm passes. " + result), false);
            home(p);
        }
    }

    /** Victors of the fight at this slot wait safely on the empty summit, then go home. */
    static void homecoming(BlockPos centre, Collection<UUID> roster, int now) {
        homecomings.put(centre.toImmutable(), new Homecoming(new LinkedHashSet<>(roster), now + HOMECOMING));
    }

    /**
     * A called player no call, fight or homecoming holds any more goes back to the aerie: after a
     * crash, stop or relog on the summit, or on respawning elsewhere after dying up there (a wipe,
     * or a death before the Roc arrived), as the crypt sends its fallen back to the graveyard.
     */
    private static void recover(MinecraftServer server) {
        var called = StormSummitManager.savedCalls();
        if (called.isEmpty()) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!called.contains(p.getUuidAsString()) || !p.isAlive() || p.networkHandler.player != p
                    || pending(p.getUuid()) || StormRocEncounter.enrolled(p)) continue;
            home(p);
        }
    }

    /** A member who dies or disconnects drops out; one still at the aerie never went, so forgets their way home. */
    private static void drop(ServerPlayerEntity player) {
        boolean up = StormRocIntro.carried(player.getUuid());
        StormRocIntro.end(player);
        for (Call call : calls)
            if (call.members.remove(player.getUuid()) && !up) forget(player.getUuidAsString());
    }

    /**
     * A reload or stop cancels every call. Players still at the aerie forget their way home; those
     * on the summit go home now on a reload, or on their next login after a stop.
     */
    private static void cancelAll(MinecraftServer server, boolean reload) {
        if (StormSummitManager.savedCalls() != null) {
            for (Call call : calls) for (UUID id : call.members) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                boolean up = StormRocIntro.carried(id);
                if (p != null) StormRocIntro.end(p); else StormRocIntro.forget(id);
                if (!up) {
                    forget(id.toString());
                    if (p != null) p.sendMessage(Text.literal("The storm passes before it answers. Strike the gong again."), false);
                } else if (reload && p != null && p.isAlive() && onSlot(p, call.centre)) home(p);
            }
            if (reload) homecomings.forEach((centre, home) -> home.players().forEach(id -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p != null && p.isAlive() && onSlot(p, centre)) home(p);
            }));
        }
        calls.clear();
        homecomings.clear();
        rings.clear();
    }

    private static boolean onSlot(ServerPlayerEntity player, BlockPos centre) {
        return StormSummitManager.inRealm(player) && StormSummit.nearestCentre(player.getEntityPos()).equals(centre);
    }

    private static void home(ServerPlayerEntity player) {
        boolean aerie = StormSummitManager.sendHome(player);
        player.sendMessage(Text.literal(aerie ? "The wind sets you down at the aerie." : "No return point was recorded; sent to world spawn."), true);
    }

    private static void forget(String id) {
        var returns = StormSummitManager.savedReturns();
        var called = StormSummitManager.savedCalls();
        if (returns == null || !called.remove(id)) return;
        returns.remove(id);
        StormSummitManager.persist();
    }

    // ------------------------------------------------------------------ rewards

    /**
     * A victory at an aerie: places its reward chests, and every challenger, fallen ones
     * included, becomes a victor who may claim a book from them. Each chest is filled the first time
     * it stands; one later found broken or blocked is placed again, empty, so victors can still claim.
     * Returns the note for the victory message.
     */
    static String reward(MinecraftServer server, String key, Collection<UUID> roster) {
        var sites = StormSummitManager.savedSites();
        if (sites == null) return "";
        String[] parts = key.split("\\|");
        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(parts[0])));
        if (world == null) return "";
        BlockPos anchor = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        StormSummitManager.Site site = sites.computeIfAbsent(key, k -> new StormSummitManager.Site());
        boolean first = site.filled.isEmpty();
        Gong gong = find(world, anchor);
        if (gong != null) placeChests(world, gong, site);
        for (UUID id : roster) if (!site.victors.contains(id.toString())) site.victors.add(id.toString());
        StormSummitManager.persist();
        if (site.chests.stream().noneMatch(at -> world.getBlockState(BlockPos.fromLong(at)).isOf(Blocks.CHEST))) return "";
        return first && !site.filled.isEmpty() ? " Reward chests wait at the aerie." : " Open the aerie's chests to claim your spell book.";
    }

    /** Puts back any reward chest that is missing, each turned toward the gong's axis; fills each cell's first one only. */
    static void placeChests(ServerWorld world, Gong gong, StormSummitManager.Site site) {
        Direction right = gong.facing().rotateYClockwise();
        for (int i = 0; i < StormAerieLayout.CHESTS.length; i++) {
            int[] frame = StormAerieLayout.CHESTS[i];
            BlockPos pos = cell(gong, frame);
            long at = pos.asLong();
            if (!site.chests.contains(at)) site.chests.add(at);
            BlockState here = world.getBlockState(pos);
            // Still standing, or blocked for now: a later victory tries again.
            if (here.isOf(Blocks.CHEST) || !here.isAir() && !here.isReplaceable()) continue;
            Direction facing = frame[1] > 0 ? right.getOpposite() : frame[1] < 0 ? right : gong.facing();
            world.setBlockState(pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing));
            if (site.filled.contains(at) || !(world.getBlockEntity(pos) instanceof ChestBlockEntity chest)) continue;
            List<ItemStack> contents = contents(world, gong.anchor(), i == 0 ? -1 : 1);
            for (int slot = 0; slot < contents.size(); slot++) chest.setStack(slot, contents.get(slot));
            chest.markDirty();
            site.filled.add(at);
        }
    }

    /** Church treasure plus the Roc's leavings in empty slots: feathers, copper and perhaps a lightning rod. Seeded by the aerie. */
    static List<ItemStack> contents(ServerWorld world, BlockPos base, int side) {
        List<ItemStack> contents = new ArrayList<>(GuardianChurchLoot.roll(world, base, side));
        Random random = new Random(world.getSeed() ^ base.asLong() * 31 + side);
        List<ItemStack> leavings = new ArrayList<>(List.of(new ItemStack(Items.FEATHER, 3 + random.nextInt(6)),
                new ItemStack(Items.COPPER_INGOT, 2 + random.nextInt(5))));
        if (random.nextInt(3) == 0) leavings.add(new ItemStack(Items.LIGHTNING_ROD));
        for (ItemStack stack : leavings) {
            int at = random.nextInt(contents.size());
            for (int n = 0; n < contents.size() && !contents.get(at).isEmpty(); n++) at = (at + 1) % contents.size();
            if (contents.get(at).isEmpty()) contents.set(at, stack);
        }
        return contents;
    }

    /** The reward chests an aerie has placed, for checks and tests; empty before its first victory. */
    public static List<BlockPos> chests(String site) {
        var sites = StormSummitManager.savedSites();
        var record = sites == null ? null : sites.get(site);
        return record == null ? List.of() : record.chests.stream().map(BlockPos::fromLong).toList();
    }

    /**
     * Opening one of an aerie's reward chests gives a victor their Ultimate spell book, once per
     * aerie; a full inventory keeps it for later. Returns whether a book was handed over.
     */
    public static boolean claimBook(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        var sites = StormSummitManager.savedSites();
        if (sites == null || sites.isEmpty() || !(world.getBlockEntity(pos) instanceof ChestBlockEntity)) return false;
        String prefix = world.getRegistryKey().getValue() + "|";
        for (var entry : sites.entrySet()) {
            if (!entry.getKey().startsWith(prefix) || !entry.getValue().chests.contains(pos.asLong())) continue;
            return entry.getValue().victors.contains(player.getUuidAsString())
                    && SpellBooks.claim(player, ModItems.ULTIMATE_SPELL_BOOK, "aerie:" + entry.getKey(), BOOK_MESSAGE);
        }
        return false;
    }

    // ------------------------------------------------------------------ status

    static String status() {
        StringBuilder out = new StringBuilder();
        for (Call call : calls) out.append(" Aerie call to slot ").append(call.slot).append(" for ").append(call.members.size())
                .append(call.arrived < 0 ? " player(s), in the intro at the aerie." : " player(s), waiting on the summit.");
        homecomings.forEach((centre, home) -> out.append(" Victors at ").append(centre.getX()).append(" go home soon (")
                .append(home.players().size()).append(")."));
        var sites = StormSummitManager.savedSites();
        if (sites != null) out.append(" Aeries won: ").append(sites.size()).append('.');
        return out.toString();
    }
}
