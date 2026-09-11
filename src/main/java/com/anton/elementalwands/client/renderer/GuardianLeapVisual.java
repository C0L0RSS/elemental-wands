package com.anton.elementalwands.client.renderer;

import java.util.ArrayList;
import java.util.List;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.entity.GuardianWaveSurface;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/** Body shadow follows the actual airborne position; it does not reveal the locked destination. */
final class GuardianLeapVisual {
    record Mark(Vec3d a, Vec3d b, boolean core) {}
    static List<Mark> prepare(FracturedGuardianEntity entity, float partialTick) {
        if (entity.getLeapTime(partialTick) < com.anton.elementalwands.entity.GuardianLeapRules.TAKEOFF) return List.of();
        Vec3d position=entity.getLerpedPos(partialTick);
        Vec3d base=new Vec3d(position.x,entity.getLeapTarget().y,position.z);
        Vec3d center=GuardianWaveSurface.ground(entity.getEntityWorld(),entity,base,base.x,base.z);
        if (center==null) return List.of();
        var result=new ArrayList<Mark>();
        for(int i=0;i<24;i++) {
            double a=i*Math.PI/12;
            result.add(new Mark(center.add(0,.04,0),center.add(1.6*Math.cos(a),.04,1.6*Math.sin(a)),false));
        }
        return List.copyOf(result);
    }
    static void submit(FracturedGuardianRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
        if(state.leapMarks.isEmpty()) return;
        queue.submitCustom(matrices,RenderLayer.getLightning(),(entry,out) -> {
            for(int i=0;i<state.leapMarks.size();i++) {
                Mark mark=state.leapMarks.get(i),next=state.leapMarks.get((i+1)%state.leapMarks.size());
                for(Vec3d p:List.of(mark.a,mark.b,next.b,mark.a))
                    out.vertex(entry,(float)(p.x-state.x),(float)(p.y-state.y),(float)(p.z-state.z)).color(10,22,28,95);
            }
        });
    }
}
