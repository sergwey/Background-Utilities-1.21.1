package net.xlebupaksa.backutils.network;

/**
 * The last thing the server said about a settings change. Kept apart from
 * {@link AdminProfileFeedbackCache}, since the two belong to different tabs and a refusal about a
 * profile must not appear under the settings' Save button.
 */
public final class AdminConfigFeedbackCache {

    private static volatile boolean ok;
    private static volatile String message = "";
    private static volatile int revision;

    private AdminConfigFeedbackCache() {}

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
