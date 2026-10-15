package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
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
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import org.lwjgl.glfw.GLFW;

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
 * the result and does not try to be a pallette of everything the text can contain. An effect written
 * into a line animates here, because the line is drawn the way the item's is — see {@link ItemEdit#line}
 * — and because each drawn row is wrapped and made into its component once per change rather than sixty
 * times a second, which is what gives an effect somewhere to keep the state it animates from: see
 * {@link DrawnRows}.
 *
 * <p><b>The text is white and upright unless the markup says otherwise.</b> The game draws a renamed
 * item's name in its rarity's colour and in italic, and a line of lore in dark purple italic, and none of
 * that is what this editor writes: the default style says white and not italic, so what the preview shows
 * and what the item shows is what the operator typed and not what the game would have done with it.
 *
 * <p><b>The panel is two columns.</b> Everything that is read stands on the left — the fields, the preview
 * and the attributes — and the picker stands in a column of its own at the panel's right edge, with the row
 * of formatting buttons ending against its left edge and the preview beginning on the row under them, where
 * the picker is still being drawn beside it. That column is reserved for the whole of the preview's height
 * and not only for the picker: the preview's width has to be the same on the row beside the picker and on
 * the rows below it, because the width is what its text is wrapped into — see {@link Rows}. The same shape
 * answers a window too narrow for both: the picker takes the row under the preview rather than being drawn
 * over it, and the preview is then as wide as the panel.
 *
 * <p><b>The top row belongs to the editor rather than to the work.</b> The title stands in the middle of
 * its row, the button that closes the editor at its left end where the title used to be, and the button that
 * writes the item at its right end where the way out used to be — Apply having moved up from the foot of a
 * panel that scrolls, so that the one button that saves the work is not something an operator with a long
 * attribute list has to scroll to find. The two buttons do exactly what they did before they changed places:
 * this one writes and stays open, that one closes and writes nothing — with one thing asked first, because
 * markup the library will draw as its characters is worth a word before it is written: see {@link #commit}.
 *
 * <p><b>Markup that styles nothing is markup the item shows as its tags.</b> The library parses a literal as
 * it draws it and gives up on one whose runs carry nothing it can draw, at which point the literal is drawn
 * as its own characters — so a name of nothing but tags is a name of tags on screen, and the editor says so
 * before writing it rather than after somebody reads the item. The preview is the same reading: it draws what
 * the item will draw, tags and all, because a preview that hid that would be showing a line the item will not
 * have. Nothing is rewritten, dropped or reordered for it: the text is stored as it was typed, and the word
 * said about it is a reading of the item and not a licence to change what the operator wrote.
 *
 * <p><b>Ctrl+Z and Ctrl+Y walk one opening's editing back and forward.</b> Everything this screen can change
 * is a step of the history — the name, the lore, the colour, the attribute list, and the selection in the
 * preview, which is what the buttons act on. The chords are taken before the focused field is asked, because
 * the library's own fields keep an undo apiece and an undo of one field alone is half an edit; the history
 * lives only as long as the screen, so a reopening starts fresh. One step per change, a typed character
 * included, and a state identical to the one already kept is not kept again.
 *
 * <p><b>The buttons work on what is selected in the preview.</b> Dragging over the drawn name or lore
 * selects the characters the item shows — not the markup's, whose tags nobody can see — and a button
 * wraps its tag around exactly those, leaving them selected so that the next button applies to the same
 * words. The drag may cross lines: the preview draws the item's lines one under another, a selection is a
 * run of that whole text, and a button writes the part of it that falls on each line. The name field's own
 * selection is used when nothing is selected in the preview, which is the
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
 * change. An area with nothing in it is no lore rather than one blank line of it: a text area cannot hold
 * no lines, so the item is read through {@link #itemLore} and not off the field.
 */
@OnlyIn(Dist.CLIENT)
public final class ItemEditorScreen extends Screen {

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
     * The room the lore's half of the preview is given before it has measured its own text.
     *
     * <p>Asked for in the layout and used again for the drawing, because a preview is drawn by this screen
     * rather than by a widget: the height has to be the same number in both places, or the text is clipped by
     * one of them and overflows the other. It is only what the layout starts with — the first frame to draw it
     * measures the wrapped text and asks for the room that takes, so that a tooltip of two lines does not leave
     * the attribute list at the foot of the screen with a screenful of nothing above it. The name's box is the
     * icon's, and needs no constant of its own.
     */
    private static final int LORE_PREVIEW_HEIGHT = 126;

    /**
     * How large the item's own icon is drawn in the preview, which is the game's sixteen made a little larger.
     *
     * <p>The icon is what the preview is of, so it leads: the text is indented by it and the room beside it,
     * and it is the tallest thing the frame has to hold — taller than the line of text drawn beside it, which
     * is why the frame reaches below the name's own box to hold it. See the note on that box in build.
     */
    private static final int PREVIEW_ICON = 20;

    /** How far the preview's text is indented, which is the icon and the room beside it. */
    private static final int PREVIEW_INDENT = PREVIEW_ICON + 6;

    /** How far the item and its description are inset from the frame that holds them. */
    private static final int PREVIEW_PAD = 6;

    /**
     * The wash inside the frame the item stands in, and the line round it.
     *
     * <p>Chosen against the panel's own near-black: the wash is a shade lighter than the panel so that the
     * frame reads as a thing rather than as a hole, and the edge is lighter again so that it reads as a frame
     * rather than as a smudge. Both are one number away from being changed.
     */
    private static final int PREVIEW_BG = 0xC02F2A34;
    private static final int PREVIEW_EDGE = 0xFF4A4550;

    /**
     * The air a tooltip leaves between its lines, which is what the lore is separated from the name by.
     *
     * <p>Held inside the name's own box rather than as a margin between the two boxes — see the note on that
     * box in build — so the box is a line of the game's text and this under it, and the lore's box follows
     * the column's own gap below that.
     */
    private static final int PREVIEW_GAP = 2;

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

    /**
     * How many steps of editing the screen keeps.
     *
     * <p>Forty is a few dozen changes — as many typed characters as a line of text, or a long run of
     * buttons — and a step is small: a name, a lore, a colour, an attribute list and the selection that
     * stood beside them.
     */
    private static final int HISTORY = 40;


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

    /**
     * The two halves of the preview as they were last wrapped, and what each of them was wrapped from.
     *
     * <p>Kept because what a row is drawn from is decided by the text and the width alone, and because a row
     * rebuilt between two frames is an effect in it that never gets to animate: see {@link Rows}.
     */
    private final Rows nameRows = new Rows();
    private final Rows loreRows = new Rows();

    /**
     * What this opening can still be walked back through: the states, oldest first, and which of them is
     * on screen.
     *
     * <p>A list with a position in it rather than a stack of past states, because the state on screen is
     * one of the entries: an undo moves the position back and a redo moves it forward, so "there is
     * nothing to go back to" is the position at the front, and nothing has to be held in two places.
     *
     * <p>It lives for one opening and no longer. A new editor is built for each opening — see
     * {@link #open} — so nothing is carried from the item edited before to the item edited now; a window
     * resize runs {@link #init} again, and so {@link #load}, which starts the history over for the same
     * reason it fills the fields over: what the screen is being given is the item as it stands.
     */
    private final List<Snapshot> history = new ArrayList<>();

    /** Which step of {@link #history} is on screen, or -1 before the first {@link #load}. */
    private int step = -1;

    /**
     * True while the screen itself is writing, so that no responder keeps a step of its own for one write
     * of a change that is made of several — see {@link #unrecorded}.
     */
    private boolean writing;

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

    /**
     * The button that writes the edit, kept because it is also the confirmation of a warning: a press that
     * finds markup drawn as its characters says so and turns this button into the press that writes it
     * anyway — see {@link #commit}.
     */
    private Button applyButton;

    /**
     * What the last press of this button asked about, or null when it asked nothing: the offending lines as
     * they stood, with their markup. Kept so that the press after a warning writes what the operator was
     * shown and nothing else — a warning answered on one text must not become leave to write another — and
     * so that a change to those lines asks again, without this screen having to watch the fields for edits.
     */
    private String warnedAbout;

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
        // The title stands in the middle of the top row and the two buttons hold its ends: the one that
        // leaves the editor where the title used to be, at the left, and the one that writes the item where
        // the way out used to be, at the right. The title is the flexed item, which is what pushes the
        // buttons to the ends, and it is centred in its own box because a flexed label draws its words at
        // the left of a box that is now wider than they are.
        //
        // The middle it stands in is the middle of the room between the two buttons rather than of the
        // panel: they are not the same width — seventy-two against ninety-six — so the title is half that
        // difference, twelve pixels, left of the panel's own middle. It is a nudge nobody reads as one, and
        // the alternative is a title stretched over the row and the buttons it would then stand over.
        Label title = new Label();
        text(title, "backutils.item_editor.title", 12f);
        title.textStyle(t -> t.textAlignHorizontal(Horizontal.CENTER));
        title.layout(l -> l.flex(1).height(ROW));

        Button close = new Button();
        text(close.text, "backutils.item_editor.button.close", 9f);
        close.layout(l -> l.width(72).height(LINE_HEIGHT));
        close.setOnClick(e -> Minecraft.getInstance().setScreen(null));

        // Apply stands beside the button that leaves the screen, where the way out of the editor used to be,
        // rather than at the foot of a panel that scrolls: the attribute list grows with the item, and the
        // one button that writes the whole edit is a button an operator had to scroll past to find. What the
        // two of them do is untouched — this one writes and stays open, and the one beside it closes and
        // writes nothing, which is what each of them did before they changed places. What this one does
        // first is ask: markup that would be drawn as its characters is worth a word before it is written,
        // and the asking is commit.
        applyButton = new Button();
        text(applyButton.text, "backutils.item_editor.button.apply", 9f);
        applyButton.layout(l -> l.width(96).height(LINE_HEIGHT));
        applyButton.setOnClick(e -> commit());

        UIElement titleRow = new UIElement();
        titleRow.layout(row());
        titleRow.addChildren(close, title, applyButton);

        // The two fields hold the workflow's own text: what is typed there is what the server is given,
        // tags and all, and nothing parses it for drawing. The preview is the parsed reading of the same
        // text, which is what makes the pair worth having.
        name.setText("", false);
        name.layout(l -> l.flex(1).height(LINE_HEIGHT));
        // Typing is the one change nothing else announces, so the field's own word that what it holds has
        // changed is what keeps a step of it — one per character, like the field's own history.
        name.setTextResponder(value -> record());

        UIElement nameRow = new UIElement();
        nameRow.layout(row());
        nameRow.addChildren(caption("backutils.item_editor.label.name"), name);

        UIElement styles = styleRow();

        lore.setLines(List.of(""));
        lore.layout(l -> l.flex(1).height(LORE_FIELD_HEIGHT));
        lore.setLinesResponder(lines -> record());

        UIElement loreRow = new UIElement();
        loreRow.layout(l -> l.widthPercent(100).height(LORE_FIELD_HEIGHT)
                .flexDirection(FlexDirection.ROW)
                // The caption stands at the top of the field rather than at the middle of it: the field is one
                // area with room for many lines, and a label floating in the middle of it reads as a label for
                // the middle line.
                .alignItems(AlignItems.FLEX_START)
                .gapAll(GAP));
        loreRow.addChildren(caption("backutils.item_editor.label.lore"), lore);

        // The preview is drawn by this screen rather than by a widget, because what it shows is a component
        // with its own styles and the game's renderer is the only thing that draws one exactly as an item
        // would. Two spaces rather than one: the name is the item's title and the lore hangs under it, and
        // the gap between the two pieces is what says so.
        //
        // Inset by the frame's own padding at the sides, because the frame is drawn round these two boxes: a box
        // on the content edge with a frame round it puts the frame outside the column every other row is
        // aligned to, which is a frame that looks like a mistake. Stretched rather than asked for a hundred
        // per cent of the column, because a box asked for a hundred per cent and then given a margin either
        // side is a box that hangs both margins off the column: the frame drawn round it would reach into the
        // picker's column beside it and be painted over the picker, this screen drawing after the widgets.
        // Stretched, the margins come off the width, and the frame lands on the column's own edges.
        //
        // The name's box is the line of text drawn at its top and the air a tooltip leaves under a line — not
        // the icon's twenty-four: the icon is drawn at this box's corner and hangs below it, beside the lore
        // rather than above it, and what holds the icon is the frame, which is drawn from six pixels above
        // this box to six below the lore's. The air is inside this box rather than a negative margin between
        // the two boxes, and that is not a matter of taste: a column with a negative margin inside it answers
        // no height to the row that holds it in this library's layout, so the row, the scroller's content and
        // everything measured from them would come out shorter than what they hold — and a panel whose
        // content is measured short is a panel that cannot be scrolled to its last attribute row.
        namePreview.layout(l -> l.widthStretch().marginLeft(PREVIEW_PAD).marginRight(PREVIEW_PAD)
                .height(rowHeight() + PREVIEW_GAP));
        lorePreview.layout(l -> l.widthStretch().marginLeft(PREVIEW_PAD).marginRight(PREVIEW_PAD)
                .height(LORE_PREVIEW_HEIGHT));

        // Neither of these two boxes is ever a target: they hold room for what this screen draws, and a box
        // that holds room is not something to click. Left to take a hit test, one of them swallows the clicks
        // of whatever it stands over — which is how half of the button that writes the item came to do
        // nothing, while it stood at the foot of the panel under a box for the lore that spanned the column.
        namePreview.setAllowHitTest(false);
        lorePreview.setAllowHitTest(false);

        attributeRows.layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.COLUMN).gapAll(2));

        Button addAttribute = new Button();
        text(addAttribute.text, "backutils.item_editor.button.add_attribute", 9f);
        addAttribute.layout(l -> l.width(90).height(LINE_HEIGHT));
        addAttribute.setOnClick(e -> {
            addAttribute();
            fill();
        });

        // The header of the list names what the item grants and adds a row to it, and holds nothing else now
        // that the button that writes the item has gone up to the top of the panel. The spacer keeps the two
        // of them at the left, where the captions of the rows above stand.
        UIElement spacer = new UIElement();
        spacer.layout(l -> l.flex(1));

        UIElement attributeHeader = new UIElement();
        attributeHeader.layout(row());
        attributeHeader.addChildren(caption("backutils.item_editor.label.attributes"), addAttribute, spacer);

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

        // The editor's own shape below the fields: a column of the picker's own width at the panel's right
        // edge, holding the picker, with the row of formatting buttons ending against its left edge and
        // everything that is read — the preview and the attributes — to the left of that.
        //
        // The picker's column is as tall as the whole column and not only as tall as the picker, and that
        // empty room below the picker is the point of the shape rather than a leftover: the preview beside it
        // keeps one width from its first row to its last, and the width is what its text is wrapped into — a
        // preview that grew once the picker's own box was behind it would re-wrap every line it had drawn
        // while the screen scrolled past the picker, which is the one thing the wrap cache must not be asked
        // to do quietly (see Rows).
        UIElement columns = new UIElement();
        columns.layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.FLEX_START)
                .gapAll(GAP)
                // Wrapped rather than squeezed, for the window too narrow to hold the picker's column and a
                // usable preview beside it. A flex row takes the room out of whichever item will give it, and
                // the preview is the one that would: asked for the least room it can hold, below which the
                // picker stands on the row under it instead. Nothing overlaps either way — the columns are
                // never drawn over one another, they are laid side by side or one above the other.
                .flexWrap(FlexWrap.WRAP));

        UIElement left = new UIElement();
        left.layout(l -> l.flex(1).minWidth(PICKER)
                .flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        left.addChildren(styles, namePreview, lorePreview, attributeHeader, attributeRows);

        columns.addChildren(left, pickerColumn());

        // Everything is inside a scroller, so the panel scrolls rather than cutting off whatever does not
        // fit: the attribute list grows with the item, and a screen that can show three of them is a screen
        // that cannot edit the fourth.
        ScrollerView scroller = new ScrollerView();
        scroller.layout(l -> l.widthPercent(100).flex(1).minHeight(0));
        scroller.viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        scroller.viewContainer.layout(l -> l.widthPercent(100).heightAuto()
                .flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        scroller.viewContainer.addChildren(titleRow, nameRow, loreRow, symbolRow, columns);

        panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        panel.addChildren(scroller);

        root.addChild(panel);
        ui = ModularUI.of(UI.of(root));
    }

    /**
     * {@return the row of formatting buttons}
     *
     * <p>One letter each, which is what was asked for: the basic decoration an item is named with, and no
     * button for anything better written into the field. Each is a switch over whatever is selected — in the
     * preview first, in the name field when the preview has no selection — and presses a tag on to it or, on
     * words that already wear it, takes it off again; with nothing selected at all it leaves an empty pair
     * with the caret between them, which is how a tag's value is typed. The colour is not here: the picker
     * beside the row applies its own.
     *
     * <p>The row is the full width of the column it shares with the preview, and the buttons are pushed to
     * its right end, which is the picker's left edge: the two of them are read as one control, so the picker
     * stands against them at the panel's right edge rather than a column away from them.
     */
    private UIElement styleRow() {
        UIElement row = new UIElement();
        row.layout(l -> l.widthPercent(100)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.FLEX_START)
                .justifyContent(AlignContent.FLEX_END)
                .gapAll(2));

        row.addChildren(
                styleButton("backutils.item_editor.style.bold", "bold"),
                styleButton("backutils.item_editor.style.italic", "italic"),
                styleButton("backutils.item_editor.style.underline", "underline"),
                styleButton("backutils.item_editor.style.strike", "strikethrough"),
                // The one button that cannot wear its own formatting: obfuscated text is drawn as glyphs the
                // client picks at random, so a label written that way is a label nobody can read, and it is not
                // a small label that wobbles — it is a row that wobbles, because the random glyphs are not all
                // the same width. Its letter says what it does instead, the way the others do with theirs.
                styleButton("backutils.item_editor.style.obfuscated", "obfuscated"),
                plainButton("backutils.item_editor.style.clear", () -> clearStyle()));

        return row;
    }

    /**
     * {@return the column the colour picker stands in}
     *
     * <p>A column of its own at the panel's right edge, as wide as the picker and as tall as the column it
     * stands beside. What is under the picker inside it is empty room, and that is the point: the preview
     * keeps one width whether it is drawing the row beside the picker or the rows below it, and the width is
     * what its text is wrapped into — see {@link Rows}.
     *
     * <p>The picker arrives with a colour square, an alpha bar and four rows of fields and sliders under
     * it, which is more than the room the column gives it — and what does not fit is drawn over what is
     * below, because nothing clips it. The alpha bar and the rows under the hex field go, exactly as
     * they do in the profile editor: an item's colour has no alpha in the markup this mod writes, and
     * the hex field is the part worth keeping, because a typed value cannot silently fail to register
     * the way a drag can.
     *
     * <p>The size is given, and the minimum is nothing, because a flex item is otherwise at least as
     * large as its contents: the square is a percentage of the width and the hex field and its copy
     * button set a floor under it, which is how the picker came out wider and taller than the room
     * reserved for it and was drawn across the preview.
     */
    private UIElement pickerColumn() {
        picker.layout(l -> l.width(PICKER).height(PICKER + PICKER_EXTRA).minWidth(0).minHeight(0));
        picker.alphaSlider.setVisible(false);
        picker.alphaSlider.layout(l -> l.display(TaffyDisplay.NONE));
        List<UIElement> pickerRows = picker.textContainer.getChildren();
        for (int i = 1; i < pickerRows.size(); i++) {
            UIElement extra = pickerRows.get(i);
            extra.setVisible(false);
            extra.layout(l -> l.display(TaffyDisplay.NONE));
        }
        // White with no alpha, which is what the field under the picker shows back: a colour written as
        // 0xFFFFFF leaves the alpha at nothing, and a hex field reading "#00ffffff" tells an operator their
        // colour is transparent when what it is, is white. The markup has no alpha in it either way — this
        // mod's colour tag is six digits — so the alpha here is only what the picker says about itself.
        picker.setColor(0xFFFFFFFF, false);

        // No colour button: the picker is the colour control, and letting go of it applies what it holds
        // to whatever is selected — see mouseReleased. It stands at the column's top, level with the row of
        // formatting buttons, rather than under it: the two are one control, and the buttons are read as
        // the picker's other half only if they stand beside it on the same line.
        UIElement column = new UIElement();
        column.layout(l -> l.width(PICKER).flexShrink(0)
                .flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        column.addChild(picker);
        return column;
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
     * {@return the top of the menu}: the row the button is on, which is the row between the fields and the
     * preview
     *
     * <p>Under the fields, and at the right edge where the button is. It opens over the row of formatting
     * buttons and the top of the colour picker, which stand in the panel's right-hand column: the picker was
     * left of the buttons until it was moved to the edge it was asked to stand at, and the empty room the
     * menu opened into went with it. It cannot stand over the preview instead, because this screen draws the
     * preview after the widgets and a menu there would be painted over; the fields are what it was kept off,
     * which leaves the column the controls are in. It is put away by the button that opened it.
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

    /**
     * {@return the style a tag draws its own label in}
     *
     * <p>Obfuscation is in here because it was asked for, and it is worth saying what it costs: the label is
     * drawn as glyphs the client picks at random, so it is not a word any more — it is the shape of the effect
     * — and the random glyphs are not all the same width, so the label shifts about inside its button. That is
     * what the style looks like, which is the point of a button wearing its own formatting.
     */
    private static ChatFormatting formattingOf(String tag) {
        return switch (tag) {
            case "bold" -> ChatFormatting.BOLD;
            case "italic" -> ChatFormatting.ITALIC;
            case "underline" -> ChatFormatting.UNDERLINE;
            case "strikethrough" -> ChatFormatting.STRIKETHROUGH;
            case "obfuscated" -> ChatFormatting.OBFUSCATED;
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
     * <p>Both paths end the same way — the lines of the item replaced, and the field's own selection put
     * back on the words — so they are written once here rather than once for each button. Where the words
     * end up is {@link ItemEdit}'s answer rather than arithmetic here, because getting that wrong is
     * invisible until somebody presses a second button and finds the tag on the end of the line.
     *
     * <p>A selection that runs across lines is written to every line it covers, one at a time, because a line
     * of an item is a component of its own: the run the selection covers on each of them is wrapped on that
     * line, and a line the selection passes through whole is wrapped whole. The lines in the middle of such a
     * selection are wholly covered, which is what a selection means in a field of several lines.
     *
     * @param change what to do to a line and to the run of it that is selected
     */
    private void edit(BiFunction<String, int[], ItemEdit.Edited> change) {
        // Every write below belongs to one press of one button, so none of them is kept on its own: the
        // caller keeps the step, once, when the whole change is on screen — see wrap, clearStyle, colour.
        unrecorded(() -> {
            Mark selection = live();
            if (selection != null) {
                for (int field = selection.fromField(); field <= selection.toField(); field++) {
                    int[] range = rangeOn(selection, field);
                    if (range == null) continue;
                    setMarkup(field, change.apply(markup(field), range).text());
                }
                // The selection itself does not move. What was put round it is invisible, so the same
                // characters of the item's text are still the ones selected, and the next button applies to
                // them rather than to whatever the offsets have become.
                return;
            }

            String text = nameMarkup();
            ItemEdit.Edited edited = change.apply(text, new int[]{chosenFrom, chosenTo});
            name.setText(edited.text(), false);
            name.setSelection(edited.from(), edited.to());
        });
    }

    /**
     * Presses a button on whatever is selected: the pair of tags goes on, or comes off again if it is there.
     *
     * <p>Which of the two it is, is decided once for the whole selection and then applied line by line. A
     * selection can run across the lines of an item and cover a bold line and a plain one, and an operator
     * pressing the button means one thing by it: deciding line by line would bold the plain line and unbold the
     * bold one, which is half a button pressed and a state nobody can predict from the button.
     */
    void wrap(String open, String close) {
        Mark selection = live();
        boolean off = selection != null && wears(selection, open);
        edit(off
                ? (text, range) -> ItemEdit.unwrapped(text, range[0], range[1], open)
                : (text, range) -> ItemEdit.wrapped(text, range[0], range[1], open, close));
        record();
    }

    /** {@return whether every character the selection covers already wears this style} */
    private boolean wears(Mark selection, String open) {
        List<ItemEdit.Run> runs = new ArrayList<>();
        for (int field = selection.fromField(); field <= selection.toField(); field++) {
            int[] range = rangeOn(selection, field);
            if (range == null) continue;
            runs.add(new ItemEdit.Run(markup(field), range[0], range[1]));
        }
        return ItemEdit.wearsAll(runs, open);
    }

    /** Takes every formatting tag out of the selection, which is what the clear button does. */
    private void clearStyle() {
        edit((text, range) -> ItemEdit.untagged(text, range[0], range[1]));
        record();
    }

    /** Sets the picker's colour on whatever is selected, which is what letting go of the picker means. */
    private void colour() {
        String hex = hexOf(picker.getColor());
        edit((text, range) -> ItemEdit.recoloured(text, range[0], range[1], hex));
        record();
    }

    /**
     * {@return the run of one line's markup that the selection covers on that line}, or null when it covers
     *         none of it
     *
     * <p>The selection is held in the characters the item shows, and one run of them can run across several
     * lines of it: the first line of such a selection begins where the drag did, the last ends where it did,
     * and the lines between are covered from end to end.
     */
    private int[] rangeOn(Mark selection, int field) {
        String text = markup(field);
        String shown = ItemEdit.shown(text);
        int from = field == selection.fromField() ? selection.fromAt() : 0;
        int to = field == selection.toField() ? selection.toAt() : shown.length();
        if (from >= to) return null;
        return ItemEdit.shownRange(text, shown, from, to);
    }

    /** {@return the markup of one line of the item}, which is what is edited and what the server gets */
    private String markup(int field) {
        if (field == NAME_FIELD) return nameMarkup();
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

        String text = nameMarkup();
        int at = Math.max(0, Math.min(name.getCursorPos(), text.length()));
        name.setText(text.substring(0, at) + inserted + text.substring(at), false);
        name.setCursor(at + inserted.length());
        // The write above is made with the field's own notification off, so the step is kept here. The
        // lore's half of this method goes through the area's responder, which keeps one of its own — the
        // second is the same state and is dropped by record.
        record();
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

    /**
     * {@return the name's markup as the field holds it}
     *
     * <p>The one reading of the field, because it is asked in several places — what the server is given, what
     * the preview is wrapped from, what a selection is measured against — and a field that has never been
     * typed in answers null rather than nothing.
     */
    private String nameMarkup() {
        return name.getText() == null ? "" : name.getText();
    }

    /** {@return the lore as it stands in the area}, one entry per line */
    List<String> loreLines() {
        return List.copyOf(lore.getLines());
    }

    /**
     * {@return the lore the item will be left with}, which is the field's lines with the one blank line a
     *         text area cannot be rid of read as no lore at all
     *
     * <p><b>A text area holds at least one line.</b> The library's own {@code TextArea#setValue} is asked
     * for no lines and puts one empty line in the field, so an item opened with no lore and an item whose
     * every line was typed away both stand in the field as one blank entry, and nothing tells them apart.
     * Read as a line, that entry is a lore line of no characters, and the edit that is sent writes it to the
     * item: the game draws an empty line of lore as the blank line in the item's tooltip where the operator
     * expected to see their name alone — the blank lore line that appears when there is no lore. Read as no
     * lore, the item keeps none and the preview is handed no lore line at all, which is what an empty field
     * means.
     *
     * <p><b>A blank line the operator typed is a different thing and survives.</b> It is one entry among
     * others rather than the whole of an otherwise empty field, so a lore of two lines whose first was left
     * blank still sends that blank line, and the item still shows the gap it was written for.
     */
    private List<String> itemLore() {
        List<String> lines = loreLines();
        return lines.size() == 1 && lines.get(0).isEmpty() ? List.of() : lines;
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
        record();
    }

    /** Rebuilds the attribute rows from the list, which is the only state the section keeps. */
    private void refreshAttributes() {
        // A row's widgets go with the rebuild, and one of them may be the field the keyboard is in: an
        // undo of a typed amount takes the row away from under its own caret. The focus is let go of
        // first, because the UI would otherwise keep sending keys to a field that is no longer in the
        // tree, which is a field that does nothing with them and says nothing about why.
        if (attributeRows.isAncestorOf(ui.getFocusedElement())) ui.clearFocus();
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
        // Every way a row can be changed arrives here — the name chosen, the amount typed, the operation
        // and the slot — so this is the one place a step of the attribute list is kept.
        record();
    }

    private void removeAttribute(int index) {
        if (index < 0 || index >= attributes.size()) return;
        attributes.remove(index);
        refreshAttributes();
        fill();
        record();
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
        // Reading the item into the fields is not an edit — there is nothing behind it to go back to —
        // so it is made off the record and the state it arrives at becomes the only entry of the history
        // so far. Done on every load rather than on the first, because a load is always the item as it
        // stands being put on screen: see the note on the history field.
        unrecorded(() -> {
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
        });

        history.clear();
        history.add(snapshot());
        step = 0;
    }

    /**
     * Sends all three sections for the server to write.
     *
     * <p>The whole edit, because the screen shows all of it: an empty lore is the operator having
     * removed every line, and an empty attribute list is an item that grants nothing. The lore is read
     * through {@link #itemLore} and not off the field, because a field with nothing in it holds one blank
     * line rather than none — sent as it stands, that line is written to the item as a blank line of lore
     * where the operator asked for none.
     *
     * <p>An edit past the bounds the message and the reader both keep is refused here, in words, and
     * nothing is sent: the wire would otherwise be written with a smaller item than the one on screen, or
     * throw on Apply.
     */
    private void send() {
        if (source == null) return;
        ItemEditPayload payload = ItemEditPayload.full(source.where(), source.containerId(),
                source.slot(), name.getText(), itemLore(), attributeList());
        String problem = ItemEditPayload.problem(payload);
        if (problem != null) {
            // Nothing is sent, so the item is untouched and this message is the whole of what happened.
            say(Component.literal(problem));
            return;
        }
        ItemEditorNetwork.send(payload);
    }

    /**
     * Writes the edit, or says a word about it once before writing it.
     *
     * <p>A name or a line of lore whose markup styles none of its own characters is not markup to the
     * library: it is drawn as the characters it is written with, so the item shows the tags themselves
     * rather than the words they were meant to shape — see {@link ItemEdit#styles} and {@link
     * ItemEdit#shown}, which is the same reading the preview draws by. That is worth a word, because the
     * operator typed one thing and the item would show another, and it is worth only a word: this screen
     * never rewrites, drops or reorders what was typed, and the press that follows the word writes it as it
     * stands.
     *
     * <p>So the first press names the lines that would be drawn as their tags and says what that means, and
     * the button becomes the confirmation; the press after it writes the edit. Anything else cancels: a
     * press whose offending lines are the ones already asked about writes them, and a press that finds
     * different ones asks about those, so a confirmation cannot land on a text the operator was not shown.
     *
     * <p><b>This is a word, not a gate.</b> The same name can reach an item without this screen being open
     * at all — the library's own anvil surface lets markup through, and a name can arrive from a command or
     * another mod — so an operator is told here, and nothing here claims every route passes through it.
     */
    private void commit() {
        List<String> lore = itemLore();
        List<ItemEdit.Unstyled> lines = ItemEdit.unstyled(nameMarkup(), lore);
        String asked = about(nameMarkup(), lore, lines);
        if (lines.isEmpty() || asked.equals(warnedAbout)) {
            warnedAbout = null;
            applyButton.text.setText(Text.of("backutils.item_editor.button.apply"), false);
            send();
            return;
        }

        warnedAbout = asked;
        applyButton.text.setText(Text.of("backutils.item_editor.button.apply_anyway"), false);
        for (ItemEdit.Unstyled line : lines) warn(line);
    }

    /**
     * Tells the operator that one line of the edit will be drawn as the characters of its own tags, and
     * which line it is: the notice the library's own reading of an item would otherwise give them, said
     * before the item is written rather than after somebody reads it.
     */
    private void warn(ItemEdit.Unstyled line) {
        Component where = line.title()
                ? Component.translatable("backutils.item_editor.label.name")
                : Component.translatable("backutils.item_editor.label.lore_line", line.line() + 1);
        say(Component.translatable("backutils.item_editor.unstyled", where));
    }

    /** Says one thing to the operator, on the line every notice from this screen already uses. */
    private static void say(Component message) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        player.displayClientMessage(message, false);
    }

    /**
     * {@return what a warning about these lines is about}: which lines they are and the markup each holds,
     * in the order they stand.
     *
     * <p>Compared with the answer to the same question asked again, which is what tells a confirmed press
     * from one that has to ask: a line of it changed, a line stopped offending, or another one started, and
     * the two answers differ.
     */
    private static String about(String name, List<String> lore, List<ItemEdit.Unstyled> lines) {
        StringBuilder out = new StringBuilder();
        for (ItemEdit.Unstyled line : lines) {
            String where = line.title() ? "name " : "lore " + (line.line() + 1) + ' ';
            out.append(where).append(line.title() ? name : lore.get(line.line())).append('\u0000');
        }
        return out.toString();
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

    // ------------------------------------------------------------------
    // The history: what an undo puts back, and the two chords that ask for one
    // ------------------------------------------------------------------

    /**
     * One step: everything inside this screen that an edit can alter.
     *
     * <p>The whole state rather than a difference, because an undo has to put a state back and not a
     * change: the name, the lore, the colour the picker holds, the attributes, and the selection in the
     * preview — which is an edit's own state as well, since the buttons act on it.
     *
     * <p>Not the field the caret is in, the name field's remembered selection, or the drag in progress.
     * Those are readings of what the operator is doing rather than of what is being edited: the caret's
     * field is taken again from the focused field on every frame, and a selection belongs to the text it
     * was taken in, which {@link #live} already checks before anything is done with it.
     */
    private record Snapshot(String name, List<String> lore, int colour, List<ItemEdit.Granted> attributes,
                            Mark mark, String marked) { }

    /** {@return the state on screen as one step of the history} */
    private Snapshot snapshot() {
        return new Snapshot(nameMarkup(), loreLines(), picker.getColor(), attributeList(), mark, marked);
    }

    /**
     * Keeps the state as it now stands, so that an undo can come back to it.
     *
     * <p>Called after a change rather than before one, which is what makes the step the position stands
     * on the state on screen: a change writes itself into the fields, the change is kept, and an undo
     * steps back to the entry before it.
     *
     * <p>A state identical to the one already kept is not kept again, so re-choosing the colour that is
     * already chosen, or an edit that turns out to change nothing, does not fill the history with steps
     * that undo to themselves. What is kept is one step per change, a typed character included — the
     * library's own fields keep their history at the same grain, see {@code TextField} — rather than a
     * run of typing gathered into one step, which would make undoing a single word of it impossible.
     *
     * <p>A change made after an undo throws away what was undone: the redo of a step is the step that
     * followed it, and this one no longer does.
     */
    private void record() {
        if (writing) return;

        Snapshot now = snapshot();
        if (step >= 0 && history.get(step).equals(now)) return;

        while (history.size() > step + 1) history.remove(history.size() - 1);
        history.add(now);
        if (history.size() > HISTORY) history.remove(0);
        step = history.size() - 1;
    }

    /**
     * Runs one change with the history deaf to it, so that a change made of several writes is one step.
     *
     * <p>A formatting tag written to every line a selection covers is one press of one button, and a
     * state put back by an undo is not a change at all; without this each write would be its own step and
     * only the first of them could be walked back to.
     */
    private void unrecorded(Runnable change) {
        writing = true;
        try {
            change.run();
        } finally {
            writing = false;
        }
    }

    /** Goes back one step, if there is one behind the state on screen; nothing happens when there is not. */
    private void undo() {
        if (step <= 0) return;
        step--;
        restore(history.get(step));
    }

    /** Goes forward again through a step that was undone, if there is one. */
    private void redo() {
        if (step < 0 || step + 1 >= history.size()) return;
        step++;
        restore(history.get(step));
    }

    /**
     * Puts one step back on screen: every field a change can have written, and the preview with them.
     *
     * <p>Written out in one place, because an undo that put the fields back and left the picker or the
     * attribute list as the undone change left them would be an undo of part of an edit, and the state it
     * is meant to restore is one the operator had. The writes are made off the record, since putting a
     * state back is not a change to it.
     */
    private void restore(Snapshot state) {
        unrecorded(() -> {
            name.setText(state.name(), false);
            lore.setLines(state.lore());
            picker.setColor(state.colour(), false);
            attributes.clear();
            attributes.addAll(state.attributes());
            refreshAttributes();
            mark = state.mark();
            marked = state.marked();
            // The rows the preview draws are keyed on the text they were wrapped from, which has just
            // changed, so the next frame would build them again anyway. They are forgotten outright
            // because this is a state being put back rather than a change being made, and a preview
            // redrawn must not be a thing that happens only when comparing that key says so.
            nameRows.forget();
            loreRows.forget();
        });
    }

    // ------------------------------------------------------------------
    // The preview: what it draws, and what can be selected in it
    // ------------------------------------------------------------------

    /**
     * One character of a drawn row: how far it stands from the row's left edge, how wide it is drawn, and
     * which characters of the item it is.
     *
     * <p>The distance and not the place, because the place moves with the layout and the distance does not:
     * see {@link #glyphs}.
     */
    private record Glyph(int x, int width, int from, int to) { }

    /**
     * One drawn row of the preview: the line of the item it came from, where it sits, where its text begins,
     * and its characters.
     *
     * <p>The characters are the row's own and still measured from the row's left edge; the left is the edge
     * they are measured from, taken again each frame because a frame's layout is what puts the row where it
     * is.
     */
    private record Line(int field, int y, int left, List<Glyph> glyphs) { }

    /** Where the mouse is: which line of the item, and which character of that line. */
    private record Spot(int field, int at) { }

    /**
     * What is selected: a run of the characters the item shows, from one place in it to another.
     *
     * <p>Held as the two ends rather than as one line and a run of it, because the preview draws every line of
     * the item one under another and a drag can cross from one to the next: the name is the first line of what
     * is drawn and the lore follows it, so a selection is a run of that whole text and only the drawing knows
     * where the lines break in it.
     */
    private record Mark(int fromField, int fromAt, int toField, int toAt) { }

    /**
     * Draws both halves of the preview: the name and the lore as the game would draw them, from the same
     * markup the server is about to be given, inside a frame of their own.
     *
     * <p>Drawn here rather than by widgets because a widget takes a plain string, and a plain string is the
     * one thing this must not be: what is being checked is the formatting. Where to draw is asked of the
     * pieces that reserve the space rather than worked out from a row count, because a row count has to be
     * kept in step with the layout by hand — which is what put the preview across the style row once.
     *
     * <p>The frame is drawn rather than given as an element around the two boxes, and that is a decision made
     * after trying the other way: a box put round them in the layout came out shorter than what it held, so
     * the lore was positioned below its own frame and stood over the attribute list — where, being a box that
     * takes a hit test, it swallowed half of the button that writes the item, which then stood at the foot of
     * the panel. A rectangle drawn from the two boxes this screen already measures cannot do either: it is
     * the same numbers the drawing is done from, and the frame the two boxes ask for is exactly the column
     * they stand in — see the note on the previews' width in build.
     *
     * <p>It also writes down what it drew, and that is what the mouse is measured against afterwards: the
     * geometry of the last frame is the only text on screen, so it is the only thing a click can mean.
     */
    private void drawPreviews(GuiGraphics graphics) {
        drawn.clear();
        markLive = live() != null;
        // The lore, read once for the whole frame: the copy of the stack the icon is drawn from carries it,
        // and the preview is wrapped from it, and every read of it copies the field's own list out. Read as
        // the item will have it rather than off the field, so that an item with no lore has no lore line to
        // draw and none to send: the field's one blank entry is what an empty field is made of, and read as a
        // line it is a line of no characters — see itemLore.
        List<String> lore = itemLore();
        // One copy of the item for the whole frame: the icon draws it.
        ItemStack preview = previewStack(lore);

        int left = Math.round(namePreview.getPositionX()) - PREVIEW_PAD;
        int top = Math.round(namePreview.getPositionY()) - PREVIEW_PAD;
        int right = Math.round(namePreview.getPositionX() + namePreview.getSizeWidth()) + PREVIEW_PAD;
        int bottom = Math.round(lorePreview.getPositionY() + lorePreview.getSizeHeight()) + PREVIEW_PAD;

        // Clipped to the frame, which is also what keeps the icon inside it: an item's model is larger than the
        // sixteen pixels it is given room for, and a panel that has been scrolled must not have a model drawn
        // over whatever took its place.
        graphics.enableScissor(left, top, right, bottom);
        graphics.fill(left, top, right, bottom, PREVIEW_BG);
        graphics.fill(left, top, right, top + 1, PREVIEW_EDGE);
        graphics.fill(left, bottom - 1, right, bottom, PREVIEW_EDGE);
        graphics.fill(left, top, left + 1, bottom, PREVIEW_EDGE);
        graphics.fill(right - 1, top, right, bottom, PREVIEW_EDGE);

        drawIcon(graphics, preview);
        drawPreview(graphics, namePreview, List.of(nameMarkup()), true);
        drawPreview(graphics, lorePreview, lore, false);
        graphics.disableScissor();
    }

    /**
     * Draws the item's own icon at the head of the preview, larger than the game's own sixteen.
     *
     * <p>From a copy of the stack carrying the name and the lore the fields now hold, because that is what the
     * server is about to write: a mod that restyles an item from its name reads the stack, and a preview of
     * the stack as it stands would show the item as it was rather than as it is about to be. The component is
     * built the same way the server builds it, for the same reason.
     *
     * <p>Drawn through a moved and scaled pose rather than by asking for a bigger icon, because there is no
     * bigger icon to ask for: an item is drawn in a slot sixteen pixels square, and the way to have it larger is
     * to draw the slot larger. Everything about the icon is scaled together that way — the model, whatever a
     * resource pack or another mod has done to it, and the light on it.
     *
     * <p>The depth is scaled with the rest, which a flat sprite would not have noticed: the tools are drawn from
     * models with a third dimension, and a pose that left depth at one would stretch them along it, which is the
     * axis their GUI turn points towards the viewer.
     */
    private void drawIcon(GuiGraphics graphics, ItemStack preview) {
        if (preview == null) return;

        float scale = PREVIEW_ICON / 16f;
        graphics.pose().pushPose();
        graphics.pose().translate(Math.round(namePreview.getPositionX()) + ICON_PAD,
                Math.round(namePreview.getPositionY()) + ICON_PAD, 0);
        graphics.pose().scale(scale, scale, scale);
        graphics.renderItem(preview, 0, 0);
        graphics.pose().popPose();
    }

    /**
     * {@return the item as the editor is about to leave it}, or null when the slot holds nothing
     *
     * <p>The lore is handed in rather than read here, because the same lines wrap the preview under the icon
     * and every read of them copies the field's own list out: one copy for the frame is enough.
     */
    private ItemStack previewStack(List<String> lore) {
        ItemStack stack = stack();
        if (stack == null || stack.isEmpty()) return null;

        ItemStack preview = stack.copy();
        String markup = nameMarkup();
        if (markup.isEmpty()) {
            preview.remove(DataComponents.CUSTOM_NAME);
        } else {
            preview.set(DataComponents.CUSTOM_NAME, ItemEdit.line(markup));
        }

        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            if (!line.isEmpty()) lines.add(ItemEdit.line(line));
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
     * @param lines the markup of each line this half shows: the name is one line of text, and the lore is
     *              one for each line it has
     * @param title whether this is the item's name, which is one line of text, or its lore, which is one
     *              line for each line the lore has
     */
    private void drawPreview(GuiGraphics graphics, UIElement area, List<String> lines, boolean title) {
        Minecraft minecraft = Minecraft.getInstance();
        int box = Math.round(area.getPositionX());
        int left = box + PREVIEW_INDENT;
        int top = Math.round(area.getPositionY());
        int inner = Math.round(area.getSizeWidth()) - PREVIEW_INDENT;
        int bottom = top + Math.round(area.getSizeHeight());

        // A box the layout has not measured answers nothing, and a preview with no room draws nothing at
        // all — which is how it came out blank once with a name sitting in the field above it. The room
        // that was asked for is the fallback, so it is the same arithmetic the layout is given: the panel's
        // width less the picker's column and the gap beside it, less the frame's own padding and the room
        // the icon is drawn in. A fallback as wide as the panel would wrap the text for a width the box
        // never has, and the wrap is kept — see Rows, whose key is the width.
        if (inner <= 0) {
            inner = Math.max(1, Math.round(this.width) - 2 * PAD - PICKER - GAP
                    - 2 * PREVIEW_PAD - PREVIEW_INDENT);
        }
        if (bottom <= top) {
            bottom = top + (title ? rowHeight() + PREVIEW_GAP : LORE_PREVIEW_HEIGHT);
        }

        // The room one drawn row takes. The text is drawn at the game's own size — a preview is read beside the
        // text it is of, and two sizes of the same words on one screen is a screen that looks wrong — so this is
        // the font's own line height and nothing else.
        int lineHeight = rowHeight();

        // Wrapped for the whole half rather than row by row inside the drawing loop, because the room the lore
        // asks for is the room its text takes and that has to be known before anything is drawn. Measuring the
        // rows that fit in the box instead would have a preview clipped to a box smaller than its text measure
        // itself as smaller still, and never grow. The wrap, and the component each row is drawn from, are kept
        // until the text or the width they are wrapped into changes — see Rows — because a row rebuilt inside
        // the draw call is an effect in it that starts again on every frame.
        List<List<Wrapped>> rows = (title ? nameRows : loreRows).of(minecraft.font, lines, inner);
        int wanted = 0;
        for (List<Wrapped> line : rows) wanted += line.size();
        if (title) {
            // The icon's room is not asked for here: the icon is drawn at this box's corner and hangs below
            // it, and what holds it is the frame — see the note on the name's box in build. The air under the
            // text is part of the box, because the box's height is what puts the lore where a tooltip puts it.
            roomFor(namePreview, Math.max(rowHeight(), wanted * lineHeight) + PREVIEW_GAP);
        } else {
            // The lore's box is exactly its text, and nothing when there is no text: a tooltip has no room
            // after its last line, and the room a preview does not use is room the attribute list wants.
            roomFor(lorePreview, wanted * lineHeight);
        }

        // Clipped to the room it was given, the way a widget would be: the panel scrolls, and a preview
        // that has been scrolled out of the way must not be drawn over whatever took its place. The clip
        // starts at the box rather than the text, so the icon drawn beside it is inside it too.
        graphics.enableScissor(box, top, box + inner + PREVIEW_INDENT, bottom);
        int y = top;
        for (int index = 0; index < rows.size() && y + lineHeight <= bottom; index++) {
            for (Wrapped line : rows.get(index)) {
                if (y + lineHeight > bottom) break;
                drawLine(graphics, line, title ? NAME_FIELD : index, left, y, lineHeight);
                y += lineHeight;
            }
        }
        graphics.disableScissor();
    }

    /** {@return the room one drawn row of the preview takes}, which is the game's own line height */
    private static int rowHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }

    /**
     * Asks the layout for the room one half of the preview has measured that it needs, if it has not got it
     *
     * <p>Asked for only when the number has changed, and that is not a saving but a requirement: the layout is
     * recomputed whenever the tree is dirty, so a height written on every frame would leave it dirty on every
     * frame — a relayout per frame for a number that is the same, and a warning about it from the library in a
     * development environment. The asking is done while the frame is being drawn and is applied to the next
     * one, which is a frame nobody can see.
     */
    private static void roomFor(UIElement element, int height) {
        float current = element.getSizeHeight();
        if (!Float.isNaN(current) && Math.abs(current - height) <= 0.5F) return;
        element.layout(l -> l.height(height));
        element.markTaffyStyleDirty();
    }

    /**
     * One wrapped row of the preview: the component the game draws it with, and where each of its characters
     * stands.
     *
     * <p>The component is the very one the server writes — {@link ItemEdit#line} — so that this is not a
     * drawing of the item but the item: the markup travels as the text of a literal, the library's own hook
     * parses it as the game draws it, and a colour and an effect written into a line appear here as they will
     * appear on the item. Built with the row and kept for as long as the row, because the library hangs the
     * state an effect animates from off that parse — {@link DrawnRows} says the same thing at length — and the
     * size is the one thing that differs, a matter of the pose the preview is drawn in rather than of the
     * component.
     *
     * <p>The geometry is kept with it, measured from the row's own left edge rather than from the screen's:
     * the distances between a row's characters do not change while the layout does, so the drawing and the
     * mouse both add the row's place to them — see {@link #glyphs}. The index each character carries is its
     * place in what the <em>line</em> of the item shows, which is what a selection is counted in: the row's
     * place in its line was added as the row was measured, because a click on the second row of a wrapped lore
     * line is a click on a character of that line and not on the first character of the row.
     */
    private record Wrapped(Component component, List<Glyph> glyphs) { }

    /**
     * The rows one half of the preview was last wrapped into, and the text, the width and the font they were
     * wrapped from.
     *
     * <p>Kept rather than wrapped again on every frame, for the reasons {@link DrawnRows} gives: this is the
     * same work over the same text, and more than that, each row carries the component the game draws it with,
     * and an effect in one has nowhere to keep its state if that component is built again sixty times a second.
     *
     * <p><b>What decides the rows is everything the wrap reads</b>, and it is meant to be nothing else: the
     * markup of every line, the width they are wrapped into, and the font they are measured with. A line that
     * is retyped, a lore line added or removed, the window resized or the panel's columns moved, all arrive as
     * one of those three.
     *
     * <p>The font's own glyph widths are the one input this cannot watch: the game's font is a final field
     * that reloads its providers in place, so a resource pack replacing a glyph or the unicode font being
     * switched on changes the widths under the same instance. The rows then stay as they were wrapped until
     * the text or the width changes, which is the same bargain {@link HudBanner} strikes with the same inputs.
     */
    private static final class Rows {

        private List<List<Wrapped>> rows = List.of();
        private List<String> from = List.of();
        private Font font;
        private int width = Integer.MIN_VALUE;

        /** {@return the rows for this text, wrapped again only when something they were built from has changed} */
        List<List<Wrapped>> of(Font font, List<String> lines, int width) {
            if (font == this.font && width == this.width && lines.equals(this.from)) return rows;

            List<List<Wrapped>> built = new ArrayList<>(lines.size());
            for (String line : lines) built.add(wrap(font, line, width));
            rows = built;
            from = List.copyOf(lines);
            this.font = font;
            this.width = width;
            return rows;
        }

        /**
         * Forgets what was wrapped, so that the next frame measures and builds it again.
         *
         * <p>For an undo, which puts a state back rather than changing one: the key above decides by
         * comparing the text, and a preview that has just been undone out of must not depend on that
         * comparison happening to differ. One frame's wrapping is what it costs.
         */
        void forget() {
            font = null;
            width = Integer.MIN_VALUE;
            from = List.of();
        }
    }

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

        // When the library will draw the markup as its own characters — see ItemEdit.drawnAsCharacters —
        // a row is a slice of those characters and nothing else, drawn as a literal of that slice. Written
        // through the runs instead, the tags would be tidied on the way through: a closer with nothing open,
        // or a pair round nothing, is dropped by that reading, and a preview that dropped them would be
        // showing a line the item will not have. The slice needs no tags of its own: it is drawn as the
        // characters it holds either way, which is what the whole line is drawn as.
        boolean asWritten = ItemEdit.drawnAsCharacters(markup);

        int start = 0;
        while (start < shown.length()) {
            // Measured a character at a time and added up, which is both what the drawing measures and the
            // only way this stays linear: measuring a substring per character is quadratic in the length of
            // a lore line. It runs when the text or the width changes rather than on every frame, which is
            // what the rows being kept is for — see Rows.
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

            String characters = shown.substring(start, end);
            Component row;
            if (asWritten) {
                row = Component.literal(characters).withStyle(ItemEdit.DEFAULT_STYLE);
            } else {
                int[] range = ItemEdit.shownRange(markup, shown, start, end);
                // The run with the tags over it written round it, and not a slice of the markup: a colour
                // written round a whole line reaches over the ends of every row it wraps into, and a slice
                // would leave the row drawn in whatever the hover gives unstyled text — which is how a
                // coloured name came out white.
                row = ItemEdit.line(ItemEdit.marked(markup, range[0], range[1]));
            }
            out.add(new Wrapped(row, glyphs(font, characters, start)));
            start = end;
        }
        return out;
    }

    /**
     * {@return each character of one row of the preview, and where it stands}
     *
     * <p>Measured once with the row rather than while the row is drawn, which is the same work over the same
     * characters: the font's own answer for each of them, added up into a place. Drawn from these, the pen
     * that puts the selection behind a character and the mouse that turns a click into a position in a field
     * walk the same numbers, and they have to be the characters' own — the index each glyph carries is its
     * place in what the line of the item shows, the row's place in it added to the character's place in the
     * row, which is what a selection means and what the markup's own indices are not. Taken again every frame,
     * this is a fresh one-character string and a lookup per character of every row on screen.
     *
     * <p>Distances and not places, because a row's left edge moves with the layout — the panel scrolls, the
     * window is resized — while the distance between two of its characters does not. Whoever draws or measures
     * the row adds its left edge, so a row that has moved takes every character with it.
     */
    private static List<Glyph> glyphs(Font font, String shown, int from) {
        List<Glyph> out = new ArrayList<>();
        int pen = 0;
        for (int i = 0; i < shown.length(); ) {
            int codepoint = shown.codePointAt(i);
            int width = font.width(new String(Character.toChars(codepoint)));
            int length = Character.charCount(codepoint);
            out.add(new Glyph(pen, width, from + i, from + i + length));
            pen += width;
            i += length;
        }
        return out;
    }

    /**
     * Draws one drawn row of the preview, and writes down where each of its characters landed.
     *
     * <p>What the mouse is measured against afterwards is the index every character has in the text the item
     * shows, which is the whole reason a click on a drawn character can become a position in a field: what is
     * drawn and what is selected are the same text, and that index says which part of it this character is.
     * The distances are the row's own — see {@link #glyphs} — and the place the row starts at is added to them
     * here, so a row the layout has moved takes its characters with it.
     */
    private void drawLine(GuiGraphics graphics, Wrapped line, int field, int left, int y, int lineHeight) {
        Font font = Minecraft.getInstance().font;

        // Behind the text rather than over it, so that what is selected can still be read while it is.
        for (Glyph glyph : line.glyphs()) {
            if (!selected(field, glyph.from(), glyph.to())) continue;
            int x = left + glyph.x();
            graphics.fill(x, y - 1, x + glyph.width(), y + lineHeight - 1, SELECTION_BG);
        }

        // The component the server writes, drawn by the game's own renderer: the library's hook on a literal
        // parses it as it is drawn, which is what makes a colour and an effect appear here as they will appear
        // on the item. The colour given here is only the last resort the game uses for text with none of its
        // own, which the line's default style always gives it. The component is the row's own rather than one
        // built here, which is what gives an effect in it somewhere to keep its state: see {@link DrawnRows}.
        graphics.drawString(font, line.component(), left, y, 0xFFFFFF);
        drawn.add(new Line(field, y, left, line.glyphs()));
    }

    /** {@return whether one character of the item's text is inside what is selected} */
    private boolean selected(int field, int from, int to) {
        Mark selection = mark;
        // The character is inside the run when it starts before the run ends and ends after it starts, in the
        // order the preview draws the item's lines in — which is the name first and the lore under it.
        return markLive && selection != null
                && before(field, from, selection.toField(), selection.toAt())
                && before(selection.fromField(), selection.fromAt(), field, to);
    }

    /** {@return whether one place in the item's text comes before another}, the name counting first */
    private static boolean before(int field, int at, int otherField, int otherAt) {
        int line = order(field);
        int other = order(otherField);
        return line != other ? line < other : at < otherAt;
    }

    /** {@return where a line of the item stands in the preview}, which is the name and then the lore */
    private static int order(int field) {
        return field == NAME_FIELD ? 0 : field + 1;
    }

    /**
     * {@return the selection, or null when the text it was measured in is no longer what the lines hold}
     *
     * <p>The one place the question is answered, so that the highlight and the buttons cannot disagree
     * about whether there is a selection at all.
     */
    private Mark live() {
        Mark selection = mark;
        if (selection == null) return null;
        if (marked != null && shownOf(selection).equals(marked)) return selection;

        mark = null;
        marked = null;
        return null;
    }

    /**
     * {@return the characters the lines of a selection show}
     *
     * <p>What a selection is remembered by, and what tells a stale one from a live one: the characters of the
     * item's text are the same after a button has wrapped them as they were before, so a selection whose
     * characters are still there is one the buttons can still be pressed on.
     */
    private String shownOf(Mark selection) {
        StringBuilder out = new StringBuilder();
        for (int field = selection.fromField(); field <= selection.toField(); field++) {
            out.append(ItemEdit.shown(markup(field))).append('\n');
        }
        return out.toString();
    }

    /**
     * {@return where the mouse is in the preview}, or null when it is not over any drawn text
     *
     * <p>The nearer edge of the character under the mouse is the one it counts as, which is what a text
     * field does and what a hand expects: a drag that ends between two characters ends before whichever
     * of them it is nearer the front of.
     */
    private Spot spot(double mouseX, double mouseY) {
        int lineHeight = rowHeight();
        for (int i = 0; i < drawn.size(); i++) {
            Line line = drawn.get(i);
            if (mouseY < line.y() || mouseY >= line.y() + lineHeight) continue;

            int at = -1;
            for (Glyph glyph : line.glyphs()) {
                // The row's own distances, placed where the row was drawn: see drawLine.
                int x = line.left() + glyph.x();
                at = mouseX < x + glyph.width() / 2 ? glyph.from() : glyph.to();
                if (mouseX < x + glyph.width()) break;
            }
            // An empty line has no characters to be between.
            return at < 0 ? null : new Spot(line.field(), at);
        }
        return null;
    }

    /**
     * {@return the selection a drag has reached}
     *
     * <p>It runs across lines, because that is what a selection over a preview is: the name is the first line
     * of what is drawn and the lore follows under it, and dragging from a word in the name to a word in the
     * lore selects everything between them — which the buttons then write to line by line.
     *
     * <p>A drag that has left the drawn text takes the rest of the preview: downwards everything to the end of
     * the last line drawn and upwards everything from the start of the first, which is what running off the
     * end of a field means everywhere else.
     */
    private Mark extend(double mouseX, double mouseY) {
        if (anchor == null || drawn.isEmpty()) return null;

        Spot spot = spot(mouseX, mouseY);
        if (spot == null) {
            // The mouse is above the first row drawn, below the last, or in the seam between two of them — the
            // name's box is taller than the line drawn in it, and the lore is pulled up into the rest of it. The
            // nearest row is the one the drag has reached, and which end of it is taken depends on which way the
            // mouse is going, so a drag stopped in a seam still takes the whole of the line it was heading for
            // rather than everything to the end of the preview.
            Line nearest = drawn.get(0);
            for (Line row : drawn) {
                if (Math.abs(row.y() - mouseY) < Math.abs(nearest.y() - mouseY)) nearest = row;
            }
            boolean upwards = mouseY < nearest.y();
            int at = upwards ? 0 : ItemEdit.shown(markup(nearest.field())).length();
            spot = new Spot(nearest.field(), at);
        }

        // The two ends in the order the item is read, whichever way round the drag went.
        boolean forwards = before(anchor.field(), anchor.at(), spot.field(), spot.at());
        Mark wanted = forwards
                ? new Mark(anchor.field(), anchor.at(), spot.field(), spot.at())
                : new Mark(spot.field(), spot.at(), anchor.field(), anchor.at());
        // A drag that has not crossed a character has selected nothing, and an empty selection is nothing
        // rather than a run of no characters that the next button would wrap.
        boolean crossed = wanted.fromField() != wanted.toField() || wanted.fromAt() != wanted.toAt();
        return crossed ? wanted : null;
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
            marked = mark == null ? null : shownOf(mark);
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
        // The undo and redo chords are taken here: before anything with the focus is asked, and whether or
        // not there is a step to go to. Both halves matter. The library's own text fields keep an undo
        // apiece — see TextField's history — and it answers these very chords, so one that reached the
        // focused field would undo that field alone: the name put back with the lore left as the undone
        // change wrote it, which is a state the operator never had. And a chord that fell through when the
        // history was empty would reach that same field undo and do exactly the same thing, which is not
        // "nothing to undo, so nothing happens". Shift with Ctrl+Z is the other direction, as it is in the
        // field's own table, and must not be the one chord that still reaches it.
        if (Screen.hasControlDown() && !Screen.hasAltDown()
                && (keyCode == GLFW.GLFW_KEY_Z || keyCode == GLFW.GLFW_KEY_Y)) {
            if (keyCode == GLFW.GLFW_KEY_Z && !Screen.hasShiftDown()) {
                undo();
            } else {
                redo();
            }
            return true;
        }

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
