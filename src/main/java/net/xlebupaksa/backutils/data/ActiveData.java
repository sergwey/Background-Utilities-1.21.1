package net.xlebupaksa.backutils.data;

import net.xlebupaksa.backutils.BackUtils;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/** Owns the {@code active_profile} table, in {@code active_profile.db}. */
public class ActiveData extends DataManager {

    public ActiveData(Path worldPath) {
        super(worldPath, "active_profile.db");
    }

    /**
     * The table is keyed by the account name, and the primary key compares it as written; the index made
     * here compares it ignoring case, which is how every lookup in this class asks. Two spellings of one
     * account name are one player, and the row is written with whichever spelling reached it — the
     * administrator's editor writes the spelling already stored for an offline player — so without this
     * a player could hold two rows, or hold one the lookup did not find.
     *
     * <p>The index is tolerant like the profile tables' indexes: a database that already holds a pair of
     * rows differing only in case logs the failure instead of refusing to start, and the lookups still
     * answer, by scanning. Such a database can then gain a second active row for that player, which is one
     * more reason the failure is logged rather than swallowed.
     */
    @Override
    public void initDb() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE TABLE IF NOT EXISTS active_profile (" +
                    "player_name TEXT PRIMARY KEY, " +
                    "name_profile_id INTEGER NOT NULL, " +
                    "chat_profile_id INTEGER NOT NULL" +
                    ");");
        } catch (SQLException e) {
            throw new RuntimeException("Epic SQL connection fail at active", e);
        }

        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_active_profile_player_nocase " +
                    "ON active_profile(player_name COLLATE NOCASE);");
        } catch (SQLException e) {
            BackUtils.LOGGER.warn("Could not enforce one active profile per player: this database "
                    + "already holds the same account name in two spellings. Remove one and restart "
                    + "to enable the guarantee.", e);
        }
    }

    public Optional<ActiveProfile> find(String player) {
        return query(
                "SELECT player_name, name_profile_id, chat_profile_id " +
                        "FROM active_profile WHERE player_name = ? COLLATE NOCASE",
                rs -> new ActiveProfile(
                        rs.getString("player_name"),
                        rs.getLong("name_profile_id"),
                        rs.getLong("chat_profile_id")),
                player
        ).stream().findFirst();
    }

    /**
     * Points this player at a chat profile, writing their row if they have none.
     *
     * <p>The upsert names no conflict target, and that is deliberate: the row it may meet is found
     * through either index, the primary key or the case-insensitive one made in {@link #initDb()}, and
     * a target can name only one of them. The row keeps the spelling of the account name it already
     * has — this is not where an account name is rewritten.
     */
    public void setActiveChat(String player, long chatProfileId) {
        update(
                "INSERT INTO active_profile (player_name, name_profile_id, chat_profile_id) " +
                        "VALUES (?, 0, ?) " +
                        "ON CONFLICT DO UPDATE SET chat_profile_id = excluded.chat_profile_id",
                player, chatProfileId);
    }

    /** See {@link #setActiveChat} for why the upsert names no conflict target. */
    public void setActiveName(String player, long nameProfileId) {
        update(
                "INSERT INTO active_profile (player_name, name_profile_id, chat_profile_id) " +
                        "VALUES (?, ?, 0) " +
                        "ON CONFLICT DO UPDATE SET name_profile_id = excluded.name_profile_id",
                player, nameProfileId);
    }

    public void clearActiveChat(String player) {
        update("UPDATE active_profile SET chat_profile_id = 0 WHERE player_name = ? COLLATE NOCASE",
                player);
    }

    public void clearActiveName(String player) {
        update("UPDATE active_profile SET name_profile_id = 0 WHERE player_name = ? COLLATE NOCASE",
                player);
    }
}