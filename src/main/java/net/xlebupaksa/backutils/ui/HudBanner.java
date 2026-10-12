package net.xlebupaksa.backutils.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.client.BackUtilsClientConfig;
import net.xlebupaksa.backutils.client.InlineSupport;
import net.xlebupaksa.backutils.data.MarkupWrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Text that stays on screen beside the log's menu button.
 *
 * <p>What it draws is one string and nothing else: how that string looks is Ember's business, as it
 * is for every other line this mod draws. The text is handed to vanilla's font as a component, so
 * Ember parses the markup inside it; a mod such as Inline, whose matches ride on styles, is asked to
 * parse it first - see {@link InlineSupport} for why that has to be asked for rather than simply
 * drawn. Neither is depended on: the string is drawn as it was typed.
 *
 * <p>It is drawn white and shadowed, which is chat's own treatment - the log's, too - rather than
 * settings of its own. A colour a player wants is a {@code <color>} tag in the text, and the drop
 * shadow is not something a vanilla style carries, so there is nothing for a setting to say about
 * it that the string could have said.
 *
 * <p>It hangs off {@link RoleplayLogButton} rather than a corner of its own, so it sits to the left
 * of the button and, the button being above the log's first row, higher than the log. Its rows are
 * wrapped to the space left of the button and each is right-aligned against it, which is what keeps a
 * long text from running under the button - the one arrangement that would put text inside the
 * button's own drawn square.
 *
 * <p>Each row's component is built once and kept, as {@link DrawnRows} explains at length: the
 * effects Ember and Inline attach to a component hang off the parse, so a component rebuilt inside
 * the draw call gives that state a new identity sixty times a second and the effects restart.
 */
@OnlyIn(Dist.CLIENT)
public final class HudBanner {

    private static final HudBanner INSTANCE = new HudBanner();

    /** The gap between the banner and the button beside it, matching the log's own gap below it. */
    private static final int BUTTON_GAP = 4;

    /** One pixel more than the font's line height, matching the log's own row spacing. */
    private static final int ROW_GAP = 1;

    /** How the lines of the stored text are told apart, since a command argument cannot hold one. */
    private static final String LINE_BREAK = "\\n";

    /** One drawn row: the component, and the width it was measured at. */
    private record Row(Component component, int width) {}

    private final List<Row> rows = new ArrayList<>();

    /** The text the rows were built from, or null when nothing has been built yet. */
    private String builtFrom;
    /** The space the rows were wrapped into, so a resized window wraps them again. */
    private int builtFor;
    private boolean stale = true;

    private HudBanner() {}

    public static HudBanner instance() {
        return INSTANCE;
    }

    /** {@return true when there is text to draw}, an empty one being the feature switched off */
    public boolean visible() {
        return !BackUtilsClientConfig.getBannerText().isBlank();
    }

