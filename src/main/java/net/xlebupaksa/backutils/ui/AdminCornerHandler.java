package net.xlebupaksa.backutils.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Draws the bottom-right corner over the chat screen, and opens the administrator menu when its
 * button is pressed.
 *
 * <p>Only the chat screen, and only there because screens render over the HUD layers: the corner is
 * drawn in the world by {@link RoleplayLogHudLayer} and over chat here, which is the same rule the
 * log and its own button follow. Any other screen — an inventory, the menu, a chest — hides it.
 */
@OnlyIn(Dist.CLIENT)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public class AdminCornerHandler {

    public static void register() {
        NeoForge.EVENT_BUS.register(AdminCornerHandler.class);
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;

        GuiGraphics graphics = event.getGuiGraphics();
        AdminCorner.renderIcon(graphics);
        AdminCorner.renderButton(graphics, event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onMouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;
        if (AdminCorner.buttonClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            // Swallowed, so the press does not also reach the chat box or a line of chat under it.
            event.setCanceled(true);
        }
    }
}
