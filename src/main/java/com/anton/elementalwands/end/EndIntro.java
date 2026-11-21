package com.anton.elementalwands.end;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;

/**
 * The cinematic that opens the First Wizard's fight (the developer's decisions, Nov 17-18). The first time in a world
 * the Ender Dragon circles under the pool of ink over the island, breathes into it, and the pool sends the breath back
 * and kills it; then one edge of the pool comes down in front of the players and pours itself into the first player's
 * shape while the sky drains. On later attempts (the dragon's death is saved) only the condensing plays.
 *
 * <p>The server runs the clock, holds every watcher where they stand (unhurt, unable to cast or act), keeps the copy
 * on its spot and cannot-be-hurt, sets how far it has formed, plays the placeholder sounds and saves the dragon's
 * death. Each client draws the scene from the same baked track ({@link EndIntroTrack}): the camera, the pool, the
 * dragon (an actor drawn only on clients, so it can hurt nobody, break nothing and has no boss bar), the breath and
 * the pour. Everyone can skip; it ends early only once every watcher has. One fight at a time, so one scene.</p>
 *
 * <p>{@link EndEncounter} owns it: it starts it when it places the boss, adds late party members, starts the fight
 * when it ends, and cuts it short with the fight (a reset, a reload, the boss gone, the server stopping, nobody
 * left).</p>
 */
public final class EndIntro {
    /** A unanimous skip ends the scene no sooner than this, so a key still held from before cannot. */
    public static final int SKIP_AFTER = 20;
    private static EndIntro running;

    private final ServerWorld world;
    private final FirstWizardEntity copy;
    private final EndIntroTrack.Scene scene;
    private final Map<UUID, Vec3d> watchers = new LinkedHashMap<>();
    private final Set<UUID> skipped = new HashSet<>();
    private long start;
    private int clock = -1;

    private EndIntro(ServerWorld world, FirstWizardEntity copy, boolean full) {
        this.world = world;
        this.copy = copy;
        this.scene = EndIntroTrack.get().scene(full);
    }

