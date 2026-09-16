package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

/** A short vanilla-textured burn cue; never changes the entity's fire ticks or collision size. */
final class GuardianBurnVisual {
    // Vanilla multiplies width by 1.4 and each flame quad is 1.4 times that tall.
    // These dimensions yield one 1.54-wide, 2.16-high flame instead of a full-body stack.
    static final float WIDTH = 1.1f;
    static final float HEIGHT = .65f;

    static void capture(FracturedGuardianRenderState state) {
        state.burning = state.onFire;
        state.onFire = false; // Suppress only this Guardian's automatic full-size overlay.
    }

    static void submit(FracturedGuardianRenderState state, MatrixStack matrices,
                       OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (!state.burning || state.arenaHidden || state.invisible || state.invisibleToPlayer || state.deathTime > 0) return;
        // Deferred rendering must not mutate the Guardian's real render dimensions.
        EntityRenderState flame = new EntityRenderState();
        flame.width = WIDTH;
        flame.height = HEIGHT;
        queue.submitFire(matrices, flame,
                MathHelper.rotateAround(MathHelper.Y_AXIS, camera.orientation, new Quaternionf()));
    }

    private GuardianBurnVisual() {}
}
