package com.anton.elementalwands.registry;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.entity.BoulderProjectileEntity;
import com.anton.elementalwands.entity.BrinicleShardProjectileEntity;
import com.anton.elementalwands.entity.CalamityTornadoEntity;
import com.anton.elementalwands.entity.HollowPurpleOrbEntity;
import com.anton.elementalwands.entity.InfernoWaveEntity;
import com.anton.elementalwands.entity.SingularityBoltEntity;
import com.anton.elementalwands.entity.FireSpiritEntity;
import com.anton.elementalwands.entity.StoneZombieEntity;
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

        public static final EntityType<BrinicleShardProjectileEntity> BRINICLE_SHARD_PROJECTILE = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "brinicle_shard_projectile"),
                        FabricEntityTypeBuilder
                                        .<BrinicleShardProjectileEntity>create(SpawnGroup.MISC,
                                                        BrinicleShardProjectileEntity::new)
                                        .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                                        .trackRangeBlocks(64)
                                        .trackedUpdateRate(10)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID,
                                                                        "brinicle_shard_projectile"))));

        public static final EntityType<InfernoWaveEntity> INFERNO_WAVE = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "inferno_wave"),
                        FabricEntityTypeBuilder.<InfernoWaveEntity>create(SpawnGroup.MISC, InfernoWaveEntity::new)
                                        .dimensions(EntityDimensions.fixed(3.0f, 2.0f))
                                        .trackRangeBlocks(64)
                                        .trackedUpdateRate(1)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "inferno_wave"))));

        public static final EntityType<VacuumBladeEntity> VACUUM_BLADE = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "vacuum_blade"),
                        FabricEntityTypeBuilder.<VacuumBladeEntity>create(SpawnGroup.MISC, VacuumBladeEntity::new)
                                        .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
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
                                        .dimensions(EntityDimensions.fixed(6.0f, 12.0f))
                                        .trackRangeBlocks(128)
                                        .trackedUpdateRate(1)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "calamity_tornado"))));

        public static final EntityType<SingularityBoltEntity> SINGULARITY_BOLT = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "singularity_bolt"),
                        FabricEntityTypeBuilder
                                        .<SingularityBoltEntity>create(SpawnGroup.MISC, SingularityBoltEntity::new)
                                        .dimensions(EntityDimensions.fixed(0.35f, 0.35f))
                                        .trackRangeBlocks(64)
                                        .trackedUpdateRate(1)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "singularity_bolt"))));

        public static final EntityType<HollowPurpleOrbEntity> HOLLOW_PURPLE_ORB = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "hollow_purple_orb"),
                        FabricEntityTypeBuilder
                                        .<HollowPurpleOrbEntity>create(SpawnGroup.MISC, HollowPurpleOrbEntity::new)
                                        .dimensions(EntityDimensions.fixed(6.0f, 6.0f))
                                        .trackRangeBlocks(128)
                                        .trackedUpdateRate(1)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "hollow_purple_orb"))));

        // 1.5x vanilla zombie: 0.8 * 1.5 = 1.2 wide, 1.9 * 1.5 = 2.85 tall
        public static final EntityType<StoneZombieEntity> STONE_ZOMBIE = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "stone_zombie"),
                        FabricEntityTypeBuilder
                                        .<StoneZombieEntity>create(SpawnGroup.MONSTER, StoneZombieEntity::new)
                                        .dimensions(EntityDimensions.fixed(1.2f, 2.85f))
                                        .trackRangeBlocks(80)
                                        .trackedUpdateRate(3)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "stone_zombie"))));

        public static final EntityType<FireSpiritEntity> FIRE_SPIRIT = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "fire_spirit"),
                        FabricEntityTypeBuilder
                                        .<FireSpiritEntity>create(SpawnGroup.MONSTER, FireSpiritEntity::new)
                                        .dimensions(EntityDimensions.fixed(0.8f, 0.8f))
                                        .trackRangeBlocks(64)
                                        .trackedUpdateRate(3)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "fire_spirit"))));

        private ModEntities() {
        }

        public static void registerAll() {
                // classload hook — field initializers run on class load
        }
}
