package net.xlebupaksa.backutils.data;

import java.util.regex.Pattern;

public final class MarkupUtil {

    /**
     * Matches one tag, following the same grammar Ember's Text API uses: an optional slash, a
     * name, optional whitespace-separated {@code key=value} / {@code key:value} attributes, and an
     * optional self-closing slash.
     *
     * <p>The name must start with a letter, which keeps ordinary text such as {@code <3 ... >}
     * from being read as a tag and "closed" with a bogus {@code </3>}.
     */
    private static final Pattern TAG = Pattern.compile(
            "<(/?)([a-zA-Z][a-zA-Z0-9_]*)((?:\\s+[a-zA-Z][a-zA-Z0-9]*(?:[=:](?:[\"'][^\"']*[\"']|[^\\s>/]+))?)*)(/?)>");

    public static String strip(String input) {
        return TAG.matcher(input).replaceAll("");
    }

    /**
     * The tags that carry colour: Ember's {@code color}/{@code c} and the gradient effects.
     *
     * <p>Openers and closers are removed together: a closing tag with nothing to close yields no
     * formatting at all, which makes the client render the raw markup instead of the text.
     */
    private static final Pattern COLOUR_TAG = Pattern.compile(
            "<(/?)(?:color|c|grad|gradient|rainbow)(?:\\s[^<>]*)?>",
            Pattern.CASE_INSENSITIVE);

    /**
     * {@return the text with all colour markup removed, leaving other formatting alone}
     *
     * <p>Used for names written to the roleplay log, which is meant to read as plain white: a
     * player's own name profile may be any colour, and that must not tint the log. Other
     * formatting is kept, notably the underline that marks the actor's own line.
     */
    public static String stripColour(String input) {
        return input == null ? null : COLOUR_TAG.matcher(input).replaceAll("");
    }

    public static String balance(String input) {
        if (input == null || input.isEmpty()) return input;

        java.util.Deque<String> open = new java.util.ArrayDeque<>();
        java.util.regex.Matcher m = TAG.matcher(input);
        while (m.find()) {
            boolean closing = !m.group(1).isEmpty();
            boolean selfClosing = !m.group(4).isEmpty();
            String name = m.group(2);
            if (closing) {
                if (!open.isEmpty()) open.pop();
            } else if (!selfClosing) {
                open.push(name);
            }
        }

        if (open.isEmpty()) return input;

        StringBuilder sb = new StringBuilder(input);
        while (!open.isEmpty()) {
            sb.append("</").append(open.pop()).append(">");
        }
        return sb.toString();
    }
}