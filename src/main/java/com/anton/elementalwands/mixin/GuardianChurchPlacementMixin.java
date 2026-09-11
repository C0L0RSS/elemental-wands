package com.anton.elementalwands.mixin;

import java.util.Optional;
import com.mojang.datafixers.util.Either;
import com.anton.elementalwands.church.GuardianChurchPlacement;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.JigsawStructure;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(JigsawStructure.class)
public abstract class GuardianChurchPlacementMixin {
    @Inject(method="getStructurePosition",at=@At("RETURN"),cancellable=true)
    private void requireGentleTerrain(Structure.Context context,CallbackInfoReturnable<Optional<Structure.StructurePosition>> ci) {
        var self=(JigsawStructure)(Object)this;
        if (!self.getStartPool().getKey().map(k -> k.getValue().equals(Identifier.of("elementalwands","guardian_church"))).orElse(false)
                || ci.getReturnValue().isEmpty()) return;
        var proposed=ci.getReturnValue().get();var pieces=proposed.generate();
        var bounds=pieces.getBoundingBox();
        // Select the least disruptive valid patch, rather than accepting the first flat enough one.
        var survey=new GuardianChurchPlacement.Survey(context);
        int[][] shifts={{0,0},{16,0},{-16,0},{0,16},{0,-16},{16,16},{16,-16},{-16,16},{-16,-16},
                {32,0},{-32,0},{0,32},{0,-32},{32,32},{32,-32},{-32,32},{-32,-32}};
        // Vanilla tests the final biome AFTER this hook. Avoid detailed terrain work
        // if every possible shifted origin fails that exact test. Test every possible
        // elevation/origin, so nearby biome boundaries retain the same placement.
        boolean possibleBiome=false;
        for(int[] shift:shifts) {
            int dy=survey.floor(bounds.offset(shift[0],0,shift[1]))-(bounds.getMinY()+3);
            if(survey.validBiome(proposed.position().add(shift[0],dy,shift[1]))) {possibleBiome=true;break;}
        }
        if(!possibleBiome) {ci.setReturnValue(Optional.empty());return;}
        int bestX=0,bestY=0,bestZ=0;long bestScore=Long.MAX_VALUE;
        for (int[] shift:shifts) {
            var shifted=bounds.offset(shift[0],0,shift[1]);
            int dy=survey.floor(shifted)-(bounds.getMinY()+3);
            long score=survey.score(shifted.offset(0,dy,0),bestScore);
            if(score<0 || score>=bestScore)continue;
            bestScore=score;bestX=shift[0];bestY=dy;bestZ=shift[1];
            if(score==0)break;
        }
        if(bestScore!=Long.MAX_VALUE) {
            for(var piece:pieces.toList().pieces())piece.translate(bestX,bestY,bestZ);
            ci.setReturnValue(Optional.of(new Structure.StructurePosition(proposed.position().add(bestX,bestY,bestZ),Either.right(pieces))));
            return;
        }
        ci.setReturnValue(Optional.empty());
    }
}
