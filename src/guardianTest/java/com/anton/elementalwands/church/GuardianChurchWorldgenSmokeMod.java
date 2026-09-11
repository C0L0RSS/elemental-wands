package com.anton.elementalwands.church;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;

/** Fresh normal terrain: vanilla structure locator, full chunk generation, and rotated socket discovery. */
public final class GuardianChurchWorldgenSmokeMod implements ModInitializer {
    private int tick;
    private String savedKeeper;
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                if(++tick==25) {
                    var world=server.getOverworld();
                    if(!GuardianChurchManager.sites().isEmpty()) savedKeeper=GuardianChurchManager.sites().getFirst().guardian;
                    var generator=world.getChunkManager().getChunkGenerator();
                    var context=new net.minecraft.world.gen.structure.Structure.Context(world.getRegistryManager(),generator,generator.getBiomeSource(),world.getChunkManager().getNoiseConfig(),world.getStructureTemplateManager(),world.getSeed(),new net.minecraft.util.math.ChunkPos(-32,-35),world,biome -> true);
                    if(GuardianChurchPlacement.suitable(context,new net.minecraft.util.math.BlockBox(-528,99,-596,-496,134,-544)))throw new AssertionError("Known cliff-side footprint passed the placement filter");
                    var tag=TagKey.of(RegistryKeys.STRUCTURE,Identifier.of("elementalwands","guardian_church"));
                    var found=world.locateStructure(tag,new BlockPos(0,80,0),8,false);
                    if(found==null) throw new AssertionError("Vanilla locate did not find a naturally scheduled church");
                    System.out.println("NATURAL CHURCH LOCATED: "+found);
                    for(int x=(found.getX()>>4)-6;x<=(found.getX()>>4)+6;x++) for(int z=(found.getZ()>>4)-6;z<=(found.getZ()>>4)+6;z++){
                        var chunk=world.getChunk(x,z);world.setChunkForced(x,z,true);
                        for(var be:chunk.getBlockEntities().values()) if(be instanceof GuardianSocketEntity) GuardianChurchManager.discover(world,be.getPos(),be.getCachedState());
                    }
                    if(GuardianChurchManager.sites().isEmpty()) throw new AssertionError("Located structure produced no socket");
                }
                if(tick==100) {
                    var world=server.getOverworld();var s=GuardianChurchManager.sites().getFirst();
                    if(s.layoutVersion!=2 || !world.getBlockState(s.socket()).isOf(com.anton.elementalwands.registry.ModBlocks.GUARDIAN_SOCKET))throw new AssertionError("Natural socket lost its version or building origin");
                    int untouched=0;
                    for(int x:new int[]{-14,14})for(int z:new int[]{-6,-3}) {
                        var p=s.at(x,0,z);
                        int actual=world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ())-1;
                        if(!world.getBlockState(new BlockPos(p.getX(),actual,p.getZ())).isIn(net.minecraft.registry.tag.BlockTags.DIRT)) {System.out.println("COURTYARD SAMPLE: "+p+" top="+actual+" block="+world.getBlockState(new BlockPos(p.getX(),actual,p.getZ())));continue;}
                        int original=world.getChunkManager().getChunkGenerator().getHeight(p.getX(),p.getZ(),net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG,world,world.getChunkManager().getNoiseConfig())-1;
                        if(actual!=original)throw new AssertionError("Natural side courtyard was flattened: "+p+" original="+original+" actual="+actual);
                        untouched++;
                    }
                    if(untouched==0)throw new AssertionError("No unobstructed natural courtyard sample found");
                    if(!world.getEntitiesByClass(com.anton.elementalwands.entity.FracturedGuardianEntity.class,new net.minecraft.util.math.Box(s.anchor()).expand(12),e -> e.isAlive()).isEmpty())throw new AssertionError("Living guardian visible before the ritual");
                    if(!s.terrainBlended)throw new AssertionError("Natural ruin did not finish terrain blending");
                    var offering=world.getBlockEntity(s.offering());
                    if(!(offering instanceof net.minecraft.block.entity.ChestBlockEntity chest) || !chest.getStack(0).isOf(com.anton.elementalwands.registry.ModItems.GUARDIAN_HEART)) throw new AssertionError("Natural rotated church did not stock the offering chest");
                    if(world.getBlockState(s.at(0,-2,-5)).isAir())throw new AssertionError("Natural courtyard lacks its authored floor");
                    if(!world.getBlockState(s.at(0,2,5)).isAir())throw new AssertionError("Natural summoning area has an overhead obstruction");
                    Files.writeString(Path.of("WORLDGEN_PASSED.txt"),"Vanilla locate found a church in fresh normal terrain. Generated socket, rotation, floor, and stocked heart chest verified.\n"+s.anchor()+" rotation "+s.rotation);
                    System.out.println("NATURAL CHURCH PASSED: "+s.anchor()+" rotation "+s.rotation);server.stop(false);
                }
            }catch(Throwable e){e.printStackTrace();try{Files.writeString(Path.of("WORLDGEN_FAILED.txt"),e.toString());}catch(Exception ignored){}server.stop(false);}
        });
    }
}
