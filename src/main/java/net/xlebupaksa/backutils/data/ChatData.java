package net.xlebupaksa.backutils.data;

import net.xlebupaksa.backutils.BackUtils;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Owns the {@code chat_profile} table, in {@code chat_profile.db}. */
public class ChatData extends DataManager {

    private static final RowMapper<ChatProfile> MAPPER = rs -> new ChatProfile(
            rs.getLong("chat_profile_id"),
            rs.getString("chat_profile_name"),
            rs.getString("chat_format"),
            rs.getString("sound") == null ? "" : rs.getString("sound"),
            rs.getString("player_name"));

    public ChatData(Path worldPath) {
        super(worldPath, "chat_profile.db");
    }

    @Override
    public void initDb() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_profile (" +
                    "chat_profile_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "chat_profile_name TEXT NOT NULL, " +
                    "chat_format TEXT NOT NULL, " +
                    "sound TEXT NOT NULL DEFAULT '', " +
                    "player_name TEXT NOT NULL" +
                    ");");
        } catch (SQLException e) {
            throw new RuntimeException("Could not open the chat profile database", e);
        }
        addSoundColumn();
        createIndexes();
    }

    /**
     * Adds the sound channel to a database created before profiles had one, so profiles in use
     * survive rather than being re-created to gain a column. The ALTER fails harmlessly once the
     * column is there.
     */
    private void addSoundColumn() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("ALTER TABLE chat_profile ADD COLUMN sound TEXT NOT NULL DEFAULT ''");
        } catch (SQLException e) {
            // Already present.
        }
    }

    /**
     * Indexes are created separately and tolerantly; see {@code NameData#createIndexes}.
     */
    private void createIndexes() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_profile_player " +
                    "ON chat_profile(player_name);");
        } catch (SQLException e) {
            BackUtils.LOGGER.warn("Could not index chat_profile(player_name)", e);
        }

        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_profile_player_name " +
                    "ON chat_profile(player_name, chat_profile_name);");
        } catch (SQLException e) {
            BackUtils.LOGGER.warn("Could not enforce unique chat profiles: this database already "
                    + "contains duplicates. Remove them and restart to enable the guarantee.", e);
        }
    }

    public Optional<ChatProfile> findById(long id) {
        return query(
                "SELECT chat_profile_id, chat_profile_name, chat_format, sound, player_name " +
                        "FROM chat_profile WHERE chat_profile_id = ?",
                MAPPER,
                id
        ).stream().findFirst();
    }

    public List<ChatProfile> findByPlayer(String player) {
        return query(
                "SELECT chat_profile_id, chat_profile_name, chat_format, sound, player_name " +
                        "FROM chat_profile WHERE player_name = ? ORDER BY chat_profile_name",
                MAPPER,
                player);
    }

    /**
     * {@return how many chat profiles each player has, keyed by account name}
     *
     * <p>One query rather than one per player.
     */
    public Map<String, Integer> countsByPlayer() {
        return counts(
                "SELECT player_name, COUNT(*) AS total FROM chat_profile GROUP BY player_name");
    }

    public Optional<ChatProfile> findByPlayerAndName(String player, String name) {
        return query(
                "SELECT chat_profile_id, chat_profile_name, chat_format, sound, player_name " +
                        "FROM chat_profile WHERE player_name = ? AND chat_profile_name = ?",
                MAPPER,
                player, name
        ).stream().findFirst();
    }

    public long create(String name, String format, String sound, String player) {
        return insertAndGetId(
                "INSERT INTO chat_profile (chat_profile_name, chat_format, sound, player_name) " +
                        "VALUES (?, ?, ?, ?)",
                name, format, sound, player);
    }

    /** Rewrites a profile's format and sound. */
    public void update(String name, String newFormat, String sound, String player) {
        update(
                "UPDATE chat_profile SET chat_format = ?, sound = ? " +
                        "WHERE player_name = ? AND chat_profile_name = ?",
                newFormat, sound, player, name);
    }

    /**
     * Renames a profile: the name is the key the caller holds, so it cannot also be what the same
     * statement changes. The active-profile table stores the row id, which does not move, so it
     * needs no fixing up. {@code super.update} is qualified because this class has its own
     * four-argument {@code update}, which would otherwise take the statement as a profile name.
     */
    @SuppressWarnings("UnusedReturnValue")
    public int rename(String player, String oldName, String newName) {
        return super.update(
                "UPDATE chat_profile SET chat_profile_name = ? " +
                        "WHERE player_name = ? AND chat_profile_name = ?",
                newName, player, oldName);
    }

    /** Removes a profile. Unlike {@link NameData#delete}, this call reaches no overload but the
     * varargs helper, so it needs no {@code super} qualifier. */
    public void delete(String name, String player) {
        update(
                "DELETE FROM chat_profile " +
                        "WHERE player_name = ? AND chat_profile_name = ?",
                player, name);
    }
}
