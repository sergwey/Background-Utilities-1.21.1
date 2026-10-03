package net.xlebupaksa.backutils.client;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.xlebupaksa.backutils.ui.RoleplayLogOverlay;

/**
 * Client-side settings for everything this mod draws: all per-player and purely local, never sent to
 * the server, and kept apart from {@link net.xlebupaksa.backutils.BackUtilsConfig}.
 */
public final class BackUtilsClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_ENABLED = BUILDER
            .comment("Show roleplay actions in the top-right corner of the screen.")
            .define("roleplayLogEnabled", true);

    public static final ModConfigSpec.IntValue MAX_LINES = BUILDER
            .comment("How many entries are kept on screen. The oldest scrolls off once this",
                    "is exceeded.")
            .defineInRange("maxLines", 8, 1, 32);

    public static final ModConfigSpec.IntValue LINE_LIFETIME_SECONDS = BUILDER
            .comment("How long an entry stays before it starts fading.")
            .defineInRange("lineLifetimeSeconds", 20, 3, 300);

    public static final ModConfigSpec.IntValue FADE_SECONDS = BUILDER
            .comment("How long the fade-out itself takes.")
            .defineInRange("fadeSeconds", 2, 1, 30);

    public static final ModConfigSpec.DoubleValue FONT_SIZE = BUILDER
            .comment("Log text size, in pixels. Minecraft's normal chat text is 9.")
            .defineInRange("fontSize", 10.0D, 4.0D, 32.0D);

    public static final ModConfigSpec.IntValue RIGHT_MARGIN = BUILDER
            .comment("Gap between the right edge of the log column and the screen edge,",
                    "in pixels.")
            .defineInRange("rightMargin", 4, 0, 200);

    public static final ModConfigSpec.IntValue TOP_MARGIN = BUILDER
            .comment("Extra space between the menu button and the first line of text, in",
                    "pixels. The text is always placed below the button, so this only ever",
                    "adds to that gap.")
            .defineInRange("topMargin", 1, 0, 400);

    public static final ModConfigSpec.IntValue WRAP_CHARACTERS = BUILDER
            .comment("Longest line before the text wraps. This is what sets the width of the",
                    "log column. Words are kept whole where possible, and markup tags do not",
                    "count towards the length.")
            .defineInRange("wrapCharacters", 30, 16, 240);

    public static final ModConfigSpec.IntValue BUTTON_SIZE = BUILDER
            .comment("Size of the square menu button, in pixels.")
            .defineInRange("buttonSize", 20, 8, 64);

    public static final ModConfigSpec.IntValue BUTTON_MARGIN = BUILDER
            .comment("Gap between the menu button and the top-right corner, in pixels.")
            .defineInRange("buttonMargin", 4, 0, 200);

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    public static final ModConfigSpec.DoubleValue MENU_BACKGROUND_OPACITY = BUILDER
            .comment("How dark the menu's background is drawn, from 0 (invisible) to 1",
                    "(solid).")
            .defineInRange("menuBackgroundOpacity", 0.5D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue MENU_SCALE = BUILDER
            .comment("Size of the menu's contents, in percent.")
            .defineInRange("menuScalePercent", 100, 50, 200);

    // ------------------------------------------------------------------
    // Background administrator alerts (per player and client-side: nothing here affects what the
    // server records or who may read it)
    // ------------------------------------------------------------------

    public static final ModConfigSpec.BooleanValue ADMIN_ALERT_ENABLED = BUILDER
            .comment("Play a sound when an operator alert arrives.",
                    "Only ever heard by players with permission level 2 or above.")
            .define("adminAlertEnabled", true);

    public static final ModConfigSpec.BooleanValue ADMIN_ALERT_BLINK = BUILDER
            .comment("Show the blinking marker beside the log until the administrator menu",
                    "has been opened.")
            .define("adminAlertMarker", true);

    public static final ModConfigSpec.ConfigValue<String> ADMIN_ALERT_SOUND = BUILDER
            .comment("The alert sound. Any sound id works, including one from a resourcepack.")
            .define("adminAlertSound", "minecraft:block.note_block.bell");

    public static final ModConfigSpec.DoubleValue ADMIN_ALERT_VOLUME = BUILDER
            .comment("Alert volume, 0 to 2.")
            .defineInRange("adminAlertVolume", 0.3D, 0.0D, 2.0D);

    public static final ModConfigSpec.DoubleValue ADMIN_ALERT_PITCH = BUILDER
            .comment("Alert pitch, 0.5 to 2.")
            .defineInRange("adminAlertPitch", 1.5D, 0.5D, 2.0D);

    public static final ModConfigSpec.ConfigValue<String> HIDDEN_MARKER = BUILDER
            .comment("Shown in the administrator menu in front of an action this viewer is not",
                    "normally allowed to see, and beside each witness it is hidden from.",
                    "Drawn in bold.",
                    "The default is a fisheye, which looks like an eye. A canoe (U+1F6F6) was",
                    "the first choice, but Minecraft's own fonts carry no emoji glyphs and it",
                    "rendered as an empty box - set it here if a resourcepack supplies one.")
            .define("hiddenMarker", "\u25c9");

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BackUtilsClientConfig() {}

    // Reads are guarded because a config value throws before the config has been loaded, and the
    // overlay runs from the first frame; the fallbacks mirror the defaults above.

    public static boolean isLogEnabled() {
        try {
            return LOG_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    public static int getMaxLines() {
        try {
            return MAX_LINES.get();
        } catch (Exception e) {
            return 8;
        }
    }

    public static int getLineLifetimeSeconds() {
        try {
            return LINE_LIFETIME_SECONDS.get();
        } catch (Exception e) {
            return 20;
        }
    }

    public static int getFadeSeconds() {
        try {
            return FADE_SECONDS.get();
        } catch (Exception e) {
            return 2;
        }
    }

    public static double getFontSize() {
        try {
            return FONT_SIZE.get();
        } catch (Exception e) {
            return 10.0D;
        }
    }

    public static int getRightMargin() {
        try {
            return RIGHT_MARGIN.get();
        } catch (Exception e) {
            return 4;
        }
    }

    public static int getTopMargin() {
        try {
            return TOP_MARGIN.get();
        } catch (Exception e) {
            return 1;
        }
    }

    public static int getWrapCharacters() {
        try {
            return WRAP_CHARACTERS.get();
        } catch (Exception e) {
            return 30;
        }
    }

    public static int getButtonSize() {
        try {
            return BUTTON_SIZE.get();
        } catch (Exception e) {
            return 20;
        }
    }

    public static int getButtonMargin() {
        try {
            return BUTTON_MARGIN.get();
        } catch (Exception e) {
            return 4;
        }
    }

    public static double getMenuBackgroundOpacity() {
        try {
            return MENU_BACKGROUND_OPACITY.get();
        } catch (Exception e) {
            return 0.5D;
        }
    }

    public static int getMenuScalePercent() {
        try {
            return MENU_SCALE.get();
        } catch (Exception e) {
            return 100;
        }
    }

    // ------------------------------------------------------------------
    // Writing back (each setter persists, then tells the overlay: saving raises no reload event)
    // ------------------------------------------------------------------

    public static void setLogEnabled(boolean value) {
        apply(LOG_ENABLED, value);
    }

    public static void setMaxLines(int value) {
        apply(MAX_LINES, value);
    }

    public static void setLineLifetimeSeconds(int value) {
        apply(LINE_LIFETIME_SECONDS, value);
    }

    public static void setFadeSeconds(int value) {
        apply(FADE_SECONDS, value);
    }

    public static void setFontSize(double value) {
        apply(FONT_SIZE, value);
    }

    public static void setRightMargin(int value) {
        apply(RIGHT_MARGIN, value);
    }

    public static void setTopMargin(int value) {
        apply(TOP_MARGIN, value);
    }

    public static void setWrapCharacters(int value) {
        apply(WRAP_CHARACTERS, value);
    }

    public static void setButtonSize(int value) {
        apply(BUTTON_SIZE, value);
    }

    public static void setButtonMargin(int value) {
        apply(BUTTON_MARGIN, value);
    }

    public static void setMenuBackgroundOpacity(double value) {
        apply(MENU_BACKGROUND_OPACITY, value);
    }

    // ------------------------------------------------------------------
    // Alert reads and writes
    // ------------------------------------------------------------------

    public static boolean isAdminAlertEnabled() {
        try {
            return ADMIN_ALERT_ENABLED.get();
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean isAdminAlertMarkerEnabled() {
        try {
            return ADMIN_ALERT_BLINK.get();
        } catch (Exception e) {
            return true;
        }
    }

    public static String getAdminAlertSound() {
        try {
            String value = ADMIN_ALERT_SOUND.get();
            return value == null || value.isBlank() ? "minecraft:block.note_block.bell" : value;
        } catch (Exception e) {
            return "minecraft:block.note_block.bell";
        }
    }

    public static double getAdminAlertVolume() {
        try {
            return ADMIN_ALERT_VOLUME.get();
        } catch (Exception e) {
            return 0.3D;
        }
    }

    public static double getAdminAlertPitch() {
        try {
            return ADMIN_ALERT_PITCH.get();
        } catch (Exception e) {
            return 1.5D;
        }
    }

    public static String getHiddenMarker() {
        try {
            String value = HIDDEN_MARKER.get();
            return value == null || value.isEmpty() ? "\u25c9" : value;
        } catch (Exception e) {
            return "\u25c9";
        }
    }

    public static void setAdminAlertEnabled(boolean value) {
        apply(ADMIN_ALERT_ENABLED, value);
    }

    public static void setAdminAlertMarker(boolean value) {
        apply(ADMIN_ALERT_BLINK, value);
    }

    public static void setAdminAlertSound(String value) {
        apply(ADMIN_ALERT_SOUND, value);
    }

    public static void setAdminAlertVolume(double value) {
        apply(ADMIN_ALERT_VOLUME, value);
    }

    public static void setAdminAlertPitch(double value) {
        apply(ADMIN_ALERT_PITCH, value);
    }

    private static <T> void apply(ModConfigSpec.ConfigValue<T> spec, T value) {
        try {
            spec.set(value);
            spec.save();
        } catch (Exception e) {
            // A config that has not loaded yet cannot be written; nothing useful to do here.
            return;
        }
        RoleplayLogOverlay.instance().onConfigChanged();
    }
}
