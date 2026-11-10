package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.client.AdminAlerts;
import net.xlebupaksa.backutils.client.AdminMenuCache;
import net.xlebupaksa.backutils.client.BackUtilsClientConfig;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.data.MarkupWrap;
import net.xlebupaksa.backutils.network.AdminActionPayload;
import net.xlebupaksa.backutils.network.AdminConfigCache;
import net.xlebupaksa.backutils.network.AdminConfigNetwork;
import net.xlebupaksa.backutils.network.AdminLogPayload;
import net.xlebupaksa.backutils.network.AdminLogBatchPayload;
import net.xlebupaksa.backutils.network.AdminNetwork;
import net.xlebupaksa.backutils.network.AdminProfileNetwork;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The administrator menu — the "background" side of the mod, reached with {@code /backutils menu},
 * which the server only answers for a permission level of 2 or above.
 *
 * <p>Every control dispatches the command an operator would type, so the permission checks, ranges
 * and confirmation messages are the server's own: a client cannot write a server's configuration,
 * and the menu cannot drift out of step with the commands it runs.
 *
 * <p>One reused instance with explicit pixel sizes, drawing its rows with vanilla's font because
 * ldlib2 text bypasses the pipeline that Ember's markup hooks.
 */
@OnlyIn(Dist.CLIENT)
public final class BackUtilsAdminScreen extends Screen {

    private static final long DOUBLE_CLICK_NANOS = 400_000_000L;

    private static final int ROW_IDLE = 0x44101016;
    private static final int ROW_HOVER = 0x883A4658;
    private static final int ROW_SELECTED = 0xAA4E7FB8;
    private static final int MUTED = 0xFF9AA3B2;
    private static final int PLAIN = 0xFFE2E8F0;
    private static final int MARKER_GREY = 0xFF8A8A8A;

