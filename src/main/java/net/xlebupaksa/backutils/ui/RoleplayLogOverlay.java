package net.xlebupaksa.backutils.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.client.BackUtilsClientConfig;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.data.MarkupWrap;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The roleplay log shown in the top-right corner.
 *
 * <p>Drawn with vanilla's font, deliberately, and not with ldlib2: ldlib2 renders through its own
 * distance-field font, while Ember's Text API hooks vanilla's {@code Font$StringRenderOutput} and
 * {@code BakedGlyph}, so ldlib2 text loses every Ember effect. It goes through
 * {@link GuiGraphics#drawString} instead.
 *
 * <p>The log lives in a virtual column whose width is fixed by the wrap setting: rows are measured
 * against it and left-aligned inside it, so the text starts at a stable boundary instead of
 * drifting with the length of each message, and the menu button is centred on the same column.
 *
 * <p>Ember's typewriter has no start delay and every row of a wrapped message is a separate
 * literal, so rows are revealed one at a time: a row is drawn once the rows before it have
 * finished typing, which is when Ember first sees it and starts its animation.
 *
 * <p>Each row's component is built once and kept for the life of the entry, rather than rebuilt at
 * every draw: Ember parses a literal's markup as that literal is drawn, and keys the state behind
 * its effects on what the parse produced, so rebuilding would re-parse the same text every frame and
 * move that state each time. See {@link DrawnRows}.
 */
@OnlyIn(Dist.CLIENT)
public final class RoleplayLogOverlay {

    private static final RoleplayLogOverlay INSTANCE = new RoleplayLogOverlay();

    /** Reads the speed the server baked into the typewriter tag, so both ends agree. */
    private static final Pattern SPEED = Pattern.compile("speed=([0-9]+(?:\\.[0-9]+)?)");
    private static final double FALLBACK_SPEED = 12.0D;
    private static final int BUTTON_GAP = 4;
    private static final int ROW_GAP = 1;

    /** Average character advance, cached because the font and the wrap setting may change. */
    private static Font measuredFont;
    private static int measuredChars = -1;
    private static float measuredAverage;

    /**
     * A pangram, used to work out an average character advance: ordinary prose is full of narrow
     * characters, so measuring one wide letter such as {@code x} overstates the column.
     */
    private static final String WIDTH_SAMPLE = "the quick brown fox jumps over the lazy dog";

    /** How much earlier than the computed end an entry is dropped, in seconds (two ticks). */
    private static final double EARLY_REMOVAL = 0.1D;

    private final List<Entry> entries = new ArrayList<>();

    private RoleplayLogOverlay() {}

    public static RoleplayLogOverlay instance() {
        return INSTANCE;
    }

    /** One entry, wrapped into rows of markup text. */
    private static final class Entry {
        final long id;
        /** The rows, and the components they are drawn with; see {@link DrawnRows}. */
        final DrawnRows drawn;
        /** Whether this line is a roll of the dice, which the corner button draws differently. */
        final boolean roll;
        /** When each row begins typing, in seconds from the entry's arrival. */
        final double[] rowStarts;
        final double typingSeconds;
        /** {@link System#nanoTime()} of the first frame it was drawn; -1 until then. */
        long arrivedAt = -1L;

        Entry(long id, DrawnRows drawn, boolean roll, double[] rowStarts, double typingSeconds) {
            this.id = id;
            this.drawn = drawn;
            this.roll = roll;
            this.rowStarts = rowStarts;
            this.typingSeconds = typingSeconds;
        }
    }

    // ------------------------------------------------------------------
    // Layout, shared with the button
    // ------------------------------------------------------------------

    /** {@return the width of the virtual column, in screen pixels} */
    public static int columnWidth() {
        Font font = Minecraft.getInstance().font;
        int chars = Math.max(1, BackUtilsClientConfig.getWrapCharacters());
        if (font != measuredFont || chars != measuredChars) {
            measuredFont = font;
            measuredChars = chars;
            measuredAverage = font.width(WIDTH_SAMPLE) / (float) WIDTH_SAMPLE.length();
        }
        return Math.round(measuredAverage * chars * scale());
    }

    /** {@return the left edge of the virtual column, in screen pixels} */
    public static int columnLeft(int screenWidth) {
        return screenWidth - BackUtilsClientConfig.getRightMargin() - columnWidth();
    }

    /**
     * {@return the y at which the first row of text is drawn, below the button so the two cannot
     * overlap}
     */
    public static int textTop() {
        return BackUtilsClientConfig.getButtonMargin()
                + BackUtilsClientConfig.getButtonSize()
                + BUTTON_GAP
                + BackUtilsClientConfig.getTopMargin();
    }

    private static float scale() {
        return (float) (BackUtilsClientConfig.getFontSize() / 9.0D);
    }

    /**
     * {@return the width a row takes up once it is drawn, in screen pixels}
     *
     * <p>Unlike {@code Font#width} this applies the font scale and counts a pixel per character for
     * bold rows, which vanilla draws one pixel wider than their advance.
     */
    private static int drawnWidth(String row) {
        String visible = MarkupUtil.strip(row);
        int width = Minecraft.getInstance().font.width(visible);
        if (isBold(row)) width += visible.length();
        return Math.round(width * scale());
    }

    /** {@return true when this row carries the bold style, in either of Ember's spellings} */
    private static boolean isBold(String row) {
        String lower = row.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("<bold") || lower.contains("<b>");
    }

    // ------------------------------------------------------------------
    // Receiving lines
    // ------------------------------------------------------------------

    /** Adds an entry from the server, dropping the oldest beyond the configured limit. */
    public void accept(long id, String text, boolean roll) {
        if (!BackUtilsClientConfig.isLogEnabled()) return;
        if (text == null || text.isBlank()) return;

        for (Entry existing : entries) {
            if (existing.id == id) return;
        }

        List<String> rows = MarkupWrap.wrapByWidth(text, columnWidth(), RoleplayLogOverlay::drawnWidth);
        double speed = speedOf(rows);

        double[] starts = new double[rows.size()];
        double elapsed = 0.0D;
        for (int i = 0; i < rows.size(); i++) {
            starts[i] = elapsed;
            elapsed += MarkupUtil.strip(rows.get(i)).length() / speed;
        }

        entries.add(0, new Entry(id, new DrawnRows(rows), roll, starts, elapsed));
        trimToLimit();
    }

    /** {@return the typing speed, read back out of the typewriter tag} */
    private static double speedOf(List<String> rows) {
        for (String row : rows) {
            Matcher matcher = SPEED.matcher(row);
            if (matcher.find()) {
                try {
                    return Math.max(0.1D, Double.parseDouble(matcher.group(1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return FALLBACK_SPEED;
    }

    private void trimToLimit() {
        int max = BackUtilsClientConfig.getMaxLines();
        while (entries.size() > max) {
            entries.remove(entries.size() - 1);
        }
    }

    public void clear() {
        entries.clear();
    }

    /** {@return true while at least one entry is on screen} */
    public boolean hasVisibleEntries() {
        return !entries.isEmpty();
    }

    /** {@return true while any entry is still being typed out} */
    public boolean isTyping() {
        return typing(false);
    }

    /**
     * {@return true while a roll of the dice is still being typed out}
     *
     * <p>Told apart from {@link #isTyping} because the corner button has an animation of its own for
     * a roll, and a roll landing in a busy log should show that rather than the ordinary one.
     */
    public boolean isRolling() {
        return typing(true);
    }

    private boolean typing(boolean rollsOnly) {
        long now = System.nanoTime();
        for (Entry entry : entries) {
            if (rollsOnly && !entry.roll) continue;
            if (entry.arrivedAt < 0L) continue;
            if (age(entry, now) < entry.typingSeconds) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /**
     * Ages, draws the entries and then the button.
     *
     * @param hoverable whether a cursor is available: false in the HUD, true over chat
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, boolean hoverable) {
        long now = System.nanoTime();
        expire(now);

        Font font = Minecraft.getInstance().font;
        float scale = scale();
        int columnLeft = columnLeft(graphics.guiWidth());

        if (!entries.isEmpty()) {
            var pose = graphics.pose();
            pose.pushPose();
            // Drawing inside the scaled space keeps every coordinate above a plain pixel value.
            if (scale != 1.0f) pose.scale(scale, scale, 1.0f);

            float y = textTop() / scale;
            int lineStep = font.lineHeight + ROW_GAP;
            // Rows are wrapped to the measured column width, so one fixed x is enough and the
            // left edge of the log stays straight.
            float x = columnLeft / scale;

            for (Entry entry : entries) {
                float opacity = opacityOf(entry, now);
                double age = age(entry, now);
                int argb = whiteWithAlpha(opacity);

                for (int i = 0; i < entry.drawn.size(); i++) {
                    // Rows are revealed one at a time; y advances regardless, so later rows do
                    // not jump as the typing progresses.
                    if (age >= entry.rowStarts[i]) {
                        graphics.drawString(font, entry.drawn.component(i),
                                Math.round(x), Math.round(y), argb, true);
                    }
                    y += lineStep;
                }
            }
            pose.popPose();
        }

        RoleplayLogButton.render(graphics, mouseX, mouseY, hoverable, isTyping(), isRolling());
    }

    private static double age(Entry entry, long now) {
        if (entry.arrivedAt < 0L) return 0.0D;
        return Math.max(0.0D, (now - entry.arrivedAt) / 1_000_000_000.0D);
    }

    private static float opacityOf(Entry entry, long now) {
        if (entry.arrivedAt < 0L) entry.arrivedAt = now;

        double age = Math.max(0.0D, (now - entry.arrivedAt) / 1_000_000_000.0D);
        double lifetime = BackUtilsClientConfig.getLineLifetimeSeconds();
        double fade = Math.max(0.05D, BackUtilsClientConfig.getFadeSeconds());

        if (age <= lifetime) return 1.0f;
        return (float) Math.max(0.0D, 1.0D - (age - lifetime) / fade);
    }

    /**
     * Drops entries that have finished fading.
     *
     * <p>Timed with {@link System#nanoTime()} rather than level time, which restarts when the
     * client changes dimension. Removal happens {@link #EARLY_REMOVAL} before the fade is over:
     * at the very end the alpha is a fraction of a percent, and a frame at that level reads as a
     * blink rather than a fade.
     */
    private void expire(long now) {
        double lifetime = BackUtilsClientConfig.getLineLifetimeSeconds();
        double fade = Math.max(0.05D, BackUtilsClientConfig.getFadeSeconds());
        double cutoff = Math.max(0.0D, lifetime + fade - EARLY_REMOVAL);
        entries.removeIf(entry -> entry.arrivedAt >= 0L
                && (now - entry.arrivedAt) / 1_000_000_000.0D >= cutoff);
    }

    /** {@return opaque white with {@code opacity} carried in the alpha byte} */
    private static int whiteWithAlpha(float opacity) {
        int alpha = Math.round(Math.max(0f, Math.min(1f, opacity)) * 255f);
        return (alpha << 24) | 0x00FFFFFF;
    }

    /** Re-applies client settings on the next frame. */
    public void onConfigChanged() {
        // Only the measured column is cached.
        measuredChars = -1;
    }
}
