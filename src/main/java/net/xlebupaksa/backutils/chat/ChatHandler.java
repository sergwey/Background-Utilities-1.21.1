package net.xlebupaksa.backutils.chat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.data.MarkupWrap;
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
 *
 * <p>{@code /me} and {@code /sme} are those two action forms written as commands, and this class is what carries them
 * out: {@link #actionOutcome} is the whole of what may happen to one — a silence of the action kind stopping it, the
 * recording switch deciding whether one allowed to happen is recorded at all — and {@link #recordAction} is the
 * recording of it, whoever cut the body out of what was typed. An action the switch has stopped is neither recorded
 * nor said: nothing is published as ordinary chat, and the actor is told the actions are disabled. Having all of it
 * here is what makes the two ways of writing an action one behaviour rather than two that have to be kept in
 * agreement.
 *
 * <p>{@link #deliver} is the same for a whole line: what a player said becomes what everybody sees in that one method,
 * whoever asked for it to be said — the chat event handing over what was typed, or a command sending a line on the
 * player's behalf — so neither of them can come to say a line by rules the other does not know.
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
     *
     * <p>What the line becomes is {@link #deliver}'s and not this method's: the event's own business is only whether
     * vanilla is left to deliver the line as well. An action is cancelled before it is delivered rather than after —
     * its raw form, markers and all, is exactly what chat must not show, and for a silent one that includes the words
     * nobody else was meant to see — so the cancellation cannot be undone by a delivery that fails part-way. A chat
     * line is cancelled once the server is known to be there, because a message with no server behind it is one
     * vanilla's own pipeline still has to deliver.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings("unused") // called by the event bus
    public void onChat(ServerChatEvent event) {
        try {
            ServerPlayer actor = event.getPlayer();
            String raw = event.getMessage().getString();
            Route route = route(raw);
            boolean action = route == Route.ACTION || route == Route.SILENT_ACTION;

            if (action || actor.getServer() != null) event.setCanceled(true);

            deliver(actor, raw);
        } catch (Exception e) {
            // The event keeps the fate its own form asked for above: an action stays unspoken, where a chat
            // line of a player with no server behind it is still vanilla's to deliver.
            BackUtils.LOGGER.error("Could not format a chat message from {}", event.getUsername(), e);
        }
    }

    // ------------------------------------------------------------------
    // A line a player said
    // ------------------------------------------------------------------

    /**
     * Delivers one line a player said, whichever way it was said, and reports whether it was delivered.
     *
     * <p>This is the one place a line becomes a line. Everything a line goes through is here, once: the marker
     * that picks the channel, the sender's profile format and the server's separator for it, the item triggers
     * — see {@link ShowItem} — the typewriter the profile's sound asks for, the audience the channel reaches,
     * the silencing gates, the refusals a player is told about, and the console record. A second way of saying
     * a line therefore cannot come to have rules of its own: the chat event hands over what was typed — an item
     * trigger included, which is what {@link ShowItem} reads out of the message — and nothing else knows a way in.
     *
     * <p>An action is recorded rather than said, with the one exception that is a state of the log rather than
     * a second route. A player silenced in actions gets nothing through at all — no log, no witnesses, no line,
     * and the notice is the whole of the answer — and that is asked before the log's own state, because a
     * moderation decision must not depend on whether recording happens to be on. While recording is off an action
     * is neither recorded nor said: the words are not published as ordinary chat, which would be a second way of
     * saying a line, and the actor is told the actions are disabled — the same for an ordinary action and a silent
     * one, because neither has anywhere left to go.
     *
     * @return true when the line was delivered as this mod composes it; false when it was refused, having told
     *         the player why, or when there was no server to deliver it on
     */
    public static boolean deliver(ServerPlayer actor, String raw) {
        Route route = route(raw);

        if (route == Route.ACTION || route == Route.SILENT_ACTION) {
            return switch (actionOutcome(silencedInActions(actor),
                    BackUtilsConfig.isActionLoggingEnabled())) {
                case REFUSED -> {
                    notifyActionsMuted(actor);
                    yield false;
                }
                // Nothing is said and nothing is recorded. An action this server is not recording is not an
                // action, and publishing its words as ordinary chat would be a second way of saying a line
                // the player wrote as one — the lines they marked as not for everyone among them.
                case DISABLED -> {
                    notifyActionsDisabled(actor);
                    yield false;
                }
                case RECORDED -> recordAction(actor, stripPrefix(raw, route),
                        route == Route.SILENT_ACTION);
            };
        }

        return sayInChannel(actor, stripPrefix(raw, route), route == Route.GLOBAL_CHAT);
    }

    /**
     * What one action does with a line, given the two things that can stop it reaching the log.
     *
     * <p>Asked as a question with an answer rather than decided where the work is done, because the order
     * of the two is the whole of it and an order is checkable without a game: a silence of the action kind
     * is a moderation state, and it stops the action before anything else — wherever it was written and
     * whatever the recording switch says — while that switch only decides whether an action allowed to
     * happen is recorded at all. Read the other way round, a silenced player's action is answered by the
     * switch's own branch, which is a moderator's decision quietly not applying.
     */
    public enum ActionOutcome {
        /** Silenced in actions: nothing is said, nothing is recorded, and the actor is told why. */
        REFUSED,
        /** The server is not recording actions: nothing is said, nothing is recorded, and the actor is told. */
        DISABLED,
        /** Recorded in the roleplay log, which is what an action is for. */
        RECORDED
    }

    /** {@return what an action does with a line}, given the player's silence and the recording switch */
    public static ActionOutcome actionOutcome(boolean silencedInActions, boolean loggingEnabled) {
        if (silencedInActions) return ActionOutcome.REFUSED;
        return loggingEnabled ? ActionOutcome.RECORDED : ActionOutcome.DISABLED;
    }

    /** {@return true when this player is silenced in actions}, which is the flag that stops one */
    public static boolean silencedInActions(ServerPlayer actor) {
        SilenceState silence = silenceOf(actor);
        return silence != null && silence.silenced(SilenceKind.ACTIONS);
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /**
     * Records an action, whichever way it was written.
     *
     * <p>An asterisk pair in chat and {@code /me} differ in one thing only: how the body is cut out of
     * what the player typed. Everything after that — the silencing gate, the row, the console record,
     * who is sent it and the alert to operators — is here once, so the two forms cannot come to record
     * an action differently, and a change to one is a change to both.
     *
     * @param silent when true the entry is visible only to the player who performed it
     * @return true when the action was recorded, false when the player is silenced for actions
     */
    public static boolean recordAction(ServerPlayer actor, String body, boolean silent) {
        // Asked again here rather than left to the callers: this is the one entry point every way of
        // writing an action reaches, and the gate belongs where the recording happens.
        if (silencedInActions(actor)) {
            notifyActionsMuted(actor);
            return false;
        }

        RoleplayLog.recordAction(actor, ActionText.template(body), silent);
        RoleplayLog.logAction(actor, body, silent);
        return true;
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    /**
     * Says one chat line in the channel it was asked for: the refusals, the wording, the console record and
     * the audience.
     *
     * <p>Nothing here cancels anything, deliberately: this is reached by a line a command sent as well as by
     * one typed, and a command has no event behind it. Whether vanilla is left to deliver the typed line as
     * well is the chat event's own business, decided before this is called; see {@link #deliver}.
     *
     * <p>The channel is asked for the server before anything is said, because a line with no server behind it
     * is one nothing can be delivered on.
     *
     * @return true when the line was delivered, false when the channel refused it, having said why
     */
    private static boolean sayInChannel(ServerPlayer player, String text, boolean global) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;

        SilenceKind kind = global ? SilenceKind.GLOBAL_CHAT : SilenceKind.LOCAL_CHAT;
        String channel = global ? "global" : "local";
        // The word for the channel is localised too, so a refusal reads in the player's own language
        // rather than naming the channel in English inside a translated sentence.
        Component channelName = Component.translatable(
                global ? "backutils.channel.global" : "backutils.channel.local");

        String message = normalise(text);
        if (message.isEmpty()) {
            // The prefix was the whole message: there is nothing to send.
            player.sendSystemMessage(notice(Component.translatable("backutils.chat.nothing")), false);
            return false;
        }

        // Both refusals tell the sender why: a message that vanishes with no explanation reads as a
        // bug rather than as moderation.
        boolean staffOnly = global
                ? BackUtilsConfig.isGlobalChatStaffOnly()
                : BackUtilsConfig.isLocalChatStaffOnly();
        if (staffOnly && !player.hasPermissions(2)) {
            player.sendSystemMessage(notice(Component.translatable("backutils.chat.staff_only",
                    channelName)), false);
            return false;
        }

        SilenceState silence = silenceOf(player);
        if (silence != null && silence.silenced(kind)) {
            player.sendSystemMessage(notice(Component.translatable("backutils.chat.silenced",
                    channelName)), false);
            return false;
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
                BackUtilsConfig.getTypingPitch(),
                player);

        // The console record keeps the real account name and the channel.
        BackUtils.LOGGER.info("[{}] {}: {}", channel, playerName, message);

        for (ServerPlayer recipient : audience(server, player, global)) {
            recipient.sendSystemMessage(component, false);
        }
        return true;
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

    /**
     * {@return this player's silences, or null when they have no row}
     *
     * <p>Public because the private-message gate is a second reader of the same state, and one lookup
     * means one answer to what "silenced" is — including the answer for a player the row does not
     * mention, which is null here and {@link SilenceState#none} at the command that prints one.
     */
    public static SilenceState silenceOf(ServerPlayer player) {
        if (BackUtils.data() == null) return null;
        return BackUtils.data().silences().find(player.getName().getString());
    }

    /** Wraps a notice in the grey brackets the mod's refusals are drawn in. */
    private static Component notice(Component text) {
        return Component.literal("§7[").append(text).append(Component.literal("]"));
    }

    /**
     * Tells an actor that they are silenced in actions, which is why their action did nothing at all.
     *
     * <p>Sent to the actor alone and exactly once per action: it is a fact about their line, not about
     * anyone else's. Public because a silent action's route has nothing else to do — the words must not be
     * said — while every route that does say them reaches the notice through the method below.
     */
    public static void notifyActionsMuted(ServerPlayer actor) {
        actor.sendSystemMessage(notice(Component.translatable("backutils.chat.actions_silenced")),
                false);
    }

    /**
     * Tells an actor that this server is not recording actions, which is why their action did nothing.
     *
     * <p>A wording of its own rather than the one above: the two are different states of the world, and
     * telling a player their actions are silenced when no silence is set reads as a moderation state that
     * cannot be cleared — which is exactly how it was read. An action an operator has silenced is stopped
     * by a decision about the player; an action on a server that is not recording is stopped by a decision
     * about the log, and the player is owed that difference.
     */
    public static void notifyActionsDisabled(ServerPlayer actor) {
        actor.sendSystemMessage(notice(Component.translatable("backutils.chat.actions_disabled")),
                false);
    }

    /**
     * Tells the sender of a private message that their own private messages are silenced.
     *
     * <p>Public because the private-message gate refuses a command rather than a line, and the wording
     * of a silence belongs here with the other two refusals, where the three cannot drift apart. The
     * gate is a command listener and so has no {@link ServerPlayer} of its own to tell: a command can
     * be run from the console, and the source is what answers it either way.
     */
    public static void notifyMessagesMuted(CommandSourceStack source) {
        source.sendSystemMessage(notice(Component.translatable("backutils.chat.messages_silenced")));
    }

    /**
     * Tells the sender of a private message that it is not being delivered, without saying why.
     *
     * <p>Deliberately vague, and deliberately not the wording above: the reason is a moderation state
     * of somebody else's, which this mod does not advertise to another player. What the sender is owed
     * is the knowledge that the message did not arrive, so that it does not read as a bug, and that is
     * the whole of what this says.
     */
    public static void notifyRecipientNotReceiving(CommandSourceStack source) {
        source.sendSystemMessage(
                notice(Component.translatable("backutils.chat.recipient_not_receiving")));
    }

    /**
     * {@return the line with nobody behind it: the message is composed exactly as a player's is, but a word
     * asking for an item is drawn as the word it was, because there is no inventory to read one from}
     *
     * <p>Kept because the composition is worth driving without a server - a profile's format, the line break
     * and the typewriter are all decided here - and because a caller with no player should get a line rather
     * than an exception.
     */
    public static Component composeMessage(String displayedName, String separator, String format,
                                           String playerName, String message, String sound,
                                           boolean typing, double speed, double pitch) {
        return composeMessage(displayedName, separator, format, playerName, message, sound,
                typing, speed, pitch, null);
    }

    /**
     * Builds the line everyone sees.
     *
     * <p>Ember gives every character of a literal containing a typewriter span a place in the reveal, so the name and
     * separator sit in a markup-free literal of their own and the effect and its sound stay confined to the message; the
     * tag and the text it wraps stay one literal, which Ember needs to apply it at all. The sound the profile stored is a
     * name in the server's palette rather than an id Ember would know, so the palette goes with it and
     * {@link ProfileSound#wrap} does the looking up.
     *
     * <p>A {@code \n} written in the profile's own text becomes a real line break here, the way one typed into a message
     * already does in {@link #normalise}: vanilla's chat splits a message into as many lines as it has breaks, so a
     * profile's text needs no renderer of this mod's to break a line. It is done last, after the typewriter tag is
     * wrapped around the body, so a tag that spans a break is reopened on the row below rather than left open across it.
     *
     * <p>A message asking for an item - {@code [item]}, {@code [i]}, {@code [offhand]} and the rest - is what
     * {@link ShowItem} answers: its words are marked before the format is applied and resolved in the finished body,
     * because this method is the last thing to rewrite the line and so the first thing to know what is holding a tag
     * open by the time the item goes in. The marking is on the message and not on the body, so a format's own text is
     * never read as a request for an item.
     */
    public static Component composeMessage(String displayedName, String separator, String format,
                                           String playerName, String message, String sound,
                                           boolean typing, double speed, double pitch,
                                           ServerPlayer player) {
        String body = chatFormat(format, playerName, ShowItem.mark(message));
        // Gated on the same switch as the log's typing effect, and the volume is ProfileSound's, not the profile's.
        if (typing) {
            body = ProfileSound.wrap(body, sound, speed, pitch, BackUtilsConfig.getProfileSounds());
        }
        body = MarkupWrap.withLineBreaks(body);
        // The prefix is guarded the way the words beside it are: a format of nothing but tags wraps no character,
        // and a literal that parses to no styled span is drawn as the characters of its tags.
        String prefix = displayedName + separator;
        return Component.literal(MarkupUtil.drawsNothing(prefix) ? "" : prefix)
                .append(ShowItem.compose(body, player));
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
