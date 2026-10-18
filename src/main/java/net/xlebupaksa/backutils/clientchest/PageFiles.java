package net.xlebupaksa.backutils.clientchest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The page files themselves: where one lives, how it is written, and what happens to one that cannot
 * be read. Nothing here knows what an item is.
 *
 * <p>Kept apart from {@link ChestPages} for two reasons. It is the half that can be exercised
 * without a game — the compile harness cannot create an {@code ItemStack}, but it can write and read
 * a {@code CompoundTag} and rename a file. And it is the half where a mistake costs a player their
 * library, so its rules are stated in one place:
 *
 * <ul>
 *   <li><b>Never write in place.</b> A page is written to a temporary file beside it and moved onto
 *       the page's name, so a crash, a full disk or a killed process leaves either the old page or
 *       the new one and never half of either.
 *   <li><b>Never delete something that could not be read.</b> A page that fails to parse is renamed
 *       to a {@code corrupt_} name and left there: a page that cannot be read by this build may be
 *       perfectly readable by the next one, and a library is not something to throw away over a
 *       parser.
 *   <li><b>An empty page has no file.</b> Which is what makes "a page file exists" the same
 *       statement as "that page holds something", and lets the folder itself answer which pages are
 *       worth jumping to.
 * </ul>
 */
public final class PageFiles {

    /** The stem of a page file: {@code page0.nbt}, {@code page17.nbt}. */
    private static final String PAGE = "page";

    private static final String SUFFIX = ".nbt";

    /** The stem of a file set aside because it could not be read, and of a file being written. */
    private static final String CORRUPT = "corrupt_page";
    private static final String SAVING = "saving_page";

    /** The names file, which lives beside the pages and moves with them. */
    private static final String NAMES = "page_names.json";

    private final Path folder;

    public PageFiles(Path folder) {
        this.folder = folder;
    }

    /** {@return the folder the pages are in} */
    public Path folder() {
        return folder;
    }

    /** {@return the file one page is kept in} */
    public Path path(int page) {
        return folder.resolve(PAGE + page + SUFFIX);
    }

    /**
     * {@return the page a file name stands for, or {@code -1} when it is not a page file}
     *
     * <p>The single reading of the naming rule: the folder listing, the migration from the tool this
     * is modelled on, and the jump buttons all have to agree about which files are pages, and this is
     * the only place that decides.
     */
    public static int pageOf(String fileName) {
        if (!fileName.startsWith(PAGE) || !fileName.endsWith(SUFFIX)) return -1;
        String digits = fileName.substring(PAGE.length(), fileName.length() - SUFFIX.length());
        if (digits.isEmpty()) return -1;
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) return -1;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            // A name long enough to overflow an int is not a page this library ever wrote.
            return -1;
        }
    }

    /** {@return true when one page has a file} */
    public boolean exists(int page) {
        return Files.isRegularFile(path(page));
    }

    /**
     * {@return the tag a page's file holds}
     *
     * @throws IOException when the file cannot be read or does not parse; the caller is expected to
     *                     {@link #quarantine} it rather than delete it
     */
    public CompoundTag read(int page) throws IOException {
        // Unlimited: a page is a player's own file, and refusing to load the biggest sword somebody
        // made because it is over a size limit the game invented for packets would be absurd.
        return NbtIo.readCompressed(path(page), NbtAccounter.unlimitedHeap());
    }

    /**
     * Writes one page, replacing it only once the new contents are safely on disk.
     *
     * @throws IOException when it could not be written; the page's previous file is untouched
     */
    public void write(int page, CompoundTag tag) throws IOException {
        Files.createDirectories(folder);
        Path temporary = folder.resolve(SAVING + page + "_" + System.currentTimeMillis() + SUFFIX);
        boolean moved = false;
        try {
            NbtIo.writeCompressed(tag, temporary);
            try {
                Files.move(temporary, path(page), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                // Some file systems cannot replace a name atomically. The plain move is then the best
                // there is, rather than refusing to save.
                Files.move(temporary, path(page), StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            // Only a temporary this call did not move: a page that was written must not be reported as
            // one that was not, which is what an exception from the cleanup would do.
            try {
                if (!moved) Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A temporary nothing reads is not worth losing the write over.
            }
        }
    }

    /** {@return true when a page had a file to delete} */
    public boolean delete(int page) {
        try {
            return Files.deleteIfExists(path(page));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Sets one page's file aside under a name no page answers to.
     *
     * @return where it was moved to
     * @throws IOException when even that could not be done, in which case the file stays where it is
     */
    public Path quarantine(int page) throws IOException {
        Path target = folder.resolve(CORRUPT + page + "_" + System.currentTimeMillis() + SUFFIX);
        Files.move(path(page), target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /** {@return the files set aside because they could not be read}, oldest name first */
    public List<Path> quarantined() {
        List<Path> found = new ArrayList<>();
        if (!Files.isDirectory(folder)) return found;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, CORRUPT + "*" + SUFFIX)) {
            for (Path file : files) found.add(file);
        } catch (IOException e) {
            // Unreadable folder: nothing to warn about rather than a crash on opening the chest.
            return found;
        }
        found.sort(null);
        return found;
    }

    /**
     * {@return every page index that has a file}, ascending
     *
     * <p>The folder is the authority rather than the cache: an empty page has no file, so a file
     * means a page that holds something, whatever this session happens to have loaded.
     */
    public TreeSet<Integer> pages() {
        TreeSet<Integer> found = new TreeSet<>();
        if (!Files.isDirectory(folder)) return found;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, PAGE + "*" + SUFFIX)) {
            for (Path file : files) {
                int page = pageOf(file.getFileName().toString());
                if (page >= 0) found.add(page);
            }
        } catch (IOException e) {
            return found;
        }
        return found;
    }

    /**
     * Moves a library written by the tool this feature is modelled on into this folder.
     *
     * <p>A move rather than a copy, which is what the owner asked for: the other tool was never
     * installed here, so what is being imported is an abandoned library rather than one still in use,
     * and leaving a second copy behind is how two libraries become two different ones.
     *
     * <p>Nothing is overwritten. A file whose name this folder already has is left where it is: the
     * page here is the one the player has been using since.
     *
     * @return how many files were moved
     */
    public int migrateFrom(Path source) {
        if (!Files.isDirectory(source)) return 0;
        createDirectoriesSafely(folder);
        int moved = 0;
        for (String pattern : new String[]{PAGE + "*" + SUFFIX, NAMES}) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(source, pattern)) {
                for (Path file : files) {
                    Path target = folder.resolve(file.getFileName().toString());
                    if (Files.exists(target)) continue;
                    try {
                        Files.move(file, target);
                        moved++;
                    } catch (IOException e) {
                        // One file that will not move must not stop the rest: the count is what the
                        // command reports, and the files left behind stay where they were.
                        continue;
                    }
                }
            } catch (IOException e) {
                continue;
            }
        }
        return moved;
    }

    /** Creates the folder, without the checked exception a caller that cannot fail usefully hates. */
    private static void createDirectoriesSafely(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            // Left to the write that follows, which reports in a way the caller can act on.
        }
    }
}
