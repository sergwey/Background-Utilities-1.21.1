package net.xlebupaksa.backutils.log;

/**
 * Works out whether a chat line is a roleplay action, and what text it carries: {@code *Dev done something*} from ordinary
 * chat, or {@code /me done something}. The asterisk form is deliberately conservative, since a stray asterisk is far more
 * common than an intentional action, so anything but a single balanced pair around non-empty text stays ordinary chat.
 */
public final class ActionText {

    /** What {@code *…*} and {@code /me} become, with the name inserted later. */
    public static final String ACTION_TEMPLATE = "{player} %s";

    private ActionText() {}

    /** {@return the action body when the message is {@code *…*}, otherwise null} — an empty body, an unclosed asterisk or
     *  a closing asterisk that is not final all yield null. */
    public static String stripAsterisks(String message) {
        if (message == null) return null;

        String trimmed = message.trim();
        if (trimmed.length() < 3) return null;
        if (trimmed.charAt(0) != '*' || trimmed.charAt(trimmed.length() - 1) != '*') return null;

        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) return null;

        // A body still holding an asterisk is ambiguous — "*waves* at *Bob*".
        if (body.indexOf('*') >= 0) return null;

        return body;
    }

    /** {@return the action body with surrounding asterisks removed} — used by {@code /me}, where players type the
     *  asterisks out of habit; only a matching pair is removed, so {@code /me *waves*} and {@code /me waves} are alike. */
    public static String stripLeadingAsterisks(String body) {
        if (body == null) return "";
        String trimmed = body.trim();
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '*'
                && trimmed.charAt(trimmed.length() - 1) == '*') {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * {@return the silent-action body when the message is {@code ^…^}, otherwise null}
     * <p>Stricter than {@link #stripAsterisks}: {@code ^} collides with a common emoticon, so the body must contain a
     * letter, or {@code ^_^} would log an action called {@code _}.
     */
    public static String stripCarets(String message) {
        if (message == null) return null;

        String trimmed = message.trim();
        if (trimmed.length() < 3) return null;
        if (trimmed.charAt(0) != '^' || trimmed.charAt(trimmed.length() - 1) != '^') return null;

        String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) return null;
        if (body.indexOf('^') >= 0) return null;

        for (int i = 0; i < body.length(); i++) {
            if (Character.isLetter(body.charAt(i))) return body;
        }
        return null;
    }

    /**
     * {@return the stored template for an action, with the {@code {player}} placeholder}
     * <p>Stored as a placeholder rather than a baked-in name so the log renders per viewer; newlines are folded to keep one
     * action on one line.
     */
    public static String template(String body) {
        return ACTION_TEMPLATE.formatted(body.replace("\n", " ").trim());
    }
}
