package com.anton.elementalwands.client.particle;

import com.anton.elementalwands.client.particle.ElementalBillboardParticle.Profile;
import com.anton.elementalwands.client.particle.ElementalBillboardParticle.SpriteMode;
import com.anton.elementalwands.registry.ModParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

/**
 * Client-only profiles for Space's Starved Cosmos particle package.
 *
 * <p>The textures carry the affinity's cyan, magenta, violet, bone-white, and
 * near-black material clusters. Profiles therefore stay close to neutral white
 * so the authored pixel shading is preserved instead of flattened by tinting.</p>
 */
public final class SpaceParticleFactories {

    private static final Profile SPACE_MOTE = new Profile(
            0.055f, 0.095f, 0.22f,
            14, 24,
            0.92f, 0.03f, 0.64f,
            -0.014f, 0.965f,
            -0.16f, 0.16f,
            0xFFFFFF, 0xE8E2F0,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_SINGULARITY = new Profile(
            0.54f, 0.68f, 0.78f,
            5, 8,
            1.0f, 0.0f, 0.76f,
            0.0f, 1.0f,
            -0.055f, 0.055f,
            0xFFFFFF, 0xF1EAF7,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_BROKEN_ORBIT = new Profile(
            0.72f, 0.96f, 0.68f,
            8, 13,
            1.0f, 0.02f, 0.68f,
            0.0f, 1.0f,
            -0.18f, 0.18f,
            0xFFFFFF, 0xEAE2F4,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_IMPLOSION_RING = new Profile(
            0.82f, 1.06f, 0.34f,
            8, 11,
            0.96f, 0.0f, 0.72f,
            0.0f, 1.0f,
            -0.025f, 0.025f,
            0xFFFFFF, 0xF0E8F5,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_EXPANSION_RING = new Profile(
            0.58f, 0.68f, 4.6f,
            8, 10,
            0.96f, 0.0f, 0.58f,
            0.0f, 1.0f,
            -0.02f, 0.02f,
            0xFFFFFF, 0xF0E8F5,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_RIFT = new Profile(
            1.15f, 1.42f, 0.72f,
            5, 8,
            1.0f, 0.0f, 0.76f,
            0.0f, 1.0f,
            -0.025f, 0.025f,
            0xFFFFFF, 0xF4EEF8,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_DYING_STAR_CYAN = new Profile(
            1.08f, 1.32f, 0.82f,
            5, 7,
            1.0f, 0.0f, 0.76f,
            0.0f, 0.99f,
            -0.06f, 0.06f,
            0xFFFFFF, 0xEAFBFF,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_DYING_STAR_MAGENTA = new Profile(
            1.08f, 1.32f, 0.82f,
            5, 7,
            1.0f, 0.0f, 0.76f,
            0.0f, 0.99f,
            -0.06f, 0.06f,
            0xFFFFFF, 0xFFEAF9,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_PINPOINT = new Profile(
            0.13f, 0.20f, 0.42f,
            5, 8,
            1.0f, 0.0f, 0.68f,
            0.0f, 0.98f,
            -0.08f, 0.08f,
            0xFFFFFF, 0xFFF8E7,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_ECLIPSE = new Profile(
            4.65f, 5.05f, 0.96f,
            11, 14,
            1.0f, 0.0f, 0.82f,
            0.0f, 1.0f,
            -0.014f, 0.014f,
            0xFFFFFF, 0xF8F3FA,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_GRAVITY_LENS = new Profile(
            4.75f, 5.35f, 1.28f,
            10, 14,
            1.0f, 0.0f, 0.58f,
            0.0f, 0.99f,
            -0.025f, 0.025f,
            0xFFFFFF, 0xEEE8F3,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_CONSUMPTION = new Profile(
            0.15f, 0.27f, 0.38f,
            10, 17,
            0.9f, 0.03f, 0.62f,
            0.0f, 0.94f,
            -0.09f, 0.09f,
            0xFFFFFF, 0xE8DFF0,
            SpriteMode.BY_AGE, false, true);

    private static final Profile SPACE_FINAL_COLLAPSE = new Profile(
            5.15f, 5.45f, 1.24f,
            12, 12,
            1.0f, 0.0f, 0.91f,
            0.0f, 1.0f,
            0.0f, 0.0f,
            0xFFFFFF, 0xFAF4FB,
            SpriteMode.BY_AGE, false, true);

    private SpaceParticleFactories() {
    }

    public static void registerAll() {
        ParticleFactoryRegistry factories = ParticleFactoryRegistry.getInstance();
        factories.register(ModParticles.SPACE_MOTE,
                sprites -> new ElementalParticleFactory(sprites, SPACE_MOTE));
        factories.register(ModParticles.SPACE_SINGULARITY,
                sprites -> new ElementalParticleFactory(sprites, SPACE_SINGULARITY));
        factories.register(ModParticles.SPACE_BROKEN_ORBIT,
                sprites -> new ElementalParticleFactory(sprites, SPACE_BROKEN_ORBIT));
        factories.register(ModParticles.SPACE_IMPLOSION_RING,
                sprites -> new ElementalParticleFactory(sprites, SPACE_IMPLOSION_RING, false));
        factories.register(ModParticles.SPACE_EXPANSION_RING,
                sprites -> new ElementalParticleFactory(sprites, SPACE_EXPANSION_RING, false));
        factories.register(ModParticles.SPACE_RIFT,
                sprites -> new ElementalParticleFactory(sprites, SPACE_RIFT, false, false));
        factories.register(ModParticles.SPACE_DYING_STAR_CYAN,
                sprites -> new ElementalParticleFactory(sprites, SPACE_DYING_STAR_CYAN, false));
        factories.register(ModParticles.SPACE_DYING_STAR_MAGENTA,
                sprites -> new ElementalParticleFactory(sprites, SPACE_DYING_STAR_MAGENTA, false));
        factories.register(ModParticles.SPACE_PINPOINT,
                sprites -> new ElementalParticleFactory(sprites, SPACE_PINPOINT));
        factories.register(ModParticles.SPACE_ECLIPSE,
                sprites -> new ElementalParticleFactory(sprites, SPACE_ECLIPSE, true, false));
        factories.register(ModParticles.SPACE_GRAVITY_LENS,
                sprites -> new ElementalParticleFactory(sprites, SPACE_GRAVITY_LENS, true, false));
        factories.register(ModParticles.SPACE_CONSUMPTION,
                sprites -> new ElementalParticleFactory(sprites, SPACE_CONSUMPTION));
        factories.register(ModParticles.SPACE_FINAL_COLLAPSE,
                sprites -> new ElementalParticleFactory(sprites, SPACE_FINAL_COLLAPSE, false, false));
    }
}
