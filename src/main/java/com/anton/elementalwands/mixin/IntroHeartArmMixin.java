package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.GuardianIntroClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In the Guardian's intro the caller holds their main arm out level, the heart resting on it. */
@Mixin(PlayerEntityModel.class)
public abstract class IntroHeartArmMixin extends BipedEntityModel<PlayerEntityRenderState> {
    private IntroHeartArmMixin(ModelPart root) { super(root); }

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void holdHeartOut(PlayerEntityRenderState state, CallbackInfo ci) {
        float reach = GuardianIntroClient.reach(state.id);
        if (reach <= 0) return;
        ModelPart arm = state.mainArm == Arm.LEFT ? leftArm : rightArm;
        arm.pitch = MathHelper.lerp(reach, arm.pitch, com.anton.elementalwands.entity.GuardianIntro.ARM_PITCH + GuardianIntroClient.tremble());
        arm.yaw = MathHelper.lerp(reach, arm.yaw, 0);
        arm.roll = MathHelper.lerp(reach, arm.roll, 0);
    }
}
