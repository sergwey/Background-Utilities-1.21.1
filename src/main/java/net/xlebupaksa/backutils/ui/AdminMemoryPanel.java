package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.data.MarkupWrap;
import net.xlebupaksa.backutils.network.AdminActionPayload;
import net.xlebupaksa.backutils.network.AdminLogBatchPayload;
import net.xlebupaksa.backutils.network.AdminMemoryCache;
import net.xlebupaksa.backutils.network.AdminMemoryPayload;
import net.xlebupaksa.backutils.network.AdminNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's memory, as an administrator sees it: every line that player has, whether it is still
 * shown to them or has been withheld.
 *
 * <p>This is the administrator's answer to "what does this player remember", which is the access list
 * and not the visibility rule: a line withheld from the player is still a line they witnessed, and is
 * exactly the line an administrator needs to find to give it back. The marker in front of a row and
 * the faded text say whether the player can see it now.
 *
 * <p>Rows select the way every list in this mod now does — a click replaces the selection, ctrl
 * toggles, shift takes the range in the order drawn — and the buttons act on the selection. Copying is
 * ctrl+C and lights the copied rows, as it does in the client's own memory menu.
 */
@OnlyIn(Dist.CLIENT)
public final class AdminMemoryPanel {

    private static final int LINE_STEP = 10;
    /** The same height as the profile panel's strip, so the two tabs' first lines sit level. */
    private static final int SCOPE_HEIGHT = 16;
    private static final int FILTER_HEIGHT = 14;
    private static final int STATUS_HEIGHT = 10;
    private static final int BUTTON_HEIGHT = 14;
    private static final int GAP = 4;

    private static final long DELETE_CONFIRM_NANOS = 5_000_000_000L;
    private static final long DOUBLE_CLICK_NANOS = 400_000_000L;

    /** One drawn line, with what the copied blink needs. */
    private static final class Drawn {
        final AdminMemoryPayload.Row data;
        final UIElement element;
        volatile long copiedAt = -1L;

        Drawn(AdminMemoryPayload.Row data, UIElement element) {
            this.data = data;
            this.element = element;
        }

        boolean blinking() {
            return copiedAt >= 0L;
        }
    }

    private final UIElement root = new UIElement();
    private final Label scope = new Label();
    private final TextField filter = new TextField();
    private final ScrollerView list = new ScrollerView();
    private final Label status = new Label();
    /** One button for both directions: its caption says which way the click will go. */
    private final Button toggleHide = new Button();
    private final Button teleport = new Button();
    private final Button delete = new Button();

    private final MultiSelection selection = new MultiSelection();
    private final List<Drawn> drawn = new ArrayList<>();

    private String player = "";
    private String requested = "";
    private int builtRevision = -1;
    private long deleteArmedAt;
    private long lastClickId;
    private long lastClickAt;
    private int listWidth = 200;

    public AdminMemoryPanel() {
        build();
    }

    /** {@return the element to mount as this panel's content} */
    public UIElement root() {
        return root;
    }

    /**
     * Points the panel at one player, or at nobody.
     *
     * <p>The selection is dropped whenever the player changes: an id selected in one player's memory
     * means something else in another's, and an action applied to it would be about a line the
     * administrator is no longer looking at.
     */
    public void setPlayer(String name) {
        String wanted = name == null ? "" : name;
        if (wanted.equals(player) && !wanted.isEmpty()) return;

        player = wanted;
        selection.clear();
        builtRevision = -1;
        requested = "";
        deleteArmedAt = 0L;
        filter.setText("", false);
        if (!player.isEmpty()) ask();
        rebuild();
    }

    /** {@return true while the filter has the keyboard, so ctrl+C must not take the copy} */
    public boolean typing() {
        return filter.isFocused();
    }

