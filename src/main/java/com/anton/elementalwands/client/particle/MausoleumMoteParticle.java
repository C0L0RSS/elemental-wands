package com.anton.elementalwands.client.particle;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * A faint mote drawn into the mausoleum veil. Spawned at the veil's eye with its starting offset
 * as the "velocity", like an enchanting-table glyph: it fades in out there, is pulled in slowly
 * and then faster, swaying a little, and fades as it reaches the veil.
 */
final class MausoleumMoteParticle extends BillboardParticle {
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    private final double targetX, targetY, targetZ, offsetX, offsetY, offsetZ;
    private final float phase;

    private MausoleumMoteParticle(ClientWorld world, double x, double y, double z, double dx, double dy, double dz,
                                  SpriteProvider sprites, Random random) {
        super(world, x + dx, y + dy, z + dz, sprites.getSprite(random));
        targetX = x; targetY = y; targetZ = z;
        offsetX = dx; offsetY = dy; offsetZ = dz;
        phase = random.nextFloat() * MathHelper.TAU;
        maxAge = 60 + random.nextInt(61);
        scale = .05f + random.nextFloat() * .03f;
        collidesWithWorld = false;
        gravityStrength = 0;
        setColor(.78f, .93f, .95f);
        setAlpha(0);
    }

    @Override
    public void tick() {
        lastX = x; lastY = y; lastZ = z;
        if (age++ >= maxAge) { markDead(); return; }
        float k = (float) age / maxAge;
        double pull = 1 - Math.pow(k, 1.6), sway = (1 - k) * .15;
        x = targetX + offsetX * pull + MathHelper.sin(phase + age * .065f) * sway;
        y = targetY + offsetY * pull + MathHelper.cos(phase + age * .055f) * sway * .7;
        z = targetZ + offsetZ * pull;
        setAlpha(Math.min(1, k / .2f) * Math.min(1, (1 - k) / .15f) * .55f);
    }

    @Override
    protected RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    @Override
    protected int getBrightness(float tint) {
        return FULL_BRIGHT_LIGHT;
    }

    static final class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider sprites;

        Factory(SpriteProvider sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType effect, ClientWorld world, double x, double y, double z,
                                       double dx, double dy, double dz, Random random) {
            return new MausoleumMoteParticle(world, x, y, z, dx, dy, dz, sprites, random);
        }
    }
}
