package net.xlebupaksa.backutils.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.xlebupaksa.backutils.ui.AdminCornerHandler;
import net.xlebupaksa.backutils.ui.ChatScreenOverlayHandler;

/**
 * Entry point for everything that is only valid on the physical client: {@code BackUtils} calls
 * {@link #register(IEventBus)} behind a {@code FMLEnvironment.dist.isClient()} check, so nothing
 * here is resolved on a dedicated server.
 *
 * <p>The mod bus carries lifecycle and registration events, the common bus
 * ({@code NeoForge.EVENT_BUS}) carries gameplay events, and registering one on the other fails mod
 * loading.
 */
public final class ClientSetup {

    private ClientSetup() {}

    public static void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(ClientNametagHandler.class);
        // The hold-W gesture over an effect tool in an inventory.
        NeoForge.EVENT_BUS.register(EffectToolGesture.class);
        // The right-click that places what the held effect tool is aiming at.
        NeoForge.EVENT_BUS.register(EffectToolClick.class);
        // The click that removes what is already placed, and the blink a deletion leaves behind.
        NeoForge.EVENT_BUS.register(DeleteToolClick.class);
        // What this client is drawing, which is what removes, counts and draws placed effects.
        NeoForge.EVENT_BUS.register(PlacedEffects.class);
        // Where the held tool's preview or view of what is placed is drawn.
        NeoForge.EVENT_BUS.register(EffectToolPreviewRenderer.class);
        // Agreeing with the server about a player who is being held, so the client stops predicting
        // the fall of somebody the server is not letting fall.
        NeoForge.EVENT_BUS.register(FrozenClient.class);
        // Space over an item in an inventory, which opens the item editor on it.
        NeoForge.EVENT_BUS.register(ItemEditorGesture.class);
        ClientConfigLoader.register(modEventBus);
        ChatScreenOverlayHandler.register();
        AdminCornerHandler.register();
    }
}
