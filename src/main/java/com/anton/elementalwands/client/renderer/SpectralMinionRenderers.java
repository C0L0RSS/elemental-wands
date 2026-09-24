package com.anton.elementalwands.client.renderer;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.SkeletonEntityRenderer;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.client.render.entity.state.SkeletonEntityRenderState;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;

/** Vanilla undead bodies washed in the Necromancer's pale soul-fire tint. */
public final class SpectralMinionRenderers {
    /** ARGB multiplier applied to the vanilla textures. */
    static final int TINT = 0xFF9FE0F0;

    private SpectralMinionRenderers() {}

    public static final class Skeleton extends SkeletonEntityRenderer {
        public Skeleton(EntityRendererFactory.Context context) { super(context); }
        @Override protected int getMixColor(SkeletonEntityRenderState state) { return TINT; }
    }

    public static final class Zombie extends ZombieEntityRenderer {
        public Zombie(EntityRendererFactory.Context context) { super(context); }
        @Override protected int getMixColor(ZombieEntityRenderState state) { return TINT; }
    }
}
