package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.pyreking.PyreKingIntroTrack;
import com.anton.elementalwands.hall.PyreHall;
import com.anton.elementalwands.network.ModNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Plays the Pyre King's intro on this client: flies the camera along the scene's baked track
 * ({@link PyreKingIntroTrack}) on the server's clock, letterboxes the screen and hides the HUD,
 * keeps the hall dark until its fires catch, shows the title, and lets the player hold Sneak to
 * skip. His renderer asks here for the scene's time, so that it can draw the web of chains, his
 * cold blade and his dead eyes. The server holds the player still; this only keeps inputs quiet.
 */
public final class PyreKingIntroClient {
    private static final int SKIP_HOLD = 16, HAND_BACK = 8, TITLE_FADE = 14;
    private static final double BARS = .11, DARKEST = .72;
    private static BlockPos centre = BlockPos.ORIGIN;
    private static long start, skippedAt;
    private static boolean active, skipped;
    private static int hold, king = -1;

    private PyreKingIntroClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.PyreKingIntroPayload.ID, (payload, context) -> {
            if (!payload.active()) { active = false; return; }
            centre = payload.centre();
            start = payload.start();
            king = payload.kingId();
            active = true;
            skipped = false;
            hold = 0;
        });
        ClientTickEvents.START_CLIENT_TICK.register(PyreKingIntroClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> active = false);
    }

    private static PyreKingIntroTrack track() { return PyreKingIntroTrack.get(); }
    private static int length() { return track().length(); }
    private static int ret() { return track().beat("return"); }

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
            ClientPlayNetworking.send(ModNetworking.PyreKingIntroSkipPayload.INSTANCE);
        }
    }

    private static double time(float tickDelta) {
        var world = MinecraftClient.getInstance().world;
        return world == null ? -1 : world.getTime() - start + tickDelta;
    }

    private static double smooth(double s) {
        s = MathHelper.clamp(s, 0, 1);
        return s * s * (3 - 2 * s);
    }

    /** The player is held by the intro: no walking, jumping or sneaking. */
    public static boolean frozen() { return active; }

    /** 0 while the scene owns the camera, rising to 1 as it hands the view back. */
    private static double handBack(float tickDelta) {
        if (!active) return 1;
        var world = MinecraftClient.getInstance().world;
        if (skipped && world != null) return smooth((world.getTime() - skippedAt + tickDelta) / HAND_BACK);
        return smooth((time(tickDelta) - ret()) / (length() - ret()));
    }

    /** The scene owns the camera: HUD, hand, bobbing and block outline stay hidden. */
    public static boolean cinematic() { return active && handBack(0) < 1; }

    // ------------------------------------------------------------------ what his renderer asks

    /** The scene's tick for this king, or -1 when no intro of his plays on this client. */
    public static double sceneTime(int kingId, float tickDelta) {
        if (!active || kingId != king) return -1;
        return MathHelper.clamp(time(tickDelta), 0, length());
    }

    /** A point of the track (hall cells) in the world. */
    public static Vec3d place(Vec3d cell) { return new Vec3d(centre.getX() + cell.x, PyreHall.SURFACE_Y + cell.y, centre.getZ() + cell.z); }

    /** The height of the stone under a point of the hall: the fight floor, or the throne's dais. */
    public static double floor(double x, double z) {
        boolean dais = z - centre.getZ() < -45 && Math.abs(x - centre.getX() - .5) <= 9.5;
        return PyreHall.SURFACE_Y + (dais ? 7 : 1);
    }

    /** The hall's light under the scene: 1 as normal, 0 at its darkest. A player who skipped alone gets the light back. */
    public static float arenaLight(float tickDelta) {
        if (!active) return 1;
        // Never black: the web and the statue must read against the dark.
        return (float) MathHelper.lerp(handBack(tickDelta), 1 - DARKEST * track().dark(Math.max(0, time(tickDelta))), 1);
    }

    // ------------------------------------------------------------------ the camera

    /** Where the camera looks this frame, or null when the player's own view applies. */
    public static BossIntroCamera.Pose pose(float tickDelta) {
        var player = MinecraftClient.getInstance().player;
        if (!active || player == null) return null;
        double back = handBack(tickDelta);
        // The last of the glide would pass through the player's own head: their eyes take over just before it.
        if (back >= .94) return null;
        double t = MathHelper.clamp(time(tickDelta), 0, length());
        BossIntroCamera.Pose shot = new BossIntroCamera.Pose(place(track().eye(t)), track().yaw(t), track().pitch(t));
        if (back <= 0) return shot;
        return new BossIntroCamera.Pose(shot.pos().lerp(player.getCameraPosVec(tickDelta), back),
                MathHelper.lerpAngleDegrees((float) back, shot.yaw(), player.getYaw(tickDelta)),
                MathHelper.lerp((float) back, shot.pitch(), player.getPitch(tickDelta)));
    }

    public static float fov(float fov, float tickDelta) {
        if (!active) return fov;
        return (float) MathHelper.lerp(handBack(tickDelta), track().fov(MathHelper.clamp(time(tickDelta), 0, length())), fov);
    }

    /** Letterbox, the fade in from black, title and skip prompt; drawn in place of the HUD while the scene owns the view. */
    public static void drawCinematic(DrawContext context, RenderTickCounter counter) {
        var client = MinecraftClient.getInstance();
        float delta = counter.getTickProgress(false);
        double t = MathHelper.clamp(time(delta), 0, length()), back = handBack(delta);
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        int bar = (int) Math.round(h * BARS * (1 - back));
        context.fill(0, 0, w, bar, 0xFF000000);
        context.fill(0, h - bar, w, h, 0xFF000000);
        double dark = track().black(t) * (1 - back);
        if (dark > 0) context.fill(0, 0, w, h, alpha(dark));
        int from = track().beat("title"), to = track().beat("title_end");
        double enter = smooth((t - from) / TITLE_FADE);
        double title = enter * (1 - smooth((t - (to - TITLE_FADE)) / TITLE_FADE)) * (1 - back);
        if (title > 0) {
            String upper = Text.translatable("entity.elementalwands.pyre_king").getString().toUpperCase(java.util.Locale.ROOT);
            int width = client.textRenderer.getWidth(upper);
            float scale = Math.min(2.6f, (w - 48f) / Math.max(1, width));
            // Low in the frame, above the letterbox, so it never covers his head.
            float centreY = h * .76f + (float) (12 * (1 - enter));
            int halfCard = (int) Math.ceil(width * scale / 2) + 12;
            int cardTop = (int) Math.floor(centreY - 4 * scale) - 9, cardBottom = (int) Math.ceil(centreY + (client.textRenderer.fontHeight - 4) * scale) + 10;
            context.fill(w / 2 - halfCard, cardTop, w / 2 + halfCard, cardBottom, alpha(title * .9));
            var m = context.getMatrices();
            m.pushMatrix();
            m.translate(w / 2f, centreY);
            m.scale(scale, scale);
            context.drawText(client.textRenderer, upper, -width / 2, -4, alpha(title) | 0xFFD9A0, true);
            m.popMatrix();
            int line = (int) (width * scale / 2 * smooth((t - from) / 20));
            context.fill(w / 2 - line, cardBottom - 6, w / 2 + line, cardBottom - 5, alpha(title * .85) | 0xFF7A1E);
        }
        if (!skipped && t > 30 && back <= 0) {
            Text hint = Text.translatable("guardian.elementalwands.intro_skip", client.options.sneakKey.getBoundKeyLocalizedText());
            int x = w - client.textRenderer.getWidth(hint) - 10, y = h - Math.max(bar, 0) - 16;
            // Shown for a few seconds, then out of the picture; it comes back while Sneak is held.
            double shown = hold > 0 ? .8 : smooth((t - 30) / 20) * (1 - smooth((t - 150) / 40)) * .6;
            context.drawText(client.textRenderer, hint, x, y, alpha(shown) | 0xFFFFFF, true);
            if (hold > 0) context.fill(x, y + 10, x + client.textRenderer.getWidth(hint) * Math.min(hold, SKIP_HOLD) / SKIP_HOLD, y + 11, alpha(shown) | 0xFF7A1E);
        }
    }

    /** After skipping alone, the player gets their view back while the others still watch. */
    public static void drawWaiting(DrawContext context) {
        if (!active || !skipped || cinematic()) return;
        var client = MinecraftClient.getInstance();
        Text text = Text.translatable("guardian.elementalwands.intro_waiting");
        context.drawCenteredTextWithShadow(client.textRenderer, text, context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 3, 0xCCFFC890);
    }

    private static int alpha(double a) { return (int) Math.round(MathHelper.clamp(a, 0, 1) * 255) << 24; }
}
