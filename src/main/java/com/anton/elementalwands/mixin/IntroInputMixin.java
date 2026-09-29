package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.NecromancerIntroClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Held players stay put; the Sneak key still reaches the intro's skip prompt. */
@Mixin(KeyboardInput.class)
public abstract class IntroInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void introHold(CallbackInfo ci) {
        if (!NecromancerIntroClient.frozen()) return;
        playerInput = PlayerInput.DEFAULT;
        movementVector = Vec2f.ZERO;
    }
}
