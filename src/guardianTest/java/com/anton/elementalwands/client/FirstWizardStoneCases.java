package com.anton.elementalwands.client;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEffectEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.spell.FaultlineSpell;
import com.anton.elementalwands.entity.firstwizard.spell.FireballSpell;
import com.anton.elementalwands.entity.firstwizard.spell.StoneWallSpell;
import com.anton.elementalwands.registry.ModSpellBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * The Stone Wall and Faultline cases of {@link FirstWizardFightSmoke}, staged on the server like the others. The copy
 * stands at the mark facing -Z, the player 14 blocks in front of it.
 *
 * <ul>
 *   <li>{@code wall-rise}: the ink line, the wall rising where intended (across the line to the player, a few blocks
 *       behind them, about 27 by 5 by 2, every block the boss's own, unbreakable and set to clear itself after a
 *       crash), standing about 6 s, crumbling from the top, and the floor and the air round it block-for-block as
 *       they were.</li>
 *   <li>{@code wall-block}: while it stands a body walked into it stops at its face, and a Fireball thrown at a player
 *       behind it bursts on it and does not reach them.</li>
 *   <li>{@code wall-shove}: a player moved onto the line before it rises takes one medium hit and is set down on the
 *       nearer side, never inside it.</li>
 *   <li>{@code wall-stop}: the fight stopped while it stands: every block goes at once and the floor is as it was.</li>
 *   <li>{@code wall-fireball}: the combination: a Fireball cast as the wall's follow-up reaches the player in front of
 *       it, not the wall.</li>
 *   <li>{@code faultline-hit}: one medium hit, thrown about 7 blocks, and the fall forgiven.</li>
 *   <li>{@code faultline-sidestep}: moved 6 aside after the aim lock: untouched, not thrown.</li>
 *   <li>{@code faultline-jump}: jumping as the wave arrives does not dodge it.</li>
 *   <li>{@code faultline-wall}: a low wall across its path stops every lane; the player behind it is untouched.</li>
 * </ul>
 */
final class FirstWizardStoneCases {
    private FirstWizardStoneCases() {}

    static final List<String> NAMES = List.of("wall-rise", "wall-block", "wall-shove", "wall-stop", "wall-fireball",
            "faultline-hit", "faultline-sidestep", "faultline-jump", "faultline-wall");

    /** Where every check looks for the wall and what it must leave alone: round the line it rises on. */
    private static final int X0 = -24, X1 = 25, Z0 = -34, Z1 = 6, BELOW = 2, ABOVE = 8;

    // The server's measurements for the case in hand (server thread only).
    private static BlockState[] before;
    private static int most, firstSeen, fullAt, firstGone, allGone, standingAt60, stoppedLeft = -1, lingeringAfterStop = -1;
    private static boolean unbreakable, scheduled, offFloor, inWallAfter, staged, fireballCast, moved;
    private static double standStopZ = Double.NaN, wallNear = Double.NaN, wallFar = Double.NaN, wallMinX, wallMaxX, top, heightAtHit = -1, prevHeight;
    private static int wallTop;
    private static Vec3d burstAt, shovedTo;
    private static int countAtTest = -1, tickAtTest = -1;
    private static final List<Integer> woundTicks = new ArrayList<>();

    // Set on the server thread, read by the client's.
    private static volatile boolean reporting, jumpWanted;
    private static long start;

