package net.xlebupaksa.backutils.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.log.Rolls;
import net.xlebupaksa.backutils.profile.ProfileLoader;

import java.util.List;

/**
 * {@code /roll} and {@code /hroll} — the dice, for staff only.
 *
 * <p>{@code /roll} leaves a line in the roleplay log naming the number; {@code /hroll} leaves the
 * same line with the number obfuscated, and the number itself where only an administrator reads it.
 * Neither posts anything to chat.
 *
 * <p>The shapes, all optional from the right: no arguments rolls once for nobody, a bare number is
 * the maximum, and a player selector or name comes first, then the maximum, then the reason, which
 * is free text and takes the rest of the line. A first argument that is only digits is read as the
 * maximum, because no player can be named that way.
 */
public class RollCommands {

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(open().requires(RollCommands::mayRoll));
        dispatcher.register(hidden().requires(RollCommands::mayRoll));
    }

    /** {@return the {@code /roll} tree, without its permission gate, which the test builds too} */
    public static LiteralArgumentBuilder<CommandSourceStack> open() {
        return tree("roll", false);
    }

    /** {@return the {@code /hroll} tree, without its permission gate} */
    public static LiteralArgumentBuilder<CommandSourceStack> hidden() {
        return tree("hroll", true);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree(String literal, boolean hidden) {
        Command<CommandSourceStack> roll = ctx -> run(ctx, hidden);

        return Commands.literal(literal)
                .executes(roll)
                .then(Commands.argument("max", maximum())
                        .executes(roll)
                        .then(reason(roll)))
                .then(Commands.argument("players", EntityArgument.players())
                        .executes(roll)
                        .then(Commands.argument("max", maximum())
                                .executes(roll)
                                .then(reason(roll)))
                        .then(reason(roll)));
    }

    /** The reason takes the rest of the line, so it is the last argument in every shape. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> reason(
            Command<CommandSourceStack> executes) {
        return Commands.argument("reason", StringArgumentType.greedyString()).executes(executes);
    }

    /** The same bounds the config declares, so a roll cannot ask for more than it allows. */
    private static IntegerArgumentType maximum() {
        return IntegerArgumentType.integer(1, 1000000);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, boolean hidden) {
        try {
            CommandSourceStack source = ctx.getSource();
            // A command needs a place on the map and somebody to name, so the console cannot roll.
            ServerPlayer roller = source.getPlayerOrException();

            int max = given(ctx, "max") ? IntegerArgumentType.getInteger(ctx, "max")
                    : BackUtilsConfig.getRollMax();
            String reason = given(ctx, "reason")
                    ? StringArgumentType.getString(ctx, "reason").trim() : "";

            List<ServerPlayer> subjects = List.of();
            if (given(ctx, "players")) {
                subjects = List.copyOf(EntityArgument.getOptionalPlayers(ctx, "players"));
                if (subjects.isEmpty()) {
                    source.sendFailure(Component.translatable("backutils.command.no_players"));
                    return 0;
                }
            }

            List<Integer> results = Rolls.execute(roller, subjects, max, reason, hidden);
            if (results.isEmpty()) {
                source.sendFailure(Component.translatable("backutils.roll.no_log"));
                return 0;
            }

            // Nothing is said in chat, not even to the one who rolled: the line is in the action log,
            // which they are a witness of, and the console holds the numbers. Failures above are the
            // exception, since a command that silently does nothing reads as a broken one.
            return results.size();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("backutils.command.error",
                    String.valueOf(e.getMessage())));
            return 0;
        }
    }

    /** {@return true when the command was given that argument} */
    private static boolean given(CommandContext<CommandSourceStack> ctx, String name) {
        for (var node : ctx.getNodes()) {
            if (node.getNode().getName().equals(name)) return true;
        }
        return false;
    }

    private static boolean mayRoll(CommandSourceStack source) {
        return source.hasPermission(ProfileLoader.PROFILE_PERMISSION_LEVEL);
    }
}
