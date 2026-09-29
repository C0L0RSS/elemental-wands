package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.necromancer.NecromancerIntro;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * The intro's zombie. While its soul is torn out it hangs lifted, arched back with its face to the
 * sky, shaking; the scene spawns it on its first frame, so its age is the scene's clock.
 */
public final class IntroZombieRenderer extends ZombieEntityRenderer {
    private static final float LEAN = 28, LOOK_UP = 70;

    public IntroZombieRenderer(EntityRendererFactory.Context context) { super(context); }

    private static float torn(float age) {
        if (age >= NecromancerIntro.CRUMBLE) return 0;
        return (float)NecromancerIntro.smooth((age - NecromancerIntro.ARCH) / 10.0);
    }

    @Override
    public void updateRenderState(ZombieEntity entity, ZombieEntityRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        float torn = torn(state.age);
        state.pitch = MathHelper.lerp(torn, state.pitch, -LOOK_UP);
    }

    @Override
    protected void setupTransforms(ZombieEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseHeight) {
        super.setupTransforms(state, matrices, bodyYaw, baseHeight);
        float torn = torn(state.age);
        if (torn <= 0) return;
        // A fast tremble that grows as the pull gets stronger.
        float shake = torn * (.8f + (state.age - NecromancerIntro.ARCH) / 30f);
        matrices.translate(Math.sin(state.age * 2.9) * .015 * shake, 0, Math.cos(state.age * 3.7) * .015 * shake);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-LEAN * torn + (float)Math.sin(state.age * 3.1) * 1.5f * shake));
    }
}
