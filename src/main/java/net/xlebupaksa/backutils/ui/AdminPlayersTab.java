package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.network.AdminPlayerListPayload;
import net.xlebupaksa.backutils.network.AdminProfileCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The administrator's Players tab: choose a player, then read their chat profiles, their name
 * profiles and their memory.
 *
 * <p>The player list is deliberately above the three, rather than repeated inside each: the question
 * "who" is asked once, and what follows is about the player already chosen. That is also why the two
 * profile halves are pointed at a fixed player and lose their own player browsers — the browsing is
 * this panel's job now.
 *
 * <p>The list is the union of the chat and name lists the server sends. Both are already sent on every
 * request, and a player can be in one and not the other: somebody offline with a name profile has no
 * chat profile, and somebody offline with a chat profile has no name profile. Showing either alone
 * would hide half the players an administrator may need.
 */
@OnlyIn(Dist.CLIENT)
public final class AdminPlayersTab {

    private static final int PLAYER_ROW_HEIGHT = 14;
    private static final int SCOPE_HEIGHT = 10;
    private static final int FILTER_HEIGHT = 14;
    private static final int HEADER_HEIGHT = 16;
    private static final int GAP = 4;
    private static final int BACK_WIDTH = 50;
    private static final int TAB_STRIP_HEIGHT = 22;
    private static final int TAB_CONTENT_PAD = 5;

    private final UIElement root = new UIElement();
    private final UIElement browse = new UIElement();
    private final UIElement detail = new UIElement();

    private final Label count = new Label();
    private final TextField filter = new TextField();
    private final ScrollerView players = new ScrollerView();

    private final Button back = new Button();
    private final Label who = new Label();
    private final TabView tabs = new TabView();
    private final Tab chatHeader = new Tab();
    private final Tab namesHeader = new Tab();
    private final Tab memoryHeader = new Tab();
    private final AdminProfileTab chat = new AdminProfileTab(false);
    private final AdminProfileTab names = new AdminProfileTab(true);
    private final AdminMemoryPanel memory = new AdminMemoryPanel();

    /** The row elements of the player list, for their background repaint. */
    private final List<UIElement> playerRows = new ArrayList<>();

    private String selected = "";
    private int builtPlayersRevision = -1;
    private int listWidth = 200;

    public AdminPlayersTab() {
        build();
    }

    /** {@return the element to mount as this tab's content} */
    public UIElement root() {
        return root;
    }

