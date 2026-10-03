package net.xlebupaksa.backutils.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
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
        ClientConfigLoader.register(modEventBus);
        ChatScreenOverlayHandler.register();
    }
}
