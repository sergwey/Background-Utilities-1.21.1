package net.xlebupaksa.backutils.network;

/**
 * The last thing the server said about a profile change.
 *
 * <p>{@link #revision()} lets a screen read the message without consuming it. The profile it
 * was about is not kept: the window that asked is the only one that reacts.
 */
public final class ProfileFeedbackCache {

    private static volatile boolean ok;
    private static volatile String message = "";
    private static volatile int revision;

    private ProfileFeedbackCache() {}

    public static boolean ok() {
        return ok;
    }

    public static String message() {
        return message;
    }

    public static int revision() {
        return revision;
    }

    public static void push(boolean success, String text) {
        ok = success;
        message = text == null ? "" : text;
        revision++;
    }

    public static void clear() {
        ok = false;
        message = "";
        revision++;
    }
}
