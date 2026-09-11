package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.GuardianFanRules;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Five hovering, visibly charged stones; exact release sockets are shared with server projectiles. */
final class GuardianFanVisual {
    static void submit(FracturedGuardianRenderState state,MatrixStack matrices,OrderedRenderCommandQueue queue) {
        float age=state.fanTime;
        if(age<0)return;
        if(state.unstable && age>=GuardianFanRules.REPEAT)age-=GuardianFanRules.REPEAT;
        if(age>=GuardianFanRules.RELEASE)return;
        float form=Math.clamp(age/12,0,1),size=(float)(GuardianFanRules.RADIUS*2)*form;
        for(int i=0;i<GuardianFanRules.COUNT;i++) {
            Vec3d socket=GuardianFanRules.socket(Vec3d.ZERO,state.fanYaw,state.fanPitch,i);
            socket=socket.add(0,-.9*(1-form),0);
            matrices.push();matrices.translate(socket.x,socket.y,socket.z);
            // Cubes settle before the lock; release collider matches their upright .64-block envelope.
            if(age<GuardianFanRules.LOCK)matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((1-form)*90));
            matrices.push();matrices.scale(size,size,size);matrices.translate(-.5,-.5,-.5);
            queue.submitBlock(matrices,Blocks.COBBLESTONE.getDefaultState(),state.light,OverlayTexture.DEFAULT_UV,0);
            matrices.pop();
            float r=size*.58f;
            queue.submitCustom(matrices,RenderLayer.getLightning(),(entry,out)->{
                for(int side=-1;side<=1;side+=2) {
                    out.vertex(entry,-r,-r,side*r).color(95,240,255,220);
                    out.vertex(entry,-r,-r+.045f,side*r).color(95,240,255,220);
                    out.vertex(entry,r,-r+.045f,side*r).color(200,255,255,220);
                    out.vertex(entry,r,-r,side*r).color(200,255,255,220);
                }
            });
            matrices.pop();
        }
    }
}
