package com.anton.elementalwands.client;

import static com.anton.elementalwands.entity.necromancer.NecromancerIntro.*;

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
 * Plays the Necromancer's intro on this client: flies the camera through the scene's shots on the
 * server's clock, letterboxes the screen and hides the HUD, shows the title, and lets the player
 * hold Sneak to skip. The server holds the player still; this only keeps inputs quiet.
 */
public final class NecromancerIntroClient {
    public record Pose(Vec3d pos, float yaw, float pitch) {}
    private static final float FOV = 55;
    private static final int SKIP_HOLD = 16, HAND_BACK = 8, TITLE_FADE = 10;
    private static final double BARS = .11;
    private static Vec3d centre;
    private static float frame;
    private static long start, skippedAt;
    private static boolean active, skipped;
    private static int hold;

    private NecromancerIntroClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.NecromancerIntroPayload.ID, (payload, context) -> {
            if (!payload.active()) { active = false; return; }
            centre = payload.centre();
            frame = payload.yaw();
            start = payload.start();
            active = true;
            skipped = false;
            hold = 0;
        });
        ClientTickEvents.START_CLIENT_TICK.register(NecromancerIntroClient::tick);
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
            ClientPlayNetworking.send(ModNetworking.NecromancerIntroSkipPayload.INSTANCE);
        }
    }

    private static double time(float tickDelta) {
        var world = MinecraftClient.getInstance().world;
        return world == null ? -1 : world.getTime() - start + tickDelta;
    }

    /** The player is held by an intro: no walking, jumping or sneaking. */
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
    public static Pose pose(float tickDelta) {
        var player = MinecraftClient.getInstance().player;
        if (!active || player == null) return null;
        double back = handBack(tickDelta);
        if (back >= 1) return null;
        Pose shot = shot(time(tickDelta));
        if (back <= 0) return shot;
        return new Pose(shot.pos().lerp(player.getCameraPosVec(tickDelta), back),
                MathHelper.lerpAngleDegrees((float)back, shot.yaw(), player.getYaw(tickDelta)),
                MathHelper.lerp((float)back, shot.pitch(), player.getPitch(tickDelta)));
    }

    /** Normal lighting outside the crypt cinematic, including a skip or cancelled scene. */
    public static float arenaLight(float tickDelta) {
        var world = MinecraftClient.getInstance().world;
        if (!active || skipped || world == null || world.getRegistryKey() != com.anton.elementalwands.crypt.HollowCryptRealm.WORLD) return 1;
        return (float)ringProgress(time(tickDelta));
    }

    /** Dim the constant ambient fill, while the soul effects and lantern pools remain readable. */
    public static float ambientLight(float normal, float tickDelta) {
        return MathHelper.lerp(arenaLight(tickDelta), Math.min(normal, .025f), normal);
    }

    public static float fov(float fov, float tickDelta) {
        return active ? (float)MathHelper.lerp(handBack(tickDelta), FOV, fov) : fov;
    }

    /**
     * The five shots, authored around the circle with the players to the south (+Z): the zombie,
     * with the circle behind the camera; its soul torn out toward the lens; a pan after the soul that
     * finds the Necromancer hauling it into his staff; a low hero angle as it burns inside; and
     * from the players' side as he levels the staff and the slam's ring rolls out.
     */
    static Pose shot(double t) {
        Vec3d toward = new Vec3d(VICTIM_TO.x, 0, VICTIM_TO.z).normalize(), side = new Vec3d(toward.z, 0, -toward.x);
        if (t < 60) {
            // Low beside the row of graves as the zombie shuffles toward the lens; he is well off to the right.
            double s = smooth(t / 60);
            return look(VICTIM_TO.add(2.6 - .4 * s, 1.05, 3.6 - .6 * s), victimAt(t).add(0, 1.5, 0), 0);
        }
        Vec3d closeUp = VICTIM_TO.add(2, 1.45, 1.8);
        if (t < REVEAL) {
            // Moving in on the chest as the soul is pulled out of it, face first.
            double s = smooth((t - 60) / (REVEAL - 60));
            Vec3d chest = victimAt(t).add(0, 1.3, 0), soul = tornAt(soulOrigin(), STAFF_HEAD, t - PULL);
            return look(VICTIM_TO.add(2.9, 1.15, 2.6).lerp(closeUp, s), chest.lerp(soul, .6 * smooth((t - PULL) / 16)), 0);
        }
        if (t < SOUL_ARRIVE + 2) {
            // Flying just ahead of the soul's face as it is dragged backward, the Necromancer growing
            // behind it; near the staff the camera stops and lets it go into the flame.
            Vec3d from = soulOrigin(), dir = STAFF_HEAD.subtract(from).normalize(), across = new Vec3d(-dir.z, 0, dir.x);
            Vec3d soul = tornAt(from, STAFF_HEAD, t - PULL);
            Vec3d track = soul.subtract(dir.multiply(2.6)).add(across.multiply(1)).add(0, .25, 0);
            // The stop is well off the soul's line, so it flies past at a distance, not into the lens.
            Vec3d settle = toward.multiply(6).add(side.multiply(2.8)).add(0, 1.2, 0);
            double near = smooth((6.5 - soul.distanceTo(STAFF_HEAD)) / 4.5);
            // Swing round to its face on an arc, clear of the soul rather than through it.
            double swing = smooth((t - REVEAL) / 10);
            Vec3d eye = closeUp.lerp(track, swing).add(across.multiply(1.6 * Math.sin(Math.PI * swing))).lerp(settle, near);
            // Just past the face, so he shows behind it.
            return look(eye, soul.add(dir.multiply(.6)).lerp(new Vec3d(0, 2.4, 0), near), 0);
        }
        if (t < HERO_END) {
            // Low and close enough to fill the frame, far enough to keep the raised flame in it.
            double s = smooth((t - SOUL_ARRIVE) / (HERO_END - SOUL_ARRIVE)), lower = smooth((t - STAFF_LOWER) / (TURN - STAFF_LOWER));
            double shake = .03 * Math.exp(-(t - SOUL_ARRIVE) / 8);
            return look(toward.multiply(4.2 - .5 * s).add(side.multiply(1.2)).add(0, .7, 0), new Vec3d(0, 2.55 - .6 * lower, 0), shake);
        }
        // From the players' side: keep his face and the staff in view, then pull back as the ring spreads.
        double s = smooth((t - HERO_END) / (LENGTH - HERO_END)), shake = t >= SLAM ? .14 * Math.exp(-(t - SLAM) / 5) : 0;
        return look(new Vec3d(2.4 + .4 * s, 1.05 + .35 * s, 6.4 + 2 * s), new Vec3d(0, 1.65, 0), shake);
    }

    private static Pose look(Vec3d eye, Vec3d target, double shake) {
        Vec3d at = place(centre, frame, eye), to = place(centre, frame, target);
        if (shake > 0) {
            double t = time(0);
            at = at.add(Math.sin(t * 3.1) * shake, Math.sin(t * 4.3 + 1) * shake, Math.cos(t * 3.7) * shake);
        }
        Vec3d d = to.subtract(at);
        return new Pose(at, (float)Math.toDegrees(Math.atan2(-d.x, d.z)), (float)-Math.toDegrees(Math.atan2(d.y, d.horizontalLength())));
    }

    /** Letterbox, fade-in, title and skip prompt; drawn in place of the HUD while the scene owns the view. */
    public static void drawCinematic(DrawContext context, RenderTickCounter counter) {
        var client = MinecraftClient.getInstance();
        float delta = counter.getTickProgress(false);
        double t = time(delta), back = handBack(delta);
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        int bar = (int)Math.round(h * BARS * smooth(t / 10) * (1 - back));
        context.fill(0, 0, w, bar, 0xFF000000);
        context.fill(0, h - bar, w, h, 0xFF000000);
        double dark = 1 - smooth(t / 14);
        if (dark > 0) context.fill(0, 0, w, h, alpha(dark) | 0x000000);
        double titleEnter = smooth((t - TITLE) / TITLE_FADE);
        double title = titleEnter * (1 - smooth((t - (TITLE_END - TITLE_FADE)) / TITLE_FADE)) * (1 - back);
        if (title > 0) {
            Text name = Text.translatable("entity.elementalwands.hollow_necromancer");
            String upper = name.getString().toUpperCase(java.util.Locale.ROOT);
            int width = client.textRenderer.getWidth(upper);
            float scale = Math.min(2.6f, (w - 48f) / Math.max(1, width));
            float centreY = h * .7f + (float)(16 * (1 - titleEnter));
            int halfCard = (int)Math.ceil(width * scale / 2) + 12;
            int cardTop = (int)Math.floor(centreY - 4 * scale) - 9;
            int cardBottom = (int)Math.ceil(centreY + (client.textRenderer.fontHeight - 4) * scale) + 10;
            // The padded black card and title slide upward together, clear of the brightest wave.
            context.fill(w / 2 - halfCard, cardTop, w / 2 + halfCard, cardBottom, alpha(title * .9));
            var m = context.getMatrices();
            m.pushMatrix();
            m.translate(w / 2f, centreY);
            m.scale(scale, scale);
            context.drawText(client.textRenderer, upper, -width / 2, -4, alpha(title) | 0xD8F4FF, true);
            m.popMatrix();
            int line = (int)(width * scale / 2 * smooth((t - TITLE) / 14));
            int lineY = cardBottom - 6;
            context.fill(w / 2 - line, lineY, w / 2 + line, lineY + 1, alpha(title * .8) | 0x6AF2FF);
        }
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

    private static int alpha(double a) { return (int)Math.round(MathHelper.clamp(a, 0, 1) * 255) << 24; }
}
