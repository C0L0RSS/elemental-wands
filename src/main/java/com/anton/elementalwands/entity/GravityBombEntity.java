package com.anton.elementalwands.entity;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.party.WandAllies;
import com.anton.elementalwands.registry.*;
import com.anton.elementalwands.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.storage.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Swept impact bomb that becomes a stationary, cover-aware gravity field. */
public final class GravityBombEntity extends Entity {
    private static final net.minecraft.registry.RegistryKey<net.minecraft.entity.damage.DamageType> COLLAPSE_DAMAGE =
            net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE, net.minecraft.util.Identifier.of("elementalwands", "gravity_collapse"));
    private static final TrackedData<Boolean> WELL = DataTracker.registerData(GravityBombEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> IMPACT_AGE = DataTracker.registerData(GravityBombEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Long> COLLAPSE_START = DataTracker.registerData(GravityBombEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Long> BURST_START = DataTracker.registerData(GravityBombEntity.class, TrackedDataHandlerRegistry.LONG);
    public static final int COLLAPSE_TICKS = 8, BURST_TICKS = 12;
    private final PositionInterpolator interpolator = new PositionInterpolator(this, 2);
    private ServerPlayerEntity owner;
    private long expires;
    public GravityBombEntity(EntityType<? extends GravityBombEntity> type, World world) { super(type, world); setNoGravity(true); }
    public GravityBombEntity(ServerWorld world, ServerPlayerEntity p) {
        this(ModEntities.GRAVITY_BOMB, world); owner = p;
        setPosition(p.getEyePos().add(0, -.12, 0)); setVelocity(p.getRotationVec(1).multiply(1.05).add(0, .22, 0));
    }
    @Override protected void initDataTracker(DataTracker.Builder b) { b.add(WELL, false); b.add(IMPACT_AGE, 0); b.add(COLLAPSE_START, -1L); b.add(BURST_START, -1L); }
    @Override public PositionInterpolator getInterpolator() { return interpolator; }
    public boolean isWell() { return dataTracker.get(WELL); }
    public int wellAge() { return Math.max(0, age - dataTracker.get(IMPACT_AGE)); }
    public boolean isCollapsing() { return dataTracker.get(COLLAPSE_START) >= 0; }
    public boolean isBurst() { return dataTracker.get(BURST_START) >= 0; }
    public long collapseStart() { return dataTracker.get(COLLAPSE_START); }
    public float collapseAge() { return isCollapsing() ? getEntityWorld().getTime() - collapseStart() : 0; }
    public float burstAge() { return isBurst() ? getEntityWorld().getTime() - dataTracker.get(BURST_START) : 0; }
    public void beginCollapse() {
        if (isCollapsing() || isBurst() || !isWell()) return;
        dataTracker.set(COLLAPSE_START, getEntityWorld().getTime());
        if (getEntityWorld() instanceof ServerWorld w)
            w.playSound(null, getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, .8f, 1.6f);
    }
    @Override public void tick() {
        super.tick();
        if (getEntityWorld().isClient()) {
            interpolator.tick();
            return;
        }
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        if (isBurst()) {
            if (burstAge() >= BURST_TICKS) discard();
            return; // Cosmetic debris only; damage and pull have already ended.
        }
        if (owner == null || !GravityWellManager.owns(owner, this)) { discard(); return; }
        if (isWell()) {
            if (!isCollapsing() && world.getTime() >= expires) GravityWellManager.expired(owner, this);
            affect(world, false);
            return;
        }
        Vec3d from = getEntityPos(), to = from.add(getVelocity());
        if (age > 60 || !world.isChunkLoaded(BlockPos.ofFloored(to)) || !world.getWorldBorder().contains(BlockPos.ofFloored(to))
                || to.y < world.getBottomY() || to.y > world.getTopYInclusive()) {
            GravityWellManager.cancel(owner); return;
        }
        var block = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, this));
        Vec3d end = block.getType() == HitResult.Type.MISS ? to : block.getPos();
        var entity = ProjectileUtil.raycast(this, from, end, getBoundingBox().stretch(to.subtract(from)).expand(1),
                e -> e instanceof LivingEntity living && eligible(living), from.squaredDistanceTo(end));
        if (entity != null) activate(world, entity.getPos());
        else if (block.getType() != HitResult.Type.MISS) activate(world, block.getPos().add(Vec3d.of(block.getSide().getVector()).multiply(.12)));
        else { setPosition(to); setVelocity(getVelocity().multiply(.99).add(0, -.06, 0)); }
    }
    private void activate(ServerWorld w, Vec3d pos) {
        if (!GuardianArenaManager.canTeleport(owner, w, pos.add(0, .9, 0))) { GravityWellManager.cancel(owner); return; }
        setPosition(pos); setVelocity(Vec3d.ZERO); dataTracker.set(WELL, true); dataTracker.set(IMPACT_AGE, age);
        expires = w.getTime() + GravityWellManager.DURATION;
        GravityWellManager.impact(owner, this);
        w.playSound(null, getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, .65f, 1.4f);
        w.playSound(null, getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, .8f, .65f);
    }
    private boolean eligible(LivingEntity e) {
        return e.isAlive() && !e.isSpectator() && !WandAllies.protectedFrom(owner, e)
                && (!(e instanceof FracturedGuardianEntity g) || GuardianArenaManager.eligible(g, owner))
                && (!(e instanceof net.minecraft.entity.player.PlayerEntity p) || (!p.isCreative() && GuardianArenaManager.canCast(p)));
    }
    private void affect(ServerWorld w, boolean burst) {
        Vec3d center = getEntityPos();
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(GravityWellManager.RADIUS), this::eligible)) {
            Vec3d target = e.getBoundingBox().getCenter();
            Vec3d delta = center.subtract(target);
            if (delta.lengthSquared() > GravityWellManager.RADIUS * GravityWellManager.RADIUS) continue;
            if (w.raycast(new RaycastContext(center, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this)).getType() != HitResult.Type.MISS) continue;
            if (burst) SpellCombat.damage(e, w, new DamageSource(w.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE).getOrThrow(COLLAPSE_DAMAGE), this, owner), GravityWellManager.DAMAGE, owner, WizardAffinity.SPACE);
            if (!(e instanceof FracturedGuardianEntity) && !e.hasVehicle()) {
                double resistance = MathHelper.clamp(e.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE), 0, 1);
                Vec3d direction = delta.normalize();
                double force = (burst ? .65 : .075) * (1 - resistance);
                // Preserve tangential/outward movement; bound only the added inward speed.
                double amount = Math.max(0, Math.min(force, (burst ? .8 : .32) - e.getVelocity().dotProduct(direction)));
                Vec3d impulse = direction.multiply(amount);
                e.addVelocity(impulse.x, MathHelper.clamp(impulse.y, -.12, burst ? .25 : .025), impulse.z);
                e.velocityModified = true;
            }
        }
    }
    public void collapse() {
        if (!(getEntityWorld() instanceof ServerWorld w) || owner == null || !isWell() || isBurst()) return;
        dataTracker.set(BURST_START, w.getTime());
        affect(w, true);
        // Renderer carries the same stream motes from suction into the outward burst.
        w.playSound(null, getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 1, .7f);
    }
    @Override protected void readCustomData(ReadView view) { owner = null; }
    @Override protected void writeCustomData(WriteView view) {}
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean canBeHitByProjectile() { return false; }
    @Override public boolean damage(ServerWorld w, DamageSource s, float amount) { return false; }
}
