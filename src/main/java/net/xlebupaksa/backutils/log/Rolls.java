package net.xlebupaksa.backutils.log;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.data.ActionLogEntry;
import net.xlebupaksa.backutils.data.BackUtilsData;
import net.xlebupaksa.backutils.data.LogData;
import net.xlebupaksa.backutils.network.RoleplayLogBroadcaster;
import net.xlebupaksa.backutils.network.RoleplayLogNetwork;
import net.xlebupaksa.backutils.network.RoleplayLogPayload;
import net.xlebupaksa.backutils.network.RoleplayLogRemovePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The dice: what {@code /roll} and {@code /hroll} leave in the roleplay log.
 *
 * <p>The line is composed from the server's own format, and the two name placeholders are left in
 * it rather than filled in here, so every reader sees the roller's roleplay name and the roller
 * sees themselves underlined, exactly as an action does.
 *
 * <p>A hidden roll writes nothing when it is rolled. It waits in {@code pending_hidden_roll} until
 * the configured delay is up, then enters the log as the line its witnesses remember; the roller
 * alone gets an early, private copy with the result in it. That is what keeps the result from
 * reaching a player through the history or the ten-second catch-up sweep, without ever showing
 * anybody an obfuscated line.
 */
public final class Rolls {

    /** The placeholders a roll line may carry, besides the two the log fills in. */
    public static final String RESULT = "{result}";
    public static final String MAX_ROLL = "{max_roll}";
    public static final String REASON = "{reason}";
    /** The finished line, for the wording the extremes wrap around it. */
    public static final String LINE = "{line}";

    /** Which end of the range a result landed on, if either. */
    public enum Extreme { NONE, LOW, HIGH }

