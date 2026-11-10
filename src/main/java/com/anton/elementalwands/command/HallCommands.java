package com.anton.elementalwands.command;

import com.anton.elementalwands.hall.PyreHall;
import com.anton.elementalwands.hall.PyreHallManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** Burned Throne Hall controls: operators visit, inspect, reset it and stand the Pyre King in it; anyone in it may leave. */
public final class HallCommands {
    private HallCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var slot = CommandManager.argument("slot", IntegerArgumentType.integer(0, PyreHall.SLOTS - 1));
        java.util.function.Predicate<ServerCommandSource> op = source -> source.hasPermissionLevel(2);
        dispatcher.register(CommandManager.literal("ew").then(CommandManager.literal("hall")
                .then(CommandManager.literal("enter").requires(op)
                        .executes(c -> reply(c.getSource(), PyreHallManager.enter(c.getSource().getPlayerOrThrow(), 0)))
                        .then(slot.executes(c -> reply(c.getSource(),
                                PyreHallManager.enter(c.getSource().getPlayerOrThrow(), IntegerArgumentType.getInteger(c, "slot"))))))
                .then(CommandManager.literal("summon").requires(op).executes(c -> reply(c.getSource(), PyreHallManager.summon(c.getSource().getPlayerOrThrow()))))
                .then(CommandManager.literal("reset").requires(op).executes(c -> reply(c.getSource(), PyreHallManager.reset(c.getSource().getPlayerOrThrow()))))
                // Anyone in the hall may leave, so nobody is stranded there.
                .then(CommandManager.literal("leave").executes(c -> reply(c.getSource(), PyreHallManager.leave(c.getSource().getPlayerOrThrow()))))
                .then(CommandManager.literal("status").requires(op).executes(c -> reply(c.getSource(), PyreHallManager.status())))));
    }

    private static int reply(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(message), false);
        return 1;
    }
}
