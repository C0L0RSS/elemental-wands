package com.anton.elementalwands.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ClientPlayerData {

    private static int unlockedSkills = 0;

    public static final int SKILL_SECONDARY = 1;
    public static final int SKILL_ULTIMATE  = 2;

    private ClientPlayerData() {}

    public static boolean isSecondaryUnlocked() {
        return (unlockedSkills & SKILL_SECONDARY) != 0;
    }

    public static boolean isUltimateUnlocked() {
        return (unlockedSkills & SKILL_ULTIMATE) != 0;
    }

    public static void setUnlockedSkills(int skills) {
        unlockedSkills = skills;
    }

    public static void reset() {
        unlockedSkills = 0;
    }
}
