package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.client.ClientPlayerData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.random.Random;
import com.mojang.blaze3d.vertex.VertexFormat;

/** Actual baked block models and GPU-format buffers; separate from human playtesting. */
public final class NatureClientChecks {
    public static void check(MinecraftClient client){
        int models=0;
        for(var block:new net.minecraft.block.Block[]{ModSpellBlocks.NATURE_SEEDLING,ModSpellBlocks.NATURE_ROOTS,
                ModSpellBlocks.NATURE_RAFT,ModSpellBlocks.NATURE_HEARTWOOD,ModSpellBlocks.NATURE_FLOWERING_LEAVES}){
            for(var state:block.getStateManager().getStates()){
                var model=client.getBlockRenderManager().getModel(state);
                require(!model.particleSprite().getContents().getId().getPath().contains("missing"),"Missing Nature block model "+state);
                int quads=0;
                for(var part:model.getParts(Random.create(0)))for(var quad:part.getQuads(null)){
                    require(!quad.sprite().getContents().getId().getPath().contains("missing"),"Missing Nature face texture "+state);
                    quads++;
                }
                require(quads>20 && quads<6000,"Nature model lost geometry or is unbounded: "+state+" "+quads);
                require(client.getBlockColors().getColor(state,null,null,0x30B821)==0xFF30B821,"Nature model face palette not applied");
                models++;
            }
        }
        checkMesh("seed",-1);
        for(String type:new String[]{"human","wide","boss"})for(int stacks=1;stacks<=5;stacks++){
            for(String mode:stacks==5?new String[]{"rooted","opening","resisting","lingering"}:new String[]{"lingering"}){
                checkMesh(type+"_"+stacks+"_"+mode,-1);
                checkMesh(type+"_"+stacks+"_"+mode,.25f);
            }
        }
        ClientPlayerData.setEntangleStacks(9999,0,100,30);
        require(ClientPlayerData.getEntangleStacks(9999)==0,"Ultimate visual incorrectly slows wand cooldowns");
        ClientPlayerData.expireEntangles(130);
        require(ClientPlayerData.getEntangleState(9999)==null,"Ultimate visual outlives root");
        ClientPlayerData.setEntangleStacks(9999,5,200,40);
        ClientPlayerData.setEntangleStacks(9999,5,220,40);
        require(ClientPlayerData.getEntangleState(9999).visualStartedAtTick()==200,"Refreshing stacks restarts vine growth");
        ClientPlayerData.clearEntangleState(9999);
        System.out.println("NATURE CLIENT CHECKS PASSED: "+models+" baked models, 25 meshes, growth buffers, palette and root lifecycle");
    }
    private static void checkMesh(String name,float reveal){
        try(var allocator=new BufferAllocator(4*1024*1024)){
            var format=VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;
            var out=new BufferBuilder(allocator,VertexFormat.DrawMode.QUADS,format);
            NatureMeshes.draw(name,out,new MatrixStack().peek(),0x00F000F0,1,reveal,null,false);
            try(var buffer=out.end()){
                int count=buffer.getDrawParameters().vertexCount();
                require(count>0 && count<60000,"Empty or unbounded Nature mesh: "+name);
                var data=buffer.getBuffer();int stride=format.getVertexSize();
                for(int i=0;i<count;i++)for(int axis=0;axis<3;axis++){
                    float value=data.getFloat(i*stride+axis*4);
                    require(Float.isFinite(value)&&Math.abs(value)<6,"Nature vertex invalid: "+name);
                }
            }
        }
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
