package com.anton.elementalwands.summit;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.entity.roc.StormRocEntity;
import com.anton.elementalwands.entity.roc.StormRocRules;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.party.PartyManager;
import com.anton.elementalwands.party.PartyStore;
import com.anton.elementalwands.registry.ModBlocks;
import com.anton.elementalwands.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.c2s.play.AcknowledgeChunksC2SPacket;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkSentS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import software.bernie.geckolib.network.packet.blockentity.BlockEntityAnimTriggerPacket;

/**
 * Test-only Fabric entrypoint, packaged solely by tools/aerie_server_smoke.init.gradle. Places the
 * real aerie template in four rotations on stone pads (it carries no ground of its own) and drives
 * the gong with simulated players: its anchor and parts in every rotation, the strike swing sent to
 * clients, strike timing, who goes, the intro (simulated clients report the cloud and their landing:
 * held at the gong, carried up, the fight once all have landed), the sealed roster, a loss by falls,
 * three wins at a rotated aerie with the reward chest and book claims, busy slots, a disconnect during
 * the intro, a death during it on the summit, crash recovery, a wipe by death, and clients that never
 * reach the cloud or never land.
 */
public final class StormAerieServerSmoke implements ModInitializer {
    private static final Path PASSED = Path.of("AERIE_SERVER_PASSED.txt"), FAILED = Path.of("AERIE_SERVER_FAILED.txt");
    private static final Identifier TEMPLATE = Identifier.of("elementalwands", "storm_aerie");
    private static final Map<UUID, List<String>> CHAT = new ConcurrentHashMap<>();
    /** GeckoLib block animation triggers each simulated player received: "x, y, z|controller|animation". */
    private static final Map<UUID, List<String>> SWINGS = new ConcurrentHashMap<>();
    /** Simulated players sent a chunk batch they have yet to acknowledge, as a client does next tick. */
    private static final Set<UUID> BATCHES = ConcurrentHashMap.newKeySet();
    /** The intro each simulated player was told to play: "start <slot centre>", or "end". */
    private static final Map<UUID, List<String>> INTROS = new ConcurrentHashMap<>();
    /** Simulated clients play the intro quickly: the cloud this long after it starts, the landing this long after the move up. */
    private static final int INTRO_CLOUD = 30, INTRO_LAND = 40;
    private static final String NAME_RINGER = "AerieRinger", NAME_NEAR = "AerieNear", NAME_FAR = "AerieFar",
            NAME_BYSTANDER = "AerieBystander", NAME_CREATIVE = "AerieCreative", NAME_SOLO = "AerieSolo";

    /** One placed template, and its gong as the production code finds it. */
    private static final class Site {
        final String name;
        final BlockRotation rotation;
        final BlockPos origin;
        final List<BlockPos> parts = new ArrayList<>();
        StormAerie.Gong gong;
        String key;
        Site(String name, BlockRotation rotation, BlockPos origin) { this.name = name; this.rotation = rotation; this.origin = origin; }
        BlockPos cell(int[] frame) { return StormAerie.cell(gong, frame); }
        Vec3d stand() { return Vec3d.ofBottomCenter(cell(StormAerieLayout.STAND)); }
        BlockPos chest(int i) { return cell(StormAerieLayout.CHESTS[i]); }
        List<BlockPos> chests() { List<BlockPos> out = new ArrayList<>(); for (int i = 0; i < StormAerieLayout.CHESTS.length; i++) out.add(chest(i)); return out; }
        /** The block a strike clicks: parts across the face, and every third strike the anchor itself. */
        BlockPos gongBlock(int i) { return i % 3 == 2 ? gong.anchor() : parts.get(Math.floorMod(i * 37, parts.size())); }
        /** The yaw of a player at the stand point looking straight at the gong. */
        float standYaw() { Direction d = gong.facing().getOpposite(); return (float) Math.toDegrees(Math.atan2(-d.getOffsetX(), d.getOffsetZ())); }
    }

    private interface Body { boolean run(int t) throws Exception; }
    private record Step(String name, int timeout, Body body) {}

    private final List<Step> steps = new ArrayList<>();
    private final List<String> passed = new ArrayList<>();
    /** Checks that failed without stopping the run, each with its step. */
    private final List<String> failures = new ArrayList<>();
    private int tick, index, begun, swingMark;
    private boolean finished;
    private MinecraftServer server;
    private ServerWorld overworld, realm;
    private Site a, b;
    private final List<Site> sites = new ArrayList<>();
    private UUID ringer, near, far, bystander, creative, solo;
    private final List<UUID> everyone = new ArrayList<>(), squatters = new ArrayList<>();
    private final Map<UUID, Vec3d> pins = new LinkedHashMap<>();
    /** When each simulated client's intro started and its player was carried up. */
    private final Map<UUID, Integer> introAt = new LinkedHashMap<>(), carriedAt = new LinkedHashMap<>();
    /** Simulated clients that never report the cloud, or never land. */
    private final Set<UUID> lostInCloud = new HashSet<>(), neverLand = new HashSet<>();
    /** Scenario scratch state. */
    private BlockPos centre;
    private int mark, endedAt;
    private Vec3d farAt, bystanderAt, soloAt;
    private List<List<String>> snapshot;

    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (finished) return;
            try { tick(server); }
            catch (Throwable error) {
                finished = true;
                error.printStackTrace();
                String where = index < steps.size() ? steps.get(index).name() : "setup";
                String report = "step=" + where + " t=" + (tick - begun) + "\n" + error + "\npassed before failure:\n" + String.join("\n", passed) + "\n";
                System.out.println("AERIE SMOKE FAILED: " + report);
                try { Files.writeString(FAILED, report); } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }

    private void tick(MinecraftServer server) throws Exception {
        if (++tick > 16000) throw new AssertionError("Aerie fixture timed out");
        if (tick < 25) return;
        if (tick == 25) { setup(server); begun = tick + 1; return; }
        // Simulated clients: confirm every teleport at once, take and acknowledge the chunks streamed to
        // them (until then the server does not count them as tracking those chunks, so block animation
        // triggers there never reach them), stay healthy, and stand where pinned.
        for (UUID id : everyone) {
            ServerPlayerEntity p = player(id);
            if (p == null) continue;
            if (p.isInTeleportationState()) p.onTeleportationDone();
            p.networkHandler.chunkDataSender.sendChunkBatches(p);
            if (BATCHES.remove(id)) p.networkHandler.onAcknowledgeChunks(new AcknowledgeChunksC2SPacket(64));
            if (p.isAlive()) p.setHealth(p.getMaxHealth());
            Vec3d pin = pins.get(id);
            if (pin != null && p.isAlive() && StormSummitManager.inRealm(p) && !p.isSpectator()) { p.setPosition(pin); p.setVelocity(Vec3d.ZERO); }
            // Each plays the intro its own way: into the cloud, then (once carried up) landed.
            if (p == null || !StormRocIntro.watching(p)) { introAt.remove(id); carriedAt.remove(id); continue; }
            int start = introAt.computeIfAbsent(id, k -> tick);
            if (tick - start == INTRO_CLOUD && !lostInCloud.contains(id)) StormRocIntro.inCloud(p);
            if (!StormRocIntro.carried(id)) continue;
            int up = carriedAt.computeIfAbsent(id, k -> tick);
            if (tick - up == INTRO_LAND && !neverLand.contains(id)) StormRocIntro.landed(p);
        }
        Step step = steps.get(index);
        int t = tick - begun;
        if (t > step.timeout()) throw new AssertionError("Step did not finish within " + step.timeout() + " ticks");
        if (!step.body().run(t)) return;
        boolean clean = failures.stream().noneMatch(f -> f.startsWith(step.name() + ": "));
        passed.add((clean ? "" : "[SOFT FAILURE] ") + step.name());
        System.out.println("AERIE SMOKE STEP " + (clean ? "PASSED" : "FAILED") + " (" + passed.size() + "): " + step.name() + " at t=" + t);
        begun = tick + 1;
        if (++index < steps.size()) return;
        finished = true;
        if (failures.isEmpty()) {
            Files.writeString(PASSED, "Storm aerie dedicated-server checks passed (" + passed.size() + "):\n" + String.join("\n", passed) + "\n");
            System.out.println("AERIE SMOKE PASSED");
        } else {
            Files.writeString(FAILED, "Storm aerie checks ran to the end with " + failures.size() + " failure(s):\n" + String.join("\n", failures)
                    + "\n\nSteps:\n" + String.join("\n", passed) + "\n");
            System.out.println("AERIE SMOKE FAILED: " + failures);
        }
        server.stop(false);
    }

