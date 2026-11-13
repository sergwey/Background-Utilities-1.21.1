package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.ui.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ColorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Inspector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.item.AdvancedItemProperties;
import net.xlebupaksa.backutils.item.ItemBehaviour;
import net.xlebupaksa.backutils.item.ItemEdit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The item editor's Advanced screen: Properties, and Behaviour as an ordered command list per trigger.
 *
 * <p>The screen edits the draft held by the main editor rather than the item. Back returns to that
 * editor with the draft in place, and the Apply at the top writes the same edit a main Apply would,
 * without having to go back first; the main Apply is still the one that remains on screen. A name or
 * a lore line that has not been applied yet is therefore not thrown away by coming here.
 *
 * <p>The property rows are LDLib2 configurators inside an {@link Inspector}, and the tabs are the
 * library's {@link TabView}. That is the same machinery Photon's inspector uses: a group heading
 * with LDLib2's own triangle and border, a toggle for the group, and one labelled row per value.
 * Keeping the rows in the library means the screen inherits its focus, scrolling, copy/paste and
 * popup behaviour instead of drawing a second, flatter copy of it.
 */
@OnlyIn(Dist.CLIENT)
public class ItemAdvancedScreen extends Screen {

    private static final int PAD = 8;
    private static final int LINE = 14;
    private static final int PANEL_BG = 0xF0101016;
    private static final int MUTED = 0xFF9AA3B2;
    private static final int PLAIN = 0xFFE2E8F0;

    private final ItemEditorScreen parent;
    private final AdvancedItemProperties properties;

    private final UIElement root = new UIElement();
    private final UIElement panel = new UIElement();
    private TabView tabs;
    private Tab propertiesTab;
    private Tab behaviourTab;
    private Inspector inspector;
    private Inspector behaviourInspector;
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
        title.textStyle(t -> t.fontSize(12f).textShadow(false).textColor(PLAIN)
                .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        title.layout(l -> l.flex(1).height(18));

        Button apply = new Button();
        text(apply.text, "backutils.item_editor.button.apply", 9f);
        apply.layout(l -> l.width(82).height(LINE));
        apply.setOnClick(e -> {
            if (parent != null) parent.applyFromAdvanced();
        });

        UIElement titleRow = new UIElement();
        titleRow.layout(l -> l.widthPercent(100).height(18).flexShrink(0)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(4));
        titleRow.addChildren(back, title, apply);

        // TabView draws its tab strip above its own content box, not inside that box. A fixed,
        // small strip keeps it from claiming a tall blank band above the properties.
        UIElement tabTop = new UIElement();
        tabTop.layout(l -> l.widthPercent(100).height(2).flexShrink(0));

        Inspector inspector = new Inspector();
        this.inspector = inspector;
        inspector.layout(l -> l.widthPercent(100).heightPercent(100));
        inspector.scrollerView.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .adaptiveWidth(false).adaptiveHeight(false));
        inspector.inspect(IConfigurable.create(this::buildProperties));

        Inspector behaviour = new Inspector();
        this.behaviourInspector = behaviour;
        behaviour.layout(l -> l.widthPercent(100).heightPercent(100));
        behaviour.scrollerView.scrollerStyle(s -> s.mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .adaptiveWidth(false).adaptiveHeight(false));
        behaviour.inspect(IConfigurable.create(this::buildBehaviour));

