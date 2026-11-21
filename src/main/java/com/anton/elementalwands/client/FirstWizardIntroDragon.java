package com.anton.elementalwands.client;

import com.anton.elementalwands.end.EndIntroTrack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * The intro's Ender Dragon: the vanilla entity and its vanilla renderer, but never in any world. Each client makes its
 * own, places it from the track every tick (where it is, which way it faces, its wing beat, when it is struck and how
 * far its death has run) and draws it straight into the world, so it has no AI, hurts nobody, breaks nothing, has no
 * boss bar and leaves nothing behind. Its neck and tail follow its path as the vanilla dragon's do, from the history
 * of where it has been. The track adds a nose pitch and a roll the vanilla model does not have, so it can bank, climb
 * to breathe up into the pool, and writhe in the stream that kills it. Its death is vanilla's rays and dissolve.
 */
public final class FirstWizardIntroDragon {
    private static EnderDragonEntity dragon;
    private static ClientWorld made;
    private static double shownAt = -1;

    private FirstWizardIntroDragon() {}

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(FirstWizardIntroDragon::tick);
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            if (dragon == null) return;
            var client = MinecraftClient.getInstance();
            var camera = context.worldState().cameraRenderState;
            if (camera == null || camera.pos == null) return;
            draw(client, context.matrices(), context.commandQueue(), camera, client.getRenderTickCounter().getTickProgress(false));
        });
    }

    /** For fixtures: the dragon is in the scene on this client now. */
    public static boolean shown() { return dragon != null; }
    /** For fixtures: where it is, or null. */
    public static Vec3d position() { return dragon == null ? null : dragon.getEntityPos(); }

    private static void tick(MinecraftClient client) {
        EndIntroTrack.Scene scene = FirstWizardIntroClient.scene();
        double t = FirstWizardIntroClient.sceneTime(0);
        if (scene == null || !scene.full() || client.world == null || t < 0 || scene.value("shown", t + 1) < .5) { dragon = null; shownAt = -1; return; }
        if (dragon == null || made != client.world) {
            dragon = new EnderDragonEntity(EntityType.ENDER_DRAGON, client.world);
            dragon.setSilent(true);
            made = client.world;
            shownAt = -1;
        }
        // Its last sample is the scene's now and its current one a tick on, so a frame between ticks lands on the scene's own clock.
        double now = Math.floor(t);
        Vec3d at = EndIntroTrack.world(scene.point("dragon", now)), next = EndIntroTrack.world(scene.point("dragon", now + 1));
        boolean fresh = shownAt < 0;
        shownAt = now;
        dragon.setPosition(at.x, at.y, at.z);
        dragon.resetPosition();
        dragon.setPosition(next.x, next.y, next.z);
        float yawNow = scene.value("d_yaw", now), yawNext = scene.value("d_yaw", now + 1);
        dragon.lastYaw = yawNow;
        dragon.setYaw(yawNext);
        dragon.lastWingPosition = scene.value("wing", now);
        dragon.wingPosition = scene.value("wing", now + 1);
        dragon.hurtTime = Math.round(scene.value("hurt", now + 1));
        dragon.ticksSinceDeath = Math.round(scene.value("death", now + 1));
        if (fresh) for (int i = 0; i < 12; i++) dragon.frameTracker.tick(at.y, yawNow);
        dragon.frameTracker.tick(next.y, yawNext);
        dragon.age++;
    }

    private static void draw(MinecraftClient client, net.minecraft.client.util.math.MatrixStack matrices,
            net.minecraft.client.render.command.OrderedRenderCommandQueue queue, net.minecraft.client.render.state.CameraRenderState camera, float delta) {
        EndIntroTrack.Scene scene = FirstWizardIntroClient.scene();
        double t = FirstWizardIntroClient.sceneTime(delta);
        if (scene == null || t < 0) return;
        var manager = client.getEntityRenderDispatcher();
        EntityRenderState state = manager.getAndUpdateRenderState(dragon, delta);
        // Lit to read against the End's dark sky, as the boss rule asks of anything the players must follow.
        state.light = LightmapTextureManager.pack(13, 15);
        state.shadowRadius = 0;
        state.shadowPieces.clear();
        state.displayName = null;
        Vec3d at = dragon.getLerpedPos(delta);
        double yaw = Math.toRadians(MathHelper.lerp(delta, dragon.lastYaw, dragon.getYaw()));
        // Its nose pitch is about its right-hand side, its roll about where it faces (vanilla: it faces sin yaw, -cos yaw).
        float pitch = (float) Math.toRadians(scene.value("d_pitch", t)), roll = (float) Math.toRadians(scene.value("d_roll", t));
        float fx = (float) Math.sin(yaw), fz = (float) -Math.cos(yaw);
        matrices.push();
        matrices.translate(at.x - camera.pos.x, at.y - camera.pos.y, at.z - camera.pos.z);
        // Turned round the middle of its body, not its feet.
        matrices.translate(0, 1.5, 0);
        matrices.multiply(new Quaternionf().rotateAxis(roll, fx, 0, fz).rotateAxis(pitch, -fz, 0, fx));
        matrices.translate(0, -1.5, 0);
        manager.render(state, camera, 0, 0, 0, matrices, queue);
        matrices.pop();
    }
}
