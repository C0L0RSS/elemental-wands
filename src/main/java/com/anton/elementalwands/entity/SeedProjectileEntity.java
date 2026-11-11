package com.anton.elementalwands.entity;

import com.anton.elementalwands.party.WandAllies;

import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.util.FlowerTurretManager;
import com.anton.elementalwands.util.NatureCombat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * The Nature wand's seed. Thrown by the caster it deals a light direct hit and marks its target
 * for the caster's flowers. A flower spits a smaller one that flies straight, hits only enemies
 * and never opens a hurt window of its own. A seed that lands on the ground scatters pollen.
 */
public class SeedProjectileEntity extends ProjectileEntity {

    private static final double INITIAL_SPEED = 1.5;
    private static final double GRAVITY = 0.03;
    private static final double DRAG = 0.99;
    private static final int MAX_LIFETIME_TICKS = 100;
    private static final int FLOWER_LIFETIME_TICKS = 30;
    private static final net.minecraft.entity.data.TrackedData<Boolean> FROM_FLOWER =
            net.minecraft.entity.data.DataTracker.registerData(SeedProjectileEntity.class,
                    net.minecraft.entity.data.TrackedDataHandlerRegistry.BOOLEAN);

    private int ticksAlive;
    /** A flower's seed keeps the target it was fired at, so that target is hit even if it is not a monster. */
    private Entity intended;

    public SeedProjectileEntity(EntityType<? extends SeedProjectileEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public SeedProjectileEntity(ServerWorld world, LivingEntity owner) {
        super(ModEntities.SEED_PROJECTILE, world);
        setOwner(owner);
        setNoGravity(true);

        Vec3d eye = owner.getEyePos();
        setPosition(eye.x, eye.y, eye.z);

        Vec3d dir = owner.getRotationVec(1.0f).normalize();
        setVelocity(dir.multiply(INITIAL_SPEED));
    }

    /** A flower's seed: fired from the flower, credited to the flower's owner. */
    public SeedProjectileEntity(ServerWorld world, LivingEntity owner, Vec3d from, Vec3d velocity, Entity intended) {
        super(ModEntities.SEED_PROJECTILE, world);
        setOwner(owner);
        setNoGravity(true);
        dataTracker.set(FROM_FLOWER, true);
        this.intended = intended;
        setPosition(from.x, from.y, from.z);
        setVelocity(velocity);
    }

    public boolean fromFlower() {
        return dataTracker.get(FROM_FLOWER);
    }

    private boolean protectsAlly(Entity target) {
        return WandAllies.protectedFrom(getOwner(), target)
                || (getEntityWorld() instanceof ServerWorld world && owner != null
                    && WandAllies.protectedFrom(world, owner.getUuid(), target));
    }

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
        builder.add(FROM_FLOWER, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getEntityWorld() instanceof ServerWorld sw)) {
            return;
        }

        boolean flower = fromFlower();
        ticksAlive++;
        if (ticksAlive > (flower ? FLOWER_LIFETIME_TICKS : MAX_LIFETIME_TICKS)) {
            discard();
            return;
        }

        // Clients draw its trail of pollen and leaves from the entity itself (BlockNatureEffects).

        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        // ProjectileUtil uses COLLIDER raycasts, which skip non-collision blocks like
        // flowers, tall grass, and saplings. Do a secondary OUTLINE raycast so the seed
        // also registers a hit on those blocks (and uses whichever is closer to the start).
        if (hit.getType() != HitResult.Type.ENTITY) {
            Vec3d start = getEntityPos();
            Vec3d end = start.add(getVelocity());
            BlockHitResult outlineHit = sw.raycast(new RaycastContext(start, end,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE, this));
            if (outlineHit.getType() == HitResult.Type.BLOCK) {
                BlockState outlineState = sw.getBlockState(outlineHit.getBlockPos());
                if (!outlineState.isAir() && outlineState.getFluidState().isEmpty()) {
                    if (hit.getType() == HitResult.Type.MISS
                            || outlineHit.getPos().squaredDistanceTo(start)
                                    < hit.getPos().squaredDistanceTo(start)) {
                        hit = outlineHit;
                    }
                }
            }
        }
        if (hit.getType() != HitResult.Type.MISS) {
            onCollision(hit);
            return;
        }

