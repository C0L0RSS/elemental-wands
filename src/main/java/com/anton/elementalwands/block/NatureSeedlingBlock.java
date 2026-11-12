package com.anton.elementalwands.block;

import com.anton.elementalwands.util.FlowerTurretManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The base of a Sentry Bloom flower: roots, leaves and stem. It has no collision, so nothing
 * stands on it or is stopped by it, but a player can aim at its stem: one hit breaks it, and its
 * owner uses it from close up to collect the health it holds. It unfurls through four
 * stages, and while its owner's Full Bloom lasts it is grown: a bigger rosette, a taller stem and a branch for a second head.
 * {@code FlowerTurretManager} owns its lifetime, its heads and its restoration.
 */
public final class NatureSeedlingBlock extends Block {
    public static final MapCodec<NatureSeedlingBlock> CODEC=createCodec(NatureSeedlingBlock::new);
    public static final IntProperty STAGE=IntProperty.of("stage",0,3);
    public static final BooleanProperty GROWN=BooleanProperty.of("grown");
    /** The stem and the head on it, not the spread of the leaves: aiming past a flower stays easy. */
    private static final VoxelShape[] STEM={Block.createCuboidShape(5,0,5,11,6,11),Block.createCuboidShape(4,0,4,12,9,12),
            Block.createCuboidShape(4,0,4,12,12,12),Block.createCuboidShape(4,0,4,12,16,12)};
    private static final VoxelShape GROWN_STEM=Block.createCuboidShape(3,0,3,13,16,13);
    public NatureSeedlingBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(STAGE,0).with(GROWN,false));}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(STAGE,GROWN);}
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){
        return state.get(GROWN) && state.get(STAGE)==3 ? GROWN_STEM : STEM[state.get(STAGE)];
    }
    /** Using the flower with anything but a wand in hand; a wand's own click goes through {@code WandLoadouts}. */
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){
        if(world instanceof ServerWorld server)
            return player instanceof ServerPlayerEntity owner && FlowerTurretManager.collectAt(server,owner,pos) ? ActionResult.SUCCESS : ActionResult.PASS;
        // The client cannot tell whose flower it is; the server decides.
        double reach=FlowerTurretManager.CLICK_REACH+0.5;
        return player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos))<=reach*reach ? ActionResult.SUCCESS : ActionResult.PASS;
    }
}
