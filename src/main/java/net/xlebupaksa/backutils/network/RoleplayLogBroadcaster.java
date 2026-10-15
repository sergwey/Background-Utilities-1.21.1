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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        // Asked for by id rather than by reading the newest row and comparing: the row is delivered by
        // the code that has just inserted it, so it is almost always the newest, and the one case where
        // it is not — a second row written in between — is the case that would silently deliver nothing.
        ActionLogEntry entry = log.find(logId);
        if (entry == null) return;

        // The actor's name is resolved through this map rather than once per witness: see send.
        Map<String, String> names = new HashMap<>();
        for (UUID viewerId : audience) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer == null) continue;
            send(viewer, entry, names, sound, volume, pitch, roll);
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

        // One name per actor for the whole sweep, not one per viewer per entry: a busy log holds many
        // lines from the same few people, and the sweep walks every player.
        Map<String, String> names = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID viewerId = player.getUUID();
            if (!SentLogWatermark.known(viewerId)) {
                seed(log, player);
                continue;
            }

            List<ActionLogEntry> pending =
                    log.visibleTo(viewerId, SentLogWatermark.sentUpTo(viewerId));
            for (ActionLogEntry entry : pending) {
                send(player, entry, names);
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
    private static void send(ServerPlayer viewer, ActionLogEntry entry, Map<String, String> names) {
        send(viewer, entry, names, "", 0f, 1f, false);
    }

    /**
     * The same, with a sound for the line to arrive with. The catch-up sweep sends neither a sound
     * nor the roll flag: an entry that could not be pushed when it happened is not worth a noise
     * minutes later, and a roll it failed to push types itself out under the ordinary animation.
     *
     * @param names the actor names already resolved for this pass, which is what keeps one name from
     *              being read once per witness: {@link ProfileLoader#logName} is up to three queries
     *              with a connection each, so an action witnessed by ten players would otherwise read
     *              the same name thirty times on the server thread, at the moment of the action
     */
    private static void send(ServerPlayer viewer, ActionLogEntry entry, Map<String, String> names,
                             String sound, float volume, float pitch, boolean roll) {
        // Marked sent even when the client cannot display it, so the sweep does not re-scan for it.
        SentLogWatermark.markSent(viewer.getUUID(), entry.id());
        if (!RoleplayLogNetwork.canReceive(viewer)) return;

        String name = names.computeIfAbsent(entry.actorName(), ProfileLoader::logName);
        String line = entry.textFor(viewer.getUUID(), name);
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
