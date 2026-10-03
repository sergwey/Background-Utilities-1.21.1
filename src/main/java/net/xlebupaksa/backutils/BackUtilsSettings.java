package net.xlebupaksa.backutils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.profile.ProfileSound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Every server setting the background menu can change, described once.
 *
 * <p>The menu needs a label, a group and the bounds for each setting. Those live here beside the
 * settings they describe and are sent to the client as they are — label, group, kind, current
 * value, bounds and the comment from the config file — so there is no second list on the client to
 * drift from this one, and a setting added here needs no client change beyond a widget for its kind.
 *
 * <p>{@link #check} and {@link #apply} are deliberately separate: checking is pure, so the client
 * can run it before sending and the server can run it again as the authority.
 */
public final class BackUtilsSettings {

    /** What a setting is, which is what decides the control the menu draws for it. */
    public enum Kind {
        BOOL,
        /** A number within the range the config file declares. */
        NUMBER,
        /** Free text: a separator, or a sound. */
        TEXT,
        /** A list, shown but not edited here. */
        LIST
    }

    /** Where a setting belongs in the menu, which is what its sub-tab is. */
    public enum Group {
        CHAT("Chat"),
        ACTIONS("Actions"),
        LOG("Log"),
        PROFILES("Profiles");

        private final String display;

        Group(String display) {
            this.display = display;
        }

        /** {@return the name of this group's sub-tab} */
        public String display() {
            return display;
        }
    }

    /**
     * One setting, as the menu sees it: {@code key} is the config key a change addresses,
     * {@code value} is the current value as text, {@code min} and {@code max} bound a
     * {@link Kind#NUMBER}, and {@code comment} is the config file's explanation, one line per entry.
     */
    public record Setting(String key, String label, Group group, Kind kind, String value,
                          double min, double max, List<String> comment) {}

    /** One row of the table; {@code normalise} tidies a text value or refuses it. */
    private record Entry(String key, String label, Group group, Kind kind,
                         ModConfigSpec.ConfigValue<?> spec,
                         Supplier<String> read, Consumer<String> write,
                         UnaryOperator<String> normalise) {}

    private static final String TYPING_PRESETS = String.join(", ", ProfileSound.PRESETS);

    /** The settings in the order the menu lists them: grouped by sub-tab, and in reading order. */
    private static final List<Entry> ENTRIES = List.of(
            text("chatSeparator", "Local separator", Group.CHAT,
                    BackUtilsConfig.CHAT_SEPARATOR, BackUtilsConfig::getSeparator,
                    BackUtilsConfig::setSeparator, MarkupUtil::balance),
            text("globalChatSeparator", "Global separator", Group.CHAT,
                    BackUtilsConfig.GLOBAL_CHAT_SEPARATOR, BackUtilsConfig::getGlobalSeparator,
                    BackUtilsConfig::setGlobalSeparator, MarkupUtil::balance),
            number("localChatRadius", "Local chat radius", Group.CHAT,
                    BackUtilsConfig.LOCAL_CHAT_RADIUS, BackUtilsConfig::getLocalChatRadius,
                    BackUtilsConfig::setLocalChatRadius),
            yesNo("localChatStaffOnly", "Local chat: staff only", Group.CHAT,
                    BackUtilsConfig.LOCAL_CHAT_RESTRICTED, BackUtilsConfig::isLocalChatStaffOnly,
                    BackUtilsConfig::setLocalChatStaffOnly),
            yesNo("globalChatStaffOnly", "Global chat: staff only", Group.CHAT,
                    BackUtilsConfig.GLOBAL_CHAT_RESTRICTED, BackUtilsConfig::isGlobalChatStaffOnly,
                    BackUtilsConfig::setGlobalChatStaffOnly),

            yesNo("roleplayActionsEnabled", "Record actions", Group.ACTIONS,
                    BackUtilsConfig.LOG_ACTIONS_ENABLED, BackUtilsConfig::isActionLoggingEnabled,
                    BackUtilsConfig::setActionLoggingEnabled),
            number("logRadius", "Witness radius", Group.ACTIONS,
                    BackUtilsConfig.LOG_RADIUS, BackUtilsConfig::getLogRadius,
                    BackUtilsConfig::setLogRadius),

            yesNo("typingEffect", "Typing effect", Group.LOG,
                    BackUtilsConfig.TYPING_ENABLED, BackUtilsConfig::isTypingEnabled,
                    BackUtilsConfig::setTypingEnabled),
            text("typingSound", "Typing sound", Group.LOG,
                    BackUtilsConfig.TYPING_SOUND, BackUtilsConfig::getTypingSound,
                    value -> BackUtilsConfig.setTypingSound(value, BackUtilsConfig.getTypingVolume(),
                            BackUtilsConfig.getTypingPitch()),
                    BackUtilsSettings::checkSound),
            number("typingVolume", "Typing volume", Group.LOG,
                    BackUtilsConfig.TYPING_VOLUME, BackUtilsConfig::getTypingVolume,
                    value -> BackUtilsConfig.setTypingSound(BackUtilsConfig.getTypingSound(),
                            value, BackUtilsConfig.getTypingPitch())),
            number("typingPitch", "Typing pitch", Group.LOG,
                    BackUtilsConfig.TYPING_PITCH, BackUtilsConfig::getTypingPitch,
                    value -> BackUtilsConfig.setTypingSound(BackUtilsConfig.getTypingSound(),
                            BackUtilsConfig.getTypingVolume(), value)),
            number("typingSpeed", "Typing speed", Group.LOG,
                    BackUtilsConfig.TYPING_SPEED, BackUtilsConfig::getTypingSpeed,
                    BackUtilsConfig::setTypingSpeed),

            list("profileSounds", "Profile sounds", Group.PROFILES,
                    BackUtilsConfig.PROFILE_SOUNDS,
                    () -> String.join(", ", BackUtilsConfig.getProfileSounds()))
    );

    private static final Map<String, Entry> BY_KEY = index();

    private BackUtilsSettings() {}

    // ------------------------------------------------------------------
    // The table
    // ------------------------------------------------------------------

    private static Map<String, Entry> index() {
        Map<String, Entry> index = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) index.put(entry.key(), entry);
        return Map.copyOf(index);
    }

    private static Entry text(String key, String label, Group group,
                              ModConfigSpec.ConfigValue<?> spec, Supplier<String> read,
                              Consumer<String> write, UnaryOperator<String> normalise) {
        return new Entry(key, label, group, Kind.TEXT, spec, read, write, normalise);
    }

    private static Entry number(String key, String label, Group group,
                                ModConfigSpec.ConfigValue<?> spec, Supplier<Double> read,
                                Consumer<Double> write) {
        return new Entry(key, label, group, Kind.NUMBER, spec, () -> number(read.get()),
                value -> write.accept(parse(value)), UnaryOperator.identity());
    }

    /** A switch the config file stores as {@code 0} or {@code 1} and the menu shows as on or off. */
    private static Entry yesNo(String key, String label, Group group,
                               ModConfigSpec.ConfigValue<?> spec, Supplier<Boolean> read,
                               Consumer<Boolean> write) {
        return new Entry(key, label, group, Kind.BOOL, spec,
                () -> String.valueOf(read.get()),
                value -> write.accept(Boolean.parseBoolean(value)),
                UnaryOperator.identity());
    }

    private static Entry list(String key, String label, Group group,
                              ModConfigSpec.ConfigValue<?> spec, Supplier<String> read) {
        return new Entry(key, label, group, Kind.LIST, spec, read, value -> { },
                UnaryOperator.identity());
    }

    public static List<Setting> all() {
        List<Setting> settings = new ArrayList<>(ENTRIES.size());
        for (Entry entry : ENTRIES) {
            double[] range = rangeOf(entry);
            settings.add(new Setting(entry.key(), entry.label(), entry.group(), entry.kind(),
                    read(entry), range[0], range[1], commentOf(entry)));
        }
        return List.copyOf(settings);
    }

    /** {@return the label of a setting, or the key itself when it is not one of this mod's} */
    public static String labelOf(String key) {
        Entry entry = BY_KEY.get(key);
        return entry == null ? (key == null ? "" : key) : entry.label();
    }

    private static String read(Entry entry) {
        try {
            String value = entry.read().get();
            return value == null ? "" : value;
        } catch (Exception e) {
            // A config that has not been loaded yet: the menu still draws, with the defaults.
            return "";
        }
    }

    private static double[] rangeOf(Entry entry) {
        try {
            ModConfigSpec.Range<?> range = entry.spec().getSpec().getRange();
            if (range == null) return new double[] {0.0D, 0.0D};
            Object min = range.getMin();
            Object max = range.getMax();
            if (!(min instanceof Number lower) || !(max instanceof Number upper)) {
                return new double[] {0.0D, 0.0D};
            }
            return new double[] {lower.doubleValue(), upper.doubleValue()};
        } catch (Exception e) {
            return new double[] {0.0D, 0.0D};
        }
    }

    private static List<String> commentOf(Entry entry) {
        try {
            String comment = entry.spec().getSpec().getComment();
            if (comment == null || comment.isBlank()) return List.of();
            List<String> lines = new ArrayList<>();
            for (String line : comment.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) lines.add(trimmed);
            }
            return List.copyOf(lines);
        } catch (Exception e) {
            return List.of();
        }
    }

    // ------------------------------------------------------------------
    // Checking a value
    // ------------------------------------------------------------------

    /**
     * {@return the value to store for this setting}
     *
     * @throws IllegalArgumentException with something to show the person who typed the value
     */
    public static String check(String key, String raw) {
        Entry entry = BY_KEY.get(key);
        if (entry == null) throw new IllegalArgumentException("Unknown setting '" + key + "'.");

        String text = raw == null ? "" : raw;
        switch (entry.kind()) {
            case BOOL -> {
                String value = text.trim();
                if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on")) return "true";
                if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off")) return "false";
                throw new IllegalArgumentException("Is it on or off?");
            }
            case NUMBER -> {
                double value = parse(text.trim());
                double[] range = rangeOf(entry);
                if (value < range[0] || value > range[1]) {
                    throw new IllegalArgumentException("Must be between " + number(range[0])
                            + " and " + number(range[1]) + ".");
                }
                return number(value);
            }
            case TEXT -> {
                // Not trimmed: a separator is usually spaces and markup, so the entry's own
                // normaliser decides what a value needs.
                String value = entry.normalise().apply(text);
                return value == null ? "" : value;
            }
            case LIST -> throw new IllegalArgumentException(
                    "Not changed here — use /backutils profiles sound add or remove.");
        }
        throw new IllegalArgumentException("Unknown kind for '" + key + "'.");
    }

    /**
     * Writes a value that has already been through {@link #check}; the only part that needs a
     * loaded config, and it holds no rules of its own.
     */
    public static void apply(String key, String value) {
        Entry entry = BY_KEY.get(key);
        if (entry == null) throw new IllegalArgumentException("Unknown setting '" + key + "'.");
        entry.write().accept(value);
    }

    /** {@return the sound to store for the typing effect}; a namespaced id is checked against the
     * sound registry, and a bare word is left to Ember's presets. */
    private static String checkSound(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Give a sound id, or one of Ember's presets: " + TYPING_PRESETS + ".");
        }
        if (value.indexOf(':') >= 0) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
                throw new IllegalArgumentException("No sound called '" + value
                        + "'. Give a sound id, or one of Ember's presets: " + TYPING_PRESETS + ".");
            }
        }
        return value;
    }

    private static double parse(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a number.");
        }
    }

    /**
     * {@return a number written the way the menu shows it}
     *
     * <p>The same formatting on both sides, so "24" and "24.0" cannot look like an unsaved change.
     */
    private static String number(double value) {
        if (!Double.isFinite(value)) return "0";
        if (value == Math.floor(value) && Math.abs(value) < 1.0e9D) {
            return String.valueOf((long) value);
        }
        String text = String.format(Locale.ROOT, "%.3f", value);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }
}
