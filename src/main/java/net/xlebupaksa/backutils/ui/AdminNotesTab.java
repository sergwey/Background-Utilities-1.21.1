package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.client.SymbolChatPanel;
import net.xlebupaksa.backutils.data.NoteData;
import net.xlebupaksa.backutils.network.AdminNotesCache;
import net.xlebupaksa.backutils.network.NoteFeedbackPayload;
import net.xlebupaksa.backutils.network.NoteNetwork;
import net.xlebupaksa.backutils.network.NoteStatePayload;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One player's shared administrator notes: a read-only view by default, with an explicit edit mode
 * and an exclusive server-side edit lease.
 *
 * <p>The tab is deliberately inside the player detail view. There is no route to it that starts
 * from a list of all notes, and the server answers no note packet without the administrator
 * permission check in {@code NoteNetwork}.
 */
@OnlyIn(Dist.CLIENT)
public final class AdminNotesTab {

    private static final int GAP = 4;
    private static final int PAGE_BUTTON_WIDTH = 18;
    private static final int SMALL_BUTTON_WIDTH = 46;
    private static final int SAVE_BUTTON_WIDTH = 60;
    private static final int CANCEL_BUTTON_WIDTH = 52;
    private static final int SYMBOL_BUTTON_WIDTH = 52;
    private static final int ADD_BUTTON_WIDTH = 52;
    private static final int DELETE_BUTTON_WIDTH = 64;
    private static final int TOOLBAR_HEIGHT = 16;
    private static final int STATUS_HEIGHT = 10;
    private static final int WARNING_HEIGHT = 11;
    private static final int SYMBOL_MENU_HEIGHT = 90;
    private static final long HEARTBEAT_MILLIS = 10_000L;
    private static final long SAVE_TIMEOUT_MILLIS = 20_000L;
    private static final long DELETE_CONFIRM_NANOS = 5_000_000_000L;

    private final UIElement root = new UIElement();
    private final Label warning = new Label();
    private final UIElement toolbar = new UIElement();
    private final Label pageLabel = new Label();
    private final Label status = new Label();
    private final Label meta = new Label();
    private final NotesArea editor = new NotesArea();

    private final Button previous = new Button();
    private final Button next = new Button();
    private final Button add = new Button();
    private final Button delete = new Button();
    private final Button edit = new Button();
    private final Button save = new Button();
    private final Button cancel = new Button();
    private final Button discard = new Button();
    private final Button symbols = new Button();

    private final List<String> pages = new ArrayList<>();
    private String player = "";
    private String requested = "";
    private int page;
    private int version;
    private int seenRevision = -1;
    private int seenFeedback = -1;
    private boolean editing;
    private boolean requesting;
    private boolean saving;
    private boolean dirty;
    private boolean retainedDraft;
    private boolean loading;
    private String holderUuid = "";
    private String holderName = "";
    private long leaseUntil;
    private long lastHeartbeat;
    private long lastRequestAt;
    private long saveStartedAt;
    private long deleteArmedAt;
    private String warningText = "";
    private String metaText = "";
    private String lastEditor = "";
    private AbstractWidget symbolMenu;
    private int builtWidth = 200;

    public AdminNotesTab() {
        pages.add("");
        build();
    }

    public UIElement root() {
        return root;
    }

    public void layout(int width, int height) {
        builtWidth = Math.max(80, width);
        root.layout(l -> l.width(builtWidth).height(Math.max(80, height)));
        editor.layout(l -> l.widthPercent(100).flex(1).minHeight(0));
        pageLabel.layout(l -> l.flex(1).height(TOOLBAR_HEIGHT));
        updateControls();
    }

