package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.FireLeapClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The local caster flies the committed arc in place of ordinary walking, swimming or creative flight. */
@Mixin(PlayerEntity.class)
public abstract class FireLeapMotionMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void elementalwands$leap(Vec3d input, CallbackInfo ci) {
        if (FireLeapClient.step((PlayerEntity) (Object) this)) ci.cancel();
    }
}