    // ------------------------------------------------------------------ setup

    private void setup(MinecraftServer server) throws Exception {
        this.server = server;
        overworld = server.getOverworld();
        realm = server.getWorld(StormSummit.WORLD);
        require(realm != null, "Storm Summit dimension missing");
        require(StormSummitManager.savedCalls() != null, "Storm Summit storage did not load");
        StructureTemplate template = overworld.getStructureTemplateManager().getTemplate(TEMPLATE)
                .orElseThrow(() -> new AssertionError("Aerie template missing"));
        a = new Site("A/none", BlockRotation.NONE, new BlockPos(1000, 100, 1000));
        b = new Site("B/clockwise_90", BlockRotation.CLOCKWISE_90, new BlockPos(1200, 100, 1000));
        sites.addAll(List.of(a, b, new Site("C/clockwise_180", BlockRotation.CLOCKWISE_180, new BlockPos(1400, 100, 1000)),
                new Site("D/counterclockwise_90", BlockRotation.COUNTERCLOCKWISE_90, new BlockPos(1600, 100, 1000))));
        for (Site site : sites) {
            for (int x = (site.origin.getX() - 64) >> 4; x <= (site.origin.getX() + 64) >> 4; x++)
                for (int z = (site.origin.getZ() - 64) >> 4; z <= (site.origin.getZ() + 64) >> 4; z++) {
                    overworld.getChunk(x, z);
                    overworld.setChunkForced(x, z, true);
                }
            StructurePlacementData data = new StructurePlacementData().setRotation(site.rotation).setIgnoreEntities(true);
            // The levelled summit worldgen would give it: a stone floor under the whole box.
            BlockBox box = template.calculateBoundingBox(data, site.origin);
            int floor = site.origin.getY() + StormAerieLayout.FLOOR;
            for (BlockPos pos : BlockPos.iterate(box.getMinX(), floor - 1, box.getMinZ(), box.getMaxX(), floor, box.getMaxZ()))
                overworld.setBlockState(pos, Blocks.STONE.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            require(template.place(overworld, site.origin, site.origin, data, overworld.getRandom(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE),
                    "Template did not place at " + site.name);
        }
        Vec3d aStandGuess = null;
        for (Site site : sites) {
            geometry(template, site);
            if (site == a) aStandGuess = site.stand();
        }
        passed.add("Template placed on a stone floor in NONE, CLOCKWISE_90, CLOCKWISE_180 and COUNTERCLOCKWISE_90: one anchor at the transformed GONG with the rotated facing and a gong block entity; "
                + (StormAerieLayout.GONG_CELLS.length - 1) + " parts in its plane facing the same way, each at its GONG_CELLS offset (edge cells two columns out included); the anchor and every part resolve to it, while a part in front of the plane or turned another way does not; "
                + "every block's outline and collision are the template-rotated plate's stepped outline, pixel by pixel, 3 px thick through the middle, and no gong block stands where the plate is not; "
                + "STAND in front of the plate within reach and the chest cell clear over the floor; the return point faces the gong");

        BlockPos base = a.gong.foot();
        ringer = create(overworld, NAME_RINGER, aStandGuess);
        near = create(overworld, NAME_NEAR, Vec3d.ofBottomCenter(base.add(0, 0, -20)));
        far = create(overworld, NAME_FAR, Vec3d.ofBottomCenter(base.add(0, 0, -40)));
        bystander = create(overworld, NAME_BYSTANDER, Vec3d.ofBottomCenter(base.add(4, 0, 6)));
        creative = create(overworld, NAME_CREATIVE, Vec3d.ofBottomCenter(base.add(-3, 0, 6)));
        solo = create(overworld, NAME_SOLO, Vec3d.ofBottomCenter(base.add(2, 0, 8)));
        player(creative).changeGameMode(GameMode.CREATIVE);
        farAt = player(far).getEntityPos();
        soloAt = player(solo).getEntityPos();
        bystanderAt = player(bystander).getEntityPos();
        // A party of the ringer, a member 20 blocks from the gong and one 40 blocks away.
        Field storeField = PartyManager.class.getDeclaredField("store");
        storeField.setAccessible(true);
        PartyStore store = (PartyStore) storeField.get(PartyManager.get(server));
        Map<UUID, String> members = new LinkedHashMap<>();
        members.put(ringer, NAME_RINGER); members.put(near, NAME_NEAR); members.put(far, NAME_FAR);
        store.replace(null, new PartyStore.Party(UUID.randomUUID(), ringer, members));
        require(PartyManager.get(server).allied(ringer, far) && !PartyManager.get(server).allied(ringer, bystander), "Fixture party was not recorded");
        script();
    }

    private void geometry(StructureTemplate template, Site site) {
        StructurePlacementData data = new StructurePlacementData().setRotation(site.rotation);
        int[] cell = StormAerieLayout.GONG;
        BlockPos expected = site.origin.add(StructureTemplate.transform(data, new BlockPos(cell[0], cell[1], cell[2])));
        Direction facing = site.rotation.rotate(Direction.SOUTH), along = facing.rotateYClockwise(), left = facing.rotateYCounterclockwise();
        List<BlockPos> anchors = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(site.origin.add(-30, -2, -30), site.origin.add(30, StormAerieLayout.SIZE_Y + 2, 30))) {
            BlockState state = overworld.getBlockState(pos);
            if (state.isOf(ModBlocks.STORM_GONG)) anchors.add(pos.toImmutable());
            else if (state.isOf(ModBlocks.STORM_GONG_PART)) site.parts.add(pos.toImmutable());
        }
        require(anchors.equals(List.of(expected)), site.name + ": anchors at " + anchors + ", expected one at " + expected);
        require(overworld.getBlockState(expected).get(StormGongBlock.FACING) == facing, site.name + ": anchor faces " + overworld.getBlockState(expected) + ", expected " + facing);
        require(overworld.getBlockEntity(expected) instanceof StormGongBlockEntity, site.name + ": the anchor has no gong block entity: " + overworld.getBlockEntity(expected));
        int cells = StormAerieLayout.GONG_CELLS.length;
        require(site.parts.size() == cells - 1, site.name + " gong has " + site.parts.size() + " parts, expected " + (cells - 1));
        for (BlockPos pos : site.parts) {
            BlockPos d = pos.subtract(expected);
            BlockState state = overworld.getBlockState(pos);
            int forward = d.getX() * facing.getOffsetX() + d.getZ() * facing.getOffsetZ(), side = d.getX() * left.getOffsetX() + d.getZ() * left.getOffsetZ();
            int[] at = StormAerieLayout.GONG_CELLS[state.get(StormGongBlock.CELL)];
            require(forward == 0 && side == at[0] && d.getY() == at[1] && state.get(StormGongBlock.CELL) > 0 && state.get(StormGongBlock.FACING) == facing,
                    site.name + ": part " + pos + " (" + state + ") is not at its cell's place on the plate in the anchor's plane");
            StormAerie.Gong gong = StormAerie.gong(overworld, pos);
            require(gong != null && gong.anchor().equals(expected) && gong.facing() == facing, site.name + ": part " + pos + " resolved to " + gong + ", expected anchor " + expected + " facing " + facing);
        }
        site.gong = StormAerie.gong(overworld, expected);
        require(site.gong != null && site.gong.anchor().equals(expected) && site.gong.facing() == facing, site.name + ": the anchor resolved to " + site.gong);
        require(StormAerie.gong(overworld, expected.down()) == null, site.name + ": a non-gong block resolved to a gong");
        if (site == a) {
            // A part in front of the gong's plane, then one in it but turned, belongs to no gong.
            BlockPos stray = expected.offset(facing).up();
            require(overworld.getBlockState(stray).isAir(), site.name + ": stray test cell is taken: " + overworld.getBlockState(stray));
            overworld.setBlockState(stray, ModBlocks.STORM_GONG_PART.getDefaultState().with(StormGongBlock.FACING, facing));
            require(StormAerie.gong(overworld, stray) == null, site.name + ": a part in front of the plate's plane resolved to its gong");
            overworld.setBlockState(stray, ModBlocks.STORM_GONG_PART.getDefaultState().with(StormGongBlock.FACING, along));
            require(StormAerie.gong(overworld, stray) == null, site.name + ": a part facing " + along + " resolved to a gong facing " + facing);
            overworld.setBlockState(stray, Blocks.AIR.getDefaultState());
        }
        plateShapes(site, data, expected, left);
        site.key = StormAerie.siteKey(overworld, site.gong.anchor());
        require(site.key.equals("minecraft:overworld|" + expected.getX() + "|" + expected.getY() + "|" + expected.getZ()), site.name + " site key " + site.key);
        int standY = site.origin.getY() + StormAerieLayout.FLOOR + 1;
        require(site.gong.foot().getY() == standY && expected.getY() == standY + StormAerieLayout.GONG_UP, site.name + ": anchor at y " + expected.getY() + ", standing level " + standY);
        BlockPos stand = site.cell(StormAerieLayout.STAND);
        require(stand.getY() == standY, site.name + ": STAND at y " + stand.getY() + ", floor+1 is " + standY);
        BlockPos toStand = stand.subtract(expected);
        require(toStand.getX() * facing.getOffsetX() + toStand.getZ() * facing.getOffsetZ() > 0, site.name + ": STAND " + stand + " is not on the struck side of the plate");
        Vec3d eye = Vec3d.ofBottomCenter(stand).add(0, 1.62, 0);
        double reach = site.parts.stream().mapToDouble(pos -> Math.sqrt(new Box(pos).squaredMagnitude(eye))).min().orElse(99);
        require(reach <= 4.5, site.name + ": the gong is " + reach + " blocks from a player at STAND");
        clearOverPaving(site, stand, "STAND");
        for (int i = 0; i < StormAerieLayout.CHESTS.length; i++) {
            BlockPos chest = site.chest(i);
            require(chest.getY() == standY, site.name + ": chest " + i + " at y " + chest.getY());
            require(!chest.equals(stand), site.name + ": chest " + i + " is on the stand point");
            clearOverPaving(site, chest, "chest " + i);
        }
        StormSummitManager.Point home = StormAerie.stand(overworld, site.gong);
        require(new Vec3d(home.x(), home.y(), home.z()).distanceTo(site.stand()) < 1e-6 && Math.abs(wrap(home.yaw() - site.standYaw())) < .5,
                site.name + ": return point " + home + " is not STAND facing the gong (" + site.standYaw() + ")");
        System.out.println("AERIE GEOMETRY " + site.name + ": origin=" + site.origin + " anchor=" + expected + " facing=" + facing
                + " stand=" + stand + " chests=" + site.chests() + " parts=" + site.parts.size() + " reach=" + String.format("%.2f", reach));
    }

    /**
     * The click and collision shapes are the plate the renderer draws, in every rotation. The plate
     * is laid out in the template's own (south-facing) frame, where the gong's left is +x: each
     * GONG_CELLS row spans u0..u1 pixels from its cell's low-x side. Every pixel centre of every
     * cell, mid-thickness and just outside the 3 px plate, is carried through the template's
     * rotation into the world, and must be inside the block's outline exactly when it is plate.
     */
    private void plateShapes(Site site, StructurePlacementData data, BlockPos anchor, Direction left) {
        int[] g = StormAerieLayout.GONG;
        int checked = 0;
        for (int c = 0; c < StormAerieLayout.GONG_CELLS.length; c++) {
            int[] cell = StormAerieLayout.GONG_CELLS[c];
            BlockPos pos = anchor.offset(left, cell[0]).up(cell[1]);
            BlockState state = overworld.getBlockState(pos);
            require(c == 0 ? state.isOf(ModBlocks.STORM_GONG) : state.isOf(ModBlocks.STORM_GONG_PART) && state.get(StormGongBlock.CELL) == c,
                    site.name + ": cell " + c + " at " + pos + " holds " + state);
            var outline = state.getOutlineShape(overworld, pos);
            require(outline.getBoundingBoxes().equals(state.getCollisionShape(overworld, pos).getBoundingBoxes()), site.name + ": cell " + c + " collision differs from its outline");
            for (int u = 0; u < 16; u++) for (int v = 0; v < 16; v++) for (double depth : new double[]{.5, 6 / 16.0, 10 / 16.0}) {
                int row = v / 4;
                boolean plate = depth == .5 && cell[2 + 2 * row] <= u && u < cell[3 + 2 * row];
                Vec3d local = new Vec3d(g[0] + cell[0] + (u + .5) / 16, g[1] + cell[1] + (v + .5) / 16, g[2] + depth);
                Vec3d world = Vec3d.of(site.origin).add(StructureTemplate.transformAround(local, net.minecraft.util.BlockMirror.NONE, data.getRotation(), BlockPos.ORIGIN));
                BlockPos at = BlockPos.ofFloored(world);
                require(at.equals(pos), site.name + ": cell " + c + " pixel " + u + "," + v + " lands in " + at + ", not " + pos);
                Vec3d inBlock = world.subtract(Vec3d.of(pos));
                boolean inside = outline.getBoundingBoxes().stream().anyMatch(b -> b.contains(inBlock));
                require(inside == plate, site.name + ": cell " + c + " pixel " + u + "," + v + " depth " + depth + " is " + (inside ? "" : "not ")
                        + "in the shape, but is " + (plate ? "" : "not ") + "plate; boxes " + outline.getBoundingBoxes());
                checked++;
            }
        }
        // No gong block where the plate is not: the rest of its five-by-four cells stay empty.
        for (int side = -2; side <= 2; side++) for (int up = 0; up < 4; up++) {
            BlockPos pos = anchor.offset(left, side).up(up);
            int s = side, u = up;
            boolean cell = java.util.Arrays.stream(StormAerieLayout.GONG_CELLS).anyMatch(c -> c[0] == s && c[1] == u);
            require(cell || overworld.getBlockState(pos).isAir(), site.name + ": " + pos + " off the plate holds " + overworld.getBlockState(pos));
        }
        System.out.println("AERIE PLATE SHAPES " + site.name + ": " + checked + " points match");
    }

    private void clearOverPaving(Site site, BlockPos pos, String what) {
        require(overworld.getBlockState(pos).isAir() && overworld.getBlockState(pos.up()).isAir(), site.name + ": " + what + " cell " + pos + " is not clear: "
                + overworld.getBlockState(pos) + " / " + overworld.getBlockState(pos.up()));
        require(overworld.getBlockState(pos.down()).isSolidBlock(overworld, pos.down()), site.name + ": " + what + " cell " + pos + " has no paving: " + overworld.getBlockState(pos.down()));
    }

    // ------------------------------------------------------------------ the script

    private void script() {
        step("In every rotation a first strike on a part (the real block path) warns and names only the striker, swings that gong for the striker, and records nothing", 170, t -> {
            int i = t / 40, phase = t % 40;
            if (i == sites.size()) { go(solo, overworld, soloAt, 0); return true; }
            Site site = sites.get(i);
            if (phase == 0) go(solo, overworld, site.stand(), site.standYaw());
            if (phase == 30) {
                int before = swings(solo, site);
                BlockPos part = site.gongBlock(i * 3);
                require(overworld.getBlockState(part).isOf(ModBlocks.STORM_GONG_PART), site.name + ": strike target " + part + " is not a part");
                ActionResult result = player(solo).interactionManager.interactBlock(player(solo), overworld, ItemStack.EMPTY, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(part), site.gong.facing(), part, false));
                require(result.isAccepted(), site.name + ": striking a part was not accepted: " + result);
                String said = lastChat(solo);
                require(said.equals("Strike the gong again within 5 seconds to call the Storm Roc for: " + NAME_SOLO + "."), site.name + ": first strike said: " + said);
                require(swings(solo, site) == before + 1, site.name + ": the striker saw " + (swings(solo, site) - before) + " swings of " + site.gong.anchor() + "; received " + SWINGS.get(solo));
                notCalled(solo, "after a first strike at " + site.name);
            }
            return false;
        });
        step("A creative ringer is refused and never called", 30, t -> {
            if (t == 0) require(ring(creative, a, 0).startsWith("Only a Survival or Adventure player"), "Creative first ring was not refused");
            if (t < 12) return false;
            require(ring(creative, a, 1).startsWith("Only a Survival or Adventure player"), "Creative second ring was not refused");
            require(!StormAerie.pending(creative) && !called(creative) && !returns(creative), "Creative ringer was recorded for a call");
            return true;
        });
        step("First strike (on a part) only warns (names ringer + near member) and swings the gong for both; a strike 9 ticks later is swallowed; a second strike (on the anchor) 110 ticks after the first only warns again; a strike 10 ticks after a swallowed one (19 after the new first) commits", 200, t -> {
            if (t == 0) {
                // The real block path: right-clicking one of the gong's parts with an empty hand.
                swingMark = swings(ringer, a);
                int nearBefore = swings(near, a);
                BlockPos block = a.gongBlock(0);
                require(overworld.getBlockState(block).isOf(ModBlocks.STORM_GONG_PART), "First strike target is not a part");
                ActionResult result = player(ringer).interactionManager.interactBlock(player(ringer), overworld, ItemStack.EMPTY, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(block), a.gong.facing(), block, false));
                require(result.isAccepted(), "Right-clicking the storm gong was not accepted: " + result);
                String said = lastChat(ringer);
                require(said.startsWith("Strike the gong again within 5 seconds") && said.contains(NAME_RINGER) && said.contains(NAME_NEAR)
                        && !said.contains(NAME_FAR) && !said.contains(NAME_BYSTANDER), "First strike did not warn with the right names: " + said);
                require(swings(ringer, a) == swingMark + 1, "The first strike did not swing the gong for the striker: " + SWINGS.get(ringer));
                require(swings(near, a) == nearBefore + 1, "The near member, 20 blocks off, did not see the gong swing: " + SWINGS.get(near));
                notCalled(ringer, "after the first strike");
            }
            if (t == 9) {
                require(ring(ringer, a, 1) == null, "A strike 9 ticks after the first was not swallowed");
                require(swings(ringer, a) == swingMark + 1, "A swallowed strike swung the gong");
                notCalled(ringer, "after a 9-tick strike");
            }
            if (t == 110) {
                require(a.gongBlock(2).equals(a.gong.anchor()), "Third strike target is not the anchor");
                String r = ring(ringer, a, 2);
                require(r != null && r.startsWith("Strike the gong again"), "A strike 110 ticks after the first did not warn again: " + r);
                require(swings(ringer, a) == swingMark + 2, "The repeated first strike did not swing the gong");
                notCalled(ringer, "after a late strike");
            }
            if (t == 119) { require(ring(ringer, a, 3) == null, "A strike 9 ticks after the new first strike was not swallowed"); notCalled(ringer, "after a held strike"); }
            if (t < 129) return false;
            String r = ring(ringer, a, 4);
            require(r != null && r.startsWith("The storm gong calls the Storm Roc for:") && r.contains(NAME_RINGER) && r.contains(NAME_NEAR)
                    && !r.contains(NAME_FAR) && !r.contains(NAME_BYSTANDER), "Second strike 10-100 ticks after the first did not commit: " + r);
            require(alternating(ringer, a), "Consecutive strikes at A repeated a swing the client would not restart: " + swingsAt(ringer, a));
            swingMark = swings(ringer, a);
            return true;
        });
        step("The commit records the ringer and the near member only, both returning to A's STAND facing the gong; a repeat strike is refused", 5, t -> {
            for (UUID id : List.of(ringer, near)) {
                require(StormAerie.pending(id) && called(id), name(id) + " was not held by the call");
                returnPoint(id, a);
            }
            for (UUID id : List.of(far, bystander, creative, solo))
                require(!StormAerie.pending(id) && !called(id) && !returns(id), name(id) + " was taken by the call");
            require(lastChat(near).contains(NAME_RINGER + " struck the storm gong"), "Near member was not told: " + lastChat(near));
            String again = ring(ringer, a, 5);
            require(again != null && again.startsWith("The storm is already answering"), "A pending ringer could strike again: " + again);
            Object call = calls().getFirst();
            centre = (BlockPos) field(call, "centre");
            require(calls().size() == 1 && field(call, "slot").equals(0) && centre.equals(StormSummit.centre(0, 0)), "Unexpected call " + StormSummitManager.status());
            for (UUID id : List.of(ringer, near)) {
                ServerPlayerEntity p = player(id);
                require(StormRocIntro.watching(p) && INTROS.get(id).equals(List.of("start " + centre.toShortString())), name(id) + " was not told to play the intro for this slot: " + INTROS.get(id));
                require(p.getEntityWorld() == overworld && p.getEntityPos().distanceTo(a.stand()) < .01 && Math.abs(wrap(p.getYaw() - a.standYaw())) < .5,
                        name(id) + " was not set at A's STAND facing the gong for the intro: " + p.getEntityPos() + " yaw " + p.getYaw());
            }
            for (UUID id : List.of(far, bystander, creative, solo)) require(INTROS.get(id).isEmpty(), name(id) + " was told to play the intro");
            return true;
        });
        step("The intro holds the pair at A's STAND, unhurt and unmoving, until each client reports the cloud; then both land on the slot's arrival facing where the scene ends, the gong unstruck; the far member and the bystander stay in the Overworld", 200, t -> {
            if (!StormSummitManager.inRealm(player(ringer))) {
                for (UUID id : List.of(ringer, near))
                    require(player(id).getEntityWorld() == overworld && player(id).getEntityPos().distanceTo(a.stand()) < .01, name(id) + " left A's STAND before the cloud: " + player(id).getEntityPos());
                if (t == 5) {
                    require(!player(ringer).damage(overworld, overworld.getDamageSources().generic(), 4), "A player in the intro was hurt");
                    require(player(bystander).damage(overworld, overworld.getDamageSources().generic(), 4), "The bystander could not be hurt either, so the check proves nothing");
                    require(AttackEntityCallback.EVENT.invoker().interact(player(ringer), overworld, Hand.MAIN_HAND, player(bystander), null) == ActionResult.FAIL
                            && UseItemCallback.EVENT.invoker().interact(player(ringer), overworld, Hand.MAIN_HAND) == ActionResult.FAIL, "A player in the intro could attack or use an item");
                    require(AttackEntityCallback.EVENT.invoker().interact(player(bystander), overworld, Hand.MAIN_HAND, player(ringer), null) == ActionResult.PASS, "The bystander could not attack either, so the check proves nothing");
                    player(near).setPosition(a.stand().add(3, 0, 0));
                }
                if (t == 8) require(player(near).getEntityPos().distanceTo(a.stand()) < .01, "A player in the intro was not held at A's STAND: " + player(near).getEntityPos());
                return false;
            }
            mark = tick;
            require(t >= INTRO_CLOUD - 3, "Carried up " + t + " ticks into the intro, before the client reported the cloud");
            require(swings(ringer, a) == swingMark, "The intro struck the gong " + (swings(ringer, a) - swingMark) + " more times");
            StormRocIntroTrack track = StormRocIntroTrack.get();
            for (UUID id : List.of(ringer, near)) {
                ServerPlayerEntity p = player(id);
                require(StormSummitManager.inRealm(p) && p.getEntityPos().distanceTo(StormSummit.arrival(centre)) < .01, name(id) + " did not land at the arrival: " + p.getEntityPos());
                require(Math.abs(wrap(p.getYaw() - track.yaw(track.length()))) < .5, name(id) + " faces " + p.getYaw() + " at the arrival, not where the scene ends");
                require(StormRocIntro.watching(p) && !StormRocEncounter.enrolled(p), name(id) + " left the intro or was enrolled before landing");
            }
            require(player(far).getEntityWorld() == overworld && player(far).getEntityPos().distanceTo(farAt) < .01, "Far member moved");
            require(player(bystander).getEntityWorld() == overworld && player(bystander).getEntityPos().distanceTo(bystanderAt) < .01, "Bystander moved");
            return true;
        });
        step("The fight starts as both clients report landing, with exactly {ringer, near} and A's site key, and the intro ends for both", 120, t -> {
            if (!StormRocEncounter.enrolled(player(ringer))) return false;
            int wait = tick - mark;
            require(wait >= INTRO_LAND - 2 && wait <= INTRO_LAND + 3, "Fight started " + wait + " ticks after arrival; the clients landed " + INTRO_LAND + " after");
            fight(centre, a.key, Set.of(ringer, near));
            for (UUID id : List.of(ringer, near))
                require(!StormRocIntro.watching(player(id)) && INTROS.get(id).getLast().equals("end"), name(id) + "'s intro did not end with the fight start: " + INTROS.get(id));
            for (UUID id : List.of(far, bystander, creative, solo)) require(!StormRocEncounter.enrolled(player(id)), name(id) + " was enrolled");
            require(!StormAerie.pending(ringer) && calls().isEmpty(), "The call outlived its fight start");
            return true;
        });
        step("Loss: both challengers fall; the fight ends and both return to A's STAND in the Overworld in Survival, with records cleared and no chests", 60, t -> {
            if (t == 0) for (UUID id : List.of(ringer, near)) player(id).setPosition(centre.getX() + .5, 60, centre.getZ() + .5);
            if (StormRocEncounter.activeAt(centre) || player(ringer).getEntityWorld() != overworld || player(near).getEntityWorld() != overworld) return false;
            for (UUID id : List.of(ringer, near)) home(id, a, "after the loss");
            require(StormAerie.chests(a.key).isEmpty(), "A loss placed chests");
            return true;
        });