        tabs = new TabView();
        tabs.tabHeaderContainer.layout(l -> l.height(16).paddingAll(0));
        tabs.tabScroller.layout(l -> l.height(16).marginBottom(0));
        tabs.layout(l -> l.widthPercent(100).flex(1).minHeight(0));
        propertiesTab = new Tab().setText(Text.of("backutils.item_editor.tab.properties"), false);
        behaviourTab = new Tab().setText(Text.of("backutils.item_editor.tab.behaviour"), false);
        propertiesTab.textStyle(t -> t.textColor(PLAIN));
        behaviourTab.textStyle(t -> t.textColor(MUTED));
        propertiesTab.setOnTabSelected(() -> propertiesTab.textStyle(t -> t.textColor(PLAIN)));
        propertiesTab.setOnTabUnselected(() -> propertiesTab.textStyle(t -> t.textColor(MUTED)));
        behaviourTab.setOnTabSelected(() -> behaviourTab.textStyle(t -> t.textColor(PLAIN)));
        behaviourTab.setOnTabUnselected(() -> behaviourTab.textStyle(t -> t.textColor(MUTED)));
        tabs.addTab(propertiesTab, inspector);
        tabs.addTab(behaviourTab, behaviour);
        propertiesTab.textStyle(t -> t.textColor(PLAIN));
        behaviourTab.textStyle(t -> t.textColor(MUTED));

        Label hint = new Label();
        hint.setText(Component.translatable("backutils.item_editor.advanced.hint"));
        hint.textStyle(t -> t.fontSize(8f).textColor(MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER));
        hint.layout(l -> l.widthPercent(100).height(10).flexShrink(0));

