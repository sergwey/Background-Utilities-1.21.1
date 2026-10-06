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

    /** Tells one client whether the player it is driving is being held. */
    public static void send(ServerPlayer player, boolean held) {
        PacketDistributor.sendToPlayer(player, new FreezePayload(held));
    }

    private static void onFreeze(FreezePayload payload, IPayloadContext context) {
        // Onto the game thread rather than off the network one: what this does is reach into the
        // player the client is driving, and the player is the game thread's.
        context.enqueueWork(() -> FrozenClient.held(payload.held()));
    }
}
