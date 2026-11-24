package com.anton.elementalwands.client.blockink;

import com.anton.elementalwands.client.blockfire.BlockFireMesh;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.Identifier;

/**
 * Draws the First Wizard's effects each frame: the ink round every copy ({@link CopyFx}) and every effect entity by its
 * kind's drawer ({@link InkDrawers}). Spell effects follow the entities' synced clocks. The copy also keeps a short
 * history of its joints for the return from a cast. The meshes for ink and each element draw before the translucent pass.
 */
public final class InkEffects {
    private record Meshes(BlockFireMesh fire, AtlasMesh ink, AtlasMesh space, AtlasMesh wind, AtlasMesh stone, AtlasMesh nature) {}
    private static final RenderStateDataKey<Meshes> MESHES = RenderStateDataKey.create(() -> "First Wizard ink");
    private static final Meshes NONE = new Meshes(null, null, null, null, null, null);
    private static final Identifier FIRE_ATLAS = Identifier.of("elementalwands", "textures/misc/first_wizard_fire.png");
    private static final Identifier WIND_ATLAS = Identifier.of("elementalwands", "textures/misc/first_wizard_wind.png");
    private static final Identifier SPACE_ATLAS = Identifier.of("elementalwands", "textures/misc/first_wizard_space.png");
    private static final RenderLayer FIRE = RenderLayer.getEntityCutoutNoCull(FIRE_ATLAS);
    private static final RenderLayer FIRE_SMOKE = RenderLayer.getEntityTranslucent(FIRE_ATLAS);

    private InkEffects() {}

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            ClientWorld world = context.world();
            context.worldState().setData(MESHES, NONE);
            if (world == null) return;
            boolean any = false;
            for (Entity e : world.getEntities()) if (e instanceof FirstWizardEntity || e instanceof FirstWizardEffectEntity) { any = true; break; }
            if (!any) return;
            var client = MinecraftClient.getInstance();
            float delta = context.tickCounter().getTickProgress(false);
            double now = (world.getTime() + delta) / 20.0;
            boolean embers = client.options.getParticles().getValue() == ParticlesMode.ALL;
            boolean lens = !context.camera().isThirdPerson();
            var camera = context.camera().getPos();
            var fire = new BlockFireMesh(camera, lens, embers, now, world);
            var ink = new AtlasMesh(InkKit.ATLAS, camera, lens, now, world);
            var space = new AtlasMesh(SPACE_ATLAS, camera, lens, now, world);
            var wind = new AtlasMesh(WIND_ATLAS, camera, lens, now, world);
            var stone = new AtlasMesh(StoneFx.ATLAS, camera, lens, now, world);
            var nature = new AtlasMesh(NatureInk.ATLAS, camera, lens, now, world);
            InkFrame frame = new InkFrame(fire, ink, space, wind, stone, nature, now, delta, world, embers);
            for (Entity e : world.getEntities()) {
                if (e.isRemoved()) continue;
                if (e instanceof FirstWizardEntity copy && copy.isAlive()) CopyFx.draw(frame, copy);
                else if (e instanceof FirstWizardEffectEntity effect) {
                    InkDrawer drawer = InkDrawers.get(effect.kind());
                    if (drawer != null) drawer.draw(frame, effect);
                }
            }
            context.worldState().setData(MESHES, new Meshes(fire, ink, space, wind, stone, nature));
        });
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(context -> {
            Meshes meshes = context.worldState().getDataOrDefault(MESHES, NONE);
            if (meshes.fire() == null) return;
            var consumers = context.consumers();
            var entry = context.matrices().peek();
            if (meshes.fire().hasFire()) {
                meshes.fire().emitFire(consumers.getBuffer(FIRE), entry);
                flush(consumers, FIRE);
            }
            for (AtlasMesh mesh : new AtlasMesh[]{meshes.ink(), meshes.space(), meshes.wind(), meshes.stone(), meshes.nature()}) if (mesh.hasSolid()) {
                mesh.emitSolid(consumers.getBuffer(mesh.solidLayer), entry);
                flush(consumers, mesh.solidLayer);
            }
            if (meshes.fire().hasSmoke()) {
                meshes.fire().emitSmoke(consumers.getBuffer(FIRE_SMOKE), entry);
                flush(consumers, FIRE_SMOKE);
            }
            for (AtlasMesh mesh : new AtlasMesh[]{meshes.nature(), meshes.stone(), meshes.wind(), meshes.space(), meshes.ink()}) if (mesh.hasSee()) {
                mesh.emitSee(consumers.getBuffer(mesh.seeLayer), entry);
                flush(consumers, mesh.seeLayer);
            }
        });
    }

    private static void flush(VertexConsumerProvider consumers, RenderLayer layer) {
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw(layer);
    }
}
