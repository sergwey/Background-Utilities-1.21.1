package net.xlebupaksa.backutils.data;

/** The three things a player can be silenced in, independently. */
public enum SilenceKind {

    /** Roleplay actions, both {@code *...*} and {@code /me}. */
    ACTIONS("actions"),
    /** Ordinary chat heard only by players nearby. */
    LOCAL_CHAT("local chat"),
    /** Chat sent to the whole server with a leading {@code !}. */
    GLOBAL_CHAT("global chat");

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
