package net.xlebupaksa.backutils.item;

import net.minecraft.world.item.ItemStack;

/**
 * Recognises the two operator tools wherever one of them is held.
 *
 * <p>One answer in one place because three renderer hooks ask the same question — the held item, the
 * arm pose that holds it, and the swing that pretends to use it. Answered separately, one of them
 * eventually disagrees about what counts as a tool, and the disagreement is visible in the game as a
 * hand held round nothing.
 */
public final class ToolItems {

    private ToolItems() {
    }

    /** Whether {@code stack} is one of the tools that is kept out of other people's view. */
    public static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof EffectToolItem || stack.getItem() instanceof DeleteToolItem;
    }
}
