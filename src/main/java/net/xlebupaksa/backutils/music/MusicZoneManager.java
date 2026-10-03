package net.xlebupaksa.backutils.music;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.MusicZone;
import net.xlebupaksa.backutils.data.MusicZoneData;
import net.xlebupaksa.backutils.network.ZoneMusicNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps track of which players are inside which music zones. Detection is server-side and
 * authoritative, and only transitions are sent, so a long stay in a zone costs no traffic. Zones are
 * anonymous, so nothing here is addressed by name.
 */
public final class MusicZoneManager {

    /** Which zones each online player is currently inside. */
    private static final Map<UUID, Set<Long>> INSIDE = new HashMap<>();

    private MusicZoneManager() {}

    /** Tests every player against every zone and sends the differences, on a slow schedule: half a
     * second of latency is not noticeable under a fade, and the cost stays off the tick loop. */
    public static void sweep(MinecraftServer server) {
        MusicZoneData zones = zones();
        if (zones == null) return;

        long now = System.currentTimeMillis();

        // Zones that have run out of time stop their music and disappear.
        for (MusicZone zone : zones.expired(now)) {
            stopForEveryone(server, zone.id());
            zones.delete(zone.id());
        }

        List<MusicZone> live = zones.all();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Set<Long> was = INSIDE.computeIfAbsent(player.getUUID(), key -> new HashSet<>());
            Set<Long> isNow = new HashSet<>();

            String dimension = player.level().dimension().location().toString();
            for (MusicZone zone : live) {
                if (zone.contains(dimension, player.getX(), player.getY(), player.getZ())) {
                    isNow.add(zone.id());
                }
            }

            for (MusicZone zone : live) {
                if (isNow.contains(zone.id()) && !was.contains(zone.id())) {
                    ZoneMusicNetwork.sendPlay(player, zone);
                }
            }
            for (Long zoneId : was) {
                if (!isNow.contains(zoneId)) {
                    ZoneMusicNetwork.sendStop(player, zoneId);
                }
            }

            was.clear();
            was.addAll(isNow);
        }
    }

    // ------------------------------------------------------------------
    // Creating
    // ------------------------------------------------------------------

    /** Adds a spherical zone centred on the given position. */
    public static MusicZone addRadius(ServerPlayer source, double radius, String sound,
                                      String channel, float volume, float pitch,
                                      long expiresAtMillis) {
        MusicZoneData zones = zones();
        if (zones == null) return null;

        return zones.add(
                source.level().dimension().location().toString(), true,
                source.getX(), source.getY(), source.getZ(),
                0, 0, 0,
                radius, sound, channel, volume, pitch, expiresAtMillis);
    }

    /** Adds a box zone between two corners. */
    public static MusicZone addBox(ServerPlayer source, double x1, double y1, double z1,
                                   double x2, double y2, double z2, String sound,
                                   String channel, float volume, float pitch,
                                   long expiresAtMillis) {
        MusicZoneData zones = zones();
        if (zones == null) return null;

        return zones.add(
                source.level().dimension().location().toString(), false,
                x1, y1, z1, x2, y2, z2,
                0, sound, channel, volume, pitch, expiresAtMillis);
    }

    // ------------------------------------------------------------------
    // Removing
    // ------------------------------------------------------------------

    /** Removes one zone by its id, or returns null when no zone has that id. There is deliberately
     * no "stop everything": one mistyped command would silence a whole server's ambience. */
    public static MusicZone stopById(MinecraftServer server, long zoneId) {
        MusicZoneData zones = zones();
        if (zones == null) return null;

        MusicZone target = null;
        for (MusicZone zone : zones.all()) {
            if (zone.id() == zoneId) {
                target = zone;
                break;
            }
        }
        if (target == null) return null;

        zones.delete(zoneId);
        // Sent to everyone rather than only to whoever is standing inside: a client not holding
        // this loop ignores the stop, and a player who entered between the sweep and now cannot be
        // left with a loop that is never stopped.
        if (server != null) stopForEveryone(server, zoneId);
        forgetEverywhere(zoneId);
        return target;
    }

    /** {@return the zones this player is standing in, which were removed}, all of them when zones
     * overlap, since "the zone the player is standing in" has no single answer. */
    public static List<MusicZone> stopContaining(ServerPlayer player) {
        MusicZoneData zones = zones();
        if (zones == null) return List.of();

        String dimension = player.level().dimension().location().toString();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        List<MusicZone> removed = new ArrayList<>();
        for (MusicZone zone : zones.all()) {
            if (zone.contains(dimension, x, y, z)) {
                zones.delete(zone.id());
                if (player.getServer() != null) stopForEveryone(player.getServer(), zone.id());
                forgetEverywhere(zone.id());
                removed.add(zone);
            }
        }
        return removed;
    }

    /** {@return every zone, for the list command} */
    public static List<MusicZone> all() {
        MusicZoneData zones = zones();
        return zones == null ? List.of() : zones.all();
    }

    /** Drops a player's record, so they are treated as fresh on their next login. */
    public static void forget(UUID playerId) {
        INSIDE.remove(playerId);
    }

    public static void clear() {
        INSIDE.clear();
    }

    private static void stopForEveryone(MinecraftServer server, long zoneId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ZoneMusicNetwork.sendStop(player, zoneId);
        }
        forgetEverywhere(zoneId);
    }

    /** Removes a zone id from every player's record, so nobody is left holding a dead loop. */
    private static void forgetEverywhere(long zoneId) {
        for (Set<Long> held : INSIDE.values()) {
            held.remove(zoneId);
        }
    }

    private static MusicZoneData zones() {
        var data = BackUtils.data();
        return data == null ? null : data.musicZones();
    }
}
