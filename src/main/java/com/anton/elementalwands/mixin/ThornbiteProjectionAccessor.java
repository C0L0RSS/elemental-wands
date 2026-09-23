package com.anton.elementalwands.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Match a world-space stem to the held item's separate first-person projection. */
@Mixin(GameRenderer.class)
public interface ThornbiteProjectionAccessor {
    @Invoker("getFov") float elementalwands$getFov(Camera camera, float delta, boolean world);
}
