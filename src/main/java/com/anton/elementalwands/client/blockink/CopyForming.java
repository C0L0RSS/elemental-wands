package com.anton.elementalwands.client.blockink;

import com.anton.elementalwands.client.FirstWizardIntroClient;
import com.anton.elementalwands.client.renderer.FirstWizardRenderer;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;

/**
 * How the copy looks while the intro pours it into shape (end/EndIntro): built from the ground up out of the ink that
 * falls on it, boots first. Each part of the player model grows out of what has already formed: the legs up from the
 * boots, the body up from the hips, the off arm and then the wand arm down from the shoulders, the head up from the
 * neck; the wand appears last, as the last strand reaches the hand. Its head stays bowed until the scene raises it.
 * The skin is the owner's from the first boot (the profile is set before it forms); the ink round it is
 * {@link EndSkyFx}'s. The shadow and the motes round the copy wait until it is whole.
 *
 * <p>FirstWizardRenderer makes two calls here: {@link #capture} from its render-state update and {@link #shape} from
 * its model's pose. Outside an intro both do nothing.</p>
 */
public final class CopyForming {
    /** How far it has formed (0 to 1) and raised its head (0 bowed, 1 up). */
    private record Look(float formed, float raised) {}
    private static final RenderStateDataKey<Look> LOOK = RenderStateDataKey.create(() -> "First Wizard forming");
    private static final Look WHOLE = new Look(1, 1);
    /** How far its head is bowed before it raises it, radians. */
    private static final float BOWED = .95f;

    private CopyForming() {}

    /** How far this copy has formed: the scene's clock where an intro plays on this client, the server's word otherwise. */
    public static float formed(FirstWizardEntity copy, float delta) {
        double scene = FirstWizardIntroClient.formed(copy.getId(), delta);
        return scene >= 0 ? (float) scene : copy.formed();
    }

    /** From the renderer's render-state update: what the model and its features need this frame. */
    public static void capture(FirstWizardEntity copy, FirstWizardRenderer.State state, float delta) {
        float formed = formed(copy, delta);
        double raised = FirstWizardIntroClient.raised(copy.getId(), delta);
        Look look = formed >= 1 && (raised < 0 || raised >= 1) ? WHOLE : new Look(formed, raised < 0 ? 1 : (float) raised);
        state.appearance.setData(LOOK, look);
        if (look == WHOLE) return;
        if (formed < 1) {
            // No shadow until it has legs: the outer state's flag only stops the shadow (the body is drawn from its own state).
            if (formed < .25f) state.invisible = true;
        }
        // The wand comes with the last strand.
        if (formed < .97f) { state.appearance.rightHandItemState.clear(); state.appearance.leftHandItemState.clear(); }
        // Ask for the skin now, so the first boot already wears it.
        if (formed <= 0) MinecraftClient.getInstance().getPlayerSkinCache().getFuture(copy.profile());
    }

    /** From the model's pose, after the player pose is set: grows each part out of what has formed. */
    public static void shape(PlayerEntityModel model, PlayerEntityRenderState state) {
        Look look = state.getDataOrDefault(LOOK, WHOLE);
        if (look == WHOLE) return;
        float f = look.formed();
        grow(model.leftLeg, part(f, 0, .3f), 12, true);
        grow(model.rightLeg, part(f, 0, .3f), 12, true);
        grow(model.body, part(f, .3f, .55f), 12, true);
        boolean leftHanded = state.mainArm == Arm.LEFT;
        grow(leftHanded ? model.rightArm : model.leftArm, part(f, .55f, .68f), 12, false);
        grow(leftHanded ? model.leftArm : model.rightArm, part(f, .62f, .78f), 12, false);
        grow(model.head, part(f, .75f, .95f), 8, false);
        // Bowed while it forms, then raised to meet the players.
        model.head.pitch = model.head.pitch + BOWED * (1 - look.raised());
        // Arms hang still while they form.
        if (f < .8f) { model.leftArm.pitch *= .3f; model.rightArm.pitch *= .3f; }
    }

    private static float part(float f, float from, float to) {
        float k = Math.clamp((f - from) / (to - from), 0f, 1f);
        return k * k * (3 - 2 * k);
    }

    /**
     * Scales a part along its length. Upward grows it from its far end (legs from the boot, the body from the hips);
     * otherwise it grows from its pivot (arms down from the shoulder, the head up from the neck).
     */
    private static void grow(ModelPart part, float k, float length, boolean upward) {
        // Scaled away rather than hidden: the model's pose (scale included) is reset every frame, its visibility is not.
        if (k <= .001f) { part.xScale = part.yScale = part.zScale = 0; return; }
        if (k >= 1) return;
        part.yScale *= k;
        part.xScale *= .55f + .45f * k;
        part.zScale *= .55f + .45f * k;
        if (upward) part.originY += length * (1 - k);
    }
}
