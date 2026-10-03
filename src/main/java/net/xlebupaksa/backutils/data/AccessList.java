package net.xlebupaksa.backutils.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Encodes the {@code access_list} column: the set of players who were close enough to
 * witness an action when it happened.
 *
 * <p>The column holds a comma-separated list of player UUIDs in canonical form, for
 * example {@code 069a79f4-44e9-4726-a5be-fca90e38aaf5,853c80ef-3c37-49fd-aa49-938b674adae6}.
 *
 * <p>UUIDs, not names: a name-based list silently stops matching the moment somebody changes
 * their username, and this list is what decides whether a log entry is delivered at all.
 *
 * <p>Reads are forgiving — entries that are not UUIDs are skipped, so a hand-edited or legacy row
 * cannot throw during login.
 */
public final class AccessList {

    private static final Pattern UUID_SHAPE = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private AccessList() {}

    public static String encode(List<UUID> players) {
        StringBuilder sb = new StringBuilder();
        for (UUID player : players) {
            if (sb.length() > 0) sb.append(',');
            sb.append(player);
        }
        return sb.toString();
    }

    /** {@return the UUIDs in the column, ignoring anything that is not a UUID} */
    public static List<UUID> decode(String stored) {
        List<UUID> out = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return out;

        for (String part : stored.split(",")) {
            String candidate = part.trim();
            if (candidate.isEmpty() || !UUID_SHAPE.matcher(candidate).matches()) continue;
            try {
                UUID id = UUID.fromString(candidate);
                if (!out.contains(id)) out.add(id);
            } catch (IllegalArgumentException ignored) {
                // Skip the entry rather than fail the whole row.
            }
        }
        return out;
    }

    /** Appends a player without producing a duplicate. */
    public static String append(String stored, UUID player) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(decode(stored));
        ids.add(player);
        return encode(new ArrayList<>(ids));
    }

    /**
     * Removes a player, used to hide one entry from one person.
     *
     * <p>Decoding first means a malformed entry is cleaned up on the way through rather than
     * surviving every rewrite.
     */
    public static String remove(String stored, UUID player) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(decode(stored));
        ids.remove(player);
        return encode(new ArrayList<>(ids));
    }
}
