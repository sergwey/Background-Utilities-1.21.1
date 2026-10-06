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
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.data.MarkupWrap;
import net.xlebupaksa.backutils.data.ProfileOptions;
import net.xlebupaksa.backutils.network.AdminPlayerListPayload;
import net.xlebupaksa.backutils.network.AdminProfileCache;
import net.xlebupaksa.backutils.network.AdminProfileEditPayload;
import net.xlebupaksa.backutils.network.AdminProfileFeedbackCache;
import net.xlebupaksa.backutils.network.AdminProfileNetwork;
import net.xlebupaksa.backutils.network.ProfileListPayload;
import net.xlebupaksa.backutils.profile.ProfileMarkup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One half of the background menu's profile tab: either the chat profiles or the name profiles. */
@OnlyIn(Dist.CLIENT)
public final class AdminProfileTab {

    private static final int ROW_IDLE = 0x44101016;
    private static final int ROW_HOVER = 0x883A4658;
    private static final int ROW_IN_USE = 0x664E7FB8;
    private static final int MUTED = 0xFF9AA3B2;
    private static final int PLAIN = 0xFFE2E8F0;
    private static final int WARNING = 0xFFFF8080;
    private static final int GOOD = 0xFF8FD98F;

    private static final int LINE_STEP = 10;
    /** The height of the strip with Back, the scope and New profile on it. */
    private static final int BAR_HEIGHT = 16;
    /**
     * Widths that hold the Russian captions: {@code "< Назад"} and {@code "Новый профиль"} are
     * wider than {@code "< Back"} and {@code "New profile"} at the same font size.
     */
    private static final int BACK_WIDTH = 50;
    private static final int CREATE_WIDTH = 88;
    /** One player's line, inside its own padded row. */
    private static final int PLAYER_ROW_HEIGHT = 14;
    private static final int FILTER_HEIGHT = 14;
    private static final int STATUS_HEIGHT = 10;
    private static final int GAP = 4;

    private static final long DOUBLE_CLICK_NANOS = 400_000_000L;
    private static final long DELETE_CONFIRM_NANOS = 5_000_000_000L;

    /** What {@code {m}} and {@code {player}} are replaced with in a preview line. */
    private static String messageSample() {
        return Text.of("backutils.profiles.message.sample");
    }

    /** A profile row: what it says, its Delete button, and whether it is the one in use. */
    private static final class ProfileRow {
        final UIElement element;
        final ProfileListPayload.Row data;
        final Button deleteButton;

        ProfileRow(UIElement element, ProfileListPayload.Row data, Button deleteButton) {
            this.element = element;
            this.data = data;
            this.deleteButton = deleteButton;
        }
    }

    /** True for the Names half, false for Chat. */
    private final boolean names;

    /** The player this panel is fixed to, or "" when it browses every player. */
    private String fixedTarget = "";

    /** Which menu opened the editor on these profiles, and therefore where closing it goes. */
    private final boolean forAdmin;

    private final UIElement root = new UIElement();
    private final UIElement bar = new UIElement();
    private final Button back = new Button();
    private final Label scope = new Label();
    private final Button create = new Button();
    private final TextField filter = new TextField();
    private final ScrollerView list = new ScrollerView();
    private final Label status = new Label();

    /** The player whose profiles are showing, or "" while the player list is. */
    private String player = "";
    /** The player a profiles request is outstanding for, so it is asked for once. */
    private String requested = "";
    /** Every row currently drawn, for the background repaint. */
    private final List<UIElement> rowElements = new ArrayList<>();
    /** The profile rows, which are the ones that can be tinted as in use. */
    private final List<ProfileRow> profileRows = new ArrayList<>();

    private int builtPlayersRevision = -1;
    private int builtProfilesRevision = -1;
    private int builtFeedbackRevision = -1;

    /** The row whose Delete button is armed, or "" when none is. */
    private String armed = "";
    private long armedAt;
    /** The profile last clicked, for the double-click shortcut. */
    private String lastClick = "";
    private long lastClickAt;

    /** The width the list is drawn at, which is what the rows wrap to. */
    private int listWidth = 200;

    /** A panel that browses every player, for the background menu. */
    public AdminProfileTab(boolean names) {
        this.names = names;
        this.forAdmin = true;
        build();
    }

    /** A panel fixed to one player, for the menu that has only one to show. */
    public AdminProfileTab(boolean names, String target) {
        this.names = names;
        this.forAdmin = false;
        this.fixedTarget = target == null ? "" : target;
        this.player = this.fixedTarget;
        build();
    }