        panel.style(s -> s.background(new ColorRectTexture(PANEL_BG)));
        panel.addChildren(titleRow, tabTop, tabs, hint);
        root.addChild(panel);
        ui = ModularUI.of(UI.of(root));
    }

    private void fill() {
        root.layout(l -> l.width(this.width).height(this.height)
                .flexDirection(FlexDirection.COLUMN));
        panel.layout(l -> l.width(this.width).height(this.height).paddingAll(PAD)
                .flexDirection(FlexDirection.COLUMN).gapAll(2));
        int tabsHeight = Math.max(40, this.height - PAD * 2 - 18 - 2 - 10 - 6);
        tabs.layout(l -> l.widthPercent(100).height(tabsHeight).minHeight(0));
        tabs.tabContentContainer(c -> c.layout(l -> l.paddingAll(2).minHeight(0)));
        // The tab content container is sized by the TabView, but its children are not: a
        // heightPercent inspector had no resolved parent height to take and grew to its rows
        // instead, so the scroller never became taller than its content. Give it the content box
        // explicitly, which is what gives the Inspector a viewport to scroll.
        int contentHeight = Math.max(30, tabsHeight - 20);
        inspector.layout(l -> l.widthPercent(100).height(contentHeight).minHeight(0));
        behaviourInspector.layout(l -> l.widthPercent(100).height(contentHeight).minHeight(0));
    }

    /**
     * The same dim the main editor paints behind its panel, at the operator's configured menu
     * opacity. Without it this screen sat on the game's default blur while the screen it was
     * opened from sat on this dim, which is what made the two look like different editors.
     */
    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics,
            int mouseX, int mouseY, float partialTick) {
        int alpha = (int) Math.round(Math.max(0.0D, Math.min(1.0D,
                net.xlebupaksa.backutils.client.BackUtilsClientConfig.getMenuBackgroundOpacity()))
                * 255.0D);
        graphics.fill(0, 0, this.width, this.height, (alpha << 24) | 0x00101016);
    }

    @Override
    public void onClose() {
        leave();
    }

    private void leave() {
        Minecraft.getInstance().setScreen(parent.reopen(properties));
    }

    // ------------------------------------------------------------------
    // Configurable groups
    // ------------------------------------------------------------------

    private void buildProperties(ConfiguratorGroup root) {
        buildConsumable(root);
        buildDurability(root);
        buildDyed(root);
        buildEnchantments(root);
        buildMisc(root);
        buildAttributes(root);
    }

    private void buildBehaviour(ConfiguratorGroup root) {
        addBehaviourTrigger(root, "backutils.item_editor.label.when_attacked",
                properties.behaviour.attack, true, true,
                "backutils.item_editor.tooltip.trigger.attack");
        addBehaviourTrigger(root, "backutils.item_editor.label.when_used",
                properties.behaviour.use, true, false,
                "backutils.item_editor.tooltip.trigger.use");
        addBehaviourTrigger(root, "backutils.item_editor.label.when_eaten",
                properties.behaviour.eaten, false, false,
                "backutils.item_editor.tooltip.trigger.eaten");
    }

    private void addBehaviourTrigger(ConfiguratorGroup root, String label,
            ItemBehaviour.Trigger trigger, boolean cooldown, boolean asTarget, String tipKey) {
        ToggleGroup group = new ToggleGroup(Text.of(label), !trigger.enabled, trigger.enabled,
                value -> trigger.enabled = value);
        group.setTips(Component.translatable(tipKey));

        if (cooldown) {
            group.addConfigurator(number("backutils.item_editor.label.cooldown_seconds",
                    0.0f, ItemBehaviour.MAX_COOLDOWN_SECONDS,
                    () -> trigger.cooldownSeconds,
                    value -> trigger.cooldownSeconds = value)
                    .setTips(Component.translatable(
                            "backutils.item_editor.tooltip.trigger.cooldown")));
        }

        addCommandList(group, "backutils.item_editor.label.commands",
                "backutils.item_editor.tooltip.commands", trigger.commands);
        if (asTarget) {
            addCommandList(group, "backutils.item_editor.label.commands_as_target",
                    "backutils.item_editor.tooltip.commands_as_target", trigger.targetCommands);
        }
        root.addConfigurator(group);
    }

    private void addCommandList(ConfiguratorGroup group, String label, String tipKey,
            java.util.List<String> commands) {
        ArrayConfiguratorGroup<String> list = new ArrayConfiguratorGroup<>(
                Text.of(label), true, () -> commands, this::commandConfigurator, true);
        list.setAddDefault(() -> "");
        list.setCanRemove(true);
        list.setCanReorder(true);
        list.setTips(Component.translatable(tipKey));
        list.setOnUpdate(updated -> {
            commands.clear();
            commands.addAll(updated);
        });
        group.addConfigurator(list);
    }

    private Configurator commandConfigurator(Supplier<String> getter, Consumer<String> setter) {
        StringConfigurator command =
                new StringConfigurator("", getter, setter, "", true);
        command.setTextValidator(value -> value == null
                || value.length() <= ItemBehaviour.MAX_COMMAND_LENGTH);
        command.textField.layout(l -> l.widthPercent(100).height(LINE));
        return command;
    }

    private void buildConsumable(ConfiguratorGroup root) {
        ConfiguratorGroup group = section(root, "backutils.item_editor.group.consumable",
                () -> properties.consumable, value -> properties.consumable = value);

        group.addConfigurator(integer("backutils.item_editor.label.nutrition",
                0, Integer.MAX_VALUE, () -> properties.nutrition,
                value -> properties.nutrition = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.nutrition")));
        group.addConfigurator(number("backutils.item_editor.label.saturation",
                0.0f, Float.MAX_VALUE, () -> properties.saturation,
                value -> properties.saturation = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.saturation")));
        group.addConfigurator(bool("backutils.item_editor.label.can_always_eat",
                () -> properties.canAlwaysEat, value -> properties.canAlwaysEat = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.can_always_eat")));
        group.addConfigurator(number("backutils.item_editor.label.consume_seconds",
                0.05f, 60.0f, () -> properties.eatSeconds,
                value -> properties.eatSeconds = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.consume_seconds")));

        ArrayConfiguratorGroup<AdvancedItemProperties.Effect> effects =
                new ArrayConfiguratorGroup<>(
                        Text.of("backutils.item_editor.label.effects"), true,
                        () -> properties.effects,
                        this::effectConfigurator,
                        true);
        effects.setTips(Component.translatable("backutils.item_editor.tooltip.effects"));
        effects.setAddDefault(this::newEffect);
        effects.setCanRemove(true);
        effects.setCanReorder(false);
        effects.setOnUpdate(list -> {
            properties.effects.clear();
            properties.effects.addAll(list);
        });
        group.addConfigurator(effects);
    }

    private void buildDurability(ConfiguratorGroup root) {
        ConfiguratorGroup group = section(root, "backutils.item_editor.group.durability",
                () -> properties.durability, value -> {
                    properties.durability = value;
                    if (value && properties.maxStackSize > 1) properties.maxStackSize = 1;
                });
        group.addConfigurator(integer("backutils.item_editor.label.current_durability",
                0, 1_000_000, () -> properties.currentDurability,
                value -> properties.currentDurability = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.current_durability")));
        group.addConfigurator(integer("backutils.item_editor.label.max_durability",
                1, 1_000_000, () -> properties.maxDamage,
                value -> properties.maxDamage = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.max_durability")));
        group.addConfigurator(bool("backutils.item_editor.label.unbreakable",
                () -> properties.unbreakable, value -> properties.unbreakable = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.unbreakable")));
    }

    private void buildDyed(ConfiguratorGroup root) {
        ConfiguratorGroup group = section(root, "backutils.item_editor.group.dyed",
                () -> properties.dyed, value -> properties.dyed = value);
        ColorConfigurator color = new ColorConfigurator(
                Text.of("backutils.item_editor.label.dyed_color"),
                // DyedItemColor is RGB only. The selector is ARGB, so give it an opaque alpha or its
                // preview and popup start fully transparent and the chosen colour disappears.
                () -> 0xFF000000 | (properties.dyedColor & 0xFFFFFF),
                value -> properties.dyedColor = value & 0xFFFFFF,
                0xFFFFFFFF, true);
        // The item stores no alpha, so do not offer a control that can only be overwritten on the
        // next tick. The selector still keeps its RGB channels; its alpha stays at the opaque value.
        color.colorSelector.alphaSlider.setDisplay(false);
        color.setTips(Component.translatable("backutils.item_editor.tooltip.dyed_color"));
        group.addConfigurator(color);
    }

    private void buildEnchantments(ConfiguratorGroup root) {
        ConfiguratorGroup group = section(root, "backutils.item_editor.group.enchantments",
                () -> properties.enchantments, value -> properties.enchantments = value);

        ArrayConfiguratorGroup<AdvancedItemProperties.EnchantmentLevel> enchantments =
                new ArrayConfiguratorGroup<>(
                        Text.of("backutils.item_editor.label.enchantment_list"), true,
                        () -> properties.enchantmentLevels,
                        this::enchantmentConfigurator,
                        true);
        enchantments.setTips(Component.translatable(
                "backutils.item_editor.tooltip.enchantment_list"));
        enchantments.setAddDefault(this::newEnchantment);
        enchantments.setCanRemove(true);
        enchantments.setCanReorder(false);
        enchantments.setOnUpdate(list -> {
            properties.enchantmentLevels.clear();
            properties.enchantmentLevels.addAll(list);
        });
        group.addConfigurator(enchantments);
    }

    private void buildMisc(ConfiguratorGroup root) {
        ConfiguratorGroup group = section(root, "backutils.item_editor.group.misc",
                () -> properties.misc, value -> properties.misc = value);
        group.addConfigurator(bool("backutils.item_editor.label.fire_resistant",
                () -> properties.fireResistant, value -> properties.fireResistant = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.fire_resistant")));
        group.addConfigurator(bool("backutils.item_editor.label.glint",
                () -> properties.glint, value -> properties.glint = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.glint")));
        group.addConfigurator(bool("backutils.item_editor.label.hide_enchants",
                () -> properties.hideEnchants, value -> properties.hideEnchants = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.hide_enchants")));
        group.addConfigurator(bool("backutils.item_editor.label.cannot_be_placed",
                () -> properties.cannotBePlaced, value -> properties.cannotBePlaced = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.cannot_be_placed")));
        group.addConfigurator(integer("backutils.item_editor.label.max_stack_size",
                1, 99, () -> properties.maxStackSize,
                value -> properties.maxStackSize = value)
                .setTips(Component.translatable("backutils.item_editor.tooltip.max_stack_size")));
    }

    private void buildAttributes(ConfiguratorGroup root) {
        if (parent == null) return;
        ArrayConfiguratorGroup<ItemEdit.Granted> attributes = new ArrayConfiguratorGroup<>(
                Text.of("backutils.item_editor.label.attributes"), true,
                () -> parent.attributeDraft(),
                this::attributeConfigurator,
                true);
        attributes.setAddDefault(this::newAttribute);
        attributes.setCanRemove(true);
        attributes.setCanReorder(false);
        attributes.setTips(Component.translatable("backutils.item_editor.tooltip.attributes"));
        attributes.setOnUpdate(list -> {
            parent.attributeDraft().clear();
            parent.attributeDraft().addAll(list);
            parent.attributeDraftChanged();
        });
        root.addConfigurator(attributes);
    }

    // ------------------------------------------------------------------
    // Configurators
    // ------------------------------------------------------------------

    private ConfiguratorGroup section(ConfiguratorGroup root, String key,
            Supplier<Boolean> enabled, Consumer<Boolean> setter) {
        boolean on = Boolean.TRUE.equals(enabled.get());
        ToggleGroup group = new ToggleGroup(Text.of(key), !on, on, setter);
        root.addConfigurator(group);
        return group;
    }

    /**
     * A {@link ConfiguratorGroup} with the library's checkbox in its own heading.
     *
     * <p>The heading is the thing that owns a property group, so the checkbox belongs there rather
     * than in a duplicate "Enabled" row inside the body. The heading still collapses by clicking
     * the empty part of it; a click on the checkbox is the checkbox's own.
     */
    private static final class ToggleGroup extends ConfiguratorGroup {

        private final Toggle toggle;

        private ToggleGroup(String label, boolean collapsed, boolean on,
                Consumer<Boolean> setter) {
            super("", collapsed);
            setLabel(Component.literal(label));
            toggle = new Toggle();
            toggle.toggleLabel.setText("");
            toggle.layout(l -> l.width(14).height(14));
            styleToggle(toggle);
            toggle.setOn(on, false);
            toggle.setOnToggleChanged(value -> {
                setter.accept(value);
                // The checkbox is the whole opening gesture: checking the option both gives the
                // group its components and unfolds the rows that configure them. Unchecking folds
                // them away again instead of leaving a disabled group's controls on screen.
                setCollapse(!value);
            });
            lineContainer.addChild(toggle);
        }

        @Override
        protected void onLineContainerClick(UIEvent event) {
            for (UIElement element = event.target; element != null; element = element.getParent()) {
                if (element == toggle) return;
            }
            // Only the folder arrow collapses a group. Clicking the checkbox or the help icon must
            // not also fold the group away under the pointer.
            if (event.target != folderIcon) return;
            super.onLineContainerClick(event);
        }
    }

    private static void styleToggle(Toggle toggle) {
        toggle.toggleStyle(s -> s
                .baseTexture(IGuiTexture.EMPTY)
                .hoverTexture(new ColorRectTexture(0x20FFFFFF))
                .unmarkTexture(Icons.CHECKBOX_BLANK)
                .markTexture(Icons.CHECKBOX_MARKED));
    }

    private Configurator effectConfigurator(Supplier<AdvancedItemProperties.Effect> getter,
            Consumer<AdvancedItemProperties.Effect> setter) {
        ConfiguratorGroup group = new ConfiguratorGroup("", false);
        group.hideTitle();
        group.configuratorContainer(container -> container
                .layout(l -> l.paddingAll(0).marginLeft(0).gapAll(1))
                .style(s -> s.backgroundTexture(IGuiTexture.EMPTY)));

        SelectorConfigurator<String> effectSelector = new SelectorConfigurator<>(
                Text.of("backutils.item_editor.label.effect"),
                () -> getter.get().id(),
                id -> setter.accept(withId(getter.get(), id)),
                firstEffectId(), false, effectIds(), Function.identity());
        effectSelector.setTips(Component.translatable("backutils.item_editor.tooltip.effect"));
        group.addConfigurator(effectSelector);
        group.addConfigurator(number("backutils.item_editor.label.duration",
                0.05f, 60.0f,
                () -> getter.get().durationTicks() / 20.0f,
                value -> setter.accept(withDuration(getter.get(), value)))
                .setTips(Component.translatable("backutils.item_editor.tooltip.duration")));
        group.addConfigurator(integer("backutils.item_editor.label.level",
                1, 256, () -> getter.get().amplifier() + 1,
                value -> setter.accept(withAmplifier(getter.get(), value - 1)))
                .setTips(Component.translatable("backutils.item_editor.tooltip.level")));
        group.addConfigurator(bool("backutils.item_editor.label.ambient",
                () -> getter.get().ambient(),
                value -> setter.accept(withAmbient(getter.get(), value)))
                .setTips(Component.translatable("backutils.item_editor.tooltip.effect.ambient")));
        group.addConfigurator(bool("backutils.item_editor.label.particles",
                () -> getter.get().visible(),
                value -> setter.accept(withVisible(getter.get(), value)))
                .setTips(Component.translatable("backutils.item_editor.tooltip.effect.particles")));
        group.addConfigurator(bool("backutils.item_editor.label.icon",
                () -> getter.get().showIcon(),
                value -> setter.accept(withIcon(getter.get(), value)))
                .setTips(Component.translatable("backutils.item_editor.tooltip.effect.icon")));
        return group;
    }

    private Configurator enchantmentConfigurator(
            Supplier<AdvancedItemProperties.EnchantmentLevel> getter,
            Consumer<AdvancedItemProperties.EnchantmentLevel> setter) {
        ConfiguratorGroup group = new ConfiguratorGroup("", false);
        group.hideTitle();
        group.configuratorContainer(container -> container
                .layout(l -> l.paddingAll(0).marginLeft(0).gapAll(1))
                .style(s -> s.backgroundTexture(IGuiTexture.EMPTY)));
        SelectorConfigurator<String> enchantmentSelector = new SelectorConfigurator<>(
                Text.of("backutils.item_editor.label.enchantment"),
                () -> getter.get().id(),
                id -> setter.accept(new AdvancedItemProperties.EnchantmentLevel(
                        id, getter.get().level())),
                firstEnchantmentId(), false, enchantmentIds(), Function.identity());
        enchantmentSelector.setTips(Component.translatable(
                "backutils.item_editor.tooltip.enchantment"));
        group.addConfigurator(enchantmentSelector);
        group.addConfigurator(integer("backutils.item_editor.label.level",
                1, 255, () -> getter.get().level(),
                value -> setter.accept(new AdvancedItemProperties.EnchantmentLevel(
                        getter.get().id(), value)))
                .setTips(Component.translatable("backutils.item_editor.tooltip.level")));
        return group;
    }

    private Configurator attributeConfigurator(Supplier<ItemEdit.Granted> getter,
            Consumer<ItemEdit.Granted> setter) {
        ConfiguratorGroup group = new ConfiguratorGroup("", false);
        group.hideTitle();
        group.configuratorContainer(container -> container
                .layout(l -> l.paddingAll(0).marginLeft(0).gapAll(1))
                .style(s -> s.backgroundTexture(IGuiTexture.EMPTY)));

        group.addConfigurator(new SelectorConfigurator<>(
                Text.of("backutils.item_editor.label.attribute"),
                () -> getter.get().attribute().toString(),
                id -> setter.accept(withAttribute(getter.get(), id)),
                firstAttributeId(), false, attributeIds(), Function.identity())
                .setTips(Component.translatable("backutils.item_editor.tooltip.attribute")));
        group.addConfigurator(number("backutils.item_editor.label.amount",
                -1_000_000.0f, 1_000_000.0f,
                () -> (float) getter.get().amount(),
                value -> setter.accept(withAmount(getter.get(), value)))
                .setTips(Component.translatable("backutils.item_editor.tooltip.amount")));
        group.addConfigurator(new SelectorConfigurator<>(
                Text.of("backutils.item_editor.label.operation"),
                () -> getter.get().operation().name(),
                name -> setter.accept(withOperation(getter.get(), name)),
                AttributeModifier.Operation.ADD_VALUE.name(), false,
                operationIds(), Function.identity())
                .setTips(Component.translatable("backutils.item_editor.tooltip.operation")));
        group.addConfigurator(new SelectorConfigurator<>(
                Text.of("backutils.item_editor.label.slot"),
                () -> getter.get().slot().name(),
                name -> setter.accept(withSlot(getter.get(), name)),
                EquipmentSlotGroup.ANY.name(), false,
                slotIds(), Function.identity())
                .setTips(Component.translatable("backutils.item_editor.tooltip.slot")));
        return group;
    }

    private static BooleanConfigurator bool(String key, Supplier<Boolean> getter,
            Consumer<Boolean> setter) {
        BooleanConfigurator configurator =
                new BooleanConfigurator(Text.of(key), getter, setter, false, true);
        styleToggle(configurator.toggle);
        return configurator;
    }

    private static NumberConfigurator integer(String key, int min, int max,
            Supplier<Integer> getter, IntConsumer setter) {
        NumberConfigurator field = new NumberConfigurator(
                Text.of(key), () -> (Number) getter.get(),
                value -> setter.accept(value == null ? min : value.intValue()),
                min, false);
        field.setType(ConfigNumber.Type.INTEGER);
        field.setRange(min, max);
        field.setWheel(1);
        return field;
    }

    private static NumberConfigurator number(String key, float min, float max,
            Supplier<Float> getter, Consumer<Float> setter) {
        NumberConfigurator field = new NumberConfigurator(
                Text.of(key), () -> (Number) getter.get(),
                value -> setter.accept(value == null ? min : value.floatValue()),
                min, false);
        field.setType(ConfigNumber.Type.FLOAT);
        field.setRange(min, max);
        field.setWheel(0.1f);
        return field;
    }

    // ------------------------------------------------------------------
    // Draft helpers
    // ------------------------------------------------------------------

    private AdvancedItemProperties.Effect newEffect() {
        List<String> used = new ArrayList<>();
        for (AdvancedItemProperties.Effect effect : properties.effects) used.add(effect.id());
        String next = effectIds().stream().filter(id -> !used.contains(id)).findFirst()
                .orElse(firstEffectId());
        return new AdvancedItemProperties.Effect(next, 200, 0, false, true, true);
    }

    private AdvancedItemProperties.EnchantmentLevel newEnchantment() {
        List<String> used = new ArrayList<>();
        for (AdvancedItemProperties.EnchantmentLevel line : properties.enchantmentLevels) {
            used.add(line.id());
        }
        String next = enchantmentIds().stream().filter(id -> !used.contains(id)).findFirst()
                .orElse(firstEnchantmentId());
        return new AdvancedItemProperties.EnchantmentLevel(next, 1);
    }

    private ItemEdit.Granted newAttribute() {
        List<String> used = new ArrayList<>();
        if (parent != null) {
            for (ItemEdit.Granted granted : parent.attributeDraft()) {
                used.add(granted.attribute().toString());
            }
        }
        String next = attributeIds().stream().filter(id -> !used.contains(id)).findFirst()
                .orElse(firstAttributeId());
        return new ItemEdit.Granted(ResourceLocation.parse(next), 1.0D,
                AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.ANY);
    }

    private static ItemEdit.Granted withAttribute(ItemEdit.Granted granted, String id) {
        ResourceLocation attribute = ResourceLocation.tryParse(id == null ? "" : id);
        return attribute == null ? granted
                : new ItemEdit.Granted(attribute, granted.amount(), granted.operation(),
                        granted.slot());
    }

    private static ItemEdit.Granted withAmount(ItemEdit.Granted granted, float amount) {
        return new ItemEdit.Granted(granted.attribute(), amount, granted.operation(),
                granted.slot());
    }

    private static ItemEdit.Granted withOperation(ItemEdit.Granted granted, String name) {
        return new ItemEdit.Granted(granted.attribute(), granted.amount(),
                operation(name), granted.slot());
    }

    private static ItemEdit.Granted withSlot(ItemEdit.Granted granted, String name) {
        return new ItemEdit.Granted(granted.attribute(), granted.amount(), granted.operation(),
                slot(name));
    }

    private static AttributeModifier.Operation operation(String name) {
        try {
            return AttributeModifier.Operation.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return AttributeModifier.Operation.ADD_VALUE;
        }
    }

    private static EquipmentSlotGroup slot(String name) {
        try {
            return EquipmentSlotGroup.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return EquipmentSlotGroup.ANY;
        }
    }

    private static String firstAttributeId() {
        List<String> ids = attributeIds();
        return ids.isEmpty() ? "minecraft:generic.attack_damage" : ids.getFirst();
    }

    private static List<String> attributeIds() {
        return BuiltInRegistries.ATTRIBUTE.keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .toList();
    }

    private static List<String> operationIds() {
        return Arrays.stream(AttributeModifier.Operation.values()).map(Enum::name).toList();
    }

    private static List<String> slotIds() {
        return Arrays.stream(EquipmentSlotGroup.values()).map(Enum::name).toList();
    }

    private static AdvancedItemProperties.Effect withId(
            AdvancedItemProperties.Effect effect, String id) {
        return new AdvancedItemProperties.Effect(id, effect.durationTicks(), effect.amplifier(),
                effect.ambient(), effect.visible(), effect.showIcon());
    }

    private static AdvancedItemProperties.Effect withDuration(
            AdvancedItemProperties.Effect effect, float seconds) {
        return new AdvancedItemProperties.Effect(effect.id(),
                Math.max(1, Math.round(seconds * 20.0f)), effect.amplifier(), effect.ambient(),
                effect.visible(), effect.showIcon());
    }

    private static AdvancedItemProperties.Effect withAmplifier(
            AdvancedItemProperties.Effect effect, int amplifier) {
        return new AdvancedItemProperties.Effect(effect.id(), effect.durationTicks(), amplifier,
                effect.ambient(), effect.visible(), effect.showIcon());
    }

    private static AdvancedItemProperties.Effect withAmbient(
            AdvancedItemProperties.Effect effect, boolean ambient) {
        return new AdvancedItemProperties.Effect(effect.id(), effect.durationTicks(),
                effect.amplifier(), ambient, effect.visible(), effect.showIcon());
    }

    private static AdvancedItemProperties.Effect withVisible(
            AdvancedItemProperties.Effect effect, boolean visible) {
        return new AdvancedItemProperties.Effect(effect.id(), effect.durationTicks(),
                effect.amplifier(), effect.ambient(), visible, effect.showIcon());
    }

    private static AdvancedItemProperties.Effect withIcon(
            AdvancedItemProperties.Effect effect, boolean icon) {
        return new AdvancedItemProperties.Effect(effect.id(), effect.durationTicks(),
                effect.amplifier(), effect.ambient(), effect.visible(), icon);
    }

    private static String firstEffectId() {
        List<String> ids = effectIds();
        return ids.isEmpty() ? "minecraft:absorption" : ids.getFirst();
    }

    private static String firstEnchantmentId() {
        List<String> ids = enchantmentIds();
        return ids.isEmpty() ? "minecraft:sharpness" : ids.getFirst();
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

    private static void text(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement element,
            String key, float size) {
        element.setText(Text.of(key), false);
        element.textStyle(t -> t.fontSize(size).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
    }
}
