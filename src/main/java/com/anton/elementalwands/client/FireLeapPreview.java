package com.anton.elementalwands.client;

import java.util.*;
import com.anton.elementalwands.entity.FireLeapEntity;
import com.anton.elementalwands.entity.GuardianWaveSurface;
import com.anton.elementalwands.util.FireLeapRules;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.Vec3d;

/** Private aim geometry never goes over the network. Committed carriers reveal only the landing ring. */
public final class FireLeapPreview {
    public record Segment(Vec3d a,Vec3d b,double width,int color) {}
    private static List<Segment> frame=List.of();
    private static Vec3d camera=Vec3d.ZERO;
    public static void init() {
        WorldRenderEvents.END_EXTRACTION.register(context -> {
            var client=MinecraftClient.getInstance();var result=new ArrayList<Segment>();
            camera=context.camera().getPos();
            if(client.player!=null) {
                if(WandControls.aimingLeap()) {
                    Vec3d to=FireLeapRules.target(client.player);
                    boolean valid=to!=null && FireLeapRules.supported(client.world,client.player,client.player.getEntityPos(),1.5)
                            && FireLeapRules.validTarget(client.player,to);
                    if(to==null) to=client.player.getEyePos().add(client.player.getRotationVec(1).multiply(FireLeapRules.RANGE));
                    int color=valid ? 0x60ffc36a : 0x90ff5148;
                    Vec3d previous=client.player.getEntityPos();
                    for(int i=1;i<=40;i++) {
                        Vec3d next=FireLeapRules.position(client.player.getEntityPos(),to,i/40.);
                        // Avoid drawing the near-camera takeoff leg as a full-screen strip.
                        if(i%2==0 && previous.squaredDistanceTo(camera)>4 && next.squaredDistanceTo(camera)>4)
                            result.add(new Segment(previous,next,.008,color));
                        previous=next;
                    }
                    if(valid) sigil(result,client,to,false,client.world.getTime()+context.tickCounter().getTickProgress(false));
                    else {
                        result.add(new Segment(to.add(-.3,.05,-.3),to.add(.3,.05,.3),.025,0xc0ff5148));
                        result.add(new Segment(to.add(-.3,.05,.3),to.add(.3,.05,-.3),.025,0xc0ff5148));
                    }
                }
                for(var entity:client.world.getEntities()) if(entity instanceof FireLeapEntity leap && leap.locksPassenger()
                        && leap.squaredDistanceTo(client.player)<96*96) {
                    Vec3d to=leap.destination();
                    sigil(result,client,to,true,client.world.getTime()+context.tickCounter().getTickProgress(false));
                }
            }
            frame=List.copyOf(result);
        });
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            var out=context.consumers().getBuffer(RenderLayer.getLightning());var entry=context.matrices().peek();
            for(var line:frame) {
                Vec3d a=line.a.subtract(camera),b=line.b.subtract(camera);
                Vec3d side=a.add(b).multiply(.5).crossProduct(b.subtract(a)).normalize().multiply(line.width);
                for(Vec3d v:List.of(a.subtract(side),a.add(side),b.add(side),b.subtract(side)))
                    out.vertex(entry,(float)v.x,(float)v.y,(float)v.z).color(line.color);
            }
            if(context.consumers() instanceof net.minecraft.client.render.VertexConsumerProvider.Immediate immediate)
                immediate.draw(RenderLayer.getLightning());
        });
    }
    /** Broken outer seal, eight inward flame runes, a central fire glyph and rising ember flecks. */
    private static void sigil(List<Segment> result,MinecraftClient client,Vec3d origin,boolean committed,double time) {
        int amber=committed?0xb0ff852e:0x78ffad54,gold=committed?0xc0ffd37c:0x90ffd37c;
        double width=committed?.04:.027;
        for(int sector=0;sector<8;sector++) {
            double angle=sector*Math.PI/4;
            for(int i=0;i<5;i++) {
                double a=angle-.29+i*.116,b=a+.116;
                groundLine(result,client,origin,polar(a,5),polar(b,5),width,amber);
                if(i>0 && i<4)groundLine(result,client,origin,polar(a,4.8),polar(b,4.8),width*.65,gold);
            }
            // Each tooth resembles a small angular flame, staying inside the exact damage radius.
            Vec3d tip=polar(angle,4.12),left=polar(angle-.06,4.6),right=polar(angle+.06,4.6);
            groundLine(result,client,origin,left,tip,width,gold);groundLine(result,client,origin,tip,right,width,gold);
            groundLine(result,client,origin,polar(angle,4.55),polar(angle,4.32),width,amber);
        }
        double[][] flame={{0,-.98},{-.18,-.23},{-.43,-.5},{-.63,.19},{-.43,.6},{0,.83},{.43,.6},{.63,.19},{.35,-.49},{.19,-.1},{0,-.98}};
        for(int i=1;i<flame.length;i++)groundLine(result,client,origin,new Vec3d(flame[i-1][0],0,flame[i-1][1]),new Vec3d(flame[i][0],0,flame[i][1]),width*1.3,amber);
        for(int i=0;i<4;i++) {
            double a=i*Math.PI/2;
            groundLine(result,client,origin,polar(a,.24),polar(a+Math.PI/2,.24),width,gold);
            Vec3d point=polar(a+Math.PI/4,1.25);
            groundLine(result,client,origin,point,point.add(polar(a+Math.PI*.9,.25)),width*.7,gold);
            groundLine(result,client,origin,point,point.add(polar(a-Math.PI*.4,.25)),width*.7,gold);
        }
        for(int i=0;i<12;i++) {
            double phase=(time*.018+i*.173)%1,angle=i*Math.PI/6+Math.sin(time*.018+i)*.04;
            Vec3d offset=polar(angle,4.6),floor=GuardianWaveSurface.ground(client.world,client.player,origin,origin.x+offset.x,origin.z+offset.z);
            if(floor==null || !GuardianWaveSurface.visible(client.world,client.player,origin,floor))continue;
            Vec3d spark=floor.add(0,.06+phase*.65,0);
            int c=((int)((1-phase)*(committed?175:105))<<24)|0xFFD27A;
            result.add(new Segment(spark,spark.add(0,.035,0),.018,c));
        }
    }
    private static Vec3d polar(double angle,double radius) { return new Vec3d(Math.cos(angle)*radius,0,Math.sin(angle)*radius); }
    private static void groundLine(List<Segment> result,MinecraftClient client,Vec3d origin,Vec3d a,Vec3d b,double width,int color) {
        Vec3d p=GuardianWaveSurface.ground(client.world,client.player,origin,origin.x+a.x,origin.z+a.z);
        Vec3d q=GuardianWaveSurface.ground(client.world,client.player,origin,origin.x+b.x,origin.z+b.z);
        if(p!=null && q!=null && p.distanceTo(q)<1.2 && GuardianWaveSurface.visible(client.world,client.player,origin,p))
            result.add(new Segment(p.add(0,.045,0),q.add(0,.045,0),width,color));
    }
    private FireLeapPreview() {}
}