    /** Sizes everything to the room the tab content has. */
    public void layout(int width, int height) {
        int w = Math.max(80, width);
        int h = Math.max(60, height);
        listWidth = w;

        root.layout(l -> l.width(w).height(h));
        browse.layout(l -> l.width(w).height(h).flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        int listHeight = Math.max(20, h - SCOPE_HEIGHT - FILTER_HEIGHT - GAP * 2);
        count.layout(l -> l.width(w).height(SCOPE_HEIGHT));
        filter.layout(l -> l.width(w).height(FILTER_HEIGHT));
        players.layout(l -> l.width(w).height(listHeight));

        detail.layout(l -> l.width(w).height(h).flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        int tabsHeight = Math.max(40, h - HEADER_HEIGHT - GAP);
        tabs.layout(l -> l.width(w).height(tabsHeight));
        back.layout(l -> l.width(BACK_WIDTH).height(14));
        who.layout(l -> l.flex(1).height(HEADER_HEIGHT));

        // What a tab view adds around its content: its strip at the top and ldlib2's own five-pixel
        // padding on every side.
        int innerWidth = Math.max(40, w - TAB_CONTENT_PAD * 2);
        int innerHeight = Math.max(40, tabsHeight - TAB_STRIP_HEIGHT - TAB_CONTENT_PAD * 2);
        chat.layout(innerWidth, innerHeight);
        names.layout(innerWidth, innerHeight);
        memory.layout(innerWidth, innerHeight);
    }

    /** Brings the list, or the open player, up to date with whatever the server last sent. */
    public void sync() {
        if (selected.isEmpty()) {
            if (AdminProfileCache.playersRevision() != builtPlayersRevision) rebuildPlayers();
            return;
        }
        // Only the half in view follows the server. The two profile panels share one cache, and both
        // reading it would have them taking turns asking for the kind the other one is not showing,
        // which rebuilds the visible list with the other kind's rows on every round trip.
        Tab shown = tabs.getSelectedTab();
        if (shown == namesHeader) {
            names.sync();
        } else if (shown == chatHeader) {
            chat.sync();
        } else {
            memory.sync();
        }
    }

    /** {@return true while a text field in this tab has the keyboard, so ctrl+C must not take it} */
    public boolean typing() {
        return filter.isFocused() || memory.typing();
    }

    /** Writes the captions again in the language the client is set to now. */
    public void relabel() {
        back.setText(Text.of("backutils.profiles.back"), false);
        chatHeader.text.setText(Text.of("backutils.admin.tab.chat"), false);
        namesHeader.text.setText(Text.of("backutils.admin.tab.names"), false);
        memoryHeader.text.setText(Text.of("backutils.admin.tab.memory"), false);
        filter.textFieldStyle(s -> s.placeholder(
                Component.translatable("backutils.profiles.search.players")));
        chat.relabel();
        names.relabel();
        memory.relabel();
    }

    /** {@return true when the memory tab is in view and copied its selection} */
    public boolean copySelection() {
        return tabs.getSelectedTab() == memoryHeader && memory.copySelection();
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private void build() {
        count.setText("", false);
        count.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));

        filter.setText("", false);
        filter.layout(l -> l.height(FILTER_HEIGHT));
        filter.setTextResponder(text -> rebuildPlayers());
        filter.textFieldStyle(s -> s.placeholder(
                Component.translatable("backutils.profiles.search.players")));

        players.scrollerStyle(s -> s
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .adaptiveWidth(false)
                .adaptiveHeight(false));
        players.viewContainer(v -> v.getLayout().gapAll(1));

        browse.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        browse.addChildren(count, filter, players);

        back.setText(Text.of("backutils.profiles.back"), false);
        back.layout(l -> l.width(BACK_WIDTH).height(14));
        back.setOnClick(e -> showPlayers());

        who.setText("", false);
        who.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.PLAIN).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        who.layout(l -> l.flex(1).height(HEADER_HEIGHT));

        UIElement header = new UIElement();
        header.layout(l -> l.widthPercent(100).height(HEADER_HEIGHT)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(GAP));
        header.addChildren(back, who);

        chatHeader.text.setText(Text.of("backutils.admin.tab.chat"), false);
        namesHeader.text.setText(Text.of("backutils.admin.tab.names"), false);
        memoryHeader.text.setText(Text.of("backutils.admin.tab.memory"), false);
        tabs.tabContentContainer(c -> c.style(s -> s.backgroundTexture(IGuiTexture.EMPTY)));
        tabs.addTab(chatHeader, chat.root());
        tabs.addTab(namesHeader, names.root());
        tabs.addTab(memoryHeader, memory.root());
        // The same trailing "+" entry the client's own lists have: an administrator may start a
        // profile for this player, and the editor closes back into this menu.
        chat.setCreateRow(true, null);
        names.setCreateRow(true, null);

        detail.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        detail.addChildren(header, tabs);
        // display:none, not visibility:hidden: the hidden half must not keep taking the whole panel's
        // height in the layout, which is what pushed the open player's header to the bottom.
        detail.setDisplay(false);
        root.layout(l -> l.flexDirection(FlexDirection.COLUMN));

        root.addChildren(browse, detail);
        rebuildPlayers();
    }

    // ------------------------------------------------------------------
    // Walking between the two scopes
    // ------------------------------------------------------------------

    private void showPlayer(String name) {
        selected = name;
        who.setText(name, false);
        // Fixed targets: the panels show this player's profiles without asking who again.
        chat.setTarget(name);
        names.setTarget(name);
        memory.setPlayer(name);
        browse.setDisplay(false);
        detail.setDisplay(true);
    }

