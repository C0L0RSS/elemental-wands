package com.anton.elementalwands.client;

import java.util.Map;

import com.anton.elementalwands.registry.ModParticles;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;

/** Client-side world cues for entities whose Entangle state has been synced. */
public final class EntangleClientEffects {

    private static final double MAX_RENDER_DISTANCE_SQ = 32.0 * 32.0;
    private static final int MIN_WRAP_INTERVAL_TICKS = 4;
    private static final int ROOT_CROWN_INTERVAL_TICKS = 8;
    private static final ParticleEffect VINE_PARTICLE = ModParticles.NATURE_VINE;
    private static final ParticleEffect BLOOM_PARTICLE = ModParticles.NATURE_BLOOM;
    private static final ParticleEffect POLLEN_PARTICLE = ModParticles.NATURE_POLLEN;

    private EntangleClientEffects() {}

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        long time = client.world.getTime();
        for (Map.Entry<Integer, ClientPlayerData.EntangleState> entry
                : ClientPlayerData.getEntangledEntities().entrySet()) {
            Entity entity = client.world.getEntityById(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity == client.player) {
                continue;
            }
            if (client.player.squaredDistanceTo(entity) > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }

            int stacks = entry.getValue().stacks();
            boolean visiblyRooted = stacks >= 5 && time < entry.getValue().rootVisualUntilTick();
            int interval = Math.max(MIN_WRAP_INTERVAL_TICKS, 7 - stacks);
            double radius = Math.max(0.34, entity.getWidth() * 0.62);
            long phaseTick = time + entity.getId();

            if (phaseTick % interval == 0) {
                int count = 1 + stacks / 2;
                double climbHeight = entity.getHeight() * (0.09 + stacks * 0.14);
                for (int i = 0; i < count; i++) {
                    double phase = time * (0.15 + stacks * 0.012)
                            + entity.getId() * 0.73 + i * (Math.PI * 2.0 / count);
                    double x = entity.getX() + Math.cos(phase) * radius;
                    double z = entity.getZ() + Math.sin(phase) * radius;
                    double cycle = ((time + entity.getId() * 3L + i * 11L) % 20L) / 19.0;
                    double y = entity.getY() + 0.08 + climbHeight * cycle;
                    client.world.addParticleClient(VINE_PARTICLE, x, y, z, 0.0, 0.012, 0.0);
                    if (stacks >= 3 && (i + time) % 2 == 0) {
                        client.world.addParticleClient(BLOOM_PARTICLE,
                                x, Math.min(entity.getY() + entity.getHeight() * 0.86, y + 0.2), z,
                                0.0, 0.004, 0.0);
                    }
                }
            }

            if (visiblyRooted && phaseTick % ROOT_CROWN_INTERVAL_TICKS == 0) {
                // Five blossoms lock into a crown while the root ring remains low and thorny.
                for (int i = 0; i < 5; i++) {
                    double phase = time * 0.075 + i * (Math.PI * 2.0 / 5.0);
                    double x = entity.getX() + Math.cos(phase) * (radius + 0.04);
                    double z = entity.getZ() + Math.sin(phase) * (radius + 0.04);
                    client.world.addParticleClient(BLOOM_PARTICLE,
                            x, entity.getY() + entity.getHeight() + 0.12, z,
                            0.0, 0.005, 0.0);
                    client.world.addParticleClient(VINE_PARTICLE,
                            x, entity.getY() + 0.05, z, 0.0, 0.003, 0.0);
                }
                client.world.addParticleClient(POLLEN_PARTICLE,
                        entity.getX(), entity.getY() + entity.getHeight() * 0.82, entity.getZ(),
                        0.0, 0.012, 0.0);
            }
        }
    }
}
