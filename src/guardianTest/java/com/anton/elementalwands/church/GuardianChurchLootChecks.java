package com.anton.elementalwands.church;
import java.util.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

final class GuardianChurchLootChecks {
    static void run(ServerWorld world) {
        var registry=world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        Set<String> books=new HashSet<>(),arrangements=new HashSet<>();Set<Integer> usedSlots=new HashSet<>();
        int modest=0,rich=0,levelOne=0,levelTwo=0;
        for (int n=0;n<1024;n++) {
            long seed=n*0x9e3779b97f4a7c15L;
            var first=GuardianChurchLoot.roll(registry,seed);var second=GuardianChurchLoot.roll(registry,seed);
            check(first.size()==27,"Chest capacity changed");int iron=0,gold=0,diamonds=0,clutter=0;StringBuilder shape=new StringBuilder();
            for (int slot=0;slot<27;slot++) {
                var stack=first.get(slot);check(ItemStack.areEqual(stack,second.get(slot)),"Same site rerolled loot or placement");
                if(stack.isEmpty())continue;
                usedSlots.add(slot);shape.append(slot).append(':').append(stack).append(';');
                if(stack.isOf(Items.IRON_INGOT))iron+=stack.getCount();
                else if(stack.isOf(Items.GOLD_INGOT))gold+=stack.getCount();
                else if(stack.isOf(Items.DIAMOND))diamonds+=stack.getCount();
                else if(stack.isOf(Items.ENCHANTED_BOOK)) {
                    var stored=stack.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS,ItemEnchantmentsComponent.DEFAULT);
                    check(stored.getSize()==1,"Book is blank or contains unexpected multiple enchantments");
                    for(var enchantment:stored.getEnchantments()) {
                        int level=stored.getLevel(enchantment);
                        check(level>=1 && level<=2 && level<=enchantment.value().getMaxLevel(),"Book exceeded level II or its natural maximum");
                        books.add(enchantment.getIdAsString());if(level==1)levelOne++;else levelTwo++;
                    }
                } else if(!stack.isOf(Items.IRON_HELMET) && !stack.isOf(Items.IRON_BOOTS) && !stack.isOf(Items.IRON_CHESTPLATE) && !stack.isOf(Items.IRON_LEGGINGS))clutter++;
            }
            check(iron>=1 && iron<=8 && gold<=5 && diamonds<=2,"Chest exceeded the modest treasure budget");
            check(clutter>=2 && clutter<=5,"Ordinary supplies missing");
            if(gold==0 && diamonds==0)modest++;if(diamonds>0)rich++;
            arrangements.add(shape.toString());
        }
        check(modest>400 && rich>100,"Poor and richer chests lack meaningful variation");
        check(books.size()>20 && levelOne>0 && levelTwo>0,"Book enchantments or levels are not varied");
        check(arrangements.size()>1000 && usedSlots.size()==27,"Loot uses repetitive fixed chest slots");
        var left=GuardianChurchLoot.roll(world,new BlockPos(7,80,3),-5);
        var right=GuardianChurchLoot.roll(world,new BlockPos(7,80,3),5);
        boolean different=false;for(int i=0;i<27;i++)if(!ItemStack.areEqual(left.get(i),right.get(i)))different=true;
        check(different,"Both chests use the same roll");
        System.out.println("CHURCH LOOT CHECKS PASSED: 1024 repeatable rolls, "+books.size()+" enchantments, all 27 slots, smaller budgets and ordinary supplies.");
    }
    private static void check(boolean pass,String message) {if(!pass)throw new AssertionError(message);}
}
