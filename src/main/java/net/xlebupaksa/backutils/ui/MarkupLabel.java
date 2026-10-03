package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * A multi-line text element drawn with vanilla's font.
 *
 * <p>ldlib2's own {@code TextElement} draws through {@code LDLibFonts.font()}, which never touches
 * vanilla's {@code Font$StringRenderOutput} or {@code BakedGlyph}, the classes Ember's Text API
 * mixes into; the text goes through {@link net.minecraft.client.gui.GuiGraphics#drawString} so
 * those effects apply.
 *
 * <p>Positioning is in screen space, because {@code UIElement#drawInBackground} never translates
 * the pose to the element.
 */
@OnlyIn(Dist.CLIENT)
public final class MarkupLabel extends UIElement {

    private List<String> rows;
    private final int lineStep;
    private final int colour;
    private Component prefix;
    private int prefixColour;

    public MarkupLabel(List<String> rows, int lineStep, int colour) {
        this.rows = List.copyOf(rows);
        this.lineStep = lineStep;
        this.colour = colour;
    }

    /**
     * Replaces the lines this label draws, for the separator preview in the settings tab, where
     * rebuilding the element on each keystroke would drop it out of the tree while the field above
     * it holds the focus.
     */
    public MarkupLabel setRows(List<String> newRows) {
        this.rows = List.copyOf(newRows);
        return this;
    }

    /**
     * Draws a marker before the first row, in its own colour and outside the wrapping, so it stays
     * grey whatever formatting the action beside it carries.
     */
    public MarkupLabel withPrefix(Component prefix, int prefixColour) {
        this.prefix = prefix;
        this.prefixColour = prefixColour;
        return this;
    }

    /** {@return the height needed to show every row} */
    public int contentHeight() {
        return Math.max(lineStep, rows.size() * lineStep);
    }

    @Override
    public void drawContents(GUIContext guiContext) {
        Font font = Minecraft.getInstance().font;
        float x = getPositionX();
        float y = getPositionY();

        if (prefix != null && !prefix.getString().isEmpty()) {
            guiContext.graphics.drawString(font, prefix,
                    Math.round(x), Math.round(y), prefixColour, true);
            x += font.width(prefix);
        }

        for (String row : rows) {
            guiContext.graphics.drawString(font, Component.literal(row),
                    Math.round(x), Math.round(y), colour, true);
            y += lineStep;
        }
    }
}
