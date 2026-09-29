package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.GuardianIntroClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The caller's hands are empty while the heart rests on their outstretched arm. */
@Mixin(PlayerEntityRenderer.class)
public abstract class IntroHeartHandMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void emptyHands(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (GuardianIntroClient.reach(state.id) <= 0) return;
        state.rightHandItemState.clear();
        state.leftHandItemState.clear();
    }
}
