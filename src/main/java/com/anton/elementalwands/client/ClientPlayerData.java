package com.anton.elementalwands.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.anton.elementalwands.data.WizardAffinity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public final class ClientPlayerData {

    private static int unlockedSkills = 0;
    private static String affinity = "NONE";
    private static List<BlockPos> natureSeedlings = List.of();
    private static final Map<Integer, EntangleState> entangledEntities = new HashMap<>();

    public static final int SKILL_SECONDARY = 1;
    public static final int SKILL_ULTIMATE  = 2;

    private ClientPlayerData() {}

    public static boolean isSecondaryUnlocked() {
        return (unlockedSkills & SKILL_SECONDARY) != 0;
    }

    public static boolean isUltimateUnlocked() {
        return (unlockedSkills & SKILL_ULTIMATE) != 0;
    }

    public static WizardAffinity getAffinity() {
        try {
            return WizardAffinity.valueOf(affinity);
        } catch (IllegalArgumentException e) {
            return WizardAffinity.NONE;
        }
    }

    public static void setUnlockedSkills(int skills, String affinityStr) {
        unlockedSkills = skills;
        affinity = affinityStr;
    }

    public static void setNatureSeedlings(List<BlockPos> positions) {
        natureSeedlings = List.copyOf(positions);
    }

    public static List<BlockPos> getNatureSeedlings() {
        return natureSeedlings;
    }

    public static void clearNatureSeedlings() {
        natureSeedlings = List.of();
    }

    public static void setEntangleStacks(int entityId, int stacks, long changedAtTick,
            int rootVisualTicks) {
        if (stacks <= 0) {
            entangledEntities.remove(entityId);
            return;
        }

        int clampedStacks = Math.min(5, stacks);
        long rootVisualUntilTick = changedAtTick + Math.max(0, rootVisualTicks);
        entangledEntities.put(entityId,
                new EntangleState(clampedStacks, changedAtTick, rootVisualUntilTick));
    }

    public static EntangleState getEntangleState(int entityId) {
        return entangledEntities.get(entityId);
    }

    public static int getEntangleStacks(int entityId) {
        EntangleState state = entangledEntities.get(entityId);
        return state != null ? state.stacks() : 0;
    }

    public static Map<Integer, EntangleState> getEntangledEntities() {
        return Collections.unmodifiableMap(entangledEntities);
    }

    public static void clearEntangleState(int entityId) {
        entangledEntities.remove(entityId);
    }

    public static void clearEntangleStates() {
        entangledEntities.clear();
    }

    public static void reset() {
        unlockedSkills = 0;
        affinity = "NONE";
        natureSeedlings = List.of();
        entangledEntities.clear();
    }

    public record EntangleState(int stacks, long changedAtTick, long rootVisualUntilTick) {}
}