        // ---------------------------------------------------------------- victory at the rotated aerie
        step("At rotated aerie B, a second strike exactly 100 ticks after the first commits for ringer + near", 130, t -> {
            if (t == 0) {
                go(ringer, overworld, b.stand(), b.standYaw());
                go(near, overworld, Vec3d.ofBottomCenter(b.gong.foot().offset(b.gong.facing().rotateYClockwise(), 10)), 0);
            }
            if (t == 2) { String r = ring(ringer, b, 0); require(r != null && r.startsWith("Strike the gong again") && r.contains(NAME_NEAR), "B first strike: " + r); }
            if (t < 102) return false;
            String r = ring(ringer, b, 3);
            require(r != null && r.startsWith("The storm gong calls"), "A strike exactly 100 ticks after the first did not commit: " + r);
            for (UUID id : List.of(ringer, near)) returnPoint(id, b);
            centre = (BlockPos) field(calls().getFirst(), "centre");
            return true;
        });
        step("B's fight starts with {ringer, near} and B's site key", 400, t -> {
            if (!StormRocEncounter.enrolled(player(ringer))) return false;
            fight(centre, b.key, Set.of(ringer, near));
            pins.put(ringer, StormSummit.arrival(centre));
            return true;
        });
        step("Near falls mid-fight and spectates; the Roc skips to phase two", 10, t -> {
            if (t == 0) player(near).setPosition(centre.getX() + .5, 60, centre.getZ() + .5);
            if (t < 3) return false;
            require(player(near).isSpectator() && StormSummitManager.inRealm(player(near)), "Fallen member is not spectating on the summit");
            @SuppressWarnings("unchecked") Set<UUID> standing = (Set<UUID>) field(fightAt(centre), "standing");
            @SuppressWarnings("unchecked") Set<UUID> roster = (Set<UUID>) field(fightAt(centre), "roster");
            require(standing.equals(Set.of(ringer)) && roster.equals(Set.of(ringer, near)), "Roster/standing after the fall: " + roster + " / " + standing);
            require(boss(centre).skipToPhaseTwo(), "Roc refused to skip to phase two");
            return true;
        });
        step("Phase two begins; the Roc is brought to its last gate", StormRocRules.CHARGE_TICKS + 120, this::defeat);
        step("Victory at B places the reward chest at each of B's CHESTS cells, filled; victors wait on the summit (fallen member back in Survival)", 40, t -> {
            if (StormRocEncounter.activeAt(centre)) return false;
            endedAt = tick;
            pins.clear();
            List<BlockPos> chests = StormAerie.chests(b.key);
            require(Set.copyOf(chests).equals(Set.copyOf(b.chests())) && chests.size() == StormAerieLayout.CHESTS.length, "Chests " + chests + " are not at " + b.chests());
            for (int i = 0; i < StormAerieLayout.CHESTS.length; i++) require(contents(b.chest(i)).stream().anyMatch(s -> !s.isEmpty()), "Chest " + i + " is empty");
            require(chestCount(b) == StormAerieLayout.CHESTS.length, "Expected " + StormAerieLayout.CHESTS.length + " chest blocks at B, found " + chestCount(b));
            snapshot = new ArrayList<>();
            for (BlockPos chest : b.chests()) snapshot.add(contents(chest));
            for (UUID id : List.of(ringer, near)) {
                ServerPlayerEntity p = player(id);
                require(StormSummitManager.inRealm(p) && StormSummit.nearestCentre(p.getEntityPos()).equals(centre), name(id) + " left the summit at once");
                require(p.interactionManager.getGameMode() == GameMode.SURVIVAL, name(id) + " is in " + p.interactionManager.getGameMode() + " after the win");
                require(StormAerie.pending(id), name(id) + " is not held by the homecoming");
            }
            soft(chat(ringer).stream().anyMatch(s -> s.contains("Reward chests wait at the aerie.")), "No reward note in the first victory message: " + chat(ringer));
            System.out.println("AERIE B CHESTS: " + snapshot);
            return true;
        });
        step("Victors are sent home to B's STAND about 200 ticks after the win", 260, t -> {
            if (t == 150) for (UUID id : List.of(ringer, near)) require(StormSummitManager.inRealm(player(id)), name(id) + " went home before the delay");
            if (player(ringer).getEntityWorld() != overworld) return false;
            int delay = tick - endedAt;
            require(delay >= 195 && delay <= 206, "Homecoming took " + delay + " ticks");
            for (UUID id : List.of(ringer, near)) home(id, b, "after the win");
            System.out.println("AERIE HOMECOMING DELAY " + delay);
            return true;
        });
        step("Each roster member (incl. the fallen one) claims exactly one Ultimate book; a second claim, the far member and the bystander get nothing; a full inventory is refused without spending the receipt", 5, t -> {
            String receipt = "aerie:" + b.key;
            require(books(ringer) == 0, "Ringer already had a book");
            require(StormAerie.claimBook(player(ringer), overworld, b.chest(0)) && books(ringer) == 1, "Ringer's claim failed");
            BlockPos lastChest = b.chests().getLast();
            require(!StormAerie.claimBook(player(ringer), overworld, lastChest) && !StormAerie.claimBook(player(ringer), overworld, b.chest(0)) && books(ringer) == 1,
                    "Ringer claimed twice");
            ServerPlayerEntity n = player(near);
            for (int i = 0; i < 36; i++) n.getInventory().setStack(i, new ItemStack(Items.COBBLESTONE, 64));
            require(useChest(near, lastChest) && books(near) == 0, "A full inventory received a book");
            require(!n.getAttachedOrElse(EWAttachments.SPELL_BOOKS, new net.minecraft.nbt.NbtCompound()).getBoolean(receipt, false), "A refused claim spent the receipt");
            n.getInventory().setStack(0, ItemStack.EMPTY);
            require(useChest(near, lastChest) && books(near) == 1, "Near member's claim via the chest failed after making room");
            require(useChest(near, b.chest(0)) && books(near) == 1, "Near member claimed twice");
            require(!StormAerie.claimBook(player(far), overworld, b.chest(0)) && books(far) == 0, "Far member (not on the roster) got a book");
            require(!StormAerie.claimBook(player(bystander), overworld, lastChest) && books(bystander) == 0, "Bystander got a book");
            require(!StormAerie.claimBook(player(ringer), overworld, b.chest(0).up(3)), "A non-chest cell claimed");
            return true;
        });
        win("Second win at B", 10, () -> {
            require(Set.copyOf(StormAerie.chests(b.key)).equals(Set.copyOf(b.chests())) && chestCount(b) == StormAerieLayout.CHESTS.length,
                    "A second win changed the chests: " + StormAerie.chests(b.key) + " count " + chestCount(b));
            for (int i = 0; i < StormAerieLayout.CHESTS.length; i++) require(contents(b.chest(i)).equals(snapshot.get(i)), "A second win refilled chest " + i);
            soft(chat(ringer).stream().anyMatch(s -> s.contains("Open the aerie's chests to claim your spell book.")), "No second-win note: " + lastChat(ringer));
        });
        step("After the second win nobody can claim again", 3, t -> {
            require(!StormAerie.claimBook(player(ringer), overworld, b.chest(0)) && books(ringer) == 1, "Ringer claimed after a second win");
            useChest(near, b.chest(0));
            require(books(near) == 1, "Near member claimed after a second win: " + books(near) + " books");
            // The next win finds one chest cell broken.
            overworld.setBlockState(b.chest(0), Blocks.AIR.getDefaultState());
            return true;
        });
        win("Third win at B with chest 0 broken", 50, () -> {
            if (soft(overworld.getBlockState(b.chest(0)).isOf(Blocks.CHEST), "The broken chest cell " + b.chest(0) + " was not re-placed: " + overworld.getBlockState(b.chest(0))))
                soft(contents(b.chest(0)).stream().allMatch(String::isEmpty), "The re-placed chest was refilled: " + contents(b.chest(0)));
            for (int i = 1; i < StormAerieLayout.CHESTS.length; i++) require(contents(b.chest(i)).equals(snapshot.get(i)), "Intact chest " + i + " changed");
            soft(Set.copyOf(StormAerie.chests(b.key)).equals(Set.copyOf(b.chests())) && chestCount(b) == StormAerieLayout.CHESTS.length, "Chests after the third win: " + StormAerie.chests(b.key) + " count " + chestCount(b));
        });

