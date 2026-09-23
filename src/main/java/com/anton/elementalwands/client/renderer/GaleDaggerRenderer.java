package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.GaleDaggerEntity;
import com.anton.elementalwands.util.GaleDaggers;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import java.util.*;

/** Approved stepped dagger: pearl blade, raised ridge, compact guard and wrapped handle. Local tip is -Z. */
public final class GaleDaggerRenderer extends EntityRenderer<GaleDaggerEntity,GaleDaggerRenderer.State> {
    private static final Identifier TEXTURE=Identifier.ofVanilla("textures/block/white_concrete.png");
    private record Box(float x,float y,float z,float w,float h,float d,int color){}
    private static final List<Box> MESH=mesh();
    public static final class State extends EntityRenderState {float yaw,pitch,roll;}
    public GaleDaggerRenderer(EntityRendererFactory.Context context){super(context);}
    @Override public State createRenderState(){return new State();}
    @Override public void updateRenderState(GaleDaggerEntity e,State s,float delta){
        super.updateRenderState(e,s,delta);s.yaw=e.getYaw();s.pitch=e.getPitch();s.roll=0;
        if(!e.fired()&&e.visualOwner() instanceof PlayerEntity p){
            var anchor=GaleDaggers.anchor(p,e.index()).add(p.getLerpedPos(delta).subtract(p.getEntityPos()));
            s.x=anchor.x;s.y=anchor.y;s.z=anchor.z;s.yaw=p.getYaw();s.pitch=0;s.roll=(e.index()-1)*10.3f;
        }
    }
    @Override public void render(State s,MatrixStack m,OrderedRenderCommandQueue q,CameraRenderState camera){
        if(s.invisible)return;
        m.push();m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-s.yaw));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-s.pitch));m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(s.roll));
        q.submitCustom(m,RenderLayer.getEntityCutoutNoCull(TEXTURE),(entry,out)->{
            for(var b:MESH)drawBox(b,entry,out,s.light);
        });m.pop();super.render(s,m,q,camera);
    }
    private static List<Box> mesh(){
        var out=new ArrayList<Box>();int[] colors={0xFFF8FAF7,0xFFE8EEED,0xFFD8E2E4,0xFFBCCBD0,0xFF98AAB4};
        int[] rows={0,1,1,2,2,3,3,3,3,3,3,3,2};
        for(int r=0;r<rows.length;r++)for(int c=-rows[r];c<=rows[r];c++){
            int color=c==0?colors[0]:Math.abs(c)==rows[r]?colors[1]:colors[c<0?2:3];
            out.add(new Box(c*.038f,c==0?.018f:0,(r-9)*.063f,.040f,.036f,.065f,color));
        }
        for(int c=-4;c<=4;c++)out.add(new Box(c*.04f,0,.27f,.043f,.065f,.055f,colors[2]));
        for(int r=0;r<5;r++)out.add(new Box(0,0,.325f+r*.045f,.061f,.055f,.045f,colors[r%2==1?3:4]));
        out.add(new Box(0,0,.56f,.088f,.073f,.07f,colors[1]));return List.copyOf(out);
    }
    private static void drawBox(Box b,MatrixStack.Entry m,VertexConsumer out,int light){
        float x=b.x-b.w/2,y=b.y-b.h/2,z=b.z-b.d/2,X=x+b.w,Y=y+b.h,Z=z+b.d;
        quad(out,m,light,b.color,0,1,0,new float[]{x,Y,z,x,Y,Z,X,Y,Z,X,Y,z});
        quad(out,m,light,b.color,0,-1,0,new float[]{x,y,Z,x,y,z,X,y,z,X,y,Z});
        quad(out,m,light,b.color,0,0,-1,new float[]{x,y,z,x,Y,z,X,Y,z,X,y,z});
        quad(out,m,light,b.color,0,0,1,new float[]{X,y,Z,X,Y,Z,x,Y,Z,x,y,Z});
        quad(out,m,light,b.color,-1,0,0,new float[]{x,y,Z,x,Y,Z,x,Y,z,x,y,z});
        quad(out,m,light,b.color,1,0,0,new float[]{X,y,z,X,Y,z,X,Y,Z,X,y,Z});
    }
    private static void quad(VertexConsumer out,MatrixStack.Entry m,int light,int color,float nx,float ny,float nz,float[] p){
        for(int i=0;i<4;i++)out.vertex(m,p[i*3],p[i*3+1],p[i*3+2]).color(color).texture(i<2?0:1,i%3==0?0:1)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m,nx,ny,nz);
    }
}
