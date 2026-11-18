package com.anton.elementalwands.mixin;

import com.anton.elementalwands.util.SpringbloomManager;
import net.minecraft.entity.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class SpringbloomMovementMixin {
    @Unique private Vec3d elementalwands$beforeSpringMove;
    @Unique private boolean elementalwands$springGrounded;
    @Inject(method="move",at=@At("HEAD"))
    private void before(MovementType type,Vec3d movement,CallbackInfo ci) {
        if((Object)this instanceof ServerPlayerEntity p){elementalwands$beforeSpringMove=p.getEntityPos();elementalwands$springGrounded=p.isOnGround();}
    }
    @Inject(method="move",at=@At("RETURN"))
    private void after(MovementType type,Vec3d movement,CallbackInfo ci) {
        if((Object)this instanceof ServerPlayerEntity p && elementalwands$beforeSpringMove!=null)
            SpringbloomManager.afterMove(p,elementalwands$beforeSpringMove,movement,elementalwands$springGrounded);
    }
}
