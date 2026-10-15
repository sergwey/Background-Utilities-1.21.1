package net.xlebupaksa.backutils.data;

import net.xlebupaksa.backutils.BackUtils;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Owns the {@code name_profile} table, in {@code name_profile.db}. */
public class NameData extends DataManager {

    private static final RowMapper<NameProfile> MAPPER = rs -> new NameProfile(
            rs.getLong("name_profile_id"),
            rs.getString("name_profile_name"),
            rs.getString("displayed_name"),
            rs.getString("player_name"));

    public NameData(Path worldPath) {
        super(worldPath, "name_profile.db");
    }

    @Override
    public void initDb() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE TABLE IF NOT EXISTS name_profile (" +
                    "name_profile_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name_profile_name TEXT NOT NULL, " +
                    "displayed_name TEXT NOT NULL, " +
                    "player_name TEXT NOT NULL" +
                    ");");
        } catch (SQLException e) {
            throw new RuntimeException("Could not open the name profile database", e);
        }
        createIndexes();
    }

    /**
     * Indexes are created separately and tolerantly. {@code findByPlayer} otherwise scans the whole
     * table, and the unique index is what guarantees that a player cannot end up with two profiles
     * of the same name — the commands only check-then-insert, which two administrators can race. A
     * unique index cannot be built over a database that already holds duplicates, so that failure
     * is logged rather than fatal.
     *
     * <p>The index on the owner is built with {@code COLLATE NOCASE}, because that is how every lookup of
     * a row by its owner is compared. One account name can be stored in more than one spelling — the
     * server's own, which the commands write, and one a typed name left behind — and a row carrying
     * either spelling is still that player's, an account name being unique ignoring case. A lookup that
     * insisted on the exact spelling would hide a profile from the player it was made for, while a
     * comparison and an index that disagree about case would leave the index unable to serve it.
     *
     * <p>The uniqueness guarantee is left comparing as written, which is why it is not built the same
     * way: it is there to stop two administrators racing, it cannot be widened without failing to build
     * over a database that already holds a pair differing only in case, and a failure there would take
     * the guarantee away rather than extend it. The old index under a plain name is dropped once, and
     * the new one names the collation so that neither is rebuilt on the next start.
     */
    private void createIndexes() {
        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("DROP INDEX IF EXISTS idx_name_profile_player;");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_name_profile_player_nocase " +
                    "ON name_profile(player_name COLLATE NOCASE);");
        } catch (SQLException e) {
            BackUtils.LOGGER.warn("Could not index name_profile(player_name)", e);
        }

        try (
                Connection connection = openConnection();
                Statement stmt = connection.createStatement()
        ) {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_name_profile_player_name " +
                    "ON name_profile(player_name, name_profile_name);");
        } catch (SQLException e) {
            BackUtils.LOGGER.warn("Could not enforce unique name profiles: this database already "
                    + "contains duplicates. Remove them and restart to enable the guarantee.", e);
        }
    }

    public Optional<NameProfile> findById(long id) {
        return query(
                "SELECT name_profile_id, name_profile_name, displayed_name, player_name " +
                        "FROM name_profile WHERE name_profile_id = ?",
                MAPPER,
                id
        ).stream().findFirst();
    }

    /**
     * {@return this player's profiles}
     *
     * <p>Found under whichever spelling of the account name the rows carry: see {@link #createIndexes}
     * for why the comparison is case-insensitive.
     */
    public List<NameProfile> findByPlayer(String player) {
        return query(
                "SELECT name_profile_id, name_profile_name, displayed_name, player_name " +
                        "FROM name_profile WHERE player_name = ? COLLATE NOCASE " +
                        "ORDER BY name_profile_name",
                MAPPER,
                player);
    }

    public Optional<NameProfile> findByPlayerAndName(String player, String name) {
        return query(
                "SELECT name_profile_id, name_profile_name, displayed_name, player_name " +
                        "FROM name_profile WHERE player_name = ? COLLATE NOCASE " +
                        "AND name_profile_name = ?",
                MAPPER,
                player, name
        ).stream().findFirst();
    }

    /**
     * {@return how many name profiles each player has, keyed by account name}
     *
     * <p>See {@link ChatData#countsByPlayer()} — the same question, for the same reason.
     */
    public Map<String, Integer> countsByPlayer() {
        return counts(
                "SELECT player_name, COUNT(*) AS total FROM name_profile GROUP BY player_name");
    }

    public long create(String name, String displayedName, String player) {
        return insertAndGetId(
                "INSERT INTO name_profile (name_profile_name, displayed_name, player_name) " +
                        "VALUES (?, ?, ?)",
                name, displayedName, player);
    }

    /** Rewrites a profile's displayed name. See {@link ChatData#update} for why it returns nothing. */
    public void update(String name, String newDisplayed, String player) {
        update(
                "UPDATE name_profile SET displayed_name = ? " +
                        "WHERE player_name = ? COLLATE NOCASE AND name_profile_name = ?",
                newDisplayed, player, name);
    }

    /**
     * Renames a profile: the name is the key the caller holds, so it cannot also be what the same
     * statement changes. {@code super.update} is qualified because this class has its own
     * three-argument {@code update}, which would otherwise take the statement as a profile name.
     * See {@link ChatData#rename} for the same reasoning.
     */
    @SuppressWarnings("UnusedReturnValue")
    public int rename(String player, String oldName, String newName) {
        return super.update(
                "UPDATE name_profile SET name_profile_name = ? " +
                        "WHERE player_name = ? COLLATE NOCASE AND name_profile_name = ?",
                newName, player, oldName);
    }

    /**
     * Removes a profile. {@code super.update} is qualified because the statement and its two
     * parameters are three strings, which is the shape of {@link #update(String, String, String)},
     * so an unqualified call passes the DELETE in as a <i>displayed name</i> and removes nothing.
     */
    public void delete(String name, String player) {
        super.update(
                "DELETE FROM name_profile " +
                        "WHERE player_name = ? COLLATE NOCASE AND name_profile_name = ?",
                player, name);
    }
}
