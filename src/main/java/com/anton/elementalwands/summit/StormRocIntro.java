package com.anton.elementalwands.summit;

import com.anton.elementalwands.network.ModNetworking;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Storm Roc's intro, on the server. Each called player's client plays the scene by itself
 * (client.StormRocIntroClient, from {@link StormRocIntroTrack}): snatched from the gong, carried up
 * into the storm cloud, flung onto the summit. The server sets them at the gong's stand, holds them
 * there still and unhurt, carries them into the sky realm once their client says its screen is
 * inside the cloud (it never shows the loading screen: the cloud shot waits for the summit), holds
 * them at the arrival, and tells {@link StormAerie} when each has landed or skipped so the fight
 * can start. The scene's clock is the client's, since it pauses in the cloud for as long as loading
 * takes; the server only needs its beats.
 */
public final class StormRocIntro {
    /**
     * Game ticks from the start by which a client should have reported its screen inside the cloud;
     * one that hasn't by then (lost, frozen or far behind) is left at the aerie, out of the call: it is
     * never moved while its screen might still show the aerie.
     */
    public static final int CLOUD_LATE = StormRocIntroTrack.get().beat("cloud") + 400;
    /** The scene's own length. */
    public static final int LENGTH = StormRocIntroTrack.get().length();
    /** How long past its length a scene may run (a slow load, a lost client) before the fight starts anyway. */
    public static final int GRACE = 600;

    private static final Map<UUID, Watcher> WATCHING = new HashMap<>();

    /** One player's scene: where they are held, in which world, and how far they have got. */
    private static final class Watcher {
        Vec3d lock;
        RegistryKey<World> world;
        float yaw, pitch;
        boolean inCloud, carried, landed, skipped;
        Watcher(Vec3d lock, RegistryKey<World> world, float yaw, float pitch) { this.lock = lock; this.world = world; this.yaw = yaw; this.pitch = pitch; }
    }

    private StormRocIntro() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof PlayerEntity
                && WATCHING.containsKey(entity.getUuid()) && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)));
        // Nor can they act: no hits, item use or block use while they can't be hurt back.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, side) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHING.clear());
        // Disconnects are StormAerie's: it reads whether the player was carried up before forgetting them.
    }

    /** Held by the scene: unhurt, can't cast, act or move. */
    public static boolean watching(PlayerEntity player) { return WATCHING.containsKey(player.getUuid()); }

    /**
     * Starts the scene for these players at the gong: each stands where the striker stood, facing
     * the gong (the aerie half of the scene is laid out from there), and is told the summit's slot.
     */
    static void begin(ServerWorld world, StormAerie.Gong gong, BlockPos centre, Collection<ServerPlayerEntity> players) {
        StormSummitManager.Point stand = StormAerie.stand(world, gong);
        Vec3d at = new Vec3d(stand.x(), stand.y(), stand.z());
        float pitch = StormRocIntroTrack.get().pitch(0);
        for (ServerPlayerEntity p : players) {
            StormSummitManager.transfer(p, world, at, stand.yaw(), pitch);
            WATCHING.put(p.getUuid(), new Watcher(at, world.getRegistryKey(), stand.yaw(), pitch));
            ServerPlayNetworking.send(p, new ModNetworking.StormRocIntroPayload(true, at, stand.yaw(), centre));
        }
    }

    /**
     * Carries a player into the sky realm at the arrival, already facing where the scene ends:
     * looking up at the Roc on its home spire, so handing the view back moves nothing.
     */
    static void carryUp(ServerPlayerEntity player, ServerWorld realm, BlockPos centre) {
        Watcher w = WATCHING.get(player.getUuid());
        if (w == null || w.carried) return;
        StormRocIntroTrack track = StormRocIntroTrack.get();
        Vec3d arrival = StormSummit.arrival(centre);
        w.yaw = track.yaw(track.length());
        w.pitch = track.pitch(track.length());
        StormSummitManager.transfer(player, realm, arrival, w.yaw, w.pitch);
        w.lock = arrival;
        w.world = realm.getRegistryKey();
        w.carried = true;
    }

    /**
     * Their client says its screen is covered by the storm cloud (it covers it on a skip too, and
     * only then arms skipping the loading screen): they may be carried up unseen.
     */
    static boolean readyToCarry(UUID id) { Watcher w = WATCHING.get(id); return w != null && w.inCloud; }

    /** The client's scene has entered the cloud: from now on the move can't be seen. */
    public static void inCloud(ServerPlayerEntity player) {
        Watcher w = WATCHING.get(player.getUuid());
        if (w != null) w.inCloud = true;
    }

    /** Has been carried up to the summit. */
    static boolean carried(UUID id) { Watcher w = WATCHING.get(id); return w != null && w.carried; }

    /**
     * Their scene is over: on the summit, with the summit loaded on their client and the view handed
     * back (a skip reports it too, once loaded), so the fight never starts on an empty screen.
     */
    static boolean finished(UUID id) { Watcher w = WATCHING.get(id); return w == null || w.carried && w.landed; }

    /** The client held Sneak through the prompt. */
    public static void skip(ServerPlayerEntity player) {
        Watcher w = WATCHING.get(player.getUuid());
        if (w != null) w.skipped = true;
    }

    /** The client's scene has ended on the summit (played through or skipped): they are on their feet facing the Roc. */
    public static void landed(ServerPlayerEntity player) {
        Watcher w = WATCHING.get(player.getUuid());
        if (w != null && w.carried) w.landed = true;
    }

    /** Ends the scene for this player: the fight is starting, or they have left the call (or died). */
    static void end(ServerPlayerEntity player) {
        if (WATCHING.remove(player.getUuid()) != null && player.networkHandler != null && !player.isDisconnected())
            ServerPlayNetworking.send(player, new ModNetworking.StormRocIntroPayload(false, Vec3d.ZERO, 0, BlockPos.ORIGIN));
    }

    /** Forgets a player who died or disconnected; the call drops them. */
    static void forget(UUID id) { WATCHING.remove(id); }

    /** Every tick: watchers stay exactly where the scene left them, unmoving. */
    static void hold(MinecraftServer server) {
        for (Map.Entry<UUID, Watcher> e : WATCHING.entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            Watcher w = e.getValue();
            if (p == null || p.getEntityWorld().getRegistryKey() != w.world) continue;
            if (p.getEntityPos().squaredDistanceTo(w.lock) > .01)
                p.networkHandler.requestTeleport(w.lock.x, w.lock.y, w.lock.z, w.yaw, w.pitch);
            p.setVelocity(Vec3d.ZERO);
            p.fallDistance = 0;
            // A bow drawn before the scene began is let down, not loosed.
            if (p.isUsingItem()) p.clearActiveItem();
        }
    }
}
