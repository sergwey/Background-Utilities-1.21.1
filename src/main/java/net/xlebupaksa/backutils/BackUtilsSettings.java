package net.xlebupaksa.backutils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.profile.ProfileSound;

import java.nio.charset.StandardCharsets;
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
        /** One of a fixed set of words, which the menu draws a selector for. */
        CHOICE,
        /** A list, which the menu draws a row per entry for. */
        LIST
    }

    /** Where a setting belongs in the menu, which is what its sub-tab is. */
    public enum Group {
        CHAT("Chat"),
        ACTIONS("Actions"),
        LOG("Log"),
        MENU("Menu"),
        ROLLS("Rolls"),
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
     * {@link Kind#NUMBER}, {@code options} is what a {@link Kind#CHOICE} may be set to, and
     * {@code comment} is the config file's explanation, one line per entry.
     */
    public record Setting(String key, String label, Group group, Kind kind, String value,
                          double min, double max, List<String> options, List<String> comment) {}

    /** One row of the table; {@code normalise} tidies a text value or refuses it. */
    private record Entry(String key, String label, Group group, Kind kind,
                         ModConfigSpec.ConfigValue<?> spec,
                         Supplier<String> read, Consumer<String> write,
                         UnaryOperator<String> normalise, List<String> options) {}

    private static final String TYPING_PRESETS = String.join(", ", ProfileSound.PRESETS);

    /**
     * The banner's key, named rather than repeated because the command writes its text through the
     * banner's own rule by it — see {@link #checkBanner} — so the rule lives in one place and the
     * command's refusal is worded by the same code the menu is.
     */
    public static final String BANNER_TEXT_KEY = "bannerText";

    /**
     * The most the banner may be, measured in UTF-8 bytes.
     *
     * <p>The number is the wire's business rather than a taste of ours. The text reaches every
     * client in a payload whose codec is {@code ByteBufCodecs.STRING_UTF8}, which is
     * {@code writeUtf(value, 32767)}: that refuses a value of more than 32767 <em>characters</em>,
     * and separately refuses an encoded form longer than the reader will take, which Netty allows
     * three bytes per character for — 98301 bytes at this cap. It is sent on every change and again
     * to every player who joins, so a value past either bound is not one failed edit: it is a
     * failing encode for every modded client, on every join, for ever, until somebody corrects the
     * setting by hand.
     *
     * <p>One number in bytes covers both bounds, because the byte count of a string is never below
     * its character count: 4096 bytes is at most 4096 characters, so a value that passes here is
     * inside the character bound too, and leaves 28671 characters and 94205 bytes of the wire's own
     * room unused. That headroom is what covers the one place the two counts can disagree:
     * {@code getBytes} gives an unpaired surrogate one byte where the wire counts it as three, so
     * the wire's number is never the smaller of the two and cannot reach a bound this one is under.
     *
     * <p>4096 is generous for what this is — a line drawn on the HUD beside one button, not a
     * document — and a banner past it is refused rather than shortened, because a shortened banner
     * is a banner the operator did not write, drawn on everybody's screen with no sign that
     * anything was dropped.
     */
    private static final int MAX_BANNER_BYTES = 4096;

    /**
     * The channels a sound can play on, which is the game's own set: the client resolves the name, so
     * what is offered here has to be a name it can play on. Declared before the table, which one of its
     * rows offers them.
     */
    private static final List<String> SOUND_CHANNELS = java.util.Arrays.stream(SoundSource.values())
            .map(SoundSource::getName)
            .toList();

    /** The settings in the order the menu lists them: grouped by sub-tab, and in reading order. */
    private static final List<Entry> ENTRIES = List.of(            text("chatSeparator", "Local separator", Group.CHAT,
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
            yesNo("alertSelfActions", "Alert on own actions", Group.ACTIONS,
                    BackUtilsConfig.ALERT_SELF, BackUtilsConfig::isSelfAlertEnabled,
                    BackUtilsConfig::setSelfAlertEnabled),
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
            text("messagesLanguage", "Messages language", Group.LOG,
                    BackUtilsConfig.MESSAGES_LANGUAGE, BackUtilsConfig::getMessagesLanguage,
                    BackUtilsConfig::setMessagesLanguage, BackUtilsSettings::checkLanguage),

            text("menuMusic", "Menu music", Group.MENU,
                    BackUtilsConfig.MENU_MUSIC, BackUtilsConfig::getMenuMusic,
                    BackUtilsConfig::setMenuMusic, BackUtilsSettings::checkSoundId),
            number("menuMusicVolume", "Menu music volume", Group.MENU,
                    BackUtilsConfig.MENU_MUSIC_VOLUME, BackUtilsConfig::getMenuMusicVolume,
                    BackUtilsConfig::setMenuMusicVolume),
            choice("menuMusicChannel", "Menu music channel", Group.MENU,
                    BackUtilsConfig.MENU_MUSIC_CHANNEL, BackUtilsConfig::getMenuMusicChannel,
                    BackUtilsConfig::setMenuMusicChannel, SOUND_CHANNELS),

            // The banner hangs off the log's menu button, which is why it is a menu setting even
            // though what it belongs to is the log.
            text(BANNER_TEXT_KEY, "Banner text", Group.MENU,
                    BackUtilsConfig.BANNER_TEXT, BackUtilsConfig::getBannerText,
                    BackUtilsConfig::setBannerText, BackUtilsSettings::checkBanner),

            number("rollMax", "Default maximum", Group.ROLLS,
                    BackUtilsConfig.ROLL_MAX, () -> (double) BackUtilsConfig.getRollMax(),
                    value -> BackUtilsConfig.setRollMax((int) Math.round(value))),
            number("rollRadius", "Witness radius", Group.ROLLS,
                    BackUtilsConfig.ROLL_RADIUS, BackUtilsConfig::getRollRadius,
                    BackUtilsConfig::setRollRadius),
            text("rollFormat", "Roll line", Group.ROLLS,
                    BackUtilsConfig.ROLL_FORMAT, BackUtilsConfig::getRollFormat,
                    BackUtilsConfig::setRollFormat, MarkupUtil::balance),
            text("rollFormatNoReason", "Roll line, no reason", Group.ROLLS,
                    BackUtilsConfig.ROLL_FORMAT_NO_REASON, BackUtilsConfig::getRollFormatNoReason,
                    BackUtilsConfig::setRollFormatNoReason, MarkupUtil::balance),
            text("rollFormatAnonymous", "Roll line, nobody named", Group.ROLLS,
                    BackUtilsConfig.ROLL_FORMAT_ANONYMOUS, BackUtilsConfig::getRollFormatAnonymous,
                    BackUtilsConfig::setRollFormatAnonymous, MarkupUtil::balance),
            text("rollFormatAnonymousNoReason", "Roll line, nobody named, no reason", Group.ROLLS,
                    BackUtilsConfig.ROLL_FORMAT_ANONYMOUS_NO_REASON,
                    BackUtilsConfig::getRollFormatAnonymousNoReason,
                    BackUtilsConfig::setRollFormatAnonymousNoReason, MarkupUtil::balance),
            text("hiddenRollText", "Hidden roll result", Group.ROLLS,
                    BackUtilsConfig.ROLL_HIDDEN_TEXT, BackUtilsConfig::getHiddenRollText,
                    BackUtilsConfig::setHiddenRollText, MarkupUtil::balance),
            text("rollMinFormat", "Lowest roll line", Group.ROLLS,
                    BackUtilsConfig.ROLL_FORMAT_MIN, BackUtilsConfig::getRollMinFormat,
                    BackUtilsConfig::setRollMinFormat, MarkupUtil::balance),
            text("rollMaxFormat", "Highest roll line", Group.ROLLS,
                    BackUtilsConfig.ROLL_FORMAT_MAX, BackUtilsConfig::getRollMaxFormat,
                    BackUtilsConfig::setRollMaxFormat, MarkupUtil::balance),
            text("rollSound", "Roll sound", Group.ROLLS,
                    BackUtilsConfig.ROLL_SOUND, BackUtilsConfig::getRollSound,
                    BackUtilsConfig::setRollSound, BackUtilsSettings::checkSoundId),
            text("rollMinSound", "Lowest roll sound", Group.ROLLS,
                    BackUtilsConfig.ROLL_MIN_SOUND, BackUtilsConfig::getRollMinSound,
                    BackUtilsConfig::setRollMinSound, BackUtilsSettings::checkSoundId),
            text("rollMaxSound", "Highest roll sound", Group.ROLLS,
                    BackUtilsConfig.ROLL_MAX_SOUND, BackUtilsConfig::getRollMaxSound,
                    BackUtilsConfig::setRollMaxSound, BackUtilsSettings::checkSoundId),
            number("rollSoundVolume", "Roll sound volume", Group.ROLLS,
                    BackUtilsConfig.ROLL_SOUND_VOLUME, BackUtilsConfig::getRollSoundVolume,
                    BackUtilsConfig::setRollSoundVolume),
            number("rollSoundPitch", "Roll sound pitch", Group.ROLLS,
                    BackUtilsConfig.ROLL_SOUND_PITCH, BackUtilsConfig::getRollSoundPitch,
                    BackUtilsConfig::setRollSoundPitch),

            list("profileSounds", "Profile sounds (name=soundid or preset)", Group.PROFILES,
                    BackUtilsConfig.PROFILE_SOUNDS,
                    () -> String.join(", ", BackUtilsConfig.getProfileSounds()),
                    BackUtilsSettings::checkPalette,
                    BackUtilsSettings::writePalette)
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
        return new Entry(key, label, group, Kind.TEXT, spec, read, write, normalise, List.of());
    }

    private static Entry number(String key, String label, Group group,
                                ModConfigSpec.ConfigValue<?> spec, Supplier<Double> read,
                                Consumer<Double> write) {
        return new Entry(key, label, group, Kind.NUMBER, spec, () -> number(read.get()),
                value -> write.accept(parse(value)), UnaryOperator.identity(), List.of());
    }

    /** A switch the config file stores as {@code 0} or {@code 1} and the menu shows as on or off. */
    private static Entry yesNo(String key, String label, Group group,
                               ModConfigSpec.ConfigValue<?> spec, Supplier<Boolean> read,
                               Consumer<Boolean> write) {
        return new Entry(key, label, group, Kind.BOOL, spec,
                () -> String.valueOf(read.get()),
                value -> write.accept(Boolean.parseBoolean(value)),
                UnaryOperator.identity(), List.of());
    }

    /**
     * One of a fixed set of words, which the menu offers as a selector.
     *
     * <p>The options travel with the setting rather than being a list the client keeps of its own: the
     * set is the game's, so a client that hard-coded it would be wrong on a client that is not this one.
     */
    private static Entry choice(String key, String label, Group group,
                                ModConfigSpec.ConfigValue<?> spec, Supplier<String> read,
                                Consumer<String> write, List<String> options) {
        return new Entry(key, label, group, Kind.CHOICE, spec, read, write,
                UnaryOperator.identity(), List.copyOf(options));
    }

    /** A list the menu edits one row per entry, written back as one line of comma-separated entries. */
    private static Entry list(String key, String label, Group group,
                              ModConfigSpec.ConfigValue<?> spec, Supplier<String> read,
                              UnaryOperator<String> normalise, Consumer<String> write) {
        return new Entry(key, label, group, Kind.LIST, spec, read, write, normalise, List.of());
    }

    public static List<Setting> all() {
        List<Setting> settings = new ArrayList<>(ENTRIES.size());
        for (Entry entry : ENTRIES) {
            double[] range = rangeOf(entry);
            settings.add(new Setting(entry.key(), entry.label(), entry.group(), entry.kind(),
                    read(entry), range[0], range[1], entry.options(), commentOf(entry)));
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
            case CHOICE -> {
                String value = text.trim().toLowerCase(Locale.ROOT);
                if (!entry.options().contains(value)) {
                    throw new IllegalArgumentException("One of " + String.join(", ", entry.options())
                            + " — '" + value + "' is not one.");
                }
                return value;
            }
            case LIST -> {
                String value = entry.normalise().apply(text);
                return value == null ? "" : value;
            }
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

    /**
     * {@return the palette as one line, in the order it was given}, refusing an entry that is not a
     * {@code name=soundid} pair or a bare sound the palette may hold
     *
     * <p>Written the way the config file holds the list: entries separated by commas, each one either a
     * bare id — which is what an Ember preset is, its name being the id it resolves by — or a name and
     * the id that name plays. Blank entries are dropped, which is how a row of the menu's own list
     * removes one: a row the operator cleared is an entry they took away, and the same reading lets the
     * row under the last entry be where a new one is typed.
     *
     * <p>The checks are {@link ProfileSound}'s, the ones {@code /backutils typing_sounds add} runs, so
     * the menu and the command cannot come to accept different palettes.
     */
    static String checkPalette(String text) {
        List<String> entries = new ArrayList<>();
        for (String piece : (text == null ? "" : text).split(",")) {
            String entry = piece.trim();
            if (entry.isEmpty()) continue;

            boolean alias = ProfileSound.isAlias(entry);
            String problem = alias
                    ? ProfileSound.checkName(ProfileSound.nameOf(entry), entries)
                    : null;
            if (problem == null) problem = ProfileSound.checkId(ProfileSound.idOf(entry), entries);
            // Two entries answering to one name is what a profile cannot tell apart, and a bare entry's
            // name is the whole of it, which the id's own check does not look at.
            if (problem == null && ProfileSound.taken(ProfileSound.nameOf(entry), entries)) {
                problem = "'" + ProfileSound.nameOf(entry) + "' is already one of the profile sounds.";
            }
            if (problem != null) throw new IllegalArgumentException(problem);

            entries.add(entry);
        }
        return String.join(", ", entries);
    }

    /** Writes the palette a checked line holds; the line is already the list, one entry per comma. */
    private static void writePalette(String value) {
        BackUtilsConfig.setProfileSounds(List.of(value.split(",")));
    }

    /**
     * {@return the language the mod's own lines are written in}
     *
     * <p>A choice rather than free text: anything else would be read as English by the two places
     * that consult it, and would look like a setting that does nothing.
     */
    private static String checkLanguage(String text) {
        String value = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return BackUtilsConfig.MESSAGES_LANGUAGES.getFirst();
        if (!BackUtilsConfig.MESSAGES_LANGUAGES.contains(value)) {
            throw new IllegalArgumentException("'" + value + "' is not a language this mod writes"
                    + " in. One of " + String.join(", ", BackUtilsConfig.MESSAGES_LANGUAGES) + ".");
        }
        return value;
    }

    /**
     * {@return the banner's text, with only the whitespace a text field gathers at its edges
     * dropped}, refusing one longer than the payload can carry
     *
     * <p>Deliberately not {@link MarkupUtil#balance}: a closer the operator never typed is drawn as
     * the literal tag it is, which is the bug that put a visible {@code </typewriter>} at the end of
     * a banner whose own tag was simply never closed. Empty is allowed, and is the banner switched
     * off.
     *
     * <p>The length rule is here rather than at the wire because this is the one place both ways of
     * setting the banner meet: the command's {@code banner set} and the menu's editor both go
     * through {@link #check}, so a value one of them accepts is a value the other accepts, and both
     * refuse it in the same words. It is refused rather than shortened because a shortened banner is
     * a banner the operator did not write, drawn on everybody's screen with no sign that anything
     * was dropped. Counting bytes rather than characters is what makes the number safe to hold: the
     * codec bounds both, a text of 40 000 Cyrillic letters is 40 000 characters and 80 000 bytes,
     * and the byte count is never the smaller of the two — so one limit in bytes keeps the value
     * inside the character bound as well as its own.
     */
    private static String checkBanner(String text) {
        String value = text == null ? "" : text.trim();
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BANNER_BYTES) {
            throw new IllegalArgumentException("The banner is " + bytes + " bytes of UTF-8, and it"
                    + " may be at most " + MAX_BANNER_BYTES + ". A letter outside ASCII takes two"
                    + " bytes or more, so a text in Cyrillic or Chinese runs out sooner than its"
                    + " length suggests.");
        }
        return value;
    }

    /**
     * {@return the sound id to store for a setting this mod plays itself}
     *
     * <p>Only the shape is checked, never the sound registry. The id is resolved where it is heard: on
     * the client, whose resourcepacks the server has never seen and whose {@code sounds.json} registers
     * the events those packs add. A registry check on this side would therefore refuse exactly the
     * sounds an operator put in their own pack, which are the ones this setting is for as much as the
     * vanilla ones are - the server can only ever know about its own mods.
     *
     * <p>What the shape check still catches is a value that could never play at all: a name with a space
     * or an upper case letter in it is not an id. A typo that is shaped like one plays nothing, which is
     * heard the moment the menu opens rather than reported.
     *
     * <p>Empty is always allowed, and means no sound.
     */
    private static String checkSoundId(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return "";
        // The shape rule itself is ProfileSound's, where the profile palette is checked by it too, so
        // the two cannot drift apart into one place that consults the registry and one that does not.
        if (!ProfileSound.isSoundId(value)) {
            throw new IllegalArgumentException("'" + value
                    + "' is not a sound id. Give one such as minecraft:block.note_block.hat,"
                    + " or leave it empty for silence.");
        }
        return value;
    }

    /**
     * {@return the sound to store for the typing effect}
     *
     * <p>A bare word is left to Ember's presets; a namespaced id only has to be shaped like one, for the
     * reason {@link #checkSoundId} gives - it is resolved on the client, where a resourcepack's sounds
     * exist as much as the vanilla ones do.
     */
    private static String checkSound(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Give a sound id, or one of Ember's presets: " + TYPING_PRESETS + ".");
        }
        if (value.indexOf(':') >= 0 && ResourceLocation.tryParse(value) == null) {
            throw new IllegalArgumentException("'" + value + "' is not a sound id. Give one such as"
                    + " minecraft:block.note_block.hat, or one of Ember's presets: " + TYPING_PRESETS + ".");
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
