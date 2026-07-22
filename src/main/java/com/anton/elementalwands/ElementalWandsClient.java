package com.anton.elementalwands;

import java.util.Optional;

import com.anton.elementalwands.client.ClientPlayerData;
import com.anton.elementalwands.client.EntangleClientEffects;
import com.anton.elementalwands.client.particle.ModParticleFactories;
import com.anton.elementalwands.client.particle.NatureParticleFactories;
import com.anton.elementalwands.client.particle.SpaceParticleFactories;
import com.anton.elementalwands.client.particle.StoneParticleFactories;
import com.anton.elementalwands.client.overlay.EntangleHudOverlay;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.client.renderer.AnimatedSpellBillboardRenderer;
import com.anton.elementalwands.client.renderer.EmptyEntityRenderer;
import com.anton.elementalwands.client.renderer.FireSpiritRenderer;
import com.anton.elementalwands.client.renderer.SpellBillboardRenderer;
import com.anton.elementalwands.client.renderer.StoneZombieRenderer;
import com.anton.elementalwands.registry.ModSpellBlocks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.lwjgl.glfw.GLFW;

public class ElementalWandsClient implements ClientModInitializer {

    private static KeyBinding ultimateKey;

    @Override
    public void onInitializeClient() {
        ModParticleFactories.registerAll();
        StoneParticleFactories.registerAll();
        NatureParticleFactories.registerAll();
        SpaceParticleFactories.registerAll();
        BlockRenderLayerMap.putBlock(ModSpellBlocks.INFERNO_FLAME, BlockRenderLayer.TRANSLUCENT);
        BlockRenderLayerMap.putBlock(ModSpellBlocks.STONE_SPIKE, BlockRenderLayer.CUTOUT);

        ultimateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.elementalwands.ultimate",
                GLFW.GLFW_KEY_X,
                new KeyBinding.Category(Identifier.of("elementalwands", "general"))));

        EntityRendererRegistry.register(ModEntities.BOULDER_PROJECTILE, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.SEED_PROJECTILE,
                context -> new SpellBillboardRenderer<>(context,
                        Identifier.of("elementalwands", "textures/entity/winged_seed.png"),
                        0.72f, 0.46f, 0.0f, true));
        EntityRendererRegistry.register(ModEntities.VACUUM_BLADE,
                context -> new AnimatedSpellBillboardRenderer<>(context,
                        Identifier.of("elementalwands", "textures/entity/vacuum_blade"),
                        6, 1.1f, 0.55f, 0.0f, true,
                        blade -> blade.isMirrored()));
        EntityRendererRegistry.register(ModEntities.CALAMITY_TORNADO, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.INFERNO_WAVE,
                context -> new AnimatedSpellBillboardRenderer<>(context,
                        Identifier.of("elementalwands", "textures/entity/inferno_wave"),
                        6, 3.2f, 2.0f, 0.0f, true));
        EntityRendererRegistry.register(ModEntities.SINGULARITY_BOLT, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.HOLLOW_PURPLE_ORB, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.AWAKENED_TREE, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.STONE_ZOMBIE, StoneZombieRenderer::new);
        EntityRendererRegistry.register(ModEntities.FIRE_SPIRIT, FireSpiritRenderer::new);

        // Receive synced player data from server
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncPlayerDataPayload.ID,
                (payload, context) -> ClientPlayerData.setUnlockedSkills(payload.unlockedSkills(), payload.affinity()));
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncNatureSeedlingsPayload.ID,
                (payload, context) -> ClientPlayerData.setNatureSeedlings(payload.positions()));
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncEntangleStacksPayload.ID,
                (payload, context) -> {
                    long now = context.client().world != null ? context.client().world.getTime() : 0L;
                    ClientPlayerData.setEntangleStacks(payload.entityId(), payload.stacks(), now,
                            payload.rootVisualTicks());
                });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientPlayerData.reset());
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            ClientPlayerData.clearEntangleStates();
            ClientPlayerData.clearNatureSeedlings();
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) ->
                ClientPlayerData.clearEntangleState(entity.getId()));

        ClientTickEvents.END_CLIENT_TICK.register(ElementalWandsClient::tickClient);

        HudRenderCallback.EVENT.register(new com.anton.elementalwands.client.overlay.WandHudOverlay());
        HudRenderCallback.EVENT.register(new EntangleHudOverlay());
    }

    private static void tickClient(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        EntangleClientEffects.tick(client);
        if (client.currentScreen != null) return;
        if (!(client.player.getMainHandStack().getItem() instanceof AbstractWandItem)) return;

        spawnNatureSeedlingTargetPreview(client);

        while (ultimateKey.wasPressed()) {
            ClientPlayNetworking.send(ModNetworking.CastUltimatePayload.INSTANCE);
        }
    }

    private static void spawnNatureSeedlingTargetPreview(MinecraftClient client) {
        if (ClientPlayerData.getAffinity() != WizardAffinity.NATURE || !ClientPlayerData.isUltimateUnlocked()) {
            return;
        }

        Optional<BlockPos> target = findTargetedSyncedSeedling(client, AbstractWandItem.DEFAULT_RANGE);
        if (target.isEmpty()) return;

        BlockPos pos = target.get();
        long time = client.world.getTime();
        if (time % 2 != 0) return;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.15;
        double cz = pos.getZ() + 0.5;
        for (int i = 0; i < 8; i++) {
            double angle = (time * 0.22) + i * (Math.PI * 2.0 / 8.0);
            double x = cx + Math.cos(angle) * 0.42;
            double z = cz + Math.sin(angle) * 0.42;
            client.world.addParticleClient(ModParticles.NATURE_POLLEN, x, cy, z, 0.0, 0.02, 0.0);
        }
        client.world.addParticleClient(ModParticles.NATURE_BLOOM, cx, cy + 0.35, cz, 0.0, 0.0, 0.0);
    }

    private static Optional<BlockPos> findTargetedSyncedSeedling(MinecraftClient client, double range) {
        if (client.player == null || client.world == null) return Optional.empty();

        Vec3d start = client.player.getEyePos();
        Vec3d direction = client.player.getRotationVec(1.0f).normalize();
        Vec3d end = start.add(direction.multiply(range));

        BlockHitResult blockHit = client.world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                client.player));
        double blockLimitSq = blockHit.getType() == HitResult.Type.MISS
                ? range * range
                : start.squaredDistanceTo(blockHit.getPos()) + 0.25;

        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (BlockPos pos : ClientPlayerData.getNatureSeedlings()) {
            Optional<Vec3d> hit = new Box(pos).expand(0.2).raycast(start, end);
            if (hit.isEmpty()) continue;

            double distSq = start.squaredDistanceTo(hit.get());
            if (distSq > blockLimitSq || distSq >= bestDistSq) continue;

            best = pos;
            bestDistSq = distSq;
        }

        return Optional.ofNullable(best);
    }
}
