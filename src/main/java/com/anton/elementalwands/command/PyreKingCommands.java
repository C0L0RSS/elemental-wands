package com.anton.elementalwands.command;

import com.anton.elementalwands.entity.pyreking.PyreKingClips;
import com.anton.elementalwands.entity.pyreking.PyreKingEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Comparator;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

/**
 * Operator-only review of the nearest Pyre King: /ew pyreking &lt;clip&gt; plays one of his clips,
 * /ew pyreking stop returns him to his guard. Summon him with /summon elementalwands:pyre_king.
 */
public final class PyreKingCommands {
    private static final double RANGE = 48;

    private PyreKingCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("pyreking").requires(source -> source.hasPermissionLevel(2));
        for (PyreKingClips.Clip clip : PyreKingClips.ALL)
            root.then(CommandManager.literal(clip.name()).executes(context -> run(context.getSource(), clip.name())));
        root.then(CommandManager.literal("stop").executes(context -> run(context.getSource(), "stop")));
        dispatcher.register(CommandManager.literal("ew").then(root));
    }

    private static int run(ServerCommandSource source, String action) throws CommandSyntaxException {
        var pos = source.getPosition();
        var king = source.getWorld().getEntitiesByClass(PyreKingEntity.class,
                        new Box(pos, pos).expand(RANGE), entity -> entity.isAlive() && entity.squaredDistanceTo(pos) <= RANGE * RANGE)
                .stream().min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(pos)))
                .orElseThrow(() -> new SimpleCommandExceptionType(
                        Text.literal("No Pyre King within 48 blocks. Summon one first: /summon elementalwands:pyre_king")).create());
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
