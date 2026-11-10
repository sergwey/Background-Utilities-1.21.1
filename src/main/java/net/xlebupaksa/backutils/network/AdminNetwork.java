package net.xlebupaksa.backutils.network;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.ActionLogEntry;
import net.xlebupaksa.backutils.data.BackUtilsData;
import net.xlebupaksa.backutils.data.LogData;
import net.xlebupaksa.backutils.profile.ProfileLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The administrator menu's traffic; the gate is the server-side permission check. */
@EventBusSubscriber(modid = BackUtils.MOD_ID)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class AdminNetwork {

    /** The level an operator needs, matching the commands. */
    public static final int REQUIRED_LEVEL = ProfileLoader.PROFILE_PERMISSION_LEVEL;

    private AdminNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BackUtils.MOD_ID).optional();

        registrar.playToClient(AdminLogPayload.TYPE, AdminLogPayload.STREAM_CODEC, AdminNetwork::onLog);
        registrar.playToClient(AdminMemoryPayload.TYPE, AdminMemoryPayload.STREAM_CODEC,
                AdminNetwork::onMemory);
        registrar.playToClient(AdminAlertPayload.TYPE, AdminAlertPayload.STREAM_CODEC, AdminNetwork::onAlert);
        registrar.playToClient(AdminMenuOpenPayload.TYPE, AdminMenuOpenPayload.STREAM_CODEC, AdminNetwork::onOpen);

        registrar.playToServer(AdminLogRequestPayload.TYPE, AdminLogRequestPayload.STREAM_CODEC,
                AdminNetwork::onLogRequest);
        registrar.playToServer(AdminActionPayload.TYPE, AdminActionPayload.STREAM_CODEC,
                AdminNetwork::onAction);
        registrar.playToServer(AdminLogBatchPayload.TYPE, AdminLogBatchPayload.STREAM_CODEC,
                AdminNetwork::onBatch);
        registrar.playToServer(AdminMemoryRequestPayload.TYPE,
                AdminMemoryRequestPayload.STREAM_CODEC, AdminNetwork::onMemoryRequest);
    }

    // The client's side

    private static void onLog(AdminLogPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        net.xlebupaksa.backutils.client.AdminMenuCache.set(payload.rows());
    }

    private static void onMemory(AdminMemoryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        AdminMemoryCache.set(payload.player(), payload.rows());
    }

    private static void onAlert(AdminAlertPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        net.xlebupaksa.backutils.client.AdminAlerts.receive(payload);
    }

    private static void onOpen(AdminMenuOpenPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof net.minecraft.client.player.LocalPlayer)) return;
        net.xlebupaksa.backutils.ui.BackUtilsAdminScreen.open();
    }

    // The server's side

    /** Sends the unfiltered log to a player allowed to see it. */
    private static void onLogRequest(AdminLogRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!mayAdminister(player)) return;

        var data = BackUtils.data();
        if (data == null) return;

        // Bounded to the newest rows: the menu used to be sent the whole table, which is unbounded
        // memory and an unbounded packet on a server that has been running for a year. Older entries
        // are no longer shown in the menu; nothing is deleted, and the live log still arrives as it
        // happens. The count and its justification are LogData.SCREEN_ROWS.
        List<ActionLogEntry> entries = data.logs().recent(LogData.SCREEN_ROWS);

        // Two names are repeated far more often than the rows are: the actor, and the witnesses, who
        // are the same few people from row to row. Resolved once each per answer rather than once per
        // row, which matters because nameOf can reach the game profile cache and hence disk.
        Map<String, String> actors = new HashMap<>();
        Map<UUID, String> names = new HashMap<>();

        List<AdminLogPayload.Row> rows = new ArrayList<>(entries.size());
        for (ActionLogEntry entry : entries) {
            // Resolved here: the client has no player list for people who are offline.
            List<String> witnesses = new ArrayList<>();
            for (UUID witness : entry.accessList()) witnesses.add(nameOf(player, witness, names));

            List<String> hiddenFrom = new ArrayList<>();
            for (UUID hidden : entry.hiddenFrom()) hiddenFrom.add(nameOf(player, hidden, names));

            rows.add(new AdminLogPayload.Row(
                    entry.id(),
                    entry.createdAt(),
                    // Rendered for this viewer, markup intact: this text is also what the copy
                    // option puts on the clipboard.
                    entry.textFor(player.getUUID(),
                            actors.computeIfAbsent(entry.actorName(), ProfileLoader::logName)),
                    entry.actorName(),
                    entry.dimension() == null ? "" : entry.dimension(),
                    entry.x(), entry.y(), entry.z(),
                    entry.visibleTo(player.getUUID()),
                    entry.hiddenAll(),
                    witnesses,
                    hiddenFrom,
                    // The one field a player is never sent: what the entry keeps from them.
                    entry.adminNote()));
        }
        PacketDistributor.sendToPlayer(player, new AdminLogPayload(rows));
    }

    /**
     * {@return a name for a UUID, from the server's cache, falling back to the id itself}
     *
     * <p>{@code resolved} is what keeps one name from being looked up once per row: a busy log names
     * the same few witnesses on row after row, and the answer for somebody offline comes from the
     * game profile cache, which is a disk read behind a memory cache. Held for one answer only, so a
     * player who logs in or out between two requests is named as they are at that request.
     */
    private static String nameOf(ServerPlayer context, UUID playerId, Map<UUID, String> resolved) {
        String known = resolved.get(playerId);
        if (known != null) return known;

        String name = nameOf(context, playerId);
        resolved.put(playerId, name);
        return name;
    }

    /** {@return a name for a UUID, from the server's cache, falling back to the id itself} */
    private static String nameOf(ServerPlayer context, UUID playerId) {
        if (playerId == null) return "?";
        ServerPlayer online = context.getServer() == null ? null
                : context.getServer().getPlayerList().getPlayer(playerId);
        if (online != null) return online.getName().getString();

        return context.getServer() == null ? playerId.toString()
                : context.getServer().getProfileCache()
                        .get(playerId)
                        .map(com.mojang.authlib.GameProfile::getName)
                        .orElse(playerId.toString());
    }

    /**
     * Applies one bulk action to a set of entries.
     *
     * <p>One permission check, one target resolved and one report for the set: the administrator
     * selected a list, so it is answered as one act rather than as one report per row. Ids are claims
     * and each is looked up; an entry that has gone is skipped, because a client's list is always a
     * moment older than the table.
     */
    private static void onBatch(AdminLogBatchPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer admin)) return;
        if (!mayAdminister(admin)) {
            reportFailure(admin, "You are not allowed to administer the action log.");
            return;
        }

        BackUtilsData data = BackUtils.data();
        if (data == null) return;
        if (payload.ids().isEmpty()) return;

        switch (payload.action()) {
            case DELETE_RECORD -> deleteMany(admin, data, payload.ids());
            case HIDE_FOR_EVERYONE -> hideForEveryoneMany(admin, data, payload.ids(), true);
            case UNHIDE_FOR_EVERYONE -> hideForEveryoneMany(admin, data, payload.ids(), false);
            case HIDE_FROM_PLAYER, UNHIDE_FROM_PLAYER -> hideFromMany(admin, data, payload.ids(),
                    payload.target(), payload.action() == AdminLogBatchPayload.Kind.HIDE_FROM_PLAYER);
            default -> {
                // NONE: nothing this build knows how to do, so nothing is changed.
            }
        }
    }

    /** Removes every selected entry that is still there. */
    private static void deleteMany(ServerPlayer admin, BackUtilsData data, List<Long> ids) {
        int removed = 0;
        for (long id : ids) {
            if (data.logs().find(id) == null) continue;
            data.logs().delete(id);
            removed++;
        }
        report(admin, removed + " of " + ids.size() + " selected entries were deleted.");
    }

    /** Withholds every selected entry from everyone, or gives it back. */
    private static void hideForEveryoneMany(ServerPlayer admin, BackUtilsData data, List<Long> ids,
                                            boolean hidden) {
        int changed = 0;
        for (long id : ids) {
            if (data.logs().find(id) == null) continue;
            if (hidden) {
                data.logs().hideForEveryone(id);
            } else {
                data.logs().unhideForEveryone(id);
            }
            changed++;
        }
        report(admin, changed + " of " + ids.size() + " selected entries are now "
                + (hidden ? "hidden from everyone." : "visible to everyone."));
    }

    /**
     * Withholds every selected entry from one player.
     *
     * <p>An entry this player never witnessed is skipped rather than counted: a line they never had
     * cannot be withheld from them, and reporting it as done would be a change that did not happen.
     */
    private static void hideFromMany(ServerPlayer admin, BackUtilsData data, List<Long> ids,
                                     String targetName, boolean hidden) {
        UUID targetId = resolveUuid(admin, targetName);
        if (targetId == null) {
            reportFailure(admin, "No player called '" + targetName + "' here, so nothing changed.");
            return;
        }

        int changed = 0;
        for (long id : ids) {
            ActionLogEntry entry = data.logs().find(id);
            if (entry == null) continue;
            if (hidden && !entry.accessList().contains(targetId)) continue;
            if (hidden) {
                data.logs().hideFrom(id, targetId);
            } else {
                data.logs().unhideFrom(id, targetId);
            }
            changed++;
        }
        report(admin, changed + " of " + ids.size() + " selected entries are now "
                + (hidden ? "hidden from " : "visible to ") + nameOf(admin, targetId) + ".");
    }

    /** Runs one of the options offered when an entry is clicked. */
    private static void onAction(AdminActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!mayAdminister(player)) {
            reportFailure(player, "You are not allowed to administer the action log.");
            return;
        }

        var data = BackUtils.data();
        if (data == null) return;

        ActionLogEntry entry = data.logs().find(payload.id());
        if (entry == null) {
            reportFailure(player, "That action no longer exists.");
            return;
        }

        switch (payload.action()) {
            case HIDE_FOR_EVERYONE -> {
                data.logs().hideForEveryone(entry.id());
                report(player, "Action #" + entry.id() + " is now hidden from everyone.");
            }
            case UNHIDE_FOR_EVERYONE -> {
                data.logs().unhideForEveryone(entry.id());
                report(player, "Action #" + entry.id() + " is visible to everyone again.");
            }
            case HIDE_FROM_PLAYER -> setHiddenFor(player, data, entry, payload.target(), true);
            case UNHIDE_FROM_PLAYER -> setHiddenFor(player, data, entry, payload.target(), false);
            case HIDE_FROM_MANY -> setHiddenForMany(player, data, entry, payload.target(), true);
            case UNHIDE_FROM_MANY -> setHiddenForMany(player, data, entry, payload.target(), false);
            case DELETE_RECORD -> {
                data.logs().delete(entry.id());
                report(player, "Action #" + entry.id() + " was deleted.");
                // No list is pushed: the client re-asks for the log after every action.
            }
            case TELEPORT_TO_ACTION -> teleportToAction(player, entry);
            case TELEPORT_TO_ACTOR -> teleportToActor(player, entry);
        }
    }

    /**
     * Sends one player's memory: every line they have, hidden ones included.
     *
     * <p>The line is rendered as that player sees it — the underline on the actor's name is theirs —
     * because this is their memory and not the administrator's view of the log. A name the server
     * cannot resolve is answered with an empty list rather than with silence, so a screen waiting for
     * an answer stops saying it is waiting.
     */
    private static void onMemoryRequest(AdminMemoryRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!mayAdminister(player)) return;

        BackUtilsData data = BackUtils.data();
        if (data == null) return;

        String wanted = payload.player();
        UUID targetId = wanted.isBlank() ? null : resolveUuid(player, wanted);
        if (targetId == null) {
            PacketDistributor.sendToPlayer(player, new AdminMemoryPayload(wanted, List.of()));
            return;
        }

        Map<String, String> actors = new HashMap<>();
        List<AdminMemoryPayload.Row> rows = new ArrayList<>();
        for (ActionLogEntry entry : data.logs().rememberedBy(targetId, LogData.SCREEN_ROWS)) {
            rows.add(new AdminMemoryPayload.Row(
                    entry.id(),
                    entry.createdAt(),
                    entry.textFor(targetId,
                            actors.computeIfAbsent(entry.actorName(), ProfileLoader::logName)),
                    entry.actorName(),
                    entry.dimension() == null ? "" : entry.dimension(),
                    entry.x(), entry.y(), entry.z(),
                    entry.hiddenAll(),
                    entry.hiddenFrom().contains(targetId),
                    entry.adminNote()));
        }
        PacketDistributor.sendToPlayer(player, new AdminMemoryPayload(wanted, rows));
    }

    /** Records what an administrator did, in the server log rather than in their chat. */
    private static void report(ServerPlayer admin, String message) {
        BackUtils.LOGGER.info("[admin] {}: {}", admin.getName().getString(), message);
    }

    /**
     * Reports something that went wrong, in chat. Not routed through {@link #report}: a failure has
     * to be seen, because nothing changed and the menu looks exactly as it did before.
     */
    private static void reportFailure(ServerPlayer admin, String message) {
        admin.sendSystemMessage(Component.literal("§c" + message), false);
    }

    /**
     * Applies a hide or unhide to several players: a selector means "whoever is near me right now"
     * and is resolved here with the administrator as the source, while a name list means exactly
     * those people.
     */
    private static void setHiddenForMany(ServerPlayer admin,
                                         net.xlebupaksa.backutils.data.BackUtilsData data,
                                         ActionLogEntry entry, String target, boolean hidden) {
        if (target == null || target.isBlank()) {
            reportFailure(admin, "No players given.");
            return;
        }

        List<UUID> targets = new ArrayList<>();
        if (target.startsWith("@")) {
            try {
                var selector = new net.minecraft.commands.arguments.selector.EntitySelectorParser(
                        new com.mojang.brigadier.StringReader(target), true).parse();
                for (ServerPlayer matched : selector.findPlayers(admin.createCommandSourceStack())) {
                    targets.add(matched.getUUID());
                }
            } catch (Exception e) {
                reportFailure(admin, "That selector could not be read: " + e.getMessage());
                return;
            }
        } else {
            for (String name : target.split(";")) {
                String trimmed = name.trim();
                if (trimmed.isEmpty()) continue;
                UUID id = resolveUuid(admin, trimmed);
                if (id != null) targets.add(id);
            }
        }

        if (targets.isEmpty()) {
            reportFailure(admin, "Nothing matched, so nothing was changed.");
            return;
        }

        for (UUID id : targets) {
            if (hidden) {
                data.logs().hideFrom(entry.id(), id);
            } else {
                data.logs().unhideFrom(entry.id(), id);
            }
        }

        int count = targets.size();
        report(admin, "Action #" + entry.id() + " is now "
                + (hidden ? "hidden from " : "visible to ") + count + " player(s).");
    }

    /** {@return the UUID behind a name, online or from the server's cache, or null} */
    private static UUID resolveUuid(ServerPlayer admin, String name) {
        ServerPlayer online = admin.getServer() == null ? null
                : admin.getServer().getPlayerList().getPlayerByName(name);
        return online != null ? online.getUUID() : offlineUuid(admin, name);
    }

    /**
     * Withholds an entry from one witness, or gives it back. The name is resolved to a UUID, so the
     * witness list itself is never touched and both directions remain available.
     */
    private static void setHiddenFor(ServerPlayer admin,
                                     net.xlebupaksa.backutils.data.BackUtilsData data,
                                     ActionLogEntry entry, String targetName, boolean hidden) {
        if (targetName == null || targetName.isBlank()) {
            reportFailure(admin, "No player given.");
            return;
        }

        ServerPlayer target = admin.getServer() == null ? null
                : admin.getServer().getPlayerList().getPlayerByName(targetName);

        UUID targetId;
        String shownName;
        if (target != null) {
            targetId = target.getUUID();
            shownName = target.getName().getString();
        } else {
            targetId = offlineUuid(admin, targetName);
            shownName = targetName;
            if (targetId == null) {
                reportFailure(admin, "'" + targetName + "' has never been seen on this server, "
                        + "so there is no id to use.");
                return;
            }
        }

        if (hidden) {
            data.logs().hideFrom(entry.id(), targetId);
        } else {
            data.logs().unhideFrom(entry.id(), targetId);
        }

        report(admin, "Action #" + entry.id() + " is now "
                + (hidden ? "hidden from " : "visible to ") + shownName + ".");
    }

    /**
     * {@return the UUID behind a name, from the server's own name cache}
     *
     * <p>Hiding from somebody offline still needs their id, and the cache holds one for every
     * player who has ever joined.
     */
    private static UUID offlineUuid(ServerPlayer admin, String name) {
        if (admin.getServer() == null) return null;
        return admin.getServer().getProfileCache()
                .get(name)
                .map(com.mojang.authlib.GameProfile::getId)
                .orElse(null);
    }

    private static void teleportToAction(ServerPlayer admin, ActionLogEntry entry) {
        ServerLevel level = levelOf(admin, entry.dimension());
        if (level == null) {
            reportFailure(admin, "That action has no recorded location.");
            return;
        }
        admin.teleportTo(level, entry.x(), entry.y(), entry.z(), admin.getYRot(), admin.getXRot());
        report(admin, "Teleported to action #" + entry.id() + ".");
    }

    private static void teleportToActor(ServerPlayer admin, ActionLogEntry entry) {
        ServerPlayer actor = admin.getServer() == null ? null
                : admin.getServer().getPlayerList().getPlayer(entry.actorUuid());
        if (actor == null) {
            // Not a failure: the teleport still happens, just to the other place.
            report(admin, entry.actorName() + " is not online; teleporting to the action instead.");
            teleportToAction(admin, entry);
            return;
        }
        admin.teleportTo(actor.serverLevel(), actor.getX(), actor.getY(), actor.getZ(),
                admin.getYRot(), admin.getXRot());
        report(admin, "Teleported to " + actor.getName().getString() + ".");
    }

    private static ServerLevel levelOf(ServerPlayer admin, String dimension) {
        if (dimension == null || dimension.isBlank() || admin.getServer() == null) return null;
        for (ServerLevel level : admin.getServer().getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) return level;
        }
        return null;
    }

    /** {@return true when this player may use the administrator menu at all} */
    public static boolean mayAdminister(ServerPlayer player) {
        return player.hasPermissions(REQUIRED_LEVEL) && canReceive(player);
    }

    /** {@return true when this player's client negotiated the administrator channel} */
    public static boolean canReceive(ServerPlayer player) {
        return OptionalChannels.negotiated(player, AdminLogPayload.TYPE);
    }

    public static void requestLog() {
        PacketDistributor.sendToServer(new AdminLogRequestPayload());
    }

    /** Asks for one player's memory; the name is the one the administrator picked. */
    public static void requestMemory(String player) {
        PacketDistributor.sendToServer(new AdminMemoryRequestPayload(player));
    }

    /** Sends one option's request. */
    public static void send(AdminActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** Sends one bulk action. */
    public static void sendBatch(AdminLogBatchPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
