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
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import it.unimi.dsi.fastutil.floats.FloatConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.client.AdminAlerts;
import net.xlebupaksa.backutils.client.BackUtilsClientConfig;
import net.xlebupaksa.backutils.client.Operators;
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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

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
 * <p>The rows that read the config are seeded again on every {@link #init}, and again when
 * {@link #render} sees that the config has been reloaded since they were, not only as the tree is
 * built: the client config is reloaded while the game runs when its file changes on disk, so a
 * control still holding an older value would show a stale one and write that one back over the new
 * value on its first drag.
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

        /**
         * How to write the value the config holds now back into each control that reads one. The
         * tree outlives a config reload the way it outlives a language change, so the readers are
         * kept rather than the values they returned as the rows were built.
         */
        final List<Runnable> configControls = new ArrayList<>();
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

    /**
     * One profile row's preview: the rows the chat line itself is drawn on, drawn the way the chat draws
     * them.
     *
     * <p>A preview built as one literal per row answers differently from the chat line for a row holding
     * nothing but tags: parsed on its own such a row has no text to format, so Ember gives up on the markup
     * and draws the characters of the tags, where the chat's parse of the whole line spends them. The rows
     * here are the ones vanilla's own splitter made of the chat's body — see {@link #previewRows} — and each
     * is drawn with the call the chat draws a row with, so the two cannot come to disagree about a profile.
     * The rows are kept, not rebuilt: the sequences drawn this frame are the ones drawn on the next.
     *
     * <p>{@link #contentHeight} mirrors {@link MarkupLabel}'s, so the row a preview sits in is as tall as it
     * was when the literals were drawn: a preview that changed shape would move every row of the list.
     */
    private static final class PreviewLabel extends UIElement {

        private final List<FormattedCharSequence> rows;

        PreviewLabel(List<FormattedCharSequence> rows) {
            this.rows = rows;
        }

        /** {@return the height needed to show every row}, which the row around it is sized from */
        int contentHeight() {
            return Math.max(LINE_STEP, rows.size() * LINE_STEP);
        }

        @Override
        public void drawContents(GUIContext guiContext) {
            Font font = Minecraft.getInstance().font;
            float x = getPositionX();
            float y = getPositionY();
            for (int i = 0; i < rows.size(); i++) {
                // The call the chat draws each of its own rows with, on the rows the same splitter made.
                guiContext.graphics.drawString(font, rows.get(i),
                        Math.round(x), Math.round(y + i * LINE_STEP), PLAIN, true);
            }
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
    /**
     * Whether the name half of the Profiles tab is shown, as the tree currently has it, so
     * {@link #render} can notice the server's answer changing under an open menu.
     */
    private boolean staffApplied;
    /** Whether the operator alert's rows are in the tree, as the answer had it when they were last applied. */
    private boolean alertsApplied;
    /**
     * The alert rows themselves, built the first time they are wanted and kept until the answer says
     * no, so a client that is opped again gets the same rows back rather than a second set: building
     * them registers their captions and their config readers, which only has to happen once.
     */
    private final List<UIElement> alertRows = new ArrayList<>();
    /** The room the Profiles tab was last given, so the scope can be applied again without an open. */
    private int profileWidth = 200;
    private int profileHeight = 100;
    private UIElement newestRow;
    /** Set when the list is rebuilt, acted on once the layout is valid. */
    private boolean scrollToEndPending;
    /** The profile list's own revision, so a payload arriving mid-session rebuilds it. */
    private int builtProfileRevision = -1;
    /** The last feedback message shown, so each answer is displayed once. */
    private int builtFeedbackRevision = -1;
    /**
     * The config revision the settings rows were last seeded from, so a reload landing on this screen
     * while it is open is noticed: the tree is not rebuilt by one, and its controls would otherwise
     * keep the values they were built with.
     */
    private int seededConfigRevision = -1;
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
        tabView.addTab(translatedTab(built, "backutils.menu.tab.profiles"), built.profilePanel);
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

    /**
     * Writes the value the config holds now into every control that reads one, readout labels
     * included, and remembers the revision that came from.
     *
     * <p>Called on every open, and again from {@link #render} when the config has been reloaded since
     * - the two moments a control could otherwise be showing a value the config no longer holds. The
     * client config is reloaded while the game runs, and editing its file by hand is enough: a slider
     * or a toggle left at an older value would show it, and write it back over the new one, as soon as
     * it was touched.
     */
    private void seedConfigControls() {
        for (Runnable seed : built.configControls) {
            seed.run();
        }
        seededConfigRevision = BackUtilsClientConfig.revision();
    }

    /**
     * Puts the operator alert's rows in the config list, or takes them out again, as the answer has
     * it, and remembers which answer that was.
     *
     * <p>Called on every open and again from {@link #render} whenever the answer moves, for the
     * reason {@link #applyProfileScope} is: whether this client is an operator is the server's answer
     * and arrives while the client is running, and the tree here is built once and kept for the life
     * of the client. The administrator corner asks about it on every frame, so a player who is opped
     * rather than having been one at login gets the corner at once — and without this they would never
     * get the rows that configure it, since the build they were left out of happened long ago.
     *
     * <p>The rows are built the first time they are wanted rather than built and hidden, because a row
     * that is built and hidden is one edit away from being built and shown. Taken out rather than
     * hidden when the answer turns to no, and put back rather than built again when it turns to yes,
     * because what is registered as they are built — their captions, and the readers that seed them —
     * has to be registered exactly once.
     *
     * <p>A caller that adds rows part-way through a session seeds the controls afterwards: a row built
     * after the last seeding would show the value its control started at rather than the config's.
     */
    private void applyAlertScope() {
        boolean operator = Operators.isOperator();
        if (operator == alertsApplied) return;
        alertsApplied = operator;

        if (!operator) {
            for (UIElement row : alertRows) {
                built.configScroll.removeScrollViewChild(row);
            }
            return;
        }
        if (alertRows.isEmpty()) alertRows.addAll(alertControls(built));
        for (UIElement row : alertRows) {
            built.configScroll.addScrollViewChild(row);
        }
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
                BackUtilsClientConfig::isLogEnabled, BackUtilsClientConfig::setLogEnabled));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.font_size", 4f, 32f,
                BackUtilsClientConfig::getFontSize, " px", true,
                BackUtilsClientConfig::setFontSize));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.wrap_width", 16f, 240f,
                BackUtilsClientConfig::getWrapCharacters, " ch", true,
                v -> BackUtilsClientConfig.setWrapCharacters(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.lines_kept", 1f, 32f,
                BackUtilsClientConfig::getMaxLines, "", true,
                v -> BackUtilsClientConfig.setMaxLines(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.line_lifetime", 3f, 300f,
                BackUtilsClientConfig::getLineLifetimeSeconds, " s", true,
                v -> BackUtilsClientConfig.setLineLifetimeSeconds(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.fade", 1f, 30f,
                BackUtilsClientConfig::getFadeSeconds, " s", true,
                v -> BackUtilsClientConfig.setFadeSeconds(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.right_margin", 0f, 200f,
                BackUtilsClientConfig::getRightMargin, " px", true,
                v -> BackUtilsClientConfig.setRightMargin(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.top_margin", 0f, 400f,
                BackUtilsClientConfig::getTopMargin, " px", true,
                v -> BackUtilsClientConfig.setTopMargin(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.log_button_size", 8f, 128f,
                BackUtilsClientConfig::getButtonSize, " px", true,
                v -> BackUtilsClientConfig.setButtonSize(Math.round(v))));
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.log_button_margin", 0f, 200f,
                BackUtilsClientConfig::getButtonMargin, " px", true,
                v -> BackUtilsClientConfig.setButtonMargin(Math.round(v))));
        // The administrator corner's own button, its alert icon and that icon's fade are client
        // settings like the rest of this tab, but they are built with the alert rows below rather than
        // here: the corner exists for operators alone, and a player who will never see it has no
        // business being told it can be sized.
        scroll.addScrollViewChild(sliderRow(built, "backutils.menu.setting.backdrop_opacity", 0f, 1f,
                BackUtilsClientConfig::getMenuBackgroundOpacity, "", false,
                v -> BackUtilsClientConfig.setMenuBackgroundOpacity(v)));

        // The operator alert is client-side: the sound is played here, and only ever by the player
        // who chose it, so none of this belongs in the server's config.
        //
        // Its rows are not added here, and not at build time at all: they belong to an operator's
        // menu, and whether this client is one is the server's answer, which can change while the
        // client is running. applyAlertScope is where they are asked for, on every open and again
        // whenever that answer moves.
    }

    /**
     * {@return the rows that configure the operator alert}, for an operator's own menu
     *
     * <p>Shown to an operator and to nobody else. An alert is sent to operators alone, so to
     * anybody else every one of these is a setting for something that cannot happen — and one that
     * tells them such a thing exists to be configured, which is the operator's business and not
     * theirs.
     */
    private static List<UIElement> alertControls(Built built) {
        List<UIElement> rows = new ArrayList<>();

        rows.add(headingLabel(built, "backutils.menu.alerts.heading"));
        rows.add(toggleRow(built, "backutils.menu.alerts.sound",
                BackUtilsClientConfig::isAdminAlertEnabled,
                BackUtilsClientConfig::setAdminAlertEnabled));
        rows.add(alertSoundRow(built));
        rows.add(sliderRow(built, "backutils.menu.alerts.volume", 0f, 2f,
                BackUtilsClientConfig::getAdminAlertVolume, "", false,
                BackUtilsClientConfig::setAdminAlertVolume));
        rows.add(sliderRow(built, "backutils.menu.alerts.pitch", 0.5f, 2f,
                BackUtilsClientConfig::getAdminAlertPitch, "", false,
                BackUtilsClientConfig::setAdminAlertPitch));
        rows.add(toggleRow(built, "backutils.menu.alerts.icon",
                BackUtilsClientConfig::isAdminAlertMarkerEnabled,
                BackUtilsClientConfig::setAdminAlertMarker));
        // The corner itself, which only ever exists for an operator: its button, the icon above it and
        // the fade that icon pulses with. Client settings all three, since they are about this player's
        // own screen - which is why they are here rather than in the server's settings.
        rows.add(sliderRow(built, "backutils.menu.setting.admin_button_size", 8f, 128f,
                BackUtilsClientConfig::getAdminButtonSize, " px", true,
                v -> BackUtilsClientConfig.setAdminButtonSize(Math.round(v))));
        rows.add(sliderRow(built, "backutils.menu.alerts.icon_size", 4f, 64f,
                BackUtilsClientConfig::getAdminAlertIconSize, " px", true,
                v -> BackUtilsClientConfig.setAdminAlertIconSize(Math.round(v))));
        rows.add(sliderRow(built, "backutils.menu.alerts.icon_fade", 0f, 10f,
                BackUtilsClientConfig::getAdminAlertPulseSeconds, " s", false,
                v -> BackUtilsClientConfig.setAdminAlertPulseSeconds(v)));
        return rows;
    }

    /**
     * {@return the row that names the alert sound}, with a button to play it: the sound is otherwise
     * heard only when an alert actually arrives, which is no way to choose one.
     */
    private static UIElement alertSoundRow(Built built) {
        TextField field = new TextField();
        field.layout(l -> l.flex(1).height(14));
        field.setTextResponder(BackUtilsClientConfig::setAdminAlertSound);

        Button play = new Button();
        // Wider than the English needs: "Играть" is the shortest wording that still means play.
        translated(built, play.text, "backutils.menu.alerts.play");
        play.layout(l -> l.width(46).height(14));
        play.setOnClick(e -> AdminAlerts.preview());

        // Seeded through the same reader on every open, so a hand-edited sound reaches the field
        // rather than being written over by the one the tree was built with.
        Runnable seed = () -> field.setText(BackUtilsClientConfig.getAdminAlertSound(), false);
        built.configControls.add(seed);
        seed.run();

        return controlRow(built, "backutils.menu.alerts.sound_id", field, play);
    }

    /**
     * {@return the row for one numeric setting}
     *
     * @param current reads the setting as the config holds it now, rather than carrying the value the
     *                tree happened to be built with, so the row can be put back to it on every open
     */
    private static UIElement sliderRow(Built built, String key, float min, float max,
                                       DoubleSupplier current, String suffix, boolean whole,
                                       FloatConsumer apply) {
        Label value = new Label();
        value.textStyle(t -> t.fontSize(9f).textColor(PLAIN).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        value.layout(l -> l.width(52));

        Slider.Horizontal slider = new Slider.Horizontal();
        slider.setRange(min, max);
        slider.layout(l -> l.flex(1).height(14));
        slider.setOnValueChanged(v -> {
            value.setText(format(v, whole) + suffix, false);
            apply.accept(v);
        });

        // One reader for both the value it is built with and the value it is put back to on every
        // open, so the readout cannot disagree with the handle. notify = false throughout: seeding a
        // control must never write the value it was just given back into the config it came from.
        Runnable seed = () -> {
            float now = (float) current.getAsDouble();
            slider.setValue(now, false);
            value.setText(format(now, whole) + suffix, false);
        };
        built.configControls.add(seed);
        seed.run();

        return controlRow(built, key, slider, value);
    }

    /**
     * {@return the row for one switch}
     *
     * @param current reads the setting as the config holds it now, for the same reason as
     *                {@link #sliderRow}'s
     */
    private static UIElement toggleRow(Built built, String key, BooleanSupplier current,
                                       Consumer<Boolean> apply) {
        Toggle toggle = new Toggle();
        toggle.layout(l -> l.width(24).height(12));
        toggle.setOnToggleChanged(apply::accept);

        Runnable seed = () -> toggle.setOn(current.getAsBoolean(), false);
        built.configControls.add(seed);
        seed.run();

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
     * Builds the Profiles tab: the strip that carries the create button and the server's answer, and
     * under it the sub-tabs whose lists that button works on.
     *
     * <p>The strip sits above the sub-tabs rather than inside one, because there is one create button
     * and it serves whichever kind is in view: {@link #onProfileKindChanged} relabels it as the sub-tab
     * changes, and {@link #createProfile} opens the editor for the kind selected. Built into the chat
     * panel instead, as it was, it left the name profiles with no way to make one at all — the panel
     * under them is a fixed one, which deliberately hides the create button that its browsing form has.
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

        // A player's own name profiles: the same panel the background menu opens on a player, with its
        // browsing removed, for an operator.
        built.nameTab = new AdminProfileTab(true);

        built.chatTabHeader = translatedTab(built, "backutils.menu.tab.chat");
        built.namesTabHeader = translatedTab(built, "backutils.menu.tab.names");
        built.profileTabs = new TabView();
        // The strip stays and the panel border goes: this tab is already inside the menu's tab view.
        built.profileTabs.tabContentContainer(
                c -> c.style(s -> s.backgroundTexture(IGuiTexture.EMPTY)));
        built.profileTabs.addTab(built.chatTabHeader, wrap(built.profileList));
        built.profileTabs.addTab(built.namesTabHeader, built.nameTab.root());
        built.profileTabs.setOnTabSelected(tab -> {
            if (instance != null) instance.onProfileKindChanged(tab);
        });

        built.profilePanel = new UIElement();
        built.profilePanel.layout(l -> l.widthPercent(100).heightPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(INNER_GAP));
        built.profilePanel.addChildren(built.profileHeader, built.profileTabs);
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
     * <p>The server's own answer, synced with the rest of the profile options - and an operator counts,
     * because the corner and the administrator menu are already theirs: an operator who can rename
     * anyone through the menu but cannot make a name profile for themselves is a rule that only looks
     * like a rule. The permission stays the gate for everybody else, which is what keeps names - the
     * thing everyone else reads a player by - out of reach of a player who has not been trusted with
     * them.
     */
    private static boolean canManageNames() {
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        ProfileOptions options =
                player.getData(net.xlebupaksa.backutils.data.ModAttachments.OPTIONS.get());
        return Operators.isOperator() || (options != null && options.canManageNames());
    }

    /**
     * Writes the create button's label for the kind in view. One button serves both, so its words are
     * the only thing that says which editor a click opens.
     */
    private void labelNewProfile(boolean names) {
        built.newProfile.setText(names
                ? Text.of("backutils.menu.profiles.new_name")
                : Text.of("backutils.menu.profiles.new_chat"), false);
    }

    private void onProfileKindChanged(Tab tab) {
        labelNewProfile(tab == built.namesTabHeader);
    }

    /**
     * Puts the Profiles tab in step with what this client may do, and sizes what is under the strip.
     *
     * <p>Called on every open and again from {@link #render} whenever the answer moves, because the
     * answer is the server's: the operator level arrives with the join packet and the profile options
     * as a synced attachment, and the tree here is built once and kept for the life of the client. A
     * menu whose name half was hidden by an answer that was not in yet would otherwise stay hidden
     * until it was closed and opened again, which is the staleness the settings rows were given the
     * config revision for.
     */
    private void applyProfileScope(int width, int height) {
        boolean staff = canManageNames();
        staffApplied = staff;
        profileWidth = width;
        profileHeight = height;

        // The names half exists for an operator and for whoever the server has trusted with names;
        // hiding it is not what stops a player making one - createProfile asks again - but offering a
        // panel the server would refuse is a lie about what the player can do.
        built.namesTabHeader.setDisplay(staff);
        built.profileTabs.tabHeaderContainer.setDisplay(staff);
        if (!staff && built.profileTabs.getSelectedTab() != built.chatTabHeader) {
            built.profileTabs.selectTab(built.chatTabHeader);
        }
        labelNewProfile(staff && built.profileTabs.getSelectedTab() == built.namesTabHeader);

        // The sub-tabs take what the strip leaves, and their contents what ldlib2's own five-pixel
        // padding and the sub-tab strip leave of that. Sized before the name panel is pointed at
        // anybody: pointing it rebuilds its rows, and those wrap to the width set here.
        int tabsHeight = Math.max(20, height - PROFILE_HEADER_HEIGHT - INNER_GAP);
        int contentHeight = Math.max(20,
                tabsHeight - (staff ? TAB_STRIP_HEIGHT : 0) - TAB_CONTENT_PAD * 2);
        int contentWidth = Math.max(60, width - TAB_CONTENT_PAD * 2);
        built.profileTabs.layout(l -> l.width(width).height(tabsHeight));
        built.profileList.layout(l -> l.width(contentWidth).height(contentHeight));
        built.nameTab.layout(contentWidth, contentHeight);

        // The name panel manages the account this client is logged in as, so it is told whenever it
        // becomes reachable: pointed at nobody while the answer was no, it would draw no rows for the
        // player who has just been told yes. setTarget ignores a target it already holds.
        if (staff && Minecraft.getInstance().player != null) {
            built.nameTab.setTarget(Minecraft.getInstance().player.getGameProfile().getName());
        }
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

        // Drawn the way the chat draws its own line, so Ember renders the format here as it will in
        // chat; "{m}" becomes a sample. Every row in this list is a chat profile - the name profiles
        // are the operator's own tab - so a written \n is a break here as it is in the line itself.
        // See PreviewLabel for why the rows are the chat's own and not one literal per row.
        PreviewLabel preview = new PreviewLabel(previewRows(isDefault
                ? PREVIEW_SAMPLE
                : row.format().replace(ProfileMarkup.MESSAGE, PREVIEW_SAMPLE)));

        UIElement column = new UIElement();
        column.layout(l -> l.flex(1).flexDirection(FlexDirection.COLUMN));
        column.addChildren(name, preview);

        UIElement element = new UIElement();
        element.layout(l -> l.widthPercent(100)
                .height(Math.max(LINE_STEP * 2 + 6, LINE_STEP + preview.contentHeight() + 6))
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

    /**
     * {@return the rows a profile's preview is drawn on}, which are the rows the chat line is drawn on
     *
     * <p>The body is the chat's own and is cut the way the chat's display cuts a line. A chat profile's text
     * breaks where it asks to — the chat line draws vanilla's own rows — so a written {@code \n} is a real
     * break there, the tags open across it are reopened under it, and a row left holding nothing but tags has
     * its characters carried to a row that has text, all of which {@link MarkupWrap#withLineBreaks} does; the
     * splitter then makes the rows of it, and those rows are what is drawn. Two rows are kept, which is the
     * room this row has.
     *
     * <p>Every row in this list is a chat profile: the name profiles are the operator's own tab, and a
     * displayed name is drawn on one row wherever the game draws it, so a break written in one is not a
     * break. That is why there is no branch for one here, where the editor and the operator's own tab each
     * need one.
     */
    private List<FormattedCharSequence> previewRows(String text) {
        List<FormattedCharSequence> rows = Minecraft.getInstance().font.split(
                Component.literal(MarkupWrap.withLineBreaks(text)), Math.max(40, listWidth - 160));
        return rows.size() > 2 ? rows.subList(0, 2) : rows;
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
        // The operator alert's rows belong to a menu whose client was an operator when it was last
        // asked, which is the server's answer and may have moved since: applied before the seeding
        // below, so rows that arrive now are seeded with it.
        applyAlertScope();
        // For the same reason, and because the config can have been reloaded since the last open:
        // the controls are put back to the values it holds now, so the first drag of this visit
        // writes what the row shows rather than what the tree was built with.
        seedConfigControls();

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

        // The Profiles tab is a column of its own: the strip that carries the create button, then the
        // sub-tabs an operator gets. What is inside them, and which of them is offered at all, is
        // applyProfileScope's: it is applied on every open and again whenever the answer moves.
        built.profilePanel.layout(l -> l.width(rightWidth).height(listHeight));
        applyProfileScope(rightWidth, listHeight);

        built.ui.setScreenAndInit(this);
        // setScreenAndInit only lays out; the widget has to be registered to render and take input.
        addRenderableWidget(built.ui.getWidget());
        super.init();
        setFocused(built.ui.getWidget());

        // Memory rows wrap to the list's width, and the width only becomes known here.
        listWidth = rightWidth;

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
        // The config's file can change on disk while the menu is open as well, and that reload is
        // nobody's answer to this screen: the settings rows are put back to the values it now holds,
        // but only when the revision actually moved, so a drag in progress is not fought.
        if (BackUtilsClientConfig.revision() != seededConfigRevision) seedConfigControls();
        // The operator level and the profile options are both the server's, and either can land while
        // the menu is open: the name half of the Profiles tab follows the answer now rather than the
        // one the tree was built with. Compared rather than applied every frame, so the layout is only
        // redone when there is something to redo.
        if (canManageNames() != staffApplied) applyProfileScope(profileWidth, profileHeight);
        // The same answer decides the alert rows, and they are the one part of the tree a change can
        // add to: seeded here rather than left to the revision check below, which would not see a row
        // that did not exist when it last ran.
        if (Operators.isOperator() != alertsApplied) {
            applyAlertScope();
            seedConfigControls();
        }
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
