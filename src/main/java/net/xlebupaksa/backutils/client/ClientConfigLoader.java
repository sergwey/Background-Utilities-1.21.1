package net.xlebupaksa.backutils.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.ui.RoleplayLogOverlay;

/**
 * Reacts to the client config being loaded or reloaded. {@link ModConfigEvent} is an
 * {@code IModBusEvent}, so this must be registered on the mod event bus rather than the common one.
 */
public final class ClientConfigLoader {

    private ClientConfigLoader() {}

    /** Registers on the mod bus; called from {@code ClientSetup}. */
    public static void register(IEventBus modEventBus) {
        modEventBus.register(ClientConfigLoader.class);
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        refresh(event);
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        refresh(event);
    }

    /** Config events arrive for every mod, so anything not belonging to this mod is ignored. */
    private static void refresh(ModConfigEvent event) {
        if (event.getConfig() == null) return;
        if (!BackUtils.MOD_ID.equals(event.getConfig().getModId())) return;
        RoleplayLogOverlay.instance().onConfigChanged();
    }
}
