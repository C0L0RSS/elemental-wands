package com.anton.elementalwands.network;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.item.AbstractWandItem;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public final class ModNetworking {

    private ModNetworking() {
    }

    private static boolean payloadsRegistered = false;

    public static void registerPayloads() {
        if (payloadsRegistered) return;
        payloadsRegistered = true;
        PayloadTypeRegistry.playC2S().register(CastPrimaryPayload.ID, CastPrimaryPayload.CODEC);
    }

    public static void registerC2SReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(CastPrimaryPayload.ID,
                (payload, context) -> handleCastPrimary(context.player()));
    }

    private static void handleCastPrimary(ServerPlayerEntity player) {
        if (player.isSpectator()) return;

        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof AbstractWandItem wand)) return;

        wand.castPrimary(world, player, stack);
    }

    public record CastPrimaryPayload() implements CustomPayload {
        public static final Id<CastPrimaryPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID, "cast_primary"));
        public static final CastPrimaryPayload INSTANCE = new CastPrimaryPayload();
        public static final PacketCodec<RegistryByteBuf, CastPrimaryPayload> CODEC = PacketCodec.unit(INSTANCE);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
