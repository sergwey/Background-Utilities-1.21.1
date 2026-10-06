package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.client.BackUtilsClientConfig;
import net.xlebupaksa.backutils.client.EffectToolSlot;
import net.xlebupaksa.backutils.client.PhotonFx;
import net.xlebupaksa.backutils.item.EffectToolConfig;
import net.xlebupaksa.backutils.item.EffectToolItem;
import net.xlebupaksa.backutils.network.EffectToolConfigPayload;
import net.xlebupaksa.backutils.network.EffectToolFeedbackPayload;
import net.xlebupaksa.backutils.network.EffectToolNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The effect tool's own screen: everything the spec's list holds, written onto the tool in the slot
 * it was opened over.
 *
 * <p>An edit is sent to the server rather than written here, because the stack this screen reads is
 * the client's copy of one the server owns. The screen says what it asked for and then says what
 * came back — stored, refused, or unanswered after a wait — and it never calls a change written on
 * its own authority, since only the server knows whether it was stored at all.
 *
 * <p>Built once and kept, the way the menu and the profile editor are, with the captions written
 * again on every open so a language change reaches it.
 *
 * <p>The tool is found again by where it was hovered rather than by its stack: closing the inventory
 * leaves the <em>menu</em> in place, and a stack taken from a closing screen is not the one in the
 * player's hand. The place is an {@link EffectToolSlot.Address} rather than a bare index, because an
 * index means different things in the client's menu and the server's. When the address names no tool
 * the screen says so and edits nothing, rather than writing settings to whatever is in the hand at
 * the time.
 *
 * <p>Ranges are not checked here: every value goes through {@link EffectToolConfig#of}, which clamps
 * it, the caption above each box states the range so a correction can be seen, and the server clamps
 * the same values once more before it stores them.
 */
@OnlyIn(Dist.CLIENT)
public final class EffectToolScreen extends Screen {

    private static final int PANEL_WIDTH = 360;
    private static final int PAD = 10;
    private static final int GAP = 5;
    private static final int ROW = 16;
    private static final int LABEL_WIDTH = 118;
    /** Room for "z" beside a pair of boxes: three of them share the width the panel has left. */
    private static final int AXIS_WIDTH = 8;
    /** How wide the dropdown of available effects is, which the path box shares its row with. */
    private static final int EFFECTS_WIDTH = 108;

    /**
     * How wide the number box and the switch beside it are, in the two rows that have both.
     *
     * <p>Fixed rather than left to each control, because the two boxes being the same width is what
     * puts the two switches in the same column: a switch is read as belonging to the box beside it,
     * and only stays in one place if the box does.
     */
    private static final int SWITCH_BOX_WIDTH = 62;
    private static final int SWITCH_WIDTH = 22;

    /**
     * How long an edit may wait for the server before the screen stops saying it was sent. Long
     * enough for a slow connection to answer, short enough that a player who is not going to hear
     * anything is told so while they are still looking at the screen.
     */
    private static final long ANSWER_NANOS = 2_000_000_000L;

    private static final int PANEL_BG = 0xF0101016;
    private static final int MUTED = 0xFF9AA3B2;
    private static final int PLAIN = 0xFFE2E8F0;
    private static final int WARNING = 0xFFFF8080;
    private static final int GOOD = 0xFF8FD98F;

    private static EffectToolScreen instance;

    /** Everything built once, kept so {@link #init} can write the held tool's values into it. */
    private static final class Built {

        /** One caption and the key behind it, so {@link #applyTexts} can write it out again. */
        private record Caption(TextElement element, String key) {}

        ModularUI ui;
        UIElement root;
        UIElement panel;
        Selector<String> mode;
        Selector<String> autoRotate;
        TextField lifetime;
        Toggle infinite;
        TextField distance;
        Toggle reach;
        TextField path;
        /**
         * The effects this client has, offered beside the path box.
         *
         * <p>Hidden when there are none, which is what a client without Photon has: a dropdown over
         * no effects would be a control that does nothing, and the box beside it is still typable.
         */
        Selector<String> effects;
        TextField[] scale = new TextField[3];
        TextField[] rotation = new TextField[3];
        TextField[] offset = new TextField[3];
        Toggle forceDeath;
        TextField delay;
        Label status;
        Button apply;

        final List<Caption> captions = new ArrayList<>();
    }

    private final Built built;

    /** The mode chosen by the player, since the selector holds a name and the config an enum. */
    private EffectToolConfig.Mode chosenMode = EffectToolConfig.Mode.NONE;
    /** The auto-rotation chosen by the player, held as an enum for the same reason. */
    private EffectToolConfig.AutoRotate chosenAutoRotate = EffectToolConfig.AutoRotate.DEFAULT;
    /** The slot the tool was hovered in, searched again on every write. */
    private EffectToolSlot.Address source;

    /**
     * The tool that was hovered when this screen was opened, which is what it is about.
     *
     * <p>Kept beside the address rather than looked up again, because the address is a place and the place
     * does not survive this screen: opening it closes the container it was opened from, so a lookup that
     * answered a moment ago answers with an empty slot — or with the player's inventory menu, which is not
     * where the tool was. A screen that said "No effect tool is held." to an operator who had just held W on
     * one was reading the place instead of the tool.
     */
    private ItemStack opened = ItemStack.EMPTY;
    /** True while an edit waits for the server, with the time it was sent to measure that wait. */
    private boolean waitingForAnswer;
    private long askedAt;

    private EffectToolScreen(Built built) {
        super(Component.translatable("backutils.effect_tool.screen.title"));
        this.built = built;
    }

    /**
     * Opens the screen on a tool, named by the address the server is told about.
     *
     * <p>The tool travels with the address because an address is only a place. What the screen is about is the
     * stack that was hovered, and it holds that from the moment it opens: the place it was taken from is gone
     * by then — this screen has closed the container — and asking the place again is how the screen came to
     * say there was no tool at all to an operator who had just held W on one.
     *
     * <p>A null address is not a failure to open: it is a tool the server cannot be told about, which
     * is what the creative screen's item list holds, and the screen says so rather than doing nothing
     * and leaving the hold looking broken.
     */
    public static void open(EffectToolSlot.Address address, ItemStack tool) {
        if (Minecraft.getInstance().player == null) return;
        EffectToolScreen screen = screen();
        screen.source = address;
        screen.opened = tool == null ? ItemStack.EMPTY : tool;
        Minecraft.getInstance().setScreen(screen);
    }

    private static EffectToolScreen screen() {
        if (instance == null) {
            Built built = new Built();
            build(built);
            built.ui = ModularUI.of(UI.of(built.root));
            // Screens do not tick their UI by default.
            built.ui.setTickWhileRending(true);
            instance = new EffectToolScreen(built);
        }
        return instance;
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private static void build(Built built) {
        Label title = new Label();
        translated(built, title, "backutils.effect_tool.screen.title");
        title.textStyle(t -> t.fontSize(12f).textColor(PLAIN).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));

        // The selector's entries are the mode keys, which its own default provider renders as
        // translatable components: the captions and the settings are the same strings. What comes
        // back is a key as well, so it is read back by key — reading it as a name would answer
        // nothing, and nothing is what would then be stored.
        built.mode = new Selector<>();
        built.mode.layout(l -> l.flex(1).height(ROW));
        built.mode.setCandidates(EffectToolConfig.Mode.ALL.stream()
                .map(EffectToolConfig.Mode::key).toList());
        built.mode.setOnValueChanged(key -> {
            if (instance == null) return;
            instance.chosenMode = EffectToolConfig.Mode.byKey(key).result()
                    .orElse(EffectToolConfig.Mode.NONE);
            instance.refreshStatus();
        });

        // The four values as the spec names them, none included: the plain entity mode resolves to
        // none by itself, so a tool being configured can hold it and see it named. Keys again, and
        // read back by key again.
        built.autoRotate = new Selector<>();
        built.autoRotate.layout(l -> l.flex(1).height(ROW));
        built.autoRotate.setCandidates(EffectToolConfig.AutoRotate.ALL.stream()
                .map(EffectToolConfig.AutoRotate::key).toList());
        built.autoRotate.setOnValueChanged(key -> {
            if (instance == null) return;
            instance.chosenAutoRotate = EffectToolConfig.AutoRotate.byKey(key).result()
                    .orElse(EffectToolConfig.AutoRotate.DEFAULT);
            instance.refreshStatus();
        });
        built.lifetime = field(SWITCH_BOX_WIDTH);
        built.infinite = toggle(value -> {
            if (instance == null) return;
            built.lifetime.setDisplay(!value);
            if (value) {
                built.lifetime.setText(String.valueOf(EffectToolConfig.INFINITE_LIFETIME), false);
            }
            instance.refreshStatus();
        });
        built.distance = field(SWITCH_BOX_WIDTH);
        built.reach = toggle(value -> {
            if (instance == null) return;
            built.distance.setDisplay(!value);
            if (value) {
                built.distance.setText(String.valueOf(EffectToolConfig.PLAYER_REACH), false);
            }
            instance.refreshStatus();
        });

        built.path = field(0);

        // The list of effects this client has, beside the box it fills. A selector rather than a
        // completion popup of our own: it is the control the screen already uses for the mode and
        // the auto-rotation, and it offers exactly what it holds.
        built.effects = new Selector<>();
        built.effects.layout(l -> l.width(EFFECTS_WIDTH).height(14));
        built.effects.setOnValueChanged(id -> {
            if (instance == null) return;
            built.path.setText(id, false);
            instance.refreshStatus();
        });

        UIElement scaleRow = tripletRow(built, "backutils.effect_tool.label.scale", built.scale);
        UIElement rotationRow = tripletRow(built, "backutils.effect_tool.label.rotation",
                built.rotation);
        UIElement offsetRow = tripletRow(built, "backutils.effect_tool.label.offset",
                built.offset);

        built.forceDeath = toggle(value -> {
            if (instance != null) instance.refreshStatus();
        });
        built.delay = field(62);

        built.status = new Label();
        built.status.setText("", false);
        built.status.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        built.status.layout(l -> l.widthPercent(100).height(12));

        built.apply = new Button();
        translated(built, built.apply.text, "backutils.effect_tool.button.apply");
        built.apply.layout(l -> l.width(72).height(14));
        built.apply.setOnClick(e -> {
            if (instance != null) instance.apply();
        });

        Button close = new Button();
        translated(built, close.text, "backutils.effect_tool.button.close");
        close.layout(l -> l.width(72).height(14));
        close.setOnClick(e -> EffectToolScreen.close());

        UIElement buttons = new UIElement();
        buttons.layout(l -> l.widthPercent(100).height(ROW)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(GAP));
        buttons.addChildren(built.apply, close);

        built.panel = new UIElement();
        built.panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        built.panel.layout(l -> l.width(PANEL_WIDTH).height(panelHeight())
                .paddingAll(PAD).flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        built.panel.addChildren(title,
                labelled(built, "backutils.effect_tool.label.mode", built.mode),
                labelled(built, "backutils.effect_tool.label.auto_rotate", built.autoRotate),
                switchRow(built, "backutils.effect_tool.label.lifetime", built.lifetime,
                        built.infinite),
                switchRow(built, "backutils.effect_tool.label.distance", built.distance, built.reach),
                pairRow(built, "backutils.effect_tool.label.path", built.path, built.effects),
                scaleRow, rotationRow, offsetRow,
                labelled(built, "backutils.effect_tool.label.force_death", built.forceDeath),
                labelled(built, "backutils.effect_tool.label.delay", built.delay),
                built.status, buttons);

        built.root = new UIElement();
        built.root.layout(l -> l.flexDirection(FlexDirection.COLUMN)
                .justifyContent(AlignContent.CENTER)
                .alignItems(AlignItems.CENTER));
        built.root.addChild(built.panel);
    }

    /** {@return the height of the panel}, as the sum of the rows it shows */
    private static int panelHeight() {
        int rows = 14            // title
                + 8 * ROW        // mode, auto-rotate, lifetime, distance, path, scale, rotation,
                                 // offset
                + 2 * ROW        // force death, delay
                + 12             // status
                + ROW;           // buttons
        return PAD * 2 + rows + GAP * 12;
    }

    /** {@return a text box of the given width}, or of whatever the row has left at zero */
    private static TextField field(int width) {
        TextField field = new TextField();
        field.setText("", false);
        if (width > 0) {
            field.layout(l -> l.width(width).height(14));
        } else {
            field.layout(l -> l.flex(1).height(14));
        }
        field.setTextResponder(value -> {
            if (instance != null) instance.refreshStatus();
        });
        return field;
    }

    private static Toggle toggle(Consumer<Boolean> apply) {
        Toggle toggle = new Toggle();
        toggle.setOn(false, false);
        toggle.layout(l -> l.width(SWITCH_WIDTH).height(12));
        toggle.setText("", false);
        toggle.setOnToggleChanged(apply::accept);
        return toggle;
    }

    /** {@return one row of a caption and a control beside it} */
    private static UIElement labelled(Built built, String key, UIElement control) {
        return controlRow(built, key, control, new UIElement().layout(l -> l.flex(1)));
    }

    /** {@return a row whose control is followed by a second control}, used by the path box */
    private static UIElement pairRow(Built built, String key, UIElement control,
                                     UIElement trailing) {
        return controlRow(built, key, control, trailing);
    }

    /**
     * {@return a row of a caption, a number box and the switch that decides whether it means anything}
     *
     * <p>The box keeps its place whether or not it is being used. The two rows this is for — the
     * lifetime and the distance — each have a switch that says the number beside it is not the answer,
     * and the number was taken out of the row when that switch was on. A control that goes away takes
     * its width with it and pulls the switch back against the caption, so the switch read as part of
     * the caption rather than as part of the row, and the two rows' switches stood in different
     * columns depending on which of them was switched on.
     *
     * <p>Holding the boxes instead of the fields is what fixes both: the widths no longer depend on
     * which controls are showing, so the switches stand in one column in every combination, and a
     * caption is never the thing a switch is next to.
     */
    private static UIElement switchRow(Built built, String key, TextField box, Toggle toggle) {
        return controlRow(built, key, holding(box, SWITCH_BOX_WIDTH), holding(toggle, SWITCH_WIDTH));
    }

    /** {@return a fixed-width cell around a control}, so a row's columns do not move with it */
    private static UIElement holding(UIElement control, int width) {
        UIElement cell = new UIElement();
        cell.layout(l -> l.width(width).height(ROW));
        cell.addChild(control);
        return cell;
    }

    /** {@return the three boxes of a scale, a rotation or an offset} */
    private static UIElement tripletRow(Built built, String key, TextField[] boxes) {
        UIElement row = new UIElement();
        row.layout(l -> l.widthPercent(100).height(ROW)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(2));

        Label label = new Label();
        translated(built, label, key);
        label.textStyle(t -> t.fontSize(9f).textColor(PLAIN).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        label.layout(l -> l.width(LABEL_WIDTH).height(ROW));
        row.addChild(label);

        String[] axes = {"x", "y", "z"};
        for (int i = 0; i < 3; i++) {
            Label axis = new Label();
            // Not a translation: the axis of a coordinate is the same letter in every language, and
            // an item's own name is built from the tooltip keys rather than from these.
            axis.setText(axes[i], false);
            axis.textStyle(t -> t.fontSize(8f).textColor(MUTED).textShadow(false)
                    .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
            axis.layout(l -> l.width(AXIS_WIDTH).height(ROW));

            boxes[i] = new TextField();
            boxes[i].setText("0", false);
            boxes[i].layout(l -> l.flex(1).height(14));
            boxes[i].setTextResponder(value -> {
                if (instance != null) instance.refreshStatus();
            });
            row.addChildren(axis, boxes[i]);
        }
        return row;
    }

    /** {@return one row of a caption, a control and whatever follows it} */
    private static UIElement controlRow(Built built, String key, UIElement control,
                                        UIElement trailing) {
        Label label = new Label();
        translated(built, label, key);
        label.textStyle(t -> t.fontSize(9f).textColor(PLAIN).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
        label.layout(l -> l.width(LABEL_WIDTH).height(ROW));

        UIElement row = new UIElement();
        row.layout(l -> l.widthPercent(100).height(ROW)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(GAP));
        row.addChildren(label, control, trailing);
        return row;
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

    private static void applyTexts(Built built) {
        for (Built.Caption caption : built.captions) {
            caption.element().setText(Text.of(caption.key()), false);
        }
    }

    // ------------------------------------------------------------------
    // Reading and writing the tool
    // ------------------------------------------------------------------

    /**
     * {@return the tool this screen is about}, which is the one that was hovered when it was opened
     *
     * <p>While the address still names a tool, that stack is the answer and is remembered: a tool the server
     * has just written to, or one that was moved into another slot, is the tool the operator is looking at, and
     * the boxes follow it. When the address names nothing — the container it came from is long closed — the
     * tool that was hovered is still the tool this screen is about, and forgetting it is what a screen that
     * says "No effect tool is held." was doing.
     */
    private ItemStack tool() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return ItemStack.EMPTY;

        ItemStack live = EffectToolSlot.stackAt(player, source);
        if (live.getItem() instanceof EffectToolItem) {
            opened = live;
            return live;
        }
        return opened;
    }

    /** Writes the tool's values into the boxes, or says why there is nothing to write. */
    private void load() {
        ItemStack stack = tool();
        if (stack.isEmpty()) {
            built.apply.setActive(false);
            // The controls stay where they are, showing what they last held. They used to be hidden
            // outright, and that is the wrong failure: a screen with every field gone, one red line
            // and a live-looking Apply reads as a broken screen rather than as a slot with no tool in
            // it, and an operator cannot tell the two apart. Apply is what is taken away, because
            // that is the only part of this that could have changed anything.
            setStatus(Text.of("backutils.effect_tool.status.no_tool"), WARNING);
            return;
        }

        built.apply.setActive(true);
        showFields();

        EffectToolConfig config = EffectToolItem.configOf(stack);
        chosenMode = config.mode();
        built.mode.setSelected(config.mode().key(), false);
        chosenAutoRotate = config.autoRotate();
        built.autoRotate.setSelected(config.autoRotate().key(), false);

        built.infinite.setOn(config.isInfinite(), false);
        built.lifetime.setDisplay(!config.isInfinite());
        built.lifetime.setText(String.valueOf(config.lifetimeTicks()), false);

        built.reach.setOn(config.usesPlayerReach(), false);
        built.distance.setDisplay(!config.usesPlayerReach());
        built.distance.setText(String.valueOf(config.maxDistance()), false);

        built.path.setText(config.effectPath(), false);
        writeTriplet(built.scale, config.scale());
        writeTriplet(built.rotation, config.rotation());
        writeTriplet(built.offset, config.offset());
        built.forceDeath.setOn(config.forceDeath(), false);
        built.delay.setText(String.valueOf(config.delayTicks()), false);

        refreshStatus();
    }

    private static void writeTriplet(TextField[] boxes, EffectToolConfig.Triplet triplet) {
        String[] values = triplet.asText().split(" ");
        for (int i = 0; i < boxes.length; i++) {
            boxes[i].setText(i < values.length ? values[i] : "0", false);
        }
    }

    /** {@return the values in one row of boxes}, taking what each box holds as it stands */
    private static EffectToolConfig.Triplet readTriplet(TextField[] boxes, boolean[] kept) {
        return new EffectToolConfig.Triplet(
                number(boxes[0].getText(), kept),
                number(boxes[1].getText(), kept),
                number(boxes[2].getText(), kept));
    }

    /** {@return true when a switch is on}, a switch being able to hold no value before it is set */
    private static boolean on(Toggle toggle) {
        return Boolean.TRUE.equals(toggle.getValue());
    }

    /**
     * {@return the number a box holds}, or zero while it is mid-typing or empty
     *
     * <p>An unreadable box is one the player is still writing in, so it is reported to the caller
     * rather than quietly becoming zero.
     */
    private static double number(String text, boolean[] kept) {
        if (text == null || text.isBlank()) {
            kept[0] = true;
            return 0.0D;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException midTyping) {
            kept[0] = true;
            return 0.0D;
        }
    }

    /** {@return the whole number a box holds}, or the fallback while it is mid-typing or empty */
    private static int whole(String text, int fallback, boolean[] kept) {
        if (text == null || text.isBlank()) {
            kept[0] = true;
            return fallback;
        }
        try {
            return (int) Math.round(Double.parseDouble(text.trim()));
        } catch (NumberFormatException midTyping) {
            kept[0] = true;
            return fallback;
        }
    }

    /**
     * Sends every box to the server, clamped.
     *
     * <p>The tool's own stored values are read first, so a field that cannot be read keeps what the
     * tool already had rather than being zeroed by a stray character.
     *
     * <p>Nothing is written here: the tool this screen holds is the client's copy of the server's,
     * and a value written into it would be gone at the next sync of the slot. The server is asked to
     * store the edit, and the answer replaces the status line when it arrives.
     */
    private void apply() {
        ItemStack stack = tool();
        if (stack.isEmpty()) {
            load();
            return;
        }

        EffectToolConfig old = EffectToolItem.configOf(stack);
        // Boxes the player is part-way through typing in are not numbers yet: they keep what the
        // tool had, and the status line says so rather than reporting a change that did not happen.
        boolean[] kept = {false};
        // The lifetime and the distance are sentinels rather than text: their boxes are hidden
        // while the switch beside them is on, so the switch is the value.
        int lifetime = on(built.infinite)
                ? EffectToolConfig.INFINITE_LIFETIME
                : whole(built.lifetime.getText(), old.lifetimeTicks(), kept);
        int distance = on(built.reach)
                ? EffectToolConfig.PLAYER_REACH
                : whole(built.distance.getText(), old.maxDistance(), kept);

        EffectToolConfig wanted = EffectToolConfig.of(
                chosenMode,
                chosenAutoRotate,
                lifetime,
                distance,
                built.path.getText(),
                readTriplet(built.scale, kept),
                readTriplet(built.rotation, kept),
                readTriplet(built.offset, kept),
                on(built.forceDeath),
                whole(built.delay.getText(), old.delayTicks(), kept));

        if (source == null) {
            setStatus(Text.of("backutils.effect_tool.status.not_here"), WARNING);
            return;
        }

        waitingForAnswer = true;
        askedAt = System.nanoTime();
        setStatus(kept[0] ? Text.of("backutils.effect_tool.status.kept")
                : Text.of("backutils.effect_tool.status.sent"), kept[0] ? WARNING : MUTED);

        try {
            EffectToolNetwork.send(new EffectToolConfigPayload(source.where(), source.containerId(),
                    source.slot(), wanted));
        } catch (RuntimeException unreachable) {
            // A server that never negotiated this channel, or none at all: nothing was asked of it,
            // so the screen must not sit waiting for an answer to a question never sent.
            waitingForAnswer = false;
            setStatus(Text.of("backutils.effect_tool.status.unreachable"), WARNING);
        }
    }

    /**
     * Takes the server's answer to an edit.
     *
     * <p>Static because the answer arrives for the screen rather than from it: an answer that
     * outlives the screen it was asked from is one nobody is waiting to read.
     */
    public static void onServerAnswer(EffectToolFeedbackPayload.Reason reason) {
        EffectToolScreen screen = instance;
        if (screen != null) screen.handleAnswer(reason);
    }

    /** Brings the boxes in step with the tool, then says what the server did with the edit. */
    private void handleAnswer(EffectToolFeedbackPayload.Reason reason) {
        waitingForAnswer = false;
        // The server sends the slot before its answer, so a reload here reads what it stored. The
        // status goes on after the reload, which has a status of its own; a slot that holds no tool
        // has already been reported by the reload, and that report is the more useful one.
        boolean present = !tool().isEmpty();
        load();
        if (present) setStatus(Text.of(reason.key()), reason.stored() ? GOOD : WARNING);
    }

    /** {@return every box the screen owns}, so a missing tool can hide them all at once */
    private List<TextField> allFields() {
        List<TextField> fields = new ArrayList<>();
        fields.add(built.lifetime);
        fields.add(built.distance);
        fields.add(built.path);
        fields.addAll(List.of(built.scale));
        fields.addAll(List.of(built.rotation));
        fields.addAll(List.of(built.offset));
        fields.add(built.delay);
        return fields;
    }

    /**
     * Puts everything back where a tool's settings go.
     *
     * <p>Only ever asked for, never the reverse: the controls are built visible and stay that way, so
     * a screen looking at nothing still shows what it last held rather than a blank panel. This is the
     * one place the set is assembled, which is what keeps a control added later from being the one
     * that is forgotten here.
     */
    private void showFields() {
        for (TextField box : allFields()) box.setDisplay(true);
        built.mode.setDisplay(true);
        built.autoRotate.setDisplay(true);
        // The list is a way of filling the path box in, so it goes with the boxes: refreshEffects is
        // what decides whether this client has any effects to offer at all.
        built.effects.setDisplay(!built.effects.getCandidates().isEmpty());
    }

    /**
     * Offers the effects this client has beside the path box.
     *
     * <p>The tool's stored path becomes the dropdown's own selection when the list is built, so the
     * list does not open showing one effect while the box holds another. A path typed by hand
     * afterwards joins the candidates for as long as it is held, so the list never shows an effect
     * the box could not be set back to — an entry the dropdown already shows is one that cannot be
     * picked again.
     *
     * <p>A tool that names no effect yet is offered the first one this client has. The path is the
     * one setting a tool cannot work without, so the box is filled in rather than left empty beside a
     * list: what is written is visible in the box, and Apply is still the operator's to press.
     */
    private void refreshEffects() {
        List<String> candidates = new ArrayList<>();
        for (ResourceLocation id : PhotonFx.listEffects()) {
            candidates.add(id.toString());
        }
        if (candidates.isEmpty() || tool().isEmpty()) {
            built.effects.setDisplay(false);
            return;
        }

        String current = built.path.getText();
        if (current == null || current.isBlank()) {
            current = candidates.get(0);
            built.path.setText(current, false);
        } else if (!candidates.contains(current)) {
            candidates.add(0, current);
        }

        built.effects.setDisplay(true);
        built.effects.setCandidates(candidates);
        built.effects.setSelected(current, false);
    }

    private void refreshStatus() {
        if (tool().isEmpty()) {
            setStatus(Text.of("backutils.effect_tool.status.no_tool"), WARNING);
            return;
        }
        if (source == null) {
            // The tool is known and its settings are on screen, and there is nowhere to write them: that is the
            // creative screen's item list, which holds pictures of tools rather than tools the server keeps.
            setStatus(Text.of("backutils.effect_tool.status.not_here"), WARNING);
            return;
        }
        setStatus(chosenMode == EffectToolConfig.Mode.NONE
                ? Text.of("backutils.effect_tool.status.choose_mode")
                : Text.of("backutils.effect_tool.status.ready"), MUTED);
    }

    private void setStatus(String text, int colour) {
        built.status.setText(text, false);
        built.status.textStyle(t -> t.fontSize(9f).textColor(colour).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
    }

    // ------------------------------------------------------------------
    // Screen
    // ------------------------------------------------------------------

    /** Closes the screen without going through the inventory again. */
    private static void close() {
        EffectToolScreen screen = instance;
        if (screen == null) return;
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void init() {
        applyTexts(built);
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(200, this.width - 20));
        int panelHeight = Math.min(panelHeight(), Math.max(120, this.height - 20));

        built.root.layout(l -> l.width(this.width).height(this.height)
                .flexDirection(FlexDirection.COLUMN)
                .justifyContent(AlignContent.CENTER)
                .alignItems(AlignItems.CENTER));
        built.panel.layout(l -> l.width(panelWidth).height(panelHeight).paddingAll(PAD)
                .flexDirection(FlexDirection.COLUMN).gapAll(GAP));

        built.ui.setScreenAndInit(this);
        addRenderableWidget(built.ui.getWidget());
        super.init();
        setFocused(built.ui.getWidget());

        load();
        // After the load, so the list is filled in from the tool as it stands rather than from
        // whatever the boxes held when the screen was last open.
        refreshEffects();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Paints the dim behind the panel and nothing else, so the blurred world never shows. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int alpha = (int) Math.round(Math.max(0.0D, Math.min(1.0D,
                BackUtilsClientConfig.getMenuBackgroundOpacity())) * 255.0D);
        graphics.fill(0, 0, this.width, this.height, (alpha << 24) | 0x00101016);
    }

    /**
     * The tool can be moved while this is open, so the boxes follow it once a frame at most, and an
     * edit that is never answered stops being reported as sent.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (waitingForAnswer && System.nanoTime() - askedAt > ANSWER_NANOS) {
            waitingForAnswer = false;
            setStatus(Text.of("backutils.effect_tool.status.no_answer"), WARNING);
        }
        if (tool().isEmpty() && built.apply.isActive()) load();
    }
}
