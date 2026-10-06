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
            .comment("Gap between the right edge of the screen and the log column, in pixels.",
                    "The bottom-right alert icon and menu button keep the same margin.")
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
            .comment("Size of the log's menu button, in pixels. The sprite is drawn at this size,",
                    "so a size other than the one it was drawn at will be scaled.")
            .defineInRange("buttonSize", 40, 8, 128);

    public static final ModConfigSpec.IntValue BUTTON_MARGIN = BUILDER
            .comment("Gap between the menu button and the top-right corner, in pixels.")
            .defineInRange("buttonMargin", 0, 0, 200);

    public static final ModConfigSpec.IntValue ADMIN_BUTTON_SIZE = BUILDER
            .comment("Size of the administrator menu button in the bottom-right corner, in",
                    "pixels. The alert icon above it is centred on it.")
            .defineInRange("adminButtonSize", 20, 8, 128);

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    public static final ModConfigSpec.DoubleValue MENU_BACKGROUND_OPACITY = BUILDER
            .comment("How dark the menu's background is drawn, from 0 (invisible) to 1",
                    "(solid).")
            .defineInRange("menuBackgroundOpacity", 0.5D, 0.0D, 1.0D);

    // ------------------------------------------------------------------
    // Background administrator alerts (per player and client-side: nothing here affects what the
    // server records or who may read it)
    // ------------------------------------------------------------------

    public static final ModConfigSpec.BooleanValue ADMIN_ALERT_ENABLED = BUILDER
            .comment("Play a sound when an operator alert arrives.",
                    "Only ever heard by players with permission level 2 or above.")
            .define("adminAlertEnabled", true);

    public static final ModConfigSpec.BooleanValue ADMIN_ALERT_MARKER = BUILDER
            .comment("Show the alert icon above the administrator menu button until the menu",
                    "has been opened.")
            .define("adminAlertMarker", true);

    public static final ModConfigSpec.IntValue ADMIN_ALERT_ICON_SIZE = BUILDER
            .comment("Size of that alert icon, in pixels. It is drawn centred on the",
                    "administrator menu button, and smaller than the button by default.")
            .defineInRange("adminAlertIconSize", 14, 4, 64);

    /**
     * The fade the icon is drawn with, which is not the same thing as a fade drawn into the
     * sprite's frames: the sprite shader discards anything under alpha 0.1 before the colour
     * modulator, so a fade carried by the frames is cut off rather than reaching zero. Zero here
     * leaves the icon exactly as its frames have it.
     */
    public static final ModConfigSpec.DoubleValue ADMIN_ALERT_PULSE = BUILDER
            .comment("How long the alert icon takes to fade down and back up, in seconds.",
                    "It never fades out completely. Set 0 to draw it still instead, which",
                    "leaves a multi-frame sprite to fade however its own frames do.")
            .defineInRange("adminAlertPulseSeconds", 1.5D, 0.0D, 10.0D);

    public static final ModConfigSpec.ConfigValue<String> ADMIN_ALERT_SOUND = BUILDER
            .comment("The alert sound. Any sound id works, including one from a resourcepack.")
            .define("adminAlertSound", "minecraft:block.note_block.bell");

    public static final ModConfigSpec.DoubleValue ADMIN_ALERT_VOLUME = BUILDER
            .comment("Alert volume, 0 to 2.")
            .defineInRange("adminAlertVolume", 0.3D, 0.0D, 2.0D);

    public static final ModConfigSpec.DoubleValue ADMIN_ALERT_PITCH = BUILDER
            .comment("Alert pitch, 0.5 to 2.")
            .defineInRange("adminAlertPitch", 1.5D, 0.5D, 2.0D);

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
            return 40;
        }
    }

    public static int getButtonMargin() {
        try {
            return BUTTON_MARGIN.get();
        } catch (Exception e) {
            return 0;
        }
    }

    public static int getAdminButtonSize() {
        try {
            return ADMIN_BUTTON_SIZE.get();
        } catch (Exception e) {
            return 20;
        }
    }

    public static double getMenuBackgroundOpacity() {
        try {
            return MENU_BACKGROUND_OPACITY.get();
        } catch (Exception e) {
            return 0.5D;
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

    public static void setAdminButtonSize(int value) {
        apply(ADMIN_BUTTON_SIZE, value);
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
            return ADMIN_ALERT_MARKER.get();
        } catch (Exception e) {
            return true;
        }
    }

    public static int getAdminAlertIconSize() {
        try {
            return ADMIN_ALERT_ICON_SIZE.get();
        } catch (Exception e) {
            return 14;
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

    public static double getAdminAlertPulseSeconds() {
        try {
            return ADMIN_ALERT_PULSE.get();
        } catch (Exception e) {
            return 1.5D;
        }
    }

    public static void setAdminAlertPulseSeconds(double value) {
        apply(ADMIN_ALERT_PULSE, value);
    }

    public static void setAdminAlertEnabled(boolean value) {
        apply(ADMIN_ALERT_ENABLED, value);
    }

    public static void setAdminAlertMarker(boolean value) {
        apply(ADMIN_ALERT_MARKER, value);
    }

    public static void setAdminAlertIconSize(int value) {
        apply(ADMIN_ALERT_ICON_SIZE, value);
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
            // Written only on a real change: a slider reports every pixel of a drag and a text field
            // every keystroke, and each save rewrites the whole file.
            if (java.util.Objects.equals(spec.get(), value)) return;
            spec.set(value);
            spec.save();
        } catch (Exception e) {
            // A config that has not loaded yet cannot be written; nothing useful to do here.
            return;
        }
        RoleplayLogOverlay.instance().onConfigChanged();
    }
}
