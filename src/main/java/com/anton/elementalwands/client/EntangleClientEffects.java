package com.anton.elementalwands.client;

import java.util.Map;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

/** Client-side world cues for entities whose Entangle state has been synced. */
public final class EntangleClientEffects {

    private static final double MAX_RENDER_DISTANCE_SQ = 32.0 * 32.0;
    private static final ParticleEffect VINE_PARTICLE =
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.VINE.getDefaultState());

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
            int interval = Math.max(2, 7 - stacks);
            if ((time + entity.getId()) % interval != 0) {
                continue;
            }

            int count = stacks >= 4 ? 2 : 1;
            double radius = Math.max(0.34, entity.getWidth() * 0.62);
            double climbHeight = entity.getHeight() * (0.08 + stacks * 0.12);
            for (int i = 0; i < count; i++) {
                double phase = time * 0.21 + entity.getId() * 0.73 + i * Math.PI;
                double x = entity.getX() + Math.cos(phase) * radius;
                double z = entity.getZ() + Math.sin(phase) * radius;
                double cycle = ((time + entity.getId() * 3L + i * 11L) % 20L) / 19.0;
                double y = entity.getY() + 0.08 + climbHeight * cycle;
                client.world.addParticleClient(VINE_PARTICLE, x, y, z, 0.0, 0.012, 0.0);
            }

            if (stacks >= 5 && (time + entity.getId()) % 4 == 0) {
                for (int i = 0; i < 2; i++) {
                    double phase = time * 0.13 + i * Math.PI;
                    double x = entity.getX() + Math.cos(phase) * (radius + 0.08);
                    double z = entity.getZ() + Math.sin(phase) * (radius + 0.08);
                    client.world.addParticleClient(VINE_PARTICLE,
                            x, entity.getY() + 0.05, z, 0.0, 0.004, 0.0);
                }
            }
        }
    }
}
