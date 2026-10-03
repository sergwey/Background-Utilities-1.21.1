package net.xlebupaksa.backutils.data;

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
    }

    public Optional<ActiveProfile> find(String player) {
        return query(
                "SELECT player_name, name_profile_id, chat_profile_id " +
                        "FROM active_profile WHERE player_name = ?",
                rs -> new ActiveProfile(
                        rs.getString("player_name"),
                        rs.getLong("name_profile_id"),
                        rs.getLong("chat_profile_id")),
                player
        ).stream().findFirst();
    }

    public void setActiveChat(String player, long chatProfileId) {
        update(
                "INSERT INTO active_profile (player_name, name_profile_id, chat_profile_id) " +
                        "VALUES (?, 0, ?) " +
                        "ON CONFLICT(player_name) DO UPDATE SET chat_profile_id = excluded.chat_profile_id",
                player, chatProfileId);
    }

    public void setActiveName(String player, long nameProfileId) {
        update(
                "INSERT INTO active_profile (player_name, name_profile_id, chat_profile_id) " +
                        "VALUES (?, ?, 0) " +
                        "ON CONFLICT(player_name) DO UPDATE SET name_profile_id = excluded.name_profile_id",
                player, nameProfileId);
    }

    public void clearActiveChat(String player) {
        update("UPDATE active_profile SET chat_profile_id = 0 WHERE player_name = ?", player);
    }

    public void clearActiveName(String player) {
        update("UPDATE active_profile SET name_profile_id = 0 WHERE player_name = ?", player);
    }
}