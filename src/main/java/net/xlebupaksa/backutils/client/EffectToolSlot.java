package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.network.EffectToolConfigPayload.Where;

/**
 * Where a hovered tool is, in the terms the server can act on.
 *
 * <p>A slot index on its own says nothing: it means one thing in the menu the client has open and
 * another in the menu the server does. The two are the same menu for every screen the server opened
 * — a chest, a machine, the player's own inventory in survival — but not for the creative screen,
 * which builds a menu of its own that never exists on the server and takes the same id as the
 * player's inventory menu. A tool hovered there was addressed against the wrong menu and refused,
 * which is what this class exists to make impossible.
 *
 * <p>So a tool in the player's own inventory is addressed through the menu both sides always have,
 * and only a tool in something else is addressed through the menu the player has open. The creative
 * screen's item list is neither: its slots hold the tab's own display items rather than anything the
 * server keeps, so there is nothing there to name and nothing to write.
 */
@OnlyIn(Dist.CLIENT)
public final class EffectToolSlot {

    private EffectToolSlot() {}

    /**
     * One tool's place: which menu the index counts in, that menu's id, and the index itself.
     *
     * <p>The id travels with the index for the reason it always did: an index is only meaningful
     * against the container it was read from, and the server refuses an edit naming a menu the player
     * no longer has open.
     */
    public record Address(Where where, int containerId, int slot) {}

    /**
     * {@return the address of a hovered slot, or null when the server could not be told about it}
     *
     * <p>Null is an answer rather than a failure: the creative list is a catalogue, and a tool there
     * is a picture of one. A screen told nothing still has something useful to say about that.
     */
    public static Address of(Minecraft minecraft, Slot hovered) {
        Player player = minecraft.player;
        if (player == null || hovered == null) return null;

        Slot own = inventorySlot(player, hovered);
        if (own != null) {
            Address address = new Address(Where.PLAYER, player.inventoryMenu.containerId, own.index);
            reportIfEmpty(player, hovered, address);
            return address;
        }

        // Anything else can only be named through the menu the player has open, and the creative
        // screen's list is the one place that menu is not the server's.
        if (minecraft.screen instanceof CreativeModeInventoryScreen) return null;
        Address address = new Address(Where.OPEN_MENU, player.containerMenu.containerId, hovered.index);
        reportIfEmpty(player, hovered, address);
        return address;
    }

    /**
     * Says what an address was made of, when the stack it names is not the tool that was hovered.
     *
     * <p>A wrong address has no other symptom on this side: the screen opens with nothing in it, and
     * the server can only report that the slot it was given holds something else. Between them they do
     * not say whether the hovered slot was read wrongly, whether the two menus disagree about which
     * index means which slot, or whether the stack simply moved. These numbers do.
     */
    private static void reportIfEmpty(Player player, Slot hovered, Address address) {
        if (!stackAt(player, address).isEmpty()) return;
        BackUtils.LOGGER.info("[effecttool] addressed an empty slot: hovered container={} containerSlot={} menuIndex={} "
                        + "holding={}, resolved to where={} container={} slot={}, which holds={}",
                hovered.container.getClass().getSimpleName(), hovered.getContainerSlot(), hovered.index,
                hovered.getItem().getItem(), address.where(), address.containerId(), address.slot(),
                stackAt(player, address).getItem());
    }

    /** {@return the stack an address names on this client}, or empty when it names none */
    public static ItemStack stackAt(Player player, Address address) {
        if (address == null || player == null) return ItemStack.EMPTY;

        AbstractContainerMenu menu = address.where() == Where.PLAYER
                ? player.inventoryMenu : player.containerMenu;
        if (menu == null || menu.containerId != address.containerId()
                || address.slot() < 0 || address.slot() >= menu.slots.size()) {
            return ItemStack.EMPTY;
        }
        return menu.getSlot(address.slot()).getItem();
    }

    /**
     * {@return the slot of the player's own inventory menu that wraps the same stack}, or null
     *
     * <p>Found by what a slot holds rather than by where it sits: the creative screen's columns wrap
     * the player's inventory in slots of its own, at indices of its own, so the one thing the two
     * menus agree on is the container and the place in it. Those two together name one stack.
     */
    private static Slot inventorySlot(Player player, Slot hovered) {
        if (!(hovered.container instanceof Inventory)) return null;
        for (Slot candidate : player.inventoryMenu.slots) {
            if (candidate.container == hovered.container
                    && candidate.getContainerSlot() == hovered.getContainerSlot()) {
                return candidate;
            }
        }
        return null;
    }
}
