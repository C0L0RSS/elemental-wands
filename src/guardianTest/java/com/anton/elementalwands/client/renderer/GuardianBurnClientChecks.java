package com.anton.elementalwands.client.renderer;

import java.lang.reflect.Proxy;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.command.*;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;

/** Exercise vanilla's actual fire mesher with the Guardian's bounded replacement dimensions. */
final class GuardianBurnClientChecks {
    static void check(MinecraftClient client) throws Exception {
        var state = new FracturedGuardianRenderState();
        state.width=3.2f;state.height=5.2f;state.onFire=true;
        GuardianBurnVisual.capture(state);
        require(state.burning && !state.onFire && state.width==3.2f && state.height==5.2f,"Burn capture changed dimensions or retained automatic fire");
        var camera=new CameraRenderState();camera.orientation=new Quaternionf().rotateXYZ(.4f,.8f,.1f);
        var orientation=new Quaternionf(camera.orientation);
        int[] submissions={0};float[] height={0};
        var queue=(OrderedRenderCommandQueue)Proxy.newProxyInstance(OrderedRenderCommandQueue.class.getClassLoader(),
                new Class<?>[]{OrderedRenderCommandQueue.class},(proxy,method,args)->{
            require(method.getName().equals("submitFire"),"Unexpected burn render command");submissions[0]++;
            var flame=(EntityRenderState)args[1];
            require(flame!=state,"Fire command reused mutable Guardian state");
            try(var allocator=new BufferAllocator(4096)) {
                var vertices=new BufferBuilder(allocator,VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                VertexConsumerProvider provider=layer -> vertices;
                var render=FireCommandRenderer.class.getDeclaredMethod("render",MatrixStack.Entry.class,VertexConsumerProvider.class,
                        EntityRenderState.class,Quaternionf.class,net.minecraft.client.texture.AtlasManager.class);
                render.setAccessible(true);
                render.invoke(new FireCommandRenderer(),new MatrixStack().peek(),provider,flame,new Quaternionf(),client.getAtlasManager());
                try(var buffer=vertices.end()) {
                    require(buffer.getDrawParameters().vertexCount()==4,"Burn cue grew beyond one flame quad");
                    var data=buffer.getBuffer();int stride=VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL.getVertexSize();
                    for(int i=0;i<4;i++) {
                        float x=data.getFloat(i*stride),y=data.getFloat(i*stride+4),z=data.getFloat(i*stride+8);
                        require(Float.isFinite(x+y+z) && Math.abs(x)<.78f && y>=0 && y<2.17f,"Burn flame obscures upper body");
                        height[0]=Math.max(height[0],y);
                    }
                }
            }
            return null;
        });
        GuardianBurnVisual.submit(state,new MatrixStack(),queue,camera);
        require(submissions[0]==1 && height[0]>2 && camera.orientation.equals(orientation),"Burn cue absent or camera rotation mutated");
        state.arenaHidden=true;GuardianBurnVisual.submit(state,new MatrixStack(),queue,camera);state.arenaHidden=false;
        state.invisible=true;GuardianBurnVisual.submit(state,new MatrixStack(),queue,camera);state.invisible=false;
        state.invisibleToPlayer=true;GuardianBurnVisual.submit(state,new MatrixStack(),queue,camera);state.invisibleToPlayer=false;
        state.deathTime=1;GuardianBurnVisual.submit(state,new MatrixStack(),queue,camera);state.deathTime=0;
        state.onFire=false;GuardianBurnVisual.capture(state);GuardianBurnVisual.submit(state,new MatrixStack(),queue,camera);
        require(!state.burning && submissions[0]==1,"Flames linger after extinguish/death or reveal hidden actor");
        System.out.println("GUARDIAN BURN CLIENT CHECK PASSED: vanilla fire mesh bounded to 1.54 x 2.16 blocks; dimensions preserved; hidden/dead/extinguished states omitted.");
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
