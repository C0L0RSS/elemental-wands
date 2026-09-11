package com.anton.elementalwands.church;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.math.BlockBox;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;

/** Scores unmodified terrain, sharing height samples across nearby placement attempts. */
public final class GuardianChurchPlacement {
    private GuardianChurchPlacement() {}
    public static final class Survey {
        private final Structure.Context context;
        private final Map<Long,Integer> heights=new HashMap<>();
        private final Map<Long,Boolean> supports=new HashMap<>();
        public Survey(Structure.Context context) { this.context=context; }
        public int height(int x,int z) {
            return heights.computeIfAbsent(net.minecraft.util.math.ChunkPos.toLong(x,z),key ->
                context.chunkGenerator().getHeight(x,z,Heightmap.Type.WORLD_SURFACE_WG,context.world(),context.noiseConfig())-1);
        }
        public int floor(BlockBox box) {
            int[] values=new int[9];int i=0;
            for(int x:new int[]{box.getMinX(),(box.getMinX()+box.getMaxX())/2,box.getMaxX()})
                for(int z:new int[]{box.getMinZ(),(box.getMinZ()+box.getMaxZ())/2,box.getMaxZ()}) values[i++]=height(x,z);
            java.util.Arrays.sort(values);return values[4];
        }
        public boolean validBiome(net.minecraft.util.math.BlockPos pos) {
            return context.biomePredicate().test(context.biomeSource().getBiome(
                    net.minecraft.world.biome.source.BiomeCoords.fromBlock(pos.getX()),
                    net.minecraft.world.biome.source.BiomeCoords.fromBlock(pos.getY()),
                    net.minecraft.world.biome.source.BiomeCoords.fromBlock(pos.getZ()),
                    context.noiseConfig().getMultiNoiseSampler()));
        }
        /** Negative means reject; lower valid scores require less earthwork. */
        public long score(BlockBox box) {
            return score(box,Long.MAX_VALUE);
        }
        public long score(BlockBox box,long bestScore) {
            int floor=box.getMinY()+3;
            if(floor<context.world().getBottomY()+4 || floor>180) return -1;
            int left=box.getMinX()-4,right=box.getMaxX()+4,front=box.getMinZ()-4,back=box.getMaxZ()+4;
            long score=0;int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
            for(int x=left;x<=right;x+=2)for(int z=front;z<=back;z+=2) {
                int surface=height(x,z),delta=surface-floor;
                if(Math.abs(delta)>3) return -1;
                min=Math.min(min,surface);max=Math.max(max,surface);
                if(max-min>6) return -1;
                // Cutting high ground is costlier than a small supported foundation.
                score+=(long)delta*delta*(delta>0?3:1);
                // All remaining costs are nonnegative; this patch cannot win.
                if(score>=bestScore)return -1;
            }
            // Full noise columns are expensive. Only inspect support once the entire
            // height grid passes, and share the result between overlapping attempts.
            for(int x=left;x<=right;x+=4)for(int z=front;z<=back;z+=4)
                if(!supported(x,z))return -1;
            return score;
        }
        private boolean supported(int x,int z) {
            return supports.computeIfAbsent(net.minecraft.util.math.ChunkPos.toLong(x,z),key -> {
                var column=context.chunkGenerator().getColumnSample(x,z,context.world(),context.noiseConfig());
                int surface=height(x,z);
                for(int y=surface;y>=surface-4;y--) {
                    var state=column.getState(y);
                    if(state.isAir() || !state.getFluidState().isEmpty())return false;
                }
                return true;
            });
        }
    }
    public static boolean suitable(Structure.Context context,BlockBox box) { return new Survey(context).score(box)>=0; }
}
