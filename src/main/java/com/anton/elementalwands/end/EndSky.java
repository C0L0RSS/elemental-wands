package com.anton.elementalwands.end;

import com.anton.elementalwands.ElementalWandsMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * What the sky over the End's island holds, as each client in the End learns it: the pool of ink while the boss is
 * undefeated and no fight runs (before the first visit, and again after a wipe); the intro's scene while one plays
 * (the pool, the dragon and the pour are then drawn from the track, on the scene's clock); and nothing while a fight
 * runs (the pool became the copy) or after the win. A player sent this with {@code watching} is held by the scene and
 * its camera is the scene's.
 *
 * <p>The server sends each player in the End the state whenever it changes for them, and on their arrival.</p>
 */
public final class EndSky {
    private EndSky() {}

    public static final int GONE = 0, WAITING = 1, SCENE = 2;

    /** The sky's state for one player; {@code start}, {@code full}, {@code copy} and {@code watching} mean something only in a scene. */
    public record SkyPayload(int pool, long start, boolean full, int copy, boolean watching) implements CustomPayload {
        public static final Id<SkyPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID, "end_sky"));
        public static final PacketCodec<RegistryByteBuf, SkyPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, SkyPayload::pool, PacketCodecs.VAR_LONG, SkyPayload::start, PacketCodecs.BOOLEAN, SkyPayload::full,
                PacketCodecs.VAR_INT, SkyPayload::copy, PacketCodecs.BOOLEAN, SkyPayload::watching, SkyPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Held Sneak: this watcher skips the intro. */
    public record SkipPayload() implements CustomPayload {
        public static final SkipPayload INSTANCE = new SkipPayload();
        public static final Id<SkipPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID, "end_intro_skip"));
        public static final PacketCodec<RegistryByteBuf, SkipPayload> CODEC = PacketCodec.unit(INSTANCE);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private static final Map<UUID, SkyPayload> sent = new HashMap<>();
    private static boolean dirty;

    static void init() {
        PayloadTypeRegistry.playS2C().register(SkyPayload.ID, SkyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SkipPayload.ID, SkipPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SkipPayload.ID, (payload, context) -> EndIntro.skip(context.player()));
        ServerTickEvents.END_SERVER_TICK.register(EndSky::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> sent.remove(handler.getPlayer().getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> sent.clear());
        EndIntro.register();
    }

    /** Something changed: send again this tick. */
    static void dirty() { dirty = true; }

    /** What this player's sky holds now. */
    static SkyPayload state(ServerPlayerEntity player) {
        if (!EndArenaManager.available() || EndArenaManager.defeated()) return new SkyPayload(GONE, 0, false, -1, false);
        EndIntro intro = EndIntro.running();
        if (intro != null) return new SkyPayload(SCENE, intro.start(), intro.full(), intro.copy().getId(), intro.watches(player.getUuid()));
        // A fight starting (its boss not placed yet) still has the pool; a running one has none.
        return new SkyPayload(EndEncounter.fightPlaced() ? GONE : WAITING, 0, false, -1, false);
    }

    private static void tick(MinecraftServer server) {
        // Cheap: a handful of players in the End, compared each tick; anyone who left it is forgotten, so their return sends again.
        boolean any = dirty;
        dirty = false;
        sent.keySet().removeIf(id -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            return p == null || p.getEntityWorld().getRegistryKey() != EndArena.WORLD;
        });
        var end = server.getWorld(EndArena.WORLD);
        if (end == null) return;
        if (!any && server.getTicks() % 10 != 0 && end.getPlayers().stream().allMatch(p -> sent.containsKey(p.getUuid()))) return;
        for (ServerPlayerEntity p : end.getPlayers()) {
            SkyPayload now = state(p);
            if (now.equals(sent.get(p.getUuid())) || !ServerPlayNetworking.canSend(p, SkyPayload.ID)) continue;
            ServerPlayNetworking.send(p, now);
            sent.put(p.getUuid(), now);
        }
    }
}
