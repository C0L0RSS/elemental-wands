package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.roc.StormRocEntity;
import com.anton.elementalwands.entity.roc.StormRocPose;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** The approved six head crackles, posed from the same channels as the body. */
public final class StormRocCrackleLayer extends GeoRenderLayer<StormRocEntity,Void,StormRocRenderState> {
    private static final Identifier SHEET=Identifier.of("elementalwands","textures/entity/storm_roc_sparks.png");
    private static final RenderLayer LIGHT=RenderLayer.getEntityTranslucentEmissive(SHEET);
    private static final float RAD=(float)(Math.PI/180);
    private static final JsonObject DATA=load();
    public StormRocCrackleLayer(GeoRenderer<StormRocEntity,Void,StormRocRenderState> renderer) { super(renderer); }
    private static JsonObject load() {
        try(var stream=StormRocCrackleLayer.class.getResourceAsStream("/assets/elementalwands/storm_roc_sparks.json")) {
            if(stream==null)throw new IllegalStateException("Missing approved Storm Roc spark anchors");
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    @Override public void submitRenderTask(StormRocRenderState state,MatrixStack matrices,BakedGeoModel model,
            OrderedRenderCommandQueue queue,CameraRenderState camera,int light,int overlay,int color,boolean visible) {
        if(!visible)return;
        // Animation transforms are normally applied inside deferred model callbacks.
        // Capture channels now, rather than reading shared bones from a prior frame.
        Map<String,float[]> channels=StormRocPose.pose(state.pose,state.poseTick);
        List<Matrix4f> transforms=new ArrayList<>();
        for(JsonElement element:DATA.getAsJsonArray("anchors")) {
            String name=element.getAsJsonObject().get("bone").getAsString();
            transforms.add(transform(getGeoModel().getBone(name).orElseThrow(),channels));
        }
        boolean charged=state.pose.equals("dive") || state.pose.equals("scream")&&state.poseTick>=18
                || state.pose.equals("stuck")&&state.poseTick>=100;
        float time=state.age/20;
        queue.submitCustom(matrices,LIGHT,(entry,vertices)->{
            Matrix4f base=entry.getPositionMatrix();
            float scale=base.getScale(new Vector3f()).x;
            Vector3f right=new Vector3f(1,0,0).rotate(camera.orientation);
            Vector3f up=new Vector3f(0,1,0).rotate(camera.orientation);
            Vector3f normal=new Vector3f(0,0,1).rotate(camera.orientation);
            var mode=DATA.getAsJsonObject(charged?"charged":"normal");
            int index=0;
            for(JsonElement element:DATA.getAsJsonArray("anchors")) {
                var anchor=element.getAsJsonObject();
                String sequenceName=anchor.get("sequence").getAsString();
                var sequence=DATA.getAsJsonObject("sequences").getAsJsonObject(sequenceName);
                var frames=sequence.getAsJsonArray("frames");
                var pivot=anchor.getAsJsonArray("pivot_model_pixels");
                Vector3f at=new Matrix4f(base).mul(transforms.get(index)).transformPosition(new Vector3f(
                        -pivot.get(0).getAsFloat()/16,pivot.get(1).getAsFloat()/16,pivot.get(2).getAsFloat()/16));
                int copies=sequenceName.equals("spark")?1:mode.get("copies_per_anchor").getAsInt();
                for(int copy=0;copy<copies;copy++) {
                    double t=time+anchor.get("phase").getAsDouble()+copy*.119;
                    int frame=Math.floorMod((int)Math.floor(t*sequence.get("fps").getAsDouble()),frames.size());
                    var tile=frames.get(frame).getAsJsonObject();var uv=tile.getAsJsonArray("uv");var dimensions=tile.getAsJsonArray("size");
                    float tw=DATA.getAsJsonArray("sheet_size").get(0).getAsFloat(),th=DATA.getAsJsonArray("sheet_size").get(1).getAsFloat();
                    float u0=uv.get(0).getAsFloat()/tw,v0=uv.get(1).getAsFloat()/th;
                    float u1=u0+dimensions.get(0).getAsFloat()/tw,v1=v0+dimensions.get(1).getAsFloat()/th;
                    float jitter=DATA.get("jitter_blocks").getAsFloat()*scale*(charged?1.5f:1);
                    Vector3f center=new Vector3f(at).fma((float)Math.sin(t*31+copy*7)*jitter,right)
                            .fma((float)Math.cos(t*27+index)*jitter,up).fma(.025f*scale,normal);
                    float size=anchor.get("size_blocks").getAsFloat()*scale*mode.get("size_multiplier").getAsFloat()*(copy>0?.78f:1);
                    double angle=index*.67+copy*1.8+Math.sin(t*6)*.16;
                    Vector3f r=new Vector3f(right).mul((float)Math.cos(angle)).fma((float)Math.sin(angle),up).mul(size*.5f);
                    Vector3f u=new Vector3f(up).mul((float)Math.cos(angle)).fma(-(float)Math.sin(angle),right).mul(size*.5f);
                    var range=DATA.getAsJsonArray("alpha_range");
                    double alpha=(range.get(0).getAsDouble()+(range.get(1).getAsDouble()-range.get(0).getAsDouble())*(.5+.5*Math.sin(t*43+index*2+copy)))*mode.get("alpha_multiplier").getAsDouble();
                    int rgba=((int)(Math.min(1,alpha)*255)<<24)|0xFFFFFF;
                    vertex(vertices,new Vector3f(center).sub(r).sub(u),u0,v1,rgba);
                    vertex(vertices,new Vector3f(center).add(r).sub(u),u1,v1,rgba);
                    vertex(vertices,new Vector3f(center).add(r).add(u),u1,v0,rgba);
                    vertex(vertices,new Vector3f(center).sub(r).add(u),u0,v0,rgba);
                }
                index++;
            }
        });
    }
    private static Matrix4f transform(GeoBone bone,Map<String,float[]> channels) {
        Deque<GeoBone> chain=new ArrayDeque<>();
        for(GeoBone b=bone;b!=null;b=b.getParent())chain.addFirst(b);
        Matrix4f m=new Matrix4f();
        for(GeoBone b:chain) {
            float[] p=channels.get(b.getName());
            if(p==null)continue;
            var bind=b.getInitialSnapshot();
            float x=b.getPivotX()/16,y=b.getPivotY()/16,z=b.getPivotZ()/16;
            m.translate(-p[3]/16,p[4]/16,p[5]/16).translate(x,y,z)
                    .rotateZ(bind.getRotZ()+p[2]*RAD).rotateY(bind.getRotY()-p[1]*RAD).rotateX(bind.getRotX()-p[0]*RAD)
                    .scale(p[6],p[7],p[8]).translate(-x,-y,-z);
        }
        return m;
    }
    private static void vertex(VertexConsumer v,Vector3f p,float u,float t,int color) {
        v.vertex(p.x,p.y,p.z).color(color).texture(u,t).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0,1,0);
    }
}
