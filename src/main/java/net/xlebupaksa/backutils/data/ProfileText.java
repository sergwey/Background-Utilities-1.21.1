package net.xlebupaksa.backutils.data;

/**
 * The placeholder used inside stored profile text and action templates, plus the markup that
 * underlines a player's own name. Kept separate from the profile text helper so the data package
 * does not depend on any Minecraft classes: {@link ActionLogEntry} needs the placeholder, not the
 * component building.
 */
public final class ProfileText {

    /** Replaced with the acting player's name. */
    public static final String PLAYER_PLACEHOLDER = "{player}";

    /**
     * Replaced with the same name and an {@code 's} after it, and with nothing at all when the line
     * names nobody: the roll lines read {@code Dev's dice rolled 42/100!} and {@code dice rolled
     * 42/100!} from one format that way.
     */
    public static final String POSSESSIVE_PLACEHOLDER = "{player_possessive}";

    /** Appended to a name to make {@link #POSSESSIVE_PLACEHOLDER}. */
    private static final String POSSESSIVE = "'s";

    /**
     * Ember's underline tag is {@code u}, long form {@code underline}. {@code underlined} is
     * <b>not</b> a tag Ember knows, and an unknown tag makes the parser find no formatting in the
     * line, give up, and render the raw text — tags and all — instead of styled text.
     */
    private static final String UNDERLINE_OPEN = "<u>";
    private static final String UNDERLINE_CLOSE = "</u>";

    private ProfileText() {}

    /**
     * {@return the text, underlined when this viewer is the one who acted}
     *
     * <p>Not coloured: the roleplay log is plain white text.
     */
    public static String underlined(String text, boolean highlighted) {
        return highlighted ? UNDERLINE_OPEN + text + UNDERLINE_CLOSE : text;
    }

    /**
     * {@return the name with {@code 's} after it, or nothing when there is no name}
     *
     * <p>Nothing rather than a bare {@code 's}: the same sentence has to read correctly with and
     * without somebody to name.
     */
    public static String possessive(String name) {
        return name == null || name.isBlank() ? "" : name + POSSESSIVE;
    }
}
