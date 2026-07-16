package com.anton.elementalwands.registry;

import java.util.function.Function;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.block.InfernoFlameBlock;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MagmaBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Internal-only blocks used to give temporary spell effects their own visuals.
 *
 * <p>No {@code BlockItem}s are registered for these blocks and all three use
 * {@link AbstractBlock.Settings#dropsNothing()}, so they cannot be obtained in
 * normal play and never create item drops when a spell or player removes them.
 */
public final class ModSpellBlocks {

    public static final Block INFERNO_FLAME = register("inferno_flame", InfernoFlameBlock::new,
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.ORANGE)
                    .noCollision()
                    .nonOpaque()
                    .replaceable()
                    .breakInstantly()
                    .luminance(state -> 12)
                    .sounds(BlockSoundGroup.INTENTIONALLY_EMPTY)
                    .pistonBehavior(PistonBehavior.DESTROY)
                    .dropsNothing());

    public static final Block PYRE_COALS = register("pyre_coals", MagmaBlock::new,
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.DARK_RED)
                    .strength(1.5f, 6.0f)
                    .luminance(state -> 10)
                    .sounds(BlockSoundGroup.NETHERRACK)
                    .pistonBehavior(PistonBehavior.DESTROY)
                    .dropsNothing());

    public static final Block METEOR_CORE = register("meteor_core", settings -> new Block(settings),
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.BLACK)
                    .strength(4.0f, 30.0f)
                    .luminance(state -> 15)
                    .sounds(BlockSoundGroup.NETHERITE)
                    .pistonBehavior(PistonBehavior.BLOCK)
                    .dropsNothing());

    private ModSpellBlocks() {
    }

    /**
     * Loads this class during common initialization, which registers its static
     * block instances before Minecraft freezes the block registry.
     */
    public static void registerAll() {
        // Static field initialization performs registration.
    }

    private static Block register(String path, Function<AbstractBlock.Settings, Block> factory,
            AbstractBlock.Settings settings) {
        Identifier id = Identifier.of(ElementalWandsMod.MOD_ID, path);
        RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, id);
        Block block = factory.apply(settings.registryKey(key));
        return Registry.register(Registries.BLOCK, id, block);
    }
}
