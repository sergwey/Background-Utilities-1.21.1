package net.xlebupaksa.backutils.data;

import net.xlebupaksa.backutils.item.EffectToolConfig;
import net.xlebupaksa.backutils.item.EffectToolPlacement;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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
                modeOf(rs.getString("mode")),
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

    /**
     * {@return the mode a stored name stands for, or a plain block placement for one this build does
     * not know}
     *
     * <p>Read tolerantly, the way the face column beside it is, because a throw here is not one
     * caller's problem: this mapper is behind everything that reads the table at once — the expiry
     * sweep, the replay a joining player and a chunk send are brought up to date from, and the delete
     * tool — so a single row written by another build would stop all of them until somebody corrected
     * the database by hand. A name from another build is not a corruption to report; it is a row this
     * build cannot place as it meant to, and the rest of the table is still good.
     *
     * <p>{@link EffectToolConfig.Mode#byName} is the tolerant reading of the two lookups the enum
     * carries, since it answers a {@code DataResult} rather than throwing. An unknown name is taken
     * for {@link EffectToolConfig.Mode#BLOCK}: of the modes a row can hold, that is the plainest, and
     * the three on-grid ones outnumber the accurate one, so it is the reading that puts an unfamiliar
     * row back where it most likely was. The mode here decides only the server's own arithmetic — how
     * a placement is centred, and whether a row is resolved as an entity — because what the client
     * draws is the mode stored in the settings column.
     */
    private static EffectToolConfig.Mode modeOf(String stored) {
        return EffectToolConfig.Mode.byName(stored).result().orElse(EffectToolConfig.Mode.BLOCK);
    }

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
            // Safe here, beside the table, because expires_at is part of the table's first shape and
            // every world's copy of it.
            stmt.execute("CREATE INDEX IF NOT EXISTS placed_effect_expiry "
                    + "ON placed_effect (expires_at);");
        } catch (SQLException e) {
            throw new RuntimeException("Could not open the placed effect database", e);
        }
        addColumn("face", "TEXT NOT NULL DEFAULT 'UP'");
        addColumn("anchor_uuid", "TEXT NOT NULL DEFAULT ''");
        addColumn("target_uuid", "TEXT NOT NULL DEFAULT ''");
        createReplayIndexes();
    }

    /**
     * Indexes the two questions the replay asks every time an entity or a chunk comes into a player's
     * view: which effects name this entity, and which of them sit at this coordinate. Without them the
     * replay reads every row on the server to answer about one entity or one chunk. See
     * {@link #attachedTo} and {@link #inChunk}.
     *
     * <p><b>After the columns, never beside the table.</b> Two of these indexes are over columns that
     * a world may not have yet — {@code anchor_uuid} and {@code target_uuid} are added by the
     * migration above — and an index over a column that is not there is not a missing optimisation:
     * {@code CREATE INDEX} fails, and this class answers a failure by refusing to open, so a world
     * that ran an earlier build would not start. The columns are therefore known to exist before the
     * indexes that name them are created, which is why this is a step of its own rather than three
     * statements in the block with the table.
     */
    private void createReplayIndexes() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE INDEX IF NOT EXISTS placed_effect_target "
                    + "ON placed_effect (target_uuid, dimension);");
            stmt.execute("CREATE INDEX IF NOT EXISTS placed_effect_anchor "
                    + "ON placed_effect (anchor_uuid, dimension);");
            stmt.execute("CREATE INDEX IF NOT EXISTS placed_effect_place "
                    + "ON placed_effect (dimension, x);");
        } catch (SQLException e) {
            throw new RuntimeException("Could not index the placed effect table", e);
        }
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
     * {@return the effects one entity is named by, oldest first}
     *
     * <p>Asked of the table rather than read whole, because this runs every time an entity comes into
     * a player's view: the entity case of the replay, which is how an effect on a mob — or an accurate
     * one, which hangs off a display that is an entity like any other — is put back on a client that
     * has lost it. Reading the server's whole table to answer a question about one entity is a scan
     * per entity per player, and the two columns below are what answer it.
     *
     * <p>Either column answers, and both are searched: a row names the mob an entity-attached effect
     * is <em>on</em> in {@code target_uuid}, and the display an accurate effect <em>hangs off</em> in
     * {@code anchor_uuid}, and both are entities whose being tracked again is what puts the effect
     * back. Written as a union of two indexed lookups rather than as an {@code OR}, so each half is
     * answered by its own index and no row of the table is read to reject it.
     *
     * @param entityUuid the identity a row names in either column
     */
    public List<PlacedEffect> attachedTo(String dimension, String entityUuid) {
        if (dimension == null || entityUuid == null || entityUuid.isBlank()) return List.of();

        return query("SELECT * FROM placed_effect WHERE dimension = ? AND target_uuid = ? "
                        + "UNION SELECT * FROM placed_effect WHERE dimension = ? AND anchor_uuid = ? "
                        + "ORDER BY effect_id",
                MAPPER, dimension, entityUuid, dimension, entityUuid);
    }

    /**
     * {@return the effects whose row sits in one chunk of one dimension, oldest first}
     *
     * <p>Asked of the table rather than read whole, because this runs every time a player is sent a
     * chunk: the chunk case of the replay, which is how a placed effect is put back on a client that
     * walked away and came back. The table is every effect on the server, so choosing the chunk in
     * Java meant reading all of them for each chunk of each player.
     *
     * <p>The bounds are a superset of the blocks the chunk holds, deliberately: a row is stored either
     * at a block's low corner — around which an on-grid effect is centred, half a block on — or at an
     * exact point, and the half block of slack below the chunk's first block admits the first case
     * without admitting a block from the chunk before it. What the query returns is therefore worth
     * testing rather than already the answer, and the caller's own test still decides; see
     * {@code EffectReplay.sendIn}, which is the only caller and makes the same test it always did.
     *
     * <p>One case is not answerable from the row: an accurate effect follows the display it hangs off,
     * and a display an operator has moved is where it was moved to, which is not written down here. A
     * row like that is found by {@link #attachedTo} instead, when the display comes back into the
     * player's view, because the display is an entity that is tracked like any other.
     */
    public List<PlacedEffect> inChunk(String dimension, int chunkX, int chunkZ) {
        if (dimension == null) return List.of();

        double minX = chunkX * 16.0D - 0.5D;
        double maxX = chunkX * 16.0D + 16.0D;
        double minZ = chunkZ * 16.0D - 0.5D;
        double maxZ = chunkZ * 16.0D + 16.0D;

        return query("SELECT * FROM placed_effect WHERE dimension = ? "
                        + "AND x >= ? AND x < ? AND z >= ? AND z < ? ORDER BY effect_id",
                MAPPER, dimension, minX, maxX, minZ, maxZ);
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
