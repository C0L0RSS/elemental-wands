package com.anton.elementalwands.arena;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import static com.anton.elementalwands.arena.GuardianArenaRules.*;

public final class GuardianArenaContractTest {
    public static void run() throws Exception {
        var crowd=java.util.stream.IntStream.range(0,40).mapToObj(i -> new net.minecraft.util.math.Vec3d(0,0,1+i*.1)).toList();
        var seats=GuardianArenaRules.liftSeats(crowd,net.minecraft.util.math.Vec3d.ZERO);
        for(int i=0;i<seats.size();i++) {
            require(seats.get(i).horizontalLength()>=9.999,"A lift seat overlaps the Guardian/camera");
            for(int j=0;j<i;j++)require(seats.get(i).squaredDistanceTo(seats.get(j))>=8.999,"Two players share a lift position");
        }

        UUID first=UUID.randomUUID(),second=UUID.randomUUID(),outsider=UUID.randomUUID();
        var roster=new GuardianArenaRoster(List.of(first,second));
        require(!roster.alive(outsider),"Late arrival joined a sealed party");
        require(roster.eliminate(first) && !roster.eliminate(first),"Death must eliminate once");
        require(roster.enrolled(first) && !roster.alive(first),"A respawned UUID can rejoin");
        require(!roster.wiped(),"One casualty incorrectly wipes the surviving party");
        roster.eliminate(second); require(roster.wiped(),"All disconnected/dead players must reset the encounter");

        int cx=-137,cz=229,floor=104,top=319;
        var unique=new HashSet<BlockPos>();
        int floorCount=0;
        for (int i=0;i<constructionCount(floor,top);i++) {
            BlockPos p=constructionPosition(cx,cz,floor,top,i);
            require(unique.add(p),"Construction revisits a block and falsely reports obstruction");
            if (p.getY()==floor) floorCount++;
            else require(wall(p.getX()-cx,p.getZ()-cz),"A roof or interior obstacle was generated");
            require(p.getY()>=floor && p.getY()<=top,"Construction leaves its recoverable sky prism");
        }
        require(floorCount==16384,"Arena does not have exactly 128 by 128 walkable floor blocks");
        require(!unique.contains(new BlockPos(cx,top,cz)),"Open sky was sealed by a ceiling");
        for (int x=cx-64;x<cx+64;x++) for (int z=cz-64;z<cz+64;z++)
            require(unique.contains(new BlockPos(x,floor,z)),"Floor contains a hole");

        for (Vec3d escape:List.of(new Vec3d(cx+100,floor+1,cz),new Vec3d(cx,floor-20,cz),
                new Vec3d(cx,top+200,cz),new Vec3d(Double.NaN,0,0),new Vec3d(cx,200,Double.POSITIVE_INFINITY))) {
            require(!contains(cx,cz,floor+1,top-2,escape,.4),"Escape destination accepted");
            require(contains(cx,cz,floor+1,top-2,clamp(cx,cz,floor+1,top-2,escape,1),.4),"Boundary correction is itself outside");
        }
        require(contains(cx,cz,floor+1,top-2,new Vec3d(cx+30,floor+50,cz-30),.4),"Valid aerial Wind/Space movement was blocked");
        require(ease(-5)==0 && ease(9)==1 && ease(.5)==.5,"Ascent can overshoot its endpoints");

        // Floor and rider use the same two tick samples, rather than independent network lerps.
        for (int tick=95;tick<275;tick++) for (float delta:new float[]{0,.25f,.5f,.9f,1}) {
            double rider=GuardianArenaMotion.renderedHeight(65,145,100,160,tick,delta);
            double floorVisual=64+GuardianArenaMotion.renderedHeight(1,81,100,160,tick,delta);
            require(Math.abs(rider-floorVisual)<.00001,"Lift passenger sinks into the rendered floor");
            require(rider>=65 && rider<=145,"Lift overshoots the floor handoff");
        }

        var directory=Files.createTempDirectory("guardian-arena-journal-");
        var file=directory.resolve("arena.json");
        var journal=new GuardianArenaJournal(file);
        require(journal.read().arena==null,"Fresh world starts with a phantom arena");
        var state=new GuardianArenaJournal.State();
        var home=new GuardianArenaJournal.Point("minecraft:overworld",cx+.5,64,cz+.5,13,25);
        state.arena=new GuardianArenaJournal.Arena("minecraft:overworld",first.toString(),cx,cz,64,floor,top,home,home,false,false,false,16,List.of(12L));
        state.returns.put(second.toString(),home); state.gameModes.put(second.toString(),"adventure"); journal.write(state);
        var loaded=journal.read();
        require(loaded.arena.equals(state.arena) && loaded.returns.equals(state.returns) && loaded.gameModes.equals(state.gameModes),"Restart loses guardian, chunk ownership, or player recovery data");
        loaded.arena=null; journal.write(loaded);
        require(journal.read().returns.containsKey(second.toString()),"Cleanup discards an offline player's return point");
        Files.writeString(file,"{\"version\":999}");
        boolean rejected=false;
        try { journal.read(); } catch (java.io.IOException e) { rejected=true; }
        require(rejected,"Unknown receipt version may destructively sweep the wrong bounds");
        Files.delete(file); Files.delete(directory);
        System.out.println("Guardian arena checks passed: sealed party, permanent elimination, full floor, roofless walls, escape limits, atomic recovery roundtrip and offline returns.");
    }
    private static void require(boolean result,String message) { if (!result) throw new AssertionError(message); }
}
