package net.xlebupaksa.backutils.network;

import java.util.List;

/**
 * What the server last told an administrator about one player's memory.
 *
 * <p>Client-side state with no Minecraft imports, like the other administrator caches, so common code
 * can name it without dragging a client-only class into a dedicated server's class loading. It holds
 * one player at a time: only one player's memory is open on a screen, and a second player's rows
 * replacing the first's is what makes closing and opening another one safe.
 */
public final class AdminMemoryCache {

    private static volatile String player = "";
    private static volatile List<AdminMemoryPayload.Row> rows = List.of();
    private static volatile int revision;

    private AdminMemoryCache() {}

    /** {@return the player the held rows belong to}, empty before any answer */
    public static String player() {
        return player;
    }

    /** {@return the rows last received, which belong to {@link #player()} */
    public static List<AdminMemoryPayload.Row> rows() {
        return rows;
    }

    /** {@return true when the held rows are exactly this player's} */
    public static boolean holds(String wanted) {
        return revision > 0 && player.equals(wanted);
    }

    public static int revision() {
        return revision;
    }

    public static void set(String player, List<AdminMemoryPayload.Row> newRows) {
        AdminMemoryCache.player = player == null ? "" : player;
        rows = List.copyOf(newRows);
        revision++;
    }

    /** Forgets everything: another server's players must not survive the connection. */
    public static void clear() {
        player = "";
        rows = List.of();
        revision++;
    }
}
