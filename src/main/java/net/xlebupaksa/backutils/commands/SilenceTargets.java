package net.xlebupaksa.backutils.commands;

import net.xlebupaksa.backutils.data.SilenceKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The target list {@code /chat silence} takes, and the kind and value it may end with.
 *
 * <p>Read here rather than in the command because both halves are questions with answers a check can
 * ask without a game: which words a written line holds, and which of the names are players online.
 * What is left in the command is the shape of the argument tree and the output.
 *
 * <pre>
 * /chat silence &lt;names&gt; [&lt;kind&gt; [&lt;true|false&gt;]]
 * </pre>
 *
 * <p>A list is names separated by spaces or commas, so a line that silences several players is one
 * argument read whole rather than one argument per name — Brigadier has no argument that repeats, and
 * {@code EntityArgument} reads only selectors, so a plain list of names reaches a command as a
 * {@code greedyString} or not at all. {@code @a} is the one selector understood, meaning everyone
 * online; every other word is a name, so a selector this does not read names nobody and is refused by
 * name rather than dropped silently.
 *
 * <p>The kind is the last word of the line, or the second to last when a value follows it. A kind with no
 * value sets it, because typing a kind is what turning one on looks like and an operator who writes
 * {@code /chat silence Alice msg} means to silence; the value is there to turn one off again, which is the
 * only thing it is needed for. A one-word line is a name even when it is spelled like a kind, so a player
 * called {@code msg} can be silenced alone; a two-word line whose last word is a kind is read as the kind,
 * so such a player cannot be named in the same line as somebody else. A line that ends in {@code true} or
 * {@code false} after a word that is not a kind is read as a kind that was mistyped rather than as a list,
 * because "silence Alice localchat true" is a mistyped kind and not two players called {@code localchat}
 * and {@code true}.
 */
public final class SilenceTargets {

    /** The one selector a list understands in place of a name. */
    public static final String EVERYONE = "@a";

    /**
     * Words that mean yes and no, which is all {@code BoolArgumentType} reads too.
     *
     * <p>Spelled here rather than left to {@code BoolArgumentType}, which parses a reader rather than
     * a word and so cannot be asked about a word already cut out of a line.
     */
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    /**
     * One written target list, read.
     *
     * @param names       the names and selectors, in the order they were written
     * @param kind        the kind the line sets, or null when the line only asks
     * @param value       the value it sets, meaningful only beside a kind
     * @param unknownKind the word written where a kind belongs that is not one, or null
     */
    public record Parsed(List<String> names, SilenceKind kind, Boolean value, String unknownKind) {

        /** {@return true when this line sets a flag rather than asking what one is} */
        public boolean sets() {
            return kind != null;
        }
    }

    /** Which of the written names named somebody, and which named nobody. */
    public record Resolved(List<String> matched, List<String> unknown) {

        /**
         * {@return true when the line is refused whole}
         *
         * <p>One name that matched nobody refuses the list the name was written in: a command that
         * acted on the names that happened to exist would be a moderation change half applied, and
         * the half that was applied is the half nobody was told about.
         */
        public boolean refuses() {
            return !unknown.isEmpty();
        }
    }

    private SilenceTargets() {}

    /**
     * {@return a written list, with the kind and value off its end when it has them}
     *
     * <p>Nothing is written by reading a line, so a line that names nobody is refused later and whole:
     * the names are all known before the first row is touched.
     */
    public static Parsed parse(String raw) {
        List<String> words = words(raw);

        // A yes or no with a kind in front of it is the whole of a setting, and a yes or no without one is
        // a name that happens to be spelled like a value.
        if (words.size() >= 3) {
            Boolean value = bool(words.getLast());
            if (value != null) {
                SilenceKind kind = SilenceKind.parse(words.get(words.size() - 2));
                if (kind != null) {
                    return new Parsed(List.copyOf(words.subList(0, words.size() - 2)), kind, value,
                            null);
                }
                // A yes or no with something that is not a kind in front of it: reported as the
                // mistyped kind it is, because the alternative reading — two names, one of them
                // "true" — would answer a question nobody asked.
                return new Parsed(List.of(), null, null, words.get(words.size() - 2));
            }
        }

        // A kind as the last word, with a name in front of it, sets that kind and says nothing about its
        // value: the value left off means the flag is being turned on, which is what an operator who types
        // a kind means. One word alone is a name first, so a player called "msg" can still be silenced.
        if (words.size() >= 2) {
            SilenceKind kind = SilenceKind.parse(words.getLast());
            if (kind != null) {
                return new Parsed(List.copyOf(words.subList(0, words.size() - 1)), kind, Boolean.TRUE,
                        null);
            }
        }

        return new Parsed(List.copyOf(words), null, null, null);
    }

