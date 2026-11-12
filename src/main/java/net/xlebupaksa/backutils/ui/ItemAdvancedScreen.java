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
import com.lowdragmc.lowdraglib2.gui.ui.elements.ColorSelector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.style.LayoutStyle;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.item.AdvancedItemProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The item editor's Advanced screen: Properties, with Behaviour kept as the second tab to come.
 *
 * <p>The screen edits the draft held by the main editor rather than the item. Back returns to that
 * editor with the draft in place, and the main Apply is the one press that writes the item, so a
 * name or a lore line that has not been applied yet is not thrown away by coming here.
 *
 * <p>Every group in Properties is closed until its box is ticked. A closed group owns nothing: its
 * components are removed by the main Apply, which is what lets an operator turn a whole property
 * off rather than only edit it. The Misc row is the exception — a maximum stack size is a plain
 * number, not a thing that can be absent — so it is always visible.
 */
@OnlyIn(Dist.CLIENT)
public class ItemAdvancedScreen extends Screen {

    private static final int PAD = 10;
    private static final int GAP = 5;
    private static final int LINE = 14;
    private static final int ROW = 16;
    private static final int LABEL_WIDTH = 120;
    private static final int NUMBER_WIDTH = 52;
    private static final int PANEL_BG = 0xF0101016;
    private static final int MUTED = 0xFF9AA3B2;
    private static final int PLAIN = 0xFFE2E8F0;

    private final ItemEditorScreen parent;
    private final AdvancedItemProperties properties;

    private final UIElement root = new UIElement();
    private final UIElement panel = new UIElement();
    private final UIElement content = new UIElement();
    private final UIElement tabRow = new UIElement();
    private final Button propertiesTab = new Button();
    private final Button behaviourTab = new Button();
    private ScrollerView scroller;

    private boolean behaviour;
    private ModularUI ui;

    private ItemAdvancedScreen(ItemEditorScreen parent, AdvancedItemProperties properties) {
        super(Component.translatable("backutils.item_editor.advanced.title"));
        this.parent = parent;
        this.properties = properties == null ? new AdvancedItemProperties() : properties;
        build();
    }

    /** Opens this screen on the draft the main editor is holding. */
    public static void open(ItemEditorScreen parent, AdvancedItemProperties properties) {
        Minecraft.getInstance().setScreen(new ItemAdvancedScreen(parent, properties));
    }

    @Override
    public void init() {
        fill();
        ui.setScreenAndInit(this);
        addRenderableWidget(ui.getWidget());
        super.init();
        setFocused(ui.getWidget());
    }

    private void build() {
        Button back = new Button();
        text(back.text, "backutils.item_editor.button.back", 9f);
        back.layout(l -> l.width(82).height(LINE));
        back.setOnClick(e -> leave());

        Label title = new Label();
        title.setText(Component.translatable("backutils.item_editor.advanced.title"));
        title.textStyle(t -> t.fontSize(12f).textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER));
        title.layout(l -> l.flex(1).height(ROW));

        UIElement titleRow = new UIElement();
        titleRow.layout(l -> l.widthPercent(100).height(LINE)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(GAP));
        titleRow.addChildren(back, title);

        propertiesTab.layout(l -> l.width(112).height(LINE));
        propertiesTab.setOnClick(e -> {
            behaviour = false;
            rebuild();
        });
        behaviourTab.layout(l -> l.width(112).height(LINE));
        behaviourTab.setOnClick(e -> {
            behaviour = true;
            rebuild();
        });
        tabRow.layout(l -> l.widthPercent(100).height(LINE)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(GAP));
        tabRow.addChildren(propertiesTab, behaviourTab);

        content.layout(l -> l.widthPercent(100).heightAuto()
                .flexDirection(FlexDirection.COLUMN).gapAll(3));