        Vec3d v = getVelocity();
        Vec3d next = flower ? v : new Vec3d(v.x * DRAG, (v.y - GRAVITY) * DRAG, v.z * DRAG);
        setVelocity(next);
        setPosition(getX() + next.x, getY() + next.y, getZ() + next.z);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        if (!(getEntityWorld() instanceof ServerWorld sw)) {
            return;
        }

        Entity target = entityHitResult.getEntity();
        if (protectsAlly(target)) return;
        Entity owner = getOwner();

        if (target instanceof LivingEntity living) {
            if (fromFlower()) flowerHit(sw, living, owner);
            else {
                DamageSource source = (owner instanceof PlayerEntity caster)
                        ? sw.getDamageSources().playerAttack(caster)
                        : (owner instanceof LivingEntity livingOwner
                                ? sw.getDamageSources().thrown(this, livingOwner)
                                : sw.getDamageSources().magic());
                if (com.anton.elementalwands.util.SpellCombat.damage(living, sw, source, NatureCombat.SEED_DAMAGE, owner,
                        com.anton.elementalwands.data.WizardAffinity.NATURE))
                    NatureCombat.seedDamageDealt(owner, living);
                com.anton.elementalwands.util.BlockNatureEvents.send(sw, com.anton.elementalwands.util.BlockNatureEvents.SEED_HIT,
                        new Vec3d(living.getX(), living.getBodyY(0.5), living.getZ()));
            }
        }

        discard();
    }

    /**
     * A flower's hit must not get in the way of its owner's own: it bypasses the hurt cooldown,
     * and leaves an ordinary target's cooldown as it found it. (A boss keeps its own windows and
     * ignores this damage type for them; see the {@code no_hurt_window} tag.)
     */
    private void flowerHit(ServerWorld sw, LivingEntity living, Entity owner) {
        Entity struck = com.anton.elementalwands.util.SpellCombat.damageTarget(living);
        int cooldown = struck.timeUntilRegen;
        float before = struck instanceof LivingEntity body ? body.getHealth() : 0;
        var type = sw.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.DAMAGE_TYPE)
                .getOrThrow(FlowerTurretManager.DAMAGE_TYPE);
        if (com.anton.elementalwands.util.SpellCombat.damage(living, sw, new DamageSource(type, this, owner),
                FlowerTurretManager.DAMAGE, owner, com.anton.elementalwands.data.WizardAffinity.NATURE)) {
            struck.timeUntilRegen = cooldown;
            NatureCombat.flowerDamageDealt(owner, struck instanceof LivingEntity body ? Math.max(0, before - Math.max(0, body.getHealth())) : 0);
        }
        com.anton.elementalwands.util.BlockNatureEvents.send(sw, com.anton.elementalwands.util.BlockNatureEvents.SEED_HIT, -1, getEntityPos(), Vec3d.ZERO, 1);
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        if (!(getEntityWorld() instanceof ServerWorld sw)) {
            return;
        }

        Vec3d p = blockHitResult.getPos();
        com.anton.elementalwands.util.BlockNatureEvents.send(sw, com.anton.elementalwands.util.BlockNatureEvents.SEED_BLOCK, p);
        sw.playSound(null, blockHitResult.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_AZALEA_LEAVES_HIT,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 1.2f);

        discard();
    }

    @Override
    protected boolean canHit(Entity entity) {
        if (!super.canHit(entity) || protectsAlly(entity)) return false;
        // A flower's seed passes anything that is neither its target nor an enemy.
        return !fromFlower() || entity == intended || FlowerTurretManager.hostile(entity, getOwner());
    }
}
