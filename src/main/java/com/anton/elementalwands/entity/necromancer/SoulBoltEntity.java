package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.entity.AstralDoubleEntity;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Slow homing soul. Turns a little each tick, so strafing and cover both beat it. */
public class SoulBoltEntity extends ProjectileEntity implements FlyingItemEntity {
    public static final RegistryKey<DamageType> DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of(ElementalWandsMod.MOD_ID, "necromancer_soul"));
    private static final ItemStack STACK = new ItemStack(Items.SKELETON_SKULL);
    private UUID target;
    private double speed = NecromancerRules.BOLT_SPEED;
    private boolean loadedFromSave;

    public SoulBoltEntity(EntityType<? extends SoulBoltEntity> type, World world) { super(type, world); }

    @Override protected void initDataTracker(DataTracker.Builder builder) {}
    @Override public ItemStack getStack() { return STACK; }
    @Override protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        loadedFromSave = true; // A reloaded bolt must not resume a stale fight.
    }

    void launch(NecromancerEntity boss, ServerPlayerEntity player, Vec3d from, double speed) {
        this.speed = speed;
        setOwner(boss);
        target = player.getUuid();
        setPosition(from);
        setVelocity(player.getBoundingBox().getCenter().subtract(from).normalize().multiply(speed));
        velocityDirty = true;
    }

    @Override public void tick() {
        super.tick();
        if (getEntityWorld() instanceof ServerWorld world) {
            if (loadedFromSave || age > NecromancerRules.BOLT_LIFE || !(getOwner() instanceof NecromancerEntity boss)
                    || !boss.isAlive()) { discard(); return; }
            steer(world, boss);
            Vec3d start = getEntityPos(), end = start.add(getVelocity());
            var block = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, this));
            double fraction = block.getType() == HitResult.Type.MISS ? 1
                    : start.distanceTo(block.getPos()) / Math.max(1e-8, start.distanceTo(end));
            LivingEntity hit = null;
            Box sweep = new Box(start, end).expand(1.5);
            for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class, sweep,
                    e -> e instanceof ServerPlayerEntity p ? NecromancerCombat.canDamage(boss, p) : e instanceof AstralDoubleEntity)) {
                Box box = candidate.getBoundingBox().expand(NecromancerRules.BOLT_RADIUS);
                var contact = box.contains(start) ? java.util.Optional.of(start) : box.raycast(start, end);
                if (contact.isEmpty()) continue;
                double f = start.distanceTo(contact.get()) / Math.max(1e-8, start.distanceTo(end));
                if (f <= fraction) { fraction = f; hit = candidate; }
            }
            if (hit != null || block.getType() != HitResult.Type.MISS) {
                setPosition(start.lerp(end, fraction));
                if (hit != null) hit.damage(world, source(world, boss), NecromancerRules.BOLT_DAMAGE);
                world.spawnParticles(ParticleTypes.SOUL, getX(), getY(), getZ(), 12, .25, .25, .25, .06);
                world.spawnParticles(ParticleTypes.SCULK_SOUL, getX(), getY(), getZ(), 4, .15, .15, .15, .02);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 1.4f, .8f);
                discard();
                return;
            }
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), 1, .05, .05, .05, .005);
            if (age % 3 == 0) world.spawnParticles(ParticleTypes.SOUL, getX(), getY(), getZ(), 1, .08, .08, .08, .01);
        }
        // Same integration on both sides; the server alone decides contact and damage.
        setPosition(getEntityPos().add(getVelocity()));
    }

    /** Bounded turn toward the tracked player; a lost or ineligible target flies straight. */
    private void steer(ServerWorld world, NecromancerEntity boss) {
        if (target == null) return;
        var player = world.getServer().getPlayerManager().getPlayer(target);
        if (player == null || player.getEntityWorld() != world || !NecromancerCombat.canDamage(boss, player)) { target = null; return; }
        Vec3d velocity = getVelocity();
        Vec3d desired = player.getBoundingBox().getCenter().subtract(getEntityPos()).normalize();
        Vec3d current = velocity.normalize();
        double angle = Math.acos(Math.clamp(current.dotProduct(desired), -1, 1));
        if (angle < 1e-4) return;
        double t = Math.min(1, NecromancerRules.BOLT_TURN / angle);
        Vec3d turned = current.lerp(desired, t).normalize();
        setVelocity(turned.multiply(speed));
        velocityDirty = true;
    }

    private DamageSource source(ServerWorld world, NecromancerEntity boss) {
        return new DamageSource(world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(DAMAGE), this, boss);
    }
}
