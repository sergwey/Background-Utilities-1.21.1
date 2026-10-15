package net.xlebupaksa.backutils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.BackUtilsSettings;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.freeze.FreezeState;
import net.xlebupaksa.backutils.network.AdminAlerts;
import net.xlebupaksa.backutils.network.AdminNetwork;
import net.xlebupaksa.backutils.network.BannerTextNetwork;
import net.xlebupaksa.backutils.profile.ProfileLoader;
import net.xlebupaksa.backutils.profile.ProfileSound;

import java.util.List;

/**
 * {@code /backutils} — the server configuration root: whether actions are recorded at all, the banner
 * text drawn beside the log's button, and {@code typing_sounds}, the palette of sounds a chat profile
 * may name. Running it bare reports the current settings.
 *
 * <p>The typing effect's own settings — whether it types at all, its sound, its volume, its pitch and
 * its speed — are not a command: they belong to {@link BackUtilsConfig} and are edited from the
 * administrator menu's Log tab, because they describe how the log is drawn rather than what any
 * player may choose.
 *
 * <p>The chat separators live under {@code /chat}; {@code /freeze} and {@code /unfreeze} are roots of
 * their own, registered beside this one.
 */
public class BackUtilsCommands {

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        // /freeze [player] and /unfreeze [player] - holding a player still, and letting them go. No
        // player named is the sender, which is how an operator checks what being held is like before
        // doing it to somebody else. Roots of their own rather than nodes under /backutils: these are
        // reached for while something is going wrong, so the name that has to be got right is the
        // short one.
        d.register(Commands.literal("freeze")
                .requires(s -> s.hasPermission(ProfileLoader.PROFILE_PERMISSION_LEVEL))
                .executes(this::freezeSelf)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(this::freezeTarget)));
        d.register(Commands.literal("unfreeze")
                .requires(s -> s.hasPermission(ProfileLoader.PROFILE_PERMISSION_LEVEL))
                .executes(this::unfreezeSelf)
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(this::unfreezeTarget)));

        d.register(Commands.literal("backutils")
                .requires(s -> s.hasPermission(ProfileLoader.PROFILE_PERMISSION_LEVEL))

                .executes(this::summary)

                // /backutils menu - opens the administrator menu on the sender's client.
                .then(Commands.literal("menu").executes(this::openMenu))

                .then(Commands.literal("actions")
                        .then(Commands.literal("true").executes(ctx -> setActions(ctx, true)))
                        .then(Commands.literal("false").executes(ctx -> setActions(ctx, false))))

                // /backutils banner set <text>
                // /backutils banner clear
                // /backutils banner          - what it says now
                //
                // The text drawn beside the log's menu button, and a server setting rather than a
                // client one: an operator sets it once and every client is sent it, where a
                // client-side text would be seen by whoever typed the command and by nobody else.
                // This tree is the only one holding `banner` - the client tree that used to answer
                // it is gone - so nothing on the client can shadow this path or keep the server
                // from ever seeing it. The text is bounded, and the bound comes from the payload's
                // own codec: see BackUtilsSettings.checkBanner, which is where a value too long to
                // send is refused rather than stored.
                .then(Commands.literal("banner")
                        .executes(this::bannerGet)
                        .then(Commands.literal("set")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(this::bannerSet)))
                        .then(Commands.literal("clear").executes(this::bannerClear)))

                // /backutils typing_sounds add <name> <sound id>
                // /backutils typing_sounds list
                // /backutils typing_sounds remove <name>
                //
                // The palette a player's chat profile may name. It lives here rather than in the
                // client menu because it is a server decision: a profile stores the name, Ember
                // cannot know that name, and this list is what decides the id it plays. Named for
                // what the sounds are for rather than for the effect that plays them, whose own
                // settings the menu's Log tab holds.
                .then(Commands.literal("typing_sounds")
                        .executes(this::soundList)
                        .then(Commands.literal("list").executes(this::soundList))
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("sound", StringArgumentType.string())
                                                .suggests(ProfileSoundSuggestions.AVAILABLE)
                                                .executes(this::soundAdd))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(ProfileSoundSuggestions.PALETTE)
                                        .executes(this::soundRemove))))
        );
    }

    /** Opens the administrator menu, checking permission again because the menu exposes the whole
     * action log; the tree itself is already gated at level 2. */
    private int openMenu(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            if (!player.hasPermissions(AdminNetwork.REQUIRED_LEVEL)) {
                ctx.getSource().sendFailure(Component.literal(
                        "You need permission level " + AdminNetwork.REQUIRED_LEVEL + "."));
                return 0;
            }
            if (!AdminNetwork.canReceive(player)) {
                ctx.getSource().sendFailure(Component.literal(
                        "Your client cannot open the administrator menu."));
                return 0;
            }
            AdminAlerts.openMenu(player);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Holds a player still, or lets them go.
     *
     * <p>One handler each rather than four: a command with nobody named is the sender, and the only
     * difference between that and a named player is which player is looked up. What is said afterwards
     * is said to the sender and names the player, because an operator holding somebody in a room where
     * they are not standing needs to know which of two names took effect.
     */
    private int freezeSelf(CommandContext<CommandSourceStack> ctx) {
        try {
            return hold(ctx.getSource(), ctx.getSource().getPlayerOrException(), true);
        } catch (Exception e) {
            return failed(ctx, e);
        }
    }

    private int freezeTarget(CommandContext<CommandSourceStack> ctx) {
        try {
            return hold(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true);
        } catch (Exception e) {
            return failed(ctx, e);
        }
    }

    private int unfreezeSelf(CommandContext<CommandSourceStack> ctx) {
        try {
            return hold(ctx.getSource(), ctx.getSource().getPlayerOrException(), false);
        } catch (Exception e) {
            return failed(ctx, e);
        }
    }

    private int unfreezeTarget(CommandContext<CommandSourceStack> ctx) {
        try {
            return hold(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false);
        } catch (Exception e) {
            return failed(ctx, e);
        }
    }

    private static int hold(CommandSourceStack source, ServerPlayer player, boolean freeze) {
        String name = player.getName().getString();
        // Asked before the change, so the answer describes what happened rather than what was already
        // true: holding somebody who is already held is not an event, and the operator is told so.
        boolean was = FreezeState.frozen(player.getUUID());

        if (freeze == was) {
            source.sendSuccess(() -> Component.literal(name
                    + (freeze ? " is already held." : " is not being held.")), true);
            return 0;
        }

        if (freeze) FreezeState.freeze(player);
        else FreezeState.unfreeze(player);

        source.sendSuccess(() -> Component.literal(
                (freeze ? "Held " : "Released ") + name + "."), true);
        return 1;
    }

    private static int failed(CommandContext<CommandSourceStack> ctx, Exception e) {
        ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
        return 0;
    }

    private int summary(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Background Utils - radius " + BackUtilsConfig.getLogRadius()
                        + ", actions " + (BackUtilsConfig.isActionLoggingEnabled() ? "on" : "off")
                        + ".\nChat settings, including the separators, are under /chat."), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // Action recording
    // ------------------------------------------------------------------

    private int setActions(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        try {
            BackUtilsConfig.setActionLoggingEnabled(enabled);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Action recording " + (enabled ? "enabled" : "disabled") + "."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // The banner
    // ------------------------------------------------------------------

    /** Reports the text, drawn as it was typed so its markup is readable rather than applied. */
    private int bannerGet(CommandContext<CommandSourceStack> ctx) {
        String text = BackUtilsConfig.getBannerText();
        if (text.isBlank()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "No banner text. Set one with /backutils banner set <text>; \\n starts a new"
                            + " line."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Banner: ")
                .append(MarkupUtil.asLiteral(text)), false);
        return 1;
    }

    private int bannerSet(CommandContext<CommandSourceStack> ctx) {
        try {
            // Through the settings table rather than straight into the config, so what this refuses
            // is the banner's own rule — the same one the menu's editor is held to, and the same
            // words — rather than a second copy of the length here that could drift from it.
            String text = BackUtilsSettings.check(BackUtilsSettings.BANNER_TEXT_KEY,
                    StringArgumentType.getString(ctx, "text"));
            setBanner(text);
            ctx.getSource().sendSuccess(() -> Component.literal("Banner set: ")
                    .append(MarkupUtil.asLiteral(text)), false);
            return 1;
        } catch (IllegalArgumentException refusal) {
            // Said as it stands: it already names the limit and the size given, and an "Error:"
            // in front of it would read as a fault rather than as a value to shorten.
            ctx.getSource().sendFailure(Component.literal(refusal.getMessage()));
            return 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int bannerClear(CommandContext<CommandSourceStack> ctx) {
        try {
            setBanner("");
            ctx.getSource().sendSuccess(() -> Component.literal("Banner text cleared."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Writes the text and tells every client, in that order.
     *
     * <p>None of them reads this file itself, so a text that is stored but not sent would be stored
     * for nobody.
     */
    private static void setBanner(String text) {
        BackUtilsConfig.setBannerText(text);
        BannerTextNetwork.broadcast();
    }

    // ------------------------------------------------------------------
    // The profile sound palette
    // ------------------------------------------------------------------

    /**
     * {@return the word a quoted argument holds}, which Brigadier keeps the quotes around: a name or
     * an id read with {@code string()} arrives as the player typed it, quotation marks included, and
     * those marks are not part of either.
     */
    private static String unquote(String s) {
        if (s == null) return null;
        if (s.length() < 2) return s;
        boolean dq = s.startsWith("\"") && s.endsWith("\"");
        boolean sq = s.startsWith("'") && s.endsWith("'");
        return (dq || sq) ? s.substring(1, s.length() - 1) : s;
    }

    private int soundList(CommandContext<CommandSourceStack> ctx) {
        List<String> sounds = BackUtilsConfig.getProfileSounds();
        if (sounds.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "No profile sounds set up. Add one with /backutils typing_sounds add"
                            + " <name> <sound id>."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Profile sounds (" + sounds.size() + "), played at volume "
                        + ProfileSound.VOLUME + ":"), false);
        // One line each, and each say what Ember is handed: an alias is its id, a bare entry is
        // whatever it is, which is how an operator sees what a profile's name will actually play.
        for (String entry : sounds) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + ProfileSound.describe(entry)),
                    false);
        }
        return 1;
    }

    private int soundAdd(CommandContext<CommandSourceStack> ctx) {
        try {
            String name = unquote(StringArgumentType.getString(ctx, "name")).trim();
            String sound = unquote(StringArgumentType.getString(ctx, "sound")).trim();
            List<String> palette = BackUtilsConfig.getProfileSounds();

            // The name is the part a profile stores, so it is checked against the palette as it is;
            // the id is checked against the palette too, and only for the one thing the palette can
            // tell it that its shape cannot: whether it is another entry's name. The pack that holds
            // the sound itself belongs to the client that hears it and this server has never seen it,
            // so the id's shape, and nothing of the registry, is still the whole of the rest.
            String nameProblem = ProfileSound.checkName(name, palette);
            if (nameProblem != null) {
                ctx.getSource().sendFailure(Component.literal(nameProblem));
                return 0;
            }
            String idProblem = ProfileSound.checkId(sound, palette);
            if (idProblem != null) {
                ctx.getSource().sendFailure(Component.literal(idProblem));
                return 0;
            }

            if (!BackUtilsConfig.addProfileSound(ProfileSound.alias(name, sound))) {
                ctx.getSource().sendFailure(Component.literal(
                        "'" + name + "' is already one of the profile sounds."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Added " + name + " -> " + sound + " to the profile sounds."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int soundRemove(CommandContext<CommandSourceStack> ctx) {
        try {
            String name = unquote(StringArgumentType.getString(ctx, "name")).trim();
            if (!BackUtilsConfig.removeProfileSound(name)) {
                ctx.getSource().sendFailure(Component.literal(
                        "'" + name + "' is not one of the profile sounds."));
                return 0;
            }
            // Profiles already using it fall silent rather than being deleted.
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Removed " + name + ". Profiles using it go silent."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}
