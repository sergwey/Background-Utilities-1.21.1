package net.xlebupaksa.backutils.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.data.ModAttachments;
import net.xlebupaksa.backutils.data.ProfileSnapshot;
import net.xlebupaksa.backutils.data.SilenceKind;
import net.xlebupaksa.backutils.data.SilenceState;
import net.xlebupaksa.backutils.log.ActionText;
import net.xlebupaksa.backutils.log.RoleplayLog;
import net.xlebupaksa.backutils.profile.ProfileSound;
import net.xlebupaksa.backutils.profile.ProfileText;

import java.util.ArrayList;
import java.util.List;

/**
 * Routes what a player types: {@code !text} to global chat, {@code *text*} to an action in the roleplay log for everyone
 * nearby, {@code ^text^} to a silent action seen only by the player, and anything else to local chat.
 *
 * <p>The prefix is removed before the chat format is applied, so {@code {m}} never sees a channel marker; a lone
 * {@code !} or {@code ^} stays ordinary chat.
 */
public class ChatHandler {

    /** Prefixes are single characters so they cannot collide with each other. */
    private static final String GLOBAL_PREFIX = "!";

    /** What a raw chat message turns into. */
    public enum Route { GLOBAL_CHAT, ACTION, SILENT_ACTION, LOCAL_CHAT }

    /**
     * Classifies a raw message: the action forms are checked before the global prefix, and every form needs content after it.
     */
    public static Route route(String raw) {
        if (raw == null || raw.isBlank()) return Route.LOCAL_CHAT;

        if (ActionText.stripCarets(raw) != null) return Route.SILENT_ACTION;
        if (ActionText.stripAsterisks(raw) != null) return Route.ACTION;

        if (raw.trim().startsWith(GLOBAL_PREFIX)) {
            String body = raw.trim().substring(GLOBAL_PREFIX.length());
            if (!body.isBlank()) return Route.GLOBAL_CHAT;
        }
        return Route.LOCAL_CHAT;
    }

    /**
     * {@return the text the chat format should see, with the channel marker removed}
     *
     * <p>The marker is gone before {@code {m}} is substituted, so no format is handed a stray marker.
     */
    public static String stripPrefix(String raw, Route route) {
        if (raw == null) return "";
        String trimmed = raw.trim();

        return switch (route) {
            case GLOBAL_CHAT -> trimmed.substring(GLOBAL_PREFIX.length());
            case ACTION, SILENT_ACTION -> trimmed.substring(1, trimmed.length() - 1).trim();
            case LOCAL_CHAT -> raw;
        };
    }

    /**
     * Runs last, deliberately: every other chat handler has had its say, so filters which rewrite chat — Ember's Text API
     * markup policy in particular — have already been applied. Text reaching a {@code Component.literal} is parsed as
     * markup on the client.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused") // called by the event bus
    public void onChat(ServerChatEvent event) {
        try {
            ServerPlayer actor = event.getPlayer();
            String raw = event.getMessage().getString();
            Route route = route(raw);

            if (route == Route.ACTION || route == Route.SILENT_ACTION) {
                if (!BackUtilsConfig.isActionLoggingEnabled()) {
                    // Actions are switched off, so the line is ordinary chat with the prefixes left in.
                    chat(event, actor, raw, false);
                    return;
                }
                act(event, actor, stripPrefix(raw, route), route == Route.SILENT_ACTION);
                return;
            }

            chat(event, actor, stripPrefix(raw, route), route == Route.GLOBAL_CHAT);
        } catch (Exception e) {
            // The event is left uncancelled, so vanilla's own pipeline still delivers the message.
            BackUtils.LOGGER.error("Could not format a chat message from {}", event.getUsername(), e);
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /**
     * Records an action in the roleplay log; nothing is posted to chat in either form.
     *
     * @param silent when true the entry is visible only to the player who performed it
     */
    private void act(ServerChatEvent event, ServerPlayer actor, String body, boolean silent) {
        // The raw form must never reach chat, whoever is silenced.
        event.setCanceled(true);

        SilenceState silence = silenceOf(actor);
        if (silence != null && silence.silenced(SilenceKind.ACTIONS)) {
            actor.sendSystemMessage(notice("Your actions are silenced."), false);
            return;
        }

        RoleplayLog.recordAction(actor, ActionText.template(body), silent);
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    private void chat(ServerChatEvent event, ServerPlayer player, String text, boolean global) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        SilenceKind kind = global ? SilenceKind.GLOBAL_CHAT : SilenceKind.LOCAL_CHAT;
        String channel = global ? "global" : "local";

        String message = normalise(text);
        if (message.isEmpty()) {
            // The prefix was the whole message: there is nothing to send.
            event.setCanceled(true);
            player.sendSystemMessage(notice("Nothing to say."), false);
            return;
        }

        // Both refusals tell the sender why: a message that vanishes with no explanation reads as a
        // bug rather than as moderation.
        boolean staffOnly = global
                ? BackUtilsConfig.isGlobalChatStaffOnly()
                : BackUtilsConfig.isLocalChatStaffOnly();
        if (staffOnly && !player.hasPermissions(2)) {
            event.setCanceled(true);
            player.sendSystemMessage(notice(
                    "Only staff can use " + channel + " chat right now."), false);
            return;
        }

        SilenceState silence = silenceOf(player);
        if (silence != null && silence.silenced(kind)) {
            event.setCanceled(true);
            player.sendSystemMessage(notice(
                    "You are silenced in " + channel + " chat."), false);
            return;
        }

        // The attachment is registered with a default, so it never answers null.
        ProfileSnapshot snapshot = player.getData(ModAttachments.PROFILE.get());

        String playerName = player.getName().getString();

        Component component = composeMessage(
                ProfileText.resolve(snapshot.displayedName(), playerName),
                BackUtilsConfig.separatorFor(global),
                snapshot.chatFormat(),
                playerName,
                message,
                snapshot.chatSound(),
                BackUtilsConfig.isTypingEnabled(),
                BackUtilsConfig.getTypingSpeed(),
                BackUtilsConfig.getTypingPitch());

        // The console record keeps the real account name and the channel.
        BackUtils.LOGGER.info("[{}] {}: {}", channel, playerName, message);

        for (ServerPlayer recipient : audience(server, player, global)) {
            recipient.sendSystemMessage(component, false);
        }

        event.setCanceled(true);
    }

