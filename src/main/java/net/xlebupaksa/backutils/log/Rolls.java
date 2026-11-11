package net.xlebupaksa.backutils.log;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.data.ActionLogEntry;
import net.xlebupaksa.backutils.data.BackUtilsData;
import net.xlebupaksa.backutils.data.LogData;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.network.RoleplayLogBroadcaster;
import net.xlebupaksa.backutils.network.RoleplayLogNetwork;
import net.xlebupaksa.backutils.network.RoleplayLogPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The dice: what {@code /roll} and {@code /hroll} leave in the roleplay log.
 *
 * <p>The line is composed from the server's own format, and the two name placeholders are left in
 * it rather than filled in here, so every reader sees the roller's roleplay name and the roller
 * sees themselves underlined, exactly as an action does.
 *
 * <p>A hidden roll writes nothing when it is rolled. It waits in {@code pending_hidden_roll} until
 * the configured delay is up, then enters the log as the line its witnesses remember; the roller
 * alone gets an early, private copy with the number in the administrator's note. That is what keeps
 * the result from reaching a player through the history or the ten-second catch-up sweep.
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
     * What the administrator's private copy shows where the result is: the public line never carries
     * the number before its moment, and this is only ever sent to the one who ran the command.
     */
    private static final String HIDDEN_RESULT = "<obfuscate>???</obfuscate>";

    /** What a witness remembers when a hidden roll's time is up. */
    private static final String REMEMBERED_NAMED =
            "You remembered that {player_possessive} dice showed {result}/{max_roll}";
    private static final String REMEMBERED_ANONYMOUS =
            "You remembered that the dice showed {result}/{max_roll}";
    private static final String REMEMBERED_NAMED_RU =
            "Вы вспомнили, что кубик {player} показал {result}/{max_roll}";
    private static final String REMEMBERED_ANONYMOUS_RU =
            "Вы вспомнили, что на кубике выпало {result}/{max_roll}";

    /**
     * The administrator's private echo ids. Negative, so a transient line cannot collide with a log
     * row the client may already hold, and unique for the session, which is what the overlay dedupes on.
     */
    private static final AtomicLong ECHO_ID = new AtomicLong(-1L);

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
            Integer rolled = record(roller, null, roller, max, reason, false);
            return rolled == null ? List.of() : List.of(rolled);
        }

        List<Integer> results = new ArrayList<>(subjects.size());
        for (ServerPlayer subject : subjects) {
            Integer rolled = record(subject, subject, roller, max, reason, false);
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
            long pending = data.logs().addPendingRoll(null, "",
                    remembered(false, result, max, reason), audience, revealAt, dimension,
                    roller.getX(), roller.getY(), roller.getZ(), result, max);
            if (pending <= 0) return List.of();
            BackUtils.LOGGER.info("[hidden roll] {} rolled {}/{}{}",
                    roller.getName().getString() + " (nobody named)", result, max,
                    reason == null || reason.isBlank() ? "" : " for " + reason);
            echo(roller, null, result, max, reason);
            results.add(result);
            return List.copyOf(results);
        }

        for (ServerPlayer subject : subjects) {
            int result = roll(subject, max);
            List<UUID> seen = new ArrayList<>(audience);
            if (!seen.contains(subject.getUUID())) seen.add(subject.getUUID());

            long pending = data.logs().addPendingRoll(subject.getUUID(),
                    subject.getName().getString(), remembered(true, result, max, reason), seen,
                    revealAt, dimension, roller.getX(), roller.getY(), roller.getZ(), result, max);
            if (pending <= 0) continue;

            BackUtils.LOGGER.info("[hidden roll] {} rolled {}/{}{}",
                    subject.getName().getString(), result, max,
                    reason == null || reason.isBlank() ? "" : " for " + reason);
            echo(roller, subject, result, max, reason);
            results.add(result);
        }
        return List.copyOf(results);
    }

    /** {@return the line a witness will remember, worded in the server's language} */
    public static String remembered(boolean named, int result, int max, String reason) {
        boolean russian = BackUtilsConfig.russian();
        String template = named
                ? (russian ? REMEMBERED_NAMED_RU : REMEMBERED_NAMED)
                : (russian ? REMEMBERED_ANONYMOUS_RU : REMEMBERED_ANONYMOUS);
        String text = fillWith(template, max, "", String.valueOf(result));
        if (reason != null && !reason.isBlank()) {
            text = text + (russian ? " (за — " : " (for - ") + reason.trim() + ")";
        }
        return text;
    }

    /**
     * Sends the roller the only early copy of the result: a private line in their own log, with the
     * number in the administrator's gray note. It is not stored, and it makes no sound.
     */
    private static void echo(ServerPlayer roller, ServerPlayer subject, int result, int max,
                             String reason) {
        if (!RoleplayLogNetwork.canReceive(roller)) return;

        String line = line(subject != null, reason, result, max, true);
        // The stored line is resolved per reader when it is sent from the log; this one is not a row,
        // so the name placeholders have to be filled here or the administrator reads "{player}".
        String shown = ActionLogEntry.resolve(line,
                subject == null ? "" : subject.getName().getString());
        String text = RoleplayLog.present(
                MarkupUtil.withNote(shown, "rolled " + result + "/" + max));
        PacketDistributor.sendToPlayer(roller, new RoleplayLogPayload(
                ECHO_ID.getAndDecrement(), text, "", 0.0f, 1.0f, true));
    }

    /** Reveals every hidden roll whose moment has come, on the server thread. */
    public static void revealDue(MinecraftServer server) {
        BackUtilsData data = BackUtils.data();
        if (data == null) return;

        for (LogData.PendingRoll pending : data.logs().duePendingRolls(System.currentTimeMillis())) {
            long id = data.logs().revealPendingRoll(pending);
            if (id <= 0) continue;
            RoleplayLogBroadcaster.deliver(server, id, pending.accessList(),
                    soundFor(pending.result(), pending.maxRoll(), false),
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
                                  int max, String reason, boolean hidden) {
        MinecraftServer server = anchor.getServer();
        BackUtilsData data = BackUtils.data();
        if (server == null || data == null) return null;

        int result = roll(anchor, max);
        String who = subject == null ? "" : subject.getName().getString();

        List<UUID> audience = RoleplayLog.witnesses(anchor, BackUtilsConfig.getRollRadius());
        // The one who rolled reads it too, even from across the map: they asked for it, and nothing
        // is said in chat.
        if (!audience.contains(roller.getUUID())) audience.add(roller.getUUID());

        String sound = soundFor(result, max, hidden);

        long id = data.logs().record(
                subject == null ? null : subject.getUUID(), who,
                dress(line(subject != null, reason, result, max, hidden), result, max, hidden),
                audience,
                anchor.level().dimension().location().toString(),
                anchor.getX(), anchor.getY(), anchor.getZ(),
                // The administrator's copy, and the only copy, of a result the line hides.
                hidden ? "rolled " + result + "/" + max : "");
        RoleplayLogBroadcaster.deliver(server, id, audience, sound,
                (float) BackUtilsConfig.getRollSoundVolume(),
                (float) BackUtilsConfig.getRollSoundPitch(), true);

        // The console keeps the truth whatever the line shows, which is what a hidden roll is for.
        BackUtils.LOGGER.info("[{}] {} rolled {}/{}{}",
                hidden ? "hidden roll" : "roll",
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
    public static String dressFormat(int result, int max, boolean hidden) {
        if (hidden) return "";
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
     *
     * <p>A hidden roll is never dressed. Red and shaking, or neon rainbow, says which number came
     * up — for a roll out of a hundred it says the number outright — and that is the one thing a
     * hidden roll exists to prevent.
     */
    public static String dress(String line, int result, int max, boolean hidden) {
        String format = dressFormat(result, max, hidden);
        return format == null || format.isBlank() ? line : format.replace(LINE, line).trim();
    }

    /**
     * {@return the sound this result plays: the one for its end, or the ordinary roll sound}
     *
     * <p>A hidden roll keeps the ordinary sound whatever it rolled. The two ends have sounds of
     * their own, and hearing one would give the number away as plainly as the colour would.
     */
    public static String soundFor(int result, int max, boolean hidden) {
        if (hidden) return BackUtilsConfig.getRollSound();
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
    public static String line(boolean named, String reason, int result, int max, boolean hidden) {
        boolean reasoned = reason != null && !reason.isBlank();
        String format;
        if (named) {
            format = reasoned ? BackUtilsConfig.getRollFormat()
                    : BackUtilsConfig.getRollFormatNoReason();
        } else {
            format = reasoned ? BackUtilsConfig.getRollFormatAnonymous()
                    : BackUtilsConfig.getRollFormatAnonymousNoReason();
        }
        return fill(format, result, max, reason, hidden);
    }

    /**
     * {@return the format with the roll filled into it, and the names left for the log}
     *
     * <p>Runs of spaces are collapsed and the ends trimmed: a format written around a placeholder
     * that turned out to be empty would otherwise leave a gap in the middle of the line.
     */
    public static String fill(String format, int result, int max, String reason, boolean hidden) {
        return fillWith(format, max, reason,
                hidden ? HIDDEN_RESULT : String.valueOf(result));
    }

    /**
     * The same, with the text that stands in for the result given rather than read from the config:
     * the settings tab previews a hidden result with the value being typed into the field.
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
                 "rollFormatAnonymousNoReason", "rollMinFormat", "rollMaxFormat" -> true;
            default -> false;
        };
    }

    /**
     * {@return what a format looks like with the samples filled in, for the settings tab}
     *
     * <p>Rendered through the same substitution the log renders with, names included, so a preview
     * cannot show a line the log would not. The two extremes are shown wrapped around the ordinary
     * line, because that is what they wrap when a roll lands on one, and a hidden result is shown
     * inside that line, because that is where it appears.
     *
     * @return the line to preview, or empty when this key is not one of the formats
     */
    public static String preview(String key, String format) {
        if (format == null) return "";

        String text = switch (key) {
            case "rollFormat" -> fill(format, SAMPLE_RESULT, SAMPLE_MAX, SAMPLE_REASON, false);
            case "rollFormatNoReason" -> fill(format, SAMPLE_RESULT, SAMPLE_MAX, null, false);
            case "rollFormatAnonymous" ->
                    fill(format, SAMPLE_RESULT, SAMPLE_MAX, SAMPLE_REASON, false);
            case "rollFormatAnonymousNoReason" ->
                    fill(format, SAMPLE_RESULT, SAMPLE_MAX, null, false);
            case "rollMinFormat", "rollMaxFormat" -> format.replace(LINE, ordinarySample());
            default -> "";
        };
        return ActionLogEntry.resolve(text, SAMPLE_PLAYER);
    }

    /** {@return the sample line the extremes wrap, already named} */
    private static String ordinarySample() {
        return ActionLogEntry.resolve(
                fill(BackUtilsConfig.getRollFormatNoReason(), SAMPLE_RESULT, SAMPLE_MAX, null, false),
                SAMPLE_PLAYER);
    }
}
