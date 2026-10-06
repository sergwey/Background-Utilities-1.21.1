package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ColorSelector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.LayoutStyle;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.client.BackUtilsClientConfig;
import net.xlebupaksa.backutils.client.EffectToolSlot;
import net.xlebupaksa.backutils.client.LiteralDrawing;
import net.xlebupaksa.backutils.client.SymbolChatPanel;
import net.xlebupaksa.backutils.item.ItemEdit;
import net.xlebupaksa.backutils.network.ItemEditPayload;
import net.xlebupaksa.backutils.network.ItemEditorNetwork;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The item editor: what an operator gets when they hover an item in an inventory and press space.
 *
 * <p><b>The preview is the point.</b> The fields hold markup and the preview holds what the item will
 * actually show, drawn by the game's own text renderer from the same component the server will write —
 * so what is being edited is never guessed at, and a tag that does not parse is visible as the literal
 * text it becomes. Special effects and values are written straight into the fields: the preview shows
 * the result and does not try to be a pallette of everything the text can contain.
 *
 * <p><b>The buttons work on what is selected in the preview.</b> Dragging over the drawn name or lore
 * selects the characters the item shows — not the markup's, whose tags nobody can see — and a button
 * wraps its tag around exactly those, leaving them selected so that the next button applies to the same
 * words. The name field's own selection is used when nothing is selected in the preview, which is the
 * shorter way to format something already being typed there. The colour is not a button: a colour is
 * chosen in the picker and letting go of the picker is what applies it to the selection.
 *
 * <p><b>A selection in the preview is measured in what the item shows</b>, which is what makes it survive
 * being used: a tag put round a selection is invisible, so the same characters are still the ones
 * selected afterwards. {@link ItemEdit#shown} and {@link ItemEdit#shownRange} are the two halves of
 * that, and they are arithmetic rather than drawing, so the harness checks them without a screen.
 *
 * <p><b>A button rewrites the whole line</b> rather than splicing its pair into the markup where the words
 * are: the line is read into the tags it has and the characters between them, the chosen characters are put
 * under the new tag, and the line is written back out with every pair balanced — see {@link ItemEdit}. The
 * field is therefore not always character for character what it was a moment ago: a pair round nothing, or a
 * closing tag with nothing open, is dropped, and a tag that was never closed is closed where its words end.
 * What the item shows is the same either way, which is the trade: a field of tags nobody can read is worse
 * than a field this mod has tidied.
 *
 * <p><b>Operators only.</b> The server refuses an edit from anybody else, and the screen is not opened
 * for them in the first place: see {@link net.xlebupaksa.backutils.client.ItemEditorGesture}.
 *
 * <p>The lore is one area with real newlines rather than a row per line, and its lines map onto the
 * lore lines of the item one for one — which is why the message that carries an edit did not have to
 * change.
 */
@OnlyIn(Dist.CLIENT)
public final class ItemEditorScreen extends Screen {

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_BG = 0xF0101016;
    private static final int PAD = 10;
    private static final int GAP = 5;
    private static final int ROW = 16;
    private static final int LINE_HEIGHT = 14;
    private static final int LABEL_WIDTH = 78;
    private static final int AMOUNT_WIDTH = 44;
    private static final int OPTION_WIDTH = 74;
    private static final int STYLE_BUTTON = 16;

    /**
     * The colour picker: a square of this width, and the room its hex field needs underneath.
     *
     * <p>The picker is given this size rather than left to ask for one, and its contents are given a
     * minimum of nothing, because a flex item is otherwise at least as large as its contents: the square
     * inside it is a percentage of the width and the hex field and copy button set a floor under that,
     * which is how the picker came out wider and taller than the room reserved for it and was drawn over
     * the preview below.
     */
    private static final int PICKER = 120;
    private static final int PICKER_EXTRA = 24;

    /** How tall SymbolChat's own menu is drawn, in pixels. */
    private static final int SYMBOL_MENU_HEIGHT = 90;

    /**
     * How wide to allow for SymbolChat's menu when placing it.
     *
     * <p>Reserved rather than measured, because the menu lays its own buttons out from the place it is
     * built at: asking it how wide it is would mean building it somewhere else first and then leaving it
     * there, which is the bug this placement exists to avoid.
     */
    private static final int SYMBOL_MENU_WIDTH = 220;

    /**
     * How far left of the button the menu stands, which is a little more than the button is wide.
     *
     * <p>The button is the thing the menu belongs to, so they are read as a pair: standing exactly at its
     * right edge, the menu reads as part of the button rather than as the menu it opens.
     */
    private static final int SYMBOL_MENU_SHIFT = STYLE_BUTTON + 8;

    /**
     * The room the two halves of the preview are given.
     *
     * <p>Asked for in the layout and used again for the drawing, because a preview is drawn by this
     * screen rather than by a widget: the height has to be the same number in both places, or the text is
     * clipped by one of them and overflows the other. The lore's is a floor rather than a height — it
     * takes what is left of the panel, and this is the least it will take.
     */
    private static final int NAME_PREVIEW_HEIGHT = 22;
    private static final int LORE_PREVIEW_HEIGHT = 126;

    /** How far the preview's text is indented, which is the room the item's own icon takes beside it. */
    private static final int PREVIEW_INDENT = 22;

    /** How far the icon is inset from the corner of the box it is drawn in. */
    private static final int ICON_PAD = 1;

    /** How tall the field the lore's markup is typed in is, which is text rather than drawing. */
    private static final int LORE_FIELD_HEIGHT = 96;

    /** What is painted behind the selected characters of the preview. */
    private static final int SELECTION_BG = 0x804A90D9;

    /** The name, in the numbering the preview uses for the lines of the item: the lore is 0 upwards. */
    private static final int NAME_FIELD = -1;

    /** The lore as a whole, for the one question that is not about one of its lines: where a symbol goes. */
    private static final int LORE = -2;


    /** Which slot the item being edited is in, and which menu that index counts in. */
    private final EffectToolSlot.Address source;

    /** The attributes as they are being edited, in the order the rows are shown in. */
    private final List<ItemEdit.Granted> attributes = new ArrayList<>();

    private final UIElement root = new UIElement();
    private final UIElement panel = new UIElement();
    private final TextField name = new TextField();

    /**
     * What was selected in the name field, remembered from the last frame.
     *
     * <p>A button takes the focus away from the field, and a field that has lost focus has lost its
     * selection: read at the moment a button is pressed, the selection is already nothing, which is why
     * the first version wrapped empty tags and put them on the end instead of round the words.
     */
    private int chosenFrom;
    private int chosenTo;
    private final TextArea lore = new TextArea();

    /** The library's colour picker, the same component the profile editor puts on screen. */
    private final ColorSelector picker = new ColorSelector();
    private final UIElement attributeRows = new UIElement();

    /** The space the name is drawn in, and the space the lore is drawn in underneath it. */
    private final UIElement namePreview = new UIElement();
    private final UIElement lorePreview = new UIElement();

    /**
     * What is selected in the preview, or null when nothing is.
     *
     * <p>In the characters the item shows rather than in the markup's, which is what makes it survive the
     * formatting it is used for: a tag put round a selection is invisible, so the same characters are
     * still the ones selected afterwards and the next button applies to the same words.
     */
    private Mark mark;

    /**
     * The characters the selection was taken in.
     *
     * <p>Kept so that the selection can be shown to still mean something. It is measured in the text the
     * item shows, and that text can change under it — a lore line typed into, a name reworded — which
     * would leave it pointing at whatever now happens to sit at those indices. A selection whose text has
     * changed is dropped rather than applied to words nobody chose.
     */
    private String marked;

    /** Whether the selection still means anything, worked out once for each frame that draws it. */
    private boolean markLive;

    /** Where a drag in the preview began, held while the mouse is down. */
    private Spot anchor;

    /** Whether a drag in the preview is going on. */
    private boolean dragging;

    /** Whether the mouse went down inside the picker, which is what makes letting go of it apply one. */
    private boolean picking;

    /** What the preview drew last, in the order it drew it: the geometry the mouse is measured against. */
    private final List<Line> drawn = new ArrayList<>();

    private ModularUI ui;

    /** SymbolChat's menu, when that mod is installed, and null when it is not. */
    private AbstractWidget symbols;

    /**
     * Which field the caret was in, remembered from the last frame it was in one.
     *
     * <p>The button that opens the menu takes the focus when it is pressed, and a field that has lost the
     * focus is no longer the field the operator was typing in: asked at that moment, the answer is always
     * the name, which is what put every symbol in the name however the lore had been clicked. This is the
     * same reason the name field's selection is remembered rather than read.
     */
    private int caretField = NAME_FIELD;

    /** The button that opens and closes the menu, kept for the place the menu is put beside it. */
    private Button symbolToggle;

    private ItemEditorScreen(EffectToolSlot.Address source) {
        super(Component.translatable("backutils.item_editor.title"));
        this.source = source;
        build();
    }

    /**
     * Opens the editor on one slot, or closes whatever is open when there is no item to edit.
     *
     * <p>An address rather than a slot index, because the index alone means different things in the
     * client's menu and the server's, and the server is the side that will be asked to write.
     */
    public static void open(EffectToolSlot.Address source) {
        Minecraft.getInstance().setScreen(source == null ? null : new ItemEditorScreen(source));
    }

    @Override
    public void init() {
        // The editor takes the whole window rather than a panel in the middle of it: the preview and the
        // lore are both text that wants width, and a field a third of the screen wide is one an operator
        // scrolls sideways to read.
        fill();

        // The menu is not built here. It is built the first time it is opened, at the place it will be
        // shown, because it lays its own buttons out from the position it was constructed at — so a menu
        // built before there is a layout is a menu built in the corner. A resize drops it, and the next
        // press builds it again where it now belongs.
        symbols = null;

        ui.setScreenAndInit(this);
        addRenderableWidget(ui.getWidget());
        super.init();
        setFocused(ui.getWidget());

        load();
    }


    /** Builds the panel once. The screen is made fresh for each item, so this runs once per open. */
    private void build() {
        Label title = new Label();
        text(title, "backutils.item_editor.title", 12f);
        title.layout(l -> l.flex(1).height(ROW));

        Button close = new Button();
        text(close.text, "backutils.item_editor.button.close", 9f);
        close.layout(l -> l.width(72).height(LINE_HEIGHT));
        close.setOnClick(e -> Minecraft.getInstance().setScreen(null));

        // The title shares its row with the button that leaves the screen: the editor is one panel, and the
        // way out of it belongs at the top of it rather than among the work at the bottom.
        UIElement titleRow = new UIElement();
        titleRow.layout(row());
        titleRow.addChildren(title, close);

        // The two fields hold the workflow's own text: what is typed there is what the server is given,
        // tags and all, and nothing parses it for drawing. The preview is the parsed reading of the same
        // text, which is what makes the pair worth having.
        name.setText("", false);
        name.layout(l -> l.flex(1).height(LINE_HEIGHT));
        name.setTextResponder(value -> { });

        UIElement nameRow = new UIElement();
        nameRow.layout(row());
        nameRow.addChildren(caption("backutils.item_editor.label.name"), name);

        UIElement styles = styleRow();

        lore.setLines(List.of(""));
        lore.layout(l -> l.flex(1).height(LORE_FIELD_HEIGHT));
        lore.setLinesResponder(lines -> { });

        UIElement loreRow = new UIElement();
        loreRow.layout(l -> l.widthPercent(100).height(LORE_FIELD_HEIGHT)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .gapAll(GAP));
        loreRow.addChildren(caption("backutils.item_editor.label.lore"), lore);

        // The preview is drawn by this screen rather than by a widget, because what it shows is a component
        // with its own styles and the game's renderer is the only thing that draws one exactly as an item
        // would. Two spaces rather than one: the name is the item's title and the lore hangs under it, and
        // the gap between the two pieces is what says so.
        namePreview.layout(l -> l.widthPercent(100).height(NAME_PREVIEW_HEIGHT));
        // Nine pixels up, which is one line of the game's font: the lore belongs under the name the way a
        // tooltip has it, and the gap the panel leaves between two rows is one row too many here.
        lorePreview.layout(l -> l.widthPercent(100).flex(1).minHeight(LORE_PREVIEW_HEIGHT).marginTop(-9));

        attributeRows.layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(2));

        Button addAttribute = new Button();
        text(addAttribute.text, "backutils.item_editor.button.add_attribute", 9f);
        addAttribute.layout(l -> l.width(90).height(LINE_HEIGHT));
        addAttribute.setOnClick(e -> {
            addAttribute();
            fill();
        });

        Button apply = new Button();
        text(apply.text, "backutils.item_editor.button.apply", 9f);
        apply.layout(l -> l.width(96).height(ROW));
        apply.setOnClick(e -> send());

        // One row at the foot of the panel for what the item grants and for the button that writes it, pushed
        // to the right so that the attribute rows are the last thing on the screen.
        UIElement spacer = new UIElement();
        spacer.layout(l -> l.flex(1));

        UIElement attributeHeader = new UIElement();
        attributeHeader.layout(row());
        attributeHeader.addChildren(caption("backutils.item_editor.label.attributes"), addAttribute,
                spacer, apply);

        // The symbol button stands at the right, just under the lore field: it fills whichever field has the
        // caret, and the lore is where most of the text that wants a symbol is typed.
        UIElement symbolSpacer = new UIElement();
        symbolSpacer.layout(l -> l.flex(1));

        UIElement symbolRow = new UIElement();
        symbolRow.layout(l -> l.widthPercent(100).height(LINE_HEIGHT)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.FLEX_START));
        symbolToggle = symbolButton();
        symbolRow.addChildren(symbolSpacer, symbolToggle);

        // Everything is inside a scroller, so the panel scrolls rather than cutting off whatever does not
        // fit: the attribute list grows with the item, and a screen that can show three of them is a screen
        // that cannot edit the fourth.
        ScrollerView scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).flex(1).minHeight(0));
        scroller.viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        scroller.viewContainer.layout(l -> l.widthPercent(100).heightAuto()
                .flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        scroller.viewContainer.addChildren(titleRow, nameRow, loreRow, symbolRow, styles, namePreview,
                lorePreview, attributeHeader, attributeRows);

        panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        panel.addChildren(scroller);

        root.addChild(panel);
        ui = ModularUI.of(UI.of(root));
    }

    /**
     * {@return the row of formatting buttons}
     *
     * <p>One letter each, which is what was asked for: the basic decoration an item is named with, and no
     * button for anything better written into the field. Each wraps its tag around whatever is selected —
     * in the preview first, in the name field when the preview has no selection — or leaves an empty pair
     * with the caret between them when there is nothing selected at all, which is how a tag's value is
     * typed. The colour is not here: the picker below applies its own.
     */
    private UIElement styleRow() {
        UIElement row = new UIElement();
        row.layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.FLEX_START)
                .gapAll(2));

        // The picker arrives with a colour square, an alpha bar and four rows of fields and sliders under
        // it, which is more than the room the panel gives it — and what does not fit is drawn over what is
        // below, because nothing clips it. The alpha bar and the rows under the hex field go, exactly as
        // they do in the profile editor: an item's colour has no alpha in the markup this mod writes, and
        // the hex field is the part worth keeping, because a typed value cannot silently fail to register
        // the way a drag can.
        //
        // The size is given, and the minimum is nothing, because a flex item is otherwise at least as
        // large as its contents: the square is a percentage of the width and the hex field and its copy
        // button set a floor under it, which is how the picker came out wider and taller than the room
        // reserved for it and was drawn across the preview.
        picker.layout(l -> l.width(PICKER).height(PICKER + PICKER_EXTRA).minWidth(0).minHeight(0));
        picker.alphaSlider.setVisible(false);
        picker.alphaSlider.layout(l -> l.display(TaffyDisplay.NONE));
        List<UIElement> pickerRows = picker.textContainer.getChildren();
        for (int i = 1; i < pickerRows.size(); i++) {
            UIElement extra = pickerRows.get(i);
            extra.setVisible(false);
            extra.layout(l -> l.display(TaffyDisplay.NONE));
        }
        picker.setColor(0xFFFFFF, false);

        // No colour button: the picker is the colour control, and letting go of it applies what it holds
        // to whatever is selected — see mouseReleased. It stands beside the buttons rather than at the far
        // end of the row, because the row is as tall as the picker and a control a screen away from the
        // buttons it belongs to is a control nobody finds.
        row.addChildren(
                styleButton("backutils.item_editor.style.bold", "bold"),
                styleButton("backutils.item_editor.style.italic", "italic"),
                styleButton("backutils.item_editor.style.underline", "underline"),
                styleButton("backutils.item_editor.style.strike", "strikethrough"),
                plainButton("backutils.item_editor.style.clear", () -> clearStyle()));

        row.addChild(picker);
        return row;
    }

    /**
     * {@return the button that opens SymbolChat's menu}
     *
     * <p>One button for both fields, in the row directly under them, because a symbol is typed into the
     * markup: which field it lands in is whichever one the caret is in, and the preview never receives one.
     *
     * <p>Only on a client that has SymbolChat: a button that opens a menu nobody has is worse than no
     * button at all.
     */
    private Button symbolButton() {
        Button button = new Button();
        text(button.text, "backutils.item_editor.button.symbols", 9f);
        button.layout(l -> l.width(STYLE_BUTTON).height(LINE_HEIGHT));
        button.setOnClick(e -> openSymbols());
        button.setVisible(SymbolChatPanel.present());
        return button;
    }

    /**
     * Opens SymbolChat's menu, building it the first time, where it will be shown.
     *
     * <p>Built there and never moved afterwards, which is the whole reason it is not built at startup: the
     * menu lays its own buttons out at absolute positions taken from where it was constructed, so a menu
     * that is built in the corner and then moved leaves them behind — a grid of symbols in one corner and
     * an empty pane in another, which is exactly what it did.
     */
    private void openSymbols() {
        if (!SymbolChatPanel.present()) return;

        // Pressed again, the button puts the menu away: one that only ever opens is one that has to be
        // worked around to get the menu off the screen.
        if (menuOpen()) {
            SymbolChatPanel.hide(symbols);
            return;
        }

        if (symbols == null) {
            // Measured by making one: the menu places its own buttons from the place it is built at, so the
            // only way to know how wide it is, is to build it. This one is thrown away and the real one is
            // built at the width that answer gave, which is what keeps the two tightly together.
            AbstractWidget probe = SymbolChatPanel.panel(0, 0, SYMBOL_MENU_HEIGHT, this::insert);
            int width = probe == null ? SYMBOL_MENU_WIDTH : probe.getWidth();

            symbols = SymbolChatPanel.panel(symbolLeft(width), symbolTop(), SYMBOL_MENU_HEIGHT, this::insert);
            if (symbols == null) return;
            addRenderableWidget(symbols);
        }
        SymbolChatPanel.show(symbols);
    }

    /**
     * {@return the left of the menu}: against the right edge of the button it belongs to
     *
     * <p>Measured from the button rather than from the edge of the panel, so that the two stay together
     * however the row was laid out, and placed by its own width so that it stands beside the button rather
     * than at some reserved distance from it.
     */
    private int symbolLeft(int width) {
        int right = symbolToggle == null
                ? Math.round(this.width) - PAD
                : Math.round(symbolToggle.getPositionX() + symbolToggle.getSizeWidth());
        return Math.max(PAD, right - width - SYMBOL_MENU_SHIFT);
    }

    /**
     * {@return the top of the menu}: the row the button is on, which is the blank space under the lore
     *
     * <p>Under the fields, beside the picker rather than over it — the picker is to the left and the menu is
     * at the right edge — so an open menu covers no field and no control.
     */
    private int symbolTop() {
        return symbolToggle == null ? PAD : Math.max(PAD, Math.round(symbolToggle.getPositionY()));
    }

    /**
     * {@return a button that wraps one tag around the selection, labelled in that formatting}
     *
     * <p>The letter is drawn the way the tag draws text, so the row of them reads as what it does rather
     * than as four letters to be remembered. The label is a component rather than a translated string
     * because that is where the styling lives — the same reason the preview is drawn from one.
     */
    private Button styleButton(String key, String tag) {
        Button button = new Button();
        button.text.setText(Component.literal(Text.of(key)).withStyle(formattingOf(tag)));
        button.text.textStyle(t -> t.fontSize(9f).textShadow(false).adaptiveWidth(true));
        button.layout(l -> l.width(STYLE_BUTTON).height(LINE_HEIGHT));
        button.setOnClick(e -> wrap("<" + tag + ">", "</" + tag + ">"));
        return button;
    }

    /** {@return the style a tag draws its own label in} */
    private static ChatFormatting formattingOf(String tag) {
        return switch (tag) {
            case "bold" -> ChatFormatting.BOLD;
            case "italic" -> ChatFormatting.ITALIC;
            case "underline" -> ChatFormatting.UNDERLINE;
            case "strikethrough" -> ChatFormatting.STRIKETHROUGH;
            default -> ChatFormatting.RESET;
        };
    }

    private Button plainButton(String key, Runnable action) {
        Button button = new Button();
        text(button.text, key, 9f);
        button.layout(l -> l.width(STYLE_BUTTON).height(LINE_HEIGHT));
        button.setOnClick(e -> action.run());
        return button;
    }

    /**
     * Applies one edit to whatever is selected.
     *
     * <p>The selection made in the preview comes first, because it is the one on screen. The name field's
     * own selection is used when there is none, and that one is the reason for the two fields above
     * rather than a read of the field here: pressing a button takes the focus away from the text field,
     * and a field that has lost the focus has lost its selection, so the value has to have been taken
     * while it still had one.
     *
     * <p>Both paths end the same way — one line of the item replaced, and the field's own selection put
     * back on the words — so they are written once here rather than once for each button. Where the words
     * end up is {@link ItemEdit}'s answer rather than arithmetic here, because getting that wrong is
     * invisible until somebody presses a second button and finds the tag on the end of the line.
     *
     * @param change what to do to a line and to the run of it that is selected
     */
    private void edit(BiFunction<String, int[], ItemEdit.Edited> change) {
        Mark selection = live();
        if (selection != null) {
            String text = markup(selection.field());
            int[] range = rangeOf(selection);
            setMarkup(selection.field(), change.apply(text, range).text());
            // The selection itself does not move. What was put round it is invisible, so the same
            // characters of the item's text are still the ones selected, and the next button applies to
            // them rather than to whatever the offsets have become.
            return;
        }

        String text = name.getText() == null ? "" : name.getText();
        ItemEdit.Edited edited = change.apply(text, new int[]{chosenFrom, chosenTo});
        name.setText(edited.text(), false);
        name.setSelection(edited.from(), edited.to());
    }

    /** Wraps a pair of tags around whatever is selected. */
    void wrap(String open, String close) {
        edit((text, range) -> ItemEdit.wrapped(text, range[0], range[1], open, close));
    }

    /** Takes every formatting tag out of the selection, which is what the clear button does. */
    private void clearStyle() {
        edit((text, range) -> ItemEdit.untagged(text, range[0], range[1]));
    }

    /** Sets the picker's colour on whatever is selected, which is what letting go of the picker means. */
    private void colour() {
        String hex = hexOf(picker.getColor());
        edit((text, range) -> ItemEdit.recoloured(text, range[0], range[1], hex));
    }

    /** {@return the run of a line's markup that the selected characters of the item cover} */
    private int[] rangeOf(Mark selection) {
        String text = markup(selection.field());
        return ItemEdit.shownRange(text, ItemEdit.shown(text), selection.from(), selection.to());
    }

    /** {@return the markup of one line of the item}, which is what is edited and what the server gets */
    private String markup(int field) {
        if (field == NAME_FIELD) return name.getText() == null ? "" : name.getText();
        List<String> lines = loreLines();
        return field >= 0 && field < lines.size() ? lines.get(field) : "";
    }

    /** Replaces one line's markup, which is how the name and a single line of the lore are both edited */
    private void setMarkup(int field, String value) {
        if (field == NAME_FIELD) {
            name.setText(value, false);
            return;
        }
        List<String> lines = new ArrayList<>(loreLines());
        if (field < 0 || field >= lines.size()) return;
        lines.set(field, value);
        lore.setLines(lines);
    }

    /**
     * {@return a colour as the hex the library reads}, which is what an item's colour has to be
     *
     * <p>Six digits and no {@code #}, for the reason written in {@link ItemEdit#hex}: a hash is a string
     * this mod understands and the library does not, and the tag is then read and ignored.
     */
    private static String hexOf(int argb) {
        return String.format("%06X", argb & 0xFFFFFF);
    }

    /**
     * Inserts text where the caret is, in the field the symbol menu was opened from.
     *
     * <p>A symbol is typed into the markup rather than dropped into the preview, and the two fields hold
     * different text, so which of them it goes into is whichever field the button that opened the menu
     * stands beside.
     */
    void insert(String inserted) {
        if (inserted == null || inserted.isEmpty()) return;
        // Read now rather than when the menu was opened: the operator may have moved the caret to the other
        // field while the menu stood open, and a symbol belongs where the caret is.
        if (caretField == LORE) {
            insertIntoLore(inserted);
            return;
        }

        String text = name.getText() == null ? "" : name.getText();
        int at = Math.max(0, Math.min(name.getCursorPos(), text.length()));
        name.setText(text.substring(0, at) + inserted + text.substring(at), false);
        name.setCursor(at + inserted.length());
    }

    /** Puts a symbol into the lore at the caret, on the line the caret is on. */
    private void insertIntoLore(String inserted) {
        List<String> lines = new ArrayList<>(loreLines());
        if (lines.isEmpty()) return;

        int line = Math.max(0, Math.min(lore.getCursorLine(), lines.size() - 1));
        String text = lines.get(line) == null ? "" : lines.get(line);
        int at = Math.max(0, Math.min(lore.getCursorCol(), text.length()));
        lines.set(line, text.substring(0, at) + inserted + text.substring(at));

        lore.setLines(lines);
        // After the field has the new text, because the caret is clamped to the line it is put on.
        lore.setCursor(line, at + inserted.length());
    }

    /** {@return the lore as it stands in the area}, one entry per line */
    List<String> loreLines() {
        return List.copyOf(lore.getLines());
    }

    /** {@return the attributes as they stand in the rows} */
    List<ItemEdit.Granted> attributeList() {
        return List.copyOf(attributes);
    }

    // ------------------------------------------------------------------
    // The attributes
    // ------------------------------------------------------------------

    /** {@return every attribute this game has}, by the id the game knows it by, in a stable order */
    private static List<String> attributeIds() {
        return BuiltInRegistries.ATTRIBUTE.keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
    }

    /**
     * Adds one attribute to the list.
     *
     * <p>The first one this item does not already grant, so that adding a row twice does not make a row
     * that does nothing: {@link ItemEdit#putAttribute} replaces an attribute rather than repeating it,
     * and a new row naming one the item has would be a row that silently rewrote it.
     */
    private void addAttribute() {
        List<String> used = new ArrayList<>();
        for (ItemEdit.Granted granted : attributes) used.add(granted.attribute().toString());

        String next = null;
        for (String id : attributeIds()) {
            if (!used.contains(id)) {
                next = id;
                break;
            }
        }
        if (next == null) next = attributeIds().isEmpty() ? "" : attributeIds().get(0);
        if (next.isEmpty()) return;

        ItemEdit.Granted granted = new ItemEdit.Granted(ResourceLocation.parse(next), 1.0D,
                AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.ANY);
        List<ItemEdit.Granted> added = ItemEdit.putAttribute(attributes, granted);
        attributes.clear();
        attributes.addAll(added);
        refreshAttributes();
    }

    /** Rebuilds the attribute rows from the list, which is the only state the section keeps. */
    private void refreshAttributes() {
        attributeRows.clearAllChildren();
        for (int i = 0; i < attributes.size(); i++) {
            attributeRows.addChild(attributeRow(i));
        }
    }

    /** {@return one attribute: which one, how much, how it applies, and where it applies} */
    private UIElement attributeRow(int index) {
        ItemEdit.Granted granted = attributes.get(index);

        Selector<String> which = new Selector<>();
        which.layout(l -> l.flex(1).height(LINE_HEIGHT));
        which.setCandidates(attributeIds());
        which.setValue(granted.attribute().toString(), false);
        which.setOnValueChanged(id -> setAttribute(index, id, granted.amount(),
                granted.operation(), granted.slot()));

        TextField amount = new TextField();
        amount.setText(whole(granted.amount()), false);
        amount.layout(l -> l.width(AMOUNT_WIDTH).height(LINE_HEIGHT));
        amount.setTextResponder(value -> setAttribute(index, granted.attribute().toString(),
                number(value, granted.amount()), granted.operation(), granted.slot()));

        Selector<String> operation = new Selector<>();
        operation.layout(l -> l.width(OPTION_WIDTH).height(LINE_HEIGHT));
        operation.setCandidates(Arrays.stream(AttributeModifier.Operation.values())
                .map(Enum::name).toList());
        operation.setValue(granted.operation().name(), false);
        operation.setOnValueChanged(chosen -> setAttribute(index, granted.attribute().toString(),
                granted.amount(), operation(chosen), granted.slot()));

        Selector<String> slot = new Selector<>();
        slot.layout(l -> l.width(OPTION_WIDTH).height(LINE_HEIGHT));
        slot.setCandidates(Arrays.stream(EquipmentSlotGroup.values()).map(Enum::name).toList());
        slot.setValue(granted.slot().name(), false);
        slot.setOnValueChanged(chosen -> setAttribute(index, granted.attribute().toString(),
                granted.amount(), granted.operation(), slot(chosen)));

        UIElement row = new UIElement();
        row.layout(l -> l.widthPercent(100).height(LINE_HEIGHT)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .gapAll(2));
        row.addChildren(which, amount, operation, slot, symbol("x", () -> removeAttribute(index)));
        return row;
    }

    /** Puts one changed attribute back into the list, in the place the row it came from stands. */
    private void setAttribute(int index, String attribute, double amount,
                              AttributeModifier.Operation operation, EquipmentSlotGroup slot) {
        if (index < 0 || index >= attributes.size()) return;
        ResourceLocation id = ResourceLocation.tryParse(attribute == null ? "" : attribute);
        if (id == null) return;
        attributes.set(index, new ItemEdit.Granted(id, amount, operation, slot));
    }

    private void removeAttribute(int index) {
        if (index < 0 || index >= attributes.size()) return;
        attributes.remove(index);
        refreshAttributes();
        fill();
    }

    /** {@return a number as the box should show it}, without a trailing zero on a whole number */
    private static String whole(double amount) {
        return amount == Math.rint(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }

    /** {@return what a box holds as a number}, or the value it had when it does not read as one */
    private static double number(String text, double was) {
        try {
            return Double.parseDouble(text == null ? "" : text.trim());
        } catch (NumberFormatException notANumber) {
            return was;
        }
    }

    /** {@return the operation a name stands for}, read as adding a value when it means nothing */
    private static AttributeModifier.Operation operation(String name) {
        try {
            return AttributeModifier.Operation.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return AttributeModifier.Operation.ADD_VALUE;
        }
    }

    /** {@return the slot group a name stands for}, read the same way and for the same reason */
    private static EquipmentSlotGroup slot(String name) {
        try {
            return EquipmentSlotGroup.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return EquipmentSlotGroup.ANY;
        }
    }

    // ------------------------------------------------------------------
    // The panel
    // ------------------------------------------------------------------

    /** {@return the layout every row of this panel shares} */
    private static Consumer<LayoutStyle> row() {
        return l -> l.widthPercent(100).height(ROW)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .gapAll(GAP);
    }

    /** {@return a caption of the fixed width the rows align on} */
    private static Label caption(String key) {
        Label label = new Label();
        text(label, key, 9f);
        label.layout(l -> l.width(LABEL_WIDTH).height(ROW));
        return label;
    }

    /** {@return a small button that does one thing to one row} */
    private static Button symbol(String label, Runnable action) {
        Button button = new Button();
        text(button.text, label, 9f);
        button.layout(l -> l.width(STYLE_BUTTON).height(LINE_HEIGHT));
        button.setOnClick(e -> action.run());
        return button;
    }

    /**
     * Sets a caption from a translation key, the way every other screen in this mod does.
     *
     * <p>Takes a key or the literal symbol a small button shows: a translation key that is not one is
     * answered by the game with itself, which is exactly what a symbol wants.
     */
    private static void text(TextElement element, String key, float size) {
        element.setText(Text.of(key), false);
        element.textStyle(t -> t.fontSize(size).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
    }

    /** Fills the fields from the item as it stands, so the screen opens on what is there. */
    private void load() {
        ItemStack stack = stack();
        Component current = stack == null ? null : stack.get(DataComponents.CUSTOM_NAME);
        // Through the conversion rather than through the text: an item's name carries its styling, and
        // reading only the words would mean that opening an item and pressing apply restyled it.
        name.setText(current == null ? "" : ItemEdit.markup(current), false);

        ItemLore currentLore = stack == null ? null : stack.get(DataComponents.LORE);
        List<String> lines = new ArrayList<>();
        if (currentLore != null) {
            for (Component line : currentLore.lines()) lines.add(ItemEdit.markup(line));
        }
        lore.setLines(lines);

        ItemAttributeModifiers currentAttributes = stack == null
                ? null : stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        attributes.clear();
        if (currentAttributes != null) {
            for (ItemAttributeModifiers.Entry entry : currentAttributes.modifiers()) {
                ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value());
                if (id == null) continue;
                attributes.add(new ItemEdit.Granted(id, entry.modifier().amount(),
                        entry.modifier().operation(), entry.slot()));
            }
        }
        refreshAttributes();
    }

    /**
     * Sends all three sections for the server to write.
     *
     * <p>The whole edit, because the screen shows all of it: an empty lore is the operator having
     * removed every line, and an empty attribute list is an item that grants nothing.
     */
    private void send() {
        if (source == null) return;
        ItemEditorNetwork.send(ItemEditPayload.full(source.where(), source.containerId(),
                source.slot(), name.getText(), loreLines(), attributeList()));
    }

    /** Lays the panel out over the whole window, which is what every list change and resize asks for. */
    private void fill() {
        root.layout(l -> l.width(this.width).height(this.height)
                .flexDirection(FlexDirection.COLUMN));
        panel.layout(l -> l.width(this.width).height(this.height).paddingAll(PAD)
                .flexDirection(FlexDirection.COLUMN).gapAll(GAP));
    }

    private ItemStack stack() {
        Player player = Minecraft.getInstance().player;
        return player == null || source == null ? null : EffectToolSlot.stackAt(player, source);
    }

    /**
     * {@return one line of the item as a component the game can draw}, under the style of the hover it is a
     *         line of
     *
     * <p>Built from the parsed runs, which is the reading that carries the static styling: a colour, a
     * weight, a slant, a line. That is what the library calls its static path, and it is the whole of what
     * can be drawn here — the animated effects are rendered by the library's own message layer, which draws
     * to the screen rather than into a component, so a preview drawn as a component cannot show them
     * however the markup is written. Markup the parser refuses is drawn as the text it is.
     *
     * <p>And on a name of this editor's own making they are never drawn at all: the library's item hook
     * restyles the <em>default</em> name of an item and steps aside for one somebody set, which is the one
     * this editor is setting. So a preview that drew them would be promising an effect the hover will not
     * have; what is drawn here is what the hover shows, effects and all.
     *
     * <p>The style goes on a component the line is appended to rather than on the line, so that it is
     * inherited the way the game inherits it: a run that names its own colour or its own slant keeps it, and
     * takes the rest of the hover's style — the rarity's colour, the italic of a renamed item or of a line of
     * lore. Which is the same order the game merges the two in for its own tooltip.
     */
    private static Component drawable(String markup, Style base) {
        return Component.empty().append(ItemEdit.component(ItemEdit.spans(markup))).withStyle(base);
    }


    // ------------------------------------------------------------------
    // The preview: what it draws, and what can be selected in it
    // ------------------------------------------------------------------

    /** One character of the preview as it was drawn: where it is, and which characters of the item it is. */
    private record Glyph(int x, int width, int from, int to) { }

    /** One drawn line: the line of the item it came from, where it sits, and the characters on it. */
    private record Line(int field, int y, List<Glyph> glyphs) { }

    /** Where the mouse is: which line of the item, which character of it, which drawn line. */
    private record Spot(int field, int at, int line) { }

    /** What is selected: one line of the item, and a run of the characters that line shows. */
    private record Mark(int field, int from, int to) { }

    /**
     * Draws both halves of the preview: the name and the lore as the game would draw them, from the same
     * markup the server is about to be given.
     *
     * <p>Drawn here rather than by widgets because a widget takes a plain string, and a plain string is the
     * one thing this must not be: what is being checked is the formatting. Where to draw is asked of the
     * pieces that reserve the space rather than worked out from a row count, because a row count has to be
     * kept in step with the layout by hand — which is what put the preview across the style row once.
     *
     * <p>It also writes down what it drew, and that is what the mouse is measured against afterwards: the
     * geometry of the last frame is the only text on screen, so it is the only thing a click can mean.
     */
    private void drawPreviews(GuiGraphics graphics) {
        drawn.clear();
        markLive = live() != null;
        // One copy of the item for the whole frame: the icon draws it, and both halves of the preview ask it
        // how the game draws their line.
        ItemStack preview = previewStack();
        drawIcon(graphics, preview);
        drawPreview(graphics, namePreview, true, preview);
        drawPreview(graphics, lorePreview, false, preview);
    }

    /**
     * Draws the item's own icon at the head of the preview.
     *
     * <p>From a copy of the stack carrying the name and the lore the fields now hold, because that is what the
     * server is about to write: a mod that restyles an item from its name reads the stack, and a preview of
     * the stack as it stands would show the item as it was rather than as it is about to be. The component is
     * built the same way the server builds it, for the same reason.
     */
    private void drawIcon(GuiGraphics graphics, ItemStack preview) {
        if (preview == null) return;

        graphics.renderItem(preview,
                Math.round(namePreview.getPositionX()) + ICON_PAD,
                Math.round(namePreview.getPositionY()) + ICON_PAD);
    }

    /** {@return the item as the editor is about to leave it}, or null when the slot holds nothing */
    private ItemStack previewStack() {
        ItemStack stack = stack();
        if (stack == null || stack.isEmpty()) return null;

        ItemStack preview = stack.copy();
        String markup = name.getText() == null ? "" : name.getText();
        if (markup.isEmpty()) {
            preview.remove(DataComponents.CUSTOM_NAME);
        } else {
            preview.set(DataComponents.CUSTOM_NAME, ItemEdit.component(markup));
        }

        List<Component> lines = new ArrayList<>();
        for (String line : loreLines()) {
            if (!line.isEmpty()) lines.add(ItemEdit.component(line));
        }
        if (lines.isEmpty()) {
            preview.remove(DataComponents.LORE);
        } else {
            preview.set(DataComponents.LORE, new ItemLore(lines));
        }
        return preview;
    }

    /**
     * Draws one half of the preview in the space reserved for it.
     *
     * @param title whether this is the item's name, which is one line of text, or its lore, which is one
     *              line for each line the lore has
     */
    private void drawPreview(GuiGraphics graphics, UIElement area, boolean title, ItemStack preview) {
        Minecraft minecraft = Minecraft.getInstance();
        int box = Math.round(area.getPositionX());
        int left = box + PREVIEW_INDENT;
        int top = Math.round(area.getPositionY());
        int inner = Math.round(area.getSizeWidth()) - PREVIEW_INDENT;
        int bottom = top + Math.round(area.getSizeHeight());

        // A box the layout has not measured answers nothing, and a preview with no room draws nothing at
        // all — which is how it came out blank once with a name sitting in the field above it. The room
        // that was asked for is the fallback, which is why the same numbers are constants on both sides.
        if (inner <= 0) inner = Math.max(1, Math.round(this.width) - 2 * PAD - PREVIEW_INDENT);
        if (bottom <= top) bottom = top + (title ? NAME_PREVIEW_HEIGHT : LORE_PREVIEW_HEIGHT);

        List<String> lines = title ? List.of(name.getText() == null ? "" : name.getText()) : loreLines();
        int lineHeight = minecraft.font.lineHeight;
        // The style the game itself draws this half of the hover in, so that the preview is the tooltip an
        // operator will see rather than this mod's idea of one: the name in the item's rarity — italic as well
        // when the item carries a custom name, which is the state the editor leaves it in — and a lore line in
        // the dark purple italic the game gives every line of lore.
        Style base = tooltipStyle(preview, title);

        // Clipped to the room it was given, the way a widget would be: the panel scrolls, and a preview
        // that has been scrolled out of the way must not be drawn over whatever took its place. The clip
        // starts at the box rather than the text, so the icon drawn beside it is inside it too.
        graphics.enableScissor(box, top, box + inner + PREVIEW_INDENT, bottom);
        int y = top;
        for (int index = 0; index < lines.size() && y + lineHeight <= bottom; index++) {
            for (Wrapped line : wrap(minecraft.font, lines.get(index), inner)) {
                if (y + lineHeight > bottom) break;
                drawLine(graphics, line, title ? NAME_FIELD : index, left, y, base);
                y += lineHeight;
            }
        }
        graphics.disableScissor();
    }

    /**
     * {@return the style the game draws one half of the hover in}
     *
     * <p>Asked of {@link ItemEdit}, which asks the game: the colours and the italics of a tooltip are the
     * game's, and a preview that invents its own is a preview of some other item.
     */
    private static Style tooltipStyle(ItemStack preview, boolean title) {
        if (!title) return ItemEdit.loreStyle();
        if (preview == null) return ItemEdit.nameStyle(Rarity.COMMON, false);
        return ItemEdit.nameStyle(preview.getRarity(), preview.has(DataComponents.CUSTOM_NAME));
    }

    /** One drawn line: the characters of the item it shows, and the markup that draws them. */
    private record Wrapped(String markup, String shown) { }

    /**
     * {@return one line of markup broken into the lines the preview draws it on}
     *
     * <p>Wrapped on this side rather than by the game's splitter, and that is not a preference: a line has to
     * be cut in the same place for the drawing and for the measuring, one of which needs the markup and the
     * other the characters, and only this side knows both. It is also what makes a selection in the preview
     * countable in characters rather than in the tags that stand between them.
     */
    private static List<Wrapped> wrap(Font font, String markup, int width) {
        String shown = ItemEdit.shown(markup);
        List<Wrapped> out = new ArrayList<>();
        if (shown.isEmpty()) return out;

        int start = 0;
        while (start < shown.length()) {
            // Measured a character at a time and added up, which is both what the drawing measures and the
            // only way this stays linear: measuring a substring per character is quadratic in the length of
            // a lore line, and it runs on every frame the screen is open.
            int end = start;
            int used = 0;
            int space = -1;
            while (end < shown.length()) {
                int codepoint = shown.codePointAt(end);
                int advance = font.width(new String(Character.toChars(codepoint)));
                if (end > start && used + advance > width) break;
                used += advance;
                if (codepoint == ' ') space = end;
                end += Character.charCount(codepoint);
            }
            // A word too long for a line of its own is broken rather than dropped off the side, and a line
            // that has a space to end on ends after it rather than in the middle of the word after it.
            if (end < shown.length() && space > start) end = space + 1;
            if (end == start) end = Math.min(start + 1, shown.length());

            int[] range = ItemEdit.shownRange(markup, shown, start, end);
            out.add(new Wrapped(markup.substring(range[0], range[1]), shown.substring(start, end)));
            start = end;
        }
        return out;
    }

    /**
     * Draws one line of the preview, and writes down where each of its characters landed.
     *
     * <p>The sink is handed the index every character has in the text the item shows, which is the whole
     * reason a click on a drawn character can become a position in a field: what is drawn and what is
     * selected are the same text, and that index says which part of it this character is.
     */
    private void drawLine(GuiGraphics graphics, Wrapped line, int field, int left, int y, Style base) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<Glyph> glyphs = new ArrayList<>();

        // Measured here rather than asked of the renderer, because these are the numbers a selection is made
        // of, and they have to be the characters' own: the index each glyph carries is its place in what the
        // item shows, which is what a selection means and what the markup's own indices are not.
        int pen = left;
        for (int i = 0; i < line.shown().length(); ) {
            int codepoint = line.shown().codePointAt(i);
            String character = new String(Character.toChars(codepoint));
            int width = font.width(character);
            glyphs.add(new Glyph(pen, width, i, i + character.length()));
            pen += width;
            i += character.length();
        }

        // Behind the text rather than over it, so that what is selected can still be read while it is.
        for (Glyph glyph : glyphs) {
            if (!selected(field, glyph.from(), glyph.to())) continue;
            graphics.fill(glyph.x(), y - 1, glyph.x() + glyph.width(), y + font.lineHeight - 1, SELECTION_BG);
        }
        // Drawn from this mod's runs under the style of the hover, and not from the raw markup: the runs are
        // the component the server writes, and the library's markup is not read for an item's own name at all
        // — see drawable. The colour here is only the last resort the game uses for text with no colour of
        // its own, which the style always gives it.
        graphics.drawString(font, drawable(line.markup(), base), left, y, 0xFFFFFF);
        drawn.add(new Line(field, y, glyphs));
    }

    /** {@return whether one character of the item's text is inside what is selected} */
    private boolean selected(int field, int from, int to) {
        Mark selection = mark;
        return markLive && selection != null && selection.field() == field
                && from < selection.to() && to > selection.from();
    }

    /**
     * {@return the selection, or null when the text it was measured in is no longer what the line holds}
     *
     * <p>The one place the question is answered, so that the highlight and the buttons cannot disagree
     * about whether there is a selection at all.
     */
    private Mark live() {
        Mark selection = mark;
        if (selection == null) return null;
        if (marked != null && ItemEdit.shown(markup(selection.field())).equals(marked)) return selection;

        mark = null;
        marked = null;
        return null;
    }

    /**
     * {@return where the mouse is in the preview}, or null when it is not over any drawn text
     *
     * <p>The nearer edge of the character under the mouse is the one it counts as, which is what a text
     * field does and what a hand expects: a drag that ends between two characters ends before whichever
     * of them it is nearer the front of.
     */
    private Spot spot(double mouseX, double mouseY) {
        int lineHeight = Minecraft.getInstance().font.lineHeight;
        for (int i = 0; i < drawn.size(); i++) {
            Line line = drawn.get(i);
            if (mouseY < line.y() || mouseY >= line.y() + lineHeight) continue;

            int at = -1;
            for (Glyph glyph : line.glyphs()) {
                at = mouseX < glyph.x() + glyph.width() / 2 ? glyph.from() : glyph.to();
                if (mouseX < glyph.x() + glyph.width()) break;
            }
            // An empty line has no characters to be between.
            return at < 0 ? null : new Spot(line.field(), at, i);
        }
        return null;
    }

    /**
     * {@return the selection a drag has reached}
     *
     * <p>It stays on the line of the item the drag began on, because the buttons write to one line: a
     * selection running on into the next would name two fields and could only be written to one of them.
     * Running off the end of a line therefore takes the rest of that line, which is what dragging past
     * the end of a line means everywhere else.
     */
    private Mark extend(double mouseX, double mouseY) {
        if (anchor == null) return null;
        Spot spot = spot(mouseX, mouseY);
        if (spot == null || spot.field() != anchor.field()) {
            boolean upwards = spot != null ? spot.line() < anchor.line()
                    : anchor.line() < drawn.size() && mouseY < drawn.get(anchor.line()).y();
            int end = ItemEdit.shown(markup(anchor.field())).length();
            return upwards ? new Mark(anchor.field(), 0, anchor.at())
                    : new Mark(anchor.field(), anchor.at(), end);
        }

        int from = Math.min(anchor.at(), spot.at());
        int to = Math.max(anchor.at(), spot.at());
        // A drag that has not crossed a character has selected nothing, and an empty selection is nothing
        // rather than a run of no characters that the next button would wrap.
        return from == to ? null : new Mark(anchor.field(), from, to);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // The menu is asked before anything else, because it is drawn over the panel and the panel takes
        // every click that lands anywhere on it: a menu asked second never sees one, which is why none of
        // its symbols could be pressed.
        if (menuClick(mouseX, mouseY, button)) return true;

        // The picker is asked next, and not because it comes first in the panel: it draws itself outside
        // the space it is given, so a click on it has to be taken before anything underneath can claim it.
        if (inside(picker, mouseX, mouseY)) {
            picking = true;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // Clicking back into the name field gives the selection back to it.
        if (inside(name, mouseX, mouseY)) {
            mark = null;
            marked = null;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        Spot spot = spot(mouseX, mouseY);
        if (spot == null) return super.mouseClicked(mouseX, mouseY, button);

        dragging = true;
        anchor = spot;
        mark = null;
        marked = null;
        // The name field's remembered selection must not be what a button acts on now that a selection
        // has been made here instead.
        chosenFrom = 0;
        chosenTo = 0;
        return true;
    }

    /** {@return whether SymbolChat's menu took the click}, which it is asked before anything else is */
    private boolean menuClick(double mouseX, double mouseY, int button) {
        if (!menuOpen()) return false;
        if (!symbols.mouseClicked(mouseX, mouseY, button)) return false;

        setFocused(symbols);
        return true;
    }

    /** {@return whether the menu is on screen}, which decides whether it is asked about anything at all */
    private boolean menuOpen() {
        return symbols != null && symbols.visible;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (menuOpen() && symbols.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (dragging) {
            mark = extend(mouseX, mouseY);
            marked = mark == null ? null : ItemEdit.shown(markup(mark.field()));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean menu = menuOpen() && symbols.mouseReleased(mouseX, mouseY, button);
        dragging = false;
        // Letting go of the picker is what applies its colour: the words are selected, a colour is
        // chosen, and the colour is the one the picker was let go of holding. Applying as it changed
        // instead would put a pair of tags round the words for every shade a slider passed through.
        if (picking) {
            picking = false;
            colour();
        }
        return menu || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // The menu scrolls its list of symbols, and the panel under it would take the wheel first.
        if (menuOpen() && symbols.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The menu's own search box is typed in like any field, so the keys go to it while it holds them,
        // and only then: while a field of the panel has the keyboard, that field is what is being typed in.
        if (menuOpen() && symbols.isFocused() && symbols.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (menuOpen() && symbols.isFocused() && symbols.charTyped(character, modifiers)) {
            return true;
        }
        return super.charTyped(character, modifiers);
    }

    /** {@return whether a position is inside one of the panel's pieces} */
    private static boolean inside(UIElement element, double mouseX, double mouseY) {
        return mouseX >= element.getPositionX() && mouseX < element.getPositionX() + element.getSizeWidth()
                && mouseY >= element.getPositionY() && mouseY < element.getPositionY() + element.getSizeHeight();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Paints the dim behind the panel and nothing else, so the blurred world never shows. The same
     * opacity the effect tool's screen uses, because it is the same dim.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int alpha = (int) Math.round(Math.max(0.0D, Math.min(1.0D,
                BackUtilsClientConfig.getMenuBackgroundOpacity())) * 255.0D);
        graphics.fill(0, 0, this.width, this.height, (alpha << 24) | 0x00101016);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Taken while the field still has it. The click that presses a button arrives between two frames,
        // so the value read here is the one the button will act on, and the cleared one that follows is
        // too late to matter.
        if (name.isFocused()) {
            chosenFrom = name.getSelectionStart();
            chosenTo = name.getSelectionEnd();
            caretField = NAME_FIELD;
        } else if (lore.isFocused()) {
            caretField = LORE;
        }

        // The two fields are widgets that draw what they hold, and what they hold is markup: the library
        // builds a literal component out of the text of a field, and the text library parses any literal
        // component it is given, so the tags are consumed and the field shows the formatted words rather
        // than the words that were typed. This is the text library's own switch for that, scoped to the
        // thread, so entering it round the panel's own drawing leaves the preview below parsing as it
        // always did — which is the whole point of having both.
        LiteralDrawing.begin();
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            LiteralDrawing.end();
        }
        drawPreviews(graphics);
    }
}
