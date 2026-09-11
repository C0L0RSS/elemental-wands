package com.anton.elementalwands.church;

import java.nio.file.*;
import java.util.ArrayList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;

/** Exercises the real vanilla command without blocking entity ticks or subsequent commands. */
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
            var tag=net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.STRUCTURE,GuardianChurchLocator.ID);
            var expected=server.getOverworld().locateStructure(tag,new BlockPos(0,80,0),100,false);
            check(expected!=null && result.contains("["+expected.getX()+", ~, "+expected.getZ()+"]"),"Incremental result differs from vanilla: "+expected+" vs "+result);
            String report="LOCATE PASSED seed="+server.getOverworld().getSeed()+" elapsedMs="+(now-started)/1_000_000
                    +" tickingFrames="+(ticks-searchTick)+" maxTickGapMs="+maxGap/1_000_000+" pigAge="+pig.age+" "+result;
            Files.writeString(Path.of("LOCATE_PASSED.txt"),report+"\n");System.out.println(report);server.stop(false);
        }
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
