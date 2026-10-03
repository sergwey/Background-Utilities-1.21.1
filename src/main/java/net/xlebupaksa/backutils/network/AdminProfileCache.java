package net.xlebupaksa.backutils.network;

import java.util.List;

/**
 * What the server last told an administrator about other players' profiles.
 *
 * <p>Client-side state with no Minecraft imports, so it can be referenced from common code without
 * dragging a client-only class into a dedicated server's class loading.
 *
 * <p>The player list and one player's profiles are held separately, each with its own revision.
 */
public final class AdminProfileCache {

    private static volatile List<AdminPlayerListPayload.Player> chatPlayers = List.of();
    private static volatile List<AdminPlayerListPayload.Player> namePlayers = List.of();
    private static volatile int playersRevision;

    /** Which kind the profile list below belongs to, and whose it is. */
    private static volatile boolean profileNames;
    private static volatile String profilePlayer = "";
    private static volatile List<ProfileListPayload.Row> profiles = List.of();
    private static volatile List<String> sounds = List.of();
    private static volatile int profilesRevision;

    private AdminProfileCache() {}

    public static List<AdminPlayerListPayload.Player> players(boolean names) {
        return names ? namePlayers : chatPlayers;
    }

    public static int playersRevision() {
        return playersRevision;
    }

    public static boolean profileNames() {
        return profileNames;
    }

    public static String profilePlayer() {
        return profilePlayer;
    }

    public static List<ProfileListPayload.Row> profiles() {
        return profiles;
    }

    public static List<String> sounds() {
        return sounds;
    }

    public static int profilesRevision() {
        return profilesRevision;
    }

    /** {@return true when the profiles held are exactly this player's, of this kind} */
    public static boolean holds(boolean names, String player) {
        return profilesRevision > 0 && profileNames == names && profilePlayer.equals(player);
    }

    public static void setPlayers(boolean names, List<AdminPlayerListPayload.Player> players) {
        if (names) {
            namePlayers = List.copyOf(players);
        } else {
            chatPlayers = List.copyOf(players);
        }
        playersRevision++;
    }

    public static void setProfiles(boolean names, String player,
                                   List<ProfileListPayload.Row> rows, List<String> palette) {
        profileNames = names;
        profilePlayer = player == null ? "" : player;
        profiles = List.copyOf(rows);
        sounds = List.copyOf(palette);
        profilesRevision++;
    }

    /** Forgets everything: another server's players, and its palette, must not survive the connection. */
    public static void clear() {
        chatPlayers = List.of();
        namePlayers = List.of();
        profileNames = false;
        profilePlayer = "";
        profiles = List.of();
        sounds = List.of();
        playersRevision++;
        profilesRevision++;
    }
}
