package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.GuardianBeamTiming;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/** Cyan fullbright pulse and expanding angular rings; no persistent beam entity. */
final class GuardianBeamVisual {
    private GuardianBeamVisual() {}

    static void submit(FracturedGuardianRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
        float tick=state.beamTime;
        if (tick < 0 || tick >= GuardianBeamTiming.FIRE+GuardianBeamTiming.PULSE) return;
        Vec3d start=state.beamOrigin, end=state.beamEnd;
        Vec3d direction=end.subtract(start).normalize();
        if (direction.lengthSquared()<.5) {
            double yaw=Math.toRadians(state.bodyYaw), pitch=Math.toRadians(state.beamPitch);
            direction=new Vec3d(-Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch));
        }
        Vec3d side=direction.crossProduct(new Vec3d(0,1,0)).normalize();
        Vec3d up=side.crossProduct(direction).normalize();
        if (tick < GuardianBeamTiming.FIRE) {
            double charge=tick/GuardianBeamTiming.FIRE;
            final double radius=.12+.35*charge;
            queue.submitCustom(matrices,RenderLayer.getLightning(),(entry,vertices) -> {
                ring(vertices,entry,start,side,up,radius,.055,0,205,255,(int)(80+130*charge));
                ribbon(vertices,entry,start.subtract(up.multiply(radius*.6)),start.add(up.multiply(radius*.6)),side,.075,20,225,255,180);
            });
            return;
        }
        final float fade=GuardianBeamTiming.pulseFade(tick);
        final double progress=Math.max(0,(tick-GuardianBeamTiming.FIRE)/GuardianBeamTiming.PULSE);
        final Vec3d forward=direction;
        final double length=start.distanceTo(end);
        if (length < .01) return; // Endpoint tracking must arrive before any beam is drawn.
        queue.submitCustom(matrices,RenderLayer.getLightning(),(entry,vertices) -> {
            ribbon(vertices,entry,start,end,side,.42*fade,0,150,245,(int)(115*fade));
            ribbon(vertices,entry,start,end,up,.42*fade,0,150,245,(int)(115*fade));
            ribbon(vertices,entry,start,end,side,.17*fade,12,220,255,(int)(230*fade));
            ribbon(vertices,entry,start,end,up,.17*fade,12,220,255,(int)(230*fade));
            for (double d=.4+progress*2.2;d<length;d+=1.7) {
                ring(vertices,entry,start.add(forward.multiply(d)),side,up,.62+progress*.55,.085,
                        0,205,255,(int)(210*fade));
            }
        });
    }

    private static void ribbon(VertexConsumer out, MatrixStack.Entry entry, Vec3d a, Vec3d b, Vec3d cross,
                               double width,int r,int g,int blue,int alpha) {
        Vec3d w=cross.multiply(width);
        quad(out,entry,a.subtract(w),b.subtract(w),b.add(w),a.add(w),r,g,blue,alpha);
    }

    private static void ring(VertexConsumer out,MatrixStack.Entry entry,Vec3d center,Vec3d side,Vec3d up,
                             double radius,double thickness,int r,int g,int b,int alpha) {
        for(int i=0;i<12;i++) {
            double a=i*Math.PI/6,c=(i+1)*Math.PI/6;
            Vec3d v=side.multiply(Math.cos(a)).add(up.multiply(Math.sin(a)));
            Vec3d w=side.multiply(Math.cos(c)).add(up.multiply(Math.sin(c)));
            quad(out,entry,center.add(v.multiply(radius)),center.add(w.multiply(radius)),
                    center.add(w.multiply(radius-thickness)),center.add(v.multiply(radius-thickness)),r,g,b,alpha);
        }
    }

    private static void quad(VertexConsumer out,MatrixStack.Entry entry,Vec3d a,Vec3d b,Vec3d c,Vec3d d,
                             int r,int g,int blue,int alpha) {
        vertex(out,entry,a,r,g,blue,alpha);vertex(out,entry,b,r,g,blue,alpha);
        vertex(out,entry,c,r,g,blue,alpha);vertex(out,entry,d,r,g,blue,alpha);
        vertex(out,entry,d,r,g,blue,alpha);vertex(out,entry,c,r,g,blue,alpha);
        vertex(out,entry,b,r,g,blue,alpha);vertex(out,entry,a,r,g,blue,alpha);
    }

    private static void vertex(VertexConsumer out,MatrixStack.Entry entry,Vec3d p,int r,int g,int b,int alpha) {
        out.vertex(entry,(float)p.x,(float)p.y,(float)p.z).color(r,g,b,alpha);
    }
}
