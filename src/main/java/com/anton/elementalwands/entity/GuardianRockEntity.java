package com.anton.elementalwands.entity;

import com.anton.elementalwands.registry.ModSounds;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.storage.ReadView;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Encounter-owned rubble. Never places blocks, explodes, or damages terrain. */
public class GuardianRockEntity extends ProjectileEntity implements FlyingItemEntity {
    private static final TrackedData<Boolean> HELD = DataTracker.registerData(GuardianRockEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> SHARD = DataTracker.registerData(GuardianRockEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final ItemStack STACK = new ItemStack(Blocks.COBBLESTONE);
    private boolean loadedFromSave;
    private int flyingTicks;
    private GuardianWallImpact walls = new GuardianWallImpact();
    private java.util.Set<java.util.UUID> volleyHits = new java.util.HashSet<>();
    /** A bursting stone: ticks of flight before it bursts. */
    private int fuse;

    public GuardianRockEntity(EntityType<? extends GuardianRockEntity> type, World world) { super(type, world); }

    @Override protected void initDataTracker(DataTracker.Builder builder) { builder.add(HELD, true); builder.add(SHARD, false); }
    @Override public ItemStack getStack() { return STACK; }
    /** A small stone of the Airburst, which bursts where it arrives instead of striking one player. */
    public boolean isShard() { return dataTracker.get(SHARD); }
    /**
     * Sends a small stone to {@code aim}, where it bursts after {@code fuse} ticks (see
     * {@link GuardianAirburstRules}), or sooner against whatever it meets. Stones released with the
     * same {@code hits} hurt a player once between them.
     */
    void releaseBurst(Vec3d aim,int fuse,java.util.Set<java.util.UUID> hits,GuardianWallImpact walls) {
        this.volleyHits=hits;this.walls=walls;this.fuse=Math.max(1,fuse);
        dataTracker.set(SHARD,true);dataTracker.set(HELD,false);
        setVelocity(aim.subtract(getEntityPos()).multiply(1.0/this.fuse));velocityDirty=true;
    }
    /** A back stone, rising with the Guardian until {@link #releaseBurst} launches it. */
    void holdShard() { dataTracker.set(SHARD, true); }
    public boolean isHeld() { return dataTracker.get(HELD); }
    @Override protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        loadedFromSave = true; // Transient projectiles must not resume a stale fight after chunk reload.
    }

    public void release(Vec3d aim) {
        dataTracker.set(HELD, false);
        setVelocity(GuardianCombatRules.launchVelocity(getEntityPos(), aim));
        velocityDirty = true;
    }

    @Override public void tick() {
        super.tick();
        if (getEntityWorld() instanceof ServerWorld world) {
            if (loadedFromSave || age > 160 || !(getOwner() instanceof FracturedGuardianEntity guardian)
                    || !guardian.isAlive() || guardian.isRemoved()) { discard(); return; }
            if (dataTracker.get(HELD)) return;
            if (++flyingTicks > (isShard()?fuse:80)) {
                if (isShard()) burst(world, guardian); else discard();
                return;
            }
            Vec3d start = getEntityPos(), end = start.add(getVelocity());
            // Sweep the center and the cube's corners to stop its visible volume at cover.
            double fraction = 1;
            boolean blocked = false;
            net.minecraft.util.math.BlockPos blockContact = null;
            double r = isShard()?GuardianFanRules.RADIUS:GuardianCombatRules.ROCK_RADIUS;
            for (int i = 0; i < 9; i++) {
                Vec3d offset = i == 8 ? Vec3d.ZERO : new Vec3d((i&1)==0?-r:r, (i&2)==0?-r:r, (i&4)==0?-r:r);
                if (isShard()) {
                    Vec3d from=start.add(offset),to=end.add(offset);
                    Vec3d clipped=walls.clip(world,guardian,from,to,false,false);
                    if(clipped.squaredDistanceTo(to)>1e-10) {
                        blocked=true;fraction=Math.min(fraction,from.distanceTo(clipped)/Math.max(1e-8,start.distanceTo(end)));
                    }
                }
                var hit = world.raycast(new RaycastContext(start.add(offset), end.add(offset),
                        RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
                if (hit.getType() != HitResult.Type.MISS) {
                    blocked = true;
                    double f=start.add(offset).distanceTo(hit.getPos()) / Math.max(1e-8, start.distanceTo(end));
                    if(f <= fraction) { fraction=f; blockContact=hit.getBlockPos(); }
                }
            }
            ServerPlayerEntity direct = null;
            double entityFraction = fraction;
            for (ServerPlayerEntity player : world.getPlayers(p -> GuardianBossCombat.canDamage(guardian, p))) {
                Box box = player.getBoundingBox().expand(r);
                var hit = box.contains(start) ? java.util.Optional.of(start) : box.raycast(start, end);
                if (hit.isEmpty()) continue;
                double f = start.distanceTo(hit.get()) / Math.max(1e-8, start.distanceTo(end));
                if (f < entityFraction) { entityFraction = f; direct = player; }
            }
            // The double is fragile: rocks/shards destroy it on contact without becoming a shield.
            Vec3d clearEnd=start.lerp(end,entityFraction);
            for (AstralDoubleEntity clone : world.getEntitiesByClass(AstralDoubleEntity.class,new Box(start,clearEnd).expand(r),e->!e.isRemoved())) {
                Box box=clone.getBoundingBox().expand(r);
                if(box.contains(start)||box.raycast(start,clearEnd).isPresent())
                    clone.damage(world,world.getDamageSources().thrown(this,guardian),1);
            }
            if (direct != null || blocked) {
                setPosition(start.lerp(end, entityFraction));
                if(direct==null && blockContact!=null)walls.breakWall(world,blockContact);
                if (isShard()) burst(world, guardian); else impact(world, guardian, direct);
                return;
            }
            if (age % 2 == 0) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                    getX(), getY(), getZ(), 2, .15, .15, .15, .01);
        } else if (dataTracker.get(HELD)) return;
        // Same ballistic integration on both sides; server alone decides impacts and damage.
        setPosition(getEntityPos().add(getVelocity()));
        if (!isShard()) setVelocity(getVelocity().add(0, -GuardianCombatRules.ROCK_GRAVITY, 0));
    }

    /** Surface-offset origin for cover rays: a block impact's exact boundary must not occlude the open side. */
    private Vec3d coverOrigin(Vec3d center) {
        Vec3d velocity = getVelocity();
        return velocity.lengthSquared() < 1e-8 ? center : center.subtract(velocity.normalize().multiply(.08));
    }

    private void impact(ServerWorld world, FracturedGuardianEntity guardian, ServerPlayerEntity direct) {
        Vec3d center = getEntityPos(), from = coverOrigin(center);
        for(AstralDoubleEntity clone:world.getEntitiesByClass(AstralDoubleEntity.class,new Box(center,center).expand(3),e->!e.isRemoved())) {
            if(clone.squaredDistanceTo(center)<=9 && walls.clear(world,guardian,from,clone.getBoundingBox().getCenter(),false))
                clone.damage(world,world.getDamageSources().thrown(this,guardian),1);
        }
        for (ServerPlayerEntity player : world.getPlayers(p -> GuardianBossCombat.canDamage(guardian, p))) {
            if (player != direct && player.squaredDistanceTo(center) > 3*3) continue;
            if (!walls.clear(world, guardian, from, player.getBoundingBox().getCenter(), false)) continue;
            float damage = player == direct ? GuardianCombatRules.ROCK_DAMAGE : GuardianCombatRules.ROCK_SPLASH_DAMAGE;
            if (guardian.strike(world, player, world.getDamageSources().thrown(this, guardian), damage, "rock")) {
                Vec3d away = player.getEntityPos().subtract(center);
                player.takeKnockback(.6, -away.x, -away.z);
            }
        }
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                center.x, center.y, center.z, 48, 1.2, .4, 1.2, .12);
        world.playSound(null, center.x, center.y, center.z, ModSounds.ENTITY_GUARDIAN_ROCK_IMPACT, SoundCategory.HOSTILE, 1.4f, .6f);
        discard();
    }

    /** The Airburst: everyone within the blast and not behind cover is hurt once, and knocked straight down if they are in the air. */
    private void burst(ServerWorld world, FracturedGuardianEntity guardian) {
        Vec3d center = getEntityPos(), from = coverOrigin(center);
        double reach = GuardianAirburstRules.BLAST;
        for(AstralDoubleEntity clone:world.getEntitiesByClass(AstralDoubleEntity.class,new Box(center,center).expand(reach),e->!e.isRemoved())) {
            if(GuardianAirburstRules.inBlast(center,clone.getBoundingBox()) && walls.clear(world,guardian,from,clone.getBoundingBox().getCenter(),false))
                clone.damage(world,world.getDamageSources().thrown(this,guardian),1);
        }
        for (ServerPlayerEntity player : world.getPlayers(p -> GuardianBossCombat.canDamage(guardian, p))) {
            if (volleyHits.contains(player.getUuid()) || !GuardianAirburstRules.inBlast(center, player.getBoundingBox())) continue;
            if (!walls.clear(world, guardian, from, player.getBoundingBox().getCenter(), false)) continue;
            volleyHits.add(player.getUuid());
            if (!guardian.strike(world, player, world.getDamageSources().thrown(this, guardian), GuardianAirburstRules.DAMAGE, "airburst")) continue;
            if (!player.isOnGround()) {
                Vec3d moving = player.getVelocity();
                player.setVelocity(moving.x * .3, -GuardianAirburstRules.KNOCK_DOWN, moving.z * .3);
            } else {
                Vec3d away = player.getEntityPos().subtract(center);
                player.takeKnockback(.8, -away.x, -away.z);
            }
            player.velocityModified = true;
        }
        world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
                center.x, center.y, center.z, 40, reach * .4, reach * .4, reach * .4, .2);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 16, reach * .4, reach * .4, reach * .4, .1);
        world.playSound(null, center.x, center.y, center.z, ModSounds.ENTITY_GUARDIAN_ROCK_IMPACT, SoundCategory.HOSTILE, 1.6f, .8f);
        discard();
    }
}
