package net.xlebupaksa.backutils.data;

import net.xlebupaksa.backutils.BackUtils;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

/**
 * Owns the {@code action_log} table, in {@code action_log.db}.
 *
 * <p>{@code access_list} holds comma-separated player UUIDs; see {@link AccessList}.
 */
public class LogData extends DataManager {

    /** One list of columns, so every query stays in step with the mapper. */
    private static final String COLUMNS = "log_id, access_list, contents, actor_id, actor_name, "
            + "created_at, dimension, x, y, z, hidden_all, hidden_from, admin_note";

    /**
     * How many of the newest entries either log screen is sent.
     *
     * <p>Both screens used to be sent the whole table, which on a long-running server is unbounded
     * memory on the server, an unbounded packet, and an unbounded list for the client to lay out — all
     * of it to be scrolled by hand. Four hundred is a few hundred: far more scrollback than an
     * operator or a player reads in a sitting, while keeping even the heaviest administrator row —
     * which carries the witness and hidden-from name lists as well as the line — to a packet of a few
     * hundred kilobytes at the very outside.
     *
     * <p>The trade is that <b>older entries are no longer shown on either screen</b>. Nothing is
     * deleted: the rows are still in the table, the live log still pushes new lines as they happen,
     * and the catch-up sweep still walks everything a player has not been sent, so this bounds what a
     * screen loads rather than what the log keeps. Pruning the table is a separate decision and is
     * deliberately not made here.
     */
    public static final int SCREEN_ROWS = 400;

    /**
     * Who may see a row, spelled once because two reads of it must not drift: the incremental sweep
     * and the bounded screen load ask the same question, and a rule written twice is a rule that can
     * disagree with itself.
     *
     * <p>{@code access_list} is a comma-separated UUID list, so a match is valid only on a whole
     * element: both the stored column and the needle are padded with commas, because a plain
     * {@code LIKE '%uuid%'} would also match a UUID that merely contains this one as a substring. The
     * two placeholders are the same needle twice: the list and the hidden-from list.
     */
    private static final String VISIBILITY =
            "AND (',' || replace(access_list, ' ', '') || ',') LIKE ? "
                    + "AND COALESCE(hidden_all, 0) = 0 "
                    + "AND (',' || replace(COALESCE(hidden_from, ''), ' ', '') || ',') NOT LIKE ? ";

    private static final RowMapper<ActionLogEntry> MAPPER = rs -> new ActionLogEntry(
            rs.getLong("log_id"),
            rs.getString("actor_id") == null ? "" : rs.getString("actor_id"),
            rs.getString("actor_name") == null ? "" : rs.getString("actor_name"),
            rs.getString("contents"),
            rs.getString("created_at"),
            AccessList.decode(rs.getString("access_list")),
            rs.getString("dimension"),
            rs.getDouble("x"),
            rs.getDouble("y"),
            rs.getDouble("z"),
            rs.getInt("hidden_all") != 0,
            AccessList.decode(rs.getString("hidden_from")),
            rs.getString("admin_note") == null ? "" : rs.getString("admin_note"));

    public LogData(Path worldPath) {
        super(worldPath, "action_log.db");
    }

