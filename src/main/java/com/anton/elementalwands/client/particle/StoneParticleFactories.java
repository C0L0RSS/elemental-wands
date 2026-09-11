package com.anton.elementalwands.client.particle;

import com.anton.elementalwands.client.particle.ElementalBillboardParticle.Profile;
import com.anton.elementalwands.client.particle.ElementalBillboardParticle.SpriteMode;
import com.anton.elementalwands.registry.ModParticles;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;

/** Client profiles for weighted debris, faults, and Titan-scale Stone effects. */
public final class StoneParticleFactories {

    private static final Profile DUST = new Profile(
            0.055f, 0.11f, 0.65f,
            24, 44,
            0.72f, 0.0f, 0.58f,
            0.09f, 0.95f,
            -0.10f, 0.10f,
            0xF0F3F1, 0xBEC7C8,
            SpriteMode.RANDOM, true, false);

    private static final Profile SHARD = new Profile(
            0.09f, 0.16f, 0.55f,
            16, 28,
            1.0f, 0.0f, 0.68f,
            0.13f, 0.96f,
            -0.22f, 0.22f,
            0xEFF3F1, 0xAAB5B8,
            SpriteMode.BY_AGE, true, false);

    private static final Profile FAULT = new Profile(
            0.28f, 0.40f, 1.8f,
            8, 13,
            0.9f, 0.0f, 0.5f,
            0.0f, 1.0f,
            -0.02f, 0.02f,
            0xF3F6F2, 0xB9C6BC,
            SpriteMode.BY_AGE, false, false);

    private static final Profile SHOCKWAVE = new Profile(
            0.45f, 0.65f, 2.9f,
            8, 12,
            0.82f, 0.0f, 0.38f,
            0.0f, 1.0f,
            0.0f, 0.0f,
            0xEDF2F0, 0xB9C3C5,
            SpriteMode.BY_AGE, false, false);

    private static final Profile TITAN = new Profile(
            1.2f, 1.6f, 1.35f,
            18, 28,
            0.95f, 0.05f, 0.7f,
            0.0f, 0.98f,
            0.0f, 0.0f,
            0xEFF3F0, 0xADB9BA,
            SpriteMode.BY_AGE, false, false);

    private StoneParticleFactories() {
    }

    public static void registerAll() {
        ParticleFactoryRegistry factories = ParticleFactoryRegistry.getInstance();
        factories.register(ModParticles.STONE_DUST,
                sprites -> new ElementalParticleFactory(sprites, DUST));
        factories.register(ModParticles.STONE_SHARD,
                sprites -> new ElementalParticleFactory(sprites, SHARD));
        factories.register(ModParticles.STONE_FAULT,
                sprites -> new ElementalParticleFactory(sprites, FAULT));
        factories.register(ModParticles.STONE_SHOCKWAVE,
                sprites -> new ElementalParticleFactory(sprites, SHOCKWAVE, false, false));
        factories.register(ModParticles.STONE_TITAN,
                sprites -> new ElementalParticleFactory(sprites, TITAN, false, false));
    }
}
