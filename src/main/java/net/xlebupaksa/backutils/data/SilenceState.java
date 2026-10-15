package net.xlebupaksa.backutils.data;

/**
 * What one player is currently silenced in.
 *
 * <p>The components are in the order the table's columns are, so a row reads into this without a
 * mapping of its own to get wrong.
 *
 * @param player the player's account name, matching how the rest of the mod keys rows
 */
public record SilenceState(String player, boolean actions, boolean localChat, boolean globalChat,
                           boolean messages) {

    public static SilenceState none(String player) {
        return new SilenceState(player, false, false, false, false);
    }

    public boolean silenced(SilenceKind kind) {
        return switch (kind) {
            case ACTIONS -> actions;
            case LOCAL_CHAT -> localChat;
            case GLOBAL_CHAT -> globalChat;
            case MSG -> messages;
        };
    }

    /** {@return a copy with one flag replaced} */
    public SilenceState with(SilenceKind kind, boolean value) {
        return switch (kind) {
            case ACTIONS -> new SilenceState(player, value, localChat, globalChat, messages);
            case LOCAL_CHAT -> new SilenceState(player, actions, value, globalChat, messages);
            case GLOBAL_CHAT -> new SilenceState(player, actions, localChat, value, messages);
            case MSG -> new SilenceState(player, actions, localChat, globalChat, value);
        };
    }

    /** {@return true when nothing at all is silenced} */
    public boolean isEmpty() {
        return !actions && !localChat && !globalChat && !messages;
    }

    /** {@return a short description of what is silenced, for command output} */
    public String describe() {
        if (isEmpty()) return "nothing";
        StringBuilder sb = new StringBuilder();
        for (SilenceKind kind : SilenceKind.values()) {
            if (silenced(kind)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(kind.label());
            }
        }
        return sb.toString();
    }
}
