package com.anton.elementalwands.arena;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Write-ahead recovery receipt, scoped to a world save. Never serializes live Minecraft objects. */
public final class GuardianArenaJournal {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public record Point(String dimension,double x,double y,double z,float yaw,float pitch) {}
    public record Arena(String dimension,String guardian,int x,int z,int base,int floor,int top,
                        Point guardianHome,Point waiting,boolean gravity,boolean invulnerable,boolean noAi,double followRange,
                        List<Long> forcedChunks) {}
    public static final class State {
        public int version=1;
        public Arena arena;
        public Map<String,Point> returns=new LinkedHashMap<>();
        public Map<String,String> gameModes=new LinkedHashMap<>();
    }
    private final Path path;
    public GuardianArenaJournal(Path path) { this.path=path; }
    public State read() throws IOException {
        if (!Files.exists(path)) return new State();
        try {
            State result=JSON.fromJson(Files.readString(path),State.class);
            if (result==null || result.version!=1 || result.returns==null) throw new IOException("Invalid arena recovery receipt");
            if (result.arena!=null && (result.arena.floor<=result.arena.base || result.arena.top-result.arena.floor<32
                    || result.arena.top-result.arena.base>4096 || result.arena.forcedChunks==null))
                throw new IOException("Invalid arena recovery bounds");
            if(result.gameModes==null) result.gameModes=new LinkedHashMap<>(); // Older receipts have no mode changes.
            return result;
        } catch (RuntimeException e) { throw new IOException("Cannot parse arena recovery receipt",e); }
    }
    public void write(State state) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        byte[] bytes=JSON.toJson(state).getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel=FileChannel.open(temporary,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            ByteBuffer buffer=ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
}
