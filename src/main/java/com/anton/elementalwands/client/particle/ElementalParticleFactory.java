package com.anton.elementalwands.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;

final class ElementalParticleFactory implements ParticleFactory<SimpleParticleType> {

    private final SpriteProvider spriteProvider;
    private final ElementalBillboardParticle.Profile profile;
    private final boolean inheritSpawnVelocity;
    private final boolean randomInitialRotation;

    ElementalParticleFactory(SpriteProvider spriteProvider, ElementalBillboardParticle.Profile profile) {
        this(spriteProvider, profile, true, true);
    }

    ElementalParticleFactory(SpriteProvider spriteProvider, ElementalBillboardParticle.Profile profile,
            boolean inheritSpawnVelocity) {
        this(spriteProvider, profile, inheritSpawnVelocity, true);
    }

    ElementalParticleFactory(SpriteProvider spriteProvider, ElementalBillboardParticle.Profile profile,
            boolean inheritSpawnVelocity, boolean randomInitialRotation) {
        this.spriteProvider = spriteProvider;
        this.profile = profile;
        this.inheritSpawnVelocity = inheritSpawnVelocity;
        this.randomInitialRotation = randomInitialRotation;
    }

    @Override
    public Particle createParticle(
            SimpleParticleType effect,
            ClientWorld world,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            Random random) {
        return new ElementalBillboardParticle(
                world,
                x,
                y,
                z,
                inheritSpawnVelocity ? velocityX : 0.0,
                inheritSpawnVelocity ? velocityY : 0.0,
                inheritSpawnVelocity ? velocityZ : 0.0,
                random,
                spriteProvider,
                profile,
                randomInitialRotation);
    }
}
