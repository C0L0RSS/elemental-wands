package com.anton.elementalwands.client.renderer;

import java.util.ArrayList;
import java.util.List;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.entity.GuardianLeapRules;
import com.anton.elementalwands.entity.GuardianWaveSurface;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/** Ground-projected landing boundary; orange perimeter and red heavy-impact center. */
final class GuardianLeapVisual {
    record Mark(Vec3d a, Vec3d b, boolean core) {}
    static List<Mark> prepare(FracturedGuardianEntity entity, float partialTick) {
        if (entity.getLeapTime(partialTick)<0) return List.of();
        Vec3d center=entity.getLeapTarget();
        var result=new ArrayList<Mark>();
        for (int ring=0;ring<2;ring++) {
            double radius=ring==0?GuardianLeapRules.IMPACT_RADIUS:GuardianLeapRules.CORE_RADIUS;
            for (int i=0;i<48;i++) {
                double a=i*Math.PI/24,b=(i+1)*Math.PI/24;
                Vec3d p=GuardianWaveSurface.ground(entity.getEntityWorld(),entity,center,center.x+radius*Math.cos(a),center.z+radius*Math.sin(a));
                Vec3d q=GuardianWaveSurface.ground(entity.getEntityWorld(),entity,center,center.x+radius*Math.cos(b),center.z+radius*Math.sin(b));
                if (p!=null && q!=null && Math.abs(p.y-q.y)<1.1) result.add(new Mark(p.add(0,.07,0),q.add(0,.07,0),ring==1));
            }
        }
        return List.copyOf(result);
    }

    static void submit(FracturedGuardianRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
        if (state.leapMarks.isEmpty()) return;
        // Absolute points remain fixed while the Guardian moves through the air.
        matrices.push();
        Vec3d renderOrigin=new Vec3d(state.x,state.y,state.z);
        float width=state.leapTime<GuardianLeapRules.LOCK?.09f:.18f;
        queue.submitCustom(matrices,RenderLayer.getLightning(),(entry,out) -> {
            for (Mark mark:state.leapMarks) {
                Vec3d side=mark.b.subtract(mark.a).crossProduct(new Vec3d(0,1,0)).normalize().multiply(width);
                for (Vec3d p:List.of(mark.a.subtract(side),mark.b.subtract(side),mark.b.add(side),mark.a.add(side),
                        mark.a.add(side),mark.b.add(side),mark.b.subtract(side),mark.a.subtract(side)))
                    out.vertex(entry,(float)(p.x-renderOrigin.x),(float)(p.y-renderOrigin.y),(float)(p.z-renderOrigin.z)).color(255,mark.core?65:185,35,235);
            }
        });
        matrices.pop();
    }
}
