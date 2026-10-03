package net.xlebupaksa.backutils.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers, per player, the highest log id that has been sent to them, so delivery knows where each
 * player got to. Server-side only, and intentionally in memory.
 *
 * <p>An unknown player means "start from now", not "send everything": the watermark is forgotten when
 * someone disconnects, so a missing entry is the normal state at login, and {@link #seed} is what
 * marks the point they joined at.
 */
public final class SentLogWatermark {

    private static final Map<UUID, Long> SENT_UP_TO = new ConcurrentHashMap<>();

    private SentLogWatermark() {}

    /** {@return true when this player has a known position in the log} */
    public static boolean known(UUID viewer) {
        return SENT_UP_TO.containsKey(viewer);
    }

    /**
     * {@return the highest id already sent to this player, or 0 for an unknown one}
     */
    public static long sentUpTo(UUID viewer) {
        Long value = SENT_UP_TO.get(viewer);
        return value == null ? 0L : value;
    }

    /**
     * Sets a player's position without sending them anything, so everything already in the log is
     * behind them and only what happens next is delivered.
     */
    public static void seed(UUID viewer, long logId) {
        SENT_UP_TO.merge(viewer, logId, Math::max);
    }

    public static void markSent(UUID viewer, long logId) {
        SENT_UP_TO.merge(viewer, logId, Math::max);
    }

    /** Forgets a player's watermark. Called when they disconnect. */
    public static void forget(UUID viewer) {
        SENT_UP_TO.remove(viewer);
    }

    public static void clear() {
        SENT_UP_TO.clear();
    }
}
