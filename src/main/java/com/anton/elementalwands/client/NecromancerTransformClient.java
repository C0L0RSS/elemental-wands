package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.necromancer.NecromancerTransformScene;
import com.anton.elementalwands.entity.necromancer.NecromancerTransformTrack;
import com.anton.elementalwands.network.ModNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Plays the Necromancer's transformation on this client: flies the camera along the scene's baked
 * track ({@link NecromancerTransformTrack}) on the server's clock, letterboxes the screen and hides
 * the HUD, flashes soul-white, drains the crypt's light while the braziers are out, hides his hood
 * while the camera looks out of his eyes, and lets the player hold Sneak to skip. The server holds
 * the player still; this only keeps inputs quiet.
 */
public final class NecromancerTransformClient {
    private static final int SKIP_HOLD = 16, HAND_BACK = 8;
    private static final double BARS = .11;
    private static Vec3d centre;
    private static float frame;
    private static long start, skippedAt;
    private static int boss = -1, hold;
    private static boolean active, skipped;

    private NecromancerTransformClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.NecromancerTransformPayload.ID, (payload, context) -> {
            if (!payload.active()) { active = false; return; }
            boss = payload.bossId();
            centre = payload.centre();
            frame = payload.yaw();
            start = payload.start();
            active = true;
            skipped = false;
            hold = 0;
        });
        ClientTickEvents.START_CLIENT_TICK.register(NecromancerTransformClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> active = false);
    }

    private static NecromancerTransformTrack track() { return NecromancerTransformTrack.get(); }
    private static int length() { return track().length(); }
    private static int handover() { return track().beat("ret"); }

    private static void tick(MinecraftClient client) {
        if (!active) return;
        if (client.world == null || client.player == null || time(0) > length() + 60) { active = false; return; }
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
            // The Necromancer's two scenes share one skip; the server counts it for this one.
            ClientPlayNetworking.send(ModNetworking.NecromancerIntroSkipPayload.INSTANCE);
        }
    }

    private static double time(float tickDelta) {
        var world = MinecraftClient.getInstance().world;
        return world == null ? -1 : world.getTime() - start + tickDelta;
    }

    /** The player is held by the transformation: no walking, jumping or sneaking. */
    public static boolean frozen() { return active; }

    /** 0 while the scene owns the camera, rising to 1 as it hands the view back. */
    private static double handBack(float tickDelta) {
        if (!active) return 1;
        var world = MinecraftClient.getInstance().world;
        if (skipped && world != null) return smooth((world.getTime() - skippedAt + tickDelta) / HAND_BACK);
        return smooth((time(tickDelta) - handover()) / (double)(length() - handover()));
    }

    /** The scene owns the camera: HUD, hand, bobbing and block outline stay hidden. */
    public static boolean cinematic() { return active && handBack(0) < 1; }

    /** Where the camera looks this frame, or null when the player's own view applies. */
    public static BossIntroCamera.Pose pose(float tickDelta) {
        var player = MinecraftClient.getInstance().player;
        if (!active || player == null) return null;
        double back = handBack(tickDelta);
        if (back >= 1) return null;
        double t = Math.max(0, time(tickDelta));
        BossIntroCamera.Pose shot = new BossIntroCamera.Pose(NecromancerTransformScene.place(centre, frame, track().eye(t)),
                track().yaw(t) + frame, track().pitch(t));
        if (back <= 0) return shot;
        return new BossIntroCamera.Pose(shot.pos().lerp(player.getCameraPosVec(tickDelta), back),
                MathHelper.lerpAngleDegrees((float)back, shot.yaw(), player.getYaw(tickDelta)),
                MathHelper.lerp((float)back, shot.pitch(), player.getPitch(tickDelta)));
    }

    public static float fov(float fov, float tickDelta) {
        if (!active) return fov;
        return (float)MathHelper.lerp(handBack(tickDelta), track().fov(Math.max(0, time(tickDelta))), fov);
    }

    /** The crypt's light while the braziers are out: 1 as normal, 0 at its darkest. */
    public static float arenaLight(float tickDelta) {
        var world = MinecraftClient.getInstance().world;
        if (!active || skipped || world == null || world.getRegistryKey() != com.anton.elementalwands.crypt.HollowCryptRealm.WORLD) return 1;
        return track().dark(Math.max(0, time(tickDelta)));
    }

    /** His hood is hidden on this client while the camera looks out of his eyes from inside it. */
    public static boolean hidesHood(int entityId) {
        return active && !skipped && entityId == boss && handBack(0) <= 0 && track().pov(Math.max(0, time(0)));
    }

    /** Letterbox, fade-in, the soul-white flashes and the skip prompt; drawn in place of the HUD while the scene owns the view. */
    public static void drawCinematic(DrawContext context, RenderTickCounter counter) {
        var client = MinecraftClient.getInstance();
        float delta = counter.getTickProgress(false);
        double t = Math.max(0, time(delta)), back = handBack(delta);
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        double white = track().flash(t) * (1 - back);
        if (white > .01) context.fill(0, 0, w, h, alpha(white) | 0xDDFBFF);
        int bar = (int)Math.round(h * BARS * smooth(t / 10) * (1 - back));
        context.fill(0, 0, w, bar, 0xFF000000);
        context.fill(0, h - bar, w, h, 0xFF000000);
        double dark = track().black(t) * (1 - back);
        if (dark > 0) context.fill(0, 0, w, h, alpha(dark));
        if (!skipped && t > 10 && back <= 0) {
            Text hint = Text.translatable("necromancer.elementalwands.intro_skip", client.options.sneakKey.getBoundKeyLocalizedText());
            int x = w - client.textRenderer.getWidth(hint) - 10, y = h - Math.max(bar, 0) - 16;
            double shown = smooth((t - 10) / 10);
            context.drawText(client.textRenderer, hint, x, y, alpha(shown * .75) | 0xFFFFFF, true);
            if (hold > 0) context.fill(x, y + 10, x + client.textRenderer.getWidth(hint) * Math.min(hold, SKIP_HOLD) / SKIP_HOLD, y + 11, alpha(shown) | 0x6AF2FF);
        }
    }

    /** After skipping alone, the player gets their view back while the others still watch. */
    public static void drawWaiting(DrawContext context) {
        if (!active || !skipped || cinematic()) return;
        var client = MinecraftClient.getInstance();
        Text text = Text.translatable("necromancer.elementalwands.intro_waiting");
        context.drawCenteredTextWithShadow(client.textRenderer, text, context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 3, 0xCC9FEFFF);
    }

    private static double smooth(double s) {
        s = MathHelper.clamp(s, 0, 1);
        return s * s * (3 - 2 * s);
    }

    private static int alpha(double a) { return (int)Math.round(MathHelper.clamp(a, 0, 1) * 255) << 24; }
}
