package com.anton.elementalwands.church;

import com.anton.elementalwands.arena.*;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.registry.*;
import java.nio.file.*;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.*;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;

/** Isolated server integration: real item/anchor interaction and durable site lifecycle. */
public final class GuardianChurchSmokeMod implements ModInitializer {
    private int ticks,stage;
    private ServerPlayerEntity player;
    private GuardianChurchManager.Site site;
    private FracturedGuardianEntity guardian;
    private String last="";
    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {try {tick(server);}catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("CHURCH_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}});
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private static ServerPlayerEntity player(MinecraftServer server,int y) throws Exception {
        var method=GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);method.setAccessible(true);
        return (ServerPlayerEntity)method.invoke(null,server,UUID.randomUUID(),"ChurchTester",.5,(double)y,.5);
    }
    private void tick(MinecraftServer server) throws Exception {
        ticks++;check(ticks<2200,"Church test timed out at "+stage+" / "+last);
        var world=server.getOverworld();
        String status=GuardianArenaManager.status();if(!status.split(" — ")[0].equals(last)){last=status.split(" — ")[0];System.out.println("CHURCH SMOKE: "+last+" stage "+stage);}
        if (Boolean.getBoolean("ew.church.recovery")) {
            if(ticks==100) {
                site=GuardianChurchManager.sites().getFirst();
                check(site.phase==GuardianChurchManager.Phase.RESTORED,"Victory was lost on restart");
                for(int x=-5;x<=5;x++)for(int z=-3;z<=8;z++)world.getChunk(x,z);
                var chest=(ChestBlockEntity)world.getBlockEntity(site.at(-5,-1,35));
                check(chest.isEmpty(),"Restart refilled a previously emptied reward chest");
                check(!GuardianChurchManager.busy(),"Restart left the church active");
                Files.writeString(Path.of("CHURCH_RECOVERY_PASSED.txt"),"Completed church and collected loot survived restart without regeneration.\n");server.stop(false);
            }
            return;
        }
        if(ticks==30){
            GuardianChurchLootChecks.run(world);
            for(int x=-6;x<=6;x++)for(int z=-6;z<=9;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            GuardianChurchTerrainChecks.run(world);
            int ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);player=player(server,ground);
            String placed=GuardianChurchManager.place(player.getCommandSource());System.out.println("CHURCH PLACE: "+placed);check(placed.startsWith("Building"),placed);
            site=GuardianChurchManager.sites().getFirst();stage=1;
            var template=world.getStructureTemplateManager().getTemplate(net.minecraft.util.Identifier.of("elementalwands","guardian_church"));
            check(template.isPresent(),"Worldgen template missing");check(template.get().getSize().equals(new net.minecraft.util.math.Vec3i(33,36,53)),"Worldgen template dimensions differ from preview");
            check(world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.STRUCTURE).containsId(net.minecraft.util.Identifier.of("elementalwands","guardian_church")),"Church worldgen structure failed to register");
        }
        if(stage==1 && site.phase==GuardianChurchManager.Phase.RUINED){
            GuardianChurchUpgradeChecks.run(world);
            check(GuardianChurchManager.locate(player.getCommandSource()).contains("X 0"),"Locator cannot find the test church");
            check(world.getBlockState(site.socket()).isOf(ModBlocks.GUARDIAN_SOCKET),"Socket not built");
            check(world.getBlockEntity(site.socket()) instanceof GuardianSocketEntity,"Socket block entity missing");
            check(site.guardian==null || world.getEntity(UUID.fromString(site.guardian))==null,"Living keeper is present before the ritual");
            check(world.getBlockState(site.at(0,5,-4)).isOf(Blocks.CHISELED_DEEPSLATE),"Block-built effigy is missing its recessed face");
            var chest=(ChestBlockEntity)world.getBlockEntity(site.offering());check(chest!=null,"Offering chest missing");
            check(chest.getStack(0).isOf(ModItems.GUARDIAN_HEART),"Heart missing");check(chest.getStack(1).isOf(Items.WRITTEN_BOOK),"Lore book missing");
            check(((ChestBlockEntity)world.getBlockEntity(site.at(-5,-1,35))).isEmpty(),"Reward loot available before victory");
            check(!world.breakBlock(site.at(7,0,25),true),"Ward allowed block breaking");
            check(com.anton.elementalwands.util.TemporaryBlockManager.placeTrackedTemporaryBlocks(world,java.util.List.of(site.anchor()),Blocks.COBBLESTONE.getDefaultState(),40,b -> true).isEmpty(),"Blocked spell placement was incorrectly counted as placed");
            var old=chest.getStack(0).copy();player.setPosition(site.x-1.5,site.y-1,site.z-7.5);player.setSneaking(true);player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,ItemStack.EMPTY);
            check(GuardianChurchManager.interact(player,site.socket()).startsWith("The heart returns"),"Lost heart recall failed");
            player.setSneaking(false);player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,old);
            check(GuardianChurchManager.interact(player,site.socket()).startsWith("Place this church"),"Recalled old heart still activates");
            player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,ItemStack.EMPTY);player.setSneaking(true);GuardianChurchManager.interact(player,site.socket());player.setSneaking(false);
            var valid=player.getInventory().getStack(0); // offerOrDrop places the recalled heart into the empty selected slot.
            check(valid.isOf(ModItems.GUARDIAN_HEART),"Recalled heart not delivered");
            // Simulate trees growing through a protected ruin after worldgen.
            var mutation=GuardianChurchManager.class.getDeclaredField("mutation");mutation.setAccessible(true);mutation.setBoolean(null,true);
            try {
                world.setBlockState(site.at(0,4,-3),Blocks.OAK_LOG.getDefaultState());
                world.setBlockState(site.at(-2,4,-8),Blocks.OAK_LEAVES.getDefaultState());
                world.setBlockState(site.at(5,-1,-4),Blocks.BIRCH_LOG.getDefaultState());
            } finally {mutation.setBoolean(null,false);}
            check(world.breakBlock(site.at(5,-1,-4),false),"Ruin protection still prevents breaking trees");
            server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
            check(GuardianChurchManager.interact(player,site.socket()).contains("Peaceful"),"Peaceful ritual was admitted");
            check(world.getBlockState(site.at(0,4,-3)).isOf(Blocks.OAK_LOG)
                    && world.getBlockState(site.at(-2,4,-8)).isOf(Blocks.OAK_LEAVES),"Rejected ritual cleared shaft vegetation");
            check(!valid.isEmpty(),"Rejected ritual consumed heart");
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
            String ritual=GuardianChurchManager.interact(player,site.socket());
            check(!world.getBlockState(site.at(0,4,-3)).isOf(Blocks.OAK_LOG),"Ritual did not clear the Guardian's tree obstruction");
            check(world.getBlockState(site.at(-2,4,-8)).isAir(),"Ritual did not clear the player's overhead leaves");
            System.out.println("CHURCH RITUAL: "+ritual);check(ritual.startsWith("The heart answers"),ritual);
            check(valid.isEmpty(),"Accepted heart was not consumed");guardian=(FracturedGuardianEntity)world.getEntity(UUID.fromString(site.guardian));check(guardian.isArenaHidden(),"Guardian visible during ritual ascent");
            check(player.getEntityPos().subtract(guardian.getEntityPos()).horizontalLength()>=9.99,"Guardian overlaps the player's lift/camera");stage=2;
        }
        if(stage==2 && status.startsWith("Arena: FIGHT")){ GuardianArenaManager.stop();stage=3; }
        if(stage==3 && !GuardianArenaManager.hasActiveArena()){
            check(site.phase==GuardianChurchManager.Phase.RUINED,"Aborted fight granted restoration");
            player.setPosition(site.x-1.5,site.y-1,site.z-7.5);player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,ItemStack.EMPTY);player.setSneaking(true);GuardianChurchManager.interact(player,site.socket());player.setSneaking(false);
            var retry=GuardianChurchManager.interact(player,site.socket());
            check(retry.startsWith("The heart answers") && GuardianArenaManager.hasActiveArena(),"Socket did not start the retry");guardian=(FracturedGuardianEntity)world.getEntity(UUID.fromString(site.guardian));stage=4;
        }
        if(player!=null)player.setHealth(player.getMaxHealth());
        if(stage==4 && status.startsWith("Arena: FIGHT")){guardian.kill(world);check(site.phase==GuardianChurchManager.Phase.RESTORING,"Real boss death did not commit victory");stage=5;}
        if(stage==5 && !GuardianArenaManager.hasActiveArena()){
            check(site.phase==GuardianChurchManager.Phase.RESTORED,"Church was not restored before return");
            for (int x:new int[]{-5,5}) {
                var reward=(ChestBlockEntity)world.getBlockEntity(site.at(x,-1,35));
                var expected=GuardianChurchLoot.roll(world,site.anchor(),x);
                for (int slot=0;slot<27;slot++) check(ItemStack.areEqual(reward.getStack(slot),expected.get(slot)),"Victory chest differs from its site-specific roll");
            }
            var chest=(ChestBlockEntity)world.getBlockEntity(site.at(-5,-1,35));chest.clear();chest.markDirty();
            check(GuardianChurchManager.interact(player,site.socket()).contains("already been restored"),"Completed church can be farmed");
            var roof=site.at(7,20,25);check(world.getBlockState(roof).isOf(Blocks.WARPED_PLANKS),"Collapsed roof was not rebuilt");
            check(!GuardianChurchManager.protectedBlock(world,site.at(7,0,25)),"Completed church is still warded");
            Files.writeString(Path.of("CHURCH_PASSED.txt"),"Placement, worldgen resources, socket, heart/book, recall invalidation, sealed admission, abort/retry, real boss death, restoration, loot, and one-time completion passed.\n");System.out.println("CHURCH SMOKE PASSED");server.stop(false);
        }
    }
}
