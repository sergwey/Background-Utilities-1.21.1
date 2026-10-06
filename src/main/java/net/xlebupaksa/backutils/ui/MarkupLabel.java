package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

    /**
     * The rows, and the components they are drawn with: the same component is drawn on every frame,
     * which is what lets the state behind an effect outlive one. See {@link DrawnRows}.
     */
    private DrawnRows drawn;
    private final int lineStep;
    private final int colour;
    private Component prefix;
    private int prefixColour;
    private ResourceLocation prefixSprite;
    private int prefixSpriteSize;

    public MarkupLabel(List<String> rows, int lineStep, int colour) {
        this.lineStep = lineStep;
        this.colour = colour;
        setRows(rows);
    }

    /**
     * Replaces the lines this label draws, for the separator preview in the settings tab, where
     * rebuilding the element on each keystroke would drop it out of the tree while the field above
     * it holds the focus.
     */
    public MarkupLabel setRows(List<String> newRows) {
        this.drawn = new DrawnRows(newRows);
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

    /**
     * Draws a sprite after the text prefix, for a marker that has to be artwork: a marker drawn from
     * a glyph only appears if the font carries that glyph, and Minecraft's own fonts carry no emoji.
     */
    public MarkupLabel withPrefixSprite(ResourceLocation sprite, int size) {
        this.prefixSprite = sprite;
        this.prefixSpriteSize = size;
        return this;
    }

    /** {@return the height needed to show every row} */
    public int contentHeight() {
        return Math.max(lineStep, drawn.size() * lineStep);
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

        if (prefixSprite != null) {
            // Centred on the line of text rather than on the row step, so it sits with the words.
            guiContext.graphics.blitSprite(prefixSprite, Math.round(x),
                    Math.round(y) + (font.lineHeight - prefixSpriteSize) / 2,
                    prefixSpriteSize, prefixSpriteSize);
            x += prefixSpriteSize;
        }

        for (int i = 0; i < drawn.size(); i++) {
            guiContext.graphics.drawString(font, drawn.component(i),
                    Math.round(x), Math.round(y), colour, true);
            y += lineStep;
        }
    }
}
