package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.FaultlineSpikeEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/** Three chipped, stepped stone teeth; an authored code-native mesh using the approved stone texture. */
public final class FaultlineSpikeRenderer extends EntityRenderer<FaultlineSpikeEntity, FaultlineSpikeRenderer.State> {
    private static final Identifier TEXTURE=Identifier.of("elementalwands","textures/block/stone_spike.png");
    public static final class State extends EntityRenderState { float time, yaw; }
    public FaultlineSpikeRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(FaultlineSpikeEntity entity,State state,float delta) {
        super.updateRenderState(entity,state,delta);state.time=entity.elapsed(delta);state.yaw=entity.getYaw();
    }
    @Override public void render(State state,MatrixStack matrices,OrderedRenderCommandQueue queue,CameraRenderState camera) {
        if(state.invisible)return;
        float rise=Math.clamp(state.time/2,0,1),crumble=Math.clamp((state.time-3)/6,0,1);
        float height=rise*(1-crumble),angle=(float)Math.toRadians(state.yaw);int light=state.light;
        if(height<=0)return;
        queue.submitCustom(matrices,RenderLayer.getEntityCutoutNoCull(TEXTURE),(entry,out) -> {
            for(int tooth=0;tooth<3;tooth++) {
                float x=(tooth-1)*.29f,z=tooth==1?0:.16f;
                float size=tooth==1?1:.64f;
                for(int step=0;step<3;step++) {
                    float bottom=step*.34f*size,top=(step+1)*.34f*size;
                    float lower=(.26f-step*.07f)*size,upper=(.16f-step*.065f)*size;
                    Vector3f[] lo=ring(lower,bottom,x,z,height,angle,crumble,step);
                    Vector3f[] hi=ring(Math.max(.01f,upper),top,x+.04f*size,z-.04f*size,height,angle,crumble,step);
                    for(int side=0;side<4;side++)quad(out,entry,lo[side],lo[(side+1)%4],hi[(side+1)%4],hi[side],light);
                    quad(out,entry,hi[3],hi[2],hi[1],hi[0],light);
                }
            }
        });
        super.render(state,matrices,queue,camera);
    }
    private static Vector3f[] ring(float radius,float y,float x,float z,float height,float angle,float crumble,int step) {
        Vector3f[] result=new Vector3f[4];
        for(int i=0;i<4;i++)result[i]=new Vector3f(x+(i==0||i==3?-radius:radius)+crumble*(step-1)*.16f,
                y*height,z+(i<2?-radius:radius)).rotateY(angle);
        return result;
    }
    private static void quad(VertexConsumer out,MatrixStack.Entry entry,Vector3f a,Vector3f b,Vector3f c,Vector3f d,int light) {
        Vector3f normal=new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
        int shade=(int)(255*(.65+.30*Math.max(0,normal.y))),color=0xFF000000|shade<<16|shade<<8|shade;
        vertex(out,entry,a,0,1,normal,color,light);vertex(out,entry,b,1,1,normal,color,light);
        vertex(out,entry,c,1,0,normal,color,light);vertex(out,entry,d,0,0,normal,color,light);
    }
    private static void vertex(VertexConsumer out,MatrixStack.Entry entry,Vector3f point,float u,float v,Vector3f n,int color,int light) {
        out.vertex(entry,point.x,point.y,point.z).color(color).texture(u,v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry,n.x,n.y,n.z);
    }
}
