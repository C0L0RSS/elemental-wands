package com.anton.elementalwands.client.overlay;

import com.anton.elementalwands.client.ClientPlayerData;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.item.IceAbilityHandler;
import com.anton.elementalwands.item.SpaceAbilityHandler;
import com.anton.elementalwands.item.StoneAbilityHandler;
import com.anton.elementalwands.item.WindAbilityHandler;
import com.anton.elementalwands.util.ChillTracker;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class WandHudOverlay implements HudRenderCallback {

    private static final Identifier HUD_TEXTURE = Identifier.of("elementalwands", "textures/gui/wand_hud.png");

    private static final float HUD_SCALE = 0.56f;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int SLOT_SIZE = 36;
    private static final int SLOT_SPACING = 42;
    private static final int SLOT_Y_OFFSET_FROM_HOTBAR_TOP = 50;
    private static final int SLOT_U_STEP = 85;
    private static final int SLOT_READY_V = 0;
    private static final int SLOT_COOLDOWN_V = 80;

    private static final String NBT_LAST_PRIMARY   = "ew_last_primary";
    private static final String NBT_LAST_SECONDARY = "ew_last_secondary";
    private static final String NBT_LAST_GLOBAL    = "ew_last_global";

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (client.getDebugHud().shouldShowDebugHud()) return;

        ItemStack stack = client.player.getMainHandStack();
        if (!(stack.getItem() instanceof AbstractWandItem wand)) return;

        int width  = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        int centerX     = width  / 2;
        int hotbarTopY  = height - HOTBAR_HEIGHT;

        int scaledCenterX   = Math.round(centerX    / HUD_SCALE);
        int scaledHotbarTopY = Math.round(hotbarTopY / HUD_SCALE);

        int slotCenterY = scaledHotbarTopY - SLOT_Y_OFFSET_FROM_HOTBAR_TOP;
        int[] slotCentersX = {
            scaledCenterX - SLOT_SPACING,
            scaledCenterX,
            scaledCenterX + SLOT_SPACING
        };

        WizardAffinity affinity = ClientPlayerData.getAffinity();
        WandTheme theme      = resolveTheme(affinity);
        int      accentColor = getThemeAccent(theme);

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(HUD_SCALE, HUD_SCALE);

        boolean isFractured = affinity == WizardAffinity.NONE;

        if (isFractured) {
            renderAbility(context, client, stack, wand, AbstractWandItem.Ability.PRIMARY, 0,
                scaledCenterX, slotCenterY, primaryCooldownFor(affinity), theme, accentColor, true, affinity);
        } else {
            // Use ClientPlayerData for padlock logic (synced from server)
            boolean secondaryUnlocked = ClientPlayerData.isSecondaryUnlocked();
            boolean ultimateUnlocked  = ClientPlayerData.isUltimateUnlocked();

            renderAbility(context, client, stack, wand, AbstractWandItem.Ability.PRIMARY, 0,
                slotCentersX[0], slotCenterY, primaryCooldownFor(affinity), theme, accentColor, true, affinity);
            renderAbility(context, client, stack, wand, AbstractWandItem.Ability.SECONDARY, 1,
                slotCentersX[1], slotCenterY, secondaryCooldownFor(affinity), theme, accentColor, secondaryUnlocked, affinity);
            renderUltimateSlot(context, client, stack, wand, slotCentersX[2], slotCenterY, theme, accentColor, ultimateUnlocked);
        }

        context.getMatrices().popMatrix();
    }

    private static int primaryCooldownFor(WizardAffinity affinity) {
        return switch (affinity) {
            case STONE -> StoneAbilityHandler.getPrimaryCooldownTicks();
            case SPACE -> SpaceAbilityHandler.getPrimaryCooldownTicks();
            case ICE   -> IceAbilityHandler.getPrimaryCooldownTicks();
            default    -> AbstractWandItem.DEFAULT_PRIMARY_COOLDOWN_TICKS;
        };
    }

    private static int secondaryCooldownFor(WizardAffinity affinity) {
        return switch (affinity) {
            case SPACE -> SpaceAbilityHandler.getSecondaryCooldownTicks();
            case ICE   -> IceAbilityHandler.getSecondaryCooldownTicks();
            default    -> AbstractWandItem.DEFAULT_SECONDARY_COOLDOWN_TICKS;
        };
    }

    // -----------------------------------------------------------------------
    // Ultimate charge slot (special rendering)
    // -----------------------------------------------------------------------

    private void renderUltimateSlot(DrawContext context, MinecraftClient client, ItemStack stack,
            AbstractWandItem wand, int x, int y, WandTheme theme, int accentColor, boolean isUnlocked) {
        long now        = client.world.getTime();
        int  charge     = AbstractWandItem.getUltimateCharge(stack);
        boolean isReady = charge >= 100;

        int frameSize = SLOT_SIZE;
        int renderX   = x - (frameSize / 2);
        int renderY   = y - (frameSize / 2);

        // Frame texture — use ready variant when charged
        float v = isReady ? SLOT_READY_V : SLOT_COOLDOWN_V;
        float u = (float) (2 * SLOT_U_STEP); // slot index 2
        context.drawTexture(RenderPipelines.GUI_TEXTURED, HUD_TEXTURE, renderX, renderY,
            u, v, frameSize, frameSize, 256, 256);

        // Background fill
        if (!isUnlocked) {
            context.fill(renderX + 4, renderY + 4, renderX + frameSize - 4, renderY + frameSize - 4, 0xD0000000);
        } else if (charge == 0) {
            // Grayscale empty
            context.fill(renderX + 4, renderY + 4, renderX + frameSize - 4, renderY + frameSize - 4,
                withAlpha(0x646464, 0x64));
        } else {
            context.fill(renderX + 4, renderY + 4, renderX + frameSize - 4, renderY + frameSize - 4, 0x8A000000);
        }

        // Draw theme motif
        AnimationProfile anim = isReady
            ? new AnimationProfile(1.0f + 0.3f * MathHelper.sin((float)(now * 0.25)), 1.5f, 1.0f)
            : new AnimationProfile(charge / 100.0f * 0.8f + 0.2f, 0.5f + charge / 100.0f, 0.5f + charge / 100.0f * 0.5f);
        drawThemeCooldownMotif(context, theme, 2, renderX, renderY, now, anim);

        // Charge fill bar (bottom-to-top vertical fill)
        if (isUnlocked && charge > 0 && charge < 100) {
            int barInset = 5;
            int barX = renderX + barInset;
            int barW = frameSize - barInset * 2;
            int barMaxH = frameSize - barInset * 2;
            int barH = Math.round(barMaxH * (charge / 100.0f));
            int barBottom = renderY + frameSize - barInset;
            int barTop    = barBottom - barH;
            context.fill(barX, barTop, barX + barW, barBottom, withAlpha(accentColor, 0x70));
        }

        // Charge percentage label
        if (isUnlocked && !isReady && charge > 0) {
            String label = charge + "%";
            int tw = client.textRenderer.getWidth(label);
            int bubbleY = renderY - 8;
            context.fill(x - tw / 2 - 2, bubbleY - 1, x + tw / 2 + 2, bubbleY + 9, 0xB0000000);
            context.drawText(client.textRenderer, label, x - tw / 2, bubbleY, 0xFFFFFFFF, true);
        }

        // Pulsing glow ring when ready
        if (isUnlocked && isReady) {
            float pulse = 0.5f + 0.5f * MathHelper.sin((float)(now * 0.4));
            int glowAlpha = (int)(pulse * 0x80);
            context.fill(renderX + 2, renderY + 2, renderX + frameSize - 2, renderY + frameSize - 2,
                withAlpha(accentColor, glowAlpha));
        }

        // Padlock overlay if locked
        if (!isUnlocked) {
            drawPadlock(context, x, y, frameSize, renderX, renderY);
        }
    }

    // -----------------------------------------------------------------------
    // Standard ability slot
    // -----------------------------------------------------------------------

    private void renderAbility(DrawContext context, MinecraftClient client, ItemStack stack, AbstractWandItem wand,
            AbstractWandItem.Ability ability, int slotIndex, int x, int y, int maxCooldownTicks,
            WandTheme theme, int accentColor, boolean isUnlocked, WizardAffinity affinity) {
        long now = client.world.getTime();

        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        String key = switch (ability) {
            case PRIMARY   -> NBT_LAST_PRIMARY;
            case SECONDARY -> NBT_LAST_SECONDARY;
            default        -> NBT_LAST_GLOBAL;
        };

        long last    = nbt.getLong(key).orElse(-1_000_000_000L);
        long elapsed = now - last;

        if (ChillTracker.getStacks(client.player) > 0) {
            elapsed /= 2;
        }

        long remaining  = maxCooldownTicks - elapsed;
        boolean onCooldown = remaining > 0;

        boolean isWindSecondary  = affinity == WizardAffinity.WIND && ability == AbstractWandItem.Ability.SECONDARY;
        int windCharges          = 0;
        int windMaxCharges       = 0;
        int windRechargeTicks    = 0;
        int windRechargeDuration = 0;
        boolean windPartial      = false;

        if (isWindSecondary) {
            windMaxCharges    = WindAbilityHandler.getDashMaxCharges();
            windCharges       = WindAbilityHandler.getDashCharges(stack);
            windRechargeTicks = WindAbilityHandler.getDashRechargeTicks(stack);
            windRechargeDuration = WindAbilityHandler.getDashRechargeDurationTicks();
            onCooldown        = windCharges <= 0;
            windPartial       = windCharges > 0 && windCharges < windMaxCharges;
        }

        int frameSize = SLOT_SIZE;
        int renderX   = x - (frameSize / 2);
        int renderY   = y - (frameSize / 2);

        float v = onCooldown ? SLOT_COOLDOWN_V : SLOT_READY_V;
        float u = (float) (slotIndex * SLOT_U_STEP);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, HUD_TEXTURE, renderX, renderY,
            u, v, frameSize, frameSize, 256, 256);

        AnimationProfile animation = isWindSecondary
            ? animationProfileForWindChargeState(windCharges, windMaxCharges)
            : animationProfileForState(onCooldown);

        if (onCooldown) {
            context.fill(renderX + 4, renderY + 4, renderX + frameSize - 4, renderY + frameSize - 4, 0x8A000000);
        } else if (windPartial) {
            context.fill(renderX + 5, renderY + 5, renderX + frameSize - 5, renderY + frameSize - 5,
                withAlpha(accentColor, 0x48));
        } else {
            context.fill(renderX + 5, renderY + 5, renderX + frameSize - 5, renderY + frameSize - 5,
                withAlpha(accentColor, 0x32));
        }

        drawThemeCooldownMotif(context, theme, slotIndex, renderX, renderY, now, animation);

        if (isWindSecondary) {
            drawWindDashPips(context, renderX, renderY, windCharges, windMaxCharges,
                windRechargeTicks, windRechargeDuration, now);
        } else if (onCooldown && remaining > 20 && isUnlocked) {
            String digit  = String.valueOf((int) Math.ceil(remaining / 20.0));
            int txtWidth  = client.textRenderer.getWidth(digit);
            int bubbleY   = renderY - 8;
            context.fill(x - (txtWidth / 2) - 2, bubbleY - 1, x + (txtWidth / 2) + 2, bubbleY + 9, 0xB0000000);
            context.drawText(client.textRenderer, digit, x - (txtWidth / 2), bubbleY, 0xFFFFFFFF, true);
        }

        if (!isUnlocked) {
            context.fill(renderX + 4, renderY + 4, renderX + frameSize - 4, renderY + frameSize - 4, 0xD0000000);
            drawPadlock(context, x, y, frameSize, renderX, renderY);
        }
    }

    // -----------------------------------------------------------------------
    // Padlock helper
    // -----------------------------------------------------------------------

    private void drawPadlock(DrawContext context, int x, int y, int frameSize, int renderX, int renderY) {
        int padX = renderX + frameSize / 2;
        int padY = renderY + frameSize / 2;
        context.fill(padX - 4, padY,     padX + 4, padY + 6, 0xFF666666); // body
        context.fill(padX - 3, padY - 4, padX + 3, padY - 3, 0xFFAAAAAA); // shackle top
        context.fill(padX - 3, padY - 3, padX - 2, padY,     0xFFAAAAAA); // shackle left
        context.fill(padX + 2, padY - 3, padX + 3, padY,     0xFFAAAAAA); // shackle right
        context.fill(padX - 1, padY + 2, padX + 1, padY + 4, 0xFF000000); // keyhole
    }

    // -----------------------------------------------------------------------
    // Wind dash pips
    // -----------------------------------------------------------------------

    private void drawWindDashPips(DrawContext context, int renderX, int renderY,
            int charges, int maxCharges, int rechargeTicks, int rechargeDuration, long now) {
        int pipSize   = 3;
        int pipGap    = 2;
        int totalWidth = (maxCharges * pipSize) + ((maxCharges - 1) * pipGap);
        int startX    = renderX + ((SLOT_SIZE - totalWidth) / 2);
        int pipY      = renderY + SLOT_SIZE - 8;

        for (int i = 0; i < maxCharges; i++) {
            int pipX         = startX + (i * (pipSize + pipGap));
            boolean isFilled = i < charges;
            boolean isRecharging = !isFilled && charges < maxCharges && i == charges;

            int borderColor = 0xCC1B242A;
            int fillColor   = isFilled ? 0xFFD1F6FF : 0x7A41505A;

            if (isRecharging) {
                float pulse        = 0.5f + 0.5f * (float) Math.sin((now + i * 6L) * 0.45f);
                float refillProg   = rechargeDuration <= 0 ? 0.0f
                    : clamp01(rechargeTicks / (float) rechargeDuration);
                float alphaScale   = 0.35f + (pulse * 0.45f) + (refillProg * 0.2f);
                fillColor          = scaledAlpha(0xD8BDE9FF, alphaScale);
            }

            context.fill(pipX - 1, pipY - 1, pipX + pipSize + 1, pipY + pipSize + 1, borderColor);
            context.fill(pipX,     pipY,      pipX + pipSize,     pipY + pipSize,     fillColor);
        }
    }

    // -----------------------------------------------------------------------
    // Theme motifs
    // -----------------------------------------------------------------------

    private void drawThemeCooldownMotif(DrawContext context, WandTheme theme, int slotIndex,
            int renderX, int renderY, long now, AnimationProfile animation) {
        switch (theme) {
            case FIRE  -> drawFireCooldown(context, slotIndex, renderX, renderY, now, animation);
            case ICE   -> drawIceCooldown(context, slotIndex, renderX, renderY, now, animation);
            case WIND  -> drawWindCooldown(context, slotIndex, renderX, renderY, now, animation);
            case STONE -> drawStoneCooldown(context, slotIndex, renderX, renderY, now, animation);
            case SPACE -> drawSpaceCooldown(context, slotIndex, renderX, renderY, now, animation);
            case MANA  -> { /* fractured — intentionally blank */ }
            case ARCANE -> drawArcaneCooldown(context, slotIndex, renderX, renderY, now, animation);
        }
    }

    private void drawFireCooldown(DrawContext context, int slotIndex, int renderX, int renderY,
            long now, AnimationProfile animation) {
        context.fill(renderX + 7, renderY + 26, renderX + 29, renderY + 28,
            scaledAlpha(0x66461908, animation.alpha));
        int emberCount = Math.max(2, Math.round(6 * animation.density));
        for (int i = 0; i < emberCount; i++) {
            int rise = (int) ((now * (1.2f + animation.speed * 2.0f) + slotIndex * 11L + i * 7L) % 18L);
            int px   = renderX + 8 + (int) ((now * animation.speed + i * 13L + slotIndex * 5L) % 18L);
            int py   = renderY + 26 - rise;
            int color = (i % 2 == 0) ? scaledAlpha(0xCCFF9A32, animation.alpha)
                                     : scaledAlpha(0xCCFF5A1A, animation.alpha);
            context.fill(px, py, px + 2, py + 2, color);
        }
        int ringX = renderX + 2 + (int) ((now * (0.65f + animation.speed)) % 28L);
        context.fill(ringX, renderY + 1, ringX + 2, renderY + 3, scaledAlpha(0xAAFF7A29, animation.alpha));
    }

    private void drawIceCooldown(DrawContext context, int slotIndex, int renderX, int renderY,
            long now, AnimationProfile animation) {
        int cx = renderX + 18; int cy = renderY + 18;
        int frost = scaledAlpha(0xAACCF6FF, animation.alpha);
        context.fill(cx - 1, cy - 6, cx + 1, cy + 7, frost);
        context.fill(cx - 6, cy - 1, cx + 7, cy + 1, frost);
        context.fill(cx - 4, cy - 4, cx - 3, cy - 3, frost);
        context.fill(cx + 3, cy + 3, cx + 4, cy + 4, frost);
        context.fill(cx - 4, cy + 3, cx - 3, cy + 4, frost);
        context.fill(cx + 3, cy - 4, cx + 4, cy - 3, frost);
        int flakeCount = Math.max(2, Math.round(5 * animation.density));
        for (int i = 0; i < flakeCount; i++) {
            int px = renderX + 8 + (int) ((now * animation.speed + slotIndex * 9L + i * 11L) % 18L);
            int py = renderY + 9 + (int) (((now * (0.4f + animation.speed * 0.7f)) + i * 5L) % 16L);
            context.fill(px, py, px + 1, py + 1, scaledAlpha(0xCCEAFCFF, animation.alpha));
        }
        int edge = renderY + 2 + (int) ((now * (0.45f + animation.speed * 0.8f)) % 30L);
        context.fill(renderX + 1, edge, renderX + 3, edge + 1, scaledAlpha(0x99D6F5FF, animation.alpha));
    }

    private void drawWindCooldown(DrawContext context, int slotIndex, int renderX, int renderY,
            long now, AnimationProfile animation) {
        int lineCount = Math.max(2, Math.round(4 * animation.density));
        for (int i = 0; i < lineCount; i++) {
            int lineY = renderY + 10 + (int) ((now * (0.65f + animation.speed) + slotIndex * 7L + i * 4L) % 14L);
            int color = (i % 2 == 0) ? scaledAlpha(0x77BFE9FF, animation.alpha)
                                     : scaledAlpha(0x44D8F5FF, animation.alpha);
            context.fill(renderX + 8, lineY, renderX + 28, lineY + 1, color);
        }
        int gustX = renderX + 8 + (int) ((now * (1.0f + animation.speed * 2.0f) + slotIndex * 13L) % 18L);
        context.fill(gustX,     renderY + 13, gustX + 2,     renderY + 15, scaledAlpha(0xAAC9F6FF, animation.alpha));
        context.fill(gustX - 3, renderY + 20, gustX - 1,     renderY + 22, scaledAlpha(0x88C9F6FF, animation.alpha));
        context.fill(renderX + 1,  renderY + 4,  renderX + 3,  renderY + 7,  scaledAlpha(0x66CFF0FF, animation.alpha));
        context.fill(renderX + 33, renderY + 27, renderX + 35, renderY + 30, scaledAlpha(0x66CFF0FF, animation.alpha));
    }

    private void drawStoneCooldown(DrawContext context, int slotIndex, int renderX, int renderY,
            long now, AnimationProfile animation) {
        context.fill(renderX + 10, renderY + 10, renderX + 11, renderY + 26, scaledAlpha(0xAA7E7568, animation.alpha));
        context.fill(renderX + 20, renderY + 9,  renderX + 21, renderY + 24, scaledAlpha(0xAA6D6459, animation.alpha));
        context.fill(renderX + 11, renderY + 15, renderX + 20, renderY + 16, scaledAlpha(0x886F675C, animation.alpha));
        context.fill(renderX + 16, renderY + 16, renderX + 17, renderY + 24, scaledAlpha(0x886F675C, animation.alpha));
        int dustCount = Math.max(2, Math.round(4 * animation.density));
        for (int i = 0; i < dustCount; i++) {
            int px = renderX + 9 + (int) ((now * (0.4f + animation.speed * 0.8f) + slotIndex * 5L + i * 9L) % 16L);
            int py = renderY + 23 + (i % 2);
            context.fill(px, py, px + 1, py + 1, scaledAlpha(0xB0AAA08F, animation.alpha));
        }
        context.fill(renderX + 2, renderY + 3, renderX + 4, renderY + 5, scaledAlpha(0x7F988E7C, animation.alpha));
    }

    private void drawArcaneCooldown(DrawContext context, int slotIndex, int renderX, int renderY,
            long now, AnimationProfile animation) {
        int motes = Math.max(2, Math.round(5 * animation.density));
        for (int i = 0; i < motes; i++) {
            int px = renderX + 8 + (int) ((now * (0.7f + animation.speed) + slotIndex * 5L + i * 7L) % 18L);
            int py = renderY + 8 + (int) (((now * (0.45f + animation.speed * 0.7f)) + i * 11L) % 18L);
            context.fill(px, py, px + 2, py + 2, scaledAlpha(0x99E4DBB3, animation.alpha));
        }
        context.fill(renderX + 2,  renderY + 2,  renderX + 4,  renderY + 4,  scaledAlpha(0x88E4DBB3, animation.alpha));
        context.fill(renderX + 32, renderY + 32, renderX + 34, renderY + 34, scaledAlpha(0x88E4DBB3, animation.alpha));
    }

    private void drawSpaceCooldown(DrawContext context, int slotIndex, int renderX, int renderY,
            long now, AnimationProfile animation) {
        int centerX = renderX + (SLOT_SIZE / 2);
        int centerY = renderY + (SLOT_SIZE / 2);
        int orbitMotes = Math.max(3, Math.round(6 * animation.density));
        for (int i = 0; i < orbitMotes; i++) {
            double theta  = (now * (0.07 + animation.speed * 0.07)) + slotIndex * 0.9 + i * (Math.PI * 2.0 / orbitMotes);
            double radius = 5.0 + (i % 3) * 2.5;
            int px = centerX + (int) Math.round(Math.cos(theta) * radius);
            int py = centerY + (int) Math.round(Math.sin(theta * 1.2) * radius * 0.6);
            int color = (i % 2 == 0) ? scaledAlpha(0xCCB894FF, animation.alpha)
                                     : scaledAlpha(0x889D8DFF, animation.alpha);
            context.fill(px, py, px + 2, py + 2, color);
        }
        int ringOffset = (int) ((now * (0.9f + animation.speed)) % 10L);
        context.fill(centerX - 5 + ringOffset, centerY - 6, centerX - 4 + ringOffset, centerY + 7,
            scaledAlpha(0x66B89DFF, animation.alpha));
        context.fill(centerX - 6, centerY - 1 + ringOffset, centerX + 7, centerY + ringOffset,
            scaledAlpha(0x55CAB5FF, animation.alpha));
        context.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, scaledAlpha(0xE0E2DDFF, animation.alpha));
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private AnimationProfile animationProfileForState(boolean onCooldown) {
        return onCooldown ? new AnimationProfile(1.0f, 1.0f, 1.0f)
                         : new AnimationProfile(0.55f, 0.55f, 0.6f);
    }

    private AnimationProfile animationProfileForWindChargeState(int charges, int maxCharges) {
        if (charges <= 0)         return animationProfileForState(true);
        if (charges < maxCharges) return new AnimationProfile(0.78f, 0.78f, 0.8f);
        return animationProfileForState(false);
    }

    private int scaledAlpha(int color, float alphaScale) {
        int alpha  = (color >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, Math.round(alpha * alphaScale)));
        return (scaled << 24) | (color & 0x00FFFFFF);
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private WandTheme resolveTheme(WizardAffinity affinity) {
        return switch (affinity) {
            case FIRE  -> WandTheme.FIRE;
            case ICE   -> WandTheme.ICE;
            case WIND  -> WandTheme.WIND;
            case STONE -> WandTheme.STONE;
            case SPACE -> WandTheme.SPACE;
            case NONE  -> WandTheme.MANA;
        };
    }

    private int getThemeAccent(WandTheme theme) {
        return switch (theme) {
            case FIRE  -> 0xE0842C;
            case ICE   -> 0x8EDCF8;
            case WIND  -> 0xCFEBAE;
            case STONE -> 0xC6B79A;
            case SPACE -> 0xB29DFF;
            case MANA  -> 0xAAAAAA;
            case ARCANE -> 0xD9D2AF;
        };
    }

    private int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private enum WandTheme { FIRE, ICE, WIND, STONE, SPACE, MANA, ARCANE }

    private record AnimationProfile(float alpha, float speed, float density) {}
}
