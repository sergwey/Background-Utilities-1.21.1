package net.xlebupaksa.backutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The delete tool: what an operator holds to see the effects that are already placed and to take
 * them away again.
 *
 * <p>An item of its own rather than a mode of the effect tool, because the two are held for
 * different reasons. The effect tool is configured — its settings decide what it would place and
 * where — where this one has nothing to configure: it acts on what is there, and the same item is
 * therefore the same tool in every operator's hand. A mode of the effect tool would have put the
 * view of what is placed behind a tool that has chosen a mode and an effect path, which is exactly
 * the state a player who wants to clean up afterwards is least likely to be in.
 *
 * <p>The stack itself carries nothing, for the same reason: there is no setting on this tool that
 * two operators could disagree about, and nothing about it has to survive being given away. What it
 * does is worked out where it is used — the pink boxes by the client drawing them, the deletion by
 * the server — and the item exists to be recognised by both.
 *
 * <p>Registered like the effect tool, and found the same way: in the mod's own creative tab. It has a
 * class of its own rather than being a mode of the effect tool, because it is not a variety of that
 * tool — it has nothing to configure and acts on what is already there.
 */
public class DeleteToolItem extends Item {

    public DeleteToolItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * What the tool is for, since nothing on it says so.
     *
     * <p>The gesture has to be on the item rather than in the spec: a player handed a tool has no
     * other way to learn that alt-clicking removes their own effects, and the difference between a
     * click and an alt-click is not one anybody would find by accident.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        lines.add(Component.translatable("backutils.delete_tool.tooltip.hint")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("backutils.delete_tool.tooltip.alt")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
