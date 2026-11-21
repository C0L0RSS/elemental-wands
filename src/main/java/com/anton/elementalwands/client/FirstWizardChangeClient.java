package com.anton.elementalwands.client;

import com.anton.elementalwands.end.EndIntroTrack;
import com.anton.elementalwands.entity.firstwizard.FirstWizardChangeScene;
import com.anton.elementalwands.entity.firstwizard.FirstWizardChangeTrack;
import com.anton.elementalwands.entity.firstwizard.spell.PhaseChange;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Plays the First Wizard's change to phase two on this client, for a watcher ({@link FirstWizardChangeScene}): flies the
 * camera along the baked track ({@link FirstWizardChangeTrack}) in the copy's frame on the change's clock, letterboxes
 * the screen and hides the HUD, and lets the player hold Sneak to skip, as the First Wizard's intro does. The server
 * holds the player still and says when it ends; this only keeps inputs quiet. The body itself is drawn from the copy's
 * synced clock by FirstWizardRenderer, for everyone.
 */
public final class FirstWizardChangeClient {
    private static final int SKIP_HOLD = 16, HAND_BACK = 8;
    private static final double BARS = .11;

    private static Vec3d centre;
    private static float frame;
    private static long start, skippedAt;
    private static int hold;
    private static boolean active, skipped;
    private static ClientWorld world;

    private FirstWizardChangeClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(FirstWizardChangeScene.ChangePayload.ID, (payload, context) -> {
            if (!payload.active()) { active = false; return; }
            if (!active || start != payload.start()) { skipped = false; hold = 0; }
            centre = payload.centre();
            frame = payload.yaw();
            start = payload.start();
            world = context.client().world;
            active = true;
        });
        ClientTickEvents.START_CLIENT_TICK.register(FirstWizardChangeClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> active = false);
    }

    private static EndIntroTrack.Scene scene() { return FirstWizardChangeTrack.get().scene(); }

    private static void tick(MinecraftClient client) {
        if (!active) return;
        if (client.world == null || client.world != world || client.player == null || time(0) > PhaseChange.CINEMATIC + 60) { active = false; return; }
        // Clicks during the scene would swing or cast at nothing once it hands back.
        while (client.options.attackKey.wasPressed()) {}
        while (client.options.useKey.wasPressed()) {}
        client.options.attackKey.setPressed(false);
        client.options.useKey.setPressed(false);
        if (skipped || time(0) < 0) return;
        hold = client.options.sneakKey.isPressed() ? hold + 1 : Math.max(0, hold - 2);
        if (hold >= SKIP_HOLD) {
            skipped = true;
            skippedAt = client.world.getTime();
            ClientPlayNetworking.send(FirstWizardChangeScene.SkipPayload.INSTANCE);
        }
    }

    private static double time(float tickDelta) {
        var w = MinecraftClient.getInstance().world;
        return w == null ? -1 : w.getTime() - start + tickDelta;
    }

    private static double smooth(double s) {
        s = MathHelper.clamp(s, 0, 1);
        return s * s * (3 - 2 * s);
    }

    /** The player is held by the change: no walking, jumping or sneaking. */
    public static boolean frozen() { return active; }

    /** 0 while the scene owns the camera, rising to 1 as it hands the view back. */
    private static double handBack(float tickDelta) {
        if (!active) return 1;
        var w = MinecraftClient.getInstance().world;
        if (skipped && w != null) return smooth((w.getTime() - skippedAt + tickDelta) / HAND_BACK);
        return smooth((time(tickDelta) - PhaseChange.RETURN) / (double) (PhaseChange.CINEMATIC - PhaseChange.RETURN));
    }

    /** The scene owns the camera: HUD, hand, bobbing and block outline stay hidden. */
    public static boolean cinematic() { return active && handBack(0) < 1; }

    /** Where the camera looks this frame, or null when the player's own view applies. */
    public static BossIntroCamera.Pose pose(float tickDelta) {
        var player = MinecraftClient.getInstance().player;
        if (!active || player == null) return null;
        double back = handBack(tickDelta);
        // The last of the glide would pass through the player's own head: their eyes take over just before it.
        if (back >= .94) return null;
        EndIntroTrack.Scene scene = scene();
        double t = MathHelper.clamp(time(tickDelta), 0, scene.length());
        BossIntroCamera.Pose shot = new BossIntroCamera.Pose(FirstWizardChangeTrack.place(centre, frame, scene.eye(t)), scene.yaw(t) + frame, scene.pitch(t));
        if (back <= 0) return shot;
        return new BossIntroCamera.Pose(shot.pos().lerp(player.getCameraPosVec(tickDelta), back),
                MathHelper.lerpAngleDegrees((float) back, shot.yaw(), player.getYaw(tickDelta)),
                MathHelper.lerp((float) back, shot.pitch(), player.getPitch(tickDelta)));
    }

    public static float fov(float fov, float tickDelta) {
        if (!active) return fov;
        EndIntroTrack.Scene scene = scene();
        return (float) MathHelper.lerp(handBack(tickDelta), scene.fov(MathHelper.clamp(time(tickDelta), 0, scene.length())), fov);
    }

    /** The letterbox and the skip prompt; drawn in place of the HUD while the scene owns the view. */
    public static void drawCinematic(DrawContext context, RenderTickCounter counter) {
        var client = MinecraftClient.getInstance();
        float delta = counter.getTickProgress(false);
        double t = Math.max(0, time(delta)), back = handBack(delta);
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        int bar = (int) Math.round(h * BARS * smooth(t / 8) * (1 - back));
        context.fill(0, 0, w, bar, 0xFF000000);
        context.fill(0, h - bar, w, h, 0xFF000000);
        if (!skipped && t > 20 && back <= 0) {
            Text hint = Text.translatable("guardian.elementalwands.intro_skip", client.options.sneakKey.getBoundKeyLocalizedText());
            int x = w - client.textRenderer.getWidth(hint) - 10, y = h - Math.max(bar, 0) - 16;
            // Shown for a few seconds, then out of the picture; it comes back while Sneak is held.
            double shown = hold > 0 ? .8 : smooth((t - 20) / 16) * (1 - smooth((t - 110) / 30)) * .6;
            context.drawText(client.textRenderer, hint, x, y, alpha(shown) | 0xFFFFFF, true);
            if (hold > 0) context.fill(x, y + 10, x + client.textRenderer.getWidth(hint) * Math.min(hold, SKIP_HOLD) / SKIP_HOLD, y + 11, alpha(shown) | 0x9A4DFF);
        }
    }

    /** After skipping alone, the player gets their view back while the others still watch. */
    public static void drawWaiting(DrawContext context) {
        if (!active || !skipped || cinematic()) return;
        var client = MinecraftClient.getInstance();
        Text text = Text.translatable("guardian.elementalwands.intro_waiting");
        context.drawCenteredTextWithShadow(client.textRenderer, text, context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 3, 0xCCD9C2FF);
    }

    /** For fixtures: this client watches the change, and whether it has skipped. */
    public static boolean watching() { return active; }
    public static boolean skipped() { return skipped; }

    private static int alpha(double a) { return (int) Math.round(MathHelper.clamp(a, 0, 1) * 255) << 24; }
}
