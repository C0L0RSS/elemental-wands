package com.anton.elementalwands.client.renderer;

import java.lang.reflect.Proxy;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModSpellBlocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** Loaded-client checks, including real vertex buffers; does not claim human visual approval. */
final class FireClientChecks {
    static void check(MinecraftClient client) throws Exception {
        var meteor=new FireMeteorRenderer.State();meteor.entityType=EntityType.FALLING_BLOCK;
        require(client.getEntityRenderDispatcher().getRenderer(meteor) instanceof FireMeteorRenderer,"Meteor renderer not registered");
        var pyre=new PyreFrontRenderer.State();pyre.entityType=ModEntities.PYRE_FRONT;
        require(client.getEntityRenderDispatcher().getRenderer(pyre) instanceof PyreFrontRenderer,"Pyre wall renderer not registered");
        var flame=client.getBlockRenderManager().getModel(ModSpellBlocks.INFERNO_FLAME.getDefaultState());
        require(flame.particleSprite().getContents().getId().getNamespace().equals("minecraft"),"Ordinary ground fire is not vanilla");
        var low=client.getBlockRenderManager().getModel(ModSpellBlocks.PYRE_FLAME.getDefaultState());
        require(low.particleSprite().getContents().getId().equals(Identifier.of("elementalwands","block/fire_ground_a")),"Pyre low-fire model missing");
        require(FireSpellMeshes.meteorFaceCount()==1248,"Approved meteor mesh changed");
        for(int frame=0;frame<4;frame++)for(String family:new String[]{"entity/fire_meteor_surface_","entity/inferno_stream_","entity/inferno_front_","particle/fire/pyre_front_","particle/fire/meteor_shell_"}) {
            var id=Identifier.of("elementalwands","textures/"+family+frame+".png");
            require(client.getResourceManager().getResource(id).isPresent(),"Missing Fire art "+id);
            client.getTextureManager().getTexture(id);
        }
        for(double time:new double[]{0,.14,.55,2.7,4.2,9.4}) {
            checkMesh(time,false);checkMesh(time,true);
        }
        System.out.println("FIRE CLIENT CHECKS PASSED: baked vanilla/low fire, both registered renderers, four-frame textures, finite bounded meteor/wall buffers.");
    }
    private static void checkMesh(double time,boolean pyre) {
        int[] verticesTotal={0},submissions={0};
        var queue=(OrderedRenderCommandQueue)Proxy.newProxyInstance(OrderedRenderCommandQueue.class.getClassLoader(),new Class<?>[]{OrderedRenderCommandQueue.class},(proxy,method,args)->{
            require(method.getName().equals("submitCustom"),"Unexpected Fire submission");submissions[0]++;
            try(var allocator=new BufferAllocator(512000)) {
                var out=new BufferBuilder(allocator,VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                ((OrderedRenderCommandQueue.Custom)args[2]).render(((MatrixStack)args[0]).peek(),out);
                try(var buffer=out.end()) {
                    int count=buffer.getDrawParameters().vertexCount();verticesTotal[0]+=count;
                    var bytes=buffer.getBuffer();int stride=VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL.getVertexSize();
                    for(int i=0;i<count;i++)for(int axis=0;axis<3;axis++) {
                        float value=bytes.getFloat(i*stride+axis*4);
                        require(Float.isFinite(value) && Math.abs(value)<18,"Non-finite or unbounded Fire vertex");
                        if(pyre&&axis==1)require(value>=-.001&&value<=2.801,"Pyre wall height changed");
                    }
                }
            }
            return null;
        });
        if(pyre)FireSpellMeshes.submitPyre(new MatrixStack(),queue,time,73,new Vec3d(18,8,22),false);
        else FireSpellMeshes.submitMeteor(new MatrixStack(),queue,time,new Vec3d(18,8,22),false);
        require(pyre?verticesTotal[0]==24:verticesTotal[0]==5988,"Fire geometry count drift: "+verticesTotal[0]);
        require(submissions[0]<=13,"Excess Fire draw submissions");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
