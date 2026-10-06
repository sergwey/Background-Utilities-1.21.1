package net.xlebupaksa.backutils.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.client.BackUtilsClientConfig;

/**
 * The square menu button.
 *
 * <p>Four sprite states, drawn from the GUI sprite atlas: {@code backutils:hud/not_talking} when
 * idle, {@code backutils:hud/talking} while a line is being typed out, {@code backutils:hud/rolling}
 * while a roll of the dice is, and {@code backutils:hud/hovered} under the cursor. The two typing
 * states are animated strips, so each needs the {@code .mcmeta} beside it.
 *
 * <p>Drawn with {@link GuiGraphics#blitSprite}, which resolves through the atlas. A plain
 * {@code blit} on a texture path goes through {@code SimpleTexture}, which reads only blur/clamp
 * metadata, so it <b>cannot</b> animate; only atlas sprites honour {@code .mcmeta}. PNGs dropped
 * into {@code assets/backutils/textures/gui/sprites/} become atlas sprites on their own.
 *
 * <p>Positioned above the log text and against the right edge of it, so it reads as belonging to the
 * column and sits in the corner the text ends at.
 */
@OnlyIn(Dist.CLIENT)
public final class RoleplayLogButton {

    private static final ResourceLocation TALKING = sprite("hud/talking");
    private static final ResourceLocation ROLLING = sprite("hud/rolling");
    private static final ResourceLocation NOT_TALKING = sprite("hud/not_talking");
    private static final ResourceLocation HOVERED = sprite("hud/hovered");

    private RoleplayLogButton() {}

    private static ResourceLocation sprite(String path) {
        return ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, path);
    }

    /**
     * Draws the button.
     *
     * @param hoverable whether a cursor is available to hover with: false in the HUD
     * @param rolling   whether a roll is being typed, which is drawn in preference to an ordinary
     *                  line: a roll is the rarer thing to watch for
     */
    public static void render(GuiGraphics graphics, int mouseX, int mouseY,
                              boolean hoverable, boolean talking, boolean rolling) {
        boolean hovered = hoverable && contains(mouseX, mouseY);
        ResourceLocation sprite = hovered ? HOVERED
                : rolling ? ROLLING
                : talking ? TALKING
                : NOT_TALKING;
        graphics.blitSprite(sprite, left(graphics.guiWidth()), top(), size(), size());
    }

    /** {@return true when the press landed on the button, having opened the menu if so} */
    public static boolean clicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) return false;
        BackUtilsMenuScreen.open();
        return true;
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    /** {@return the button's left edge, against the right edge of the log's virtual column} */
    public static int left(int screenWidth) {
        int column = RoleplayLogOverlay.columnWidth();
        int columnLeft = RoleplayLogOverlay.columnLeft(screenWidth);
        // Flush with the column's right edge rather than centred on it: the button belongs in the
        // corner the text ends at, which is also the corner the eye is already in.
        return Math.max(columnLeft, columnLeft + column - size());
    }

    /** {@return the button's top edge, above the text, so the two cannot overlap} */
    public static int top() {
        return BackUtilsClientConfig.getButtonMargin();
    }

    public static int size() {
        return BackUtilsClientConfig.getButtonSize();
    }

    private static boolean contains(double mouseX, double mouseY) {
        int size = size();
        int left = left(Minecraft.getInstance().getWindow().getGuiScaledWidth());
        int top = top();
        return mouseX >= left && mouseX < left + size && mouseY >= top && mouseY < top + size;
    }
}