package com.anton.elementalwands.crypt;

import com.anton.elementalwands.church.GuardianChurchLoot;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * The graveyard's victory chests: two chests beside the open grave, filled once with bones and
 * the same seeded treasure rolls as a restored church. They never refill.
 */
final class HollowCryptRewards {
    private HollowCryptRewards() {}

    /** The side of the altar that faces the grave: where the polished ledge sits. */
    static Direction front(World world, BlockPos skull) {
        BlockPos lower = skull.down(2);
        for (Direction d : Direction.Type.HORIZONTAL)
            if (world.getBlockState(lower.offset(d)).isOf(Blocks.POLISHED_DEEPSLATE)) return d;
        return null;
    }

    /** Chest positions either side of the grave, two blocks in front of the headstone. */
    static List<BlockPos> chestSpots(BlockPos skull, Direction front) {
        BlockPos floor = skull.down(3).offset(front, 2);
        Direction side = front.rotateYClockwise();
        return List.of(floor.offset(side, 3), floor.offset(side, -3));
    }

    /** Places and fills the chests. Returns the positions that hold a reward chest. */
    static List<BlockPos> place(ServerWorld world, BlockPos skull) {
        world.getChunk(skull.getX() >> 4, skull.getZ() >> 4);
        Direction front = front(world, skull);
        List<BlockPos> placed = new ArrayList<>();
        if (front == null) return placed;
        Direction side = front.rotateYClockwise();
        List<BlockPos> spots = chestSpots(skull, front);
        for (int i = 0; i < spots.size(); i++) {
            BlockPos pos = spots.get(i);
            BlockState here = world.getBlockState(pos);
            if (!here.isAir() && !here.isReplaceable()) continue;
            // Each chest faces the grave between them.
            Direction facing = i == 0 ? side.getOpposite() : side;
            world.setBlockState(pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing));
            if (!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest)) continue;
            List<ItemStack> contents = contents(world, skull, i == 0 ? -1 : 1);
            for (int slot = 0; slot < contents.size(); slot++) chest.setStack(slot, contents.get(slot));
            chest.markDirty();
            placed.add(pos.toImmutable());
        }
        return placed;
    }

    /** Church treasure plus a handful of bones in the empty slots; seeded by the graveyard. */
    static List<ItemStack> contents(ServerWorld world, BlockPos skull, int side) {
        List<ItemStack> contents = new ArrayList<>(GuardianChurchLoot.roll(world, skull, side));
        Random random = new Random(world.getSeed() ^ skull.asLong() * 31 + side);
        int bones = 2 + random.nextInt(3);
        for (int slot = 0; slot < contents.size() && bones > 0; slot++) {
            int at = (slot * 7 + random.nextInt(3)) % contents.size();
            if (!contents.get(at).isEmpty()) continue;
            contents.set(at, new ItemStack(bones == 1 && random.nextInt(4) == 0 ? Items.BONE_BLOCK : Items.BONE, 3 + random.nextInt(8)));
            bones--;
        }
        return contents;
    }
}
