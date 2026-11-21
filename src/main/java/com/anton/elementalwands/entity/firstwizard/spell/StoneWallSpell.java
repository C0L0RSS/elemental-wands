package com.anton.elementalwands.entity.firstwizard.spell;

import com.anton.elementalwands.block.FirstWizardWallBlock;
import com.anton.elementalwands.entity.firstwizard.Arena;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Stone Wall: ink runs along the floor on a line behind its target, then a wall of the copy's stone rises there, cuts
 * off that way out for six seconds and crumbles into falling ink. The first half of the design's first combination
 * (the Fireball down the open lane follows; see {@code FirstWizardRules.FOLLOW_UPS}).
 *
 * <ul>
 *   <li><b>The line</b> is fixed at once: across the line from the copy to its target, {@link #BEHIND} blocks behind
 *       where the target is heading, {@link #LENGTH} long, clipped to the floor ({@link #line}). Ink runs along it for
 *       a second; that is the warning.</li>
 *   <li><b>It rises</b> a row a tick, {@link #HEIGHT} tall and two thick, as real blocks
 *       ({@code ModSpellBlocks.FIRST_WIZARD_WALL}) that stop players, their spells and the copy's own. It only fills
 *       air, so nothing of the floor or anything a player placed is replaced. A player on the line as it rises takes
 *       a medium hit and is set down on the nearer side, never entombed.</li>
 *   <li><b>It stands</b> {@link #STAND} ticks, then crumbles from the top, a row every {@link #CRUMBLE_EVERY} ticks,
 *       and every cell is air again. Stopping the fight, the copy's death or removal and the server stopping take it
 *       down at once (the blocks go through {@link Cast#placeBlock}); after a crash each block clears itself.</li>
 * </ul>
 */
public final class StoneWallSpell implements BossSpell {
    public static final String KIND = "stone_wall";
    /** The line is fixed at tick 1; ink runs along it for a second; the wall rises from tick 20 and the copy is free at 26. */
    public static final Timing TIMING = new Timing(0, 1, 20, 26);
    /** Blocks: its length (the player's is 3), height (the player's is 3) and thickness. */
    public static final double LENGTH = 27, HALF_THICK = 1.0;
    public static final int HEIGHT = 5;
    /** How far behind its target the line runs, and the least it may be when the floor's edge is near. */
    public static final double BEHIND = 4, BEHIND_LEAST = 2;
    /** A line shorter than this once clipped to the floor is not worth casting. */
    public static final double SHORTEST = 8;
    /** Ticks it stands once risen, and between rows as it crumbles from the top. */
    public static final int STAND = 120, CRUMBLE_EVERY = 3;
    /** It runs the line through where its target will be in this long (a fleeing target is cut off). */
    public static final double LEAD_SECONDS = .5;
    /** Lines stop this far inside the floor's edge. */
    public static final double MARGIN = FirstWizardRules.EDGE;

    @Override public FirstWizardRules.Move move() { return FirstWizardRules.Move.STONE_WALL; }
    @Override public Timing timing() { return TIMING; }

    /** The ticks after release when the last row has risen, when it starts to crumble and when the last row is gone. */
    public static int risen() { return TIMING.release() + HEIGHT - 1; }
    public static int crumbles() { return risen() + STAND; }
    public static int gone() { return crumbles() + (HEIGHT - 1) * CRUMBLE_EVERY; }

    /**
     * The wall's line: two ends on the floor, or null when no line of at least {@link #SHORTEST} fits. It crosses the
     * line from {@code boss} to {@code heading} (where its target is heading), {@link #BEHIND} blocks past it, or
     * nearer (down to {@link #BEHIND_LEAST}) when the floor's edge would cut it short; never in front of them, so a
     * target with their back to the edge draws no wall. Its middle is on a block corner, so a straight wall is exactly
     * two blocks thick. Pure, for the contract check.
     */
    public static Vec3d[] line(Arena arena, Vec3d boss, Vec3d heading) {
        Vec3d away = new Vec3d(heading.x - boss.x, 0, heading.z - boss.z);
        away = away.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : away.normalize();
        Vec3d across = new Vec3d(-away.z, 0, away.x);
        Vec3d[] best = null;
        double bestLength = 0;
        for (double back = BEHIND; back >= BEHIND_LEAST - 1e-9; back -= .5) {
            // Never pulled in front of the target: at the floor's edge there is no way out behind them to cut off.
            Vec3d raw = heading.add(away.multiply(back));
            Vec3d middle = new Vec3d(Math.round(raw.x), arena.floor(), Math.round(raw.z));
            if (!arena.inside(middle, MARGIN + 1)) continue;
            Vec3d a = middle.add(across.multiply(LENGTH / 2)), b = middle.subtract(across.multiply(LENGTH / 2));
            Vec3d endA = middle.lerp(a, arena.exit(middle, a, MARGIN)), endB = middle.lerp(b, arena.exit(middle, b, MARGIN));
            double length = endA.distanceTo(endB);
            if (length > bestLength + 1e-6) { bestLength = length; best = new Vec3d[]{endA, endB}; }
            if (length >= LENGTH * .75) break;
        }
        return bestLength >= SHORTEST ? best : null;
    }

    /** The floor cells (x, z) the wall stands on: every cell whose middle is within HALF_THICK of the line. */
    public static List<long[]> cells(Arena arena, Vec3d a, Vec3d b) {
        List<long[]> cells = new ArrayList<>();
        int x0 = (int) Math.floor(Math.min(a.x, b.x) - 2), x1 = (int) Math.ceil(Math.max(a.x, b.x) + 2);
        int z0 = (int) Math.floor(Math.min(a.z, b.z) - 2), z1 = (int) Math.ceil(Math.max(a.z, b.z) + 2);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            double cx = x + .5, cz = z + .5;
            if (GroundZone.segment(cx, cz, a.x, a.z, b.x, b.z) >= HALF_THICK) continue;
            if (!arena.inside(new Vec3d(cx, arena.floor(), cz), .5)) continue;
            cells.add(new long[]{x, z});
        }
        return cells;
    }

    /**
     * Where a body standing on the line goes: straight off it to the nearer side, clear of the wall, its feet on the
     * floor. {@code feet} is the body's position; {@code halfWidth} half its width.
     */
    public static Vec3d shove(Arena arena, Vec3d a, Vec3d b, Vec3d feet, double halfWidth) {
        Vec3d along = new Vec3d(b.x - a.x, 0, b.z - a.z);
        double length = along.length();
        along = length < 1e-6 ? new Vec3d(1, 0, 0) : along.multiply(1 / length);
        Vec3d normal = new Vec3d(-along.z, 0, along.x);
        double side = (feet.x - a.x) * normal.x + (feet.z - a.z) * normal.z;
        double sign = side >= 0 ? 1 : -1;
        // Off the band of cells (whose middles reach HALF_THICK, and whose sides half a diagonal further), body and all.
        double clear = HALF_THICK + Math.sqrt(.5) + halfWidth + .1;
        Vec3d to = feet.add(normal.multiply(sign * clear - side));
        return arena.onFloor(to, .5);
    }

    @Override public Casting begin(Cast cast) {
        ServerPlayerEntity target = cast.target();
        if (target == null) return null;
        Vec3d[] line = line(cast.arena(), cast.boss().getEntityPos(), cast.lead(target, LEAD_SECONDS));
        if (line == null) return null;
        return new Rising(cast, line[0], line[1]);
    }

    private static final class Rising implements Casting {
        private final Cast cast;
        private final Vec3d a, b;
        private final FirstWizardEffectEntity effect;
        private Wall wall;

        Rising(Cast cast, Vec3d a, Vec3d b) {
            this.cast = cast; this.a = a; this.b = b;
            Vec3d middle = a.lerp(b, .5);
            cast.aim(middle);
            cast.focus(null);
            effect = cast.effect(KIND, middle, TIMING.release(), gone() + 24).size(a.distanceTo(b) / 2).dir(b.subtract(a).multiply(.5));
            cast.world().playSound(null, middle.x, middle.y, middle.z, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 1.6f, .5f);
        }

        @Override public void tick(int t) {
            ServerPlayerEntity target = cast.target();
            if (t < TIMING.release()) {
                if (target != null) cast.face(target.getEntityPos(), FirstWizardRules.AIM_TURN);
                return;
            }
            if (t == TIMING.release()) {
                wall = new Wall(cast, a, b, effect);
                effect.stage(1);
                cast.linger(wall);
                Vec3d m = a.lerp(b, .5);
                cast.world().playSound(null, m.x, m.y, m.z, SoundEvents.BLOCK_ANCIENT_DEBRIS_BREAK, SoundCategory.HOSTILE, 2.2f, .5f);
                cast.world().playSound(null, m.x, m.y, m.z, SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 1.4f, .4f);
            }
        }

        @Override public void cancel() { if (wall == null) effect.discard(); }
    }

    /** The wall from its first row to its last falling; ticked by the fight after the copy is free. */
    private static final class Wall implements Lingering {
        private final Cast cast;
        private final Vec3d a, b;
        private final FirstWizardEffectEntity effect;
        private final List<long[]> cells;
        /** The blocks it set, per row from the floor up. */
        private final List<List<BlockPos>> rows = new ArrayList<>();
        private final long start;
        private int row, fallen;

        Wall(Cast cast, Vec3d a, Vec3d b, FirstWizardEffectEntity effect) {
            this.cast = cast; this.a = a; this.b = b; this.effect = effect;
            this.cells = cells(cast.arena(), a, b);
            this.start = cast.now();
            for (int i = 0; i < HEIGHT; i++) rows.add(new ArrayList<>());
            rise(cast.world());
        }

        @Override public boolean tick(ServerWorld world, long now) {
            int age = (int) (now - start);
            if (row < HEIGHT) { rise(world); return true; }
            int crumbling = age - (crumbles() - TIMING.release());
            if (crumbling < 0) return true;
            if (crumbling == 0) {
                effect.stage(2);
                Vec3d m = a.lerp(b, .5);
                world.playSound(null, m.x, m.y + 2, m.z, SoundEvents.BLOCK_DEEPSLATE_BRICKS_BREAK, SoundCategory.HOSTILE, 2f, .5f);
            }
            // From the top: one row every CRUMBLE_EVERY ticks.
            while (fallen < HEIGHT && crumbling >= fallen * CRUMBLE_EVERY) {
                for (BlockPos pos : rows.get(HEIGHT - 1 - fallen)) { cast.restoreBlock(pos); FirstWizardWallBlock.release(world, pos); }
                rows.get(HEIGHT - 1 - fallen).clear();
                fallen++;
            }
            return fallen < HEIGHT;
        }

        /** The next row up: first anyone in the wall's way is struck and set aside, then the row is set where there is air. */
        private void rise(ServerWorld world) {
            int floorY = (int) Math.floor(cast.arena().floor() + 1e-6);
            clear(floorY);
            int y = floorY + row;
            Box boss = cast.boss().getBoundingBox().expand(.3);
            BlockState state = com.anton.elementalwands.registry.ModSpellBlocks.FIRST_WIZARD_WALL.getDefaultState();
            for (long[] cell : cells) {
                BlockPos pos = new BlockPos((int) cell[0], y, (int) cell[1]);
                if (boss.intersects(new Box(pos))) continue;     // never round the copy itself
                if (!world.getBlockState(pos).isAir()) continue;  // only air: the floor and anything placed stay
                if (cast.placeBlock(pos, state)) { rows.get(row).add(pos); FirstWizardWallBlock.hold(world, pos); }
            }
            row++;
        }

        /** Players inside the wall's volume take a medium hit (once) and are set down on the nearer side. */
        private void clear(int floorY) {
            for (ServerPlayerEntity player : cast.players()) {
                Box body = player.getBoundingBox();
                if (body.minY >= floorY + HEIGHT || body.maxY <= floorY) continue;
                if (!touches(body)) continue;
                cast.hit(player, FirstWizardRules.MEDIUM, Cast.Hit.SPELL, effect);
                Vec3d to = shove(cast.arena(), a, b, player.getEntityPos(), player.getWidth() / 2);
                Vec3d push = to.subtract(player.getEntityPos()).multiply(1, 0, 1);
                player.fallDistance = 0;
                player.requestTeleport(to.x, Math.max(player.getY(), cast.arena().floor()), to.z);
                if (push.lengthSquared() > 1e-6) {
                    Vec3d v = push.normalize().multiply(.45);
                    player.setVelocity(v.x, .25, v.z);
                    player.velocityModified = true;
                }
            }
        }

        private boolean touches(Box body) {
            for (long[] cell : cells) {
                if (body.maxX > cell[0] && body.minX < cell[0] + 1 && body.maxZ > cell[1] && body.minZ < cell[1] + 1) return true;
            }
            return false;
        }

        @Override public void cancel(ServerWorld world) {
            for (List<BlockPos> each : rows) { for (BlockPos pos : each) { cast.restoreBlock(pos); FirstWizardWallBlock.release(world, pos); } each.clear(); }
            effect.discard();
        }
    }
}
