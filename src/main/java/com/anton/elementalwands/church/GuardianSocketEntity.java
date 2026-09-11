package com.anton.elementalwands.church;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class GuardianSocketEntity extends BlockEntity {
    public int layoutVersion;
    @Override protected void readData(net.minecraft.storage.ReadView view) { super.readData(view);layoutVersion=view.getInt("layout_version",0); }
    @Override protected void writeData(net.minecraft.storage.WriteView view) { super.writeData(view);view.putInt("layout_version",layoutVersion); }
    public GuardianSocketEntity(BlockPos pos,BlockState state) { super(GuardianChurchManager.SOCKET_ENTITY,pos,state); }
    public static void tick(World world,BlockPos pos,BlockState state,GuardianSocketEntity entity) {
        if (world.getTime()%20==0) GuardianChurchManager.discover((ServerWorld)world,pos,state);
    }
}
