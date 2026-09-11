package com.anton.elementalwands.arena;

import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.registry.ModBlocks;
import com.anton.elementalwands.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/** Test-only Fabric entrypoint, packaged solely by the optional arena smoke task. No client is simulated. */
public final class GuardianArenaSmokeMod implements ModInitializer {
    private int ticks,stage,fightTicks,descentTicks;
    private net.minecraft.entity.ItemEntity lateDrop;
    private net.minecraft.entity.ExperienceOrbEntity lateXp;
    private static int positionCorrections;
    private ServerPlayerEntity first,second;
    private FracturedGuardianEntity guardian;
    private int ground,floor;
    private String previous="";
    private GuardianArenaJournal.Arena recovery;
    private UUID recoveryObserver;
    @Override public void onInitialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (Boolean.getBoolean("ew.arena.recovery")) try {
                var recoveryState=new GuardianArenaJournal(server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                        .resolve("elementalwands/guardian-arena.json")).read();
                recovery=recoveryState.arena;
                recoveryObserver=UUID.fromString(recoveryState.gameModes.entrySet().stream().filter(e -> e.getValue().equals("adventure")).findFirst().orElseThrow().getKey());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { tick(server); }
            catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(java.nio.file.Path.of("SMOKE_FAILED.txt"),failure.toString()); } catch (Exception ignored) {}
                server.stop(false);
            }
        });
    }
    private void tick(MinecraftServer server) throws Exception {
        ticks++;
        String status=GuardianArenaManager.status();
        String phase=status.split(" — ")[0];
        if (!phase.equals(previous)) { System.out.println("ARENA SMOKE: "+status); previous=phase; }
        require(ticks<2400,"Smoke test exceeded 90 seconds: "+status);
        if (Boolean.getBoolean("ew.arena.recovery")) {
            if (ticks==150) {
                require(status.equals("No active arena."),"Restart did not finish recovery: "+status);
                require(recovery!=null,"No interrupted encounter was available to test");
                require(server.getOverworld().getBlockState(new BlockPos(recovery.x()+1,recovery.floor(),recovery.z()+1)).isAir(),"Reload left a floor behind");
                require(server.getOverworld().getBlockState(new BlockPos(recovery.x()+64,recovery.top(),recovery.z())).isAir(),"Reload left a wall behind");
                Entity recovered=server.getOverworld().getEntity(UUID.fromString(recovery.guardian()));
                require(recovered!=null && !recovered.hasNoGravity() && !recovered.isInvulnerable(),"Reload left the Guardian frozen or invulnerable");
                require(Math.abs(recovered.getY()-recovery.guardianHome().y())<2,"Reload left the Guardian in the sky");
                require(!server.getOverworld().getForcedChunks().contains(net.minecraft.util.math.ChunkPos.toLong(0,0)),"Recovery leaked its chunk force");
                require(server.getOverworld().getForcedChunks().contains(net.minecraft.util.math.ChunkPos.toLong(-1,-1)),"Recovery removed a pre-existing chunk force");
                second=player(server,recoveryObserver,"ArenaTwo",recovery.x(),recovery.floor()+10,recovery.z());
                second.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
                net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(second.networkHandler,null,server);
            }
            if(ticks==151) {
                require(second.interactionManager.getGameMode()==net.minecraft.world.GameMode.ADVENTURE,"Offline spectator's original game mode was not recovered on reconnect");
                require(second.getY()<recovery.floor(),"Offline spectator was not returned to ground after restart");
                Files.writeString(java.nio.file.Path.of("SMOKE_RECOVERY_PASSED.txt"),"Interrupted arena recovered; offline spectator rejoined safely in original Adventure mode.\n");
                System.out.println("ARENA SMOKE RECOVERY PASSED"); server.stop(false);
            }
            return;
        }
        ServerWorld world=server.getOverworld();
        if (ticks==30) {
            for (int x=-5;x<=5;x++) for (int z=-5;z<=5;z++) { world.setChunkForced(x,z,true); world.getChunk(x,z); }
            world.setChunkForced(0,0,false);
            var safeMethod=GuardianArenaManager.class.getDeclaredMethod("findSafe",ServerWorld.class,Vec3d.class,Entity.class);
            safeMethod.setAccessible(true);
            Vec3d unloadedSafe=(Vec3d)safeMethod.invoke(null,world,new Vec3d(10000.5,-1000,10000.5),null);
            require(unloadedSafe!=null && unloadedSafe.y>world.getBottomY(),"Unloaded waiting terrain produced a void destination");
            require(world.getBlockState(BlockPos.ofFloored(unloadedSafe).down()).isSolidBlock(world,BlockPos.ofFloored(unloadedSafe).down()),"Unloaded terrain return lacks support");
            ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
            first=player(server,UUID.randomUUID(),"ArenaOne",8.5,ground,0.5);
            second=player(server,UUID.randomUUID(),"ArenaTwo",-8.5,ground,0.5);
            second.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
            guardian=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,world);
            guardian.setPosition(.5,ground,.5); guardian.stopReview(); world.spawnEntity(guardian);
            first.setOnGround(true); second.setOnGround(true);
            // The old required outside waiting area is deliberately a void. Admission must still work.
            for(int x=68;x<=102;x++)for(int z=-26;z<=26;z++)for(int y=world.getBottomY();y<=ground;y++)
                world.setBlockState(new BlockPos(x,y,z),Blocks.AIR.getDefaultState(),net.minecraft.block.Block.FORCE_STATE);
            String result=GuardianArenaManager.start(first,guardian);
            System.out.println("ARENA SMOKE START: "+result);
            require(result.startsWith("Arena sealed"),result);
            require(!GuardianArenaManager.canTeleport(first,world,new Vec3d(200,ground,0)),"Boundary was not sealed during wall formation");
            require(!GuardianArenaManager.canCast(first),"Cinematic permits spell casts");
            stage=1;
        }
        if (stage==1 && status.startsWith("Arena: ASCENT")) {
            require(first.getVehicle() instanceof com.anton.elementalwands.entity.GuardianLiftEntity,"Player is teleported instead of carried");
            require(guardian.isArenaHidden() && !guardian.hasVehicle(),"Guardian appeared on the players' ascent");
            require(Math.abs(first.lastY-first.lastRenderY)<.001,"First-person and third-person lift sampling disagree");
            require(first.lastY<=first.getY(),"Ascent interpolation points backward");
            var shells=world.getEntitiesByClass(com.anton.elementalwands.entity.GuardianArenaEntity.class,
                    new net.minecraft.util.math.Box(-128,ground-4,-128,128,320,128),e -> !e.isRemoved());
            require(shells.size()==1,"Expected exactly one visible architecture shell");
            var shell=shells.getFirst();
            for (float delta:new float[]{0,.5f,1}) {
                double visibleFloor=shell.getY()+shell.floor(delta);
                double visibleFeet=first.lastY+(first.getY()-first.lastY)*delta;
                require(Math.abs(visibleFloor-visibleFeet)<.0001,"Visible floor and carried camera drift during interpolation");
            }
            Entity vehicle=first.getVehicle(); first.stopRiding();
            require(first.getVehicle()==vehicle,"Sneaking can dismount the cinematic carrier");
        }
        if(stage==1 && status.startsWith("Arena: ARRIVAL")) {
            require(!guardian.isArenaHidden() && guardian.hasVehicle(),"Sky arrival did not reveal/carry the guardian");
            require(guardian.getY()>=first.getY(),"Arrival fell beneath the arena");
            require(!GuardianArenaManager.canCast(first),"Arrival permits attacks before landing");
        }
        if (stage==1 && status.startsWith("Arena: FIGHT")) {
            require(!first.hasVehicle() && !guardian.hasVehicle(),"Combat begins while actors are still mounted");
            require(positionCorrections<20,"Lift still sends player position corrections every tick: "+positionCorrections);
            stage=2; floor=(int)Math.floor(first.getY())-1;
            require(world.getBlockState(new BlockPos(1,floor,1)).isOf(ModBlocks.ARENA_STONE),"Combat started without a physical floor");
            require(world.getBlockState(new BlockPos(0,world.getTopYInclusive(),0)).isAir(),"Arena has a ceiling");
            require(ModBlocks.ARENA_STONE.getDefaultState().getRenderType()==net.minecraft.block.BlockRenderType.INVISIBLE,"Backing floor still draws over the architecture");
            require(ModBlocks.ARENA_DARK.getDefaultState().getRenderType()==net.minecraft.block.BlockRenderType.INVISIBLE,"Backing walls still cause z-fighting");
            require(ModBlocks.ARENA_LIGHT.getDefaultState().getLuminance()==0,"Construction still triggers a room full of light emitters");
            require(!world.setBlockState(new BlockPos(1,floor,1),Blocks.AIR.getDefaultState()),"World mutation bypassed foundation protection");
            double before=first.getX(); first.requestTeleport(200,first.getY(),0);
            require(first.getX()==before,"Same-world teleport escaped");
            require(!first.teleport(server.getWorld(World.NETHER),0,80,0,java.util.Set.of(),0,0,false),"Dimension teleport escaped");
            first.requestTeleport(before+2,first.getY(),first.getZ());
            require(first.getX()==before+2,"Valid in-arena teleport was blocked");
            second.kill(world);
            require(!GuardianArenaManager.eligible(guardian,second),"Actual death did not eliminate the player");
        }
        if (stage==2) {
            if(fightTicks>0) {
                second=server.getPlayerManager().getPlayer(second.getUuid());
                require(second.isAlive() && second.isSpectator(),"Fallen player did not automatically respawn as a spectator");
                require(second.getY()>floor && Math.abs(second.getX())<64,"Spectator is not watching inside the arena");
                require(!GuardianArenaManager.eligible(guardian,second) && !GuardianArenaManager.canCast(second),"Spectator can fight or cast");
                require(!GuardianArenaManager.canTeleport(second,world,new Vec3d(200,floor+5,0)),"Spectator can leave the sealed fight");
                require(GuardianArenaManager.canTeleport(second,world,new Vec3d(20,floor+12,20)),"Spectator cannot move within the arena");
                if(fightTicks>1) {
                    var combatField=FracturedGuardianEntity.class.getDeclaredField("combat");combatField.setAccessible(true);
                    var combat=combatField.get(guardian);var barField=combat.getClass().getDeclaredField("bar");barField.setAccessible(true);
                    require(((net.minecraft.entity.boss.ServerBossBar)barField.get(combat)).getPlayers().contains(second),"Spectator cannot see the boss health bar");
                }
                float health=guardian.getHealth();
                guardian.damage(world,world.getDamageSources().playerAttack(second),5);
                require(guardian.getHealth()==health,"Eliminated spectator damaged the boss");
            }
            first.setHealth(first.getMaxHealth());
            if (++fightTicks==25) {
                com.anton.elementalwands.util.TitanDomeManager.startDome(world,first);
                com.anton.elementalwands.util.HollowPurpleChargeManager.startCharge(world,first);
                guardian.kill(world); stage=3;
            }
        }
        if (stage==3 && status.startsWith("Arena: DESCENT")) {
            require(!com.anton.elementalwands.util.HollowPurpleChargeManager.isCharging(world,first),"Space charge survived encounter return");
            var domesField=com.anton.elementalwands.util.TitanDomeManager.class.getDeclaredField("DOMES");domesField.setAccessible(true);
            require(((java.util.Map<?,?>)domesField.get(null)).isEmpty(),"Stone dome survived encounter return");
            if(++descentTicks==20) {
                lateDrop=new net.minecraft.entity.ItemEntity(world,0,floor+2,0,new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND));world.spawnEntity(lateDrop);
                lateXp=new net.minecraft.entity.ExperienceOrbEntity(world,0,floor+2,0,3);world.spawnEntity(lateXp);
            }
            if(descentTicks==22) {
                require(lateDrop.getY()<floor && lateXp.getY()<floor,"Late death drops/XP were lost during cleanup");
            }
        }
        if (stage==3 && status.equals("No active arena.")) {
            require(Math.abs(first.getY()-ground)<2,"Survivor did not return to the gathering ground");
            require(second.interactionManager.getGameMode()==net.minecraft.world.GameMode.ADVENTURE && second.getY()<floor,"Spectator was not returned with the original Adventure mode");
            require(world.getBlockState(new BlockPos(1,floor,1)).isAir(),"Cleanup left an arena floor");
            require(!world.getBlockState(new BlockPos(0,ground-1,0)).isAir(),"Original ground was deleted");
            require(!world.getForcedChunks().contains(net.minecraft.util.math.ChunkPos.toLong(0,0)),"Encounter leaked its chunk force");
            require(world.getForcedChunks().contains(net.minecraft.util.math.ChunkPos.toLong(-1,-1)),"Encounter removed a pre-existing chunk force");
            guardian=new FracturedGuardianEntity(ModEntities.FRACTURED_GUARDIAN,world);
            guardian.setPosition(.5,ground,.5); guardian.stopReview(); world.spawnEntity(guardian);
            first.setOnGround(true);
            String result=GuardianArenaManager.start(first,guardian);
            require(result.startsWith("Arena sealed"),"Second encounter could not start: "+result);
            stage=4;
        }
        if (stage==4 && status.startsWith("Arena: FIGHT")) {
            first.kill(world);
            if(second.isAlive() && GuardianArenaManager.eligible(guardian,second)) second.kill(world);
            stage=5;
        }
        if(stage==5 && status.startsWith("Arena: DESCENT")) {
            first=server.getPlayerManager().getPlayer(first.getUuid());
            second=server.getPlayerManager().getPlayer(second.getUuid());
            require(first.isAlive() && first.isSpectator() && first.getY()>floor,"Wiped party did not spectate during withdrawal");
        }
        if(stage==5 && status.equals("No active arena.")) {
            require(first.isAlive() && !first.isSpectator() && first.getY()>=ground-.1,"Wiped player did not return safely to Survival");
            require(second.interactionManager.getGameMode()==net.minecraft.world.GameMode.ADVENTURE,"Wiped observer lost original mode");
            first.setOnGround(true);
            require(GuardianArenaManager.start(first,guardian).startsWith("Arena sealed"),"Retry after party wipe failed");
            stage=6;
        }
        if (stage==6 && status.startsWith("Arena: FIGHT")) {
            second.kill(world);stage=7;
        } else if(stage==7) {
            second=server.getPlayerManager().getPlayer(second.getUuid());
            require(second.isSpectator(),"Recovery test needs a saved spectator");
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(second.networkHandler,server);
            server.getPlayerManager().remove(second); // Leave its saved spectator mode and return receipt offline across restart.
            Files.writeString(java.nio.file.Path.of("SMOKE_PASSED.txt"),"Formation, solid floor, no ceiling, protected blocks, teleports, automatic spectator respawn, original-mode restoration, exterior-void admission, party-wipe respawn, exclusion, victory descent, cleanup, and second admission passed. Stopping during combat for restart recovery.\n");
            System.out.println("ARENA SMOKE PASSED — stopping mid-fight to exercise recovery");
            server.stop(false);
        }
    }
    @SuppressWarnings("unchecked")
    private static ServerPlayerEntity player(MinecraftServer server,UUID uuid,String name,double x,double y,double z) throws Exception {
        GameProfile profile=new GameProfile(uuid,name);
        ServerPlayerEntity player=new ServerPlayerEntity(server,server.getOverworld(),profile,SyncedClientOptions.createDefault());
        ClientConnection connection=new ClientConnection(NetworkSide.SERVERBOUND) {
            @Override public void send(Packet<?> packet) { count(packet); }
            @Override public void send(Packet<?> packet,io.netty.channel.ChannelFutureListener listener) { count(packet); }
            @Override public void send(Packet<?> packet,io.netty.channel.ChannelFutureListener listener,boolean flush) { count(packet); }
            @Override public void flush() {}
            @Override public boolean isOpen() { return true; }
        };
        player.networkHandler=new ServerPlayNetworkHandler(server,connection,player,ConnectedClientData.createDefault(profile,false));
        player.setPosition(x,y,z);
        var field=PlayerManager.class.getDeclaredField("playerMap"); field.setAccessible(true);
        ((Map<UUID,ServerPlayerEntity>)field.get(server.getPlayerManager())).put(uuid,player);
        server.getPlayerManager().getPlayerList().add(player);
        server.getOverworld().onPlayerConnected(player);
        return player;
    }
    private static void count(Packet<?> packet) {
        if (packet instanceof net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket) positionCorrections++;
    }
    private static void require(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
}
