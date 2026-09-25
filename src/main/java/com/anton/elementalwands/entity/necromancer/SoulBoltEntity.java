package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.entity.AstralDoubleEntity;
import com.anton.elementalwands.registry.ModParticles;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
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
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Slow homing soul. Turns a little each tick, so strafing and cover both beat it. */
public class SoulBoltEntity extends ProjectileEntity implements GeoEntity {
    public static final RegistryKey<DamageType> DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            Identifier.of(ElementalWandsMod.MOD_ID, "necromancer_soul"));
    private static final TrackedData<Boolean> BITING = DataTracker.registerData(SoulBoltEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final RawAnimation FLIGHT = RawAnimation.begin().thenLoop("animation.soul_bolt.flight");
    private static final RawAnimation IMPACT = RawAnimation.begin().thenPlayAndHold("animation.soul_bolt.impact");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int impactTicks;
    private UUID target;
    private double speed = NecromancerRules.BOLT_SPEED;
    private boolean loadedFromSave;

    public SoulBoltEntity(EntityType<? extends SoulBoltEntity> type, World world) { super(type, world); }

    @Override protected void initDataTracker(DataTracker.Builder builder) { builder.add(BITING, false); }
    public boolean isBiting() { return dataTracker.get(BITING); }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<SoulBoltEntity>("jaw", 0,
                state -> state.setAndContinue(state.animatable().isBiting() ? IMPACT : FLIGHT)));
    }
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
        faceVelocity();
        velocityDirty = true;
    }

    @Override public void tick() {
        super.tick();
        if (getEntityWorld() instanceof ServerWorld world) {
            if (loadedFromSave || age > NecromancerRules.BOLT_LIFE || !(getOwner() instanceof NecromancerEntity boss)
                    || !boss.isAlive()) { discard(); return; }
            // Contact is resolved once. Keep only the harmless visual for the jaw snap.
            if (isBiting()) {
                if (++impactTicks >= 8) discard();
                return;
            }
            steer(world, boss);
            faceVelocity();
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
                world.spawnParticles(ModParticles.NECROMANCER_BITE_SHARD, getX(), getY(), getZ(), 4, .15, .15, .15, .02);
                world.playSound(null, getX(), getY(), getZ(), SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 1.4f, .8f);
                setVelocity(Vec3d.ZERO);
                velocityDirty = true;
                dataTracker.set(BITING, true);
                return;
            }
            world.spawnParticles(ModParticles.NECROMANCER_SOUL_WISP, getX(), getY(), getZ(), 1, .05, .05, .05, .005);
            if (age % 3 == 0) world.spawnParticles(ParticleTypes.SOUL, getX(), getY(), getZ(), 1, .08, .08, .08, .01);
        }
        if (isBiting()) return;
        faceVelocity();
        // Same integration on both sides; the server alone decides contact and damage.
        setPosition(getEntityPos().add(getVelocity()));
    }

    private void faceVelocity() {
        Vec3d v = getVelocity();
        if (v.lengthSquared() < 1e-8) return;
        setYaw((float)Math.toDegrees(Math.atan2(-v.x, v.z)));
        setPitch((float)-Math.toDegrees(Math.atan2(v.y, v.horizontalLength())));
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
