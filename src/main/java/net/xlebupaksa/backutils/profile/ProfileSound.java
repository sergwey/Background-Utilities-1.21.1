package net.xlebupaksa.backutils.profile;

import java.util.List;
import java.util.Locale;

/**
 * The sound a profile's text makes while it types itself out.
 * <p>The volume is fixed at {@link #VOLUME} for everyone, because a per-profile volume would hand its owner a loudness
 * knob for the whole channel. The palette exists because Ember plays any sound id from markup: an operator curates the
 * list with {@code /backutils profiles sound add}, a profile may only name one of those, and no client can talk around it.
 */
public final class ProfileSound {

    public static final double VOLUME = 0.1D;

    /** Ember's built-in presets, names rather than ids so they survive a rename of the sound they map to, and the default
     *  palette as the one set guaranteed to exist wherever Ember does. */
    public static final List<String> PRESETS = List.of(
            "click", "whoosh", "static", "magic", "tick", "pop", "thud", "shatter");

    private ProfileSound() {}

    public static boolean isPreset(String value) {
        return value != null && PRESETS.contains(value.toLowerCase(Locale.ROOT));
    }

    /** {@return the palette entry to store for this choice, or null when it is not allowed} — an empty string means "no
     *  sound", and a choice in the wrong case still matches, stored in the palette's spelling. */
    public static String normalise(String sound, List<String> palette) {
        String trimmed = sound == null ? "" : sound.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("off")) {
            return "";
        }
        for (String entry : palette) {
            if (entry.equals(trimmed)) return entry;
        }
        for (String entry : palette) {
            if (entry.equalsIgnoreCase(trimmed)) return entry;
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
                + String.join(", ", palette) + ".";
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
     * <p>{@link Locale#ROOT} keeps a decimal-comma locale from producing {@code volume=0,1}, which the parser reads as one
     * unparseable token and replaces with the default. {@code repeat=once} likewise matters: Ember's default,
     * {@code infinite}, would retype the line for as long as it is on screen.
     */
    public static String wrap(String text, String sound, double speed, double pitch) {
        if (sound == null || sound.isBlank()) return text;
        return "<typewriter sound=" + sound
                + " volume=" + number(VOLUME)
                + " pitch=" + number(pitch)
                + " speed=" + number(speed)
                + " repeat=once>"
                + text
                + "</typewriter>";
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
