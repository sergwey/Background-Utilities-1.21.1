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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.data.SilenceKind;
import net.xlebupaksa.backutils.data.SilenceState;
import net.xlebupaksa.backutils.profile.ProfileLoader;

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
 * /chat silence &lt;player&gt; [&lt;kind&gt; &lt;true|false&gt;]
 * </pre>
 *
 * <p>{@code global} and {@code local} are channel-wide; {@code silence} is per player and per kind,
 * so a player can be muted in one channel and not the other. The separators live here because there
 * is one per channel.
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
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(this::silenceShow)
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests(this::suggestKinds)
                                        .then(Commands.argument("silenced", BoolArgumentType.bool())
                                                .executes(this::silenceSet))))));
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

    private int silenceShow(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            SilenceState state = silenceOf(target);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    target.getName().getString() + " is silenced in: " + state.describe() + "."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int silenceSet(CommandContext<CommandSourceStack> ctx) {
        try {
            if (BackUtils.data() == null) {
                ctx.getSource().sendFailure(Component.literal("The databases are not available."));
                return 0;
            }

            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            String raw = StringArgumentType.getString(ctx, "kind");
            SilenceKind kind = SilenceKind.parse(raw);
            if (kind == null) {
                ctx.getSource().sendFailure(Component.literal(
                        "Unknown kind '" + raw + "'. Use actions, local_chat or global_chat."));
                return 0;
            }

            boolean silenced = BoolArgumentType.getBool(ctx, "silenced");
            String name = target.getName().getString();
            SilenceState updated = BackUtils.data().silences().setFlag(name, kind, silenced);

            ctx.getSource().sendSuccess(() -> Component.literal(
                    name + (silenced ? " is now silenced in " : " is no longer silenced in ")
                            + kind.label() + " (now: " + updated.describe() + ")."), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static SilenceState silenceOf(ServerPlayer player) {
        if (BackUtils.data() == null) return SilenceState.none(player.getName().getString());
        SilenceState state = BackUtils.data().silences().find(player.getName().getString());
        return state == null ? SilenceState.none(player.getName().getString()) : state;
    }

    private CompletableFuture<Suggestions> suggestKinds(CommandContext<CommandSourceStack> ctx,
                                                        SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                List.of("actions", "local_chat", "global_chat"), builder);
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
