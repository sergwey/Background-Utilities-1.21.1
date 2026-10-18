package net.xlebupaksa.backutils.clientchest;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One page of the client chest: fifty-four slots, the game version that wrote them, and the slots whose
 * items this game cannot read.
 *
 * <p>A page is not a container any server knows about. It is a file in the player's own game
 * directory, which is what makes the same pages follow them from world to world and from server to
 * server — and what makes the version a page was written by a property of the page rather than
 * something the game could look up.
 *
 * <p>Three invariants are load-bearing enough to be enforced here rather than trusted to the callers:
 *
 * <ul>
 *   <li><b>Always {@value #SLOTS} slots.</b> A page is drawn as a nine by six chest and indexed by
 *       slot, so a page of another length is either a chest with a hole in it or a file written with
 *       an index that means something else.
 *   <li><b>Items are only ever read from a page of this game's own version.</b> Item data is version
 *       specific: a sword written by a newer version can name a component this one has never heard
 *       of, and reading it as an item is how an inventory is destroyed after a game update. A page
 *       therefore carries a {@link Status}, and only {@link Status#CURRENT} hands its items out —
 *       {@link #items()} refuses the rest rather than answering with the empty array, because a
 *       caller that got an all-empty page back would take it for an empty page and save over it.
 *   <li><b>An item this game cannot read is kept, not dropped.</b> The same is true of one slot inside
 *       a page of this version: an item can name a component from a datapack or another mod that
 *       this world does not have. That item's own data is kept here as the raw tag it was written
 *       with, the slot is {@link #locked} and drawn red, and everything else in the page works — a
 *       library that lost such an item would lose it silently, and a page refused whole over one
 *       slot would take fifty-three good items with it.
 * </ul>
 */
public record ChestPage(int dataVersion, ItemStack[] items, Map<Integer, CompoundTag> dynamic,
                        ChestPage.Status status) {

    /** Slots in a page: nine columns of six rows, which is the largest chest a player knows. */
    public static final int SLOTS = 54;

    /**
     * What a page's {@code DataVersion} says about whether this game can use it.
     *
     * <p>Deliberately not a boolean: what a player is offered for a page written by another version
     * depends on which version wrote it, and the difference has to survive from reading the file to
     * the screen that offers the choice.
     */
    public enum Status {
        /** Written by this game. Its readable items are items; the rest are {@link #dynamic}. */
        CURRENT,
        /** No {@code DataVersion} at all: older than the tag, and cannot be dated. */
        MISSING_VERSION,
        /** Written by an older version; its items cannot be read until it is stamped as this one. */
        OLDER_VERSION,
        /** Written by a newer version. Never read, and never stamped backwards: that is the corruption. */
        NEWER_VERSION
    }

    public ChestPage {
        if (items.length != SLOTS) {
            throw new IllegalArgumentException(
                    "a page holds " + SLOTS + " slots, not " + items.length);
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items[slot] == null) items[slot] = ItemStack.EMPTY;
        }
        if (dynamic == null) dynamic = new LinkedHashMap<>();
        if (status != Status.CURRENT) {
            // A refused page describes a file that has not been read, so it has no items and no raw
            // slots to hold. Enforced rather than assumed: a page that carried either beside a foreign
            // status would be a page whose gate could be bypassed by reading the field instead.
            for (ItemStack item : items) {
                if (!item.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a page that is not current cannot hold items (" + status + ")");
                }
            }
            if (!dynamic.isEmpty()) {
                throw new IllegalArgumentException(
                        "a page that is not current cannot hold unread slots (" + status + ")");
            }
        }
        for (Map.Entry<Integer, CompoundTag> entry : dynamic.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= SLOTS) {
                throw new IllegalArgumentException("no slot " + slot + " in a page of " + SLOTS);
            }
            if (!items[slot].isEmpty()) {
                // One slot cannot be both an item and the tag an item could not be read from: the two
                // would disagree about what the page holds, and a save would pick one at random.
                throw new IllegalArgumentException("slot " + slot + " is both an item and unread");
            }
        }
    }

    /**
     * {@return the items of a page this game can use}
     *
     * @throws IllegalStateException when the page is not {@link Status#CURRENT}, which is the gate
     *                               that keeps item data of another version from ever being read
     */
    @Override
    public ItemStack[] items() {
        if (status != Status.CURRENT) {
            throw new IllegalStateException("items of a " + status + " page must not be read");
        }
        return items;
    }

    /** {@return the raw tag of every slot whose item this game cannot read}, by slot */
    public Map<Integer, CompoundTag> dynamic() {
        if (status != Status.CURRENT) {
            throw new IllegalStateException("unread slots of a " + status + " page must not be read");
        }
        return dynamic;
    }

    /** {@return true when this slot holds an item this game cannot read}, which is drawn and locked */
    public boolean locked(int slot) {
        return status == Status.CURRENT && dynamic.containsKey(slot);
    }

    /**
     * Forgets the unread item in one slot, leaving the slot empty and usable.
     *
     * <p>The only way out of a locked slot, and deliberately an act: the tag is the last copy of that
     * item anywhere this game can see, so it goes when somebody says so rather than when a page is
     * opened.
     *
     * @return true when the slot was locked
     */
    public boolean unlock(int slot) {
        if (status != Status.CURRENT) return false;
        return dynamic.remove(slot) != null;
    }

    /** {@return a page of this version with nothing in it} */
    public static ChestPage blank() {
        ItemStack[] items = new ItemStack[SLOTS];
        Arrays.fill(items, ItemStack.EMPTY);
        return new ChestPage(currentDataVersion(), items, new LinkedHashMap<>(), Status.CURRENT);
    }

    /**
     * {@return a page that was refused, named by why}
     *
     * <p>The data version is kept for the refusals so the screen can say which version wrote the page;
     * a page with no version at all has none to keep.
     */
    public static ChestPage refused(Status status, int dataVersion) {
        if (status == Status.CURRENT) {
            throw new IllegalArgumentException("a refused page is not a current one");
        }
        ItemStack[] items = new ItemStack[SLOTS];
        Arrays.fill(items, ItemStack.EMPTY);
        return new ChestPage(dataVersion, items, new LinkedHashMap<>(), status);
    }

    /** {@return the data version this game writes}, which is what a page has to match to be used */
    public static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    /** {@return true when this game may read and write this page} */
    public boolean usable() {
        return status == Status.CURRENT;
    }

    /**
     * {@return true when no slot holds anything}, which is what makes a page's file go away
     *
     * <p>An unread slot counts: the page still describes an item, and a page whose file was deleted
     * because every readable slot was empty would delete that item with it.
     */
    public boolean isEmpty() {
        if (!dynamic.isEmpty()) return false;
        for (ItemStack item : items) {
            if (!item.isEmpty()) return false;
        }
        return true;
    }

    /**
     * {@return every slot whose item this game cannot read}, for a screen that draws them red
     *
     * <p>A copy rather than a view of the map: unlocking a slot while the screen is walking this set —
     * which is what clearing a page does — would otherwise change the set under its own iteration.
     */
    public java.util.Set<Integer> lockedSlots() {
        return status == Status.CURRENT ? java.util.Set.copyOf(dynamic.keySet())
                : java.util.Set.of();
    }
}
