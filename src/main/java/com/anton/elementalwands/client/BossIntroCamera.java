package com.anton.elementalwands.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Whichever boss cinematic currently owns this client's camera: a boss intro or the Necromancer's
 * transformation. Each keeps its own scene (they are edited separately); the camera, HUD, input,
 * lens and light hooks ask here. Only one plays at a time, since a player can be in only one
 * realm's fight and the transformation comes long after the intro.
 */
public final class BossIntroCamera {
    public record Pose(Vec3d pos, float yaw, float pitch) {}

    private BossIntroCamera() {}

    /** The player is held by a cinematic: no walking, jumping or sneaking. */
    public static boolean frozen() {
        return GuardianIntroClient.frozen() || NecromancerIntroClient.frozen() || NecromancerTransformClient.frozen();
    }

    /** A cinematic owns the camera: HUD, hand, bobbing and block outline stay hidden. */
    public static boolean cinematic() {
        return GuardianIntroClient.cinematic() || NecromancerIntroClient.cinematic() || NecromancerTransformClient.cinematic();
    }

    /** Where the camera looks this frame, or null when the player's own view applies. */
    public static Pose pose(float tickDelta) {
        if (GuardianIntroClient.frozen()) return GuardianIntroClient.pose(tickDelta);
        if (NecromancerTransformClient.frozen()) return NecromancerTransformClient.pose(tickDelta);
        NecromancerIntroClient.Pose pose = NecromancerIntroClient.pose(tickDelta);
        return pose == null ? null : new Pose(pose.pos(), pose.yaw(), pose.pitch());
    }

    public static float fov(float fov, float tickDelta) {
        if (GuardianIntroClient.frozen()) return GuardianIntroClient.fov(fov, tickDelta);
        if (NecromancerTransformClient.frozen()) return NecromancerTransformClient.fov(fov, tickDelta);
        return NecromancerIntroClient.fov(fov, tickDelta);
    }

    /** The crypt's light under the Necromancer's cinematics: 1 as normal, 0 at its darkest. */
    public static float arenaLight(float tickDelta) {
        return Math.min(NecromancerIntroClient.arenaLight(tickDelta), NecromancerTransformClient.arenaLight(tickDelta));
    }

    /** Dim the constant ambient fill, while the soul effects and lantern pools remain readable. */
    public static float ambientLight(float normal, float tickDelta) {
        return MathHelper.lerp(arenaLight(tickDelta), Math.min(normal, .025f), normal);
    }

    public static void drawCinematic(DrawContext context, RenderTickCounter counter) {
        if (GuardianIntroClient.cinematic()) GuardianIntroClient.drawCinematic(context, counter);
        else if (NecromancerTransformClient.cinematic()) NecromancerTransformClient.drawCinematic(context, counter);
        else NecromancerIntroClient.drawCinematic(context, counter);
    }

    public static void drawWaiting(DrawContext context) {
        GuardianIntroClient.drawWaiting(context);
        NecromancerIntroClient.drawWaiting(context);
        NecromancerTransformClient.drawWaiting(context);
    }
}
