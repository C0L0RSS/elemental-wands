package com.anton.elementalwands.command;

import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Action;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Comparator;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

/** Operator-only controls for the nearest Hollow Necromancer. */
public final class NecromancerCommands {
    private NecromancerCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("necromancer").requires(source -> source.hasPermissionLevel(2));
        for (String action : new String[]{"fight", "stop", "status", "bolt", "hands", "drain", "raise", "blink"})
            root.then(CommandManager.literal(action).executes(context -> run(context.getSource(), action)));
        dispatcher.register(CommandManager.literal("ew").then(root));
    }

    private static int run(ServerCommandSource source, String action) throws CommandSyntaxException {
        var pos = source.getPosition();
        NecromancerEntity boss = source.getWorld().getEntitiesByClass(NecromancerEntity.class,
                        new Box(pos, pos).expand(32), entity -> entity.isAlive() && entity.squaredDistanceTo(pos) <= 32 * 32)
                .stream().min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(pos)))
                .orElseThrow(() -> new SimpleCommandExceptionType(Text.literal(
                        "No Hollow Necromancer within 32 blocks. Summon one with /summon elementalwands:hollow_necromancer")).create());
        String message = switch (action) {
            case "fight" -> { boss.startFight(); yield "fighting nearby Survival/Adventure players. /ew necromancer stop to end."; }
            case "stop" -> { boss.stopFight(); yield "passive; its army dissolved. Stays passive after reload."; }
            case "status" -> boss.status();
            default -> {
                boss.testAction(source.getPlayerOrThrow(), Action.valueOf(action.toUpperCase()));
                yield "one real " + action + " cast at you, then passive. Survival/Adventure players can take damage.";
            }
        };
        source.sendFeedback(() -> Text.literal(action.equals("status") ? message : "Hollow Necromancer: " + message), false);
        return 1;
    }
}
