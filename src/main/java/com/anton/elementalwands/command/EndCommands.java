package com.anton.elementalwands.command;

import com.anton.elementalwands.end.EndEncounter;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** The End arena: operators inspect, enter, begin, replay the intro, reset and lay it out; anyone in the End may leave. */
public final class EndCommands {
    private EndCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        java.util.function.Predicate<ServerCommandSource> op = source -> source.hasPermissionLevel(2);
        dispatcher.register(CommandManager.literal("ew").then(CommandManager.literal("end")
                .then(CommandManager.literal("status").requires(op).executes(c -> reply(c.getSource(), EndEncounter.status())))
                .then(CommandManager.literal("enter").requires(op).executes(c -> reply(c.getSource(), EndEncounter.enter(c.getSource().getPlayerOrThrow()))))
                .then(CommandManager.literal("begin").requires(op).executes(c -> reply(c.getSource(), EndEncounter.begin(c.getSource().getPlayerOrThrow()))))
                .then(CommandManager.literal("intro").requires(op).executes(c -> reply(c.getSource(), EndEncounter.replayIntro(c.getSource().getPlayerOrThrow(), false)))
                        .then(CommandManager.literal("full").executes(c -> reply(c.getSource(), EndEncounter.replayIntro(c.getSource().getPlayerOrThrow(), true)))))
                .then(CommandManager.literal("reset").requires(op).executes(c -> reply(c.getSource(), EndEncounter.reset(c.getSource().getServer()))))
                .then(CommandManager.literal("layout").requires(op).executes(c -> reply(c.getSource(), EndEncounter.layout(c.getSource().getServer(), false)))
                        .then(CommandManager.literal("force").executes(c -> reply(c.getSource(), EndEncounter.layout(c.getSource().getServer(), true)))))
                // Anyone in the End may leave, so nobody is stranded there.
                .then(CommandManager.literal("leave").executes(c -> reply(c.getSource(), EndEncounter.leave(c.getSource().getPlayerOrThrow()))))));
    }

    private static int reply(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(message), false);
        return 1;
    }
}
