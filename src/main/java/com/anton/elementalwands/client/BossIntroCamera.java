package com.anton.elementalwands.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

/**
 * Whichever boss intro currently owns this client's camera. Each boss keeps its own scene (the
 * Necromancer's and the Guardian's are edited separately); the camera, HUD, input and lens hooks
 * ask here. Only one plays at a time, since a player can be in only one realm's fight.
 */
public final class BossIntroCamera {
    public record Pose(Vec3d pos, float yaw, float pitch) {}

    private BossIntroCamera() {}

    /** The player is held by an intro: no walking, jumping or sneaking. */
    public static boolean frozen() { return GuardianIntroClient.frozen() || NecromancerIntroClient.frozen(); }

    /** An intro owns the camera: HUD, hand, bobbing and block outline stay hidden. */
    public static boolean cinematic() { return GuardianIntroClient.cinematic() || NecromancerIntroClient.cinematic(); }

    /** Where the camera looks this frame, or null when the player's own view applies. */
    public static Pose pose(float tickDelta) {
        if (GuardianIntroClient.frozen()) return GuardianIntroClient.pose(tickDelta);
        NecromancerIntroClient.Pose pose = NecromancerIntroClient.pose(tickDelta);
        return pose == null ? null : new Pose(pose.pos(), pose.yaw(), pose.pitch());
    }

    public static float fov(float fov, float tickDelta) {
        return GuardianIntroClient.frozen() ? GuardianIntroClient.fov(fov, tickDelta) : NecromancerIntroClient.fov(fov, tickDelta);
    }

    public static void drawCinematic(DrawContext context, RenderTickCounter counter) {
        if (GuardianIntroClient.cinematic()) GuardianIntroClient.drawCinematic(context, counter);
        else NecromancerIntroClient.drawCinematic(context, counter);
    }

    public static void drawWaiting(DrawContext context) {
        GuardianIntroClient.drawWaiting(context);
        NecromancerIntroClient.drawWaiting(context);
    }
}
