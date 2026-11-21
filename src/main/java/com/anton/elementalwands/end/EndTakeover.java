package com.anton.elementalwands.end;

import com.anton.elementalwands.ElementalWandsMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.feature.EndPlacedFeatures;

/**
 * The End belongs to the First Wizard: no vanilla dragon fight (no dragon, boss bar, crystals, exit
 * portal or gateways; {@code EndDragonFightMixin}), no spikes generated on the main island, and the main
 * island laid out as his arena ({@link EndArenaManager}) behind the portal's rules ({@link EndGate}) and
 * the encounter's ({@link EndEncounter}). The saved dragon-fight data in level.dat is left untouched.
 */
public final class EndTakeover {
    private EndTakeover() {}

    public static void init() {
        // New worlds: no spikes on the main island. Only the feature goes; the biome's mob spawns are vanilla's,
        // so enderman farms off the island still work.
        BiomeModifications.create(Identifier.of(ElementalWandsMod.MOD_ID, "end_takeover"))
                .add(ModificationPhase.REMOVALS, BiomeSelectors.includeByKey(BiomeKeys.THE_END),
                        context -> context.getGenerationSettings().removeFeature(EndPlacedFeatures.END_SPIKE));
        EndArenaManager.init();
        EndGate.init();
        EndEncounter.init();
        EndSky.init();
    }
}
