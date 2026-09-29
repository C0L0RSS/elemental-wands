package com.anton.elementalwands.registry;

public class ModBlocks {
    public static final net.minecraft.block.Block GUARDIAN_SOCKET=socket();
    private static net.minecraft.block.Block socket() {
        var id=net.minecraft.util.Identifier.of("elementalwands","guardian_socket");
        var key=net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BLOCK,id);
        return net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.BLOCK,id,
            new com.anton.elementalwands.church.GuardianSocketBlock(net.minecraft.block.AbstractBlock.Settings.create()
                .registryKey(key).strength(-1,3600000).nonOpaque().luminance(s -> s.get(com.anton.elementalwands.church.GuardianSocketBlock.PEDESTAL)?(s.get(com.anton.elementalwands.church.GuardianSocketBlock.RITUAL)==0?2:7):0)
                .dropsNothing().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)));
    }

    public static final net.minecraft.block.Block GUARDIAN_PEDESTAL=ritual("guardian_pedestal");
    public static final net.minecraft.block.Block GUARDIAN_CHEST_RUNE=ritual("guardian_chest_rune");
    private static net.minecraft.block.Block ritual(String name) {
        var id=net.minecraft.util.Identifier.of("elementalwands",name);
        var key=net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BLOCK,id);
        return net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.BLOCK,id,
                new com.anton.elementalwands.church.GuardianRitualBlock(net.minecraft.block.AbstractBlock.Settings.create()
                        .registryKey(key).strength(3.5f,6).nonOpaque().dropsNothing()
                        .luminance(s -> s.get(net.minecraft.state.property.Properties.LIT)?5:0)
                        .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)));
    }


    // The graveyard mausoleum: its carved doorway, the veil that starts the Necromancer's ritual,
    // the void windows, and unbreakable look-alikes of the vanilla stone it is built from.
    public static final net.minecraft.block.Block MAUSOLEUM_ARCH=mausoleum("mausoleum_arch",
            settings -> new com.anton.elementalwands.crypt.MausoleumArchBlock(settings.nonOpaque().sounds(net.minecraft.sound.BlockSoundGroup.DEEPSLATE_BRICKS)));
    public static final net.minecraft.block.Block MAUSOLEUM_VEIL=mausoleum("mausoleum_veil",
            settings -> new com.anton.elementalwands.crypt.MausoleumVeilBlock(settings.noCollision().nonOpaque().luminance(s -> 6)));
    public static final net.minecraft.block.Block MAUSOLEUM_WINDOW=mausoleum("mausoleum_window",
            settings -> new com.anton.elementalwands.crypt.MausoleumWindowBlock(settings.nonOpaque().luminance(s -> 2)
                    .sounds(net.minecraft.sound.BlockSoundGroup.DEEPSLATE_BRICKS)));
    /** Same blocks, same order, as SEALED in art/hollow_crypt/build_layout.py, which writes their block states. */
    public static final java.util.List<net.minecraft.block.Block> SEALED=java.util.List.of(
            sealed(net.minecraft.block.Blocks.COBBLED_DEEPSLATE), sealed(net.minecraft.block.Blocks.POLISHED_DEEPSLATE),
            sealed(net.minecraft.block.Blocks.DEEPSLATE_TILES), sealed(net.minecraft.block.Blocks.DEEPSLATE_BRICKS),
            sealed(net.minecraft.block.Blocks.CRACKED_DEEPSLATE_BRICKS), sealed(net.minecraft.block.Blocks.CHISELED_DEEPSLATE),
            sealed(net.minecraft.block.Blocks.POLISHED_BLACKSTONE_BRICKS), sealed(net.minecraft.block.Blocks.CHISELED_POLISHED_BLACKSTONE),
            sealed(net.minecraft.block.Blocks.DEEPSLATE_BRICK_STAIRS), sealed(net.minecraft.block.Blocks.DEEPSLATE_TILE_STAIRS),
            sealed(net.minecraft.block.Blocks.DEEPSLATE_BRICK_SLAB), sealed(net.minecraft.block.Blocks.POLISHED_DEEPSLATE_WALL));

    private static net.minecraft.block.Block mausoleum(String name,
            java.util.function.Function<net.minecraft.block.AbstractBlock.Settings,net.minecraft.block.Block> factory) {
        var id=net.minecraft.util.Identifier.of("elementalwands",name);
        var key=net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BLOCK,id);
        return net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.BLOCK,id,
                factory.apply(net.minecraft.block.AbstractBlock.Settings.create().registryKey(key).strength(-1,3600000)
                        .mapColor(net.minecraft.block.MapColor.DEEPSLATE_GRAY).dropsNothing()
                        .allowsSpawning((state,world,pos,type) -> false).pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)));
    }

    /** Looks, sounds and shapes like the vanilla block, but can't be mined, blown up or pushed. */
    private static net.minecraft.block.Block sealed(net.minecraft.block.Block vanilla) {
        String name="sealed_"+net.minecraft.registry.Registries.BLOCK.getId(vanilla).getPath();
        var id=net.minecraft.util.Identifier.of("elementalwands",name);
        var key=net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BLOCK,id);
        var settings=net.minecraft.block.AbstractBlock.Settings.copy(vanilla).registryKey(key).strength(-1,3600000).dropsNothing()
                .allowsSpawning((state,world,pos,type) -> false).pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK);
        net.minecraft.block.Block block=switch(vanilla){
            case net.minecraft.block.StairsBlock stairs -> new com.anton.elementalwands.crypt.SealedStairsBlock(
                    vanilla==net.minecraft.block.Blocks.DEEPSLATE_TILE_STAIRS?net.minecraft.block.Blocks.DEEPSLATE_TILES.getDefaultState()
                            :net.minecraft.block.Blocks.DEEPSLATE_BRICKS.getDefaultState(),settings);
            case net.minecraft.block.SlabBlock slab -> new net.minecraft.block.SlabBlock(settings);
            case net.minecraft.block.WallBlock wall -> new net.minecraft.block.WallBlock(settings);
            default -> new net.minecraft.block.Block(settings);
        };
        return net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.BLOCK,id,block);
    }

    public static void registerAll() {
        // Internal encounter blocks; no items or recipes. Crystal ores remain removed.
    }
}
