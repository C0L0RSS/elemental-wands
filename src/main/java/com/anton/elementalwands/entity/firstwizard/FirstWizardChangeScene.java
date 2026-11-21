package com.anton.elementalwands.entity.firstwizard;

import com.anton.elementalwands.ElementalWandsMod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * The cinematic that opens the change to phase two (the developer's request, Nov 19): the fight stops for everyone in
 * it while the copy's arm falls off, her flank swells and bursts, and the tentacles come out. {@link
 * com.anton.elementalwands.entity.firstwizard.spell.PhaseChange} owns it and runs it on the change's clock; this holds
 * its watchers, as the intros hold theirs ({@code end/EndIntro}, the Necromancer's transformation).
 *
 * <ul>
 *   <li>Everyone in the fight watches: their camera is the scene's (client/FirstWizardChangeClient flies it along the
 *       baked track, {@link FirstWizardChangeTrack}), their body is held where it stood (a player caught in the air
 *       sinks to the ground, never a fall), they cannot be hurt, and they cannot hit, cast, or use anything. Only
 *       damage that bypasses invulnerability ({@code /kill}, the void) still reaches them.</li>
 *   <li>A player who joins the fight while it plays (a late party member, a player who walks onto a summoned copy's
 *       floor) is taken in and watches from where it has got to. One who leaves it (logged out, dead, gone to another
 *       world, out of the fight) is let go at once, and a leaver never rejoins still held.</li>
 *   <li>Holding Sneak skips it for that player, who gets their view back while the others watch; it ends early only
 *       once every watcher has skipped, no sooner than {@link #SKIP_AFTER} ticks in, as the intros do.</li>
 *   <li>It ends at the change's {@code CINEMATIC} tick; stopped, the copy removed or the server stopping, it lets
 *       everyone go first.</li>
 * </ul>
 */
public final class FirstWizardChangeScene {
    /** A unanimous skip ends the scene no sooner than this, so a key still held from before cannot. */
    public static final int SKIP_AFTER = 20;
    private static final Map<UUID, FirstWizardChangeScene> WATCHING = new HashMap<>();

    /** This player watches the change of the copy with this id (or is let go: {@code active} false). */
    public record ChangePayload(int copy, long start, float yaw, Vec3d centre, boolean active) implements CustomPayload {
        public static final Id<ChangePayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID, "first_wizard_change"));
        public static final PacketCodec<RegistryByteBuf, ChangePayload> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, ChangePayload::copy, PacketCodecs.VAR_LONG, ChangePayload::start, PacketCodecs.FLOAT, ChangePayload::yaw,
                Vec3d.PACKET_CODEC, ChangePayload::centre, PacketCodecs.BOOLEAN, ChangePayload::active, ChangePayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Held Sneak: this watcher skips the change's cinematic. */
    public record SkipPayload() implements CustomPayload {
        public static final SkipPayload INSTANCE = new SkipPayload();
        public static final Id<SkipPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID, "first_wizard_change_skip"));
        public static final PacketCodec<RegistryByteBuf, SkipPayload> CODEC = PacketCodec.unit(INSTANCE);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private final FirstWizardEntity copy;
    private final ServerWorld world;
    private final Vec3d centre;
    private final float yaw;
    private final long start;
    /** Each watcher and where they are held. */
    private final Map<UUID, Vec3d> watchers = new LinkedHashMap<>();
    private final Set<UUID> skipped = new HashSet<>();
    private int clock = -1;
    private boolean over;

    /** The scene of this copy's change, whose tick 0 is the world tick {@code start}, framed on {@code centre} facing {@code yaw}. */
    public FirstWizardChangeScene(FirstWizardEntity copy, ServerWorld world, long start, float yaw, Vec3d centre) {
        this.copy = copy;
        this.world = world;
        this.start = start;
        this.yaw = yaw;
        this.centre = centre;
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(ChangePayload.ID, ChangePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SkipPayload.ID, SkipPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SkipPayload.ID, (payload, context) -> skip(context.player()));
        // Watchers cannot be hurt while their camera is elsewhere; only /kill and the void still reach them.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(entity instanceof PlayerEntity p && watching(p)));
        // Nor can they act: no hits, item use or block use while they cannot be hurt back.
        AttackEntityCallback.EVENT.register((player, w, hand, entity, hit) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, w, hand, entity, hit) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, w, hand) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, w, hand, hit) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, w, hand, pos, side) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        // A leaver is let go at once, and must not rejoin still held.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUuid();
            FirstWizardChangeScene scene = WATCHING.remove(id);
            if (scene != null) scene.forget(id);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCHING.clear());
    }

    // ------------------------------------------------------------------ what others ask

    /** A player held by a change's cinematic: frozen, unhurt and unable to act. */
    public static boolean watching(PlayerEntity player) { return WATCHING.containsKey(player.getUuid()); }

    /** A watcher asked to skip; the scene ends early only once every watcher has. */
    public static void skip(ServerPlayerEntity player) {
        FirstWizardChangeScene scene = WATCHING.get(player.getUuid());
        if (scene != null) scene.skipped.add(player.getUuid());
    }

    /** Every watcher has skipped, and the scene has run long enough for that to be deliberate. */
    public boolean everyoneSkipped() {
        return clock >= SKIP_AFTER && !watchers.isEmpty() && skipped.containsAll(watchers.keySet());
    }

    /** For status and fixtures. */
    public int watcherCount() { return watchers.size(); }

    // ------------------------------------------------------------------ running it

    /**
     * Scene tick t: everyone fighting watches (one who has just joined is taken in where it has got to), anyone no
     * longer fighting is let go, and every watcher is held where they stand.
     */
    public void tick(int t, List<ServerPlayerEntity> fighting) {
        if (over) return;
        clock = Math.max(clock, t);
        for (var it = watchers.keySet().iterator(); it.hasNext();) {
            UUID id = it.next();
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
            if (player != null && fighting.contains(player)) continue;
            it.remove();
            skipped.remove(id);
            WATCHING.remove(id, this);
            if (player != null) send(player, false);
        }
        for (ServerPlayerEntity player : fighting) if (!watchers.containsKey(player.getUuid())) watch(player);
        hold();
    }

    private void watch(ServerPlayerEntity player) {
        FirstWizardChangeScene previous = WATCHING.put(player.getUuid(), this);
        if (previous != null && previous != this) previous.forget(player.getUuid());
        Vec3d at = player.getEntityPos(), body = copy.getEntityPos().add(0, 1.3, 0), to = body.subtract(player.getEyePos());
        // Facing the copy, so the camera's return lands on a view of it.
        float face = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float down = (float) Math.clamp(-Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z))), -60, 60);
        player.teleport(world, at.x, at.y, at.z, Set.of(), face, down, false);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        watchers.put(player.getUuid(), at);
        send(player, true);
    }

    /** Keeps every watcher where the scene found them; a player caught in the air sinks to the ground, and none of it is a fall. */
    private void hold() {
        for (var entry : watchers.entrySet()) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player == null) continue;
            Vec3d lock = entry.getValue(), at = player.getEntityPos();
            if (at.y < lock.y - .001) entry.setValue(lock = new Vec3d(lock.x, at.y, lock.z));
            double dx = at.x - lock.x, dz = at.z - lock.z;
            if (dx * dx + dz * dz > .0025 || at.y > lock.y + .05)
                player.teleport(world, lock.x, lock.y, lock.z, Set.of(), player.getYaw(), player.getPitch(), false);
            player.setVelocity(0, Math.min(0, player.getVelocity().y), 0);
            player.fallDistance = 0;
        }
    }

    /** Over (played out, skipped, cut short, or the copy gone): everyone is let go. Safe to call twice. */
    public void finish() {
        if (over) return;
        over = true;
        for (UUID id : watchers.keySet()) {
            WATCHING.remove(id, this);
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
            if (player != null) {
                player.fallDistance = 0;
                send(player, false);
            }
        }
        watchers.clear();
        skipped.clear();
    }

    private void forget(UUID id) {
        watchers.remove(id);
        skipped.remove(id);
    }

    private void send(ServerPlayerEntity player, boolean active) {
        if (!player.isRemoved() && ServerPlayNetworking.canSend(player, ChangePayload.ID))
            ServerPlayNetworking.send(player, new ChangePayload(copy.getId(), start, yaw, centre, active));
    }
}