    /** Points a fixed panel at a player, on every open: the panel is reused and the target changes. */
    public void setTarget(String target) {
        String wanted = target == null ? "" : target;
        if (wanted.equals(fixedTarget) && !wanted.isEmpty()) return;

        fixedTarget = wanted;
        player = wanted;
        requested = "";
        armed = "";
        builtProfilesRevision = -1;
        filter.setText("", false);
        applyScope();
        if (player.isEmpty()) rebuildPlayers(); else rebuildCurrent();
    }

    /**
     * Writes this panel's own captions again in the language the client is set to now. The menu
     * that holds one is built once and kept, so a caption flattened as it was built would hold
     * whichever language was in force then for the rest of the session.
     */
    public void relabel() {
        back.setText(Text.of("backutils.profiles.back"), false);
        create.setText(Text.of("backutils.profiles.new.profile"), false);
        // The filter's hint and the scope line are keys as well, and both are written there.
        applyScope();
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private void build() {
        back.setText(Text.of("backutils.profiles.back"), false);
        back.layout(l -> l.width(BACK_WIDTH).height(14));
        back.setOnClick(e -> showPlayers());

        scope.setText("", false);
        scope.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        scope.layout(l -> l.flex(1).height(BAR_HEIGHT));

        create.setText(Text.of("backutils.profiles.new.profile"), false);
        create.layout(l -> l.width(CREATE_WIDTH).height(14));
        create.setOnClick(e -> create());

        bar.layout(l -> l.flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER)
                .gapAll(GAP));
        bar.addChildren(back, scope, create);

        filter.setText("");
        filter.layout(l -> l.height(FILTER_HEIGHT));
        filter.setTextResponder(text -> rebuildCurrent());

        list.scrollerStyle(s -> s
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .adaptiveWidth(false)
                .adaptiveHeight(false));
        list.viewContainer(v -> v.getLayout().gapAll(1));

        status.setText("", false);
        status.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));

        root.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        root.addChildren(bar, filter, list, status);

        // The panel opens on the player list, so the open-player controls start hidden rather than drawn.
        applyScope();
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

        // The list takes what is left: the other strips are fixed line counts and this one scrolls.
        int listHeight = Math.max(20, h - BAR_HEIGHT - FILTER_HEIGHT - STATUS_HEIGHT - GAP * 3);

        root.layout(l -> l.width(w).height(h).flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        bar.layout(l -> l.width(w).height(BAR_HEIGHT).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(GAP));
        filter.layout(l -> l.width(w).height(FILTER_HEIGHT));
        list.layout(l -> l.width(w).height(listHeight));
        status.layout(l -> l.width(w).height(STATUS_HEIGHT));
    }

    // ------------------------------------------------------------------
    // Following the server
    // ------------------------------------------------------------------

    /** Brings the panel up to date with the server, rebuilding only when a revision changed. */
    public void sync() {
        if (player.isEmpty()) {
            if (AdminProfileCache.playersRevision() != builtPlayersRevision) rebuildPlayers();
        } else if (AdminProfileCache.holds(names, player)) {
            requested = "";
            if (AdminProfileCache.profilesRevision() != builtProfilesRevision) rebuildProfiles();
        } else {
            // The rebuild follows the revision the reply sets.
            ask();
        }
        showFeedback();
    }

    /** Asks for the open player's profiles, unless they are already on their way. */
    private void ask() {
        if (player.equals(requested)) return;
        requested = player;
        AdminProfileNetwork.requestProfiles(names, player);
    }

    // ------------------------------------------------------------------
    // Walking between the two scopes
    // ------------------------------------------------------------------

    /** Opens a player: their profiles replace the player list. */
    private void showProfiles(String target) {
        player = target;
        requested = "";
        armed = "";
        filter.setText("", false);
        // Rebuilt from whatever is held, even when that is nothing yet.
        builtProfilesRevision = -1;
        applyScope();
        ask();
        rebuildCurrent();
    }

    /** Goes back to the player list. */
    private void showPlayers() {
        player = "";
        requested = "";
        armed = "";
        filter.setText("", false);
        applyScope();
        rebuildPlayers();
    }

    /** Shows or hides the controls that only make sense once a player is open. */
    private void applyScope() {
        boolean inside = !player.isEmpty();
        // A fixed panel has nobody to go back to and no player to choose, so Back and Create are
        // hidden: they are the browsing it does not do.
        boolean browsing = fixedTarget.isEmpty();
        boolean showBack = inside && browsing;
        boolean showCreate = inside && browsing;

        back.setVisible(showBack);
        back.layout(l -> l.width(showBack ? BACK_WIDTH : 0).height(14));
        create.setVisible(showCreate);
        create.layout(l -> l.width(showCreate ? CREATE_WIDTH : 0).height(14));
        filter.textFieldStyle(s -> s.placeholder(Component.translatable(
                inside ? "backutils.profiles.search.profiles"
                        : "backutils.profiles.search.players")));
        setScopeText();
    }

    private void setScopeText() {
        if (player.isEmpty()) {
            int count = AdminProfileCache.players(names).size();
            scope.setText(Text.of(count == 1 ? "backutils.profiles.player.count"
                    : "backutils.profiles.player.count.plural", count), false);
            return;
        }
        if (!AdminProfileCache.holds(names, player)) {
            // Nothing about this player's profiles is known yet, so the line names them and no more.
            scope.setText(player, false);
            return;
        }
        int count = AdminProfileCache.profiles().size();
        scope.setText(player + "  -  " + (count == 0
                ? Text.of("backutils.profiles.no.profiles")
                : Text.of(count == 1 ? "backutils.profiles.profile.count"
                        : "backutils.profiles.profile.count.plural", count)), false);
    }

    // ------------------------------------------------------------------
    // The lists
    // ------------------------------------------------------------------

    private void rebuildCurrent() {
        if (player.isEmpty()) rebuildPlayers(); else rebuildProfiles();
    }

    private void rebuildPlayers() {
        builtPlayersRevision = AdminProfileCache.playersRevision();
        clearRows();
        setScopeText();

        String needle = filterText();
        List<AdminPlayerListPayload.Player> all = AdminProfileCache.players(names);
        if (all.isEmpty()) {
            list.addScrollViewChild(muted(Text.of("backutils.profiles.no.players.yet")));
            return;
        }

        int shown = 0;
        for (AdminPlayerListPayload.Player entry : all) {
            if (!needle.isEmpty()
                    && !entry.name().toLowerCase(Locale.ROOT).contains(needle)) continue;
            list.addScrollViewChild(buildPlayerRow(entry));
            shown++;
        }
        if (shown == 0) {
            list.addScrollViewChild(muted(Text.of("backutils.profiles.no.player.match")));
        }
        setScopeText();
    }

    private void rebuildProfiles() {
        clearRows();
        if (!AdminProfileCache.holds(names, player)) {
            // Either the request is out or it was refused: better to say so than to show stale data.
            list.addScrollViewChild(muted(Text.of("backutils.profiles.loading")));
            setScopeText();
            return;
        }
        builtProfilesRevision = AdminProfileCache.profilesRevision();

        List<ProfileListPayload.Row> all = new ArrayList<>();
        // default is listed although it is the absence of a profile: it is still a choice to offer.
        all.add(new ProfileListPayload.Row(ProfileOptions.DEFAULT_PROFILE, placeholder(), "",
                AdminProfileCache.profiles().stream()
                        .noneMatch(ProfileListPayload.Row::active)));
        all.addAll(AdminProfileCache.profiles());

        String needle = filterText();
        int shown = 0;
        for (ProfileListPayload.Row row : all) {
            if (!needle.isEmpty() && !matches(row, needle)) continue;
            list.addScrollViewChild(buildProfileRow(row));
            shown++;
        }
        if (shown == 0) {
            list.addScrollViewChild(muted(Text.of("backutils.profiles.no.profile.match")));
        }
        setScopeText();
    }

    private void clearRows() {
        list.clearAllScrollViewChildren();
        rowElements.clear();
        profileRows.clear();
        // The armed confirmation's button is gone, so the next Delete asks again instead.
        armed = "";
    }

    /** {@return true when this profile answers the filter, by name or by what it draws} */
    private boolean matches(ProfileListPayload.Row row, String needle) {
        if (row.name().toLowerCase(Locale.ROOT).contains(needle)) return true;
        String preview = MarkupUtil.strip(previewOf(row)).toLowerCase(Locale.ROOT);
        return preview.contains(needle);
    }

    /** {@return the placeholder a preview substitutes, which is what this kind of profile wraps} */
    private String placeholder() {
        return names ? ProfileMarkup.PLAYER : ProfileMarkup.MESSAGE;
    }

    /** {@return the profile's text with the placeholders filled in, as the preview shows it} */
    private String previewOf(ProfileListPayload.Row row) {
        String sample = names && !player.isEmpty() ? player : messageSample();
        return row.format()
                .replace(ProfileMarkup.PLAYER, player.isEmpty()
                        ? Text.of("backutils.profiles.player.placeholder") : player)
                .replace(ProfileMarkup.MESSAGE, sample);
    }

    // ------------------------------------------------------------------
    // The rows
    // ------------------------------------------------------------------

    private UIElement buildPlayerRow(AdminPlayerListPayload.Player entry) {
        Label name = new Label();
        name.setText(entry.name(), false);
        name.textStyle(t -> t.fontSize(9f).textColor(PLAIN).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        // An explicit height, because an ldlib2 label defaults to nine pixels rather than to its line.
        name.layout(l -> l.height(PLAYER_ROW_HEIGHT));

        Label detail = new Label();
        detail.setText(Text.of("backutils.profiles.player.detail",
                Text.of(entry.online()
                        ? "backutils.profiles.online" : "backutils.profiles.offline"),
                entry.chatCount(), entry.nameCount()), false);
        detail.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        detail.layout(l -> l.height(PLAYER_ROW_HEIGHT));

        UIElement row = new UIElement();
        row.layout(l -> l.widthPercent(100).height(PLAYER_ROW_HEIGHT + 4).paddingAll(2)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER));
        row.style(s -> s.background(new ColorRectTexture(ROW_IDLE)));
        row.addChildren(name, detail);

        rowElements.add(row);
        row.addEventListener(UIEvents.MOUSE_ENTER, e ->
                row.style(s -> s.background(new ColorRectTexture(ROW_HOVER))), true);
        row.addEventListener(UIEvents.MOUSE_LEAVE, e -> paint(), true);
        row.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button != 0) return;
            showProfiles(entry.name());
        });
        return row;
    }

    /** The same profile row the player's own Profiles tab builds: three buttons, name, preview. */
    private UIElement buildProfileRow(ProfileListPayload.Row row) {
        boolean isDefault = ProfileOptions.DEFAULT_PROFILE.equals(row.name());

        // The widths hold the Russian labels, which are wider than the English ones at this size.
        UIElement buttons = new UIElement();
        buttons.layout(l -> l.height(LINE_STEP * 2 + 6)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));

        Button use = rowButton(Text.of("backutils.profiles.use"), 40, () -> use(row));
        Button edit = rowButton(Text.of("backutils.profiles.edit"), 46, () -> edit(row));
        Button delete = rowButton(Text.of("backutils.profiles.del"), 46,
                () -> delete(row, isDefault));

        buttons.addChild(use);
        if (!isDefault) buttons.addChildren(edit, delete);

        Label name = new Label();
        name.setText(row.name()
                + (row.active() ? Text.of("backutils.profiles.in.use") : "")
                + (row.sound() == null || row.sound().isBlank() ? "" : "  -  " + row.sound()), false);
        name.textStyle(t -> t.fontSize(9f).textColor(row.active() ? PLAIN : MUTED)
                .textShadow(false).adaptiveWidth(true));
        name.layout(l -> l.widthPercent(100).height(LINE_STEP));

        // Drawn through MarkupLabel, so Ember renders the format here as it will in chat; a name
        // preview substitutes the player's own account name, which is what makes it a preview.
        List<String> wrapped = MarkupWrap.wrapByWidth(previewOf(row), Math.max(40, listWidth - 160),
                line -> Minecraft.getInstance().font.width(MarkupUtil.strip(line)));
        if (wrapped.size() > 2) wrapped = wrapped.subList(0, 2);
        MarkupLabel preview = new MarkupLabel(wrapped, LINE_STEP, PLAIN);

        UIElement column = new UIElement();
        column.layout(l -> l.flex(1).flexDirection(FlexDirection.COLUMN));
        column.addChildren(name, preview);

        UIElement element = new UIElement();
        element.layout(l -> l.widthPercent(100)
                .height(Math.max(LINE_STEP * 2 + 6, LINE_STEP + preview.contentHeight() + 6))
                .paddingAll(2).flexDirection(FlexDirection.ROW).gapAll(GAP));
        element.addChildren(buttons, column);

        ProfileRow state = new ProfileRow(element, row, delete);
        profileRows.add(state);
        rowElements.add(element);

        element.addEventListener(UIEvents.MOUSE_ENTER, e ->
                element.style(s -> s.background(new ColorRectTexture(ROW_HOVER))), true);
        element.addEventListener(UIEvents.MOUSE_LEAVE, e -> paint(), true);
        // On the text side rather than the row, so confirming a delete does not also switch profiles.
        column.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button != 0) return;
            onClickRow(row);
        });
        return element;
    }

    private static Button rowButton(String text, int width, Runnable action) {
        Button button = new Button();
        button.setText(text, false);
        button.layout(l -> l.width(width).height(12));
        button.setOnClick(e -> action.run());
        return button;
    }

    private static Label muted(String text) {
        Label label = new Label();
        label.setText(text, false);
        label.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false).adaptiveWidth(true));
        return label;
    }

    /** Repaints every row: the profile in use is tinted, the rest go back to idle. */
    private void paint() {
        for (UIElement element : rowElements) {
            element.style(s -> s.background(new ColorRectTexture(ROW_IDLE)));
        }
        for (ProfileRow row : profileRows) {
            if (row.data.active()) {
                row.element.style(s -> s.background(new ColorRectTexture(ROW_IN_USE)));
            }
        }
    }

    /** A click on a profile: once to acknowledge, twice to make it the player's own. */
    private void onClickRow(ProfileListPayload.Row row) {
        long now = System.nanoTime();
        boolean doubleClick = row.name().equals(lastClick)
                && now - lastClickAt < DOUBLE_CLICK_NANOS;
        lastClick = row.name();
        lastClickAt = now;

        // Reaching for another row cancels an armed delete on this one.
        if (!armed.isEmpty() && !armed.equals(row.name())) disarm();

        if (doubleClick) use(row);
    }

    // ------------------------------------------------------------------
    // The three buttons
    // ------------------------------------------------------------------

    private void use(ProfileListPayload.Row row) {
        if (row.active()) {
            setStatus(Text.of("backutils.profiles.already.in.use", row.name()), false, false);
            return;
        }
        AdminProfileNetwork.send(AdminProfileEditPayload.use(names, player, row.name()));
    }

    /** Opens the editor on this profile. It is the same window a player's own tab opens. */
    private void edit(ProfileListPayload.Row row) {
        if (ProfileOptions.DEFAULT_PROFILE.equals(row.name())) return;
        BackUtilsProfileEditorScreen.openFor(player, names, row, forAdmin);
    }

    private void create() {
        if (player.isEmpty()) return;
        BackUtilsProfileEditorScreen.openFor(player, names, null, forAdmin);
    }

    /**
     * Removes a profile on a second click of that row's own button, so there is no doubt about
     * which profile the confirmation refers to.
     */
    private void delete(ProfileListPayload.Row row, boolean isDefault) {
        if (isDefault) return;

        long now = System.nanoTime();
        if (!row.name().equals(armed) || now - armedAt > DELETE_CONFIRM_NANOS) {
            disarm();
            armed = row.name();
            armedAt = now;
            buttonFor(row.name()).setText(Text.of("backutils.profiles.confirm.delete"), false);
            return;
        }

        disarm();
        AdminProfileNetwork.send(AdminProfileEditPayload.delete(names, player, row.name()));
    }

    /** {@return the Delete button of the row with this name, or a detached one when it is gone} */
    private Button buttonFor(String name) {
        for (ProfileRow row : profileRows) {
            if (row.data.name().equals(name)) return row.deleteButton;
        }
        return new Button();
    }

    private void disarm() {
        if (armed.isEmpty()) return;
        buttonFor(armed).setText(Text.of("backutils.profiles.del"), false);
        armed = "";
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    /**
     * Shows the server's last answer, once, and only an answer about this kind: the two panels sit
     * side by side, so a refusal about a name profile would read as being about the chat list.
     */
    private void showFeedback() {
        if (AdminProfileFeedbackCache.revision() == builtFeedbackRevision) return;
        if (AdminProfileFeedbackCache.names() != names) return;

        builtFeedbackRevision = AdminProfileFeedbackCache.revision();
        setStatus(AdminProfileFeedbackCache.message(), !AdminProfileFeedbackCache.ok(),
                AdminProfileFeedbackCache.ok());
    }

    private void setStatus(String text, boolean bad, boolean good) {
        // Cut to the room the strip has: a label draws as wide as its text whatever box it is given.
        String fitted = MarkupWrap.truncate(text, Math.max(40, listWidth),
                t -> Minecraft.getInstance().font.width(t));
        status.setText(fitted, false);
        status.textStyle(t -> t.fontSize(9f)
                .textColor(bad ? WARNING : good ? GOOD : MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
    }

    /** {@return the filter text, lower case, for matching} */
    private String filterText() {
        String text = filter.getText();
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
