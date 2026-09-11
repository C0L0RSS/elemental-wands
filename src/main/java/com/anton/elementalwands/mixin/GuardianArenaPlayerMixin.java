package com.anton.elementalwands.mixin;

import com.anton.elementalwands.arena.GuardianArenaManager;
import java.util.Set;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers pearls, old rifts, commands, and dimension travel as well as wand destination checks. */
@Mixin(ServerPlayerEntity.class)
public abstract class GuardianArenaPlayerMixin {
    @Inject(method="requestTeleport",at=@At("HEAD"),cancellable=true)
    private void constrainRequest(double x,double y,double z,CallbackInfo ci) {
        ServerPlayerEntity player=(ServerPlayerEntity)(Object)this;
        if (!GuardianArenaManager.canTeleport(player,(ServerWorld)player.getEntityWorld(),new Vec3d(x,y,z))) ci.cancel();
    }
    @Inject(method="teleport",at=@At("HEAD"),cancellable=true)
    private void constrainTeleport(ServerWorld world,double x,double y,double z,Set<PositionFlag> flags,float yaw,float pitch,boolean resetCamera,CallbackInfoReturnable<Boolean> ci) {
        ServerPlayerEntity player=(ServerPlayerEntity)(Object)this;
        Vec3d target=new Vec3d(flags.contains(PositionFlag.X)?x+player.getX():x,
                flags.contains(PositionFlag.Y)?y+player.getY():y,flags.contains(PositionFlag.Z)?z+player.getZ():z);
        if (!GuardianArenaManager.canTeleport(player,world,target)) ci.setReturnValue(false);
    }
    @Inject(method="teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;",at=@At("HEAD"),cancellable=true)
    private void constrainPortal(TeleportTarget target,CallbackInfoReturnable<ServerPlayerEntity> ci) {
        if (!GuardianArenaManager.canTeleport((ServerPlayerEntity)(Object)this,target.world(),target.position())) ci.setReturnValue(null);
    }
}
