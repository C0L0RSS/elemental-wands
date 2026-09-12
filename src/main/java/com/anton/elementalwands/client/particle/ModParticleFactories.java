package com.anton.elementalwands.client.particle;

import com.anton.elementalwands.client.particle.ElementalBillboardParticle.Profile;
import com.anton.elementalwands.client.particle.ElementalBillboardParticle.SpriteMode;
import com.anton.elementalwands.registry.ModParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

/** Client-only factory registration and visual tuning for custom spell particles. */
public final class ModParticleFactories {

    private static final Profile FIRE_EMBER = new Profile(
            0.10f, 0.18f, 0.4f,
            10, 18,
            1.0f, 0.0f, 0.65f,
            -0.055f, 0.95f,
            -0.16f, 0.16f,
            0xFFFFFF, 0xFFFFFF,
            SpriteMode.BY_AGE, false, true);

    private static final Profile FIRE_FLAME_RIBBON = new Profile(
            0.17f, 0.29f, 0.46f,
            8, 13,
            0.96f, 0.0f, 0.62f,
            -0.025f, 0.91f,
            -0.065f, 0.065f,
            0xFFFFFF, 0xFFFFFF,
            SpriteMode.BY_AGE, false, true);

    private static final Profile FIRE_IMPACT_RING = new Profile(
            0.36f, 0.50f, 2.85f,
            6, 9,
            1.0f, 0.0f, 0.42f,
            0.0f, 1.0f,
            0.0f, 0.0f,
            0xFFFFFF, 0xFFFFFF,
            SpriteMode.BY_AGE, false, true);

    private static final Profile FIRE_PYRE_FRONT = new Profile(
            1.08f, 1.30f, 1.12f,
            8, 12,
            1.0f, 0.0f, 0.70f,
            -0.012f, 0.97f,
            0.0f, 0.0f,
            0xFFFFFF, 0xFFFFFF,
            SpriteMode.BY_AGE, false, true);

    private static final Profile FIRE_METEOR_SHELL = new Profile(
            1.20f, 1.48f, 0.82f,
            9, 14,
            1.0f, 0.0f, 0.72f,
            -0.015f, 0.96f,
            -0.075f, 0.075f,
            0xFFFFFF, 0xFFFFFF,
            SpriteMode.BY_AGE, false, true);

    private static final Profile FIRE_METEOR_WARNING = new Profile(
            0.28f, 0.42f, 0.54f,
            8, 12,
            0.96f, 0.0f, 0.66f,
            -0.018f, 0.96f,
            -0.025f, 0.025f,
            0xFFFFFF, 0xFFFFFF,
            SpriteMode.BY_AGE, false, true);

    private static final Profile FIRE_METEOR_IMPACT = new Profile(
            1.75f, 2.10f, 2.70f,
            16, 20,
            1.0f, 0.0f, 0.72f,
            -0.014f, 0.975f,
            0.0f, 0.0f,
            0xFFFFFF, 0xFFFFFF,
            SpriteMode.BY_AGE, false, true);

    private static final Profile WIND_MOTE = new Profile(
            0.04f, 0.075f, 0.35f,
            16, 30,
            0.7f, 0.05f, 0.68f,
            -0.012f, 0.985f,
            -0.1f, 0.1f,
            0xFFFFF6, 0xD8DCDA,
            SpriteMode.RANDOM, false, true);

    private static final Profile WIND_CRESCENT = new Profile(
            0.14f, 0.22f, 0.72f,
            9, 15,
            0.9f, 0.03f, 0.62f,
            0.0f, 0.95f,
            -0.045f, 0.045f,
            0xFFFFF7, 0xD1D5D4,
            SpriteMode.BY_AGE, false, true);

    private static final Profile WIND_AIR_RIBBON = new Profile(
            0.12f, 0.2f, 1.3f,
            12, 20,
            0.68f, 0.08f, 0.56f,
            -0.006f, 0.96f,
            -0.055f, 0.055f,
            0xFFFFF4, 0xCDD2D2,
            SpriteMode.BY_AGE, false, true);

    private static final Profile WIND_BURST_RING = new Profile(
            0.34f, 0.48f, 2.7f,
            7, 10,
            0.8f, 0.0f, 0.3f,
            0.0f, 1.0f,
            0.0f, 0.0f,
            0xFFFFF7, 0xD6D8D7,
            SpriteMode.BY_AGE, false, true);

