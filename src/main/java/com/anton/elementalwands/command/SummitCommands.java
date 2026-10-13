package com.anton.elementalwands.command;

import com.anton.elementalwands.summit.StormSummit;
import com.anton.elementalwands.summit.StormSummitManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** Storm Summit controls: operators visit and inspect it; anyone on it may leave. */
public final class SummitCommands {
    private SummitCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var slot = CommandManager.argument("slot", IntegerArgumentType.integer(0, StormSummit.SLOTS - 1));
        java.util.function.Predicate<ServerCommandSource> op = source -> source.hasPermissionLevel(2);
        dispatcher.register(CommandManager.literal("ew").then(CommandManager.literal("summit")
                .then(CommandManager.literal("enter").requires(op)
                        .executes(c -> reply(c.getSource(), StormSummitManager.enter(c.getSource().getPlayerOrThrow(), 0)))
                        .then(slot.executes(c -> reply(c.getSource(),
                                StormSummitManager.enter(c.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(c, "slot"))))))
                // Anyone on the summit may leave, so nobody is stranded there.
                .then(CommandManager.literal("leave").executes(c -> reply(c.getSource(), StormSummitManager.leave(c.getSource().getPlayerOrThrow()))))
                .then(CommandManager.literal("status").requires(op).executes(c -> reply(c.getSource(), StormSummitManager.status())))));
    }

    private static int reply(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(message), false);
        return 1;
    }
}
