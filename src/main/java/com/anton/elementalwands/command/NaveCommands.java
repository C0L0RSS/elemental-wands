package com.anton.elementalwands.command;

import com.anton.elementalwands.arena.GuardianArenaManager;
import com.anton.elementalwands.arena.ShatteredNave;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** Shattered Nave controls: operators enter, summon, reset and inspect; anyone inside may leave. */
public final class NaveCommands {
    private NaveCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var slot = CommandManager.argument("slot", IntegerArgumentType.integer(0, ShatteredNave.SLOTS - 1));
        java.util.function.Predicate<ServerCommandSource> op = source -> source.hasPermissionLevel(2);
        dispatcher.register(CommandManager.literal("ew").then(CommandManager.literal("nave")
                .then(CommandManager.literal("enter").requires(op)
                        .executes(c -> reply(c.getSource(), GuardianArenaManager.enter(c.getSource().getPlayerOrThrow(), 0)))
                        .then(slot.executes(c -> reply(c.getSource(),
                                GuardianArenaManager.enter(c.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(c, "slot"))))))
                // Anyone inside may leave, so a survival group is never stranded.
                .then(CommandManager.literal("leave").executes(c -> reply(c.getSource(), GuardianArenaManager.leave(c.getSource().getPlayerOrThrow()))))
                .then(CommandManager.literal("summon").requires(op).executes(c -> reply(c.getSource(), GuardianArenaManager.summon(c.getSource().getPlayerOrThrow()))))
                .then(CommandManager.literal("reset").requires(op).executes(c -> reply(c.getSource(), GuardianArenaManager.reset(c.getSource().getPlayerOrThrow()))))
                .then(CommandManager.literal("status").requires(op).executes(c -> reply(c.getSource(), GuardianArenaManager.status())))));
    }

    private static int reply(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(message), false);
        return 1;
    }
}
