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
import net.xlebupaksa.backutils.data.MarkupUtil;
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
 * and simply receives no log lines. The handlers live here, in common code, so that a method reference
 * never resolves a client-only class on a dedicated server.
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
        RoleplayLogOverlay.instance().accept(payload.id(), payload.text(), payload.roll());
        // The sound belongs to the line rather than to the text, so opening the menu does not
        // replay the rolls it holds.
        if (payload.hasSound()) {
            net.xlebupaksa.backutils.client.ClientSounds.play(
                    payload.sound(), payload.volume(), payload.pitch());
        }
    }

    private static void onHistory(LogHistoryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        LogHistoryCache.set(payload.rows());
    }

    /**
     * Runs on the server, in answer to the menu being opened: exactly the history this player may see,
     * including entries from before they logged in.
     */
    private static void onHistoryRequest(LogHistoryRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!canReceive(player)) return;

        var data = BackUtils.data();
        if (data == null) return;

        UUID viewer = player.getUUID();
        List<ActionLogEntry> visible = data.logs().visibleTo(viewer, 0L);
        // An operator's history reads like an operator's log: the line everyone saw, with the
        // administrator's note beside it.
        boolean notes = player.hasPermissions(AdminNetwork.REQUIRED_LEVEL);

        // Resolved once per actor, not per entry: a busy log holds many lines from the same few people.
        Map<String, String> names = new HashMap<>();

        List<LogHistoryPayload.Row> rows = new ArrayList<>(visible.size());
        for (ActionLogEntry entry : visible) {
            String name = names.computeIfAbsent(entry.actorName(), ProfileLoader::logName);
            String line = entry.textFor(viewer, name);
            if (notes) line = MarkupUtil.withNote(line, entry.adminNote());
            rows.add(new LogHistoryPayload.Row(entry.id(), entry.createdAt(), line));
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
