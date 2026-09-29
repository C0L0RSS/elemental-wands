package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.AstralOrbEntity;
import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Small faceted purple pearl; uses a vanilla mineral texture and a luminous inner core. */
public final class AstralOrbRenderer extends EntityRenderer<AstralOrbEntity,EntityRenderState> {
    private static final Identifier TEXTURE=Identifier.ofVanilla("textures/block/amethyst_block.png");
    private final ModelPart orb;
    public AstralOrbRenderer(EntityRendererFactory.Context context){
        super(context);var data=new ModelData();var root=data.getRoot();
        root.addChild("orb",ModelPartBuilder.create().uv(0,0).cuboid(-2,-2,-1.5f,4,4,3)
                .uv(0,0).cuboid(-1.5f,-1.5f,-2,3,3,4),ModelTransform.NONE);
        orb=TexturedModelData.of(data,16,16).createModel();
    }
    @Override public EntityRenderState createRenderState(){return new EntityRenderState();}
    @Override public void render(EntityRenderState s,MatrixStack m,OrderedRenderCommandQueue q,CameraRenderState camera){
        m.push();m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(s.age*11));m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(30));
        q.submitModelPart(orb,m,RenderLayer.getEntityCutoutNoCull(TEXTURE),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,null);
        m.pop();super.render(s,m,q,camera);
    }
}
