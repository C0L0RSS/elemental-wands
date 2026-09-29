package com.anton.elementalwands.crypt;

import com.anton.elementalwands.church.GuardianChurchLoot;
import com.anton.elementalwands.registry.ModBlocks;
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
 * The graveyard's victory chests: two chests either side of the mausoleum steps (beside the open
 * grave at an old headstone graveyard), filled once with bones and the same seeded treasure rolls
 * as a restored church. They never refill.
 */
final class HollowCryptRewards {
    private HollowCryptRewards() {}

    /** The side of the old altar that faces the grave: where the polished ledge sits. */
    static Direction front(World world, BlockPos skull) {
        BlockPos lower = skull.down(2);
        for (Direction d : Direction.Type.HORIZONTAL)
            if (world.getBlockState(lower.offset(d)).isOf(Blocks.POLISHED_DEEPSLATE)) return d;
        return null;
    }

    /**
     * Chest positions: on the ground either side of the mausoleum steps, facing them, for a site
     * at the veil's bottom-centre cell; else either side of the old altar's grave.
     */
    static List<BlockPos> chestSpots(World world, BlockPos site) {
        BlockState veil = world.getBlockState(site);
        if (veil.isOf(ModBlocks.MAUSOLEUM_VEIL)) {
            Direction facing = veil.get(MausoleumVeilBlock.FACING);
            BlockPos floor = site.down().offset(facing, 4);
            Direction left = facing.rotateYClockwise();
            return List.of(floor.offset(left, 5), floor.offset(left, -5));
        }
        Direction front = front(world, site);
        if (front == null) return List.of();
        BlockPos floor = site.down(3).offset(front, 2);
        Direction side = front.rotateYClockwise();
        return List.of(floor.offset(side, 3), floor.offset(side, -3));
    }

    /** Places and fills the chests. Returns the positions that hold a reward chest. */
    static List<BlockPos> place(ServerWorld world, BlockPos skull) {
        world.getChunk(skull.getX() >> 4, skull.getZ() >> 4);
        List<BlockPos> placed = new ArrayList<>();
        List<BlockPos> spots = chestSpots(world, skull);
        if (spots.isEmpty()) return placed;
        // The first chest lies on this side of the second.
        Direction side = Direction.getFacing(spots.get(0).getX() - spots.get(1).getX(), 0, spots.get(0).getZ() - spots.get(1).getZ());
        for (int i = 0; i < spots.size(); i++) {
            BlockPos pos = spots.get(i);
            BlockState here = world.getBlockState(pos);
            if (!here.isAir() && !here.isReplaceable()) continue;
            // Each chest faces the steps (or grave) between them.
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
