package com.anton.elementalwands.client.particle;

import com.anton.elementalwands.client.particle.ElementalBillboardParticle.Profile;
import com.anton.elementalwands.client.particle.ElementalBillboardParticle.SpriteMode;
import com.anton.elementalwands.registry.ModParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

/** Client profiles for the Fairy Bloom Nature visual language. */
public final class NatureParticleFactories {

    private static final Profile POLLEN = new Profile(
            0.035f, 0.07f, 0.35f,
            24, 46,
            0.9f, 0.08f, 0.7f,
            -0.012f, 0.985f,
            -0.08f, 0.08f,
            0xFFF4B8, 0xE8BD49,
            SpriteMode.RANDOM, false, true);

    private static final Profile PETAL = new Profile(
            0.07f, 0.13f, 0.7f,
            28, 58,
            0.92f, 0.04f, 0.72f,
            0.028f, 0.982f,
            -0.14f, 0.14f,
            0xFFF9DD, 0xF2D98E,
            SpriteMode.RANDOM, true, true);

    private static final Profile LEAF = new Profile(
            0.075f, 0.14f, 0.68f,
            22, 48,
            0.92f, 0.02f, 0.68f,
            0.035f, 0.976f,
            -0.16f, 0.16f,
            0xA6E66F, 0x2F8B4C,
            SpriteMode.RANDOM, true, false);

    private static final Profile VINE = new Profile(
            0.12f, 0.2f, 1.15f,
            12, 24,
            0.94f, 0.04f, 0.72f,
            0.005f, 0.965f,
            -0.065f, 0.065f,
            0x8DDB61, 0x276D3D,
            SpriteMode.BY_AGE, false, true);

    private static final Profile BLOOM = new Profile(
            0.345f, 0.54f, 2.25f,
            10, 17,
            0.96f, 0.02f, 0.56f,
            0.0f, 0.99f,
            -0.025f, 0.025f,
            0xFFFBE5, 0xE8B83B,
            SpriteMode.BY_AGE, false, true);

    private static final Profile HEART = new Profile(
            0.48f, 0.72f, 1.08f,
            22, 36,
            1.0f, 0.08f, 0.76f,
            -0.004f, 0.992f,
            -0.018f, 0.018f,
            0xFFFCE6, 0xEABF49,
            SpriteMode.BY_AGE, false, true);

    private NatureParticleFactories() {
    }

    public static void registerAll() {
        ParticleFactoryRegistry factories = ParticleFactoryRegistry.getInstance();
        factories.register(ModParticles.NATURE_POLLEN,
                sprites -> new ElementalParticleFactory(sprites, POLLEN));
        factories.register(ModParticles.NATURE_PETAL,
                sprites -> new ElementalParticleFactory(sprites, PETAL));
        factories.register(ModParticles.NATURE_LEAF,
                sprites -> new ElementalParticleFactory(sprites, LEAF));
        factories.register(ModParticles.NATURE_VINE,
                sprites -> new ElementalParticleFactory(sprites, VINE));
        factories.register(ModParticles.NATURE_BLOOM,
                sprites -> new ElementalParticleFactory(sprites, BLOOM, false, false));
        factories.register(ModParticles.NATURE_HEART,
                sprites -> new ElementalParticleFactory(sprites, HEART, false, false));
    }
}
