package net.xlebupaksa.backutils.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.freeze.FreezeState;
import net.xlebupaksa.backutils.network.AdminAlerts;
import net.xlebupaksa.backutils.network.AdminNetwork;
import net.xlebupaksa.backutils.profile.ProfileLoader;
import net.xlebupaksa.backutils.profile.ProfileSound;

import java.util.List;

/**
 * {@code /backutils} — the server configuration root: how far the log reaches, whether actions are
 * recorded at all, and what the typing effect sounds like. Running it bare reports the current
 * settings. The chat separators live under {@code /chat}.
 */
public class BackUtilsCommands {

    /** Ember's preset names, listed only to make the command discoverable. */
    private static final String TYPING_PRESETS =
            "click, whoosh, static, magic, tick, pop, thud, shatter";

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("backutils")
                .requires(s -> s.hasPermission(ProfileLoader.PROFILE_PERMISSION_LEVEL))

                .executes(this::summary)

                // /backutils menu - opens the administrator menu on the sender's client.
                .then(Commands.literal("menu").executes(this::openMenu))

                // /backutils freeze [player] and /backutils unfreeze [player] - holding a player
                // still, and letting them go. No player named is the sender, which is how an operator
                // checks what being held is like before doing it to somebody else.
                .then(Commands.literal("freeze")
                        .executes(this::freezeSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(this::freezeTarget)))
                .then(Commands.literal("unfreeze")
                        .executes(this::unfreezeSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(this::unfreezeTarget)))