    private void showPlayers() {
        selected = "";
        memory.setPlayer("");
        browse.setDisplay(true);
        detail.setDisplay(false);
        rebuildPlayers();
    }

    // ------------------------------------------------------------------
    // The player list
    // ------------------------------------------------------------------

    private void rebuildPlayers() {
        builtPlayersRevision = AdminProfileCache.playersRevision();
        players.clearAllScrollViewChildren();
        playerRows.clear();

        List<AdminPlayerListPayload.Player> all = mergedPlayers();
        count.setText(Text.of(all.size() == 1
                ? "backutils.profiles.player.count"
                : "backutils.profiles.player.count.plural", all.size()), false);

        String needle = filterText();
        int shown = 0;
        for (AdminPlayerListPayload.Player entry : all) {
            if (!needle.isEmpty()
                    && !entry.name().toLowerCase(Locale.ROOT).contains(needle)) continue;
            players.addScrollViewChild(buildPlayerRow(entry));
            shown++;
        }

        if (all.isEmpty()) {
            players.addScrollViewChild(muted(Text.of("backutils.profiles.no.players.yet")));
        } else if (shown == 0) {
            players.addScrollViewChild(muted(Text.of("backutils.profiles.no.player.match")));
        }
    }

    /**
     * {@return both player lists as one}, online players first, then alphabetically
     *
     * <p>Merged by name without regard to case, because the server writes names as the database or
     * the account spells them and the same person must not be offered twice. Either list's row
     * already carries both counts, so whichever is met first is the row used.
     */
    private List<AdminPlayerListPayload.Player> mergedPlayers() {
        Map<String, AdminPlayerListPayload.Player> merged =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (AdminPlayerListPayload.Player player : AdminProfileCache.players(false)) {
            merged.put(player.name(), player);
        }
        for (AdminPlayerListPayload.Player player : AdminProfileCache.players(true)) {
            merged.putIfAbsent(player.name(), player);
        }

        List<AdminPlayerListPayload.Player> list = new ArrayList<>(merged.values());
        list.sort(Comparator.comparing(AdminPlayerListPayload.Player::online).reversed()
                .thenComparing(AdminPlayerListPayload.Player::name,
                        String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private String filterText() {
        String text = filter.getText();
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private UIElement buildPlayerRow(AdminPlayerListPayload.Player entry) {
        Label name = new Label();
        name.setText(entry.name(), false);
        name.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.PLAIN).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        // An explicit height, because an ldlib2 label defaults to nine pixels rather than to its line.
        name.layout(l -> l.height(PLAYER_ROW_HEIGHT));

        Label detail = new Label();
        detail.setText(Text.of("backutils.profiles.player.detail",
                Text.of(entry.online()
                        ? "backutils.profiles.online" : "backutils.profiles.offline"),
                entry.chatCount(), entry.nameCount()), false);
        detail.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        detail.layout(l -> l.height(PLAYER_ROW_HEIGHT));

        UIElement row = new UIElement();
        row.layout(l -> l.widthPercent(100).height(PLAYER_ROW_HEIGHT + 4).paddingAll(2)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER));
        row.style(s -> s.background(new ColorRectTexture(AdminListStyle.ROW_IDLE)));
        row.addChildren(name, detail);
        playerRows.add(row);

        row.addEventListener(UIEvents.MOUSE_ENTER, e ->
                row.style(s -> s.background(new ColorRectTexture(AdminListStyle.ROW_HOVER))), true);
        row.addEventListener(UIEvents.MOUSE_LEAVE, e -> paintPlayers(), true);
        row.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button != 0) return;
            showPlayer(entry.name());
        });
        return row;
    }

    private void paintPlayers() {
        for (UIElement row : playerRows) {
            row.style(s -> s.background(new ColorRectTexture(AdminListStyle.ROW_IDLE)));
        }
    }

    private static Label muted(String text) {
        Label label = new Label();
        label.setText(text, false);
        label.textStyle(t -> t.fontSize(9f).textColor(AdminListStyle.MUTED).textShadow(false)
                .adaptiveWidth(true));
        return label;
    }
}