    /** Selects a player; an edit in progress must have been cleared by the caller. */
    public void setPlayer(String name) {
        if (Objects.equals(player, name)) {
            if (!editing && !requesting) {
                requested = "";
                ask();
            }
            return;
        }
        closeSymbols();
        editing = false;
        requesting = false;
        saving = false;
        dirty = false;
        retainedDraft = false;
        player = name == null ? "" : name;
        requested = "";
        page = 0;
        version = 0;
        holderUuid = "";
        holderName = "";
        leaseUntil = 0L;
        warningText = "";
        metaText = "";
        lastEditor = "";
        pages.clear();
        pages.add("");
        editor.setEditorEditable(false);
        editor.setLines(new String[]{""}, false);
        warning.setText("", false);
        warning.setVisible(false);
        warning.setDisplay(false);
        resetDeleteConfirmation();
        status.setText("", false);
        meta.setText("", false);
        updateControls();
        if (!player.isEmpty()) {
            NoteStatePayload cached = AdminNotesCache.state();
            if (cached != null && cached.player().equals(player)) applyState(cached);
            lastRequestAt = System.currentTimeMillis();
            ask();
        }
    }

    /** Called every client tick by the admin screen, even while another tab is visible. */
    public void tick() {
        if (player.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (saving && now - saveStartedAt > SAVE_TIMEOUT_MILLIS) {
            // A save that never answered must not lock the editor forever: keep the draft, stop
            // claiming the write is in flight, and let the administrator copy or discard it.
            saving = false;
            editing = false;
            retainedDraft = true;
            closeSymbols();
            editor.setEditorEditable(false);
            NoteNetwork.cancel(player);
            NoteNetwork.request(player);
            lastRequestAt = now;
            setWarning(Text.of("backutils.notes.save_lost"));
            updateControls();
            return;
        }
        if (requesting && now - lastRequestAt > 5_000L) {
            requesting = false;
            setWarning(Text.of("backutils.notes.error"));
            updateControls();
        }
        if (editing && now - lastHeartbeat >= HEARTBEAT_MILLIS) {
            lastHeartbeat = now;
            NoteNetwork.heartbeat(player);
        } else if (!editing && !requesting && !holderUuid.isBlank()
                && !sameUuid(holderUuid, selfUuid()) && now - lastRequestAt > 5_000L) {
            lastRequestAt = now;
            NoteNetwork.request(player);
        } else if (!editing && !requesting && !AdminNotesCache.holds(player)
                && now - lastRequestAt > 5_000L) {
            lastRequestAt = now;
            NoteNetwork.request(player);
        }
    }

    /** Brings the visible editor up to date with the server cache. */
    public void sync() {
        if (player.isEmpty()) return;
        int revision = AdminNotesCache.revision();
        if (revision == seenRevision) return;
        seenRevision = revision;

        NoteStatePayload state = AdminNotesCache.state();
        if (state != null && state.player().equals(player)) applyState(state);

        AdminNotesCache.Feedback feedback = AdminNotesCache.feedback();
        if (feedback != null && feedback.revision() != seenFeedback
                && feedback.player().equals(player)) {
            seenFeedback = feedback.revision();
            applyFeedback(feedback);
        }
    }

    public boolean typing() {
        return editor.isFocused();
    }

    public boolean copySelection() {
        if (player.isEmpty()) return false;
        String text = currentText();
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        status.setText(Text.of("backutils.notes.copied"), false);
        status.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.MUTED)
                .textShadow(false).adaptiveWidth(true));
        return true;
    }

    public boolean isEditing() {
        return editing;
    }

    public boolean isDirty() {
        return dirty || retainedDraft;
    }

    /** {@return true when leaving this player is safe} */
    public boolean canLeave() {
        if (saving) {
            setWarning(Text.of("backutils.notes.unsaved"));
            return false;
        }
        if (isDirty()) {
            warnLeave();
            return false;
        }
        if (editing) NoteNetwork.cancel(player);
        if (requesting && !player.isEmpty()) NoteNetwork.cancel(player);
        requesting = false;
        closeSymbols();
        editing = false;
        return true;
    }

    /** Releases a clean edit or warns about unsaved text when the screen is closing. */
    public boolean canClose() {
        return canLeave();
    }

    public void warnLeave() {
        setWarning(Text.of("backutils.notes.unsaved"));
    }

    /** Puts SymbolChat's menu away without changing the edit state. */
    public void hideSymbols() {
        closeSymbols();
    }

    /** Called by the screen when closing was refused, to make the warning visible. */
    public void focusWarning() {
        warnLeave();
    }

    public void release() {
        closeSymbols();
        if ((editing || requesting) && !player.isEmpty()) NoteNetwork.cancel(player);
        editing = false;
        requesting = false;
        saving = false;
        dirty = false;
        retainedDraft = false;
        editor.setEditorEditable(false);
        if (!player.isEmpty()) {
            requested = player;
            lastRequestAt = System.currentTimeMillis();
            NoteNetwork.request(player);
        }
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private void build() {
        warning.setText("", false);
        warning.textStyle(t -> t.fontSize(9f).textColor(0xFFFFB74D).textShadow(false)
                .adaptiveWidth(false));
        warning.layout(l -> l.widthPercent(100).height(WARNING_HEIGHT));
        warning.setVisible(false);
        warning.setDisplay(false);

        previous.setText(Text.of("backutils.notes.previous"), false);
        next.setText(Text.of("backutils.notes.next"), false);
        add.setText(Text.of("backutils.notes.add"), false);
        delete.setText(Text.of("backutils.notes.delete"), false);
        edit.setText(Text.of("backutils.notes.edit"), false);
        save.setText(Text.of("backutils.notes.save"), false);
        cancel.setText(Text.of("backutils.notes.cancel"), false);
        discard.setText(Text.of("backutils.notes.discard"), false);
        symbols.setText(Text.of("backutils.notes.symbols"), false);

        previous.layout(l -> l.width(PAGE_BUTTON_WIDTH).height(TOOLBAR_HEIGHT));
        next.layout(l -> l.width(PAGE_BUTTON_WIDTH).height(TOOLBAR_HEIGHT));
        add.layout(l -> l.width(ADD_BUTTON_WIDTH).height(TOOLBAR_HEIGHT));
        delete.layout(l -> l.width(DELETE_BUTTON_WIDTH).height(TOOLBAR_HEIGHT));
        edit.layout(l -> l.width(SMALL_BUTTON_WIDTH).height(TOOLBAR_HEIGHT));
        save.layout(l -> l.width(SAVE_BUTTON_WIDTH).height(TOOLBAR_HEIGHT));
        cancel.layout(l -> l.width(CANCEL_BUTTON_WIDTH).height(TOOLBAR_HEIGHT));
        discard.layout(l -> l.width(CANCEL_BUTTON_WIDTH).height(TOOLBAR_HEIGHT));
        symbols.layout(l -> l.width(SYMBOL_BUTTON_WIDTH).height(TOOLBAR_HEIGHT));

        previous.setOnClick(e -> changePage(-1));
        next.setOnClick(e -> changePage(1));
        add.setOnClick(e -> addPage());
        delete.setOnClick(e -> deletePage());
        edit.setOnClick(e -> startEdit());
        save.setOnClick(e -> save());
        cancel.setOnClick(e -> cancelEdit());
        discard.setOnClick(e -> discardDraft());
        symbols.setOnClick(e -> toggleSymbols());
        show(symbols, false);

        pageLabel.setText("", false);
        pageLabel.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.PLAIN)
                .textShadow(false).textAlignVertical(Vertical.CENTER).adaptiveWidth(false));

        toolbar.layout(l -> l.widthPercent(100).height(TOOLBAR_HEIGHT)
                .flexDirection(FlexDirection.ROW).gapAll(GAP));
        toolbar.addChildren(previous, next, pageLabel,
                add, delete, symbols, edit, save, cancel, discard);

        // Minecraft's own font is drawn on a 9 px line. An integer 9 here is the native size, so the
        // glyphs are not resampled by a fractional font scale, which is what made the notes look
        // soft at 11 px. The line spacing is kept at 1 so smaller text still reads as a block.
        editor.textAreaStyle(s -> s.fontSize(9f).lineSpacing(1f)
                .viewMode(ScrollerMode.BOTH)
                .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                .horizontalScrollDisplay(ScrollDisplay.ALWAYS)
                .textColor(AdminListStyle.PLAIN).textShadow(false));
        editor.style(s -> s.background(new ColorRectTexture(0xAA101016)));
        editor.setLinesResponder(lines -> {
            if (loading || !editing) return;
            if (page < 0 || page >= pages.size()) return;
            String text = String.join("\n", lines);
            if (!Objects.equals(pages.get(page), text)) {
                pages.set(page, text);
                dirty = true;
                updateControls();
            }
        });

        status.setText("", false);
        status.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.MUTED)
                .textShadow(false).textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        meta.setText("", false);
        meta.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.MARKER_GREY)
                .textShadow(false).textAlignVertical(Vertical.CENTER).adaptiveWidth(true));

        UIElement footer = new UIElement();
        footer.layout(l -> l.widthPercent(100).height(STATUS_HEIGHT)
                .flexDirection(FlexDirection.ROW).gapAll(GAP));
        UIElement footerSpacer = new UIElement();
        footerSpacer.layout(l -> l.flex(1));
        footer.addChildren(status, footerSpacer, meta);

        root.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(GAP)
                .widthPercent(100).heightPercent(100));
        root.addChildren(warning, toolbar, editor, footer);
        updateControls();
    }

    // ------------------------------------------------------------------
    // Server state
    // ------------------------------------------------------------------

    private void ask() {
        if (player.isEmpty() || player.equals(requested)) return;
        requested = player;
        lastRequestAt = System.currentTimeMillis();
        NoteNetwork.request(player);
    }

    private void applyState(NoteStatePayload state) {
        if (!state.player().equals(player)) return;
        int incomingVersion = state.version();
        holderUuid = state.holderUuid();
        holderName = state.holderName();
        leaseUntil = state.leaseUntilMillis();
        String self = selfUuid();

        if (requesting) {
            requesting = false;
            if (sameUuid(holderUuid, self)) {
                version = incomingVersion;
                beginEditing(state.pages());
                return;
            }
            setWarning(Text.of("backutils.notes.locked", holderName));
        }

        if (editing) {
            // While a save is in flight, an empty lease is the normal successful answer; the
            // feedback payload, not this state, is what says the write landed. Finishing here
            // would read a successful save as a lost lock.
            if (saving) {
                if (!holderUuid.isBlank() && !sameUuid(holderUuid, self)) {
                    saving = false;
                    editing = false;
                    retainedDraft = true;
                    closeSymbols();
                    editor.setEditorEditable(false);
                    setWarning(Text.of("backutils.notes.lock_lost"));
                }
                version = incomingVersion;
                updateControls();
                return;
            }
            if (!sameUuid(holderUuid, self)) {
                editing = false;
                retainedDraft = true;
                closeSymbols();
                editor.setEditorEditable(false);
                setWarning(Text.of("backutils.notes.lock_lost"));
                updateControls();
                return;
            }
            // A different version while editing means somebody else wrote between the lease and
            // this save. Keep the draft on screen and refuse to paper over their text.
            if (incomingVersion != version) {
                editing = false;
                retainedDraft = true;
                closeSymbols();
                editor.setEditorEditable(false);
                setWarning(Text.of("backutils.notes.conflict"));
            }
            version = incomingVersion;
            updateControls();
            return;
        }

        version = incomingVersion;
        if (!retainedDraft) {
            loadPages(state.pages());
            updateMeta(state);
            if (!holderUuid.isBlank() && !sameUuid(holderUuid, self)) {
                setWarning(Text.of("backutils.notes.locked", holderName));
            } else {
                clearWarning();
            }
        } else {
            updateMetaText();
        }
        updateControls();
    }

    private void applyFeedback(AdminNotesCache.Feedback feedback) {
        switch (feedback.reason()) {
            case SAVED -> {
                saving = false;
                setStatus(Text.of("backutils.notes.saved"), AdminListStyle.PLAIN);
                if (editing) {
                    NoteStatePayload cached = AdminNotesCache.state();
                    finishEditing(cached != null && cached.player().equals(player)
                            && cached.version() > version ? cached.pages() : pages);
                    NoteNetwork.request(player);
                }
            }
            case LOCKED -> {
                requesting = false;
                saving = false;
                if (editing) {
                    editing = false;
                    closeSymbols();
                    editor.setEditorEditable(false);
                }
                setWarning(Text.of("backutils.notes.locked", feedback.holderName()));
                updateControls();
            }
            case LOCK_LOST -> {
                saving = false;
                if (editing) {
                    editing = false;
                    retainedDraft = true;
                    closeSymbols();
                    editor.setEditorEditable(false);
                }
                setWarning(Text.of("backutils.notes.lock_lost"));
                updateControls();
            }
            case VERSION_CONFLICT -> {
                saving = false;
                if (editing) {
                    editing = false;
                    retainedDraft = true;
                    closeSymbols();
                    editor.setEditorEditable(false);
                }
                setWarning(Text.of("backutils.notes.conflict"));
                updateControls();
            }
            case INVALID -> {
                saving = false;
                setWarning(Text.of("backutils.notes.invalid"));
                updateControls();
            }
            case UNKNOWN_PLAYER -> {
                requesting = false;
                setWarning(Text.of("backutils.notes.unknown_player"));
                updateControls();
            }
            case NOT_ALLOWED -> {
                requesting = false;
                setWarning(Text.of("backutils.notes.not_allowed"));
                updateControls();
            }
            case ERROR -> {
                requesting = false;
                saving = false;
                setWarning(Text.of("backutils.notes.error"));
                updateControls();
            }
        }
    }

    private void beginEditing(List<String> serverPages) {
        List<String> incoming = List.copyOf(serverPages);
        pages.clear();
        pages.addAll(incoming);
        if (pages.isEmpty()) pages.add("");
        page = Math.max(0, Math.min(page, pages.size() - 1));
        dirty = false;
        retainedDraft = false;
        saveStartedAt = 0L;
        editing = true;
        askingDone();
        resetDeleteConfirmation();
        editor.setEditorEditable(true);
        editor.setLines(split(pages.get(page)), false);
        editor.focus();
        clearWarning();
        setStatus(Text.of("backutils.notes.editing"), AdminListStyle.MUTED);
        updateMetaText();
        lastHeartbeat = System.currentTimeMillis();
        updateControls();
    }

    private void finishEditing(List<String> serverPages) {
        closeSymbols();
        List<String> incoming = List.copyOf(serverPages);
        pages.clear();
        pages.addAll(incoming);
        if (pages.isEmpty()) pages.add("");
        page = Math.max(0, Math.min(page, pages.size() - 1));
        editing = false;
        saving = false;
        dirty = false;
        retainedDraft = false;
        resetDeleteConfirmation();
        saveStartedAt = 0L;
        editor.setEditorEditable(false);
        editor.setLines(split(pages.get(page)), false);
        NoteStatePayload cached = AdminNotesCache.state();
        if (cached != null && cached.player().equals(player)) lastEditor = cached.updatedBy();
        updateMetaText();
        clearWarning();
        status.setText(Text.of("backutils.notes.saved"), false);
        status.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.PLAIN)
                .textShadow(false).adaptiveWidth(true));
        updateControls();
    }

    private void loadPages(List<String> serverPages) {
        List<String> incoming = List.copyOf(serverPages);
        pages.clear();
        pages.addAll(incoming);
        if (pages.isEmpty()) pages.add("");
        page = Math.max(0, Math.min(page, pages.size() - 1));
        editor.setEditorEditable(editing);
        loading = true;
        editor.setLines(split(pages.get(page)), false);
        loading = false;
    }

    private void askingDone() {
        requested = player;
        requesting = false;
    }

    private static boolean sameUuid(String left, String right) {
        if (left == null || right == null) return false;
        return left.replace("-", "").equalsIgnoreCase(right.replace("-", ""));
    }

    private String selfUuid() {
        return Minecraft.getInstance().player == null ? ""
                : Minecraft.getInstance().player.getStringUUID();
    }

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    private void startEdit() {
        if (player.isEmpty() || editing || requesting) return;
        if (retainedDraft) {
            setWarning(Text.of("backutils.notes.unsaved"));
            return;
        }
        if (!holderUuid.isBlank() && !sameUuid(holderUuid, selfUuid())
                && leaseUntil > System.currentTimeMillis()) {
            setWarning(Text.of("backutils.notes.locked", holderName));
            return;
        }
        requesting = true;
        lastHeartbeat = System.currentTimeMillis();
        lastRequestAt = lastHeartbeat;
        setStatus(Text.of("backutils.notes.requesting"), AdminListStyle.MUTED);
        updateControls();
        NoteNetwork.requestEdit(player);
    }

    private void save() {
        if (!editing || saving) return;
        closeSymbols();
        capture();
        saving = true;
        saveStartedAt = System.currentTimeMillis();
        setStatus(Text.of("backutils.notes.saving"), AdminListStyle.MUTED);
        updateControls();
        NoteNetwork.save(player, version, pages);
    }

    private void cancelEdit() {
        if (!editing) return;
        closeSymbols();
        editing = false;
        saving = false;
        dirty = false;
        retainedDraft = false;
        resetDeleteConfirmation();
        saveStartedAt = 0L;
        editor.setEditorEditable(false);
        NoteNetwork.cancel(player);
        NoteStatePayload latest = AdminNotesCache.state();
        if (latest != null && latest.player().equals(player)) {
            version = latest.version();
            loadPages(latest.pages());
        } else {
            loadPages(pages);
        }
        setWarning(Text.of("backutils.notes.cancelled"));
        status.setText("", false);
        updateControls();
    }

    /** Drops a draft kept after a lost lease, then reloads the server's version. */
    private void discardDraft() {
        if (!retainedDraft) return;
        retainedDraft = false;
        dirty = false;
        saving = false;
        saveStartedAt = 0L;
        resetDeleteConfirmation();
        editor.setEditorEditable(false);
        // A stale save can leave this administrator holding the lease even though the document
        // refused the write. Releasing it here is what lets the next administrator edit at once.
        if (!player.isEmpty()) NoteNetwork.cancel(player);
        NoteStatePayload latest = AdminNotesCache.state();
        if (latest != null && latest.player().equals(player)) {
            version = latest.version();
            loadPages(latest.pages());
        } else {
            NoteNetwork.request(player);
        }
        clearWarning();
        setStatus(Text.of("backutils.notes.discarded"), AdminListStyle.MUTED);
        updateControls();
    }

    private void changePage(int delta) {
        if (editing) capture();
        resetDeleteConfirmation();
        closeSymbols();
        int target = page + delta;
        if (target < 0 || target >= pages.size()) return;
        page = target;
        showPage();
    }

    private void addPage() {
        if (!editing) return;
        resetDeleteConfirmation();
        closeSymbols();
        if (pages.size() >= NoteData.MAX_PAGES) {
            setWarning(Text.of("backutils.notes.too_many_pages"));
            return;
        }
        capture();
        pages.add(page + 1, "");
        page++;
        dirty = true;
        showPage();
        updateControls();
    }

    private void deletePage() {
        if (!editing) return;
        closeSymbols();
        if (pages.size() <= 1) {
            setWarning(Text.of("backutils.notes.last_page"));
            return;
        }
        long now = System.nanoTime();
        if (deleteArmedAt == 0L || now - deleteArmedAt > DELETE_CONFIRM_NANOS) {
            deleteArmedAt = now;
            delete.setText(Text.of("backutils.notes.delete_confirm"), false);
            return;
        }
        deleteArmedAt = 0L;
        delete.setText(Text.of("backutils.notes.delete"), false);
        capture();
        pages.remove(page);
        if (page >= pages.size()) page = pages.size() - 1;
        dirty = true;
        showPage();
        updateControls();
    }

    private void capture() {
        if (!editing || page < 0 || page >= pages.size()) return;
        String text = String.join("\n", editor.getValue());
        if (!Objects.equals(pages.get(page), text)) {
            pages.set(page, text);
            dirty = true;
        }
    }

    private void showPage() {
        if (page < 0 || page >= pages.size()) page = 0;
        loading = true;
        editor.setLines(split(pages.get(page)), false);
        loading = false;
        editor.setEditorEditable(editing);
        updateControls();
    }

    private String currentText() {
        if (page >= 0 && page < pages.size()) return pages.get(page);
        return "";
    }

    private static String[] split(String text) {
        return (text == null ? "" : text).split("\n", -1);
    }

    // ------------------------------------------------------------------
    // Controls and labels
    // ------------------------------------------------------------------

    private void updateControls() {
        if (player.isEmpty()) {
            warning.setVisible(false);
            warning.setDisplay(false);
            pageLabel.setText("", false);
            status.setText("", false);
            meta.setText("", false);
            setAllActionsVisible();
            return;
        }

        previous.setActive(page > 0);
        next.setActive(page + 1 < pages.size());
        show(toolbar, true);
        pageLabel.setText(Text.of("backutils.notes.page", page + 1, Math.max(1, pages.size())),
                false);

        show(edit, !editing);
        show(save, editing);
        show(cancel, editing);
        show(discard, retainedDraft && !editing);
        show(add, editing);
        show(delete, editing);
        show(symbols, editing && SymbolChatPanel.present());
        edit.setActive(!requesting && !retainedDraft);
        save.setActive(!saving);
        cancel.setActive(!saving);
        discard.setActive(!saving);
        add.setActive(!saving);
        delete.setActive(!saving);
        previous.setActive(!saving && page > 0);
        next.setActive(!saving && page + 1 < pages.size());

        if (!editing && warningText.isBlank()) {
            warning.setVisible(false);
            warning.setDisplay(false);
        }
        editor.setEditorEditable(editing && !saving);
        updateMetaText();
    }

    /** Shows or hides a child without leaving its old width or height in the layout. */
    private static void show(UIElement element, boolean visible) {
        element.setVisible(visible);
        element.setDisplay(visible);
    }

    private void setAllActionsVisible() {
        show(toolbar, false);
        show(edit, false);
        show(save, false);
        show(cancel, false);
        show(discard, false);
        show(add, false);
        show(delete, false);
        show(symbols, false);
    }

    private void updateMeta(NoteStatePayload state) {
        lastEditor = state.updatedBy();
        updateMetaText();
        meta.setVisible(!editing);
    }

    private void updateMetaText() {
        if (editing) {
            metaText = Text.of("backutils.notes.chars", currentText().length());
        } else if (lastEditor.isBlank()) {
            metaText = Text.of("backutils.notes.never");
        } else {
            metaText = Text.of("backutils.notes.meta", lastEditor, currentText().length());
        }
        meta.setText(metaText, false);
    }

    private void setWarning(String text) {
        warningText = text == null ? "" : text;
        boolean visible = !warningText.isBlank();
        warning.setText(warningText, false);
        warning.setVisible(visible);
        warning.setDisplay(visible);
        warning.textStyle(t -> t.fontSize(9f).textColor(0xFFFFB74D).textShadow(false)
                .adaptiveWidth(false));
    }

    private void clearWarning() {
        warningText = "";
        warning.setVisible(false);
        warning.setDisplay(false);
    }

    /** Disarms the two-click delete, so a confirmation cannot survive a save or a page jump. */
    private void resetDeleteConfirmation() {
        deleteArmedAt = 0L;
        delete.setText(Text.of("backutils.notes.delete"), false);
    }

    /** Writes every caption again in the language the client is set to now. */
    public void relabel() {
        previous.setText(Text.of("backutils.notes.previous"), false);
        next.setText(Text.of("backutils.notes.next"), false);
        add.setText(Text.of("backutils.notes.add"), false);
        delete.setText(Text.of("backutils.notes.delete"), false);
        edit.setText(Text.of("backutils.notes.edit"), false);
        save.setText(Text.of("backutils.notes.save"), false);
        cancel.setText(Text.of("backutils.notes.cancel"), false);
        discard.setText(Text.of("backutils.notes.discard"), false);
        symbols.setText(Text.of("backutils.notes.symbols"), false);
        resetDeleteConfirmation();
        updateControls();
    }

    private void setStatus(String text, int colour) {
        status.setText(text == null ? "" : text, false);
        status.textStyle(t -> t.fontSize(9f).textColor(colour).textShadow(false)
                .adaptiveWidth(true));
    }

    // ------------------------------------------------------------------
    // SymbolChat
    // ------------------------------------------------------------------

    private void toggleSymbols() {
        if (!editing || !SymbolChatPanel.present()) return;
        if (symbolMenu != null) {
            closeSymbols();
            return;
        }
        int width = SYMBOL_BUTTON_WIDTH;
        int x = Math.max(4, Math.round(symbols.getPositionX()) - width);
        int y = Math.max(4, Math.round(symbols.getPositionY()) - SYMBOL_MENU_HEIGHT - 2);
        AbstractWidget panel = SymbolChatPanel.panel(x, y, SYMBOL_MENU_HEIGHT,
                this::insertSymbol);
        if (panel == null) return;
        if (Minecraft.getInstance().screen instanceof BackUtilsAdminScreen screen) {
            screen.addOverlayWidget(panel);
            symbolMenu = panel;
            SymbolChatPanel.show(panel);
        }
    }

    private void closeSymbols() {
        if (symbolMenu == null) return;
        SymbolChatPanel.hide(symbolMenu);
        if (Minecraft.getInstance().screen instanceof BackUtilsAdminScreen screen) {
            screen.removeOverlayWidget(symbolMenu);
        }
        symbolMenu = null;
    }

    private void insertSymbol(String symbol) {
        if (!editing || symbol == null) return;
        editor.insertSymbol(symbol);
        dirty = true;
        updateControls();
    }

    /** A text area that can be read-only while still being selectable and scrollable. */
    private static final class NotesArea extends TextArea {
        private boolean editorEditable;

        void setEditorEditable(boolean editable) {
            editorEditable = editable;
        }

        @Override
        public boolean isEditable() {
            return editorEditable && super.isEditable();
        }

        /**
         * Ctrl+A and Ctrl+C belong to the text area even while it is read-only.
         *
         * <p>The base area declines every chord when it is not editable, which leaves the screen's
         * list-copy handler to decide what to do with them; in a read-only note that would be
         * nothing. Letting these two through is what makes select-all and copy work without a
         * separate Copy button. Paste and cut remain guarded by the base area.
         */
        @Override
        public boolean ownsKey(UIEvent event) {
            if (event.isCtrlDown() && !event.isAltDown()
                    && (event.keyCode == GLFW.GLFW_KEY_A || event.keyCode == GLFW.GLFW_KEY_C)) {
                return true;
            }
            return super.ownsKey(event);
        }

        void insertSymbol(String symbol) {
            insertText(symbol);
        }
    }
}
