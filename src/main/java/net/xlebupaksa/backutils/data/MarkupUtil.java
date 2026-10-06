package net.xlebupaksa.backutils.data;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
     * {@return the text as a component that is drawn exactly as it is written}
     *
     * <p>Ember parses the markup inside a literal as that literal is drawn, so text handed to
     * {@code Component.literal} loses its tags and takes on what they do: a format typed into a text
     * field is drawn red rather than showing the {@code <color>} that made it red, which leaves the
     * tags impossible to read or edit. Ember parses each literal separately and only when that one
     * holds both a {@code <} and a {@code >}, so splitting the text until no piece holds both makes
     * the parser find nothing and the tags stay as they were typed.
     *
     * <p>The pieces are siblings of one component, so the drawn text is the text given: nothing is
     * added, moved or marked, which matters because the field's cursor and selection are counted
     * over the value itself.
     */
    public static Component asLiteral(String text) {
        MutableComponent drawn = Component.empty();
        if (text == null || text.isEmpty()) return drawn;

        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '<' && c != '>') continue;
            if (i > start) drawn.append(Component.literal(text.substring(start, i)));
            drawn.append(Component.literal(String.valueOf(c)));
            start = i + 1;
        }
        if (start < text.length()) drawn.append(Component.literal(text.substring(start)));
        return drawn;
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

    /**
     * {@return the text with an administrator's note after it, or the text unchanged}
     *
     * <p>Grey, and grey the same way wherever it is drawn: the note is what an entry keeps from
     * players, so this is only ever called for a viewer whose permission has been checked, and the
     * line it is appended to is left intact — an operator reads exactly what the players read, with
     * the truth beside it.
     */
    public static String withNote(String text, String note) {
        return note == null || note.isBlank() ? text
                : text + "  <color col=#8a8a8a>" + note + "</color>";
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