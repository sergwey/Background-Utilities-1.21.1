package net.xlebupaksa.backutils.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * The anvil level cost an item editor wrote onto an item.
 *
 * <p>Vanilla computes the cost from the enchantments and the prior-work penalty, and there is no
 * component that replaces that number. The event is the one point where it can be changed, so the
 * marker is read here. The left-hand item wins when both sides carry one, because it is the item the
 * output is built from.
 */
public final class AnvilCosts {

    private AnvilCosts() {}

    public static void onUpdate(AnvilUpdateEvent event) {
        int cost = costOf(event.getLeft());
        if (cost < 0) cost = costOf(event.getRight());
        if (cost >= 0) event.setCost(cost);
    }

    private static int costOf(ItemStack stack) {
        return AdvancedItemProperties.anvilCostOf(stack);
    }
}
