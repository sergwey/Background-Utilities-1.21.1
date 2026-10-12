package net.xlebupaksa.backutils.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Wraps log text onto several lines, preferring to break between words.
 *
 * <p>Wrapping is driven by <b>measured width</b>, not a character count: the same number of
 * characters is a different number of pixels depending on which ones they are, so a counted line
 * can overflow its column and leave the left edge of the log ragged. Measuring each candidate line
 * removes the problem at the source: every row fits, so every row can start at the same x. The
 * measuring function is supplied by the caller, which keeps this class free of any Minecraft
 * dependency.
 *
 * <p>Two things a naive word wrap gets wrong, because the text carries Ember markup:
 *
 * <ul>
 *   <li>tags are invisible, so they must not count towards the width, and must never be cut in
 *       half - a broken tag is rendered as literal text;</li>
 *   <li><b>each line is parsed on its own</b>, so a tag opened on one line and closed on the next
 *       leaves the second starting with a dangling closing tag, and a line with no matchable
 *       formatting makes Ember draw the raw text. Tags still open at the end of a line are therefore
 *       <em>reopened</em> at the start of the next one, so one colour survives a wrap.
 *       They are not <em>closed</em>: writing a closing tag the text never had puts a tag on screen
 *       that the player did not type, which a one-shot effect with no closing form of its own - Ember's
 *       typewriter is one - draws as literal text.</li>
 * </ul>
 */
public final class MarkupWrap {

    private enum Kind { TAG, WORD, SPACE }

    private record Token(Kind kind, String raw) {}

    private MarkupWrap() {}

    /**
     * @param maxWidth the greatest width a line may occupy, in whatever unit {@code measure} returns
     * @param measure  measures the visible width of a line, ignoring markup tags
     * @return one string per line, each independently well-formed
     */
    public static List<String> wrapByWidth(String text, int maxWidth,
                                           ToIntFunction<String> measure) {
        List<String> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            rows.add("");
            return rows;
        }
        int width = Math.max(1, maxWidth);

        Deque<String> open = new ArrayDeque<>();
        StringBuilder row = new StringBuilder();
        int visible = 0;
        boolean pendingSpace = false;

        for (Token token : tokenise(text)) {
            switch (token.kind()) {
                case TAG -> {
                    String raw = token.raw();
                    if (raw.startsWith("</")) {
                        if (!open.isEmpty()) open.pop();
                    } else if (!raw.endsWith("/>")) {
                        open.push(raw);
                    }
                    row.append(raw);
                }
                case SPACE -> {
                    // Leading spaces are dropped so a wrapped line does not start indented.
                    if (visible > 0) pendingSpace = true;
                }
                case WORD -> {
                    String word = token.raw();

                    if (visible > 0) {
                        String candidate = row + (pendingSpace ? " " : "") + word;
                        if (measure.applyAsInt(candidate) > width) {
                            flush(rows, row, open);
                            visible = 0;
                            pendingSpace = false;
                        }
                    }

                    if (pendingSpace) {
                        row.append(' ');
                        visible++;
                        pendingSpace = false;
                    }

                    // A word too wide for a line of its own has to be cut, but the tags around
                    // it are carried across the cut.
                    while (!word.isEmpty()
                            && measure.applyAsInt(row.toString() + word) > width) {
                        int take = charsThatFit(row, word, width, measure);
                        if (take <= 0) {
                            // Nothing fits, which can only happen if the width is smaller than
                            // a single character; take one so the loop always makes progress.
                            take = 1;
                        }
                        row.append(word, 0, take);
                        visible += take;
                        flush(rows, row, open);
                        visible = 0;
                        word = word.substring(take);
                    }

                    if (!word.isEmpty()) {
                        row.append(word);
                        visible += word.length();
                    }
                }
            }
        }

