package net.xlebupaksa.backutils.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.client.HiddenPlayers;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Carries the set of hidden names to the clients, which is where a player list is drawn.
 *
 * <p>Optional, like every channel this mod registers: a client without this mod draws the lists from
 * what the server sent it and knows nothing to filter, which is exactly what a client without the mod
 * would do, and the server keeps working with it there.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
public final class TabHiddenNetwork {

    private TabHiddenNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();
        registrar.playToClient(
                TabHiddenPayload.TYPE,
                TabHiddenPayload.STREAM_CODEC,
                TabHiddenNetwork::onHidden);
    }

    /** Tells one client which names to keep out of its lists. */
    public static void send(ServerPlayer player, Collection<UUID> hidden) {
        if (!OptionalChannels.negotiated(player, TabHiddenPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, new TabHiddenPayload(List.copyOf(hidden)));
    }

    /** Tells every client, which is what a change to the set means. */
    public static void broadcast(MinecraftServer server, Collection<UUID> hidden) {
        if (server == null) return;
        List<UUID> copy = List.copyOf(hidden);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player, copy);
    }

    private static void onHidden(TabHiddenPayload payload, IPayloadContext context) {
        // Already on the game thread: a registrar defaults to HandlerThread.MAIN, and this is read by
        // the render thread several times a frame.
        HiddenPlayers.apply(payload.hidden());
    }
}
