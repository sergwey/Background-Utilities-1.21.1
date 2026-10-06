package net.xlebupaksa.backutils.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The configurable effect tool.
 *
 * <p>It arrives blank, from the mod's own creative tab, and is configured through its own screen,
 * opened by holding W over it in any inventory. Everything it is set to lives in one data component
 * on the stack, so two tools can be set up differently and a world only has to carry the item to
 * carry the settings.
 *
 * <p>Holding W over it in an inventory opens the screen that edits it; the preview of where the
 * effect would land is drawn by {@code EffectToolPreviewRenderer}, and using the tool places it,
 * both of which are client-only. The stack itself carries nothing but the settings: what a click
 * means is worked out on the client that made it and settled by the server from this component.
 */
public class EffectToolItem extends Item {

    public EffectToolItem(Item.Properties properties) {
        super(properties);
    }

    /** {@return the settings on this stack}, or the blank ones when no component is present */
    public static EffectToolConfig configOf(ItemStack stack) {
        EffectToolConfig config = stack.get(ModDataComponents.EFFECT_TOOL_CONFIG);
        return config == null ? EffectToolConfig.BLANK : config;
    }

    /**
     * Writes settings onto a stack, sanitised: the border the whole mod goes through, so a value
     * typed into the screen cannot reach the item out of range.
     *
     * <p>The server holds the stack a player is carrying, so it is the server that calls this for a
     * screen's edit, from {@code EffectToolNetwork}. A screen writes nothing itself: the stack it
     * holds is the client's copy of the server's, and the next sync of that slot takes a change made
     * there away again.
     */
    public static void setConfig(ItemStack stack, EffectToolConfig config) {
        stack.set(ModDataComponents.EFFECT_TOOL_CONFIG,
                config == null ? EffectToolConfig.BLANK : config.sanitised());
    }

    /**
     * The settings as a tooltip, and the one line that says how the screen is reached.
     *
     * <p>An operator who cannot recall what a tool was set to otherwise has to open the screen and
     * read it there. The hint is on every tool, configured or not: the gesture is not something
     * anybody could guess, and a tool that is already set up is the one whose screen is most worth
     * finding again.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines,
                                TooltipFlag flag) {
        EffectToolConfig config = configOf(stack);
        if (config.isBlank()) {
            lines.add(Component.translatable("backutils.effect_tool.tooltip.blank")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("backutils.effect_tool.tooltip.mode",
                    Component.translatable(config.mode().key())).withStyle(ChatFormatting.GRAY));
            if (!config.effectPath().isEmpty()) {
                lines.add(Component.literal(config.effectPath())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        lines.add(Component.translatable("backutils.effect_tool.tooltip.hold")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
