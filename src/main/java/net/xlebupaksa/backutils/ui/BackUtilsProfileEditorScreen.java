package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ColorSelector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.tysontheember.emberstextapi.compat.patchouli.PatchouliBypass;
import net.xlebupaksa.backutils.client.BackUtilsClientConfig;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.data.MarkupWrap;
import net.xlebupaksa.backutils.network.AdminProfileCache;
import net.xlebupaksa.backutils.network.AdminProfileEditPayload;
import net.xlebupaksa.backutils.network.AdminProfileFeedbackCache;
import net.xlebupaksa.backutils.network.AdminProfileNetwork;
import net.xlebupaksa.backutils.network.ProfileEditPayload;
import net.xlebupaksa.backutils.network.ProfileFeedbackCache;
import net.xlebupaksa.backutils.network.ProfileListCache;
import net.xlebupaksa.backutils.network.ProfileListPayload;
import net.xlebupaksa.backutils.network.ProfileNetwork;
import net.xlebupaksa.backutils.profile.ProfileMarkup;
import net.xlebupaksa.backutils.profile.ProfileSound;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * The window a profile is written in.
 *
 * <h2>Two ways to say the same thing</h2>
 * The simple controls — a colour, four style switches, the text the message goes inside — are
 * composed into markup on the server, so they always produce something the sanitizer accepts.
 * Advanced mode <b>replaces</b> them with the raw format: a format is either written by the
 * controls or written by hand, so the two never show at once.
 *
 * <h2>What a profile does not touch</h2>
 * Only the message. The name before it and the separator after it come from the name profile an
 * administrator sets and from the server's chat settings, which is why the server refuses
 * {@code {player}}.
 *
 * <h2>Why the widget render is wrapped</h2>
 * Ember's Text API parses every {@code Component.literal} it draws, text fields included.
 * {@link PatchouliBypass} is the library's nestable, thread-local switch for editors that draw raw
 * markup, honoured by the literal renderer and the width splitter, so the widget tree is rendered
 * with it on and the preview with it off.
 */
@OnlyIn(Dist.CLIENT)
public final class BackUtilsProfileEditorScreen extends Screen {

    private static final int PANEL_WIDTH = 470;

    private static final int PAD = 10;
    private static final int GAP = 4;
    private static final int ROW = 16;
    private static final int LINE = 10;
    private static final int LABEL_WIDTH = 84;
    /** The free-text sound field beside the palette dropdown, for an operator. */
    private static final int SOUND_FIELD_WIDTH = 120;
    /** The picker is square: its colour surface takes its height from its width. */
    private static final int PICKER = 100;
    /** Room under the surface for the picker's hex field and its Copy button. */
    private static final int PICKER_EXTRA = 20;
    private static final int SIMPLE_BLOCK_HEIGHT = PICKER + PICKER_EXTRA + GAP + ROW;
    private static final int PREVIEW_LINES = 2;

    private static final int PANEL_BG = 0xF0101016;
    private static final int MUTED = 0xFF9AA3B2;
    private static final int PLAIN = 0xFFE2E8F0;
    private static final int WARNING = 0xFFFF8080;
    private static final int GOOD = 0xFF8FD98F;

    private static final String DOCS_URL = "https://tysontheember.dev/embers-text-api/markup/syntax/";
    private static final String DOCS_TEXT = "Syntax: tysontheember.dev/embers-text-api/markup/syntax/";
    private static final String SAMPLE = "Hello there";
    /** What {@code {player}} becomes in a preview when there is no name to put there. */
    private static final String SAMPLE_PLAYER = "Player";

    /** Shown only in advanced mode, above the raw field, for a player's own chat profile. */
    private static final List<String> INSTRUCTIONS_SELF = List.of(
            "Ember markup. {m} is your message; your name is not part of a profile.",
            "Colours, gradients and animations only — no clicks, hovers or sounds.");

    /** The same instructions for the background menu, where the players' allow-list does not apply. */
    private static final List<String> INSTRUCTIONS_ADMIN_CHAT = List.of(
            "Ember markup. {m} is the message; {player} is the account name.",
            "Anything well-formed goes — the players' tag list does not apply to staff.");

    private static final List<String> INSTRUCTIONS_ADMIN_NAME = List.of(
            "Ember markup. {player} is the account name; {m} means nothing in a name.",
            "Anything well-formed but sounds — a name is drawn on every nametag.");

    private static BackUtilsProfileEditorScreen instance;

    private static final class Built {
        ModularUI ui;
        UIElement root;
        UIElement panel;
        Label title;
        TextField nameField;
        /** The controls advanced mode replaces: styles and text on the left, the picker on the right. */
        UIElement simpleBlock;
        Toggle boldToggle;
        Toggle italicToggle;
        Toggle underlineToggle;
        Toggle strikeToggle;
        ColorSelector picker;
        Label colourValue;
        /** One row, two possible contents: a chat profile's sound, or a name's text. */
        UIElement extraRow;
        Label extraLabel;
        Selector<String> soundSelector;
        /** Any sound id at all, for an operator; the dropdown beside it is the palette. */
        TextField soundField;
        TextField innerField;
        Toggle advancedToggle;
        UIElement instructionsRow;
        Label[] instructions;
        UIElement advancedFieldRow;
        TextField advancedField;
        UIElement previewRow;
        Label status;
    }

    private final Built built;

