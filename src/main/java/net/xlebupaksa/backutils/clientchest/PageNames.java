package net.xlebupaksa.backutils.clientchest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.xlebupaksa.backutils.BackUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The names pages are given, kept beside the pages rather than in them.
 *
 * <p>A name is a property of the library — <em>which</em> page is the ores — and not of the file it
 * is written in. Renaming a page must not rewrite its items, and a page refused for its version must
 * keep the name somebody gave it. So the names are one JSON object of their own, page index to name,
 * written whole whenever one changes.
 *
 * <p>One shape both ways, deliberately. The tool this is modelled on reads a name-to-page map and
 * writes a page-to-name one, so a library survives a rename only until something reloads it; here the
 * file is an object keyed by the page written as a number, and both directions use that.
 *
 * <p>Names are unique and compared without case, which is what lets a name be typed on the command
 * line as easily as it is read in a title. Two pages called {@code ores} and {@code Ores} would be a
 * question nobody could answer from the title bar.
 */
public final class PageNames {

    /** The file the names live in, beside the pages. */
    private static final String FILE_NAME = "page_names.json";

    /**
     * The longest name a page may be given.
     *
     * <p>Forty characters: a name is drawn in the chest's title beside its number and typed into a
     * command, and a longer one is a name that cannot be read in either place.
     */
    public static final int MAX_LENGTH = 40;

    private final Path folder;
    private final Map<Integer, String> byPage = new LinkedHashMap<>();

    public PageNames(Path folder) {
        this.folder = folder;
    }

    /**
     * Reads the names, or starts without them.
     *
     * <p>A file that cannot be read is set aside rather than trusted or overwritten: the names are a
     * player's own labels, and a session that treated a broken file as "no names" and then saved
     * would replace every one of them with nothing.
     */
    public void load() {
        byPage.clear();
        Path file = folder.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return;
        // Read into a map of its own and taken whole: one bad entry used to leave the names before it
        // loaded, and the next save then wrote that prefix over the file as if it were all of them.
        Map<Integer, String> found = new LinkedHashMap<>();
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                int page = Integer.parseInt(entry.getKey());
                String name = trimmed(entry.getValue().getAsString());
                if (page < 0 || name.isEmpty()) continue;
                // Unique without case here as well as in set: a hand-written file can hold both "Ores"
                // and "ores", and a name that answered for two pages would answer arbitrarily.
                if (taken(found, name)) continue;
                found.put(page, name);
            }
        } catch (Exception e) {
            quarantine(file);
            BackUtils.LOGGER.error("could not read the client chest's page names; "
                    + "the file was set aside rather than replaced", e);
            return;
        }
        byPage.putAll(found);
    }

    /** {@return true when one name in a map is this name}, compared the way names are */
    private static boolean taken(Map<Integer, String> names, String name) {
        for (String each : names.values()) {
            if (each.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Writes the names whole, onto a temporary file that is then moved over the real one. */
    public void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<Integer, String> entry : byPage.entrySet()) {
            root.addProperty(Integer.toString(entry.getKey()), entry.getValue());
        }
        Path file = folder.resolve(FILE_NAME);
        Path temporary = folder.resolve(FILE_NAME + ".saving");
        try {
            Files.createDirectories(folder);
            Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            BackUtils.LOGGER.error("could not save the client chest's page names", e);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Nothing further to do: the names in memory are still right, and the next save tries
                // again.
            }
        }
    }

    /** {@return the page one name belongs to, or {@code -1} when no page is called that} */
    public int pageOf(String name) {
        String wanted = trimmed(name);
        if (wanted.isEmpty()) return -1;
        for (Map.Entry<Integer, String> entry : byPage.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(wanted)) return entry.getKey();
        }
        return -1;
    }

    /** {@return the name of one page, or an empty string when it has none} */
    public String nameOf(int page) {
        String name = byPage.get(page);
        return name == null ? "" : name;
    }

    /**
     * Gives one page a name, or takes its name away.
     *
     * <p>A name already in use is taken over rather than refused, which is what the tool this is
     * modelled on does and what makes a name feel like a label on the page in front of you: the page
     * that had it loses it, and the title of that page stops claiming it.
     *
     * @return true when the name of some page changed
     */
    public boolean set(int page, String name) {
        if (page < 0) return false;
        String wanted = trimmed(name);
        if (wanted.isEmpty()) return byPage.remove(page) != null;
        int owner = pageOf(wanted);
        boolean changed = owner != page || !wanted.equals(nameOf(page));
        if (owner >= 0 && owner != page) byPage.remove(owner);
        byPage.put(page, wanted);
        return changed;
    }

    /** {@return every page that has a name}, ascending */
    public List<Integer> named() {
        List<Integer> pages = new ArrayList<>(byPage.keySet());
        pages.sort(null);
        return pages;
    }


    /** {@return the name as it is stored: trimmed, and no longer than a name may be} */
    private static String trimmed(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.codePointCount(0, trimmed.length()) <= MAX_LENGTH) return trimmed;
        // Counted in code points, not UTF-16 units: forty characters is forty characters, and half an
        // emoji saved to a file is a name nobody typed.
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_LENGTH)).trim();
    }

    /** Sets a names file aside under a name nothing reads, so the next save does not destroy it. */
    private void quarantine(Path file) {
        try {
            Files.move(file, folder.resolve("corrupt_page_names_" + System.currentTimeMillis() + ".json"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Left where it is: a file that will not move is still better than one overwritten.
        }
    }
}
