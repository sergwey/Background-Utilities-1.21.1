package net.xlebupaksa.backutils.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Finding the slot a message is about.
 *
 * <p><b>The id alone does not say.</b> A player in creative opens their own inventory as the creative
 * screen, whose menu is built on the client and never exists on the server — and it takes the same id
 * as the player's own inventory menu, which does. A slot index from one means something else in the
 * other, so the two are told apart by name and this is the only place that decides it.
 *
 * <p>Here rather than in either feature that asks, because the question is the same one for both and
 * the answer is exactly the kind that goes wrong quietly: a second copy of this rule would eventually
 * name a different slot than this one does, and the item that was edited would be the wrong item.
 */
public final class AddressedSlots {

    private AddressedSlots() {}

    /**
     * {@return the slot an address means, or null when it means nothing}
     *
     * <p>The player's own inventory menu is the one address that always works, because both sides
     * always have it. The menu the player has open is only acted on when its id agrees, which is what
     * stops a message about one screen being applied to another that has since been opened.
     */
    public static Slot of(ServerPlayer player, EffectToolConfigPayload.Where where, int containerId,
                          int slot) {
        AbstractContainerMenu menu = where == EffectToolConfigPayload.Where.PLAYER
                ? player.inventoryMenu
                : player.containerMenu;
        if (menu == null || menu.containerId != containerId) return null;
        if (slot < 0 || slot >= menu.slots.size()) return null;
        return menu.getSlot(slot);
    }
}
