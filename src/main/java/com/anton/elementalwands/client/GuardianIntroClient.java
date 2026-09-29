package com.anton.elementalwands.client;

import static com.anton.elementalwands.entity.GuardianIntro.*;

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
 * Plays the Guardian's intro on this client: flies the camera through the scene's shots on the
 * server's clock, letterboxes the screen and hides the HUD, flashes with the heart's strike and the
 * lightning, shows the title, and lets the player hold Sneak to skip. It also tells the player
 * renderer whose arm holds the heart out. The server holds the player still; this only keeps
 * inputs quiet.
 */
public final class GuardianIntroClient {
    private static final float FOV = 55;
    private static final int SKIP_HOLD = 16, HAND_BACK = 8;
    private static final double BARS = .11;
    /** Lightning cracks that flash the screen, matching the server's thunder. */
    private static final int[] CRACKS = {WAKE, WAKE + 7, WAKE + 15, WAKE + 26, WAKE + 34, RISE + 8, RISE + 21};
    private static Vec3d centre, hand, core;
    private static float frame;
    private static long start, skippedAt;
    private static boolean active, skipped, leftHanded;
    private static int hold, callerId = -1;

    private GuardianIntroClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.GuardianIntroPayload.ID, (payload, context) -> {
            if (!payload.active()) { active = false; return; }
            centre = payload.centre();
            frame = payload.yaw();
            hand = payload.hand();
            core = place(centre, frame, CORE);
            callerId = payload.callerId();
            leftHanded = payload.leftHanded();
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

    /**
     * How far the given player's main arm is held out with the heart on it: fully through the first
     * shot, lowering once the heart has flown. Zero for everyone else.
     */
    public static float reach(int entityId) {
        if (!active || entityId != callerId) return 0;
        float delta = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
        return (float)(1 - smooth((time(delta) - LAUNCH - 4) / 20));
    }

    /** The outstretched arm shakes with the heart. */
    public static float tremble() {
        double t = time(MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false));
        return (float)(Math.sin(t * 3.1) * .025 * vibration(t) * (t < LAUNCH ? 1 : 0));
    }

    /** Where the camera looks this frame, or null when the player's own view applies. */
    public static BossIntroCamera.Pose pose(float tickDelta) {
        var player = MinecraftClient.getInstance().player;
        if (!active || player == null) return null;
        double back = handBack(tickDelta);
        if (back >= 1) return null;
        BossIntroCamera.Pose shot = shot(time(tickDelta));
        if (back <= 0) return shot;
        return new BossIntroCamera.Pose(shot.pos().lerp(player.getCameraPosVec(tickDelta), back),
                MathHelper.lerpAngleDegrees((float)back, shot.yaw(), player.getYaw(tickDelta)),
                MathHelper.lerp((float)back, shot.pitch(), player.getPitch(tickDelta)));
    }

    public static float fov(float fov, float tickDelta) {
        return active ? (float)MathHelper.lerp(handBack(tickDelta), FOV, fov) : fov;
    }

    /**
     * The shots. First the caller's outstretched hand, from in front and to the side so their face
     * shows past the heart and the Guardian is behind the lens. Then over their shoulder as the heart
     * flies, chasing it down the nave until the kneeling statue fills the frame and the heart strikes
     * home. Low beside it as it wakes and rises, then square in front for the fist slam.
     */
    static BossIntroCamera.Pose shot(double t) {
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d ahead = new Vec3d(centre.x - hand.x, 0, centre.z - hand.z).normalize();
        Vec3d side = new Vec3d(-ahead.z, 0, ahead.x).multiply(leftHanded ? -1 : 1);
        Vec3d hover = hand.add(0, .4, 0), head = hand.subtract(ahead.multiply(.55)).subtract(side.multiply(.375)).add(0, .24, 0);
        double lifted = smooth((t - LIFT) / (LAUNCH - LIFT - 3));
        Vec3d steady = hand.lerp(hover, lifted);
        double pushed = smooth(t / LIFT);
        // In profile from the hand's side, a little ahead: the face, the outstretched arm and the heart
        // on it, with the Guardian just outside the frame ahead of them.
        Vec3d close = hand.add(side.multiply(2.1 - .45 * pushed)).add(ahead.multiply(.35 - .1 * pushed)).add(0, -.1 + .05 * pushed + .2 * lifted, 0);
        if (t < LAUNCH) {
            double shake = .004 * vibration(t) + .008 * lifted;
            return look(close, steady.lerp(head, .35 * (1 - lifted)), shake, t);
        }
        Vec3d heart = heartAt(hand, core, t), flight = new Vec3d(core.x - hover.x, 0, core.z - hover.z).normalize();
        if (t < IMPACT + 1) {
            // Swing up over the caller's shoulder, then chase the heart; near the statue the camera
            // settles in front of it and lets the heart go in.
            Vec3d chase = heart.subtract(flight.multiply(2.4)).add(0, .75, 0).add(side.multiply(.55));
            double swing = smooth((t - LAUNCH) / 7);
            Vec3d eye = close.lerp(chase, swing).add(0, .7 * Math.sin(Math.PI * swing), 0);
            double near = smooth((11 - heart.distanceTo(core)) / 7);
            eye = eye.lerp(settle(), near);
            Vec3d target = heart.add(flight.multiply(4)).lerp(core.add(0, .25, 0), near);
            return look(eye, target, 0, t);
        }
        if (t < RISE) {
            // The strike's jolt, then a slow push in as lightning crawls over it and its head comes up.
            double s = smooth((t - IMPACT) / (RISE - IMPACT)), headUp = smooth((t - HEAD) / (RISE - HEAD));
            Vec3d eye = settle().lerp(local(1.4, 1.5, 6.2), s);
            Vec3d target = core.add(0, .25, 0).lerp(local(HEAD_KNEELING.x, HEAD_KNEELING.y - .3, HEAD_KNEELING.z), .6 * headUp);
            return look(eye, target, jolt(t), t);
        }
        if (t < WIND) {
            // Low beside it, looking up as it pushes itself to its feet.
            double s = smooth((t - RISE) / (WIND - RISE));
            return look(local(-4.2 - 1.2 * s, .5 + .4 * s, 5.2 + 2 * s), local(0, 2.3 + 1.5 * s, .6 - .2 * s), jolt(t), t);
        }
        // Square in front and low for the slam, the whole Guardian filling the frame.
        double s = smooth((t - WIND) / (CLAP - WIND)), drift = smooth((t - CLAP) / (LENGTH - CLAP));
        return look(local(.5 - .2 * s, 1.35 + .1 * s, 8.8 - 1.2 * s + 1.2 * drift), local(0, 2.95, 0), jolt(t), t);
    }

