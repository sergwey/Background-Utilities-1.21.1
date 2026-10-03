package net.xlebupaksa.backutils.log;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.xlebupaksa.backutils.profile.ProfileLoader;

import java.util.Collection;
import java.util.List;

/**
 * {@code /log} — a staff-authored announcement placed in the roleplay log, as in
 * {@code /log @a[distance=..10] "something happened nearby"}.
 *
 * <p>Audience selection is left to Brigadier's entity selector, so {@code distance} and the rest work without this mod
 * reimplementing them. The text passes through untouched, because Ember's markup policy is applied on
 * {@code ServerChatEvent}, which this command never goes through.
 */
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public class LogCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("log")
                .requires(s -> s.hasPermission(ProfileLoader.PROFILE_PERMISSION_LEVEL))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(this::announce))));
    }

    private int announce(CommandContext<CommandSourceStack> ctx) {
        try {
            // getOptionalPlayers rather than getPlayers: an empty match is a no-op for an announcement, not a command error.
            Collection<ServerPlayer> targets = EntityArgument.getOptionalPlayers(ctx, "targets");
            if (targets.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("No players matched that selector."));
                return 0;
            }

            String message = unquote(StringArgumentType.getString(ctx, "message"));
            if (message.isBlank()) {
                ctx.getSource().sendFailure(Component.literal("Nothing to announce."));
                return 0;
            }

            ServerPlayer source = ctx.getSource().getPlayerOrException();
            RoleplayLog.recordAnnouncement(source, message, List.copyOf(targets));

            int count = targets.size();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Announced to " + count + " player(s)."), false);
            return count;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static String unquote(String s) {
        if (s == null) return null;
        if (s.length() < 2) return s;
        boolean dq = s.startsWith("\"") && s.endsWith("\"");
        boolean sq = s.startsWith("'") && s.endsWith("'");
        return (dq || sq) ? s.substring(1, s.length() - 1) : s;
    }
}
