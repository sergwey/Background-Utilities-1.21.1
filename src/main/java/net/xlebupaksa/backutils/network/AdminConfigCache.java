package net.xlebupaksa.backutils.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings the server last sent. Client-side state with no Minecraft imports, and the menu's
 * only source: the rows carry their own labels, groups, kinds and bounds.
 */
public final class AdminConfigCache {

    private static volatile List<AdminConfigPayload.Row> rows = List.of();
    private static volatile Map<String, AdminConfigPayload.Row> byKey = Map.of();
    private static volatile int revision;

    private AdminConfigCache() {}

    public static List<AdminConfigPayload.Row> rows() {
        return rows;
    }

    public static int revision() {
        return revision;
    }

    /** {@return the last value reported for this key, or ""} */
    public static String value(String key) {
        AdminConfigPayload.Row row = byKey.get(key);
        return row == null ? "" : row.value();
    }

    public static void set(List<AdminConfigPayload.Row> settings) {
        rows = List.copyOf(settings);
        Map<String, AdminConfigPayload.Row> index = new LinkedHashMap<>();
        for (AdminConfigPayload.Row row : settings) index.put(row.key(), row);
        byKey = Map.copyOf(index);
        revision++;
    }

    public static void clear() {
        rows = List.of();
        byKey = Map.of();
        revision++;
    }
}
