package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Slider;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Switch;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.BackUtilsSettings;
import net.xlebupaksa.backutils.data.MarkupUtil;
import net.xlebupaksa.backutils.data.MarkupWrap;
import net.xlebupaksa.backutils.network.AdminConfigCache;
import net.xlebupaksa.backutils.network.AdminConfigEditPayload;
import net.xlebupaksa.backutils.network.AdminConfigFeedbackCache;
import net.xlebupaksa.backutils.network.AdminConfigNetwork;
import net.xlebupaksa.backutils.network.AdminConfigPayload;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The background menu's Config tab: the server's settings in sub-tabs, with Save and Revert.
 *
 * <p>Rows are built from what the server sent, so no settings list lives here; the kind picks the
 * control — {@code Switch} for 0-or-1, {@code Slider} plus field for a number, {@code TextField}
 * for text, a {@link MarkupLabel} preview for the separators — a list is read-only with the
 * command that changes it, and unsaved rows are counted in the footer.
 */
@OnlyIn(Dist.CLIENT)
public final class AdminConfigTab {

    private static final int ROW_IDLE = 0x44101016;
    /** A row holding something that has not been saved. */
    private static final int ROW_CHANGED = 0x664E7FB8;
    private static final int MUTED = 0xFF9AA3B2;
    private static final int PLAIN = 0xFFE2E8F0;
    private static final int WARNING = 0xFFFF8080;
    private static final int GOOD = 0xFF8FD98F;

    private static final int ROW = 18;
    private static final int COMMENT = 10;
    private static final int PREVIEW = 12;
    private static final int LABEL_WIDTH = 132;
    private static final int FIELD_WIDTH = 52;
    private static final int UNIT_WIDTH = 48;
    private static final int GAP = 6;
    private static final int FOOTER = 18;
    /** ldlib2's own tab-content padding, and the strip above it. */
    private static final int TAB_PAD = 5;
    private static final int TAB_STRIP = 22;

    private static final String PREVIEW_NAME = "Dev";
    private static final String PREVIEW_MESSAGE = "Hello there";

    private final UIElement root = new UIElement();
    private final TabView tabs = new TabView();
    private final UIElement footer = new UIElement();
    private final Button save = new Button();
    private final Button revert = new Button();
    private final Label status = new Label();

    private final Map<BackUtilsSettings.Group, ScrollerView> lists =
            new EnumMap<>(BackUtilsSettings.Group.class);
    /** The control row of each setting, tinted while its value is unsaved. */
    private final Map<String, UIElement> rowElements = new LinkedHashMap<>();
    /** The exact-value fields, so a slider drag can move the one beside it. */
    private final Map<String, TextField> fields = new LinkedHashMap<>();
    private final Map<String, Slider.Horizontal> sliders = new LinkedHashMap<>();
    private final Map<String, MarkupLabel> previews = new LinkedHashMap<>();
    private final Map<String, String> pending = new LinkedHashMap<>();

    private int builtRevision = -1;
    private int builtFeedbackRevision = -1;

    public AdminConfigTab() {
        build();
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    private void build() {
        for (BackUtilsSettings.Group group : BackUtilsSettings.Group.values()) {
            ScrollerView list = new ScrollerView();
            list.scrollerStyle(s -> s
                    .mode(ScrollerMode.VERTICAL)
                    // ALWAYS rather than AUTO: the bar is the affordance that says the list scrolls.
                    .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                    .horizontalScrollDisplay(ScrollDisplay.NEVER)
                    .adaptiveWidth(false)
                    .adaptiveHeight(false));
            list.viewContainer(v -> v.getLayout().gapAll(2));
            lists.put(group, list);
            tabs.addTab(new Tab().setText(group.display(), false), list);
        }
        // The strip stays, the panel border goes: a box inside the menu's own tab view is a mistake.
        tabs.tabContentContainer(c -> c.style(s -> s.backgroundTexture(IGuiTexture.EMPTY)));

        save.setText("Save", false);
        save.layout(l -> l.width(64).height(14));
        save.setOnClick(e -> save());

        revert.setText("Revert", false);
        revert.layout(l -> l.width(64).height(14));
        revert.setOnClick(e -> revert());

        status.setText("", false);
        status.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));

