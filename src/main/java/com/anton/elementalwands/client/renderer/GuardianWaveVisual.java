package com.anton.elementalwands.client.renderer;

import java.util.ArrayList;
import java.util.List;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.entity.GuardianWaveSurface;
import static com.anton.elementalwands.entity.GuardianCombatRules.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** A solid-looking stone ridge with a cyan crest; purely rendered, never edits the world. */
final class GuardianWaveVisual {
    record Stone(Vec3d position, float yaw, float height, boolean crest) {}
    private record Cached(long frame, List<Stone> stones) {}
    private static final java.util.Map<FracturedGuardianEntity,Cached> CACHE = new java.util.WeakHashMap<>();
    private GuardianWaveVisual() {}

    static List<Stone> prepare(FracturedGuardianEntity entity, float partialTick) {
        long frame = entity.getEntityWorld().getTime()*4 + (int)(partialTick*4);
        Cached cached = CACHE.get(entity);
        if (cached != null && cached.frame == frame) return cached.stones;
        var result = new ArrayList<Stone>();
        for (int slot=0;slot<2;slot++) {
            float tick = entity.getWaveTime(partialTick,slot)-SLAM_IMPACT;
            boolean unstable = entity.isWaveUnstable(slot);
            double range = com.anton.elementalwands.entity.GuardianPhaseRules.waveRange(unstable);
            double speed = com.anton.elementalwands.entity.GuardianPhaseRules.waveSpeed(unstable);
            if (tick<0 || tick>=Math.ceil(range/speed)) continue;
            Vec3d origin=entity.getEntityPos().add(entity.getWaveOrigin(slot));
            double front=Math.min(range,(tick+1)*speed);
            for (int row=0;row<1;row++) {
                double radius=front-.25-row*.8;
                if (radius<.3) continue;
                int count=Math.max(12,(int)Math.ceil(radius*Math.PI*2/.95));
                for (int i=0;i<count;i++) {
                    double angle=(i+row*.5)*Math.PI*2/count;
                    Vec3d p=GuardianWaveSurface.ground(entity.getEntityWorld(),entity,origin,
                            origin.x+Math.cos(angle)*radius,origin.z+Math.sin(angle)*radius);
                    if (p==null || !GuardianWaveSurface.visible(entity.getEntityWorld(),entity,origin,p)) continue;
                    result.add(new Stone(p,(float)(-angle+Math.PI/2),row==0?(float)WAVE_HEIGHT:.28f,row==0));
                }
            }
        }
        List<Stone> stones = List.copyOf(result);
        CACHE.put(entity,new Cached(frame,stones));
        return stones;
    }

    static void submit(FracturedGuardianRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
        for (Stone stone : state.waveStones) {
            matrices.push();
            matrices.translate(stone.position.x-state.x,stone.position.y-state.y-.03,stone.position.z-state.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(stone.yaw));
            matrices.scale(1.08f,stone.height,.85f);
            matrices.translate(-.5,0,-.5);
            queue.submitBlock(matrices,stone.crest ? Blocks.COBBLESTONE.getDefaultState() : Blocks.DEEPSLATE.getDefaultState(),
                    state.light,OverlayTexture.DEFAULT_UV,0);
            matrices.pop();
            if (!stone.crest) continue;
            matrices.push();
            matrices.translate(stone.position.x-state.x,stone.position.y-state.y+stone.height,stone.position.z-state.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(stone.yaw));
            queue.submitCustom(matrices,RenderLayer.getLightning(),(entry,out) -> {
                out.vertex(entry,-.55f,.015f,-.10f).color(0,225,255,235);
                out.vertex(entry,-.55f,.015f,.10f).color(0,225,255,235);
                out.vertex(entry,.55f,.015f,.10f).color(0,225,255,235);
                out.vertex(entry,.55f,.015f,-.10f).color(0,225,255,235);
            });
            matrices.pop();
        }
    }
}
