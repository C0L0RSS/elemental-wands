package com.anton.elementalwands.end;

import com.anton.elementalwands.arena.GuardianArenaJournal.Point;
import com.anton.elementalwands.party.PartyManager;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.feature.EndPlacedFeatures;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.EndSpikeFeature;

/**
 * Test-only entrypoint for the End takeover (stage 1 of the final boss). With a stand-in boss and
 * simulated players it checks: no dragon fight and no dragon; spikes not generated; the arena laid out
 * at server start; refused players put beside the portal at once and told, with nothing saved; a
 * player's chest and shulker box holding the layout, then a forced layout that leaves everything as
 * drops; dragon eggs set down beside the arrival; the arena laid out over planted vanilla leftovers (a
 * spike with its cage and crystal, the fountain, raised terrain, an old gateway); who the island sends
 * home (on it without the portal) and who it leaves alone (far out in the End); the portal rule
 * (arrival, refusals, a party member joining, nothing else through, no mounts or pearls); a fall that
 * keeps belongings and wakes at the way back; the floor's edge, protection, buckets, temporary spell
 * blocks and mob exclusion that spares named and tamed mobs; a wipe; a victory with the way home and
 * gateways; Peaceful; a reset; and a stop mid-fight followed by logins that leave nobody in the End.
 */
public final class EndTakeoverSmoke implements ModInitializer {
    private static final Map<UUID, Integer> CHAT = new ConcurrentHashMap<>();
    private static final List<StandIn> MADE = new ArrayList<>();
    private static final Map<UUID, String> LAST = new ConcurrentHashMap<>();
    private static final BlockPos PORTAL = new BlockPos(30, 120, 10);
    /** A player's chest and shulker box on the island, inside the volume a layout changes. */
    private static final BlockPos CHEST = new BlockPos(12, 64, -12), SHULKER = new BlockPos(-12, 64, 12);

    private int ticks, stage, mark;
    private ServerPlayerEntity a, b, c, d;
    private StandIn boss;
    private Point back;
    private EndermanEntity enderman, named;
    private WolfEntity wolf;
    private BlockPos tempAt;
    private net.minecraft.block.BlockState tempOriginal;

