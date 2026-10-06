package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.VanillaSpriteTexture;
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
import com.lowdragmc.lowdraglib2.gui.ui.elements.Slider;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import it.unimi.dsi.fastutil.floats.FloatConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.client.AdminAlerts;
import net.xlebupaksa.backutils.client.BackUtilsClientConfig;
import net.xlebupaksa.backutils.client.ZoneMusicPlayer;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.data.MarkupWrap;
import net.xlebupaksa.backutils.data.ProfileOptions;
import net.xlebupaksa.backutils.network.LogHistoryCache;
import net.xlebupaksa.backutils.network.LogHistoryPayload;
import net.xlebupaksa.backutils.network.MenuMusicRequestPayload;
import net.xlebupaksa.backutils.network.ProfileEditPayload;
import net.xlebupaksa.backutils.network.ProfileFeedbackCache;
import net.xlebupaksa.backutils.network.ProfileListCache;
import net.xlebupaksa.backutils.network.ProfileListPayload;
import net.xlebupaksa.backutils.network.ProfileNetwork;
import net.xlebupaksa.backutils.network.RoleplayLogNetwork;
import net.xlebupaksa.backutils.profile.ProfileMarkup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The menu opened by the corner button: full screen and chrome-free, painting its own translucent
 * dim and owning every widget, with the portrait against the left edge and the rest to its right.
 *
 * <p>One instance, reused: a fresh {@link ModularUI} and screen per open left the previous trees
 * behind and the UI came back layered on top of itself, while {@code Screen.init} clears the widget
 * list on each show, so re-registering the widget is safe.
 *
 * <p>Everything is sized in pixels, in {@link #init}: percentage heights resolve against an
 * unknowable parent, and an unmeasured scrolling list grew past the top of the screen.
 *
 * <p>Memory rows are plain text: ldlib2 draws with its own font, bypassing Ember's hooks.
 */
@OnlyIn(Dist.CLIENT)
public final class BackUtilsMenuScreen extends Screen {

    private static final long BLINK_NANOS = 420_000_000L;
    private static final long BLINK_PHASE_NANOS = 70_000_000L;

    private static final int ROW_IDLE = 0x44101016;
    private static final int ROW_HOVER = 0x883A4658;
    private static final int ROW_COPIED = 0xAA4E7FB8;
    private static final int ROW_IN_USE = 0x664E7FB8;
    private static final int MUTED = 0xFF9AA3B2;
    private static final int PLAIN = 0xFFE2E8F0;
    private static final int WARNING = 0xFFFF8080;

    /** How quickly a second click on the same profile row counts as a double-click. */
    private static final long DOUBLE_CLICK_NANOS = 400_000_000L;

    /** Screen padding, and the gap between the portrait and the content beside it. */
    private static final int PAD = 10;
    private static final int GAP = 10;
    private static final int INNER_GAP = 6;
    private static final int TITLE_HEIGHT = 18;
    /** Room reserved for the tab strip, which is laid out at its own default height. */
    private static final int TAB_STRIP_HEIGHT = 22;
    /**
     * What one {@code TabView} adds around its content: ldlib2 itself pads {@code
     * tabContentContainer} by five on every side, and the Profiles tab nests one tab view inside the
     * menu's own, so it is paid twice there.
     */
    private static final int TAB_CONTENT_PAD = 5;
    /** Height of one line of memory text; matches the vanilla font plus a little air. */
    private static final int LINE_STEP = 10;

    /** The strip above the profile list: the New profile button and the status line. */
    private static final int PROFILE_HEADER_HEIGHT = 18;
    /** How long a row's Delete button stays armed between its two clicks. */
    private static final long DELETE_CONFIRM_NANOS = 5_000_000_000L;

    /** The sample the profile previews are shown with, where {@code {m}} would be. */
    private static final String PREVIEW_SAMPLE = "Hello there";

    private static BackUtilsMenuScreen instance;

    /** Everything built once, kept so {@link #init} can resize it on each show. */
    private static final class Built {

        /** One caption and the key behind it, so {@link #applyTexts} can write it out again. */
        private record Caption(TextElement element, String key) {}

        ModularUI ui;
        UIElement root;
        UIElement portrait;
        UIElement tabColumn;
        UIElement tabView;
        ScrollerView memoryList;
        ScrollerView configScroll;
        /** The Profiles tab is itself a tab view: chat profiles always, plus name profiles for an
         * operator. Everyone else sees one panel and no strip. */
        TabView profileTabs;
        Tab chatTabHeader;
        Tab namesTabHeader;
        UIElement profilePanel;
        UIElement profileHeader;
        Button newProfile;
        ScrollerView profileList;
        Label profileStatus;
        AdminProfileTab nameTab;
        /**
         * Every caption in the tree with the key it came from. The tree outlives a language change
         * while ldlib2 flattens a string into a literal as it is set, so the keys are kept to write
         * the captions again on every open.
         */
        final List<Caption> captions = new ArrayList<>();
    }

    /** One drawn profile: what it says, and the button that removes it. */
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

    /** A memory row, plus the state its blink needs. */
    private static final class Row {
        final UIElement element;
        final String text;
        volatile long clickedAt = -1L;

        Row(UIElement element, String text) {
            this.element = element;
            this.text = text;
        }

        boolean blinking() {
            return clickedAt >= 0L;
        }
    }

    private final Built built;
    private final List<Row> rows = new ArrayList<>();
    private int builtRevision = -1;
    /** How wide the memory list is, recalculated whenever the menu opens. */
    private int listWidth = 200;
    private UIElement newestRow;
    /** Set when the list is rebuilt, acted on once the layout is valid. */
    private boolean scrollToEndPending;
    /** The profile list's own revision, so a payload arriving mid-session rebuilds it. */
    private int builtProfileRevision = -1;
    /** The last feedback message shown, so each answer is displayed once. */
    private int builtFeedbackRevision = -1;
    private final List<ProfileRow> profileRows = new ArrayList<>();
    /** The row whose Delete button is armed, or "" when none is. */
    private String armedProfile = "";
    private long profileDeleteArmedAt;
    /** The name last clicked in the profile list, for the double-click shortcut. */
    private String lastProfileClick = "";
    private long lastProfileClickAt;

    private BackUtilsMenuScreen(Built built) {
        super(Component.translatable("backutils.menu.title"));
        this.built = built;
    }

    /** Shows the menu, building it on first use and reusing it afterwards. */
    public static void open() {
        if (instance == null) {
            Built built = build();
            built.ui = ModularUI.of(UI.of(built.root));
            // Screens do not tick their UI by default.
            built.ui.setTickWhileRending(true);
            instance = new BackUtilsMenuScreen(built);
            instance.rebuildMemoryList();
        }
        RoleplayLogNetwork.requestHistory();
        // Asked for on every open: an operator can delete a profile from the console.
        ProfileNetwork.requestList();
        // The menu's music is the server's setting, so it is asked for rather than assumed.
        PacketDistributor.sendToServer(new MenuMusicRequestPayload());
        Minecraft.getInstance().setScreen(instance);
    }

    /**
     * Fades the menu's music out with the menu.
     *
     * <p>{@code removed} rather than {@code onClose}: the menu is also left for the profile editor,
     * which replaces the screen without closing it.
     */
    @Override
    public void removed() {
        ZoneMusicPlayer.stopMenu();
        super.removed();
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private static Built build() {
        Built built = new Built();

        built.portrait = new UIElement();
        built.portrait.style(s -> s.background(VanillaSpriteTexture.of(
                ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "menu/portrait"))));

        built.tabColumn = new UIElement();
        built.tabColumn.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(INNER_GAP));

        Label title = new Label();
        translated(built, title, "backutils.menu.title");
        title.textStyle(t -> t.fontSize(12f).textColor(PLAIN).textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER)
                .adaptiveWidth(true));

        built.memoryList = scrollingList();
        built.configScroll = scrollingList();
        built.profileList = scrollingList();
        buildProfilePanel(built);

        TabView tabView = new TabView();
        tabView.addTab(translatedTab(built, "backutils.menu.tab.memory"), wrap(built.memoryList));
        tabView.addTab(translatedTab(built, "backutils.menu.tab.configuration"),
                wrap(built.configScroll));
        tabView.addTab(translatedTab(built, "backutils.menu.tab.profiles"), built.profileTabs);
        buildConfigControls(built);
        built.tabView = tabView;

        built.tabColumn.addChildren(title, tabView);

        built.root = new UIElement();
        built.root.layout(l -> l
                .paddingAll(PAD)
                .flexDirection(FlexDirection.ROW)
                .gapAll(GAP));
        built.root.addChildren(built.portrait, built.tabColumn);
        return built;
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

    /** {@return a tab whose caption comes from a key}, written again like every other caption} */
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
    private static void applyTexts(Built built) {
        for (Built.Caption caption : built.captions) {
            caption.element().setText(Text.of(caption.key()), false);
        }
        // The name panel's own captions, its filter hint and its scope line are keys as well.
        built.nameTab.relabel();
    }

    private static ScrollerView scrollingList() {
        ScrollerView list = new ScrollerView();
        list.scrollerStyle(s -> s
                .mode(ScrollerMode.VERTICAL)
                // ALWAYS rather than AUTO: the bar is the affordance that says the list scrolls.
                .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .adaptiveWidth(false)
                .adaptiveHeight(false));
        list.viewContainer(v -> v.getLayout().gapAll(1));
        return list;
    }

    private static UIElement wrap(UIElement child) {
        UIElement holder = new UIElement();
        holder.addChild(child);
        return holder;
    }

    private static void buildConfigControls(Built built) {
        ScrollerView scroll = built.configScroll;
        scroll.addScrollViewChild(toggleRow(built, "backutils.menu.setting.log",
                BackUtilsClientConfig.isLogEnabled(), BackUtilsClientConfig::setLogEnabled));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.font_size", 4f, 32f,
                (float) BackUtilsClientConfig.getFontSize(), " px", true,
                BackUtilsClientConfig::setFontSize));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.wrap_width", 16f, 240f,
                BackUtilsClientConfig.getWrapCharacters(), " ch", true,
                v -> BackUtilsClientConfig.setWrapCharacters(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.lines_kept", 1f, 32f,
                BackUtilsClientConfig.getMaxLines(), "", true,
                v -> BackUtilsClientConfig.setMaxLines(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.line_lifetime", 3f, 300f,
                BackUtilsClientConfig.getLineLifetimeSeconds(), " s", true,
                v -> BackUtilsClientConfig.setLineLifetimeSeconds(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.fade", 1f, 30f,
                BackUtilsClientConfig.getFadeSeconds(), " s", true,
                v -> BackUtilsClientConfig.setFadeSeconds(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.right_margin", 0f, 200f,
                BackUtilsClientConfig.getRightMargin(), " px", true,
                v -> BackUtilsClientConfig.setRightMargin(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.top_margin", 0f, 400f,
                BackUtilsClientConfig.getTopMargin(), " px", true,
                v -> BackUtilsClientConfig.setTopMargin(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.log_button_size", 8f, 128f,
                BackUtilsClientConfig.getButtonSize(), " px", true,
                v -> BackUtilsClientConfig.setButtonSize(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.log_button_margin", 0f, 200f,
                BackUtilsClientConfig.getButtonMargin(), " px", true,
                v -> BackUtilsClientConfig.setButtonMargin(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.admin_button_size", 8f, 128f,
                BackUtilsClientConfig.getAdminButtonSize(), " px", true,
                v -> BackUtilsClientConfig.setAdminButtonSize(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.backdrop_opacity", 0f, 1f,
                (float) BackUtilsClientConfig.getMenuBackgroundOpacity(), "", false,
                v -> BackUtilsClientConfig.setMenuBackgroundOpacity(v)));

        // The operator alert is client-side: the sound is played here, and only ever by the player
        // who chose it, so none of this belongs in the server's config.
        scroll.addScrollViewChild(headingLabel(built, "backutils.menu.alerts.heading"));
        scroll.addScrollViewChild(toggleRow(built, "backutils.menu.alerts.sound",
                BackUtilsClientConfig.isAdminAlertEnabled(),
                BackUtilsClientConfig::setAdminAlertEnabled));
        scroll.addScrollViewChild(alertSoundRow(built));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.alerts.volume", 0f, 2f,
                (float) BackUtilsClientConfig.getAdminAlertVolume(), "", false,
                BackUtilsClientConfig::setAdminAlertVolume));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.alerts.pitch", 0.5f, 2f,
                (float) BackUtilsClientConfig.getAdminAlertPitch(), "", false,
                BackUtilsClientConfig::setAdminAlertPitch));
        scroll.addScrollViewChild(toggleRow(built, "backutils.menu.alerts.icon",
                BackUtilsClientConfig.isAdminAlertMarkerEnabled(),
                BackUtilsClientConfig::setAdminAlertMarker));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.alerts.icon_size", 4f, 64f,
                BackUtilsClientConfig.getAdminAlertIconSize(), " px", true,
                v -> BackUtilsClientConfig.setAdminAlertIconSize(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.alerts.icon_fade", 0f, 10f,
                (float) BackUtilsClientConfig.getAdminAlertPulseSeconds(), " s", false,
                v -> BackUtilsClientConfig.setAdminAlertPulseSeconds(v)));
    }

    /**
     * {@return the row that names the alert sound}, with a button to play it: the sound is otherwise
     * heard only when an alert actually arrives, which is no way to choose one.
     */
    private static UIElement alertSoundRow(Built built) {
        TextField field = new TextField();
        field.setText(BackUtilsClientConfig.getAdminAlertSound(), false);
        field.layout(l -> l.flex(1).height(14));
        field.setTextResponder(BackUtilsClientConfig::setAdminAlertSound);

        Button play = new Button();
        // Wider than the English needs: "Играть" is the shortest wording that still means play.
        translated(built, play.text, "backutils.menu.alerts.play");
        play.layout(l -> l.width(46).height(14));
        play.setOnClick(e -> AdminAlerts.preview());

        return controlRow(built, "backutils.menu.alerts.sound_id", field, play);
    }

    private static UIElement sliderRow(Built built, String key, float min, float max, float initial,
                                       String suffix, boolean whole, FloatConsumer apply) {
        Label value = new Label();
        value.setText(format(initial, whole) + suffix, false);
        value.textStyle(t -> t.fontSize(9f).textColor(PLAIN).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        value.layout(l -> l.width(52));

        Slider.Horizontal slider = new Slider.Horizontal();
        slider.setRange(min, max);
        // notify = false: setting the initial value must not fire the callback.
        slider.setValue(initial, false);
        slider.layout(l -> l.flex(1).height(14));
        slider.setOnValueChanged(v -> {
            value.setText(format(v, whole) + suffix, false);
            apply.accept(v);
        });

        return controlRow(built, key, slider, value);
    }

    private static UIElement toggleRow(Built built, String key, boolean initial,
                                       Consumer<Boolean> apply) {
        Toggle toggle = new Toggle();
        toggle.setOn(initial, false);
        toggle.layout(l -> l.width(24).height(12));
        toggle.setOnToggleChanged(apply::accept);

        UIElement spacer = new UIElement();
        spacer.layout(l -> l.flex(1));
        return controlRow(built, key, toggle, spacer);
    }

    private static UIElement controlRow(Built built, String key, UIElement control,
                                        UIElement trailing) {
        Label label = new Label();
        translated(built, label, key);
        label.textStyle(t -> t.fontSize(9f).textColor(PLAIN).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        label.layout(l -> l.width(120));

        UIElement row = new UIElement();
        row.layout(l -> l
                .widthPercent(100)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .gapAll(INNER_GAP));
        row.addChildren(label, control, trailing);
        return row;
    }

    private static String format(float value, boolean whole) {
        return whole ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
    }

    // ------------------------------------------------------------------
    // Profiles
    // ------------------------------------------------------------------

    /**
     * Builds the Profiles tab: one strip for the list as a whole, then the list. Use, Edit and Delete
     * sit on the rows, because a shared bar would need a selection to act on.
     */
    private static void buildProfilePanel(Built built) {
        // One button for both kinds, relabelled by the tab strip: wide enough that the Russian of
        // either label sits inside it rather than running past its own edge.
        built.newProfile = profileButton(Text.of("backutils.menu.profiles.new_chat"), 122,
                () -> createProfile(built));

        built.profileStatus = new Label();
        built.profileStatus.setText("", false);
        built.profileStatus.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));

        built.profileHeader = new UIElement();
        built.profileHeader.layout(l -> l.widthPercent(100).height(PROFILE_HEADER_HEIGHT)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(INNER_GAP));
        built.profileHeader.addChildren(built.newProfile, built.profileStatus);

        built.profilePanel = new UIElement();
        built.profilePanel.layout(l -> l.widthPercent(100).heightPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(INNER_GAP));
        built.profilePanel.addChildren(built.profileHeader, built.profileList);

        // A player's own name profiles: the same panel the background menu opens on a player, with its
        // browsing removed, for an operator.
        built.nameTab = new AdminProfileTab(true);

        built.chatTabHeader = translatedTab(built, "backutils.menu.tab.chat");
        built.namesTabHeader = translatedTab(built, "backutils.menu.tab.names");
        built.profileTabs = new TabView();
        // The strip stays and the panel border goes: this tab is already inside the menu's tab view.
        built.profileTabs.tabContentContainer(
                c -> c.style(s -> s.backgroundTexture(IGuiTexture.EMPTY)));
        built.profileTabs.addTab(built.chatTabHeader, built.profilePanel);
        built.profileTabs.addTab(built.namesTabHeader, built.nameTab.root());
        built.profileTabs.setOnTabSelected(tab -> {
            if (instance != null) instance.onProfileKindChanged(tab);
        });
    }

    private static void createProfile(Built built) {
        boolean names = built.profileTabs.getSelectedTab() == built.namesTabHeader;
        if (names && canManageNames()) {
            // backToAdmin = false: this window is the player's own, so closing comes back here.
            BackUtilsProfileEditorScreen.openFor(ownName(), true, null, false);
            return;
        }
        BackUtilsProfileEditorScreen.open(null);
    }

    /** {@return this client's account name, or "" when there is no player yet} */
    private static String ownName() {
        return Minecraft.getInstance().player == null
                ? "" : Minecraft.getInstance().player.getGameProfile().getName();
    }

    /**
     * {@return true when this client may manage name profiles}
     *
     * <p>The server's own answer, synced with the rest of the profile options.
     */
    private static boolean canManageNames() {
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        ProfileOptions options =
                player.getData(net.xlebupaksa.backutils.data.ModAttachments.OPTIONS.get());
        return options != null && options.canManageNames();
    }

    private void onProfileKindChanged(Tab tab) {
        boolean names = tab == built.namesTabHeader;
        built.newProfile.setText(names ? Text.of("backutils.menu.profiles.new_name")
                : Text.of("backutils.menu.profiles.new_chat"), false);
    }

    private static Button profileButton(String text, int width, Runnable action) {
        Button button = new Button();
        button.setText(text, false);
        button.layout(l -> l.width(width).height(14));
        button.setOnClick(e -> action.run());
        return button;
    }

    /**
     * A row's own button: small, because three of them sit beside every profile. The widths are
     * sized for the Russian labels, which need a few pixels more than the English words do.
     */
    private static Button rowButton(String text, int width, Runnable action) {
        Button button = new Button();
        button.setText(text, false);
        button.layout(l -> l.width(width).height(12));
        button.setOnClick(e -> action.run());
        return button;
    }

    /** Fills the list from the cache, {@code default} included: hiding it would leave another profile
     * as the only way back. */
    private void rebuildProfileList() {
        built.profileList.clearAllScrollViewChildren();
        profileRows.clear();
        armedProfile = "";
        builtProfileRevision = ProfileListCache.revision();

        List<ProfileListPayload.Row> all = new ArrayList<>();
        all.add(new ProfileListPayload.Row(ProfileOptions.DEFAULT_PROFILE, ProfileMarkup.MESSAGE,
                "", ProfileListCache.rows().stream().noneMatch(ProfileListPayload.Row::active)));
        all.addAll(ProfileListCache.rows());

        for (ProfileListPayload.Row row : all) {
            built.profileList.addScrollViewChild(buildProfileRow(row));
        }
        paintProfileRows();
    }

    private UIElement buildProfileRow(ProfileListPayload.Row row) {
        boolean isDefault = ProfileOptions.DEFAULT_PROFILE.equals(row.name());

        // The buttons come first, on the left of the row, so the list reads as a column of controls.
        UIElement buttons = new UIElement();
        buttons.layout(l -> l.height(LINE_STEP * 2 + 6)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));

        Button use = rowButton(Text.of("backutils.menu.profiles.use"), 40, () -> useProfile(row));
        Button edit = rowButton(Text.of("backutils.menu.profiles.edit"), 46, () -> editProfile(row));
        Button delete = rowButton(Text.of("backutils.menu.profiles.delete"), 46,
                () -> deleteProfile(row, isDefault));

        buttons.addChild(use);
        if (!isDefault) {
            buttons.addChildren(edit, delete);
        }

        Label name = new Label();
        name.setText(row.name()
                + (row.active() ? Text.of("backutils.menu.profiles.in_use") : "")
                + (row.sound() == null || row.sound().isBlank() ? "" : "  -  " + row.sound()), false);
        name.textStyle(t -> t.fontSize(9f).textColor(row.active() ? PLAIN : MUTED)
                .textShadow(false).adaptiveWidth(true));
        name.layout(l -> l.widthPercent(100).height(LINE_STEP));

        // Through MarkupLabel, so Ember renders the preview exactly as chat will; "{m}" becomes a sample.
        String preview = isDefault
                ? PREVIEW_SAMPLE
                : row.format().replace(ProfileMarkup.MESSAGE, PREVIEW_SAMPLE);
        List<String> wrapped = MarkupWrap.wrapByWidth(preview, Math.max(40, listWidth - 160),
                line -> Minecraft.getInstance().font.width(MarkupUtil.strip(line)));
        if (wrapped.size() > 2) wrapped = wrapped.subList(0, 2);
        MarkupLabel previewLabel = new MarkupLabel(wrapped, LINE_STEP, PLAIN);

        UIElement column = new UIElement();
        column.layout(l -> l.flex(1).flexDirection(FlexDirection.COLUMN));
        column.addChildren(name, previewLabel);

        UIElement element = new UIElement();
        element.layout(l -> l.widthPercent(100)
                .height(Math.max(LINE_STEP * 2 + 6, LINE_STEP + previewLabel.contentHeight() + 6))
                .paddingAll(2)
                .flexDirection(FlexDirection.ROW)
                .gapAll(INNER_GAP));
        element.addChildren(buttons, column);

        ProfileRow state = new ProfileRow(element, row, delete);
        profileRows.add(state);

        element.addEventListener(UIEvents.MOUSE_ENTER, e ->
                element.style(s -> s.background(new ColorRectTexture(ROW_HOVER))), true);
        element.addEventListener(UIEvents.MOUSE_LEAVE, e -> paintProfileRows(), true);
        // On the text side, not the row: a second click on Delete confirms a delete.
        column.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button != 0) return;
            onProfileRowClicked(row);
        });
        return element;
    }

    /** Repaints every profile row: the one in use is tinted, and hovering is handled separately. */
    private void paintProfileRows() {
        for (ProfileRow row : profileRows) {
            row.element.style(s -> s.background(new ColorRectTexture(
                    row.data.active() ? ROW_IN_USE : ROW_IDLE)));
        }
    }

    /** A click on a row: once to acknowledge, twice to switch to it, which is what the list is for. */
    private void onProfileRowClicked(ProfileListPayload.Row row) {
        long now = System.nanoTime();
        boolean doubleClick = row.name().equals(lastProfileClick)
                && now - lastProfileClickAt < DOUBLE_CLICK_NANOS;
        lastProfileClick = row.name();
        lastProfileClickAt = now;

        // Reaching for another row cancels an armed delete on this one.
        if (!armedProfile.isEmpty() && !armedProfile.equals(row.name())) disarmDelete();

        if (doubleClick) useProfile(row);
    }

    private void useProfile(ProfileListPayload.Row row) {
        if (row.active()) {
            setProfileStatus(Text.of("backutils.menu.profiles.already_in_use", row.name()), false);
            return;
        }
        ProfileNetwork.send(ProfileEditPayload.use(row.name()));
    }

    private void editProfile(ProfileListPayload.Row row) {
        if (ProfileOptions.DEFAULT_PROFILE.equals(row.name())) return;
        BackUtilsProfileEditorScreen.open(row);
    }

    /** Removes a profile, on a second click of that row's own button, which shows the arming. */
    private void deleteProfile(ProfileListPayload.Row row, boolean isDefault) {
        if (isDefault) return;

        long now = System.nanoTime();
        if (!row.name().equals(armedProfile) || now - profileDeleteArmedAt > DELETE_CONFIRM_NANOS) {
            disarmDelete();
            armedProfile = row.name();
            profileDeleteArmedAt = now;
            buttonFor(row.name()).setText(Text.of("backutils.menu.profiles.confirm"), false);
            return;
        }

        disarmDelete();
        ProfileNetwork.send(ProfileEditPayload.delete(row.name()));
    }

    /** {@return the delete button of the row with this name, or a detached one when it is gone} */
    private Button buttonFor(String name) {
        for (ProfileRow row : profileRows) {
            if (row.data.name().equals(name)) return row.deleteButton;
        }
        return new Button();
    }

    private void disarmDelete() {
        if (armedProfile.isEmpty()) return;
        buttonFor(armedProfile).setText(Text.of("backutils.menu.profiles.delete"), false);
        armedProfile = "";
    }

    private void setProfileStatus(String text, boolean bad) {
        // Truncated rather than left to run: a label is as wide as its text, so a refusal would
        // spill. The budget is what the New profile button and the row's gap leave of the width.
        built.profileStatus.setText(fit(text, Math.max(40, listWidth - 132)), false);
        built.profileStatus.textStyle(t -> t.fontSize(9f)
                .textColor(bad ? WARNING : MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
    }

    /** {@return the text, cut to fit the width, with an ellipsis when anything was dropped} */
    private static String fit(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return "";
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) return text;

        String suffix = "...";
        int room = Math.max(0, maxWidth - font.width(suffix));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (font.width(out.toString() + c) > room) break;
            out.append(c);
        }
        return out + suffix;
    }

    /** Shows the server's answer, once per answer. */
    private void syncProfileFeedback() {
        if (ProfileFeedbackCache.revision() == builtFeedbackRevision) return;
        builtFeedbackRevision = ProfileFeedbackCache.revision();

        String message = ProfileFeedbackCache.message();
        if (message == null || message.isBlank()) return;

        setProfileStatus(message, !ProfileFeedbackCache.ok());
    }

    // ------------------------------------------------------------------
    // Memory list
    // ------------------------------------------------------------------

    private void rebuildMemoryList() {
        built.memoryList.clearAllScrollViewChildren();
        rows.clear();
        builtRevision = LogHistoryCache.revision();

        List<LogHistoryPayload.Row> history = LogHistoryCache.rows();
        if (history.isEmpty()) {
            built.memoryList.addScrollViewChild(muted(Text.of("backutils.menu.memory.empty")));
            return;
        }
        // Oldest first, straight from the payload, so the list reads in the order things happened.
        UIElement last = null;
        for (LogHistoryPayload.Row entry : history) {
            last = buildMemoryRow(entry);
            built.memoryList.addScrollViewChild(last);
        }

        // Scrolled to the newest end once the layout has run: the rows have no measured position yet.
        newestRow = last;
        scrollToEndPending = last != null;
    }

    private UIElement buildMemoryRow(LogHistoryPayload.Row entry) {
        // Markup is preserved through MarkupLabel and wrapped by measured pixel width.
        List<String> wrapped = MarkupWrap.wrapByWidth(entry.text(), Math.max(40, listWidth - 18),
                line -> Minecraft.getInstance().font.width(MarkupUtil.strip(line)));
        MarkupLabel text = new MarkupLabel(wrapped, LINE_STEP, PLAIN);

        UIElement row = new UIElement();
        row.layout(l -> l
                .widthPercent(100)
                .height(text.contentHeight() + 4)
                .paddingAll(2)
                .flexDirection(FlexDirection.ROW));
        row.style(s -> s
                .background(new ColorRectTexture(ROW_IDLE))
                .tooltips(Component.literal(entry.createdAt())));
        row.addChild(text);

        Row state = new Row(row, MarkupUtil.strip(entry.text()));
        rows.add(state);

        row.addEventListener(UIEvents.MOUSE_ENTER, e -> {
            if (!state.blinking()) row.style(s -> s.background(new ColorRectTexture(ROW_HOVER)));
        }, true);
        row.addEventListener(UIEvents.MOUSE_LEAVE, e -> {
            if (!state.blinking()) row.style(s -> s.background(new ColorRectTexture(ROW_IDLE)));
        }, true);
        row.addEventListener(UIEvents.MOUSE_DOWN, e -> {
            if (e.button != 0) return;
            Minecraft.getInstance().keyboardHandler.setClipboard(state.text);
            state.clickedAt = System.nanoTime();
        });
        return row;
    }

    private static Label muted(String text) {
        Label label = mutedLabel();
        label.setText(text, false);
        return label;
    }

    /** {@return an empty muted label}, for a caller that writes the text itself */
    private static Label mutedLabel() {
        Label label = new Label();
        label.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false).adaptiveWidth(true));
        return label;
    }

    /** {@return a muted heading from a key}, written again on every open like the rows below it */
    private static Label headingLabel(Built built, String key) {
        Label label = mutedLabel();
        translated(built, label, key);
        return label;
    }

    // ------------------------------------------------------------------
    // Screen
    // ------------------------------------------------------------------

    @Override
    public void init() {
        // The tree is built once and kept, so the captions resolved as it was built are written
        // again here: without that they would hold the language the menu was first opened in.
        applyTexts(built);

        int contentWidth = Math.max(80, this.width - PAD * 2);
        int contentHeight = Math.max(60, this.height - PAD * 2);
        // A square portrait, never wider than a third of the window so the list keeps room.
        int portrait = Math.max(32, Math.min(contentHeight, contentWidth / 3));
        int rightWidth = Math.max(40, contentWidth - portrait - GAP);
        int listHeight = Math.max(20,
                contentHeight - TITLE_HEIGHT - INNER_GAP - TAB_STRIP_HEIGHT);

        built.root.layout(l -> l
                .width(this.width)
                .height(this.height)
                .paddingAll(PAD)
                .flexDirection(FlexDirection.ROW)
                // Centred against the column beside it rather than pinned to the top of the window.
                .alignItems(AlignItems.CENTER)
                .gapAll(GAP));
        built.portrait.layout(l -> l.width(portrait).height(portrait));
        built.tabColumn.layout(l -> l
                .width(rightWidth)
                .height(contentHeight)
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(INNER_GAP));
        built.tabView.layout(l -> l
                .width(rightWidth)
                .height(contentHeight - TITLE_HEIGHT - INNER_GAP));
        // Explicit heights: without a definite viewport the scroller cannot clip or scroll.
        built.memoryList.layout(l -> l.width(rightWidth).height(listHeight));
        built.configScroll.layout(l -> l.width(rightWidth).height(listHeight));

        // The Profiles tab is a tab view of its own, so its content pays the strip and ldlib2's
        // five-pixel padding; an operator gets the strip, everyone else one panel.
        boolean staff = canManageNames();
        built.profileTabs.layout(l -> l.width(rightWidth).height(listHeight));
        built.namesTabHeader.setDisplay(staff);
        built.profileTabs.tabHeaderContainer.setDisplay(staff);
        if (!staff && built.profileTabs.getSelectedTab() != built.chatTabHeader) {
            built.profileTabs.selectTab(built.chatTabHeader);
        }
        built.newProfile.setText(
                staff && built.profileTabs.getSelectedTab() == built.namesTabHeader
                        ? Text.of("backutils.menu.profiles.new_name")
                        : Text.of("backutils.menu.profiles.new_chat"), false);

        int profileBoxHeight = staff
                ? Math.max(20, listHeight - TAB_STRIP_HEIGHT - TAB_CONTENT_PAD * 2)
                : listHeight;
        int profileBoxWidth = staff ? Math.max(60, rightWidth - TAB_CONTENT_PAD * 2) : rightWidth;
        built.profilePanel.layout(l -> l.width(profileBoxWidth).height(profileBoxHeight));
        built.profileList.layout(l -> l.width(profileBoxWidth)
                .height(Math.max(20, profileBoxHeight - PROFILE_HEADER_HEIGHT - INNER_GAP)));
        built.nameTab.layout(profileBoxWidth, profileBoxHeight);

        built.ui.setScreenAndInit(this);
        // setScreenAndInit only lays out; the widget has to be registered to render and take input.
        addRenderableWidget(built.ui.getWidget());
        super.init();
        setFocused(built.ui.getWidget());

        // Memory rows wrap to the list's width, and the width only becomes known here.
        listWidth = rightWidth;

        // The name panel manages the account this client is logged in as, so it is told on every open.
        if (staff && Minecraft.getInstance().player != null) {
            built.nameTab.setTarget(Minecraft.getInstance().player.getGameProfile().getName());
        }

        // Rebuilt on every open, not only when the history changes, so the wrapping matches the window.
        rebuildMemoryList();
        rebuildProfileList();
        // A stale refusal from a previous visit is worse than no message at all.
        setProfileStatus("", false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Paints the dim behind the menu and nothing else, so the vanilla blurred backdrop never
     * appears. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int alpha = (int) Math.round(Math.max(0.0D, Math.min(1.0D,
                BackUtilsClientConfig.getMenuBackgroundOpacity())) * 255.0D);
        graphics.fill(0, 0, this.width, this.height, (alpha << 24) | 0x00101016);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // A reply can land while the menu is open, from the history request or from a profile change.
        if (LogHistoryCache.revision() != builtRevision) rebuildMemoryList();
        if (ProfileListCache.revision() != builtProfileRevision) rebuildProfileList();
        // Only the kind in view follows the server: the two tabs read different caches.
        if (built.profileTabs.getSelectedTab() == built.namesTabHeader) built.nameTab.sync();
        syncProfileFeedback();
        applyBlink();
        super.render(graphics, mouseX, mouseY, partialTick);

        applyPendingScroll();
    }

    /** Scrolls the memory list to its newest row, after a render pass: a scroll requested while the
     * rows are unmeasured has nothing to scroll to. */
    private void applyPendingScroll() {
        if (!scrollToEndPending) return;
        scrollToEndPending = false;
        if (newestRow == null) return;

        if (!built.memoryList.scrollToChild(newestRow)) {
            built.memoryList.verticalScroller.setNormalizedValue(1.0f);
        }
    }

    /** Alternates a clicked row's background briefly, the usual "copied" acknowledgement. */
    private void applyBlink() {
        long now = System.nanoTime();
        for (Row row : rows) {
            if (!row.blinking()) continue;

            long elapsed = now - row.clickedAt;
            if (elapsed >= BLINK_NANOS) {
                row.clickedAt = -1L;
                row.element.style(s -> s.background(new ColorRectTexture(ROW_IDLE)));
                continue;
            }
            boolean bright = (elapsed / BLINK_PHASE_NANOS) % 2L == 0L;
            int colour = bright ? ROW_COPIED : ROW_IDLE;
            row.element.style(s -> s.background(new ColorRectTexture(colour)));
        }
    }
}
