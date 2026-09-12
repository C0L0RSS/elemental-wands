package com.anton.elementalwands.data;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

public final class EWAttachments {

    public static final int SKILL_SECONDARY = 1;
    public static final int SKILL_ULTIMATE  = 2;

    public static final long SECONDARY_FLUX_COST = 500L;
    public static final long ULTIMATE_FLUX_COST  = 1500L;
    public static final int  SECONDARY_XP_COST   = 15;
    public static final int  ULTIMATE_XP_COST    = 30;

    public static final AttachmentType<Long> ARCANE_FLUX = AttachmentRegistry.create(
            Identifier.of("elementalwands", "arcane_flux"),
            builder -> builder.initializer(() -> 0L).persistent(Codec.LONG).copyOnDeath());

    public static final AttachmentType<Integer> UNLOCKED_SKILLS = AttachmentRegistry.create(
            Identifier.of("elementalwands", "unlocked_skills"),
            builder -> builder.initializer(() -> 0).persistent(Codec.INT).copyOnDeath());

    public static final AttachmentType<String> AFFINITY = AttachmentRegistry.create(
            Identifier.of("elementalwands", "affinity"),
            builder -> builder.initializer(() -> "NONE").persistent(Codec.STRING).copyOnDeath());

    // Receipts live in the same player save as inventory. Death restores gear
    // before vanilla drops it; copying receipts to a dead player's replacement
    // would duplicate those restored drops.
    public static final AttachmentType<NbtCompound> ZEPHYR_GEAR = AttachmentRegistry.create(
            Identifier.of("elementalwands", "zephyr_gear"),
            builder -> builder.persistent(NbtCompound.CODEC));

    public static final AttachmentType<NbtCompound> TITAN_GEAR = AttachmentRegistry.create(
            Identifier.of("elementalwands", "titan_gear"),
            builder -> builder.persistent(NbtCompound.CODEC));

    private EWAttachments() {}

    public static void init() {
        // Triggers class loading so attachments are registered
    }

    public static WizardAffinity getAffinity(PlayerEntity player) {
        try {
            return WizardAffinity.valueOf(player.getAttachedOrElse(AFFINITY, "NONE"));
        } catch (IllegalArgumentException e) {
            return WizardAffinity.NONE;
        }
    }
}