    /**
     * Client: the jump the server asked for, and the player's position sent to the server. The camera is an armor stand,
     * and vanilla only reports the player's movement while the player is the camera, so without this the server never
     * sees a throw or a jump (in play the player is the camera).
     */
    static void client(net.minecraft.client.MinecraftClient c) {
        if (c.player == null) return;
        if (jumpWanted && c.player.isOnGround()) { jumpWanted = false; c.player.jump(); }
        if (reporting && c.getCameraEntity() != c.player && c.player.networkHandler != null)
            c.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(
                    c.player.getX(), c.player.getY(), c.player.getZ(), c.player.isOnGround(), c.player.horizontalCollision));
    }

    /**
     * On the server, every tick of a case from t = 1. The case's own clock is the server's, from the cast's start: the
     * client's tasks can reach the server in batches, so nothing is staged by the client's tick count.
     */
    static void during(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, ArmorStandEntity camera, List<Float> wounds,
                       String name, int clientTick, Vec3d home, BiConsumer<Double, Double> place) {
        ServerWorld w = server.getOverworld();
        int floorY = (int) Math.floor(home.y + 1e-6);
        if (clientTick == 1) start = w.getTime() - Math.max(0, (long) k.castAge(0));
        int t = (int) (w.getTime() - start);
        if (clientTick == 1) {
            reset();
            reporting = name.startsWith("faultline");
            before = snapshot(w, home, floorY);
            if (name.startsWith("wall")) aim(camera, home.add(9, 11, 13), home.add(0, 1.5, -15));
            else aim(camera, home.add(21, 12, -2), home.add(0, 0, -16));
            if (name.equals("faultline-wall")) for (int x = -8; x <= 8; x++) for (int y = 0; y < 2; y++)
                w.setBlockState(new BlockPos((int) Math.floor(home.x) + x, floorY + y, (int) Math.floor(home.z) - 8), Blocks.COBBLESTONE.getDefaultState());
        }
        double height = p.getY() - home.y;
        if (!wounds.isEmpty() && woundTicks.size() < wounds.size()) { while (woundTicks.size() < wounds.size()) woundTicks.add(t); if (heightAtHit < 0) heightAtHit = prevHeight; }
        prevHeight = height;
        top = Math.max(top, height);
        if (name.startsWith("wall")) wallTick(w, p, k, name, t, home, floorY, place);
        else faultTick(p, name, t, place);
        FirstWizardEffectEntity ground = effect(w, home, FireballSpell.GROUND_KIND);
        if (ground != null && burstAt == null) burstAt = ground.getEntityPos();
    }

    private static void wallTick(ServerWorld w, ServerPlayerEntity p, FirstWizardEntity k, String name, int t, Vec3d home, int floorY, BiConsumer<Double, Double> place) {
        int count = 0;
        int x0 = (int) Math.floor(home.x), z0 = (int) Math.floor(home.z);
        BlockPos.Mutable at = new BlockPos.Mutable();
        double near = -1e9, far = 1e9, minX = 1e9, maxX = -1e9;
        int high = -1;
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) for (int y = 0; y <= 6; y++) {
            at.set(x0 + x, floorY + y, z0 + z);
            BlockState s = w.getBlockState(at);
            if (!s.isOf(ModSpellBlocks.FIRST_WIZARD_WALL)) continue;
            count++;
            near = Math.max(near, at.getZ() + 1); far = Math.min(far, at.getZ());
            minX = Math.min(minX, at.getX()); maxX = Math.max(maxX, at.getX() + 1);
            high = Math.max(high, y);
            if (k.arena() != null && !k.arena().inside(Vec3d.ofCenter(at), .49)) offFloor = true;
            if (!unbreakable && s.getHardness(w, at) < 0 && s.getBlock().getBlastResistance() >= 1200) unbreakable = true;
            if (!scheduled && w.getBlockTickScheduler().isQueued(at, s.getBlock())) scheduled = true;
        }
        if (count > 0 && firstSeen < 0) firstSeen = t;
        if (count > most) { most = count; fullAt = t; wallNear = near; wallFar = far; wallMinX = minX; wallMaxX = maxX; wallTop = high; }
        if (most > 0 && count < most && firstGone < 0) firstGone = t;
        if (most > 0 && count == 0 && allGone < 0) allGone = t;
        if (t >= 60 && standingAt60 < 0) standingAt60 = count;
        switch (name) {
            case "wall-block" -> {
                if (t >= 40 && !staged) {
                    staged = true;
                    countAtTest = count; tickAtTest = t;
                    // A body walked straight at it from the copy's side stops at its face.
                    ArmorStandEntity body = new ArmorStandEntity(EntityType.ARMOR_STAND, w);
                    body.refreshPositionAndAngles(home.x, home.y, home.z - 12, 0, 0);
                    w.spawnEntity(body);
                    body.move(MovementType.SELF, new Vec3d(0, 0, -12));
                    standStopZ = body.getZ();
                    body.discard();
                    // The player goes behind it; a Fireball is thrown at them.
                    place.accept(0.0, -23.0);
                }
                if (t >= 44 && staged && !fireballCast) { fireballCast = true; k.testMove(p, FirstWizardRules.Move.FIREBALL); }
            }
            case "wall-shove" -> {
                // Onto the line once it is fixed and before it rises, a little to its far side.
                if (t >= 6 && t < 18 && !moved) { moved = true; place.accept(0.0, -17.8); }
                if (t >= 50 && shovedTo == null) {
                    shovedTo = p.getEntityPos();
                    Box body = p.getBoundingBox();
                    for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(body.minX, body.minY, body.minZ), BlockPos.ofFloored(body.maxX, body.maxY, body.maxZ)))
                        if (w.getBlockState(pos).isOf(ModSpellBlocks.FIRST_WIZARD_WALL)) inWallAfter = true;
                }
            }
            case "wall-stop" -> {
                if (t >= 60 && stoppedLeft < 0) {
                    k.stop();
                    int left = 0;
                    for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) for (int y = 0; y <= 6; y++)
                        if (w.getBlockState(at.set(x0 + x, floorY + y, z0 + z)).isOf(ModSpellBlocks.FIRST_WIZARD_WALL)) left++;
                    stoppedLeft = left;
                    lingeringAfterStop = k.lingering();
                }
            }
            case "wall-fireball" -> {
                // As the follow-up: a Fireball as soon as the wall's caster is free.
                if (t >= StoneWallSpell.TIMING.free() + FirstWizardRules.FOLLOW_GAP && !fireballCast) { fireballCast = true; k.testMove(p, FirstWizardRules.Move.FIREBALL); }
            }
            default -> {}
        }
    }

    private static void faultTick(ServerPlayerEntity p, String name, int t, BiConsumer<Double, Double> place) {
        switch (name) {
            // Six aside once the aim has locked.
            case "faultline-sidestep" -> { if (t > FaultlineSpell.TIMING.aimLock() && t < FaultlineSpell.TIMING.release() && !moved) { moved = true; place.accept(6.0, -14.0); } }
            // A real jump a few ticks before the wave reaches them (14 out: about 9 ticks after the release): in the air as it arrives.
            case "faultline-jump" -> { if (t >= FaultlineSpell.TIMING.release() + 5 && !moved) { moved = true; jumpWanted = true; } }
            default -> {}
        }
    }

    /** After a case: its checks, its notes, and the floor put back where a case changed it. */
    static void verify(MinecraftServer server, ServerPlayerEntity p, FirstWizardEntity k, String name, List<Float> hurt, Vec3d home,
                       List<String> notes, Consumer<String> fail) {
        ServerWorld w = server.getOverworld();
        reporting = false;
        int floorY = (int) Math.floor(home.y + 1e-6);
        float medium = FirstWizardRules.MEDIUM;
        switch (name) {
            case "wall-rise" -> {
                double length = wallMaxX - wallMinX, thick = wallNear - wallFar;
                notes.add(String.format(Locale.ROOT, "most %d blocks, x %.0f..%.0f (%.0f long), z %.0f..%.0f (%.0f thick), %d tall; first at %d, full at %d, first gone %d, all gone %d; at 60 %d",
                        most, wallMinX, wallMaxX, length, wallFar, wallNear, thick, wallTop + 1, firstSeen, fullAt, firstGone, allGone, standingAt60));
                double playerZ = home.z - 14;
                if (most == 0) { fail.accept("no wall rose"); break; }
                if (length < 24 || length > 30) fail.accept("the wall should be about 27 long: " + length);
                if (thick < 1.9 || thick > 3.1) fail.accept("the wall should be about 2 thick: " + thick);
                if (wallTop + 1 != StoneWallSpell.HEIGHT) fail.accept("the wall should be " + StoneWallSpell.HEIGHT + " tall: " + (wallTop + 1));
                double behind = playerZ - (wallNear + wallFar) / 2;
                if (behind < 2 || behind > 6) fail.accept("the wall should rise a few blocks behind the player (from the copy): " + behind);
                if (Math.abs((wallMinX + wallMaxX) / 2 - home.x) > 1.5) fail.accept("the wall should be centred across the line to the player");
                if (firstSeen < StoneWallSpell.TIMING.release() - 3) fail.accept("the wall rose before its ink line had warned for about a second: tick " + firstSeen);
                if (fullAt - firstSeen > 7) fail.accept("the wall should rise quickly; it took " + (fullAt - firstSeen) + " ticks");
                if (standingAt60 != most) fail.accept("the whole wall should stand at 3 s");
                double stood = (firstGone - fullAt) / 20.0;
                if (stood < 5.2 || stood > 7) fail.accept("the wall should stand about 6 s; it stood " + stood);
                if (allGone < 0 || allGone - firstGone > 20) fail.accept("the wall should crumble away within a second; gone at " + allGone);
                if (!unbreakable) fail.accept("the wall's blocks should be unbreakable and blast-proof");
                if (!scheduled) fail.accept("the wall's blocks should be set to clear themselves after a crash");
                if (offFloor) fail.accept("a wall block stood off the floor");
                compare(w, home, floorY, fail);
                if (k.lingering() > 0) fail.accept("nothing of the wall should still be running: " + k.lingering());
                if (!hurt.isEmpty()) fail.accept("nobody stood on its line; took " + hurt);
            }
            case "wall-block" -> {
                notes.add(String.format(Locale.ROOT, "%d wall blocks at case tick %d; a body walked at it stopped at z %.2f (the face at %.0f); the Fireball burst at %s; wounds %s", countAtTest, tickAtTest, standStopZ, wallNear, fmt(burstAt), hurt));
                if (Double.isNaN(standStopZ) || standStopZ < wallNear) fail.accept("a body should stop at the wall's face; it reached " + standStopZ);
                if (burstAt == null) fail.accept("the Fireball never burst");
                else if (burstAt.z < wallFar - .5 || burstAt.z > wallNear + 2.5) fail.accept("the Fireball should burst on the wall; burst at " + fmt(burstAt));
                if (!hurt.isEmpty()) fail.accept("the player behind the wall should be untouched; took " + hurt);
            }
            case "wall-shove" -> {
                notes.add("wounds " + hurt + "; set down at " + fmt(shovedTo) + " (the wall " + wallFar + ".." + wallNear + ")");
                if (hurt.size() != 1 || Math.abs(hurt.getFirst() - medium) > .01f) fail.accept("a player on the line should take one medium hit (" + medium + "); took " + hurt);
                if (inWallAfter) fail.accept("the player was left inside the wall");
                if (shovedTo == null || shovedTo.z > wallFar - .25) fail.accept("the player should be set down on the nearer (far) side; at " + fmt(shovedTo));
            }
            case "wall-stop" -> {
                notes.add("blocks left the moment it stopped " + stoppedLeft + ", lingering " + lingeringAfterStop + " (stood " + most + ")");
                if (most == 0) fail.accept("no wall rose");
                if (stoppedLeft != 0) fail.accept("stopping the fight should take the wall down at once; left " + stoppedLeft);
                if (lingeringAfterStop != 0) fail.accept("nothing should run after the stop");
                compare(w, home, floorY, fail);
            }
            case "wall-fireball" -> {
                notes.add("the Fireball burst at " + fmt(burstAt) + " (the wall " + wallFar + ".." + wallNear + "); wounds " + hurt);
                if (hurt.isEmpty() || Math.abs(hurt.getFirst() - FirstWizardRules.HEAVY) > .01f) fail.accept("the Fireball should reach the player in front of the wall (a heavy hit); took " + hurt);
                if (burstAt != null && burstAt.z <= wallNear + .3) fail.accept("the Fireball burst on the wall, not the player; at " + fmt(burstAt));
            }
            case "faultline-hit", "faultline-jump" -> {
                notes.add(String.format(Locale.ROOT, "wounds %s at ticks %s; %.2f up when struck; thrown %.1f high; on the ground at the end %s", hurt, woundTicks, heightAtHit, top, p.isOnGround()));
                if (hurt.size() != 1 || Math.abs(hurt.getFirst() - medium) > .01f) fail.accept("one medium hit (" + medium + ") and no fall damage; took " + hurt);
                if (top < 5.5 || top > 8.5) fail.accept("the player should be thrown about 7 blocks: " + top);
                if (name.equals("faultline-jump") && heightAtHit < .3) fail.accept("the player should have been in the air when it reached them: " + heightAtHit);
            }
            case "faultline-sidestep" -> {
                notes.add("wounds " + hurt + "; highest " + String.format(Locale.ROOT, "%.2f", top));
                if (!hurt.isEmpty()) fail.accept("a sidestep after the aim lock should dodge it; took " + hurt);
                if (top > 1) fail.accept("a player it missed should not be thrown: " + top);
            }
            case "faultline-wall" -> {
                notes.add("wounds " + hurt + "; highest " + String.format(Locale.ROOT, "%.2f", top));
                if (!hurt.isEmpty()) fail.accept("a wall across its path should stop the wave; took " + hurt);
                if (top > 1) fail.accept("a player behind a wall should not be thrown: " + top);
                for (int x = -8; x <= 8; x++) for (int y = 0; y < 2; y++)
                    w.setBlockState(new BlockPos((int) Math.floor(home.x) + x, floorY + y, (int) Math.floor(home.z) - 8), Blocks.AIR.getDefaultState());
            }
            default -> {}
        }
    }

    private static void reset() {
        most = 0; firstSeen = fullAt = firstGone = allGone = -1; standingAt60 = -1; stoppedLeft = lingeringAfterStop = -1;
        unbreakable = scheduled = offFloor = inWallAfter = staged = fireballCast = moved = jumpWanted = false;
        standStopZ = wallNear = wallFar = Double.NaN; top = 0; heightAtHit = -1; prevHeight = 0; wallTop = -1;
        burstAt = shovedTo = null;
        countAtTest = tickAtTest = -1;
        woundTicks.clear();
    }

    private static BlockState[] snapshot(ServerWorld w, Vec3d home, int floorY) {
        int x0 = (int) Math.floor(home.x), z0 = (int) Math.floor(home.z), i = 0;
        BlockState[] states = new BlockState[(X1 - X0 + 1) * (Z1 - Z0 + 1) * (BELOW + ABOVE)];
        BlockPos.Mutable at = new BlockPos.Mutable();
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) for (int y = -BELOW; y < ABOVE; y++)
            states[i++] = w.getBlockState(at.set(x0 + x, floorY + y, z0 + z));
        return states;
    }

    /** The floor and the air over it, block for block, as they were when the case began. */
    private static void compare(ServerWorld w, Vec3d home, int floorY, Consumer<String> fail) {
        BlockState[] now = snapshot(w, home, floorY);
        int changed = 0;
        for (int i = 0; i < now.length; i++) if (before == null || !now[i].equals(before[i])) changed++;
        if (changed > 0) fail.accept(changed + " blocks of the floor and the air over it are not as they were");
    }

    private static FirstWizardEffectEntity effect(ServerWorld w, Vec3d home, String kind) {
        var found = w.getEntitiesByClass(FirstWizardEffectEntity.class, new Box(home, home).expand(70), e -> e.kind().equals(kind) && e.isAlive());
        return found.isEmpty() ? null : found.getFirst();
    }

    private static void aim(ArmorStandEntity stand, Vec3d eye, Vec3d at) {
        Vec3d look = at.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z)), pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        stand.refreshPositionAndAngles(eye.x, eye.y - stand.getStandingEyeHeight(), eye.z, yaw, pitch);
        stand.setHeadYaw(yaw); stand.setBodyYaw(yaw);
    }

    private static String fmt(Vec3d v) { return v == null ? "none" : String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z); }
}