        // The last row is left exactly as it was typed, without the closing tags every earlier row
        // gets. Closing it would protect nothing - there is no next row for a tag to leak into - and
        // the closer would be a tag the player never wrote: a one-shot effect with no closing form of
        // its own, Ember's typewriter among them, then draws that closer as literal text at the end of
        // the line.
        rows.add(row.toString());
        return rows;
    }

    /**
     * {@return the text, cut to a width, with an ellipsis when anything was dropped}
     *
     * <p>For the one-line messages the menus show: a refusal from the server is a whole sentence,
     * and those are drawn with labels as wide as their text whatever box they were given. Measured
     * through the same function as the wrapping, so the two agree about how wide a line is.
     */
    public static String truncate(String text, int maxWidth, ToIntFunction<String> measure) {
        if (text == null || text.isEmpty()) return "";
        if (measure.applyAsInt(text) <= maxWidth) return text;

        String suffix = "...";
        int room = Math.max(0, maxWidth - measure.applyAsInt(suffix));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (measure.applyAsInt(out.toString() + c) > room) break;
            out.append(c);
        }
        return out + suffix;
    }

    /** {@return the longest prefix of {@code word} that still fits on the current line} */
    private static int charsThatFit(StringBuilder row, String word, int width,
                                    ToIntFunction<String> measure) {        for (int n = word.length() - 1; n > 0; n--) {
            if (measure.applyAsInt(row.toString() + word.substring(0, n)) <= width) return n;
        }
        return 0;
    }

    /**
     * Ends the current line and reopens its still-open tags on the next.
     *
     * <p>The line is not closed. A closing tag written here is one the text never had, and whether the
     * renderer knows that closing form is not something this class can know: a one-shot effect such as
     * Ember's typewriter has none, so the tag this would add is drawn as literal text at the end of the
     * line. Nothing needs it either - each line is parsed on its own, so a tag left open simply runs to
     * the end of the line it is on, which is where the text it was opened in ends - while the reopened
     * tags on the next line are what keeps one colour across a wrap.
     */
    private static void flush(List<String> rows, StringBuilder row, Deque<String> open) {
        rows.add(row.toString());
        row.setLength(0);
        reopenTags(row, open);
    }

    /** Re-opens the tags in their original nesting order, outermost first. */
    private static void reopenTags(StringBuilder row, Deque<String> open) {
        List<String> tags = new ArrayList<>(open);
        for (int i = tags.size() - 1; i >= 0; i--) {
            row.append(tags.get(i));
        }
    }

    private static List<Token> tokenise(String text) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);

            if (c == '<') {
                int end = text.indexOf('>', i + 1);
                if (end > 0 && isTagBody(text, i + 1, end)) {
                    tokens.add(new Token(Kind.TAG, text.substring(i, end + 1)));
                    i = end + 1;
                    continue;
                }
                // A bare '<' is just text.
                tokens.add(new Token(Kind.WORD, "<"));
                i++;
                continue;
            }

            if (c == ' ') {
                int j = i;
                while (j < n && text.charAt(j) == ' ') j++;
                tokens.add(new Token(Kind.SPACE, " "));
                i = j;
                continue;
            }

            int j = i;
            while (j < n && text.charAt(j) != ' ' && text.charAt(j) != '<') j++;
            tokens.add(new Token(Kind.WORD, text.substring(i, j)));
            i = j;
        }
        return tokens;
    }

    /**
     * {@return true when the text between the brackets looks like a tag}
     *
     * <p>Mirrors Ember's own tag grammar closely enough not to mistake arithmetic such as
     * {@code <3} for a tag.
     */
    private static boolean isTagBody(String text, int from, int to) {
        String body = text.substring(from, to);
        if (body.isEmpty()) return false;
        int index = 0;
        if (body.charAt(0) == '/') index++;
        if (index >= body.length()) return false;
        char first = body.charAt(index);
        if (!Character.isLetterOrDigit(first) && first != '_') return false;
        return body.indexOf('<') < 0;
    }
}
