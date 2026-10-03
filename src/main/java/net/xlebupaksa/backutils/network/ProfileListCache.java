package net.xlebupaksa.backutils.network;

import java.util.List;

/**
 * The chat profiles the server last sent, and the sounds it will accept.
 *
 * <p>Client-side state, but free of Minecraft imports, so common code can reference it without
 * loading a client-only class on a dedicated server. {@link #revision()} lets the open menu
 * notice fresh profiles without polling the list.
 */
public final class ProfileListCache {

    private static volatile List<ProfileListPayload.Row> rows = List.of();
    private static volatile List<String> sounds = List.of();
    private static volatile int revision;

    private ProfileListCache() {}

    public static List<ProfileListPayload.Row> rows() {
        return rows;
    }

    public static List<String> sounds() {
        return sounds;
    }

    public static int revision() {
        return revision;
    }

    public static void set(List<ProfileListPayload.Row> newRows, List<String> newSounds) {
        rows = List.copyOf(newRows);
        sounds = List.copyOf(newSounds);
        revision++;
    }

    public static void clear() {
        rows = List.of();
        sounds = List.of();
        revision++;
    }
}
