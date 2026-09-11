package com.anton.elementalwands.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Positions only the decorative cast burst; projectile origins stay authoritative. */
public final class SpellCastVisuals {
    private SpellCastVisuals() {}

    public static Vec3d burstOrigin(LivingEntity caster) {
        Vec3d eye = caster.getEyePos();
        Vec3d desired = eye.add(caster.getRotationVec(1.0f).normalize().multiply(1.6))
                .add(0.0, -0.3, 0.0);
        var hit = caster.getEntityWorld().raycast(new RaycastContext(eye, desired,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
        if (hit.getType() == HitResult.Type.MISS) return desired;
        // Keep decorative flashes on this side of a nearby wall too.
        Vec3d delta = hit.getPos().subtract(eye);
        return eye.add(delta.normalize().multiply(Math.max(0.0, delta.length() - 0.05)));
    }
}
