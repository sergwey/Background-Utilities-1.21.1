package net.xlebupaksa.backutils.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.xlebupaksa.backutils.item.EffectToolItem;
import net.xlebupaksa.backutils.ui.EffectToolScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Holds W on an effect tool in any inventory to open its configuration screen.
 *
 * <p>The gesture lives here rather than in a key binding: a binding would be a control the player
 * has to find and set, and it would fire wherever the player happened to be looking. What the mod
 * wants is the Create-mod habit of pondering an item in a menu, which is a hover and a key held
 * down — and that needs no dependency on Create, only on the events below.
 *
 * <p>The press is taken from {@link InputEvent.Key} and the duration from the client tick, because
 * {@code Screen#keyPressed} is called once per press and cannot say whether the key is still down.
 * A release, a click, or the pointer leaving the slot gives up on the gesture, so the screen opens
 * only for a deliberate hold on one tool.
 *
 * <p>The hold is shown on the tool's own tooltip, which is where the item is already being read: a
 * bar of pipes fills towards the end of the hold, with the tool's id under it. Nothing is drawn over
 * the slot, because the tooltip is already beside it and a second thing saying the same would be one
 * too many; the tooltip is also the only one of the two that can be read without looking away from
 * the pointer.
 *
 * <p>What the screen is given is an {@link EffectToolSlot.Address} rather than a slot index, because
 * the index alone means different things in the client's menu and the server's; a tool the server
 * cannot be told about is passed on as nothing, and the screen says why.
 */
@OnlyIn(Dist.CLIENT)
public final class EffectToolGesture {

    /**
     * How long W has to be held.
     *
     * <p>Four tenths of a second: long enough that it cannot happen on the way to another key, and
     * short enough that the screen feels like it opened on the press rather than after it. The spec
     * asks for "a few seconds"; that was tried and was too long to sit through, and this is the one
     * number to change if it wants to be shorter or longer again.
     */
    private static final long HOLD_NANOS = 400_000_000L;

    /**
     * How many pipes the bar on the tooltip is, filled or not.
     *
     * <p>Forty of them is about eighty pixels, which fits inside the width a tooltip line is allowed
     * without being wrapped onto a second one — a bar that wrapped would be two bars.
     */
    private static final int BAR_PIPES = 40;

    /** The slot the tool was hovered in when W went down, or -1 when no gesture is running. */
    private static int slot = -1;
    private static long startedAt;

    private EffectToolGesture() {}

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onKey(InputEvent.Key event) {
        if (event.getKey() != GLFW.GLFW_KEY_W) return;

        if (event.getAction() == GLFW.GLFW_RELEASE) {
            abandon();
            return;
        }
        // Ctrl and the rest are somebody else's shortcut, and Alt is used by the delete tool later.
        if (event.getModifiers() != 0) return;

        // Every press counts, including the auto-repeat a held key sends, so the timer follows the
        // slot rather than the event: restarting it on every repeat would mean it never elapsed.
        Slot hovered = hoveredToolSlot();
        if (hovered == null) {
            abandon();
            return;
        }
        if (hovered.index == slot) return;

        slot = hovered.index;
        startedAt = System.nanoTime();
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onMouseDown(ScreenEvent.MouseButtonPressed.Pre event) {
        abandon();
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onTick(ClientTickEvent.Post event) {
        if (slot < 0) return;

        // The key, the screen and the pointer are all checked every tick: any of them can change
        // while the other two stay as they were, and each of those changes means "not this tool".
        if (!InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(),
                GLFW.GLFW_KEY_W)) {
            abandon();
            return;
        }
        Slot hovered = hoveredToolSlot();
        if (hovered == null || hovered.index != slot) {
            abandon();
            return;
        }
        if (System.nanoTime() - startedAt < HOLD_NANOS) return;

        abandon();
        EffectToolScreen.open(EffectToolSlot.of(Minecraft.getInstance(), hovered), hovered.getItem());
    }

    /** Draws the hold's own lines on the tool's tooltip: the bar that fills, and the item's id. */
    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onTooltip(RenderTooltipEvent.GatherComponents event) {
        if (slot < 0) return;
        if (!(event.getItemStack().getItem() instanceof EffectToolItem)) return;

        // The tooltip being gathered is the hovered slot's and the hold is on the hovered slot, so
        // the two are the same tool; the index is checked anyway, because a hold that outlived its
        // slot must not draw its progress on somebody else's tooltip.
        Slot hovered = hoveredToolSlot();
        if (hovered == null || hovered.index != slot) return;

        event.getTooltipElements().add(Either.left(barOf(progress())));
        event.getTooltipElements().add(Either.left(Component
                .literal(idOf(event.getItemStack())).withStyle(ChatFormatting.DARK_GRAY)));
    }

    /**
     * {@return how far the hold has got}, from nothing to done
     *
     * <p>Read when it is drawn rather than kept, so the bar is as far along as the hold actually is:
     * a tooltip is gathered per frame and the key can be released between two of them.
     */
    private static float progress() {
        return Math.min(1.0F, (System.nanoTime() - startedAt) / (float) HOLD_NANOS);
    }

    /**
     * {@return the bar of pipes that fills as the hold runs}
     *
     * <p>Pipes rather than a drawn rectangle, which is what the gesture was asked for: a row of them
     * is the plainest thing a tooltip line can hold, and it is drawn by the same font as everything
     * around it, so it cannot sit at the wrong height or the wrong scale.
     *
     * <p>The whole bar is written every time, done pipes in one colour and the rest in another, so
     * that the tooltip is the same width from the first frame to the last. A bar that grew by adding
     * pipes would make the box it is in widen with it, and the tool would appear to jump.
     */
    private static Component barOf(float progress) {
        int done = Math.round(BAR_PIPES * progress);
        return Component.empty()
                .append(Component.literal("|".repeat(done)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("|".repeat(BAR_PIPES - done))
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** {@return the tool's own id}, which is what the tooltip names it by under the bar */
    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /**
     * {@return the slot the pointer is over when it holds an effect tool}, or null for any other
     * case, the pointer over nothing and no inventory open included.
     */
    private static Slot hoveredToolSlot() {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return null;

        Screen screen = minecraft.screen;
        if (!(screen instanceof AbstractContainerScreen<?> container)) return null;

        Slot hovered = container.getSlotUnderMouse();
        if (hovered == null || !hovered.hasItem()) return null;

        // An effect tool anywhere the player can see it, the creative list included. What that list
        // allows is not a hover but an address the server can act on, which EffectToolSlot decides.
        return hovered.getItem().getItem() instanceof EffectToolItem ? hovered : null;
    }

    /** Gives up on the hold the player is making, if any. */
    private static void abandon() {
        slot = -1;
    }
}
