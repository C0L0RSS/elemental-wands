package com.anton.elementalwands.client.renderer;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Direct port of approved workshop 05 geometry; all state is local to one render submission. */
public final class FireSpellMeshes {
    private record Face(float[][] positions, float[][] uv, float[] normal) {}
    private record Quad(Vec3d[] p, float[][] uv, int color) {}
    private static final float[][] UV={{0,1},{1,1},{1,0},{0,0}};
    private static final Vec3d UP=new Vec3d(0,1,0);
    private static final Face[] BODY=loadBody();
    private static final RenderLayer[][] LAYERS=new RenderLayer[4][4];
    private static final int SURFACE=0, PLUME=1, WALL=2, EMBER=3;
    private FireSpellMeshes() {}

    private static Face[] loadBody() {
        try(var stream=FireSpellMeshes.class.getResourceAsStream("/assets/elementalwands/fire/meteor-model.json")) {
            if(stream==null)throw new IllegalStateException("Missing approved Fire meteor mesh");
            return new Gson().fromJson(JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                    .getAsJsonObject().get("faces"),Face[].class);
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    public static int meteorFaceCount() { return BODY.length; }
    private static RenderLayer layer(int family,int frame) {
        if(LAYERS[family][frame]==null) {
            String path=switch(family) {
                case SURFACE -> "entity/fire_meteor_surface_";
                case PLUME -> "particle/fire/meteor_shell_";
                case WALL -> "particle/fire/pyre_front_";
                default -> "particle/fire/ember_";
            };
            LAYERS[family][frame]=RenderLayer.getEntityTranslucentEmissive(
                    Identifier.of("elementalwands","textures/"+path+frame+".png"));
        }
        return LAYERS[family][frame];
    }
    private static int frame(double time,int fps,int phase) { return Math.floorMod((int)Math.floor(time*fps)+phase,4); }
    private static double random(int n) { double a=Math.sin(n*127.1+43.7)*43758.5453;return a-Math.floor(a); }
    private static int color(float alpha,double tone) {
        int c=(int)Math.round(Math.clamp(tone,0,1)*255);
        return ((int)(Math.clamp(alpha,0,1)*255)<<24)|(c<<16)|(c<<8)|c;
    }
    private static void vertex(VertexConsumer out,MatrixStack.Entry entry,Vec3d p,float[] uv,int color) {
        out.vertex(entry,(float)p.x,(float)p.y,(float)p.z).color(color).texture(uv[0],uv[1])
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(entry,0,1,0);
    }
    private static final class Batch {
        final Map<Integer,List<Quad>> quads=new LinkedHashMap<>();
        final Vec3d camera; final boolean firstPerson; final double time;
        Batch(double time,Vec3d camera,boolean firstPerson) { this.time=time;this.camera=camera;this.firstPerson=firstPerson; }
        void quad(int family,int phase,Vec3d[] p,float[][] uv,float alpha) {
            Vec3d center=p[0].add(p[1]).add(p[2]).add(p[3]).multiply(.25);
            double extent=Math.max(center.distanceTo(p[0]),center.distanceTo(p[2]));
            alpha*=SpellViewClearance.opacity(firstPerson,camera.distanceTo(center),extent);
            if(alpha<=0)return;
            int key=family*4+frame(time,family==EMBER?5:8,phase);
            quads.computeIfAbsent(key,k->new ArrayList<>()).add(new Quad(p,uv,color(alpha,1)));
        }
        void sprite(Vec3d p,double size,int phase,float alpha) {
            Vec3d forward=camera.subtract(p).normalize(),right=UP.crossProduct(forward).normalize();
            if(right.lengthSquared()<1e-6)right=new Vec3d(1,0,0);
            Vec3d up=forward.crossProduct(right).normalize().multiply(size*.5);right=right.multiply(size*.5);
            quad(EMBER,phase,new Vec3d[]{p.subtract(right).subtract(up),p.add(right).subtract(up),p.add(right).add(up),p.subtract(right).add(up)},UV,alpha);
        }
        void submit(MatrixStack matrices,OrderedRenderCommandQueue queue) {
            for(var entry:quads.entrySet()) {
                int key=entry.getKey();List<Quad> faces=entry.getValue();
                faces.sort((a,b)->Double.compare(b.p[0].squaredDistanceTo(camera),a.p[0].squaredDistanceTo(camera)));
                queue.submitCustom(matrices,layer(key/4,key%4),(matrix,out)->{
                    for(Quad q:faces)for(int i=0;i<4;i++)vertex(out,matrix,q.p[i],q.uv[i],q.color);
                });
            }
        }
    }

    private static void flame(Batch batch,Vec3d root,Vec3d direction,double width,double length,int phase,int material) {
        Vec3d axis=direction.normalize(),side=axis.crossProduct(Math.abs(axis.y)>.9?new Vec3d(1,0,0):UP).normalize();
        for(int segment=0;segment<3;segment++) {
            double a=segment/3.0,b=(segment+1)/3.0;
            Vec3d pa=root.add(axis.multiply(a*length)).add(side.multiply(Math.sin(a*Math.PI)*Math.sin(batch.time*5+phase)*length*.10));
            Vec3d pb=root.add(axis.multiply(b*length)).add(side.multiply(Math.sin(b*Math.PI)*Math.sin(batch.time*5+phase)*length*.10));
            double wa=width*(1-a*.42)*.5,wb=width*(1-b*.42)*.5;
            batch.quad(material,phase,new Vec3d[]{pa.subtract(side.multiply(wa)),pa.add(side.multiply(wa)),pb.add(side.multiply(wb)),pb.subtract(side.multiply(wb))},
                    new float[][]{{0,(float)(1-a)},{1,(float)(1-a)},{1,(float)(1-b)},{0,(float)(1-b)}},1);
        }
    }
    public static void submitMeteor(MatrixStack matrices,OrderedRenderCommandQueue queue,double time,Vec3d camera,boolean firstPerson) {
        Vec3d center=new Vec3d(0,4,0);
        float opacity=SpellViewClearance.opacity(firstPerson,camera.distanceTo(center),4.3);
        if(opacity>0) {
            matrices.push();matrices.translate(0,4,0);matrices.multiply(RotationAxis.POSITIVE_Y.rotation((float)(time*.19)));
            queue.submitCustom(matrices,layer(SURFACE,frame(time,7,0)),(entry,out)->{
                for(Face face:BODY) {
                    double tone=.76+face.normal[1]*.15+face.normal[2]*.13+face.normal[0]*.06;
                    int tint=color(opacity,tone);
                    for(int i=0;i<4;i++)vertex(out,entry,new Vec3d(face.positions[i][0],face.positions[i][1],face.positions[i][2]),face.uv[i],tint);
                }
            });matrices.pop();
        }
        Batch batch=new Batch(time,camera,firstPerson);
        for(int i=0;i<46;i++) {
            double y=1-2*(i+.5)/46,a=i*2.399963+time*.24,r=Math.sqrt(1-y*y);
            Vec3d n=new Vec3d(Math.cos(a)*r,y,Math.sin(a)*r);
            flame(batch,center.add(n.multiply(4.22)),n.multiply(.72).add(0,.82,0),
                    2.4+random(i+7)*1.5,2+random(i+11)*2.2,i,i%2==0?WALL:PLUME);
        }
        for(int i=0;i<11;i++) {
            double a=i/11.0*Math.PI*2+time*.3,r=1+random(i+61)*1.4;
            Vec3d root=center.add(Math.cos(a)*r,Math.sqrt(16-r*r),Math.sin(a)*r);
            flame(batch,root,new Vec3d(Math.cos(a)*.18,1,Math.sin(a)*.18),2+random(i+83)*1.2,
                    4.2+random(i+90)*2.2,i+47,i%2==0?WALL:PLUME);
        }
        for(int i=0;i<78;i++) {
            double life=(time*.46+random(i+200))%1,a=i*2.4+time*.22,y=(random(i+201)-.5)*1.4,r=Math.sqrt(1-y*y);
            Vec3d n=new Vec3d(Math.cos(a)*r,y,Math.sin(a)*r);
            double radius=4.35+life*(2.6+random(i+208)*2);
            Vec3d p=center.add(n.multiply(radius)).add(Math.sin(life*4+i)*.2,life*3.8,Math.cos(life*4+i)*.2);
            batch.sprite(p,(.24+random(i+202)*.27)*(1-life*.45),i,(float)Math.min(1,(1-life)*2.5));
        }
        batch.submit(matrices,queue);
    }
    private static Vec3d yaw(Vec3d p,float yaw) { double a=-yaw*Math.PI/180;return new Vec3d(p.x*Math.cos(a)+p.z*Math.sin(a),p.y,-p.x*Math.sin(a)+p.z*Math.cos(a)); }
    public static void submitPyre(MatrixStack matrices,OrderedRenderCommandQueue queue,double time,float yaw,Vec3d camera,boolean firstPerson) {
        Batch batch=new Batch(time,camera,firstPerson);
        for(int i=-1;i<=1;i++) {
            double z=Math.abs(i)*.18,h=i==0?2.8:2.45;
            Vec3d[] p={new Vec3d((i-.64)*1.5,0,z),new Vec3d((i+.64)*1.5,0,z),new Vec3d((i+.64)*1.5,h,z+.24),new Vec3d((i-.64)*1.5,h,z+.24)};
            for(int j=0;j<4;j++)p[j]=yaw(p[j],yaw);
            batch.quad(WALL,i+1,p,UV,1);
            Vec3d[] cross={new Vec3d(i*1.5,0,z-.54),new Vec3d(i*1.5,0,z+.10),new Vec3d(i*1.5,h*.88,z+.10),new Vec3d(i*1.5,h*.88,z-.54)};
            for(int j=0;j<4;j++)cross[j]=yaw(cross[j],yaw);
            batch.quad(WALL,i+2,cross,UV,1);
        }
        batch.submit(matrices,queue);
    }
}
