package net.xlebupaksa.backutils.data;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the {@code music_zone} table, in {@code music_zone.db}, separate from the other databases so
 * that nothing here can be risked against their schemas.
 *
 * <p>{@code expires_at} is a wall-clock timestamp rather than a tick count, because a zone's
 * lifetime is given in seconds and must survive a server restart.
 */
public class MusicZoneData extends DataManager {

    private static final RowMapper<MusicZone> MAPPER = rs -> new MusicZone(
            rs.getLong("zone_id"),
            rs.getString("dimension"),
            rs.getInt("is_radius") != 0,
            rs.getDouble("x1"), rs.getDouble("y1"), rs.getDouble("z1"),
            rs.getDouble("x2"), rs.getDouble("y2"), rs.getDouble("z2"),
            rs.getDouble("radius"),
            rs.getString("sound"),
            rs.getString("source") == null ? "music" : rs.getString("source"),
            rs.getFloat("volume"),
            rs.getFloat("pitch"),
            rs.getLong("expires_at"));

    public MusicZoneData(Path worldPath) {
        super(worldPath, "music_zone.db");
    }

    @Override
    public void initDb() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE TABLE IF NOT EXISTS music_zone (" +
                    "zone_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "dimension TEXT NOT NULL, " +
                    "is_radius INTEGER NOT NULL DEFAULT 0, " +
                    "x1 REAL NOT NULL, y1 REAL NOT NULL, z1 REAL NOT NULL, " +
                    "x2 REAL NOT NULL DEFAULT 0, y2 REAL NOT NULL DEFAULT 0, z2 REAL NOT NULL DEFAULT 0, " +
                    "radius REAL NOT NULL DEFAULT 0, " +
                    "sound TEXT NOT NULL, " +
                    "source TEXT NOT NULL DEFAULT 'music', " +
                    "volume REAL NOT NULL DEFAULT 1, " +
                    "pitch REAL NOT NULL DEFAULT 1, " +
                    "expires_at INTEGER NOT NULL DEFAULT 0, " +
                    "created_at DATETIME DEFAULT (datetime('now', 'localtime'))" +
                    ");");
        } catch (SQLException e) {
            throw new RuntimeException("Could not open the music zone database", e);
        }
        addSourceColumn();
    }

    /**
     * Adds the sound channel to a database created before it existed. Zones may already be in
     * place, and re-creating the table to gain a column would stop music that someone had set up.
     * The ALTER fails harmlessly once the column is there.
     */
    private void addSourceColumn() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("ALTER TABLE music_zone ADD COLUMN source TEXT NOT NULL DEFAULT 'music'");
        } catch (SQLException e) {
            // Already present, which is the normal case after the first run.
        }
    }

    /** {@return every zone, oldest first} */
    public List<MusicZone> all() {
        return query("SELECT * FROM music_zone ORDER BY zone_id", MAPPER);
    }

    /**
     * Stores a new zone and returns it with its assigned id.
     *
     * @param expiresAtMillis when the zone ends, or {@link MusicZone#PERMANENT}
     */
    public MusicZone add(String dimension, boolean radius,
                         double x1, double y1, double z1,
                         double x2, double y2, double z2,
                         double radiusValue, String sound, String source, float volume, float pitch,
                         long expiresAtMillis) {
        long id = insertAndGetId(
                "INSERT INTO music_zone (dimension, is_radius, x1, y1, z1, x2, y2, z2, radius, " +
                        "sound, source, volume, pitch, expires_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                dimension, radius ? 1 : 0, x1, y1, z1, x2, y2, z2, radiusValue,
                sound, source, volume, pitch, expiresAtMillis);

        return new MusicZone(id, dimension, radius, x1, y1, z1, x2, y2, z2, radiusValue,
                sound, source, volume, pitch, expiresAtMillis);
    }

    public void delete(long zoneId) {
        update("DELETE FROM music_zone WHERE zone_id = ?", zoneId);
    }

    /** {@return how many zones were removed} */
    public int deleteAll() {
        return update("DELETE FROM music_zone");
    }

    /** {@return the zones that have run out of time, which the caller should clean up} */
    public List<MusicZone> expired(long nowMillis) {
        List<MusicZone> result = new ArrayList<>();
        for (MusicZone zone : all()) {
            if (zone.expired(nowMillis)) result.add(zone);
        }
        return result;
    }

    /** {@return true when the table can be read, used to report a broken database} */
    public boolean isAvailable() {
        // execute rather than executeQuery: what is being proved is that the statement runs against
        // the table, and a ResultSet nobody reads would be a resource held open for nothing.
        try (Connection connection = openConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute("SELECT 1 FROM music_zone LIMIT 1");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
