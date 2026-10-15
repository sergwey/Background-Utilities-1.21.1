package net.xlebupaksa.backutils.data;

/** The four things a player can be silenced in, independently. */
public enum SilenceKind {

    /** Roleplay actions, both {@code *...*} and {@code /me}. */
    ACTIONS("actions"),
    /** Ordinary chat heard only by players nearby. */
    LOCAL_CHAT("local chat"),
    /** Chat sent to the whole server with a leading {@code !}. */
    GLOBAL_CHAT("global chat"),
    /**
     * Private messages: what {@code /msg}, {@code /tell} and {@code /w} send to named players.
     *
     * <p>Spelled the way the table's column and the command's argument spell it — {@code msg} —
     * rather than the way the label reads, because {@link #parse} reads the constant's own name: one
     * name for the thing an operator types, the thing the row holds and the thing this enum answers
     * to, with no second spelling to keep in step.
     */
    MSG("private messages");

    private final String label;

    SilenceKind(String label) {
        this.label = label;
    }

    /** {@return a human-readable name, for command output} */
    public String label() {
        return label;
    }

    /** {@return the kind a command argument refers to, or null} */
    public static SilenceKind parse(String name) {
        for (SilenceKind kind : values()) {
            if (kind.name().equalsIgnoreCase(name)
                    || kind.name().replace("_", "").equalsIgnoreCase(name.replace("_", ""))) {
                return kind;
            }
        }
        return null;
    }
}
