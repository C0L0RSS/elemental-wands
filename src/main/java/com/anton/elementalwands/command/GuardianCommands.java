package com.anton.elementalwands.command;

import com.anton.elementalwands.entity.FracturedGuardianEntity;
import com.anton.elementalwands.arena.GuardianArenaManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Comparator;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

/** Operator-only controls for the nearest Guardian; no global entity mutation. */
public final class GuardianCommands {
    private GuardianCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("guardian").requires(source -> source.hasPermissionLevel(2));
        for (String action : new String[]{"awaken", "slam", "throw", "follow", "stop", "beam", "fight", "rock", "shockwave", "melee", "leap", "fan", "status"}) {
            root.then(CommandManager.literal(action).executes(context -> run(context.getSource(), action)));
        }
        root.then(CommandManager.literal("arena")
                .then(CommandManager.literal("start").executes(context -> run(context.getSource(),"arena")))
                .then(CommandManager.literal("status").executes(context -> {
                    context.getSource().sendFeedback(() -> Text.literal(GuardianArenaManager.status()),false); return 1;
                }))
                .then(CommandManager.literal("stop").executes(context -> {
                    GuardianArenaManager.stop();
                    context.getSource().sendFeedback(() -> Text.literal(GuardianArenaManager.status()),false); return 1;
                })));
        root.then(CommandManager.literal("church")
                .then(CommandManager.literal("cancel").executes(context -> {
                    String message=com.anton.elementalwands.church.GuardianChurchLocator.cancel();
                    context.getSource().sendFeedback(() -> Text.literal(message),false);return 1;
                }))
                .then(CommandManager.literal("place").executes(context -> {
                    String message=com.anton.elementalwands.church.GuardianChurchManager.place(context.getSource());
                    context.getSource().sendFeedback(() -> Text.literal(message),false);return 1;
                }))
                .then(CommandManager.literal("locate").executes(context -> {
                    String message=com.anton.elementalwands.church.GuardianChurchManager.locate(context.getSource());
                    context.getSource().sendFeedback(() -> Text.literal(message),false);return 1;
                })));
        dispatcher.register(CommandManager.literal("ew").then(root));
    }

    private static int run(ServerCommandSource source, String action) throws CommandSyntaxException {
        var pos = source.getPosition();
        var guardian = source.getWorld().getEntitiesByClass(FracturedGuardianEntity.class,
                        new Box(pos, pos).expand(32), entity -> entity.isAlive() && entity.squaredDistanceTo(pos) <= 32*32)
                .stream().min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(pos)))
                .orElseThrow(() -> new SimpleCommandExceptionType(
                        Text.literal("No Fractured Guardian within 32 blocks. Summon one first.")).create());
        if (action.equals("arena")) {
            String result=GuardianArenaManager.start(source.getPlayerOrThrow(),guardian);
            source.sendFeedback(() -> Text.literal(result),false); return 1;
        }
        if (GuardianArenaManager.owns(guardian) && !action.equals("status")) {
            if (action.equals("stop")) { GuardianArenaManager.stop(); source.sendFeedback(() -> Text.literal("Arena encounter stopped."),false); return 1; }
            source.sendFeedback(() -> Text.literal("Use /ew guardian arena status or /ew guardian arena stop during an arena encounter."),false);
            return 1;
        }
        if (action.equals("status")) {
            source.sendFeedback(() -> Text.literal("Guardian leap: " + guardian.leapStatus()),false);
            return 1;
        }
        if (action.equals("follow")) {
            guardian.followForReview(source.getPlayerOrThrow());
        } else if (action.equals("beam")) {
            guardian.fireBeamForReview(source.getPlayerOrThrow());
        } else if (action.equals("fight")) {
            guardian.startFight();
        } else if (action.equals("fan") || action.equals("rock") || action.equals("shockwave") || action.equals("melee") || action.equals("leap")) {
            guardian.testAttack(source.getPlayerOrThrow(), switch (action) {
                case "fan" -> com.anton.elementalwands.entity.GuardianCombatRules.Attack.FAN;
                case "leap" -> com.anton.elementalwands.entity.GuardianCombatRules.Attack.LEAP;
                case "rock" -> com.anton.elementalwands.entity.GuardianCombatRules.Attack.THROW;
                case "shockwave" -> com.anton.elementalwands.entity.GuardianCombatRules.Attack.SHOCKWAVE;
                default -> com.anton.elementalwands.entity.GuardianCombatRules.Attack.SLAM;
            });
        } else if (action.equals("stop")) {
            guardian.stopReview();
        } else {
            guardian.rehearse(action);
        }
        source.sendFeedback(() -> Text.literal("Fractured Guardian: " + action
                + (action.equals("follow") ? " — follows you; stops 5 blocks away."
                : action.equals("beam") ? " — firing at you; Survival players can take damage."
                : action.equals("fight") ? " — cooperative boss enabled; targets nearby Survival/Adventure players. /ew guardian stop to end."
                : action.equals("fan") || action.equals("rock") || action.equals("shockwave") || action.equals("melee") || action.equals("leap") ? " — one real attack; Survival/Adventure players can take damage."
                : action.equals("stop") ? " — passive until /ew guardian fight, including after reload." : " — animation rehearsal, no damage.")), false);
        return 1;
    }
}
