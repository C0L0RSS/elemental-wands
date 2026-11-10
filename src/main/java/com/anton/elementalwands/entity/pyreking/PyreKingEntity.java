package com.anton.elementalwands.entity.pyreking;

import com.anton.elementalwands.entity.WandBoss;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Pyre King's body: his model, his clips and nothing else yet. He stands in his guard and
 * plays a clip when an operator rehearses one (/ew pyreking). He has no fight: no targets, no
 * damage, no movement of his own. The clips that turn or move him (Turning Cut, Zip, Lunge) do
 * so only inside the clip here, so he is back where he stood when it ends.
 */
public class PyreKingEntity extends PathAwareEntity implements GeoEntity, WandBoss {
    public static final String CONTROLLER = "main";
    /** A placeholder until the fight is tuned. */
    private static final double HEALTH = 600;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.pyre_king.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private String rehearsal = "";

    public PyreKingEntity(EntityType<? extends PyreKingEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, HEALTH)
                .add(EntityAttributes.MOVEMENT_SPEED, 0)
                .add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.FALL_DAMAGE_MULTIPLIER, 0.0);
    }

    /** Play one clip from the start; a looping clip plays until {@link #stopRehearsal()}. */
    public boolean rehearse(String clip) {
        if (PyreKingClips.get(clip) == null) return false;
        stopRehearsal();
        rehearsal = clip;
        triggerAnim(CONTROLLER, clip);
        return true;
    }

    public void stopRehearsal() {
        if (rehearsal.isEmpty()) return;
        stopTriggeredAnim(CONTROLLER, rehearsal);
        rehearsal = "";
    }

    @Override public boolean isBossAggressive() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushAway(net.minecraft.entity.Entity entity) {}
    @Override public boolean cannotDespawn() { return true; }
    @Override public boolean isFireImmune() { return true; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // No blending: every clip starts and ends in his guard, and the ones that spin him end a
        // whole turn round, which a blend would unwind.
        var controller = new AnimationController<PyreKingEntity>(CONTROLLER, 0, state -> {
            if (state.controller().getTriggeredAnimation() != null) return PlayState.CONTINUE;
            return state.setAndContinue(IDLE);
        }).receiveTriggeredAnimations();
        for (PyreKingClips.Clip clip : PyreKingClips.ALL) {
            var animation = RawAnimation.begin();
            controller.triggerableAnim(clip.name(), clip.loop() ? animation.thenLoop(clip.animation()) : animation.thenPlay(clip.animation()));
        }
        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