        // ---------------------------------------------------------------- busy slots and a disconnect
        step("With slots 1-7 occupied, a solo call takes slot 0; then every slot is busy and the ringer is told the Roc is fighting elsewhere", 60, t -> {
            if (t == 0) {
                for (int slot = 1; slot < StormSummit.SLOTS; slot++) {
                    BlockPos c = StormSummitManager.slotCentre(slot);
                    squatters.add(create(realm, "Squatter" + slot, new Vec3d(c.getX() + .5, StormSummit.SURFACE_Y + 1, c.getZ() + .5)));
                }
                go(ringer, overworld, a.stand(), a.standYaw());
                go(near, overworld, Vec3d.ofBottomCenter(a.gong.foot().add(0, 0, -20)), 0);
            }
            if (t == 2) require(ring(solo, a, 0).startsWith("Strike the gong again"), "Solo first strike");
            if (t == 12) {
                String r = ring(solo, a, 1);
                require(r != null && r.startsWith("The storm gong calls") && r.contains(NAME_SOLO) && !r.contains(NAME_RINGER), "Solo call: " + r);
                require(calls().size() == 1 && field(calls().getFirst(), "slot").equals(0), "Solo call did not take slot 0: " + StormSummitManager.status());
            }
            if (t == 14) require(ring(ringer, a, 2).startsWith("Strike the gong again"), "Ringer first strike while busy");
            if (t < 25) return false;
            String r = ring(ringer, a, 3);
            require("The Storm Roc is fighting elsewhere. Try again soon.".equals(r), "All slots busy did not refuse: " + r);
            require(!StormAerie.pending(ringer) && !called(ringer) && !returns(ringer) && calls().size() == 1, "A refused call recorded the ringer");
            return true;
        });
        step("The solo ringer disconnects during the intro at the gong: the call ends, its records and scene go, and its slot is free for the next call", 60, t -> {
            if (t == 0) {
                ServerPlayerEntity s = player(solo);
                require(calls().size() == 1 && (int) field(calls().getFirst(), "arrived") < 0 && StormRocIntro.watching(s) && !StormRocIntro.carried(solo),
                        "Solo is not in the intro at the gong");
                ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(s.networkHandler, server);
                require(!StormRocIntro.watching(s), "The disconnected ringer's scene remains");
                server.getPlayerManager().remove(s);
                everyone.remove(solo);
            }
            if (t == 2) {
                require(calls().isEmpty(), "The disconnected ringer's call kept its slot: " + StormSummitManager.status());
                require(!called(solo) && !returns(solo), "The disconnected ringer's records remain");
                require(ring(ringer, a, 4).startsWith("Strike the gong again"), "Ringer first strike after the disconnect");
            }
            if (t < 13) return false;
            String r = ring(ringer, a, 5);
            require(r != null && r.startsWith("The storm gong calls") && r.contains(NAME_NEAR), "Call after the slot was freed: " + r);
            require(calls().size() == 1 && field(calls().getFirst(), "slot").equals(0), "The freed slot was not reused: " + StormSummitManager.status());
            centre = (BlockPos) field(calls().getFirst(), "centre");
            for (UUID id : squatters) { server.getPlayerManager().remove(player(id)); everyone.remove(id); }
            return true;
        });

