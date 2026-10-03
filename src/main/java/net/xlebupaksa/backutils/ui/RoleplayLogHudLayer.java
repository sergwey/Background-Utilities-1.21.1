package net.xlebupaksa.backutils.ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.network.LogHistoryCache;
import net.xlebupaksa.backutils.network.AdminConfigCache;
import net.xlebupaksa.backutils.network.AdminConfigFeedbackCache;
import net.xlebupaksa.backutils.network.AdminProfileCache;
import net.xlebupaksa.backutils.network.AdminProfileFeedbackCache;
import net.xlebupaksa.backutils.network.ProfileFeedbackCache;
import net.xlebupaksa.backutils.network.ProfileListCache;

/**
 * Hooks the roleplay log into the HUD.
 *
 * <p>A plain {@code LayeredDraw.Layer} rather than ldlib2's {@code ModularHudLayer}: the overlay
 * draws itself with vanilla's font so Ember's effects apply, which means there is no ldlib2 UI to
 * tick or lay out here.
 *
 * <p>Visibility is split across two hooks so the log is drawn exactly once per frame: this layer
 * draws it while no screen is open, and {@link ChatScreenOverlayHandler} draws it from
 * {@code ScreenEvent.Render.Post} while the chat screen is open, because HUD layers render
 * <i>behind</i> an open screen and chat is the one screen the log has to stay readable over.
 * Every other screen hides it.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = BackUtils.MOD_ID, value = Dist.CLIENT)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class RoleplayLogHudLayer {

    private RoleplayLogHudLayer() {}

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "roleplay_log"),
                RoleplayLogHudLayer::renderHud);
    }

    private static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        // Any open screen wins; the chat screen is drawn on top by the overlay handler.
        if (Minecraft.getInstance().screen != null) return;
        if (Minecraft.getInstance().player == null) return;
        if (Minecraft.getInstance().level == null) return;

        // No screen is open, so there is no cursor to hover with.
        RoleplayLogOverlay.instance().render(graphics, 0, 0, false);
    }

    /** Draws the log over an open chat screen, where a cursor exists and the button is hoverable. */
    public static void renderOverChatScreen(GuiGraphics graphics, int mouseX, int mouseY,
                                            float partialTick) {
        if (Minecraft.getInstance().player == null) return;
        RoleplayLogOverlay.instance().render(graphics, mouseX, mouseY, true);
    }

    /** Drops per-connection state so a new server starts with a clean slate. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RoleplayLogOverlay.instance().clear();
        LogHistoryCache.clear();
        // The profiles and the palette belong to the server just left, as do its settings.
        ProfileListCache.clear();
        ProfileFeedbackCache.clear();
        AdminProfileCache.clear();
        AdminProfileFeedbackCache.clear();
        AdminConfigCache.clear();
        AdminConfigFeedbackCache.clear();
    }
}
