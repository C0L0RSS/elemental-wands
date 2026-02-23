package com.anton.elementalwands.registry;

import com.anton.elementalwands.ElementalWandsMod;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block FIRE_CRYSTAL_ORE = registerOre("fire_crystal_ore");
    public static final Block WIND_CRYSTAL_ORE = registerOre("wind_crystal_ore");
    public static final Block STONE_CRYSTAL_ORE = registerOre("stone_crystal_ore");
    public static final Block ICE_CRYSTAL_ORE = registerOre("ice_crystal_ore");
    public static final Block SPACE_CRYSTAL_ORE = registerOre("space_crystal_ore");

    public static void registerAll() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            entries.add(FIRE_CRYSTAL_ORE);
            entries.add(WIND_CRYSTAL_ORE);
            entries.add(STONE_CRYSTAL_ORE);
            entries.add(ICE_CRYSTAL_ORE);
            entries.add(SPACE_CRYSTAL_ORE);
        });
    }

    private static Block registerOre(String path) {
        Identifier id = Identifier.of(ElementalWandsMod.MOD_ID, path);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);

        AbstractBlock.Settings blockSettings = AbstractBlock.Settings.copy(Blocks.IRON_ORE)
                .registryKey(blockKey);
        Block block = new Block(blockSettings);
        Registry.register(Registries.BLOCK, id, block);

        // Register the corresponding BlockItem
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings itemSettings = new Item.Settings()
                .registryKey(itemKey)
                .useBlockPrefixedTranslationKey();
        BlockItem blockItem = new BlockItem(block, itemSettings);
        Registry.register(Registries.ITEM, id, blockItem);

        return block;
    }
}