    static void register() {
        // Watchers cannot be hurt while their camera is elsewhere, and nor can the copy before the fight; /kill still reaches both.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(entity instanceof PlayerEntity p && watching(p) || holds(entity)));
        // Nor can they act: no hits, item use or block use while they cannot be hurt back.
        AttackEntityCallback.EVENT.register((player, w, hand, entity, hit) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseEntityCallback.EVENT.register((player, w, hand, entity, hit) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, w, hand) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, w, hand, hit) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, w, hand, pos, side) -> watching(player) ? ActionResult.FAIL : ActionResult.PASS);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> running = null);
    }

    // ------------------------------------------------------------------ what others ask

    /** The scene now playing, or null. */
    public static EndIntro running() { return running; }
    /** A player held by the scene: frozen, unhurt and unable to cast. */
    public static boolean watching(PlayerEntity player) { return running != null && running.watchers.containsKey(player.getUuid()); }
    /** The copy the scene is forming: it cannot be hurt until the fight starts. */
    public static boolean holds(Entity entity) { return running != null && running.copy == entity; }

    /** A watcher asked to skip; the scene ends early only once every watcher has. */
    public static void skip(ServerPlayerEntity player) {
        if (watching(player)) running.skipped.add(player.getUuid());
    }

    public boolean full() { return scene.full(); }
    /** The world tick the scene's clock counts from (its tick 0). */
    public long start() { return start; }
    public int clock() { return clock; }
    public int length() { return scene.length(); }
    public FirstWizardEntity copy() { return copy; }
    int watcherCount() { return watchers.size(); }
    boolean watches(UUID id) { return watchers.containsKey(id); }

    // ------------------------------------------------------------------ starting it

    /**
     * The scene for these players, with the copy (already placed and passive) wearing the skin owner's skin from its
     * first boot. The copy is set on its spot, unformed.
     */
    static EndIntro begin(ServerWorld world, FirstWizardEntity copy, List<ServerPlayerEntity> players, ServerPlayerEntity owner, boolean full) {
        if (running != null) running.cancel();
        EndIntro intro = new EndIntro(world, copy, full);
        if (owner != null) copy.wear(owner);
        copy.setFormed(0);
        intro.placeCopy();
        intro.start = world.getTime() + 1;
        running = intro;
        for (ServerPlayerEntity p : players) intro.addWatcher(p);
        return intro;
    }

    /** A party member who arrived during the scene: held where they stand, and their client joins it where it has got to. */
    void addWatcher(ServerPlayerEntity player) {
        Vec3d at = player.getEntityPos();
        Vec3d form = EndIntroTrack.world(EndIntroTrack.get().form());
        float yaw = (float) Math.toDegrees(Math.atan2(-(form.x - at.x), form.z - at.z));
        // Facing where the copy forms, so the camera's return lands on a view of it.
        player.teleport(world, at.x, at.y, at.z, Set.of(), yaw, 0, false);
        player.setVelocity(Vec3d.ZERO);
        player.fallDistance = 0;
        watchers.put(player.getUuid(), at);
        EndSky.dirty();
    }

    private void placeCopy() {
        Vec3d form = EndIntroTrack.world(EndIntroTrack.get().form());
        // Facing south, toward the arrival.
        copy.setVelocity(Vec3d.ZERO);
        copy.refreshPositionAndAngles(form.x, form.y, form.z, 0, 0);
        copy.setBodyYaw(0);
        copy.setHeadYaw(0);
    }

    // ------------------------------------------------------------------ running it

    /** Runs one tick; false once the scene is over and the fight should start. */
    boolean tick() {
        int t = ++clock;
        if (t == 0) { start = world.getTime(); EndSky.dirty(); }
        holdWatchers();
        // An operator made it fight mid-scene: the scene gives way and the fight starts properly.
        if (copy.isBossAggressive() || t >= scene.length() || t >= SKIP_AFTER && !watchers.isEmpty() && everyoneSkipped()) {
            finish();
            return false;
        }
        if (copy.getEntityPos().squaredDistanceTo(EndIntroTrack.world(EndIntroTrack.get().form())) > .01) placeCopy();
        copy.setVelocity(Vec3d.ZERO);
        copy.setYaw(0); copy.setBodyYaw(0); copy.setHeadYaw(0);
        copy.setFormed(scene.value("formed", t));
        if (scene.full() && t == scene.beat("death")) EndArenaManager.setDragonDead(true);
        sounds(t);
        return true;
    }

    private void holdWatchers() {
        for (var it = watchers.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (p == null || p.isRemoved() || !p.isAlive() || p.getEntityWorld() != world) {
                it.remove();
                skipped.remove(entry.getKey());
                EndSky.dirty();
                continue;
            }
            Vec3d lock = entry.getValue();
            if (p.getEntityPos().squaredDistanceTo(lock) > .01) p.teleport(world, lock.x, lock.y, lock.z, Set.of(), p.getYaw(), p.getPitch(), false);
            p.setVelocity(Vec3d.ZERO);
            p.fallDistance = 0;
        }
    }

    private boolean everyoneSkipped() {
        for (UUID id : watchers.keySet()) if (!skipped.contains(id)) return false;
        return true;
    }

    /** Over (ran out, everyone skipped, or an operator started the fight): the copy is whole and everyone is let go. */
    private void finish() {
        if (scene.full() && clock >= scene.beat("death")) EndArenaManager.setDragonDead(true);
        release();
        placeCopy();
    }

    /** Cut short with the fight (a reset, a reload, the boss gone, the server stopping): everyone is let go. */
    void cancel() { release(); }

    private void release() {
        if (running == this) running = null;
        watchers.clear();
        skipped.clear();
        if (!copy.isRemoved()) copy.setFormed(1);
        EndSky.dirty();
    }

    // ------------------------------------------------------------------ placeholder sounds (sounds are parked for this boss)

    private void sounds(int t) {
        EndIntroTrack track = EndIntroTrack.get();
        Vec3d bloom = EndIntroTrack.world(track.bloom()), form = EndIntroTrack.world(track.form());
        if (scene.full()) {
            Vec3d dragon = scene.point("dragon", t);
            Vec3d at = dragon == null ? bloom : EndIntroTrack.world(dragon);
            if (t == 300 || t == scene.beat("hover") + 120) sound(at, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 6f, .9f);
            if (t == scene.beat("turn") + 30 || t == scene.beat("turn") + 60) sound(at, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 4f, .9f);
            if (t == scene.beat("breath")) sound(at, SoundEvents.ENTITY_ENDER_DRAGON_SHOOT, 6f, .8f);
            if (t == scene.beat("bloom") + 20) sound(bloom, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 4f, .5f);
            if (t == scene.beat("back")) {
                sound(bloom, SoundEvents.ENTITY_ENDER_DRAGON_SHOOT, 6f, .55f);
                sound(bloom, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), 5f, .6f);
            }
            for (int hit : scene.hits()) if (t == hit) sound(at, SoundEvents.ENTITY_ENDER_DRAGON_HURT, 6f, .8f + (hit % 7) * .03f);
            if (t == scene.beat("death")) sound(at, SoundEvents.ENTITY_ENDER_DRAGON_DEATH, 8f, 1f);
            if (t == scene.beat("burst")) sound(at, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 5f, .55f);
        }
        if (t == scene.beat("sag")) sound(form.add(0, 30, 0), SoundEvents.BLOCK_HONEY_BLOCK_SLIDE, 4f, .5f);
        if (t == scene.beat("touch")) {
            sound(form, SoundEvents.ITEM_BUCKET_EMPTY, 3f, .5f);
            sound(form, SoundEvents.BLOCK_SCULK_SPREAD, 3f, .6f);
        }
        int formAt = scene.beat("form");
        if (t > formAt && t < scene.beat("formed") && (t - formAt) % 40 == 0) sound(form, SoundEvents.BLOCK_SCULK_SPREAD, 2f, .5f + (t - formAt) / 800f);
        if (t == scene.beat("formed")) sound(form.add(0, 1, 0), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 2.5f, .6f);
        if (t == scene.beat("raise") + 10) sound(form.add(0, 1.6, 0), SoundEvents.ENTITY_ILLUSIONER_PREPARE_MIRROR, 2.5f, .7f);
    }

    private void sound(Vec3d at, SoundEvent sound, float volume, float pitch) {
        world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.HOSTILE, volume, pitch);
    }
}