    /**
     * {@return which of the written names are players online, and which are nobody}
     *
     * <p>{@code @a} becomes every player online, in the order the server lists them. Names are matched
     * without regard to case, the way the rest of the game matches an account name, and the name
     * answered with is the account's own spelling rather than the one that was typed, so the row the
     * command writes is spelled the way every other row naming that player is.
     */
    public static Resolved resolve(List<String> names, List<String> online) {
        Map<String, String> known = new LinkedHashMap<>();
        for (String name : online) known.putIfAbsent(key(name), name);

        List<String> matched = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String name : names) {
            if (EVERYONE.equals(name)) {
                for (String player : online) {
                    if (!matched.contains(player)) matched.add(player);
                }
            } else {
                String player = known.get(key(name));
                if (player == null) {
                    if (!unknown.contains(name)) unknown.add(name);
                } else if (!matched.contains(player)) {
                    matched.add(player);
                }
            }
        }
        return new Resolved(List.copyOf(matched), List.copyOf(unknown));
    }

    /** {@return the words of a written line: split on commas and whitespace, blanks dropped} */
    public static List<String> words(String raw) {
        List<String> words = new ArrayList<>();
        if (raw == null) return words;

        StringBuilder word = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (isSeparator(c)) {
                if (word.length() > 0) {
                    words.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.append(c);
            }
        }
        if (word.length() > 0) words.add(word.toString());
        return words;
    }

    /**
     * {@return true when this character separates one name from the next}
     *
     * <p>Read here and not by a regular expression, so that what the command splits a list on, what
     * its suggestions call the end of a word and what the names are filtered with are one rule.
     */
    public static boolean isSeparator(char c) {
        return c == ',' || Character.isWhitespace(c);
    }

    /**
     * {@return what may be written next, after the whole words of a list that is still being typed}
     *
     * <p>After a kind, only the value. After a value that a kind stands in front of, nothing: the list
     * is over, and a word past it would be read as another name.
     *
     * <p>Otherwise it depends on whether the word being typed has been started, which is the difference
     * between a word that is finished and one that is half written: half a word is a name, and the names
     * are what its candidates are matched against while it is typed, however many names already stand in
     * front of it. A whole word behind the cursor is a name that is finished, and what follows a name is a
     * kind — the list can still be grown by typing another name by hand, but a suggestion of one more name
     * where the command's next word is a kind is a list an operator reads as the wrong argument.
     *
     * <p>The first word of all is a name, so the names online and {@code @a} are what it is offered.
     * A value with no kind in front of it is a name, so after one the kinds come as after any name.
     *
     * <p>Here rather than in the command because it is the same reading of a list that {@link #parse}
     * does, kept beside it: a suggestion that offered a kind where {@code parse} would read a name
     * would be offering a line that means something else.
     */
    public static List<String> following(List<String> written, Collection<String> online,
                                         boolean starting) {
        String last = written.isEmpty() ? "" : written.getLast();
        // A kind the line ends on, with a name in front of it, is a setting: what may follow is the value
        // that refines it, whether that word is being started or is half written. A lone word is a name
        // first, which is why the name has to be there.
        if (written.size() >= 2 && SilenceKind.parse(last) != null) return List.of(TRUE, FALSE);
        if (written.size() >= 3 && bool(last) != null
                && SilenceKind.parse(written.get(written.size() - 2)) != null) {
            return List.of();
        }

        // A word that has been started and not finished: it is a name being typed — the list may hold
        // several, and this is where a second one is found — or the kind that would end the line. Both are
        // offered, and Brigadier filters them to what has been written of that word.
        if (!starting) return written.isEmpty() ? everyone(online) : namesAndKinds(online);

        // A whole word behind the cursor. The first word of all is a name; after a name comes a kind, and
        // the names are deliberately not offered with it: there they are the third word of a command whose
        // third word is a kind, which a list reads as the wrong argument. Growing the list is a name typed
        // by hand, which the half-written case above is what suggests one for.
        return written.isEmpty() ? everyone(online) : kindWords();
    }

    /** {@return the players online and {@code @a}}, the two things the first word of a list may be */
    private static List<String> everyone(Collection<String> online) {
        List<String> candidates = new ArrayList<>(online);
        candidates.add(EVERYONE);
        return candidates;
    }

    /** {@return the players online and the kinds}, the two things a name still being typed may turn into */
    private static List<String> namesAndKinds(Collection<String> online) {
        List<String> candidates = new ArrayList<>(online);
        candidates.addAll(kindWords());
        return candidates;
    }

    /** {@return the kinds a line may set}, spelled as {@link SilenceKind#parse} reads them */
    public static List<String> kindWords() {
        List<String> candidates = new ArrayList<>(SilenceKind.values().length);
        for (SilenceKind kind : SilenceKind.values()) {
            candidates.add(kind.name().toLowerCase(Locale.ROOT));
        }
        return candidates;
    }

    /** {@return what a written word means, or null when it is not a yes or a no} */
    private static Boolean bool(String word) {
        if (TRUE.equalsIgnoreCase(word)) return Boolean.TRUE;
        if (FALSE.equalsIgnoreCase(word)) return Boolean.FALSE;
        return null;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
