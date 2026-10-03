package net.xlebupaksa.backutils.network;

/**
 * The last thing the server said about a profile an administrator changed, readable without consuming
 * it: a screen reads the message for as long as the revision is the one it last showed.
 */
public final class AdminProfileFeedbackCache {

    private static volatile boolean ok;
    private static volatile boolean names;
    private static volatile String player = "";
    private static volatile String message = "";
    private static volatile int revision;

    private AdminProfileFeedbackCache() {}

    public static boolean ok() {
        return ok;
    }

    public static boolean names() {
        return names;
    }

    public static String player() {
        return player;
    }

    public static String message() {
        return message;
    }

    public static int revision() {
        return revision;
    }

    /** {@return true when this message is about the profile in question} */
    public static boolean about(boolean profileNames, String profilePlayer) {
        return names == profileNames && player.equals(profilePlayer == null ? "" : profilePlayer);
    }

    public static void push(boolean success, boolean profileNames, String profilePlayer,
                            String text) {
        ok = success;
        names = profileNames;
        player = profilePlayer == null ? "" : profilePlayer;
        message = text == null ? "" : text;
        revision++;
    }

    public static void clear() {
        ok = false;
        names = false;
        player = "";
        message = "";
        revision++;
    }
}
