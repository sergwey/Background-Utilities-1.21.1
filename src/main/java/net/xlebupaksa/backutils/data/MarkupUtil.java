package net.xlebupaksa.backutils.data;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
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
     * {@return true when a renderer shows no text of this}: its tags, its blanks, its control characters
     *
     * <p>Three things draw no text. A tag is spent on the words under it, which is what {@link #strip} takes
     * out. A blank draws an empty space. And a control character is not drawn at all — it is a mark for
     * something drawn elsewhere, the chat line's item mark among them, and what that stands for is a
     * component, which no tag reaches.
     *
     * <p>Asked where the question is whether there is text for a tag to wrap: the wrapper hands a row's
     * characters to a row with text in it and not to a row of marks, and the chat line keeps a piece's tag
     * characters in hand rather than drawing them when the piece has no text of its own.
     */
    public static boolean drawsNothing(String input) {
        if (input == null) return true;
        String drawn = strip(input);
        for (int i = 0; i < drawn.length(); i++) {
            char c = drawn.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isISOControl(c)) return false;
        }
        return true;
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

    /**
     * {@return the text with a closing tag for every tag it leaves open}
     *
     * <p>For markup an administrator types — a chat separator, a profile format or displayed name — where
     * a tag left open runs on into whatever is drawn beside it: the rest of the chat line, in the
     * separator's case.
     *
     * <p>Openers are tracked in a stack that a closing tag pops <i>without its name being looked at</i>,
     * which is deliberately how Ember reads the markup it is handed: {@code MarkupParser.parse} pops its
     * own style stack on any closing tag and consumes one with nothing to pop rather than drawing it. The
     * closers appended here are therefore exactly the openers still on that stack, and the result has as
     * many closing tags as opening ones. A closing tag that names something other than the innermost
     * opener is read the same way Ember reads it — it closes the innermost one — so
     * {@code <a><b></a>} comes back as {@code <a><b></a></a>}, which nests no differently for a parser
     * that never compares the two names. Making the names match would mean rewriting the text between
     * them, which is markup the author wrote and this has no business editing.
     *
     * <p>Text that is already complete comes back untouched, so this can be applied where it may not be
     * needed.
     */
    public static String balance(String input) {
        if (input == null || input.isEmpty()) return input;

        Deque<String> open = new ArrayDeque<>();
        Matcher m = TAG.matcher(input);
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