                .then(Commands.literal("radius")
                        .executes(this::radiusGet)
                        .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(0.0D, 512.0D))
                                .executes(this::radiusSet)))

                .then(Commands.literal("actions")
                        .then(Commands.literal("true").executes(ctx -> setActions(ctx, true)))
                        .then(Commands.literal("false").executes(ctx -> setActions(ctx, false))))

                // /backutils typing on | off
                // /backutils typing minecraft:block.note_block.snare 0.3 1.5
                .then(Commands.literal("typing")
                        .executes(this::typingGet)
                        .then(Commands.literal("on").executes(ctx -> setTyping(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setTyping(ctx, false)))
                        .then(Commands.literal("speed")
                                .then(Commands.argument("charactersPerSecond",
                                                DoubleArgumentType.doubleArg(0.1D, 1000.0D))
                                        .executes(this::typingSpeed)))
                        .then(Commands.argument("sound", StringArgumentType.string())
                                .then(Commands.argument("volume", DoubleArgumentType.doubleArg(0.0D, 2.0D))
                                        .then(Commands.argument("pitch", DoubleArgumentType.doubleArg(0.5D, 2.0D))
                                                .executes(this::typingSet)))))

                // /backutils profiles sound add <sound>
                // /backutils profiles sound remove <sound>
                //
                // The palette a player's chat profile may choose from. It lives here rather than in
                // the client menu because it is a server decision: Ember plays any sound id, and
                // this list is what limits the choice.
                .then(Commands.literal("profiles")
                        .executes(this::profileSoundList)
                        .then(Commands.literal("sound")
                                .executes(this::profileSoundList)
                                .then(Commands.literal("list").executes(this::profileSoundList))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("sound", ResourceLocationArgument.id())
                                                .suggests(ProfileSoundSuggestions.AVAILABLE)
                                                .executes(this::profileSoundAdd)))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("sound", ResourceLocationArgument.id())
                                                .suggests(ProfileSoundSuggestions.PALETTE)
                                                .executes(this::profileSoundRemove)))))
        );
    }

    /** Opens the administrator menu, checking permission again because the menu exposes the whole
     * action log; the tree itself is already gated at level 2. */
    private int openMenu(CommandContext<CommandSourceStack> ctx) {        try {
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
                        + ", typing " + (BackUtilsConfig.isTypingEnabled() ? "on" : "off")
                        + ".\nChat settings, including the separators, are under /chat."), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // Witness radius and action recording
    // ------------------------------------------------------------------

    private int radiusGet(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Action log radius: " + BackUtilsConfig.getLogRadius() + " blocks."), false);
        return 1;
    }

    private int radiusSet(CommandContext<CommandSourceStack> ctx) {
        try {
            double blocks = DoubleArgumentType.getDouble(ctx, "blocks");
            BackUtilsConfig.setLogRadius(blocks);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Action log radius set to " + blocks + " blocks."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

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
    // Typing effect
    // ------------------------------------------------------------------

    private int typingGet(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Typing: " + (BackUtilsConfig.isTypingEnabled() ? "on" : "off")
                        + ", sound " + BackUtilsConfig.getTypingSound()
                        + ", volume " + BackUtilsConfig.getTypingVolume()
                        + ", pitch " + BackUtilsConfig.getTypingPitch()
                        + ", speed " + BackUtilsConfig.getTypingSpeed() + " chars/s."), false);
        return 1;
    }

    private int setTyping(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        try {
            BackUtilsConfig.setTypingEnabled(enabled);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Typing effect " + (enabled ? "enabled" : "disabled") + "."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int typingSpeed(CommandContext<CommandSourceStack> ctx) {
        try {
            double speed = DoubleArgumentType.getDouble(ctx, "charactersPerSecond");
            BackUtilsConfig.setTypingSpeed(speed);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Typing speed set to " + speed + " characters per second."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int typingSet(CommandContext<CommandSourceStack> ctx) {
        try {
            String sound = unquote(StringArgumentType.getString(ctx, "sound"));
            double volume = DoubleArgumentType.getDouble(ctx, "volume");
            double pitch = DoubleArgumentType.getDouble(ctx, "pitch");

            if (sound == null || sound.isBlank()) {
                ctx.getSource().sendFailure(Component.literal("No sound given."));
                return 0;
            }

            // A bare word is passed through to Ember's preset table; a namespaced id is checked
            // here so a typo is reported instead of failing silently at play time.
            if (sound.indexOf(':') >= 0) {
                ResourceLocation id = ResourceLocation.tryParse(sound);
                if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
                    ctx.getSource().sendFailure(Component.literal(
                            "Unknown sound '" + sound + "'. Give a sound id, or one of Ember's "
                                    + "presets: " + TYPING_PRESETS + "."));
                    return 0;
                }
            }

            BackUtilsConfig.setTypingSound(sound, volume, pitch);
            BackUtilsConfig.setTypingEnabled(true);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Typing sound set to " + sound + " at volume " + volume
                            + " and pitch " + pitch + "."), false);
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
    // The profile sound palette
    // ------------------------------------------------------------------

    private int profileSoundList(CommandContext<CommandSourceStack> ctx) {
        List<String> sounds = BackUtilsConfig.getProfileSounds();
        if (sounds.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "No profile sounds set up. Add one with /backutils profiles sound add <sound>."),
                    false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Profile sounds (" + sounds.size() + "), played at volume "
                        + ProfileSound.VOLUME + ": " + String.join(", ", sounds)), false);
        return 1;
    }

    private int profileSoundAdd(CommandContext<CommandSourceStack> ctx) {
        try {
            String sound = profileSoundName(ctx);
            if (sound == null) {
                ctx.getSource().sendFailure(Component.literal(
                        "Unknown sound. Pick one from the list, or use an id such as "
                                + "minecraft:block.note_block.bell."));
                return 0;
            }
            if (!BackUtilsConfig.addProfileSound(sound)) {
                ctx.getSource().sendFailure(Component.literal(
                        "'" + sound + "' is already one of the profile sounds."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Added " + sound + " to the profile sounds."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private int profileSoundRemove(CommandContext<CommandSourceStack> ctx) {
        try {
            String sound = profileSoundName(ctx);
            if (sound == null) {
                ctx.getSource().sendFailure(Component.literal("Unknown sound."));
                return 0;
            }
            if (!BackUtilsConfig.removeProfileSound(sound)) {
                ctx.getSource().sendFailure(Component.literal(
                        "'" + sound + "' is not one of the profile sounds."));
                return 0;
            }
            // Profiles already using it fall silent rather than being deleted.
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Removed " + sound + ". Profiles using it go silent."), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    /** {@return the sound to store, or null when it is neither registered nor one of Ember's presets};
     * a bare preset arrives as {@code minecraft:click} and is mapped back to the bare word. */
    private static String profileSoundName(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "sound");
        if (BuiltInRegistries.SOUND_EVENT.containsKey(id)) return id.toString();
        if ("minecraft".equals(id.getNamespace()) && ProfileSound.isPreset(id.getPath())) {
            return id.getPath();
        }
        return null;
    }
}
