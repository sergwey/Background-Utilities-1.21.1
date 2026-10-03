package net.xlebupaksa.backutils.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.MusicZone;

/**
 * Both ends of the zone music traffic.
 *
 * <p>Registered optional, so a client without this mod still connects and simply hears nothing.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class ZoneMusicNetwork {

    private ZoneMusicNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();

        registrar.playToClient(
                ZoneMusicPayload.TYPE,
                ZoneMusicPayload.STREAM_CODEC,
                ZoneMusicNetwork::onZoneMusic);
    }

    /** Runs on the client only. */
    private static void onZoneMusic(ZoneMusicPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        net.xlebupaksa.backutils.client.ZoneMusicPlayer.apply(payload);
    }

    /** Starts or retunes a zone's music for one player. */
    public static void sendPlay(ServerPlayer player, MusicZone zone) {
        if (!canReceive(player)) return;
        PacketDistributor.sendToPlayer(player, ZoneMusicPayload.play(
                zone.id(), zone.sound(), zone.source(), zone.volume(), zone.pitch()));
    }

    /** Fades one player's copy of a zone out. */
    public static void sendStop(ServerPlayer player, long zoneId) {
        if (!canReceive(player)) return;
        PacketDistributor.sendToPlayer(player, ZoneMusicPayload.stop(zoneId));
    }

    public static boolean canReceive(ServerPlayer player) {
        return OptionalChannels.negotiated(player, ZoneMusicPayload.TYPE);
    }
}
