package com.anton.elementalwands.mixin;

import com.anton.elementalwands.church.GuardianChurchLocator;
import net.minecraft.command.argument.RegistryPredicateArgumentType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.LocateCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocateCommand.class)
public abstract class GuardianChurchLocateMixin {
    @Inject(method="executeLocateStructure",at=@At("HEAD"),cancellable=true)
    private static void locateChurchIncrementally(ServerCommandSource source,
            RegistryPredicateArgumentType.RegistryPredicate<Structure> predicate,CallbackInfoReturnable<Integer> ci) {
        boolean churchOnly=predicate.getKey().map(key -> key.getValue().equals(GuardianChurchLocator.ID),tag -> {
            var entries=source.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE).getOptional(tag);
            return entries.isPresent() && entries.get().size()==1 && entries.get().get(0).matchesId(GuardianChurchLocator.ID);
        });
        if(!churchOnly)return;
        String message=GuardianChurchLocator.start(source,100);
        source.sendFeedback(() -> Text.literal(message),false);
        ci.setReturnValue(1);
    }
}