    /** True when somebody else's profile is being edited from the background menu. */
    private boolean adminMode;
    /** True when a staff edit was opened from the background menu rather than the player's own. */
    private boolean returnToAdmin = true;
    /** True when a displayed name is being written rather than a chat format. */
    private boolean nameMode;
    /** The player whose profile this is, for the background menu; "" for the player's own. */
    private String target = "";

    /** The profile being changed, or null when this is a new one. */
    private ProfileListPayload.Row editing;
    /** The chosen colour as {@code #rrggbb}, or "" for none. */
    private String colour = "";
    private boolean bold;
    private boolean italic;
    private boolean underline;
    private boolean strikethrough;
    private boolean advanced;
    private String sound = "";
    /** The last feedback revision acted on, so each answer is handled once. */
    private int feedbackRevision = -1;
    /** The palette revision the dropdown was last filled from. */
    private int paletteRevision = -1;
    /** The colour the picker was last seen holding, so a change can be noticed. */
    private int lastPickerColour;
    /** Set once the form has been sent, so a second click cannot send it twice. */
    private boolean sent;

    /** The clickable link's rectangle, measured while rendering. */
    private int linkX;
    private int linkY;
    private int linkWidth;
    private int linkHeight;

    private BackUtilsProfileEditorScreen(Built built) {
        super(Component.literal("Background Utilities - Profile"));
        this.built = built;
    }

    /** Opens the window on the player's own profile. */
    public static void open(ProfileListPayload.Row existing) {
        BackUtilsProfileEditorScreen screen = screen();
        screen.adminMode = false;
        screen.nameMode = false;
        screen.target = "";
        screen.load(existing);
        Minecraft.getInstance().setScreen(screen);
    }

    /** Opens the window on somebody else's profile, the way the background menu does. */
    public static void openFor(String player, boolean names, ProfileListPayload.Row existing) {
        openFor(player, names, existing, true);
    }

    /**
     * The same, for a player managing their own name profiles from their own menu.
     *
     * <p>{@code backToAdmin} decides where closing goes next: an operator editing their own name from
     * the player menu is not dropped into the background menu.
     */
    public static void openFor(String player, boolean names, ProfileListPayload.Row existing,
                               boolean backToAdmin) {
        BackUtilsProfileEditorScreen screen = screen();
        screen.adminMode = true;
        screen.nameMode = names;
        screen.target = player == null ? "" : player;
        screen.returnToAdmin = backToAdmin;
        screen.load(existing);
        Minecraft.getInstance().setScreen(screen);
    }

