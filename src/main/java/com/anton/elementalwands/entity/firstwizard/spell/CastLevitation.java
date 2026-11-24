package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;

/** A vertical cast stroke. The attackable body follows the rise and respects block collisions. */
final class CastLevitation {
    private final FirstWizardEntity boss;
    private final double startY;

    CastLevitation(Cast cast) {
        boss = cast.boss();
        startY = boss.getY();
    }

    void height(double blocks) {
        boss.setVelocity(boss.getVelocity().multiply(1, 0, 1));
        boss.move(MovementType.SELF, new Vec3d(0, startY + blocks - boss.getY(), 0));
    }

    static double ease(double progress) {
        double t = Math.clamp(progress, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
