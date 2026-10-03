package net.xlebupaksa.backutils;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.xlebupaksa.backutils.profile.ProfileSound;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class BackUtilsConfig {

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
