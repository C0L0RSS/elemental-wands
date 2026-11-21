package com.anton.elementalwands.client;

import com.anton.elementalwands.end.EndArena;
import com.anton.elementalwands.end.EndIntroTrack;
import com.anton.elementalwands.end.EndSky;
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
 * This client's view of the sky over the End's island ({@link EndSky}) and, for a watcher, the First Wizard's intro:
 * flies the camera along the scene's baked track ({@link EndIntroTrack}) on the server's clock, letterboxes the screen
 * and hides the HUD, shows the name, and lets the player hold Sneak to skip. The pool, the dragon and the pour ask here
 * for the scene's time; so does the copy's forming look. The server holds the player still; this only keeps inputs quiet.
 */
public final class FirstWizardIntroClient {
    private static final int SKIP_HOLD = 16, HAND_BACK = 8, TITLE_FADE = 14;
    private static final double BARS = .11;
    /** Ticks for the pool to spread back over the sky when it returns (after a wipe). */
    private static final double SPREAD = 60;

    private static int pool = EndSky.GONE, copy = -1, hold;
    private static long start, skippedAt;
    private static boolean full, watching, skipped, known;
    private static ClientWorld world;
    /** How much of the waiting pool shows, 0 to 1: it spreads back after a wipe, and is there at once on arrival. */
    private static double spread, lastSpread;