    /** Sizes the panel to the room the player detail gives it. */
    public void layout(int width, int height) {
        int w = Math.max(80, width);
        int h = Math.max(60, height);
        listWidth = w;

        int listHeight = Math.max(20, h - SCOPE_HEIGHT - FILTER_HEIGHT - STATUS_HEIGHT
                - BUTTON_HEIGHT * 2 - GAP * 5);

        root.layout(l -> l.width(w).height(h).flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        scope.layout(l -> l.width(w).height(SCOPE_HEIGHT));
        filter.layout(l -> l.width(w).height(FILTER_HEIGHT));
        list.layout(l -> l.width(w).height(listHeight));
        status.layout(l -> l.width(w).height(STATUS_HEIGHT));
    }

    /** Brings the panel up to date with the server, and plays any copied-row blink. */
    public void sync() {
        if (player.isEmpty()) return;
        if (!AdminMemoryCache.holds(player)) {
            // Asked once per open or per action; a reply that never comes must not become a request
            // every frame.
            if (!player.equals(requested)) ask();
            return;
        }
        if (builtRevision < 0 || AdminMemoryCache.revision() != builtRevision) rebuild();
        animate();
    }

    /** Writes the captions again in the language the client is set to now. */
    public void relabel() {
        updateToggleLabel();
        teleport.setText(Text.of("backutils.admin.option.teleport.to.action"), false);
        delete.setText(Text.of(deleteArmedAt == 0L
                ? "backutils.admin.option.delete.record"
                : "backutils.admin.option.delete.confirm"), false);
        filter.textFieldStyle(s -> s.placeholder(
                Component.translatable("backutils.admin.memory.search")));
    }

    /**
     * Copies the selected lines, in the order they are drawn.
     *
     * <p>Only the drawn ones: a row hidden by the filter is not one the administrator is looking at,
     * and a copy that quietly included it would be a copy of something they cannot see. The note an
     * administrator may read goes with the line, in brackets, as it does in the action log's own copy.
     *
     * @return true when something was copied
     */
    public boolean copySelection() {
        if (typing() || selection.isEmpty()) return false;

        List<String> lines = new ArrayList<>();
        for (Drawn row : drawn) {
            if (!selection.contains(row.data.id())) continue;
            String text = MarkupUtil.strip(row.data.text());
            lines.add(row.data.note() == null || row.data.note().isBlank()
                    ? text : text + " (" + MarkupUtil.strip(row.data.note()) + ")");
        }
        if (lines.isEmpty()) return false;

        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", lines));
        long now = System.nanoTime();
        for (Drawn row : drawn) {
            if (selection.contains(row.data.id())) row.copiedAt = now;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private void build() {
        scope.setText("", false);
        scope.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));

        filter.setText("", false);
        filter.layout(l -> l.height(FILTER_HEIGHT));
        filter.setTextResponder(text -> rebuild());
        filter.textFieldStyle(s -> s.placeholder(
                Component.translatable("backutils.admin.memory.search")));

        list.scrollerStyle(s -> s
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .adaptiveWidth(false)
                .adaptiveHeight(false));
        list.viewContainer(v -> v.getLayout().gapAll(1));

        status.setText("", false);
        status.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));

        toggleHide.setText(Text.of("backutils.admin.memory.hide"), false);
        toggleHide.setOnClick(e -> toggleSelected());
        teleport.setText(Text.of("backutils.admin.option.teleport.to.action"), false);
        teleport.setOnClick(e -> teleportToAction());
        delete.setText(Text.of("backutils.admin.option.delete.record"), false);
        delete.setOnClick(e -> deleteSelected());

        UIElement hideRow = new UIElement();
        hideRow.layout(l -> l.widthPercent(100).height(BUTTON_HEIGHT)
                .flexDirection(FlexDirection.ROW).gapAll(GAP));
        toggleHide.layout(l -> l.flex(1).height(BUTTON_HEIGHT));
        hideRow.addChild(toggleHide);

        UIElement actionRow = new UIElement();
        actionRow.layout(l -> l.widthPercent(100).height(BUTTON_HEIGHT)
                .flexDirection(FlexDirection.ROW).gapAll(GAP));
        teleport.layout(l -> l.flex(1).height(BUTTON_HEIGHT));
        delete.layout(l -> l.flex(1).height(BUTTON_HEIGHT));
        actionRow.addChildren(teleport, delete);

        UIElement buttons = new UIElement();
        buttons.layout(l -> l.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        buttons.addChildren(hideRow, actionRow);

        root.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        root.addChildren(scope, filter, list, buttons, status);

        updateControls();
    }

    // ------------------------------------------------------------------
    // Following the server
    // ------------------------------------------------------------------

    private void ask() {
        if (player.isEmpty() || player.equals(requested)) return;
        requested = player;
        AdminNetwork.requestMemory(player);
    }

    /** Asks again after an action, which is the one case where the rows are known to have changed. */
    private void refresh() {
        if (player.isEmpty()) return;
        requested = player;
        AdminNetwork.requestMemory(player);
    }