    @Override
    public void initDb() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE TABLE IF NOT EXISTS action_log (" +
                    "log_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "access_list TEXT NOT NULL, " +
                    "contents TEXT NOT NULL, " +
                    "created_at DATETIME DEFAULT (datetime('now', 'localtime'))" +
                    ");");
        } catch (SQLException e) {
            throw new RuntimeException("Could not open the action log database", e);
        }
        addActorColumns();
        addPositionColumns();
        addHidingColumns();
        addAdminNoteColumn();
        createIndexes();
    }

    /**
     * Adds the administrator's note: what an entry says to an administrator and not to a player,
     * such as the number behind a hidden roll. It lives with the row rather than in the contents,
     * because the contents are what every witness is sent.
     */
    private void addAdminNoteColumn() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("ALTER TABLE action_log ADD COLUMN admin_note TEXT NOT NULL DEFAULT ''");
            BackUtils.LOGGER.info("Added action_log.admin_note");
        } catch (SQLException e) {
            // Already there, which is the normal case after the first start.
        }
    }

    /**
     * Adds the hiding overlay beside {@code access_list} rather than editing the list, because
     * hiding has to be undoable: {@code access_list} always means "who was close enough", and
     * these two columns say what has since been withheld from them.
     */
    private void addHidingColumns() {
        for (String column : new String[]{"hidden_all INTEGER NOT NULL DEFAULT 0",
                "hidden_from TEXT NOT NULL DEFAULT ''"}) {
            try (
                    Connection connection = openConnection();
                    Statement stmt = connection.createStatement()
            ) {
                stmt.execute("ALTER TABLE action_log ADD COLUMN " + column);
            } catch (SQLException e) {
                // Already there, which is the normal case after the first start.
            }
        }
    }

    /** Adds where an action happened, so an administrator can be teleported to it. */
    private void addPositionColumns() {
        for (String column : new String[]{"dimension TEXT", "x REAL", "y REAL", "z REAL"}) {
            try (
                    Connection connection = openConnection();
                    Statement stmt = connection.createStatement()
            ) {
                stmt.execute("ALTER TABLE action_log ADD COLUMN " + column);
            } catch (SQLException e) {
                // Already there, which is the normal case after the first start.
            }
        }
    }

    /**
     * Adds the actor columns to a database created before they existed. {@code ALTER TABLE ...
     * ADD COLUMN} is non-destructive, so every existing row survives, and the failure is tolerated
     * because it also fires when the columns are already present.
     */
    private void addActorColumns() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("ALTER TABLE action_log ADD COLUMN actor_id TEXT");
            BackUtils.LOGGER.info("Added action_log.actor_id");
        } catch (SQLException e) {
            // Already there.
        }
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("ALTER TABLE action_log ADD COLUMN actor_name TEXT");
            BackUtils.LOGGER.info("Added action_log.actor_name");
        } catch (SQLException e) {
            // Already there.
        }
    }

    private void createIndexes() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_action_log_actor " +
                    "ON action_log(actor_id);");
        } catch (SQLException e) {
            BackUtils.LOGGER.warn("Could not index action_log(actor_id)", e);
        }
    }

    /** {@return the id of a new entry, with the actor's UUID and account name stored beside it} */
    public long record(UUID actorId, String actorName, String template, List<UUID> accessList) {
        return record(actorId, actorName, template, accessList, null, 0, 0, 0);
    }

    /**
     * {@return the id of a new entry, with where it happened}
     *
     * @param dimension the dimension key, or null when the caller has no position
     */
    public long record(UUID actorId, String actorName, String template, List<UUID> accessList,
                       String dimension, double x, double y, double z) {
        return record(actorId, actorName, template, accessList, dimension, x, y, z, "");
    }

    /**
     * {@return the id of a new entry, with a note only an administrator will be shown}
     *
     * @param actorId   null for a line that names nobody, which is stored as an empty id
     * @param adminNote what to keep from the players, or empty
     */
    public long record(UUID actorId, String actorName, String template, List<UUID> accessList,
                       String dimension, double x, double y, double z, String adminNote) {
        return insertAndGetId(
                "INSERT INTO action_log (access_list, contents, actor_id, actor_name, " +
                        "dimension, x, y, z, admin_note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                AccessList.encode(accessList), template, actorId == null ? "" : actorId.toString(),
                actorName == null ? "" : actorName, dimension, x, y, z,
                adminNote == null ? "" : adminNote);
    }

    /**
     * {@return entries the given player is allowed to see after {@code afterId}, oldest first}
     *
     * <p>The catch-up read, which is bounded by the watermark rather than by a count: a player is only
     * ever asked for what has been added since they were last sent anything.
     */
    public List<ActionLogEntry> visibleTo(UUID viewer, long afterId) {
        String needle = "," + viewer + ",";
        // hidden_from is padded the same way, so a substring of an access_list entry cannot match.
        return query(
                "SELECT " + COLUMNS + " FROM action_log " +
                        "WHERE log_id > ? " +
                        VISIBILITY +
                        "ORDER BY log_id ASC",
                MAPPER,
                afterId, "%" + needle + "%", "%" + needle + "%");
    }

    /**
     * {@return up to {@code limit} of the newest entries the given player is allowed to see, newest
     * first}
     *
     * <p>What a screen is loaded from, and the reason it is bounded is {@link #SCREEN_ROWS}: the query
     * asks for the newest rows and stops, rather than reading the table and throwing the tail away,
     * which is what bounds the memory and the packet on a server that has been running for a year.
     */
    public List<ActionLogEntry> recentVisibleTo(UUID viewer, int limit) {
        String needle = "," + viewer + ",";
        return query(
                "SELECT " + COLUMNS + " FROM action_log " +
                        // The log's ids are AUTOINCREMENT and start at one, so this selects every row
                        // and leaves the shared visibility rule as the only narrowing.
                        "WHERE log_id > 0 " +
                        VISIBILITY +
                        "ORDER BY log_id DESC LIMIT ?",
                MAPPER,
                "%" + needle + "%", "%" + needle + "%", limit);
    }

    /** {@return up to {@code limit} most recent entries over the whole table} */
    public List<ActionLogEntry> recent(int limit) {
        return query("SELECT " + COLUMNS + " FROM action_log ORDER BY log_id DESC LIMIT ?",
                MAPPER, limit);
    }

    /** {@return one entry, or null when no row has that id} */
    public ActionLogEntry find(long logId) {
        List<ActionLogEntry> rows = query(
                "SELECT " + COLUMNS + " FROM action_log WHERE log_id = ?", MAPPER, logId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** Removes a row for good; unlike hiding this is not reversible. */
    public void delete(long logId) {
        update("DELETE FROM action_log WHERE log_id = ?", logId);
    }

    public void hideForEveryone(long logId) {
        update("UPDATE action_log SET hidden_all = 1 WHERE log_id = ?", logId);
    }

    public void unhideForEveryone(long logId) {
        update("UPDATE action_log SET hidden_all = 0 WHERE log_id = ?", logId);
    }

    /** Withholds an entry from one player, who keeps their place in the witness list. */
    public void hideFrom(long logId, UUID player) {
        update("UPDATE action_log SET hidden_from = ? WHERE log_id = ?",
                AccessList.append(hiddenFrom(logId), player), logId);
    }

    public void unhideFrom(long logId, UUID player) {
        update("UPDATE action_log SET hidden_from = ? WHERE log_id = ?",
                AccessList.remove(hiddenFrom(logId), player), logId);
    }

    private String hiddenFrom(long logId) {
        List<String> current = query(
                "SELECT hidden_from FROM action_log WHERE log_id = ?",
                rs -> rs.getString("hidden_from"),
                logId);
        return current.isEmpty() ? "" : current.getFirst();
    }

    /** {@return the highest {@code log_id} currently stored, or 0 when the log is empty} */
    public long latestId() {
        List<Long> ids = query(
                "SELECT COALESCE(MAX(log_id), 0) AS latest FROM action_log",
                rs -> rs.getLong("latest"));
        return ids.isEmpty() ? 0L : ids.getFirst();
    }
}
