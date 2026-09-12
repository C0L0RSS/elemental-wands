package com.anton.elementalwands.util;

import com.anton.elementalwands.registry.ModSpellBlocks;
import com.google.gson.Gson;
import net.minecraft.block.*;
import net.minecraft.util.math.Direction;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** One exported layout shared with the approved nine-block-high, eleven-block-wide workshop tree. */
public final class NatureTreeLayout {
    public record Cell(int x,int y,int z,String kind,float stage) {
        public int growthStage(){return kind.contains("leaves")?3:y==0?0:y<=3?1:2;}
        public BlockState state(){return switch(kind){
            case "heart" -> ModSpellBlocks.NATURE_HEARTWOOD.getDefaultState();
            case "bloom-leaves" -> ModSpellBlocks.NATURE_FLOWERING_LEAVES.getDefaultState();
            case "leaves" -> Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT,true);
            case "log-x" -> Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.X);
            case "log-z" -> Blocks.OAK_LOG.getDefaultState().with(PillarBlock.AXIS,Direction.Axis.Z);
            default -> Blocks.OAK_LOG.getDefaultState();
        };}
    }
    private static final List<Cell> CELLS=load();
    private static List<Cell> load(){
        var stream=NatureTreeLayout.class.getResourceAsStream("/assets/elementalwands/nature/tree_layout.json");
        if(stream==null)throw new IllegalStateException("Missing Nature tree layout");
        try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
            return List.of(new Gson().fromJson(reader,Cell[].class));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    public static List<Cell> cells(){return CELLS;}
    private NatureTreeLayout(){}
}
