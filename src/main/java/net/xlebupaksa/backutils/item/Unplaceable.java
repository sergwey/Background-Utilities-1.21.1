package net.xlebupaksa.backutils.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * A block item that refuses to be placed.
 *
 * <p>The marker lives in {@code minecraft:custom_data}, like the hidden-enchantment marker, because
 * it has to survive a save and has to travel with the stack when it is copied. The refusal itself
 * happens earlier, in {@code BlockItem.place}, so the client never predicts a block it will not be
 * allowed to keep; the server-side {@link BlockEvent.EntityPlaceEvent} check remains as a fallback
 * for a placement that reaches it by another route.
 *
 * <p>Both hands are checked rather than trying to work out which one the game used: the placement
 * event does not carry the stack, and refusing a marked stack in the other hand is safer than
 * letting the marked one be placed. The flag does nothing on an item that is not a block.
 */
public final class Unplaceable {

    public static final String KEY = "backutils:cannot_be_placed";

    private Unplaceable() {}

    /** {@return true when this stack asked not to be placed} */
    public static boolean is(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(KEY);
    }

    /** Writes or clears the marker, leaving every other custom-data key alone. */
    public static void set(ItemStack stack, boolean unplaceable) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (unplaceable) tag.putBoolean(KEY, true);
            else tag.remove(KEY);
        });
    }

    /** The placement gate; registered on the game bus by {@code BackUtils}. */
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) return;
        if (is(living.getMainHandItem()) || is(living.getOffhandItem())) {
            event.setCanceled(true);
        }
    }
}
