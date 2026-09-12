package com.anton.elementalwands.entity;

import com.anton.elementalwands.item.AbstractWandItem;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.util.StoneClusterRules;
import com.anton.elementalwands.util.StoneStaggerAccess;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.*;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** One non-solid model while held; one authoritative swept projectile after release. */
public final class StoneClusterEntity extends ProjectileEntity {
    private static final TrackedData<Integer> MASS = DataTracker.registerData(StoneClusterEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> BEFORE_MASS = DataTracker.registerData(StoneClusterEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> HELD = DataTracker.registerData(StoneClusterEntity.class,TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> CASTER = DataTracker.registerData(StoneClusterEntity.class,TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<BlockPos> GROUND = DataTracker.registerData(StoneClusterEntity.class,TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Long> GATHER_TIME = DataTracker.registerData(StoneClusterEntity.class,TrackedDataHandlerRegistry.LONG);
    private double distance;
    private int flyingTicks;
    private boolean loaded;
    private float visualMass, previousVisualMass;
    public StoneClusterEntity(EntityType<? extends StoneClusterEntity> type, World world) {
        super(type,world); setNoGravity(true);
    }
    @Override protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(MASS,0).add(BEFORE_MASS,0).add(HELD,false).add(CASTER,-1)
                .add(GROUND,BlockPos.ORIGIN).add(GATHER_TIME,-1_000_000_000L);
    }
    public int mass() { return dataTracker.get(MASS); }
    public int beforeMass() { return dataTracker.get(BEFORE_MASS); }
    public boolean held() { return dataTracker.get(HELD); }
    public Entity caster() { return getEntityWorld().getEntityById(dataTracker.get(CASTER)); }
    public BlockPos ground() { return dataTracker.get(GROUND); }
    public float gatherAge(float delta) { return getEntityWorld().getTime()-dataTracker.get(GATHER_TIME)+delta; }
    public float visualMass(float delta) { return MathHelper.lerp(delta,previousVisualMass,visualMass); }
    public static Vec3d heldPosition(Entity owner, int mass) {
        return owner.getEntityPos().add(0,owner.getHeight()+.35+StoneClusterRules.radius(mass),0);
    }
    public void hold(PlayerEntity owner) {
        setOwner(owner); dataTracker.set(CASTER,owner.getId()); dataTracker.set(HELD,true);
        setPosition(heldPosition(owner,25));
    }
    public void gather(int mass, int before, BlockPos from) {
        dataTracker.set(MASS,StoneClusterRules.mass(mass)); dataTracker.set(BEFORE_MASS,before);
        dataTracker.set(GROUND,from); dataTracker.set(GATHER_TIME,getEntityWorld().getTime());
    }
    public void chip(int mass) { dataTracker.set(MASS,mass); dataTracker.set(BEFORE_MASS,mass); }
    public void release(PlayerEntity owner, int mass) {
        setOwner(owner); dataTracker.set(CASTER,owner.getId()); dataTracker.set(MASS,mass);
        dataTracker.set(HELD,false); visualMass = previousVisualMass = mass;
        Vec3d aimStart = owner.getEyePos();
        Vec3d aimEnd = aimStart.add(owner.getRotationVec(1).multiply(StoneClusterRules.RANGE));
        var hit = terrainRaycast(aimStart,aimEnd,owner);
        Vec3d aim = hit.getType()==HitResult.Type.MISS ? aimEnd : hit.getPos();
        // Resolve the crosshair's nearest target so an overhead release does not sail above it.
        double limit = aimStart.squaredDistanceTo(aim);
        for (LivingEntity target : getEntityWorld().getEntitiesByClass(LivingEntity.class,
                new Box(aimStart,aimEnd).expand(1),target -> eligible(owner,target))) {
            var point = target.getBoundingBox().expand(.05).raycast(aimStart,aimEnd);
            if (point.isPresent() && aimStart.squaredDistanceTo(point.get()) < limit) {
                aim = point.get(); limit = aimStart.squaredDistanceTo(aim);
            }
        }
        Vec3d direction = aim.subtract(getEntityPos());
        if (direction.lengthSquared()<1e-8) direction=owner.getRotationVec(1);
        setVelocity(direction.normalize().multiply(StoneClusterRules.speed(mass))); velocityDirty=true;
    }
    public static boolean eligible(Entity owner, LivingEntity target) {
        return target!=owner && target.isAlive() && !target.isSpectator()
                && !(target instanceof PlayerEntity player && player.isCreative())
                && !owner.isTeammate(target)
                && !(target instanceof TameableEntity pet && pet.isOwner((LivingEntity)owner));
    }
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override protected void readCustomData(ReadView view) { super.readCustomData(view); loaded=true; }

    // Low vegetation is not cover, even when vanilla gives it a physical shape
    // (azalea, lily pads, moss carpet). Logs, leaves and constructed defenses remain solid.
    private static boolean softVegetation(BlockState state) {
        return com.anton.elementalwands.registry.ModSpellBlocks.isNatureGrowth(state)
                || state.getBlock() instanceof PlantBlock
                || state.getBlock() instanceof AbstractPlantPartBlock
                || state.getBlock() instanceof VineBlock
                || state.getBlock() instanceof SugarCaneBlock
                || state.isOf(Blocks.MOSS_CARPET) || state.isOf(Blocks.PALE_MOSS_CARPET);
    }
    private BlockHitResult terrainRaycast(Vec3d from, Vec3d to, Entity context) {
        return getEntityWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,context) {
            @Override public VoxelShape getBlockShape(BlockState state, BlockView world, BlockPos pos) {
                return softVegetation(state)?VoxelShapes.empty():super.getBlockShape(state,world,pos);
            }
        });
    }
    private boolean overlapsTerrain(Box box) {
        VoxelShape volume=VoxelShapes.cuboid(box);
        ShapeContext context=ShapeContext.of(this);
        // Include neighbouring cells for shapes extending outside their block, such as fences.
        for(BlockPos pos:BlockPos.iterate(BlockPos.ofFloored(box.minX-1,box.minY-1,box.minZ-1),
                BlockPos.ofFloored(box.maxX+1,box.maxY+1,box.maxZ+1))) {
            BlockState state=getEntityWorld().getBlockState(pos);
            if(softVegetation(state))continue;
            VoxelShape shape=state.getCollisionShape(getEntityWorld(),pos,context);
            if(!shape.isEmpty() && VoxelShapes.matchesAnywhere(volume,
                    shape.offset(pos.getX(),pos.getY(),pos.getZ()),BooleanBiFunction.AND))return true;
        }
        return false;
    }
    @Override public void tick() {
        super.tick();
        previousVisualMass=visualMass; visualMass += (mass()-visualMass)*.35f;
        if (held()) {
            Entity owner=caster();
            if (owner!=null) setPosition(heldPosition(owner,mass()));
            if (getEntityWorld() instanceof ServerWorld && (loaded || owner==null || !owner.isAlive())) discard();
            return;
        }
        Vec3d start=getEntityPos(), end=start.add(getVelocity());
        if (getEntityWorld() instanceof ServerWorld world) {
            if (loaded || ++flyingTicks>80 || distance>StoneClusterRules.RANGE
                    || !(getOwner() instanceof PlayerEntity owner) || !owner.isAlive()
                    || owner.getEntityWorld()!=world) { discard(); return; }
            double r=StoneClusterRules.radius(mass()), length=Math.max(1e-8,start.distanceTo(end));
            if (overlapsTerrain(new Box(start,start).expand(r))) {
                impact(world,owner,null); return;
            }
            double fraction=1; boolean blocked=false;
            for (int i=0;i<9;i++) {
                Vec3d offset=i==8?Vec3d.ZERO:new Vec3d((i&1)==0?-r:r,(i&2)==0?-r:r,(i&4)==0?-r:r);
                var hit=terrainRaycast(start.add(offset),end.add(offset),this);
                if (hit.getType()!=HitResult.Type.MISS) {
                    blocked=true; fraction=Math.min(fraction,start.add(offset).distanceTo(hit.getPos())/length);
                }
            }
            LivingEntity victim=null;
            for (LivingEntity target:world.getEntitiesByClass(LivingEntity.class,new Box(start,end).expand(r),
                    target -> eligible(owner,target))) {
                Box box=target.getBoundingBox().expand(r);
                var point=box.contains(start)?java.util.Optional.of(start):box.raycast(start,end);
                if (point.isEmpty()) continue;
                double t=start.distanceTo(point.get())/length;
                if (t<fraction || (!blocked && victim==null && t<=fraction)) { fraction=t; victim=target; }
            }
            if (blocked || victim!=null) {
                setPosition(start.lerp(end,fraction)); impact(world,owner,victim); return;
            }
            distance += length;
        }
        // Water does not extinguish or heavily slow either projectile.
        setPosition(end); setVelocity(getVelocity().add(0,mass()>0?-.006:-.003,0));
    }
    private void impact(ServerWorld world, PlayerEntity owner, LivingEntity victim) {
        if (victim!=null) {
            float damage=StoneClusterRules.damage(mass());
            if (victim.damage(world,world.getDamageSources().thrown(this,owner),damage)) {
                AbstractWandItem.onWandDamageDealt(owner,damage);
                if (mass()>=StoneClusterRules.STAGGER_MASS) stagger(world,victim);
            }
        }
        world.spawnParticles(ModParticles.STONE_SHARD,getX(),getY(),getZ(),mass()>0?30:9,.4,.4,.4,.13);
        world.spawnParticles(ModParticles.STONE_DUST,getX(),getY(),getZ(),mass()>0?24:5,.5,.3,.5,.05);
        world.playSound(null,getBlockPos(),SoundEvents.BLOCK_DEEPSLATE_BREAK,SoundCategory.PLAYERS,1f,.65f);
        discard();
    }
    private void stagger(ServerWorld world, LivingEntity target) {
        // Encounter bosses retain their authored motion; ordinary targets receive physical recoil.
        if (target instanceof FracturedGuardianEntity) return;
        ((StoneStaggerAccess)target).elementalwands$staggerUntil(world.getTime()+StoneClusterRules.STAGGER_TICKS);
        target.setSprinting(false);
        var packet=new ModNetworking.StoneStaggerPayload(target.getId(),StoneClusterRules.STAGGER_TICKS);
        for (ServerPlayerEntity player:PlayerLookup.tracking(target)) ServerPlayNetworking.send(player,packet);
        if (target instanceof ServerPlayerEntity player) { player.stopGliding(); ServerPlayNetworking.send(player,packet); }
        double resistance=1-Math.clamp(target.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE),0,1);
        Vec3d horizontal=new Vec3d(getVelocity().x,0,getVelocity().z).normalize();
        Vec3d old=target.getVelocity();
        double vertical=target.isOnGround()?.18:Math.min(old.y,-.45);
        target.setVelocity(old.x*.35+horizontal.x*.8*resistance,
                resistance>0?vertical:old.y,old.z*.35+horizontal.z*.8*resistance);
        target.velocityModified=true;
    }
}
