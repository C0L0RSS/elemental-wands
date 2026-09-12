package com.anton.elementalwands.registry;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.entity.AwakenedTreeEntity;
import com.anton.elementalwands.entity.SeedProjectileEntity;
import com.anton.elementalwands.entity.HollowPurpleOrbEntity;
import com.anton.elementalwands.entity.InfernoWaveEntity;
import com.anton.elementalwands.entity.SingularityBoltEntity;
import com.anton.elementalwands.entity.FireSpiritEntity;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.entity.GuardianRockEntity;
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

        public static final EntityType<com.anton.elementalwands.entity.PyreFrontEntity> PYRE_FRONT = Registry.register(
                Registries.ENTITY_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "pyre_front"),
                FabricEntityTypeBuilder.<com.anton.elementalwands.entity.PyreFrontEntity>create(SpawnGroup.MISC, com.anton.elementalwands.entity.PyreFrontEntity::new)
                        .dimensions(EntityDimensions.fixed(.1f, .1f)).trackRangeBlocks(96).trackedUpdateRate(1)
                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "pyre_front"))));

        public static final EntityType<com.anton.elementalwands.entity.StoneClusterEntity> STONE_CLUSTER = Registry.register(
                Registries.ENTITY_TYPE, Identifier.of(ElementalWandsMod.MOD_ID,"stone_cluster"),
                EntityType.Builder.<com.anton.elementalwands.entity.StoneClusterEntity>create(
                        com.anton.elementalwands.entity.StoneClusterEntity::new,SpawnGroup.MISC)
                        .dimensions(2f,2f).maxTrackingRange(8).trackingTickInterval(1)
                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,Identifier.of(ElementalWandsMod.MOD_ID,"stone_cluster"))));

        public static final EntityType<SeedProjectileEntity> SEED_PROJECTILE = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "seed_projectile"),
                        FabricEntityTypeBuilder
                                        .<SeedProjectileEntity>create(SpawnGroup.MISC,
                                                        SeedProjectileEntity::new)
                                        .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                                        .trackRangeBlocks(64)
                                        .trackedUpdateRate(1)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID,
                                                                        "seed_projectile"))));

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

        public static final EntityType<AwakenedTreeEntity> AWAKENED_TREE = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "awakened_tree"),
                        FabricEntityTypeBuilder
                                        .<AwakenedTreeEntity>create(SpawnGroup.MISC, AwakenedTreeEntity::new)
                                        .dimensions(EntityDimensions.fixed(3.5f, 6.0f))
                                        .trackRangeBlocks(96)
                                        .trackedUpdateRate(3)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "awakened_tree"))));

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

        public static final EntityType<GuardianRockEntity> GUARDIAN_ROCK = Registry.register(
                        Registries.ENTITY_TYPE, Identifier.of(ElementalWandsMod.MOD_ID, "guardian_rock"),
                        FabricEntityTypeBuilder.<GuardianRockEntity>create(SpawnGroup.MISC, GuardianRockEntity::new)
                                        .dimensions(EntityDimensions.fixed(1.4f, 1.4f))
                                        .trackRangeBlocks(96).trackedUpdateRate(1)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "guardian_rock"))));

        // Core-sized provisional hitbox; the long hands reach beyond it.
        // Summon-only boss; its encounter controller disables aggression in Peaceful.
        public static final EntityType<FracturedGuardianEntity> FRACTURED_GUARDIAN = Registry.register(
                        Registries.ENTITY_TYPE,
                        Identifier.of(ElementalWandsMod.MOD_ID, "fractured_guardian"),
                        FabricEntityTypeBuilder
                                        .<FracturedGuardianEntity>create(SpawnGroup.MISC, FracturedGuardianEntity::new)
                                        .dimensions(EntityDimensions.fixed(3.2f, 5.2f))
                                        .trackRangeBlocks(192)
                                        .trackedUpdateRate(1)
                                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,
                                                        Identifier.of(ElementalWandsMod.MOD_ID, "fractured_guardian"))));

        public static final EntityType<com.anton.elementalwands.entity.GuardianArenaEntity> GUARDIAN_ARENA = Registry.register(
                Registries.ENTITY_TYPE, Identifier.of(ElementalWandsMod.MOD_ID,"guardian_arena"),
                FabricEntityTypeBuilder.<com.anton.elementalwands.entity.GuardianArenaEntity>create(SpawnGroup.MISC,
                        com.anton.elementalwands.entity.GuardianArenaEntity::new)
                        .dimensions(EntityDimensions.fixed(128,1)).trackRangeBlocks(256).trackedUpdateRate(1)
                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,Identifier.of(ElementalWandsMod.MOD_ID,"guardian_arena"))));

        public static final EntityType<com.anton.elementalwands.entity.GuardianLiftEntity> GUARDIAN_LIFT = Registry.register(
                Registries.ENTITY_TYPE, Identifier.of(ElementalWandsMod.MOD_ID,"guardian_lift"),
                FabricEntityTypeBuilder.<com.anton.elementalwands.entity.GuardianLiftEntity>create(SpawnGroup.MISC,
                        com.anton.elementalwands.entity.GuardianLiftEntity::new)
                        .dimensions(EntityDimensions.fixed(.1f,.1f)).trackRangeBlocks(256).trackedUpdateRate(1)
                        .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE,Identifier.of(ElementalWandsMod.MOD_ID,"guardian_lift"))));

        private ModEntities() {
        }

        public static void registerAll() {
                // classload hook — field initializers run on class load
        }
}
