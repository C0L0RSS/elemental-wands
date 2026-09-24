package com.anton.elementalwands.entity;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.util.AstralDoubleManager;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.UUID;

/** Stationary one-hit summon. Discarding avoids loot, equipment drops and a corpse animation. */
public final class AstralDoubleEntity extends PathAwareEntity {
    private static final TrackedData<String> OWNER=DataTracker.registerData(AstralDoubleEntity.class,TrackedDataHandlerRegistry.STRING);
    public AstralDoubleEntity(EntityType<? extends AstralDoubleEntity> type,World world){
        super(type,world);setAiDisabled(true);setNoGravity(true);setPersistent();
    }
    public AstralDoubleEntity(ServerWorld world,ServerPlayerEntity p,Vec3d pos){
        this(ModEntities.ASTRAL_DOUBLE,world);dataTracker.set(OWNER,p.getUuidAsString());setPosition(pos);
        setYaw(p.getYaw());setBodyYaw(p.getYaw());setHeadYaw(p.getYaw());setLeftHanded(p.getMainArm()==net.minecraft.util.Arm.LEFT);
        equipStack(EquipmentSlot.MAINHAND,new ItemStack(ModItems.FRACTURED_WAND));setEquipmentDropChance(EquipmentSlot.MAINHAND,0);
    }
    public static DefaultAttributeContainer.Builder createAttributes(){return createMobAttributes().add(EntityAttributes.MAX_HEALTH,1).add(EntityAttributes.MOVEMENT_SPEED,0).add(EntityAttributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void initDataTracker(DataTracker.Builder b){super.initDataTracker(b);b.add(OWNER,"");}
    public UUID ownerUuid(){try{return UUID.fromString(dataTracker.get(OWNER));}catch(IllegalArgumentException e){return null;}}
    public ServerPlayerEntity owner(){return getEntityWorld() instanceof ServerWorld w && ownerUuid()!=null ? w.getServer().getPlayerManager().getPlayer(ownerUuid()):null;}
    public void face(Vec3d d){float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));setYaw(yaw);setBodyYaw(yaw);setHeadYaw(yaw);setPitch((float)Math.toDegrees(-Math.asin(d.y)));}
    @Override public void tick(){
        setVelocity(Vec3d.ZERO);super.tick();setVelocity(Vec3d.ZERO);
        if(getEntityWorld() instanceof ServerWorld w){
            var p=owner();if(p==null || AstralDoubleManager.active(p)!=this){AstralDoubleManager.destroyed(this);return;}
            if(age%3==0){double a=age*.19;
                w.spawnParticles(ModParticles.SPACE_MOTE,getX()+Math.cos(a)*.45,getY()+.2+(age%30)/20.,getZ()+Math.sin(a)*.45,1,.03,.03,.03,.004);
            }
        }
    }
    @Override public boolean damage(ServerWorld world,DamageSource source,float amount){
        if(isRemoved() || amount<=0 || !Float.isFinite(amount))return false;
        var p=owner();var attacker=source.getAttacker();
        if(p==null || (attacker!=null && WandAllies.protectedFrom(attacker,this)))return false;
        if(attacker instanceof FracturedGuardianEntity g && !GuardianArenaManager.eligible(g,p))return false;
        AstralDoubleManager.destroyed(this);return true;
    }
    @Override public boolean isPushable(){return false;}
    @Override public void takeKnockback(double strength,double x,double z){}
    @Override public boolean canBeLeashed(){return false;}
    @Override public boolean shouldSave(){return false;}
}