        // ---------------------------------------------------------------- death in the wait, and crash recovery
        step("The pair lands on the summit again", 400, t -> {
            if (!StormSummitManager.inRealm(player(ringer)) || !StormSummitManager.inRealm(player(near))) return false;
            mark = tick;
            return true;
        });
        step("Near dies during the intro on the summit (killing damage still lands): the respawned player is sent to A's STAND with belongings kept, the scene over", 30, t -> {
            if (t == 0) {
                require(StormRocIntro.watching(player(near)), "Near is not in the intro on the summit");
                player(near).kill(realm);
                require(!player(near).isAlive(), "Near member did not die");
            }
            if (t == 2) respawn(near);
            if (t < 3) return false;
            ServerPlayerEntity n = player(near);
            boolean there = n.getEntityWorld() == overworld && n.getEntityPos().distanceTo(a.stand()) < .01;
            if (!there && t < 12) return false;
            if (soft(there, "A called player who died during the arrival wait respawned at " + n.getEntityPos() + " in "
                    + n.getEntityWorld().getRegistryKey().getValue() + " instead of A's STAND " + a.stand() + "; called=" + called(near) + " return=" + returns(near)))
                home(near, a, "after dying in the arrival wait");
            else go(near, overworld, a.stand(), a.standYaw());
            require(books(near) == 1, "Belongings lost on the summit death: books=" + books(near));
            require(!StormRocIntro.watching(player(near)) && INTROS.get(near).getLast().equals("end"), "The dead member's scene was not ended: " + INTROS.get(near));
            require(StormAerie.pending(ringer) && StormSummitManager.inRealm(player(ringer)) && StormRocIntro.watching(player(ringer)), "The ringer's call ended with the death");
            return true;
        });
        step("Recovery: with the call lost (as after a crash) the ringer is on the summit with no fight; the recovery tick sends them to A's STAND and no fight starts", 100, t -> {
            if (t == 0) {
                require(tick - mark < INTRO_LAND, "Too late to test the arrival wait");
                // A crash forgets the calls and the scenes alike.
                calls().clear();
                watchers().clear();
                require(!StormAerie.pending(ringer) && called(ringer) && StormSummitManager.inRealm(player(ringer)), "Recovery precondition");
            }
            if (t == 2) home(ringer, a, "by the recovery path");
            if (t < 80) return false;
            require(!StormRocEncounter.activeAt(centre) && !StormRocEncounter.enrolled(player(ringer)), "A fight started after recovery");
            return true;
        });

