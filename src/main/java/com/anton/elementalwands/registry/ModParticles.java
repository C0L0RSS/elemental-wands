package com.anton.elementalwands.registry;

import com.anton.elementalwands.ElementalWandsMod;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Particle types shared by spell logic on the logical server and particle
 * factories on the client.
 */
public final class ModParticles {

    public static final SimpleParticleType FIRE_EMBER = register("fire_ember");
    public static final SimpleParticleType FIRE_FLAME_RIBBON = register("fire_flame_ribbon");
    public static final SimpleParticleType FIRE_ASH = register("fire_ash");
    public static final SimpleParticleType FIRE_IMPACT_RING = register("fire_impact_ring");
    public static final SimpleParticleType FIRE_METEOR = register("fire_meteor", true);

    public static final SimpleParticleType WIND_MOTE = register("wind_mote");
    public static final SimpleParticleType WIND_CRESCENT = register("wind_crescent");
    public static final SimpleParticleType WIND_AIR_RIBBON = register("wind_air_ribbon");
    public static final SimpleParticleType WIND_BURST_RING = register("wind_burst_ring");
    // The ultimate landing burst is the Wind hero effect and remains visible on Minimal.
    public static final SimpleParticleType WIND_ZEPHYR_IMPACT = register("wind_zephyr_impact", true);

    /** Shared particles for the Fractured wand's unstable arcane beam. */
    public static final SimpleParticleType ARCANE_MOTE = register("arcane_mote");
    // Beam telegraphing is gameplay-critical, so it remains visible on Minimal.
    public static final SimpleParticleType ARCANE_THREAD = register("arcane_thread", true);

    private ModParticles() {
    }

    /**
     * Class-load hook. Call once from the common mod initializer before any
     * spell attempts to send these particle types to a client.
     */
    public static void registerAll() {
    }

    private static SimpleParticleType register(String path) {
        return register(path, false);
    }

    private static SimpleParticleType register(String path, boolean alwaysSpawn) {
        Identifier id = Identifier.of(ElementalWandsMod.MOD_ID, path);
        return Registry.register(Registries.PARTICLE_TYPE, id, FabricParticleTypes.simple(alwaysSpawn));
    }
}
