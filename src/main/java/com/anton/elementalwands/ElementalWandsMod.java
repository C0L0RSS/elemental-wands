package com.anton.elementalwands;

import com.anton.elementalwands.data.EWAttachments;
import com.anton.elementalwands.data.WizardAffinity;
import com.anton.elementalwands.item.StoneAbilityHandler;
import com.anton.elementalwands.network.ModNetworking;
import com.anton.elementalwands.registry.ModBlocks;
import com.anton.elementalwands.registry.ModItems;
import com.anton.elementalwands.registry.ModEntities;
import com.anton.elementalwands.registry.ModParticles;
import com.anton.elementalwands.registry.ModSpellBlocks;
import com.anton.elementalwands.util.BlinkRiftManager;
import com.anton.elementalwands.util.SeedlingManager;
import com.anton.elementalwands.util.EntangleTracker;
import com.anton.elementalwands.util.HollowPurpleChargeManager;
import com.anton.elementalwands.util.MeteorManager;
import com.anton.elementalwands.util.SoulboundInventoryCarrier;
import com.anton.elementalwands.util.TemporaryBlockManager;
import com.anton.elementalwands.util.TendrilBloomManager;
import com.anton.elementalwands.util.TitanDomeManager;
import com.anton.elementalwands.util.WaylayDashVfxManager;
import com.anton.elementalwands.util.ZephyrStrikeManager;
import com.anton.elementalwands.util.OvergrowthManager;
import com.anton.elementalwands.world.ModWorldGen;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import com.anton.elementalwands.entity.AwakenedTreeEntity;
import com.anton.elementalwands.entity.FireSpiritEntity;
import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.command.GuardianCommands;
import com.anton.elementalwands.entity.StoneZombieEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.Heightmap;

public class ElementalWandsMod implements ModInitializer {
    public static final String MOD_ID = "elementalwands";

    private static final int PROGRESSION_REFRESH_INTERVAL = 40; // Sync every 2 seconds (40 ticks)

