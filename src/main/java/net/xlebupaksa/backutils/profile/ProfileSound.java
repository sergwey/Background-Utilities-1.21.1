package net.xlebupaksa.backutils.profile;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The sound a profile's text makes while it types itself out.
 * <p>The volume is fixed at {@link #VOLUME} for everyone, because a per-profile volume would hand its owner a loudness
 * knob for the whole channel. The palette exists because Ember plays any sound id from markup: an operator curates the
 * list with {@code /backutils sounds add}, a profile may only name one of those, and no client can talk around it.
 * <p>An entry is either a bare name — Ember's own preset, which Ember resolves itself — or a name of the operator's
 * choosing and the id it should play, stored {@code name=soundid}. A profile stores the <em>name</em>, so what a name
 * sounds like can be changed without touching a profile, and everything stored before the palette had names keeps
 * working as what it always was: a bare entry, handed to Ember exactly as it stands.
 */
public final class ProfileSound {

    public static final double VOLUME = 0.1D;

    /** Separates a name from the id it plays. Nothing needs escaping around it: a name cannot hold one and neither
     *  can a sound id, so the first one is always the name's end. */
    private static final char ALIAS = '=';

    /** Ember's built-in presets, names rather than ids so they survive a rename of the sound they map to, and the default
     *  palette as the one set guaranteed to exist wherever Ember does. */
    public static final List<String> PRESETS = List.of(
            "click", "whoosh", "static", "magic", "tick", "pop", "thud", "shatter");

    /** Names an entry may not take: the two words that already mean silence in the profile editor, and the one profile
     *  everybody has without writing it, whose sound is that same silence. */
    private static final List<String> RESERVED_NAMES = List.of("none", "off", "default");

    private ProfileSound() {}

    public static boolean isPreset(String value) {
        return value != null && PRESETS.contains(value.toLowerCase(Locale.ROOT));
    }

    /** {@return the name an entry answers to} — what a profile stores, and the part before {@code =} in an alias. */
    public static String nameOf(String entry) {
        if (entry == null) return "";
        int separator = entry.indexOf(ALIAS);
        return separator < 0 ? entry : entry.substring(0, separator);
    }

    /** {@return the id an entry plays} — a bare entry is its own id, which is how a preset reaches Ember untouched. */
    public static String idOf(String entry) {
        if (entry == null) return "";
        int separator = entry.indexOf(ALIAS);
        return separator < 0 ? entry : entry.substring(separator + 1);
    }

    /** {@return true when the entry carries an id of its own rather than naming an Ember preset} */
    public static boolean isAlias(String entry) {
        return entry != null && entry.indexOf(ALIAS) >= 0;
    }

    /** {@return the entry to store for a name and the sound it should play} */
    public static String alias(String name, String id) {
        return name + ALIAS + id;
    }

    /** {@return the names of a palette, in order and without repeats} — what a player chooses from, and what the
     *  editor is sent, since a player never sees an id it may not use. */
    public static List<String> names(List<String> palette) {
        List<String> names = new ArrayList<>(palette.size());
        for (String entry : palette) {
            String name = nameOf(entry);
            if (!names.contains(name)) names.add(name);
        }
        return List.copyOf(names);
    }

    /** {@return true when an entry in the palette already answers to this name} */
    public static boolean taken(String name, List<String> palette) {
        if (name == null || name.isBlank()) return false;
        for (String entry : palette) {
            if (nameOf(entry).equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** {@return the palette entry to store for this choice, or null when it is not allowed} — an empty string means "no
     *  sound", a choice in the wrong case still matches, and what is stored is the entry's spelling of its name. */
    public static String normalise(String sound, List<String> palette) {
        String trimmed = sound == null ? "" : sound.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("off")) {
            return "";
        }
        for (String entry : palette) {
            if (nameOf(entry).equals(trimmed)) return nameOf(entry);
        }
        for (String entry : palette) {
            if (nameOf(entry).equalsIgnoreCase(trimmed)) return nameOf(entry);
        }
        return null;
    }

    /** {@return null when the sound may be used, otherwise the reason to show the player} */
    public static String check(String sound, List<String> palette) {
        String trimmed = sound == null ? "" : sound.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("off")) {
            return null;
        }
        if (normalise(trimmed, palette) != null) return null;
        if (palette.isEmpty()) {
            return "No profile sounds are set up on this server.";
        }
        return "'" + trimmed + "' is not in this server's sound list: "
                + String.join(", ", names(palette)) + ".";
    }

    /**
     * {@return null when a palette entry may take this name, otherwise the reason to show}
     *
     * <p>A name is what a profile stores, so it has to be a word an operator can hand out and write in the config file
     * by hand: lower case and unbroken, the shape the palette's own values have always had. A word that already means
     * something to the editor — silence, or the profile everybody has — is refused rather than allowed to shadow it.
     */
    public static String checkName(String name, List<String> palette) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            return "Give the sound a name, such as bell.";
        }
        if (!isIdPart(value, "_-")) {
            return "'" + value + "' is not a usable name. Use lower-case letters, digits, - and _,"
                    + " with no spaces.";
        }
        if (RESERVED_NAMES.contains(value)) {
            if (value.equals("default")) {
                return "'default' is the profile every player has without writing one.";
            }
            return "'" + value + "' already means no sound in the profile editor.";
        }
        if (taken(value, palette)) {
            return "'" + value + "' is already one of the profile sounds.";
        }
        return null;
    }

    /** {@return null when the sound id may be stored, otherwise the reason to show} — empty is refused, since a name
     *  that plays nothing is what removing it is for. */
    public static String checkId(String id) {
        String value = id == null ? "" : id.trim();
        if (value.isEmpty()) {
            return "Give the sound id this name should play, such as minecraft:block.note_block.bell.";
        }
        return isSoundId(value) ? null
                : "'" + value + "' is not a sound id. Give one such as minecraft:block.note_block.bell,"
                        + " or a bare Ember preset.";
    }

    /**
     * {@return true when the value is shaped like a sound id}
     *
     * <p>{@link ResourceLocation#tryParse} and nothing else: whether a sound exists is decided where it is heard, on
     * the client, whose resourcepacks the server has never seen. The registry is deliberately not consulted, so an id
     * only a pack adds is as acceptable as a vanilla one — the rule the menu's own sound settings are checked by.
     */
    public static boolean isSoundId(String id) {
        return id != null && ResourceLocation.tryParse(id.trim()) != null;
    }

    /** {@return how one entry reads to an operator} — the name, what it plays, and, for a bare name, that Ember is the
     *  one resolving it. */
    public static String describe(String entry) {
        if (isAlias(entry)) return nameOf(entry) + " -> " + idOf(entry);
        return nameOf(entry) + (isPreset(entry) ? " -> (preset)" : " -> (bare)");
    }

    /**
     * {@return null when a staff-chosen sound may be used, otherwise the reason}
     * <p>The palette is deliberately not applied, since a staff-written format already reaches any sound id through raw
     * markup; what is checked is that the value is a sound, so a typo is not stored and silently played as nothing.
     */
    public static String checkStaff(String sound) {
        String trimmed = sound == null ? "" : sound.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("off")) {
            return null;
        }
        if (isPreset(trimmed)) return null;
        return looksLikeId(trimmed) ? null
                : "'" + trimmed + "' is not a sound id. Use a preset, or namespace:path.";
    }

    /** {@return the value to store for a staff choice} — the palette's own spelling when the choice names one of its
     *  entries, anything else as given. */
    public static String normaliseStaff(String sound, List<String> palette) {
        String fromPalette = normalise(sound, palette);
        if (fromPalette != null) return fromPalette;
        String trimmed = sound == null ? "" : sound.trim();
        return trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("off") ? "" : trimmed;
    }

    /**
     * {@return true when this is shaped like a sound id}
     * <p>Written out rather than parsed with {@code ResourceLocation} so the checks run without a game: lower case, with
     * an optional {@code namespace:path}.
     */
    private static boolean looksLikeId(String value) {
        int colon = value.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : value.substring(0, colon);
        String path = colon < 0 ? value : value.substring(colon + 1);
        if (namespace.isEmpty() || path.isEmpty()) return false;
        return isIdPart(namespace, "_-") && isIdPart(path, "_-./");
    }

    private static boolean isIdPart(String value, String extra) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean fine = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || extra.indexOf(c) >= 0;
            if (!fine) return false;
        }
        return !value.isEmpty();
    }

    /**
     * Wraps a finished line in Ember's typewriter effect with this profile's sound.
     *
     * <p>The stored name is resolved against the palette here, at the one place the markup is built: Ember cannot know
     * an operator's name for a sound, so a caller that left the name unresolved would hand Ember a name it reads as an
     * id and play nothing. The palette is an argument rather than read from the config here, so this class stays off
     * the config the way the rest of the package is and the translation can be exercised without a server.
     *
     * <p>{@link Locale#ROOT} keeps a decimal-comma locale from producing {@code volume=0,1}, which the parser reads as one
     * unparseable token and replaces with the default. {@code repeat=once} likewise matters: Ember's default,
     * {@code infinite}, would retype the line for as long as it is on screen.
     */
    public static String wrap(String text, String sound, double speed, double pitch, List<String> palette) {
        String resolved = resolve(sound, palette);
        if (resolved == null || resolved.isBlank()) return text;
        return "<typewriter sound=" + resolved
                + " volume=" + number(VOLUME)
                + " pitch=" + number(pitch)
                + " speed=" + number(speed)
                + " repeat=once>"
                + text
                + "</typewriter>";
    }

    /**
     * {@return what Ember should be given for a stored name}
     *
     * <p>An alias is looked up here and its id returned; a bare entry is returned as it stands, which is what keeps
     * Ember's own presets — and every id stored before the palette had names — playing exactly as they did. A name no
     * longer in the palette is returned as it stands as well: an existing profile keeps what it stored, and the client
     * either resolves it or plays nothing, rather than the server rewriting a profile nobody asked it to touch.
     */
    public static String resolve(String name, List<String> palette) {
        if (name == null || name.isBlank()) return name;
        for (String entry : palette) {
            if (nameOf(entry).equals(name)) return idOf(entry);
        }
        for (String entry : palette) {
            if (nameOf(entry).equalsIgnoreCase(name)) return idOf(entry);
        }
        return name;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
