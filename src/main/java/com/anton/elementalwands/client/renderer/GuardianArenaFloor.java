package com.anton.elementalwands.client.renderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Atlases;
import static com.anton.elementalwands.arena.GuardianArenaRules.HALF;

/** One batched top surface, with one vanilla texture repetition per Minecraft block. */
final class GuardianArenaFloor {
    private static final Identifier[] TEXTURES={
            Identifier.ofVanilla("block/quartz_block_bottom"),
            Identifier.ofVanilla("block/polished_deepslate"),
            Identifier.ofVanilla("block/polished_andesite")
    };
    private GuardianArenaFloor() {}

    static void submit(MatrixStack matrices,OrderedRenderCommandQueue queue,float height,float radius) {
        submit(matrices, queue, height, radius, java.util.Set.of());
    }

    static void submit(MatrixStack matrices, OrderedRenderCommandQueue queue, float height, float radius,
            java.util.Set<Long> spellTiles) {
        if (radius<=0) return;
        // Resolve sprites per submission so resource-pack reloads cannot leave stale atlas UVs.
        // AtlasManager indexes definitions (minecraft:blocks), not GPU texture paths.
        // The render layer below still needs the texture path, not the definition ID.
        var atlas=MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(Atlases.BLOCKS);
        Sprite[] sprites={atlas.getSprite(TEXTURES[0]),atlas.getSprite(TEXTURES[1]),atlas.getSprite(TEXTURES[2])};
        float extent=radius>=HALF-.001f?HALF+5:radius;
        int minimum=(int)Math.floor(-extent),maximum=(int)Math.ceil(extent);
        queue.submitCustom(matrices,RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE),(entry,vertices) -> {
            for (int x=minimum;x<maximum;x++) for (int z=minimum;z<maximum;z++) {
                if (spellTiles.contains(net.minecraft.util.math.ChunkPos.toLong(x,z))) continue;
                float left=Math.max(x,-extent),right=Math.min(x+1,extent);
                float near=Math.max(z,-extent),far=Math.min(z+1,extent);
                if (right<=left || far<=near) continue;
                boolean border=x<-HALF+8 || x>=HALF-8 || z<-HALF+8 || z>=HALF-8;
                boolean aisle=(x>=-8 && x<8) || (z>=-8 && z<8);
                Sprite sprite=sprites[border?2:aisle?1:0];
                float u0=uv(sprite.getMinU(),sprite.getMaxU(),left-x);
                float u1=uv(sprite.getMinU(),sprite.getMaxU(),right-x);
                float v0=uv(sprite.getMinV(),sprite.getMaxV(),near-z);
                float v1=uv(sprite.getMinV(),sprite.getMaxV(),far-z);
                // Clip the UVs with the expanding edge, instead of stretching or sliding tiles.
                vertex(vertices,entry,left,height,near,u0,v0);
                vertex(vertices,entry,left,height,far,u0,v1);
                vertex(vertices,entry,right,height,far,u1,v1);
                vertex(vertices,entry,right,height,near,u1,v0);
            }
        });
    }
    private static float uv(float minimum,float maximum,float fraction) { return minimum+(maximum-minimum)*fraction; }
    private static void vertex(VertexConsumer vertices,MatrixStack.Entry entry,float x,float y,float z,float u,float v) {
        vertices.vertex(entry,x,y,z).color(0xFFFFFFFF).texture(u,v)
                .overlay(OverlayTexture.DEFAULT_UV).light(0x00F000F0).normal(entry,0,1,0);
    }
}
