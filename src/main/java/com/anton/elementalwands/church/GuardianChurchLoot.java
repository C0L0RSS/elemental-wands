package com.anton.elementalwands.church;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Independent, repeatable chest rolls. Ordinary supplies outnumber uncommon treasure. */
public final class GuardianChurchLoot {
    private static final Item[] CLUTTER={Items.STICK,Items.STRING,Items.BONE,Items.PAPER,Items.FEATHER,Items.ROTTEN_FLESH,Items.WHEAT_SEEDS};
    private static final Item[] ARMOR={Items.IRON_HELMET,Items.IRON_BOOTS,Items.IRON_LEGGINGS,Items.IRON_CHESTPLATE};
    private GuardianChurchLoot() {}

    public static List<ItemStack> roll(ServerWorld world,BlockPos anchor,int chestSide) {
        // Neither world time, recall tokens nor combat retries can change the roll.
        long seed=mix(world.getSeed()^mix(anchor.asLong())^(chestSide<0?0x53C7A9281BL:0x2B811DA75FL));
        return roll(world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT),seed);
    }
    public static List<ItemStack> roll(Registry<Enchantment> enchantments,long seed) {
        Random random=new Random(seed);
        List<ItemStack> items=new ArrayList<>();
        int quality=random.nextInt(100);
        // Half the chests are modest, 35% are better, and 15% are a richer find.
        if (quality<50) {
            add(items,Items.IRON_INGOT,1+random.nextInt(4));
            if (random.nextInt(100)<45) addBook(items,enchantments,random);
        } else if (quality<85) {
            add(items,Items.IRON_INGOT,3+random.nextInt(4));
            add(items,Items.GOLD_INGOT,1+random.nextInt(3));
            if (random.nextInt(100)<30) add(items,Items.DIAMOND,1);
            if (random.nextInt(100)<65) addBook(items,enchantments,random);
            if (random.nextInt(100)<15) add(items,ARMOR[random.nextInt(ARMOR.length)],1);
        } else {
            add(items,Items.IRON_INGOT,4+random.nextInt(5));
            add(items,Items.GOLD_INGOT,2+random.nextInt(4));
            add(items,Items.DIAMOND,1+random.nextInt(2));
            addBook(items,enchantments,random);
            if (random.nextInt(100)<45) add(items,ARMOR[random.nextInt(ARMOR.length)],1);
        }
        for (int i=0,n=2+random.nextInt(4);i<n;i++) add(items,CLUTTER[random.nextInt(CLUTTER.length)],1+random.nextInt(5));
        List<Integer> slots=new ArrayList<>();for (int i=0;i<27;i++) slots.add(i);
        Collections.shuffle(slots,random);
        List<ItemStack> contents=new ArrayList<>(Collections.nCopies(27,ItemStack.EMPTY));
        for (int i=0;i<items.size();i++) contents.set(slots.get(i),items.get(i));
        return contents;
    }
    private static void add(List<ItemStack> items,Item item,int count) { items.add(new ItemStack(item,count)); }
    private static void addBook(List<ItemStack> items,Registry<Enchantment> registry,Random random) {
        var choices=registry.getEntrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getValue().toString()))
                .map(e -> registry.getOrThrow(e.getKey()))
                .filter(e -> e.value().getMinLevel()<=2 && e.value().getMaxLevel()>=1).toList();
        if (choices.isEmpty()) return;
        var entry=choices.get(random.nextInt(choices.size()));
        int low=Math.max(1,entry.value().getMinLevel()),high=Math.min(2,entry.value().getMaxLevel());
        var stored=new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        stored.set(entry,low+random.nextInt(high-low+1));
        ItemStack book=new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponentTypes.STORED_ENCHANTMENTS,stored.build());items.add(book);
    }
    private static long mix(long n) {
        n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;
        n=(n^(n>>>27))*0x94d049bb133111ebL;
        return n^(n>>>31);
    }
}
