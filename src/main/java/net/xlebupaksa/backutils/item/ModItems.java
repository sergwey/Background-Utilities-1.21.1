package net.xlebupaksa.backutils.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.xlebupaksa.backutils.data.ModAttachments;

/**
 * The items this mod adds.
 *
 * <p>The effect tool stacks to one: a stack of tools carrying one configuration would be a stack of
 * items that disagree about what they do. The delete tool stacks to one for the plainer reason that
 * it is a tool — a pile of them does nothing a single one does not, and a tool is not something a
 * player counts out.
 *
 * <p>Registered the way the command argument is, off {@link RegisterEvent}, rather than through a
 * {@code DeferredRegister} the way {@link ModAttachments} does it: the mod already has that hook
 * for the registries it adds one entry to, and a second mechanism for one item would hide which bus
 * the entry belongs to.
 */
public final class ModItems {

    public static final EffectToolItem EFFECT_TOOL = new EffectToolItem(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON)
            .component(ModDataComponents.EFFECT_TOOL_CONFIG, EffectToolConfig.BLANK));

    /**
     * The delete tool, which carries no component of its own.
     *
     * <p>Built rather than registered through a deferred holder, exactly as the effect tool is: the
     * registry entry is made in {@code BackUtilsRegistries.onRegister}, from this one instance, so
     * the item a stack holds and the item the registry knows are the same object.
     */
    public static final DeleteToolItem DELETE_TOOL = new DeleteToolItem(new Item.Properties()
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON));

    private ModItems() {}
}
