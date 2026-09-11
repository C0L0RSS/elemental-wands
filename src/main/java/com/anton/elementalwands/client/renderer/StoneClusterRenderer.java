package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.StoneClusterEntity;
import com.anton.elementalwands.util.StoneClusterRules;
import com.google.gson.Gson;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Actual beveled rock mesh, with independent gathering motion for each fragment. */
public final class StoneClusterRenderer extends EntityRenderer<StoneClusterEntity,StoneClusterRenderer.State> {
    private record Face(float[][] positions,float[][] uv) {}
    private record Part(int threshold,float[] center,List<Face> faces) {}
    private record Mesh(List<Part> parts) {}
    private static final Identifier TEXTURE=Identifier.of("elementalwands","textures/block/stone_spike.png");
    private static final Mesh MESH=loadMesh();
    public static final class State extends EntityRenderState {
        float mass, time, gatherAge; int targetMass,beforeMass; boolean held;
        Vec3d groundDelta=Vec3d.ZERO,ownerOffset=Vec3d.ZERO;
    }
    public StoneClusterRenderer(EntityRendererFactory.Context context) { super(context); }
    private static Mesh loadMesh() {
        var resource=StoneClusterRenderer.class.getResourceAsStream("/assets/elementalwands/models/entity/stone_cluster.json");
        if(resource==null)throw new IllegalStateException("Missing Stone cluster mesh");
        try(var reader=new InputStreamReader(resource,StandardCharsets.UTF_8)) { return new Gson().fromJson(reader,Mesh.class); }
        catch(java.io.IOException e) { throw new IllegalStateException("Cannot read Stone cluster mesh",e); }
    }
    @Override public State createRenderState() { return new State(); }
    @Override public boolean shouldRender(StoneClusterEntity entity,Frustum frustum,double x,double y,double z) {
        return entity.held()?entity.squaredDistanceTo(x,y,z)<64*64:super.shouldRender(entity,frustum,x,y,z);
    }
    @Override public void updateRenderState(StoneClusterEntity entity,State state,float delta) {
        super.updateRenderState(entity,state,delta);
        state.mass=entity.held()?entity.visualMass(delta):entity.mass(); state.targetMass=entity.mass();
        state.beforeMass=entity.beforeMass(); state.held=entity.held(); state.time=entity.age+delta;
        state.gatherAge=entity.gatherAge(delta); state.ownerOffset=Vec3d.ZERO;
        Vec3d rendered=entity.getLerpedPos(delta);
        if(state.held && entity.caster()!=null) {
            var owner=entity.caster();
            Vec3d desired=owner.getLerpedPos(delta).add(0,owner.getHeight()+.35+StoneClusterRules.radius(entity.mass()),0);
            state.ownerOffset=desired.subtract(rendered); rendered=desired;
        }
        state.groundDelta=entity.ground().toCenterPos().add(0,.6,0).subtract(rendered);
    }
    @Override public void render(State s,MatrixStack matrices,OrderedRenderCommandQueue queue,CameraRenderState camera) {
        if(s.invisible)return;
        submitMesh(s,matrices,queue);
        super.render(s,matrices,queue,camera);
    }
    static void submitMesh(State s,MatrixStack matrices,OrderedRenderCommandQueue queue) {
        matrices.push(); matrices.translate(s.ownerOffset.x,s.ownerOffset.y,s.ownerOffset.z);
        float radius=(float)StoneClusterRules.radius(Math.round(s.mass));
        float rotation=s.time*(s.held?.018f:.16f);
        // Snapshot all values; queued rendering must not capture a mutable entity state.
        float mass=s.mass,age=s.gatherAge; int target=s.targetMass,before=s.beforeMass,light=s.light;
        boolean held=s.held; Vec3d from=s.groundDelta;
        queue.submitCustom(matrices,RenderLayer.getEntityCutoutNoCull(TEXTURE),(entry,out) -> {
            int index=0;
            for(Part part:MESH.parts()) {
                float visible=target==0?(index==0?1:0):Math.clamp((mass-part.threshold()+5)/6,0,1);
                if(visible<=0){index++;continue;}
                float rise=1;
                if(held && part.threshold()>=before && target>before && age<14)
                    rise=Math.clamp((age-index*.16f)/10f,0,1);
                rise=rise*rise*(3-2*rise);
                float scale=radius*visible*(target==0?1.7f:1f);
                Vec3d shift=rise<1?from.multiply(1-rise):Vec3d.ZERO;
                for(Face face:part.faces()) {
                    Vector3f[] points=new Vector3f[face.positions().length];
                    for(int i=0;i<points.length;i++) {
                        float[] p=face.positions()[i];
                        points[i]=new Vector3f(p[0]*scale,p[1]*scale,p[2]*scale).rotateY(rotation);
                        if(!held)points[i].rotateX(rotation*.7f);
                        points[i].add((float)shift.x,(float)shift.y,(float)shift.z);
                    }
                    Vector3f normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0])).normalize();
                    float shade=.66f+.26f*Math.max(0,normal.y)+.08f*Math.max(0,-normal.x);
                    int c=Math.clamp((int)(255*shade),0,255),color=0xFF000000|(c<<16)|(c<<8)|c;
                    for(int i=1;i<points.length-1;i++) {
                        vertex(out,entry,points[0],face.uv()[0],normal,color,light);
                        vertex(out,entry,points[i],face.uv()[i],normal,color,light);
                        vertex(out,entry,points[i+1],face.uv()[i+1],normal,color,light);
                        vertex(out,entry,points[i+1],face.uv()[i+1],normal,color,light);
                    }
                }
                index++;
            }
        });
        matrices.pop();
    }
    private static void vertex(VertexConsumer out,MatrixStack.Entry entry,Vector3f point,float[] uv,
            Vector3f normal,int color,int light) {
        out.vertex(entry,point.x,point.y,point.z).color(color).texture(uv[0],uv[1])
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry,normal.x,normal.y,normal.z);
    }
}
