package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.FlashoverEmberEntity;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** A faceted ember held inside three charred fragments; no inventory item or world block. */
public final class FlashoverEmberRenderer extends EntityRenderer<FlashoverEmberEntity,FlashoverEmberRenderer.State> {
    public static final class State extends EntityRenderState { boolean armed,settled,primed;float time;net.minecraft.util.math.Vec3d offset=net.minecraft.util.math.Vec3d.ZERO; }
    public FlashoverEmberRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(FlashoverEmberEntity entity,State state,float delta) {
        super.updateRenderState(entity,state,delta);state.armed=entity.armed();state.settled=entity.settled();state.time=entity.age+delta;state.primed=entity.primed();state.offset=entity.attachedId()<0?net.minecraft.util.math.Vec3d.ZERO:entity.attachedPosition(delta).subtract(entity.getLerpedPos(delta));
    }
    @Override public void render(State state,MatrixStack matrices,OrderedRenderCommandQueue queue,CameraRenderState camera) {
        if(state.invisible)return;
        matrices.push();matrices.translate(state.offset.x,state.offset.y,state.offset.z);
        float t=state.time,glow=state.primed ? (.7f+.3f*(float)Math.sin(state.time*1.5)) : state.armed?1f:.45f;
        for(int i=0;i<3;i++) {
            double a=i*Math.PI*2/3+t*.025;
            matrices.push();matrices.translate(Math.cos(a)*.115,.09+Math.sin(t*.08+i)*.015,Math.sin(a)*.115);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)a));matrices.multiply(RotationAxis.POSITIVE_Z.rotation(.4f));
            matrices.scale(.095f,.14f,.085f);matrices.translate(-.5,-.5,-.5);
            queue.submitBlock(matrices,Blocks.BLACKSTONE.getDefaultState(),state.light,OverlayTexture.DEFAULT_UV,0);matrices.pop();
        }
        matrices.push();matrices.translate(0,.12,0);matrices.multiply(RotationAxis.POSITIVE_Y.rotation(t*.06f));
        queue.submitCustom(matrices,RenderLayer.getLightning(),(entry,out) -> {
            float pulse=.09f+(float)Math.sin(t*.12)*.008f;
            for(int i=0;i<4;i++) {
                double a=i*Math.PI/2,b=(i+1)*Math.PI/2;
                float x=(float)Math.cos(a)*pulse,z=(float)Math.sin(a)*pulse,xx=(float)Math.cos(b)*pulse,zz=(float)Math.sin(b)*pulse;
                for(int side:new int[]{-1,1}) {
                    int color=((int)(220*glow)<<24)|0xFF8A24;
                    out.vertex(entry,0,side*pulse*1.5f,0).color(color);
                    out.vertex(entry,side<0?xx:x,0,side<0?zz:z).color(color);
                    out.vertex(entry,side<0?x:xx,0,side<0?z:zz).color(color);
                    out.vertex(entry,side<0?x:xx,0,side<0?z:zz).color(color);
                }
            }
        });matrices.pop();matrices.pop();super.render(state,matrices,queue,camera);
    }
}
