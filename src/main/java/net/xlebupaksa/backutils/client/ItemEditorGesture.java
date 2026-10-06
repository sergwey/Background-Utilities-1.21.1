package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.xlebupaksa.backutils.ui.ItemEditorScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Space over an item in any inventory opens the item editor for it.
 *
 * <p>The key bindings are the ones the mod this feature is modelled on uses: space to edit the item
 * under the pointer. That mod's shift and space opens a list of its own factories first; they are not
 * copied, because the factories this mod has are the editor's own three sections and a list of them
 * would be a list of one screen. Both keys therefore open the editor rather than one of them opening a
 * menu that lists what the other one does.
 *
 * <p>A press rather than a hold, which is how that mod's key works and is the opposite of the effect
 * tool's: a tool is configured rarely and a wand that vanished on a stray keystroke would be worse
 * than one that took a moment, while an item is edited constantly and waiting for a hold would be felt
 * on every item.
 *
 * <p><b>Only an inventory.</b> The event fires for every screen, and this screen is not one of the
 * container's: an editor that re-opened itself when the operator typed a space in a name field would
 * make names impossible to write.
 */
@OnlyIn(Dist.CLIENT)
public final class ItemEditorGesture {

    private ItemEditorGesture() {}

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() != GLFW.GLFW_KEY_SPACE) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> container)) return;

        Slot hovered = container.getSlotUnderMouse();
        // An empty slot is not an item to edit, and a slot the server cannot be told about is passed
        // on as nothing: EffectToolSlot decides that, and the editor closes rather than pretending.
        if (hovered == null || !hovered.hasItem()) return;
        if (!operator(minecraft)) return;

        ItemEditorScreen.open(EffectToolSlot.of(minecraft, hovered));
        // Taken, so whatever else listens for the key does not also act on it.
        event.setCanceled(true);
    }

    /**
     * {@return true when this client is allowed to edit items}
     *
     * <p>Asked of the command tree rather than of the player, because a client is not told its own
     * permission level and the tree is: this mod's root command carries a level-two requirement, and
     * brigadier sends a client only the commands it may run. So a client that can see the root is a
     * client whose player is an operator — and one that cannot never opens a screen whose every button
     * the server would refuse.
     *
     * <p>The server's own gate is the real one; this only decides whether to offer the screen.
     */
    private static boolean operator(Minecraft minecraft) {
        if (minecraft.getConnection() == null) return false;
        return minecraft.getConnection().getCommands().getRoot().getChild("backutils") != null;
    }
}
