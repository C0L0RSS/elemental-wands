package com.anton.elementalwands.command;

import com.anton.elementalwands.entity.pyreking.PyreKingClips;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import com.anton.elementalwands.entity.pyreking.PyreKingRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Comparator;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

/**
 * Operator-only control of the nearest Pyre King. /ew pyreking fight and calm start and stop his
 * fight; move &lt;name&gt; has him use one move on you and stand passive again; unbound starts the
 * phase change; &lt;clip&gt; plays one of his clips and stop returns him to his guard. Summon him with
 * /summon elementalwands:pyre_king.
 */
public final class PyreKingCommands {
    private static final double RANGE = 48;

    private PyreKingCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("pyreking").requires(source -> source.hasPermissionLevel(2));
        for (PyreKingClips.Clip clip : PyreKingClips.ALL)
            root.then(CommandManager.literal(clip.name()).executes(context -> run(context.getSource(), clip.name())));
        root.then(CommandManager.literal("stop").executes(context -> run(context.getSource(), "stop")));
        root.then(CommandManager.literal("fight").executes(context -> {
            king(context.getSource()).startFight();
            context.getSource().sendFeedback(() -> Text.literal("The Pyre King fights whoever is in Survival or Adventure nearby."), false);
            return 1;
        }));
        root.then(CommandManager.literal("calm").executes(context -> {
            king(context.getSource()).stopFight();
            context.getSource().sendFeedback(() -> Text.literal("The Pyre King stands passive in his guard."), false);
            return 1;
        }));
        root.then(CommandManager.literal("unbound").executes(context -> {
            boolean started = king(context.getSource()).requestUnbound();
            context.getSource().sendFeedback(() -> Text.literal(started ? "Unbound starts as soon as his move in hand allows (he must be fighting)."
                    : "He is already unbound."), false);
            return 1;
        }));
        root.then(CommandManager.literal("status").executes(context -> {
            String status = king(context.getSource()).status();
            context.getSource().sendFeedback(() -> Text.literal(status), false);
            return 1;
        }));
        root.then(CommandManager.literal("intro").executes(context -> {
            String said = com.anton.elementalwands.hall.PyreHallManager.intro(context.getSource().getPlayerOrThrow());
            context.getSource().sendFeedback(() -> Text.literal(said), false);
            return 1;
        }));
        var move = CommandManager.literal("move");
        for (PyreKingRules.Move each : PyreKingRules.Move.values())
            move.then(CommandManager.literal(each.name().toLowerCase(java.util.Locale.ROOT)).executes(context -> {
                king(context.getSource()).testMove(context.getSource().getPlayerOrThrow(), each);
                context.getSource().sendFeedback(() -> Text.literal("The Pyre King uses " + each.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                        + " on you, then stands passive. It only hurts in Survival or Adventure."), false);
                return 1;
            }));
        root.then(move);
        dispatcher.register(CommandManager.literal("ew").then(root));
    }

    private static PyreKingEntity king(ServerCommandSource source) throws CommandSyntaxException {
        var pos = source.getPosition();
        return source.getWorld().getEntitiesByClass(PyreKingEntity.class,
                        new Box(pos, pos).expand(RANGE), entity -> entity.isAlive() && entity.squaredDistanceTo(pos) <= RANGE * RANGE)
                .stream().min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(pos)))
                .orElseThrow(() -> new SimpleCommandExceptionType(
                        Text.literal("No Pyre King within 48 blocks. Summon one first: /summon elementalwands:pyre_king")).create());
    }

    private static int run(ServerCommandSource source, String action) throws CommandSyntaxException {
        var king = king(source);
        if (action.equals("stop")) {
            king.stopRehearsal();
            source.sendFeedback(() -> Text.literal("The Pyre King returns to his guard."), false);
            return 1;
        }
        king.rehearse(action);
        var clip = PyreKingClips.get(action);
        source.sendFeedback(() -> Text.literal("The Pyre King plays " + action + (clip.loop() ? " until /ew pyreking stop." : " (" + clip.seconds() + " s).")), false);
        return 1;
    }
}
