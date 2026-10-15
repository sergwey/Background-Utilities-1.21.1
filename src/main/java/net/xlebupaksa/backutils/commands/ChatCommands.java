package net.xlebupaksa.backutils.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.data.BackUtilsData;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.data.SilenceData;
import net.xlebupaksa.backutils.data.SilenceState;
import net.xlebupaksa.backutils.profile.ProfileLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /chat} — the chat system's configuration.
 *
 * <pre>
 * /chat radius &lt;blocks&gt;
 * /chat global &lt;true|false&gt;
 * /chat local &lt;true|false&gt;
 * /chat separator [local|global] &lt;value&gt;
 * /chat silence &lt;names...&gt; [&lt;kind&gt; &lt;true|false&gt;]
 * </pre>
 *
 * <p>{@code global} and {@code local} are channel-wide; {@code silence} is per player and per kind,
 * so a player can be muted in one channel and not the other. The separators live here because there
 * is one per channel.
 *
 * <p>{@code silence} takes a list — names separated by spaces or commas, or {@code @a} for everyone
 * online — read by {@link SilenceTargets}. Every name in it is resolved before the first row is
 * written, so a line that names somebody who is not there changes nothing rather than silencing the
 * half that does exist.
 */
public class ChatCommands {

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("chat")
                .requires(s -> s.hasPermission(ProfileLoader.PROFILE_PERMISSION_LEVEL))
                .executes(this::status)

