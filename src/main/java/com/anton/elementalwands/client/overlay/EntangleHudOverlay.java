package com.anton.elementalwands.client.overlay;

import com.anton.elementalwands.client.ClientPlayerData;
import com.anton.elementalwands.client.ClientPlayerData.EntangleState;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.util.EntangleTracker;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Renders the affected player's vine vignette and a Nature caster's stack buds. */
public final class EntangleHudOverlay implements HudRenderCallback {

    private static final Identifier VIGNETTE_SPRITE =
            Identifier.of("elementalwands", "hud/entangle_vignette");
    private static final int HOTBAR_CLEAR_HALF_WIDTH = 112;

    private float displayedLocalStacks;

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            displayedLocalStacks = 0.0f;
            return;
        }
        if (client.getDebugHud().shouldShowDebugHud()) return;

        renderVineVignette(context, tickCounter, client);
        renderTargetBuds(context, tickCounter, client);
    }

    private void renderVineVignette(DrawContext context, RenderTickCounter tickCounter,
            MinecraftClient client) {
        int targetStacks = ClientPlayerData.getEntangleStacks(client.player.getId());
        float smoothing = Math.min(1.0f, tickCounter.getDynamicDeltaTicks() * 0.18f);
        displayedLocalStacks = MathHelper.lerp(smoothing, displayedLocalStacks, targetStacks);
        if (Math.abs(displayedLocalStacks - targetStacks) < 0.015f) {
            displayedLocalStacks = targetStacks;
        }
        if (displayedLocalStacks <= 0.025f || client.player.isSpectator()) return;

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float intensity = MathHelper.clamp(displayedLocalStacks / EntangleTracker.MAX_STACKS, 0.0f, 1.0f);
        float visibleFraction = 0.18f + intensity * 0.82f;
        int depthX = Math.max(3, Math.round(width * 0.155f * visibleFraction));
        int depthY = Math.max(3, Math.round(height * 0.175f * visibleFraction));

        float pulse = getRecentStackPulse(client, ClientPlayerData.getEntangleState(client.player.getId()),
                tickCounter);
        float opacity = MathHelper.clamp(0.38f + intensity * 0.38f + pulse * 0.12f, 0.0f, 0.88f);
        drawVignetteEdges(context, width, height, depthX, depthY, opacity);
    }

    private void drawVignetteEdges(DrawContext context, int width, int height,
            int depthX, int depthY, float opacity) {
        context.enableScissor(0, 0, width, depthY);
        drawFullVignette(context, width, height, opacity);
        context.disableScissor();

        int center = width / 2;
        context.enableScissor(0, height - depthY,
                Math.max(0, center - HOTBAR_CLEAR_HALF_WIDTH), height);
        drawFullVignette(context, width, height, opacity);
        context.disableScissor();

        context.enableScissor(Math.min(width, center + HOTBAR_CLEAR_HALF_WIDTH), height - depthY,
                width, height);
        drawFullVignette(context, width, height, opacity);
        context.disableScissor();

        int verticalBottom = Math.max(depthY, height - depthY);
        context.enableScissor(0, depthY, depthX, verticalBottom);
        drawFullVignette(context, width, height, opacity);
        context.disableScissor();

        context.enableScissor(width - depthX, depthY, width, verticalBottom);
        drawFullVignette(context, width, height, opacity);
        context.disableScissor();
    }

    private void drawFullVignette(DrawContext context, int width, int height, float opacity) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, VIGNETTE_SPRITE,
                0, 0, width, height, opacity);
    }

    private void renderTargetBuds(DrawContext context, RenderTickCounter tickCounter,
            MinecraftClient client) {
        if (!(client.player.getMainHandStack().getItem() instanceof AbstractWandItem)
                || ClientPlayerData.getAffinity() != WizardAffinity.NATURE) {
            return;
        }

        Entity targeted = client.targetedEntity;
        if (!(targeted instanceof LivingEntity living) || !living.isAlive()) return;

        EntangleState state = ClientPlayerData.getEntangleState(targeted.getId());
        if (state == null || state.stacks() <= 0) return;

        int budWidth = 6;
        int gap = 2;
        int totalWidth = EntangleTracker.MAX_STACKS * budWidth
                + (EntangleTracker.MAX_STACKS - 1) * gap;
        int startX = (context.getScaledWindowWidth() - totalWidth) / 2;
        int y = context.getScaledWindowHeight() / 2 - 18;
        float pulse = getRecentStackPulse(client, state, tickCounter);

        for (int i = 0; i < EntangleTracker.MAX_STACKS; i++) {
            boolean filled = i < state.stacks();
            boolean newest = filled && i == state.stacks() - 1;
            boolean maxPulse = state.stacks() >= EntangleTracker.MAX_STACKS;
            drawBud(context, startX + i * (budWidth + gap), y,
                    filled, pulse * ((newest || maxPulse) ? 1.0f : 0.0f));
        }
    }

    private void drawBud(DrawContext context, int x, int y, boolean filled, float pulse) {
        int stem = filled ? 0xFF79552E : 0x88413425;
        int darkLeaf = filled ? 0xFF3E7134 : 0x88404E3B;
        int lightLeaf = filled ? 0xFF71A94C : 0x88606B58;

        if (pulse > 0.0f) {
            int boost = MathHelper.clamp(Math.round(pulse * 70), 0, 70);
            darkLeaf = brighten(darkLeaf, boost);
            lightLeaf = brighten(lightLeaf, boost);
        }

        context.fill(x + 2, y + 3, x + 4, y + 8, stem);
        context.fill(x, y + 2, x + 3, y + 5, darkLeaf);
        context.fill(x + 3, y + 1, x + 6, y + 4, lightLeaf);
        context.fill(x + 1, y + 1, x + 2, y + 2, darkLeaf);
        context.fill(x + 4, y, x + 5, y + 1, lightLeaf);
    }

    private float getRecentStackPulse(MinecraftClient client, EntangleState state,
            RenderTickCounter tickCounter) {
        if (state == null) return 0.0f;
        double now = client.world.getTime() + tickCounter.getTickProgress(false);
        double age = now - state.changedAtTick();
        if (age < 0.0 || age >= 8.0) return 0.0f;
        return (float) (Math.sin(age / 8.0 * Math.PI) * (1.0 - age / 8.0));
    }

    private int brighten(int color, int amount) {
        int alpha = color >>> 24;
        int red = Math.min(255, ((color >>> 16) & 0xFF) + amount);
        int green = Math.min(255, ((color >>> 8) & 0xFF) + amount);
        int blue = Math.min(255, (color & 0xFF) + amount / 2);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
