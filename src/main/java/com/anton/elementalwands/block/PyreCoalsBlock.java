package com.anton.elementalwands.block;

import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.MagmaBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Keep magma contact rules for enemies, with the same ownership as the Pyre front. */
public final class PyreCoalsBlock extends MagmaBlock {
    public static final MapCodec<MagmaBlock> CODEC = createCodec(PyreCoalsBlock::new);
    public PyreCoalsBlock(Settings settings) { super(settings); }
    @Override public MapCodec<MagmaBlock> getCodec() { return CODEC; }
    @Override public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (world instanceof ServerWorld serverWorld
                && WandAllies.protectedFrom(serverWorld, TemporaryBlockManager.casterAt(serverWorld, pos), entity)) return;
        if(world instanceof ServerWorld sw && !entity.bypassesSteppingEffects() && entity instanceof net.minecraft.entity.LivingEntity) {
            var id=TemporaryBlockManager.casterAt(sw,pos);
            com.anton.elementalwands.util.SpellCombat.damage(entity,sw,world.getDamageSources().hotFloor(),1,id==null?null:sw.getPlayerByUuid(id),com.anton.elementalwands.data.WizardAffinity.FIRE);
        }
    }
}
