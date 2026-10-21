package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.roc.StormRocEntity;
import com.anton.elementalwands.entity.roc.StormRocPose;
import com.anton.elementalwands.entity.roc.StormRocRules;
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
    /** The spark sheet, drawn emissive; Roc effects that crackle share it. */
    static final RenderLayer LIGHT=RenderLayer.getEntityTranslucentEmissive(SHEET);
    private static final float RAD=(float)(Math.PI/180);
    static final JsonObject DATA=load();
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
        Map<String,float[]> channels=state.frame!=null?state.frame.channels():StormRocPose.pose(state.pose,state.poseTick);
        // The Thunder Bite: lightning crackles at the corners of its open mouth and along its jaw
        // while it rears up, flares when its aim locks on, and bursts out from the bill as it snaps
        // shut, in the same sprites, before dying away.
        var bite=StormRocRules.Action.BITE;
        float biteTick=state.pose.startsWith("bite")?state.poseTick:-1;
        int impact=state.pose.equals("bite_leap")?StormRocRules.BITE_LEAP_IMPACT:bite.impact;
        float burst=biteTick>=impact?(biteTick-impact)/BURST_TICKS:-1;
        // The Storm Sweep crackles along its bill from its coil until the sweep is through, and
        // trails crackles along the bill's path as it scythes the front.
        var sweep=StormRocRules.Action.SWING_LEFT;
        float sweepTick=state.pose.startsWith("swing")?state.poseTick:-1;
        boolean sweeping=sweepTick>=0&&sweepTick<sweep.impact+StormRocRules.SWING_SWEEP+4;
        // The Storm Scream's breath gathers crackles in its flared crest, unlike the bite's mouth,
        // growing as it draws in; with the blast they leap to its open bill and blaze there.
        var scream=StormRocRules.Action.SCREAM;
        float screamTick=state.pose.equals("scream")?state.poseTick:-1;
        boolean breathing=screamTick>=6&&screamTick<scream.impact,screaming=breathing||screamTick>=scream.impact&&screamTick<scream.impact+14;
        // The Thunderclap's wings crackle along their edges as they rise, until they hit the rock.
        var clap=StormRocRules.Action.WING;
        float clapTick=state.pose.equals("wing")?state.poseTick:-1;
        boolean clapping=clapTick>=4&&clapTick<clap.impact+2;
        // The Raptor Strike's talons crackle from its coil, when its aim locks, through its flight
        // and flare at the strike before they die away.
        var pounce=StormRocRules.Action.POUNCE;
        float pounceTick=state.pose.equals("pounce")?state.poseTick:-1;
        boolean striking=pounceTick>=pounce.lock&&pounceTick<pounce.impact+TALON_TICKS;
        // The Thunder Charge's talons crackle from its first rake through its run until its skid
        // stops; its gaping bill blazes with crackles from the launch for as long as it runs, and
        // goes dark as the skid snaps it shut.
        float stampedeTick=state.pose.equals("stampede")?state.poseTick:-1;
        boolean running=state.pose.equals("stampede_run"),skidding=state.pose.equals("stampede_skid")&&state.poseTick<StormRocRules.STAMPEDE_SKID_TICKS;
        boolean rushing=stampedeTick>=4||running||skidding;
        boolean screeching=stampedeTick>=StormRocRules.Action.STAMPEDE.impact-2||running;
        List<JsonObject> anchors=new ArrayList<>();
        for(JsonElement element:DATA.getAsJsonArray("anchors"))anchors.add(element.getAsJsonObject());
        int head=anchors.size();
        if(breathing)for(JsonElement element:DATA.getAsJsonArray("crest_anchors"))anchors.add(element.getAsJsonObject());
        else if(clapping)for(JsonElement element:DATA.getAsJsonArray("wing_anchors"))anchors.add(element.getAsJsonObject());
        else if(striking||rushing)for(JsonElement element:DATA.getAsJsonArray("talon_anchors"))anchors.add(element.getAsJsonObject());
        if(screeching||(!breathing&&!clapping&&!striking&&!rushing)&&(biteTick>=0&&burst<1||sweeping||screaming))
            for(JsonElement element:DATA.getAsJsonArray("mouth_anchors"))anchors.add(element.getAsJsonObject());
        List<Matrix4f> transforms=new ArrayList<>();
        for(var anchor:anchors)transforms.add(transform(getGeoModel().getBone(anchor.get("bone").getAsString()).orElseThrow(),channels));
        // It flares while calling the lightning down, as it does in the dive, the scream and the
        // Thunder Bite, until its burst dies away.
        boolean charged=state.charged || state.pose.equals("dive") || state.pose.equals("call") || screaming || clapping || striking || rushing
                || biteTick>=0&&burst<1 || sweeping
                || state.pose.equals("stuck")&&state.poseTick>=100;
        // The mouth crackles grow when the aim locks, and flare at the bite before they fade.
        float mouthSize=screeching?1.7f:rushing?1.1f:striking?pounceTick<pounce.impact?.8f+.6f*(pounceTick-pounce.lock)/(pounce.impact-pounce.lock):2f*(1-(pounceTick-pounce.impact)/TALON_TICKS)+.3f
                :clapping?.7f+.8f*Math.min(1,clapTick/clap.impact):breathing?.6f+.9f*Math.min(1,screamTick/scream.impact):screaming?2.2f*(1-(screamTick-scream.impact)/14)+.3f
                :biteTick<bite.lock?1:burst<0?1.35f:2.2f*(1-burst)+.2f;
        float time=state.age/20;
        queue.submitCustom(matrices,LIGHT,(entry,vertices)->{
            Matrix4f base=entry.getPositionMatrix();
            float scale=base.getScale(new Vector3f()).x;
            Vector3f right=new Vector3f(1,0,0).rotate(camera.orientation);
            Vector3f up=new Vector3f(0,1,0).rotate(camera.orientation);
            Vector3f normal=new Vector3f(0,0,1).rotate(camera.orientation);
            var mode=DATA.getAsJsonObject(charged?"charged":"normal");
            var range=DATA.getAsJsonArray("alpha_range");
            Vector3f bill=null;
            int index=0;
            for(var anchor:anchors) {
                String sequenceName=anchor.get("sequence").getAsString();
                var sequence=DATA.getAsJsonObject("sequences").getAsJsonObject(sequenceName);
                var pivot=anchor.getAsJsonArray("pivot_model_pixels");
                Vector3f at=new Matrix4f(base).mul(transforms.get(index)).transformPosition(new Vector3f(
                        -pivot.get(0).getAsFloat()/16,pivot.get(1).getAsFloat()/16,pivot.get(2).getAsFloat()/16));
                if(anchor.get("bone").getAsString().equals("spark_beak_tip"))bill=at;
                int copies=sequenceName.equals("spark")?1:mode.get("copies_per_anchor").getAsInt();
                for(int copy=0;copy<copies;copy++) {
                    double t=time+anchor.get("phase").getAsDouble()+copy*.119;
                    float jitter=DATA.get("jitter_blocks").getAsFloat()*scale*(charged?1.5f:1);
                    Vector3f center=new Vector3f(at).fma((float)Math.sin(t*31+copy*7)*jitter,right)
                            .fma((float)Math.cos(t*27+index)*jitter,up).fma(.025f*scale,normal);
                    float size=anchor.get("size_blocks").getAsFloat()*scale*mode.get("size_multiplier").getAsFloat()*(copy>0?.78f:1)*(index>=head?mouthSize:1);
                    double angle=index*.67+copy*1.8+Math.sin(t*6)*.16;
                    double alpha=(range.get(0).getAsDouble()+(range.get(1).getAsDouble()-range.get(0).getAsDouble())*(.5+.5*Math.sin(t*43+index*2+copy)))*mode.get("alpha_multiplier").getAsDouble();
                    sprite(vertices,sequence,t,center,size,angle,alpha,right,up);
                }
                index++;
            }
            // The sweep's trail: crackles where the bill has just been, fading behind it.
            if(sweepTick>=sweep.impact-1&&sweepTick<=sweep.impact+StormRocRules.SWING_SWEEP+3) {
                var arc=DATA.getAsJsonObject("sequences").getAsJsonObject("arc");
                for(int k=1;k<=SWEEP_TRAIL;k++) {
                    float past=sweepTick-k*.6f;
                    if(past<sweep.impact-1)break;
                    var s=StormRocPose.socket(state.pose,past,"beak");
                    Vector3f at=new Matrix4f(base).transformPosition(new Vector3f((float)-s.x/1.25f,(float)s.y/1.25f,(float)s.z/1.25f));
                    double t=time+k*.21;
                    sprite(vertices,arc,t,at,1.05f*scale*(1-.06f*k),k*1.3+Math.sin(t*6)*.2,1.05-.1*k,right,up);
                }
            }
            // The bite's burst: crackles leap out from the bill in every direction to the bite's
            // reach, flickering, and fade.
            if(bill!=null&&burst>=0&&burst<1) {
                var arc=DATA.getAsJsonObject("sequences").getAsJsonObject("arc");
                float reach=(float)(StormRocRules.BITE_RADIUS*Math.min(1,.35+burst*1.6));
                for(int i=0;i<BURST_SPARKS;i++) {
                    double y=1-2*(i+.5)/BURST_SPARKS,ring=Math.sqrt(1-y*y),a=i*2.39996;
                    Vector3f center=new Vector3f(bill).add(new Vector3f((float)(Math.cos(a)*ring),(float)(y*.6),(float)(Math.sin(a)*ring)).mul(reach*(.55f+.45f*(i%3)/2f)));
                    double t=time+i*.137;
                    sprite(vertices,arc,t,center,1.15f*scale*(1-.4f*burst),i*1.1+Math.sin(t*6)*.2,1.1*(1-burst),right,up);
                }
            }
        });
    }
    private static final float BURST_TICKS=9;
    private static final float TALON_TICKS=8;
    private static final int SWEEP_TRAIL=8;
    private static final int BURST_SPARKS=16;
    /** One camera-facing crackle from the sheet, on the frame its sequence shows at time t. */
    static void sprite(VertexConsumer vertices,JsonObject sequence,double t,Vector3f center,float size,double angle,double alpha,Vector3f right,Vector3f up) {
        var frames=sequence.getAsJsonArray("frames");
        int frame=Math.floorMod((int)Math.floor(t*sequence.get("fps").getAsDouble()),frames.size());
        var tile=frames.get(frame).getAsJsonObject();var uv=tile.getAsJsonArray("uv");var dimensions=tile.getAsJsonArray("size");
        float tw=DATA.getAsJsonArray("sheet_size").get(0).getAsFloat(),th=DATA.getAsJsonArray("sheet_size").get(1).getAsFloat();
        float u0=uv.get(0).getAsFloat()/tw,v0=uv.get(1).getAsFloat()/th;
        float u1=u0+dimensions.get(0).getAsFloat()/tw,v1=v0+dimensions.get(1).getAsFloat()/th;
        Vector3f r=new Vector3f(right).mul((float)Math.cos(angle)).fma((float)Math.sin(angle),up).mul(size*.5f);
        Vector3f u=new Vector3f(up).mul((float)Math.cos(angle)).fma(-(float)Math.sin(angle),right).mul(size*.5f);
        int rgba=((int)(Math.max(0,Math.min(1,alpha))*255)<<24)|0xFFFFFF;
        vertex(vertices,new Vector3f(center).sub(r).sub(u),u0,v1,rgba);
        vertex(vertices,new Vector3f(center).add(r).sub(u),u1,v1,rgba);
        vertex(vertices,new Vector3f(center).add(r).add(u),u1,v0,rgba);
        vertex(vertices,new Vector3f(center).sub(r).add(u),u0,v0,rgba);
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
