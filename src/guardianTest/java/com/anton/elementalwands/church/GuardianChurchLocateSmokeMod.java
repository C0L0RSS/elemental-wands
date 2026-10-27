package com.anton.elementalwands.church;

import com.anton.elementalwands.world.StructureArrival;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.regex.Pattern;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.gen.structure.Structure;

/** Exercises the real vanilla command without blocking entity ticks or subsequent commands, then stands on what it reports. */
public final class GuardianChurchLocateSmokeMod implements ModInitializer {
    private int ticks,searchTick;
    private long last,maxGap,started,worldTime;
    private ServerCommandSource source;
    private net.minecraft.entity.passive.PigEntity pig;
    private final ArrayList<String> messages=new ArrayList<>();
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try { tick(server); }
            catch(Throwable e) {
                e.printStackTrace();
                try {Files.writeString(Path.of("LOCATE_FAILED.txt"),e.toString());}catch(Exception ignored){}
                GuardianChurchLocator.cancel();server.stop(false);
            }
        });
    }
    private void command(MinecraftServer server,String command) {
        long before=System.nanoTime();
        server.getCommandManager().parseAndExecute(source,command);
        long millis=(System.nanoTime()-before)/1_000_000;
        check(millis<1000,"Command blocked for "+millis+" ms: "+command);
        System.out.println("LOCATE COMMAND RETURNED in "+millis+" ms: "+command);
    }
    private void tick(MinecraftServer server) throws Exception {
        ticks++;
        if(ticks==25) {
            source=server.getCommandSource().withPosition(new Vec3d(0,80,0)).withOutput(new CommandOutput() {
                public void sendMessage(Text text) {messages.add(text.getString());System.out.println("LOCATE FEEDBACK: "+text.getString());}
                public boolean shouldReceiveFeedback(){return true;}
                public boolean shouldTrackOutput(){return true;}
                public boolean shouldBroadcastConsoleToOps(){return false;}
            });
            var world=server.getOverworld();world.getChunk(0,0);world.setChunkForced(0,0,true);
            pig=new net.minecraft.entity.passive.PigEntity(net.minecraft.entity.EntityType.PIG,world);
            pig.setPosition(8,world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,8,8),8);
            pig.setAiDisabled(true);pig.setInvulnerable(true);world.spawnEntity(pig);
            // Cancel an in-flight request, then restart through the church alias.
            command(server,"locate structure elementalwands:guardian_church");
            check(GuardianChurchLocator.searching(),"Vanilla command did not start the incremental locator");
        }
        if(ticks==30) {
            command(server,"ew guardian church cancel");check(!GuardianChurchLocator.searching(),"Cancellation retained search");
            command(server,"ew guardian church locate");check(GuardianChurchLocator.searching(),"Church alias did not start search");
            command(server,"locate structure #elementalwands:guardian_church");
            check(messages.getLast().contains("already running"),"Duplicate/tag command started a second search");
            // Restart through the exact reported command for the measured run.
            command(server,"ew guardian church cancel");
            command(server,"locate structure elementalwands:guardian_church");
            searchTick=ticks;started=last=System.nanoTime();worldTime=server.getOverworld().getTime();
        }
        if(searchTick==0)return;
        long now=System.nanoTime();maxGap=Math.max(maxGap,now-last);last=now;
        check((now-started)<150_000_000_000L,"Search fixture timed out");
        if(ticks==searchTick+5) {
            command(server,"time query gametime");
            check(messages.getLast().contains("time"),"Follow-up command did not execute during search");
        }
        if(!GuardianChurchLocator.searching()) {
            String result=messages.stream().filter(s -> s.startsWith("Natural church:")).findFirst().orElseThrow(() -> new AssertionError("No located church: "+messages));
            check(ticks>searchTick+5,"Search did not exercise multiple ticks");
            check(server.getOverworld().getTime()>worldTime+5,"World time stopped");
            check(pig.age>5,"Mob ticks stopped");
            check(maxGap<1_500_000_000L,"Server tick stalled for "+maxGap/1_000_000+" ms");
            // Confirm the result agrees with vanilla on the same now-generated starts.
            var world=server.getOverworld();
            var tag=net.minecraft.registry.tag.TagKey.of(RegistryKeys.STRUCTURE,GuardianChurchLocator.ID);
            var expected=world.locateStructure(tag,new BlockPos(0,80,0),100,false);
            check(expected!=null,"Vanilla found no church");
            var church=structure(world,GuardianChurchLocator.ID);
            var arrival=StructureArrival.find(world,church,expected);
            check(arrival!=null && result.contains("["+arrival.getX()+", "+arrival.getY()+", "+arrival.getZ()+"]"),"Incremental result differs from vanilla: "+arrival+" vs "+result);
            standing(world,church,expected,arrival);
            // The graveyard goes through vanilla /locate, which reports a height for it too.
            server.getCommandManager().getDispatcher().execute("locate structure elementalwands:hollow_graveyard",source);
            var found=Pattern.compile("\\[(-?\\d+), (-?\\d+), (-?\\d+)]").matcher(messages.getLast());
            check(found.find(),"Graveyard locate reported no coordinates: "+messages.getLast());
            var gate=new BlockPos(Integer.parseInt(found.group(1)),Integer.parseInt(found.group(2)),Integer.parseInt(found.group(3)));
            var graveyardId=Identifier.of("elementalwands","hollow_graveyard");var graveyard=structure(world,graveyardId);
            var yard=world.locateStructure(net.minecraft.registry.tag.TagKey.of(RegistryKeys.STRUCTURE,graveyardId),new BlockPos(0,80,0),100,false);
            check(gate.equals(StructureArrival.find(world,graveyard,yard)),"Graveyard locate did not report its arrival: "+messages.getLast());
            standing(world,graveyard,yard,gate);
            String report="LOCATE PASSED seed="+world.getSeed()+" elapsedMs="+(now-started)/1_000_000
                    +" tickingFrames="+(ticks-searchTick)+" maxTickGapMs="+maxGap/1_000_000+" pigAge="+pig.age+" "+result
                    +" graveyard="+gate.toShortString();
            Files.writeString(Path.of("LOCATE_PASSED.txt"),report+"\n");System.out.println(report);server.stop(false);
        }
    }
    private static RegistryEntry<Structure> structure(ServerWorld world,Identifier id) {
        return world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE).getOrThrow(RegistryKey.of(RegistryKeys.STRUCTURE,id));
    }
    /** Builds the arrival's terrain and requires a clear two-block gap on a solid floor, at the height reported before it existed. */
    private static void standing(ServerWorld world,RegistryEntry<Structure> structure,BlockPos located,BlockPos arrival) {
        world.getChunk(arrival);
        for(var p:new BlockPos[]{arrival,arrival.up()})
            check(world.getBlockState(p).getCollisionShape(world,p).isEmpty() && world.getFluidState(p).isEmpty(),"Arrival "+arrival+" is blocked by "+world.getBlockState(p));
        check(world.getBlockState(arrival.down()).isSideSolidFullSquare(world,arrival.down(),Direction.UP),"Arrival "+arrival+" has no floor: "+world.getBlockState(arrival.down()));
        check(arrival.equals(StructureArrival.find(world,structure,located)),"Built ground disagrees with the template arrival "+arrival);
        System.out.println("LOCATE ARRIVAL STANDS: "+structure.getIdAsString()+" "+arrival.toShortString()+" on "+world.getBlockState(arrival.down()));
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
