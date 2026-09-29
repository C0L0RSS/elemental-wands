package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.BossIntroCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A boss intro flies the camera; the player's own body is then drawn like any other. */
@Mixin(Camera.class)
public abstract class IntroCameraMixin {
    @Shadow private boolean thirdPerson;
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void introShot(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        BossIntroCamera.Pose pose = BossIntroCamera.pose(tickProgress);
        if (pose == null) return;
        setRotation(pose.yaw(), pose.pitch());
        setPos(pose.pos());
        this.thirdPerson = true;
    }
}
