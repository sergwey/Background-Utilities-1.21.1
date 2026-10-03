package net.xlebupaksa.backutils.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.ActionLogEntry;
import net.xlebupaksa.backutils.profile.ProfileLoader;
import net.xlebupaksa.backutils.ui.RoleplayLogOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Both ends of the roleplay log traffic.
 *
 * <p>The payloads are registered with {@code optional()}, so a client without them can still connect
 * and simply receives no log lines. The handlers live here, in common code, so that registering a
 * method reference never resolves a client-only class on a dedicated server.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class RoleplayLogNetwork {

    private RoleplayLogNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();

        registrar.playToClient(
                RoleplayLogPayload.TYPE,
                RoleplayLogPayload.STREAM_CODEC,
                RoleplayLogNetwork::onLine);

        registrar.playToClient(
                LogHistoryPayload.TYPE,
                LogHistoryPayload.STREAM_CODEC,
                RoleplayLogNetwork::onHistory);

        registrar.playToServer(
                LogHistoryRequestPayload.TYPE,
                LogHistoryRequestPayload.STREAM_CODEC,
                RoleplayLogNetwork::onHistoryRequest);
    }

    /** Runs on the client only. The registrar already marshals this to the main thread. */
    private static void onLine(RoleplayLogPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        RoleplayLogOverlay.instance().accept(payload.id(), payload.text());
    }

    private static void onHistory(LogHistoryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        LogHistoryCache.set(payload.rows());
    }

    /**
     * Runs on the server, in answer to the menu being opened. The reply is built from
     * {@code access_list} rather than from any remembered guest list, so it is exactly the history this
     * player is entitled to see, including entries from before they logged in.
     */
    private static void onHistoryRequest(LogHistoryRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!canReceive(player)) return;

        var data = BackUtils.data();
        if (data == null) return;

        UUID viewer = player.getUUID();
        List<ActionLogEntry> visible = data.logs().visibleTo(viewer, 0L);

        // Resolved once per actor rather than once per entry: this walks the profile database, and a
        // busy server's log can hold many lines from the same few people.
        Map<String, String> names = new HashMap<>();

        List<LogHistoryPayload.Row> rows = new ArrayList<>(visible.size());
        for (ActionLogEntry entry : visible) {
            String name = names.computeIfAbsent(entry.actorName(), ProfileLoader::logName);
            rows.add(new LogHistoryPayload.Row(
                    entry.id(), entry.createdAt(), entry.textFor(viewer, name)));
        }

        PacketDistributor.sendToPlayer(player, new LogHistoryPayload(rows));
    }

    /**
     * {@return true when this player's client can receive log traffic; the payloads are optional, so a
     * client that never negotiated the channel must not be sent to}
     */
    public static boolean canReceive(ServerPlayer player) {
        return OptionalChannels.negotiated(player, RoleplayLogPayload.TYPE);
    }

    /** Asks the server for the whole history visible to this player. */
    public static void requestHistory() {
        PacketDistributor.sendToServer(new LogHistoryRequestPayload());
    }
}
