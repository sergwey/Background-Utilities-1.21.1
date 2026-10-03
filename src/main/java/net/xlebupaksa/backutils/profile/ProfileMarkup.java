package net.xlebupaksa.backutils.profile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The one place player-authored Ember markup is checked.
 *
 * <p>A chat profile's format is rendered on everyone's screen, and parts of Ember's language are
 * not decoration: {@code <click action=run_command>} attaches a real click event, {@code <hover>}
 * and {@code <item nbt=...>} build tooltips and parse SNBT, {@code <typewriter sound=...>} and the
 * {@code [sound]} family play any sound id for every viewer, and {@code <lang>} splices a
 * translation back into the markup before the tags are parsed.
 *
 * <p>So this is an allow-list, not a deny-list: Ember consumes an unknown tag silently, so a
 * deny-list would pass through every tag a future version adds. What is allowed is appearance
 * only — weight, slant, lines, colour, gradient, animations — and sound keys are refused even as
 * bare words.
 *
 * <p>An operator's format is held to its shape rather than to the tag table; see
 * {@link #validateStaff(String, boolean)}.
 *
 * <p>Plain Java with no Minecraft types: the client runs the same checks for live feedback while
 * typing, and the server runs them again as the authority.
 */
public final class ProfileMarkup {

    /** Where the player's message text goes. A format without it cannot say anything. */
    public static final String MESSAGE = ProfileText.MESSAGE;

    /** The player's own name, filled in before Ember ever sees the string. */
    public static final String PLAYER = ProfileText.PLAYER;

    /**
     * The wire's own string bound, and the only length a format has: a packet string of more than
     * 32767 UTF-8 bytes throws inside the codec, so checking the same bound here turns that into a
     * message instead. There is deliberately no shorter limit.
     */
    public static final int MAX_WIRE_BYTES = 32767;

    /** Player-only limits: animation tags are a nuisance to a channel, and deep nesting is costly. */
    public static final int MAX_TAGS = 24;
    public static final int MAX_DEPTH = 8;

    /** A cap rather than a rule about content: the list is a dropdown and each profile is a row. */
    public static final int MAX_PROFILES_PER_PLAYER = 16;

    /** The default inner text: the message, and nothing else. */
    public static final String DEFAULT_INNER = MESSAGE;

    /** One selectable colour. The hex is what the editor writes; Ember also accepts the names. */
    public record Colour(String name, String hex) {}

    /** The swatches, in the order the editor draws them. */
    public static final List<Colour> COLOURS = List.of(
            new Colour("Black", "#000000"),
            new Colour("Dark blue", "#0000AA"),
            new Colour("Dark green", "#00AA00"),
            new Colour("Dark aqua", "#00AAAA"),
            new Colour("Dark red", "#AA0000"),
            new Colour("Dark purple", "#AA00AA"),
            new Colour("Gold", "#FFAA00"),
            new Colour("Grey", "#AAAAAA"),
            new Colour("Dark grey", "#555555"),
            new Colour("Blue", "#5555FF"),
            new Colour("Green", "#55FF55"),
            new Colour("Aqua", "#55FFFF"),
            new Colour("Red", "#FF5555"),
            new Colour("Pink", "#FF55FF"),
            new Colour("Yellow", "#FFFF55"),
            new Colour("White", "#FFFFFF")
    );

    /** The vanilla names, accepted in markup because Ember also accepts them. */
    private static final Set<String> COLOUR_NAMES = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white");

    /** The tags a player may use, and the attribute names each may carry. */
    private static final Map<String, Set<String>> TAGS = buildTags();

    /** Attribute names that hold a colour, or a comma-separated list of them. */
    private static final Set<String> COLOUR_ATTRIBUTES = Set.of(
            "value", "color", "col", "c", "colors", "palette", "pal");

    /** Attribute names that take a number, so a word in one of them is a typo, not a value. */
    private static final Set<String> NUMBER_ATTRIBUTES = Set.of(
            "angle", "ang", "deg", "frequency", "freq", "f", "length", "len", "l",
            "amplitude", "amp", "a", "wavelength", "wlen", "phase", "p", "radius", "r", "rad",
            "offsetx", "dx", "x", "offsety", "dy", "y", "alpha", "minalpha", "min", "width", "w",
            "saturation", "sat", "brightness", "bright", "val", "shiftchance", "shift",
            "jitterchance", "jitter", "blinkchance", "blink", "offset", "off", "chromatic",
            "chrom", "chromthreshold", "slices", "base", "intensity", "int", "softness", "soft",
            "core", "rim", "pulsedepth", "flicker", "flickerspeed", "fspeed", "speed", "s",
            "fadewidth", "fw", "gap");

    /** Attribute names that are switched on or off. */
    private static final Set<String> BOOLEAN_ATTRIBUTES = Set.of("hue", "cyclic");

    /**
     * Ember's second markup language, in square brackets, refused as a whole: it is a separate
     * registry whose {@code [sound]}, {@code [drip]}, {@code [matrix]}, {@code [shatter]} and
     * {@code [slam]} play sounds, and every animation in it has an angle-bracket equivalent that
     * is already allowed. Listed by name rather than refused on sight, because a bracket that is
     * not one of these is left as literal text — "[ooc]" and "[1]" are things people write.
     */
    private static final Set<String> BRACKET_TAGS = Set.of(
            "sound", "drip", "matrix", "shatter", "slam", "rock", "breathe", "grow", "float",
            "drift", "vibrate", "spin", "slide", "fadein", "fadeout",
            "bg", "background", "scale", "offset", "anchor", "align", "shadow", "fade", "wrap",
            "clamp");

    /**
     * Keys that would let a tag play a sound, refused wherever they appear. Redundant while no
     * allowed tag has one — it is here for the day the tag table grows.
     */
    private static final Set<String> SOUND_KEYS = Set.of(
            "sound", "snd", "volume", "vol", "v", "pitch", "pit", "at", "soundmode", "sm",
            "cursor", "cursorchar", "cursorblink", "repeat", "resetdelay");

    /** The characters an attribute value may contain. No quotes, no tags, no section signs. */
    private static final String VALUE_CHARS = "abcdefghijklmnopqrstuvwxyz"
            + "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_#.,+-";

    private ProfileMarkup() {}

    private static Map<String, Set<String>> buildTags() {
        Map<String, Set<String>> tags = new LinkedHashMap<>();

        Set<String> none = Set.of();
        tags.put("bold", none);
        tags.put("b", none);
        tags.put("italic", none);
        tags.put("i", none);
        tags.put("underline", none);
        tags.put("u", none);
        tags.put("strikethrough", none);
        tags.put("s", none);

        tags.put("color", Set.of("value", "color", "col"));
        tags.put("c", Set.of("value", "color", "col"));

        tags.put("grad", Set.of("colors", "palette", "pal", "hue", "cyclic", "angle", "ang",
                "deg", "mode", "frequency", "freq", "f", "length", "len", "l"));
        tags.put("gradient", tags.get("grad"));

        tags.put("shake", Set.of("amplitude", "amp", "a", "frequency", "freq", "f"));
        tags.put("wave", Set.of("amplitude", "amp", "a", "frequency", "freq", "f",
                "wavelength", "wlen", "l"));
        tags.put("turb", Set.of("amplitude", "amp", "a", "frequency", "freq", "f"));
        tags.put("turbulence", tags.get("turb"));
        tags.put("wiggle", Set.of("amplitude", "amp", "a", "frequency", "freq", "f",
                "phase", "p"));
        tags.put("bounce", Set.of("amplitude", "amp", "a", "frequency", "freq", "f",
                "phase", "p"));
        tags.put("circle", Set.of("radius", "r", "rad", "frequency", "freq", "f", "phase", "p"));
        tags.put("pend", Set.of("frequency", "freq", "f", "angle", "ang", "deg",
                "radius", "r", "rad"));
        tags.put("pendulum", tags.get("pend"));
        tags.put("swing", Set.of("angle", "ang", "deg", "amp", "a", "frequency", "freq", "f",
                "phase", "p"));

        tags.put("rainbow", Set.of("frequency", "freq", "f", "phase", "p", "angle", "ang", "deg",
                "length", "len", "l", "saturation", "sat", "brightness", "bright", "val"));
        tags.put("rainb", tags.get("rainbow"));

        tags.put("glitch", Set.of("frequency", "freq", "f", "shiftchance", "shift",
                "jitterchance", "jitter", "blinkchance", "blink", "offset", "off", "chromatic",
                "chrom", "chromthreshold", "slices"));
        tags.put("scroll", Set.of("speed", "s", "width", "w", "clip", "fadewidth", "fw",
                "direction", "dir", "d", "gap"));
        tags.put("fade", Set.of("minalpha", "min", "frequency", "freq", "f", "phase", "p"));
        tags.put("pulse", Set.of("base", "amplitude", "amp", "a", "frequency", "freq", "f",
                "phase", "p", "colors", "palette", "pal", "hue"));
        tags.put("shadow", Set.of("offsetx", "dx", "x", "offsety", "dy", "y",
                "color", "col", "c", "alpha"));
        tags.put("outline", Set.of("width", "w", "color", "col", "c", "alpha"));
        tags.put("neon", Set.of("radius", "r", "rad", "intensity", "int", "softness", "soft",
                "core", "rim", "frequency", "freq", "f", "pulsedepth", "flicker",
                "flickerspeed", "fspeed", "color", "col", "c"));
        tags.put("glow", tags.get("neon"));

        return Map.copyOf(tags);
    }

    /** {@return a refusal} */
    public static Result refuse(String error) {
        return new Result(false, null, error);
    }

    /** {@return an acceptance carrying the value the caller should store} */
    public static Result accept(String value) {
        return new Result(true, value, null);
    }

    /**
     * The outcome of a check.
     *
     * @param value the value to store, or null when refused
     * @param error the reason to show, phrased for whoever typed it, or null when accepted
     */
    public record Result(boolean ok, String value, String error) {}

    // ------------------------------------------------------------------
    // Names
    // ------------------------------------------------------------------

    /**
     * Checks a profile name: letters, digits, spaces, {@code _} and {@code -}, and not
     * {@code default}. A name is a dropdown entry and a {@code /profile} argument, so markup,
     * control characters and quotes are refused rather than escaped. No length limit.
     */
    public static Result validateName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return refuse("Give the profile a name.");
        if (trimmed.equalsIgnoreCase("default")) {
            return refuse("'default' is reserved — it means \"no profile\".");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean fine = Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-';
            if (!fine) {
                return refuse("A name may only contain letters, digits, spaces, '_' and '-'.");
            }
        }
        // The same wire bound a format has: a profile name travels in the payload that stores it.
        int bytes = utf8Length(trimmed);
        if (bytes > MAX_WIRE_BYTES) {
            return refuse("Too long to store: " + bytes + " of " + MAX_WIRE_BYTES + " bytes.");
        }
        return accept(trimmed);
    }

    // ------------------------------------------------------------------
    // Building a format from the controls
    // ------------------------------------------------------------------

    /**
     * Builds the format the editor's simple controls describe, and checks it with {@link #validate}
     * — the inner text is part of the format, so the format is what has to be checked. Tag order is
     * fixed, colour outermost, because Ember's closers pop whatever is on top of its style stack.
     */
    public static Result compose(String colour, boolean bold, boolean italic,
                                 boolean underline, boolean strike, String inner) {
        Result built = composeTags(colour, bold, italic, underline, strike, inner);
        if (!built.ok()) return built;
        return validate(built.value());
    }

    /**
     * The same controls, checked the way an operator's format is.
     *
     * @param nameProfile whether this is a displayed name rather than a chat format, which decides
     *                    whether {@code {m}} is required and {@code {player}} allowed
     */
    public static Result composeStaff(String colour, boolean bold, boolean italic,
                                      boolean underline, boolean strike, String inner,
                                      boolean nameProfile) {
        Result built = composeTags(colour, bold, italic, underline, strike, inner);
        if (!built.ok()) return built;
        return validateStaff(built.value(), nameProfile);
    }

    /** {@return the tags the controls describe around the inner text, before any check} */
    private static Result composeTags(String colour, boolean bold, boolean italic,
                                      boolean underline, boolean strike, String inner) {
        String body = inner == null || inner.isBlank() ? DEFAULT_INNER : inner.trim();

        StringBuilder out = new StringBuilder();
        List<String> closers = new ArrayList<>();

        if (colour != null && !colour.isBlank()) {
            // col=, not value=: see rewriteTag — this is the form Ember renders.
            out.append("<color col=").append(colour.trim()).append('>');
            closers.add("</color>");
        }
        if (bold) {
            out.append("<bold>");
            closers.add("</bold>");
        }
        if (italic) {
            out.append("<italic>");
            closers.add("</italic>");
        }
        if (underline) {
            out.append("<underline>");
            closers.add("</underline>");
        }
        if (strike) {
            out.append("<strikethrough>");
            closers.add("</strikethrough>");
        }

        out.append(body);
        for (int i = closers.size() - 1; i >= 0; i--) out.append(closers.get(i));

        return accept(out.toString());
    }

    // ------------------------------------------------------------------
    // The check itself
    // ------------------------------------------------------------------

    /**
     * Parses a format and refuses anything not on the allow-list. A scan rather than a regex
     * because tags must balance — an unclosed {@code <color>} leaks its colour down the chat line —
     * and a closer has to match the tag it closes.
     */
    public static Result validate(String format) {
        return scan(format, false, false);
    }

    /**
     * Parses a format written by an operator, and refuses only what is wrong.
     *
     * <p>An operator is on the other side of the allow-list: {@code /profile} has always taken a raw
     * format at permission level 2, so applying the list here would only make the menus weaker than
     * the command line. No tag or nesting caps either. What is checked is shape — balanced, matching
     * tags, the wire's length, and {@code {m}} or {@code {player}} where each belongs — and an
     * unknown tag is left to Ember, which is the one place that knows what it has.
     *
     * @param nameProfile whether this is a displayed name rather than a chat format
     */
    public static Result validateStaff(String format, boolean nameProfile) {
        return scan(format, true, nameProfile);
    }

    /**
     * The one scan, shared by both checks.
     *
     * @param staff true to accept any well-formed tag, false to require the allow-list
     */
    private static Result scan(String format, boolean staff, boolean nameProfile) {
        if (format == null || format.isBlank()) return refuse("The format is empty.");
        // The wire's bound, and the only length check there is: see MAX_WIRE_BYTES. Checked in
        // bytes because that is what the codec counts, and an emoji is four of them.
        int bytes = utf8Length(format);
        if (bytes > MAX_WIRE_BYTES) {
            return refuse("Too long to store: " + bytes + " of " + MAX_WIRE_BYTES + " bytes.");
        }

        StringBuilder out = new StringBuilder(format.length());
        Deque<String> open = new ArrayDeque<>();
        int tagCount = 0;
        int messageCount = 0;
        int playerCount = 0;
        // Characters outside any tag, which is what a name has to have some of: a format of
        // nothing but tags draws nothing at all.
        int visible = 0;
        int i = 0;
        // Text is copied into `out` in chunks, up to each tag: that is what lets a tag be
        // rewritten on the way through without disturbing anything around it.
        int copied = 0;

        while (i < format.length()) {
            char c = format.charAt(i);

            if (c == '§') {
                return refuse("Section signs are not allowed.");
            }
            if (c == '\n' || c == '\r') {
                return refuse("Keep the format on one line.");
            }
            if (c < 0x20) {
                return refuse("Control characters are not allowed.");
            }

            if (c != '<' || i + 1 >= format.length()) {
                // The square-bracket language is refused for players as a whole; staff are
                // trusted with it, and Ember leaves any bracket it does not know as text.
                if (c == '[' && !staff) {
                    Result bracket = checkBracket(format, i);
                    if (bracket != null) return bracket;
                }
                messageCount += countMessage(format, i);
                playerCount += countPlayer(format, i);
                if (!Character.isWhitespace(c)) visible++;
                i++;
                continue;
            }

            char next = format.charAt(i + 1);
            if (!Character.isLetter(next) && next != '/') {
                // A literal '<' — "2 < 3" is text, not a tag, and Ember reads it that way too.
                i++;
                continue;
            }

            int end = findTagEnd(format, i);
            if (end < 0) return refuse("There is an unclosed '<'.");

            String body = format.substring(i + 1, end);
            boolean selfClosing = body.trim().endsWith("/");
            String effective = selfClosing
                    ? body.trim().substring(0, body.trim().length() - 1).trim()
                    : body.trim();

            Result tagResult = staff
                    ? checkStaffTag(effective, selfClosing, open, nameProfile)
                    : checkTag(effective, selfClosing, open);
            if (tagResult != null) return tagResult;

            out.append(format, copied, i + 1).append(rewriteTag(effective));
            if (selfClosing) out.append('/');
            copied = end;

            tagCount++;
            // Counted for players only. An operator's format is checked for its shape and the
            // wire, and how many tags it takes to say something is theirs to decide.
            if (!staff && tagCount > MAX_TAGS) {
                return refuse("More than " + MAX_TAGS + " tags.");
            }
            if (!staff && open.size() > MAX_DEPTH) {
                return refuse("Nested more than " + MAX_DEPTH + " deep.");
            }

            i = end + 1;
        }

        if (!open.isEmpty()) {
            return refuse("<" + open.peek() + "> is never closed.");
        }
        if (nameProfile) {
            if (messageCount > 0) {
                return refuse(MESSAGE + " means nothing in a name — use " + PLAYER + " for the "
                        + "player's name.");
            }
            if (visible == 0) {
                return refuse("A name profile needs some text in it, or " + PLAYER + ".");
            }
        } else if (!staff && messageCount == 0) {
            // A player's format has to say where their message goes — that is the one thing it is
            // for. An operator's need not: a format without it becomes a prefix with the message
            // appended after it, which is a deliberate thing to write and a visible one, since the
            // preview then shows the format with no message in it.
            return refuse("The format must contain " + MESSAGE + " — that is where your message "
                    + "goes.");
        }
        if (!staff && playerCount > 0) {
            // A profile styles the message and nothing else. The name in front of it belongs to
            // the name profile an administrator sets, so {player} here would either duplicate
            // that name or let a player write their own — neither of which is this feature.
            return refuse("A profile formats the message only — your name is not part of it.");
        }

        out.append(format, copied, format.length());
        return accept(out.toString());
    }

    /**
     * Rewrites a colour tag's attribute to the spelling Ember actually renders.
     *
     * <p>{@code <color value=…>} and {@code <color color=…>} are parsed into the span and then
     * dropped — Ember's {@code StyleUtil.applyTextSpanFormatting} applies weight, slant, lines,
     * fonts, effects and click events but never the colour — while {@code <color col=…>} becomes a
     * {@code ColorEffect}, whose colour is applied. Only the attribute name is touched, and only
     * when {@code col} is not already there.
     *
     * <p>{@code <grad>} is left alone: its colours belong in {@code colors=}.
     */
    private static String rewriteTag(String body) {
        int nameEnd = 0;
        while (nameEnd < body.length() && isNameChar(body.charAt(nameEnd))) nameEnd++;
        if (nameEnd == 0) return body;

        String name = body.substring(0, nameEnd).toLowerCase(Locale.ROOT);
        if (!name.equals("color") && !name.equals("c")) return body;

        String rest = body.substring(nameEnd);
        String lower = rest.toLowerCase(Locale.ROOT);
        if (lower.contains("col=") || lower.contains("col:")) return body;

        return body.substring(0, nameEnd) + rename(rest, "value|color", "col");
    }

    /**
     * {@return the attribute renamed, with {@code =} or {@code :} as the author wrote it}
     *
     * <p>Anchored to the start of a token, so an attribute whose name merely ends in one of these
     * is left alone.
     */
    private static String rename(String rest, String keys, String to) {
        String pattern = "(?i)(?<![A-Za-z0-9_-])(" + keys + ")";
        String rewritten = rest.replaceFirst(pattern + "=", to + "=");
        if (rewritten.equals(rest)) rewritten = rest.replaceFirst(pattern + ":", to + "=");
        return rewritten;
    }

    /** {@return how many {@code {m}} placeholders start at this index} */
    private static int countMessage(String format, int index) {
        if (!format.startsWith(MESSAGE, index)) return 0;
        return 1;
    }

    /** {@return how many {@code {player}} placeholders start at this index} */
    private static int countPlayer(String format, int index) {
        if (!format.startsWith(PLAYER, index)) return 0;
        return 1;
    }

    /**
     * {@return a refusal when this square bracket opens one of Ember's message tags}
     *
     * <p>An unrecognised bracket is left alone — "[ooc]" and "[1]" stay readable — and a recognised
     * one is refused: the family plays sounds, and nothing in it is worth the risk when the
     * angle-bracket animations cover the same ground.
     */
    private static Result checkBracket(String format, int index) {
        int end = format.indexOf(']', index + 1);
        if (end < 0) return null;

        String body = format.substring(index + 1, end).trim();
        if (body.isEmpty()) return null;

        // A closing form, [end], and the attribute form, [bg color=...], both start with the tag
        // name, so the first token is what to look up.
        int nameEnd = 0;
        while (nameEnd < body.length() && isNameChar(body.charAt(nameEnd))) nameEnd++;
        String name = body.substring(0, nameEnd).toLowerCase(Locale.ROOT);

        if (BRACKET_TAGS.contains(name)) {
            if (name.equals("sound")) return refuse("A profile cannot play sounds.");
            return refuse("[" + name + "] is not allowed — use <wave>, <rainbow> and the like.");
        }

        // Belt and braces: whatever the tag is called, a sound key inside it is a refusal.
        String rest = body.toLowerCase(Locale.ROOT);
        for (String key : SOUND_KEYS) {
            if (rest.contains(key + "=") || rest.endsWith(" " + key) || rest.equals(key)) {
                return refuse("A profile cannot play sounds.");
            }
        }
        return null;
    }

    /**
     * {@return null when the tag is allowed, otherwise the refusal}
     *
     * @param body        the tag's contents, without the angle brackets or a trailing slash
     * @param selfClosing whether the tag closed itself, so it never opens a style
     * @param open        the stack of currently open tags, pushed or popped in place
     */
    private static Result checkTag(String body, boolean selfClosing, Deque<String> open) {
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return refuse("Empty tag <>.");

        if (trimmed.charAt(0) == '/') {
            String name = trimmed.substring(1).trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) return refuse("Empty closing tag.");
            if (open.isEmpty()) {
                return refuse("</" + name + "> closes nothing.");
            }
            String top = open.peek();
            if (!top.equals(name)) {
                return refuse("</" + name + "> closes the wrong tag: <" + top + "> is open.");
            }
            open.pop();
            return null;
        }

        // <dur:1.5> is Ember's own timing helper: no attributes, no closing tag, no styling.
        int colon = trimmed.indexOf(':');
        if (colon > 0 && trimmed.substring(0, colon).equalsIgnoreCase("dur")) {
            String value = trimmed.substring(colon + 1).trim();
            if (!isNumber(value)) return refuse("<dur:...> takes a number of seconds.");
            return null;
        }

        int nameEnd = 0;
        while (nameEnd < trimmed.length() && isNameChar(trimmed.charAt(nameEnd))) nameEnd++;
        if (nameEnd == 0) return refuse("'" + trimmed + "' is not a tag name.");

        String name = trimmed.substring(0, nameEnd).toLowerCase(Locale.ROOT);
        Set<String> allowed = TAGS.get(name);
        if (allowed == null) {
            return refuse("<" + name + "> is not allowed. Colours, gradients, weight, slant, "
                    + "lines and animations are.");
        }

        String rest = trimmed.substring(nameEnd);
        Result attributes = checkAttributes(name, rest, allowed);
        if (attributes != null) return attributes;

        if (!selfClosing) open.push(name);
        return null;
    }

    /**
     * {@return null when an operator's tag is well formed, otherwise the refusal}
     *
     * <p>{@link #checkTag} with the allow-list removed: any tag name and any attribute, since what
     * the installed Ember understands is not something this can know. What is kept is whether the
     * line renders at all — the tag has a name, its closer matches it, the format is not left open.
     */
    private static Result checkStaffTag(String body, boolean selfClosing, Deque<String> open,
                                        boolean nameProfile) {
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return refuse("Empty tag <>.");

        if (trimmed.charAt(0) == '/') {
            String name = trimmed.substring(1).trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) return refuse("Empty closing tag.");
            if (open.isEmpty()) {
                return refuse("</" + name + "> closes nothing.");
            }
            String top = open.peek();
            if (!top.equals(name)) {
                return refuse("</" + name + "> closes the wrong tag: <" + top + "> is open.");
            }
            open.pop();
            return null;
        }

        // <dur:1.5> is Ember's own timing helper: no attributes, no closing tag, no styling.
        int colon = trimmed.indexOf(':');
        if (colon > 0 && trimmed.substring(0, colon).equalsIgnoreCase("dur")) return null;

        int nameEnd = 0;
        while (nameEnd < trimmed.length() && isNameChar(trimmed.charAt(nameEnd))) nameEnd++;
        if (nameEnd == 0) return refuse("'" + trimmed + "' is not a tag name.");

        if (nameProfile) {
            Result name = checkNameTag(trimmed);
            if (name != null) return name;
        }

        // No attribute check here, deliberately: a tag Ember does not know is left to Ember, and
        // so is a parameter <i>this</i> table does not know. A staff format is not the string the
        // allow-list exists to contain, and refusing an attribute Ember would merely ignore would
        // be the check guessing at a library it cannot see.
        if (!selfClosing) open.push(staffTagName(trimmed));
        return null;
    }

    /**
     * {@return the name of a tag, for the checks that cannot use the tag table}
     *
     * <p>A dash is part of the name here and not in {@link #isNameChar}: reading {@code <some-tag>}
     * as {@code some} would make its own closer look like the wrong tag.
     */
    private static String staffTagName(String body) {
        int end = 0;
        while (end < body.length()
                && (isNameChar(body.charAt(end)) || body.charAt(end) == '-')) {
            end++;
        }
        return body.substring(0, end).toLowerCase(Locale.ROOT);
    }

    /**
     * {@return the refusal when a tag has no business in a displayed name, or null}
     *
     * <p>A name is drawn on every nametag, in the tab list and in front of every line its owner
     * writes, so a typewriter or a sound there repeats for as long as the name is on screen.
     * Everything else Ember offers is allowed, as in a chat format written by an operator.
     */
    private static Result checkNameTag(String body) {
        String name = staffTagName(body);
        if (name.equals("typewriter")) {
            return refuse("A name does not type itself out — that belongs in a chat profile.");
        }

        // Checked token by token rather than by searching the whole body for "sound=": a
        // substring test would find the key inside an ordinary value, and a name is not the place
        // to refuse a colour because its attribute happens to end in the wrong letters.
        for (String token : splitAttributes(body.substring(name.length()))) {
            if (token.isEmpty()) continue;
            int split = indexOfAny(token, '=', ':');
            String key = split >= 0 ? token.substring(0, split) : token;
            if (SOUND_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                return refuse("A name cannot play sounds — it is drawn on every nametag.");
            }
        }
        return null;
    }

    /** {@return null when every attribute is allowed, otherwise the refusal} */
    private static Result checkAttributes(String tag, String rest, Set<String> allowed) {
        for (String token : splitAttributes(rest)) {
            if (token.isEmpty()) continue;

            String key = token;
            String value = null;
            int split = indexOfAny(token, '=', ':');
            if (split >= 0) {
                key = token.substring(0, split);
                value = unquote(token.substring(split + 1));
            }

            String lower = key.toLowerCase(Locale.ROOT);

            // Refused wherever they appear, without exception: these are the keys that make a
            // tag audible, and no allowed tag has a legitimate use for one. A bare word counts
            // too — Ember reads a bare token as "this parameter is on", so <typewriter sound>
            // would otherwise be a way to ask for a sound without writing '='.
            if (SOUND_KEYS.contains(lower)) {
                return refuse("'" + key + "' is not allowed — a profile may not play sounds.");
            }

            if (value == null) {
                // A bare word: an attribute that is switched on, as in <grad hue>, or a colour
                // written on its own, as in <color red>. Ember reads the first bare token of an
                // ordinary tag as its value, which is the colour case here.
                if (allowed.contains(lower)) continue;
                if (COLOUR_ATTRIBUTES.contains(lower) && looksLikeColour(key)) continue;
                return refuse("<" + tag + "> has no attribute called '" + key + "'.");
            }

            if (!allowed.contains(lower)) {
                return refuse("<" + tag + "> has no attribute called '" + key + "'.");
            }
            if (value.isEmpty()) return refuse("'" + key + "' has no value.");
            if (value.length() > 64) return refuse("'" + key + "' has an overlong value.");
            for (int i = 0; i < value.length(); i++) {
                if (VALUE_CHARS.indexOf(value.charAt(i)) < 0) {
                    return refuse("'" + key + "=" + value + "' contains a character a profile "
                            + "may not use. Quotes and tags are not allowed in a value.");
                }
            }
            if (COLOUR_ATTRIBUTES.contains(lower)) {
                if (!isColourList(value)) {
                    return refuse("'" + value + "' is not a colour. Use #rrggbb, or a name such "
                            + "as red, gold or aqua.");
                }
            } else if (BOOLEAN_ATTRIBUTES.contains(lower)) {
                if (!"true".equals(value) && !"false".equals(value)) {
                    return refuse("'" + key + "' is true or false.");
                }
            } else if (NUMBER_ATTRIBUTES.contains(lower)) {
                if (!isNumber(value)) {
                    return refuse("'" + key + "=" + value + "' should be a number.");
                }
            } else if (!isWord(value)) {
                return refuse("'" + key + "=" + value + "' is not a value this tag understands.");
            }
        }
        return null;
    }

    /** {@return true when the value is a colour, or a comma-separated list of them} */
    private static boolean isColourList(String value) {
        for (String part : value.split(",")) {
            if (!looksLikeColour(part.trim())) return false;
        }
        return true;
    }

    /** {@return true when this token is a colour: a hex value, or a vanilla colour name} */
    private static boolean looksLikeColour(String token) {
        String v = token.trim();
        if (v.isEmpty()) return false;

        String hex = v;
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
        if ((hex.length() == 3 || hex.length() == 6 || hex.length() == 8) && isHex(hex)) return true;

        return COLOUR_NAMES.contains(v.toLowerCase(Locale.ROOT));
    }

    private static boolean isHex(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), 16) < 0) return false;
        }
        return !s.isEmpty();
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty()) return false;
        boolean digit = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                digit = true;
            } else if (c != '.' && c != '-' && c != '+') {
                return false;
            }
        }
        return digit;
    }

    /** {@return true for a bare word, which is how Ember spells an enum or a boolean} */
    private static boolean isWord(String s) {
        if (s.isEmpty() || s.length() > 16) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetter(c) && c != '_') return false;
        }
        return true;
    }

    /**
     * {@return how many bytes this text takes in UTF-8}
     *
     * <p>What the packet codec counts, so what the one length check has to count: an emoji is one
     * character and four bytes.
     */
    private static int utf8Length(String text) {
        int bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) {
                bytes += 1;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    /** {@return the index of the first '>' that is not inside quotes, or -1} */
    private static int findTagEnd(String format, int start) {
        char quote = 0;
        for (int i = start + 1; i < format.length(); i++) {
            char c = format.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '>') return i;
        }
        return -1;
    }

    /** {@return the tag's attributes, split on whitespace but not inside quotes} */
    private static List<String> splitAttributes(String rest) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;

        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                current.append(c);
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    /** {@return the index of the first '=' or ':' in the token, or -1} */
    private static int indexOfAny(String token, char a, char b) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == a || c == b) return i;
        }
        return -1;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' || first == '\'') && first == last) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * A format that {@link #compose} produced, taken apart again.
     *
     * @param colour the hex or name the colour tag carries, or ""
     */
    public record Simple(String colour, boolean bold, boolean italic, boolean underline,
                         boolean strike, String inner) {}

    /**
     * {@return the controls a format was built from, or null when it was not built that way}
     *
     * <p>Only the exact shape {@link #compose} writes is recognised, in its exact order: guessing at
     * hand-written markup and showing it as toggles would silently rewrite the format. Anything else
     * opens in advanced mode, which changes nothing.
     */
    public static Simple splitSimple(String format) {
        return splitSimple(format, MESSAGE);
    }

    /**
     * The same, for a format whose placeholder is not the message.
     *
     * @param placeholder {@code {m}} for a chat format, {@code {player}} for a display name
     */
    public static Simple splitSimple(String format, String placeholder) {
        if (format == null || format.isBlank()) return null;

        int i = 0;
        String colour = "";
        if (format.startsWith("<color ", i)) {
            int end = format.indexOf('>', "<color ".length());
            if (end < 0) return null;

            // value=, color= or col=: the editor writes col=, and reading the other two back costs
            // one loop.
            String attributes = format.substring("<color ".length(), end).trim();
            String lower = attributes.toLowerCase(Locale.ROOT);
            for (String key : List.of("value=", "color=", "col=")) {
                if (lower.startsWith(key)) {
                    colour = attributes.substring(key.length()).trim();
                    break;
                }
            }
            if (colour.isEmpty() || !isColourList(colour)) return null;
            i = end + 1;
        }

        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;

        if (format.startsWith("<bold>", i)) {
            bold = true;
            i += "<bold>".length();
        }
        if (format.startsWith("<italic>", i)) {
            italic = true;
            i += "<italic>".length();
        }
        if (format.startsWith("<underline>", i)) {
            underline = true;
            i += "<underline>".length();
        }
        if (format.startsWith("<strikethrough>", i)) {
            strike = true;
            i += "<strikethrough>".length();
        }

        String closers = (strike ? "</strikethrough>" : "")
                + (underline ? "</underline>" : "")
                + (italic ? "</italic>" : "")
                + (bold ? "</bold>" : "")
                + (colour.isEmpty() ? "" : "</color>");

        if (!format.endsWith(closers) || format.length() < i + closers.length()) return null;
        String inner = format.substring(i, format.length() - closers.length());
        if (inner.isEmpty()) return null;
        // Text that is itself tagged is not what the controls would have produced, and splitting
        // it apart here would move the tags the player wrote.
        if (inner.indexOf('<') >= 0) return null;
        if (!inner.contains(placeholder)) return null;

        return new Simple(colour, bold, italic, underline, strike, inner);
    }
}
