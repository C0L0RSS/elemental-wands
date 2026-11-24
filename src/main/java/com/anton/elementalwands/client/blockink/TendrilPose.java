package com.anton.elementalwands.client.blockink;

import net.minecraft.util.math.Vec3d;

/** Rest-pose seam for the fight fixture's named design studies. Coordinates are in world space. */
@FunctionalInterface
interface TendrilPose {
    /** Sample one arm from its socket (s = 0) to tip (s = 1). Angles are in radians. */
    Vec3d rest(TendrilFx.Frame fr, int i, double s, double phase, double quest, double hold, double unfurl, double burst);

    /** Radius at s, from the root (0) to the tip (1). */
    default double radius(double s) { return TendrilRest.girth(s); }

    /** Turns a second of tentacle i's crawl clock. */
    default double crawl(int i) { return TendrilRest.CRAWL[i]; }

    /** Turns a second of tentacle i's questing tip. */
    default double quest(int i) { return TendrilRest.QUEST[i]; }

    /** The pose the {@code wizard.pose} property names, or the game's own. */
    static TendrilPose pick(TendrilPose own) {
        String name = System.getProperty("wizard.pose");
        if (name == null || name.isBlank()) return own;
        try {
            return (TendrilPose) Class.forName(TendrilPose.class.getPackageName() + ".TendrilPose" + name).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("no tentacle pose TendrilPose" + name, e);
        }
    }
}