    private void rebuild() {
        if (player.isEmpty()) return;

        if (!AdminMemoryCache.holds(player)) {
            // The cache can still be the previous player's. Drawing it under this name would show one
            // player's lines as another's, and the buttons would act on ids from that other memory.
            list.clearAllScrollViewChildren();
            drawn.clear();
            selection.clear();
            scope.setText("", false);
            list.addScrollViewChild(muted(Text.of("backutils.admin.memory.loading")));
            updateControls();
            return;
        }

        List<AdminMemoryPayload.Row> all = AdminMemoryCache.rows();
        List<Long> allIds = new ArrayList<>(all.size());
        for (AdminMemoryPayload.Row row : all) allIds.add(row.id());
        // Against every row, not the filtered ones: a filter hides a row from view, not from the
        // selection, and the buttons still act on what was selected.
        selection.retainAll(allIds);
        builtRevision = AdminMemoryCache.revision();

        list.clearAllScrollViewChildren();
        drawn.clear();

        if (all.isEmpty()) {
            list.addScrollViewChild(muted(Text.of("backutils.admin.memory.empty")));
            updateScope(0);
            updateControls();
            return;
        }

        String needle = filterText();
        int shown = 0;
        for (AdminMemoryPayload.Row row : all) {
            if (!needle.isEmpty() && !matches(row, needle)) continue;
            list.addScrollViewChild(buildRow(row));
            shown++;
        }
        if (shown == 0) {
            list.addScrollViewChild(muted(Text.of("backutils.admin.memory.no.match")));
        }
        updateScope(all.size());
        updateControls();
    }

