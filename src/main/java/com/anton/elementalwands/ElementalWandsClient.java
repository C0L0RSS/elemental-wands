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
import com.anton.elementalwands.client.renderer.EmptyEntityRenderer;
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
        BlockRenderLayerMap.putBlock(ModSpellBlocks.PYRE_KING_FIRE, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(com.anton.elementalwands.registry.ModBlocks.PYRE_SEAL, BlockRenderLayer.CUTOUT);
        BlockRenderLayerMap.putBlock(com.anton.elementalwands.registry.ModBlocks.PYRE_GATE_VEIL, BlockRenderLayer.TRANSLUCENT);
        BlockRenderLayerMap.putBlock(ModSpellBlocks.STONE_SPIKE, BlockRenderLayer.CUTOUT);
        for (var relic : com.anton.elementalwands.registry.ModBlocks.SUMMIT_RELICS) BlockRenderLayerMap.putBlock(relic, BlockRenderLayer.CUTOUT);
        EntangleClientEffects.register();
        com.anton.elementalwands.client.SeedMarkClientEffects.register();
        for (var block : new net.minecraft.block.Block[]{ModSpellBlocks.NATURE_SEEDLING,
                ModSpellBlocks.NATURE_ROOT_KNOT,ModSpellBlocks.NATURE_ROOTS,
                ModSpellBlocks.NATURE_HEARTWOOD,ModSpellBlocks.NATURE_FLOWERING_LEAVES}) {
            BlockRenderLayerMap.putBlock(block,BlockRenderLayer.CUTOUT);
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(
                    (state,world,pos,tint)->tint<0?-1:0xFF000000|tint,block);
        }
        // The oak's plain leaves reuse the vanilla model, so they take the biome's foliage colour as it does.
        BlockRenderLayerMap.putBlock(ModSpellBlocks.NATURE_OAK_LEAVES,BlockRenderLayer.CUTOUT);
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(
                (state,world,pos,tint)->world!=null && pos!=null
                        ? net.minecraft.client.color.world.BiomeColors.getFoliageColor(world,pos)
                        : net.minecraft.world.biome.FoliageColors.DEFAULT,ModSpellBlocks.NATURE_OAK_LEAVES);


        com.anton.elementalwands.client.WandControls.init();
        com.anton.elementalwands.client.GuardianOfferingHint.init();
        com.anton.elementalwands.client.MausoleumHint.init();
        for(var block:new net.minecraft.block.Block[]{com.anton.elementalwands.registry.ModBlocks.GUARDIAN_SOCKET,
                com.anton.elementalwands.registry.ModBlocks.GUARDIAN_PEDESTAL,
                com.anton.elementalwands.registry.ModBlocks.GUARDIAN_CHEST_RUNE})
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(
                    (state,world,pos,tint)->tint<0?-1:0xFF000000|tint,block);

        // Gravity Bombs, Astral orbs, Singularity Bolts and Hollow Purple orbs are drawn as void glass by BlockSpaceEffects.
        EntityRendererRegistry.register(ModEntities.GRAVITY_BOMB,EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.ASTRAL_DOUBLE,com.anton.elementalwands.client.renderer.AstralDoubleRenderer::new);
        EntityRendererRegistry.register(ModEntities.ASTRAL_ORB,EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.GALE_DAGGER,com.anton.elementalwands.client.renderer.GaleDaggerRenderer::new);
        EntityRendererRegistry.register(ModEntities.SPRINGBLOOM,com.anton.elementalwands.client.renderer.SpringbloomRenderer::new);
        EntityRendererRegistry.register(ModEntities.SENTRY_BLOOM,com.anton.elementalwands.client.renderer.SentryBloomRenderer::new);
        EntityRendererRegistry.register(ModEntities.SENTRY_BLOOM_POD,com.anton.elementalwands.client.renderer.SentryBloomPodRenderer::new);
        EntityRendererRegistry.register(ModEntities.OVERGROWTH_SEED,com.anton.elementalwands.client.renderer.OvergrowthSeedRenderer::new);
        EntityRendererRegistry.register(ModEntities.FAULTLINE_SPIKE,com.anton.elementalwands.client.renderer.FaultlineSpikeRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.UpdraftLaunchPayload.ID, (payload, context) -> {
            var p = context.player();
            p.setVelocity(com.anton.elementalwands.util.UpdraftManager.launch(p.getVelocity()));
            p.setOnGround(false); p.fallDistance = 0;
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.StoneMotionPayload.ID, (payload, context) -> {
            if (context.client().world == null) return;
            var entity = context.client().world.getEntityById(payload.entityId());
            if (entity instanceof com.anton.elementalwands.util.StoneMotionAccess motion)
                motion.elementalwands$stoneMotion(payload.yaw(), payload.speed(), payload.mode());
        });
        EntityRendererRegistry.register(ModEntities.THORN_LASH,com.anton.elementalwands.client.renderer.ThornLashRenderer::new);
        EntityRendererRegistry.register(ModEntities.SEED_PROJECTILE,com.anton.elementalwands.client.renderer.NatureSeedRenderer::new);
        // Sky Shear blades are drawn as block wind by BlockWindEffects.
        EntityRendererRegistry.register(ModEntities.VACUUM_BLADE, EmptyEntityRenderer::new);
        // Fire spell entities are drawn as block fire by BlockFireEffects.
        EntityRendererRegistry.register(ModEntities.INFERNO_WAVE, com.anton.elementalwands.client.renderer.EmptyEntityRenderer::new);
        EntityRendererRegistry.register(net.minecraft.entity.EntityType.FALLING_BLOCK, com.anton.elementalwands.client.renderer.FireMeteorRenderer::new);
        EntityRendererRegistry.register(ModEntities.PYRE_FRONT, com.anton.elementalwands.client.renderer.EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.SINGULARITY_BOLT, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.HOLLOW_PURPLE_ORB, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.AWAKENED_TREE, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.STONE_ZOMBIE, StoneZombieRenderer::new);
        EntityRendererRegistry.register(ModEntities.FIRE_SPIRIT, FireSpiritRenderer::new);
        EntityRendererRegistry.register(ModEntities.FRACTURED_GUARDIAN, FracturedGuardianRenderer::new);
        EntityRendererRegistry.register(ModEntities.STORM_ROC, com.anton.elementalwands.client.renderer.StormRocRenderer::new);
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(com.anton.elementalwands.registry.ModBlocks.STORM_GONG_ENTITY,
                context -> new com.anton.elementalwands.client.renderer.StormGongRenderer<>());
        EntityRendererRegistry.register(ModEntities.ROC_EFFECT, com.anton.elementalwands.client.renderer.RocEffectRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROC_BODY_PART, EmptyEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.HOLLOW_NECROMANCER, com.anton.elementalwands.client.renderer.NecromancerRenderer::new);
        EntityRendererRegistry.register(ModEntities.PYRE_KING, com.anton.elementalwands.client.renderer.PyreKingRenderer::new);
        EntityRendererRegistry.register(ModEntities.SOUL_BOLT, com.anton.elementalwands.client.renderer.SoulBoltRenderer::new);
        EntityRendererRegistry.register(ModEntities.GRASPING_HAND, com.anton.elementalwands.client.renderer.GraspingHandRenderer::new);
        EntityRendererRegistry.register(ModEntities.SOUL_FIREBALL, com.anton.elementalwands.client.renderer.SoulFireballRenderer::new);
        EntityRendererRegistry.register(ModEntities.HARVEST_SOUL, com.anton.elementalwands.client.renderer.HarvestSoulRenderer::new);
        EntityRendererRegistry.register(ModEntities.NECROMANCER_SOUL, com.anton.elementalwands.client.renderer.NecromancerSoulRenderer::new);
        EntityRendererRegistry.register(ModEntities.INTRO_ZOMBIE, com.anton.elementalwands.client.renderer.IntroZombieRenderer::new);
        EntityRendererRegistry.register(ModEntities.INTRO_SOUL, com.anton.elementalwands.client.renderer.IntroSoulRenderer::new);
        com.anton.elementalwands.client.NecromancerIntroClient.init();
        EntityRendererRegistry.register(ModEntities.TRANSFORM_SOUL, com.anton.elementalwands.client.renderer.TransformSoulRenderer::new);
        com.anton.elementalwands.client.NecromancerTransformClient.init();
        EntityRendererRegistry.register(ModEntities.GUARDIAN_INTRO_ZOMBIE, com.anton.elementalwands.client.renderer.GuardianIntroZombieRenderer::new);
        com.anton.elementalwands.client.GuardianIntroClient.init();
        com.anton.elementalwands.client.StormRocIntroClient.init();
        com.anton.elementalwands.client.PyreKingIntroClient.init();
        com.anton.elementalwands.client.GuardianPullClient.init();
        EntityRendererRegistry.register(ModEntities.HOLLOW_CRAWLER, context -> new com.anton.elementalwands.client.renderer.HollowUndeadRenderer<>(context, "hollow_crawler", .6f));
        EntityRendererRegistry.register(ModEntities.HOLLOW_ARCHER, context -> new com.anton.elementalwands.client.renderer.HollowUndeadRenderer<>(context, "hollow_archer", .4f));
        EntityRendererRegistry.register(ModEntities.HOLLOW_BRUTE, context -> new com.anton.elementalwands.client.renderer.HollowUndeadRenderer<>(context, "hollow_brute", .6f));
        com.anton.elementalwands.client.FireLeapClient.init();
        com.anton.elementalwands.client.FireLeapPreview.init();
        EntityRendererRegistry.register(ModEntities.FLASHOVER_EMBER, com.anton.elementalwands.client.renderer.EmptyEntityRenderer::new);
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
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.SyncSeedMarkPayload.ID, (payload, context) -> {
            if (context.client().world != null)
                ClientPlayerData.setSeedMark(payload.entityId(), context.client().world.getTime() + payload.ticks());
        });
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
            ClientPlayerData.setSeedMark(-1, 0);
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) ->
                ClientPlayerData.clearEntangleState(entity.getId()));

        ClientTickEvents.END_CLIENT_TICK.register(ElementalWandsClient::tickClient);

        HudRenderCallback.EVENT.register(new com.anton.elementalwands.client.overlay.WandHudOverlay());
        HudRenderCallback.EVENT.register(new EntangleHudOverlay());
        com.anton.elementalwands.client.NecromancerDrainEffects.register();
        com.anton.elementalwands.client.blockfire.BlockFireEffects.register();
        com.anton.elementalwands.client.blockwind.BlockWindEffects.register();
        com.anton.elementalwands.client.blockspace.BlockSpaceEffects.register();
        com.anton.elementalwands.client.blocknature.BlockNatureEffects.register();
        com.anton.elementalwands.client.NaveShaftEffects.register();
        com.anton.elementalwands.client.PyreSeaHaze.register();
        com.anton.elementalwands.client.SummitCloudEffects.register();
        ClientTickEvents.END_CLIENT_TICK.register(com.anton.elementalwands.client.SummitStormClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(com.anton.elementalwands.client.StormRocMotion::tick);
        HudRenderCallback.EVENT.register(new com.anton.elementalwands.client.overlay.NecromancerDrainOverlay());
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
