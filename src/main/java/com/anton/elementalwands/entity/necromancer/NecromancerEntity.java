package com.anton.elementalwands.entity.necromancer;

import com.anton.elementalwands.entity.WandBoss;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Summon-only caster boss. {@link NecromancerCombat} owns every decision; this class owns state. */
public class NecromancerEntity extends PathAwareEntity implements GeoEntity, WandBoss {
    static final String CONTROLLER = "necromancer";
    private static final String PASSIVE_TAG = "ew_necromancer_passive";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.hollow_necromancer.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.hollow_necromancer.walk");
    private static final TrackedData<Integer> DRAIN_TARGET = DataTracker.registerData(NecromancerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private record HurtWindow(long until, float damage) {}
    private final Map<UUID, HurtWindow> hurtWindows = new HashMap<>();
    private static final UUID ENVIRONMENT_DAMAGE = new UUID(0, 0);
    private final NecromancerCombat combat = new NecromancerCombat(this);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public NecromancerEntity(EntityType<? extends NecromancerEntity> type, World world) {
        super(type, world);
        setPersistent();
        experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, NecromancerRules.health(1))
                .add(EntityAttributes.MOVEMENT_SPEED, .26)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 0.0)
                .add(EntityAttributes.FOLLOW_RANGE, NecromancerRules.ENCOUNTER_RANGE);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(DRAIN_TARGET, -1);
    }

    /** Entity id of the drained player, so a client tether can follow it; -1 when idle. */
    public int getDrainTarget() { return dataTracker.get(DRAIN_TARGET); }
    void setDrainTarget(int id) { dataTracker.set(DRAIN_TARGET, id); }

    @Override public boolean isBossAggressive() { return !getCommandTags().contains(PASSIVE_TAG); }

    public void startFight() { removeCommandTag(PASSIVE_TAG); combat.start(); }
    public void stopFight() { addCommandTag(PASSIVE_TAG); combat.cancel(); }
    public void testAction(ServerPlayerEntity player, NecromancerRules.Action action) {
        addCommandTag(PASSIVE_TAG);
        combat.cancel();
        combat.testAction(player, action);
    }
    public String status() { return combat.status(); }
    NecromancerCombat combat() { return combat; }

    @Override
    public double getAttributeValue(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute) {
        double value = super.getAttributeValue(attribute);
        // Vanilla caps max health at 1024; large parties need more without changing the shared attribute.
        if (attribute.equals(EntityAttributes.MAX_HEALTH) && getAttributeInstance(attribute).getBaseValue() > 1024)
            return Math.max(value, getAttributeInstance(attribute).getBaseValue());
        return value;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(world, source, amount);
        Entity attacker = source.getAttacker();
        // Its own army and spells never wear it down.
        if (attacker == this || NecromancerMinion.belongsTo(attacker, this)) return false;
        if (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.DROWN)) return false;
        // Per-attacker hurt windows: one teammate's hit must not swallow another's simultaneous spell.
        long now = world.getTime();
        hurtWindows.values().removeIf(window -> window.until() <= now);
        UUID key = attacker == null ? ENVIRONMENT_DAMAGE : attacker.getUuid();
        HurtWindow previous = hurtWindows.get(key);
        timeUntilRegen = previous == null ? 0 : 10 + (int)(previous.until() - now);
        lastDamageTaken = previous == null ? 0 : previous.damage();
        boolean accepted = super.damage(world, source, amount);
        if (accepted) hurtWindows.put(key, new HurtWindow(previous == null ? now + 10 : previous.until(), lastDamageTaken));
        return accepted;
    }

    @Override protected boolean isInSameTeam(Entity other) {
        return NecromancerMinion.belongsTo(other, this) || super.isInSameTeam(other);
    }

    @Override public void addVelocity(double x, double y, double z) {
        // Authored blinks and steps only; spell impulses do not shove the boss.
    }
    @Override public boolean isPushable() { return false; }
    @Override protected void initGoals() {
        // The encounter controller chooses spells and movement.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getEntityWorld() instanceof ServerWorld world)) return;
        if (!isAlive()) { combat.cancel(); return; }
        if (isBossAggressive()) combat.tick(world);
        else combat.tickReview(world);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (getEntityWorld() instanceof ServerWorld) combat.cancel();
        super.remove(reason);
    }

    @Override
    protected void readCustomData(net.minecraft.storage.ReadView view) {
        super.readCustomData(view);
        // A saved mid-cast NoAI flag must not immobilize the next encounter.
        if (isBossAggressive()) setAiDisabled(false);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<NecromancerEntity>(CONTROLLER, 3, state -> {
                    if (state.controller().getTriggeredAnimation() != null) return PlayState.CONTINUE;
                    return state.setAndContinue(state.isMoving() ? WALK : IDLE);
                }).receiveTriggeredAnimations()
                .triggerableAnim("bolt", RawAnimation.begin().thenPlay("animation.hollow_necromancer.cast_bolt"))
                .triggerableAnim("hands", RawAnimation.begin().thenPlay("animation.hollow_necromancer.cast_hands"))
                .triggerableAnim("drain", RawAnimation.begin().thenPlay("animation.hollow_necromancer.drain"))
                .triggerableAnim("raise", RawAnimation.begin().thenPlay("animation.hollow_necromancer.raise"))
                .triggerableAnim("blink", RawAnimation.begin().thenPlay("animation.hollow_necromancer.blink")));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
