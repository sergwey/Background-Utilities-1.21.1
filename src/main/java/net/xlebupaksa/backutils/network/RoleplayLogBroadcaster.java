package net.xlebupaksa.backutils.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.ActionLogEntry;
import net.xlebupaksa.backutils.data.LogData;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.log.RoleplayLog;
import net.xlebupaksa.backutils.profile.ProfileLoader;

import java.util.List;
import java.util.UUID;

/**
 * Sends roleplay log lines to the players allowed to see them. Delivery is always derived from
 * {@code access_list} rather than from a remembered guest list, so an entry reaches everyone it should
 * regardless of whether they were online at the time.
 */
public final class RoleplayLogBroadcaster {

    private RoleplayLogBroadcaster() {}

    /**
     * Sends one specific entry to the given players, and to nobody else. It is re-read from storage, so
     * the stored text stays the single source of truth; an id that cannot be found is skipped, since the
     * next {@link #flushPending} pass would deliver it anyway.
     */
    public static void deliver(MinecraftServer server, long logId, List<UUID> audience) {
        deliver(server, logId, audience, "", 0f, 1f, false);
    }

    /**
     * The same, with a sound for the line to arrive with.
     *
     * @param sound the sound every viewer of this line hears, or empty for none
     * @param roll  whether this is a roll of the dice, which the client draws differently
     */
    public static void deliver(MinecraftServer server, long logId, List<UUID> audience,
                               String sound, float volume, float pitch, boolean roll) {
        LogData log = log();
        if (log == null) return;

        ActionLogEntry entry = null;
        for (ActionLogEntry candidate : log.recent(1)) {
            if (candidate.id() == logId) {
                entry = candidate;
                break;
            }
        }
        if (entry == null) return;

        for (UUID viewerId : audience) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer == null) continue;
            send(viewer, entry, sound, volume, pitch, roll);
        }
    }

    /**
     * Catches every online player up on entries they have not been sent yet, on a slow schedule, for
     * the entries the push path could not cover. A player with no watermark is seeded rather than sent
     * to, so joining does not replay the log.
     */
    public static void flushPending(MinecraftServer server) {
        LogData log = log();
        if (log == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID viewerId = player.getUUID();
            if (!SentLogWatermark.known(viewerId)) {
                seed(log, player);
                continue;
            }

            List<ActionLogEntry> pending =
                    log.visibleTo(viewerId, SentLogWatermark.sentUpTo(viewerId));
            for (ActionLogEntry entry : pending) {
                send(player, entry);
            }
        }
    }

    /** Marks the log's current end as this player's starting point, sending nothing. */
    public static void seed(ServerPlayer player) {
        LogData log = log();
        if (log == null) return;
        seed(log, player);
    }

    private static void seed(LogData log, ServerPlayer player) {
        SentLogWatermark.seed(player.getUUID(), log.latestId());
    }

    /** The single place that renders, records and transmits one entry to one viewer. */
    private static void send(ServerPlayer viewer, ActionLogEntry entry) {
        send(viewer, entry, "", 0f, 1f, false);
    }

    /**
     * The same, with a sound for the line to arrive with. The catch-up sweep sends neither a sound
     * nor the roll flag: an entry that could not be pushed when it happened is not worth a noise
     * minutes later, and a roll it failed to push types itself out under the ordinary animation.
     */
    private static void send(ServerPlayer viewer, ActionLogEntry entry,
                             String sound, float volume, float pitch, boolean roll) {
        // Marked sent even when the client cannot display it, so the sweep does not re-scan for it.
        SentLogWatermark.markSent(viewer.getUUID(), entry.id());
        if (!RoleplayLogNetwork.canReceive(viewer)) return;

        String line = entry.textFor(viewer.getUUID(), ProfileLoader.logName(entry.actorName()));
        // An operator reads the truth beside the line, because the line is what everyone else reads.
        if (viewer.hasPermissions(AdminNetwork.REQUIRED_LEVEL)) {
            line = MarkupUtil.withNote(line, entry.adminNote());
        }

        PacketDistributor.sendToPlayer(viewer, new RoleplayLogPayload(
                entry.id(),
                // Rendered for this viewer, then wrapped so it types itself out; the menu's history is
                // sent plain, so opening it does not retype the backlog.
                RoleplayLog.present(line),
                sound, volume, pitch, roll));
    }

    private static LogData log() {
        var data = BackUtils.data();
        return data == null ? null : data.logs();
    }
}