    /** {@return the one reused instance, building it on first use} */
    private static BackUtilsProfileEditorScreen screen() {
        if (instance == null) {
            Built built = new Built();
            build(built);
            built.ui = ModularUI.of(UI.of(built.root));
            built.ui.setTickWhileRending(true);
            instance = new BackUtilsProfileEditorScreen(built);
        }
        return instance;
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private static void build(Built built) {
        built.title = text("New profile", PLAIN, 12f, LINE + 4);

        built.nameField = new TextField();
        built.nameField.setText("", false);
        built.nameField.layout(l -> l.flex(1).height(14));
        built.nameField.textFieldStyle(s -> s.placeholder(Component.literal("Profile name")));
        built.nameField.setTextResponder(value -> {
            if (instance != null) instance.refreshStatus();
        });
        UIElement nameRow = row("Name", built.nameField);

        built.boldToggle = styleToggle(value -> setStyle(StylePart.BOLD, value));
        built.italicToggle = styleToggle(value -> setStyle(StylePart.ITALIC, value));
        built.underlineToggle = styleToggle(value -> setStyle(StylePart.UNDERLINE, value));
        built.strikeToggle = styleToggle(value -> setStyle(StylePart.STRIKE, value));

        // The picker on the left, the switches on the right, stacked so they line up with it.
        UIElement left = new UIElement();
        left.layout(l -> l.width(PICKER).height(SIMPLE_BLOCK_HEIGHT)
                .flexDirection(FlexDirection.COLUMN).gapAll(GAP));

        built.picker = new ColorSelector();
        built.picker.layout(l -> l.width(PICKER).height(PICKER + PICKER_EXTRA));
        // The picker arrives with a hex field, three RGB rows and an alpha bar under the colour square.
        // The alpha bar and the RGB rows go: there is no room for a full-height picker plus four more
        // rows, and a transparent chat colour is an invisible message. The hex field stays, because a
        // typed value cannot silently fail to register the way a drag can.
        built.picker.alphaSlider.setVisible(false);
        built.picker.alphaSlider.layout(l -> l.height(0));
        List<UIElement> pickerRows = built.picker.textContainer.getChildren();
        for (int i = 1; i < pickerRows.size(); i++) {
            UIElement row = pickerRows.get(i);
            row.setVisible(false);
            row.layout(l -> l.height(0));
        }
        built.picker.setColor(0xFFFFFFFF, false);
        built.picker.setOnColorChangeListener((IntConsumer) argb -> {
            if (instance != null) instance.onColourPicked(argb);
        });

        built.colourValue = text("", MUTED, 9f, ROW);
        Button none = new Button();
        none.setText("None", false);
        none.layout(l -> l.width(50).height(14));
        none.setOnClick(e -> {
            if (instance != null) instance.clearColour();
        });

        UIElement colourControls = new UIElement();
        colourControls.layout(l -> l.width(PICKER).height(ROW)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(2));
        colourControls.addChildren(none, built.colourValue);

        left.addChildren(built.picker, colourControls);

        UIElement styles = new UIElement();
        styles.layout(l -> l.flex(1).height(SIMPLE_BLOCK_HEIGHT)
                .flexDirection(FlexDirection.COLUMN).justifyContent(AlignContent.CENTER).gapAll(GAP));
        styles.addChildren(labelled(built.boldToggle, "Bold"), labelled(built.italicToggle, "Italic"),
                labelled(built.underlineToggle, "Underline"), labelled(built.strikeToggle, "Strike"));

        built.simpleBlock = new UIElement();
        built.simpleBlock.layout(l -> l.widthPercent(100).height(SIMPLE_BLOCK_HEIGHT)
                .flexDirection(FlexDirection.ROW).gapAll(GAP));
        built.simpleBlock.addChildren(left, styles);

        built.soundSelector = new Selector<>();
        built.soundSelector.layout(l -> l.flex(1).height(14));
        built.soundSelector.setOnValueChanged(value -> {
            if (instance == null) return;
            // "none" is the dropdown's word for "no sound"; the profile stores an empty string.
            instance.sound = "none".equals(value) ? "" : value;
            instance.refreshStatus();
        });

        // The text a displayed name is made of: {player} for the account name, or anything else, with
        // the colour and the switches around it.
        built.innerField = new TextField();
        built.innerField.setText(ProfileMarkup.PLAYER, false);
        built.innerField.layout(l -> l.flex(1).height(14));
        built.innerField.textFieldStyle(s ->
                s.placeholder(Component.literal(ProfileMarkup.PLAYER)));
        built.innerField.setTextResponder(value -> {
            if (instance != null) instance.refreshStatus();
        });

        // Only for an operator: the dropdown holds the palette vetted for players, which is no limit on
        // markup that can reach any sound id.
        built.soundField = new TextField();
        built.soundField.setText("", false);
        built.soundField.layout(l -> l.width(SOUND_FIELD_WIDTH).height(14));
        built.soundField.textFieldStyle(s -> s.placeholder(Component.literal("any sound id")));
        built.soundField.setTextResponder(value -> {
            if (instance == null) return;
            instance.sound = value == null ? "" : value.trim();
            instance.refreshStatus();
        });

        built.extraLabel = text("Sound", PLAIN, 9f, ROW);
        built.extraLabel.layout(l -> l.width(LABEL_WIDTH).height(ROW));

        built.extraRow = new UIElement();
        built.extraRow.layout(l -> l.widthPercent(100).height(ROW)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(GAP));
        built.extraRow.addChildren(built.extraLabel, built.soundSelector, built.soundField,
                built.innerField);

        built.advancedToggle = new Toggle();
        built.advancedToggle.setOn(false, false);
        built.advancedToggle.layout(l -> l.width(20).height(12));
        built.advancedToggle.setText("", false);
        built.advancedToggle.setOnToggleChanged(value -> {
            if (instance != null) instance.onAdvancedToggled(value);
        });
        UIElement advancedRow = row("Advanced mode", built.advancedToggle);

        // Two lines always, so the strip only ever changes its words. Plain labels, because drawing
        // the explanation of markup through Ember would parse it.
        built.instructions = new Label[INSTRUCTIONS_SELF.size()];
        built.instructionsRow = new UIElement();
        built.instructionsRow.layout(l -> l.widthPercent(100).height(instructionHeight()));
        UIElement instructionColumn = new UIElement();
        instructionColumn.layout(l -> l.widthPercent(100).heightPercent(100)
                .flexDirection(FlexDirection.COLUMN));
        for (int i = 0; i < built.instructions.length; i++) {
            Label label = text("", MUTED, 8f, LINE);
            label.layout(l -> l.widthPercent(100).height(LINE));
            built.instructions[i] = label;
            instructionColumn.addChild(label);
        }
        // The link is drawn in render(), because a line that opens a browser needs a click handler and
        // ldlib2's labels have none. The space for it is reserved here.
        UIElement linkSpace = new UIElement();
        linkSpace.layout(l -> l.widthPercent(100).height(LINE + 6));
        instructionColumn.addChild(linkSpace);
        built.instructionsRow.addChild(instructionColumn);

        built.advancedField = new TextField();
        built.advancedField.setText("", false);
        built.advancedField.layout(l -> l.widthPercent(100).height(14));
        built.advancedField.textFieldStyle(s ->
                s.placeholder(Component.literal("<grad colors=#ff5555,#5555ff>{m}</grad>")));
        built.advancedField.setTextResponder(value -> {
            if (instance != null) instance.refreshStatus();
        });
        built.advancedFieldRow = new UIElement();
        built.advancedFieldRow.layout(l -> l.widthPercent(100).height(ROW));
        built.advancedFieldRow.addChild(built.advancedField);

        Label previewCaption = text("Preview", MUTED, 8f, LINE);
        previewCaption.layout(l -> l.widthPercent(100).height(LINE));

        built.previewRow = new UIElement();
        built.previewRow.layout(l -> l.widthPercent(100).height(PREVIEW_LINES * LINE + 4));

        built.status = text("", MUTED, 9f, ROW);
        UIElement statusRow = new UIElement();
        statusRow.layout(l -> l.widthPercent(100).height(ROW));
        statusRow.addChild(built.status);

        UIElement buttons = new UIElement();
        buttons.layout(l -> l.widthPercent(100).height(ROW)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(GAP));
        Button save = new Button();
        save.setText("Save", false);
        save.layout(l -> l.width(60).height(14));
        save.setOnClick(e -> {
            if (instance != null) instance.save();
        });
        Button cancel = new Button();
        cancel.setText("Cancel", false);
        cancel.layout(l -> l.width(60).height(14));
        cancel.setOnClick(e -> {
            if (instance != null) instance.onClose();
        });
        buttons.addChildren(save, cancel);

        built.panel = new UIElement();
        built.panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        built.panel.layout(l -> l.width(PANEL_WIDTH).height(panelHeightFor(false, true, false))
                .paddingAll(PAD).flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        built.panel.addChildren(built.title, nameRow, built.simpleBlock, built.extraRow, advancedRow,
                built.instructionsRow, built.advancedFieldRow, previewCaption, built.previewRow,
                statusRow, buttons);

        // The full-screen root centres the panel rather than positioning it by hand, so it stays
        // centred when the window is resized.
        built.root = new UIElement();
        built.root.layout(l -> l.flexDirection(FlexDirection.COLUMN)
                .justifyContent(AlignContent.CENTER)
                .alignItems(AlignItems.CENTER));
        built.root.addChild(built.panel);
    }

    /** {@return the height of the instruction strip, which is the same in every mode} */
    private static int instructionHeight() {
        return INSTRUCTIONS_SELF.size() * LINE + LINE + 6;
    }

    /**
     * A labelled switch, with the toggle's own text cleared: it defaults to the word "Toggle", which
     * draws across the label beside it.
     */
    private static Toggle styleToggle(java.util.function.Consumer<Boolean> apply) {
        Toggle toggle = new Toggle();
        toggle.setOn(false, false);
        toggle.setText("", false);
        toggle.layout(l -> l.width(20).height(12));
        toggle.setOnToggleChanged(apply::accept);
        return toggle;
    }

    private static UIElement labelled(Toggle toggle, String name) {
        Label label = text(name, PLAIN, 9f, ROW);
        label.layout(l -> l.width(48).height(ROW));

        UIElement holder = new UIElement();
        holder.layout(l -> l.height(ROW).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(2));
        holder.addChildren(toggle, label);
        return holder;
    }

    /** Which of the four styles a toggle switches, so one callback can serve all of them. */
    private enum StylePart { BOLD, ITALIC, UNDERLINE, STRIKE }

    private static void setStyle(StylePart part, boolean value) {
        if (instance == null) return;
        switch (part) {
            case BOLD -> instance.bold = value;
            case ITALIC -> instance.italic = value;
            case UNDERLINE -> instance.underline = value;
            case STRIKE -> instance.strikethrough = value;
        }
        instance.refreshStatus();
    }

    private static UIElement row(String label, UIElement control) {
        Label name = text(label, PLAIN, 9f, ROW);
        name.layout(l -> l.width(LABEL_WIDTH).height(ROW));

        UIElement element = new UIElement();
        element.layout(l -> l.widthPercent(100).height(ROW)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(GAP));
        element.addChildren(name, control);
        return element;
    }

    private static Label text(String value, int colour, float size, int height) {
        Label label = new Label();
        label.setText(value, false);
        label.textStyle(t -> t.fontSize(size).textColor(colour).textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER)
                .adaptiveWidth(true));
        label.layout(l -> l.height(height));
        return label;
    }

    // ------------------------------------------------------------------
    // Loading a profile into the form
    // ------------------------------------------------------------------

    private void load(ProfileListPayload.Row existing) {
        editing = existing;
        colour = "";
        bold = false;
        italic = false;
        underline = false;
        strikethrough = false;
        advanced = false;
        sound = "";
        sent = false;
        paletteRevision = -1;
        // Only answers that arrive after this point belong to this sitting. A staff edit is
        // answered on its own channel, so the sitting follows that one instead.
        feedbackRevision = adminMode
                ? AdminProfileFeedbackCache.revision()
                : ProfileFeedbackCache.revision();

        // Which placeholder the controls wrap, and so which shape of format they reproduce: a chat
        // format carries {m}, a displayed name carries {player} or whatever the box holds.
        String placeholder = nameMode ? ProfileMarkup.PLAYER : ProfileMarkup.MESSAGE;

        String format = existing == null ? "" : existing.format();
        ProfileMarkup.Simple simple = existing == null
                ? null : ProfileMarkup.splitSimple(format, placeholder);
        // A simple profile is one the controls can reproduce exactly. What the splitter does not
        // recognise is a format with its own tags inside the text, which opens in advanced mode.
        if (simple != null && !nameMode && !placeholder.equals(simple.inner())) simple = null;

        applyMode();
        applyTitle(existing);

        if (existing == null) {
            built.nameField.setText("", false);
            built.advancedField.setText("", false);
            built.innerField.setText(ProfileMarkup.PLAYER, false);
        } else {
            built.nameField.setText(existing.name(), false);
            if (simple != null) {
                colour = simple.colour();
                bold = simple.bold();
                italic = simple.italic();
                underline = simple.underline();
                strikethrough = simple.strike();
                built.advancedField.setText(format, false);
                if (nameMode) built.innerField.setText(simple.inner(), false);
            } else {
                // Written by hand, or by an administrator through /profile: showing it as toggles would
                // rewrite it into something it is not.
                advanced = true;
                built.advancedField.setText(format, false);
                if (nameMode) built.innerField.setText(ProfileMarkup.PLAYER, false);
            }
            sound = existing.sound() == null ? "" : existing.sound();
        }

        applyColour();
        applyStyleToggles();
        applyAdvancedVisibility();
        applyPalette();
        refreshStatus();
    }

    /**
     * Points the window at what it is editing: a name has no sound and gets the box its own text is
     * written in, and the placeholder on the raw field follows the kind.
     */
    private void applyMode() {
        built.advancedField.textFieldStyle(s -> s.placeholder(Component.literal(nameMode
                ? "<bold>{player}</bold>"
                : "<grad colors=#ff5555,#5555ff>{m}</grad>")));

        List<String> lines = instructionLines();
        for (int i = 0; i < built.instructions.length; i++) {
            built.instructions[i].setText(i < lines.size() ? lines.get(i) : "", false);
        }
        applyExtraRow();
    }

    /**
     * Shows the one control the shared row carries, and hides the other.
     *
     * <p>The control is switched out of the layout with {@code display: none}; the row is only given
     * no height. A collapsed row is still measured against the widget inside it, and a dropdown with
     * a scrollable list cannot satisfy a box of no height, so it re-lays the whole tree out on every
     * frame. An empty box has nothing in it to measure.
     */
    private void applyExtraRow() {
        boolean sound = hasSound();
        // The text is what a displayed name is made of, so it is offered wherever the controls are;
        // in advanced mode the tags say it instead, by hand.
        boolean text = nameMode && !advanced;
        if (!sound) this.sound = "";

        boolean shown = sound || text;
        built.extraLabel.setText(sound ? "Sound" : "Text", false);
        built.soundSelector.setDisplay(sound);
        // The free-text sound is the operator's half of the row: the dropdown offers the palette, this
        // offers the rest of the game's sounds.
        built.soundField.setDisplay(sound && unrestricted());
        built.innerField.setDisplay(text);
        built.extraRow.setVisible(shown);
        built.extraRow.layout(l -> l.widthPercent(100).height(shown ? ROW : 0)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(GAP));
    }

    /** {@return true when this window offers a sound, which a name never does} */
    private boolean hasSound() {
        return !nameMode;
    }

    /**
     * {@return true when the checks are the operators' permissive ones}
     *
     * <p>Either an operator editing somebody else's profile, or one editing their own. It comes from
     * the synced options the chat picker uses, {@code canManageNames}, which the server checks again
     * on the way in; this copy answers while the value is typed.
     */
    private boolean unrestricted() {
        return adminMode || canManageNames();
    }

    /** {@return true when the local player is allowed to manage name profiles} */
    private static boolean canManageNames() {
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        net.xlebupaksa.backutils.data.ProfileOptions options =
                player.getData(net.xlebupaksa.backutils.data.ModAttachments.OPTIONS.get());
        return options != null && options.canManageNames();
    }

    /** {@return the two lines above the raw field, for what this window is editing} */
    private static List<String> instructionLinesFor(boolean admin, boolean names) {
        if (!admin) return INSTRUCTIONS_SELF;
        return names ? INSTRUCTIONS_ADMIN_NAME : INSTRUCTIONS_ADMIN_CHAT;
    }

    private List<String> instructionLines() {
        return instructionLinesFor(adminMode, nameMode);
    }

    private void applyTitle(ProfileListPayload.Row existing) {
        if (!adminMode) {
            built.title.setText(existing == null
                    ? "New profile"
                    : "Editing '" + existing.name() + "'", false);
            return;
        }

        String kind = nameMode ? "Name profile" : "Chat profile";
        built.title.setText(existing == null
                ? "New " + kind.toLowerCase(Locale.ROOT) + " for " + target
                : kind + " '" + existing.name() + "' for " + target, false);
    }

    private void applyStyleToggles() {
        built.boldToggle.setOn(bold, false);
        built.italicToggle.setOn(italic, false);
        built.underlineToggle.setOn(underline, false);
        built.strikeToggle.setOn(strikethrough, false);
    }

    /** Points the picker at the stored colour, or at white when there is none. */
    private void applyColour() {
        int argb = 0xFFFFFFFF;
        if (!colour.isBlank()) {
            try {
                String hex = colour.startsWith("#") ? colour.substring(1) : colour;
                argb = 0xFF000000 | Integer.parseInt(hex, 16);
            } catch (NumberFormatException ignored) {
                // A name, not a hex: the picker shows white and the label names the colour.
            }
        }
        built.picker.setColor(argb, false);
        // Recorded as well as set: the poll compares against this, and without it the first tick would
        // take the picker's own value for a choice the player had just made.
        lastPickerColour = argb;
        updateColourLabel();
    }

    private void onColourPicked(int argb) {
        // Opaque, always: see the note where the alpha slider is hidden.
        colour = String.format(Locale.ROOT, "#%06x", argb & 0xFFFFFF);
        updateColourLabel();
        refreshStatus();
    }

    private void clearColour() {
        colour = "";
        updateColourLabel();
        refreshStatus();
    }

    private void updateColourLabel() {
        if (built.colourValue == null) return;
        built.colourValue.setText(colour.isBlank() ? "none" : colour, false);
    }

    /**
     * Fills the sound list from the palette the server sent. "None" is offered first and is a real
     * choice: a profile without a sound still styles the text.
     *
     * <p>A staff edit takes the palette that came with the profile list it is editing, because an
     * administrator working through somebody's profiles may never have opened their own Profiles tab.
     */
    private void applyPalette() {
        // A name has no sound, so its dropdown is left alone: filling a list nothing can open is work
        // for a widget that is not in the layout.
        if (!hasSound()) return;

        List<String> candidates = new ArrayList<>();
        candidates.add("none");
        candidates.addAll(adminMode ? AdminProfileCache.sounds() : ProfileListCache.sounds());
        built.soundSelector.setCandidates(candidates);
        built.soundSelector.setSelected(sound == null || sound.isBlank() ? "none" : sound, false);
        // The field and the dropdown say the same thing, so switching between them loses nothing.
        built.soundField.setText(sound == null ? "" : sound, false);
    }

    private void onAdvancedToggled(boolean value) {
        advanced = value;
        if (value) {
            // Seeded from the controls, so switching modes starts from what the toggles already say
            // rather than a blank field.
            ProfileMarkup.Result composed = compose();
            if (composed.ok()) built.advancedField.setText(composed.value(), false);
        }
        applyAdvancedVisibility();
        refreshStatus();
    }

    /** {@return the controls composed into a format, checked for whoever is writing it} */
    private ProfileMarkup.Result compose() {
        if (unrestricted()) {
            return ProfileMarkup.composeStaff(colour, bold, italic, underline, strikethrough,
                    currentInner(), nameMode);
        }
        return ProfileMarkup.compose(colour, bold, italic, underline, strikethrough,
                currentInner());
    }

    /**
     * Shows one way of writing the format and hides the other. Advanced mode replaces the controls
     * rather than sitting under them, and the window is resized to match.
     */
    private void applyAdvancedVisibility() {
        // Hidden AND collapsed: the window is sized for the mode it is in, so a hidden row that kept
        // its height would push the buttons past the bottom edge. The shared row is the exception,
        // because the dropdown it can hold cannot be measured into nothing — see applyExtraRow.
        built.simpleBlock.setVisible(!advanced);
        built.simpleBlock.layout(l -> l.widthPercent(100)
                .height(advanced ? 0 : SIMPLE_BLOCK_HEIGHT)
                .flexDirection(FlexDirection.ROW).gapAll(GAP));
        built.instructionsRow.setVisible(advanced);
        built.advancedFieldRow.setVisible(advanced);
        built.instructionsRow.layout(l -> l.widthPercent(100)
                .height(advanced ? instructionHeight() : 0));
        built.advancedFieldRow.layout(l -> l.widthPercent(100).height(advanced ? ROW : 0));
        built.advancedToggle.setOn(advanced, false);
        applyExtraRow();
        layoutPanel();
    }

    // ------------------------------------------------------------------
    // The form's contents
    // ------------------------------------------------------------------

    private String currentName() {
        return editing != null ? editing.name() : currentNewName();
    }

    private String currentNewName() {
        String text = built.nameField.getText();
        return text == null ? "" : text.trim();
    }

    /**
     * {@return the text the formatting wraps}
     *
     * <p>A chat format wraps the message, so the controls write {@code {m}}. A displayed name wraps
     * text the administrator writes, so it comes from the box.
     */
    private String currentInner() {
        if (!nameMode) return ProfileMarkup.DEFAULT_INNER;
        String text = built.innerField.getText();
        return text == null || text.isBlank() ? ProfileMarkup.PLAYER : text.trim();
    }

    private String currentAdvanced() {
        String text = built.advancedField.getText();
        return text == null ? "" : text.trim();
    }

    /** {@return the format the form currently describes, checked for whoever is writing it} */
    private ProfileMarkup.Result currentFormat() {
        if (advanced) {
            return unrestricted()
                    ? ProfileMarkup.validateStaff(currentAdvanced(), nameMode)
                    : ProfileMarkup.validate(currentAdvanced());
        }
        return compose();
    }

    /**
     * Substitutes stand-ins for the placeholders so the preview reads as what will be drawn. A name
     * preview puts the account name in place of {@code {player}}, the only way to see the shape the
     * rest of the server reads.
     */
    private String previewText() {
        ProfileMarkup.Result format = currentFormat();
        String text = format.ok() ? format.value() : (advanced ? currentAdvanced() : currentInner());
        String who = target.isBlank() ? SAMPLE_PLAYER : target;
        return text.replace(ProfileMarkup.PLAYER, who).replace(ProfileMarkup.MESSAGE, SAMPLE);
    }

    private void refreshStatus() {
        if (built.status == null || built.nameField == null) return;

        String problem = localProblem();
        if (problem != null) {
            setStatus(problem, WARNING);
            return;
        }
        if (editing != null && !currentNewName().equals(editing.name())) {
            setStatus("Will be renamed to '" + currentNewName() + "'.", GOOD);
            return;
        }
        String rewritten = storedForm();
        setStatus(rewritten == null ? "Looks good" : rewritten, GOOD);
    }

    /**
     * {@return what will be stored when it differs from what was typed, or null}
     *
     * <p>Only advanced mode can differ. The one rewrite that happens is a colour attribute spelled
     * the way Ember parses but never draws — {@code value=} for {@code col=}.
     */
    private String storedForm() {
        if (!advanced) return null;
        String typed = currentAdvanced();
        ProfileMarkup.Result format = currentFormat();
        if (!format.ok() || format.value().equals(typed)) return null;
        return "Stored as " + format.value();
    }

    /** {@return the first thing wrong with the form, or null when nothing is} */
    private String localProblem() {
        ProfileMarkup.Result name = ProfileMarkup.validateName(currentNewName());
        if (!name.ok()) return name.error();

        ProfileMarkup.Result format = currentFormat();
        if (!format.ok()) return format.error();

        // A name has no sound row, and the value it would carry is cleared rather than kept.
        if (!hasSound()) return null;
        return unrestricted()
                ? ProfileSound.checkStaff(sound)
                : ProfileSound.check(sound, ProfileListCache.sounds());
    }

    private void setStatus(String message, int colour) {
        // Cut to the panel: a label draws as wide as its text whatever its box says.
        built.status.setText(fit(message), false);
        built.status.textStyle(t -> t.fontSize(9f).textColor(colour).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
    }

    /** {@return the text, cut to the panel's inner width, with an ellipsis when it was too long} */
    private String fit(String text) {
        if (text == null || text.isEmpty()) return "";
        int available = Math.round(built.panel.getSizeWidth()) - PAD * 2;
        if (available <= 0) available = PANEL_WIDTH - PAD * 2;

        Font font = Minecraft.getInstance().font;
        if (font.width(text) <= available) return text;

        String suffix = "...";
        int room = Math.max(0, available - font.width(suffix));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (font.width(out.toString() + c) > room) break;
            out.append(c);
        }
        return out + suffix;
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    private void save() {
        if (sent) return;

        String problem = localProblem();
        if (problem != null) {
            setStatus(problem, WARNING);
            return;
        }

        int flags = (bold ? ProfileEditPayload.FLAG_BOLD : 0)
                | (italic ? ProfileEditPayload.FLAG_ITALIC : 0)
                | (underline ? ProfileEditPayload.FLAG_UNDERLINE : 0)
                | (strikethrough ? ProfileEditPayload.FLAG_STRIKE : 0)
                | (advanced ? ProfileEditPayload.FLAG_ADVANCED : 0);

        String inner = advanced ? currentAdvanced() : currentInner();
        String name = currentName();
        String newName = currentNewName();

        sent = true;
        setStatus("Saving...", MUTED);

        if (adminMode) {
            // A different payload, not a flag on the player's own: that one carries no target, which is
            // what makes it safe for everyone to send, while this one names the player and the server
            // refuses it below staff permission.
            AdminProfileNetwork.send(editing == null
                    ? AdminProfileEditPayload.create(nameMode, target, newName, colour, flags,
                            inner, sound)
                    : AdminProfileEditPayload.update(nameMode, target, name, newName, colour, flags,
                            inner, sound));
            return;
        }

        ProfileNetwork.send(editing == null
                ? ProfileEditPayload.create(newName, colour, flags, inner, sound)
                : ProfileEditPayload.update(name, newName, colour, flags, inner, sound));
    }

    /**
     * Watches for the server's answer. Read on the client tick rather than during rendering: a
     * successful save closes this window by opening the menu, and switching screens mid-render leaves
     * the frame half drawn.
     */
    @Override
    public void tick() {
        PatchouliBypass.enter();
        try {
            super.tick();
        } finally {
            PatchouliBypass.exit();
        }
        pollColour();
        pollPalette();
        checkFeedback();
    }

    /**
     * Picks up a palette that arrived after the window opened, because a dropdown offering nothing but
     * "none" looks like a server with no sounds.
     */
    private void pollPalette() {
        if (!adminMode || !hasSound()) return;
        int revision = AdminProfileCache.profilesRevision();
        if (revision == paletteRevision) return;
        paletteRevision = revision;
        applyPalette();
    }

    /**
     * Picks up a colour chosen in the picker, read from the widget every tick rather than trusted to
     * its change callback: a colour that fails to arrive there would never be recorded, and comparing
     * one integer per tick cannot miss a change.
     */
    private void pollColour() {
        if (built.picker == null) return;
        int argb = built.picker.getColor();
        if (argb == lastPickerColour) return;
        lastPickerColour = argb;
        onColourPicked(argb);
    }

    /**
     * Reads the answer on the channel for the kind of edit this window was opened in: the player's own
     * changes on the profile channel, a staff edit on the administrator's, so a background edit cannot
     * close a window waiting for something else.
     */
    private void checkFeedback() {
        if (adminMode) {
            checkAdminFeedback();
            return;
        }

        if (ProfileFeedbackCache.revision() == feedbackRevision) return;
        feedbackRevision = ProfileFeedbackCache.revision();

        boolean ok = ProfileFeedbackCache.ok();
        if (ok && sent) {
            // Back to the list, which the server has already re-sent with this change in it.
            sent = false;
            BackUtilsMenuScreen.open();
            return;
        }
        if (!ok) {
            sent = false;
            setStatus(ProfileFeedbackCache.message(), WARNING);
        }
    }

    /**
     * The same for a staff edit. The answer is only read when it is about <i>this</i> profile; the
     * revision is left untouched otherwise, so an answer about somebody else's neither closes this
     * window nor is lost.
     */
    private void checkAdminFeedback() {
        if (AdminProfileFeedbackCache.revision() == feedbackRevision) return;
        if (!AdminProfileFeedbackCache.about(nameMode, target)) return;

        feedbackRevision = AdminProfileFeedbackCache.revision();
        if (AdminProfileFeedbackCache.ok()) {
            if (sent) {
                sent = false;
                // Back to the menu it came from, which re-reads both lists from the server: the counts
                // beside a name changed too.
                if (returnToAdmin) {
                    BackUtilsAdminScreen.open();
                } else {
                    BackUtilsMenuScreen.open();
                }
            }
            return;
        }
        sent = false;
        setStatus(AdminProfileFeedbackCache.message(), WARNING);
    }

    // ------------------------------------------------------------------
    // Screen
    // ------------------------------------------------------------------

    private void layoutPanel() {
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(200, this.width - 20));
        int wanted = panelHeightFor(advanced, hasSound(), nameMode && !advanced);
        int panelHeight = Math.min(wanted, Math.max(120, this.height - 20));

        built.root.layout(l -> l.width(this.width).height(this.height)
                .flexDirection(FlexDirection.COLUMN)
                .justifyContent(AlignContent.CENTER)
                .alignItems(AlignItems.CENTER));
        built.panel.layout(l -> l.width(panelWidth).height(panelHeight).paddingAll(PAD)
                .flexDirection(FlexDirection.COLUMN).gapAll(GAP));
    }

    /**
     * {@return the window's height in a mode, as the sum of the rows it shows}
     *
     * <p>Summed rather than fixed, because the controls give way to the instructions and a name puts
     * its own text row in place of the sound. A panel sized for the wrong mode pushes the buttons past
     * the bottom edge.
     *
     * <p>The gaps are counted over every child, including the ones not showing: a row of no height
     * still sits between two others.
     */
    private static int panelHeightFor(boolean advanced, boolean sound, boolean text) {
        int rows = (LINE + 4)                                  // title
                + ROW                                          // profile name
                + (advanced ? 0 : SIMPLE_BLOCK_HEIGHT)         // the controls
                + (sound || text ? ROW : 0)                    // sound, or the name's text
                + ROW                                          // advanced mode
                + (advanced ? instructionHeight() : 0)         // instructions
                + (advanced ? ROW : 0)                         // the raw format
                + LINE                                         // Preview caption
                + (PREVIEW_LINES * LINE + 4)                   // the preview
                + ROW                                          // status
                + ROW;                                         // the buttons
        return PAD * 2 + rows + GAP * 10;
    }

    @Override
    public void init() {
        layoutPanel();

        // Text measurement and rendering both have to see the raw string, or the caret and the text
        // disagree about how long the format is.
        PatchouliBypass.enter();
        try {
            built.ui.setScreenAndInit(this);
        } finally {
            PatchouliBypass.exit();
        }

        addRenderableWidget(built.ui.getWidget());
        super.init();
        setFocused(built.ui.getWidget());
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
        // The widget tree — text fields included — is drawn with markup parsing off, so a format being
        // typed is visible as the characters it is made of. Below it the preview and the link are
        // markup on purpose.
        PatchouliBypass.enter();
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            PatchouliBypass.exit();
        }

        drawPreview(graphics);
        drawDocsLink(graphics, mouseX, mouseY);
    }

