package com.anton.elementalwands.network;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.item.AbstractWandItem;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public final class ModNetworking {

    private ModNetworking() {
    }

    private static boolean payloadsRegistered = false;

    public static void registerPayloads() {
        if (payloadsRegistered)
            return;
        payloadsRegistered = true;

        // C2S
        PayloadTypeRegistry.playC2S().register(CastUltimatePayload.ID, CastUltimatePayload.CODEC);

        // S2C
        PayloadTypeRegistry.playS2C().register(SyncPlayerDataPayload.ID, SyncPlayerDataPayload.CODEC);
    }

    public static void registerC2SReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(CastUltimatePayload.ID,
                (payload, context) -> handleCastUltimate(context.player()));
    }

    // -----------------------------------------------------------------------
    // Sync helper
    // -----------------------------------------------------------------------

    /**
     * Sends the player's current unlock bitmask and affinity to their client so
     * the HUD can render padlock/glow states and themes correctly.
     */
    public static void syncPlayerData(ServerPlayerEntity player) {
        int skills = player.getAttachedOrElse(EWAttachments.UNLOCKED_SKILLS, 0);
        String affinity = player.getAttachedOrElse(EWAttachments.AFFINITY, "NONE");
        ServerPlayNetworking.send(player, new SyncPlayerDataPayload(skills, affinity));
    }

    // -----------------------------------------------------------------------
    // Handlers
    // -----------------------------------------------------------------------

    private static void handleCastPrimary(ServerPlayerEntity player) {
        if (player.isSpectator()) return;
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof AbstractWandItem wand)) return;
        wand.castPrimary(world, player, stack);
    }

    private static void handleCastUltimate(ServerPlayerEntity player) {
        if (player.isSpectator()) return;
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof AbstractWandItem wand)) return;
        wand.castUltimate(world, player, stack);
    }

    // -----------------------------------------------------------------------
    // Payload records
    // -----------------------------------------------------------------------

    public record CastPrimaryPayload() implements CustomPayload {
        public static final Id<CastPrimaryPayload> ID = new Id<>(
                Identifier.of(ElementalWandsMod.MOD_ID, "cast_primary"));
        public static final CastPrimaryPayload INSTANCE = new CastPrimaryPayload();
        public static final PacketCodec<RegistryByteBuf, CastPrimaryPayload> CODEC = PacketCodec.unit(INSTANCE);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record CastUltimatePayload() implements CustomPayload {
        public static final Id<CastUltimatePayload> ID = new Id<>(
                Identifier.of(ElementalWandsMod.MOD_ID, "cast_ultimate"));
        public static final CastUltimatePayload INSTANCE = new CastUltimatePayload();
        public static final PacketCodec<RegistryByteBuf, CastUltimatePayload> CODEC = PacketCodec.unit(INSTANCE);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S2C packet carrying the server-authoritative unlocked-skills bitmask and affinity. */
    public record SyncPlayerDataPayload(int unlockedSkills, String affinity) implements CustomPayload {
        public static final Id<SyncPlayerDataPayload> ID = new Id<>(
                Identifier.of(ElementalWandsMod.MOD_ID, "sync_player_data"));
        public static final PacketCodec<RegistryByteBuf, SyncPlayerDataPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.INTEGER, SyncPlayerDataPayload::unlockedSkills,
                        PacketCodecs.STRING,  SyncPlayerDataPayload::affinity,
                        SyncPlayerDataPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}