    /** Where the chase comes to rest: in front of the kneeling statue, a little to one side. */
    private static Vec3d settle() { return local(1.8, 2.0, 8.2); }

    private static Vec3d local(double x, double y, double z) { return place(centre, frame, new Vec3d(x, y, z)); }

    /** Camera shake: the heart's strike, the lightning and the slam. */
    private static double jolt(double t) {
        double shake = t >= IMPACT ? .12 * Math.exp(-(t - IMPACT) / 5) : 0;
        for (int crack : CRACKS) if (t >= crack) shake += .03 * Math.exp(-(t - crack) / 3);
        if (t >= CLAP) shake += .2 * Math.exp(-(t - CLAP) / 5);
        return shake;
    }

    private static BossIntroCamera.Pose look(Vec3d at, Vec3d to, double shake, double t) {
        if (shake > 0) at = at.add(Math.sin(t * 3.1) * shake, Math.sin(t * 4.3 + 1) * shake, Math.cos(t * 3.7) * shake);
        Vec3d d = to.subtract(at);
        return new BossIntroCamera.Pose(at, (float)Math.toDegrees(Math.atan2(-d.x, d.z)), (float)-Math.toDegrees(Math.atan2(d.y, d.horizontalLength())));
    }

    /** White-hot flashes: the heart's strike, each lightning crack and the slam. */
    private static double flash(double t) {
        double light = t >= IMPACT ? .7 * Math.exp(-(t - IMPACT) / 3) : 0;
        for (int crack : CRACKS) if (t >= crack) light += .16 * Math.exp(-(t - crack) / 1.5);
        if (t >= CLAP) light += .5 * Math.exp(-(t - CLAP) / 2.5);
        return Math.min(.9, light);
    }

    /** Letterbox, fade-in, flashes, title and skip prompt; drawn in place of the HUD while the scene owns the view. */
    public static void drawCinematic(DrawContext context, RenderTickCounter counter) {
        var client = MinecraftClient.getInstance();
        float delta = counter.getTickProgress(false);
        double t = time(delta), back = handBack(delta);
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight();
        double white = flash(t) * (1 - back);
        if (white > .01) context.fill(0, 0, w, h, alpha(white) | 0xE8FFFF);
        int bar = (int)Math.round(h * BARS * smooth(t / 10) * (1 - back));
        context.fill(0, 0, w, bar, 0xFF000000);
        context.fill(0, h - bar, w, h, 0xFF000000);
        double dark = 1 - smooth(t / 16);
        if (dark > 0) context.fill(0, 0, w, h, alpha(dark) | 0x000000);
        double title = smooth((t - TITLE) / 8) * (1 - smooth((t - (TITLE_END - 8)) / 8)) * (1 - back);
        if (title > 0) {
            Text name = Text.translatable("entity.elementalwands.fractured_guardian");
            String upper = name.getString().toUpperCase(java.util.Locale.ROOT);
            var m = context.getMatrices();
            m.pushMatrix();
            m.translate(w / 2f, h * .7f);
            m.scale(2.6f, 2.6f);
            int width = client.textRenderer.getWidth(upper);
            context.drawText(client.textRenderer, upper, -width / 2, -4, alpha(title) | 0xE4ECEC, true);
            m.popMatrix();
            int line = (int)(width * 2.6 * .6 * smooth((t - TITLE) / 14));
            context.fill(w / 2 - line, (int)(h * .7f) + 12, w / 2 + line, (int)(h * .7f) + 13, alpha(title * .8) | 0x6AF2FF);
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
