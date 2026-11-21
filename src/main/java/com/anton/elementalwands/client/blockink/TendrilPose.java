package com.anton.elementalwands.client.blockink;

import net.minecraft.util.math.Vec3d;

/**
 * Phase two's tentacles at rest: where each of the three lies along its length, how thick it is, and how fast it moves.
 * {@link TendrilFx} does the rest (the burst out of her flank, the tube, the wound) and blends each tentacle from this
 * pose into what the move in hand asks of it ({@link TendrilMoves}, and number 2's strikes) and back. The game's own is
 * {@link TendrilRest}.
 *
 * <p>A design-study seam: the fight fixture's {@code -PwizardPose=<Name>} (system property {@code wizard.pose}) draws the
 * rest pose with {@code TendrilPose<Name>} from this package instead of the game's own. A study that leaves out the
 * thickness or the rhythms gets the game's.
 */
@FunctionalInterface
interface TendrilPose {
    /**
     * Tentacle i (0 to 2; 2 is the one that strikes) at rest, s from the root (0) to the tip (1), in world space.
     * phase is its crawl clock and quest its tip's (radians, advancing at {@link #crawl} and {@link #quest} turns a
     * second, faster while a move asks); hold 0 to 1 draws them in and stills them (the game no longer holds them still
     * and always passes 0: its moves' poses are {@link TendrilMoves}'s); unfurl below 1 is a tentacle still curled from bursting out (it overshoots to about 1.15 once);
     * burst is ticks since it burst out (huge once long out). {@link TendrilFx#clear} keeps a point off her front.
     */
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
