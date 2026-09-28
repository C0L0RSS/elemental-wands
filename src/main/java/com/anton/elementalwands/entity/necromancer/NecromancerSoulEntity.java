package com.anton.elementalwands.entity.necromancer;

import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The caster inside: the Necromancer's soul, torn out of the colossus's ribcage at a quarter
 * health. {@link NecromancerCombat} flies it and casts through it. Its wounds are the boss's:
 * every hit passes to the colossus and counts toward dragging the soul back. Never saves.
 */
public class NecromancerSoulEntity extends MobEntity implements GeoEntity {
    private static final RawAnimation FLIGHT = RawAnimation.begin().thenLoop("animation.soul_bolt.flight");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID boss;

    public NecromancerSoulEntity(EntityType<? extends NecromancerSoulEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        noClip = true;
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.MAX_HEALTH, 100);
    }

    void bind(NecromancerEntity owner) { boss = owner.getUuid(); }

    @Override public boolean shouldSave() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushAway(net.minecraft.entity.Entity entity) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<NecromancerSoulEntity>("jaw", 0, state -> state.setAndContinue(FLIGHT)));
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.FALL) || source.isOf(DamageTypes.DROWN)) return false;
        if (source.getAttacker() instanceof NecromancerEntity || source.getAttacker() instanceof NecromancerMinion) return false;
        if (boss == null || !(world.getEntity(boss) instanceof NecromancerEntity owner) || !owner.soulHit(world, source, amount)) return false;
        // The red flash and hurt sound on the soul itself; its own health never runs out.
        super.damage(world, source, Math.min(amount, 1));
        setHealth(getMaxHealth());
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        setVelocity(Vec3d.ZERO);
        if (getEntityWorld().isClient()) {
            // A crown of soul fire rising off the skull.
            var random = getRandom();
            for (int i = 0; i < 2; i++)
                getEntityWorld().addParticleClient(ParticleTypes.SOUL_FIRE_FLAME, getX() + (random.nextDouble() - .5) * .8,
                        getY() + .9 + random.nextDouble() * .4, getZ() + (random.nextDouble() - .5) * .8, 0, .03, 0);
            return;
        }
        if (boss == null || !(getEntityWorld() instanceof ServerWorld world) || !(world.getEntity(boss) instanceof NecromancerEntity owner)
                || !owner.isAlive()) discard();
    }
}
