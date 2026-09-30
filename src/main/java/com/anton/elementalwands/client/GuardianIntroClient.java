package com.anton.elementalwands.client;

import static com.anton.elementalwands.entity.GuardianIntro.*;

import com.anton.elementalwands.entity.GuardianIntroTrack;
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
 * Plays the Guardian's intro on this client: flies the camera along the scene's baked track
 * ({@link GuardianIntroTrack}) on the server's clock, letterboxes the screen and hides the HUD,
 * whites out as the fists land, shows the title, and lets the player hold Sneak to skip. The
 * server holds the player still; this only keeps inputs quiet.
 */
public final class GuardianIntroClient {
    private static final int SKIP_HOLD = 16, HAND_BACK = 8, TITLE_FADE = 10;
    private static final double BARS = .11;
    private static Vec3d centre;
    private static float frame;
    private static long start, skippedAt;
    private static boolean active, skipped;
    private static int hold;

    private GuardianIntroClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.GuardianIntroPayload.ID, (payload, context) -> {
            if (!payload.active()) { active = false; return; }
            centre = payload.centre();
            frame = payload.yaw();
            start = payload.start();
            active = true;
            skipped = false;
            hold = 0;
        });
        ClientTickEvents.START_CLIENT_TICK.register(GuardianIntroClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> active = false);
    }

    private static void tick(MinecraftClient client) {
        if (!active) return;
        if (client.world == null || client.player == null || time(0) > LENGTH + 60) { active = false; return; }
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
            ClientPlayNetworking.send(ModNetworking.GuardianIntroSkipPayload.INSTANCE);
        }
    }

    private static double time(float tickDelta) {
        var world = MinecraftClient.getInstance().world;
        return world == null ? -1 : world.getTime() - start + tickDelta;
    }

    /** The player is held by the intro: no walking, jumping or sneaking. */
    public static boolean frozen() { return active; }

    /** 0 while the scene owns the camera, rising to 1 as it hands the view back. */
    private static double handBack(float tickDelta) {
        if (!active) return 1;
        var world = MinecraftClient.getInstance().world;
        if (skipped && world != null) return smooth((world.getTime() - skippedAt + tickDelta) / HAND_BACK);
        return smooth((time(tickDelta) - RETURN) / (LENGTH - RETURN));
    }

    /** The scene owns the camera: HUD, hand, bobbing and block outline stay hidden. */
    public static boolean cinematic() { return active && handBack(0) < 1; }

    /** Where the camera looks this frame, or null when the player's own view applies. */
    public static BossIntroCamera.Pose pose(float tickDelta) {
        var player = MinecraftClient.getInstance().player;
        if (!active || player == null) return null;
        double back = handBack(tickDelta);
        if (back >= 1) return null;
        BossIntroCamera.Pose shot = shot(Math.max(0, time(tickDelta)));
        if (back <= 0) return shot;
        return new BossIntroCamera.Pose(shot.pos().lerp(player.getCameraPosVec(tickDelta), back),
                MathHelper.lerpAngleDegrees((float)back, shot.yaw(), player.getYaw(tickDelta)),
                MathHelper.lerp((float)back, shot.pitch(), player.getPitch(tickDelta)));
    }

    public static float fov(float fov, float tickDelta) {
        if (!active) return fov;
        return (float)MathHelper.lerp(handBack(tickDelta), GuardianIntroTrack.get().fov(Math.max(0, time(tickDelta))), fov);
    }

    /**
     * The shot at scene time t, off the track, turned into the world from the Guardian's frame. The
     * smash's jolt is in the track's path; nothing else shakes the camera.
     */
    static BossIntroCamera.Pose shot(double t) {
        GuardianIntroTrack track = GuardianIntroTrack.get();
        return new BossIntroCamera.Pose(place(centre, frame, track.eye(t)), track.yaw(t) + frame, track.pitch(t));
    }

    /** Letterbox, fade-in, the white-out, title and skip prompt; drawn in place of the HUD while the scene owns the view. */
    public static void drawCinematic(DrawContext context, RenderTickCounter counter) {
        var client = MinecraftClient.getInstance();
        GuardianIntroTrack track = GuardianIntroTrack.get();
        float delta = counter.getTickProgress(false);
        double t = Math.max(0, time(delta)), back = handBack(delta);
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        double white = track.flash(t) * (1 - back);
        if (white > .01) context.fill(0, 0, w, h, alpha(white) | 0xF4FFFF);
        int bar = (int)Math.round(h * BARS * smooth(t / 10) * (1 - back));
        context.fill(0, 0, w, bar, 0xFF000000);
        context.fill(0, h - bar, w, h, 0xFF000000);
        double dark = track.black(t);
        if (dark > 0) context.fill(0, 0, w, h, alpha(dark) | 0x000000);
        double titleEnter = smooth((t - TITLE) / TITLE_FADE);
        double title = titleEnter * (1 - smooth((t - (TITLE_END - TITLE_FADE)) / TITLE_FADE)) * (1 - back);
        if (title > 0) {
            Text name = Text.translatable("entity.elementalwands.fractured_guardian");
            String upper = name.getString().toUpperCase(java.util.Locale.ROOT);
            int width = client.textRenderer.getWidth(upper);
            float scale = Math.min(2.6f, (w - 48f) / Math.max(1, width));
            float centreY = h * .7f + (float)(16 * (1 - titleEnter));
            int halfCard = (int)Math.ceil(width * scale / 2) + 12;
            int cardTop = (int)Math.floor(centreY - 4 * scale) - 9;
            int cardBottom = (int)Math.ceil(centreY + (client.textRenderer.fontHeight - 4) * scale) + 10;
            // The padded black card and title slide upward together, matching the Necromancer's reveal.
            context.fill(w / 2 - halfCard, cardTop, w / 2 + halfCard, cardBottom, alpha(title * .9));
            var m = context.getMatrices();
            m.pushMatrix();
            m.translate(w / 2f, centreY);
            m.scale(scale, scale);
            context.drawText(client.textRenderer, upper, -width / 2, -4, alpha(title) | 0xE4ECEC, true);
            m.popMatrix();
            int line = (int)(width * scale / 2 * smooth((t - TITLE) / 14));
            int lineY = cardBottom - 6;
            context.fill(w / 2 - line, lineY, w / 2 + line, lineY + 1, alpha(title * .8) | 0x6AF2FF);
        }
        if (!skipped && t > 10 && back <= 0) {
            Text hint = Text.translatable("guardian.elementalwands.intro_skip", client.options.sneakKey.getBoundKeyLocalizedText());
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
        Text text = Text.translatable("guardian.elementalwands.intro_waiting");
        context.drawCenteredTextWithShadow(client.textRenderer, text, context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 3, 0xCC9FEFFF);
    }

    private static int alpha(double a) { return (int)Math.round(MathHelper.clamp(a, 0, 1) * 255) << 24; }
}
