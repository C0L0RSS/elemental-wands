package com.anton.elementalwands.util;

import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.registry.ModSpellBlocks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import java.nio.file.*;
import java.util.*;

/** Disposable real-server checks for the September audit fixes. */
public final class AuditFixSmokeMod implements ModInitializer {
    private int tick;
    private FireServerChecks fireChecks;
    private ServerPlayerEntity player;
    private BlockPos flame;
    private ItemStack committedWand;
    private Vec3d chargeAnchor;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { run(server); }
            catch (Throwable e) {
                e.printStackTrace();
                try { Files.writeString(Path.of("AUDIT_FAILED.txt"),e.toString()); } catch(Exception ignored) {}
                server.stop(false);
            }
        });
    }
    private void run(MinecraftServer server) throws Exception {
        tick++;
        var world=server.getOverworld();
        if(tick==30) {
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            int ground=world.getTopY(Heightmap.Type.MOTION_BLOCKING,0,0);
            var factory=com.anton.elementalwands.arena.GuardianArenaSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
            factory.setAccessible(true);
            player=(ServerPlayerEntity)factory.invoke(null,server,UUID.randomUUID(),"AuditTester",.5,(double)ground,.5);
            player.onTeleportationDone();player.setOnGround(true);
            EquipmentRecoveryChecks.run(server,player);
            checkNatureOwnership(world,new BlockPos(-25,ground,0));
            checkMeteor(world,new BlockPos(25,ground,0));
            flame=player.getBlockPos().east(16);
            TemporaryBlockManager.placeTemporaryBlocks(world,List.of(flame),ModSpellBlocks.INFERNO_FLAME.getDefaultState(),12,s->s.isAir());
            TitanDomeManager.startDome(world,player);
            fireChecks=new FireServerChecks(world,player);
        }
        if(fireChecks!=null)fireChecks.check(world,tick);
        if(tick==75) {
            require(world.getBlockState(flame).isOf(ModSpellBlocks.TITAN_DOME),"Fixture failed to cover flame with Titan shell");
            var domes=TitanDomeManager.class.getDeclaredField("DOMES");domes.setAccessible(true);
            Object dome=((List<?>)((Map<?,?>)domes.get(null)).values().iterator().next()).getFirst();
            var collapse=TitanDomeManager.class.getDeclaredMethod("beginDomeCollapse",ServerWorld.class,dome.getClass(),int.class);
            collapse.setAccessible(true);collapse.invoke(null,world,dome,server.getTicks());
            TitanDomeManager.cancelForEncounter(player);
            require(world.getBlockState(flame).isAir(),"Titan restored an expired flame");
            require(player.getMainHandStack().isOf(ModItems.FRACTURED_WAND),"Titan lost wand on cancellation");
            int wands=0;
            for(int i=0;i<player.getInventory().size();i++)if(player.getInventory().getStack(i).isOf(ModItems.FRACTURED_WAND))wands++;
            require(wands==1,"Cancellation during collapse duplicated the real wand");
            committedWand=player.getMainHandStack();
            player.setAttached(com.anton.elementalwands.data.EWAttachments.AFFINITY,"SPACE");
            com.anton.elementalwands.item.AbstractWandItem.addUltimateCharge(committedWand,100);
            com.anton.elementalwands.item.SpaceAbilityHandler.castUltimate(world,player,committedWand);
            require(HollowPurpleChargeManager.isCharging(world,player),"Hollow Purple did not start");
            chargeAnchor=player.getEntityPos();
            player.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.ENDER_PEARL));
        }
        if(tick==76) {
            require(HollowPurpleChargeManager.isCharging(world,player),"Slot switching cancelled the committed ultimate");
            require(player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED)==0,"Charge did not disable walking");
            require(net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(player,world,Hand.MAIN_HAND)
                    ==net.minecraft.util.ActionResult.FAIL,"Pearl can bypass commitment");
            var before=player.getEntityPos();
            com.anton.elementalwands.item.SpaceAbilityHandler.castSecondary(world,player,committedWand);
            require(player.getEntityPos().equals(before),"Blink bypassed commitment");
            player.setPosition(chargeAnchor.add(2,0,2));
            player.setVelocity(1,.1,1);
        }
        if(tick==77) {
            require(Math.abs(player.getX()-chargeAnchor.x)<.001 && Math.abs(player.getZ()-chargeAnchor.z)<.001,
                    "Charge allowed horizontal displacement");
            player.setYaw(90); // Aiming remains available while committed.
        }
        if(tick==144)require(HollowPurpleChargeManager.isCharging(world,player),"Ultimate released before its 70-tick charge");
        if(tick==145) {
            require(!HollowPurpleChargeManager.isCharging(world,player),"Ultimate did not finish after 70 ticks");
            var orbs=world.getEntitiesByClass(com.anton.elementalwands.entity.HollowPurpleOrbEntity.class,
                    new Box(chargeAnchor,chargeAnchor).expand(30),e->true);
            require(orbs.size()==1 && orbs.getFirst().getVelocity().x<0,"Committed ultimate did not fire once in the final aim direction");
            require(player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED)>0,"Movement stayed locked after release");
            require(com.anton.elementalwands.item.AbstractWandItem.getUltimateCharge(committedWand)==0,"Committed ultimate refunded charge");
            player.setStackInHand(Hand.MAIN_HAND,committedWand);
            com.anton.elementalwands.item.AbstractWandItem.addUltimateCharge(committedWand,100);
            com.anton.elementalwands.item.SpaceAbilityHandler.castUltimate(world,player,committedWand);
            player.setHealth(0);
        }
        if(tick==146) {
            require(!HollowPurpleChargeManager.isCharging(world,player),"Death retained a charging session");
            require(player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.MOVEMENT_SPEED)>0,"Death retained movement lock");
            require(com.anton.elementalwands.item.AbstractWandItem.getUltimateCharge(committedWand)==0,"Death refunded charge");
            Files.writeString(Path.of("AUDIT_PASSED.txt"),"Meteor preserves occupied spawn block entity and contents; Nature ownership transfer; Titan expired flame cleanup; Zephyr/Titan serialized equipment recovery and idempotence; committed Hollow Purple slot swap, movement/item/blink lock, final aim, exact release, no refund and death cleanup.\n");
            System.out.println("AUDIT FIX RUNTIME CHECKS PASSED");server.stop(false);
        }
    }
    private void checkNatureOwnership(ServerWorld world,BlockPos anchor) {
        var old=SeedlingManager.placeVerdantGrowth(world,List.of(anchor.down()),Set.of(),200);
        require(old.placed().contains(anchor),"Could not place roots");
        require(SeedlingManager.tryPlantSeedling(world,player,new BlockHitResult(Vec3d.ofCenter(anchor.down()),Direction.UP,anchor.down(),false)),"Could not plant on another growth lease");
        SeedlingManager.restoreBlocks(world,old.placements());
        require(world.getBlockState(anchor).isOf(ModSpellBlocks.NATURE_SEEDLING),"Old thicket erased newer flower");
        SeedlingManager.destroySeedlingAtAnchor(world,anchor);
        require(world.getBlockState(anchor).isAir(),"Flower resurrected old roots");
        var fire=TemporaryBlockManager.placeTrackedTemporaryBlocks(world,List.of(anchor),ModSpellBlocks.INFERNO_FLAME.getDefaultState(),200,s->s.isAir());
        var roots=SeedlingManager.placeVerdantGrowth(world,List.of(anchor.down()),Set.of(),200);
        TemporaryBlockManager.restoreTemporaryBlocks(world,fire);
        require(world.getBlockState(anchor).isOf(ModSpellBlocks.NATURE_ROOTS),"Old flame erased newer roots");
        SeedlingManager.restoreBlocks(world,roots.placements());
        require(world.getBlockState(anchor).isAir(),"Nature restored an untracked flame");
    }
    private void checkMeteor(ServerWorld world,BlockPos target) throws Exception {
        for(int y=4;y<=35;y++)world.setBlockState(target.up(y),Blocks.STONE.getDefaultState(),3);
        BlockPos spawn=target.up(35);
        world.setBlockState(spawn,Blocks.CHEST.getDefaultState(),3);
        ChestBlockEntity chest=(ChestBlockEntity)world.getBlockEntity(spawn);
        chest.setStack(0,new ItemStack(Items.DIAMOND,7));
        MeteorManager.spawnMeteor(world,player,Vec3d.ofBottomCenter(target),35,0);
        require(world.getBlockEntity(spawn)==chest && chest.getStack(0).getCount()==7,"Meteor replaced occupied spawn chest or contents");
        var meteors=MeteorManager.class.getDeclaredField("METEORS");meteors.setAccessible(true);((Map<?,?>)meteors.get(null)).clear();
        for(var falling:world.getEntitiesByClass(FallingBlockEntity.class,new Box(spawn).expand(3),e->true)){
            falling.tick();falling.discard();
        }
        require(world.getBlockEntity(spawn)==chest && chest.getStack(0).getCount()==7,"Meteor's first tick changed spawn chest");
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
