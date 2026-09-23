package com.anton.elementalwands.util;

import com.anton.elementalwands.item.AbstractWandItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Positions only decorative cast particles; projectile origins stay authoritative. */
public final class SpellCastVisuals {
    private SpellCastVisuals() {}

    // Approximate held wand tip relative to the camera: forward, toward the casting hand, and down.
    // It sits in the lower hand-side corner of a first-person view and near the raised hand in third person.
    private static final double TIP_FORWARD = 0.75;
    private static final double TIP_SIDE = 0.38;
    private static final double TIP_DROP = 0.32;
    /** Cast flashes bloom just beyond the tip rather than on it. */
    private static final double BURST_REACH = 0.45;
    /** Distance along the true path over which a wake leaves the wand and joins that path. */
    private static final double WAKE_BLEND = 3.0;

    public static Vec3d wandTip(LivingEntity caster) {
        Vec3d eye = caster.getEyePos();
        return clearOfCover(caster, eye, eye.add(tipOffset(caster)));
    }

    public static Vec3d burstOrigin(LivingEntity caster) {
        Vec3d eye = caster.getEyePos();
        Vec3d reach = caster.getRotationVec(1.0f).normalize().multiply(BURST_REACH);
        return clearOfCover(caster, eye, eye.add(tipOffset(caster)).add(reach));
    }

    private static Vec3d tipOffset(LivingEntity caster) {
        Vec3d look = caster.getRotationVec(1.0f).normalize();
        float yaw = caster.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        // Horizontal right of the view; stays defined when looking straight up or down.
        Vec3d right = new Vec3d(-MathHelper.cos(yaw), 0.0, -MathHelper.sin(yaw));
        Vec3d up = right.crossProduct(look);
        return look.multiply(TIP_FORWARD)
                .add(right.multiply(TIP_SIDE * handSide(caster)))
                .subtract(up.multiply(TIP_DROP));
    }

    /** +1 when the wand is in the right hand, -1 for the left, matching left-handed and off-hand holds. */
    private static double handSide(LivingEntity caster) {
        boolean mainHand = !(caster.getOffHandStack().getItem() instanceof AbstractWandItem)
                || caster.getMainHandStack().getItem() instanceof AbstractWandItem;
        Arm arm = mainHand ? caster.getMainArm() : caster.getMainArm().getOpposite();
        return arm == Arm.RIGHT ? 1.0 : -1.0;
    }

    private static Vec3d clearOfCover(LivingEntity caster, Vec3d eye, Vec3d desired) {
        var hit = caster.getEntityWorld().raycast(new RaycastContext(eye, desired,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, caster));
        if (hit.getType() == HitResult.Type.MISS) return desired;
        // Keep decorative flashes on this side of a nearby wall too.
        Vec3d delta = hit.getPos().subtract(eye);
        return eye.add(delta.normalize().multiply(Math.max(0.0, delta.length() - 0.05)));
    }

    /**
     * Bends a projectile's decorative wake out of the wand tip onto its true eye-line path.
     * Collision, damage and travel distance keep using the entity's real position.
     */
    public record Wake(Vec3d origin, Vec3d direction, Vec3d tipOffset) {
        /** For entities without a caster at launch (client copies, reloads): particles stay on the path. */
        public static final Wake NONE = new Wake(Vec3d.ZERO, Vec3d.ZERO, Vec3d.ZERO);

        public static Wake from(LivingEntity caster, Vec3d origin, Vec3d direction) {
            return new Wake(origin, direction.normalize(), wandTip(caster).subtract(origin));
        }

        public Vec3d at(Vec3d point) {
            if (this == NONE) return point;
            double t = MathHelper.clamp(point.subtract(origin).dotProduct(direction) / WAKE_BLEND, 0.0, 1.0);
            return point.add(tipOffset.multiply(1.0 - t * t * (3.0 - 2.0 * t)));
        }
    }
}