    private void drawPreview(GuiGraphics graphics) {
        Font font = Minecraft.getInstance().font;
        int x = Math.round(built.previewRow.getPositionX());
        int y = Math.round(built.previewRow.getPositionY());
        int width = Math.round(built.previewRow.getSizeWidth());

        List<String> lines = MarkupWrap.wrapByWidth(previewText(), Math.max(40, width),
                line -> font.width(MarkupUtil.strip(line)));
        for (int i = 0; i < Math.min(PREVIEW_LINES, lines.size()); i++) {
            graphics.drawString(font, Component.literal(lines.get(i)), x, y + i * LINE, PLAIN, false);
        }
    }

    private void drawDocsLink(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!advanced) {
            linkWidth = 0;
            return;
        }

        Font font = Minecraft.getInstance().font;
        int x = Math.round(built.instructionsRow.getPositionX());
        int y = Math.round(built.instructionsRow.getPositionY()) + INSTRUCTIONS_SELF.size() * LINE + 2;

        boolean hovered = mouseX >= x && mouseX < x + font.width(DOCS_TEXT)
                && mouseY >= y && mouseY < y + LINE;
        Component link = Component.literal(DOCS_TEXT).withStyle(style -> style
                .withColor(hovered ? ChatFormatting.WHITE : ChatFormatting.AQUA)
                .withUnderlined(true));
        graphics.drawString(font, link, x, y, PLAIN, false);