    /**
     * The administrator's private echo ids. Negative, so a transient line cannot collide with a log
     * row the client may already hold. Random rather than counted: a waiting roll keeps its own id
     * across a restart, and a counter starting again at -1 could take a new line off instead.
     */
    private static long nextEchoId() {
        return -ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    private Rolls() {}
    /**
     * Rolls once for each subject, or once naming nobody when the list is empty.
     *
     * @return the numbers rolled, in the order they were recorded, and empty when there is no
     *         database to write to
     */
    public static List<Integer> execute(ServerPlayer roller, List<ServerPlayer> subjects,
                                        int max, String reason, boolean hidden) {
        BackUtilsData data = BackUtils.data();
        MinecraftServer server = roller.getServer();
        if (data == null || server == null) return List.of();

        if (hidden) return hold(roller, subjects, max, reason);

        if (subjects.isEmpty()) {
            Integer rolled = record(roller, null, roller, max, reason);
            return rolled == null ? List.of() : List.of(rolled);
        }

        List<Integer> results = new ArrayList<>(subjects.size());
        for (ServerPlayer subject : subjects) {
            Integer rolled = record(subject, subject, roller, max, reason);
            if (rolled != null) results.add(rolled);
        }
        return List.copyOf(results);
    }

    /**
     * Holds one hidden roll per subject until its witnesses remember it, and gives the roller a
     * private copy now.
     *
     * <p>The roller is the anchor, as the one who called for the roll: the line is remembered by the
     * players around them, and by the player it names wherever that player is. Nothing is written to
     * the log until the moment is up, because any row written now would be readable through the
     * history and the catch-up sweep before the delay had passed.
     */
    private static List<Integer> hold(ServerPlayer roller, List<ServerPlayer> subjects,
                                      int max, String reason) {
        BackUtilsData data = BackUtils.data();
        MinecraftServer server = roller.getServer();
        if (data == null || server == null) return List.of();

        long revealAt = System.currentTimeMillis()
                + Math.max(0, BackUtilsConfig.getHiddenRollDelay()) * 1000L;
        List<UUID> audience = RoleplayLog.witnesses(roller, BackUtilsConfig.getRollRadius());
        String dimension = roller.level().dimension().location().toString();

        List<Integer> results = new ArrayList<>(Math.max(1, subjects.size()));
        if (subjects.isEmpty()) {
            int result = roll(roller, max);
            long echoId = nextEchoId();
            long pending = data.logs().addPendingRoll(null, "",
                    dressHidden(remembered(false, result, max, reason), result, max),
                    audience, revealAt, dimension,
                    roller.getX(), roller.getY(), roller.getZ(), result, max,
                    roller.getUUID(), echoId);
            if (pending <= 0) return List.of();
            BackUtils.LOGGER.info("[hidden roll] {} rolled {}/{}{}",
                    roller.getName().getString() + " (nobody named)", result, max,
                    reason == null || reason.isBlank() ? "" : " for " + reason);
            echo(roller, null, result, max, reason, echoId);
            results.add(result);
            return List.copyOf(results);
        }

        for (ServerPlayer subject : subjects) {
            int result = roll(subject, max);
            List<UUID> seen = new ArrayList<>(audience);
            if (!seen.contains(subject.getUUID())) seen.add(subject.getUUID());

            long echoId = nextEchoId();
            long pending = data.logs().addPendingRoll(subject.getUUID(),
                    subject.getName().getString(),
                    dressHidden(remembered(true, result, max, reason), result, max), seen,
                    revealAt, dimension, roller.getX(), roller.getY(), roller.getZ(), result, max,
                    roller.getUUID(), echoId);
            if (pending <= 0) continue;

            BackUtils.LOGGER.info("[hidden roll] {} rolled {}/{}{}",
                    subject.getName().getString(), result, max,
                    reason == null || reason.isBlank() ? "" : " for " + reason);
            echo(roller, subject, result, max, reason, echoId);
            results.add(result);
        }
        return List.copyOf(results);
    }

    /** {@return the line a witness will remember, worded by the hidden roll's own formats} */
    public static String remembered(boolean named, int result, int max, String reason) {
        boolean reasoned = reason != null && !reason.isBlank();
        String format;
        if (named) {
            format = reasoned ? BackUtilsConfig.getHiddenRollFormat()
                    : BackUtilsConfig.getHiddenRollFormatNoReason();
        } else {
            format = reasoned ? BackUtilsConfig.getHiddenRollFormatAnonymous()
                    : BackUtilsConfig.getHiddenRollFormatAnonymousNoReason();
        }
        return fill(format, result, max, reason);
    }

    /**
     * {@return the remembered line wrapped in the treatment its extreme earns}
     *
     * <p>The hidden roll has its own two treatments, so an operator can colour the remembered line
     * differently from the public one, or clear them for none at all.
     */
    public static String dressHidden(String line, int result, int max) {
        String format = switch (extreme(result, max)) {
            case LOW -> BackUtilsConfig.getHiddenRollMinFormat();
            case HIGH -> BackUtilsConfig.getHiddenRollMaxFormat();
            case NONE -> "";
        };
        return format == null || format.isBlank() ? line : format.replace(LINE, line).trim();
    }

    /** {@return the sound a remembered hidden roll plays, chosen by the result's end} */
    private static String hiddenSoundFor(int result, int max) {
        return switch (extreme(result, max)) {
            case LOW -> BackUtilsConfig.getHiddenRollMinSound();
            case HIGH -> BackUtilsConfig.getHiddenRollMaxSound();
            case NONE -> BackUtilsConfig.getHiddenRollSound();
        };
    }

    /**
     * Sends the roller the only early copy of the result: their own log's line, with the real number
     * and in gray so it reads as a private copy. It is not stored, and it makes no sound.
     */
    private static void echo(ServerPlayer roller, ServerPlayer subject, int result, int max,
                             String reason, long echoId) {
        if (!RoleplayLogNetwork.canReceive(roller)) return;

        String line = line(subject != null, reason, result, max);
        // The stored line is resolved per reader when it is sent from the log; this one is not a row,
        // so the name placeholders have to be filled here or the administrator reads "{player}".
        String shown = ActionLogEntry.resolve(line,
                subject == null ? "" : subject.getName().getString());
        // Gray because it is the operator's private copy, not a line the players were given; the
        // number itself is in the text, where the administrator asked to read it.
        String text = RoleplayLog.present("<color col=#8a8a8a>" + shown + "</color>");
        PacketDistributor.sendToPlayer(roller, new RoleplayLogPayload(
                echoId, text, "", 0.0f, 1.0f, true));
    }

    /** Reveals every hidden roll whose moment has come, on the server thread. */
    public static void revealDue(MinecraftServer server) {
        BackUtilsData data = BackUtils.data();
        if (data == null) return;

        for (LogData.PendingRoll pending : data.logs().duePendingRolls(System.currentTimeMillis())) {
            long id = data.logs().revealPendingRoll(pending);
            if (id <= 0) continue;

            ServerPlayer roller = pending.rollerId() == null ? null
                    : server.getPlayerList().getPlayer(pending.rollerId());
            if (roller != null && pending.echoId() != 0
                    && RoleplayLogNetwork.canReceiveRemove(roller)) {
                // The roller already has the result in their private line; replace it with the
                // remembered one instead of leaving both copies on their screen.
                PacketDistributor.sendToPlayer(roller,
                        new RoleplayLogRemovePayload(pending.echoId()));
            }

            RoleplayLogBroadcaster.deliver(server, id, pending.accessList(),
                    hiddenSoundFor(pending.result(), pending.maxRoll()),
                    (float) BackUtilsConfig.getRollSoundVolume(),
                    (float) BackUtilsConfig.getRollSoundPitch(), true);
        }
    }

    /**
     * Records one roll and sends it out.
     *
     * @param anchor   where the roll happens and who witnesses it: the player being rolled for, or
     *                 the one who ran the command when nobody is named
     * @param subject  who the line names, or null for a line that names nobody
     * @param roller   the one who asked for it, who reads and hears it wherever they are
     */
    private static Integer record(ServerPlayer anchor, ServerPlayer subject, ServerPlayer roller,
                                  int max, String reason) {
        MinecraftServer server = anchor.getServer();
        BackUtilsData data = BackUtils.data();
        if (server == null || data == null) return null;

        int result = roll(anchor, max);
        String who = subject == null ? "" : subject.getName().getString();

        List<UUID> audience = RoleplayLog.witnesses(anchor, BackUtilsConfig.getRollRadius());
        // The one who rolled reads it too, even from across the map: they asked for it, and nothing
        // is said in chat.
        if (!audience.contains(roller.getUUID())) audience.add(roller.getUUID());

        long id = data.logs().record(
                subject == null ? null : subject.getUUID(), who,
                dress(line(subject != null, reason, result, max), result, max),
                audience,
                anchor.level().dimension().location().toString(),
                anchor.getX(), anchor.getY(), anchor.getZ(), "");
        RoleplayLogBroadcaster.deliver(server, id, audience, soundFor(result, max),
                (float) BackUtilsConfig.getRollSoundVolume(),
                (float) BackUtilsConfig.getRollSoundPitch(), true);

        BackUtils.LOGGER.info("[roll] {} rolled {}/{}{}",
                subject == null ? anchor.getName().getString() + " (nobody named)" : who,
                result, max, reason == null || reason.isBlank() ? "" : " for " + reason);
        return result;
    }

    /** {@return which end of the range a result landed on; a one-sided roll has no extremes} */
    public static Extreme extreme(int result, int max) {
        if (max <= 1) return Extreme.NONE;
        if (result <= 1) return Extreme.LOW;
        if (result >= max) return Extreme.HIGH;
        return Extreme.NONE;
    }

    /** {@return the wording the config gives that end, or empty for the ordinary line} */
    public static String dressFormat(int result, int max) {
        return switch (extreme(result, max)) {
            case LOW -> BackUtilsConfig.getRollMinFormat();
            case HIGH -> BackUtilsConfig.getRollMaxFormat();
            case NONE -> "";
        };
    }

    /**
     * {@return the line as the players read it, wrapped in the treatment its result earns}
     *
     * <p>The treatment wraps the finished line rather than replacing it, so how a line is worded —
     * with or without a reason — stays decided in one place.
     */
    public static String dress(String line, int result, int max) {
        String format = dressFormat(result, max);
        return format == null || format.isBlank() ? line : format.replace(LINE, line).trim();
    }

    /** {@return the sound this result plays: the one for its end, or the ordinary roll sound} */
    public static String soundFor(int result, int max) {
        return switch (extreme(result, max)) {
            case LOW -> BackUtilsConfig.getRollMinSound();
            case HIGH -> BackUtilsConfig.getRollMaxSound();
            case NONE -> BackUtilsConfig.getRollSound();
        };
    }

    /** {@return a result from one to {@code max}, both ends included} */
    public static int roll(ServerPlayer anchor, int max) {
        return anchor.level().getRandom().nextInt(Math.max(1, max)) + 1;
    }

    /**
     * {@return the line the log stores, worded for the shape of roll it was}
     *
     * <p>Four wordings: a roll that names somebody and a roll that names nobody each have one with
     * a reason and one without. The actorless pair exists because the named line is a sentence
     * about whoever was rolled for, and with nobody named there is no subject left to read it as.
     *
     * @param named whether the roll names a player, which is what decides the pair of formats
     */
    public static String line(boolean named, String reason, int result, int max) {
        boolean reasoned = reason != null && !reason.isBlank();
        String format;
        if (named) {
            format = reasoned ? BackUtilsConfig.getRollFormat()
                    : BackUtilsConfig.getRollFormatNoReason();
        } else {
            format = reasoned ? BackUtilsConfig.getRollFormatAnonymous()
                    : BackUtilsConfig.getRollFormatAnonymousNoReason();
        }
        return fill(format, result, max, reason);
    }

    /**
     * {@return the format with the roll filled into it, and the names left for the log}
     *
     * <p>Runs of spaces are collapsed and the ends trimmed: a format written around a placeholder
     * that turned out to be empty would otherwise leave a gap in the middle of the line.
     */
    public static String fill(String format, int result, int max, String reason) {
        return fillWith(format, max, reason, String.valueOf(result));
    }

    /**
     * The same, with the text that stands in for the result given rather than read from the config:
     * the settings tab previews a format with the sample result.
     */
    public static String fillWith(String format, int max, String reason, String resultText) {
        String text = format == null ? "" : format;
        text = text.replace(RESULT, resultText == null ? "" : resultText);
        text = text.replace(MAX_ROLL, String.valueOf(max));
        text = text.replace(REASON, reason == null ? "" : reason.trim());
        return text.replaceAll("[ \\t]{2,}", " ").trim();
    }

    // ------------------------------------------------------------------
    // Previews
    // ------------------------------------------------------------------

    /** The samples a format is previewed with in the settings tab. */
    private static final String SAMPLE_PLAYER = "Dev";
    private static final int SAMPLE_RESULT = 42;
    private static final int SAMPLE_MAX = 100;
    private static final String SAMPLE_REASON = "a spot check";

    /** {@return true when this setting's value is one of the roll's own formats} */
    public static boolean isFormat(String key) {
        return switch (key) {
            case "rollFormat", "rollFormatNoReason", "rollFormatAnonymous",
                 "rollFormatAnonymousNoReason", "rollMinFormat", "rollMaxFormat",
                 "hiddenRollFormat", "hiddenRollFormatNoReason", "hiddenRollFormatAnonymous",
                 "hiddenRollFormatAnonymousNoReason", "hiddenRollMinFormat",
                 "hiddenRollMaxFormat" -> true;
            default -> false;
        };
    }

    /**
     * {@return what a format looks like with the samples filled in, for the settings tab}
     *
     * <p>Rendered through the same substitution the log renders with, names included, so a preview
     * cannot show a line the log would not. The two extremes are shown wrapped around the ordinary
     * line, because that is what they wrap when a roll lands on one.
     *
     * @return the line to preview, or empty when this key is not one of the formats
     */
    public static String preview(String key, String format) {
        if (format == null) return "";

        String text = switch (key) {
            case "rollFormat" -> fill(format, SAMPLE_RESULT, SAMPLE_MAX, SAMPLE_REASON);
            case "rollFormatNoReason" -> fill(format, SAMPLE_RESULT, SAMPLE_MAX, null);
            case "rollFormatAnonymous" -> fill(format, SAMPLE_RESULT, SAMPLE_MAX, SAMPLE_REASON);
            case "rollFormatAnonymousNoReason" -> fill(format, SAMPLE_RESULT, SAMPLE_MAX, null);
            case "hiddenRollFormat" -> fill(format, SAMPLE_RESULT, SAMPLE_MAX, SAMPLE_REASON);
            case "hiddenRollFormatNoReason" -> fill(format, SAMPLE_RESULT, SAMPLE_MAX, null);
            case "hiddenRollFormatAnonymous" ->
                    fill(format, SAMPLE_RESULT, SAMPLE_MAX, SAMPLE_REASON);
            case "hiddenRollFormatAnonymousNoReason" ->
                    fill(format, SAMPLE_RESULT, SAMPLE_MAX, null);
            case "rollMinFormat", "rollMaxFormat" -> format.replace(LINE, ordinarySample());
            case "hiddenRollMinFormat", "hiddenRollMaxFormat" ->
                    format.replace(LINE, hiddenOrdinarySample());
            default -> "";
        };
        return ActionLogEntry.resolve(text, SAMPLE_PLAYER);
    }

    /** {@return the sample line the extremes wrap, already named} */
    private static String ordinarySample() {
        return ActionLogEntry.resolve(
                fill(BackUtilsConfig.getRollFormatNoReason(), SAMPLE_RESULT, SAMPLE_MAX, null),
                SAMPLE_PLAYER);
    }

    /** {@return the remembered sample the hidden extremes wrap, already named} */
    private static String hiddenOrdinarySample() {
        return ActionLogEntry.resolve(
                fill(BackUtilsConfig.getHiddenRollFormatNoReason(), SAMPLE_RESULT, SAMPLE_MAX, null),
                SAMPLE_PLAYER);
    }
}
