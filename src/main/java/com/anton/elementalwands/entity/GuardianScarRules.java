package com.anton.elementalwands.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The Scar: it drives its hands into the floor and tears two cracks outward, one each side of its
 * target, which erupt into walls of stone spikes. It is not judged as a hit. A moving player cannot
 * be struck by a line, so the walls pen them instead: between them a player can only run toward the
 * Guardian or away from it, and it follows with a leap or a slam into the pen.
 */
public final class GuardianScarRules {
    /** Server ticks of the {@code scar} clip: hands hit the floor and the aim commits, then the walls erupt. */
    public static final int PLANT = 20, ERUPT = 30, DURATION = 50;
    /** Ticks the cracks show on the floor before they erupt. */
    public static final int COMMIT = ERUPT - PLANT;
    /** The far end of a wall erupts this many ticks after the near end. */
    public static final int RIPPLE = 6;
    /** Ticks the walls stand, and how long after they fall before it scores the floor again (scaled). */
    public static final int LIFE = 120, LOCKOUT = 100;
    /** A pen that is about to fall no longer counts as one. */
    public static final int FADING = 30;
    public static final double MIN_RANGE = 7, MAX_RANGE = 30;
    /** Each wall stands this far to one side of the line from the Guardian through its target. */
    public static final double HALF_WIDTH = 4;
    /** Walls start this far from the Guardian, run this far past its target, and are never longer than MAX_LENGTH. */
    public static final double START = 3, AHEAD = 10, MAX_LENGTH = 38;
    /** One spike per block of wall; each is a column this wide and tall, too high to jump. */
    public static final double SPACING = 1, WIDTH = 1.1, HEIGHT = 4;
    /** Caught by the eruption, and touching a standing spike (at most once every TOUCH_INTERVAL ticks). */
    public static final float ERUPT_DAMAGE = 14, TOUCH_DAMAGE = 10;
    public static final int TOUCH_INTERVAL = 20;
    /** Co-op: this many players besides its target get one wall each, this far ahead of where they are heading. */
    public static final int MAX_FLANKS = 2;
    public static final double FLANK_LEAD = 3.5;
    private GuardianScarRules() {}

    /** One straight wall along the floor; heights are ignored. */
    public record Wall(Vec3d from, Vec3d to) {
        public double length() { return Math.hypot(to.x - from.x, to.z - from.z); }
        /** Where its spikes stand, one every {@link #SPACING} blocks, from the Guardian's end outward. */
        public List<Vec3d> columns() {
            List<Vec3d> points = new ArrayList<>();
            double length = length();
            int count = (int)Math.floor(length / SPACING) + 1;
            for (int i = 0; i < count; i++) points.add(from.lerp(to, length < 1e-6 ? 0 : i * SPACING / length));
            return points;
        }
        /** Horizontal distance from a point to the wall's line. */
        public double distance(Vec3d point) {
            double dx = to.x - from.x, dz = to.z - from.z, squared = dx * dx + dz * dz;
            double t = squared < 1e-8 ? 0 : Math.clamp(((point.x - from.x) * dx + (point.z - from.z) * dz) / squared, 0, 1);
            return Math.hypot(point.x - (from.x + dx * t), point.z - (from.z + dz * t));
        }
    }

    /** The pen: two parallel walls either side of {@code axis}, from {@code near} to {@code far} blocks out. */
    public record Pen(Vec3d origin, Vec3d axis, double near, double far) {
        private Vec3d side() { return new Vec3d(-axis.z, 0, axis.x); }
        public Wall wall(boolean left) {
            Vec3d offset = side().multiply(left ? HALF_WIDTH : -HALF_WIDTH);
            return new Wall(origin.add(axis.multiply(near)).add(offset), origin.add(axis.multiply(far)).add(offset));
        }
        /** Between the walls, with no way out sideways. */
        public boolean contains(Vec3d point) {
            Vec3d delta = new Vec3d(point.x - origin.x, 0, point.z - origin.z);
            double along = delta.dotProduct(axis), across = Math.abs(delta.dotProduct(side()));
            return along >= near && along <= far && across < HALF_WIDTH - WIDTH / 2;
        }
        /** Near enough the pen that another wall there would only cut across it. */
        public boolean crowds(Vec3d point) {
            Vec3d delta = new Vec3d(point.x - origin.x, 0, point.z - origin.z);
            return delta.dotProduct(axis) > 0 && Math.abs(delta.dotProduct(side())) < HALF_WIDTH + 2;
        }
    }

    /** The pen round {@code aim}, where its target will be when the walls erupt; null if that is on top of it. */
    public static Pen pen(Vec3d origin, Vec3d aim) {
        Vec3d delta = new Vec3d(aim.x - origin.x, 0, aim.z - origin.z);
        double reach = delta.length();
        if (reach < START + 1) return null;
        return new Pen(origin, delta.normalize(), START, Math.min(reach + AHEAD, MAX_LENGTH));
    }

    /**
     * Co-op: one wall from the Guardian out past a point just ahead of where another player is heading,
     * so their way round it is cut. A player who is not moving gets it on the side away from the pen.
     * Null if they are beside the Guardian or so near the pen that the wall would cut across it.
     */
    public static Wall flank(Pen pen, Vec3d position, Vec3d drift) {
        Vec3d flat = new Vec3d(drift.x, 0, drift.z);
        Vec3d led = position.add(flat.multiply(COMMIT));
        Vec3d out = new Vec3d(position.x - pen.origin().x, 0, position.z - pen.origin().z);
        if (out.lengthSquared() < 1e-6) return null;
        Vec3d round = new Vec3d(-out.z, 0, out.x).normalize();
        // Away from the pen: round the Guardian in the direction that turns them further from the pen's line.
        if (round.dotProduct(pen.axis()) > 0) round = round.multiply(-1);
        Vec3d heading = flat.length() > .03 ? flat.normalize() : round;
        Vec3d through = led.add(heading.multiply(FLANK_LEAD));
        Vec3d ray = new Vec3d(through.x - pen.origin().x, 0, through.z - pen.origin().z);
        double reach = ray.length();
        if (reach < START + 1 || pen.crowds(through)) return null;
        Vec3d direction = ray.normalize();
        return new Wall(pen.origin().add(direction.multiply(START)),
                pen.origin().add(direction.multiply(Math.min(reach + AHEAD, MAX_LENGTH))));
    }

    /** A spike's body, standing on {@code base}. */
    public static Box column(Vec3d base) {
        double r = WIDTH / 2;
        return new Box(base.x - r, base.y, base.z - r, base.x + r, base.y + HEIGHT, base.z + r);
    }

    /** Ticks after the eruption starts that the spike this far along a wall of {@code count} comes up. */
    public static int ripple(int index, int count) {
        return count <= 1 ? 0 : Math.round(index * (float)RIPPLE / (count - 1));
    }
}
