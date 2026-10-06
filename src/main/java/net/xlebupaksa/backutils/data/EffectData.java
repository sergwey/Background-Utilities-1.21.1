package net.xlebupaksa.backutils.data;

import net.xlebupaksa.backutils.item.EffectToolConfig;
import net.xlebupaksa.backutils.item.EffectToolPlacement;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the {@code placed_effect} table, in {@code placed_effect.db}, separate from the other
 * databases so that nothing here can be risked against their schemas.
 *
 * <p>This table is the mod's answer to a gap in Photon: Photon keeps no server-side state, so an
 * effect sent to the clients watching at the time is unknown to a player who arrives afterwards,
 * and forgotten by everyone after a restart. Everything needed to put an effect back is therefore
 * written here, and {@link #all()} is what a joining player is brought up to date from.
 *
 * <p>{@code expires_at} is a wall-clock timestamp rather than a tick count, because a lifetime given
 * in ticks must survive a server restart to mean anything.
 */
public class EffectData extends DataManager {

    private static final RowMapper<PlacedEffect> MAPPER = rs -> {
        EffectPlacement placement = new EffectPlacement(
                EffectToolConfig.Mode.valueOf(rs.getString("mode")),
                rs.getString("dimension"),
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                rs.getFloat("yaw"), rs.getFloat("pitch"),
                EffectToolPlacement.Face.byName(rs.getString("face")));
        return new PlacedEffect(
                rs.getLong("effect_id"),
                placement,
                rs.getString("owner_uuid"),
                rs.getString("owner_name"),
                rs.getString("effect_path"),
                rs.getString("config_nbt"),
                rs.getString("anchor_uuid"),
                rs.getString("target_uuid"),
                rs.getLong("created_at"),
                rs.getLong("expires_at"),
                rs.getInt("duration_ticks"));
    };

    public EffectData(Path worldPath) {
        super(worldPath, "placed_effect.db");
    }

    @Override
    public void initDb() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE TABLE IF NOT EXISTS placed_effect (" +
                    "effect_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "mode TEXT NOT NULL, " +
                    "dimension TEXT NOT NULL, " +
                    "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, " +
                    "yaw REAL NOT NULL DEFAULT 0, pitch REAL NOT NULL DEFAULT 0, " +
                    "face TEXT NOT NULL DEFAULT 'UP', " +
                    "owner_uuid TEXT NOT NULL DEFAULT '', " +
                    "owner_name TEXT NOT NULL DEFAULT '', " +
                    "effect_path TEXT NOT NULL DEFAULT '', " +
                    "config_nbt TEXT NOT NULL DEFAULT '', " +
                    "anchor_uuid TEXT NOT NULL DEFAULT '', " +
                    "target_uuid TEXT NOT NULL DEFAULT '', " +
                    "created_at INTEGER NOT NULL DEFAULT 0, " +
                    "expires_at INTEGER NOT NULL DEFAULT 0, " +
                    "duration_ticks INTEGER NOT NULL DEFAULT 0" +
                    ");");
            // The one column the timed sweep asks about, once a second on every world. Without the
            // index that question is a full scan; with it, it is a lookup that usually finds nothing.
            stmt.execute("CREATE INDEX IF NOT EXISTS placed_effect_expiry "
                    + "ON placed_effect (expires_at);");
        } catch (SQLException e) {
            throw new RuntimeException("Could not open the placed effect database", e);
        }
        addColumn("face", "TEXT NOT NULL DEFAULT 'UP'");
        addColumn("anchor_uuid", "TEXT NOT NULL DEFAULT ''");
        addColumn("target_uuid", "TEXT NOT NULL DEFAULT ''");
    }

    /**
     * Adds a column to a table that was created before it existed.
     *
     * <p>A world that has already run a build of this mod has the table without the column, and
     * {@code CREATE TABLE IF NOT EXISTS} leaves it as it stands. The table is asked what it holds
     * rather than the statement being attempted and its failure swallowed: a duplicate-column error
     * and a database that cannot be written at all are not the same thing, and only one of them is
     * ordinary.
     */
    private void addColumn(String name, String definition) {
        List<String> columns = query("PRAGMA table_info(placed_effect)", rs -> rs.getString("name"));
        if (columns.contains(name)) return;
        update("ALTER TABLE placed_effect ADD COLUMN " + name + " " + definition);
    }

    /** {@return every placed effect, oldest first} */
    public List<PlacedEffect> all() {
        return query("SELECT * FROM placed_effect ORDER BY effect_id", MAPPER);
    }

    /**
     * Stores a newly placed effect and returns it with its assigned id.
     *
     * <p>Refuses a placement the type says cannot be stored, rather than writing a row that could
     * never be shown. The caller places a self effect and keeps nothing; see
     * {@link EffectPlacement} for why.
     *
     * @param anchorUuid    the display an accurate placement hangs off, or empty when it hangs off
     *                      something the world already had
     * @param targetUuid    the entity an entity-attached placement hangs off, or empty for a place
     * @param expiresAtMillis when the effect is removed, or {@link PlacedEffect#PERMANENT}
     */
    public PlacedEffect add(EffectPlacement placement, String ownerUuid, String ownerName,
                            String effectPath, EffectToolConfig config, String anchorUuid,
                            String targetUuid, long createdAtMillis, long expiresAtMillis) {
        if (!EffectPlacement.storable(placement.mode())) {
            throw new IllegalArgumentException(
                    "An effect placement of mode " + placement.mode() + " cannot be stored");
        }

        String anchor = anchorUuid == null ? "" : anchorUuid;
        String target = targetUuid == null ? "" : targetUuid;
        long id = insertAndGetId(
                "INSERT INTO placed_effect (mode, dimension, x, y, z, yaw, pitch, face, " +
                        "owner_uuid, owner_name, effect_path, config_nbt, anchor_uuid, target_uuid, " +
                        "created_at, expires_at, duration_ticks) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                placement.mode().name(), placement.dimension(),
                placement.x(), placement.y(), placement.z(), placement.yaw(), placement.pitch(),
                placement.face().name(),
                ownerUuid == null ? "" : ownerUuid,
                ownerName == null ? "" : ownerName,
                effectPath == null ? "" : effectPath,
                config == null ? "" : config.toStoredText(),
                anchor, target, createdAtMillis, expiresAtMillis,
                config == null ? 0 : config.lifetimeTicks());

        return new PlacedEffect(id, placement, ownerUuid, ownerName, effectPath,
                config == null ? "" : config.toStoredText(), anchor, target, createdAtMillis,
                expiresAtMillis, config == null ? 0 : config.lifetimeTicks());
    }

    /**
     * Stores an effect that hangs off a place rather than off an entity.
     *
     * <p>The shorter way in for the many placements that name no entity: a caller with a block or a
     * point to describe does not have to say so twice.
     */
    public PlacedEffect add(EffectPlacement placement, String ownerUuid, String ownerName,
                            String effectPath, EffectToolConfig config, String anchorUuid,
                            long createdAtMillis, long expiresAtMillis) {
        return add(placement, ownerUuid, ownerName, effectPath, config, anchorUuid, "",
                createdAtMillis, expiresAtMillis);
    }

    public void remove(long effectId) {
        update("DELETE FROM placed_effect WHERE effect_id = ?", effectId);
    }

    /**
     * Writes down a new display for an effect that has lost the one it was placed with.
     *
     * <p>An accurate effect hangs off a blank display, and a display is an entity: it is gone after a
     * restart, and it can be removed by anything that removes entities. The row remembers the one it
     * was placed with by identity, so an effect whose display has gone is an effect that can be put
     * back only by giving it another — and the row has to be told about the new one, or the next
     * restart would look for the old one again and put back a second.
     */
    public void updateAnchor(long effectId, String anchorUuid) {
        update("UPDATE placed_effect SET anchor_uuid = ? WHERE effect_id = ?",
                anchorUuid == null ? "" : anchorUuid, effectId);
    }

    /**
     * Removes every effect that sits in one block, which is what the delete tool does.
     *
     * @return how many were removed
     */
    public int removeAt(EffectPlacement where) {
        return update("DELETE FROM placed_effect WHERE dimension = ? AND "
                        + "CAST(FLOOR(x) AS INTEGER) = ? AND CAST(FLOOR(y) AS INTEGER) = ? AND "
                        + "CAST(FLOOR(z) AS INTEGER) = ?",
                where.dimension(), where.blockX(), where.blockY(), where.blockZ());
    }

    /**
     * Removes every effect one account placed, which is what alt-clicking the delete tool does.
     *
     * @return how many were removed
     */
    public int removeOwnedBy(String ownerUuid) {
        return update("DELETE FROM placed_effect WHERE owner_uuid = ?",
                ownerUuid == null ? "" : ownerUuid);
    }

    /** {@return how many effects were removed}, for the command that empties the table */
    public int removeAll() {
        return update("DELETE FROM placed_effect");
    }

    /**
     * {@return the effects that have run out of time, which the caller should clean up}
     *
     * <p>Asked of the table rather than of every row, because this is the one query that runs on a
     * timer: the sweep happens once a second whether or not anything has expired, and reading the
     * whole table to find nothing would be a full scan and a database connection per second, for
     * ever, on a world where an effect has not been placed in weeks. The index is what makes the
     * question cheap.
     */
    public List<PlacedEffect> expired(long nowMillis) {
        return query("SELECT * FROM placed_effect WHERE expires_at > 0 AND expires_at <= ?",
                MAPPER, nowMillis);
    }
}
