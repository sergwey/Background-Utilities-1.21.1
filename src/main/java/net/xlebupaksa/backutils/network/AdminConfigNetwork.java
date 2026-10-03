package net.xlebupaksa.backutils.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The background menu's settings traffic. {@link BackUtilsSettings#check} is the same function the
 * menu runs before sending, and the server runs it again; a batch is checked in full before any of
 * it is written.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class AdminConfigNetwork {

    private AdminConfigNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();

        registrar.playToServer(
                AdminConfigRequestPayload.TYPE,
                AdminConfigRequestPayload.STREAM_CODEC,
                AdminConfigNetwork::onRequest);

        registrar.playToServer(
                AdminConfigEditPayload.TYPE,
                AdminConfigEditPayload.STREAM_CODEC,
                AdminConfigNetwork::onEdit);

        registrar.playToClient(
                AdminConfigPayload.TYPE,
                AdminConfigPayload.STREAM_CODEC,
                AdminConfigNetwork::onValues);

        registrar.playToClient(
                AdminConfigFeedbackPayload.TYPE,
                AdminConfigFeedbackPayload.STREAM_CODEC,
                AdminConfigNetwork::onFeedback);
    }

    // ------------------------------------------------------------------
    // The client's side
    // ------------------------------------------------------------------

    public static void request() {
        PacketDistributor.sendToServer(new AdminConfigRequestPayload());
    }

    /** Sends a batch of changes. The server decides whether they happen. */
    public static void send(AdminConfigEditPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void onValues(AdminConfigPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        AdminConfigCache.set(payload.settings());
    }

    private static void onFeedback(AdminConfigFeedbackPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        AdminConfigFeedbackCache.push(payload.ok(), payload.message());
    }

    // ------------------------------------------------------------------
    // The server's side
    // ------------------------------------------------------------------

    private static void onRequest(AdminConfigRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!AdminNetwork.mayAdminister(player)) return;
        sendSettings(player);
    }

    private static void onEdit(AdminConfigEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!AdminNetwork.mayAdminister(player)) {
            feedback(player, false, "You are not allowed to change the server's settings.");
            return;
        }

        try {
            Map<String, String> checked = new LinkedHashMap<>();
            for (AdminConfigEditPayload.Change change : payload.changes()) {
                String key = change.key();
                try {
                    checked.put(key, BackUtilsSettings.check(key, change.value()));
                } catch (IllegalArgumentException e) {
                    // No values are sent with a refusal: nothing was written, and sending them
                    // would discard the refused edit along with the message explaining why.
                    feedback(player, false, BackUtilsSettings.labelOf(key) + ": " + e.getMessage());
                    return;
                }
            }

            if (checked.isEmpty()) {
                sendSettings(player);
                return;
            }

            for (Map.Entry<String, String> entry : checked.entrySet()) {
                BackUtilsSettings.apply(entry.getKey(), entry.getValue());
            }

            feedback(player, true, saved(checked));
            sendSettings(player);
        } catch (Exception e) {
            BackUtils.LOGGER.error("Could not change the server settings for {}",
                    player.getName().getString(), e);
            feedback(player, false, "Something went wrong on the server; see its log.");
            sendSettings(player);
        }
    }

    /** Sends the whole table, in the order the menu lists it. */
    private static void sendSettings(ServerPlayer player) {
        if (!canReceive(player)) return;

        List<AdminConfigPayload.Row> rows = new ArrayList<>();
        for (BackUtilsSettings.Setting setting : BackUtilsSettings.all()) {
            rows.add(new AdminConfigPayload.Row(setting.key(), setting.label(), setting.group(),
                    setting.kind(), setting.value(), setting.min(), setting.max(),
                    setting.comment()));
        }
        PacketDistributor.sendToPlayer(player, new AdminConfigPayload(rows));
    }

    private static String saved(Map<String, String> checked) {
        if (checked.size() == 1) {
            String key = checked.keySet().iterator().next();
            return BackUtilsSettings.labelOf(key) + " is now " + checked.get(key) + ".";
        }
        List<String> labels = new ArrayList<>();
        for (String key : checked.keySet()) labels.add(BackUtilsSettings.labelOf(key));
        return "Saved " + labels.size() + " settings: " + String.join(", ", labels) + ".";
    }

    /** Answers the change: to the server log, and to the window that asked, never to chat. */
    private static void feedback(ServerPlayer admin, boolean ok, String message) {
        String said = message == null || message.isBlank()
                ? "The change was refused, without a reason." : message;

        if (ok) {
            BackUtils.LOGGER.info("[config] {}: {}", admin.getName().getString(), said);
        } else {
            BackUtils.LOGGER.info("[config] refused for {}: {}",
                    admin.getName().getString(), said);
        }

        if (canReceive(admin)) {
            PacketDistributor.sendToPlayer(admin, new AdminConfigFeedbackPayload(ok, said));
        }
    }

    /** {@return true when this client negotiated the settings channel} */
    public static boolean canReceive(ServerPlayer player) {
        return OptionalChannels.negotiated(player, AdminConfigRequestPayload.TYPE);
    }
}
