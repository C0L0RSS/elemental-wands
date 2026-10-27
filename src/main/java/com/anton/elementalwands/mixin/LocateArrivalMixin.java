package com.anton.elementalwands.mixin;

import com.anton.elementalwands.world.StructureArrival;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import net.minecraft.command.argument.RegistryPredicateArgumentType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.LocateCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla structure results keep the player's height; ours report and teleport to a standing cell. */
@Mixin(LocateCommand.class)
public abstract class LocateArrivalMixin {
    @SuppressWarnings("unchecked")
    @WrapOperation(method="executeLocateStructure",at=@At(value="INVOKE",target="Lnet/minecraft/server/command/LocateCommand;sendCoordinates(Lnet/minecraft/server/command/ServerCommandSource;Lnet/minecraft/command/argument/RegistryPredicateArgumentType$RegistryPredicate;Lnet/minecraft/util/math/BlockPos;Lcom/mojang/datafixers/util/Pair;Ljava/lang/String;ZLjava/time/Duration;)I"))
    private static int reportArrival(ServerCommandSource source,RegistryPredicateArgumentType.RegistryPredicate<?> predicate,BlockPos origin,
            Pair<BlockPos,? extends RegistryEntry<?>> found,String key,boolean includeY,Duration time,Operation<Integer> original) {
        BlockPos arrival=found.getSecond().value() instanceof Structure
                ?StructureArrival.find(source.getWorld(),(RegistryEntry<Structure>)found.getSecond(),found.getFirst()):null;
        if(arrival==null)return original.call(source,predicate,origin,found,key,includeY,time);
        return original.call(source,predicate,origin,Pair.of(arrival,found.getSecond()),key,true,time);
    }
}
