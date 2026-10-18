package net.xlebupaksa.backutils.clientchest;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.xlebupaksa.backutils.BackUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The library itself: pages of fifty-four slots, as many as the player stores, kept in the player's
 * game directory.
 *
 * <p>Client-side by construction. Nothing here talks to a server and nothing here is drawn; it is the
 * answer to "what does page seven hold", and it is the same answer on every world and every server
 * because the files are the player's own.
 *
 * <p>Three decisions are worth stating because they are what keeps a library alive:
 *
 * <ul>
 *   <li><b>A page is read when it is first asked about, not at startup.</b> A hundred files read on
 *       the way into a world would be a stall in the one place a player notices it; what is needed
 *       early — which pages hold anything, and which have names — comes from the folder listing
 *       instead, which is one directory read.
 *   <li><b>The folder is the authority on which pages are worth visiting.</b> An empty page has no
 *       file, so a file is the statement that a page holds something, and the jump buttons read the
 *       folder rather than trusting the cache.
 *   <li><b>A page this game cannot read is never written.</b> {@link #save} refuses a page whose
 *       {@link ChestPage#usable()} is false, so the one operation that could destroy a library from
 *       another version cannot reach it.
 * </ul>
 */
public final class ChestPages {

    /**
     * The highest page the chest will open.
     *
     * <p>A bound on a number somebody types, not on the library: pages are files, and a page costs
     * nothing until it holds something, so there is no count to keep — a player who fills a hundred
     * pages opens the hundred-and-first and it exists. A million is far past any player and still
     * small enough that a mistyped number opens an empty page rather than puzzling anything.
     *
     * <p>What keeps this honest is that nothing is read ahead of time: a page is read the first time
     * it is looked at and stays in memory for the session, so a library of a thousand pages costs what
     * the pages that were visited cost, not what the library costs.
     */
    public static final int LAST_PAGE = 1_000_000;

    /** The folder, under the game directory, that the pages live in. */
    private static final String FOLDER = "backutils/client_chest";

    private static final String ITEMS = "items";

    /**
     * The key that marks a slot holding an item this game could not read, and the tag that is kept
     * for it. Written beside the item's own data, so a reader that knows the key keeps the slot and a
     * reader that does not sees a slot it cannot parse — which is the same answer either way.
     */
    private static final String DYNAMIC = "dynamic";

    private final Path gameDirectory;
    private final PageFiles files;
    private final PageNames names;
    private final Map<Integer, ChestPage> cache = new HashMap<>();

    public ChestPages(Path gameDirectory) {
        this.gameDirectory = gameDirectory;
        this.files = new PageFiles(gameDirectory.resolve(FOLDER));
        this.names = new PageNames(files.folder());
        this.names.load();
    }

    /**
     * The one library of the running game.
     *
     * <p>One instance rather than one per caller: each has its own cache and its own copy of the names,
     * so two of them would disagree about a rename and the next save from either would write the whole
     * names file from its own copy. It also saves reading that file again every time a chest is opened.
     */
    private static ChestPages running;

    /** {@return the library of the game that is running} */
    public static ChestPages ofRunningGame() {
        if (running == null) running = new ChestPages(Minecraft.getInstance().gameDirectory.toPath());
        return running;
    }

    /** {@return the folder the pages are in}, which is what a warning about one points at */
    public Path folder() {
        return files.folder();
    }

    /** {@return how many pages the chest will open}, which is a bound on a typed number */
    public int pageCount() {
        return LAST_PAGE + 1;
    }

    /** {@return the page as it is written, which is zero-based and clamped to something openable} */
    public int clamp(int page) {
        return Math.max(0, Math.min(LAST_PAGE, page));
    }

    /** {@return the names pages are given} */
    public PageNames names() {
        return names;
    }

    /**
     * {@return one page, read from disk the first time it is asked about}
     *
     * <p>A page that cannot be read is replaced in memory by an empty one and its file is set aside;
     * the complaint is reported once by {@link #quarantined()} rather than from here, because this is
     * called while a screen is being built.
     */
    public ChestPage page(int index) {
        int page = clamp(index);
        ChestPage cached = cache.get(page);
        return cached != null ? cached : read(page);
    }

    private ChestPage read(int page) {
        if (!files.exists(page)) {
            ChestPage blank = ChestPage.blank();
            cache.put(page, blank);
            return blank;
        }
        HolderLookup.Provider registries = registries();
        if (registries == null) {
            // No level, so nothing to read items against: the file is left exactly as it is and the
            // page is answered empty for now. Not remembered, because an empty page that was
            // remembered would have its file written over by the next change.
            return ChestPage.blank();
        }
        CompoundTag tag;
        try {
            tag = files.read(page);
        } catch (Throwable failure) {
            if (failure instanceof Error error) throw error;
            return setAside(page, failure);
        }
        ChestPage decoded;
        try {
            decoded = decode(tag, registries);
        } catch (RuntimeException failure) {
            return setAside(page, failure);
        }
        cache.put(page, decoded);
        return decoded;
    }

    /**
     * Forgets what one page's file was read as, so that the next look reads it again.
     *
     * <p>What a reload is, and the only thing that makes an edit made outside the game visible: the
     * cache is otherwise never wrong, because this class is the only writer.
     */
    public void forget(int index) {
        cache.remove(clamp(index));
    }

    /**
     * Writes one page, or deletes its file when it is empty.
     *
     * <p>Called after every change, which is what the tool this is modelled on does: a page is a few
     * kilobytes, and a save that only happened when a screen closed would lose the last move of
     * anybody whose game crashed while they were arranging things.
     */
    public void save(int index) {
        int page = clamp(index);
        ChestPage cached = cache.get(page);
        if (cached == null || !cached.usable()) return;
        if (cached.isEmpty()) {
            if (!files.delete(page)) {
                BackUtils.LOGGER.error("could not delete the file of emptied client chest page {}",
                        page + 1);
            }
            // Emptied in place rather than replaced: the screen holds this page's array, and a new
            // object here would leave it writing into a page nothing reads any more.
            for (int slot = 0; slot < ChestPage.SLOTS; slot++) cached.items()[slot] = ItemStack.EMPTY;
            return;
        }
        HolderLookup.Provider registries = registries();
        if (registries == null) return;
        try {
            files.write(page, encode(cached, registries));
        } catch (IOException | RuntimeException failure) {
            BackUtils.LOGGER.error("could not save client chest page {}; the file on disk is the one "
                    + "from before this change", page + 1, failure);
        }
    }

    /**
     * Stamps a page from another game version as this one.
     *
     * <p>The only way a page of an older version becomes usable, and deliberately a separate step:
     * item data is version specific, so "read this file anyway" is a decision a player makes about one
     * page after being told which version wrote it, not something a program does silently. A newer
     * version's page is never stamped: reading data written by a newer game is the corruption this
     * whole gate exists to prevent.
     *
     * @return true when the page is now of this version
     */
    public boolean importVersion(int index) {
        int page = clamp(index);
        ChestPage known = page(page);
        if (known.status() != ChestPage.Status.MISSING_VERSION
                && known.status() != ChestPage.Status.OLDER_VERSION) {
            return false;
        }
        CompoundTag tag;
        try {
            tag = files.read(page);
        } catch (Throwable failure) {
            if (failure instanceof Error error) throw error;
            setAside(page, failure);
            return false;
        }
        int written = tag.contains(SharedConstants.DATA_VERSION_TAG, Tag.TAG_INT)
                ? tag.getInt(SharedConstants.DATA_VERSION_TAG) : 0;
        if (written > ChestPage.currentDataVersion()) {
            // The file says something newer than the page that was read was: stamping that down is the
            // one thing this whole status is here to prevent, so it is refused rather than trusted.
            return false;
        }
        tag.putInt(SharedConstants.DATA_VERSION_TAG, ChestPage.currentDataVersion());
        try {
            files.write(page, tag);
        } catch (IOException failure) {
            BackUtils.LOGGER.error("could not import client chest page {}", page + 1, failure);
            return false;
        }
        cache.remove(page);
        return true;
    }

    /**
     * Throws one page away.
     *
     * <p>Refused for a page of this version: those are deleted by emptying them, which is an act with
     * something to lose. This exists for a file this build cannot use at all, where keeping it is
     * keeping a page that will never open.
     */
    public void discard(int index) {
        int page = clamp(index);
        if (page(page).usable()) return;
        if (files.delete(page)) {
            // The name goes with the page: a name pointing at nothing is offered by the jumps to a page
            // that is not there, and the next page at that index would inherit it.
            if (names.set(page, "")) names.save();
            cache.put(page, ChestPage.blank());
        } else {
            // Still there: forget the cache rather than pretend, so the page reads as what it is.
            BackUtils.LOGGER.error("could not delete the file of client chest page {}", page + 1);
            cache.remove(page);
        }
    }

    /**
     * {@return the nearest pages before and after this one that are worth going to}, each {@code -1}
     * when there is none, so the jump buttons can say whether they would do anything
     *
     * <p>"Worth going to" is a page that holds something or has a name: an empty page with no name is
     * a page nobody meant to visit, and stepping onto one of a hundred of those is what the jump
     * buttons exist to avoid.
     */
    public int[] nearest(int from) {
        TreeSet<Integer> interesting = files.pages();
        interesting.addAll(names.named());
        for (Map.Entry<Integer, ChestPage> entry : cache.entrySet()) {
            if (!entry.getValue().isEmpty()) interesting.add(entry.getKey());
        }
        Integer previous = interesting.lower(from);
        Integer next = interesting.higher(from);
        return new int[]{previous == null ? -1 : previous, next == null ? -1 : next};
    }

    /** {@return the files that were set aside because they could not be read}, for a warning */
    public List<Path> quarantined() {
        return files.quarantined();
    }

    /**
     * Moves the library of the tool this feature is modelled on into this one.
     *
     * <p>What is imported is an abandoned folder: that tool runs on Fabric and cannot be installed
     * beside this one on 1.21.1, which is why the move is offered rather than a second copy being
     * read in place.
     *
     * @return how many files came across
     */
    public int migrateFromOriginal() {
        int moved = files.migrateFrom(gameDirectory.resolve("nbteditor/client_chest"));
        if (moved > 0) {
            names.load();
            cache.clear();
        }
        return moved;
    }

    /** {@return true when the abandoned library of the other tool is there to be imported} */
    public boolean hasOriginalLibrary() {
        return !new PageFiles(gameDirectory.resolve("nbteditor/client_chest")).pages().isEmpty();
    }

    /** Turns one page into the file it is written as. */
    private static CompoundTag encode(ChestPage page, HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        root.putInt(SharedConstants.DATA_VERSION_TAG, ChestPage.currentDataVersion());
        ListTag list = new ListTag();
        for (int slot = 0; slot < ChestPage.SLOTS; slot++) {
            CompoundTag unread = page.dynamic().get(slot);
            // The item wins when a page somehow holds both: something being drawn in the slot is what
            // the player can see, and writing the tag instead would lose it without a word.
            if (unread != null && page.items()[slot].isEmpty()) {
                // The item's own tag with the marker added: the slot cannot be read as an item, and
                // what is kept is exactly what was written, so that a world which does have the
                // component can read it back.
                CompoundTag marked = unread.copy();
                marked.putBoolean(DYNAMIC, true);
                list.add(marked);
                continue;
            }
            // Optional: a slot with nothing in it is an empty compound, which is what a reader
            // expects for an empty slot and what the tool this is modelled on writes.
            list.add(page.items()[slot].saveOptional(registries));
        }
        root.put(ITEMS, list);
        return root;
    }

    /** Turns the file a page is written as back into a page, or into the reason it is refused. */
    private static ChestPage decode(CompoundTag root, HolderLookup.Provider registries) {
        int current = ChestPage.currentDataVersion();
        if (!root.contains(SharedConstants.DATA_VERSION_TAG, Tag.TAG_INT)) {
            return ChestPage.refused(ChestPage.Status.MISSING_VERSION, 0);
        }
        int version = root.getInt(SharedConstants.DATA_VERSION_TAG);
        if (version != current) {
            return ChestPage.refused(version > current
                    ? ChestPage.Status.NEWER_VERSION
                    : ChestPage.Status.OLDER_VERSION, version);
        }
        if (!root.contains(ITEMS, Tag.TAG_LIST)) {
            // A page file with no list of slots is not an empty page — an empty page has no file at
            // all — so it is a file that cannot be read, and saying so sets it aside instead of
            // treating it as empty and deleting it on the next change.
            throw new IllegalStateException("page file without an items list");
        }
        ListTag list = root.getList(ITEMS, Tag.TAG_COMPOUND);
        ItemStack[] items = new ItemStack[ChestPage.SLOTS];
        Map<Integer, CompoundTag> unread = new LinkedHashMap<>();
        for (int slot = 0; slot < ChestPage.SLOTS; slot++) {
            CompoundTag entry = slot < list.size() ? list.getCompound(slot) : new CompoundTag();
            if (entry.isEmpty()) {
                items[slot] = ItemStack.EMPTY;
                continue;
            }
            if (entry.getBoolean(DYNAMIC)) {
                // A slot that was already kept rather than read, written back by this build or by
                // one that has the component: the marker is stripped and the tag kept whole.
                CompoundTag kept = entry.copy();
                kept.remove(DYNAMIC);
                unread.put(slot, kept);
                items[slot] = ItemStack.EMPTY;
                continue;
            }
            try {
                ItemStack parsed = ItemStack.parse(registries, entry).orElse(null);
                // Empty as well as null: a tag that parses into nothing — air, or a count of zero — is
                // an item this game cannot show, and keeping its tag is better than dropping it.
                if (parsed == null || parsed.isEmpty()) {
                    unread.put(slot, entry.copy());
                    items[slot] = ItemStack.EMPTY;
                } else {
                    items[slot] = parsed;
                }
            } catch (RuntimeException failure) {
                // One slot that will not parse must not cost the other fifty-three their page, and
                // must not be dropped either: the tag is kept as it was written, the slot is locked,
                // and the page opens with the rest of it usable.
                unread.put(slot, entry.copy());
                items[slot] = ItemStack.EMPTY;
            }
        }
        return new ChestPage(version, items, unread, ChestPage.Status.CURRENT);
    }

    /**
     * Sets a page's file aside, says so in the log, and answers an empty page that is not remembered.
     *
     * <p>Not remembered is the point: the file is still there (moved, not deleted), and a page cached
     * as empty would have that file — or what is left of its name — written over by the next change.
     * {@link #quarantined()} re-reads the folder, so nothing has to be remembered to warn about it.
     */
    private ChestPage setAside(int page, Throwable failure) {
        try {
            Path moved = files.quarantine(page);
            BackUtils.LOGGER.error("client chest page {} could not be read; it was moved to {}",
                    page + 1, moved.getFileName(), failure);
        } catch (IOException e) {
            BackUtils.LOGGER.error("client chest page {} could not be read or set aside", page + 1, e);
        }
        return ChestPage.blank();
    }

    /** {@return the registries items are read and written against, or null outside a world} */
    private static HolderLookup.Provider registries() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess();
    }
}
