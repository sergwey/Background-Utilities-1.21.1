package net.xlebupaksa.backutils.clientchest;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.xlebupaksa.backutils.BackUtils;

import java.util.function.IntPredicate;

/**
 * The menu behind the chest screen: the page's fifty-four slots, the player's own, and no server.
 *
 * <p>A negative container id, and synchronisation silenced. The two do different jobs. The id is one
 * no server-created menu can have, so that nothing which ever does reach the server can name a menu
 * that exists there; the silence is what stops the client trying to keep a menu the server has never
 * heard of in step with it, which is the whole of what makes a fake container work. The screen goes
 * further and does not send the click at all — see {@code ChestScreen.slotClicked}.
 *
 * <p>The player's half wraps the real inventory rather than a copy, which is the point of the design:
 * an item taken out of the chest really is in the player's inventory afterwards, and the creative slot
 * packets the screen sends are what make the server agree about that.
 */
public final class ChestMenu extends AbstractContainerMenu {

    /** A container id below every id a server hands out, so the two can never be confused. */
    public static final int CONTAINER_ID = -2718;

    /** Slots of the page, which is how the two halves of the menu are told apart. */
    static final int PAGE_SLOTS = ChestPage.SLOTS;

    /** Rows of the page, which is what the six-row chest texture is drawn for. */
    private static final int ROWS = 6;

    private final SimpleContainer container;

    /**
     * Which page slots are locked, asked of the screen because the page is the screen's to know.
     *
     * <p>Refused here rather than only in the screen's click handling, because shift-click and a drag
     * both reach the slots without a click of their own: a locked slot holds the only tag of an item
     * this game cannot read, and an item placed over it would take that tag with it.
     */
    private IntPredicate locked = slot -> false;

    /** Tells the menu which page slots hold an item this game cannot read. */
    public void locked(IntPredicate locked) {
        this.locked = locked;
    }

    public ChestMenu(Inventory playerInventory, SimpleContainer container) {
        // No menu type: this menu is never created by a server and never looked up in a registry, and
        // the id is the only part of a menu a click packet names.
        super(null, CONTAINER_ID);
        checkContainerSize(container, PAGE_SLOTS);
        this.container = container;

        // Nine by six, at the positions a chest of that size puts them, so the vanilla texture fits.
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(container, column + row * 9, 8 + column * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return !ChestMenu.this.locked.test(this.index) && super.mayPlace(stack);
                    }
                });
            }
        }
        // The player's main inventory and hotbar, at the heights the six-row chest leaves for them.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        8 + column * 18, 139 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 197));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        // A menu in a file has no block to stand near.
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Outside creative the player's half of this screen is not its to change: a shift-click that
        // emptied a page into an inventory the server knows nothing about would be a picture of items
        // nobody has. The screen refuses the same thing one click earlier; this is the other door.
        if (!player.isCreative()) return ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < PAGE_SLOTS
                ? this.moveItemStackTo(stack, PAGE_SLOTS, this.slots.size(), true)
                : this.moveItemStackTo(stack, 0, PAGE_SLOTS, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * Does nothing, deliberately.
     *
     * <p>This is the equivalent of the reference implementation's {@code disableSyncing}: the client
     * half of a menu has no synchroniser, but a menu that reports changes is a menu something could
     * one day report them for, and this one has no server to report them to. The player's inventory is
     * pushed with creative slot packets instead, which the screen does after each change it allows.
     */
    @Override
    public void broadcastChanges() {
        // Nothing to synchronise: see the class note.
    }

    /**
     * Gives back whatever was on the cursor when the screen closed.
     *
     * <p>Into the page first: whatever the cursor holds came out of a slot of it, so there is room —
     * a slot is free that held that stack a moment ago. Only a menu whose page filled up from
     * somewhere else needs the second answer, and outside creative there is none to give, which is
     * why it is said in the log rather than turned into a second copy of the item.
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        ItemStack carried = this.getCarried();
        if (carried.isEmpty()) return;
        this.setCarried(ItemStack.EMPTY);
        ItemStack leftOver = this.container.addItem(carried);
        if (leftOver.isEmpty()) {
            this.container.setChanged();
            return;
        }
        if (player.isCreative() && player.getInventory().add(leftOver)) {
            player.getInventory().setChanged();
            return;
        }
        BackUtils.LOGGER.error("the client chest could not take back the stack that was on the cursor "
                + "({}); it is lost rather than duplicated", leftOver);
    }
}
