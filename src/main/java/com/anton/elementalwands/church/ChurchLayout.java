package com.anton.elementalwands.church;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

/** Same authored coordinates are used by worldgen, restoration, and the offline preview. */
public final class ChurchLayout {
    public static final int MIN_X=-16, MAX_X=16, MIN_Y=-5, MAX_Y=30, MIN_Z=-10, MAX_Z=42;
    public static final int COUNT=33*36*53;
    private static Map<BlockPos,BlockState> ruin,restored,legacyRuin,legacyRestored;
    private ChurchLayout() {}
    public static Map<BlockPos,BlockState> blocks(boolean whole) {
        if (ruin==null) { ruin=read("ruined"); restored=read("restored"); }
        return whole?restored:ruin;
    }
    private static Map<BlockPos,BlockState> read(String name) {
        try (var stream=ChurchLayout.class.getResourceAsStream("/data/elementalwands/church/"+name+".json")) {
            if (stream==null) throw new IOException("Missing church layout");
            Map<BlockPos,BlockState> out=new HashMap<>();
            for (var value:JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray()) {
                var a=value.getAsJsonArray(); var id=Identifier.of(a.get(3).getAsString());
                if (!Registries.BLOCK.containsId(id)) throw new IOException("Unknown church block: "+id);
                out.put(new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt()),Registries.BLOCK.get(id).getDefaultState());
            }
            return Map.copyOf(out);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    public static boolean naturalCourtyard(int x,int z) {
        return z<9 && Math.abs(x)>3 && !(x>=-5 && x<=4 && z>=-6 && z<=0);
    }
    public static BlockState state(boolean whole,BlockPos local,BlockRotation rotation,int version) {
        if(version>=2) return state(whole,local,rotation);
        if(legacyRuin==null) { legacyRuin=read("ruined_legacy");legacyRestored=read("restored_legacy"); }
        return (whole?legacyRestored:legacyRuin).getOrDefault(local,Blocks.AIR.getDefaultState()).rotate(rotation);
    }
    public static BlockPos local(int index) {
        return new BlockPos(MIN_X+index%33,MIN_Y+(index/33)%36,MIN_Z+index/(33*36));
    }
    public static BlockPos at(BlockPos anchor,BlockRotation rotation,BlockPos local) { return anchor.add(local.rotate(rotation)); }
    public static BlockState state(boolean whole,BlockPos local,BlockRotation rotation) { return blocks(whole).getOrDefault(local,Blocks.AIR.getDefaultState()).rotate(rotation); }
}