    /** The boss for this fixture: a still zombie that records what the encounter tells it. */
    static final class StandIn extends ZombieEntity implements EndFighter {
        BlockPos centre;
        double radius;
        final List<UUID> roster = new ArrayList<>(), participants = new ArrayList<>(), removed = new ArrayList<>();
        UUID skin;
        boolean stopped, complete;
        StandIn(World world) { super(EntityType.ZOMBIE, world); setAiDisabled(true); }
        /** A zombie would vanish on Peaceful; the real boss is not a hostile type and stays. */
        @Override public void checkDespawn() {}
        @Override public void startFight(BlockPos centre, double floorRadius, List<ServerPlayerEntity> players, UUID skinOwner) {
            this.centre = centre; radius = floorRadius; skin = skinOwner;
            players.forEach(p -> { roster.add(p.getUuid()); participants.add(p.getUuid()); });
        }
        @Override public void addParticipant(ServerPlayerEntity player) { participants.add(player.getUuid()); }
        @Override public void removeParticipant(UUID player) { removed.add(player); }
        @Override public void stopFight() { stopped = true; }
        @Override public boolean fightComplete() { return complete; }
        @Override public Entity asEntity() { return this; }
    }

    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { tick(server); }
            catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(Path.of("END_FAILED.txt"), "Stage " + stage + ": " + failure + "\n"); } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }

    private void tick(MinecraftServer server) throws Exception {
        ticks++;
        require(ticks < 11000, "End takeover smoke exceeded nine minutes at stage " + stage + ": " + EndEncounter.status());
        ServerWorld world = server.getOverworld(), end = server.getWorld(World.END);
        if (ticks < 40) return;
        switch (stage) {
            case 0 -> {
                require(end != null, "The End is not loaded");
                require(end.getEnderDragonFight() == null, "The End still has a vanilla dragon fight");
                var biome = end.getRegistryManager().getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.THE_END).value();
                for (var step : biome.getGenerationSettings().getFeatures())
                    for (var feature : step) require(!feature.matchesKey(EndPlacedFeatures.END_SPIKE), "The End biome still generates the main island's spikes");
                // Laid out when the server started, before anyone came near a portal.
                require(EndArenaManager.building(), "The arena was not being laid out when the server started: " + EndArenaManager.status());
                EndEncounter.setBossFactory(w -> { StandIn s = new StandIn(w); MADE.add(s); return s; });
                stagePortal(world);
                a = player(server, world, "EndFirst", 30.5, 120, 14.5);
                b = player(server, world, "EndAlly", 31.5, 120, 14.5);
                c = player(server, world, "EndStranger", 29.5, 120, 14.5);
                PartyManager parties = PartyManager.get(server);
                parties.create(a);
                parties.invite(a, b);
                parties.accept(b, null);
                require(parties.allied(a.getUuid(), b.getUuid()) && !parties.allied(a.getUuid(), c.getUuid()), "The parties are not as staged");
                back = EndGate.wayBack(world, PORTAL);
                require(back != null && back.dimension().equals("minecraft:overworld"), "No way back was found beside the portal");
                int bx = (int) Math.floor(back.x()), bz = (int) Math.floor(back.z());
                int gap = Math.max(Math.max(29 - bx, bx - 31), Math.max(9 - bz, bz - 11));   // cells between it and the portal's 3x3
                require(gap >= 2 && Math.abs(back.y() - 120) < .01, "The way back is not a standing spot beside the portal: " + back);
                // Into the portal before the arena is ready: refused, put beside the portal at once, told to try again; nothing saved.
                refusedAtPortal(world, a, "try again in a moment");
                next();
            }
            case 1 -> {
                if (!EndArenaManager.ready()) { require(ticks - mark < 2400, "The arena was not laid out in two minutes: " + EndArenaManager.status()); return; }
                // An existing world's island: vanilla's leftovers, a player's chest and shulker box, dragon eggs and an item lying there.
                plantLeftovers(end);
                end.setBlockState(CHEST, Blocks.CHEST.getDefaultState());
                ((ChestBlockEntity) end.getBlockEntity(CHEST)).setStack(0, new ItemStack(Items.DIAMOND, 3));
                ((ChestBlockEntity) end.getBlockEntity(CHEST)).setStack(5, new ItemStack(Items.STONE, 10));
                end.setBlockState(SHULKER, Blocks.SHULKER_BOX.getDefaultState());
                ((ShulkerBoxBlockEntity) end.getBlockEntity(SHULKER)).setStack(0, new ItemStack(Items.EMERALD, 4));
                end.setBlockState(new BlockPos(5, 64, 5), Blocks.DRAGON_EGG.getDefaultState());
                end.setBlockState(new BlockPos(-20, 64, -3), Blocks.DRAGON_EGG.getDefaultState());
                ItemEntity gold = new ItemEntity(end, 20.5, 64.1, 20.5, new ItemStack(Items.GOLD_INGOT, 2));
                gold.setVelocity(Vec3d.ZERO);
                end.spawnEntity(gold);
                // Someone who lives far out in the End logs in: they are not the island's to send home.
                d = player(server, end, "EndIslander", 200.5, 100, .5);
                EndEncounter.loggedIn(d);
                require(EndEncounter.layout(server, false).startsWith("Checking the island"), "/ew end layout did not start a checked layout");
                next();
            }
            case 2 -> {
                if (EndArenaManager.building()) { require(ticks - mark < 1200, "The check did not finish: " + EndArenaManager.status()); return; }
                // The chest and shulker box hold the layout: nothing changed, both reported.
                require(Set.copyOf(EndArenaManager.heldBy()).equals(Set.of(CHEST, SHULKER)), "The layout was not held by exactly the chest and shulker box: " + EndArenaManager.heldBy());
                require(EndEncounter.status().contains("12, 64, -12") && EndEncounter.status().contains("-12, 64, 12"), "Status does not list what holds the layout: " + EndEncounter.status());
                require(end.getBlockState(CHEST).isOf(Blocks.CHEST) && end.getBlockState(SHULKER).isOf(Blocks.SHULKER_BOX)
                        && end.getBlockState(new BlockPos(5, 64, 5)).isOf(Blocks.DRAGON_EGG) && end.getBlockState(new BlockPos(12, 70, 12)).isOf(Blocks.END_STONE),
                        "A held layout changed the island");
                require(d.getEntityWorld() == end && d.getEntityPos().distanceTo(new Vec3d(200.5, 100, .5)) < .01, "A player far out in the End was moved at login");
                refusedAtPortal(world, a, "layout force");
                require(EndEncounter.layout(server, true).startsWith("Laying the End's arena out, breaking"), "/ew end layout force did not start");
                next();
            }
            case 3 -> {
                if (!EndArenaManager.ready()) { require(ticks - mark < 2400, "The forced layout did not finish: " + EndArenaManager.status()); return; }
                checkArena(end);
                checkDrops(end);
                // By another way than the portal (a command, an old gateway): sent back, and no fight starts for them.
                d.teleport(end, 10.5, 64, 10.5, Set.of(), 0, 0, false);
                require(EndArena.inFootprint(end, d.getEntityPos()), "The islander was not moved onto the floor");
                next();
            }
            case 4 -> {
                if (d.getEntityWorld() == end) { require(ticks - mark < 45, "A player who reached the floor without the portal stayed there"); return; }
                require(!EndEncounter.running(), "A fight started for a player who did not come through the portal");
                if (!server.isDedicated()) require(EndArenaManager.clientGuarded(), "The client was not told the arena is protected");
                // A named enderman and a tamed wolf stand on the floor before the fight; an unnamed enderman comes during it.
                named = EntityType.ENDERMAN.create(end, SpawnReason.COMMAND);
                named.setCustomName(Text.literal("Pearl"));
                named.setAiDisabled(true);
                named.refreshPositionAndAngles(25.5, 64, -25.5, 0, 0);
                end.spawnEntity(named);
                wolf = EntityType.WOLF.create(end, SpawnReason.COMMAND);
                wolf.setTamed(true, true);
                wolf.setOwner(b);
                wolf.setAiDisabled(true);
                wolf.refreshPositionAndAngles(-25.5, 64, -25.5, 0, 0);
                end.spawnEntity(wolf);
                // Through the portal: one saved way back, and the fight starts.
                int saved = EndArenaManager.writes;
                TeleportTarget into = EndGate.target(world, a, PORTAL);
                require(into != null && EndArenaManager.writes == saved + 1, "Admission did not save the way back exactly once");
                require(a.teleportTo(into) != null, "The first player's trip was blocked");
                next();
            }
            case 5 -> {
                if (EndEncounter.boss() == null) { require(ticks - mark < 600, "No fight started for the first arrival: " + EndEncounter.status()); return; }
                require(a.getEntityWorld() == end && a.getEntityPos().distanceTo(EndArena.arrival()) < .01 && Math.abs(a.getYaw() - 180) < .01,
                        "The first player did not arrive at the arrival spot facing north: " + a.getEntityPos());
                a.onTeleportationDone();
                boss = (StandIn) EndEncounter.boss();
                require(boss.centre.equals(new BlockPos(0, 63, 0)) && boss.radius == 44.5, "The boss was started on the wrong floor");
                require(boss.roster.equals(List.of(a.getUuid())) && a.getUuid().equals(boss.skin), "The roster or skin owner is wrong");
                require(boss.getEntityPos().distanceTo(EndArena.bossSpot()) < .01, "The boss is not at the floor's centre");
                require(EndArenaManager.persist() && Files.readString(save(server)).contains(a.getUuidAsString()), "The way back was not saved");
                // A player of another party is refused each time, put beside the portal and told why; nothing else goes through.
                refusedAtPortal(world, c, "Another party");
                refusedAtPortal(world, c, "Another party");
                require(EndGate.target(world, new ItemEntity(world, 30.5, 120.5, 10.5, new ItemStack(Items.STONE)), PORTAL) == null,
                        "An item went through during a fight");
                // A party member joins.
                TeleportTarget join = EndGate.target(world, b, PORTAL);
                require(join != null, "A party member was refused");
                require(b.teleportTo(join) != null, "The party member's trip was blocked");
                next();
            }
            case 6 -> {
                if (!EndEncounter.fighting(b)) { require(ticks - mark < 100, "The party member did not join the fight"); return; }
                b.onTeleportationDone();
                require(boss.participants.contains(b.getUuid()), "The boss was not told of the late party member");
                // A fall keeps belongings and wakes at the way back, out of the fight.
                a.getInventory().insertStack(new ItemStack(Items.DIAMOND, 5));
                int xp = a.experienceLevel = 7;
                a.damage(end, end.getDamageSources().genericKill(), 1e6f);
                require(a.isDead(), "The first player did not die");
                require(end.getEntitiesByClass(ItemEntity.class, a.getBoundingBox().expand(8), e -> true).isEmpty(), "Belongings dropped in the arena");
                require(boss.removed.contains(a.getUuid()) && EndEncounter.fell(a), "The fallen player is still in the fight");
                a = respawn(server, a);
                require(count(a, Items.DIAMOND) == 5 && a.experienceLevel == xp, "The respawned player lost their belongings");
                next();
            }
            case 7 -> {
                if (a.getEntityWorld() != world || a.getEntityPos().distanceTo(new Vec3d(back.x(), back.y(), back.z())) > .01) {
                    require(ticks - mark < 100, "The fallen player did not wake at the way back: " + a.getEntityPos()); return;
                }
                refusedAtPortal(world, a, "You fell in this fight");
                require(EndEncounter.running() && EndEncounter.fighting(b), "The fight did not go on for the party member");
                // The floor's edge: a fighter off it is set back on it.
                b.setPosition(70.5, 50, .5);
                next();
            }
            case 8 -> {
                if (EndArena.radius(b.getEntityPos()) > 44.5) { require(ticks - mark < 40, "A fighter off the floor was not set back"); return; }
                require(Math.abs(b.getY() - 64) < .01 && b.fallDistance == 0, "The fighter was not set on the floor's edge with the fall forgiven");
                require(!EndEncounter.canTeleport(b, end, new Vec3d(100, 64, 0)) && !EndEncounter.canTeleport(b, world, new Vec3d(0, 100, 0))
                        && EndEncounter.canTeleport(b, end, new Vec3d(10.5, 64, 10.5)), "A fighter's teleports are not held to the floor");
                require(!EndEncounter.canTeleport(c, end, EndArena.arrival()), "A stranger could teleport into a running fight");
                // Protection: no explosion damage; temporary spell blocks still work on the floor.
                BlockPos floor = new BlockPos(10, 63, 10);
                var before = end.getBlockState(floor);
                require(EndArenaManager.keepsTerrain(end, floor), "The arena is not protected");
                end.createExplosion(null, 10.5, 64, 10.5, 4, World.ExplosionSourceType.TNT);
                require(end.getBlockState(floor).equals(before), "An explosion damaged the floor");
                // A bucket cannot be emptied on the floor, aimed at it or used through the item path.
                b.setPitch(90);
                ItemStack lava = new ItemStack(Items.LAVA_BUCKET);
                b.setStackInHand(Hand.MAIN_HAND, lava);
                BlockPos under = b.getBlockPos().down();
                require(end.getBlockState(under).isSolidBlock(end, under), "The fighter is not standing on the floor for the bucket check");
                ActionResult used = b.interactionManager.interactItem(b, end, lava, Hand.MAIN_HAND);
                ActionResult aimed = b.interactionManager.interactBlock(b, end, lava, Hand.MAIN_HAND,
                        new BlockHitResult(Vec3d.ofCenter(under).add(0, .5, 0), Direction.UP, under, false));
                require(used == ActionResult.FAIL && aimed == ActionResult.FAIL, "A bucket was used on the floor: " + used + ", " + aimed);
                require(end.getBlockState(under.up()).isAir() && end.getFluidState(under.up()).isEmpty() && b.getStackInHand(Hand.MAIN_HAND).isOf(Items.LAVA_BUCKET),
                        "Lava was poured on the floor");
                b.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                tempAt = new BlockPos(3, 63, 3);
                tempOriginal = end.getBlockState(tempAt);
                require(TemporaryBlockManager.placeTemporaryBlocks(end, List.of(tempAt), Blocks.MAGMA_BLOCK.getDefaultState(), 20, s -> true) == 1
                        && end.getBlockState(tempAt).isOf(Blocks.MAGMA_BLOCK), "A temporary spell block could not cover the floor");
                // Vanilla mobs are kept out of the arena during a fight.
                enderman = EntityType.ENDERMAN.create(end, SpawnReason.COMMAND);
                enderman.refreshPositionAndAngles(20.5, 64, 20.5, 0, 0);
                end.spawnEntity(enderman);
                require(EndEncounter.blocksSpawn(end, enderman), "A vanilla mob may spawn into a running fight");
                next();
            }
            case 9 -> {
                if (ticks - mark < 25) return;
                require(enderman.isRemoved(), "An enderman stayed in the arena during a fight");
                require(!named.isRemoved() && !wolf.isRemoved(), "The fight removed a named or tamed mob");
                require(end.getBlockState(tempAt).equals(tempOriginal), "The temporary spell block was not restored");
                named.discard(); wolf.discard();
                // A wipe: the last fighter falls.
                b.onTeleportationDone();
                b.damage(end, end.getDamageSources().genericKill(), 1e6f);
                b = respawn(server, b);
                next();
            }
            case 10 -> {
                if (EndEncounter.running() || b.getEntityWorld() != world) { require(ticks - mark < 100, "The wipe did not end the fight"); return; }
                require(boss.stopped && boss.isRemoved(), "The wiped fight's boss was not stopped and removed");
                // No fight runs and the arena is ready: still, a mounted player and a thrown pearl do not get through.
                c.setPosition(30.5, 120, 10.5);
                BoatEntity boat = EntityType.OAK_BOAT.create(world, SpawnReason.COMMAND);
                boat.refreshPositionAndAngles(30.5, 120, 10.5, 0, 0);
                world.spawnEntity(boat);
                require(c.startRiding(boat, true, true), "The stranger could not board the boat");
                require(EndGate.target(world, boat, PORTAL) == null, "A boat carrying a player went through the portal");
                require(!c.hasVehicle() && c.getEntityWorld() == world && c.getEntityPos().distanceTo(new Vec3d(back.x(), back.y(), back.z())) < .01,
                        "The boat's rider was not put beside the portal: " + c.getEntityPos());
                require(boat.getEntityPos().distanceTo(new Vec3d(back.x(), back.y(), back.z())) < .01, "The boat was left in the portal");
                boat.discard();
                EnderPearlEntity pearl = new EnderPearlEntity(world, c, new ItemStack(Items.ENDER_PEARL));
                pearl.refreshPositionAndAngles(30.5, 120.2, 10.5, 0, 0);
                world.spawnEntity(pearl);
                require(EndGate.target(world, pearl, PORTAL) == null && pearl.isRemoved(), "A thrown pearl went through the portal");
                require(c.getEntityWorld() == world && !EndEncounter.running(), "The pearl carried its thrower across");
                // The portal opens again; the first player's second try starts a new fight and wins it.
                TeleportTarget again = EndGate.target(world, a, PORTAL);
                require(again != null && a.teleportTo(again) != null, "The portal stayed shut after a wipe");
                next();
            }
            case 11 -> {
                if (EndEncounter.boss() == null || EndEncounter.boss() == boss) { require(ticks - mark < 300, "No second fight started"); return; }
                a.onTeleportationDone();
                boss = (StandIn) EndEncounter.boss();
                boss.complete = true;
                next();
            }
            case 12 -> {
                if (EndEncounter.running()) { require(ticks - mark < 40, "The win was not noticed"); return; }
                require(EndArenaManager.defeated() && Files.readString(save(server)).contains("\"defeated\": true"), "The win was not saved");
                require(end.getBlockState(new BlockPos(0, 62, 0)).isOf(Blocks.END_PORTAL), "The way home did not open");
                for (int i : EndArena.WIN_GATEWAYS)
                    require(end.getBlockState(EndArena.gateway(i)).isOf(Blocks.END_GATEWAY), "A gateway did not open at " + EndArena.gateway(i));
                // After the win the island is the players': no layout without force.
                require(EndEncounter.layout(server, false).startsWith("The First Wizard is beaten here") && !EndArenaManager.building(),
                        "/ew end layout laid the island out after the win");
                // After the win the End is ordinary: anyone arrives, and no fight starts.
                TeleportTarget after = EndGate.target(world, c, PORTAL);
                require(after != null && c.teleportTo(after) != null, "The portal refused a player after the win");
                next();
            }
            case 13 -> {
                if (ticks - mark < 5) return;
                c.onTeleportationDone();
                require(!EndEncounter.running() && c.getEntityWorld() == end && c.getEntityPos().distanceTo(EndArena.arrival()) < .01,
                        "Entering after the win started a fight or missed the arrival");
                if (!server.isDedicated()) require(!EndArenaManager.clientGuarded(), "The client still protects the island after the win");
                require(EndEncounter.reset(server).startsWith("The End is reset"), "The reset failed");
                require(!EndArenaManager.defeated(), "The reset left the boss defeated");
                require(end.getBlockState(new BlockPos(0, 62, 0)).isOf(Blocks.END_STONE) && end.getBlockState(new BlockPos(0, 63, 0)).isOf(Blocks.OBSIDIAN),
                        "The reset did not close the way home");
                for (int i : EndArena.WIN_GATEWAYS)
                    require(!end.getBlockState(EndArena.gateway(i)).isOf(Blocks.END_GATEWAY), "The reset left a gateway open");
                // Peaceful: the portal and /ew end begin refuse.
                server.setDifficulty(Difficulty.PEACEFUL, true);
                require(EndEncounter.begin(a).contains("Peaceful"), "/ew end begin started a fight on Peaceful");
                refusedAtPortal(world, b, "Peaceful");
                server.setDifficulty(Difficulty.NORMAL, true);
                require(EndEncounter.begin(a).startsWith("Arrived"), "/ew end begin did not start a fight");
                next();
            }
            case 14 -> {
                if (EndEncounter.boss() == null) { require(ticks - mark < 300, "The begun fight did not place its boss"); return; }
                boss = (StandIn) EndEncounter.boss();
                // The player of another party who stood on the island was sent back.
                require(c.getEntityWorld() == world, "A stranger on the island stayed for another party's fight");
                // The difficulty drops to Peaceful mid-fight: it ends without a result and the fighter goes back.
                server.setDifficulty(Difficulty.PEACEFUL, true);
                next();
            }
            case 15 -> {
                if (EndEncounter.running()) { require(ticks - mark < 40, "A fight went on on Peaceful"); return; }
                require(boss.stopped && boss.isRemoved() && !EndArenaManager.defeated(), "Peaceful did not end the fight without a result");
                require(a.getEntityWorld() == world && a.getEntityPos().distanceTo(new Vec3d(back.x(), back.y(), back.z())) < .01,
                        "The fighter was not sent to their way back when Peaceful ended the fight");
                server.setDifficulty(Difficulty.NORMAL, true);
                require(EndEncounter.begin(a).startsWith("Arrived"), "/ew end begin did not start a fight after Peaceful");
                next();
            }
            case 16 -> {
                if (EndEncounter.boss() == null || EndEncounter.boss() == boss) { require(ticks - mark < 300, "The begun fight did not place its boss"); return; }
                boss = (StandIn) EndEncounter.boss();
                // The server stops mid-fight: the fight ends without a result; at their next login nobody is left in the End.
                EndEncounter.interrupt(server);
                require(!EndEncounter.running() && boss.stopped && boss.isRemoved(), "Stopping did not end the fight");
                require(a.getEntityWorld() == end, "Stopping moved the fighter (they go at their next login)");
                EndEncounter.loggedIn(a);
                next();
            }
            case 17 -> {
                if (a.getEntityWorld() == end) { require(ticks - mark < 40, "A player was stranded in the End after a stop"); return; }
                require(a.getEntityPos().distanceTo(new Vec3d(back.x(), back.y(), back.z())) < .01, "The relogged fighter is not at their way back");
                require(EndArenaManager.status().contains("undefeated"), "Status does not report the boss");
                Files.writeString(Path.of("END_PASSED.txt"), "No dragon fight or dragon; no spikes in the biome; the arena laid out at server start; "
                        + "refused players (not ready, held, another party, fallen, Peaceful, mounted) put beside the portal at once and told, with nothing saved; "
                        + "a chest and shulker box holding the layout and reported, then a forced layout leaving every item and both boxes as drops; "
                        + "dragon eggs set down beside the arrival; an item lying there kept; the arena laid over a planted spike, crystal, cage, fountain, "
                        + "raised terrain and old gateway with a flat empty floor; a player far out in the End left alone at login, one put on the floor by "
                        + "command sent back; one saved write per admission; a party member joining; a fall keeping belongings; the floor's edge, teleports, "
                        + "explosions, buckets, temporary spell blocks; mobs swept but a named and a tamed one kept; a wipe; a boat with a rider and a pearl "
                        + "refused; a win with the way home and gateways and /ew end layout refused after it; the client told of the protection; a reset; "
                        + "Peaceful refusing and ending a fight; a stop mid-fight and logins sending everyone back passed.\n");
                System.out.println("END TAKEOVER SMOKE PASSED");
                server.stop(false);
                stage = 99;
            }
            default -> {}
        }
    }

    /** The player steps into the portal and is refused: beside it at once, told why, and nothing is saved. */
    private void refusedAtPortal(ServerWorld world, ServerPlayerEntity player, String reason) {
        player.setPosition(30.5, 120, 10.5);
        int heard = CHAT.getOrDefault(player.getUuid(), 0), saved = EndArenaManager.writes;
        require(EndGate.target(world, player, PORTAL) == null, player.getName().getString() + " was let through (expected: " + reason + ")");
        require(player.getEntityWorld() == world && player.getEntityPos().distanceTo(new Vec3d(back.x(), back.y(), back.z())) < .01,
                "A refused player was left in the portal: " + player.getEntityPos());
        require(CHAT.getOrDefault(player.getUuid(), 0) == heard + 1 && LAST.getOrDefault(player.getUuid(), "").toLowerCase(java.util.Locale.ROOT).contains(reason.toLowerCase(java.util.Locale.ROOT)),
                "A refused player was not told once why (" + reason + "): " + LAST.get(player.getUuid()));
        require(EndArenaManager.writes == saved, "A refusal saved the End's record");
    }

    /** The forced layout broke the chest and shulker box as a player would: every item and both boxes lie as drops. */
    private static void checkDrops(ServerWorld end) {
        require(!end.getBlockState(CHEST).isOf(Blocks.CHEST) && !end.getBlockState(SHULKER).isOf(Blocks.SHULKER_BOX), "The forced layout left a container");
        Map<net.minecraft.item.Item, Integer> loose = new java.util.HashMap<>();
        int boxed = 0, boxes = 0;
        List<ItemEntity> items = end.getEntitiesByClass(ItemEntity.class, EndArena.footprintBox(), e -> true);
        for (ItemEntity item : items) {
            ItemStack stack = item.getStack();
            loose.merge(stack.getItem(), stack.getCount(), Integer::sum);
            if (stack.isOf(Items.SHULKER_BOX)) {
                boxes += stack.getCount();
                var contents = stack.get(DataComponentTypes.CONTAINER);
                if (contents != null) for (ItemStack in : contents.iterateNonEmpty()) if (in.isOf(Items.EMERALD)) boxed += in.getCount();
            }
        }
        require(loose.getOrDefault(Items.CHEST, 0) == 1 && loose.getOrDefault(Items.DIAMOND, 0) == 3 && loose.getOrDefault(Items.STONE, 0) == 10,
                "The chest and its contents did not all drop: " + loose);
        require(boxes == 1 && boxed == 4 && loose.getOrDefault(Items.EMERALD, 0) == 0, "The shulker box did not drop whole with its contents: " + loose);
        require(loose.getOrDefault(Items.GOLD_INGOT, 0) == 2, "An item lying on the island was lost: " + loose);
        for (BlockPos old : List.of(new BlockPos(5, 64, 5), new BlockPos(-20, 64, -3)))
            require(!end.getBlockState(old).isOf(Blocks.DRAGON_EGG), "A dragon egg was left on the floor at " + old);
        int eggs = 0;
        for (int x = 0; x <= 12; x++) for (int y = 60; y <= 70; y++)
            if (end.getBlockState(new BlockPos(x, y, 45)).isOf(Blocks.DRAGON_EGG)) eggs++;
        require(eggs == 2 && end.getBlockState(new BlockPos(2, 64, 45)).isOf(Blocks.DRAGON_EGG) && end.getBlockState(new BlockPos(3, 64, 45)).isOf(Blocks.DRAGON_EGG),
                "The dragon eggs were not set down beside the arrival (" + eggs + " found)");
        items.forEach(Entity::discard);
    }

    private void next() { stage++; mark = ticks; System.out.println("END SMOKE stage " + stage + ": " + EndEncounter.status()); }

    private static Path save(MinecraftServer server) { return server.getSavePath(WorldSavePath.ROOT).resolve("elementalwands").resolve("end.json"); }

    /** A stone platform in the Overworld with a three-by-three End portal in its middle. */
    private static void stagePortal(ServerWorld world) {
        for (int x = -1; x <= 3; x++) for (int z = -1; z <= 2; z++) { world.setChunkForced(x, z, true); world.getChunk(x, z); }
        for (int x = 24; x <= 36; x++)
            for (int z = 4; z <= 16; z++) {
                world.setBlockState(new BlockPos(x, 119, z), Blocks.STONE.getDefaultState());
                for (int y = 120; y <= 124; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
            }
        for (int x = 29; x <= 31; x++) for (int z = 9; z <= 11; z++) world.setBlockState(new BlockPos(x, 120, z), Blocks.END_PORTAL.getDefaultState());
    }

    /** What vanilla would have left on an island visited before the takeover. Fresh worlds must have no spikes. */
    private static void plantLeftovers(ServerWorld end) {
        List<EndSpikeFeature.Spike> spikes = EndSpikeFeature.getSpikes(end);
        for (EndSpikeFeature.Spike s : spikes) {
            end.getChunk(s.getCenterX() >> 4, s.getCenterZ() >> 4);
            require(!end.getBlockState(new BlockPos(s.getCenterX(), s.getHeight() - 1, s.getCenterZ())).isOf(Blocks.OBSIDIAN),
                    "A spike was generated on the main island");
        }
        EndSpikeFeature.Spike s = spikes.get(0);
        int r = s.getRadius();
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r + 1) continue;
                for (int y = 30; y < s.getHeight(); y++) end.setBlockState(new BlockPos(s.getCenterX() + x, y, s.getCenterZ() + z), Blocks.OBSIDIAN.getDefaultState());
                end.setBlockState(new BlockPos(s.getCenterX() + x, s.getHeight() + 2, s.getCenterZ() + z), Blocks.IRON_BARS.getDefaultState());
            }
        EndCrystalEntity crystal = new EndCrystalEntity(end, s.getCenterX() + .5, s.getHeight() + 1, s.getCenterZ() + .5);
        end.spawnEntity(crystal);
        for (int y = 60; y <= 67; y++) end.setBlockState(new BlockPos(0, y, 0), Blocks.BEDROCK.getDefaultState());
        end.setBlockState(new BlockPos(1, 64, 0), Blocks.END_PORTAL.getDefaultState());
        for (int y = 64; y <= 70; y++) end.setBlockState(new BlockPos(12, y, 12), Blocks.END_STONE.getDefaultState());
        BlockPos old = EndArena.gateway(3);
        end.getChunk(old.getX() >> 4, old.getZ() >> 4);
        end.setBlockState(old, Blocks.END_GATEWAY.getDefaultState());
        end.setBlockState(old.up(), Blocks.BEDROCK.getDefaultState());
        end.setBlockState(old.down(), Blocks.BEDROCK.getDefaultState());
    }

    private static void checkArena(ServerWorld end) {
        require(end.getAliveEnderDragons().isEmpty(), "A dragon is alive in the End");
        for (int x = -45; x <= 45; x++)
            for (int z = -45; z <= 45; z++) {
                if (x * x + z * z > 1980.25) continue;
                require(!end.getBlockState(new BlockPos(x, 63, z)).isAir(), "Floor hole at " + x + ", " + z);
                for (int y = 64; y <= 120; y++)
                    require(end.getBlockState(new BlockPos(x, y, z)).isAir(), "Something stands on the floor at " + new BlockPos(x, y, z));
            }
        EndSpikeFeature.Spike s = EndSpikeFeature.getSpikes(end).get(0);
        for (int y = 30; y <= 63; y++)
            require(!end.getBlockState(new BlockPos(s.getCenterX(), y, s.getCenterZ())).isOf(Blocks.OBSIDIAN), "The planted spike's foot was left at y=" + y);
        require(end.getEntitiesByClass(EndCrystalEntity.class, new Box(-80, 0, -80, 80, 200, 80), e -> true).isEmpty(), "An end crystal was left");
        require(!end.getBlockState(EndArena.gateway(3)).isOf(Blocks.END_GATEWAY), "An old vanilla gateway was left");
        for (int y = 0; y <= 63; y++) require(!end.getBlockState(new BlockPos(0, y, 0)).isOf(Blocks.BEDROCK), "The fountain's bedrock was left at y=" + y);
        require(end.getBlockState(new BlockPos(0, 63, 0)).isOf(Blocks.OBSIDIAN), "The floor's centre is not the builder's");
        require(end.getBlockState(new BlockPos(0, 64, 42)).isAir() && end.getBlockState(new BlockPos(0, 65, 42)).isAir(), "The arrival spot is blocked");
    }

    private static int count(ServerPlayerEntity player, net.minecraft.item.Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().size(); i++) if (player.getInventory().getStack(i).isOf(item)) n += player.getInventory().getStack(i).getCount();
        return n;
    }

    /** A simulated player has no client to press Respawn; do what the respawn packet does. */
    private static ServerPlayerEntity respawn(MinecraftServer server, ServerPlayerEntity dead) {
        var handler = dead.networkHandler;
        handler.player = server.getPlayerManager().respawnPlayer(dead, false, Entity.RemovalReason.KILLED);
        return handler.player;
    }

    @SuppressWarnings("unchecked")
    private static ServerPlayerEntity player(MinecraftServer server, ServerWorld world, String name, double x, double y, double z) throws Exception {
        UUID uuid = UUID.randomUUID();
        GameProfile profile = new GameProfile(uuid, name);
        ServerPlayerEntity player = new ServerPlayerEntity(server, world, profile, SyncedClientOptions.createDefault());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND) {
            @Override public void send(Packet<?> packet) { hear(uuid, packet); }
            @Override public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener) { hear(uuid, packet); }
            @Override public void send(Packet<?> packet, io.netty.channel.ChannelFutureListener listener, boolean flush) { hear(uuid, packet); }
            @Override public void flush() {}
            @Override public boolean isOpen() { return true; }
        };
        player.networkHandler = new ServerPlayNetworkHandler(server, connection, player, ConnectedClientData.createDefault(profile, false));
        player.setPosition(x, y, z);
        var field = PlayerManager.class.getDeclaredField("playerMap");
        field.setAccessible(true);
        ((Map<UUID, ServerPlayerEntity>) field.get(server.getPlayerManager())).put(uuid, player);
        server.getPlayerManager().getPlayerList().add(player);
        world.onPlayerConnected(player);
        player.changeGameMode(GameMode.SURVIVAL);
        return player;
    }

    private static void hear(UUID player, Packet<?> packet) {
        if (packet instanceof GameMessageS2CPacket message && !message.overlay()) {
            CHAT.merge(player, 1, Integer::sum);
            LAST.put(player, message.content().getString());
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
