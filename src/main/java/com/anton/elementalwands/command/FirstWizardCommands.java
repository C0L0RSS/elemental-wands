package com.anton.elementalwands.command;

import com.anton.elementalwands.entity.firstwizard.FirstWizardEntity;
import com.anton.elementalwands.entity.firstwizard.FirstWizardRules;
import com.anton.elementalwands.entity.firstwizard.spell.BossSpells;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Comparator;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

/**
 * Operator-only control of the nearest First Wizard: /ew wizard fight, calm, move &lt;name&gt;, phase two, status and stop. Summon
 * one with /summon elementalwands:first_wizard; it fights on a round floor where it stands.
 */
public final class FirstWizardCommands {
    private static final double RANGE = 64;

    private FirstWizardCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("wizard").requires(source -> source.hasPermissionLevel(2));
        root.then(CommandManager.literal("fight").executes(context -> {
            wizard(context.getSource()).fight();
            context.getSource().sendFeedback(() -> Text.literal("The First Wizard fights whoever is in Survival or Adventure nearby."), false);
            return 1;
        }));
        root.then(CommandManager.literal("calm").executes(context -> {
            wizard(context.getSource()).calm();
            context.getSource().sendFeedback(() -> Text.literal("The First Wizard stands passive; what it already cast burns out."), false);
            return 1;
        }));
        root.then(CommandManager.literal("stop").executes(context -> {
            wizard(context.getSource()).stop();
            context.getSource().sendFeedback(() -> Text.literal("The First Wizard stands passive and everything it left is cleared."), false);
            return 1;
        }));
        root.then(CommandManager.literal("status").executes(context -> {
            String status = wizard(context.getSource()).status();
            context.getSource().sendFeedback(() -> Text.literal(status), false);
            return 1;
        }));
        root.then(CommandManager.literal("phase").then(CommandManager.literal("two").executes(context -> {
            boolean started = wizard(context.getSource()).requestPhaseTwo();
            context.getSource().sendFeedback(() -> Text.literal(started
                    ? "The First Wizard is held at half health; the change to phase two starts as soon as the move in hand allows."
                    : "The First Wizard is already changing or in phase two."), false);
            return started ? 1 : 0;
        })));
        var move = CommandManager.literal("move");
        for (FirstWizardRules.Move each : FirstWizardRules.Move.values())
            move.then(CommandManager.literal(each.id).executes(context -> {
                String name = each.id.replace('_', ' ');
                if (BossSpells.get(each) == null) {
                    context.getSource().sendError(Text.literal("The First Wizard's " + name + " is not built yet."));
                    return 0;
                }
                if (each.phase > 1 && !wizard(context.getSource()).inPhaseTwo()) {
                    context.getSource().sendError(Text.literal("The First Wizard's " + name + " is a phase-two move; /ew wizard phase two first."));
                    return 0;
                }
                boolean cast = wizard(context.getSource()).testMove(context.getSource().getPlayerOrThrow(), each);
                context.getSource().sendFeedback(() -> Text.literal(cast ? "The First Wizard casts " + name + " at you, then stands passive. It only hurts in Survival or Adventure."
                        : "The First Wizard cannot cast " + name + " from where it stands."), false);
                return cast ? 1 : 0;
            }));
        root.then(move);
        dispatcher.register(CommandManager.literal("ew").then(root));
    }

    private static FirstWizardEntity wizard(ServerCommandSource source) throws CommandSyntaxException {
        var pos = source.getPosition();
        return source.getWorld().getEntitiesByClass(FirstWizardEntity.class,
                        new Box(pos, pos).expand(RANGE), entity -> entity.isAlive() && entity.squaredDistanceTo(pos) <= RANGE * RANGE)
                .stream().min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(pos)))
                .orElseThrow(() -> new SimpleCommandExceptionType(
                        Text.literal("No First Wizard within 64 blocks. Summon one first: /summon elementalwands:first_wizard")).create());
    }
}
