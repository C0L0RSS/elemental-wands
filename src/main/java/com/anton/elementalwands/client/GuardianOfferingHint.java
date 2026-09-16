package com.anton.elementalwands.client;

import com.anton.elementalwands.church.GuardianChurchManager;
import com.anton.elementalwands.church.GuardianSocketBlock;
import com.anton.elementalwands.registry.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/** A local aiming hint; admission, token validation and consumption stay server-owned. */
public final class GuardianOfferingHint {
    private GuardianOfferingHint() {}
    public static Text message(MinecraftClient client) {
        if(client.player==null || client.world==null || client.currentScreen!=null || client.options.hudHidden
                || client.player.isSpectator() || !client.player.getMainHandStack().isOf(ModItems.GUARDIAN_HEART)
                || !(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType()!=HitResult.Type.BLOCK) return null;
        var socket=GuardianChurchManager.ritualSocket(client.world,hit.getBlockPos());
        if(socket==null)return null;
        var state=client.world.getBlockState(socket);
        if(!state.get(GuardianSocketBlock.PEDESTAL) || state.get(GuardianSocketBlock.RITUAL)!=0)return null;
        return Text.translatable("hint.elementalwands.guardian_heart",client.options.useKey.getBoundKeyLocalizedText());
    }
    public static void init() {
        HudRenderCallback.EVENT.register((context,counter)->{
            var client=MinecraftClient.getInstance();var text=message(client);
            if(text!=null)context.drawCenteredTextWithShadow(client.textRenderer,text,
                    context.getScaledWindowWidth()/2,context.getScaledWindowHeight()/2+25,0xFFD0EFE7);
        });
    }
}
