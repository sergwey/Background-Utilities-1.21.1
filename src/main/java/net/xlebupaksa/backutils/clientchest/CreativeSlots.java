package net.xlebupaksa.backutils.clientchest;

/**
 * The bridge between a player's inventory and the slot numbers a creative set-slot packet counts in.
 *
 * <p>The client chest moves items by changing the <em>client's</em> inventory and then telling the
 * server what it now holds, one slot at a time, with the packet creative mode already sends for
 * exactly this. Those packets number the player's slots the way the inventory menu does — armour
 * first, then the main inventory, then the hotbar, then the off hand — which is not the order an
 * {@code Inventory} stores them in, so every send needs this translation and nothing else in the
 * feature should have to know it.
 *
 * <p>Written out here, in the one place, because a mapping that is off by a slot does not fail: it
 * silently gives a player somebody else's slot, which is the worst kind of bug a creative tool can
 * have.
 */
public final class CreativeSlots {

    /** Slots an {@code Inventory} holds: hotbar, main, armour, off hand. */
    public static final int INVENTORY_SLOTS = 41;

    private CreativeSlots() {}

    /**
     * {@return the slot number a creative set-slot packet uses for one inventory slot}
     *
     * @throws IllegalArgumentException when the index is not an inventory slot
     */
    public static int containerSlot(int inventoryIndex) {
        // Hotbar: the inventory's first nine are the menu's last nine of the player's half.
        if (inventoryIndex >= 0 && inventoryIndex < 9) return 36 + inventoryIndex;
        // Main inventory: the same number in both, which is the one coincidence of the mapping.
        if (inventoryIndex >= 9 && inventoryIndex < 36) return inventoryIndex;
        // Armour: stored boots first and numbered helmet first, so the four are reversed.
        if (inventoryIndex >= 36 && inventoryIndex < 40) return 44 - inventoryIndex;
        // Off hand: after the hotbar, which is the last slot of the player's half.
        if (inventoryIndex == 40) return 45;
        throw new IllegalArgumentException("not an inventory slot: " + inventoryIndex);
    }
}
