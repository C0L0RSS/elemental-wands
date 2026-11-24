package com.anton.elementalwands.util;

import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.entity.HollowPurpleOrbEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
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
            var factory=com.anton.elementalwands.arena.GuardianNaveSmokeMod.class.getDeclaredMethod("player",MinecraftServer.class,UUID.class,String.class,double.class,double.class,double.class);
            factory.setAccessible(true);
            player=(ServerPlayerEntity)factory.invoke(null,server,UUID.randomUUID(),"AuditTester",.5,(double)ground,.5);
            player.onTeleportationDone();player.setOnGround(true);
            EquipmentRecoveryChecks.run(server,player);
            checkNatureOwnership(world,new BlockPos(-25,ground,0));
            checkMeteor(world,new BlockPos(25,ground,0));
            checkMeteorRoof(world,new BlockPos(25,ground,25));
            checkMeteorArenaLid(server.getWorld(com.anton.elementalwands.crypt.HollowCryptRealm.WORLD));
            flame=player.getBlockPos().east(16);
            TemporaryBlockManager.placeTemporaryBlocks(world,List.of(flame),ModSpellBlocks.INFERNO_FLAME.getDefaultState(),12,s->s.isAir());
            TitanDomeManager.startDome(world,player);
            fireChecks=new FireServerChecks(world,player);
        }
        if(fireChecks!=null)fireChecks.check(world,tick);
        if(tick==65) checkPurpleProtection(world, new BlockPos(28, 150, 0));
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
            Files.writeString(Path.of("AUDIT_PASSED.txt"),"Meteor preserves occupied spawn block entity and contents, starts above an overworld roof and under an arena's barrier lid; Nature ownership transfer; Titan expired flame cleanup; Zephyr/Titan serialized equipment recovery and idempotence; committed Hollow Purple slot swap, movement/item/blink lock, final aim, exact release, no refund and death cleanup.\n");
            System.out.println("AUDIT FIX RUNTIME CHECKS PASSED");server.stop(false);
        }
    }
    private void checkPurpleProtection(ServerWorld world, BlockPos protectedPos) {
        world.setBlockState(protectedPos, Blocks.CHEST.getDefaultState());
        var chest = (ChestBlockEntity) world.getBlockEntity(protectedPos);
        chest.setStack(0, new ItemStack(Items.EMERALD));
        world.setBlockState(protectedPos.east(), Blocks.STONE.getDefaultState());
        PlayerBlockBreakEvents.BEFORE.register((w, p, pos, state, entity) -> {
            if (w != world || !pos.equals(protectedPos)) return true;
            require(p == player && entity == chest, "Break veto received the wrong caster or container");
            return false;
        });
        Vec3d origin = protectedPos.toCenterPos();
        var loose = new ItemEntity(world, origin.x, origin.y + 1, origin.z, new ItemStack(Items.EMERALD));
        world.spawnEntity(loose);
        new HollowPurpleOrbEntity(world, player, origin, new Vec3d(1, 0, 0)).tick();
        require(world.getBlockEntity(protectedPos) == chest && chest.getStack(0).isOf(Items.EMERALD),
                "Hollow Purple erased a vetoed chest");
        require(world.isAir(protectedPos.east()), "Hollow Purple skipped an unprotected block");
        require(loose.isAlive(), "Hollow Purple erased a dropped item");
    }
    /** Overlapping temporary layers: an older lease may neither erase a newer one nor come back from under it. */
    private void checkNatureOwnership(ServerWorld world,BlockPos anchor) {
        java.util.function.Predicate<net.minecraft.block.BlockState> open=s->s.isAir()||s.isReplaceable();
        var roots=TemporaryBlockManager.placeTrackedTemporaryBlocks(world,List.of(anchor),ModSpellBlocks.NATURE_ROOTS.getDefaultState(),200,open);
        require(world.getBlockState(anchor).isOf(ModSpellBlocks.NATURE_ROOTS),"Could not place roots");
        var bloom=TemporaryBlockManager.placeTrackedTemporaryBlocks(world,List.of(anchor),ModSpellBlocks.SPRINGBLOOM.getDefaultState(),200,open);
        require(world.getBlockState(anchor).isOf(ModSpellBlocks.SPRINGBLOOM),"Could not place on another growth lease");
        TemporaryBlockManager.restoreTemporaryBlocks(world,roots);
        require(world.getBlockState(anchor).isOf(ModSpellBlocks.SPRINGBLOOM),"Old roots erased newer growth");
        TemporaryBlockManager.restoreTemporaryBlocks(world,bloom);
        require(world.getBlockState(anchor).isAir(),"Newer growth resurrected the old roots");
        var fire=TemporaryBlockManager.placeTrackedTemporaryBlocks(world,List.of(anchor),ModSpellBlocks.INFERNO_FLAME.getDefaultState(),200,s->s.isAir());
        var over=TemporaryBlockManager.placeTrackedTemporaryBlocks(world,List.of(anchor),ModSpellBlocks.NATURE_ROOTS.getDefaultState(),200,open);
        TemporaryBlockManager.restoreTemporaryBlocks(world,fire);
        require(world.getBlockState(anchor).isOf(ModSpellBlocks.NATURE_ROOTS),"Old flame erased newer growth");
        TemporaryBlockManager.restoreTemporaryBlocks(world,over);
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
    /** Outside the boss arenas a roof still shelters what is under it: the meteor starts above the roof. */
    private void checkMeteorRoof(ServerWorld world,BlockPos target) throws Exception {
        BlockPos roof=target.up(10);
        world.setBlockState(roof,Blocks.STONE.getDefaultState(),3);
        require(meteorStartHeight(world,target)==35,"Meteor started under an overworld roof");
        world.setBlockState(roof,Blocks.AIR.getDefaultState(),3);
    }
    /** In a boss arena the meteor starts just under the barrier lid, never on the open air above it. */
    private void checkMeteorArenaLid(ServerWorld crypt) throws Exception {
        require(crypt!=null,"Hollow Crypt world is not loaded");
        // Between two fight slots, so no layout work touches it.
        int x=512,z=512;crypt.getChunk(x>>4,z>>4);
        BlockPos target=new BlockPos(x,crypt.getTopY(Heightmap.Type.MOTION_BLOCKING,x,z),z);
        for(int y=1;y<=35;y++)require(crypt.getBlockState(target.up(y)).isAir(),"Crypt fixture column is not open");
        BlockPos lid=target.up(31);
        crypt.setBlockState(lid,Blocks.BARRIER.getDefaultState(),3);
        int start=meteorStartHeight(crypt,target);
        require(start==30,"Meteor did not start just under the arena's barrier lid: "+start);
        crypt.setBlockState(lid,Blocks.AIR.getDefaultState(),3);
    }
    /** Spawns a harmless meteor over the target and returns how many blocks above it the meteor started. */
    private int meteorStartHeight(ServerWorld world,BlockPos target) throws Exception {
        var meteor=MeteorManager.spawnMeteor(world,player,Vec3d.ofBottomCenter(target),35,0);
        var meteors=MeteorManager.class.getDeclaredField("METEORS");meteors.setAccessible(true);((Map<?,?>)meteors.get(null)).clear();
        int start=(int)Math.round(meteor.getY()-target.getY());
        meteor.discard();
        return start;
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
