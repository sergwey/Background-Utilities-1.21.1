package net.xlebupaksa.backutils.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.client.AdminAlerts;
import net.xlebupaksa.backutils.client.BackUtilsClientConfig;
import net.xlebupaksa.backutils.network.AdminNetwork;

/**
 * The bottom-right corner: the operator alert icon, and under it the button that opens the
 * administrator menu.
 *
 * <p>Both are placed against vanilla's chat box, which is twelve pixels tall at the bottom of the
 * screen on a band that begins two pixels higher, so they sit just above where the player types.
 * The icon keeps the button's place whether the button is drawn or not, so nothing moves when chat
 * opens.
 *
 * <p>The icon is drawn while an alert is unread and this client is an operator, which is what keeps
 * it off a client that has one pending without being allowed to act on it. It is smaller than the
 * button and centred on it, so the two read as one column. Their sizes are separate settings from
 * the log's own button, so enlarging that one leaves this corner as it was.
 *
 * <p>Like the log's own button, the corner belongs to the world and to the chat screen only: any
 * other screen hides it.
 */
@OnlyIn(Dist.CLIENT)
public final class AdminCorner {

    /** Vanilla's chat input: {@code ChatScreen} puts its box at {@code height - 12}, twelve tall. */
    private static final int CHAT_BOX_HEIGHT = 12;
    /** The band vanilla paints behind that input, which starts two pixels above the box. */
    private static final int CHAT_BAND_HEIGHT = CHAT_BOX_HEIGHT + 2;
    /** The gap between the chat band, the button and the icon. */
    private static final int GAP = 4;

    private static final ResourceLocation ALERT = sprite("hud/alert");
    private static final ResourceLocation BUTTON = sprite("hud/admin_button");
    private static final ResourceLocation BUTTON_HOVERED = sprite("hud/admin_button_hovered");

    private AdminCorner() {}

    private static ResourceLocation sprite(String path) {
        return ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, path);
    }

    /** How dim the alert icon is allowed to fade: never out, since an alert has to stay visible. */
    private static final float PULSE_FLOOR = 0.35F;

    /** Draws the alert icon, wherever an operator has an alert they have not looked at yet. */
    public static void renderIcon(GuiGraphics graphics) {
        if (!iconVisible()) return;

        int size = iconSize();
        float alpha = IconPulse.alpha(System.nanoTime(),
                BackUtilsClientConfig.getAdminAlertPulseSeconds(), PULSE_FLOOR);

        // The fade is a colour on the quad itself rather than a shader colour: the sprite shader
        // multiplies each fragment by its own vertex colour, so this cannot land on a different
        // shader or be reset before the quad is drawn, and never passes through the alpha 0.1 cut
        // the frames themselves are subject to.
        graphics.blit(iconLeft(graphics.guiWidth()), iconTop(graphics.guiHeight()), 0, size, size,
                Minecraft.getInstance().getGuiSprites().getSprite(ALERT),
                1.0F, 1.0F, 1.0F, alpha);
    }

    /** Draws the administrator menu button; the chat screen is the only place it is offered. */
    public static void renderButton(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isOperator()) return;
        int size = buttonSize();
        boolean hovered = contains(mouseX, mouseY, graphics.guiWidth(), graphics.guiHeight());
        graphics.blitSprite(hovered ? BUTTON_HOVERED : BUTTON,
                left(graphics.guiWidth()), buttonTop(graphics.guiHeight()), size, size);
    }

    /** {@return true when the press landed on the button, having opened the administrator menu} */
    public static boolean buttonClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isOperator()) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (!contains(mouseX, mouseY, minecraft.getWindow().getGuiScaledWidth(),
                minecraft.getWindow().getGuiScaledHeight())) {
            return false;
        }
        BackUtilsAdminScreen.open();
        return true;
    }

    /** {@return true when this client is an operator and has an alert it has not read} */
    public static boolean iconVisible() {
        if (!BackUtilsClientConfig.isAdminAlertMarkerEnabled()) return false;
        if (!AdminAlerts.hasPending()) return false;
        return isOperator();
    }

    private static boolean isOperator() {
        Minecraft minecraft = Minecraft.getInstance();
        // The level the client was told it has, which is the same gate the server applies.
        return minecraft.player != null
                && minecraft.player.hasPermissions(AdminNetwork.REQUIRED_LEVEL);
    }

    /** {@return true when the pointer is inside the button's square} */
    private static boolean contains(double mouseX, double mouseY, int guiWidth, int guiHeight) {
        int size = buttonSize();
        int left = left(guiWidth);
        int top = buttonTop(guiHeight);
        return mouseX >= left && mouseX < left + size && mouseY >= top && mouseY < top + size;
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    private static int buttonSize() {
        return BackUtilsClientConfig.getAdminButtonSize();
    }

    private static int iconSize() {
        return BackUtilsClientConfig.getAdminAlertIconSize();
    }

    /** {@return the left edge of the buttons, against the config's right margin} */
    private static int left(int guiWidth) {
        return guiWidth - BackUtilsClientConfig.getRightMargin() - buttonSize();
    }

    /** {@return the left edge of the icon, centred on the button below it} */
    private static int iconLeft(int guiWidth) {
        return left(guiWidth) + (buttonSize() - iconSize()) / 2;
    }

    /** {@return the top of the button, clear of the band vanilla paints behind the chat box} */
    private static int buttonTop(int guiHeight) {
        return guiHeight - CHAT_BAND_HEIGHT - GAP - buttonSize();
    }

    /** {@return the top of the alert icon, one gap above the button's place} */
    private static int iconTop(int guiHeight) {
        return buttonTop(guiHeight) - GAP - iconSize();
    }
}
