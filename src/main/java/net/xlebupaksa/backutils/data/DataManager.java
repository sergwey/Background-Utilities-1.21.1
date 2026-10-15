package net.xlebupaksa.backutils.data;

import net.xlebupaksa.backutils.BackUtils;

import java.nio.file.Path;
import java.sql.*;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for the mod's SQLite databases: one file each, with the table that file holds.
 *
 * <p>Every statement runs on the thread that asks for it — the server thread, in every caller this
 * has — and opens, uses and closes a connection of its own. Nothing is queued and nothing is pooled,
 * so a manager holds no resource between calls and there is nothing to shut down when the server
 * stops: the only thing that ends with the session is the reference to it.
 */
public abstract class DataManager {

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "SQLite JDBC driver not found on classpath. Add org.xerial:sqlite-jdbc as a jarJar dependency.",
                    e);
        }
    }

    private final String jdbcUrl;

    protected DataManager(Path worldPath, String fileName) {
        Path dbPath = worldPath.resolve("serverconfig").resolve("backutils");
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.resolve(fileName);
    }

    public abstract void initDb();

    protected Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement pragma = connection.createStatement()) {
            // Every statement opens its own connection, so the pragmas are applied every
            // time. journal_mode is stored in the database file itself; busy_timeout is per
            // connection. Without them, a statement that meets another connection using the
            // same file fails with SQLITE_BUSY instead of waiting its turn.
            pragma.execute("PRAGMA journal_mode = WAL");
            pragma.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException e) {
            // Not fatal: the connection still works, just without the concurrency guards.
            BackUtils.LOGGER.warn("Could not apply SQLite pragmas to {}", jdbcUrl, e);
        }
        return connection;
    }

    protected <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Query failed: " + sql, e);
        }
    }

    protected int update(String sql, Object... params) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Update failed: " + sql, e);
        }
    }

    protected long insertAndGetId(String sql, Object... params) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Insert failed: " + sql, e);
        }
    }

    /**
     * {@return a count per player, from a query selecting {@code player_name} and {@code total}}
     *
     * <p>Shared by the two profile tables: one row per player rather than one query per player. A row per
     * player <i>name</i>, strictly — one account name stored in two spellings is two rows here — so
     * whoever reads these counts has to add the spellings together rather than take one of them.
     */
    protected Map<String, Integer> counts(String sql) {
        List<Map.Entry<String, Integer>> rows = query(sql, rs ->
                new AbstractMap.SimpleEntry<>(rs.getString("player_name"), rs.getInt("total")));
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> row : rows) totals.put(row.getKey(), row.getValue());
        return totals;
    }

    private void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}