        ScrollerView scroller = new ScrollerView();
        this.scroller = scroller;
        scroller.layout(l -> l.widthPercent(100).flex(1).minHeight(0));
        scroller.viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        scroller.viewContainer.layout(l -> l.widthPercent(100).heightAuto()
                .flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        scroller.viewContainer.addChild(content);

        Label hint = new Label();
        hint.setText(Component.translatable("backutils.item_editor.advanced.hint"));
        hint.textStyle(t -> t.fontSize(8f).textColor(MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER));
        hint.layout(l -> l.widthPercent(100).height(12));

        panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        panel.addChildren(titleRow, tabRow, scroller, hint);
        root.addChild(panel);
        ui = ModularUI.of(UI.of(root));
        rebuild();
    }

    private void fill() {
        root.layout(l -> l.width(this.width).height(this.height)
                .flexDirection(FlexDirection.COLUMN));
        panel.layout(l -> l.width(this.width).height(this.height).paddingAll(PAD)
                .flexDirection(FlexDirection.COLUMN).gapAll(GAP));
    }

    @Override
    public void onClose() {
        leave();
    }

    private void leave() {
        Minecraft.getInstance().setScreen(parent.reopen(properties));
    }

    /** Rebuilds the tab labels and the visible group list. */
    private void rebuild() {
        updateTab(propertiesTab, "backutils.item_editor.tab.properties", !behaviour);
        updateTab(behaviourTab, "backutils.item_editor.tab.behaviour", behaviour);

        content.clearAllChildren();
        if (behaviour) {
            Label label = new Label();
            label.setText(Component.translatable("backutils.item_editor.behaviour.placeholder"));
            label.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false));
            label.layout(l -> l.widthPercent(100).height(ROW));
            content.addChild(label);
            relayout();
            return;
        }

        addGroup("backutils.item_editor.group.consumable", properties.consumable,
                () -> {
                    properties.consumable = !properties.consumable;
                    rebuild();
                }, this::consumableBody);

        addGroup("backutils.item_editor.group.durability", properties.durability,
                () -> {
                    properties.durability = !properties.durability;
                    if (properties.durability && properties.maxStackSize > 1) {
                        properties.maxStackSize = 1;
                    }
                    rebuild();
                }, this::durabilityBody);

        addGroup("backutils.item_editor.group.dyed", properties.dyed,
                () -> {
                    properties.dyed = !properties.dyed;
                    rebuild();
                }, this::dyedBody);

        addGroup("backutils.item_editor.group.enchantments", properties.enchantments,
                () -> {
                    properties.enchantments = !properties.enchantments;
                    rebuild();
                }, this::enchantmentsBody);

        addGroup("backutils.item_editor.group.misc", properties.misc,
                () -> {
                    properties.misc = !properties.misc;
                    rebuild();
                }, this::miscBody);
        relayout();
    }

    /**
     * Makes the scroller measure the content again after a group opened or closed.
     *
     * <p>A group's body is added after the first layout, and a scroller whose view container has not
     * been marked dirty keeps the height it measured before the body existed — the body is drawn but
     * cannot be scrolled to. Marking the content and the container is what tells the scroller there
     * is more of it now.
     */
    private void relayout() {
        content.markTaffyStyleDirty();
        if (scroller == null) return;
        scroller.markTaffyStyleDirty();
        scroller.viewContainer.markTaffyStyleDirty();
    }

    private void updateTab(Button tab, String key, boolean selected) {
        tab.setText(Component.literal((selected ? "» " : "") + Text.of(key)));
        tab.text.textStyle(t -> t.fontSize(9f).textShadow(false)
                .textColor(selected ? PLAIN : MUTED)
                .textAlignVertical(Vertical.CENTER));
    }

    private void addGroup(String key, boolean open, Runnable toggle, Body body) {
        UIElement block = new UIElement();
        block.layout(l -> l.widthPercent(100).heightAuto()
                .flexDirection(FlexDirection.COLUMN).gapAll(2));

        Button header = new Button();
        header.layout(l -> l.widthPercent(100).height(18));
        header.setText(Component.literal((open ? "[x] " : "[ ] ") + Text.of(key)));
        header.text.textStyle(t -> t.fontSize(9f).textShadow(false)
                .textColor(PLAIN).textAlignVertical(Vertical.CENTER));
        header.setOnClick(e -> toggle.run());

        block.addChild(header);
        if (open) block.addChild(body.build());
        content.addChild(block);
    }

    @FunctionalInterface
    private interface Body {
        UIElement build();
    }

    private UIElement body() {
        UIElement element = new UIElement();
        element.layout(l -> l.widthPercent(100).heightAuto().paddingLeft(18)
                .flexDirection(FlexDirection.COLUMN).gapAll(2));
        return element;
    }

    // ------------------------------------------------------------------
    // Properties
    // ------------------------------------------------------------------

    private UIElement consumableBody() {
        UIElement body = body();

        UIElement first = new UIElement();
        first.layout(rowLayout());
        first.addChildren(caption("backutils.item_editor.label.nutrition", LABEL_WIDTH),
                intField(properties.nutrition, NUMBER_WIDTH,
                        value -> properties.nutrition = Math.max(0, value)),
                caption("backutils.item_editor.label.saturation", LABEL_WIDTH),
                floatField(properties.saturation, NUMBER_WIDTH,
                        value -> properties.saturation = Math.max(0.0f, value)));
        body.addChild(first);

        UIElement second = new UIElement();
        second.layout(rowLayout());
        second.addChildren(check("backutils.item_editor.label.can_always_eat",
                        properties.canAlwaysEat, () -> {
                            properties.canAlwaysEat = !properties.canAlwaysEat;
                            rebuild();
                        }),
                caption("backutils.item_editor.label.consume_seconds", LABEL_WIDTH),
                floatField(properties.eatSeconds, NUMBER_WIDTH,
                        value -> properties.eatSeconds = value));
        body.addChild(second);

        UIElement effectsHeader = new UIElement();
        effectsHeader.layout(rowLayout());
        Label effects = caption("backutils.item_editor.label.effects", 150);
        Button add = new Button();
        text(add.text, "backutils.item_editor.button.add_effect", 9f);
        add.layout(l -> l.width(96).height(LINE));
        add.setOnClick(e -> {
            addEffect();
            rebuild();
        });
        UIElement spacer = new UIElement();
        spacer.layout(l -> l.flex(1));
        effectsHeader.addChildren(effects, add, spacer);
        body.addChild(effectsHeader);

        for (int i = 0; i < properties.effects.size(); i++) {
            body.addChild(effectRow(i));
        }
        return body;
    }

    private UIElement effectRow(int index) {
        AdvancedItemProperties.Effect effect = properties.effects.get(index);
        UIElement block = new UIElement();
        block.layout(l -> l.widthPercent(100).heightAuto()
                .flexDirection(FlexDirection.COLUMN).gapAll(1));

        Selector<String> which = new Selector<>();
        which.layout(l -> l.flex(1).height(LINE));
        which.setCandidates(effectIds());
        which.setValue(effect.id(), false);
        // Every handler reads the effect from the draft at the moment it runs. A handler that kept
        // this row's first Effect would write its old id and flags back when a later field changed,
        // which is how changing one flag could turn the row back into whatever it started as.
        which.setOnValueChanged(id -> {
            AdvancedItemProperties.Effect current = effectAt(index);
            if (current != null) setEffect(index, new AdvancedItemProperties.Effect(
                    id, current.durationTicks(), current.amplifier(), current.ambient(),
                    current.visible(), current.showIcon()));
        });

        TextField seconds = floatField(effect.durationTicks() / 20.0f, NUMBER_WIDTH,
                value -> {
                    AdvancedItemProperties.Effect current = effectAt(index);
                    if (current != null) setEffect(index, new AdvancedItemProperties.Effect(
                            current.id(), Math.max(1, Math.round(value * 20.0f)),
                            current.amplifier(), current.ambient(), current.visible(),
                            current.showIcon()));
                });
        TextField level = intField(effect.amplifier() + 1, 42,
                value -> {
                    AdvancedItemProperties.Effect current = effectAt(index);
                    if (current != null) setEffect(index, new AdvancedItemProperties.Effect(
                            current.id(), current.durationTicks(), Math.max(0, value - 1),
                            current.ambient(), current.visible(), current.showIcon()));
                });

        Button remove = new Button();
        text(remove.text, "backutils.item_editor.button.remove", 9f);
        remove.layout(l -> l.width(32).height(LINE));
        remove.setOnClick(e -> {
            if (index < properties.effects.size()) properties.effects.remove(index);
            rebuild();
        });

        UIElement row = new UIElement();
        row.layout(rowLayout());
        row.addChildren(which, seconds,
                caption("backutils.item_editor.label.seconds_short", 18), level,
                caption("backutils.item_editor.label.level_short", 26), remove);
        block.addChild(row);

        String ambientKey = "backutils.item_editor.label.ambient";
        String particlesKey = "backutils.item_editor.label.particles";
        String iconKey = "backutils.item_editor.label.icon";

        Button ambient = check(ambientKey, effect.ambient(), () -> {});
        ambient.setOnClick(e -> {
            AdvancedItemProperties.Effect current = effectAt(index);
            if (current == null) return;
            boolean now = !current.ambient();
            setEffect(index, new AdvancedItemProperties.Effect(current.id(),
                    current.durationTicks(), current.amplifier(), now,
                    current.visible(), current.showIcon()));
            setCheck(ambient, ambientKey, now);
        });

        Button particles = check(particlesKey, effect.visible(), () -> {});
        particles.setOnClick(e -> {
            AdvancedItemProperties.Effect current = effectAt(index);
            if (current == null) return;
            boolean now = !current.visible();
            setEffect(index, new AdvancedItemProperties.Effect(current.id(),
                    current.durationTicks(), current.amplifier(), current.ambient(),
                    now, current.showIcon()));
            setCheck(particles, particlesKey, now);
        });

        Button icon = check(iconKey, effect.showIcon(), () -> {});
        icon.setOnClick(e -> {
            AdvancedItemProperties.Effect current = effectAt(index);
            if (current == null) return;
            boolean now = !current.showIcon();
            setEffect(index, new AdvancedItemProperties.Effect(current.id(),
                    current.durationTicks(), current.amplifier(), current.ambient(),
                    current.visible(), now));
            setCheck(icon, iconKey, now);
        });

        UIElement flags = new UIElement();
        flags.layout(rowLayout());
        flags.addChildren(ambient, particles, icon);
        block.addChild(flags);
        return block;
    }

    private AdvancedItemProperties.Effect effectAt(int index) {
        return index >= 0 && index < properties.effects.size()
                ? properties.effects.get(index) : null;
    }

    private void setEffect(int index, AdvancedItemProperties.Effect effect) {
        if (index >= 0 && index < properties.effects.size()) {
            properties.effects.set(index, effect);
        }
    }

    private void addEffect() {
        if (properties.effects.size() >= 32) return;
        List<String> used = new ArrayList<>();
        for (AdvancedItemProperties.Effect effect : properties.effects) used.add(effect.id());
        List<String> candidates = effectIds();
        String next = candidates.stream().filter(id -> !used.contains(id)).findFirst()
                .orElse(candidates.isEmpty() ? "" : candidates.getFirst());
        if (next.isEmpty()) return;
        properties.effects.add(new AdvancedItemProperties.Effect(next, 200, 0,
                false, true, true));
    }

    private UIElement durabilityBody() {
        UIElement body = body();
        UIElement first = new UIElement();
        first.layout(rowLayout());
        first.addChildren(
                caption("backutils.item_editor.label.current_durability", LABEL_WIDTH),
                intField(properties.currentDurability, NUMBER_WIDTH,
                        value -> properties.currentDurability = Math.max(0, value)),
                caption("backutils.item_editor.label.max_durability", LABEL_WIDTH),
                intField(properties.maxDamage, NUMBER_WIDTH,
                        value -> properties.maxDamage = Math.max(1, value)));
        body.addChild(first);
        body.addChild(check("backutils.item_editor.label.unbreakable", properties.unbreakable, () -> {
            properties.unbreakable = !properties.unbreakable;
            rebuild();
        }));
        return body;
    }

    private UIElement dyedBody() {
        UIElement body = body();
        body.addChild(caption("backutils.item_editor.label.dyed_color", LABEL_WIDTH));

        ColorSelector picker = new ColorSelector();
        picker.layout(l -> l.width(120).height(144));
        picker.alphaSlider.setVisible(false);
        picker.alphaSlider.layout(l -> l.display(TaffyDisplay.NONE));
        // The rows under the hex field are the alpha/r/g/b fields, and the alpha bar is already off.
        // The item marker's colour is six-digit RGB; keeping the hex field and the square is enough,
        // and the rows would otherwise be drawn over the next group.
        List<UIElement> pickerRows = picker.textContainer.getChildren();
        for (int i = 1; i < pickerRows.size(); i++) {
            UIElement extra = pickerRows.get(i);
            extra.setVisible(false);
            extra.layout(l -> l.display(TaffyDisplay.NONE));
        }
        picker.setColor(0xFF000000 | (properties.dyedColor & 0xFFFFFF), false);
        picker.registerValueListener(value -> properties.dyedColor = value & 0xFFFFFF);

        // The picker's own layout measures its preview from its parent's height. In a scroller a
        // picker added straight to a height-auto column can be measured as its text rows alone, so
        // the next group is laid over it; the fixed holder gives it the room it draws into.
        UIElement holder = new UIElement();
        holder.layout(l -> l.width(120).height(144));
        holder.addChild(picker);
        body.addChild(holder);
        return body;
    }

    private UIElement enchantmentsBody() {
        UIElement body = body();

        UIElement header = new UIElement();
        header.layout(rowLayout());
        Button add = new Button();
        text(add.text, "backutils.item_editor.button.add_enchantment", 9f);
        add.layout(l -> l.width(116).height(LINE));
        add.setOnClick(e -> {
            addEnchantment();
            rebuild();
        });
        UIElement spacer = new UIElement();
        spacer.layout(l -> l.flex(1));
        header.addChildren(caption("backutils.item_editor.label.enchantments", 100), add, spacer);
        body.addChild(header);

        for (int i = 0; i < properties.enchantmentLevels.size(); i++) {
            body.addChild(enchantmentRow(i));
        }
        return body;
    }

    private UIElement enchantmentRow(int index) {
        AdvancedItemProperties.EnchantmentLevel line = properties.enchantmentLevels.get(index);

        Selector<String> which = new Selector<>();
        which.layout(l -> l.flex(1).height(LINE));
        which.setCandidates(enchantmentIds());
        which.setValue(line.id(), false);
        which.setOnValueChanged(id -> setEnchantment(index,
                new AdvancedItemProperties.EnchantmentLevel(id, line.level())));

        TextField level = intField(line.level(), 42,
                value -> setEnchantment(index,
                        new AdvancedItemProperties.EnchantmentLevel(line.id(), value)));

        Button remove = new Button();
        text(remove.text, "backutils.item_editor.button.remove", 9f);
        remove.layout(l -> l.width(32).height(LINE));
        remove.setOnClick(e -> {
            if (index < properties.enchantmentLevels.size()) {
                properties.enchantmentLevels.remove(index);
            }
            rebuild();
        });

        UIElement row = new UIElement();
        row.layout(rowLayout());
        row.addChildren(which, level,
                caption("backutils.item_editor.label.level_short", 26), remove);
        return row;
    }

    private void setEnchantment(int index, AdvancedItemProperties.EnchantmentLevel line) {
        if (index >= 0 && index < properties.enchantmentLevels.size()) {
            properties.enchantmentLevels.set(index, line);
        }
    }

    private void addEnchantment() {
        if (properties.enchantmentLevels.size() >= 32) return;
        List<String> used = new ArrayList<>();
        for (AdvancedItemProperties.EnchantmentLevel line : properties.enchantmentLevels) {
            used.add(line.id());
        }
        List<String> candidates = enchantmentIds();
        String next = candidates.stream().filter(id -> !used.contains(id)).findFirst()
                .orElse(candidates.isEmpty() ? "" : candidates.getFirst());
        if (next.isEmpty()) return;
        properties.enchantmentLevels.add(new AdvancedItemProperties.EnchantmentLevel(next, 1));
    }

    private UIElement miscBody() {
        UIElement body = body();
        body.addChild(check("backutils.item_editor.label.fire_resistant", properties.fireResistant,
                () -> {
                    properties.fireResistant = !properties.fireResistant;
                    rebuild();
                }));
        body.addChild(check("backutils.item_editor.label.glint", properties.glint, () -> {
            properties.glint = !properties.glint;
            rebuild();
        }));
        body.addChild(check("backutils.item_editor.label.hide_enchants",
                properties.hideEnchants, () -> {
                    properties.hideEnchants = !properties.hideEnchants;
                    rebuild();
                }));

        UIElement stack = new UIElement();
        stack.layout(rowLayout());
        stack.addChildren(
                caption("backutils.item_editor.label.max_stack_size", LABEL_WIDTH),
                intField(properties.maxStackSize, NUMBER_WIDTH,
                        value -> properties.maxStackSize = Math.max(1, Math.min(99, value))));
        body.addChild(stack);
        return body;
    }

    // ------------------------------------------------------------------
    // Small controls
    // ------------------------------------------------------------------

    private static Consumer<LayoutStyle> rowLayout() {
        return l -> l.widthPercent(100).height(ROW)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .gapAll(GAP);
    }

    private static Label caption(String key, int width) {
        Label label = new Label();
        label.setText(Component.translatable(key));
        label.textStyle(t -> t.fontSize(9f).textShadow(false)
                .textColor(MUTED).textAlignVertical(Vertical.CENTER).adaptiveWidth(false));
        label.layout(l -> l.width(width).height(ROW));
        return label;
    }

    private static TextField intField(int value, int width, IntConsumer sink) {
        TextField field = new TextField();
        field.setText(String.valueOf(value), false);
        field.layout(l -> l.width(width).height(LINE));
        field.setTextResponder(text -> {
            try {
                sink.accept(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
                // Mid-typing: "-", "", and the like. Apply reads the last value that parsed.
            }
        });
        return field;
    }

    private static TextField floatField(float value, int width, Consumer<Float> sink) {
        TextField field = new TextField();
        field.setText(String.valueOf(value), false);
        field.layout(l -> l.width(width).height(LINE));
        field.setTextResponder(text -> {
            try {
                sink.accept(Float.parseFloat(text.trim()));
            } catch (NumberFormatException ignored) {
                // As above.
            }
        });
        return field;
    }

    private static Button check(String key, boolean on, Runnable action) {
        Button button = new Button();
        setCheck(button, key, on);
        button.textStyle(t -> t.fontSize(9f).textShadow(false)
                .textColor(PLAIN).textAlignVertical(Vertical.CENTER));
        button.layout(l -> l.width(136).height(LINE));
        button.setOnClick(e -> action.run());
        return button;
    }

    /** Rewrites one checkmark without rebuilding the row its effect fields live in. */
    private static void setCheck(Button button, String key, boolean on) {
        button.setText(Component.literal((on ? "[x] " : "[ ] ") + Text.of(key)));
    }

    private static void text(TextElement element, String key, float size) {
        element.setText(Text.of(key), false);
        element.textStyle(t -> t.fontSize(size).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
    }

    private static List<String> effectIds() {
        return BuiltInRegistries.MOB_EFFECT.keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
    }

    private static List<String> enchantmentIds() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        return minecraft.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).keySet()
                .stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
    }
}
