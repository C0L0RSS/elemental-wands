package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.StormRocIntroClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Each player sees only their own Storm Roc intro: players, and the fight's Roc until the scene's has handed over, are not drawn. */
@Mixin(EntityRenderManager.class)
public class StormRocIntroHideMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void elementalwands$introHides(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (StormRocIntroClient.hides(entity)) cir.setReturnValue(false);
    }
}
