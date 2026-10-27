package net.xlebupaksa.backutils.network;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.client.NameTags;

/**
 * Both ends of the name-tag traffic: the server's answer, sent to the clients that draw the tags.
 *
 * <p>The setting is the server's and the drawing is the client's, so this is the only way the two can
 * agree. Every player is told, with no permission check and none possible: what this carries is what
 * everybody's screen does.
 *
 * <p>Registered optional, like every other channel here, so a client without this mod still connects —
 * and draws tags the way it always would, which is the one thing a client that has never heard of the
 * setting can do.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class NameTagNetwork {

    private NameTagNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();

        registrar.playToClient(
                NameTagPayload.TYPE,
                NameTagPayload.STREAM_CODEC,
                NameTagNetwork::onNameTags);
    }

    /** Runs on the client only. */
    private static void onNameTags(NameTagPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof LocalPlayer)) return;
        NameTags.setHidden(payload.hidden());
    }

    /**
     * Tells a player who has just joined what the server's answer is.
     *
     * <p>Sent on joining rather than asked for by the client: a client cannot know when the setting
     * changed, and the server knows the moment a player can be told anything at all.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        send(player);
    }

    /** Tells every player, which is what a change to the setting is. */
    public static void broadcast() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player);
    }

    private static void send(ServerPlayer player) {
        if (!OptionalChannels.negotiated(player, NameTagPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, new NameTagPayload(BackUtilsConfig.hidesPlayerNameTags()));
    }
}