    /** Rebuilds what is drawn on the next frame; called when the text changes. */
    public void onConfigChanged() {
        stale = true;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /**
     * Draws the banner, if there is one.
     *
     * <p>Called wherever the log's button is drawn - the world, and over the chat screen - so the
     * two cannot disagree about where the column is.
     */
    public void render(GuiGraphics graphics) {
        if (!visible()) return;
        rebuildIfStale(graphics.guiWidth());
        if (rows.isEmpty()) return;

        Font font = Minecraft.getInstance().font;
        int lineStep = font.lineHeight + ROW_GAP;
        int right = right(RoleplayLogButton.left(graphics.guiWidth()));
        int top = top(RoleplayLogButton.top(), RoleplayLogButton.size(), lineStep * rows.size());

        int y = top;
        for (Row row : rows) {
            graphics.drawString(font, row.component(), rowLeft(right, row.width()), y, 0xFFFFFFFF, true);
            y += lineStep;
        }
    }

    /**
     * Rebuilds the rows when the text or the space they are wrapped into has changed.
     *
     * <p>The wrap is kept rather than redone every frame for the same reason the components are: it
     * is the same work over the same string, and a row that is rebuilt is a row that is re-measured.
     */
    private void rebuildIfStale(int guiWidth) {
        String text = BackUtilsClientConfig.getBannerText();
        int available = available(guiWidth);
        if (!stale && Objects.equals(text, builtFrom) && available == builtFor) return;

        rows.clear();
        for (String line : lines(text)) {
            // Wrapping measures each candidate as the row it is about to become: the wrap closes a tag
            // it splits and reopens it on the next row, so the row that is drawn is the row to measure.
            for (String wrapped : MarkupWrap.wrapByWidth(line, available, HudBanner::rowWidth)) {
                Component component = InlineSupport.styled(wrapped);
                rows.add(new Row(component, drawnWidth(component)));
            }
        }
        builtFrom = text;
        builtFor = available;
        stale = false;
    }

    // ------------------------------------------------------------------
    // Pure logic, kept out of the draw call so the harness can cover it
    // ------------------------------------------------------------------

    /**
     * {@return the banner's lines}, in the order they are drawn.
     *
     * <p>A command argument cannot hold a newline, so the two characters {@code \n} stand for one
     * and the text is stored as typed. Interior empty lines are kept, since a deliberate gap
     * between two lines is a thing to want; a trailing one is not, being invisible.
     */
    static List<String> lines(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        // The escape is turned into a real newline before splitting rather than being split on
        // directly: as a regular expression, "\\n" is a newline, so splitting on it would never match
        // the two characters the text actually holds.
        for (String line : text.replace("\r", "").replace(LINE_BREAK, "\n").split("\n", -1)) {
            result.add(line);
        }
        while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    /** {@return the width a row may occupy: everything left of the button, less the gap} */
    public static int available(int buttonLeft) {
        return Math.max(0, right(buttonLeft));
    }

    /**
     * {@return the width a row of markup is drawn at, in pixels}
     *
     * <p>Handed to Inline, so that a row holding inline symbols is measured as the row Inline will
     * draw rather than as the text it was typed as: a symbol is wider than the placeholder standing in
     * for it, and its style is what says how much wider.
     *
     * <p>Nothing is balanced here, and nothing is balanced before drawing either. The text is the
     * player's own, Ember parses each literal on its own so an unclosed tag cannot reach past the row
     * it is in, and a closer this mod invented would be a tag the player never typed - which is what
     * put a visible {@code </typewriter>} at the end of a banner whose tag was simply never closed.
     */
    static int rowWidth(String row) {
        return drawnWidth(InlineSupport.styled(row));
    }

    /**
     * {@return the width a row is drawn at, in pixels}
     *
     * <p>Measured on the component that is about to be drawn, through its visual order, rather than on
     * the text with the markup stripped out. A row with its tags stripped measures narrower than the
     * glyphs the renderer walks - Ember takes the tags' effects, but the width a row is laid out with
     * is the width of what it is handed - and a row measured narrower than it is drawn reaches past the
     * button. Measuring the whole row can only err the other way, leaving a few pixels of gap, which is
     * the direction that cannot overlap.
     */
    static int drawnWidth(Component row) {
        return Minecraft.getInstance().font.width(row.getVisualOrderText());
    }

    /**
     * {@return the banner's right edge, one gap left of the button}
     *
     * <p>Every row ends here, so no row can reach the button however long the text is: a row that
     * does not fit is wrapped, not drawn under it.
     */
    public static int right(int buttonLeft) {
        return Math.max(0, buttonLeft - BUTTON_GAP);
    }

    /** {@return the left edge of a right-aligned row}, clamped to the screen rather than drawn off it */
    public static int rowLeft(int right, int rowWidth) {
        return Math.max(0, right - rowWidth);
    }

    /**
     * {@return the banner's top edge, centred on the button}
     *
     * <p>Clamped to the top of the screen, because the button itself sits in the very corner by
     * default: a block taller than the button would otherwise start above it.
     */
    public static int top(int buttonTop, int buttonSize, int blockHeight) {
        return Math.max(0, buttonTop + (buttonSize - blockHeight) / 2);
    }
}
