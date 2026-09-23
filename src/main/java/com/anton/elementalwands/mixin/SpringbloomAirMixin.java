package com.anton.elementalwands.mixin;

import com.anton.elementalwands.data.EWAttachments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Preserve the server-chosen arc: held movement cannot add distance throughout a 20-block leap. */
@Mixin(LivingEntity.class)
public abstract class SpringbloomAirMixin {
    @ModifyVariable(method="travel",at=@At("HEAD"),argsOnly=true)
    private Vec3d springbloomInput(Vec3d input) {
        return (Object)this instanceof PlayerEntity p && p.getAttachedOrElse(EWAttachments.SPRINGBLOOM_FLIGHT,false)
                ? Vec3d.ZERO : input;
    }
}