    private FirstWizardIntroClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(EndSky.SkyPayload.ID, (payload, context) -> {
            var client = context.client();
            boolean arriving = !known || world != client.world;
            boolean scene = payload.pool() == EndSky.SCENE;
            if (scene && (pool != EndSky.SCENE || start != payload.start())) { skipped = false; hold = 0; }
            if (payload.pool() == EndSky.WAITING && arriving) spread = lastSpread = 1;
            if (payload.pool() == EndSky.WAITING && pool == EndSky.SCENE) spread = lastSpread = 0;
            pool = payload.pool();
            start = payload.start();
            full = payload.full();
            copy = payload.copy();
            watching = scene && payload.watching();
            world = client.world;
            known = true;
        });
        ClientTickEvents.START_CLIENT_TICK.register(FirstWizardIntroClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> forget());
        FirstWizardIntroDragon.init();
    }

    private static void forget() { pool = EndSky.GONE; watching = false; known = false; world = null; copy = -1; }

    /** What the server last said, if it is still about this client's world (the End). */
    private static boolean current() {
        var client = MinecraftClient.getInstance();
        return known && client.world != null && client.world == world && client.world.getRegistryKey() == EndArena.WORLD;
    }

    private static void tick(MinecraftClient client) {
        if (known && client.world != world) forget();
        lastSpread = spread;
        if (current() && pool == EndSky.WAITING) spread = Math.min(1, spread + 1 / SPREAD);
        else spread = lastSpread = 0;
        if (!frozen()) return;
        if (client.player == null || time(0) > scene().length() + 60) { watching = false; return; }
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
            ClientPlayNetworking.send(EndSky.SkipPayload.INSTANCE);
        }
    }

    // ------------------------------------------------------------------ what the sky's drawers ask

    /** The scene playing over the island on this client, or null. */
    public static EndIntroTrack.Scene scene() { return current() && pool == EndSky.SCENE ? EndIntroTrack.get().scene(full) : null; }

    /** The scene's tick, clamped to its length, or -1 when none plays here. */
    public static double sceneTime(float tickDelta) {
        EndIntroTrack.Scene scene = scene();
        if (scene == null) return -1;
        return MathHelper.clamp(time(tickDelta), 0, scene.length());
    }

    /** The waiting pool's share of the sky, 0 to 1, or 0 when it is not there. */
    public static double waitingPool(float tickDelta) {
        if (!current() || pool != EndSky.WAITING) return 0;
        return MathHelper.lerp(tickDelta, lastSpread, spread);
    }

    /** The copy the scene forms, by entity id, or -1. */
    public static int sceneCopy() { return scene() == null ? -1 : copy; }

    /** How far the scene has formed this copy, or -1 when no scene forms it here. */
    public static double formed(int entityId, float tickDelta) {
        double t = sceneTime(tickDelta);
        return t < 0 || entityId != copy ? -1 : scene().value("formed", t);
    }

    /** How far it has raised its head, 0 (bowed) to 1, or -1 when no scene forms it here. */
    public static double raised(int entityId, float tickDelta) {
        double t = sceneTime(tickDelta);
        return t < 0 || entityId != copy ? -1 : scene().value("raise", t);
    }

    private static double time(float tickDelta) {
        var w = MinecraftClient.getInstance().world;
        return w == null ? -1 : w.getTime() - start + tickDelta;
    }

    private static double smooth(double s) {
        s = MathHelper.clamp(s, 0, 1);
        return s * s * (3 - 2 * s);
    }

    // ------------------------------------------------------------------ the camera

    /** The player is held by the intro: no walking, jumping or sneaking. */
    public static boolean frozen() { return watching && current(); }

    private static int ret() { return scene().beat("return"); }

    /** 0 while the scene owns the camera, rising to 1 as it hands the view back. */
    private static double handBack(float tickDelta) {
        if (!frozen()) return 1;
        var w = MinecraftClient.getInstance().world;
        if (skipped && w != null) return smooth((w.getTime() - skippedAt + tickDelta) / HAND_BACK);
        return smooth((time(tickDelta) - ret()) / (scene().length() - ret()));
    }

    /** The scene owns the camera: HUD, hand, bobbing and block outline stay hidden. */
    public static boolean cinematic() { return frozen() && handBack(0) < 1; }

    /** Where the camera looks this frame, or null when the player's own view applies. */
    public static BossIntroCamera.Pose pose(float tickDelta) {
        var player = MinecraftClient.getInstance().player;
        if (!frozen() || player == null) return null;
        double back = handBack(tickDelta);
        // The last of the glide would pass through the player's own head: their eyes take over just before it.
        if (back >= .94) return null;
        EndIntroTrack.Scene scene = scene();
        double t = MathHelper.clamp(time(tickDelta), 0, scene.length());
        BossIntroCamera.Pose shot = new BossIntroCamera.Pose(EndIntroTrack.world(scene.eye(t)), scene.yaw(t), scene.pitch(t));
        if (back <= 0) return shot;
        return new BossIntroCamera.Pose(shot.pos().lerp(player.getCameraPosVec(tickDelta), back),
                MathHelper.lerpAngleDegrees((float) back, shot.yaw(), player.getYaw(tickDelta)),
                MathHelper.lerp((float) back, shot.pitch(), player.getPitch(tickDelta)));
    }

    public static float fov(float fov, float tickDelta) {
        if (!frozen()) return fov;
        EndIntroTrack.Scene scene = scene();
        return (float) MathHelper.lerp(handBack(tickDelta), scene.fov(MathHelper.clamp(time(tickDelta), 0, scene.length())), fov);
    }

    /** Letterbox, the fade in from black, the name and the skip prompt; drawn in place of the HUD while the scene owns the view. */
    public static void drawCinematic(DrawContext context, RenderTickCounter counter) {
        var client = MinecraftClient.getInstance();
        EndIntroTrack.Scene scene = scene();
        if (scene == null) return;
        float delta = counter.getTickProgress(false);
        double t = MathHelper.clamp(time(delta), 0, scene.length()), back = handBack(delta);
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        int bar = (int) Math.round(h * BARS * (1 - back));
        context.fill(0, 0, w, bar, 0xFF000000);
        context.fill(0, h - bar, w, h, 0xFF000000);
        double dark = scene.value("black", t) * (1 - back);
        if (dark > 0) context.fill(0, 0, w, h, alpha(dark));
        double title = scene.value("title", t) * (1 - back);
        if (title > 0) {
            String upper = Text.translatable("entity.elementalwands.first_wizard").getString().toUpperCase(java.util.Locale.ROOT);
            int width = client.textRenderer.getWidth(upper);
            float scale = Math.min(2.6f, (w - 48f) / Math.max(1, width));
            double enter = smooth((t - scene.beat("title")) / TITLE_FADE);
            // Low in the frame, above the letterbox, so it never covers its face.
            float centreY = h * .76f + (float) (12 * (1 - enter));
            int halfCard = (int) Math.ceil(width * scale / 2) + 12;
            int cardTop = (int) Math.floor(centreY - 4 * scale) - 9, cardBottom = (int) Math.ceil(centreY + (client.textRenderer.fontHeight - 4) * scale) + 10;
            context.fill(w / 2 - halfCard, cardTop, w / 2 + halfCard, cardBottom, alpha(title * .9));
            var m = context.getMatrices();
            m.pushMatrix();
            m.translate(w / 2f, centreY);
            m.scale(scale, scale);
            context.drawText(client.textRenderer, upper, -width / 2, -4, alpha(title) | 0xE6D2FF, true);
            m.popMatrix();
            int line = (int) (width * scale / 2 * smooth((t - scene.beat("title")) / 20));
            context.fill(w / 2 - line, cardBottom - 6, w / 2 + line, cardBottom - 5, alpha(title * .85) | 0x9A4DFF);
        }
        if (!skipped && t > 30 && back <= 0) {
            Text hint = Text.translatable("guardian.elementalwands.intro_skip", client.options.sneakKey.getBoundKeyLocalizedText());
            int x = w - client.textRenderer.getWidth(hint) - 10, y = h - Math.max(bar, 0) - 16;
            // Shown for a few seconds, then out of the picture; it comes back while Sneak is held.
            double shown = hold > 0 ? .8 : smooth((t - 30) / 20) * (1 - smooth((t - 150) / 40)) * .6;
            context.drawText(client.textRenderer, hint, x, y, alpha(shown) | 0xFFFFFF, true);
            if (hold > 0) context.fill(x, y + 10, x + client.textRenderer.getWidth(hint) * Math.min(hold, SKIP_HOLD) / SKIP_HOLD, y + 11, alpha(shown) | 0x9A4DFF);
        }
    }

    /** After skipping alone, the player gets their view back while the others still watch. */
    public static void drawWaiting(DrawContext context) {
        if (!frozen() || !skipped || cinematic()) return;
        var client = MinecraftClient.getInstance();
        Text text = Text.translatable("guardian.elementalwands.intro_waiting");
        context.drawCenteredTextWithShadow(client.textRenderer, text, context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 3, 0xCCD9C2FF);
    }

    /** For fixtures: this client watches the scene, and whether it has skipped. */
    public static boolean watching() { return frozen(); }
    public static boolean skipped() { return skipped; }

    private static int alpha(double a) { return (int) Math.round(MathHelper.clamp(a, 0, 1) * 255) << 24; }

    /** A track point in the world (a convenience for the drawers). */
    public static Vec3d place(Vec3d rel) { return EndIntroTrack.world(rel); }
}
