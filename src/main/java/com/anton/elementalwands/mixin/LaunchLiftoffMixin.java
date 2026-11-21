package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.SpringbloomManager;
import com.anton.elementalwands.util.UpdraftManager;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports each accepted move packet to the launch managers once vanilla has applied its position
 * and ground flag. Rejected moves return before this call, and server-side moves never reach it.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class LaunchLiftoffMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method = "onPlayerMove", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;setMovement(ZZLnet/minecraft/util/math/Vec3d;)V"))
    private void accepted(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        SpringbloomManager.acceptedMove(player);
        UpdraftManager.acceptedMove(player);
    }
}
