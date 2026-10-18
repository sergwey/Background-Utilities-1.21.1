package net.xlebupaksa.backutils.clientchest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Makes the server agree with the player's inventory after the chest screen has changed it.
 *
 * <p>Moving an item out of a page means putting it in the client's inventory, and the client's
 * inventory is not the server's: nothing has been told. Creative mode already has the packet for
 * exactly this — "this is what my slot now holds" — so the chest sends one of those for every slot the
 * screen has changed since it was last sent.
 *
 * <p>A snapshot of what the server was last told, rather than a log of what was clicked: a click
 * changes two or three slots through pick-up, place, swap and merge, and comparing the whole inventory
 * against the last thing sent catches every one of them without the screen having to know which
 * happened. The comparison is the one vanilla uses to decide two stacks are the same, count and
 * components included.
 *
 * <p>Deliberately no packet listeners. Reading the server's own updates would keep the snapshot
 * exactly current, but the only thing a stale snapshot costs is a slot being sent again with the
 * value the client already has — which is harmless, because the client's inventory is the side that
 * decides what it holds in creative. Nothing here is sent at all outside creative.
 */
public final class ChestSync {

    /** What the server was last told each inventory slot holds. */
    private final ItemStack[] known = new ItemStack[CreativeSlots.INVENTORY_SLOTS];

    public ChestSync(Inventory inventory) {
        this.snapshot(inventory);
    }

    /** Takes the inventory as it stands now for what the server already knows. */
    private void snapshot(Inventory inventory) {
        for (int slot = 0; slot < CreativeSlots.INVENTORY_SLOTS; slot++) {
            this.known[slot] = inventory.getItem(slot).copy();
        }
    }

    /**
     * Sends the slots that have changed since the last time, and remembers the new values.
     *
     * <p>Nothing at all happens outside creative: the server ignores the packets from a player who is
     * not in creative, so sending them would be noise that looks like it works.
     */
    public void push(Player player) {
        // A teardown can run after the player is gone, and there is nothing to push then.
        if (player == null || !player.isCreative()) return;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < CreativeSlots.INVENTORY_SLOTS; slot++) {
            ItemStack held = inventory.getItem(slot);
            if (ItemStack.matches(held, this.known[slot])) continue;
            connection.send(new ServerboundSetCreativeModeSlotPacket(
                    CreativeSlots.containerSlot(slot), held.copy()));
            this.known[slot] = held.copy();
        }
    }
}
