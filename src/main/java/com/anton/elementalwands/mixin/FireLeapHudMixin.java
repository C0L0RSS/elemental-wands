package com.anton.elementalwands.mixin;

import com.anton.elementalwands.entity.FireLeapEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The temporary spell carrier cannot be voluntarily dismounted. Keep ordinary mount hints. */
@Mixin(InGameHud.class)
public abstract class FireLeapHudMixin {
    @Inject(method="setOverlayMessage",at=@At("HEAD"),cancellable=true)
    private void omitLeapDismountHint(Text message,boolean tinted,CallbackInfo ci) {
        var player=MinecraftClient.getInstance().player;
        if(player!=null && player.getVehicle() instanceof FireLeapEntity
                && message.getContent() instanceof TranslatableTextContent text && text.getKey().equals("mount.onboard")) ci.cancel();
    }
}
