package com.anton.elementalwands.summit;

import com.anton.elementalwands.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The gong's anchor: it holds nothing, but its GeckoLib animatable sways the plate in the wind and
 * swings it when struck. The server triggers the swing so every client tracking the aerie sees it.
 * GeckoLib does not restart a triggered animation that is still playing, so strikes alternate
 * between two identical swings and each one starts the swing afresh.
 */
public final class StormGongBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final String CONTROLLER = "gong", STRIKE = "strike", STRIKE_B = "strike_b";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.storm_gong.idle");
    private static final RawAnimation SWING = RawAnimation.begin().thenPlay("animation.storm_gong.strike"),
            SWING_B = RawAnimation.begin().thenPlay("animation.storm_gong.strike_b");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Server side and never saved: whether the next strike plays the second swing. */
    private boolean second;

    public StormGongBlockEntity(BlockPos pos, BlockState state) { super(ModBlocks.STORM_GONG_ENTITY, pos, state); }

    /** Swings the gong for everyone near it. Server side; a strike mid-swing starts the swing again. */
    public void strike() {
        triggerAnim(CONTROLLER, second ? STRIKE_B : STRIKE);
        second = !second;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // The idle sway resumes by itself once a strike's swing has played out.
        controllers.add(new AnimationController<StormGongBlockEntity>(CONTROLLER, 3, state -> state.setAndContinue(IDLE))
                .triggerableAnim(STRIKE, SWING).triggerableAnim(STRIKE_B, SWING_B));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