        // ---------------------------------------------------------------- wipe by death
        step("Wipe call: ringer + near strike at A", 30, t -> {
            if (t == 0) require(ring(ringer, a, 6).startsWith("Strike the gong again"), "Wipe first strike");
            if (t < 15) return false;
            require(ring(ringer, a, 7).startsWith("The storm gong calls"), "Wipe call did not commit");
            centre = (BlockPos) field(calls().getFirst(), "centre");
            return true;
        });
        step("Wipe fight starts", 400, t -> StormRocEncounter.enrolled(player(ringer)) && StormRocEncounter.enrolled(player(near)));
        step("Wipe by death: both die, the fight ends, and both respawns are sent to A's STAND in Survival", 40, t -> {
            if (t == 0) { player(ringer).kill(realm); player(near).kill(realm); }
            if (t == 2) { require(!StormRocEncounter.activeAt(centre), "Wipe did not end the fight"); respawn(ringer); respawn(near); }
            if (t < 3) return false;
            boolean there = List.of(ringer, near).stream().allMatch(id -> player(id).getEntityWorld() == overworld && player(id).getEntityPos().distanceTo(a.stand()) < .01);
            if (!there && t < 15) return false;
            if (soft(there, "Wiped players respawned at " + player(ringer).getEntityPos() + " / " + player(near).getEntityPos()
                    + " instead of A's STAND " + a.stand() + "; called=" + called(ringer) + "," + called(near)))
                for (UUID id : List.of(ringer, near)) home(id, a, "after a wipe by death");
            return true;
        });