    private String filterText() {
        String text = filter.getText();
        return text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean matches(AdminMemoryPayload.Row row, String needle) {
        return MarkupUtil.strip(row.text()).toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    private void updateScope(int count) {
        scope.setText(Text.of(count == 1
                ? "backutils.admin.memory.count"
                : "backutils.admin.memory.count.plural", count), false);
    }

    private void updateControls() {
        int count = selection.size();
        toggleHide.setActive(count > 0);
        teleport.setActive(count == 1);
        delete.setActive(count > 0);
        updateToggleLabel();
        status.setText(Text.of(count == 0
                ? "backutils.admin.memory.hint"
                : "backutils.admin.memory.selected", count), false);
    }

    /**
     * Writes the one hide button's caption: "show" when every selected line is withheld from this
     * player, "hide" otherwise.
     *
     * <p>The state is the per-player flag, not the global one: a line hidden from everyone has its own
     * marker and is not what this button moves. A mixed selection reads as hide, so one click puts all
     * of it away and the second brings all of it back.
     */
    private void updateToggleLabel() {
        toggleHide.setText(Text.of(allSelectedHidden()
                ? "backutils.admin.memory.show" : "backutils.admin.memory.hide"), false);
    }

    /** {@return true when every selected line carries this player's own withholding flag} */
    private boolean allSelectedHidden() {
        if (selection.isEmpty() || player.isEmpty()) return false;

        boolean any = false;
        for (AdminMemoryPayload.Row row : AdminMemoryCache.rows()) {
            if (!selection.contains(row.id())) continue;
            any = true;
            if (!row.hiddenFromPlayer()) return false;
        }
        return any;
    }

    // ------------------------------------------------------------------
    // Rows
    // ------------------------------------------------------------------

    private UIElement buildRow(AdminMemoryPayload.Row data) {
        boolean visible = !data.hiddenAll() && !data.hiddenFromPlayer();
        String body = MarkupUtil.withNote(data.text(), data.note());
        List<String> wrapped = MarkupWrap.wrapByWidth(body, Math.max(40, listWidth - 30),
                line -> Minecraft.getInstance().font.width(MarkupUtil.strip(line)));

        MarkupLabel label = new MarkupLabel(wrapped, LINE_STEP,
                visible ? AdminListStyle.PLAIN : AdminListStyle.MUTED);
        // The tick box says what the player can see now, which is the state the hide and show
        // buttons move; the marker says the line is withheld for a reason worth seeing.
        label.withPrefix(AdminListStyle.checkbox(visible), AdminListStyle.MARKER_GREY);
        if (!visible) {
            label.withPrefixSprite(AdminListStyle.HIDDEN_MARKER, AdminListStyle.HIDDEN_MARKER_SIZE);
        }

        UIElement element = new UIElement();
        element.layout(l -> l.widthPercent(100)
                .height(label.contentHeight() + 4)
                .paddingAll(2)
                .flexDirection(FlexDirection.ROW));
        element.style(s -> s.background(new ColorRectTexture(AdminListStyle.ROW_IDLE))
                .tooltips(Component.literal(data.createdAt())));
        element.addChild(label);

        Drawn row = new Drawn(data, element);
        drawn.add(row);
        paint(row);

        element.addEventListener(UIEvents.MOUSE_ENTER, e -> {
            if (!row.blinking() && !selection.contains(data.id())) {
                element.style(s -> s.background(new ColorRectTexture(AdminListStyle.ROW_HOVER)));
            }
        }, true);
        element.addEventListener(UIEvents.MOUSE_LEAVE, e -> {
            if (!row.blinking()) paint(row);
        }, true);
        element.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button != 0) return;
            onClick(row);
        });
        return element;
    }

    private void onClick(Drawn row) {
        long now = System.nanoTime();
        boolean plain = !Screen.hasControlDown() && !Screen.hasShiftDown();
        boolean doubleClick = plain && row.data.id() == lastClickId
                && now - lastClickAt < DOUBLE_CLICK_NANOS;
        lastClickAt = now;
        lastClickId = row.data.id();

        if (doubleClick && row.data.dimension() != null && !row.data.dimension().isBlank()) {
            AdminNetwork.send(AdminActionPayload.of(
                    AdminActionPayload.Kind.TELEPORT_TO_ACTION, row.data.id()));
            return;
        }

        selection.click(row.data.id(), Screen.hasControlDown(), Screen.hasShiftDown(),
                drawnIds());
        // A new selection disarms the delete button, so a confirmation cannot land on another row.
        deleteArmedAt = 0L;
        delete.setText(Text.of("backutils.admin.option.delete.record"), false);

        for (Drawn other : drawn) paint(other);
        updateControls();
    }

    private List<Long> drawnIds() {
        List<Long> ids = new ArrayList<>(drawn.size());
        for (Drawn row : drawn) ids.add(row.data.id());
        return ids;
    }

    private void paint(Drawn row) {
        int colour = selection.contains(row.data.id())
                ? AdminListStyle.ROW_SELECTED : AdminListStyle.ROW_IDLE;
        row.element.style(s -> s.background(new ColorRectTexture(colour)));
    }

    private static Label muted(String text) {
        Label label = new Label();
        label.setText(text, false);
        label.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.MUTED).textShadow(false)
                .adaptiveWidth(true));
        return label;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /**
     * Hides every selected line from this player, or gives every one of them back, in one click.
     *
     * <p>One button rather than two: the list already says which state the selection is in beside
     * each line, so the button only has to say which way it will move it.
     */
    private void toggleSelected() {
        if (selection.isEmpty() || player.isEmpty()) return;

        AdminNetwork.sendBatch(new AdminLogBatchPayload(
                allSelectedHidden() ? AdminLogBatchPayload.Kind.UNHIDE_FROM_PLAYER
                        : AdminLogBatchPayload.Kind.HIDE_FROM_PLAYER,
                selection.ids(), player));
        // Re-asked rather than patched: the server decides what the hidden list now says.
        refresh();
    }

    private void teleportToAction() {
        long id = selection.primary();
        if (selection.size() != 1 || id <= 0L) return;
        AdminNetwork.send(AdminActionPayload.of(AdminActionPayload.Kind.TELEPORT_TO_ACTION, id));
    }

    private void deleteSelected() {
        if (selection.isEmpty()) return;

        long now = System.nanoTime();
        if (now - deleteArmedAt > DELETE_CONFIRM_NANOS) {
            deleteArmedAt = now;
            delete.setText(Text.of("backutils.admin.option.delete.confirm"), false);
            return;
        }

        deleteArmedAt = 0L;
        delete.setText(Text.of("backutils.admin.option.delete.record"), false);
        AdminNetwork.sendBatch(new AdminLogBatchPayload(
                AdminLogBatchPayload.Kind.DELETE_RECORD, selection.ids(), ""));
        refresh();
    }

    // ------------------------------------------------------------------
    // The copied acknowledgement
    // ------------------------------------------------------------------

    private void animate() {
        long now = System.nanoTime();
        for (Drawn row : drawn) {
            if (!row.blinking()) continue;

            long elapsed = now - row.copiedAt;
            if (elapsed >= AdminListStyle.BLINK_NANOS) {
                row.copiedAt = -1L;
                paint(row);
                continue;
            }
            boolean bright = (elapsed / AdminListStyle.BLINK_PHASE_NANOS) % 2L == 0L;
            int rest = selection.contains(row.data.id())
                    ? AdminListStyle.ROW_SELECTED : AdminListStyle.ROW_IDLE;
            row.element.style(s -> s.background(new ColorRectTexture(
                    bright ? AdminListStyle.ROW_COPIED : rest)));
        }
    }
}
