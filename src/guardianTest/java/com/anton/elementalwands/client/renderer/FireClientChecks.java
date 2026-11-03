package com.anton.elementalwands.client.renderer;

import static com.anton.elementalwands.client.blockfire.BlockFireKit.*;

import com.anton.elementalwands.client.blockfire.BlockFireMesh;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.BlockRenderType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
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
        var flame=client.getBlockRenderManager().getModel(ModSpellBlocks.INFERNO_FLAME.getDefaultState());
        require(flame.particleSprite().getContents().getId().getNamespace().equals("minecraft"),"Ordinary ground fire is not vanilla");
        require(ModSpellBlocks.PYRE_FLAME.getDefaultState().getRenderType()==BlockRenderType.INVISIBLE,"Pyre carpet block should leave drawing to block fire");
        require(client.getResourceManager().getResource(BlockFireMesh.ATLAS).isPresent(),"Missing block-fire atlas "+BlockFireMesh.ATLAS);
        client.getTextureManager().getTexture(BlockFireMesh.ATLAS);
        for(double time:new double[]{0,.14,.55,2.7,4.2,9.4})checkMesh(time);
        System.out.println("FIRE CLIENT CHECKS PASSED: vanilla ground fire, invisible carpet block, block-fire atlas, finite bounded block-fire buffers.");
    }
    /** Flames, bursts and puffs around a point 20 blocks from the camera: finite, near their source, opaque fire only. */
    private static void checkMesh(double time) {
        Vec3d camera=new Vec3d(18,8,22),at=camera.add(20,-6,0);
        var mesh=new BlockFireMesh(camera,false,false,time,null);
        flame(mesh,at,UP,new Flame(.98,2.8,100).count(7).heat(1.08).speed(1.4));
        burst(mesh,at,time%1.2,new Burst(1.5,16,.42,.42,400).smoke(false));
        puff(mesh,at.add(0,1,0),time%.24,.2,31,3.6,6);
        require(mesh.cubes()>0 && mesh.hasFire() && !mesh.hasSmoke(),"Block fire produced no cubes, or smoke with particles off");
        try(var allocator=new BufferAllocator(1<<20)) {
            var out=new BufferBuilder(allocator,VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            mesh.emitFire(out,new MatrixStack().peek());
            try(var buffer=out.end()) {
                int count=buffer.getDrawParameters().vertexCount();
                require(count==mesh.cubes()*24,"Block-fire vertex count drift: "+count);
                var bytes=buffer.getBuffer();int stride=VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL.getVertexSize();
                for(int i=0;i<count;i++) {
                    double x=bytes.getFloat(i*stride),y=bytes.getFloat(i*stride+4),z=bytes.getFloat(i*stride+8);
                    require(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z),"Non-finite block-fire vertex");
                    require(new Vec3d(x,y,z).add(camera).distanceTo(at)<6,"Block-fire vertex strayed from its source");
                }
            }
        }
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