        footer.layout(l -> l.flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER)
                .gapAll(GAP));
        footer.addChildren(save, revert, status);

        root.layout(l -> l.flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        root.addChildren(tabs, footer);
    }

    /** {@return the element to mount as the Config tab's content} */
    public UIElement root() {
        return root;
    }

    /** Sizes everything to the room the tab content has. */
    public void layout(int width, int height) {
        int w = Math.max(120, width);
        int h = Math.max(60, height);
        int tabsHeight = Math.max(40, h - FOOTER - GAP);

        root.layout(l -> l.width(w).height(h).flexDirection(FlexDirection.COLUMN).gapAll(GAP));
        tabs.layout(l -> l.width(w).height(tabsHeight));
        footer.layout(l -> l.width(w).height(FOOTER).flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER).gapAll(GAP));

        int innerWidth = Math.max(60, w - TAB_PAD * 2);
        int innerHeight = Math.max(40, tabsHeight - TAB_STRIP - TAB_PAD * 2);
        for (ScrollerView list : lists.values()) {
            list.layout(l -> l.width(innerWidth).height(innerHeight));
        }
    }

    // ------------------------------------------------------------------
    // Following the server
    // ------------------------------------------------------------------

    /** Brings the tab up to date with whatever the server last sent. */
    public void sync() {
        if (AdminConfigCache.revision() != builtRevision) rebuild();
        showFeedback();
    }

    /**
     * Redraws every row from the server's values, dropping unsaved edits; a refusal keeps them.
     */
    private void rebuild() {
        builtRevision = AdminConfigCache.revision();
        pending.clear();
        rowElements.clear();
        fields.clear();
        sliders.clear();
        previews.clear();
        for (ScrollerView list : lists.values()) list.clearAllScrollViewChildren();

        Map<BackUtilsSettings.Group, Integer> counts =
                new EnumMap<>(BackUtilsSettings.Group.class);
        for (AdminConfigPayload.Row setting : AdminConfigCache.rows()) {
            ScrollerView list = lists.get(setting.group());
            if (list == null) continue;
            list.addScrollViewChild(block(setting));
            counts.merge(setting.group(), 1, Integer::sum);
        }
        for (Map.Entry<BackUtilsSettings.Group, ScrollerView> entry : lists.entrySet()) {
            if (counts.getOrDefault(entry.getKey(), 0) == 0) {
                entry.getValue().addScrollViewChild(muted("Nothing in this group."));
            }
        }
        refreshFooter();
    }

    // ------------------------------------------------------------------
    // One setting
    // ------------------------------------------------------------------

    /** {@return the block for one setting: its row, then the config file's explanation} */
    private UIElement block(AdminConfigPayload.Row setting) {
        UIElement row = new UIElement();
        row.layout(l -> l.widthPercent(100).height(ROW)
                .flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).gapAll(GAP));
        row.style(s -> s.background(new ColorRectTexture(ROW_IDLE))
                .tooltips(Component.literal(String.join(" ", setting.comment()))));

        Label label = text(setting.label(), PLAIN, 9f, ROW);
        label.layout(l -> l.width(LABEL_WIDTH).height(ROW));
        row.addChild(label);

        UIElement block = new UIElement();
        block.layout(l -> l.widthPercent(100).height(ROW + COMMENT + 2)
                .flexDirection(FlexDirection.COLUMN));
        boolean separator = false;

        switch (setting.kind()) {
            case BOOL -> {
                Label readout = text(onOff(setting.value()), MUTED, 9f, ROW);
                readout.layout(l -> l.width(UNIT_WIDTH).height(ROW));
                row.addChildren(switchFor(setting, readout), readout);
            }
            case NUMBER -> {
                String unit = unit(setting.key());
                Label units = text(unit, MUTED, 8f, ROW);
                units.layout(l -> l.width(unit.isEmpty() ? 0 : UNIT_WIDTH).height(ROW));
                row.addChildren(sliderFor(setting), fieldFor(setting), units);
            }
            case TEXT -> {
                row.addChild(fieldFor(setting));
                separator = isSeparator(setting.key());
                if (separator) block.layout(l -> l.widthPercent(100)
                        .height(ROW + COMMENT + 2 + PREVIEW).flexDirection(FlexDirection.COLUMN));
            }
            case LIST -> row.addChild(readOnly(setting));
        }

        block.addChild(row);

        Label comment = text(comment(setting), MUTED, 8f, COMMENT);
        comment.layout(l -> l.widthPercent(100).height(COMMENT));
        block.addChild(comment);

        if (separator) {
            MarkupLabel preview = new MarkupLabel(List.of(""), COMMENT + 2, PLAIN);
            preview.layout(l -> l.widthPercent(100).height(PREVIEW));
            previews.put(setting.key(), preview);
            block.addChild(preview);
            updatePreview(setting.key());
        }

        rowElements.put(setting.key(), row);
        return block;
    }

    /** A switch, for a setting the config file keeps as 0 or 1. */
    private Switch switchFor(AdminConfigPayload.Row setting, Label readout) {
        Switch toggle = new Switch();
        toggle.layout(l -> l.width(26).height(12));
        // notify = false: loading a value is not a change the administrator made.
        toggle.setOn(Boolean.parseBoolean(setting.value()), false);
        toggle.setOnSwitchChanged(on -> {
            readout.setText(on ? "on" : "off", false);
            set(setting.key(), String.valueOf(on));
        });
        return toggle;
    }

    /** A slider for the coarse part of a number, and a field for the exact part. */
    private Slider.Horizontal sliderFor(AdminConfigPayload.Row setting) {
        Slider.Horizontal slider = new Slider.Horizontal();
        slider.setRange((float) setting.min(), (float) setting.max());
        slider.setValue(valueOf(setting.value(), setting.min()), false);
        slider.layout(l -> l.flex(1).height(12));
        slider.setOnValueChanged(value -> {
            String text = number(String.valueOf(value), setting.min());
            // The field and the pending value both take the slider's text, so they cannot disagree.
            TextField field = fields.get(setting.key());
            if (field != null) field.setText(text, false);
            set(setting.key(), text);
        });
        sliders.put(setting.key(), slider);
        return slider;
    }

    private TextField fieldFor(AdminConfigPayload.Row setting) {
        TextField field = new TextField();
        field.setText(setting.value(), false);
        boolean number = setting.kind() == BackUtilsSettings.Kind.NUMBER;
        if (number) {
            field.layout(l -> l.width(FIELD_WIDTH).height(14));
        } else {
            field.layout(l -> l.flex(1).height(14));
        }
        field.setTextResponder(text -> {
            if (number) {
                Slider.Horizontal slider = sliders.get(setting.key());
                if (slider != null) {
                    try {
                        double value = Double.parseDouble(text.trim());
                        if (value >= setting.min() && value <= setting.max()) {
                            slider.setValue((float) value, false);
                        }
                    } catch (NumberFormatException ignored) {
                        // Mid-typing: "-", "1e" and the like. Save is where it has to parse.
                    }
                }
            }
            if (previews.containsKey(setting.key())) updatePreview(setting.key());
            set(setting.key(), text);
        });
        fields.put(setting.key(), field);
        return field;
    }

    /** What a list looks like when it is not editable here. */
    private Label readOnly(AdminConfigPayload.Row setting) {
        // Cut to the row: a label draws as wide as its text and would run off a narrow tab.
        int width = Math.max(120, Math.round(root.getSizeWidth()) - LABEL_WIDTH - GAP * 2);
        Label value = text(MarkupWrap.truncate(setting.value(), width,
                text -> Minecraft.getInstance().font.width(text)), MUTED, 8f, ROW);
        value.layout(l -> l.flex(1).height(ROW));
        return value;
    }

    /** Draws a separator through {@link MarkupLabel}, as it will read in chat. */
    private void updatePreview(String key) {
        MarkupLabel preview = previews.get(key);
        TextField field = fields.get(key);
        if (preview == null || field == null) return;

        String separator = field.getText();
        String line = PREVIEW_NAME + (separator == null ? "" : separator) + PREVIEW_MESSAGE;
        int width = Math.max(40, Math.round(root.getSizeWidth()) - LABEL_WIDTH - GAP * 2);
        List<String> wrapped = MarkupWrap.wrapByWidth(line, width,
                text -> Minecraft.getInstance().font.width(MarkupUtil.strip(text)));
        preview.setRows(wrapped.isEmpty() ? List.of("") : wrapped.subList(0, 1));
    }

    // ------------------------------------------------------------------
    // Unsaved values
    // ------------------------------------------------------------------

    /** Records a typed or dragged value, and marks the row while it differs from the server's. */
    private void set(String key, String value) {
        boolean changed = value != null && !value.equals(AdminConfigCache.value(key));
        if (changed) {
            pending.put(key, value);
        } else {
            pending.remove(key);
        }

        UIElement row = rowElements.get(key);
        if (row != null) {
            row.style(s -> s.background(new ColorRectTexture(changed ? ROW_CHANGED : ROW_IDLE)));
        }
        refreshFooter();
    }

    /** Sends everything unsaved, after checking it the way the server will. */
    private void save() {
        if (pending.isEmpty()) {
            setStatus("Nothing to save.", MUTED);
            return;
        }

        List<AdminConfigEditPayload.Change> changes = new ArrayList<>();
        for (Map.Entry<String, String> entry : pending.entrySet()) {
            String value;
            try {
                value = BackUtilsSettings.check(entry.getKey(), entry.getValue());
            } catch (IllegalArgumentException problem) {
                setStatus(BackUtilsSettings.labelOf(entry.getKey()) + ": " + problem.getMessage(),
                        WARNING);
                return;
            }
            changes.add(new AdminConfigEditPayload.Change(entry.getKey(), value));
        }

        AdminConfigNetwork.send(new AdminConfigEditPayload(changes));
        setStatus("Saving...", MUTED);
    }

    /** Drops the unsaved values and draws the server's again. */
    private void revert() {
        boolean had = !pending.isEmpty();
        rebuild();
        setStatus(had ? "Reverted to the server's values." : "Nothing to revert.", MUTED);
    }

    // ------------------------------------------------------------------
    // The footer
    // ------------------------------------------------------------------

    private void refreshFooter() {
        int count = pending.size();
        if (count == 0) {
            setStatus("No unsaved changes", MUTED);
        } else {
            setStatus(count + (count == 1 ? " unsaved change" : " unsaved changes"), PLAIN);
        }
    }

    /** Shows the server's last answer, once. */
    private void showFeedback() {
        if (AdminConfigFeedbackCache.revision() == builtFeedbackRevision) return;
        builtFeedbackRevision = AdminConfigFeedbackCache.revision();
        setStatus(AdminConfigFeedbackCache.message(),
                AdminConfigFeedbackCache.ok() ? GOOD : WARNING);
    }

    private void setStatus(String text, int colour) {
        // Cut to the room the footer has, since a label draws as wide as its text.
        String fitted = MarkupWrap.truncate(text,
                Math.max(60, Math.round(root.getSizeWidth()) - 160),
                value -> Minecraft.getInstance().font.width(value));
        status.setText(fitted, false);
        status.textStyle(t -> t.fontSize(9f).textColor(colour).textShadow(false)
                .textAlignVertical(Vertical.CENTER).adaptiveWidth(true));
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private static boolean isSeparator(String key) {
        return key.equals("chatSeparator") || key.equals("globalChatSeparator");
    }

    private static String onOff(String value) {
        return Boolean.parseBoolean(value) ? "on" : "off";
    }

    /** {@return the unit a number is measured in, which is presentation and not config} */
    private static String unit(String key) {
        return switch (key) {
            case "localChatRadius", "logRadius" -> "blocks";
            case "typingSpeed" -> "chars/s";
            default -> "";
        };
    }

    /**
     * {@return the one line of explanation to show under a setting}
     *
     * <p>The whole comment is the tooltip, on one line: ldlib2 draws a newline there as a blank box.
     */
    private String comment(AdminConfigPayload.Row setting) {
        List<String> lines = setting.comment();
        if (lines.isEmpty()) return "";
        int width = Math.max(120, Math.round(root.getSizeWidth()) - 12);
        return MarkupWrap.truncate(lines.getFirst(), width,
                value -> Minecraft.getInstance().font.width(value));
    }

    /**
     * {@return a value parsed for a slider, or the fallback when the stored text is not a number}
     */
    private static float valueOf(String text, double fallback) {
        try {
            return (float) Double.parseDouble(text.trim());
        } catch (NumberFormatException ignored) {
            return (float) fallback;
        }
    }

    /**
     * {@return a number written the way this tab writes numbers, so equal values compare equal}
     */
    private static String number(String text, double fallback) {
        try {
            double value = Double.parseDouble(text.trim());
            if (value == Math.floor(value) && Math.abs(value) < 1.0e9D) {
                return String.valueOf((long) value);
            }
            String formatted = String.format(Locale.ROOT, "%.3f", value);
            while (formatted.endsWith("0")) {
                formatted = formatted.substring(0, formatted.length() - 1);
            }
            if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
            return formatted;
        } catch (NumberFormatException ignored) {
            return String.valueOf(fallback);
        }
    }

    private static Label text(String value, int colour, float size, int height) {
        Label label = new Label();
        label.setText(value == null ? "" : value, false);
        label.textStyle(t -> t.fontSize(size).textColor(colour).textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT).textAlignVertical(Vertical.CENTER)
                .adaptiveWidth(true));
        label.layout(l -> l.height(height));
        return label;
    }

    private static Label muted(String text) {
        Label label = new Label();
        label.setText(text, false);
        label.textStyle(t -> t.fontSize(9f).textColor(MUTED).textShadow(false).adaptiveWidth(true));
        return label;
    }
}
