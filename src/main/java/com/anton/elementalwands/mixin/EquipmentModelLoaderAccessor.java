package com.anton.elementalwands.mixin;

import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The game's armour textures by equipment asset, for drawing worn armour outside an entity renderer (the Storm Roc intro's player). */
@Mixin(EntityRenderManager.class)
public interface EquipmentModelLoaderAccessor {
    @Accessor("equipmentModelLoader")
    EquipmentModelLoader elementalwands$equipmentModelLoader();
}