                .then(Commands.literal("radius")
                        .executes(this::radiusGet)
                        .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0D, 512.0D))
                                .executes(this::radiusSet)))

                .then(Commands.literal("global")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> channel(ctx, true))))

                .then(Commands.literal("local")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> channel(ctx, false))))

                .then(Commands.literal("separator")
                        .executes(this::separatorShow)
                        .then(Commands.literal("local")
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> separatorSet(ctx, false))))
                        .then(Commands.literal("global")
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> separatorSet(ctx, true)))))

                .then(Commands.literal("silence")
                        .then(Commands.argument("targets", StringArgumentType.greedyString())
                                .suggests(this::suggestTargets)
                                .executes(this::silence))));
    }

    // ------------------------------------------------------------------

    private int status(CommandContext<CommandSourceStack> ctx) {
        BackUtilsConfigState state = BackUtilsConfigState.current();
        ctx.getSource().sendSuccess(() -> Component.literal(String.join("\n", state.lines())), false);
        return 1;
    }

    private int radiusGet(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Local chat radius: " + BackUtilsConfig.getLocalChatRadius() + " blocks."), false);
        return 1;
    }

    private int radiusSet(CommandContext<CommandSourceStack> ctx) {
        try {
            double blocks = DoubleArgumentType.getDouble(ctx, "blocks");
            BackUtilsConfig.setLocalChatRadius(blocks);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Local chat radius set to " + blocks + " blocks."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Opens or closes a channel to ordinary players. The argument is {@code enabled}, read as "may
     * everyone use this channel"; the stored config value is the inverse, staff-only flag.
     */
    private int channel(CommandContext<CommandSourceStack> ctx, boolean global) {
        try {
            boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
            String channel = global ? "Global" : "Local";

            if (global) {
                BackUtilsConfig.setGlobalChatStaffOnly(!enabled);
            } else {
                BackUtilsConfig.setLocalChatStaffOnly(!enabled);
            }

            ctx.getSource().sendSuccess(() -> Component.literal(enabled
                    ? channel + " chat is now open to everyone."
                    : channel + " chat is now staff-only (permission level 2+)."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Separators
    // ------------------------------------------------------------------

    private int separatorShow(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Separators — local: [" + BackUtilsConfig.getSeparator() + "]"
                        + ", global: [" + BackUtilsConfig.getGlobalSeparator() + "]"), false);
        return 1;
    }

    private int separatorSet(CommandContext<CommandSourceStack> ctx, boolean global) {
        try {
            // Balanced before storing: an unclosed markup tag would swallow the rest of every
            // chat line.
            String value = MarkupUtil.balance(
                    unquote(StringArgumentType.getString(ctx, "value")));

            if (global) {
                BackUtilsConfig.setGlobalSeparator(value);
            } else {
                BackUtilsConfig.setSeparator(value);
            }

            String channel = global ? "Global" : "Local";
            ctx.getSource().sendSuccess(() -> Component.literal(
                    channel + " chat separator set to: [" + value + "]"), false);
            return 1;
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

    // ------------------------------------------------------------------
    // Per-player silence
    // ------------------------------------------------------------------

    private int silence(CommandContext<CommandSourceStack> ctx) {
        try {
            BackUtilsData data = BackUtils.data();
            if (data == null) {
                ctx.getSource().sendFailure(Component.literal("The databases are not available."));
                return 0;
            }
            SilenceData silences = data.silences();

            SilenceTargets.Parsed parsed =
                    SilenceTargets.parse(StringArgumentType.getString(ctx, "targets"));

            if (parsed.unknownKind() != null) {
                ctx.getSource().sendFailure(Component.literal("Unknown kind '"
                        + parsed.unknownKind() + "'. Use "
                        + String.join(", ", SilenceTargets.kindWords()) + "."));
                return 0;
            }
            if (parsed.names().isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("Say who to silence."));
                return 0;
            }

            // Resolved before anything is written: a list is one command, so a name that matches
            // nobody refuses all of it rather than silencing the names that happened to be online.
            SilenceTargets.Resolved resolved =
                    SilenceTargets.resolve(parsed.names(), onlineNames(ctx.getSource()));
            if (resolved.refuses()) {
                ctx.getSource().sendFailure(Component.literal(
                        "No player named " + quoted(resolved.unknown()) + "."));
                return 0;
            }
            if (resolved.matched().isEmpty()) {
                // Only the selector can land here: a name that matched nobody was refused above, so
                // what is left is @a with nobody online to be it.
                ctx.getSource().sendFailure(Component.literal(
                        "Nobody is online to match " + SilenceTargets.EVERYONE + "."));
                return 0;
            }

            for (String name : resolved.matched()) {
                if (!parsed.sets()) {
                    SilenceState state = silenced(silences, name);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            name + " is silenced in: " + state.describe() + "."), false);
                    continue;
                }

                boolean silenced = parsed.value();
                SilenceState updated = silences.setFlag(name, parsed.kind(), silenced);
                ctx.getSource().sendSuccess(() -> Component.literal(
                        name + (silenced ? " is now silenced in " : " is no longer silenced in ")
                                + parsed.kind().label() + " (now: " + updated.describe() + ")."), true);
            }
            return resolved.matched().size();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static SilenceState silenced(SilenceData silences, String name) {
        SilenceState state = silences.find(name);
        return state == null ? SilenceState.none(name) : state;
    }

    /** {@return the account names of the players online, in the order the server lists them} */
    private static List<String> onlineNames(CommandSourceStack source) {
        List<String> names = new ArrayList<>();
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            names.add(player.getName().getString());
        }
        return names;
    }

    /** {@return the written names as one readable phrase, such as {@code 'a', 'b'}} */
    private static String quoted(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            if (sb.length() > 0) sb.append(", ");
            sb.append('\'').append(name).append('\'');
        }
        return sb.toString();
    }

    /**
     * Offers what can be written next: the players online and {@code @a} while names are being
     * written, and {@code true} or {@code false} once a kind has been.
     *
     * <p>The whole line so far arrives as one remaining string, because the list is one argument, so
     * the word being typed is the last one in it and everything before it is a whole word — which is
     * what {@link SilenceTargets#following} reads. The suggestions are offered from where that word
     * begins, which is what keeps Brigadier's own filtering to that word rather than to the whole
     * list: the half-typed name is the only thing offered candidates are matched against.
     */
    private CompletableFuture<Suggestions> suggestTargets(CommandContext<CommandSourceStack> ctx,
                                                          SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        boolean starting = remaining.isEmpty()
                || SilenceTargets.isSeparator(remaining.charAt(remaining.length() - 1));
        List<String> written = SilenceTargets.words(remaining);
        String partial = starting || written.isEmpty() ? "" : written.getLast();
        List<String> whole = partial.isEmpty() ? written : written.subList(0, written.size() - 1);

        return SharedSuggestionProvider.suggest(
                SilenceTargets.following(whole, ctx.getSource().getOnlinePlayerNames(), starting),
                builder.createOffset(builder.getStart() + remaining.length() - partial.length()));
    }

    /** Gathers the whole channel configuration in one place, for the bare command. */
    private record BackUtilsConfigState(List<String> lines) {
        static BackUtilsConfigState current() {
            return new BackUtilsConfigState(List.of(
                    "Chat setup",
                    "  local radius: " + BackUtilsConfig.getLocalChatRadius() + " blocks",
                    "  global chat: " + access(BackUtilsConfig.isGlobalChatStaffOnly()),
                    "  local chat:  " + access(BackUtilsConfig.isLocalChatStaffOnly()),
                    "  separators:  local [" + BackUtilsConfig.getSeparator() + "]"
                            + ", global [" + BackUtilsConfig.getGlobalSeparator() + "]",
                    "  actions:     " + (BackUtilsConfig.isActionLoggingEnabled() ? "on" : "off"),
                    "  '!' before a message sends it to global chat.",
                    "  '^text^' performs a silent action; '*text*' a normal one."));
        }

        private static String access(boolean staffOnly) {
            return staffOnly ? "staff only (permission 2+)" : "everyone";
        }
    }
}
