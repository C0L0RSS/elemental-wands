package com.anton.elementalwands.registry;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.entity.BoulderProjectileEntity;
import com.anton.elementalwands.entity.CalamityTornadoEntity;
import com.anton.elementalwands.entity.ChillSnowballEntity;
import com.anton.elementalwands.entity.InfernoWaveEntity;
import com.anton.elementalwands.entity.VacuumBladeEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModEntities {

        public static final EntityType<BoulderProjectileEntity> BOULDER_PROJECTILE = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "boulder_projectile"),
                        FabricEntityTypeBuilder
                                        .<BoulderProjectileEntity>create(SpawnGroup.MISC, BoulderProjectileEntity::new)
                                        .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                                        .trackRangeBlocks(64)
                                        .trackedUpdateRate(10)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID,
                                                                        "boulder_projectile"))));

        public static final EntityType<ChillSnowballEntity> CHILL_SNOWBALL = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "chill_snowball"),
                        FabricEntityTypeBuilder.<ChillSnowballEntity>create(SpawnGroup.MISC, ChillSnowballEntity::new)
                                        .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                                        .trackRangeBlocks(64)
                                        .trackedUpdateRate(10)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "chill_snowball"))));

        public static final EntityType<InfernoWaveEntity> INFERNO_WAVE = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "inferno_wave"),
                        FabricEntityTypeBuilder.<InfernoWaveEntity>create(SpawnGroup.MISC, InfernoWaveEntity::new)
                                        .dimensions(EntityDimensions.fixed(3.0f, 2.0f)) // Wide crescent hitbox
                                        .trackRangeBlocks(64)
                                        .trackedUpdateRate(1)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "inferno_wave"))));

        public static final EntityType<VacuumBladeEntity> VACUUM_BLADE = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "vacuum_blade"),
                        FabricEntityTypeBuilder.<VacuumBladeEntity>create(SpawnGroup.MISC, VacuumBladeEntity::new)
                                        .dimensions(EntityDimensions.fixed(0.5f, 0.5f)) // Slim blade
                                        .trackRangeBlocks(64)
                                        .trackedUpdateRate(1)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "vacuum_blade"))));

        public static final EntityType<CalamityTornadoEntity> CALAMITY_TORNADO = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "calamity_tornado"),
                        FabricEntityTypeBuilder
                                        .<CalamityTornadoEntity>create(SpawnGroup.MISC,
                                                        (type, world) -> new CalamityTornadoEntity(type, world))
                                        .dimensions(EntityDimensions.fixed(6.0f, 12.0f)) // Large tornado
                                        .trackRangeBlocks(128) // Larger tracking range for big entity
                                        .trackedUpdateRate(1)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "calamity_tornado"))));

        private ModEntities() {
        }

        public static void registerAll() {
                // classload hook
        }
}
