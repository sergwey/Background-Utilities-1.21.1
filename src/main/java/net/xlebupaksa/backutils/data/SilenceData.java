package net.xlebupaksa.backutils.data;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Owns the {@code silence} table, in {@code silence.db}, a separate file so that a moderation
 * setting shares no schema, migration path or corruption risk with the data the server must not
 * lose.
 *
 * <p>Rows are keyed by account name, matching how the rest of the mod keys its tables: a player who
 * renames their account is no longer matched by an existing row.
 */
public class SilenceData extends DataManager {

    private static final RowMapper<SilenceState> MAPPER = rs -> new SilenceState(
            rs.getString("player_name"),
            rs.getInt("actions") != 0,
            rs.getInt("local_chat") != 0,
            rs.getInt("global_chat") != 0);

    public SilenceData(Path worldPath) {
        super(worldPath, "silence.db");
    }

    @Override
    public void initDb() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE TABLE IF NOT EXISTS silence (" +
                    "player_name TEXT PRIMARY KEY, " +
                    "actions INTEGER NOT NULL DEFAULT 0, " +
                    "local_chat INTEGER NOT NULL DEFAULT 0, " +
                    "global_chat INTEGER NOT NULL DEFAULT 0" +
                    ");");
        } catch (SQLException e) {
            throw new RuntimeException("Could not open the silence database", e);
        }
    }

    /** {@return what the named player is silenced in, or empty when they have no row} */
    public SilenceState find(String player) {
        if (player == null || player.isBlank()) return null;
        List<SilenceState> rows = query(
                "SELECT player_name, actions, local_chat, global_chat FROM silence " +
                        "WHERE player_name = ? COLLATE NOCASE",
                MAPPER, player);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * Sets one flag, leaving the other two as they were. Read-then-write rather than a
     * column-name-interpolated upsert: building SQL out of a caller-supplied column name is how a
     * quiet moderation tool becomes an injection point.
     */
    public SilenceState setFlag(String player, SilenceKind kind, boolean value) {
        SilenceState current = find(player);
        if (current == null) current = SilenceState.none(player);

        SilenceState updated = current.with(kind, value);
        update("INSERT INTO silence (player_name, actions, local_chat, global_chat) " +
                        "VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(player_name) DO UPDATE SET " +
                        "actions = excluded.actions, " +
                        "local_chat = excluded.local_chat, " +
                        "global_chat = excluded.global_chat",
                updated.player(),
                updated.actions() ? 1 : 0,
                updated.localChat() ? 1 : 0,
                updated.globalChat() ? 1 : 0);
        return updated;
    }
}