        // ---------------------------------------------------------------- clients that never get through the intro
        step("Late clients: with the far member brought near, ringer + near + far strike at A; far's client will never report the cloud, near's never land", 30, t -> {
            if (t == 0) {
                go(far, overworld, Vec3d.ofBottomCenter(a.gong.foot().add(0, 0, -10)), 0);
                lostInCloud.add(far);
                neverLand.add(near);
            }
            if (t == 2) require(ring(ringer, a, 8).startsWith("Strike the gong again"), "Late-clients first strike");
            if (t < 15) return false;
            String r = ring(ringer, a, 9);
            require(r != null && r.startsWith("The storm gong calls") && r.contains(NAME_NEAR) && r.contains(NAME_FAR), "Late-clients call: " + r);
            centre = (BlockPos) field(calls().getFirst(), "centre");
            mark = tick;
            return true;
        });
        step("A client that never reports the cloud is left at the aerie, out of the call, its scene ended and records cleared, and told; the others are carried up without it", StormRocIntro.CLOUD_LATE + 40, t -> {
            if (StormAerie.pending(far)) {
                require(player(far).getEntityWorld() == overworld, "Far was carried up without reporting the cloud");
                return false;
            }
            int late = tick - mark;
            require(late >= StormRocIntro.CLOUD_LATE - 2 && late <= StormRocIntro.CLOUD_LATE + 3, "Far was let go " + late + " ticks after the call, not at " + StormRocIntro.CLOUD_LATE);
            ServerPlayerEntity f = player(far);
            require(f.getEntityWorld() == overworld && f.getEntityPos().distanceTo(a.stand()) < .01, "Far moved off A's STAND: " + f.getEntityPos());
            require(!StormRocIntro.watching(f) && INTROS.get(far).getLast().equals("end"), "Far's scene did not end: " + INTROS.get(far));
            require(!called(far) && !returns(far), "Far's records remain");
            require(lastChat(far).equals("The storm passes you by. Strike the gong again in a little while."), "Far was told: " + lastChat(far));
            // Withholding the cloud can't chain the scene's protection: no call takes them again for a while.
            require("The storm won't answer you again yet.".equals(ring(far, a, 10)), "Far could ring again at once: " + lastChat(far));
            require(!StormAerie.group(player(ringer), overworld, a.gong.anchor()).contains(f), "A call could take far again at once");
            for (UUID id : List.of(ringer, near)) require(StormSummitManager.inRealm(player(id)) && StormRocIntro.watching(player(id)), name(id) + " was not carried up");
            return true;
        });
        step("A scene that never lands doesn't hold up the fight: after its length and grace that member is sent home and the fight starts with the one who landed", StormRocIntro.LENGTH + StormRocIntro.GRACE + 40, t -> {
            if (!StormRocEncounter.enrolled(player(ringer))) return false;
            int waited = tick - mark;
            int due = StormRocIntro.LENGTH + StormRocIntro.GRACE;
            require(waited >= due - 2 && waited <= due + 3, "The fight started " + waited + " ticks after the call, not at " + due);
            fight(centre, a.key, Set.of(ringer));
            home(near, a, "after a scene that never landed");
            for (UUID id : List.of(ringer, near))
                require(!StormRocIntro.watching(player(id)) && INTROS.get(id).getLast().equals("end"), name(id) + "'s scene did not end: " + INTROS.get(id));
            lostInCloud.clear();
            neverLand.clear();
            return true;
        });
        step("The late-clients fight ends with the ringer's fall, home at A's STAND", 60, t -> {
            if (t == 0) player(ringer).setPosition(centre.getX() + .5, 60, centre.getZ() + .5);
            if (StormRocEncounter.activeAt(centre) || player(ringer).getEntityWorld() != overworld) return false;
            home(ringer, a, "after the late-clients fight");
            return true;
        });
    }

    /** A full aerie win at B: ring with the given gap, fight, Roc to its last gate, checks at the end, homecoming. */
    private void win(String name, int gap, Runnable atVictory) {
        step(name + ": ring with a " + gap + "-tick gap commits", gap + 5, t -> {
            if (t == 0) require(ring(ringer, b, 5).startsWith("Strike the gong again"), name + " first strike");
            if (t < gap) return false;
            String r = ring(ringer, b, 6);
            require(r != null && r.startsWith("The storm gong calls"), name + " second strike after " + gap + " ticks: " + r);
            centre = (BlockPos) field(calls().getFirst(), "centre");
            return true;
        });
        step(name + ": fight starts with B's site key", 400, t -> {
            if (!StormRocEncounter.enrolled(player(ringer))) return false;
            fight(centre, b.key, Set.of(ringer, near));
            pins.put(ringer, StormSummit.arrival(centre));
            pins.put(near, StormSummit.arrival(centre).add(2, 0, 0));
            require(boss(centre).skipToPhaseTwo(), "Roc refused to skip");
            return true;
        });
        step(name + ": phase two and the last gate", StormRocRules.CHARGE_TICKS + 120, this::defeat);
        step(name + ": victory checks", 40, t -> {
            if (StormRocEncounter.activeAt(centre)) return false;
            pins.clear();
            atVictory.run();
            return true;
        });
        step(name + ": homecoming to B's STAND", 260, t -> {
            if (player(ringer).getEntityWorld() != overworld || player(near).getEntityWorld() != overworld) return false;
            for (UUID id : List.of(ringer, near)) home(id, b, name);
            return true;
        });
    }

    private boolean defeat(int t) {
        StormRocEntity boss = boss(centre);
        require(boss != null, "The fight lost its Roc");
        if (!boss.phaseTwo() || boss.stage() != StormRocRules.Stage.AIR) return false;
        boss.setHealth(1.005f);
        return true;
    }

    private void step(String name, int timeout, Body body) { steps.add(new Step(name, timeout, body)); }

    // ------------------------------------------------------------------ checks

    private void notCalled(UUID id, String when) {
        require(!StormAerie.pending(id) && !called(id) && !returns(id) && calls().isEmpty(), name(id) + " was called " + when);
    }

    private void returnPoint(UUID id, Site site) {
        StormSummitManager.Point point = StormSummitManager.savedReturns().get(id.toString());
        require(point != null, name(id) + " has no return point");
        Vec3d stand = site.stand();
        require(point.dimension().equals("minecraft:overworld") && new Vec3d(point.x(), point.y(), point.z()).distanceTo(stand) < 1e-6,
                name(id) + " returns to " + point + ", not " + site.name + "'s STAND " + stand);
        require(Math.abs(wrap(point.yaw() - site.standYaw())) < .5, name(id) + " return yaw " + point.yaw() + " does not face the gong (" + site.standYaw() + ")");
    }

    /** Back at a site's STAND in the Overworld, in Survival, held by nothing, records cleared. */
    private void home(UUID id, Site site, String when) {
        ServerPlayerEntity p = player(id);
        require(p.getEntityWorld() == overworld && p.getEntityPos().distanceTo(site.stand()) < .01,
                name(id) + " is not at " + site.name + "'s STAND " + when + ": " + p.getEntityPos() + " in " + p.getEntityWorld().getRegistryKey().getValue());
        require(Math.abs(wrap(p.getYaw() - site.standYaw())) < .5, name(id) + " does not face the gong " + when + ": yaw " + p.getYaw());
        require(p.interactionManager.getGameMode() == GameMode.SURVIVAL, name(id) + " is in " + p.interactionManager.getGameMode() + " " + when);
        require(!StormAerie.pending(id) && !StormRocEncounter.enrolled(p), name(id) + " is still held " + when);
        require(!called(id) && !returns(id) && !StormSummitManager.savedModes().containsKey(id.toString()), name(id) + "'s records remain " + when);
    }

    private void fight(BlockPos at, String site, Set<UUID> expected) throws Exception {
        Object fight = fightAt(at);
        require(fight != null, "No fight at " + at);
        require(site.equals(field(fight, "site")), "Fight site " + field(fight, "site") + " is not " + site);
        require(expected.equals(field(fight, "roster")) && expected.equals(field(fight, "standing")), "Fight roster " + field(fight, "roster") + " is not " + expected);
    }

    private int chestCount(Site site) {
        int count = 0;
        for (BlockPos pos : BlockPos.iterate(site.gong.anchor().add(-16, -6, -16), site.gong.anchor().add(16, 12, 16)))
            if (overworld.getBlockState(pos).isOf(Blocks.CHEST)) count++;
        return count;
    }

    private List<String> contents(BlockPos pos) {
        require(overworld.getBlockEntity(pos) instanceof ChestBlockEntity, "No chest at " + pos);
        ChestBlockEntity chest = (ChestBlockEntity) overworld.getBlockEntity(pos);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < chest.size(); i++) { ItemStack s = chest.getStack(i); out.add(s.isEmpty() ? "" : s.getCount() + "x" + s.getItem()); }
        return out;
    }

    private int books(UUID id) {
        var inventory = player(id).getInventory();
        int n = 0;
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i).isOf(ModItems.ULTIMATE_SPELL_BOOK)) n += inventory.getStack(i).getCount();
        return n;
    }

    /** Opens a chest the way a client does, through the real use-block event. */
    private boolean useChest(UUID id, BlockPos pos) {
        ActionResult result = UseBlockCallback.EVENT.invoker().interact(player(id), overworld, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
        if (result != ActionResult.PASS) System.out.println("AERIE CHEST USE by " + name(id) + " at " + pos + " returned " + result);
        return true;
    }

    // ------------------------------------------------------------------ plumbing

    private String ring(UUID id, Site site, int which) { return StormAerie.ring(player(id), site.gongBlock(which)); }
    /** The trigger a strike at this site sends: the anchor, the gong's controller and its swing. */
    /** The swings a strike at this site sends: the anchor, the gong's controller, and either of its two identical strikes. */
    private static List<String> swingsAt(UUID id, Site site) {
        String at = site.gong.anchor().toShortString() + "|" + StormGongBlockEntity.CONTROLLER + "|";
        return SWINGS.getOrDefault(id, List.of()).stream().filter(w -> w.equals(at + StormGongBlockEntity.STRIKE) || w.equals(at + StormGongBlockEntity.STRIKE_B)).toList();
    }
    private static int swings(UUID id, Site site) { return swingsAt(id, site).size(); }
    /** Each strike plays the other of the two swings, so a client restarts the swing every time. */
    private static boolean alternating(UUID id, Site site) {
        List<String> seen = swingsAt(id, site);
        for (int i = 1; i < seen.size(); i++) if (seen.get(i).equals(seen.get(i - 1))) return false;
        return true;
    }
    private boolean called(UUID id) { return StormSummitManager.savedCalls().contains(id.toString()); }
    private boolean returns(UUID id) { return StormSummitManager.savedReturns().containsKey(id.toString()); }
    private ServerPlayerEntity player(UUID id) { return server.getPlayerManager().getPlayer(id); }
    private String name(UUID id) { ServerPlayerEntity p = player(id); return p == null ? id.toString() : p.getName().getString(); }
    private static List<String> chat(UUID id) { return CHAT.getOrDefault(id, List.of()); }
    private static String lastChat(UUID id) { List<String> c = chat(id); return c.isEmpty() ? "" : c.getLast(); }
    private static float wrap(float degrees) { return net.minecraft.util.math.MathHelper.wrapDegrees(degrees); }

    private static Map<?, ?> watchers() {
        try { Field f = StormRocIntro.class.getDeclaredField("WATCHING"); f.setAccessible(true); return (Map<?, ?>) f.get(null); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> calls() {
        try { Field f = StormAerie.class.getDeclaredField("calls"); f.setAccessible(true); return (List<Object>) f.get(null); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static Object fightAt(BlockPos at) {
        try { Field f = StormRocEncounter.class.getDeclaredField("fights"); f.setAccessible(true); return ((Map<?, ?>) f.get(null)).get(at); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static StormRocEntity boss(BlockPos at) {
        Object fight = fightAt(at);
        return fight == null ? null : (StormRocEntity) field(fight, "boss");
    }

    private static Object field(Object owner, String name) {
        try { Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private void go(UUID id, ServerWorld world, Vec3d pos, float yaw) {
        StormSummitManager.transfer(player(id), world, pos, yaw, 0);
    }

    private void respawn(UUID id) {
        ServerPlayerEntity dead = player(id);
        require(!dead.isAlive(), name(id) + " is not dead");
        ServerPlayNetworkHandler handler = dead.networkHandler;
        handler.player = server.getPlayerManager().respawnPlayer(dead, false, Entity.RemovalReason.KILLED);
        ServerPlayerEntity fresh = player(id);
        fresh.setLoaded(true);
        fresh.onTeleportationDone();
        fresh.setNoGravity(true);
        System.out.println("AERIE RESPAWN " + name(id) + " at " + fresh.getEntityPos() + " in " + fresh.getEntityWorld().getRegistryKey().getValue());
    }

    private UUID create(ServerWorld world, String name, Vec3d at) throws Exception {
        UUID uuid = UUID.nameUUIDFromBytes(("aerie-smoke-" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, name);
        ServerPlayerEntity player = new ServerPlayerEntity(server, world, profile, SyncedClientOptions.createDefault());
        CHAT.put(uuid, new CopyOnWriteArrayList<>());
        SWINGS.put(uuid, new CopyOnWriteArrayList<>());
        INTROS.put(uuid, new CopyOnWriteArrayList<>());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND) {
            @Override public void send(Packet<?> packet) { hear(uuid, packet); }
            @Override public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener) { hear(uuid, packet); }
            @Override public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener, boolean flush) { hear(uuid, packet); }
            @Override public void flush() {}
            @Override public boolean isOpen() { return true; }
        };
        player.networkHandler = new ServerPlayNetworkHandler(server, connection, player, ConnectedClientData.createDefault(profile, false));
        player.setPosition(at.x, at.y, at.z);
        Field map = PlayerManager.class.getDeclaredField("playerMap");
        map.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID, ServerPlayerEntity> players = (Map<UUID, ServerPlayerEntity>) map.get(server.getPlayerManager());
        players.put(uuid, player);
        server.getPlayerManager().getPlayerList().add(player);
        world.onPlayerConnected(player);
        player.setLoaded(true);
        player.onTeleportationDone();
        player.setNoGravity(true);
        player.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        everyone.add(uuid);
        return uuid;
    }

    private static void hear(UUID player, Packet<?> packet) {
        if (packet instanceof GameMessageS2CPacket message) {
            List<String> heard = CHAT.get(player);
            if (heard != null) heard.add(message.content().getString());
        }
        if (packet instanceof ChunkSentS2CPacket) BATCHES.add(player);
        if (packet instanceof CustomPayloadS2CPacket custom && custom.payload() instanceof ModNetworking.StormRocIntroPayload intro) {
            List<String> told = INTROS.get(player);
            if (told != null) told.add(intro.active() ? "start " + intro.summit().toShortString() : "end");
        }
        if (packet instanceof CustomPayloadS2CPacket custom && custom.payload() instanceof BlockEntityAnimTriggerPacket trigger) {
            List<String> seen = SWINGS.get(player);
            if (seen != null) seen.add(trigger.pos().toShortString() + "|" + trigger.controllerName().orElse("?") + "|" + trigger.animName());
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    /** A check whose failure is recorded while the run goes on, so later scenarios still report. */
    private boolean soft(boolean condition, String message) {
        if (!condition) {
            failures.add(steps.get(index).name() + ": " + message);
            System.out.println("AERIE SMOKE SOFT FAILURE: " + message);
        }
        return condition;
    }

}
