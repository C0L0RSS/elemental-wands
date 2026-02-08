package com.anton.elementalwands.registry;

import java.util.function.Function;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.item.FireWandItem;
import com.anton.elementalwands.item.IceWandItem;
import com.anton.elementalwands.item.StoneWandItem;
import com.anton.elementalwands.item.WindWandItem;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModItems {

    public static final Item FIRE_WAND = register("fire_wand", settings -> new FireWandItem(settings));
    public static final Item WIND_WAND = register("wind_wand", settings -> new WindWandItem(settings));
    public static final Item STONE_WAND = register("stone_wand", settings -> new StoneWandItem(settings));
    public static final Item ICE_WAND = register("ice_wand", settings -> new IceWandItem(settings));
    public static final Item TITAN_SWORD = register("titan_sword", settings -> new Item(
            settings.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f).fireproof().maxCount(1)));

    public static void registerAll() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(FIRE_WAND);
            entries.add(WIND_WAND);
            entries.add(STONE_WAND);
            entries.add(ICE_WAND);
            entries.add(TITAN_SWORD);
        });
    }

    private static Item register(String path, Function<Item.Settings, Item> factory) {
        Identifier id = Identifier.of(ElementalWandsMod.MOD_ID, path);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);

        Item.Settings settings = new Item.Settings()
                .maxCount(1)
                .registryKey(key); // ✅ THIS fixes "Item id not set"

        Item item = factory.apply(settings);
        return Registry.register(Registries.ITEM, id, item);
    }
}
