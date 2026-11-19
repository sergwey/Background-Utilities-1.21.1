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
import net.xlebupaksa.backutils.data.NoteData;
import net.xlebupaksa.backutils.data.NoteService;

import java.util.UUID;

/** The optional admin channel's note requests, replies and edit lease. */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class NoteNetwork {

    private NoteNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();

        registrar.playToServer(NoteRequestPayload.TYPE, NoteRequestPayload.STREAM_CODEC,
                NoteNetwork::onRequest);
        registrar.playToServer(NoteEditRequestPayload.TYPE, NoteEditRequestPayload.STREAM_CODEC,
                NoteNetwork::onEdit);
        registrar.playToServer(NoteSavePayload.TYPE, NoteSavePayload.STREAM_CODEC,
                NoteNetwork::onSave);
        registrar.playToServer(NoteCancelPayload.TYPE, NoteCancelPayload.STREAM_CODEC,
                NoteNetwork::onCancel);
        registrar.playToServer(NoteHeartbeatPayload.TYPE, NoteHeartbeatPayload.STREAM_CODEC,
                NoteNetwork::onHeartbeat);

        registrar.playToClient(NoteStatePayload.TYPE, NoteStatePayload.STREAM_CODEC,
                NoteNetwork::onState);
        registrar.playToClient(NoteFeedbackPayload.TYPE, NoteFeedbackPayload.STREAM_CODEC,
                NoteNetwork::onFeedback);
    }

    public static void request(String player) {
        PacketDistributor.sendToServer(new NoteRequestPayload(player));
    }

    public static void requestEdit(String player) {
        PacketDistributor.sendToServer(new NoteEditRequestPayload(player));
    }

    public static void save(String player, int version, java.util.List<String> pages) {
        PacketDistributor.sendToServer(new NoteSavePayload(player, version, pages));
    }

    public static void cancel(String player) {
        PacketDistributor.sendToServer(new NoteCancelPayload(player));
    }

    public static void heartbeat(String player) {
        PacketDistributor.sendToServer(new NoteHeartbeatPayload(player));
    }

    // Client side.

    private static void onState(NoteStatePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        AdminNotesCache.set(payload);
    }

    private static void onFeedback(NoteFeedbackPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        AdminNotesCache.setFeedback(payload);
    }

    // Server side.

    private static void onRequest(NoteRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        try {
            handleRequest(payload, context);
        } catch (RuntimeException error) {
            fail(admin, payload.player(), error);
        }
    }

    private static void onEdit(NoteEditRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        try {
            handleEdit(payload, context);
        } catch (RuntimeException error) {
            fail(admin, payload.player(), error);
        }
    }

    private static void onSave(NoteSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        try {
            handleSave(payload, context);
        } catch (RuntimeException error) {
            fail(admin, payload.player(), error);
        }
    }

    private static void onCancel(NoteCancelPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        try {
            handleCancel(payload, context);
        } catch (RuntimeException error) {
            fail(admin, payload.player(), error);
        }
    }

    private static void onHeartbeat(NoteHeartbeatPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        try {
            handleHeartbeat(payload, context);
        } catch (RuntimeException error) {
            fail(admin, payload.player(), error);
        }
    }

    private static void fail(ServerPlayer admin, String player, RuntimeException error) {
        BackUtils.LOGGER.error("Could not process administrator notes for {}", player, error);
        feedback(admin, player, NoteFeedbackPayload.Reason.ERROR, "", 0);
    }

    private static void handleRequest(NoteRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        if (!canAdminister(admin)) return;
        UUID target = AdminNetwork.resolveUuid(admin, payload.player());
        if (target == null) {
            feedback(admin, payload.player(), NoteFeedbackPayload.Reason.UNKNOWN_PLAYER, "", 0);
            return;
        }
        sendState(admin, payload.player(), target);
    }

    private static void handleEdit(NoteEditRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        if (!canAdminister(admin)) return;
        UUID target = AdminNetwork.resolveUuid(admin, payload.player());
        if (target == null) {
            feedback(admin, payload.player(), NoteFeedbackPayload.Reason.UNKNOWN_PLAYER, "", 0);
            return;
        }
        NoteService.LockResult lock = NoteService.acquire(admin, target, payload.player());
        sendState(admin, payload.player(), target);
        if (!lock.granted()) {
            int seconds = Math.max(0, (int) ((lock.state().lease() == null ? 0L
                    : lock.state().lease().expiresAtMillis() - System.currentTimeMillis()) / 1000L));
            feedback(admin, payload.player(), NoteFeedbackPayload.Reason.LOCKED,
                    lock.holderName(), seconds);
            return;
        }
        broadcast(target, payload.player());
    }

    private static void handleSave(NoteSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        if (!canAdminister(admin)) return;
        UUID target = AdminNetwork.resolveUuid(admin, payload.player());
        if (target == null) {
            feedback(admin, payload.player(), NoteFeedbackPayload.Reason.UNKNOWN_PLAYER, "", 0);
            return;
        }
        NoteService.SaveAttempt attempt = NoteService.save(admin, target, payload.player(),
                payload.baseVersion(), payload.pages());
        sendState(admin, payload.player(), target);
        switch (attempt.result()) {
            case SAVED -> {
                BackUtils.LOGGER.info("Administrator {} saved notes for {} (version {}, {} pages)",
                        admin.getName().getString(), payload.player(),
                        attempt.state().document().version(), payload.pages().size());
                feedback(admin, payload.player(), NoteFeedbackPayload.Reason.SAVED, "", 0);
                broadcast(target, payload.player());
            }
            case VERSION_MISMATCH -> feedback(admin, payload.player(),
                    NoteFeedbackPayload.Reason.VERSION_CONFLICT, "", 0);
            case INVALID -> feedback(admin, payload.player(),
                    NoteFeedbackPayload.Reason.INVALID, "", 0);
        }
    }

    private static void handleCancel(NoteCancelPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        if (!canAdminister(admin)) return;
        UUID target = AdminNetwork.resolveUuid(admin, payload.player());
        if (target == null) return;
        NoteService.release(admin, target, payload.player());
        sendState(admin, payload.player(), target);
        broadcast(target, payload.player());
    }

    private static void handleHeartbeat(NoteHeartbeatPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        if (!canAdminister(admin)) return;
        UUID target = AdminNetwork.resolveUuid(admin, payload.player());
        if (target == null) return;
        NoteService.State state = NoteService.heartbeat(admin, target, payload.player());
        sendState(admin, payload.player(), target);
        if (state.lease() == null || !state.lease().heldBy(admin)) {
            feedback(admin, payload.player(), NoteFeedbackPayload.Reason.LOCK_LOST, "", 0);
        }
    }

    /** Empties the lease cache when its holder leaves, so nobody waits out a dead editor's lock. */
    @SubscribeEvent
    public static void releaseOnLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) NoteService.releaseAll(player);
    }

    @SubscribeEvent
    public static void clearOnStop(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        NoteService.clear();
    }

    private static void sendState(ServerPlayer admin, String playerName, UUID target) {
        NoteService.State state = NoteService.state(target, playerName);
        PacketDistributor.sendToPlayer(admin, toPayload(playerName, state));
    }

    private static NoteStatePayload toPayload(String requestName, NoteService.State state) {
        NoteService.Lease lease = state.lease();
        NoteData.Document document = state.document();
        String playerName = state.playerName().isBlank() ? requestName : state.playerName();
        return new NoteStatePayload(requestName, playerName, document.version(), document.texts(),
                document.updatedAt(), document.updatedBy(),
                lease == null ? "" : lease.holderUuid(), lease == null ? "" : lease.holderName(),
                lease == null ? 0L : lease.expiresAtMillis());
    }

    private static void broadcast(UUID target, String playerName) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks
                .getCurrentServer();
        if (server == null) return;
        for (ServerPlayer witness : server.getPlayerList().getPlayers()) {
            if (!canAdminister(witness) || !canReceive(witness)) continue;
            try {
                sendState(witness, playerName, target);
            } catch (RuntimeException error) {
                BackUtils.LOGGER.error("Could not broadcast notes for {}", playerName, error);
            }
        }
    }

    private static void feedback(ServerPlayer admin, String player, NoteFeedbackPayload.Reason reason,
            String holder, int seconds) {
        PacketDistributor.sendToPlayer(admin,
                new NoteFeedbackPayload(player, reason, holder, seconds));
    }

    /** Incoming note packets need the permission, not a negotiated outgoing channel. */
    private static boolean canAdminister(ServerPlayer player) {
        return player.hasPermissions(AdminNetwork.REQUIRED_LEVEL);
    }

    private static boolean canReceive(ServerPlayer player) {
        return OptionalChannels.negotiated(player, NoteRequestPayload.TYPE);
    }
}
