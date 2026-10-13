package net.xlebupaksa.backutils.network;

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

/**
 * Both ends of the banner traffic: the text the server holds, sent to the clients that draw it.
 *
 * <p>The text is the server's, so a player who typed a command cannot be the only one who sees what
 * it set. Every player is told, with no permission check and none possible: what this carries is
 * drawn on everybody's screen.
 *
 * <p>Registered optional, like every other channel here, so a client without this mod still
 * connects. This is the one class in the package that listens on both buses - the payload
 * registration is a mod-bus event and the join is a game-bus one - which the loader handles by
 * registering the two methods separately.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class BannerTextNetwork {

    private BannerTextNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();

        registrar.playToClient(
                BannerTextPayload.TYPE,
                BannerTextPayload.STREAM_CODEC,
                BannerTextNetwork::onBannerText);
    }

    /** Runs on the client only. */
    private static void onBannerText(BannerTextPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;

        int before = BannerTextCache.revision();
        BannerTextCache.set(payload.text());
        // The rows keep the components other mods' effects hang off, so they are only thrown away
        // when the text is not the one already drawn - and a resend of it is not a change.
        if (BannerTextCache.revision() != before) {
            net.xlebupaksa.backutils.ui.HudBanner.instance().onConfigChanged();
        }
    }

    /**
     * Tells a player who has just joined what the banner says.
     *
     * <p>Sent on joining rather than asked for by the client: the client cannot know when the text
     * changed, and the server knows the moment a player can be told anything at all.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        send(player);
    }

    /** Tells every player, which is what a change to the text is. */
    public static void broadcast() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player);
    }

    private static void send(ServerPlayer player) {
        if (!OptionalChannels.negotiated(player, BannerTextPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player,
                new BannerTextPayload(BackUtilsConfig.getBannerText()));
    }
}
