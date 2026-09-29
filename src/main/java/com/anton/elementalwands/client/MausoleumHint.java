package com.anton.elementalwands.client;

import com.anton.elementalwands.registry.ModBlocks;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Looking at the graveyard mausoleum's doorway from up to 12 blocks away says how to start the
 * fight. A local hint only; walking into the veil is handled on the server.
 */
public final class MausoleumHint {
    private static final double REACH = 12;

    private MausoleumHint() {}

    /** The hint's two lines, or null when the player isn't looking at the doorway. */
    public static Text[] message(MinecraftClient client) {
        if (client.player == null || client.world == null || client.currentScreen != null || client.options.hudHidden
                || client.player.isSpectator() || client.getCameraEntity() == null) return null;
        if (!(client.getCameraEntity().raycast(REACH, 1, false) instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK)
            return null;
        var state = client.world.getBlockState(hit.getBlockPos());
        if (!state.isOf(ModBlocks.MAUSOLEUM_VEIL) && !state.isOf(ModBlocks.MAUSOLEUM_ARCH)) return null;
        return new Text[]{Text.translatable("hint.elementalwands.mausoleum"), Text.translatable("hint.elementalwands.mausoleum.party")};
    }

    public static void init() {
        HudRenderCallback.EVENT.register((context, counter) -> {
            var client = MinecraftClient.getInstance();
            var lines = message(client);
            if (lines == null) return;
            int x = context.getScaledWindowWidth() / 2, y = context.getScaledWindowHeight() / 2 + 25;
            context.drawCenteredTextWithShadow(client.textRenderer, lines[0], x, y, 0xFFD0EFE7);
            context.drawCenteredTextWithShadow(client.textRenderer, lines[1], x, y + 11, 0xFF8FB3AE);
        });
    }
}
