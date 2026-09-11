package com.anton.elementalwands.registry;

public class ModBlocks {
    public static final net.minecraft.block.Block GUARDIAN_SOCKET=socket();
    private static net.minecraft.block.Block socket() {
        var id=net.minecraft.util.Identifier.of("elementalwands","guardian_socket");
        var key=net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BLOCK,id);
        return net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.BLOCK,id,
            new com.anton.elementalwands.church.GuardianSocketBlock(net.minecraft.block.AbstractBlock.Settings.create()
                .registryKey(key).strength(-1,3600000).dropsNothing().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)));
    }


    public static final net.minecraft.block.Block ARENA_STONE=arena("guardian_arena_stone");
    public static final net.minecraft.block.Block ARENA_DARK=arena("guardian_arena_dark");
    public static final net.minecraft.block.Block ARENA_LIGHT=arena("guardian_arena_light");

    private static net.minecraft.block.Block arena(String name) {
        var id=net.minecraft.util.Identifier.of("elementalwands",name);
        var key=net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.BLOCK,id);
        var block=new com.anton.elementalwands.block.GuardianArenaBlock(net.minecraft.block.AbstractBlock.Settings.create()
                .registryKey(key).strength(-1,3600000).nonOpaque().dropsNothing().allowsSpawning((state,world,pos,type) -> false)
                .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK));
        return net.minecraft.registry.Registry.register(net.minecraft.registry.Registries.BLOCK,id,block);
    }

    public static void registerAll() {
        // Internal encounter blocks; no items or recipes. Crystal ores remain removed.
    }
}
