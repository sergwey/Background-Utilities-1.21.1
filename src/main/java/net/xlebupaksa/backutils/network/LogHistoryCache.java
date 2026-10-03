package net.xlebupaksa.backutils.network;

import java.util.List;

/**
 * The log history most recently received from the server. Client-side state with no Minecraft imports, so
 * common code can reference it without dragging a client-only class into a dedicated server's class
 * loading.
 */
public final class LogHistoryCache {

    private static volatile List<LogHistoryPayload.Row> rows = List.of();
    private static volatile int revision;

    private LogHistoryCache() {}

    public static List<LogHistoryPayload.Row> rows() {
        return rows;
    }

    /** Bumped on every change, so a screen can tell whether it needs to rebuild. */
    public static int revision() {
        return revision;
    }

    public static void set(List<LogHistoryPayload.Row> newRows) {
        rows = List.copyOf(newRows);
        revision++;
    }

    public static void clear() {
        rows = List.of();
        revision++;
    }
}
