package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.xlebupaksa.backutils.data.ModAttachments;
import net.xlebupaksa.backutils.data.ProfileOptions;
import net.xlebupaksa.backutils.data.ProfileSnapshot;
import net.xlebupaksa.backutils.network.ProfileEditPayload;
import net.xlebupaksa.backutils.network.ProfileNetwork;

/**
 * The profile pickers shown at the bottom of the chat screen.
 *
 * <p>Everything it displays comes from the two synced attachments. It must never touch
 * {@code BackUtils.data()}: those databases live on the server and do not exist on a client
 * connected to a dedicated server.
 *
 * <p>Both pickers are offered only to players who can use them, matching the permission level the
 * {@code /backutils} commands require.
 */
public final class ChatScreenOverlay {

    private ModularUI modularUI;
    private UpSelector<String> nameSelector;
    private UpSelector<String> chatSelector;

    private ProfileOptions lastOptions;
    private ProfileSnapshot lastSnapshot;

    private static final int SELECTOR_WIDTH  = 60;
    private static final int SELECTOR_HEIGHT = 12;

    /** {@return true when there is something to show} */
    public boolean build(ChatScreen screen) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;

        ProfileOptions options = options(player);
        // A name profile is what everyone else reads you as, so that picker stays with staff; a
        // chat profile is the player's own formatting, but a picker offering nothing but "default"
        // is noise, so it appears once there is a choice to make.
        boolean showNames = options.canManageNames();
        boolean showChat = options.canManageChat() && options.chatProfiles().size() > 1;
        if (!showNames && !showChat) return false;

        UIElement root = new UIElement();
        root.layout(l -> l
                .widthPercent(100)
                .heightPercent(100)
                .paddingAll(4)
                .paddingBottom(15)
                .flexDirection(FlexDirection.COLUMN)
                .justifyContent(AlignContent.FLEX_END));

        UIElement row = new UIElement();
        row.layout(l -> l
                .widthPercent(100)
                .heightAuto()
                .flexDirection(FlexDirection.ROW)
                .gapAll(6));
        root.addChild(row);

        if (showNames) {
            nameSelector = new UpSelector<>();
            nameSelector.layout(l -> l.width(SELECTOR_WIDTH).height(SELECTOR_HEIGHT));
            nameSelector.setOnValueChanged(name ->
                    sendCommand("profile name use @s " + commandArgument(name)));
            row.addChild(nameSelector);
        } else {
            nameSelector = null;
        }

        if (showChat) {
            chatSelector = new UpSelector<>();
            chatSelector.layout(l -> l.width(SELECTOR_WIDTH).height(SELECTOR_HEIGHT));
            // Over a payload rather than the command: /profile is gated at permission level 2,
            // while picking a chat profile is not a staff action. The server reads the sender
            // from the connection.
            chatSelector.setOnValueChanged(chat ->
                    ProfileNetwork.send(ProfileEditPayload.use(chat)));
            row.addChild(chatSelector);
        } else {
            chatSelector = null;
        }

        ProfileSnapshot snapshot = snapshot(player);
        lastOptions = options;
        lastSnapshot = snapshot;
        refresh(options, snapshot);

        modularUI = ModularUI.of(UI.of(root));
        modularUI.setScreenAndInit(screen);
        return true;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (modularUI == null) return;
        syncWithServerState();
        modularUI.getWidget().render(g, mouseX, mouseY, partialTick);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (modularUI == null) return false;
        return modularUI.getWidget().mouseClicked(mx, my, button);
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (modularUI == null) return false;
        return modularUI.getWidget().mouseReleased(mx, my, button);
    }

    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (modularUI == null) return false;
        return modularUI.getWidget().mouseScrolled(mx, my, scrollX, scrollY);
    }

    public void mouseMoved(double mx, double my) {
        if (modularUI == null) return;
        modularUI.getWidget().mouseMoved(mx, my);
    }

    public void close() {
        modularUI = null;
        nameSelector = null;
        chatSelector = null;
        lastOptions = null;
        lastSnapshot = null;
    }

    /**
     * Re-reads the synced {@link ProfileOptions} and snapshot, so the lists cannot go stale while
     * this screen is open.
     */
    private void syncWithServerState() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        ProfileOptions options = options(player);
        if (!options.canManageNames() && !options.canManageChat()) return;
        ProfileSnapshot snapshot = snapshot(player);

        if (options.equals(lastOptions) && snapshot.equals(lastSnapshot)) return;
        lastOptions = options;
        lastSnapshot = snapshot;
        refresh(options, snapshot);
    }

    private void refresh(ProfileOptions options, ProfileSnapshot snapshot) {
        if (nameSelector != null) {
            nameSelector.setCandidates(options.nameProfiles());
            nameSelector.setSelected(
                    snapshot.nameProfileId() == 0L ? ProfileOptions.DEFAULT_PROFILE : snapshot.nameProfileName(),
                    false);
        }
        if (chatSelector != null) {
            chatSelector.setCandidates(options.chatProfiles());
            chatSelector.setSelected(
                    snapshot.chatProfileId() == 0L ? ProfileOptions.DEFAULT_PROFILE : snapshot.chatProfileName(),
                    false);
        }
    }

    private static ProfileOptions options(LocalPlayer player) {
        // Registered with a default, so the attachment never answers null.
        return player.getData(ModAttachments.OPTIONS.get());
    }

    private static ProfileSnapshot snapshot(LocalPlayer player) {
        return player.getData(ModAttachments.PROFILE.get());
    }

    private static void sendCommand(String command) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.connection.sendCommand(command);
    }

    /** Quotes a profile name for Brigadier when it contains spaces, quotes or backslashes. */
    private static String commandArgument(String value) {
        if (value == null) return "\"\"";
        if (value.indexOf(' ') < 0 && value.indexOf('"') < 0 && value.indexOf('\\') < 0) return value;
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
