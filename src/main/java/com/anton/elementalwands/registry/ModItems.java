package com.anton.elementalwands.registry;

import java.util.function.Function;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.item.FireWandItem;
import com.anton.elementalwands.item.FracturedWandItem;
import com.anton.elementalwands.item.IceWandItem;
import com.anton.elementalwands.item.SpaceWandItem;
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

    // ── Wands ──────────────────────────────────────────
    public static final Item FRACTURED_WAND = register("fractured_wand", settings -> new FracturedWandItem(settings));
    public static final Item FIRE_WAND = register("fire_wand", settings -> new FireWandItem(settings));
    public static final Item WIND_WAND = register("wind_wand", settings -> new WindWandItem(settings));
    public static final Item STONE_WAND = register("stone_wand", settings -> new StoneWandItem(settings));
    public static final Item ICE_WAND = register("ice_wand", settings -> new IceWandItem(settings));
    public static final Item SPACE_WAND = register("space_wand", settings -> new SpaceWandItem(settings));
    public static final Item TITAN_SWORD = register("titan_sword", settings -> new Item(
            settings.sword(ToolMaterial.NETHERITE, 3.0f, -2.4f).fireproof().maxCount(1)));

    // ── Raw Crystals (stackable to 64) ─────────────────
    public static final Item RAW_FIRE_CRYSTAL = registerSimple("raw_fire_crystal", 64);
    public static final Item RAW_WIND_CRYSTAL = registerSimple("raw_wind_crystal", 64);
    public static final Item RAW_STONE_CRYSTAL = registerSimple("raw_stone_crystal", 64);
    public static final Item RAW_ICE_CRYSTAL = registerSimple("raw_ice_crystal", 64);
    public static final Item RAW_SPACE_CRYSTAL = registerSimple("raw_space_crystal", 64);

    // ── Refined Crystals (stackable to 64) ─────────────
    public static final Item FIRE_CRYSTAL = registerSimple("fire_crystal", 64);
    public static final Item WIND_CRYSTAL = registerSimple("wind_crystal", 64);
    public static final Item STONE_CRYSTAL = registerSimple("stone_crystal", 64);
    public static final Item ICE_CRYSTAL = registerSimple("ice_crystal", 64);
    public static final Item SPACE_CRYSTAL = registerSimple("space_crystal", 64);

    // ── Misc ───────────────────────────────────────────
    public static final Item RESET_RUNE = registerSimple("reset_rune", 16);

    public static void registerAll() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(FRACTURED_WAND);
            entries.add(FIRE_WAND);
            entries.add(WIND_WAND);
            entries.add(STONE_WAND);
            entries.add(ICE_WAND);
            entries.add(SPACE_WAND);
            entries.add(TITAN_SWORD);
            entries.add(RESET_RUNE);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(RAW_FIRE_CRYSTAL);
            entries.add(RAW_WIND_CRYSTAL);
            entries.add(RAW_STONE_CRYSTAL);
            entries.add(RAW_ICE_CRYSTAL);
            entries.add(RAW_SPACE_CRYSTAL);
            entries.add(FIRE_CRYSTAL);
            entries.add(WIND_CRYSTAL);
            entries.add(STONE_CRYSTAL);
            entries.add(ICE_CRYSTAL);
            entries.add(SPACE_CRYSTAL);
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

    private static Item registerSimple(String path, int maxCount) {
        Identifier id = Identifier.of(ElementalWandsMod.MOD_ID, path);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);

        Item.Settings settings = new Item.Settings()
                .maxCount(maxCount)
                .registryKey(key);

        Item item = new Item(settings);
        return Registry.register(Registries.ITEM, id, item);
    }
}
