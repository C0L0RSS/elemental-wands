package com.anton.elementalwands.client.overlay;

import com.anton.elementalwands.client.ClientPlayerData;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.item.FireAbilityHandler;
import com.anton.elementalwands.item.NatureAbilityHandler;
import com.anton.elementalwands.item.SpaceAbilityHandler;
import com.anton.elementalwands.item.StoneAbilityHandler;
import com.anton.elementalwands.item.WindAbilityHandler;

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

    private static final Identifier HUD_TEXTURE = Identifier.of("elementalwands", "textures/gui/wand_hud_v2.png");
    private static final Identifier[] FIRE_ABILITY_TEXTURES = {
        Identifier.of("elementalwands", "textures/gui/ability/fire_primary.png"),
        Identifier.of("elementalwands", "textures/gui/ability/fire_secondary.png"),
        Identifier.of("elementalwands", "textures/gui/ability/fire_ultimate.png")
    };
    private static final Identifier[] WIND_ABILITY_TEXTURES = {
        Identifier.of("elementalwands", "textures/gui/ability/wind_primary.png"),
        Identifier.of("elementalwands", "textures/gui/ability/wind_secondary.png"),
        Identifier.of("elementalwands", "textures/gui/ability/wind_ultimate.png")
    };
    private static final Identifier[] STONE_ABILITY_TEXTURES = {
        Identifier.of("elementalwands", "textures/gui/ability/stone_primary.png"),
        Identifier.of("elementalwands", "textures/gui/ability/stone_secondary.png"),
        Identifier.of("elementalwands", "textures/gui/ability/stone_ultimate.png")
    };
    private static final Identifier[] NATURE_ABILITY_TEXTURES = {
        Identifier.of("elementalwands", "textures/gui/ability/nature_primary.png"),
        Identifier.of("elementalwands", "textures/gui/ability/nature_secondary.png"),
        Identifier.of("elementalwands", "textures/gui/ability/nature_ultimate.png")
    };
    private static final Identifier[] SPACE_ABILITY_TEXTURES = {
        Identifier.of("elementalwands", "textures/gui/ability/space_primary.png"),
        Identifier.of("elementalwands", "textures/gui/ability/space_secondary.png"),
        Identifier.of("elementalwands", "textures/gui/ability/space_ultimate.png")
    };

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
            case NATURE -> NatureAbilityHandler.getPrimaryCooldownTicks();
            default    -> AbstractWandItem.DEFAULT_PRIMARY_COOLDOWN_TICKS;
        };
    }

    private static int secondaryCooldownFor(WizardAffinity affinity) {
        return switch (affinity) {
            case FIRE  -> FireAbilityHandler.getSecondaryCooldownTicks();
            case SPACE -> SpaceAbilityHandler.getSecondaryCooldownTicks();
            case NATURE -> NatureAbilityHandler.getSecondaryCooldownTicks();
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

        if (ClientPlayerData.getEntangleStacks(client.player.getId()) > 0) {
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
            case NATURE -> drawNatureCooldown(context, slotIndex, renderX, renderY, now, animation);
            case WIND  -> drawWindCooldown(context, slotIndex, renderX, renderY, now, animation);
            case STONE -> drawStoneCooldown(context, slotIndex, renderX, renderY, now, animation);
            case SPACE -> drawSpaceCooldown(context, slotIndex, renderX, renderY, now, animation);
            case MANA  -> { /* fractured — intentionally blank */ }
            case ARCANE -> drawArcaneCooldown(context, slotIndex, renderX, renderY, now, animation);
        }
    }

    private void drawFireCooldown(DrawContext context, int slotIndex, int renderX, int renderY,
            long now, AnimationProfile animation) {
        Identifier glyph = FIRE_ABILITY_TEXTURES[Math.max(0, Math.min(slotIndex,
                FIRE_ABILITY_TEXTURES.length - 1))];
        context.drawTexture(RenderPipelines.GUI_TEXTURED, glyph,
                renderX + 4, renderY + 4, 0.0f, 0.0f,
                28, 28, 32, 32, 32, 32);

        // A restrained two-pixel pulse keeps a ready Fire slot alive without
        // obscuring its spell-specific glyph.
        int pulse = (int) ((now * (0.55f + animation.speed)) % 24L);
        int emberX = renderX + 6 + pulse;
        context.fill(emberX, renderY + 30, emberX + 2, renderY + 32,
                scaledAlpha(0xCCFF9A32, animation.alpha));
    }

    private void drawNatureCooldown(DrawContext context, int slotIndex, int renderX, int renderY,
            long now, AnimationProfile animation) {
        Identifier glyph = NATURE_ABILITY_TEXTURES[Math.max(0, Math.min(slotIndex,
                NATURE_ABILITY_TEXTURES.length - 1))];
        context.drawTexture(RenderPipelines.GUI_TEXTURED, glyph,
                renderX + 4, renderY + 4, 0.0f, 0.0f,
                28, 28, 32, 32, 32, 32);

        int pollenCount = Math.max(2, Math.round(4 * animation.density));
        for (int i = 0; i < pollenCount; i++) {
            int px = renderX + 8 + (int) ((now * animation.speed + slotIndex * 9L + i * 11L) % 18L);
            int py = renderY + 9 + (int) (((now * (0.4f + animation.speed * 0.7f)) + i * 5L) % 16L);
            context.fill(px, py, px + 1, py + 1, scaledAlpha(0xCCDDBD57, animation.alpha));
        }
    }

    private void drawWindCooldown(DrawContext context, int slotIndex, int renderX, int renderY,
            long now, AnimationProfile animation) {
        Identifier glyph = WIND_ABILITY_TEXTURES[Math.max(0, Math.min(slotIndex,
                WIND_ABILITY_TEXTURES.length - 1))];
        context.drawTexture(RenderPipelines.GUI_TEXTURED, glyph,
                renderX + 4, renderY + 4, 0.0f, 0.0f,
                28, 28, 32, 32, 32, 32);

        // A single pearl-white streamline supplies motion without turning the
        // icon cyan or covering the charge pips used by Waylay Dash.
        int streamX = renderX + 5
                + (int) ((now * (0.7f + animation.speed)) % 23L);
        context.fill(streamX, renderY + 29, streamX + 3, renderY + 30,
                scaledAlpha(0xB8F4F2EC, animation.alpha));
    }

    private void drawStoneCooldown(DrawContext context, int slotIndex, int renderX, int renderY,
            long now, AnimationProfile animation) {
        Identifier glyph = STONE_ABILITY_TEXTURES[Math.max(0, Math.min(slotIndex,
                STONE_ABILITY_TEXTURES.length - 1))];
        context.drawTexture(RenderPipelines.GUI_TEXTURED, glyph,
                renderX + 4, renderY + 4, 0.0f, 0.0f,
                28, 28, 32, 32, 32, 32);

        int dustCount = Math.max(2, Math.round(3 * animation.density));
        for (int i = 0; i < dustCount; i++) {
            int px = renderX + 9 + (int) ((now * (0.4f + animation.speed * 0.8f) + slotIndex * 5L + i * 9L) % 16L);
            int py = renderY + 23 + (i % 2);
            context.fill(px, py, px + 1, py + 1, scaledAlpha(0xB0B69E83, animation.alpha));
        }
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
        Identifier glyph = SPACE_ABILITY_TEXTURES[Math.max(0, Math.min(slotIndex,
                SPACE_ABILITY_TEXTURES.length - 1))];
        context.drawTexture(RenderPipelines.GUI_TEXTURED, glyph,
                renderX + 4, renderY + 4, 0.0f, 0.0f,
                28, 28, 32, 32, 32, 32);

        int centerX = renderX + (SLOT_SIZE / 2);
        int centerY = renderY + (SLOT_SIZE / 2);
        int orbitMotes = Math.max(2, Math.round(4 * animation.density));
        for (int i = 0; i < orbitMotes; i++) {
            double theta  = (now * (0.07 + animation.speed * 0.07)) + slotIndex * 0.9 + i * (Math.PI * 2.0 / orbitMotes);
            double radius = 12.0 + (i % 2) * 2.0;
            int px = centerX + (int) Math.round(Math.cos(theta) * radius);
            int py = centerY + (int) Math.round(Math.sin(theta * 1.2) * radius * 0.6);
            int color = (i % 2 == 0) ? scaledAlpha(0xCCB894FF, animation.alpha)
                                     : scaledAlpha(0x889D8DFF, animation.alpha);
            context.fill(px, py, px + 1, py + 1, color);
        }
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
            case NATURE -> WandTheme.NATURE;
            case WIND  -> WandTheme.WIND;
            case STONE -> WandTheme.STONE;
            case SPACE -> WandTheme.SPACE;
            case NONE  -> WandTheme.MANA;
        };
    }

    private int getThemeAccent(WandTheme theme) {
        return switch (theme) {
            case FIRE  -> 0xE0842C;
            case NATURE -> 0x7FD36B;
            case WIND  -> 0xEEEDE7;
            case STONE -> 0xC6B79A;
            case SPACE -> 0xB29DFF;
            case MANA  -> 0xAAAAAA;
            case ARCANE -> 0xD9D2AF;
        };
    }

    private int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private enum WandTheme { FIRE, NATURE, WIND, STONE, SPACE, MANA, ARCANE }

    private record AnimationProfile(float alpha, float speed, float density) {}
}
