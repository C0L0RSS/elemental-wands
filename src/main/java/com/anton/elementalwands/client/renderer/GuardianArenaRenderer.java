package com.anton.elementalwands.client.renderer;

import com.anton.elementalwands.entity.GuardianArenaEntity;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import static com.anton.elementalwands.arena.GuardianArenaRules.HALF;

/** Coarse architectural courses keep the 128-block cinematic to hundreds of draws, not millions. */
public final class GuardianArenaRenderer extends EntityRenderer<GuardianArenaEntity,GuardianArenaRenderer.State> {
    public static final class State extends EntityRenderState { float floor,radius,wall; boolean drawFloor; java.util.Set<Long> spellTiles = java.util.Set.of(); }
    private long sampledTick = Long.MIN_VALUE;
    private net.minecraft.util.math.BlockPos sampledOrigin;
    private net.minecraft.world.World sampledWorld;
    private java.util.Set<Long> spellTiles = java.util.Set.of();
    public GuardianArenaRenderer(EntityRendererFactory.Context context) { super(context); }
    @Override public boolean shouldRender(GuardianArenaEntity e,net.minecraft.client.render.Frustum frustum,double x,double y,double z) { return e.shouldRender(e.squaredDistanceTo(x,y,z)); }
    @Override public State createRenderState() { return new State(); }
    @Override public void updateRenderState(GuardianArenaEntity entity, State state, float delta) {
        super.updateRenderState(entity,state,delta);
        state.floor=entity.floor(delta); state.radius=entity.radius(delta); state.wall=entity.wall(delta); state.drawFloor=entity.drawFloor();
        var origin = net.minecraft.util.math.BlockPos.ofFloored(entity.getX(),entity.getY()+state.floor-1,entity.getZ());
        var world = entity.getEntityWorld();
        long sample = world.getTime()/2;
        if (sample != sampledTick || !origin.equals(sampledOrigin) || world != sampledWorld) {
            var tiles = new java.util.HashSet<Long>();
            if (state.radius >= HALF) for (int x=-HALF;x<HALF;x++) for(int z=-HALF;z<HALF;z++) {
                if (world.getBlockState(origin.add(x,0,z)).isOf(com.anton.elementalwands.registry.ModSpellBlocks.PYRE_COALS))
                    tiles.add(net.minecraft.util.math.ChunkPos.toLong(x,z));
            }
            spellTiles = java.util.Set.copyOf(tiles); sampledTick=sample; sampledOrigin=origin; sampledWorld=world;
        }
        state.spellTiles=spellTiles;
    }
    @Override public void render(State s, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (s.drawFloor && s.radius>0) {
            GuardianArenaFloor.submit(matrices,queue,s.floor,s.radius,s.spellTiles);
            float r=s.radius;
            if (s.floor>1) for (int side=0;side<4;side++) {
                matrices.push(); matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side*90));
                block(matrices,queue,Blocks.DEEPSLATE_BRICKS,-r,0,r-2,r*2,s.floor-1,2);
                matrices.pop();
            }
        }
        for (int side=0;side<4;side++) {
            matrices.push(); matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(side*90));
            // Recessed wall is behind every column and moulding: no coplanar visible faces.
            block(matrices,queue,Blocks.SMOOTH_QUARTZ,-HALF-5,0,HALF+2.5f,(HALF+5)*2,s.wall,2);
            for (int x=-56;x<=56;x+=16) {
                column(matrices,queue,x,s.floor,s.wall);
                if (x==56) continue;
                // One monumental lancet per bay; avoid thousands of repeated arch draws.
                float spring=s.wall-20;
                if (spring>s.floor+20) {
                    // Tall dark lancet recess, narrow luminous centre, and a stepped pointed arch.
                    float sill=s.floor+4, windowHeight=spring-sill+8;
                    block(matrices,queue,Blocks.GRAY_CONCRETE,x+4,sill,HALF+2.25f,8,windowHeight,.3f);
                    block(matrices,queue,Blocks.SEA_LANTERN,x+7.7f,sill+2,HALF+2.12f,.6f,windowHeight-4,.2f);
                    block(matrices,queue,Blocks.QUARTZ_PILLAR,x+6.8f,sill,HALF+1.95f,.45f,windowHeight,.4f);
                    block(matrices,queue,Blocks.QUARTZ_PILLAR,x+8.75f,sill,HALF+1.95f,.45f,windowHeight,.4f);
                    for (int step=0;step<7;step++) {
                        float lower=(float)(Math.sqrt(256-Math.pow(16-step,2))*.85);
                        float upper=(float)(Math.sqrt(256-Math.pow(15-step,2))*.85);
                        block(matrices,queue,Blocks.QUARTZ_BRICKS,x+1+step,spring+lower,HALF+.8f,1,upper-lower+1.1f,1.2f);
                        block(matrices,queue,Blocks.QUARTZ_BRICKS,x+14-step,spring+lower,HALF+.8f,1,upper-lower+1.1f,1.2f);
                    }
                    block(matrices,queue,Blocks.CHISELED_QUARTZ_BLOCK,x+7,spring+11.1f,HALF+.55f,2,2,1.6f);
                }
            }
            // Cornice follows the rising wall top, leaving the entire sky aperture open.
            if (s.wall>8) {
                block(matrices,queue,Blocks.QUARTZ_BRICKS,-HALF,s.wall-3,HALF+.2f,HALF*2,1,4);
                block(matrices,queue,Blocks.SMOOTH_QUARTZ,-HALF,s.wall-1,HALF,HALF*2,1,5);
            }
            matrices.pop();
        }
    }
    private static void column(MatrixStack m,OrderedRenderCommandQueue q,float x,float floor,float height) {
        if (height<=0) return;
        block(m,q,Blocks.QUARTZ_PILLAR,x-1.4f,0,HALF+.55f,2.8f,height,2.8f);
        for (float offset:new float[]{-.85f,0,.85f})
            block(m,q,Blocks.SMOOTH_QUARTZ,x+offset-.16f,0,HALF+.3f,.32f,height,.5f);
        for (float y=12;y<height-6;y+=24)
            block(m,q,Blocks.CHISELED_QUARTZ_BLOCK,x-1.7f,y,HALF+.1f,3.4f,1.1f,3.4f);
        if (floor+5<height) {
            block(m,q,Blocks.POLISHED_ANDESITE,x-2.4f,floor,HALF,4.8f,1,4.5f);
            block(m,q,Blocks.QUARTZ_BRICKS,x-2.1f,floor+1,HALF+.05f,4.2f,1.2f,4);
            block(m,q,Blocks.CHISELED_QUARTZ_BLOCK,x-1.8f,floor+2.2f,HALF+.1f,3.6f,1.8f,3.6f);
        }
        if (height>8) {
            block(m,q,Blocks.CHISELED_QUARTZ_BLOCK,x-1.8f,height-6,HALF+.1f,3.6f,2,3.6f);
            block(m,q,Blocks.QUARTZ_BRICKS,x-2.1f,height-4,HALF,4.2f,1.3f,4.2f);
            block(m,q,Blocks.SMOOTH_QUARTZ,x-2.5f,height-2.7f,HALF-.05f,5,1.2f,4.7f);
        }
    }
    private static void block(MatrixStack m, OrderedRenderCommandQueue q, Block block,
                              float x,float y,float z,float w,float h,float d) {
        if (w<=0 || h<=0 || d<=0) return;
        m.push(); m.translate(x,y,z); m.scale(w,h,d);
        q.submitBlock(m,block.getDefaultState(),0x00F000F0,OverlayTexture.DEFAULT_UV,0);
        m.pop();
    }
}
