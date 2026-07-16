package com.anton.elementalwands.client.particle;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * A reusable camera-facing spell particle whose motion and presentation are
 * driven by a profile rather than hard-coded in each ability.
 */
final class ElementalBillboardParticle extends BillboardParticle {

    enum SpriteMode {
        RANDOM,
        BY_AGE
    }

    record Profile(
            float minScale,
            float maxScale,
            float endScaleMultiplier,
            int minLifetime,
            int maxLifetime,
            float peakAlpha,
            float fadeInFraction,
            float fadeOutFraction,
            float gravity,
            float drag,
            float minRotationSpeed,
            float maxRotationSpeed,
            int colorStart,
            int colorEnd,
            SpriteMode spriteMode,
            boolean collidesWithWorld,
            boolean fullBright) {

        Profile {
            if (minScale <= 0.0f || maxScale < minScale) {
                throw new IllegalArgumentException("Invalid particle scale range");
            }
            if (minLifetime <= 0 || maxLifetime < minLifetime) {
                throw new IllegalArgumentException("Invalid particle lifetime range");
            }
            if (peakAlpha < 0.0f || peakAlpha > 1.0f) {
                throw new IllegalArgumentException("Particle alpha must be between zero and one");
            }
            if (fadeInFraction < 0.0f || fadeOutFraction < fadeInFraction || fadeOutFraction > 1.0f) {
                throw new IllegalArgumentException("Invalid particle alpha curve");
            }
        }
    }

    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    private final SpriteProvider spriteProvider;
    private final Profile profile;
    private final float startScale;
    private final float rotationSpeed;

    ElementalBillboardParticle(
            ClientWorld world,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            Random random,
            SpriteProvider spriteProvider,
            Profile profile,
            boolean randomInitialRotation) {
        super(world, x, y, z, spriteProvider.getFirst());
        this.spriteProvider = spriteProvider;
        this.profile = profile;

        setVelocity(velocityX, velocityY, velocityZ);
        gravityStrength = profile.gravity();
        velocityMultiplier = profile.drag();
        collidesWithWorld = profile.collidesWithWorld();

        maxAge = randomIntInclusive(random, profile.minLifetime(), profile.maxLifetime());
        startScale = randomFloat(random, profile.minScale(), profile.maxScale());
        scale = startScale;
        rotationSpeed = randomFloat(random, profile.minRotationSpeed(), profile.maxRotationSpeed());
        zRotation = randomInitialRotation ? random.nextFloat() * MathHelper.TAU : 0.0f;
        lastZRotation = zRotation;

        float colorBlend = random.nextFloat();
        setColor(
                MathHelper.lerp(colorBlend, red(profile.colorStart()), red(profile.colorEnd())),
                MathHelper.lerp(colorBlend, green(profile.colorStart()), green(profile.colorEnd())),
                MathHelper.lerp(colorBlend, blue(profile.colorStart()), blue(profile.colorEnd())));

        if (profile.spriteMode() == SpriteMode.RANDOM) {
            setSprite(spriteProvider.getSprite(random));
        } else {
            updateSprite(spriteProvider);
        }
        updatePresentation();
    }

    @Override
    public void tick() {
        lastZRotation = zRotation;
        super.tick();
        if (dead) {
            return;
        }

        zRotation += rotationSpeed;
        if (profile.spriteMode() == SpriteMode.BY_AGE) {
            updateSprite(spriteProvider);
        }
        updatePresentation();
    }

    @Override
    protected RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Override
    protected int getBrightness(float tint) {
        return profile.fullBright() ? FULL_BRIGHT_LIGHT : super.getBrightness(tint);
    }

    private void updatePresentation() {
        float progress = MathHelper.clamp((float) age / (float) maxAge, 0.0f, 1.0f);
        scale = MathHelper.lerp(progress, startScale, startScale * profile.endScaleMultiplier());

        float alphaMultiplier;
        if (profile.fadeInFraction() > 0.0f && progress < profile.fadeInFraction()) {
            alphaMultiplier = progress / profile.fadeInFraction();
        } else if (progress > profile.fadeOutFraction()) {
            float fadeDuration = 1.0f - profile.fadeOutFraction();
            alphaMultiplier = fadeDuration <= 0.0f
                    ? 0.0f
                    : 1.0f - ((progress - profile.fadeOutFraction()) / fadeDuration);
        } else {
            alphaMultiplier = 1.0f;
        }
        setAlpha(profile.peakAlpha() * MathHelper.clamp(alphaMultiplier, 0.0f, 1.0f));
    }

    private static int randomIntInclusive(Random random, int min, int max) {
        return min == max ? min : min + random.nextInt(max - min + 1);
    }

    private static float randomFloat(Random random, float min, float max) {
        return min == max ? min : MathHelper.lerp(random.nextFloat(), min, max);
    }

    private static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0f;
    }

    private static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0f;
    }

    private static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0f;
    }
}
