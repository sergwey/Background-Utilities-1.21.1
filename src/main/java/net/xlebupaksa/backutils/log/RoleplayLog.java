package net.xlebupaksa.backutils.log;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.data.ActionLogEntry;
import net.xlebupaksa.backutils.data.LogData;
import net.xlebupaksa.backutils.data.ProfileText;
import net.xlebupaksa.backutils.network.AdminAlerts;
import net.xlebupaksa.backutils.network.RoleplayLogBroadcaster;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Records roleplay actions and works out who gets to see them.
 *
 * <p>An entry is visible only to the players in its access list, a list of UUIDs stored with the row, and its
 * {@code contents} hold a {@code {player}} placeholder rather than a baked-in name, because each viewer may render that
 * name differently. The manual {@code /log} command is the exception: it stores finished prose, promotes the executor's
 * name into {@code actor_name} for administration, and leaves their UUID out of the access list.
 */
public final class RoleplayLog {

    /** {@code {player}} as stored in the log, before per-viewer substitution. */
    private static final String PLACEHOLDER = ProfileText.PLAYER_PLACEHOLDER;

    private RoleplayLog() {}

    public static void recordAction(ServerPlayer actor, String template) {
        recordAction(actor, template, false);
    }

    /**
     * Records an action, optionally as a silent one: written to the log like any other, but sent to the actor alone.
     *
     * @param template the text, containing {@code {player}} where their name belongs
     * @param silent   when true, only the actor sees it
     */
    public static void recordAction(ServerPlayer actor, String template, boolean silent) {
        LogData log = log();
        if (log == null) return;
        if (template == null || template.isBlank()) return;

        MinecraftServer server = actor.getServer();
        if (server == null) return;

        List<UUID> audience = silent
                ? List.of(actor.getUUID())
                : nearbyAndSelf(server, actor);
        long id = log.record(actor.getUUID(), actor.getName().getString(), template, audience,
                actor.level().dimension().location().toString(),
                actor.getX(), actor.getY(), actor.getZ());

        // The actor is in the audience, so the entry reaches them too and comes out underlined.
        RoleplayLogBroadcaster.deliver(server, id, audience);

        // Operators hear about every action, including ones they were nowhere near, but as a nudge rather than the line
        // itself: otherwise every action in the world would be typed across their screen.
        AdminAlerts.notifyOperators(server, id, actor.getName().getString());
    }

    /**
     * Records an announcement made by staff, who choose the audience with a selector.
     *
     * @param template the finished text, shown to everyone as-is
     */
    public static void recordAnnouncement(ServerPlayer source, String template, List<ServerPlayer> audience) {
        LogData log = log();
        if (log == null || audience.isEmpty()) return;

        MinecraftServer server = source.getServer();
        if (server == null) return;

        List<UUID> recipients = new ArrayList<>(audience.size());
        for (ServerPlayer player : audience) recipients.add(player.getUUID());

        long id = log.record(source.getUUID(), source.getName().getString(), template, recipients,
                // The announcer's own position, not the recipients': the only position this entry has.
                source.level().dimension().location().toString(),
                source.getX(), source.getY(), source.getZ());
        RoleplayLogBroadcaster.deliver(server, id, recipients);
    }

    /** {@return the acting player plus everyone within the configured radius} */
    private static List<UUID> nearbyAndSelf(MinecraftServer server, ServerPlayer actor) {
        double radius = BackUtilsConfig.getLogRadius();
        double radiusSquared = radius * radius;

        List<UUID> audience = new ArrayList<>();
        audience.add(actor.getUUID());

        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer other : level.players()) {
                if (other == actor) continue;
                // Same dimension only: coordinates are comparable, proximity is not.
                if (other.level() != actor.level()) continue;
                if (other.distanceToSqr(actor) <= radiusSquared) audience.add(other.getUUID());
            }
        }
        return audience;
    }

    private static LogData log() {
        var data = BackUtils.data();
        return data == null ? null : data.logs();
    }

    public static String placeholder() {
        return PLACEHOLDER;
    }

    /**
     * Wraps a finished line in Ember's typewriter effect, so it is revealed as if being typed.
     *
     * <p>The tag is {@code typewriter} (or {@code type}); Ember knows no {@code typing} tag, and an unknown tag yields no
     * formatting, so the client renders the raw text. {@code repeat=once} matters because Ember's default, {@code infinite},
     * would retype every line forever. Only broadcast lines are wrapped, so opening the menu does not retype the backlog.
     * The numbers use {@link Locale#ROOT} because a decimal-comma locale would produce {@code volume=0,5}, which the parser
     * reads as one unparseable token.
     */
    public static String present(String text) {
        if (!BackUtilsConfig.isTypingEnabled()) return text;

        return "<typewriter sound=" + BackUtilsConfig.getTypingSound()
                + " volume=" + number(BackUtilsConfig.getTypingVolume())
                + " pitch=" + number(BackUtilsConfig.getTypingPitch())
                + " speed=" + number(BackUtilsConfig.getTypingSpeed())
                + " repeat=once>"
                + text
                + "</typewriter>";
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
