package net.xlebupaksa.backutils.network;


/**
 * What the server last said about one player's notes. One target at a time, like the memory cache:
 * only one player's notes are open on the admin screen.
 */
public final class AdminNotesCache {

    public record Feedback(String player, NoteFeedbackPayload.Reason reason, String holderName,
                           int seconds, int revision) {}

    private static volatile NoteStatePayload state;
    private static volatile Feedback feedback;
    private static volatile int revision;

    private AdminNotesCache() {}

    public static synchronized void set(NoteStatePayload payload) {
        state = payload;
        revision++;
    }

    public static synchronized void setFeedback(NoteFeedbackPayload payload) {
        feedback = new Feedback(payload.player(), payload.reason(), payload.holderName(),
                payload.seconds(), ++revision);
    }

    public static synchronized NoteStatePayload state() {
        return state;
    }

    public static synchronized Feedback feedback() {
        return feedback;
    }

    public static synchronized int revision() {
        return revision;
    }

    /** {@return true when the held state is exactly this player's} */
    public static synchronized boolean holds(String player) {
        return state != null && state.player().equals(player);
    }

    public static synchronized void clear() {
        state = null;
        feedback = null;
        revision++;
    }
}
