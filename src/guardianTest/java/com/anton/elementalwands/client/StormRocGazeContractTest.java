package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.roc.StormRocPose;
import com.anton.elementalwands.entity.roc.StormRocRules;
import java.util.List;
import java.util.Map;
import org.joml.Vector3d;

/** Pure checks on the Roc's client gaze; run by the Gradle check task without launching Minecraft. */
public final class StormRocGazeContractTest {
    /** Clip ticks where the gaze may hold its head, and whether it is in flight. */
    private record Window(String pose, int from, int to, boolean flight) {}

    public static void main(String[] args) {
        // Its gaze turns the head round and up or down without twisting it about its bill. In flight
        // the shortest arc from a glance to one side down to a player on the other turned it upside down.
        var windows = List.of(
            new Window("spire_flight", 0, 64, true),
            new Window("flight", 0, 144, true),
            new Window("land", 0, StormRocRules.LAND_LEAD + StormRocRules.CALL_AT, false),
            // The call's head is on the sky until it whips down, before its gaze takes hold.
            new Window("call", StormRocRules.CALL_VOLLEY + StormRocRules.LARGE_WARNING - 2, StormRocRules.CALL_TICKS, false),
            new Window("bite", 0, StormRocRules.Action.BITE.lock, false),
            new Window("bite_leap", 0, StormRocRules.Action.BITE.lock, false),
            new Window("pounce", 0, StormRocRules.Action.POUNCE.lock, false),
            new Window("stampede_run", 0, 9, false));
        int checked = 0;
        for (Window w : windows) {
            for (int tick = w.from(); tick <= w.to(); tick += 2) {
                for (int yaw = -80; yaw <= 80; yaw += 10) {
                    for (int pitch = w.flight() ? -75 : -50; pitch <= (w.flight() ? -5 : 35); pitch += 10) {
                        Map<String, float[]> channels = StormRocPose.pose(w.pose(), tick);
                        double before = roll(channels);
                        StormRocMotion.gaze(channels, w.pose(), yaw, pitch, 1);
                        double twist = wrap(roll(channels) - before);
                        require(Math.abs(twist) < 2, w.pose() + " tick " + tick + ": looking at yaw " + yaw + ", pitch " + pitch + " twists the head " + Math.round(twist) + " degrees about its bill");
                        // In flight the gaze reaches all the way from wherever the clip has the head.
                        if (w.flight()) require(StormRocMotion.bill(channels).dot(StormRocMotion.direction(yaw, pitch)) > Math.cos(Math.toRadians(1)),
                            w.pose() + " tick " + tick + ": the bill misses its gaze at yaw " + yaw + ", pitch " + pitch);
                        checked++;
                    }
                }
            }
        }
        System.out.println("Storm Roc gaze contract passed: " + checked + " gazes keep the head upright");
    }

    /** Degrees the head's top is turned from upright about its bill, in the model's space. */
    private static double roll(Map<String, float[]> channels) {
        Vector3d bill = StormRocMotion.bill(channels), up = StormRocMotion.head(channels).transform(new Vector3d(0, 1, 0));
        require(Math.abs(bill.y) < .99, "A gaze window holds the bill too near vertical to measure its roll");
        Vector3d side = new Vector3d(bill).cross(0, 1, 0).normalize(), level = new Vector3d(side).cross(bill);
        return Math.toDegrees(Math.atan2(up.dot(side), up.dot(level)));
    }

    private static double wrap(double degrees) { return ((degrees % 360) + 540) % 360 - 180; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
