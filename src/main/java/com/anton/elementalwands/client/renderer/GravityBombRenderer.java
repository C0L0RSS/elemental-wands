package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.GravityBombEntity;
import com.anton.elementalwands.util.GravityWellManager;
import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Faceted dark core, tilted broken orbits, and inward spirals: no cross-shaped glints. */
public final class GravityBombRenderer extends EntityRenderer<GravityBombEntity, GravityBombRenderer.State> {
    private static final Identifier CORE = Identifier.ofVanilla("textures/block/crying_obsidian.png");
    private static final Identifier GLOW = Identifier.ofVanilla("textures/block/white_concrete.png");
    public static final class State extends EntityRenderState { public boolean well, collapsing, burst; public float wellAge, collapseAge, burstAge; public double flowTime, collapseTime; public net.minecraft.util.math.Vec3d flight; }
    private final ModelPart core, shellPlate;
    public GravityBombRenderer(EntityRendererFactory.Context context) {
        super(context);
        var data = new ModelData();
        data.getRoot().addChild("core", ModelPartBuilder.create().uv(0,0).cuboid(-2,-2,-2,4,4,4), ModelTransform.NONE);
        core = TexturedModelData.of(data,16,16).createModel();
        var shellData=new ModelData();
        shellData.getRoot().addChild("plate",ModelPartBuilder.create().uv(0,0).cuboid(-1,-1,-.16f,2,2,.32f),ModelTransform.NONE);
        shellPlate=TexturedModelData.of(shellData,16,16).createModel();
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(GravityBombEntity e, State s, float delta) {
        super.updateRenderState(e,s,delta); s.well = e.isWell(); s.wellAge = e.wellAge() + delta;
        s.collapsing=e.isCollapsing();s.burst=e.isBurst();
        s.collapseAge=e.collapseAge()+delta;s.burstAge=e.burstAge()+delta;
        s.flight=e.getVelocity().normalize();
        s.flowTime=(double)e.getEntityWorld().getTime()+delta;s.collapseTime=e.collapseStart();
    }
    @Override public void render(State s, MatrixStack m, OrderedRenderCommandQueue q, CameraRenderState camera) {
        m.push();
        if(!s.well) {
            // Condense near the camera so the eye-height spawn cannot fill the view.
            double dx=s.x-camera.pos.x,dy=s.y-camera.pos.y,dz=s.z-camera.pos.z;
            float nearScale=(float)Math.min(1,Math.sqrt(dx*dx+dy*dy+dz*dz)/1.5);
            m.scale(nearScale,nearScale,nearScale);
        }
        if(s.well)m.translate(0,.3,0);
        if(!s.well) {
            // A compact six-plate shell breathes around a luminous energy seed.
            m.push();
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(s.age*16));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(25+s.age*9));
            float pulse=(float)Math.pow(.5+.5*Math.sin(s.age*1.05),3);
            for(int face=0;face<6;face++) {
                m.push();
                if(face<4)m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face*90));
                else m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(face==4?90:-90));
                m.translate(0,0,.067+.009*pulse);
                q.submitModelPart(shellPlate,m,RenderLayer.getEntityCutoutNoCull(CORE),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,null);
                m.pop();
            }
            q.submitCustom(m,RenderLayer.getEntityTranslucent(GLOW),(entry,out)->
                    cube(out,entry,0,0,0,.056f+.01f*pulse,((170+(int)(85*pulse))<<24)|0x8A32D6));
            m.pop();
        } else if(!s.burst) {
            m.push();
            float growth=(float)Math.min(1,s.wellAge/10.0);
            float size=(.7f+1.5f*growth)+.15f*(float)Math.sin(s.age*.3);
            if(s.collapsing)size*=1+.45f*Math.min(1,s.collapseAge/GravityBombEntity.COLLAPSE_TICKS);
            m.scale(size,size,size);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(s.age*4));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(35));
            q.submitModelPart(core,m,RenderLayer.getEntityCutoutNoCull(CORE),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,null);
            m.pop();
            // The six casing plates snap open and arc away as the field unfurls.
            if(s.wellAge<10)for(int face=0;face<6;face++) {
                double u=s.wellAge/10.0,a=face*Math.PI/3;
                m.push();m.translate(Math.cos(a)*(.08+1.8*u),.06+.7*Math.sin(Math.PI*u),Math.sin(a)*(.08+1.8*u));
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(face*60+s.wellAge*19));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(35+s.wellAge*23));
                float shardScale=(float)((1+3*Math.sin(Math.PI*u))*(1-u));m.scale(shardScale,shardScale,shardScale);
                q.submitModelPart(shellPlate,m,RenderLayer.getEntityCutoutNoCull(CORE),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,null);
                m.pop();
            }
        }
        var right=new org.joml.Vector3f(1,0,0).rotate(camera.orientation);
        var up=new org.joml.Vector3f(0,1,0).rotate(camera.orientation);
        q.submitCustom(m,RenderLayer.getEntityTranslucent(GLOW),(entry,out)->{
            if(!s.well) {
                // Tiny satellites and a tapering wake communicate contained energy in flight.
                for(int i=0;i<3;i++) {
                    double a=s.age*.27+i*Math.PI*2/3;
                    cube(out,entry,(float)Math.cos(a)*.125f,(float)Math.sin(a*1.7)*.07f,(float)Math.sin(a)*.125f,.012f,0xCF6426A0);
                }
                for(int i=0;i<10;i++) {
                    float behind=.1f+i*.06f,wobble=.018f*(float)Math.sin(s.age*.6-i*.9);
                    mote(out,entry,right,up,(float)-s.flight.x*behind+wobble,(float)-s.flight.y*behind,
                            (float)-s.flight.z*behind-wobble,.022f*(1-i/12f),((150-i*13)<<24)|0x7A2BBB);
                }
            }
            if(s.well&&!s.burst)for(int orbit=0;orbit<3;orbit++) {
                double radius=(.65+orbit*.25)*Math.min(1,s.wellAge/10.0);
                for(int i=0;i<32;i++) {
                    if((i+orbit*3)%10>6)continue;
                    double a=i*Math.PI*2/32+s.age*.035*(orbit%2==0?1:-1);
                    ribbon(out,entry,a,a+.15,radius,radius,.025,orbit*.9,.25,0xCC702AC2);
                }
            }
            if(s.well) {
                double opening=Math.min(1,s.wellAge/10.0);
                double grow=1-Math.pow(1-opening,3);
                if(!s.burst&&s.wellAge<10) {
                    for(int i=0;i<64;i++) {
                        double a=i*2.39996323,r=(.2+2.5*opening)*( .7+.3*fract(i*.56984));
                        float y=(float)(.05+Math.sin(Math.PI*opening)*(.2+fract(i*.7548)*.55));
                        mote(out,entry,right,up,(float)(Math.cos(a)*r),y,(float)(Math.sin(a)*r),
                                .025f+(i%3)*.009f,((int)(220*(1-opening))<<24)|(i%7==0?0xB86CF0:0x7025B2));
                    }
                }
                double squeeze=s.collapsing ? Math.min(1,s.collapseAge/GravityBombEntity.COLLAPSE_TICKS) : 0;
                if(!s.burst)for(int i=0;i<96;i++)if(i%12<11) {
                    double a=i*Math.PI*2/96;
                    ribbon(out,entry,a,a+.061,GravityWellManager.RADIUS*grow,GravityWellManager.RADIUS*grow,.055,0,-.25,0xCC8139CC);
                }
                if(!s.burst) {
                    double time=s.collapsing ? s.collapseTime : s.flowTime;
                    double contraction=1-squeeze*squeeze;
                    // Shallow dark center with a plum falloff; the terrain stays intact.
                    for(int ring=0;ring<5;ring++)for(int i=0;i<64;i++) {
                        double a=i*Math.PI*2/64,r=(.13+ring*.23)*grow;
                        int alpha=235-ring*23;
                        ribbon(out,entry,a,a+.099,r,r,.14,0,-.25+ring*.004,
                                (alpha<<24)|(ring<2?0x10071E:0x28103E));
                    }
                    // Four layered curved bands form an original pixel-built vortex.
                    for(int arm=0;arm<4;arm++)for(int step=0;step<56;step++) {
                        double phase=step/56.0,next=(step+1)/56.0;
                        double r=(.5+3.35*(1-phase))*grow*contraction;
                        double R=(.5+3.35*(1-next))*grow*contraction;
                        double a=arm*Math.PI/2+phase*3.7+time*.035+squeeze*.65;
                        double b=arm*Math.PI/2+next*3.7+time*.035+squeeze*.65;
                        double width=(.07+.12*(1-phase))*Math.max(.1,contraction);
                        double y=-.2+.13*(1-phase);
                        ribbon(out,entry,a,b,r,R,width*2.0,0,y,0x99401569);
                        ribbon(out,entry,a,b,r,R,width,0,y+.009,step%7<4?0xDA6323A4:0xCB4C187E);
                        if(step%9<6)ribbon(out,entry,a,b,r+width*.45,R+width*.45,width*.24,0,y+.016,0xC28D3DDD);
                    }
                }
                // Stable identities carry a dense, continuous stream all the way to the
                // core. On collapse the SAME motes accelerate inward, then explode out.
                for(int i=0;i<480;i++) {
                    double time=s.collapsing ? s.collapseTime : s.flowTime;
                    double phase=fract((i/4)*.61803398875+time*.022);
                    double baseAngle=(i%4)*Math.PI/2+phase*3.7+time*.035+(fract(i*.56984029)-.5)*.16;
                    double radius=GravityWellManager.RADIUS*(1-phase)*grow;
                    double height=(.08+fract(i*.75487766)*.38)*radius/GravityWellManager.RADIUS;
                    double angle=baseAngle;
                    double opacity=.5+.5*Math.sin(Math.PI*phase);
                    if(s.collapsing) {
                        double contraction=1-squeeze*squeeze;
                        radius*=contraction;height*=contraction;
                        angle+=squeeze*.65;opacity=.8+.2*squeeze;
                    }
                    if(s.burst) {
                        double burst=Math.min(1,s.burstAge/GravityBombEntity.BURST_TICKS);
                        radius=(2.4+fract(i*.56984029)*1.8)*Math.pow(burst,.7);
                        angle=i*2.39996323;
                        height=(.12+fract(i*.75487766)*1.5)*burst;
                        opacity=Math.pow(1-burst,1.3);
                    }
                    float x=(float)(Math.cos(angle)*radius),z=(float)(Math.sin(angle)*radius);
                    float y=(float)height;
                    float size=.024f+(i%5)*.004f+(float)squeeze*.012f;
                    int rgb=i%11==0 ? 0xA453E4 : i%3==0 ? 0x7028AE : 0x491875;
                    int color=((int)(225*opacity)<<24)|rgb;
                    mote(out,entry,right,up,x,y,z,size,color);
                    // A small fading tail makes direction readable instead of looking like static dust.
                    double tail=s.burst ? -.2 : .12+.28*squeeze;
                    mote(out,entry,right,up,x+(float)(Math.cos(angle)*tail),y+.015f,
                            z+(float)(Math.sin(angle)*tail),size*.55f,((int)(100*opacity)<<24)|rgb);
                }
                if(s.burst) {
                    double burst=Math.min(1,s.burstAge/GravityBombEntity.BURST_TICKS);
                    for(int i=0;i<96;i++) {
                        double a=i*Math.PI*2/96;
                        ribbon(out,entry,a,a+.067,4.2*burst,4.2*burst,.07*(1-burst),0,-.15,
                                ((int)(220*(1-burst))<<24)|0x8D3DDD);
                    }
                }
            }
        });
        m.pop();super.render(s,m,q,camera);
    }
    private static void cube(VertexConsumer out,MatrixStack.Entry m,float x,float y,float z,float r,int color) {
        float[][] vertices={{x-r,y-r,z-r},{x+r,y-r,z-r},{x+r,y+r,z-r},{x-r,y+r,z-r},
                {x-r,y-r,z+r},{x+r,y-r,z+r},{x+r,y+r,z+r},{x-r,y+r,z+r}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
        for(int[] face:faces)for(int i=0;i<4;i++) {
            float[] v=vertices[face[i]];
            out.vertex(m,v[0],v[1],v[2]).color(color).texture(i<2?0:1,i%3==0?0:1)
                    .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(m,0,1,0);
        }
    }
    private static double fract(double value) { return value-Math.floor(value); }
    private static void mote(VertexConsumer out,MatrixStack.Entry m,org.joml.Vector3f right,org.joml.Vector3f up,
            float x,float y,float z,float size,int color) {
        for(int i=0;i<4;i++) {
            float horizontal=(i==0||i==3?-size:size),vertical=i<2?-size:size;
            out.vertex(m,x+right.x*horizontal+up.x*vertical,y+right.y*horizontal+up.y*vertical,z+right.z*horizontal+up.z*vertical)
                    .color(color).texture(i<2?0:1,i%3==0?0:1).overlay(OverlayTexture.DEFAULT_UV)
                    .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(m,0,1,0);
        }
    }
    private static void ribbon(VertexConsumer out,MatrixStack.Entry m,double a,double b,double r,double R,double width,double tilt,double y,int color) {
        double[][] points={{Math.cos(a)*(r-width),Math.sin(a)*Math.sin(tilt)*r+y,Math.sin(a)*(r-width)},
                {Math.cos(b)*(R-width),Math.sin(b)*Math.sin(tilt)*R+y,Math.sin(b)*(R-width)},
                {Math.cos(b)*(R+width),Math.sin(b)*Math.sin(tilt)*R+y,Math.sin(b)*(R+width)},
                {Math.cos(a)*(r+width),Math.sin(a)*Math.sin(tilt)*r+y,Math.sin(a)*(r+width)}};
        for(int i=0;i<4;i++)out.vertex(m,(float)points[i][0],(float)points[i][1],(float)points[i][2]).color(color)
                .texture(i<2?0:1,i%3==0?0:1).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(m,0,1,0);
    }
}
