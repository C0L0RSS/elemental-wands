package com.anton.elementalwands.client.model;

import com.anton.elementalwands.client.renderer.PyreKingRenderState;
import com.anton.elementalwands.entity.pyreking.PyreKingAim;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import java.util.ArrayList;
import java.util.Collections;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.util.RenderUtil;

/** The approved review model, installed unchanged by art/pyre_king/build_art.py --install. */
public class PyreKingModel extends GeoModel<PyreKingEntity> {
    private static final Identifier MODEL = Identifier.of("elementalwands", "geckolib/models/pyre_king.geo.json");
    public static final Identifier TEXTURE = Identifier.of("elementalwands", "textures/entity/pyre_king.png");
    private static final Identifier ANIMATION = Identifier.of("elementalwands", "geckolib/animations/pyre_king.animation.json");

    @Override public Identifier getModelResource(GeoRenderState renderState) { return MODEL; }
    @Override public Identifier getTextureResource(GeoRenderState renderState) { return TEXTURE; }
    @Override public Identifier getAnimationResource(PyreKingEntity animatable) { return ANIMATION; }

    /**
     * Aims a thrown sword. The clips throw it at one authored point; the server syncs how far the
     * real target is from that point, and {@link PyreKingAim} says how much of that the sword and
     * (for Zip and Lunge) his body have taken at this moment of the clip. The server sweeps the
     * same numbers for hits. Nothing here moves a sword that is in his hand.
     */
    @Override
    public void setCustomAnimations(AnimationState<PyreKingEntity> animationState) {
        GeoBone root = getBone("root").orElse(null), sword = getBone("sword").orElse(null);
        if (root == null || sword == null) return;
        root.updatePosition(0, 0, 0);
        if (!(animationState.renderState() instanceof PyreKingRenderState state)) return;
        // Bone units are pixels with x mirrored; his front is -z. A clip that carries him is drawn from where it began.
        root.updatePosition(-state.carryRight * 16, state.carryUp * 16, -state.carryAhead * 16);
        PyreKingAim.Throw thrown = PyreKingAim.get(state.clip);
        if (thrown == null || state.aimRight == 0 && state.aimUp == 0 && state.aimAhead == 0) return;
        var controller = animationState.manager() == null ? null : animationState.manager().getAnimationControllers().get(PyreKingEntity.CONTROLLER);
        if (controller == null || controller.getCurrentAnimation() == null
                || !controller.getCurrentAnimation().animation().name().equals("animation.pyre_king." + state.clip)) return;
        double t = controller.getCurrentAnimationSeconds();
        double[] body = thrown.bodyOffset(t, state.aimRight, state.aimAhead), blade = thrown.swordOffset(t, state.aimRight, state.aimUp, state.aimAhead);
        root.updatePosition((float) (-(body[0] + state.carryRight) * 16), state.carryUp * 16, (float) (-(body[2] + state.carryAhead) * 16));
        // The sword hangs off his hand, so its extra travel is given in the hand's own axes.
        Vector3f extra = new Vector3f((float) (blade[0] - body[0]), (float) blade[1], (float) -(blade[2] - body[2]));
        if (extra.lengthSquared() < 1e-8f) return;
        var chain = new ArrayList<GeoBone>();
        for (GeoBone bone = sword.getParent(); bone != null; bone = bone.getParent()) chain.add(bone);
        Collections.reverse(chain);
        MatrixStack stack = new MatrixStack();
        for (GeoBone bone : chain) RenderUtil.prepMatrixForBone(stack, bone);
        new Matrix3f(stack.peek().getPositionMatrix()).invert().transform(extra);
        sword.updatePosition(sword.getPosX() - extra.x * 16, sword.getPosY() + extra.y * 16, sword.getPosZ() + extra.z * 16);
    }
}