    private static final Profile WIND_ZEPHYR_IMPACT = new Profile(
            1.1f, 1.4f, 2.25f,
            14, 22,
            0.95f, 0.02f, 0.58f,
            0.0f, 0.98f,
            0.0f, 0.0f,
            0xFFFFF8, 0xC8CDCE,
            SpriteMode.BY_AGE, false, true);

    private static final Profile WIND_SLIPSTREAM = new Profile(
            0.2f, 0.32f, 1.55f,
            8, 13,
            0.72f, 0.03f, 0.58f,
            0.0f, 0.97f,
            0.0f, 0.0f,
            0xFFFDF2, 0x9EA4A8,
            SpriteMode.BY_AGE, false, true);

    private static final Profile WIND_SHEAR_FEATHER = new Profile(
            0.12f, 0.21f, 1.85f,
            12, 20,
            0.84f, 0.02f, 0.62f,
            0.035f, 0.96f,
            -0.055f, 0.055f,
            0xFFFFF7, 0x7E858B,
            SpriteMode.BY_AGE, false, true);

    private static final Profile ARCANE_MOTE = new Profile(
            0.045f, 0.085f, 0.35f,
            16, 30,
            0.9f, 0.08f, 0.66f,
            -0.02f, 0.97f,
            -0.14f, 0.14f,
            0xD9F7FF, 0x53A7FF,
            SpriteMode.BY_AGE, false, true);

    private static final Profile ARCANE_THREAD = new Profile(
            0.09f, 0.15f, 1.35f,
            10, 18,
            0.82f, 0.05f, 0.58f,
            0.0f, 0.94f,
            -0.07f, 0.07f,
            0xE8FCFF, 0x6BC5FF,
            SpriteMode.BY_AGE, false, true);

    private ModParticleFactories() {
    }

    public static void registerAll() {
        ParticleFactoryRegistry factories = ParticleFactoryRegistry.getInstance();
        factories.register(ModParticles.FIRE_EMBER,
                sprites -> new ElementalParticleFactory(sprites, FIRE_EMBER));
        factories.register(ModParticles.FIRE_FLAME_RIBBON,
                sprites -> new ElementalParticleFactory(sprites, FIRE_FLAME_RIBBON));
        factories.register(ModParticles.FIRE_IMPACT_RING,
                sprites -> new ElementalParticleFactory(sprites, FIRE_IMPACT_RING, false, false));
        factories.register(ModParticles.FIRE_PYRE_FRONT,
                sprites -> new ElementalParticleFactory(sprites, FIRE_PYRE_FRONT, false, false));
        factories.register(ModParticles.FIRE_METEOR_SHELL,
                sprites -> new ElementalParticleFactory(sprites, FIRE_METEOR_SHELL, false));
        factories.register(ModParticles.FIRE_METEOR_WARNING,
                sprites -> new ElementalParticleFactory(sprites, FIRE_METEOR_WARNING, false, false));
        factories.register(ModParticles.FIRE_METEOR_IMPACT,
                sprites -> new ElementalParticleFactory(sprites, FIRE_METEOR_IMPACT, false, false));
        factories.register(ModParticles.WIND_MOTE,
                sprites -> new ElementalParticleFactory(sprites, WIND_MOTE));
        factories.register(ModParticles.WIND_CRESCENT,
                sprites -> new ElementalParticleFactory(sprites, WIND_CRESCENT));
        factories.register(ModParticles.WIND_AIR_RIBBON,
                sprites -> new ElementalParticleFactory(sprites, WIND_AIR_RIBBON, true, false));
        factories.register(ModParticles.WIND_BURST_RING,
                sprites -> new ElementalParticleFactory(sprites, WIND_BURST_RING, false));
        factories.register(ModParticles.WIND_ZEPHYR_IMPACT,
                sprites -> new ElementalParticleFactory(sprites, WIND_ZEPHYR_IMPACT, false, false));
        factories.register(ModParticles.WIND_SLIPSTREAM,
                sprites -> new ElementalParticleFactory(sprites, WIND_SLIPSTREAM, true, false));
        factories.register(ModParticles.WIND_SHEAR_FEATHER,
                sprites -> new ElementalParticleFactory(sprites, WIND_SHEAR_FEATHER));
        factories.register(ModParticles.ARCANE_MOTE,
                sprites -> new ElementalParticleFactory(sprites, ARCANE_MOTE));
        factories.register(ModParticles.ARCANE_THREAD,
                sprites -> new ElementalParticleFactory(sprites, ARCANE_THREAD));
    }
}
