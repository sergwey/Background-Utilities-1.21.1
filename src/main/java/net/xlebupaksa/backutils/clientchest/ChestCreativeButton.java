package net.xlebupaksa.backutils.clientchest;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The chest's button on the creative inventory screen: the bottom left corner of the screen, drawn as
 * the chest it is.
 *
 * <p>The corner rather than beside the panel, because it is the player's library and not part of the
 * creative menu: it is in the same place whatever screen is open, and the corner is the one place on
 * screen that nothing else claims.
 *
 * <p>A chest rather than a word. A chest is recognised at a glance, and a button this size is too
 * small for a label — which is what the label was doing before, drawn across the button beside it.
 *
 * <p>Only on the creative screen, because the transfer between a page and the player's inventory is a
 * creative-mode act: the game drops creative slot packs from anybody else. Outside creative the
 * command still opens the library, read-only, which is what the tinted slots inside it are explaining.
 */
public final class ChestCreativeButton {

    /** How far the button sits from the corner of the screen. */
    private static final int MARGIN = 4;

    /** The button's size: half again a slot, so the chest in it reads at a glance. */
    private static final int SIZE = 30;

    /** The item drawn on it. Its own stack, never modified: only its picture is wanted. */
    private static final ItemStack ICON = new ItemStack(Items.CHEST, 1);

    /** Half again the size an item is drawn at in a slot, which is half again the button's own size. */
    private static final float ICON_SCALE = 1.5F;

    private ChestCreativeButton() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) return;
        // The bottom left of the screen rather than of the panel: the panel is centred, and the corner
        // is where a button that belongs to the player rather than to the menu belongs.
        event.addListener(new ChestButton(MARGIN, screen.height - MARGIN - SIZE));
    }

    /** A vanilla-looking button with an item in it, and the tooltip that says what the item is for. */
    private static final class ChestButton extends Button {

        private ChestButton(int x, int y) {
            super(x, y, SIZE, SIZE, Component.empty(), button -> ChestScreen.open(),
                    Button.DEFAULT_NARRATION);
            this.setTooltip(Tooltip.create(
                    Component.translatable("backutils.client_chest.button.tooltip")));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            // Half again, and centred: an item draws itself at sixteen pixels whatever pose it is
            // given, so the picture is scaled rather than the item.
            float drawn = 16 * ICON_SCALE;
            graphics.pose().pushPose();
            graphics.pose().translate(this.getX() + (SIZE - drawn) / 2,
                    this.getY() + (SIZE - drawn) / 2, 0.0F);
            graphics.pose().scale(ICON_SCALE, ICON_SCALE, 1.0F);
            graphics.renderItem(ICON, 0, 0);
            graphics.pose().popPose();
        }
    }
}
