package com.anton.elementalwands.mixin;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.anton.elementalwands.util.SpellCombat;
import com.anton.elementalwands.data.WizardAffinity;
@Mixin(LivingEntity.class)
public abstract class SpellBurnDamageMixin {
    @Unique private float ew$healthBeforeBurn;
    @Unique private Entity ew$burnOwner;
    @Inject(method="damage",at=@At("HEAD"))
    private void ew$before(ServerWorld world,DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir){ew$healthBeforeBurn=((LivingEntity)(Object)this).getHealth();ew$burnOwner=SpellCombat.burnOwner((LivingEntity)(Object)this,source);}
    @ModifyVariable(method="damage",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float ew$scale(float amount,ServerWorld world,DamageSource source,float original){
        var target=(LivingEntity)(Object)this;var owner=SpellCombat.burnOwner(target,source);
        return amount*SpellCombat.multiplier(owner,WizardAffinity.FIRE);
    }
    @Inject(method="damage",at=@At("RETURN"))
    private void ew$after(ServerWorld world,DamageSource source,float amount,CallbackInfoReturnable<Boolean> cir){
        if(cir.getReturnValueZ()){var target=(LivingEntity)(Object)this;SpellCombat.reward(target,ew$burnOwner,WizardAffinity.FIRE,ew$healthBeforeBurn);}
    }
}
