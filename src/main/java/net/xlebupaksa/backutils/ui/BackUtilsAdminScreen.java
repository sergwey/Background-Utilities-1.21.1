package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
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
import net.xlebupaksa.backutils.network.AdminNetwork;
import net.xlebupaksa.backutils.network.AdminProfileNetwork;

import java.util.ArrayList;
import java.util.List;

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
        Button refresh;
        UIElement header;
        UIElement details;
        TabView tabView;
        Tab logTab;
        Tab profilesTab;
        Tab configTab;
        TabView profileTabs;
        Tab chatTabHeader;
        Tab namesTabHeader;
        AdminProfileTab chatTab;
        AdminProfileTab nameTab;
        AdminConfigTab config;
        /** The three lines describing the hovered entry, carried in the header row. */
        Label hoverDate;
        Label hoverActor;
        Label hoverPlace;
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
    private final List<UIElement> rowElements = new ArrayList<>();
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

        // The profile tab is itself a tab view, so the two halves answer the same question about two
        // kinds of profile.
        built.chatTab = new AdminProfileTab(false);
        built.nameTab = new AdminProfileTab(true);

        built.chatTabHeader = translatedTab(built, "backutils.admin.tab.chat");
        built.namesTabHeader = translatedTab(built, "backutils.admin.tab.names");
        built.profileTabs = new TabView();
        // The inner tab view keeps its strip and gives up its panel border: the outer view already
        // draws a box.
        built.profileTabs.tabContentContainer(
                c -> c.style(s -> s.backgroundTexture(IGuiTexture.EMPTY)));
        built.profileTabs.addTab(built.chatTabHeader, built.chatTab.root());
        built.profileTabs.addTab(built.namesTabHeader, built.nameTab.root());

        built.profilesTab = translatedTab(built, "backutils.admin.tab.profiles");
        tabView.addTab(built.profilesTab, built.profileTabs);

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

        Label witnessesHeading = new Label();
        translated(built, witnessesHeading, "backutils.admin.witness.heading");
        witnessesHeading.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false)
                .adaptiveWidth(true).adaptiveHeight(true));

        // The filter does double duty: part of a name narrows the list, while a selector such as
        // @a[distance=..10] goes to the server, which resolves it with the administrator as source.
        built.filterField = new TextField();
        built.filterField.setText("");
        built.filterField.layout(l -> l.widthPercent(100).height(14));
        built.filterField.textFieldStyle(s -> s.placeholder(
                Component.translatable("backutils.admin.witness.search")));
        built.filterField.setTextResponder(text ->
                rebuildWitnesses(built, instance == null ? null : instance.selected));

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

        Button hideListed = optionButton(built, "backutils.admin.option.hide.listed",
                () -> sendMany(AdminActionPayload.Kind.HIDE_FROM_MANY));
        Button showListed = optionButton(built, "backutils.admin.option.show.listed",
                () -> sendMany(AdminActionPayload.Kind.UNHIDE_FROM_MANY));

        Button copy = optionButton(built, "backutils.admin.option.copy.text",
                BackUtilsAdminScreen::copySelected);
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
                built.deleteButton,
                witnessesHeading, hideListed, showListed, built.filterField, built.witnessList);

        rebuildWitnesses(built, null);
    }

    /**
     * Sends a hide or unhide for everything the filter shows, with a selector passed through as-is:
     * only the server can resolve one.
     */
    private static void sendMany(AdminActionPayload.Kind kind) {
        if (instance == null || instance.selected == null) return;

        String filter = instance.filterText();
        String target = filter.startsWith("@") ? filter : String.join(";", instance.listedWitnesses());
        if (target.isBlank()) return;

        send(new AdminActionPayload(kind, instance.selected.id(), target));
    }

    /** Deletes the selected record, on a second click: hiding is reversible, deleting is not. */
    private static void onDeleteClicked() {
        if (instance == null || instance.selected == null) return;

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
        send(AdminActionPayload.of(AdminActionPayload.Kind.DELETE_RECORD, instance.selected.id()));
    }

    /** {@return the filter text, trimmed} */
    private String filterText() {
        String text = built.filterField.getText();
        return text == null ? "" : text.trim();
    }

    /** {@return the witnesses the filter currently shows} */
    private List<String> listedWitnesses() {
        if (selected == null) return List.of();
        String filter = filterText().toLowerCase(java.util.Locale.ROOT);
        // A selector cannot be matched against names locally, so everything stays listed.
        if (filter.isEmpty() || filter.startsWith("@")) return selected.witnesses();

        List<String> shown = new ArrayList<>();
        for (String witness : selected.witnesses()) {
            if (witness.toLowerCase(java.util.Locale.ROOT).contains(filter)) shown.add(witness);
        }
        return shown;
    }

    /**
     * Fills the witness list for the selected entry, with {@link MarkupLabel} rather than buttons:
     * the marker is a sprite, which ldlib2's own text element cannot draw.
     */
    private static void rebuildWitnesses(Built built, AdminLogPayload.Row row) {
        built.witnessList.clearAllScrollViewChildren();
        if (row == null) {
            built.witnessList.addScrollViewChild(
                    muted(Text.of("backutils.admin.witness.none.selected")));
            return;
        }
        if (row.witnesses().isEmpty()) {
            built.witnessList.addScrollViewChild(muted(Text.of("backutils.admin.witness.none")));
            return;
        }

        List<String> shown = instance == null ? row.witnesses() : instance.listedWitnesses();
        if (shown.isEmpty()) {
            built.witnessList.addScrollViewChild(
                    muted(Text.of("backutils.admin.witness.no.match")));
            return;
        }

        for (String witness : shown) {
            // While it is hidden from everyone, every witness counts as withheld.
            boolean canSee = !row.hiddenAll() && !row.hiddenFrom().contains(witness);

            MarkupLabel label = new MarkupLabel(List.of(witness), LINE_STEP,
                    canSee ? PLAIN : MUTED);
            label.withPrefix(checkbox(canSee), MARKER_GREY);
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
                send(new AdminActionPayload(
                        canSee ? AdminActionPayload.Kind.HIDE_FROM_PLAYER
                               : AdminActionPayload.Kind.UNHIDE_FROM_PLAYER,
                        row.id(), witness));
            }, true);
            built.witnessList.addScrollViewChild(element);
        }
    }

    /** {@return the tick box in front of a witness, with the marker when they cannot see it} */
    private static Component checkbox(boolean canSee) {
        return Component.literal(canSee ? "[x] " : "[ ] ");
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

    private static long idOrZero() {
        return instance == null || instance.selected == null ? 0L : instance.selected.id();
    }

    private static void send(AdminActionPayload payload) {
        if (payload.id() <= 0L) return;
        AdminNetwork.send(payload);
        // Re-asked rather than patched locally: the server decided what the access list now says.
        AdminMenuCache.refresh();
    }

    private static void copySelected() {
        if (instance == null || instance.selected == null) return;
        String note = instance.selected.note();
        String text = instance.selected.text();
        Minecraft.getInstance().keyboardHandler.setClipboard(note == null || note.isBlank()
                ? text : text + " (" + note + ")");
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
        if (rows.isEmpty()) {
            Label empty = new Label();
            empty.setText(Text.of("backutils.admin.log.empty"), false);
            empty.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false).adaptiveWidth(true));
            built.logList.addScrollViewChild(empty);
            return;
        }

        List<AdminLogPayload.Row> matching = matchingRows(rows);
        if (matching.isEmpty()) {
            Label none = new Label();
            none.setText(Text.of("backutils.admin.log.no.match"), false);
            none.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false).adaptiveWidth(true));
            built.logList.addScrollViewChild(none);
            return;
        }

        for (AdminLogPayload.Row row : matching) {
            built.logList.addScrollViewChild(buildRow(row));
        }

        // Re-resolved by id after a refresh, which keeps the options panel in step with the server.
        if (selected != null) {
            AdminLogPayload.Row updated = null;
            for (AdminLogPayload.Row candidate : rows) {
                if (candidate.id() == selected.id()) {
                    updated = candidate;
                    break;
                }
            }
            selected = updated;
            rebuildWitnesses(built, updated);
            if (updated != null) showSelectionDetails(updated);
            else {
                // On the leading line, which is where an answer is looked for.
                built.selectedActor.setText(Text.of("backutils.admin.option.gone"), false);
                built.selectedId.setText("", false);
                built.selectedPlace.setText("", false);
            }
        }

        // Snapped to the top, because the list is newest first. Deferred: the rows have no measured
        // position yet.
        scrollToNewestPending = true;
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
        element.style(s -> s.background(new ColorRectTexture(ROW_IDLE)));
        element.addChild(label);
        rowElements.add(element);

        element.addEventListener(UIEvents.MOUSE_ENTER, e -> {
            showHover(row);
            if (selected == null || selected.id() != row.id()) {
                element.style(s -> s.background(new ColorRectTexture(ROW_HOVER)));
            }
        }, true);
        element.addEventListener(UIEvents.MOUSE_LEAVE, e -> {
            showHover(null);
            if (selected == null || selected.id() != row.id()) {
                element.style(s -> s.background(new ColorRectTexture(ROW_IDLE)));
            }
        }, true);
        element.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button != 0) return;
            onClickRow(row, element);
        });
        return element;
    }

    /** {@return the entry's text with the administrator's note after it, when there is one} */
    private static String shownText(AdminLogPayload.Row row) {
        return MarkupUtil.withNote(row.text(), row.note());
    }

    /** Selects a row, or teleports when the same row is clicked twice in quick succession. */
    private void onClickRow(AdminLogPayload.Row row, UIElement element) {
        long now = System.nanoTime();
        boolean doubleClick = row.id() == lastClickId && now - lastClickAt < DOUBLE_CLICK_NANOS;
        lastClickAt = now;
        lastClickId = row.id();

        if (doubleClick && row.dimension() != null && !row.dimension().isBlank()) {
            send(AdminActionPayload.of(AdminActionPayload.Kind.TELEPORT_TO_ACTION, row.id()));
            return;
        }

        selected = row;
        for (UIElement other : rowElements) {
            other.style(s -> s.background(new ColorRectTexture(ROW_IDLE)));
        }
        element.style(s -> s.background(new ColorRectTexture(ROW_SELECTED)));

        // A new selection disarms the delete button, so a confirmation cannot land on another row.
        deleteArmedAt = 0L;
        built.deleteButton.setText(Text.of("backutils.admin.option.delete.record"), false);

        rebuildWitnesses(built, row);
        showSelectionDetails(row);
        showHover(row);
    }

    /** Writes the selected entry's three lines, the name first as above the log. */
    private void showSelectionDetails(AdminLogPayload.Row row) {
        built.selectedActor.setText(row.actor(), false);
        built.selectedId.setText(Text.of("backutils.admin.option.id", row.id(),
                row.visible() ? "" : Text.of("backutils.admin.option.not.witness"),
                row.hiddenAll() ? Text.of("backutils.admin.option.hidden") : ""), false);
        built.selectedPlace.setText(placeOf(row), false);
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

    /** {@return the profile half that is showing, which is the only one that follows the server} */
    private AdminProfileTab selectedProfileTab() {
        return built.profileTabs.getSelectedTab() == built.namesTabHeader
                ? built.nameTab : built.chatTab;
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

        // The profile tab is a tab view inside a tab view, so its content pays the strip and the
        // padding twice.
        int panelWidth = Math.max(60, columnWidth - TAB_CONTENT_PAD * 2);
        int panelHeight = Math.max(60, tabHeight - TAB_STRIP_HEIGHT - TAB_CONTENT_PAD * 2);
        built.profileTabs.layout(l -> l.width(panelWidth).height(panelHeight));

        int subWidth = Math.max(40, panelWidth - TAB_CONTENT_PAD * 2);
        int subHeight = Math.max(40, panelHeight - TAB_STRIP_HEIGHT - TAB_CONTENT_PAD * 2);
        built.chatTab.layout(subWidth, subHeight);
        built.nameTab.layout(subWidth, subHeight);

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
        super.render(graphics, mouseX, mouseY, partialTick);
        applyPendingScroll();
    }

    /** Brings the tab in view up to date with whatever the server last sent it. */
    private void syncVisibleTab() {
        Tab selected = built.tabView.getSelectedTab();
        if (selected == built.profilesTab) {
            selectedProfileTab().sync();
        } else if (selected == built.configTab) {
            built.config.sync();
        }
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
