package com.anton.elementalwands.end;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.arena.GuardianArenaJournal.Point;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.EndConfiguredFeatures;
import net.minecraft.world.gen.feature.EndSpikeFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The End's main island as the arena: laying it out, its protection, its way home and gateways, the
 * saved record ({@code elementalwands/end.json}) and moving players in and out. Who fights and when is
 * {@link EndEncounter}'s; who may come through the portal is {@link EndGate}'s.
 *
 * <p>Laying out first checks, one chunk column per tick, the whole volume it would change for block
 * entities (chests, shulker boxes, signs and the rest: signs of a player's build) and dragon eggs. A
 * block entity holds an automatic layout: nothing is changed, operators are told, and the portal stays
 * shut until an operator clears the island or forces the layout, which breaks each such block the way
 * a player would, so containers spill. Dragon eggs never hold it: they are lifted out first and set
 * down again beside the arrival. Then it clears what vanilla put on the island by code, one chunk
 * column per tick (only blocks that are not air: the terrain above the floor, the spikes with their
 * cages and the bedrock fountain; end crystals are discarded, never detonated), then stamps the
 * builder's tiles one per tick. It runs when the server starts if the arena is not laid out with the
 * installed tiles, and only while the boss is undefeated: after the win the island is the players'.
 */
public final class EndArenaManager {
    private EndArenaManager() {}

    private static final Logger LOG = LoggerFactory.getLogger("ElementalWands/End");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    static final class State {
        int version = 1;
        /** The installed tiles' fingerprint when the arena was last laid out completely; empty if never. */
        String layout = "";
        boolean defeated;
        /** The way home's basin is open at the floor's centre. */
        boolean wayHome;
        /** Gateways this mod opened, as x, y, z. */
        List<int[]> gateways = new ArrayList<>();
        /** Where each player came from: a standing spot beside the portal they used. */
        Map<String, Point> returns = new LinkedHashMap<>();
        /** Dragon eggs lifted out of the way of a layout and not yet set down again. */
        int eggs;
        /** The intro's dragon has died in this world: later intros play only the condensing. */
        boolean dragonDead;
    }

    /**
     * One laying out: the check, chunk column by chunk column; then (if nothing holds it) the eggs are
     * lifted and, when forced, block entities broken; then the clear, column by column, vanilla's old
     * gateways, and the tiles.
     */
    private static final class Job {
        final List<ChunkPos> scan = new ArrayList<>(), columns = new ArrayList<>();
        final List<Identifier> tiles = new ArrayList<>();
        final List<BlockPos> origins = new ArrayList<>();
        /** What the check found: block entities and dragon eggs in the volume the layout changes. */
        final List<BlockPos> found = new ArrayList<>(), eggs = new ArrayList<>();
        final String layout;
        final boolean force;
        int next;
        boolean decided, gatewaysCleared;
        Job(String layout, boolean force) { this.layout = layout; this.force = force; }
        int steps() { return scan.size() + columns.size() + tiles.size(); }
    }

    private static State state;
    private static Path path;
    private static String layout = "";
    private static Job job;
    private static boolean internalTeleport;
    /** Block entities that held the last automatic layout; empty when nothing holds it. */
    private static List<BlockPos> held = List.of();
    /** Records written since start; the fixture checks a portal attempt writes at most one. */
    static int writes;
    /** The client's copy of {@link #guarded()}, sent by the server, so its own protection agrees. */
    private static volatile boolean clientGuarded;
    private static final int HELD_LISTED = 12;
    static final String HELD = "Players' things stand on the island (chests, signs and the like), and laying out the arena would destroy them."
            + " An operator must clear them and run /ew end layout, or run /ew end layout force.";

    /** The server tells each client whether the arena is held, so the client refuses the same block uses (no flicker). */
    public record GuardPayload(boolean guarded) implements net.minecraft.network.packet.CustomPayload {
        public static final Id<GuardPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID, "end_guard"));
        public static final net.minecraft.network.codec.PacketCodec<net.minecraft.network.RegistryByteBuf, GuardPayload> CODEC =
                net.minecraft.network.codec.PacketCodec.tuple(net.minecraft.network.codec.PacketCodecs.BOOLEAN, GuardPayload::guarded, GuardPayload::new);
        @Override public Id<? extends net.minecraft.network.packet.CustomPayload> getId() { return ID; }
    }

    /** Keeps the arena loaded while it is laid out and while a fight runs. No SERIALIZE flag: a crash leaves no tickets. */
    static final net.minecraft.server.world.ChunkTicketType ARENA_TICKET = net.minecraft.registry.Registry.register(
            net.minecraft.registry.Registries.TICKET_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "end_arena"),
            new net.minecraft.server.world.ChunkTicketType(0,
                    net.minecraft.server.world.ChunkTicketType.FOR_LOADING | net.minecraft.server.world.ChunkTicketType.FOR_SIMULATION));
    static final int TICKET_RADIUS = 5 + net.minecraft.server.world.ChunkLevels.getLevelFromType(net.minecraft.server.world.ChunkLevelType.FULL)
            - net.minecraft.server.world.ChunkLevels.getLevelFromType(net.minecraft.server.world.ChunkLevelType.ENTITY_TICKING);

    public static void init() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(GuardPayload.ID, GuardPayload.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { load(server); layOutAtStart(server); });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> layout = fingerprint(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { state = null; path = null; job = null; ticketHeld = false; held = List.of(); });
        ServerTickEvents.END_SERVER_TICK.register(EndArenaManager::tick);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            sendGuard(player);
            if (!held.isEmpty() && operator(player)) player.sendMessage(Text.literal(heldNotice()), false);
        });
        // Not even a crack: mining the arena never starts for a Survival player.
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> protects(world, pos, player) ? ActionResult.FAIL : ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, block, entity) -> !protects(world, pos, player));
        // On both sides: a client that is refused here sends no item use either, so a bucket never pours.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            Item item = player.getStackInHand(hand).getItem();
            BlockPos at = hit.getBlockPos();
            boolean placing = item instanceof BlockItem || item instanceof BucketItem || item instanceof FlintAndSteelItem
                    || item instanceof FireChargeItem || item instanceof BoneMealItem;
            return (protects(world, at, player) || protects(world, at.offset(hit.getSide()), player))
                    && (placing || world.getBlockState(at).isOf(Blocks.DRAGON_EGG)) ? ActionResult.FAIL : ActionResult.PASS;
        });
        // A bucket used without aiming at a block (or by a client that skipped the block use) is checked by its own aim.
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!(player.getStackInHand(hand).getItem() instanceof BucketItem) || !guardedOn(world) || player.isCreative()) return ActionResult.PASS;
            if (protects(world, player.getBlockPos(), player)) return ActionResult.FAIL;
            if (player.raycast(player.getBlockInteractionRange(), 1, true) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                    && (protects(world, hit.getBlockPos(), player) || protects(world, hit.getBlockPos().offset(hit.getSide()), player)))
                return ActionResult.FAIL;
            return ActionResult.PASS;
        });
    }

    // ------------------------------------------------------------------ rules other systems ask

    /** The arena is held while the boss is undefeated; after the win the island is ordinary. */
    static boolean guarded() { return state != null && !state.defeated; }

    public static boolean defeated() { return state != null && state.defeated; }

    /** Spells and explosions destroy nothing in the arena's footprint while the boss is undefeated. */
    public static boolean keepsTerrain(World world, BlockPos pos) {
        return !world.isClient() && guarded() && EndArena.inFootprint(world, pos);
    }

    public static boolean keepsTerrain(World world, Vec3d pos) {
        return !world.isClient() && guarded() && EndArena.inFootprint(world, pos);
    }

    /** Whether the arena is held, as this side knows it: the server's record, or what it sent this client. */
    private static boolean guardedOn(World world) { return world.isClient() ? clientGuarded : guarded(); }

    /** Survival and Adventure players may not change the footprint while the boss is undefeated, on either side. */
    static boolean protects(World world, BlockPos pos, PlayerEntity player) {
        return guardedOn(world) && EndArena.inFootprint(world, pos) && !player.isCreative();
    }

    public static void setClientGuarded(boolean guarded) { clientGuarded = guarded; }

    /** What this JVM's client was last told; for fixtures on the integrated host. */
    public static boolean clientGuarded() { return clientGuarded; }

    private static void sendGuard(ServerPlayerEntity player) {
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, GuardPayload.ID))
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new GuardPayload(guarded()));
    }

    private static void sendGuardToAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) sendGuard(player);
    }

    static boolean operator(ServerPlayerEntity player) { return player.getPermissionLevel() >= 2; }

    /** Laying out is held by block entities; the reason the portal gives. */
    static boolean isHeld() { return !held.isEmpty(); }

    /** The block entities that held the last layout, for fixtures. */
    static List<BlockPos> heldBy() { return held; }

    private static String heldNotice() {
        return "[Elemental Wands] Laying out the End's arena is held by " + held.size() + " block entit" + (held.size() == 1 ? "y" : "ies")
                + " on the main island: " + listHeld() + ". Clear them and run /ew end layout, or run /ew end layout force"
                + " (it breaks them as a player would: containers drop their contents). /ew end status lists them.";
    }

    private static String listHeld() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < held.size() && i < HELD_LISTED; i++) {
            BlockPos p = held.get(i);
            out.append(i == 0 ? "" : "; ").append(p.getX()).append(", ").append(p.getY()).append(", ").append(p.getZ());
        }
        if (held.size() > HELD_LISTED) out.append("; and ").append(held.size() - HELD_LISTED).append(" more");
        return out.toString();
    }

    /** Someone was refused because the layout is held: every operator online hears where. */
    static void tellOperatorsHeld(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList())
            if (operator(player)) player.sendMessage(Text.literal(heldNotice()), false);
    }

    static boolean internalTeleport() { return internalTeleport; }

    // ------------------------------------------------------------------ the record

    static boolean available() { return state != null; }
    static Map<String, Point> returns() { return state == null ? null : state.returns; }

    static void setDefeated(MinecraftServer server, boolean defeated) {
        if (state == null) return;
        state.defeated = defeated;
        persist();
        sendGuardToAll(server);
    }

    /**
     * The Ender Dragon is dead in this world: the First Wizard's intro killed it, or vanilla's record says it was
     * killed before the takeover. The intro then plays only the condensing.
     */
    public static boolean dragonDead(MinecraftServer server) {
        if (state != null && state.dragonDead) return true;
        var vanilla = server.getSaveProperties().getDragonFight();
        return vanilla != null && vanilla.previouslyKilled();
    }

    static void setDragonDead(boolean dead) {
        if (state == null || state.dragonDead == dead) return;
        state.dragonDead = dead;
        persist();
    }

    /** Laid out with the installed tiles, nothing laying it out now, and no layout held by block entities (that shuts the portal). */
    public static boolean ready() {
        return state != null && job == null && held.isEmpty() && !state.layout.isEmpty() && (state.layout.equals(layout) || state.defeated);
    }

    public static boolean building() { return job != null; }

    /** Number of arena tiles the mod carries; zero means the layout is not installed. */
    static int installedTiles(ServerWorld end) {
        int count = 0;
        var templates = end.getStructureTemplateManager();
        for (int i = 0; i < EndArena.TILE_COUNT; i++)
            for (int j = 0; j < EndArena.TILE_COUNT; j++)
                if (templates.getTemplate(EndArena.tile(i, j)).isPresent()) count++;
        return count;
    }

    // ------------------------------------------------------------------ laying out

    /** The server started: an undefeated End not laid out with the installed tiles is laid out now, so the first entry need not wait. */
    private static void layOutAtStart(MinecraftServer server) {
        ServerWorld end = server.getWorld(EndArena.WORLD);
        if (end == null || state == null) return;
        if (state.defeated || ready()) { if (state.eggs > 0) setDownEggs(end); return; }
        String problem = raise(end);
        if (problem != null) LOG.warn("The End arena was not laid out at start: {}", problem);
    }

    /**
     * Starts laying the arena out unless it is ready, already being laid out, or held by block entities
     * (then only an operator's {@code /ew end layout} checks again). Returns a reason it cannot, or null.
     */
    static String raise(ServerWorld end) {
        if (ready() || job != null) return null;
        if (!held.isEmpty()) return HELD;
        return layOut(end, false);
    }

    /**
     * Lays the arena out again from scratch: checks the volume first and, unless {@code force}, stops
     * there if block entities stand in it. Returns a reason it cannot start, or null.
     */
    static String layOut(ServerWorld end, boolean force) {
        if (state == null) return "The End arena's storage is unavailable; see the server log.";
        if (installedTiles(end) == 0) return "The End arena's layout is not installed (no structure/end_island tiles).";
        if (job != null) return "The arena is already being laid out.";
        held = List.of();
        Job next = new Job(layout, force);
        int low = EndArena.TILE_ORIGIN, high = EndArena.TILE_ORIGIN + EndArena.TILE * EndArena.TILE_COUNT - 1;
        for (int cx = Math.floorDiv(EndArena.CENTRE.getX() + low, 16); cx <= Math.floorDiv(EndArena.CENTRE.getX() + high, 16); cx++)
            for (int cz = Math.floorDiv(EndArena.CENTRE.getZ() + low, 16); cz <= Math.floorDiv(EndArena.CENTRE.getZ() + high, 16); cz++)
                next.scan.add(new ChunkPos(cx, cz));
        int reach = (EndArena.CLEAR_RADIUS >> 4) + 1;
        for (int cx = -reach; cx <= reach; cx++)
            for (int cz = -reach; cz <= reach; cz++) {
                // A column the clear's circle touches at all.
                int nx = Math.max(cx * 16, Math.min(0, cx * 16 + 15)), nz = Math.max(cz * 16, Math.min(0, cz * 16 + 15));
                if ((long) nx * nx + (long) nz * nz <= (long) EndArena.CLEAR_RADIUS * EndArena.CLEAR_RADIUS) next.columns.add(new ChunkPos(cx, cz));
            }
        var templates = end.getStructureTemplateManager();
        for (int i = 0; i < EndArena.TILE_COUNT; i++)
            for (int j = 0; j < EndArena.TILE_COUNT; j++)
                if (templates.getTemplate(EndArena.tile(i, j)).isPresent()) {
                    next.tiles.add(EndArena.tile(i, j));
                    next.origins.add(EndArena.tileOrigin(i, j));
                }
        job = next;
        hold(end);
        LOG.info("Laying out the End arena{}: {} columns to check, {} to clear, {} tiles", force ? " (forced)" : "",
                next.scan.size(), next.columns.size(), next.tiles.size());
        return null;
    }

    // ---- the volume a layout changes, in blocks relative to the centre

    /** Inside the tiles' square, from the tiles' bottom up to the floor; and above the floor up to the clear's top within its radius. */
    private static boolean changes(int x, int y, int z) {
        int dx = x - EndArena.CENTRE.getX(), dz = z - EndArena.CENTRE.getZ();
        int low = EndArena.TILE_ORIGIN, high = EndArena.TILE_ORIGIN + EndArena.TILE * EndArena.TILE_COUNT - 1;
        if (dx < low || dx > high || dz < low || dz > high) return false;
        int bottom = EndArena.CENTRE.getY() + EndArena.BOTTOM;
        if (y < bottom) return false;
        return y <= EndArena.CENTRE.getY() || inClear(dx, dz) && y <= EndArena.CLEAR_TOP;
    }

    /** Dragon eggs are also taken from below the tiles, where the clear removes what vanilla's fight left. */
    private static boolean takesEgg(int x, int y, int z) {
        return changes(x, y, z) || inClear(x - EndArena.CENTRE.getX(), z - EndArena.CENTRE.getZ()) && y <= EndArena.CLEAR_TOP;
    }

    private static boolean inClear(int dx, int dz) { return (long) dx * dx + (long) dz * dz <= (long) EndArena.CLEAR_RADIUS * EndArena.CLEAR_RADIUS; }

    /** Vanilla's own: the fountain's portal, the way home's and the gateways are the layout's to change. */
    private static boolean players(BlockState here) {
        return here.hasBlockEntity() && !here.isOf(Blocks.END_PORTAL) && !here.isOf(Blocks.END_GATEWAY);
    }

    /** Finds block entities and dragon eggs in one chunk column of the volume. */
    private static void scanColumn(ServerWorld end, ChunkPos column, Job job) {
        var chunk = end.getChunk(column.x, column.z);
        for (BlockPos pos : chunk.getBlockEntityPositions())
            if (changes(pos.getX(), pos.getY(), pos.getZ()) && players(end.getBlockState(pos))) job.found.add(pos.toImmutable());
        var sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            var section = sections[i];
            if (section.isEmpty() || !section.hasAny(s -> s.isOf(Blocks.DRAGON_EGG))) continue;
            int baseY = net.minecraft.util.math.ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(i));
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                if (section.getBlockState(x, y, z).isOf(Blocks.DRAGON_EGG)) {
                    BlockPos pos = new BlockPos(column.getStartX() + x, baseY + y, column.getStartZ() + z);
                    if (takesEgg(pos.getX(), pos.getY(), pos.getZ())) job.eggs.add(pos);
                }
        }
    }

    /**
     * The check is done. Block entities hold an automatic layout; otherwise (or when forced) the
     * destructive part begins: forced block entities are broken as a player would break them, and the
     * eggs are lifted out of the way.
     */
    private static boolean decide(ServerWorld end, Job job) {
        if (!job.found.isEmpty() && !job.force) {
            held = List.copyOf(job.found);
            LOG.warn("Laying out the End arena is held by {} block entities: {}", held.size(), listHeld());
            tellOperatorsHeld(end.getServer());
            return false;
        }
        state.layout = "";
        persist();
        for (BlockPos pos : job.found)
            if (players(end.getBlockState(pos))) end.breakBlock(pos, true);
        if (!job.found.isEmpty()) LOG.info("Forced End layout broke {} block entities: {}", job.found.size(), job.found);
        int lifted = 0;
        for (BlockPos pos : job.eggs) if (end.getBlockState(pos).isOf(Blocks.DRAGON_EGG)) lifted++;
        if (lifted > 0) {
            // Saved before they go: a crash in between sets down one egg too many, never one too few.
            state.eggs += lifted;
            persist();
            for (BlockPos pos : job.eggs)
                if (end.getBlockState(pos).isOf(Blocks.DRAGON_EGG)) end.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            LOG.info("Lifted {} dragon egg(s) out of the End arena's way", lifted);
        }
        return true;
    }

    /** The eggs the layout lifted go down side by side just off the floor's south edge, beside the arrival. */
    private static void setDownEggs(ServerWorld end) {
        if (state.eggs <= 0) return;
        for (int i = 0; i < state.eggs; i++) {
            int x = EndArena.CENTRE.getX() + EndArena.EGG_SPOT[0] + i, z = EndArena.CENTRE.getZ() + EndArena.EGG_SPOT[1];
            end.getChunk(x >> 4, z >> 4);
            int y = end.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
            end.setBlockState(new BlockPos(x, y, z), Blocks.DRAGON_EGG.getDefaultState(), Block.NOTIFY_ALL);
        }
        LOG.info("Set down {} dragon egg(s) beside the End arena's arrival", state.eggs);
        state.eggs = 0;
        persist();
    }

    private static boolean ticketHeld;

    /** Keeps the arena's chunks loaded and ticking while it is laid out or a fight runs, and lets them go after. */
    static void hold(ServerWorld end) {
        boolean need = job != null || EndEncounter.running();
        if (need == ticketHeld) return;
        if (need) end.getChunkManager().addTicket(ARENA_TICKET, new ChunkPos(EndArena.CENTRE), TICKET_RADIUS);
        else end.getChunkManager().removeTicket(ARENA_TICKET, new ChunkPos(EndArena.CENTRE), TICKET_RADIUS);
        ticketHeld = need;
    }

    private static void tick(MinecraftServer server) {
        if (state == null || job == null) return;
        ServerWorld end = server.getWorld(EndArena.WORLD);
        if (end == null) { job = null; return; }
        if (job.next < job.scan.size()) {
            scanColumn(end, job.scan.get(job.next++), job);
            return;
        }
        if (!job.decided) {
            job.decided = true;
            if (!decide(end, job)) { job = null; hold(end); }
            return;
        }
        int column = job.next - job.scan.size();
        if (column < job.columns.size()) {
            clearColumn(end, job.columns.get(column));
            job.next++;
            return;
        }
        if (!job.gatewaysCleared) {
            job.gatewaysCleared = true;
            if (!state.defeated) clearVanillaGateways(end);
            return;
        }
        int tile = column - job.columns.size();
        if (tile < job.tiles.size()) {
            place(end, job.tiles.get(tile), job.origins.get(tile));
            job.next++;
            return;
        }
        if (state.defeated && state.wayHome) place(end, EndArena.WAY_HOME, EndArena.wayHomeOrigin());
        state.layout = job.layout;
        persist();
        setDownEggs(end);
        job = null;
        hold(end);
        LOG.info("The End arena is laid out");
    }

    /** Spike blocks below the floor become end stone where the island was solid round them, and air where it was not. */
    private static final Map<EndSpikeFeature.Spike, boolean[]> spikeFill = new HashMap<>();

    private static void clearColumn(ServerWorld end, ChunkPos column) {
        end.getChunk(column.x, column.z);
        List<EndSpikeFeature.Spike> spikes = EndSpikeFeature.getSpikes(end);
        int floor = EndArena.CENTRE.getY(), bottom = end.getBottomY(), top = Math.min(EndArena.CLEAR_TOP, end.getTopYInclusive());
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        long limit = (long) EndArena.CLEAR_RADIUS * EndArena.CLEAR_RADIUS;
        int eggs = 0;
        for (int x = column.getStartX(); x <= column.getEndX(); x++)
            for (int z = column.getStartZ(); z <= column.getEndZ(); z++) {
                int dx = x - EndArena.CENTRE.getX(), dz = z - EndArena.CENTRE.getZ();
                if ((long) dx * dx + (long) dz * dz > limit) continue;
                for (int y = floor + 1; y <= top; y++) {
                    pos.set(x, y, z);
                    BlockState here = end.getBlockState(pos);
                    if (here.isAir()) continue;
                    // Anything the check did not see (placed since): broken as a player would, never deleted outright.
                    if (players(here)) { end.breakBlock(pos, true); here = end.getBlockState(pos); if (here.isAir()) continue; }
                    if (here.isOf(Blocks.DRAGON_EGG)) eggs++;
                    end.setBlockState(pos, Blocks.AIR.getDefaultState(), flags);
                }
                EndSpikeFeature.Spike spike = spikeAt(spikes, x, z);
                for (int y = bottom; y <= floor; y++) {
                    pos.set(x, y, z);
                    BlockState here = end.getBlockState(pos);
                    BlockState replacement = leftover(end, here, spike, dx, dz, y);
                    if (replacement == null) continue;
                    if (here.isOf(Blocks.DRAGON_EGG)) eggs++;
                    end.setBlockState(pos, replacement, flags);
                }
            }
        if (eggs > 0) { state.eggs += eggs; persist(); }
        Box box = new Box(column.getStartX(), bottom, column.getStartZ(), column.getEndX() + 1, end.getTopYInclusive() + 1, column.getEndZ() + 1);
        end.getEntitiesByClass(EndCrystalEntity.class, box, e -> true).forEach(net.minecraft.entity.Entity::discard);
    }

    private static EndSpikeFeature.Spike spikeAt(List<EndSpikeFeature.Spike> spikes, int x, int z) {
        for (EndSpikeFeature.Spike spike : spikes) {
            long dx = x - spike.getCenterX(), dz = z - spike.getCenterZ();
            if (dx * dx + dz * dz <= (long) (spike.getRadius() * spike.getRadius() + 1)) return spike;
        }
        return null;
    }

    /** What a block below the floor that vanilla's fight or spikes left becomes, or null to keep it. */
    private static BlockState leftover(ServerWorld end, BlockState here, EndSpikeFeature.Spike spike, int dx, int dz, int y) {
        if (here.isAir()) return null;
        if (here.isOf(Blocks.IRON_BARS) || here.isOf(Blocks.FIRE) || here.isOf(Blocks.TORCH) || here.isOf(Blocks.WALL_TORCH)
                || here.isOf(Blocks.DRAGON_EGG)) return Blocks.AIR.getDefaultState();
        boolean fountain = dx * dx + dz * dz <= 64;
        if (fountain && (here.isOf(Blocks.BEDROCK) || here.isOf(Blocks.END_PORTAL) || here.isOf(Blocks.END_GATEWAY)))
            return Blocks.END_STONE.getDefaultState();
        if (spike != null && (here.isOf(Blocks.OBSIDIAN) || here.isOf(Blocks.BEDROCK)))
            return solidAround(end, spike, y) ? Blocks.END_STONE.getDefaultState() : Blocks.AIR.getDefaultState();
        return null;
    }

    /** Whether the island is solid just outside a spike at this height: two of its four sides are end stone. */
    private static boolean solidAround(ServerWorld end, EndSpikeFeature.Spike spike, int y) {
        int bottom = end.getBottomY();
        boolean[] fill = spikeFill.computeIfAbsent(spike, s -> {
            boolean[] out = new boolean[EndArena.CENTRE.getY() - bottom + 1];
            int r = s.getRadius() + 1;
            int[][] probes = {{r, 0}, {-r, 0}, {0, r}, {0, -r}};
            BlockPos.Mutable at = new BlockPos.Mutable();
            for (int i = 0; i < out.length; i++) {
                int solid = 0;
                for (int[] p : probes)
                    if (end.getBlockState(at.set(s.getCenterX() + p[0], bottom + i, s.getCenterZ() + p[1])).isOf(Blocks.END_STONE)) solid++;
                out[i] = solid >= 2;
            }
            return out;
        });
        int i = y - bottom;
        return i >= 0 && i < fill.length && fill[i];
    }

    /** Vanilla's gateways from an earlier dragon fight: the outer End opens only with this boss's win. */
    private static void clearVanillaGateways(ServerWorld end) {
        for (int i = 0; i < EndArena.GATEWAY_COUNT; i++) {
            BlockPos at = EndArena.gateway(i);
            end.getChunk(at.getX() >> 4, at.getZ() >> 4);
            if (end.getBlockState(at).isOf(Blocks.END_GATEWAY)) removeGateway(end, at);
        }
    }

    private static void removeGateway(ServerWorld end, BlockPos at) {
        end.getChunk(at.getX() >> 4, at.getZ() >> 4);
        for (BlockPos pos : BlockPos.iterate(at.add(-1, -2, -1), at.add(1, 2, 1))) {
            BlockState here = end.getBlockState(pos);
            if (here.isOf(Blocks.END_GATEWAY) || here.isOf(Blocks.BEDROCK))
                end.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
    }

    private static void place(ServerWorld end, Identifier id, BlockPos origin) {
        StructureTemplate template = end.getStructureTemplateManager().getTemplate(id).orElse(null);
        if (template == null) { LOG.warn("The End arena's structure {} is missing", id); return; }
        template.place(end, origin, origin, new StructurePlacementData().setIgnoreEntities(true).setUpdateNeighbors(false),
                end.getRandom(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }

    // ------------------------------------------------------------------ the win

    /** Opens the way home at the floor's centre and four of vanilla's gateways at the compass points. */
    static void openWayOut(ServerWorld end) {
        if (state == null) return;
        place(end, EndArena.WAY_HOME, EndArena.wayHomeOrigin());
        state.wayHome = true;
        var feature = end.getRegistryManager().getOptional(RegistryKeys.CONFIGURED_FEATURE)
                .flatMap(registry -> registry.getOptional(EndConfiguredFeatures.END_GATEWAY_DELAYED));
        for (int i : EndArena.WIN_GATEWAYS) {
            BlockPos at = EndArena.gateway(i);
            end.getChunk(at.getX() >> 4, at.getZ() >> 4);
            if (end.getBlockState(at).isOf(Blocks.END_GATEWAY)) continue;
            end.syncWorldEvent(3000, at, 0);
            feature.ifPresent(f -> f.value().generate(end, end.getChunkManager().getChunkGenerator(), Random.create(), at));
            if (end.getBlockState(at).isOf(Blocks.END_GATEWAY)) state.gateways.add(new int[]{at.getX(), at.getY(), at.getZ()});
            else LOG.warn("Could not open the End gateway at {}", at);
        }
        persist();
    }

    /** Closes the way home (the floor's centre tile is laid again) and the gateways this mod opened. */
    static void closeWayOut(ServerWorld end) {
        if (state == null) return;
        if (state.wayHome) {
            int[] t = EndArena.centreTile();
            place(end, EndArena.tile(t[0], t[1]), EndArena.tileOrigin(t[0], t[1]));
            state.wayHome = false;
        }
        for (int[] g : state.gateways) removeGateway(end, new BlockPos(g[0], g[1], g[2]));
        state.gateways.clear();
        persist();
    }

    // ------------------------------------------------------------------ moving players

    /** Records where a player will come back to, before they move. False if it could not be saved. */
    static boolean recordReturn(ServerPlayerEntity player, Point point) {
        if (state == null || point == null) return false;
        state.returns.put(player.getUuidAsString(), point);
        if (persist()) return true;
        state.returns.remove(player.getUuidAsString());
        return false;
    }

    static void forgetReturn(ServerPlayerEntity player) {
        if (state != null && state.returns.remove(player.getUuidAsString()) != null) persist();
    }

    static boolean hasReturn(ServerPlayerEntity player) { return state != null && state.returns.containsKey(player.getUuidAsString()); }

    /** To the arrival spot on the south rim, facing the centre. */
    static void arrive(ServerPlayerEntity player, ServerWorld end) {
        transfer(player, end, EndArena.arrival(), EndArena.ARRIVAL_YAW, 0);
    }

    /** Back to the recorded way back, or world spawn. True if a way back was recorded. */
    static boolean sendHome(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        String id = player.getUuidAsString();
        Point point = state == null ? null : state.returns.get(id);
        ServerWorld world = point == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(point.dimension())));
        boolean recorded = world != null && world.getRegistryKey() != EndArena.WORLD;
        if (recorded) transfer(player, world, new Vec3d(point.x(), point.y(), point.z()), point.yaw(), point.pitch());
        else toSpawn(player);
        // Save where they now stand before retiring the record: a crash in between repeats the trip home.
        server.getPlayerManager().saveAllPlayerData();
        if (state != null && state.returns.remove(id) != null) persist();
        return recorded;
    }

    /** To the top of world spawn: where anyone with no way back goes. */
    static void toSpawn(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        ServerWorld spawn = server.getSpawnWorld();
        BlockPos pos = server.getSpawnPoint().getPos();
        spawn.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        int y = spawn.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
        transfer(player, spawn, new Vec3d(pos.getX() + .5, y, pos.getZ() + .5), server.getSpawnPoint().yaw(), 0);
    }

    static void transfer(ServerPlayerEntity player, ServerWorld world, Vec3d pos, float yaw, float pitch) {
        player.stopRiding();
        internalTeleport = true;
        try { player.teleport(world, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, true); }
        finally { internalTeleport = false; }
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        player.velocityModified = true;
    }

    // ------------------------------------------------------------------ status and storage

    static String status() {
        if (state == null) return "End arena storage is unavailable.";
        StringBuilder out = new StringBuilder("End arena: ");
        if (job != null) out.append(job.decided ? "laying out" : "checking the island before laying out").append(job.force ? " (forced)" : "")
                .append(", step ").append(job.next).append('/').append(job.steps());
        else if (!held.isEmpty()) out.append("held: ").append(held.size()).append(" block entit").append(held.size() == 1 ? "y" : "ies")
                .append(" would be destroyed, at ").append(listHeld()).append(" (clear them and run /ew end layout, or /ew end layout force)");
        else if (state.layout.isEmpty()) out.append("not laid out yet");
        else out.append("laid out").append(state.layout.equals(layout) ? "" : state.defeated
                ? " (the installed layout changed; the island is kept since the win)" : " (the installed layout changed; it is laid out again on next entry)");
        if (state.eggs > 0) out.append("; ").append(state.eggs).append(" dragon egg(s) lifted, set down when the layout finishes");
        out.append(state.defeated ? "; the boss is defeated" : "; the boss is undefeated");
        out.append(state.dragonDead ? "; the intro's dragon is dead (intros play the condensing only)" : "");
        if (state.wayHome) out.append("; the way home is open");
        if (!state.gateways.isEmpty()) out.append("; ").append(state.gateways.size()).append(" gateway(s) open");
        out.append("; ").append(state.returns.size()).append(" way(s) back held.");
        return out.toString();
    }

    /** Identifies the installed arena tiles, so a changed builder output is laid out again. */
    private static String fingerprint(MinecraftServer server) {
        CRC32 crc = new CRC32();
        boolean any = false;
        for (int i = 0; i < EndArena.TILE_COUNT; i++)
            for (int j = 0; j < EndArena.TILE_COUNT; j++) {
                var resource = server.getResourceManager().getResource(
                        Identifier.of(ElementalWandsMod.MOD_ID, "structure/end_island/arena_" + i + "_" + j + ".nbt"));
                if (resource.isEmpty()) continue;
                try (var in = resource.get().getInputStream()) {
                    crc.update(i * 31 + j);
                    crc.update(in.readAllBytes());
                    any = true;
                } catch (IOException e) { LOG.error("Could not read End arena tile {} {}", i, j, e); }
            }
        return any ? Long.toHexString(crc.getValue()) : "none";
    }

    private static void load(MinecraftServer server) {
        job = null;
        spikeFill.clear();
        layout = fingerprint(server);
        path = server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands").resolve("end.json");
        try {
            state = Files.exists(path) ? JSON.fromJson(Files.readString(path), State.class) : new State();
            if (state == null || state.version != 1 || state.layout == null || state.returns == null)
                throw new IOException("Invalid End arena record");
            if (state.gateways == null) state.gateways = new ArrayList<>();
            persist();
        } catch (Exception e) {
            state = null;
            LOG.error("End arena storage needs attention; the End portal stays shut", e);
        }
    }

    static boolean persist() {
        if (state == null || path == null) return false;
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, JSON.toJson(state), StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            writes++;
            return true;
        } catch (IOException e) {
            LOG.error("Could not save the End arena record", e);
            return false;
        }
    }
}
