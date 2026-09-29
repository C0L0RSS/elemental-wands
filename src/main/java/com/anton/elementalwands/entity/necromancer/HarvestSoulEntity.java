package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.util.SoulGlow;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Soul Harvest: a soul called out of the ground that drifts into the colossus's ribcage and
 * heals it. Fragile on purpose: any spell or blow destroys it. Never saves.
 */
public class HarvestSoulEntity extends MobEntity implements GeoEntity {
    private static final RawAnimation RISE = RawAnimation.begin().thenPlay("animation.harvest_soul.rise");
    private static final RawAnimation DRIFT = RawAnimation.begin().thenLoop("animation.harvest_soul.drift");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID boss;
    private int seed;

    public HarvestSoulEntity(EntityType<? extends HarvestSoulEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        noClip = true; // A ghost: it passes through graves and roots on its way in.
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.MAX_HEALTH, NecromancerRules.HARVEST_SOUL_HEALTH);
    }

    void bind(NecromancerEntity owner) { boss = owner.getUuid(); seed = getRandom().nextInt(1000); }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<HarvestSoulEntity>("soul", 2,
                state -> state.setAndContinue(state.animatable().age < NecromancerRules.HARVEST_RISE ? RISE : DRIFT)));
    }

    @Override public boolean shouldSave() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushAway(net.minecraft.entity.Entity entity) {}
    @Override public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
        return source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.FALL) || source.isOf(DamageTypes.DROWN)
                || source.getAttacker() instanceof NecromancerEntity || source.getAttacker() instanceof NecromancerMinion
                || super.isInvulnerableTo(world, source);
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        boolean dealt = super.damage(world, source, amount);
        if (dealt && getHealth() <= 0) {
            world.spawnParticles(ParticleTypes.SOUL, getX(), getY() + .4, getZ(), 12, .25, .25, .25, .05);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.PARTICLE_SOUL_ESCAPE.value(), SoundCategory.HOSTILE, 1.6f, 1.4f);
            if (world.getEntity(boss) instanceof NecromancerEntity owner) owner.combat().harvestDestroyed(this, source);
            discard();
        }
        return dealt;
    }

    @Override
    public void tick() {
        super.tick();
        setVelocity(Vec3d.ZERO);
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        if (boss == null || !(world.getEntity(boss) instanceof NecromancerEntity owner) || !owner.isAlive() || !owner.isColossus()) {
            world.spawnParticles(ParticleTypes.SOUL, getX(), getY() + .4, getZ(), 6, .2, .2, .2, .02);
            discard();
            return;
        }
        SoulGlow.light(world, getBoundingBox().getCenter(), NecromancerRules.GLOW_HARVEST, NecromancerRules.GLOW_LINGER);
        if (age < NecromancerRules.HARVEST_RISE) {
            // Claws its way up out of the soil before it starts to drift.
            setPosition(getX(), getY() + 1.6 / NecromancerRules.HARVEST_RISE, getZ());
            world.spawnParticles(ParticleTypes.SOUL, getX(), getY(), getZ(), 1, .15, .05, .15, .01);
            return;
        }
        Vec3d ribs = owner.getEntityPos().add(0, 2.6, 0), delta = ribs.subtract(getEntityPos());
        if (delta.length() <= NecromancerRules.HARVEST_ABSORB) { owner.combat().harvestAbsorbed(world, this); discard(); return; }
        Vec3d step = delta.normalize().multiply(NecromancerRules.HARVEST_SPEED);
        // A slow sideways weave, so it reads as a drifting soul rather than a projectile.
        Vec3d side = new Vec3d(-step.z, 0, step.x).normalize().multiply(.03 * MathHelper.sin((age + seed) * .15f));
        Vec3d next = getEntityPos().add(step).add(side);
        setPosition(next.x, next.y, next.z);
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        setYaw(yaw); setBodyYaw(yaw); setHeadYaw(yaw);
        if (age % 3 == 0) world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, false, getX(), getY() + .35, getZ(), 1, .08, .08, .08, .005);
    }
}
