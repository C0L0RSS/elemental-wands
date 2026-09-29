package com.anton.elementalwands.world;

import com.anton.elementalwands.ElementalWandsMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModWorldGen {

    public static void registerAll() {
        // Church generation is data-driven: worldgen/structure, structure_set and template_pool.
        // The Hollow graveyard's structure JSON uses this type to pick level, dry ground.
        // Crystal ore worldgen remains removed.
        Registry.register(Registries.STRUCTURE_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "hollow_graveyard"),
                HollowGraveyardStructure.TYPE);
    }
}
