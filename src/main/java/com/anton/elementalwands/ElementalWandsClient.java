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
import com.anton.elementalwands.client.renderer.FireWaveRenderer;
import com.anton.elementalwands.client.renderer.FireSpiritRenderer;
import com.anton.elementalwands.client.renderer.FracturedGuardianRenderer;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class ElementalWandsClient implements ClientModInitializer {


    @Override
    public void onInitializeClient() {
        com.anton.elementalwands.client.wand.WandItemModel.register();
        ModParticleFactories.registerAll();
        StoneParticleFactories.registerAll();
        NatureParticleFactories.registerAll();
        SpaceParticleFactories.registerAll();
        BlockRenderLayerMap.putBlock(ModSpellBlocks.INFERNO_FLAME, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModSpellBlocks.PYRE_FLAME, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(ModSpellBlocks.STONE_SPIKE, BlockRenderLayer.CUTOUT);
        EntangleClientEffects.register();
        for (var block : new net.minecraft.block.Block[]{ModSpellBlocks.NATURE_SEEDLING,
                ModSpellBlocks.NATURE_ROOT_KNOT,ModSpellBlocks.NATURE_ROOTS,ModSpellBlocks.NATURE_RAFT,
                ModSpellBlocks.NATURE_HEARTWOOD,ModSpellBlocks.NATURE_FLOWERING_LEAVES}) {
            BlockRenderLayerMap.putBlock(block,BlockRenderLayer.CUTOUT);
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(
                    (state,world,pos,tint)->tint<0?-1:0xFF000000|tint,block);
        }


        com.anton.elementalwands.client.WandControls.init();
        com.anton.elementalwands.client.GuardianOfferingHint.init();
        for(var block:new net.minecraft.block.Block[]{com.anton.elementalwands.registry.ModBlocks.GUARDIAN_SOCKET,
                com.anton.elementalwands.registry.ModBlocks.GUARDIAN_PEDESTAL,
                com.anton.elementalwands.registry.ModBlocks.GUARDIAN_CHEST_RUNE})
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(
                    (state,world,pos,tint)->tint<0?-1:0xFF000000|tint,block);

        EntityRendererRegistry.register(ModEntities.OVERGROWTH_SEED,com.anton.elementalwands.client.renderer.OvergrowthSeedRenderer::new);
        EntityRendererRegistry.register(ModEntities.THORN_LASH,com.anton.elementalwands.client.renderer.ThornLashRenderer::new);
        EntityRendererRegistry.register(ModEntities.SEED_PROJECTILE,com.anton.elementalwands.client.renderer.NatureSeedRenderer::new);
        EntityRendererRegistry.register(ModEntities.VACUUM_BLADE,
                context -> new AnimatedSpellBillboardRenderer<>(context,
                        Identifier.of("elementalwands", "textures/entity/vacuum_blade"),
                        6, .95f, .42f, 0.0f, true,
                        blade -> blade.isMirrored()));
        EntityRendererRegistry.register(ModEntities.INFERNO_WAVE, FireWaveRenderer::new);
        EntityRendererRegistry.register(net.minecraft.entity.EntityType.FALLING_BLOCK, com.anton.elementalwands.client.renderer.FireMeteorRenderer::new);
        EntityRendererRegistry.register(ModEntities.PYRE_FRONT, com.anton.elementalwands.client.renderer.PyreFrontRenderer::new);
        EntityRendererRegistry.register(ModEntities.SINGULARITY_BOLT, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.HOLLOW_PURPLE_ORB, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.AWAKENED_TREE, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.STONE_ZOMBIE, StoneZombieRenderer::new);
        EntityRendererRegistry.register(ModEntities.FIRE_SPIRIT, FireSpiritRenderer::new);
        EntityRendererRegistry.register(ModEntities.FRACTURED_GUARDIAN, FracturedGuardianRenderer::new);
        EntityRendererRegistry.register(ModEntities.GUARDIAN_LIFT, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.FIRE_LEAP, EmptyEntityRenderer::new);
        com.anton.elementalwands.client.FireLeapPreview.init();
        EntityRendererRegistry.register(ModEntities.FLASHOVER_EMBER, com.anton.elementalwands.client.renderer.FlashoverEmberRenderer::new);
        EntityRendererRegistry.register(ModEntities.GUARDIAN_ARENA, com.anton.elementalwands.client.renderer.GuardianArenaRenderer::new);
        EntityRendererRegistry.register(ModEntities.GUARDIAN_ROCK, com.anton.elementalwands.client.renderer.GuardianRockRenderer::new);
        EntityRendererRegistry.register(ModEntities.STONE_CLUSTER, com.anton.elementalwands.client.renderer.StoneClusterRenderer::new);

        // Receive synced player data from server
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncPlayerDataPayload.ID,
                (payload, context) -> {
                    ClientPlayerData.setUnlockedSkills(payload.unlockedSkills(), payload.affinity());
                    ClientPlayerData.setOwned(payload.owned());
                    ClientPlayerData.setHubData(payload.flux(), payload.loadout(), payload.canEdit());
                    ClientPlayerData.setProgress(payload.xp(),payload.credits());
                    if (context.client().currentScreen instanceof com.anton.elementalwands.client.screen.WandHubScreen hub) hub.refresh();
                });
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.FlashoverStatePayload.ID,(payload,context) -> {
            long now=context.client().world==null?0:context.client().world.getTime();
            ClientPlayerData.setFlashover(payload.active(),payload.armed(),payload.remaining(),payload.duration(),payload.slots(),now);
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.FireBuildPayload.ID, (payload, context) -> {
            long now = context.client().world == null ? 0 : context.client().world.getTime();
            ClientPlayerData.setFireBuild(payload.heat(),payload.overheated(),payload.hopRemaining(),now);
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.WelcomePayload.ID, (payload, context) -> com.anton.elementalwands.client.WandWelcome.queue());
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.HubFeedbackPayload.ID, (payload, context) -> {
            if (payload.open()) context.client().setScreen(new com.anton.elementalwands.client.screen.WandHubScreen());
            if (context.client().currentScreen instanceof com.anton.elementalwands.client.screen.WandHubScreen hub) hub.feedback(payload.message());
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncNatureSeedlingsPayload.ID,
                (payload, context) -> ClientPlayerData.setNatureSeedlings(payload.positions()));
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncEntangleStacksPayload.ID,
                (payload, context) -> {
                    long now = context.client().world != null ? context.client().world.getTime() : 0L;
                    ClientPlayerData.setEntangleStacks(payload.entityId(), payload.stacks(), now,
                            payload.rootVisualTicks());
                });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            com.anton.elementalwands.client.WandWelcome.reset();
            ClientPlayerData.reset(); com.anton.elementalwands.client.WandControls.clear();
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncStoneClusterPayload.ID, (payload, context) -> {
            if (context.client().world != null) ClientPlayerData.setStoneCluster(payload.mass(),payload.remaining(),
                    payload.duration(),context.client().world.getTime());
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.StoneStaggerPayload.ID, (payload, context) -> {
            if (context.client().world == null) return;
            var target=context.client().world.getEntityById(payload.entityId());
            if (target instanceof com.anton.elementalwands.util.StoneStaggerAccess stagger) {
                stagger.elementalwands$staggerUntil(context.client().world.getTime()+payload.ticks());
                target.setSprinting(false);
                if (target instanceof net.minecraft.entity.player.PlayerEntity player) player.stopGliding();
            }
        });
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            com.anton.elementalwands.client.WandControls.clear();
            ClientPlayerData.clearStoneCluster();
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
        com.anton.elementalwands.client.WandWelcome.tick(client);
        EntangleClientEffects.tick(client);
        com.anton.elementalwands.client.WandControls.tick(client);
        if (client.currentScreen != null) return;
        if (!(client.player.getMainHandStack().getItem() instanceof AbstractWandItem)) return;
    }

}
