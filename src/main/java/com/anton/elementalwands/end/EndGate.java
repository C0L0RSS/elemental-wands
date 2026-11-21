package com.anton.elementalwands.end;

import com.anton.elementalwands.arena.GuardianArenaJournal.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Difficulty;
import net.minecraft.world.TeleportTarget;

/**
 * The overworld's End portals under this boss: who may go through, where they arrive, and the way back
 * that is recorded for them first. {@code EndPortalArrivalMixin} hands every trip into the End here,
 * so vanilla's obsidian platform is never made. Trips out of the End are vanilla's.
 *
 * <p>The End portal has no collision and a stronghold's has lava under it, so nobody is ever left
 * standing in one: a refused player is put at once on the spot beside the portal that would have been
 * their way back (world spawn if there is none), and told why.
 */
public final class EndGate {
    private EndGate() {}

    /** Players allowed into the arena by the portal and on their way; the teleport check lets them pass once. */
    private static final Map<UUID, Integer> passes = new HashMap<>();
    private static final int PASS = 40;

    static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> passes.remove(handler.getPlayer().getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> passes.clear());
    }

    /**
     * The key to the End: the five Wand Shards. Shards are not in the game yet, so the portal is open to
     * everyone for now. This is the one place the rule lives.
     */
    public static boolean mayEnter(ServerPlayerEntity player) {
        return true;
    }

    /**
     * Where an entity in {@code from} that touched the End portal at {@code portal} goes, or null to keep
     * it out. Players are refused (and put beside the portal) on Peaceful before the win, while another
     * party fights, after they fell in the running fight, and until the arena is laid out. Mounts and
     * vehicles carrying a player never go through: their riders are refused the same way. Ender pearls
     * are lost in the portal, so they never carry their thrower across. Nothing else goes through while
     * a fight runs or before the arena is laid out.
     */
    public static TeleportTarget target(ServerWorld from, Entity entity, BlockPos portal) {
        ServerWorld end = from.getServer().getWorld(EndArena.WORLD);
        if (entity instanceof EnderPearlEntity) { entity.discard(); return null; }
        if (!(entity instanceof ServerPlayerEntity player)) {
            List<ServerPlayerEntity> riders = new ArrayList<>();
            for (Entity passenger : entity.getPassengersDeep()) if (passenger instanceof ServerPlayerEntity p) riders.add(p);
            if (!riders.isEmpty()) {
                // Off first, then the mount and its riders beside the portal; nobody stays in it.
                entity.removeAllPassengers();
                for (ServerPlayerEntity rider : riders) rider.stopRiding();
                Point back = wayBack(from, portal);
                if (back != null) entity.teleport(from, back.x(), back.y(), back.z(), Set.of(), back.yaw(), back.pitch(), false);
                for (ServerPlayerEntity rider : riders)
                    refuse(rider, from, portal, "The End portal takes no mounts or vehicles. Get down and step in on foot.");
                return null;
            }
            if (end == null || EndEncounter.running() || !EndArenaManager.ready()) return null;
            return new TeleportTarget(end, EndArena.arrival(), Vec3d.ZERO, EndArena.ARRIVAL_YAW, 0, Set.of(),
                    TeleportTarget.SEND_TRAVEL_THROUGH_PORTAL_PACKET.then(TeleportTarget.ADD_PORTAL_CHUNK_TICKET));
        }
        if (end == null) return refuse(player, from, portal, "The End portal is still. (The End is not loaded.)");
        if (!EndArenaManager.available()) return refuse(player, from, portal, "The End portal is still. (The End arena's record is unavailable; see the server log.)");
        if (!mayEnter(player)) return refuse(player, from, portal, "The End portal does not open for you yet.");
        boolean free = player.isCreative() || player.isSpectator();
        if (!free) {
            if (!EndArenaManager.defeated() && end.getDifficulty() == Difficulty.PEACEFUL)
                return refuse(player, from, portal, "The End portal is still. (The First Wizard does not wake on Peaceful.)");
            String refusal = EndEncounter.refusal(player);
            if (refusal != null) return refuse(player, from, portal, refusal);
        }
        if (!EndArenaManager.ready()) {
            String problem = EndArenaManager.raise(end);
            if (problem == null) return refuse(player, from, portal, "The island beyond is still being made ready. Try again in a moment.");
            if (EndArenaManager.isHeld()) EndArenaManager.tellOperatorsHeld(from.getServer());
            return refuse(player, from, portal, "The End portal is still. (" + problem + ")");
        }
        if (!record(player, from, portal)) return refuse(player, from, portal, "The End portal is still. (Could not record your way back.)");
        passes.put(player.getUuid(), from.getServer().getTicks() + PASS);
        return new TeleportTarget(end, EndArena.arrival(), Vec3d.ZERO, EndArena.ARRIVAL_YAW, 0, Set.of(),
                TeleportTarget.SEND_TRAVEL_THROUGH_PORTAL_PACKET.then(TeleportTarget.ADD_PORTAL_CHUNK_TICKET).then(arrived -> {
                    passes.remove(arrived.getUuid());
                    if (arrived instanceof ServerPlayerEntity p && !free) EndEncounter.arrived(p);
                }));
    }

    /** A player the portal let through may be moved into the arena while a fight runs. */
    static boolean hasPass(ServerPlayerEntity player) {
        Integer until = passes.get(player.getUuid());
        if (until == null) return false;
        if (until < player.getEntityWorld().getServer().getTicks()) { passes.remove(player.getUuid()); return false; }
        return true;
    }

    /** Refused: out of the portal at once, onto the spot beside it (world spawn if there is none), and told why. Nothing is saved. */
    private static TeleportTarget refuse(ServerPlayerEntity player, ServerWorld from, BlockPos portal, String message) {
        Point back = wayBack(from, portal);
        if (back != null) EndArenaManager.transfer(player, from, new Vec3d(back.x(), back.y(), back.z()), back.yaw(), back.pitch());
        else EndArenaManager.toSpawn(player);
        player.sendMessage(Text.literal(message), false);
        return null;
    }

    // ------------------------------------------------------------------ the way back

    /**
     * Saves the player's way back before they move. With no safe spot beside the portal the way back is
     * world spawn, never the portal itself.
     */
    private static boolean record(ServerPlayerEntity player, ServerWorld from, BlockPos portal) {
        Point back = wayBack(from, portal);
        if (back != null) return EndArenaManager.recordReturn(player, back);
        EndArenaManager.forgetReturn(player);
        return true;
    }

    /**
     * A safe place to stand beside the portal: ground under the feet, room for the body, and two blocks
     * clear of every portal block (or the player would fall straight back in). Facing the portal. Null if
     * none is found near it.
     */
    static Point wayBack(ServerWorld world, BlockPos touched) {
        Set<BlockPos> portal = portalBlocks(world, touched);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE, y0 = touched.getY();
        double cx = 0, cz = 0;
        for (BlockPos p : portal) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            cx += p.getX() + .5; cz += p.getZ() + .5;
        }
        cx /= portal.size(); cz /= portal.size();
        Point best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos.Mutable feet = new BlockPos.Mutable();
        for (int reach = 2; reach <= 7 && best == null; reach++)
            for (int x = minX - reach; x <= maxX + reach; x++)
                for (int z = minZ - reach; z <= maxZ + reach; z++) {
                    int gap = Math.max(Math.max(minX - x, x - maxX), Math.max(minZ - z, z - maxZ));
                    if (gap != reach) continue;
                    for (int dy = -1; dy <= 3; dy++) {
                        feet.set(x, y0 + dy, z);
                        Double standing = standingHeight(world, feet);
                        if (standing == null || nearPortal(portal, x, z)) continue;
                        double score = (x + .5 - cx) * (x + .5 - cx) + (z + .5 - cz) * (z + .5 - cz) + Math.abs(dy) * 4;
                        if (score < bestScore) {
                            bestScore = score;
                            float yaw = (float) Math.toDegrees(Math.atan2(-(cx - (x + .5)), cz - (z + .5)));
                            best = new Point(world.getRegistryKey().getValue().toString(), x + .5, standing, z + .5, yaw, 0);
                        }
                    }
                }
        return best;
    }

    /** The portal blocks joined to the one touched, on its level. */
    private static Set<BlockPos> portalBlocks(ServerWorld world, BlockPos touched) {
        Set<BlockPos> out = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        out.add(touched.toImmutable());
        queue.add(touched.toImmutable());
        while (!queue.isEmpty() && out.size() < 256) {
            BlockPos at = queue.poll();
            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockPos next = at.offset(d);
                if (!out.contains(next) && world.getBlockState(next).isOf(Blocks.END_PORTAL)) { out.add(next); queue.add(next); }
            }
        }
        return out;
    }

    private static boolean nearPortal(Set<BlockPos> portal, int x, int z) {
        for (BlockPos p : portal) if (Math.abs(p.getX() - x) < 2 && Math.abs(p.getZ() - z) < 2) return true;
        return false;
    }

    /** The feet height for standing in this cell on what is under it, or null if it is not a safe place to stand. */
    private static Double standingHeight(ServerWorld world, BlockPos feet) {
        BlockPos below = feet.down();
        BlockState ground = world.getBlockState(below);
        if (unsafe(ground)) return null;
        VoxelShape shape = ground.getCollisionShape(world, below);
        if (shape.isEmpty()) return null;
        double top = shape.getMax(Direction.Axis.Y);
        if (top < .4) return null;
        for (BlockPos body : new BlockPos[]{feet, feet.up()}) {
            BlockState state = world.getBlockState(body);
            if (unsafe(state) || !state.getCollisionShape(world, body).isEmpty() || !state.getFluidState().isEmpty()) return null;
        }
        if (top > 1 && !world.getBlockState(feet.up(2)).getCollisionShape(world, feet.up(2)).isEmpty()) return null;
        return below.getY() + Math.min(top, 1.5);
    }

    private static boolean unsafe(BlockState state) {
        return state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.LAVA) || state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)
                || state.isOf(Blocks.MAGMA_BLOCK) || state.isOf(Blocks.CACTUS) || state.isOf(Blocks.SWEET_BERRY_BUSH)
                || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.CAMPFIRE) || state.isOf(Blocks.SOUL_CAMPFIRE)
                || !state.getFluidState().isEmpty() && state.getFluidState().isIn(net.minecraft.registry.tag.FluidTags.LAVA);
    }
}
