package net.xlebupaksa.backutils.data;

/**
 * What one player is currently silenced in.
 *
 * @param player the player's account name, matching how the rest of the mod keys rows
 */
public record SilenceState(String player, boolean actions, boolean localChat, boolean globalChat) {

    public static SilenceState none(String player) {
        return new SilenceState(player, false, false, false);
    }

    public boolean silenced(SilenceKind kind) {
        return switch (kind) {
            case ACTIONS -> actions;
            case LOCAL_CHAT -> localChat;
            case GLOBAL_CHAT -> globalChat;
        };
    }

    /** {@return a copy with one flag replaced} */
    public SilenceState with(SilenceKind kind, boolean value) {
        return switch (kind) {
            case ACTIONS -> new SilenceState(player, value, localChat, globalChat);
            case LOCAL_CHAT -> new SilenceState(player, actions, value, globalChat);
            case GLOBAL_CHAT -> new SilenceState(player, actions, localChat, value);
        };
    }

    /** {@return true when nothing at all is silenced} */
    public boolean isEmpty() {
        return !actions && !localChat && !globalChat;
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
