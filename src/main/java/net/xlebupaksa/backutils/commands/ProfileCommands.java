package net.xlebupaksa.backutils.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.ActiveProfile;
import net.xlebupaksa.backutils.data.ChatProfile;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.data.NameProfile;
import net.xlebupaksa.backutils.data.ProfileOptions;
import net.xlebupaksa.backutils.profile.ProfileLoader;
import net.xlebupaksa.backutils.profile.ProfileText;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ProfileCommands {

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("profile")
                .requires(s -> s.hasPermission(ProfileLoader.PROFILE_PERMISSION_LEVEL))

                .then(Commands.literal("reload")
                        .executes(this::reloadSelf)
                        .then(Commands.literal("all").executes(this::reloadAll))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(this::reloadTarget)))

                .then(Commands.literal("name")
                        .then(Commands.literal("create")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .then(Commands.argument("displayed", StringArgumentType.greedyString())
                                                        .executes(this::nameCreate)))))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests(this::suggestNameProfiles)
                                                .then(Commands.argument("displayed", StringArgumentType.greedyString())
                                                        .executes(this::nameEdit)))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests(this::suggestNameProfiles)
                                                .executes(this::nameDelete))))
                        .then(Commands.literal("use")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests(this::suggestNameProfiles)
                                                .executes(this::nameUse))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(this::nameList))))

                .then(Commands.literal("chat")
                        .then(Commands.literal("create")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .then(Commands.argument("format", StringArgumentType.greedyString())
                                                        .executes(this::chatCreate)))))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests(this::suggestChatProfiles)
                                                .then(Commands.argument("format", StringArgumentType.greedyString())
                                                        .executes(this::chatEdit)))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests(this::suggestChatProfiles)
                                                .executes(this::chatDelete))))
                        .then(Commands.literal("use")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests(this::suggestChatProfiles)
                                                .executes(this::chatUse))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(this::chatList))))
        );
    }

    // ------------------------------------------------------------------
    // Name profile commands
    // ------------------------------------------------------------------

    private int nameCreate(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            String name = StringArgumentType.getString(ctx, "name");
            if (ProfileOptions.DEFAULT_PROFILE.equals(name)) {
                ctx.getSource().sendFailure(Component.literal(
                        "'default' is reserved and cannot be used as a profile name."));
                return 0;
            }
            String displayed = sanitise(StringArgumentType.getString(ctx, "displayed"));
            String p = target.getName().getString();

            if (BackUtils.data().names().findByPlayerAndName(p, name).isPresent()) {
                ctx.getSource().sendFailure(Component.literal(
                        p + " already has a name profile called '" + name + "'."));
                return 0;
            }
            long id = BackUtils.data().names().create(name, displayed, p);
            // Push even though the active profile did not change, so the new choice reaches the
            // player's client.
            ProfileLoader.reload(target);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Created name profile '" + name + "' (id " + id + ") for " + p + "."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int nameEdit(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            String name = StringArgumentType.getString(ctx, "name");
            String displayed = sanitise(StringArgumentType.getString(ctx, "displayed"));
            String p = target.getName().getString();

            if (BackUtils.data().names().findByPlayerAndName(p, name).isEmpty()) {
                ctx.getSource().sendFailure(Component.literal(
                        p + " has no name profile called '" + name + "'."));
                return 0;
            }
            BackUtils.data().names().update(name, displayed, p);
            ProfileLoader.reload(target);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Updated name profile '" + name + "' for " + p + "."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int nameDelete(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            String name = StringArgumentType.getString(ctx, "name");
            String p = target.getName().getString();

            Optional<NameProfile> opt = BackUtils.data().names().findByPlayerAndName(p, name);
            if (opt.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal(
                        p + " has no name profile called '" + name + "'."));
                return 0;
            }
            long id = opt.get().id();
            BackUtils.data().names().delete(name, p);

            Optional<ActiveProfile> active = BackUtils.data().active().find(p);
            if (active.isPresent() && active.get().nameProfileId() == id) {
                BackUtils.data().active().clearActiveName(p);
            }
            ProfileLoader.reload(target);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Deleted name profile '" + name + "' for " + p + "."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int nameUse(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            String name = StringArgumentType.getString(ctx, "name");
            String p = target.getName().getString();

            if (ProfileOptions.DEFAULT_PROFILE.equals(name)) {
                BackUtils.data().active().clearActiveName(p);
                ProfileLoader.reload(target);
                ctx.getSource().sendSuccess(() -> Component.literal(
                        p + " reset to the default name."), false);
                return 1;
            }

            Optional<NameProfile> opt = BackUtils.data().names().findByPlayerAndName(p, name);
            if (opt.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal(
                        p + " has no name profile called '" + name + "'."));
                return 0;
            }
            BackUtils.data().active().setActiveName(p, opt.get().id());
            ProfileLoader.reload(target);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    p + " is now using name profile '" + name + "'."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int nameList(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            List<NameProfile> list = BackUtils.data().names()
                    .findByPlayer(target.getName().getString());
            if (list.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        target.getName().getString() + " has no name profiles."), false);
                return 1;
            }
            for (NameProfile n : list) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "[" + n.id() + "] " + n.name() + " -> " + n.displayedName()), false);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Chat profile commands
    // ------------------------------------------------------------------

    private int chatCreate(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            String name = StringArgumentType.getString(ctx, "name");
            if (ProfileOptions.DEFAULT_PROFILE.equals(name)) {
                ctx.getSource().sendFailure(Component.literal(
                        "'default' is reserved and cannot be used as a profile name."));
                return 0;
            }
            String format = sanitise(StringArgumentType.getString(ctx, "format"));
            String p = target.getName().getString();

            if (!format.contains(ProfileText.MESSAGE)) {
                ctx.getSource().sendFailure(Component.literal(
                        "The chat format must contain " + ProfileText.MESSAGE
                                + " — that is where the message text is inserted."));
                return 0;
            }
            if (BackUtils.data().chats().findByPlayerAndName(p, name).isPresent()) {
                ctx.getSource().sendFailure(Component.literal(
                        p + " already has a chat profile called '" + name + "'."));
                return 0;
            }
            long id = BackUtils.data().chats().create(name, format, "", p);
            ProfileLoader.reload(target);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Created chat profile '" + name + "' (id " + id + ") for " + p
                            + ". " + ProfileText.MESSAGE + " marks where the message goes."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int chatEdit(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            String name = StringArgumentType.getString(ctx, "name");
            String format = sanitise(StringArgumentType.getString(ctx, "format"));
            String p = target.getName().getString();

            if (!format.contains(ProfileText.MESSAGE)) {
                ctx.getSource().sendFailure(Component.literal(
                        "The chat format must contain " + ProfileText.MESSAGE
                                + " — that is where the message text is inserted."));
                return 0;
            }
            if (BackUtils.data().chats().findByPlayerAndName(p, name).isEmpty()) {
                ctx.getSource().sendFailure(Component.literal(
                        p + " has no chat profile called '" + name + "'."));
                return 0;
            }
            // The sound is left alone: it belongs to the player, and this command edits the format.
            ChatProfile existing = BackUtils.data().chats().findByPlayerAndName(p, name).orElseThrow();
            BackUtils.data().chats().update(name, format, existing.sound(), p);
            ProfileLoader.reload(target);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Updated chat profile '" + name + "' for " + p + "."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int chatDelete(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            String name = StringArgumentType.getString(ctx, "name");
            String p = target.getName().getString();

            Optional<ChatProfile> opt = BackUtils.data().chats().findByPlayerAndName(p, name);
            if (opt.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal(
                        p + " has no chat profile called '" + name + "'."));
                return 0;
            }
            long id = opt.get().id();
            BackUtils.data().chats().delete(name, p);

            Optional<ActiveProfile> active = BackUtils.data().active().find(p);
            if (active.isPresent() && active.get().chatProfileId() == id) {
                BackUtils.data().active().clearActiveChat(p);
            }
            ProfileLoader.reload(target);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Deleted chat profile '" + name + "' for " + p + "."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int chatUse(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            String name = StringArgumentType.getString(ctx, "name");
            String p = target.getName().getString();

            if (ProfileOptions.DEFAULT_PROFILE.equals(name)) {
                BackUtils.data().active().clearActiveChat(p);
                ProfileLoader.reload(target);
                ctx.getSource().sendSuccess(() -> Component.literal(
                        p + " reset to the default chat format."), false);
                return 1;
            }

            Optional<ChatProfile> opt = BackUtils.data().chats().findByPlayerAndName(p, name);
            if (opt.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal(
                        p + " has no chat profile called '" + name + "'."));
                return 0;
            }
            BackUtils.data().active().setActiveChat(p, opt.get().id());
            ProfileLoader.reload(target);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    p + " is now using chat profile '" + name + "'."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int chatList(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            List<ChatProfile> list = BackUtils.data().chats()
                    .findByPlayer(target.getName().getString());
            if (list.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        target.getName().getString() + " has no chat profiles."), false);
                return 1;
            }
            for (ChatProfile c : list) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "[" + c.id() + "] " + c.name() + " -> " + c.format()
                                + (c.sound() == null || c.sound().isBlank()
                                        ? "" : "   (sound " + c.sound() + ")")), false);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Reload / separator
    // ------------------------------------------------------------------

    private int reloadSelf(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer self = ctx.getSource().getPlayerOrException();
            ProfileLoader.ReloadResult r = ProfileLoader.reload(self);
            Component feedback = switch (r) {
                case UNCHANGED -> Component.literal("Profile already in sync.");
                case UPDATED -> Component.literal("Profile reloaded from database.");
                case RESET_TO_DEFAULT -> Component.literal("No active profile — reset to default.");
            };
            ctx.getSource().sendSuccess(() -> feedback, false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int reloadTarget(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            ProfileLoader.ReloadResult r = ProfileLoader.reload(target);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Reloaded " + target.getName().getString() + ": " + r.name()), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int reloadAll(CommandContext<CommandSourceStack> ctx) {
        try {
            if (storageUnavailable(ctx.getSource())) return 0;
            MinecraftServer server = ctx.getSource().getServer();
            int updated = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (ProfileLoader.reload(player) != ProfileLoader.ReloadResult.UNCHANGED) updated++;
            }
            int u = updated;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Reloaded " + u + " player profile(s)."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** {@return true, having said why, when the databases could not be opened} */
    private static boolean storageUnavailable(CommandSourceStack source) {
        if (BackUtils.data() != null) return false;
        source.sendFailure(Component.literal(
                "Profile storage is unavailable — see the server log for details."));
        return true;
    }

    /** Strips a single layer of quotes and closes any markup tag the author left open, which would
     * otherwise leak its styling across the rest of the chat line. */
    private static String sanitise(String value) {
        return MarkupUtil.balance(unquote(value));
    }

    private static String unquote(String s) {
        if (s == null) return null;
        if (s.length() < 2) return s;
        boolean dq = s.startsWith("\"") && s.endsWith("\"");
        boolean sq = s.startsWith("'") && s.endsWith("'");
        if (dq || sq) return s.substring(1, s.length() - 1);
        return s;
    }

    private CompletableFuture<Suggestions> suggestNameProfiles(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            List<String> names = BackUtils.data().names()
                    .findByPlayer(target.getName().getString())
                    .stream().map(NameProfile::name).toList();
            b.suggest(ProfileOptions.DEFAULT_PROFILE);
            for (String n : names) {
                b.suggest(n.contains(" ") ? "\"" + n + "\"" : n);
            }
            return b.buildFuture();
        } catch (Exception e) {
            return b.buildFuture();
        }
    }

    private CompletableFuture<Suggestions> suggestChatProfiles(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder b) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            List<String> names = BackUtils.data().chats()
                    .findByPlayer(target.getName().getString())
                    .stream().map(ChatProfile::name).toList();
            b.suggest(ProfileOptions.DEFAULT_PROFILE);
            for (String n : names) {
                b.suggest(n.contains(" ") ? "\"" + n + "\"" : n);
            }
            return b.buildFuture();
        } catch (Exception e) {
            return b.buildFuture();
        }
    }
}
