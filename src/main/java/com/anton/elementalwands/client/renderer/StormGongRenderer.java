package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.client.model.StormGongModel;
import com.anton.elementalwands.summit.StormGongBlockEntity;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Draws the aerie's gong from its anchor block. The model is the layout's own geometry facing
 * south, where players strike it, so it turns from south to the anchor's facing; GeckoLib's own
 * turn assumes a north-facing model and would hang the mallet behind the plate. The gong reaches
 * five blocks above and two to each side of its anchor and marks a peak from afar, so it is drawn
 * outside its block's box and from further than a block entity's usual 64 blocks.
 */
public final class StormGongRenderer<R extends BlockEntityRenderState & GeoRenderState> extends GeoBlockRenderer<StormGongBlockEntity, R> {
    public StormGongRenderer() { super(new StormGongModel()); }

    @Override
    protected void rotateBlock(Direction facing, MatrixStack matrices) {
        float yaw = switch (facing) {
            case NORTH -> 180;
            case EAST -> 90;
            case WEST -> -90;
            default -> 0;
        };
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
    }

    @Override public boolean rendersOutsideBoundingBox() { return true; }

    @Override public int getRenderDistance() { return 160; }
}