    /**
     * The marker drawn in front of an entry an administrator would not normally be shown, and
     * beside each witness it is withheld from. A sprite rather than a glyph, because Minecraft's
     * fonts carry no emoji and the obvious ones rendered as an empty box.
     */
    private static final ResourceLocation HIDDEN_MARKER =
            ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "menu/hidden_action");
    /** Drawn at the sprite's own size, which is the height a row of this list has room for. */
    private static final int HIDDEN_MARKER_SIZE = 10;

    private static final int PAD = 10;
    private static final int GAP = 8;
    private static final int INNER_GAP = 4;
    private static final int TAB_STRIP_HEIGHT = 22;
    /**
     * What one {@code TabView} adds around its content: ldlib2 itself pads {@code
     * tabContentContainer} by five on every side, and the profile tab nests one tab view inside
     * another so this is paid twice.
     */
    private static final int TAB_CONTENT_PAD = 5;
    /** The title row on its own, which is all the header shows over the profile tab. */
    private static final int TITLE_ROW_HEIGHT = 14;
    private static final int LINE_STEP = 10;
    /** The header row's height: three lines of hovered-entry details, the tallest thing in it. */
    private static final int HEADER_HEIGHT = LINE_STEP * 3;
    /** The options column takes this share of the window width, never less than the floor. */
    private static final float OPTIONS_WIDTH_SHARE = 0.30F;
    private static final int OPTIONS_MIN_WIDTH = 170;

    private static BackUtilsAdminScreen instance;

    /** Everything built once, resized on each show. */
    private static final class Built {

        /** One caption and the key behind it, so {@link #applyTexts} can write it out again. */
        private record Caption(TextElement element, String key) {}

        ModularUI ui;
        UIElement root;
        UIElement body;
        UIElement listColumn;
        UIElement optionsColumn;
        ScrollerView logList;
        ScrollerView witnessList;
        TextField filterField;
        TextField logFilter;
        Button deleteButton;
        /** The one hide/show button for the listed witnesses: its caption says which way it goes. */
        Button hideListed;
        Button refresh;
        UIElement header;
        UIElement details;
        TabView tabView;
        Tab logTab;
        Tab playersTab;
        Tab configTab;
        AdminPlayersTab players;
        AdminConfigTab config;
        /** The three lines describing the hovered entry, carried in the header row. */
        Label hoverDate;
        Label hoverActor;
        Label hoverPlace;
        /** The witness heading, which says "this action" or "every selected action". */
        Label witnessHeading;
        /** The same for the selected entry, in the options column. */
        Label selectedId;
        Label selectedActor;
        Label selectedPlace;
        /**
         * Every caption in the tree with the key it came from. The tree outlives a language change
         * while ldlib2 flattens a string into a literal as it is set, so the keys are kept to write
         * the captions again on every open.
         */
        final List<Caption> captions = new ArrayList<>();
    }

    /** How long the delete button stays armed between its two clicks. */
    private static final long DELETE_CONFIRM_NANOS = 5_000_000_000L;

    private final Built built;
    /** The drawn row elements, by entry id, so a refresh paints the ones still here. */
    private final Map<Long, UIElement> rowElements = new LinkedHashMap<>();
    /** When each copied row started its acknowledgement, by entry id. */
    private final Map<Long, Long> copiedAt = new HashMap<>();
    /** What is selected, with the anchor a shift-click ranges from. */
    private final MultiSelection selection = new MultiSelection();
    /** The ids the list is drawing, in drawing order, which is what a shift range follows. */
    private List<Long> drawnIds = List.of();
    private int builtRevision = -1;
    private int listWidth = 200;
    /** When the delete button was first clicked, or 0 when it is not armed. */
    private long deleteArmedAt;
    /** Set when the list is rebuilt, acted on once the layout is valid. */
    private boolean scrollToNewestPending;

    /** The entry whose options are showing, and when it was last clicked. */
    private AdminLogPayload.Row selected;
    private long lastClickAt;
    private long lastClickId = -1L;

    /** True while a tab other than the action log is in view, which decides the whole layout. */
    private boolean formSelected;

    private BackUtilsAdminScreen(Built built) {
        super(Component.translatable("backutils.admin.screen.title"));
        this.built = built;
    }

    /** Shows the menu, building it on first use and reusing it afterwards. */
    public static void open() {
        if (instance == null) {
            Built built = new Built();
            build(built);
            built.ui = ModularUI.of(UI.of(built.root));
            built.ui.setTickWhileRending(true);
            instance = new BackUtilsAdminScreen(built);
        }

        AdminAlerts.clear();
        AdminMenuCache.refresh();
        // Asked for on every open: a profile can be created from the console or by another
        // administrator, and the list would otherwise be stale.
        AdminProfileNetwork.requestPlayers();
        // An operator can edit the config file by hand between openings.
        AdminConfigNetwork.request();
        Minecraft.getInstance().setScreen(instance);
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private static void build(Built built) {
        Label title = new Label();
        translated(built, title, "backutils.admin.screen.heading");
        title.textStyle(t -> t.fontSize(12f).textColor(PLAIN).textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER)
                .adaptiveWidth(true));

        built.logList = new ScrollerView();
        built.logList.scrollerStyle(s -> s
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .adaptiveWidth(false)
                .adaptiveHeight(false));
        built.logList.viewContainer(v -> v.getLayout().gapAll(1));

        // Held control scrolls further per notch, which is what makes a long log navigable rather
        // than a scrollbar to be dragged: a multiple of the scroller's own step, not a number here.
        built.logList.addEventListener(UIEvents.MOUSE_WHEEL, e -> {
            if (!e.isCtrlDown()) return;
            built.logList.verticalScroller.scrollByWheel(e.deltaY * 4.0D);
            e.stopImmediatePropagation();
        }, true);

        UIElement logHolder = new UIElement();
        logHolder.addChild(built.logList);

        UIElement header = new UIElement();
        header.layout(l -> l.flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(GAP));

        built.logFilter = new TextField();
        built.logFilter.setText("");
        built.logFilter.layout(l -> l.width(120).height(14));
        built.logFilter.textFieldStyle(s -> s.placeholder(
                Component.translatable("backutils.admin.log.search")));
        built.logFilter.setTextResponder(text -> {
            if (instance != null) instance.rebuildList();
        });

        // The hovered entry's details, as three plain lines carried in the header row rather than a
        // tooltip: ldlib2 draws a tooltip with its own font, which has no glyph for the hidden
        // marker, and renders a line break as an empty box. The name leads.
        built.hoverActor = detailLine(PLAIN);
        built.hoverDate = detailLine(MUTED);
        built.hoverPlace = detailLine(MUTED);

        UIElement details = new UIElement();
        details.layout(l -> l.flex(1).height(HEADER_HEIGHT)
                .flexDirection(FlexDirection.COLUMN));
        details.addChildren(built.hoverActor, built.hoverDate, built.hoverPlace);
        translated(built, built.hoverPlace, "backutils.admin.detail.hint");
        built.details = details;

        // The details take the middle of the row, between the title and the search box. Over the
        // profile tab the title and the alert marker are the whole row, and the room freed is a row
        // of the player list.
        built.refresh = refreshButton(built);
        built.header = header;
        header.addChildren(title, built.refresh, alertLabel(), details, built.logFilter);

        TabView tabView = new TabView();
        built.logTab = translatedTab(built, "backutils.admin.tab.log");
        tabView.addTab(built.logTab, logHolder);
        tabView.layout(l -> l.flex(1));
        built.tabView = tabView;

        // The player tab owns its own player list and an inner tab view for the chosen player's
        // chat profiles, name profiles and memory: asking "who" once and then showing everything
        // about them is the shape of the question.
        built.players = new AdminPlayersTab();
        built.playersTab = translatedTab(built, "backutils.admin.tab.players");
        tabView.addTab(built.playersTab, built.players.root());

        // The settings tab: its sub-tabs are groups of settings, and one Save button sits below them.
        built.config = new AdminConfigTab();
        built.configTab = translatedTab(built, "backutils.admin.tab.config");
        tabView.addTab(built.configTab, built.config.root());

        tabView.setOnTabSelected(tab -> {
            if (instance != null) instance.onTabChanged(tab);
        });

        built.listColumn = new UIElement();
        built.listColumn.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(INNER_GAP));
        built.listColumn.addChildren(header, tabView);

        buildOptions(built);

        built.body = new UIElement();
        built.body.layout(l -> l.flexDirection(FlexDirection.ROW).gapAll(GAP));
        built.body.addChildren(built.listColumn, built.optionsColumn);

        built.root = new UIElement();
        built.root.layout(l -> l.paddingAll(PAD));
        built.root.addChild(built.body);
    }

    /**
     * Sets a caption from a translation key and remembers the pair, so {@link #applyTexts} can put
     * the text back: ldlib2 keeps whatever string it was given as a literal, and the tree here is
     * built once and kept for the life of the client.
     */
    private static void translated(Built built, TextElement element, String key) {
        element.setText(Text.of(key), false);
        built.captions.add(new Built.Caption(element, key));
    }

    /** {@return a tab whose caption comes from a key}, written again like every other caption */
    private static Tab translatedTab(Built built, String key) {
        Tab tab = new Tab();
        // Tab.setText delegates to this label, and the label is what has to be written again.
        translated(built, tab.text, key);
        return tab;
    }

    /**
     * Writes every remembered caption in the language the client is set to now. The language is
     * chosen on another screen, which replaces this one, so doing it here reaches every change
     * without a restart.
     */
    private void applyTexts() {
        for (Built.Caption caption : built.captions) {
            caption.element().setText(Text.of(caption.key()), false);
        }
        // Delete is the one caption with a second wording, so it is written to the state it is in:
        // coming back to an armed button must not have it read as unarmed.
        if (deleteArmedAt != 0L) {
            built.deleteButton.setText(Text.of("backutils.admin.option.delete.confirm"), false);
        }
        // The player tab owns captions of its own: its tabs, its buttons and its two search hints.
        built.players.relabel();
        // The hide button's caption is a state as much as a caption, so a language change writes the
        // one the selection is in rather than the one it was built with.
        updateHideButton();
    }

    private static Button refreshButton(Built built) {
        Button button = new Button();
        translated(built, button.text, "backutils.admin.screen.refresh");
        // Wide enough for "Обновить", which needs 48 px of the width - 8 the button leaves.
        button.layout(l -> l.width(56).height(14));
        button.setOnClick(e -> AdminMenuCache.refresh());
        return button;
    }

    private static Label alertLabel() {
        Label label = new Label();
        label.setText("", false);
        label.textStyle(t -> t.fontSize(9f).textColor(MARKER_GREY).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        return label;
    }

    /**
     * One line of an entry's details, with its height pinned: an ldlib2 label with only {@code
     * adaptiveWidth} has no height of its own, so three in a column stack on the same y.
     */
    private static Label detailLine(int colour) {
        Label label = new Label();
        label.setText("", false);
        label.layout(l -> l.widthPercent(100).height(LINE_STEP));
        label.textStyle(t -> t.fontSize(9f).textColor(colour).textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER).adaptiveWidth(false));
        return label;
    }

    /** Writes the three detail lines for an entry, or blanks them; nothing hovered leaves the
     * selected entry's lines in place. */
    private void showHover(AdminLogPayload.Row row) {
        AdminLogPayload.Row shown = row != null ? row : selected;

        if (shown == null) {
            built.hoverActor.setText("", false);
            built.hoverDate.setText("", false);
            built.hoverPlace.setText(Text.of("backutils.admin.detail.hint"), false);
            return;
        }

        built.hoverActor.setText(shown.visible()
                ? Text.of("backutils.admin.detail.actor", shown.actor())
                : Text.of("backutils.admin.detail.actor.not.witness", shown.actor()), false);
        built.hoverDate.setText(shown.createdAt(), false);
        built.hoverPlace.setText(placeOf(shown), false);
    }

    /** {@return an entry's location, and the note an administrator alone is shown} */
    private static String placeOf(AdminLogPayload.Row row) {
        String place = row.dimension() == null || row.dimension().isBlank()
                ? Text.of("backutils.admin.detail.no.location")
                : row.dimension() + "   " + Math.round(row.x())
                        + " " + Math.round(row.y()) + " " + Math.round(row.z());
        return row.note() == null || row.note().isBlank() ? place : place + "   -   " + row.note();
    }

    private static void buildOptions(Built built) {
        Label heading = new Label();
        translated(built, heading, "backutils.admin.option.heading");
        heading.textStyle(t -> t.fontSize(10f).textColor(PLAIN).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));

        // The selected entry, as three short lines: one line was clipped at the right-hand edge.
        built.selectedActor = detailLine(PLAIN);
        built.selectedId = detailLine(MUTED);
        built.selectedPlace = detailLine(MUTED);
        translated(built, built.selectedActor, "backutils.admin.option.hint");

        built.witnessHeading = new Label();
        translated(built, built.witnessHeading, "backutils.admin.witness.heading");
        built.witnessHeading.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false)
                .adaptiveWidth(true).adaptiveHeight(true));

        // The filter does double duty: part of a name narrows the list, while a selector such as
        // @a[distance=..10] goes to the server, which resolves it with the administrator as source.
        built.filterField = new TextField();
        built.filterField.setText("");
        built.filterField.layout(l -> l.widthPercent(100).height(14));
        built.filterField.textFieldStyle(s -> s.placeholder(
                Component.translatable("backutils.admin.witness.search")));
        built.filterField.setTextResponder(text ->
                rebuildWitnesses(built, instance == null ? List.of() : instance.selectedRows()));

        built.witnessList = new ScrollerView();
        built.witnessList.scrollerStyle(s -> s
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .adaptiveWidth(false)
                .adaptiveHeight(false));
        built.witnessList.viewContainer(v -> v.getLayout().gapAll(1));
        // Takes whatever vertical space is left rather than a fixed height, so on a short window this
        // is what shrinks.
        built.witnessList.layout(l -> l.widthPercent(100).flex(1));

        built.hideListed = new Button();
        built.hideListed.layout(l -> l.widthPercent(100).height(16));
        built.hideListed.setOnClick(e -> toggleListed());
        built.hideListed.setText(Text.of("backutils.admin.option.hide.listed"), false);

        Button copy = optionButton(built, "backutils.admin.option.copy.text", () -> {
            if (instance != null) instance.copySelection();
        });
        Button toAction = optionButton(built, "backutils.admin.option.teleport.to.action",
                () -> send(AdminActionPayload.of(AdminActionPayload.Kind.TELEPORT_TO_ACTION, idOrZero())));
        Button toActor = optionButton(built, "backutils.admin.option.teleport.to.actor",
                () -> send(AdminActionPayload.of(AdminActionPayload.Kind.TELEPORT_TO_ACTOR, idOrZero())));

        built.deleteButton = optionButton(built, "backutils.admin.option.delete.record",
                BackUtilsAdminScreen::onDeleteClicked);

        built.optionsColumn = new UIElement();
        built.optionsColumn.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(INNER_GAP));
        // The filter sits above the list it filters and the bulk buttons above it: "what this does",
        // then "which players", then "the players". A spacer would be a witness row not shown.
        built.optionsColumn.addChildren(heading,
                built.selectedActor, built.selectedId, built.selectedPlace,
                copy, toAction, toActor,
                built.deleteButton, built.hideListed,
                built.witnessHeading, built.filterField, built.witnessList);

        rebuildWitnesses(built, List.of());
    }

    /**
     * Writes the one hide button's caption: show when every listed pair is already withheld, hide
     * otherwise.
     *
     * <p>A selector target cannot be resolved locally, so it always reads as hide: guessing which
     * players it names would be worse than saying what the click does.
     */
    private void updateHideButton() {
        if (built.hideListed == null) return;
        built.hideListed.setText(Text.of(allListedHidden()
                ? "backutils.admin.option.show.listed"
                : "backutils.admin.option.hide.listed"), false);
    }

    /** Hides every listed pair, or gives every one of them back, in one click. */
    private static void toggleListed() {
        if (instance == null) return;
        sendMany(instance.allListedHidden()
                ? AdminLogBatchPayload.Kind.UNHIDE_FROM_MANY
                : AdminLogBatchPayload.Kind.HIDE_FROM_MANY);
    }

    /**
     * {@return true when every listed witness of every selected entry is already withheld from it}
     *
     * <p>"Listed" is the witness filter's answer, so the button follows the filter the same way the
     * old two buttons did.
     */
    private boolean allListedHidden() {
        List<AdminLogPayload.Row> rows = selectedRows();
        if (rows.isEmpty() || filterText().startsWith("@")) return false;

        boolean any = false;
        for (AdminLogPayload.Row row : rows) {
            for (String witness : listedWitnesses(row)) {
                if (!row.witnesses().contains(witness)) continue;
                any = true;
                if (!row.hiddenAll() && !row.hiddenFrom().contains(witness)) return false;
            }
        }
        return any;
    }

    /**
     * Sends a hide or unhide for everything the filter lists across the selection.
     *
     * <p>The target is the union of the witnesses the filter shows, or a selector passed through as
     * it is: only the server can resolve one. The server then crosses every selected entry with every
     * named player and skips the pairs where the player never witnessed the entry, so an entry is
     * only ever hidden from somebody who had it.
     */
    private static void sendMany(AdminLogBatchPayload.Kind kind) {
        if (instance == null || instance.selection.isEmpty()) return;

        List<AdminLogPayload.Row> rows = instance.selectedRows();
        String filter = instance.filterText();
        String target;
        if (filter.startsWith("@")) {
            target = filter;
        } else {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            for (AdminLogPayload.Row row : rows) names.addAll(instance.listedWitnesses(row));
            target = String.join(";", names);
        }
        if (target.isBlank()) return;

        sendBatch(new AdminLogBatchPayload(kind, instance.selection.ids(), target), true);
    }

    /** Deletes the selected records, on a second click: hiding is reversible, deleting is not. */    private static void onDeleteClicked() {
        if (instance == null || instance.selection.isEmpty()) return;

        long now = System.nanoTime();
        if (now - instance.deleteArmedAt > DELETE_CONFIRM_NANOS) {
            instance.deleteArmedAt = now;
            instance.built.deleteButton.setText(Text.of("backutils.admin.option.delete.confirm"),
                    false);
            return;
        }

        instance.deleteArmedAt = 0L;
        instance.built.deleteButton.setText(Text.of("backutils.admin.option.delete.record"),
                false);
        sendBatch(new AdminLogBatchPayload(AdminLogBatchPayload.Kind.DELETE_RECORD,
                instance.selection.ids(), ""), true);
    }

    /** {@return the filter text, trimmed} */
    private String filterText() {
        String text = built.filterField.getText();
        return text == null ? "" : text.trim();
    }

    /**
     * {@return the witnesses the filter currently shows for one entry}
     *
     * <p>The entry is passed in rather than read off the selection: the list is rebuilt from whatever
     * the caller has in hand, and a filter that silently answered about a different row would be a
     * coupling between two writes rather than a reading of one.
     */
    private List<String> listedWitnesses(AdminLogPayload.Row row) {
        if (row == null) return List.of();
        String filter = filterText().toLowerCase(java.util.Locale.ROOT);
        // A selector cannot be matched against names locally, so everything stays listed.
        if (filter.isEmpty() || filter.startsWith("@")) return row.witnesses();

        List<String> shown = new ArrayList<>();
        for (String witness : row.witnesses()) {
            if (witness.toLowerCase(java.util.Locale.ROOT).contains(filter)) shown.add(witness);
        }
        return shown;
    }

    /** {@return the names of a union the filter shows}, which the bulk buttons send */
    private List<String> listedWitnesses(java.util.Collection<String> names) {
        String filter = filterText().toLowerCase(java.util.Locale.ROOT);
        // A selector cannot be matched against names locally, so everything stays listed.
        if (filter.isEmpty() || filter.startsWith("@")) return new ArrayList<>(names);

        List<String> shown = new ArrayList<>();
        for (String name : names) {
            if (name.toLowerCase(java.util.Locale.ROOT).contains(filter)) shown.add(name);
        }
        return shown;
    }

    /**
     * Fills the witness list for the selection, with {@link MarkupLabel} rather than buttons: the
     * marker is a sprite, which ldlib2's own text element cannot draw.
     *
     * <p>The list is the union: a player who witnessed any selected entry is offered, with how many
     * of them they can still see. Hiding is per entry, so a player who did not witness one is skipped
     * for it on the server — which is exactly what the count beside the name is saying.
     */
    private static void rebuildWitnesses(Built built, List<AdminLogPayload.Row> rows) {
        built.witnessList.clearAllScrollViewChildren();
        // The hide button is about the same selection and the same filter, so it is written here:
        // every path that changes either one arrives through this method.
        if (instance != null) instance.updateHideButton();
        built.witnessHeading.setText(Text.of(rows.size() > 1
                ? "backutils.admin.witness.heading.many"
                : "backutils.admin.witness.heading"), false);

        if (rows.isEmpty()) {
            built.witnessList.addScrollViewChild(
                    muted(Text.of("backutils.admin.witness.none.selected")));
            return;
        }

        Map<String, int[]> counts = new LinkedHashMap<>();
        for (AdminLogPayload.Row row : rows) {
            for (String witness : row.witnesses()) {
                int[] count = counts.computeIfAbsent(witness, key -> new int[2]);
                count[0]++;
                if (!row.hiddenAll() && !row.hiddenFrom().contains(witness)) count[1]++;
            }
        }
        if (counts.isEmpty()) {
            built.witnessList.addScrollViewChild(muted(Text.of("backutils.admin.witness.none")));
            return;
        }

        List<String> shown = instance == null ? new ArrayList<>(counts.keySet())
                : instance.listedWitnesses(counts.keySet());
        if (shown.isEmpty()) {
            built.witnessList.addScrollViewChild(
                    muted(Text.of("backutils.admin.witness.no.match")));
            return;
        }

        boolean many = rows.size() > 1;
        for (String witness : shown) {
            int[] count = counts.get(witness);
            boolean canSee = count[1] == count[0];
            String name = many ? witness + "  (" + count[1] + "/" + count[0] + ")" : witness;

            MarkupLabel label = new MarkupLabel(List.of(name), LINE_STEP, canSee ? PLAIN : MUTED);
            label.withPrefix(AdminListStyle.checkbox(canSee), MARKER_GREY);
            if (!canSee) label.withPrefixSprite(HIDDEN_MARKER, HIDDEN_MARKER_SIZE);

            UIElement element = new UIElement();
            element.layout(l -> l.widthPercent(100)
                    .height(LINE_STEP + 4)
                    .paddingAll(2)
                    .flexDirection(FlexDirection.ROW));
            element.style(s -> s.background(new ColorRectTexture(ROW_IDLE)));
            element.addChild(label);

            element.addEventListener(UIEvents.MOUSE_DOWN, e -> {
                if (e.button != 0) return;
                if (instance != null) instance.toggleWitness(witness, canSee);
            }, true);
            built.witnessList.addScrollViewChild(element);
        }
    }

    /**
     * Hides or shows every selected entry this player witnessed.
     *
     * <p>A player who can see all of them is hidden from all of them; one who is missing some is
     * given the rest back, so a partly hidden witness is one click from being whole again rather than
     * one click further into being hidden. The server skips entries the player never witnessed, so
     * the ids sent are the ones the row's own witness list contains.
     */
    private void toggleWitness(String witness, boolean canSee) {
        List<Long> ids = new ArrayList<>();
        for (AdminLogPayload.Row row : selectedRows()) {
            if (!row.witnesses().contains(witness)) continue;
            boolean visible = !row.hiddenAll() && !row.hiddenFrom().contains(witness);
            if (canSee || !visible) ids.add(row.id());
        }
        if (ids.isEmpty()) return;

        sendBatch(new AdminLogBatchPayload(canSee
                ? AdminLogBatchPayload.Kind.HIDE_FROM_PLAYER
                : AdminLogBatchPayload.Kind.UNHIDE_FROM_PLAYER, ids, witness), true);
    }

    private static Label muted(String text) {
        Label label = new Label();
        label.setText(text, false);
        label.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false).adaptiveWidth(true));
        return label;
    }

    private static Button optionButton(Built built, String key, Runnable action) {
        Button button = new Button();
        translated(built, button.text, key);
        button.layout(l -> l.widthPercent(100).height(16));
        button.setOnClick(e -> action.run());
        return button;
    }

    // ------------------------------------------------------------------
    // The options
    // ------------------------------------------------------------------

    /** {@return the one selected entry's id}, or 0 when the selection is not exactly one */
    private static long idOrZero() {
        return instance == null || instance.selection.size() != 1 ? 0L
                : instance.selection.primary();
    }

    private static void send(AdminActionPayload payload) {
        if (payload.id() <= 0L) return;
        AdminNetwork.send(payload);
        // Re-asked rather than patched locally: the server decided what the access list now says.
        AdminMenuCache.refresh();
    }

    /** Sends a bulk action, and asks for the log again unless the caller has a reason not to. */
    private static void sendBatch(AdminLogBatchPayload payload, boolean refresh) {
        if (payload.ids().isEmpty()) return;
        AdminNetwork.sendBatch(payload);
        if (refresh) AdminMenuCache.refresh();
    }

    /**
     * Copies the selected entries, in the order the list draws them.
     *
     * <p>The note an administrator alone may read goes with its line, in brackets, as it did when one
     * entry could be copied at a time. The copied rows are lit for a moment, and the lit rows are the
     * ones that went to the clipboard.
     *
     * @return true when something was copied
     */
    private boolean copySelection() {
        List<AdminLogPayload.Row> rows = selectedRows();
        if (rows.isEmpty()) return false;

        List<String> lines = new ArrayList<>(rows.size());
        for (AdminLogPayload.Row row : rows) {
            String note = row.note();
            lines.add(note == null || note.isBlank()
                    ? row.text() : row.text() + " (" + note + ")");
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", lines));

        long now = System.nanoTime();
        for (AdminLogPayload.Row row : rows) copiedAt.put(row.id(), now);
        return true;
    }

    /**
     * {@return the selected rows in the order the list draws them}, then any a filter hides
     *
     * <p>A filter hides a row from view, not from the selection: what was selected before the filter
     * was typed is still what the buttons act on, and the copy of it is still a copy of it. The drawn
     * ones come first because that is the order the clipboard and the blink follow.
     */
    private List<AdminLogPayload.Row> selectedRows() {
        if (selection.isEmpty()) return List.of();

        List<AdminLogPayload.Row> rows = AdminMenuCache.rows();
        Map<Long, AdminLogPayload.Row> byId = new HashMap<>();
        for (AdminLogPayload.Row row : rows) byId.put(row.id(), row);

        List<AdminLogPayload.Row> chosen = new ArrayList<>();
        for (Long id : drawnIds) {
            if (!selection.contains(id)) continue;
            AdminLogPayload.Row row = byId.get(id);
            if (row != null) chosen.add(row);
        }
        for (AdminLogPayload.Row row : rows) {
            if (selection.contains(row.id()) && !chosen.contains(row)) chosen.add(row);
        }
        return chosen;
    }

    /** {@return the log rows the action-log filter currently shows} */
    private List<AdminLogPayload.Row> matchingRows(List<AdminLogPayload.Row> rows) {
        String filter = logFilterText().toLowerCase(java.util.Locale.ROOT);
        if (filter.isEmpty()) return rows;

        List<AdminLogPayload.Row> matching = new ArrayList<>();
        for (AdminLogPayload.Row row : rows) {
            // Markup is stripped before matching, so typing part of what is on screen finds the row.
            if (MarkupUtil.strip(row.text()).toLowerCase(java.util.Locale.ROOT).contains(filter)
                    || row.actor().toLowerCase(java.util.Locale.ROOT).contains(filter)
                    || matchesWitness(row, filter)) {
                matching.add(row);
            }
        }
        return matching;
    }

    /** {@return true when any witness of this entry matches the filter} */
    private static boolean matchesWitness(AdminLogPayload.Row row, String lowerCaseFilter) {
        for (String witness : row.witnesses()) {
            if (witness.toLowerCase(java.util.Locale.ROOT).contains(lowerCaseFilter)) return true;
        }
        return false;
    }

    /** {@return the action-log filter text, trimmed} */
    private String logFilterText() {
        String text = built.logFilter.getText();
        return text == null ? "" : text.trim();
    }

    // ------------------------------------------------------------------
    // The action list
    // ------------------------------------------------------------------

    private void rebuildList() {
        built.logList.clearAllScrollViewChildren();
        rowElements.clear();
        builtRevision = AdminMenuCache.revision();

        List<AdminLogPayload.Row> rows = AdminMenuCache.rows();
        List<Long> allIds = new ArrayList<>(rows.size());
        for (AdminLogPayload.Row row : rows) allIds.add(row.id());
        // A refresh can remove rows, and a selection kept as indexes would move to the row below it.
        selection.retainAll(allIds);
        selected = rowFor(selection.primary(), rows);

        if (rows.isEmpty()) {
            drawnIds = List.of();
            built.logList.addScrollViewChild(listNote("backutils.admin.log.empty"));
            showSelectionDetails();
            rebuildWitnesses(built, List.of());
            return;
        }

        List<AdminLogPayload.Row> matching = matchingRows(rows);
        drawnIds = new ArrayList<>(matching.size());
        for (AdminLogPayload.Row row : matching) drawnIds.add(row.id());

        if (matching.isEmpty()) {
            built.logList.addScrollViewChild(listNote("backutils.admin.log.no.match"));
            showSelectionDetails();
            rebuildWitnesses(built, selectedRows());
            return;
        }

        for (AdminLogPayload.Row row : matching) {
            built.logList.addScrollViewChild(buildRow(row));
        }
        paintRows();
        showSelectionDetails();
        rebuildWitnesses(built, selectedRows());

        // Snapped to the top, because the list is newest first. Deferred: the rows have no measured
        // position yet.
        scrollToNewestPending = true;
    }

    private static Label listNote(String key) {
        Label label = new Label();
        label.setText(Text.of(key), false);
        label.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false).adaptiveWidth(true));
        return label;
    }

    private static AdminLogPayload.Row rowFor(long id, List<AdminLogPayload.Row> rows) {
        if (id <= 0L) return null;
        for (AdminLogPayload.Row row : rows) {
            if (row.id() == id) return row;
        }
        return null;
    }

    private UIElement buildRow(AdminLogPayload.Row row) {
        List<String> wrapped = MarkupWrap.wrapByWidth(shownText(row), Math.max(40, listWidth - 40),
                line -> Minecraft.getInstance().font.width(MarkupUtil.strip(line)));

        MarkupLabel label = new MarkupLabel(wrapped, LINE_STEP, PLAIN);
        if (!row.visible()) {
            // The marker says the administrator would not normally be shown this entry.
            label.withPrefixSprite(HIDDEN_MARKER, HIDDEN_MARKER_SIZE);
        }

        UIElement element = new UIElement();
        element.layout(l -> l.widthPercent(100)
                .height(label.contentHeight() + 4)
                .paddingAll(2)
                .flexDirection(FlexDirection.ROW));
        element.addChild(label);
        rowElements.put(row.id(), element);
        paint(row.id(), element);

        element.addEventListener(UIEvents.MOUSE_ENTER, e -> {
            showHover(row);
            if (!selection.contains(row.id()) && !copiedAt.containsKey(row.id())) {
                element.style(s -> s.background(new ColorRectTexture(ROW_HOVER)));
            }
        }, true);
        element.addEventListener(UIEvents.MOUSE_LEAVE, e -> {
            showHover(null);
            paint(row.id(), element);
        }, true);
        element.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button != 0) return;
            onClickRow(row);
        });
        return element;
    }

    /** Paints one row from the selection, which is the state a hover must not overwrite. */
    private void paint(long id, UIElement element) {
        element.style(s -> s.background(new ColorRectTexture(
                selection.contains(id) ? ROW_SELECTED : ROW_IDLE)));
    }

    private void paintRows() {
        for (Map.Entry<Long, UIElement> entry : rowElements.entrySet()) {
            paint(entry.getKey(), entry.getValue());
        }
    }

    /** {@return the entry's text with the administrator's note after it, when there is one} */
    private static String shownText(AdminLogPayload.Row row) {
        return MarkupUtil.withNote(row.text(), row.note());
    }

    /** Selects a row, with the usual ctrl and shift meanings, or teleports on a plain double click. */
    private void onClickRow(AdminLogPayload.Row row) {
        long now = System.nanoTime();
        boolean plain = !Screen.hasControlDown() && !Screen.hasShiftDown();
        boolean doubleClick = plain && row.id() == lastClickId
                && now - lastClickAt < DOUBLE_CLICK_NANOS;
        lastClickAt = now;
        lastClickId = row.id();

        if (doubleClick && row.dimension() != null && !row.dimension().isBlank()) {
            send(AdminActionPayload.of(AdminActionPayload.Kind.TELEPORT_TO_ACTION, row.id()));
            return;
        }

        selection.click(row.id(), Screen.hasControlDown(), Screen.hasShiftDown(), drawnIds);
        selected = rowFor(selection.primary(), AdminMenuCache.rows());

        // A new selection disarms the delete button, so a confirmation cannot land on another row.
        deleteArmedAt = 0L;
        built.deleteButton.setText(Text.of("backutils.admin.option.delete.record"), false);

        paintRows();
        rebuildWitnesses(built, selectedRows());
        showSelectionDetails();
        showHover(row);
    }

    /** Writes what the options panel is about: one entry, several, or nothing. */
    private void showSelectionDetails() {
        int count = selection.size();
        if (count == 0) {
            showNothingSelected();
            return;
        }
        if (count > 1) {
            built.selectedActor.setText(
                    Text.of("backutils.admin.option.many.selected", count), false);
            built.selectedId.setText("", false);
            built.selectedPlace.setText(selected == null ? "" : placeOf(selected), false);
            return;
        }

        AdminLogPayload.Row row = selected;
        if (row == null) {
            showNothingSelected();
            return;
        }
        built.selectedActor.setText(row.actor(), false);
        built.selectedId.setText(Text.of("backutils.admin.option.id", row.id(),
                row.visible() ? "" : Text.of("backutils.admin.option.not.witness"),
                row.hiddenAll() ? Text.of("backutils.admin.option.hidden") : ""), false);
        built.selectedPlace.setText(placeOf(row), false);
    }

    private void showNothingSelected() {
        built.selectedActor.setText(Text.of("backutils.admin.option.hint"), false);
        built.selectedId.setText("", false);
        built.selectedPlace.setText("", false);
    }

    // ------------------------------------------------------------------
    // Screen
    // ------------------------------------------------------------------

    /**
     * Switches between the tabs. The options column and the log's strips belong to the log, so hiding
     * them is what gives the other tabs the width of the window, and the layout pass
     * {@link #init()} runs follows.
     */
    private void onTabChanged(Tab tab) {
        formSelected = tab != built.logTab;
        layoutAll();
        // Asked for when the tab is first opened as well as on the open of the menu: a reply can be
        // lost, and an empty config tab looks like a bug.
        if (tab == built.configTab && AdminConfigCache.rows().isEmpty()) {
            AdminConfigNetwork.request();
        }
    }

    @Override
    public void init() {
        // The tree is built once and kept, so the captions resolved as it was built are written
        // again here: without that they would hold the language the menu was first opened in.
        applyTexts();

        // Whatever tab was left selected is the one that opens: the screen is one reused instance,
        // so coming back from the editor lands where the edit started.
        formSelected = built.tabView.getSelectedTab() != built.logTab;
        layoutAll();
        // After the layout, because the config tab's explanations wrap to the width it just set, and
        // a language change between two opens has to rewrite them along with the labels and units.
        built.config.relabel();

        built.ui.setScreenAndInit(this);
        addRenderableWidget(built.ui.getWidget());
        super.init();
        setFocused(built.ui.getWidget());

        rebuildList();
    }

    /** Sizes everything, for the tab that is showing, as shares of a window with a floor so neither
     * column collapses. */
    private void layoutAll() {
        int contentWidth = Math.max(160, this.width - PAD * 2);
        int contentHeight = Math.max(80, this.height - PAD * 2);

        int optionsWidth = Math.max(OPTIONS_MIN_WIDTH,
                Math.round(contentWidth * OPTIONS_WIDTH_SHARE));
        boolean form = formSelected;

        // Over a form tab the options column is hidden and its room goes to the lists. The log keeps
        // its own width either way: its rows wrap to that width, and rebuilding them for a tab that
        // is not on screen is work for nothing.
        int columnWidth = form
                ? contentWidth
                : Math.max(80, contentWidth - optionsWidth - GAP);
        int headerHeight = form ? TITLE_ROW_HEIGHT : HEADER_HEIGHT;

        this.listWidth = Math.max(80, contentWidth - optionsWidth - GAP);
        int listHeight = Math.max(30, contentHeight - HEADER_HEIGHT
                - INNER_GAP - TAB_STRIP_HEIGHT);

        built.root.layout(l -> l.width(this.width).height(this.height).paddingAll(PAD));
        built.body.layout(l -> l.width(contentWidth).height(contentHeight)
                .flexDirection(FlexDirection.ROW).gapAll(GAP));
        built.listColumn.layout(l -> l.width(columnWidth).height(contentHeight)
                .flexDirection(FlexDirection.COLUMN).gapAll(INNER_GAP));
        built.optionsColumn.layout(l -> l.width(form ? 0 : optionsWidth).height(contentHeight)
                .flexDirection(FlexDirection.COLUMN).gapAll(INNER_GAP));
        built.header.layout(l -> l.width(columnWidth).height(headerHeight)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(GAP));
        built.logList.layout(l -> l.width(this.listWidth).height(listHeight));

        // Hidden and collapsed both: a hidden strip that kept its height would push the rest of the
        // column down. The options column is the same idea one size up, switched off as well as
        // narrowed, because a control of zero width is still a control the mouse can find.
        built.optionsColumn.setVisible(!form);
        built.details.setVisible(!form);
        built.details.layout(l -> l.flex(1).height(form ? 0 : HEADER_HEIGHT)
                .flexDirection(FlexDirection.COLUMN));
        built.logFilter.setVisible(!form);
        built.logFilter.layout(l -> l.width(form ? 0 : 120).height(14));

        int tabHeight = Math.max(40, contentHeight - headerHeight - INNER_GAP);
        built.tabView.layout(l -> l.width(columnWidth).height(tabHeight));

        // What the outer tab view adds around the content it mounts: its own strip above, and
        // ldlib2's five-pixel padding on every side.
        int panelWidth = Math.max(60, columnWidth - TAB_CONTENT_PAD * 2);
        int panelHeight = Math.max(60, tabHeight - TAB_STRIP_HEIGHT - TAB_CONTENT_PAD * 2);

        // The player tab is mounted directly, so it pays the strip and the padding once; inside it
        // the chosen player's detail has a tab view of its own, which the tab sizes for itself.
        built.players.layout(panelWidth, panelHeight);

        // The settings tab is mounted directly, so it pays the strip and the padding once.
        built.config.layout(panelWidth, panelHeight);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int alpha = (int) Math.round(Math.max(0.0D, Math.min(1.0D,
                BackUtilsClientConfig.getMenuBackgroundOpacity())) * 255.0D);
        graphics.fill(0, 0, this.width, this.height, (alpha << 24) | 0x00101016);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (AdminMenuCache.revision() != builtRevision) rebuildList();
        // Only the tab in view follows the server: the profile halves share one cache, and both
        // reading it would have them taking turns asking for a player they are not showing.
        syncVisibleTab();
        applyRowBlink();
        super.render(graphics, mouseX, mouseY, partialTick);
        applyPendingScroll();
    }

    /** Alternates the background of the rows that were copied, the usual copied acknowledgement. */
    private void applyRowBlink() {
        long now = System.nanoTime();
        for (var iterator = copiedAt.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            UIElement element = rowElements.get(entry.getKey());
            long elapsed = now - entry.getValue();

            if (elapsed >= AdminListStyle.BLINK_NANOS) {
                iterator.remove();
                if (element != null) paint(entry.getKey(), element);
                continue;
            }
            if (element == null) continue;

            boolean bright = (elapsed / AdminListStyle.BLINK_PHASE_NANOS) % 2L == 0L;
            int colour = bright ? AdminListStyle.ROW_COPIED
                    : (selection.contains(entry.getKey()) ? ROW_SELECTED : ROW_IDLE);
            element.style(s -> s.background(new ColorRectTexture(colour)));
        }
    }

    /** Brings the tab in view up to date with whatever the server last sent it. */
    private void syncVisibleTab() {
        Tab selected = built.tabView.getSelectedTab();
        if (selected == built.playersTab) {
            built.players.sync();
        } else if (selected == built.configTab) {
            built.config.sync();
        }
    }

    /**
     * Takes ctrl+C for the list in view, unless a text field has the keyboard.
     *
     * <p>Checked before the focused widget sees the key, which is why the text-field test matters:
     * an administrator typing in the filter must get its own copy behaviour, not a list copy that
     * would also swallow the keystroke.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_C && Screen.hasControlDown()) {
            if (built.tabView.getSelectedTab() == built.playersTab
                    && !built.players.typing() && built.players.copySelection()) {
                return true;
            }
            if (built.tabView.getSelectedTab() == built.logTab
                    && !built.logFilter.isFocused() && !built.filterField.isFocused()
                    && copySelection()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Scrolls the list to its newest entry, once the layout can answer for it: a scroll requested
     * while the rows are unmeasured does nothing.
     */
    private void applyPendingScroll() {
        if (!scrollToNewestPending) return;
        scrollToNewestPending = false;

        var children = built.logList.viewContainer.getChildren();
        if (!children.isEmpty() && built.logList.scrollToChild(children.getFirst())) return;
        built.logList.verticalScroller.setNormalizedValue(0.0f);
    }
}
