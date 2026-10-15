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
 * measuring function is supplied by the caller, which keeps the wrapping itself free of any
 * Minecraft type.
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
 *
 * <p>One more thing separates the chat line from the rows a menu draws of it: the chat line is
 * <b>one literal</b>, which Ember parses in one piece, while a menu hands Ember one literal per row.
 * A row of nothing but tags parses to no spans at all on its own, and Ember then leaves the markup
 * alone and draws the characters of the tags; in the chat line the same tags are spent wherever they
 * stand, the parse having found the text under the break, and that row draws nothing. Such a row's
 * characters are therefore carried to the nearest row that has text - see
 * {@link #carryTagOnlyRows} - which is what makes a caller drawing the rows one at a time draw what
 * the chat line draws.
 */
public final class MarkupWrap {

    private enum Kind { TAG, WORD, SPACE }

    private record Token(Kind kind, String raw) {}

    /**
     * How a line break is written in text a command argument or a one-line field has to hold: the two
     * characters {@code \n}, as the banner's text does. Kept beside the wrapping because the rows a
     * broken line makes are wrapped by the same code.
     */
    private static final String LINE_BREAK = "\\n";

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
                    trackOpenTags(open, token.raw());
                    row.append(token.raw());
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
     * {@return the text with every written {@code \n} made a real line break}, the tags still open at
     * a break reopened on the row under it.
     *
     * <p>The two characters are turned into a newline rather than being split on directly: as a
     * regular expression {@code "\\n"} <em>is</em> a newline, so splitting on the two characters the
     * text actually holds would never match — the same trap {@code HudBanner.lines} names, which is
     * also where the convention comes from. A command argument and a one-line text field can hold no
     * newline, so a written one is the only way to ask for a break.
     *
     * <p>Reopened, never closed, for the reason {@link #flush} gives at length: a closer this class
     * invented is a tag the player never typed, and one Ember has no closing form for is then drawn as
     * literal text. Reopening is what keeps the markup valid per row, each row being parsed on its own
     * — and it is why the typewriter sound around a broken profile retypes each row rather than
     * leaving its closer stranded on the last one.
     *
     * <p>A break the text already carried is treated the same way, so a caller cannot end up with a
     * tag open across a row boundary merely because the break came in with a substituted message. A
     * trailing one is dropped, as {@code ChatHandler.normalise} drops it from a message: it draws no
     * row of its own, and one left in would put the reopened tags of an open tag on a blank row.
     *
     * <p>A row left holding nothing but tags is then handed to a row that has text to draw, which is
     * the whole of {@link #carryTagOnlyRows}: such a row is the one row where parsing it on its own
     * gives a different answer from parsing the line it belongs to.
     */
    public static String withLineBreaks(String text) {
        if (text == null) return null;
        String trimmed = withoutTrailingBreak(text);
        if (!hasLineBreak(trimmed)) return trimmed;

        StringBuilder out = new StringBuilder(trimmed.length());
        Deque<String> open = new ArrayDeque<>();
        int from = 0;
        int i = 0;
        while (i < trimmed.length()) {
            int size = lineBreakAt(trimmed, i);
            if (size == 0) {
                i++;
                continue;
            }

            String row = trimmed.substring(from, i);
            out.append(row);
            trackOpenTags(open, row);
            out.append('\n');
            reopenTags(out, open);
            i += size;
            from = i;
        }
        return carryTagOnlyRows(out.append(trimmed, from, trimmed.length()).toString());
    }

    /**
     * {@return the text with the characters of every row that has no text moved to a row that has some}
     *
     * <p>Ember parses a literal as it draws it, and a literal whose tags wrap no text parses to no
     * spans at all — at which point Ember gives up on the markup and draws the characters of the tags
     * themselves. A chat line is one literal, parsed whole, so a row of nothing but tags draws nothing
     * there; a caller drawing the rows one literal at a time is handed that row on its own and gets the
     * markup back, which is the difference between the preview a menu shows and the line a player sees.
     * Handing the row's characters to the nearest row that has text — the next one, or the one above
     * when there is no next — is what makes the two agree.
     *
     * <p>Nothing is added and nothing is dropped. Every break is left exactly where it was, so the line
     * still breaks into the same rows a player sees, and the same characters merely end up on the other
     * side of a break - the copies a reopening had already written below it travelling with them. No
     * tag has to be understood for this to be right: a tag is never drawn where the line is parsed, and
     * where Ember does fall back to drawing the text, every word and every tag is still there, on the
     * row that has the text rather than alone on a row of its own.
     *
     * <p>Two questions are asked of a row here, and they are not the same one. Whether a row has text for a
     * tag to be handed to is {@link MarkupUtil#drawsNothing}: a tag, a blank and a control character all
     * draw no text — a control character is a mark, and the chat line's mark becomes an item, which is a
     * component no tag of that row reaches — so a text with no row but those is left exactly as the breaks
     * left it. Whether a row's own characters travel is {@link #tagOnly}, which is narrower: a row with a
     * control character on it has something written on it, and carrying that mark would take the item, and
     * the break above it, onto another row.
     */
    private static String carryTagOnlyRows(String text) {
        String[] rows = text.split("\n", -1);
        // Is there a row with text in it, which a row that draws nothing could hand its characters to?
        // Asked with MarkupUtil.drawsNothing rather than with tagOnly, because a row holding a control
        // character has no text in it: a control character is a mark, and what the chat line's mark stands
        // for — an item — is a component, which no tag of that row could wrap. Handing a row's tags to it
        // would put those tags on a row with nothing under them, which is the markup-drawn-as-characters
        // shape this method exists to avoid.
        boolean anyText = false;
        for (String row : rows) {
            if (!MarkupUtil.drawsNothing(row)) {
                anyText = true;
                break;
            }
        }
        if (!anyText) return text;

        // A row at the very end has no row below it to hand its characters to, so they go to the row
        // above instead and the break before it is left to draw the empty row the chat line draws there.
        int last = rows.length - 1;
        while (last > 0 && tagOnly(rows[last])) {
            rows[last - 1] = rows[last - 1] + rows[last];
            rows[last] = "";
            last--;
        }

        StringBuilder out = new StringBuilder(text.length());
        String carried = "";
        for (int k = 0; k < rows.length; k++) {
            if (k > 0) out.append('\n');
            String row = carried + rows[k];
            if (k < rows.length - 1 && tagOnly(row)) {
                // Nothing is drawn on this row at all, so its characters travel with the row below.
                carried = row;
                continue;
            }
            out.append(row);
            carried = "";
        }
        return out.toString();
    }

    /**
     * {@return true when this row has nothing written on it but its tags and its blanks}
     *
     * <p>The rows whose characters travel to a row that has words. A tag is spent on the words under it and
     * a blank draws an empty space, so a row of them is a row whose literal would draw the characters of
     * its tags rather than nothing — see {@link #carryTagOnlyRows} — and handing those characters to the
     * row below is what makes a caller that draws one literal per row draw what the whole line draws.
     *
     * <p>A control character is deliberately not one of these. It is a mark for something drawn elsewhere
     * — the chat line's item mark, which becomes a component — so a row it stands on is a row something is
     * written on, and its characters stay on the row they were written on: carrying them would take the
     * mark, and so the item, across a break. What such a row lacks is text for a tag to wrap, which is the
     * other question this file asks, and asked in {@link #carryTagOnlyRows} with
     * {@link MarkupUtil#drawsNothing}.
     */
    private static boolean tagOnly(String row) {
        return MarkupUtil.strip(row).isBlank();
    }

    /** {@return true when this text asks for a break at all}, written or already real */
    private static boolean hasLineBreak(String text) {
        return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.contains(LINE_BREAK);
    }

    /** {@return the text without the breaks at its end}, which draw no row of their own */
    private static String withoutTrailingBreak(String text) {
        int end = text.length();
        while (end > 0) {
            char c = text.charAt(end - 1);
            if (c == '\n' || c == '\r') {
                end--;
            } else if (end >= LINE_BREAK.length()
                    && text.startsWith(LINE_BREAK, end - LINE_BREAK.length())) {
                end -= LINE_BREAK.length();
            } else {
                break;
            }
        }
        return end == text.length() ? text : text.substring(0, end);
    }

    /** {@return how many characters the break at this index takes}, 0 when there is none */
    private static int lineBreakAt(String text, int index) {
        char c = text.charAt(index);
        if (c == '\n' || c == '\r') return 1;
        return text.startsWith(LINE_BREAK, index) ? LINE_BREAK.length() : 0;
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
            out.append(text.charAt(i));
            // Measured on the builder rather than on a copy with the character appended to it, which
            // would build a second string for every character tried. The question is the same one.
            if (measure.applyAsInt(out.toString()) > room) {
                out.setLength(out.length() - 1);
                break;
            }
        }
        return out + suffix;
    }

    /** {@return the longest prefix of {@code word} that still fits on the current line} */
    private static int charsThatFit(StringBuilder row, String word, int width,
                                    ToIntFunction<String> measure) {
        for (int n = word.length() - 1; n > 0; n--) {
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

    /**
     * Pushes and pops one tag on the stack of what is open, so the next row knows what to reopen.
     *
     * <p>The one reading of a tag, shared with the wrapping: a closer pops the tag it closes, a
     * self-closing tag neither opens nor closes anything, and everything else stays open until its own
     * closer arrives. Kept in one place because a second copy of this is how a reopened colour and a
     * wrapped one would come to disagree.
     */
    private static void trackOpenTags(Deque<String> open, String tag) {
        for (Token token : tokenise(tag)) {
            if (token.kind() != Kind.TAG) continue;

            String raw = token.raw();
            if (raw.startsWith("</")) {
                if (!open.isEmpty()) open.pop();
            } else if (!raw.endsWith("/>")) {
                open.push(raw);
            }
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
