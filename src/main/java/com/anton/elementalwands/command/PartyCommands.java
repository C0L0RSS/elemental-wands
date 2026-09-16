package com.anton.elementalwands.command;

import com.anton.elementalwands.party.PartyManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.IOException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class PartyCommands {
    @FunctionalInterface private interface Action { void run(PartyManager parties, ServerPlayerEntity player) throws IOException; }
    private static int run(ServerCommandSource source, Action action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrThrow();
        try { action.run(PartyManager.get(source.getServer()), player); return 1; }
        catch (IllegalArgumentException e) { source.sendError(Text.literal(e.getMessage())); return 0; }
        catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger("elementalwands").error("Could not save parties", e);
            source.sendError(Text.literal("Could not save the party change. Nothing changed; check the server log.")); return 0;
        }
    }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(root("party"));
        dispatcher.register(CommandManager.literal("ew").then(root("party")));
    }
    private static LiteralArgumentBuilder<ServerCommandSource> root(String name) {
        var root = CommandManager.literal(name).executes(ctx -> run(ctx.getSource(), PartyManager::list))
                .then(CommandManager.literal("list").executes(ctx -> run(ctx.getSource(), PartyManager::list)))
                .then(CommandManager.literal("create").executes(ctx -> run(ctx.getSource(), PartyManager::create)))
                .then(CommandManager.literal("leave").executes(ctx -> run(ctx.getSource(), PartyManager::leave)))
                .then(CommandManager.literal("disband").executes(ctx -> run(ctx.getSource(), PartyManager::disband)))
                .then(CommandManager.literal("invite").then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                            var target = EntityArgumentType.getPlayer(ctx, "player");
                            return run(ctx.getSource(), (parties, player) -> parties.invite(player, target));
                        })));
        for (String verb : new String[]{"accept", "decline"}) {
            root.then(CommandManager.literal(verb)
                    .executes(ctx -> run(ctx.getSource(), (parties, player) -> {
                        if (verb.equals("accept")) parties.accept(player, null); else parties.decline(player, null);
                    }))
                    .then(CommandManager.argument("leader", StringArgumentType.word())
                            .suggests((ctx, builder) -> CommandSource.suggestMatching(ctx.getSource().getPlayerNames(), builder))
                            .executes(ctx -> run(ctx.getSource(), (parties, player) -> {
                                String leader = StringArgumentType.getString(ctx, "leader");
                                if (verb.equals("accept")) parties.accept(player, leader); else parties.decline(player, leader);
                            }))));
        }
        for (String verb : new String[]{"kick", "transfer"}) {
            root.then(CommandManager.literal(verb).then(CommandManager.argument("member", StringArgumentType.word())
                    .suggests((ctx, builder) -> CommandSource.suggestMatching(
                            PartyManager.get(ctx.getSource().getServer()).memberNames(ctx.getSource().getPlayerOrThrow()), builder))
                    .executes(ctx -> run(ctx.getSource(), (parties, player) -> {
                        String member = StringArgumentType.getString(ctx, "member");
                        if (verb.equals("kick")) parties.kick(player, member); else parties.transfer(player, member);
                    }))));
        }
        return root;
    }
}
