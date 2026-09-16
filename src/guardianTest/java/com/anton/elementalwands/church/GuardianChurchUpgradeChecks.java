package com.anton.elementalwands.church;

import com.anton.elementalwands.registry.ModBlocks;
import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

/** Real block entities and registry-aware item snapshots, including interrupted upgrade replay. */
final class GuardianChurchUpgradeChecks {
    static void run(ServerWorld world) throws Exception {
        var flag=GuardianChurchManager.class.getDeclaredField("mutation");flag.setAccessible(true);
        for(var rotation:BlockRotation.values()) {
            var origin=new BlockPos(240+rotation.ordinal()*128,0,40);
            for(int x=(origin.getX()-64)>>4;x<=(origin.getX()+64)>>4;x++)
                for(int z=-3;z<=7;z++)world.getChunk(x,z);
            var temporary=new GuardianChurchManager.Site();temporary.x=origin.getX();temporary.y=0;temporary.z=40;temporary.rotation=rotation;
            flag.setBoolean(null,true);
            try {
                // Only the migration footprint is needed; the facade sentinel must survive unchanged.
                for(int x=-5;x<=4;x++)for(int y=-1;y<=7;y++)for(int z=-6;z<=6;z++) {
                    var local=new BlockPos(x,y,z);
                    world.setBlockState(temporary.at(x,y,z),ChurchLayout.state(false,local,rotation,0),Block.NOTIFY_LISTENERS);
                }
                world.setBlockState(temporary.at(0,0,11),Blocks.CHISELED_STONE_BRICKS.getDefaultState());
            } finally { flag.setBoolean(null,false); }
            GuardianChurchManager.discover(world,origin,world.getBlockState(origin));
            var s=GuardianChurchManager.sites().stream().filter(v -> v.anchor().equals(origin)).findFirst().orElseThrow();
            check(s.rotation==rotation,"Legacy rotation changed");
            var token=s.token;
            var old=(ChestBlockEntity)world.getBlockEntity(s.offering());
            old.setStack(4,new ItemStack(Items.DIAMOND,7));
            old.setStack(19,new ItemStack(Items.WRITTEN_BOOK));old.markDirty();
            var expected=old.getStack(19).copy();
            if(rotation==BlockRotation.CLOCKWISE_180) {
                var ops=world.getRegistryManager().getOps(com.mojang.serialization.JsonOps.INSTANCE);
                s.statueMoveInventory=new JsonArray();
                for(int i=0;i<27;i++)s.statueMoveInventory.add(old.getStack(i).isEmpty()?JsonNull.INSTANCE:ItemStack.CODEC.encodeStart(ops,old.getStack(i)).getOrThrow());
                // Simulate reload after the old inventory has already been removed.
                flag.setBoolean(null,true);
                try { old.clear();world.setBlockState(s.offering(),Blocks.AIR.getDefaultState()); }
                finally {flag.setBoolean(null,false);}
            }
            check(GuardianChurchManager.moveStatue(world,s),"Statue migration failed");
            var moved=(ChestBlockEntity)world.getBlockEntity(s.offering());
            check(s.token.equals(token) && s.layoutVersion==2 && s.statueMoveInventory==null,"Identity or migration receipt incorrect");
            check(moved.getStack(4).isOf(Items.DIAMOND) && moved.getStack(4).getCount()==7 && ItemStack.areEqual(moved.getStack(19),expected),"Inventory changed while moving the offering");
            check(world.getBlockState(s.socket()).isOf(ModBlocks.GUARDIAN_SOCKET),"New rotated socket missing");
            check(world.getBlockState(s.socket()).get(GuardianSocketBlock.PEDESTAL),"Moved socket lacks the offering model");
            check(world.getBlockState(s.socket().down()).isOf(ModBlocks.GUARDIAN_PEDESTAL),"Moved pedestal lacks support");
            check(world.getBlockState(s.at(0,3,-4)).isOf(ModBlocks.GUARDIAN_CHEST_RUNE),"Moved statue lacks matching rune");
            check(GuardianChurchManager.ritualSocket(world,s.socket().down()).equals(s.socket()),"Column click did not resolve to rotated bowl");
            check(world.getBlockState(s.anchor()).isAir() && world.getBlockState(s.at(0,5,2)).isAir(),"Old socket/statue still blocks the approach");
            check(world.getBlockState(s.at(0,5,-4)).isOf(Blocks.CHISELED_DEEPSLATE),"New statue missing");
            check(world.getBlockState(s.at(0,0,11)).isOf(Blocks.CHISELED_STONE_BRICKS),"Migration moved the church facade");
            int count=GuardianChurchManager.sites().size();
            GuardianChurchManager.discover(world,s.socket(),world.getBlockState(s.socket()));
            check(GuardianChurchManager.sites().size()==count,"Moved socket discovered a duplicate site");
            moved.setStack(4,ItemStack.EMPTY);
            check(GuardianChurchManager.moveStatue(world,s) && moved.getStack(4).isEmpty(),"Repeat migration duplicated collected contents");
            // Existing version-two church, including interrupted partial upgrade and a foreign inventory.
            var socketEntity=world.getBlockEntity(s.socket());
            flag.setBoolean(null,true);
            try {
                world.setBlockState(s.socket(),world.getBlockState(s.socket()).with(GuardianSocketBlock.PEDESTAL,false));
                world.setBlockState(s.socket().down(),Blocks.BARREL.getDefaultState());
                world.setBlockState(s.at(0,3,-4),Blocks.CHISELED_DEEPSLATE.getDefaultState());
            } finally {flag.setBoolean(null,false);}
            check(!GuardianChurchManager.upgradePedestal(world,s),"Pedestal upgrade overwrote a foreign inventory");
            check(world.getBlockState(s.at(0,3,-4)).isOf(Blocks.CHISELED_DEEPSLATE),"Blocked upgrade partially changed the statue");
            flag.setBoolean(null,true);
            try {world.setBlockState(s.socket().down(),ModBlocks.GUARDIAN_PEDESTAL.getDefaultState().rotate(rotation));}
            finally {flag.setBoolean(null,false);}
            check(GuardianChurchManager.upgradePedestal(world,s),"Partial pedestal upgrade did not resume");
            check(world.getBlockEntity(s.socket())==socketEntity && s.token.equals(token),"Pedestal upgrade replaced anchor identity or heart token");
            check(ItemStack.areEqual(moved.getStack(19),expected) && moved.getStack(4).isEmpty(),"Pedestal upgrade changed offering inventory");
            check(world.getBlockState(s.at(0,3,-4)).get(net.minecraft.state.property.Properties.HORIZONTAL_FACING)==rotation.rotate(net.minecraft.util.math.Direction.NORTH),"Chest rune rotation disagrees with pedestal");
            check(GuardianChurchManager.upgradePedestal(world,s),"Pedestal upgrade was not idempotent");
            flag.setBoolean(null,true);
            try {
                // Socket chunk saved first, while the other chunk still has the previous stonework.
                world.setBlockState(s.socket().down(),Blocks.AIR.getDefaultState());
                world.setBlockState(s.at(0,3,-4),Blocks.CHISELED_DEEPSLATE.getDefaultState());
            } finally {flag.setBoolean(null,false);}
            check(GuardianChurchManager.upgradePedestal(world,s)
                    && world.getBlockState(s.socket().down()).isOf(ModBlocks.GUARDIAN_PEDESTAL)
                    && world.getBlockState(s.at(0,3,-4)).isOf(ModBlocks.GUARDIAN_CHEST_RUNE),"Partially saved upgrade left an unsupported bowl");
            s.phase=GuardianChurchManager.Phase.RESTORED;
            check(!GuardianChurchManager.upgradePedestal(world,s),"Completed pedestal was upgraded again");
        }
        var finished=new GuardianChurchManager.Site();finished.phase=GuardianChurchManager.Phase.RESTORED;
        check(!GuardianChurchManager.moveStatue(world,finished),"Completed legacy site was modified");
        System.out.println("CHURCH UPGRADE CHECKS PASSED: four rotations, chest slots/components, interrupted replay, identity, facade, no duplicate sites/items, completed-site exclusion.");
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
