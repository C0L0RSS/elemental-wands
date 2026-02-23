package com.anton.elementalwands.world;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.registry.ModBlocks;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.YOffset;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.placementmodifier.BiomePlacementModifier;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;
import net.minecraft.world.gen.placementmodifier.SquarePlacementModifier;

import java.util.List;

public class ModWorldGen {

    public static void registerAll() {
        registerOreGen("fire_crystal_ore", ModBlocks.FIRE_CRYSTAL_ORE);
        registerOreGen("wind_crystal_ore", ModBlocks.WIND_CRYSTAL_ORE);
        registerOreGen("stone_crystal_ore", ModBlocks.STONE_CRYSTAL_ORE);
        registerOreGen("ice_crystal_ore", ModBlocks.ICE_CRYSTAL_ORE);
        registerOreGen("space_crystal_ore", ModBlocks.SPACE_CRYSTAL_ORE);
    }

    private static void registerOreGen(String name, Block oreBlock) {
        Identifier cfId = Identifier.of(ElementalWandsMod.MOD_ID, name);
        RegistryKey<ConfiguredFeature<?, ?>> cfKey = RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, cfId);

        Identifier pfId = Identifier.of(ElementalWandsMod.MOD_ID, name + "_placed");
        RegistryKey<PlacedFeature> pfKey = RegistryKey.of(RegistryKeys.PLACED_FEATURE, pfId);

        // Add to overworld biomes using the placed feature key
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_ORES,
                pfKey);
    }
}
