package net.xlebupaksa.backutils;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.xlebupaksa.backutils.profile.ProfileSound;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class BackUtilsConfig {

    /**
     * The defaults of the settings whose value is a line of markup or a sound id, named so the
     * getters below cannot drift from the {@code define} calls they mirror. A config that has not
     * loaded yet reads as its default, and a roll dressed in nothing at all would be worse than one
     * dressed wrongly.
     */
    private static final String ROLL_FORMAT_DEFAULT =
            "{player_possessive} dice rolled {result}/{max_roll} for {reason}!";
    private static final String ROLL_FORMAT_NO_REASON_DEFAULT =
            "{player_possessive} dice rolled {result}/{max_roll}!";
    /** The same two lines in Russian, for a server whose own lines are written in it. */
    private static final String ROLL_FORMAT_RU =
            "{player} бросает кубик: {result}/{max_roll} — {reason}!";
    private static final String ROLL_FORMAT_NO_REASON_RU =
            "{player} бросает кубик: {result}/{max_roll}!";
    /**
     * The same pair again for a roll that names nobody: there is no player such a line could belong
     * to, so it is worded on its own rather than as the named line with its name left out.
     */
    private static final String ROLL_FORMAT_ANONYMOUS_DEFAULT =
            "Dice rolled {result}/{max_roll} for {reason}!";
    private static final String ROLL_FORMAT_ANONYMOUS_NO_REASON_DEFAULT =
            "Dice rolled {result}/{max_roll}!";
    private static final String ROLL_FORMAT_ANONYMOUS_RU =
            "На кубике выпало: {result}/{max_roll} — {reason}!";
    private static final String ROLL_FORMAT_ANONYMOUS_NO_REASON_RU =
            "На кубике выпало: {result}/{max_roll}!";
    private static final String HIDDEN_ROLL_TEXT_DEFAULT = "<obfuscate>???</obfuscate>";
    private static final String MIN_ROLL_FORMAT =
            "<color col=#ff0000><shake><glitch>{line}</glitch></shake></color>";
    private static final String MAX_ROLL_FORMAT = "<neon><rainbow>{line}</rainbow></neon>";
    private static final String ROLL_SOUND_DEFAULT = "minecraft:block.note_block.hat";
    private static final String MIN_ROLL_SOUND_DEFAULT = "minecraft:block.anvil.land";
    private static final String MAX_ROLL_SOUND_DEFAULT = "minecraft:entity.player.levelup";
    private static final String MENU_MUSIC_DEFAULT = "minecraft:music.menu";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> CHAT_SEPARATOR = BUILDER
            .comment("String placed between the player's displayed name and the message in",
                    "LOCAL chat.")
            .define("chatSeparator", " — ");

    public static final ModConfigSpec.ConfigValue<String> GLOBAL_CHAT_SEPARATOR = BUILDER
            .comment("The same, for GLOBAL chat - the '!' channel. Keeping them apart lets",
                    "the two channels be told apart at a glance.")
            .define("globalChatSeparator", " » ");

    // ------------------------------------------------------------------
    // Roleplay action log
    // ------------------------------------------------------------------

    public static final ModConfigSpec.DoubleValue LOG_RADIUS = BUILDER
            .comment("How far away a player may be and still witness an action. Everyone",
                    "within this distance is added to the entry's access list.")
            .defineInRange("logRadius", 24.0D, 0.0D, 512.0D);

    public static final ModConfigSpec.IntValue LOG_ACTIONS_ENABLED = BUILDER
            .comment("Whether *asterisk* messages and /me are recorded in the roleplay log.",
                    "0 = off, 1 = on.")
            .defineInRange("roleplayActionsEnabled", 1, 0, 1);

    public static final ModConfigSpec.IntValue ALERT_SELF = BUILDER
            .comment("Whether an operator is alerted about their own actions as well as",
                    "everyone else's. 0 = other operators only, 1 = the actor too.")
            .defineInRange("alertSelfActions", 1, 0, 1);

    // ------------------------------------------------------------------
    // Dice rolls
    // ------------------------------------------------------------------

    public static final ModConfigSpec.IntValue ROLL_MAX = BUILDER
            .comment("The maximum a roll may come up, used whenever the command does not give",
                    "one of its own.")
            .defineInRange("rollMax", 100, 1, 1000000);

    public static final ModConfigSpec.DoubleValue ROLL_RADIUS = BUILDER
            .comment("How far away a player may be and still witness a roll. Everyone within",
                    "this distance of the player being rolled for is added to the entry's",
                    "access list.")
            .defineInRange("rollRadius", 24.0D, 0.0D, 512.0D);

    /**
     * The line a roll leaves in the log, for a roll that names somebody. Written with placeholders
     * rather than composed in code, so an operator can reword it without a new build:
     * {@code {player}} is the player being rolled for, {@code {player_possessive}} the same with an
     * {@code 's} after it, {@code {result}} the number rolled, {@code {max_roll}} the maximum, and
     * {@code {reason}} whatever the command was given.
     *
     * <p>A roll that names nobody is worded by {@link #ROLL_FORMAT_ANONYMOUS}, so the player
     * placeholders come out empty only in a hand-written line that puts them in one.
     */
    public static final ModConfigSpec.ConfigValue<String> ROLL_FORMAT = BUILDER
            .comment("The log line for a roll that has a reason. Placeholders: {player},",
                    "{player_possessive}, {result}, {max_roll}, {reason}.",
                    "Ember's markup tags work here, so the line can be coloured or styled.")
            .define("rollFormat", ROLL_FORMAT_DEFAULT);

    public static final ModConfigSpec.ConfigValue<String> ROLL_FORMAT_NO_REASON = BUILDER
            .comment("The same, for a roll given no reason. A separate line rather than an",
                    "empty {reason}, which would leave 'for !' behind.")
            .define("rollFormatNoReason", ROLL_FORMAT_NO_REASON_DEFAULT);

    /**
     * The line for a roll that names nobody. The player placeholders resolve to nothing here, and
     * borrowing the named line would leave it reading as a sentence with its subject cut off, so
     * this is a wording of its own.
     */
    public static final ModConfigSpec.ConfigValue<String> ROLL_FORMAT_ANONYMOUS = BUILDER
            .comment("The log line for a roll that names nobody. Placeholders: {result},",
                    "{max_roll}, {reason}; the player placeholders are empty in this line.",
                    "Ember's markup tags work here, so the line can be coloured or styled.")
            .define("rollFormatAnonymous", ROLL_FORMAT_ANONYMOUS_DEFAULT);

    public static final ModConfigSpec.ConfigValue<String> ROLL_FORMAT_ANONYMOUS_NO_REASON = BUILDER
            .comment("The same, for such a roll given no reason. A separate line rather than an",
                    "empty {reason}, which would leave 'for !' behind.")
            .define("rollFormatAnonymousNoReason", ROLL_FORMAT_ANONYMOUS_NO_REASON_DEFAULT);

    /**
     * What stands in for the result on a hidden roll. The default is Ember's own obfuscate tag, so
     * the number is not merely unreadable but scrambled as it is drawn; the text inside it is what
     * shows if the effect is ever unavailable. The long spelling is the one that carries the
     * effect: {@code <obf>} is parsed and then does nothing.
     */
    public static final ModConfigSpec.ConfigValue<String> ROLL_HIDDEN_TEXT = BUILDER
            .comment("Shown instead of {result} on a hidden roll. The default scrambles it with",
                    "Ember's <obfuscate> tag; the short <obf> spelling carries no effect, and",
                    "plain text such as ??? works too.")
            .define("hiddenRollText", HIDDEN_ROLL_TEXT_DEFAULT);

    /**
     * The line as it reads on the lowest and the highest roll, wrapped around the ordinary line so
     * that the reason is worded in one place: {@code {line}} is what {@code rollFormat} produced.
     */
    public static final ModConfigSpec.ConfigValue<String> ROLL_FORMAT_MIN = BUILDER
            .comment("Wraps the roll line when the lowest number comes up. {line} is that line.",
                    "The default is red, shaking and glitching. Leave it empty for no treatment.")
            .define("rollMinFormat", MIN_ROLL_FORMAT);

    public static final ModConfigSpec.ConfigValue<String> ROLL_FORMAT_MAX = BUILDER
            .comment("The same for the highest number. The default is neon rainbow.")
            .define("rollMaxFormat", MAX_ROLL_FORMAT);

    public static final ModConfigSpec.ConfigValue<String> ROLL_SOUND = BUILDER
            .comment("Played to everyone who witnesses the roll. A full sound id, such as",
                    "minecraft:block.note_block.hat; leave it empty for silence.")
            .define("rollSound", ROLL_SOUND_DEFAULT);

    public static final ModConfigSpec.DoubleValue ROLL_SOUND_VOLUME = BUILDER
            .comment("Volume of the roll sounds, 0 to 2.")
            .defineInRange("rollSoundVolume", 0.5D, 0.0D, 2.0D);

    public static final ModConfigSpec.DoubleValue ROLL_SOUND_PITCH = BUILDER
            .comment("Pitch of the roll sounds, 0.5 to 2.")
            .defineInRange("rollSoundPitch", 1.0D, 0.5D, 2.0D);

    public static final ModConfigSpec.ConfigValue<String> ROLL_MIN_SOUND = BUILDER
            .comment("Played instead, when the lowest number comes up.")
            .define("rollMinSound", MIN_ROLL_SOUND_DEFAULT);

    public static final ModConfigSpec.ConfigValue<String> ROLL_MAX_SOUND = BUILDER
            .comment("Played instead, when the highest number comes up.")
            .define("rollMaxSound", MAX_ROLL_SOUND_DEFAULT);

    // ------------------------------------------------------------------
    // The client menus
    // ------------------------------------------------------------------

    /** The languages the mod's own lines can be written in. */
    public static final List<String> MESSAGES_LANGUAGES = List.of("en_us", "ru_ru");

    /**
     * Which language the lines this mod writes into the log are worded in.
     *
     * <p>Only the mod's own wording: a format that has been edited by hand is used exactly as it was
     * written whatever this says, and the notices a player is sent are translated by that player's
     * own client, which needs no setting at all.
     */
    public static final ModConfigSpec.ConfigValue<String> MESSAGES_LANGUAGE = BUILDER
            .comment("The language of the lines this mod words itself: the four roll",
                    "formats below, named or actorless, each with and without a reason,",
                    "while they are still as they shipped. One of en_us, ru_ru. A format",
                    "edited by hand is used as written whichever this is.")
            .define("messagesLanguage", "en_us");

    /**
     * The music the corner menu plays for whoever opens it. A full sound id, or empty for silence:
     * the client resolves it, so a track from a resourcepack works as well as a vanilla one.
     */
    public static final ModConfigSpec.ConfigValue<String> MENU_MUSIC = BUILDER
            .comment("Played on a loop for the player who opens the corner menu, and faded out",
                    "when they close it. A full sound id, such as minecraft:music.menu;",
                    "leave it empty to play nothing.")
            .define("menuMusic", MENU_MUSIC_DEFAULT);

    public static final ModConfigSpec.DoubleValue MENU_MUSIC_VOLUME = BUILDER
            .comment("Volume of that music, 0 to 2. It plays on the music channel, so the",
                    "player's own music slider still applies.")
            .defineInRange("menuMusicVolume", 1.0D, 0.0D, 2.0D);

    // ------------------------------------------------------------------
    // Typewriter effect for log entries
    // ------------------------------------------------------------------

    public static final ModConfigSpec.IntValue TYPING_ENABLED = BUILDER
            .comment("Whether new log entries are revealed as if being typed.",
                    "0 = off, 1 = on.")
            .defineInRange("typingEffect", 1, 0, 1);

    public static final ModConfigSpec.ConfigValue<String> TYPING_SOUND = BUILDER
            .comment("Sound played per typed character. Either a full sound id",
                    "('minecraft:block.note_block.snare') or one of Ember's presets:",
                    "click, whoosh, static, magic, tick, pop, thud, shatter.",
                    "Ember itself has no default here — without a sound the typing is silent.")
            .define("typingSound", "minecraft:block.note_block.snare");

    public static final ModConfigSpec.DoubleValue TYPING_VOLUME = BUILDER
            .comment("Volume of the typing sound.")
            .defineInRange("typingVolume", 0.3D, 0.0D, 2.0D);

    public static final ModConfigSpec.DoubleValue TYPING_PITCH = BUILDER
            .comment("Pitch of the typing sound.")
            .defineInRange("typingPitch", 1.5D, 0.5D, 2.0D);

    public static final ModConfigSpec.DoubleValue TYPING_SPEED = BUILDER
            .comment("Typing speed in characters per second. Ember's own default is 12.")
            .defineInRange("typingSpeed", 12.0D, 0.1D, 1000.0D);

    // ------------------------------------------------------------------
    // Chat channels
    // ------------------------------------------------------------------

    public static final ModConfigSpec.DoubleValue LOCAL_CHAT_RADIUS = BUILDER
            .comment("How far away a player may be and still hear local chat. A message",
                    "starting with '!' goes to global chat instead and ignores this.")
            .defineInRange("localChatRadius", 24.0D, 0.0D, 512.0D);

    public static final ModConfigSpec.IntValue GLOBAL_CHAT_RESTRICTED = BUILDER
            .comment("When on, only players with permission level 2 or above may use global",
                    "chat. 0 = everyone, 1 = staff only.")
            .defineInRange("globalChatStaffOnly", 0, 0, 1);

    public static final ModConfigSpec.IntValue LOCAL_CHAT_RESTRICTED = BUILDER
            .comment("When on, only players with permission level 2 or above may use local",
                    "chat. 0 = everyone, 1 = staff only.")
            .defineInRange("localChatStaffOnly", 0, 0, 1);

    // ------------------------------------------------------------------
    // Profile sounds
    // ------------------------------------------------------------------

    /**
     * The sounds a profile may play as its text types out, and the only ones it may use: either
     * Ember's preset names (click, whoosh, static, magic, tick, pop, thud, shatter) or a full sound
     * id such as {@code minecraft:block.note_block.bell}. An operator adds to it with
     * {@code /backutils profiles sound add <sound>}.
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PROFILE_SOUNDS = BUILDER
            .comment("Sounds a chat profile may be set to play as its text types out. Players",
                    "choose from this list in the profile editor; anything not on it is",
                    "refused by the server. Both Ember presets (click, pop, tick, ...) and full",
                    "sound ids (minecraft:block.note_block.bell) are allowed. The volume is",
                    "fixed at 0.1 for every profile.")
            .defineListAllowEmpty("profileSounds", ProfileSound.PRESETS, () -> "click",
                    sound -> sound instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BackUtilsConfig() {}

    public static String getSeparator() {
        try {
            return CHAT_SEPARATOR.get();
        } catch (Exception e) {
            return " — ";
        }
    }

    public static void setSeparator(String value) {
        CHAT_SEPARATOR.set(value);
        CHAT_SEPARATOR.save();
    }

    public static String getGlobalSeparator() {
        try {
            return GLOBAL_CHAT_SEPARATOR.get();
        } catch (Exception e) {
            return " » ";
        }
    }

    public static void setGlobalSeparator(String value) {
        GLOBAL_CHAT_SEPARATOR.set(value);
        GLOBAL_CHAT_SEPARATOR.save();
    }

    public static String separatorFor(boolean global) {
        return global ? getGlobalSeparator() : getSeparator();
    }

    public static double getLogRadius() {
        try {
            return LOG_RADIUS.get();
        } catch (Exception e) {
            return 24.0D;
        }
    }

    public static void setLogRadius(double value) {
        LOG_RADIUS.set(value);
        LOG_RADIUS.save();
    }

    public static boolean isActionLoggingEnabled() {
        try {
            return LOG_ACTIONS_ENABLED.get() != 0;
        } catch (Exception e) {
            return true;
        }
    }

    public static void setActionLoggingEnabled(boolean enabled) {
        LOG_ACTIONS_ENABLED.set(enabled ? 1 : 0);
        LOG_ACTIONS_ENABLED.save();
    }

    public static boolean isSelfAlertEnabled() {
        try {
            return ALERT_SELF.get() != 0;
        } catch (Exception e) {
            return true;
        }
    }

    public static void setSelfAlertEnabled(boolean enabled) {
        ALERT_SELF.set(enabled ? 1 : 0);
        ALERT_SELF.save();
    }

    // ------------------------------------------------------------------
    // Dice rolls
    // ------------------------------------------------------------------

    public static int getRollMax() {
        try {
            return ROLL_MAX.get();
        } catch (Exception e) {
            return 100;
        }
    }

    public static void setRollMax(int value) {
        ROLL_MAX.set(value);
        ROLL_MAX.save();
    }

    public static double getRollRadius() {
        try {
            return ROLL_RADIUS.get();
        } catch (Exception e) {
            return 24.0D;
        }
    }

    public static void setRollRadius(double value) {
        ROLL_RADIUS.set(value);
        ROLL_RADIUS.save();
    }

    public static String getRollFormat() {
        String stored = stored(ROLL_FORMAT);
        if (stored != null && !stored.equals(ROLL_FORMAT_DEFAULT)) return stored;
        // Left as it shipped, so it follows the language the server writes its lines in.
        return russian() ? ROLL_FORMAT_RU : ROLL_FORMAT_DEFAULT;
    }

    public static void setRollFormat(String value) {
        ROLL_FORMAT.set(value);
        ROLL_FORMAT.save();
    }

    public static String getRollFormatNoReason() {
        String stored = stored(ROLL_FORMAT_NO_REASON);
        if (stored != null && !stored.equals(ROLL_FORMAT_NO_REASON_DEFAULT)) return stored;
        return russian() ? ROLL_FORMAT_NO_REASON_RU : ROLL_FORMAT_NO_REASON_DEFAULT;
    }

    public static void setRollFormatNoReason(String value) {
        ROLL_FORMAT_NO_REASON.set(value);
        ROLL_FORMAT_NO_REASON.save();
    }

    public static String getRollFormatAnonymous() {
        String stored = stored(ROLL_FORMAT_ANONYMOUS);
        if (stored != null && !stored.equals(ROLL_FORMAT_ANONYMOUS_DEFAULT)) return stored;
        return russian() ? ROLL_FORMAT_ANONYMOUS_RU : ROLL_FORMAT_ANONYMOUS_DEFAULT;
    }

    public static void setRollFormatAnonymous(String value) {
        ROLL_FORMAT_ANONYMOUS.set(value);
        ROLL_FORMAT_ANONYMOUS.save();
    }

    public static String getRollFormatAnonymousNoReason() {
        String stored = stored(ROLL_FORMAT_ANONYMOUS_NO_REASON);
        if (stored != null && !stored.equals(ROLL_FORMAT_ANONYMOUS_NO_REASON_DEFAULT)) {
            return stored;
        }
        return russian() ? ROLL_FORMAT_ANONYMOUS_NO_REASON_RU
                : ROLL_FORMAT_ANONYMOUS_NO_REASON_DEFAULT;
    }

    public static void setRollFormatAnonymousNoReason(String value) {
        ROLL_FORMAT_ANONYMOUS_NO_REASON.set(value);
        ROLL_FORMAT_ANONYMOUS_NO_REASON.save();
    }

    public static String getHiddenRollText() {
        try {
            return text(ROLL_HIDDEN_TEXT, HIDDEN_ROLL_TEXT_DEFAULT);
        } catch (Exception e) {
            return HIDDEN_ROLL_TEXT_DEFAULT;
        }
    }

    public static void setHiddenRollText(String value) {
        ROLL_HIDDEN_TEXT.set(value);
        ROLL_HIDDEN_TEXT.save();
    }

    public static String getRollMinFormat() {
        try {
            return text(ROLL_FORMAT_MIN, MIN_ROLL_FORMAT);
        } catch (Exception e) {
            return MIN_ROLL_FORMAT;
        }
    }

    public static void setRollMinFormat(String value) {
        ROLL_FORMAT_MIN.set(value);
        ROLL_FORMAT_MIN.save();
    }

    public static String getRollMaxFormat() {
        try {
            return text(ROLL_FORMAT_MAX, MAX_ROLL_FORMAT);
        } catch (Exception e) {
            return MAX_ROLL_FORMAT;
        }
    }

    public static void setRollMaxFormat(String value) {
        ROLL_FORMAT_MAX.set(value);
        ROLL_FORMAT_MAX.save();
    }

    public static String getRollSound() {
        try {
            return text(ROLL_SOUND, ROLL_SOUND_DEFAULT);
        } catch (Exception e) {
            return ROLL_SOUND_DEFAULT;
        }
    }

    public static void setRollSound(String value) {
        ROLL_SOUND.set(value);
        ROLL_SOUND.save();
    }

    public static String getRollMinSound() {
        try {
            return text(ROLL_MIN_SOUND, MIN_ROLL_SOUND_DEFAULT);
        } catch (Exception e) {
            return MIN_ROLL_SOUND_DEFAULT;
        }
    }

    public static void setRollMinSound(String value) {
        ROLL_MIN_SOUND.set(value);
        ROLL_MIN_SOUND.save();
    }

    public static String getRollMaxSound() {
        try {
            return text(ROLL_MAX_SOUND, MAX_ROLL_SOUND_DEFAULT);
        } catch (Exception e) {
            return MAX_ROLL_SOUND_DEFAULT;
        }
    }

    public static void setRollMaxSound(String value) {
        ROLL_MAX_SOUND.set(value);
        ROLL_MAX_SOUND.save();
    }

    public static double getRollSoundVolume() {
        try {
            return ROLL_SOUND_VOLUME.get();
        } catch (Exception e) {
            return 0.5D;
        }
    }

    public static void setRollSoundVolume(double value) {
        ROLL_SOUND_VOLUME.set(value);
        ROLL_SOUND_VOLUME.save();
    }

    public static double getRollSoundPitch() {
        try {
            return ROLL_SOUND_PITCH.get();
        } catch (Exception e) {
            return 1.0D;
        }
    }

    public static void setRollSoundPitch(double value) {
        ROLL_SOUND_PITCH.set(value);
        ROLL_SOUND_PITCH.save();
    }

    /** {@return the stored text, or the default when the file holds nothing} */
    private static String text(ModConfigSpec.ConfigValue<String> spec, String fallback) {
        String value = spec.get();
        return value == null || value.isEmpty() ? fallback : value;
    }

    /** {@return the value in the file, or null when there is nothing to read} */
    private static String stored(ModConfigSpec.ConfigValue<String> spec) {
        try {
            return text(spec, null);
        } catch (Exception e) {
            return null;
        }
    }

    /** {@return true when the mod's own lines are worded in Russian} */
    private static boolean russian() {
        return "ru_ru".equalsIgnoreCase(getMessagesLanguage());
    }

    public static String getMessagesLanguage() {
        try {
            return text(MESSAGES_LANGUAGE, MESSAGES_LANGUAGES.getFirst());
        } catch (Exception e) {
            return MESSAGES_LANGUAGES.getFirst();
        }
    }

    public static void setMessagesLanguage(String value) {
        MESSAGES_LANGUAGE.set(value);
        MESSAGES_LANGUAGE.save();
    }

    // ------------------------------------------------------------------
    // The client menus
    // ------------------------------------------------------------------

    public static String getMenuMusic() {
        try {
            return text(MENU_MUSIC, MENU_MUSIC_DEFAULT);
        } catch (Exception e) {
            return MENU_MUSIC_DEFAULT;
        }
    }

    public static void setMenuMusic(String value) {
        MENU_MUSIC.set(value);
        MENU_MUSIC.save();
    }

    public static double getMenuMusicVolume() {
        try {
            return MENU_MUSIC_VOLUME.get();
        } catch (Exception e) {
            return 1.0D;
        }
    }

    public static void setMenuMusicVolume(double value) {
        MENU_MUSIC_VOLUME.set(value);
        MENU_MUSIC_VOLUME.save();
    }

    // ------------------------------------------------------------------
    // Typewriter effect
    // ------------------------------------------------------------------

    public static boolean isTypingEnabled() {
        try {
            return TYPING_ENABLED.get() != 0;
        } catch (Exception e) {
            return true;
        }
    }

    public static void setTypingEnabled(boolean enabled) {
        TYPING_ENABLED.set(enabled ? 1 : 0);
        TYPING_ENABLED.save();
    }

    public static String getTypingSound() {
        try {
            String v = TYPING_SOUND.get();
            return v == null || v.isBlank() ? "minecraft:block.note_block.snare" : v;
        } catch (Exception e) {
            return "minecraft:block.note_block.snare";
        }
    }

    public static double getTypingVolume() {
        try {
            return TYPING_VOLUME.get();
        } catch (Exception e) {
            return 0.3D;
        }
    }

    public static double getTypingPitch() {
        try {
            return TYPING_PITCH.get();
        } catch (Exception e) {
            return 1.5D;
        }
    }

    public static double getTypingSpeed() {
        try {
            return TYPING_SPEED.get();
        } catch (Exception e) {
            return 12.0D;
        }
    }

    public static void setTypingSpeed(double value) {
        TYPING_SPEED.set(value);
        TYPING_SPEED.save();
    }

    public static void setTypingSound(String sound, double volume, double pitch) {
        TYPING_SOUND.set(sound);
        TYPING_VOLUME.set(volume);
        TYPING_PITCH.set(pitch);
        TYPING_SOUND.save();
        TYPING_VOLUME.save();
        TYPING_PITCH.save();
    }

    // ------------------------------------------------------------------
    // Chat channels
    // ------------------------------------------------------------------

    public static double getLocalChatRadius() {
        try {
            return LOCAL_CHAT_RADIUS.get();
        } catch (Exception e) {
            return 24.0D;
        }
    }

    public static void setLocalChatRadius(double value) {
        LOCAL_CHAT_RADIUS.set(value);
        LOCAL_CHAT_RADIUS.save();
    }

    public static boolean isGlobalChatStaffOnly() {
        try {
            return GLOBAL_CHAT_RESTRICTED.get() != 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void setGlobalChatStaffOnly(boolean value) {
        GLOBAL_CHAT_RESTRICTED.set(value ? 1 : 0);
        GLOBAL_CHAT_RESTRICTED.save();
    }

    public static boolean isLocalChatStaffOnly() {
        try {
            return LOCAL_CHAT_RESTRICTED.get() != 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void setLocalChatStaffOnly(boolean value) {
        LOCAL_CHAT_RESTRICTED.set(value ? 1 : 0);
        LOCAL_CHAT_RESTRICTED.save();
    }

    // ------------------------------------------------------------------
    // Profile sounds
    // ------------------------------------------------------------------

    /**
     * {@return the sounds a profile may use, cleaned of blanks and duplicates and in the order the
     * editor should offer them}
     */
    public static List<String> getProfileSounds() {
        try {
            // An empty list is a real answer — the operator has vetted nothing — so it is not
            // replaced with the presets, and only an unloaded config falls back.
            List<? extends String> raw = PROFILE_SOUNDS.get();

            LinkedHashSet<String> clean = new LinkedHashSet<>();
            for (String entry : raw) {
                if (entry == null) continue;
                String trimmed = entry.trim();
                if (!trimmed.isEmpty()) clean.add(trimmed);
            }
            return List.copyOf(clean);
        } catch (Exception e) {
            return ProfileSound.PRESETS;
        }
    }

    /** {@return false when the sound is already in the palette} */
    public static boolean addProfileSound(String sound) {
        if (sound == null || sound.isBlank()) return false;

        List<String> sounds = new ArrayList<>(getProfileSounds());
        if (sounds.contains(sound)) return false;

        sounds.add(sound);
        PROFILE_SOUNDS.set(List.copyOf(sounds));
        PROFILE_SOUNDS.save();
        return true;
    }

    /** {@return false when the sound was not in the palette} */
    public static boolean removeProfileSound(String sound) {
        List<String> sounds = new ArrayList<>(getProfileSounds());
        if (!sounds.remove(sound)) return false;

        PROFILE_SOUNDS.set(List.copyOf(sounds));
        PROFILE_SOUNDS.save();
        return true;
    }
}
