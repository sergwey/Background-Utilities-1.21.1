package net.xlebupaksa.backutils.item;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Hides the enchantment lines of an item without taking its enchantments away.
 *
 * <p>{@code minecraft:hide_additional_tooltip} does not do this on 1.21.1: it only skips the
 * item's own {@code appendHoverText}, and {@link ItemStack#getTooltipLines} appends
 * {@code ENCHANTMENTS} afterwards regardless. Hiding an enchantment is therefore a tooltip job, not
 * a component one. The marker lives in {@code minecraft:custom_data} so the enchantments remain
 * active and the filter survives a save; the client removes the lines when the tooltip is built.
 */
public final class HiddenEnchantments {

    private static final String KEY = "backutils:hide_enchantments";

    private HiddenEnchantments() {}

    /** {@return true when this stack asked for its enchantment lines to be hidden} */
    public static boolean hidden(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(KEY);
    }

    /**
     * Writes or clears the marker, leaving every other custom-data key alone.
     *
     * <p>This is an edit of the item, so it uses the same copy-on-write update as any other custom
     * data: a shared tag must not be changed in place.
     */
    public static void set(ItemStack stack, boolean hide) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (hide) tag.putBoolean(KEY, true);
            else tag.remove(KEY);
        });
    }

    /** The client-side tooltip filter; registered only on the physical client. */
    public static void onTooltip(ItemTooltipEvent event) {
        removeEnchantmentLines(event.getItemStack(), event.getToolTip());
    }

    static void removeEnchantmentLines(ItemStack stack, List<Component> lines) {
        if (!hidden(stack)) return;

        // The tooltip's enchantment lines are exactly the full names ItemEnchantments appends. A
        // plain string comparison ignores the style the line carries, which is what we want: the
        // name is the stable part across the rarity and level formatting. Stored enchantments are
        // included because an enchanted book keeps its lines there rather than in ENCHANTMENTS.
        Set<String> names = new HashSet<>();
        Set<Component> components = new HashSet<>();
        collectNames(stack.get(DataComponents.ENCHANTMENTS), names, components);
        collectNames(stack.get(DataComponents.STORED_ENCHANTMENTS), names, components);
        if (names.isEmpty()) return;
        lines.removeIf(line -> components.contains(line) || names.contains(line.getString()));
    }

    private static void collectNames(ItemEnchantments enchantments, Set<String> names,
            Set<Component> components) {
        if (enchantments == null || enchantments.isEmpty()) return;
        for (var entry : enchantments.entrySet()) {
            Component name = Enchantment.getFullname(entry.getKey(), entry.getIntValue());
            names.add(name.getString());
            components.add(name);
        }
    }
}
