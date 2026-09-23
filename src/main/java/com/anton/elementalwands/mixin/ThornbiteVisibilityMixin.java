package com.anton.elementalwands.mixin;

import com.anton.elementalwands.client.renderer.ThornbiteVisual;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the held spell visible when its owner is just outside the camera frustum. */
@Mixin(EntityRenderer.class)
public abstract class ThornbiteVisibilityMixin {
    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void elementalwands$includeBite(Entity entity, CallbackInfoReturnable<Box> cir) {
        if (entity instanceof PlayerEntity player) {
            var bite = ThornbiteVisual.forHolder(player, 0);
            if (bite != null) cir.setReturnValue(cir.getReturnValue().union(new Box(bite.contact(), bite.contact()).expand(1)));
        }
    }
}
