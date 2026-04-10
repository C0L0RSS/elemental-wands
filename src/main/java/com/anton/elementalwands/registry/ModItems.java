package com.anton.elementalwands.registry;

import java.util.function.Function;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.item.UniversalWandItem;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModItems {

    // ── Wands ──────────────────────────────────────────
    public static final Item FRACTURED_WAND = register("fractured_wand", settings -> new UniversalWandItem(settings));
    public static final Item TITAN_SWORD = register("titan_sword", settings -> new Item(
            settings.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f).fireproof().maxCount(1)));

    // ── Spawn Eggs ─────────────────────────────────────
    public static final Item STONE_ZOMBIE_SPAWN_EGG = register("stone_zombie_spawn_egg",
            settings -> new SpawnEggItem(settings.spawnEgg(ModEntities.STONE_ZOMBIE).maxCount(64)));
    public static final Item FIRE_SPIRIT_SPAWN_EGG = register("fire_spirit_spawn_egg",
            settings -> new SpawnEggItem(settings.spawnEgg(ModEntities.FIRE_SPIRIT).maxCount(64)));

    public static void registerAll() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(FRACTURED_WAND);
            entries.add(TITAN_SWORD);
            entries.add(STONE_ZOMBIE_SPAWN_EGG);
            entries.add(FIRE_SPIRIT_SPAWN_EGG);
        });
    }

    private static Item register(String path, Function<Item.Settings, Item> factory) {
        Identifier id = Identifier.of(ElementalWandsMod.MOD_ID, path);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);

        Item.Settings settings = new Item.Settings()
                .maxCount(1)
                .registryKey(key);

        Item item = factory.apply(settings);
        return Registry.register(Registries.ITEM, id, item);
    }
}
