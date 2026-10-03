package net.xlebupaksa.backutils.ui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public class ChatScreenOverlayHandler {

    private static ChatScreenOverlay activeOverlay;

    public static void register() {
        NeoForge.EVENT_BUS.register(ChatScreenOverlayHandler.class);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ChatScreen screen)) return;
        ChatScreenOverlay overlay = new ChatScreenOverlay();
        // Nothing to build for players who cannot change their profile anyway.
        activeOverlay = overlay.build(screen) ? overlay : null;
        forceChatFocus(screen);
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (!(event.getScreen() instanceof ChatScreen)) return;
        if (activeOverlay != null) {
            activeOverlay.close();
            activeOverlay = null;
        }
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof ChatScreen screen)) return;

        // HUD layers render behind an open screen, so the log is drawn here while chat is open.
        RoleplayLogHudLayer.renderOverChatScreen(event.getGuiGraphics(),
                event.getMouseX(), event.getMouseY(), event.getPartialTick());

        if (activeOverlay == null) return;
        if (isTypingCommand(screen)) return;

        activeOverlay.mouseMoved(event.getMouseX(), event.getMouseY());

        var pose = event.getGuiGraphics().pose();
        pose.pushPose();
        pose.translate(0f, 0f, 400f);
        activeOverlay.render(event.getGuiGraphics(),
                event.getMouseX(), event.getMouseY(), event.getPartialTick());
        pose.popPose();
    }

    private static boolean isTypingCommand(ChatScreen screen) {
        for (var child : screen.children()) {
            if (child instanceof EditBox box) {
                return box.getValue().startsWith("/");
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onMouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen screen)) return;
        if (isTypingCommand(screen)) return;

        // The corner button first: it opens the menu and swallows the press if it was hit.
        if (RoleplayLogButton.clicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }

        if (activeOverlay != null && activeOverlay.mouseClicked(
                event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
        forceChatFocus(screen);
    }

    @SubscribeEvent
    public static void onMouseUp(ScreenEvent.MouseButtonReleased.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen screen)) return;
        if (isTypingCommand(screen)) return;
        if (activeOverlay != null && activeOverlay.mouseReleased(
                event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
        forceChatFocus(screen);
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen screen)) return;
        if (isTypingCommand(screen)) return;
        if (activeOverlay != null && activeOverlay.mouseScrolled(
                event.getMouseX(), event.getMouseY(),
                event.getScrollDeltaX(), event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
        forceChatFocus(screen);
    }

    private static void forceChatFocus(ChatScreen screen) {
        for (var child : screen.children()) {
            if (child instanceof EditBox box) {
                screen.setFocused(box);
                box.setFocused(true);
                return;
            }
        }
    }
}
