package net.xlebupaksa.backutils.data;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Administrators' shared notes about one player.
 *
 * <p>One document per player, keyed by UUID so a name change does not split it. The document has a
 * version that every save must name; a save that names an older version is refused rather than
 * overwriting somebody else's change. Page ordering is the integer index, and every save rewrites
 * the whole page set inside one transaction.
 */
public final class NoteData extends DataManager {

    public static final int MAX_PAGES = 32;
    public static final int MAX_PAGE_BYTES = 32 * 1024;
    public static final int MAX_TOTAL_BYTES = 256 * 1024;

    /** One page as the UI needs it. */
    public record Page(String text, String updatedAt, String updatedBy) {
        public Page {
            text = text == null ? "" : text;
            updatedAt = updatedAt == null ? "" : updatedAt;
            updatedBy = updatedBy == null ? "" : updatedBy;
        }
    }

    /** A player's whole note document. Missing documents read as version 0 with one blank page. */
    public record Document(String playerName, int version, List<Page> pages,
                           String updatedAt, String updatedBy) {
        public Document {
            playerName = playerName == null ? "" : playerName;
            pages = List.copyOf(pages);
            updatedAt = updatedAt == null ? "" : updatedAt;
            updatedBy = updatedBy == null ? "" : updatedBy;
        }

        public static Document blank(String playerName) {
            return new Document(playerName, 0, List.of(new Page("", "", "")), "", "");
        }

        public List<String> texts() {
            List<String> out = new ArrayList<>(pages.size());
            for (Page page : pages) out.add(page.text());
            return out;
        }
    }

    public enum SaveResult {
        SAVED,
        VERSION_MISMATCH,
        INVALID
    }

    public record SaveOutcome(SaveResult result, Document document) {
        public boolean saved() {
            return result == SaveResult.SAVED;
        }
    }

    public NoteData(java.nio.file.Path worldPath) {
        super(worldPath, "notes.db");
    }

    @Override
    public void initDb() {
        update("""
                CREATE TABLE IF NOT EXISTS note_document (
                    player_uuid TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL DEFAULT '',
                    version INTEGER NOT NULL DEFAULT 0,
                    updated_at TEXT NOT NULL DEFAULT '',
                    updated_by_uuid TEXT NOT NULL DEFAULT '',
                    updated_by_name TEXT NOT NULL DEFAULT ''
                )
                """);
        update("""
                CREATE TABLE IF NOT EXISTS note_page (
                    player_uuid TEXT NOT NULL,
                    page_index INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    updated_at TEXT NOT NULL DEFAULT '',
                    updated_by_name TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY (player_uuid, page_index)
                )
                """);
    }

    /** {@return a document, with one blank page when nothing has been written yet} */
    public Document read(UUID player, String fallbackName) {
        String uuid = player.toString();
        List<Page> stored = query("""
                        SELECT text, updated_at, updated_by_name
                        FROM note_page WHERE player_uuid = ? ORDER BY page_index
                        """,
                rs -> new Page(rs.getString("text"), rs.getString("updated_at"),
                        rs.getString("updated_by_name")),
                uuid);
        List<Page> pages = stored.isEmpty() ? List.of(new Page("", "", "")) : stored;

        List<Document> docs = query("""
                        SELECT player_name, version, updated_at, updated_by_name
                        FROM note_document WHERE player_uuid = ?
                        """,
                rs -> new Document(rs.getString("player_name"), rs.getInt("version"), pages,
                        rs.getString("updated_at"), rs.getString("updated_by_name")),
                uuid);
        return docs.isEmpty() ? new Document(fallbackName, 0, pages, "", "") : docs.get(0);
    }

    /**
     * Writes all pages, first checking the version the caller started from.
     *
     * <p>The check and the write are one {@code BEGIN IMMEDIATE} transaction, so two saves that
     * arrive together cannot both decide they are current.
     */
    public SaveOutcome save(UUID player, String playerName, int baseVersion, List<String> pages,
            String editorUuid, String editorName) {
        if (pages == null || pages.isEmpty() || pages.size() > MAX_PAGES) {
            return new SaveOutcome(SaveResult.INVALID, read(player, playerName));
        }
        int total = 0;
        for (String page : pages) {
            if (page == null || page.indexOf('\u0000') >= 0) {
                return new SaveOutcome(SaveResult.INVALID, read(player, playerName));
            }
            int bytes = page.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_PAGE_BYTES) {
                return new SaveOutcome(SaveResult.INVALID, read(player, playerName));
            }
            total += bytes;
        }
        if (total > MAX_TOTAL_BYTES) {
            return new SaveOutcome(SaveResult.INVALID, read(player, playerName));
        }

        String uuid = player.toString();
        String now = Instant.now().toString();
        String name = playerName == null ? "" : playerName;
        try (Connection connection = openConnection()) {
            try (Statement begin = connection.createStatement()) {
                begin.execute("BEGIN IMMEDIATE");
            }
            try {
                int current = 0;
                String storedName = name;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT version, player_name FROM note_document WHERE player_uuid = ?")) {
                    select.setString(1, uuid);
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            current = rs.getInt("version");
                            storedName = rs.getString("player_name");
                        }
                    }
                }
                if (current != baseVersion) {
                    rollback(connection);
                    return new SaveOutcome(SaveResult.VERSION_MISMATCH,
                            read(player, storedName));
                }

                int next = current + 1;
                try (PreparedStatement upsert = connection.prepareStatement("""
                                INSERT INTO note_document
                                    (player_uuid, player_name, version, updated_at,
                                     updated_by_uuid, updated_by_name)
                                VALUES (?, ?, ?, ?, ?, ?)
                                ON CONFLICT(player_uuid) DO UPDATE SET
                                    player_name = excluded.player_name,
                                    version = excluded.version,
                                    updated_at = excluded.updated_at,
                                    updated_by_uuid = excluded.updated_by_uuid,
                                    updated_by_name = excluded.updated_by_name
                                """)) {
                    upsert.setString(1, uuid);
                    upsert.setString(2, name);
                    upsert.setInt(3, next);
                    upsert.setString(4, now);
                    upsert.setString(5, editorUuid == null ? "" : editorUuid);
                    upsert.setString(6, editorName == null ? "" : editorName);
                    upsert.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM note_page WHERE player_uuid = ?")) {
                    delete.setString(1, uuid);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                                INSERT INTO note_page
                                    (player_uuid, page_index, text, updated_at, updated_by_name)
                                VALUES (?, ?, ?, ?, ?)
                                """)) {
                    for (int i = 0; i < pages.size(); i++) {
                        insert.setString(1, uuid);
                        insert.setInt(2, i);
                        insert.setString(3, pages.get(i));
                        insert.setString(4, now);
                        insert.setString(5, editorName == null ? "" : editorName);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                try (Statement commit = connection.createStatement()) {
                    commit.execute("COMMIT");
                }
                List<Page> written = new ArrayList<>(pages.size());
                for (String page : pages) written.add(new Page(page, now, editorName));
                return new SaveOutcome(SaveResult.SAVED,
                        new Document(name, next, written, now, editorName));
            } catch (SQLException | RuntimeException error) {
                rollback(connection);
                throw error;
            }
        } catch (SQLException error) {
            throw new RuntimeException("Could not save notes for " + player, error);
        }
    }

    private static void rollback(Connection connection) {
        try (Statement rollback = connection.createStatement()) {
            rollback.execute("ROLLBACK");
        } catch (SQLException ignored) {
            // The connection is about to close; a failed rollback has nothing left to repair.
        }
    }
}