    /**
     * {@return everyone who should receive this message: the sender always, and for local chat the players in the same
     * dimension within the configured radius.
     */
    private static List<ServerPlayer> audience(MinecraftServer server, ServerPlayer sender,
                                               boolean global) {
        List<ServerPlayer> list = server.getPlayerList().getPlayers();
        if (global) return list;

        double radius = BackUtilsConfig.getLocalChatRadius();
        double limit = radius * radius;

        List<ServerPlayer> nearby = new ArrayList<>();
        for (ServerPlayer candidate : list) {
            // The sender always hears their own line.
            if (candidate == sender || (sameLevel(candidate, sender)
                    && candidate.distanceToSqr(sender) <= limit)) {
                nearby.add(candidate);
            }
        }
        return nearby;
    }

    /**
     * {@return true when both players are in the same level}
     *
     * <p>Levels are {@link AutoCloseable} in NeoForge, so an IDE reads {@code level()} as a resource opened and never
     * closed; it belongs to the server and is borrowed here for one comparison.
     */
    @SuppressWarnings("resource")
    private static boolean sameLevel(ServerPlayer a, ServerPlayer b) {
        return a.level() == b.level();
    }

    private static SilenceState silenceOf(ServerPlayer player) {
        if (BackUtils.data() == null) return null;
        return BackUtils.data().silences().find(player.getName().getString());
    }

    private static Component notice(String text) {
        return Component.literal("§7[" + text + "]");
    }

    /**
     * Builds the line everyone sees.
     *
     * <p>Ember gives every character of a literal containing a typewriter span a place in the reveal, so the name and
     * separator sit in a markup-free literal of their own and the effect and its sound stay confined to the message; the
     * tag and the text it wraps stay one literal, which Ember needs to apply it at all.
     */
    public static Component composeMessage(String displayedName, String separator, String format,
                                           String playerName, String message, String sound,
                                           boolean typing, double speed, double pitch) {
        String body = chatFormat(format, playerName, message);
        // Gated on the same switch as the log's typing effect, and the volume is ProfileSound's, not the profile's.
        if (typing) body = ProfileSound.wrap(body, sound, speed, pitch);
        return Component.literal(displayedName + separator).append(Component.literal(body));
    }

    /**
     * Substitutes the placeholders in a chat format. {@code {m}} may appear more than once; a format without it predates
     * validation, so the message is appended rather than swallowed.
     */
    private static String chatFormat(String format, String playerName, String message) {
        String resolved = ProfileText.resolve(format, playerName);
        return resolved.contains(ProfileText.MESSAGE)
                ? resolved.replace(ProfileText.MESSAGE, message)
                : resolved + message;
    }

    /**
     * Turns the literal {@code \n} players type into real line breaks, and drops trailing blank lines.
     */
    private static String normalise(String raw) {
        String text = raw.replace("\\n", "\n");
        while (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