    @Override
    public void onInitialize() {
        ModParticles.registerAll();
        ModSpellBlocks.registerAll();
        EWAttachments.init();
        com.anton.elementalwands.party.PartyManager.init();

        TemporaryBlockManager.init();
        StoneAbilityHandler.init();
        com.anton.elementalwands.util.FaultlineManager.init();
        com.anton.elementalwands.util.StoneChargeManager.init();
        com.anton.elementalwands.util.StoneClusterManager.init();
        EntangleTracker.init();
        com.anton.elementalwands.util.NatureCombat.init();
        OvergrowthManager.init();
        SeedlingManager.init();
        TendrilBloomManager.init();
        MeteorManager.init();
        TitanDomeManager.init();
        BlinkRiftManager.init();
        HollowPurpleChargeManager.init();
        ModBlocks.registerAll();
        ModEntities.registerAll();
        FabricDefaultAttributeRegistry.register(ModEntities.FRACTURED_GUARDIAN, FracturedGuardianEntity.createAttributes().build());
        FabricDefaultAttributeRegistry.register(ModEntities.AWAKENED_TREE, AwakenedTreeEntity.createAttributes().build());
        FabricDefaultAttributeRegistry.register(ModEntities.STONE_ZOMBIE, StoneZombieEntity.createAttributes().build());
        SpawnRestriction.register(
                ModEntities.STONE_ZOMBIE,
                SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                ZombieEntity::canSpawnInDark);
        BiomeModifications.addSpawn(
                BiomeSelectors.foundInOverworld(),
                SpawnGroup.MONSTER,
                ModEntities.STONE_ZOMBIE,
                15, 1, 3);
        FabricDefaultAttributeRegistry.register(ModEntities.FIRE_SPIRIT, FireSpiritEntity.createAttributes().build());
        SpawnRestriction.register(
                ModEntities.FIRE_SPIRIT,
                SpawnLocationTypes.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                ZombieEntity::canSpawnInDark);
        BiomeModifications.addSpawn(
                BiomeSelectors.foundInOverworld(),
                SpawnGroup.MONSTER,
                ModEntities.FIRE_SPIRIT,
                10, 1, 3);
        ModItems.registerAll();
        WaylayDashVfxManager.init();
        ZephyrStrikeManager.init();
        ModNetworking.registerPayloads();
        ModNetworking.registerC2SReceivers();
        com.anton.elementalwands.util.WandLoadouts.init();
        com.anton.elementalwands.util.FireBuildManager.init();
        com.anton.elementalwands.util.FlashoverManager.init();
        ModWorldGen.registerAll();
        com.anton.elementalwands.arena.GuardianArenaManager.init();
        com.anton.elementalwands.church.GuardianChurchManager.init();

        // ── First-join starter kit ──────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            server.execute(() -> {
                com.anton.elementalwands.util.WandGuide.removeLegacyBooks(player.getInventory());
                ModNetworking.syncPlayerData(player);
                SeedlingManager.syncActiveSeedlings(player);
                EntangleTracker.syncPlayer(player);
                ModNetworking.welcomeIfNeeded(player);
            });
        });

        // ── Soulbound: recover protected gear on respawn ───
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            if (alive)
                return; // Alive == end-of-portal, not death

            List<ItemStack> stashed = ((SoulboundInventoryCarrier) oldPlayer).elementalWands$consumeSoulboundItems();
            if (stashed != null) {
                for (ItemStack stack : stashed) {
                    if (!newPlayer.getInventory().insertStack(stack.copy())) {
                        newPlayer.dropItem(stack.copy(), false);
                    }
                }
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ModNetworking.syncPlayerData(newPlayer));

        // ── /ew unlock + /ew affinity + /ew admin commands ──────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            GuardianCommands.register(dispatcher);
            com.anton.elementalwands.command.PartyCommands.register(dispatcher);
            dispatcher.register(
                CommandManager.literal("ew")
                        .then(CommandManager.literal("hub").executes(ctx -> {
                            ModNetworking.openHub(ctx.getSource().getPlayerOrThrow()); return 1;
                        }))
                        .then(CommandManager.literal("unlock")
                                .then(CommandManager.literal("secondary")
                                        .executes(ctx -> handleSkillUnlock(ctx.getSource(), "secondary")))
                                .then(CommandManager.literal("ultimate")
                                        .executes(ctx -> handleSkillUnlock(ctx.getSource(), "ultimate"))))
                        .then(CommandManager.literal("affinity")
                                .then(CommandManager.literal("fire")
                                        .executes(ctx -> handleAffinitySet(ctx.getSource(), WizardAffinity.FIRE)))
                                .then(CommandManager.literal("wind")
                                        .executes(ctx -> handleAffinitySet(ctx.getSource(), WizardAffinity.WIND)))
                                .then(CommandManager.literal("stone")
                                        .executes(ctx -> handleAffinitySet(ctx.getSource(), WizardAffinity.STONE)))
                                .then(CommandManager.literal("nature")
                                        .executes(ctx -> handleAffinitySet(ctx.getSource(), WizardAffinity.NATURE)))
                                .then(CommandManager.literal("space")
                                        .executes(ctx -> handleAffinitySet(ctx.getSource(), WizardAffinity.SPACE)))
                                .then(CommandManager.literal("reset")
                                        .executes(ctx -> handleAffinityReset(ctx.getSource())))));

            // ── /ew admin unlock|unlockall <player> (cheats-on, no cost) ───
            dispatcher.register(
                CommandManager.literal("ew")
                        .then(CommandManager.literal("admin")
                                .requires(src -> src.hasPermissionLevel(2))
                                .then(CommandManager.literal("unlock")
                                        .then(CommandManager.literal("secondary")
                                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                                        .executes(ctx -> handleAdminUnlock(
                                                                ctx.getSource(),
                                                                EntityArgumentType.getPlayer(ctx, "player"),
                                                                EWAttachments.SKILL_SECONDARY))))
                                        .then(CommandManager.literal("ultimate")
                                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                                        .executes(ctx -> handleAdminUnlock(
                                                                ctx.getSource(),
                                                                EntityArgumentType.getPlayer(ctx, "player"),
                                                                EWAttachments.SKILL_ULTIMATE)))))
                                .then(CommandManager.literal("unlockall")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(ctx -> handleAdminUnlock(
                                                        ctx.getSource(),
                                                        EntityArgumentType.getPlayer(ctx, "player"),
                                                        EWAttachments.SKILL_SECONDARY | EWAttachments.SKILL_ULTIMATE))))));
        });

        // ── Periodic progression sync ─────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % PROGRESSION_REFRESH_INTERVAL != 0)
                return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                refreshProgression(player);
            }
        });
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    // ── /ew admin unlock handler (op-only, no cost) ──────────────────────────

    private static int handleAdminUnlock(ServerCommandSource source, ServerPlayerEntity target, int skillBits) {
        if (EWAttachments.getAffinity(target) == WizardAffinity.NONE) {
            source.sendFeedback(() -> Text.literal("Choose an element before granting its spells."), false);
            return 0;
        }
        int currentSkills = com.anton.elementalwands.util.WandProgression.skills(target);
        int newSkills = currentSkills | skillBits;

        if (newSkills == currentSkills && com.anton.elementalwands.data.WandSpells.forAffinity(EWAttachments.getAffinity(target)).stream()
                .filter(spell -> com.anton.elementalwands.util.WandProgression.adminMatches(spell,skillBits)).allMatch(spell -> com.anton.elementalwands.util.WandProgression.owns(target, spell))) {
            source.sendFeedback(() -> Text.literal("Player already has the specified ability unlocked.")
                    .formatted(Formatting.YELLOW), false);
            return 0;
        }

        com.anton.elementalwands.util.WandProgression.grant(target, skillBits);
        refreshProgression(target);
        ModNetworking.syncPlayerData(target);

        source.sendFeedback(() -> Text.literal("Unlocked abilities for " + target.getName().getString() + ".")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── /ew unlock handler ──────────────────────────────────────────────────

    public static int handleSkillUnlock(ServerCommandSource source, String skillName) {
        if (!source.isExecutedByPlayer())
            return 0;
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        if (!com.anton.elementalwands.util.WandLoadouts.canEdit(player)) {
            player.sendMessage(Text.literal("Change spells or affinity outside combat."), false);
            return 0;
        }

        var spell = com.anton.elementalwands.data.WandSpells.forAffinity(EWAttachments.getAffinity(player)).stream()
                .filter(s -> s.ability() == (skillName.equals("secondary") ? com.anton.elementalwands.item.AbstractWandItem.Ability.SECONDARY
                        : com.anton.elementalwands.item.AbstractWandItem.Ability.ULTIMATE)).findFirst().orElse(null);
        if (spell == null) return 0;
        boolean alreadyOwned = com.anton.elementalwands.util.WandProgression.owns(player, spell);
        String result = com.anton.elementalwands.util.WandProgression.purchase(player, EWAttachments.getAffinity(player).name(), spell.id());
        player.sendMessage(Text.literal(result), false);
        ModNetworking.syncPlayerData(player);
        return !alreadyOwned && com.anton.elementalwands.util.WandProgression.owns(player, spell) ? 1 : 0;
    }

    // ── /ew affinity <element> handler ──────────────────────────────────────

    public static int handleAffinitySet(ServerCommandSource source, WizardAffinity newAffinity) {
        if (!source.isExecutedByPlayer())
            return 0;
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        if (!com.anton.elementalwands.util.WandLoadouts.canEdit(player)) {
            player.sendMessage(Text.literal("Change spells or affinity outside combat."), false);
            return 0;
        }

        com.anton.elementalwands.util.WandProgression.migrate(player);
        player.setAttached(EWAttachments.AFFINITY, newAffinity.name());
        com.anton.elementalwands.util.WandProgression.migrate(player);

        // Give the player a wand if they don't already have one anywhere in inventory
        if (!playerHasWand(player)) {
            player.getInventory().insertStack(new ItemStack(ModItems.FRACTURED_WAND));
        }

        refreshProgression(player);
        ModNetworking.syncPlayerData(player);

        player.sendMessage(
                Text.literal("Your path is chosen. The wand awakens.")
                        .formatted(Formatting.GOLD),
                false);
        return 1;
    }

    private static int handleAffinityReset(ServerCommandSource source) {
        if (!source.isExecutedByPlayer())
            return 0;
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception e) {
            return 0;
        }

        if (!com.anton.elementalwands.util.WandLoadouts.canEdit(player)) {
            player.sendMessage(Text.literal("Change spells or affinity outside combat."), false);
            return 0;
        }

        com.anton.elementalwands.util.WandProgression.migrate(player);
        player.setAttached(EWAttachments.AFFINITY, WizardAffinity.NONE.name());

        refreshProgression(player);
        ModNetworking.syncPlayerData(player);

        player.sendMessage(
                Text.literal("Choose an element in the hub. All balances and purchases are saved.")
                        .formatted(Formatting.DARK_RED),
                false);
        return 1;
    }

    private static boolean playerHasWand(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(ModItems.FRACTURED_WAND)) {
                return true;
            }
        }
        return false;
    }

    /** Keep progression visible without rebuilding an inventory book on each hit. */
    public static void refreshProgression(ServerPlayerEntity player) {
        ModNetworking.syncPlayerData(player);
    }
}
