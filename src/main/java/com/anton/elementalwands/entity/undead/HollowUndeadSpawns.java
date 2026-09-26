package com.anton.elementalwands.entity.undead;

import com.anton.elementalwands.registry.ModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.world.Heightmap;

/**
 * Attributes and natural spawning. The family joins the ordinary night monster pool wherever
 * vanilla zombies spawn, under the same darkness rules; the monster cap is unchanged, so they
 * share it with zombies and skeletons rather than adding to it.
 */
public final class HollowUndeadSpawns {
    /** Spawn weight and group size: crawlers are common packs, archers pairs, brutes rare loners. */
    static final int CRAWLER_WEIGHT = 45, CRAWLER_MIN = 2, CRAWLER_MAX = 4;
    static final int ARCHER_WEIGHT = 25, ARCHER_MIN = 1, ARCHER_MAX = 2;
    static final int BRUTE_WEIGHT = 8, BRUTE_MIN = 1, BRUTE_MAX = 1;

    private HollowUndeadSpawns() {}

    public static void register() {
        FabricDefaultAttributeRegistry.register(ModEntities.HOLLOW_CRAWLER, HollowCrawlerEntity.createAttributes().build());
        FabricDefaultAttributeRegistry.register(ModEntities.HOLLOW_ARCHER, HollowArcherEntity.createAttributes().build());
        FabricDefaultAttributeRegistry.register(ModEntities.HOLLOW_BRUTE, HollowBruteEntity.createAttributes().build());
        spawn(ModEntities.HOLLOW_CRAWLER, CRAWLER_WEIGHT, CRAWLER_MIN, CRAWLER_MAX);
        spawn(ModEntities.HOLLOW_ARCHER, ARCHER_WEIGHT, ARCHER_MIN, ARCHER_MAX);
        spawn(ModEntities.HOLLOW_BRUTE, BRUTE_WEIGHT, BRUTE_MIN, BRUTE_MAX);
    }

    private static <T extends HostileEntity> void spawn(EntityType<T> type, int weight, int min, int max) {
        SpawnRestriction.register(type, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld().and(BiomeSelectors.spawnsOneOf(EntityType.ZOMBIE)),
                SpawnGroup.MONSTER, type, weight, min, max);
    }
}
