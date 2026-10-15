package net.xlebupaksa.backutils.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.client.FrozenClient;

/**
 * The one message a suspension needs, which is whether it is happening.
 *
 * <p>Optional, like every channel this mod registers: a server that holds players and a client that
 * has never heard of holding them still work, with the client predicting a fall it is not having while
 * it is in the air and nothing worse.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
public final class FreezeNetwork {

    private FreezeNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();
        registrar.playToClient(
                FreezePayload.TYPE,
                FreezePayload.STREAM_CODEC,
                FreezeNetwork::onFreeze);
    }

    /**
     * Tells one client whether the player it is driving is being held.
     *
     * <p>Nothing is sent to a client that did not negotiate the channel: a player running without
     * this mod is exactly who the optional registration is for, and NeoForge answers a payload on a
     * channel the client never offered by throwing out of {@code ServerCommonPacketListenerImpl.send}
     * rather than dropping it, which would turn holding such a player into a failed command. They are
     * held all the same — the pin and the attributes are what hold them — with only their client's
     * prediction of the fall left unanswered.
     */
    public static void send(ServerPlayer player, boolean held) {
        if (!canReceive(player)) return;
        PacketDistributor.sendToPlayer(player, new FreezePayload(held));
    }

    /** {@return true when this player's client negotiated the freeze channel} */
    public static boolean canReceive(ServerPlayer player) {
        return OptionalChannels.negotiated(player, FreezePayload.TYPE);
    }

    private static void onFreeze(FreezePayload payload, IPayloadContext context) {
        // Already on the game thread, so nothing is hopped and nothing needs to be: a registrar defaults
        // to HandlerThread.MAIN and wraps every handler in MainThreadPayloadHandler, whose own enqueueWork
        // runs the body outright when it is already on that thread. Every other channel here reaches the
        // client's state the same way; registering this one for the network thread would have to bring the
        // hop back with it.
        FrozenClient.held(payload.held());
    }
}
