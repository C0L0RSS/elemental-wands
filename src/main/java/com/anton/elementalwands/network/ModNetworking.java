package com.anton.elementalwands.network;

import com.anton.elementalwands.ElementalWandsMod;
import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.item.AbstractWandItem;

import java.util.Collection;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

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
        PayloadTypeRegistry.playC2S().register(CastSlotPayload.ID, CastSlotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ReleaseSpellPayload.ID, ReleaseSpellPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HubActionPayload.ID, HubActionPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(AlternateSpellPayload.ID, AlternateSpellPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FlashoverStatePayload.ID, FlashoverStatePayload.CODEC);

        // S2C
        PayloadTypeRegistry.playS2C().register(SyncPlayerDataPayload.ID, SyncPlayerDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HubFeedbackPayload.ID, HubFeedbackPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(WelcomePayload.ID, WelcomePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FireBuildPayload.ID, FireBuildPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FireLeapCommitPayload.ID, FireLeapCommitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncNatureSeedlingsPayload.ID, SyncNatureSeedlingsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncEntangleStacksPayload.ID, SyncEntangleStacksPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncStoneClusterPayload.ID, SyncStoneClusterPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StoneStaggerPayload.ID, StoneStaggerPayload.CODEC);
    }

    public static void registerC2SReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(AlternateSpellPayload.ID, (payload, context) -> com.anton.elementalwands.util.FlashoverManager.detonate(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(CastUltimatePayload.ID,
                (payload, context) -> handleCastUltimate(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(FireLeapCommitPayload.ID, (payload, context) ->
                com.anton.elementalwands.util.FireLeapManager.commit(context.player(),new net.minecraft.util.math.Vec3d(payload.x(),payload.y(),payload.z())));
        ServerPlayNetworking.registerGlobalReceiver(ReleaseSpellPayload.ID, (payload, context) -> com.anton.elementalwands.util.FireBuildManager.stop(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(CastSlotPayload.ID,
                (payload, context) -> com.anton.elementalwands.util.WandLoadouts.cast(context.player(), payload.slot()));
        ServerPlayNetworking.registerGlobalReceiver(HubActionPayload.ID,
                (payload, context) -> handleHub(context.player(), payload));
    }

    // -----------------------------------------------------------------------
    // Sync helper
    // -----------------------------------------------------------------------

    /**
     * Sends the player's current unlock bitmask and affinity to their client so
     * the HUD can render padlock/glow states and themes correctly.
     */
    public static void syncPlayerData(ServerPlayerEntity player) {
        syncFireBuild(player);
        com.anton.elementalwands.util.FlashoverManager.sync(player);
        int skills = com.anton.elementalwands.util.WandProgression.skills(player);
        String affinity = player.getAttachedOrElse(EWAttachments.AFFINITY, "NONE");
        ServerPlayNetworking.send(player, new SyncPlayerDataPayload(skills, affinity,
                com.anton.elementalwands.util.WandProgression.flux(player),
                com.anton.elementalwands.util.WandLoadouts.get(player),
                com.anton.elementalwands.util.WandLoadouts.canEdit(player),
                com.anton.elementalwands.util.WandProgression.owned(player)));
    }

    public static void openHub(ServerPlayerEntity player) {
        com.anton.elementalwands.util.WandGuide.removeLegacyBooks(player.getInventory());
        syncPlayerData(player);
        ServerPlayNetworking.send(player, new HubFeedbackPayload(true, ""));
    }

    private static void handleHub(ServerPlayerEntity player, HubActionPayload payload) {
        String message = "";
        if (payload.action().equals("welcome_seen")) { player.setAttached(EWAttachments.WELCOME_SEEN, true); return; }
        if (payload.action().equals("refresh")) { syncPlayerData(player); return; }
        if (!EWAttachments.getAffinity(player).name().equals(payload.affinity())) {
            message = "Your affinity changed. Reopen the hub.";
        } else if (payload.action().equals("equip")) {
            message = com.anton.elementalwands.util.WandLoadouts.equip(player, payload.affinity(), payload.slot(), payload.value());
        } else if (!com.anton.elementalwands.util.WandLoadouts.canEdit(player)) {
            message = "You can change spells outside combat.";
        } else if (payload.action().equals("unlock")) {
            message = com.anton.elementalwands.util.WandProgression.purchase(player, payload.affinity(), payload.value());
        } else if (payload.action().equals("choose")) {
            try {
                var choice = com.anton.elementalwands.data.WizardAffinity.valueOf(payload.value());
                if (choice != com.anton.elementalwands.data.WizardAffinity.NONE) {
                    ElementalWandsMod.handleAffinitySet(player.getCommandSource(), choice);
                    message = "Element changed. Your progress is saved.";
                }
            } catch (IllegalArgumentException ignored) { message = "Unknown element."; }
        }
        syncPlayerData(player);
        ServerPlayNetworking.send(player, new HubFeedbackPayload(false, message));
    }

    public static void syncNatureSeedlings(ServerPlayerEntity player, List<BlockPos> positions) {
        ServerPlayNetworking.send(player, new SyncNatureSeedlingsPayload(List.copyOf(positions)));
    }

    public static void syncEntangleStacks(ServerPlayerEntity player, LivingEntity target, int stacks,
            int rootVisualTicks) {
        ServerPlayNetworking.send(player,
                new SyncEntangleStacksPayload(target.getId(), stacks, Math.max(0, rootVisualTicks)));
    }

    /** Sends a target's Entangle state to every client that can currently see it. */
    public static void syncEntangleStacks(LivingEntity target, int stacks, int rootVisualTicks) {
        Collection<ServerPlayerEntity> trackingPlayers = PlayerLookup.tracking(target);
        for (ServerPlayerEntity player : trackingPlayers) {
            syncEntangleStacks(player, target, stacks, rootVisualTicks);
        }

        // PlayerLookup does not guarantee that a tracked player includes themselves.
        if (target instanceof ServerPlayerEntity player && !trackingPlayers.contains(player)) {
            syncEntangleStacks(player, target, stacks, rootVisualTicks);
        }
    }

    // -----------------------------------------------------------------------
    // Handlers
    // -----------------------------------------------------------------------

    private static void handleCastUltimate(ServerPlayerEntity player) {
        com.anton.elementalwands.util.WandLoadouts.cast(player, 2);
    }

    // -----------------------------------------------------------------------
    // Payload records
    // -----------------------------------------------------------------------

    public record SyncStoneClusterPayload(int mass, int remaining, int duration) implements CustomPayload {
        public static final Id<SyncStoneClusterPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID,"stone_cluster"));
        public static final PacketCodec<RegistryByteBuf,SyncStoneClusterPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER,SyncStoneClusterPayload::mass,
                PacketCodecs.INTEGER,SyncStoneClusterPayload::remaining,
                PacketCodecs.INTEGER,SyncStoneClusterPayload::duration,SyncStoneClusterPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record StoneStaggerPayload(int entityId, int ticks) implements CustomPayload {
        public static final Id<StoneStaggerPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID,"stone_stagger"));
        public static final PacketCodec<RegistryByteBuf,StoneStaggerPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER,StoneStaggerPayload::entityId,
                PacketCodecs.INTEGER,StoneStaggerPayload::ticks,StoneStaggerPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
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
    public record SyncPlayerDataPayload(int unlockedSkills, String affinity, long flux, List<String> loadout, boolean canEdit, List<String> owned) implements CustomPayload {
        public static final Id<SyncPlayerDataPayload> ID = new Id<>(
                Identifier.of(ElementalWandsMod.MOD_ID, "sync_player_data"));
        public static final PacketCodec<RegistryByteBuf, SyncPlayerDataPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.INTEGER, SyncPlayerDataPayload::unlockedSkills,
                        PacketCodecs.STRING,  SyncPlayerDataPayload::affinity,
                        PacketCodecs.VAR_LONG, SyncPlayerDataPayload::flux,
                        PacketCodecs.STRING.collect(PacketCodecs.toList(3)), SyncPlayerDataPayload::loadout,
                        PacketCodecs.BOOLEAN, SyncPlayerDataPayload::canEdit,
                        PacketCodecs.string(64).collect(PacketCodecs.toList(256)), SyncPlayerDataPayload::owned,
                        SyncPlayerDataPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void syncFireBuild(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new FireBuildPayload(com.anton.elementalwands.util.FireBuildManager.heat(player),
                com.anton.elementalwands.util.FireBuildManager.overheated(player), com.anton.elementalwands.util.FireBuildManager.hopRemaining(player)));
    }
    public record AlternateSpellPayload() implements CustomPayload {
        public static final AlternateSpellPayload INSTANCE=new AlternateSpellPayload();
        public static final Id<AlternateSpellPayload> ID=new Id<>(Identifier.of(ElementalWandsMod.MOD_ID,"alternate_spell"));
        public static final PacketCodec<RegistryByteBuf,AlternateSpellPayload> CODEC=PacketCodec.unit(INSTANCE);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record FlashoverStatePayload(int active,int armed,int remaining,int duration,java.util.List<Integer> slots) implements CustomPayload {
        public static final Id<FlashoverStatePayload> ID=new Id<>(Identifier.of(ElementalWandsMod.MOD_ID,"flashover_state"));
        public static final PacketCodec<RegistryByteBuf,FlashoverStatePayload> CODEC=PacketCodec.tuple(
                PacketCodecs.VAR_INT,FlashoverStatePayload::active,PacketCodecs.VAR_INT,FlashoverStatePayload::armed,
                PacketCodecs.VAR_INT,FlashoverStatePayload::remaining,PacketCodecs.VAR_INT,FlashoverStatePayload::duration,
                PacketCodecs.VAR_INT.collect(PacketCodecs.toList(3)),FlashoverStatePayload::slots,FlashoverStatePayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record FireLeapCommitPayload(double x,double y,double z) implements CustomPayload {
        public static final Id<FireLeapCommitPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID,"fire_leap_commit"));
        public static final PacketCodec<RegistryByteBuf,FireLeapCommitPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.DOUBLE,FireLeapCommitPayload::x,PacketCodecs.DOUBLE,FireLeapCommitPayload::y,
                PacketCodecs.DOUBLE,FireLeapCommitPayload::z,FireLeapCommitPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record FireBuildPayload(float heat, boolean overheated, int hopRemaining) implements CustomPayload {
        public static final Id<FireBuildPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID,"fire_build"));
        public static final PacketCodec<RegistryByteBuf,FireBuildPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.FLOAT,FireBuildPayload::heat,PacketCodecs.BOOLEAN,FireBuildPayload::overheated,
                PacketCodecs.VAR_INT,FireBuildPayload::hopRemaining,FireBuildPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record ReleaseSpellPayload() implements CustomPayload {
        public static final ReleaseSpellPayload INSTANCE = new ReleaseSpellPayload();
        public static final Id<ReleaseSpellPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID,"release_spell"));
        public static final PacketCodec<RegistryByteBuf,ReleaseSpellPayload> CODEC = PacketCodec.unit(INSTANCE);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record CastSlotPayload(int slot) implements CustomPayload {
        public static final Id<CastSlotPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID, "cast_slot"));
        public static final PacketCodec<RegistryByteBuf, CastSlotPayload> CODEC = PacketCodec.tuple(PacketCodecs.VAR_INT, CastSlotPayload::slot, CastSlotPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record HubActionPayload(String action, String affinity, int slot, String value) implements CustomPayload {
        public static final Id<HubActionPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID, "hub_action"));
        public static final PacketCodec<RegistryByteBuf, HubActionPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.string(32), HubActionPayload::action, PacketCodecs.string(16), HubActionPayload::affinity,
                PacketCodecs.VAR_INT, HubActionPayload::slot, PacketCodecs.string(64), HubActionPayload::value, HubActionPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public static void welcomeIfNeeded(ServerPlayerEntity player) {
        if (!player.getAttachedOrElse(EWAttachments.WELCOME_SEEN, false)) ServerPlayNetworking.send(player, WelcomePayload.INSTANCE);
    }
    public record WelcomePayload() implements CustomPayload {
        public static final WelcomePayload INSTANCE = new WelcomePayload();
        public static final Id<WelcomePayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID, "welcome"));
        public static final PacketCodec<RegistryByteBuf, WelcomePayload> CODEC = PacketCodec.unit(INSTANCE);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record HubFeedbackPayload(boolean open, String message) implements CustomPayload {
        public static final Id<HubFeedbackPayload> ID = new Id<>(Identifier.of(ElementalWandsMod.MOD_ID, "hub_feedback"));
        public static final PacketCodec<RegistryByteBuf, HubFeedbackPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOLEAN, HubFeedbackPayload::open, PacketCodecs.STRING, HubFeedbackPayload::message, HubFeedbackPayload::new);
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S2C packet carrying the caster's current active Nature seedling anchors. */
    public record SyncNatureSeedlingsPayload(List<BlockPos> positions) implements CustomPayload {
        public static final Id<SyncNatureSeedlingsPayload> ID = new Id<>(
                Identifier.of(ElementalWandsMod.MOD_ID, "sync_nature_seedlings"));
        public static final PacketCodec<RegistryByteBuf, SyncNatureSeedlingsPayload> CODEC =
                BlockPos.PACKET_CODEC.<RegistryByteBuf>cast()
                        .collect(PacketCodecs.toList(32))
                        .xmap(SyncNatureSeedlingsPayload::new, SyncNatureSeedlingsPayload::positions);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S2C packet carrying one visible entity's current Entangle stack count. */
    public record SyncEntangleStacksPayload(int entityId, int stacks, int rootVisualTicks)
            implements CustomPayload {
        public static final Id<SyncEntangleStacksPayload> ID = new Id<>(
                Identifier.of(ElementalWandsMod.MOD_ID, "sync_entangle_stacks"));
        public static final PacketCodec<RegistryByteBuf, SyncEntangleStacksPayload> CODEC =
                PacketCodec.tuple(
                        PacketCodecs.INTEGER, SyncEntangleStacksPayload::entityId,
                        PacketCodecs.INTEGER, SyncEntangleStacksPayload::stacks,
                        PacketCodecs.INTEGER, SyncEntangleStacksPayload::rootVisualTicks,
                        SyncEntangleStacksPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }
}
