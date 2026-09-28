package com.anton.elementalwands.command;

import com.anton.elementalwands.entity.necromancer.NecromancerEntity;
import com.anton.elementalwands.entity.necromancer.NecromancerFightLog;
import com.anton.elementalwands.entity.necromancer.NecromancerRules.Action;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
        for (String action : new String[]{"fight", "stop", "status", "transform", "siege", "bolt", "hands", "drain", "blink", "shift", "ambush", "swipe", "grab", "rush"})
            root.then(CommandManager.literal(action).executes(context -> run(context.getSource(), action)));
        root.then(CommandManager.literal("wave").then(CommandManager.argument("number", IntegerArgumentType.integer(1, 4))
                .executes(context -> wave(context.getSource(), IntegerArgumentType.getInteger(context, "number")))));
        for (boolean on : new boolean[]{true, false})
            root.then(CommandManager.literal("log").then(CommandManager.literal(on ? "on" : "off").executes(context -> {
                NecromancerFightLog.setEnabled(on);
                context.getSource().sendFeedback(() -> Text.literal(on
                        ? "Necromancer fight log on: fights that start from now record casts, stages and damage, then write logs/necromancer-fight-*.txt."
                        : "Necromancer fight log off."), true);
                return 1;
            })));
        dispatcher.register(CommandManager.literal("ew").then(root));
    }

    private static NecromancerEntity nearest(ServerCommandSource source) throws CommandSyntaxException {
        var pos = source.getPosition();
        // Siege perches stand high above the clearing, so the search reaches well overhead.
        return source.getWorld().getEntitiesByClass(NecromancerEntity.class,
                        new Box(pos, pos).expand(48), entity -> entity.isAlive() && entity.squaredDistanceTo(pos) <= 48 * 48)
                .stream().min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(pos)))
                .orElseThrow(() -> new SimpleCommandExceptionType(Text.literal(
                        "No Hollow Necromancer within 48 blocks. Summon one with /summon elementalwands:hollow_necromancer")).create());
    }

    private static int wave(ServerCommandSource source, int number) throws CommandSyntaxException {
        NecromancerEntity boss = nearest(source);
        int bodies = boss.testWave(source.getPlayerOrThrow(), number);
        source.sendFeedback(() -> Text.literal("Hollow Necromancer: raised siege wave " + number + " (" + bodies
                + " bodies for the nearby party), then passive. /ew necromancer stop dissolves them."), false);
        return 1;
    }

    private static int run(ServerCommandSource source, String action) throws CommandSyntaxException {
        NecromancerEntity boss = nearest(source);
        String message = switch (action) {
            case "fight" -> { boss.startFight(); yield "fighting nearby Survival/Adventure players. /ew necromancer stop to end."; }
            case "stop" -> { boss.stopFight(); yield "passive; its army dissolved. Stays passive after reload."; }
            case "status" -> boss.status();
            case "transform" -> {
                if (boss.isColossus() || boss.isTransforming()) yield "already transformed.";
                boss.requestTransform();
                yield "transforming into the colossus now, skipping any remaining siege (finds open ground first).";
            }
            case "siege" -> {
                if (!boss.isBossAggressive()) boss.startFight(); // Starting the fight clears pending state, so it comes first.
                if (!boss.requestSiege()) yield "no siege left to start: it is " + boss.stage().name().toLowerCase() + (boss.isColossus() ? " (colossus)" : "") + ".";
                yield "starting the current duel's siege: it takes its perch and raises waves. Kill them to bring it down.";
            }
            default -> {
                Action cast = Action.valueOf(action.toUpperCase());
                if (cast.colossusOnly() && !boss.isColossus() || cast.robedOnly() && boss.isColossus())
                    yield cast.colossusOnly() ? action + " needs the colossus; use /ew necromancer transform first." : action + " is a robed-form spell.";
                boss.testAction(source.getPlayerOrThrow(), cast);
                yield "one real " + action + " cast at you, then passive. Survival/Adventure players can take damage.";
            }
        };
        source.sendFeedback(() -> Text.literal(action.equals("status") ? message : "Hollow Necromancer: " + message), false);
        return 1;
    }
}