        linkX = x;
        linkY = y;
        linkWidth = font.width(DOCS_TEXT);
        linkHeight = LINE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && linkWidth > 0
                && mouseX >= linkX && mouseX < linkX + linkWidth
                && mouseY >= linkY && mouseY < linkY + linkHeight) {
            // Vanilla's own handling, so the browser prompt and the "always allow" behaviour are the
            // ones the player already knows from chat links.
            handleComponentClicked(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DOCS_URL))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Open the Ember syntax reference"))));
            return true;
        }

        PatchouliBypass.enter();
        try {
            return super.mouseClicked(mouseX, mouseY, button);
        } finally {
            PatchouliBypass.exit();
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        PatchouliBypass.enter();
        try {
            return super.mouseReleased(mouseX, mouseY, button);
        } finally {
            PatchouliBypass.exit();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PatchouliBypass.enter();
        try {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        } finally {
            PatchouliBypass.exit();
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        PatchouliBypass.enter();
        try {
            super.mouseMoved(mouseX, mouseY);
        } finally {
            PatchouliBypass.exit();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        PatchouliBypass.enter();
        try {
            return super.keyPressed(keyCode, scanCode, modifiers);
        } finally {
            PatchouliBypass.exit();
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        PatchouliBypass.enter();
        try {
            return super.charTyped(codePoint, modifiers);
        } finally {
            PatchouliBypass.exit();
        }
    }

    @Override
    public void onClose() {
        // Closing goes back to the list the window was opened from rather than leaving the player in
        // the world: this window is a step in a menu, not a screen of its own.
        sent = false;
        if (adminMode && returnToAdmin) {
            BackUtilsAdminScreen.open();
            return;
        }
        BackUtilsMenuScreen.open();
    }
}
