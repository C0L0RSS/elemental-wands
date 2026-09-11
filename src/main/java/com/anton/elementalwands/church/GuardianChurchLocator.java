package com.anton.elementalwands.church;

import com.anton.elementalwands.mixin.GuardianChurchChunkAccessor;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.text.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import net.minecraft.world.gen.chunk.placement.*;
import net.minecraft.world.gen.structure.Structure;
import org.slf4j.LoggerFactory;

/** One scheduled structure-start request at a time; never waits for terrain on the tick thread. */
public final class GuardianChurchLocator {
    public static final Identifier ID=Identifier.of("elementalwands","guardian_church");
    private static final ChunkTicketType SEARCH_TICKET=Registry.register(Registries.TICKET_TYPE,
            Identifier.of("elementalwands","church_search"),new ChunkTicketType(0,ChunkTicketType.FOR_LOADING));
    private static final int TICKET_RADIUS=ChunkLevels.getLevelFromType(ChunkLevelType.FULL)
            -ChunkLevels.getLevelFromStatus(ChunkStatus.STRUCTURE_STARTS);
    private static final long TIMEOUT_NANOS=120_000_000_000L;
    private static Search active;

    private GuardianChurchLocator() {}
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(GuardianChurchLocator::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> cancel());
    }
    public static boolean searching() { return active!=null; }
    public static String cancel() {
        if(active==null)return "No church search is running.";
        active.release();active=null;
        return "Church search cancelled.";
    }
    public static String start(ServerCommandSource source,int radius) {
        if(source.getWorld().getRegistryKey()!=World.OVERWORLD)return "Search for churches in the Overworld.";
        if(active!=null)return "A church search is already running ("+active.checked+" candidates checked). Use /ew guardian church cancel to stop it.";
        var registry=source.getWorld().getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
        var entry=registry.getOptional(RegistryKey.of(RegistryKeys.STRUCTURE,ID));
        if(entry.isEmpty())return "Church generation is unavailable in this world.";
        var placements=source.getWorld().getChunkManager().getStructurePlacementCalculator().getPlacements(entry.get());
        if(placements.isEmpty())return "No natural churches are configured for this world.";
        if(placements.stream().anyMatch(p -> !(p instanceof RandomSpreadStructurePlacement)))
            return "This world's church placement type is not supported by the incremental search.";
        active=new Search(source,entry.get(),placements.stream().map(p -> (RandomSpreadStructurePlacement)p).toList(),radius);
        return "Searching for a natural church while the world keeps running. Coordinates will appear here. Use /ew guardian church cancel to stop.";
    }
    private static void tick(MinecraftServer server) {
        if(active==null)return;
        var search=active;
        if(search.source.getServer()!=server)return;
        try {
            if(search.source.getEntity() instanceof ServerPlayerEntity player
                    && (server.getPlayerManager().getPlayer(player.getUuid())!=player || player.getEntityWorld()!=search.world)) {
                cancel();return;
            }
            if(System.nanoTime()-search.started>TIMEOUT_NANOS) {
                search.say("Church search stopped after two minutes. Try searching from another area.");cancel();return;
            }
            search.step();
        } catch(Exception e) {
            LoggerFactory.getLogger("elementalwands-church").error("Church search failed",e);
            search.say("Church search failed; see the server log.");cancel();
        }
    }
    private record Candidate(RandomSpreadStructurePlacement placement,ChunkPos pos) {}
    private static final class Search {
        final ServerCommandSource source;
        final ServerWorld world;
        final RegistryEntry<Structure> structure;
        final List<RandomSpreadStructurePlacement> placements;
        final StructurePlacementCalculator calculator;
        final BlockPos origin;
        final int radius;
        final long started=System.nanoTime();
        final ArrayDeque<Candidate> candidates=new ArrayDeque<>();
        int ring,checked;
        Candidate pending;
        CompletableFuture<OptionalChunk<Chunk>> future;
        Search(ServerCommandSource source,RegistryEntry<Structure> structure,List<RandomSpreadStructurePlacement> placements,int radius) {
            this.source=source;this.world=source.getWorld();this.structure=structure;this.placements=placements;this.radius=radius;
            origin=BlockPos.ofFloored(source.getPosition());calculator=world.getChunkManager().getStructurePlacementCalculator();
        }
        void say(String message) { source.sendFeedback(() -> Text.literal(message),false); }
        void release() {
            if(pending!=null)world.getChunkManager().removeTicket(SEARCH_TICKET,pending.pos(),TICKET_RADIUS);
            pending=null;future=null;
        }
        void step() {
            if(future!=null) {
                if(!future.isDone())return;
                var chunk=future.getNow(null).orElse(null);
                if(chunk==null)throw new IllegalStateException("Church search chunk unloaded before generation completed");
                var start=chunk.getStructureStart(structure.value());
                var pos=pending.placement().getLocatePos(pending.pos());
                release();checked++;
                if(start!=null && start.hasChildren()) {
                    var coordinates=Text.literal("["+pos.getX()+", ~, "+pos.getZ()+"]")
                            .styled(style -> style.withUnderline(true).withClickEvent(new ClickEvent.SuggestCommand("/tp @s "+pos.getX()+" ~ "+pos.getZ())));
                    source.sendFeedback(() -> Text.literal("Natural church: ").append(coordinates)
                            .append(" (at the surface; the socket may be nearby)."),false);
                    LoggerFactory.getLogger("elementalwands-church").info("Incremental church locate found {} after {} candidates in {} ms",pos,checked,(System.nanoTime()-started)/1_000_000);
                    active=null;return;
                }
            }
            if(candidates.isEmpty()) {
                if(ring>radius) {say("No church found in this search. Try another area.");active=null;return;}
                // Same expanding region perimeter and within-ring order as vanilla.
                for(var placement:placements)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++) {
                    if(Math.abs(dx)!=ring && Math.abs(dz)!=ring)continue;
                    var pos=placement.getStartChunk(calculator.getStructureSeed(),(origin.getX()>>4)+placement.getSpacing()*dx,
                            (origin.getZ()>>4)+placement.getSpacing()*dz);
                    candidates.add(new Candidate(placement,pos));
                }
                ring++;
            }
            // Bound even rejected/loaded candidates to one per tick. Terrain work uses
            // Minecraft's chunk workers, with a temporary non-simulating ticket.
            var candidate=candidates.removeFirst();
            if(!candidate.placement().shouldGenerate(calculator,candidate.pos().x,candidate.pos().z)) {checked++;return;}
            pending=candidate;
            var manager=world.getChunkManager();
            manager.addTicket(SEARCH_TICKET,pending.pos(),TICKET_RADIUS);
            future=((GuardianChurchChunkAccessor)manager).elementalwands$requestChunk(pending.pos().x,pending.pos().z,ChunkStatus.STRUCTURE_STARTS,true);
        }
    }
}
