package com.anton.elementalwands.registry;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.block.MagicCrystalOreBlock;

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

    public static final Block FIRE_CRYSTAL_ORE = registerOre("fire_crystal_ore", Blocks.IRON_ORE);
    public static final Block WIND_CRYSTAL_ORE = registerOre("wind_crystal_ore", Blocks.IRON_ORE);
    public static final Block STONE_CRYSTAL_ORE = registerOre("stone_crystal_ore", Blocks.IRON_ORE);
    public static final Block ICE_CRYSTAL_ORE = registerOre("ice_crystal_ore", Blocks.IRON_ORE);
    public static final Block SPACE_CRYSTAL_ORE = registerOre("space_crystal_ore", Blocks.IRON_ORE);

    public static final Block DEEPSLATE_FIRE_CRYSTAL_ORE = registerOre("deepslate_fire_crystal_ore",
            Blocks.DEEPSLATE_IRON_ORE);
    public static final Block DEEPSLATE_WIND_CRYSTAL_ORE = registerOre("deepslate_wind_crystal_ore",
            Blocks.DEEPSLATE_IRON_ORE);
    public static final Block DEEPSLATE_STONE_CRYSTAL_ORE = registerOre("deepslate_stone_crystal_ore",
            Blocks.DEEPSLATE_IRON_ORE);
    public static final Block DEEPSLATE_ICE_CRYSTAL_ORE = registerOre("deepslate_ice_crystal_ore",
            Blocks.DEEPSLATE_IRON_ORE);
    public static final Block DEEPSLATE_SPACE_CRYSTAL_ORE = registerOre("deepslate_space_crystal_ore",
            Blocks.DEEPSLATE_IRON_ORE);

    public static void registerAll() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            entries.add(FIRE_CRYSTAL_ORE);
            entries.add(WIND_CRYSTAL_ORE);
            entries.add(STONE_CRYSTAL_ORE);
            entries.add(ICE_CRYSTAL_ORE);
            entries.add(SPACE_CRYSTAL_ORE);
            entries.add(DEEPSLATE_FIRE_CRYSTAL_ORE);
            entries.add(DEEPSLATE_WIND_CRYSTAL_ORE);
            entries.add(DEEPSLATE_STONE_CRYSTAL_ORE);
            entries.add(DEEPSLATE_ICE_CRYSTAL_ORE);
            entries.add(DEEPSLATE_SPACE_CRYSTAL_ORE);
        });
    }

    private static Block registerOre(String path, Block baseBlock) {
        Identifier id = Identifier.of(ElementalWandsMod.MOD_ID, path);
        RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, id);

        AbstractBlock.Settings blockSettings = AbstractBlock.Settings.copy(baseBlock)
                .registryKey(blockKey);
        Block block = new MagicCrystalOreBlock(blockSettings);
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
