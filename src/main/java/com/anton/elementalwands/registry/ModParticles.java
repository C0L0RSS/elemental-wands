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
    public static final SimpleParticleType FIRE_PYRE_FISSURE = register("fire_pyre_fissure");
    public static final SimpleParticleType FIRE_PYRE_FRONT = register("fire_pyre_front", true);
    // The landing brand and contact sequence remain visible on Minimal particles.
    public static final SimpleParticleType FIRE_METEOR_WARNING = register("fire_meteor_warning", true);
    public static final SimpleParticleType FIRE_METEOR_IMPACT = register("fire_meteor_impact", true);

    public static final SimpleParticleType WIND_MOTE = register("wind_mote");
    public static final SimpleParticleType WIND_CRESCENT = register("wind_crescent");
    public static final SimpleParticleType WIND_AIR_RIBBON = register("wind_air_ribbon");
    public static final SimpleParticleType WIND_BURST_RING = register("wind_burst_ring");
    // The ultimate landing burst is the Wind hero effect and remains visible on Minimal.
    public static final SimpleParticleType WIND_ZEPHYR_IMPACT = register("wind_zephyr_impact", true);
    public static final SimpleParticleType WIND_SLIPSTREAM = register("wind_slipstream");
    public static final SimpleParticleType WIND_SHEAR_FEATHER = register("wind_shear_feather");

    public static final SimpleParticleType STONE_DUST = register("stone_dust");
    public static final SimpleParticleType STONE_SHARD = register("stone_shard");
    public static final SimpleParticleType STONE_FAULT = register("stone_fault", true);
    public static final SimpleParticleType STONE_SHOCKWAVE = register("stone_shockwave");
    public static final SimpleParticleType STONE_TITAN = register("stone_titan", true);

    public static final SimpleParticleType NATURE_POLLEN = register("nature_pollen");
    public static final SimpleParticleType NATURE_PETAL = register("nature_petal");
    public static final SimpleParticleType NATURE_LEAF = register("nature_leaf");
    public static final SimpleParticleType NATURE_VINE = register("nature_vine");
    // The heart remains critical; frequent bloom ambience respects particle settings.
    public static final SimpleParticleType NATURE_BLOOM = register("nature_bloom");
    public static final SimpleParticleType NATURE_HEART = register("nature_heart", true);

    public static final SimpleParticleType SPACE_MOTE = register("space_mote");
    public static final SimpleParticleType SPACE_SINGULARITY = register("space_singularity", true);
    public static final SimpleParticleType SPACE_BROKEN_ORBIT = register("space_broken_orbit");
    public static final SimpleParticleType SPACE_IMPLOSION_RING = register("space_implosion_ring", true);
    // The primary's three-block damage boundary remains readable on Minimal particles.
    public static final SimpleParticleType SPACE_EXPANSION_RING = register("space_expansion_ring", true);
    public static final SimpleParticleType SPACE_RIFT = register("space_rift", true);
    public static final SimpleParticleType SPACE_DYING_STAR_CYAN = register("space_dying_star_cyan");
    public static final SimpleParticleType SPACE_DYING_STAR_MAGENTA = register("space_dying_star_magenta");
    public static final SimpleParticleType SPACE_PINPOINT = register("space_pinpoint", true);
    public static final SimpleParticleType SPACE_ECLIPSE = register("space_eclipse", true);
    public static final SimpleParticleType SPACE_GRAVITY_LENS = register("space_gravity_lens", true);
    public static final SimpleParticleType SPACE_CONSUMPTION = register("space_consumption");
    public static final SimpleParticleType SPACE_FINAL_COLLAPSE = register("space_final_collapse", true);

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
