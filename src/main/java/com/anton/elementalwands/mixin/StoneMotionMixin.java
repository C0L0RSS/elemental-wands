package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.StoneMotionAccess;
import com.anton.elementalwands.util.StoneTechniqueRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep vanilla gravity, jumping and collision; replace only powered horizontal input. */
@Mixin(LivingEntity.class)
public abstract class StoneMotionMixin implements StoneMotionAccess {
    @Unique private float elementalwands$yaw, elementalwands$speed;
    @Unique private int elementalwands$mode;
    @Unique private long elementalwands$until, elementalwands$interruptUntil, elementalwands$immuneUntil;
    @Override public void elementalwands$stoneMotion(float yaw, float speed, int mode) {
        LivingEntity self = (LivingEntity)(Object)this;
        long now = self.getEntityWorld().getTime();
        if (mode == 3) {
            if (now < elementalwands$immuneUntil) return;
            elementalwands$interruptUntil = now + StoneTechniqueRules.INTERRUPT;
            elementalwands$immuneUntil = now + StoneTechniqueRules.INTERRUPT_GRACE;
            elementalwands$mode = 0;
        } else {
            elementalwands$yaw = yaw; elementalwands$speed = speed; elementalwands$mode = mode;
            elementalwands$until = now + 8; // Fail closed if state updates stop.
        }
    }
    @Override public boolean elementalwands$stoneInterrupted() {
        return ((LivingEntity)(Object)this).getEntityWorld().getTime() < elementalwands$interruptUntil;
    }
    @Unique private boolean elementalwands$powered() {
        return elementalwands$mode != 0 && ((LivingEntity)(Object)this).getEntityWorld().getTime() < elementalwands$until;
    }
    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d elementalwands$drive(Vec3d input) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (elementalwands$stoneInterrupted()) {
            self.setVelocity(0, self.getVelocity().y, 0);
            return Vec3d.ZERO;
        }
        if (!elementalwands$powered()) return input;
        double yaw = Math.toRadians(elementalwands$yaw);
        self.setVelocity(-Math.sin(yaw) * elementalwands$speed, self.getVelocity().y,
                Math.cos(yaw) * elementalwands$speed);
        return Vec3d.ZERO;
    }
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void elementalwands$interruptJump(CallbackInfo ci) {
        if (elementalwands$stoneInterrupted()) ci.cancel();
    }
    @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
    private void elementalwands$heavy(double strength, double x, double z, CallbackInfo ci) {
        // Full-speed charge is immune to ordinary knockback. Explicit interrupts bypass this.
        if (elementalwands$powered() && elementalwands$mode == 1 && elementalwands$speed >= .80f) ci.cancel();
    }
    @ModifyVariable(method = "takeKnockback", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double elementalwands$resist(double strength) {
        return elementalwands$powered() && elementalwands$mode == 1 ? strength * .15 : strength;
    }
}
