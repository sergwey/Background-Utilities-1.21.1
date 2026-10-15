package net.xlebupaksa.backutils.data;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Owns the {@code silence} table, in {@code silence.db}, a separate file so that a moderation
 * setting shares no schema, migration path or corruption risk with the data the server must not
 * lose.
 *
 * <p>Rows are keyed by account name, matching how the rest of the mod keys its tables: a player who
 * renames their account is no longer matched by an existing row.
 *
 * <p>The table is also held in memory, because it is read once for every line a player writes —
 * the question of whether they may act or speak is asked on every message, and a connection and a
 * query apiece is work the server thread does for every line anybody types. The set moves only when
 * {@link #setFlag} writes it, and the cache is rebuilt there and wherever the table is opened, so no
 * message reads a set that a write has left behind. Nothing static holds it: a manager belongs to one
 * session and one world directory, and the whole of it goes when the session does.
 */
public class SilenceData extends DataManager {

    private static final RowMapper<SilenceState> MAPPER = rs -> new SilenceState(
            rs.getString("player_name"),
            rs.getInt("actions") != 0,
            rs.getInt("local_chat") != 0,
            rs.getInt("global_chat") != 0,
            rs.getInt("msg") != 0);

    /**
     * The table, keyed by lower-cased account name because the per-player query this replaces asked
     * with {@code COLLATE NOCASE}. That collation folds ASCII only, and an account name is ASCII, so
     * folding the whole name here can only differ for a name no account can have.
     */
    private final Map<String, SilenceState> cache = new HashMap<>();

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
                    "global_chat INTEGER NOT NULL DEFAULT 0, " +
                    "msg INTEGER NOT NULL DEFAULT 0" +
                    ");");
        } catch (SQLException e) {
            throw new RuntimeException("Could not open the silence database", e);
        }
        // A world that ran a build before the fourth kind existed has the table without its column.
        // Before the read below, because the cache is built by naming every column, and a column the
        // table has not got yet is not a row that reads as unsilenced: it is a query that fails and
        // leaves every player looking unsilenced in everything.
        addColumn("msg", "INTEGER NOT NULL DEFAULT 0");
        // The one place the table is opened, and so the only place it is read whole: the constructor
        // reaches it, initAll reaches it again, and either is how a session begins looking at a
        // world's silences. Doing it after the connection above is closed keeps one connection open
        // at a time, as every other statement here does.
        reload();
    }

    /**
     * Adds a column to a table that was created before it existed.
     *
     * <p>The table is asked what it holds rather than the statement being attempted and its failure
     * swallowed: a duplicate-column error and a database that cannot be written at all are not the
     * same thing, and only one of them is ordinary. Nothing is dropped and no row is rewritten — a
     * player silenced in the three older kinds keeps all three and gains the fourth unset, which is
     * what the default is for — and running it again on the next start, as every restart after the
     * first does, is a lookup that adds nothing.
     */
    private void addColumn(String name, String definition) {
        List<String> columns = query("PRAGMA table_info(silence)", rs -> rs.getString("name"));
        if (columns.contains(name)) return;
        update("ALTER TABLE silence ADD COLUMN " + name + " " + definition);
    }

    /** {@return what the named player is silenced in, or empty when they have no row} */
    public SilenceState find(String player) {
        if (player == null || player.isBlank()) return null;
        return cache.get(key(player));
    }

    /**
     * Sets one flag, leaving the others as they were. Read-then-write rather than a
     * column-name-interpolated upsert: building SQL out of a caller-supplied column name is how a
     * quiet moderation tool becomes an injection point.
     *
     * <p>The read comes from the cache and the write is followed by a rebuild of it. The name written
     * is the row's own spelling where there is a row — {@link SilenceState#with} keeps the name it was
     * given — so a table that has been edited by hand with another spelling is updated rather than
     * given a second row, which is what the primary key on {@code player_name} would otherwise allow.
     */
    public SilenceState setFlag(String player, SilenceKind kind, boolean value) {
        SilenceState current = find(player);
        if (current == null) current = SilenceState.none(player);

        SilenceState updated = current.with(kind, value);
        try {
            update("INSERT INTO silence (player_name, actions, local_chat, global_chat, msg) " +
                            "VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT(player_name) DO UPDATE SET " +
                            "actions = excluded.actions, " +
                            "local_chat = excluded.local_chat, " +
                            "global_chat = excluded.global_chat, " +
                            "msg = excluded.msg",
                    updated.player(),
                    updated.actions() ? 1 : 0,
                    updated.localChat() ? 1 : 0,
                    updated.globalChat() ? 1 : 0,
                    updated.messages() ? 1 : 0);
        } catch (RuntimeException e) {
            // A write that failed is a write that may still have landed: the statement can have been
            // applied before closing its connection failed. A cache left holding the set from before
            // it would either silence somebody who is not or let a silenced one speak, so it is
            // rebuilt from the table before the error goes on to whoever asked for the change.
            reload();
            throw e;
        }
        reload();
        return updated;
    }

    /**
     * Reads the whole table over the cache, replacing what it held.
     *
     * <p>Whole rather than one row at a time: the rows are moderation settings rather than something a
     * message produces, so they are few, and a rebuild cannot be wrong about a row that the write in
     * hand did not touch.
     */
    private void reload() {
        List<SilenceState> rows = query(
                "SELECT player_name, actions, local_chat, global_chat, msg FROM silence", MAPPER);

        Map<String, SilenceState> loaded = new HashMap<>();
        // Two rows differing only in case are allowed by the case-sensitive primary key although
        // nothing here writes them; one of them is what the old per-player query answered with too.
        for (SilenceState row : rows) loaded.put(key(row.player()), row);

        cache.clear();
        cache.putAll(loaded);
    }

    private static String key(String player) {
        return player.toLowerCase(Locale.ROOT);
    }
}
