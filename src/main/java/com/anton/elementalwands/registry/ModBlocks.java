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


    public static void registerAll() {
        // Internal encounter blocks; no items or recipes. Crystal ores remain removed.
    }
}
