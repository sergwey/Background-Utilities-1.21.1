package net.xlebupaksa.backutils.clientchest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.xlebupaksa.backutils.ui.ItemEditorScreen;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The chest screen: a page of the library, drawn as the chest it is shaped like, and the player's own
 * inventory beside it.
 *
 * <p>Nothing on this screen talks to a server, and that is arranged twice over. Every click is handed
 * to the menu's own logic directly — {@link #slotClicked} never reaches
 * {@code MultiPlayerGameMode.handleInventoryMouseClick}, which is what would send a click packet — and
 * the menu it is handed to is one with a negative id that the server has never heard of. What does
 * leave the client is the inventory itself, one creative slot packet at a time, because an item moved
 * out of a page has really been put in the player's inventory and the server has to agree.
 *
 * <p>Outside creative the screen still opens, and still lets the page be rearranged, because moving an
 * item between two slots of a file is entirely local. What it refuses is anything that would move an
 * item between the page and the player's inventory: the server drops creative slot packets from a
 * player who is not in creative, so an item taken out would be a picture of an item nobody has, and
 * the player's inventory would drift from the server's for as long as the screen is open. The refused
 * slots are tinted, so the rule is visible rather than mysterious.
 */
public final class ChestScreen extends AbstractContainerScreen<ChestMenu> {

    /** The chest a six-row page is drawn as. */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    /** Height of the strip that holds the name box and the page buttons, and the gap below it. */
    private static final int STRIP_HEIGHT = 18;
    private static final int STRIP_GAP = 4;

    /** The page the chest was last showing, so the command and the button open where it left off. */
    private static int remembered;

    /** Whether this session has already said that a page file could not be read. */
    private static boolean warnedAboutSetAside;

    /** Whether the name box had the focus at the last tick, so losing it can commit what was typed. */
    private boolean nameBoxHadFocus;

    /** Whether the controls are over the panel's title row, which only a window too short for both is. */
    private boolean stripOverTitle;

    /** The two controls that act on the page as a whole, and the answer one of them is waiting for. */
    private static final String CLEAR = "X";
    private static final String CLEAR_AGAIN = "!";
    private static final String RELOAD = "R";

    private Button clearPage;
    private Button reloadPage;

    /** Whether the clear button is waiting for the press that does it. */
    private boolean clearing;

    private final ChestPages pages;
    private final SimpleContainer container;
    private final ChestSync sync;

    private int page;
    private EditBox nameBox;
    private Button previousPage;
    private Button nextPage;
    private Button previousInteresting;
    private Button nextInteresting;

    /**
     * The title drawn on the panel, which is not the screen's own.
     *
     * <p>The screen's title is final and is what narration reads; a page's title changes whenever the
     * page does, so it is kept separately and drawn in its place.
     */
    private Component pageTitle;

    private ChestScreen(ChestPages pages, int page, SimpleContainer container, ChestMenu menu) {
        super(menu, Minecraft.getInstance().player.getInventory(), titleFor(pages, page));
        this.pages = pages;
        this.page = page;
        this.container = container;
        this.pageTitle = titleFor(pages, page);
        this.sync = new ChestSync(Minecraft.getInstance().player.getInventory());
        // The menu refuses an item placed into a locked slot by any route, not only by a click this
        // screen handles: shift-click and drags reach the slots without one.
        IntPredicate locked = slot -> this.pages.page(this.page).locked(slot);
        menu.locked(locked);
        // A six-row chest is taller than the three-row one the base class assumes.
        this.imageHeight = 114 + 6 * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    /** Opens the page that was open last, or the first page. */
    public static void open() {
        open(remembered);
    }

    /**
     * Opens one page, or the screen that offers a choice about it when this game cannot read it.
     *
     * @param index the page, zero-based, clamped into the library
     */
    public static void open(int index) {
        Screen screen = screenFor(index);
        if (screen != null) Minecraft.getInstance().setScreen(screen);
    }

    /**
     * {@return the screen one page opens as}, or null when there is no world to open it in
     *
     * <p>A screen rather than a call to open one, because something has to come back here: the item
     * editor, whose Apply writes into a page and then puts the chest it came from back on screen.
     */
    static Screen screenFor(int index) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return null;
        ChestPages pages = ChestPages.ofRunningGame();
        warnAboutSetAsidePages(pages);
        int page = pages.clamp(index);
        ChestPage loaded = pages.page(page);
        if (!loaded.usable()) return new ChestVersionScreen(pages, page, loaded);
        remembered = page;
        SimpleContainer container = new SimpleContainer(ChestPage.SLOTS);
        ItemStack[] items = loaded.items();
        for (int slot = 0; slot < ChestPage.SLOTS; slot++) {
            container.setItem(slot, items[slot].copy());
        }
        ChestMenu menu = new ChestMenu(minecraft.player.getInventory(), container);
        return new ChestScreen(pages, page, container, menu);
    }

    /** Opens the page one name belongs to; does nothing when no page is called that. */
    public static boolean openNamed(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return false;
        ChestPages pages = ChestPages.ofRunningGame();
        int page = pages.names().pageOf(name);
        if (page < 0) return false;
        open(page);
        return true;
    }


    /**
     * Says once that a page file could not be read in an earlier session, and where it was put.
     *
     * <p>The file is set aside rather than deleted, so the player still has it; the one thing they
     * cannot do from a chat message is guess which folder it went into, so the message opens it.
     */
    private static void warnAboutSetAsidePages(ChestPages pages) {
        if (warnedAboutSetAside) return;
        List<Path> setAside = pages.quarantined();
        if (setAside.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        warnedAboutSetAside = true;
        String folder = pages.folder().toAbsolutePath().toString();
        Component notice = Component.translatable("backutils.client_chest.corrupt", setAside.size())
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, folder))
                        .withUnderlined(true));
        minecraft.player.displayClientMessage(notice, false);
    }
    private static Component titleFor(ChestPages pages, int page) {
        String name = pages.names().nameOf(page);
        Component titled = name.isEmpty()
                ? Component.translatable("backutils.client_chest.unnamed")
                : Component.literal(name);
        boolean creative = Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.isCreative();
        Component title = Component.translatable("backutils.client_chest.title", titled, page + 1);
        return creative ? title
                : title.copy().append(Component.translatable("backutils.client_chest.read_only"));
    }

    @Override
    protected void init() {
        super.init();
        // A rebuild forgets a clear that was waiting for its second press, so the button cannot empty
        // the page on the first press it is given afterwards.
        this.clearing = false;
        // Above the panel: a six-row chest leaves no room inside it, and a page is chosen often
        // enough that burying this in keybinds only would make the library awkward. The base class
        // centres the panel without knowing the strip is there, and a window scaled up four times is
        // the case that comes apart: 264 pixels of height leave a 222-pixel panel 21 above it and the
        // strip needs 22, so the strip used to be pushed below the panel — off the bottom of a window
        // that has no room there either. So the panel moves down to make the room instead, and only a
        // window too short for both keeps the panel where it is and puts the strip over its title row,
        // which is then not drawn.
        int stripY = this.roomForStrip();
        // The box is narrower than it was: the strip is one row and the six buttons have to share it.
        // What is typed is drawn in full in the title, so the box only has to be wide enough to type
        // into and to read a few characters of.
        this.nameBox = this.addRenderableWidget(new EditBox(this.font, this.leftPos + 8, stripY + 1,
                52, 16, Component.translatable("backutils.client_chest.name")));
        this.nameBox.setMaxLength(PageNames.MAX_LENGTH);
        this.nameBox.setValue(this.pages.names().nameOf(this.page));
        this.nameBox.setHint(Component.translatable("backutils.client_chest.name_hint"));
        this.previousInteresting = this.addRenderableWidget(Button.builder(
                        Component.literal("<<"), button -> jump(-1))
                .bounds(this.leftPos + 62, stripY, 18, 18).build());
        this.previousPage = this.addRenderableWidget(Button.builder(
                        Component.literal("<"), button -> step(-1))
                .bounds(this.leftPos + 80, stripY, 18, 18).build());
        this.nextPage = this.addRenderableWidget(Button.builder(
                        Component.literal(">"), button -> step(1))
                .bounds(this.leftPos + 98, stripY, 18, 18).build());
        this.nextInteresting = this.addRenderableWidget(Button.builder(
                        Component.literal(">>"), button -> jump(1))
                .bounds(this.leftPos + 116, stripY, 18, 18).build());
        this.clearPage = this.addRenderableWidget(Button.builder(
                        Component.literal(CLEAR),
                        button -> this.askToClear())
                .bounds(this.leftPos + 134, stripY, 18, 18)
                .tooltip(Tooltip.create(Component.translatable("backutils.client_chest.clear")))
                .build());
        this.reloadPage = this.addRenderableWidget(
                Button.builder(Component.literal(RELOAD), button -> this.reload())
                .bounds(this.leftPos + 152, stripY, 18, 18)
                .tooltip(Tooltip.create(Component.translatable("backutils.client_chest.reload")))
                .build());
        this.refreshButtons();
    }

    /**
     * Moves the page the library is showing, keeping what this one held.
     *
     * <p>Whatever is on the cursor goes back first: a stack on the cursor belongs to the page that is
     * about to stop being shown, and carrying it to another page would be a move nobody asked for.
     */
    private void show(int target) {
        int next = this.pages.clamp(target);
        if (next == this.page) return;
        // A name typed and then scrolled past belongs to the page it was typed on: the box is about
        // to be filled with the next page's name, so what is in it now has to be kept first.
        this.rename();
        // The cursor's stack belongs to the page being left, so it goes back into the container before
        // the page is written: after the write it would be in neither the file nor the page.
        ItemStack carried = this.menu.getCarried();
        if (!carried.isEmpty()) {
            this.menu.setCarried(ItemStack.EMPTY);
            this.container.addItem(carried);
        }
        this.capture();
        this.page = next;
        remembered = next;
        this.clearing = false;
        if (this.clearPage != null) {
            this.clearPage.setMessage(Component.literal(CLEAR));
            this.clearPage.setTooltip(Tooltip.create(
                    Component.translatable("backutils.client_chest.clear")));
        }
        this.load();
    }

    /** Puts the page the library is showing into the menu's container. */
    private void load() {
        ChestPage loaded = this.pages.page(this.page);
        ItemStack[] items = loaded.usable() ? loaded.items() : new ItemStack[ChestPage.SLOTS];
        for (int slot = 0; slot < ChestPage.SLOTS; slot++) {
            this.container.setItem(slot, items[slot] == null ? ItemStack.EMPTY : items[slot].copy());
        }
        this.pageTitle = titleFor(this.pages, this.page);
        if (this.nameBox != null) this.nameBox.setValue(this.pages.names().nameOf(this.page));
        this.refreshButtons();
    }

    /**
     * Copies the container back into the page and writes the page out.
     *
     * <p>On every change, which is what makes a crash cost the last move rather than the whole
     * session: a page is a few kilobytes and the writes are atomic, so what this costs is a file write
     * behind a click.
     */
    private void capture() {
        ChestPage page = this.pages.page(this.page);
        if (!page.usable()) return;
        ItemStack[] items = page.items();
        for (int slot = 0; slot < ChestPage.SLOTS; slot++) {
            items[slot] = this.container.getItem(slot).copy();
            // A slot that now holds an item is no longer the tag of one that could not be read: the
            // two cannot both be true of one slot, and the file would then have to choose.
            if (!items[slot].isEmpty()) page.unlock(slot);
        }
        this.pages.save(this.page);
    }

    private void step(int delta) {
        this.show(this.page + delta);
    }

    private void jump(int direction) {
        int[] nearest = this.pages.nearest(this.page);
        int target = direction < 0 ? nearest[0] : nearest[1];
        if (target >= 0) this.show(target);
    }

    private void refreshButtons() {
        int[] nearest = this.pages.nearest(this.page);
        if (this.previousInteresting != null) this.previousInteresting.active = nearest[0] >= 0;
        if (this.nextInteresting != null) this.nextInteresting.active = nearest[1] >= 0;
        if (this.previousPage != null) this.previousPage.active = this.page > 0;
        if (this.nextPage != null) this.nextPage.active = this.page < this.pages.pageCount() - 1;
    }

    /**
     * Decides whether one click may happen, then runs it without telling the server about a menu.
     *
     * <p>In creative everything is allowed: the page and the inventory may trade items freely, and
     * {@link ChestSync} tells the server what the inventory now holds.
     *
     * <p>Outside it, only a click on a page slot is allowed, and only a plain one. That is exactly
     * "rearrange the library": the cursor can only ever hold something the page gave it, because every
     * click that would have filled it from the player's inventory is refused, so putting it into
     * another page slot cannot smuggle anything in or out. Shift-click, the number keys, double-click
     * gather, the drop key and dragging all cross between the two halves, so all of them are refused.
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType type) {
        int index = slot == null ? slotId : slot.index;
        // A slot whose item could not be read holds nothing to move and nothing to put anything into:
        // the only thing that can be done with it is forgetting the tag, which is Delete.
        if (index >= 0 && index < ChestMenu.PAGE_SLOTS && this.pages.page(this.page).locked(index)) {
            return;
        }
        if (!this.allowed(index, type)) return;
        this.menu.clicked(index, mouseButton, type, this.minecraft.player);
        this.sync.push(this.minecraft.player);
        this.capture();
    }

    private boolean allowed(int index, ClickType type) {
        if (this.minecraft.player != null && this.minecraft.player.isCreative()) return true;
        return type == ClickType.PICKUP && index >= 0 && index < ChestMenu.PAGE_SLOTS;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Two pieces rather than one blit of the whole texture, which is how the game draws a chest:
        // the player's half starts at v=126, a row below where the rest of the sheet ends at 125, and
        // drawing the sheet in one go puts that half a pixel high against the slots it is behind.
        int rows = 6;
        int split = rows * 18 + 17;
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, split);
        graphics.blit(TEXTURE, this.leftPos, this.topPos + split, 0, 126, this.imageWidth,
                this.imageHeight - 126);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The page's own title, drawn here because the one the base class would draw comes from a
        // field that cannot change when the page does. Left out when the controls are over it: the
        // labels are drawn after the widgets, so it would be drawn across the name box.
        if (!this.stripOverTitle) {
            graphics.drawString(this.font, this.pageTitle, this.titleLabelX, this.titleLabelY,
                    4210752, false);
        }
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX,
                this.inventoryLabelY, 4210752, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // A slot holding an item this game cannot read is locked in every mode: there is nothing to
        // draw in it, and the tint is the only thing that says why it cannot be used.
        ChestPage page = this.pages.page(this.page);
        for (int slot : page.lockedSlots()) this.tint(graphics, this.menu.slots.get(slot));
        if (this.minecraft.player != null && !this.minecraft.player.isCreative()) {
            // The other rule made visible: an item moved into these slots would be a picture of an
            // item the server never heard of.
            for (Slot slot : this.menu.slots) {
                if (slot.index >= ChestMenu.PAGE_SLOTS) this.tint(graphics, slot);
            }
        }
        // The base class leaves the hovered item's tooltip to the screen, as every container screen
        // in the game does it for itself, so a screen that forgets says nothing about what an item is.
        this.renderTooltip(graphics, mouseX, mouseY);
        Slot hovered = this.getSlotUnderMouse();
        if (hovered != null && hovered.index < ChestMenu.PAGE_SLOTS && page.locked(hovered.index)) {
            graphics.renderTooltip(this.font,
                    Component.translatable("backutils.client_chest.locked"), mouseX, mouseY);
        }
    }

    /** Draws the red wash that says a slot cannot be used. */
    private void tint(GuiGraphics graphics, Slot slot) {
        graphics.fill(this.leftPos + slot.x, this.topPos + slot.y,
                this.leftPos + slot.x + 16, this.topPos + slot.y + 16, 0x60FF0000);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The inventory key is a letter as well as a key, and somebody naming a page an "Emerald
        // storage" means the letter. Vanilla's anvil screen swallows it for the same reason; without
        // this the chest closes on the first e of the name with half the name typed.
        if (this.nameBox != null && this.nameBox.isFocused()
                && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        // A box being typed into keeps the keyboard: Space and Delete are characters there, and an
        // editor opened on a stray space in a name is the kind of thing that makes a field unusable.
        if (this.nameBox != null && this.nameBox.isFocused() && keyCode != GLFW.GLFW_KEY_ENTER) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            Slot hovered = this.getSlotUnderMouse();
            if (hovered != null && hovered.index < ChestMenu.PAGE_SLOTS
                    && this.pages.page(this.page).unlock(hovered.index)) {
                // Nothing else to do: the slot is empty in the container already, and the file is
                // written without the tag.
                this.pages.save(this.page);
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            Slot hovered = this.getSlotUnderMouse();
            if (hovered != null && hovered.index < ChestMenu.PAGE_SLOTS && hovered.hasItem()
                    && !this.pages.page(this.page).locked(hovered.index)) {
                this.edit(hovered.index);
                return true;
            }
        }
        if (this.nameBox != null && this.nameBox.isFocused() && keyCode == GLFW.GLFW_KEY_ENTER) {
            this.rename();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            if (hasShiftDown()) {
                jump(-1);
            } else {
                step(-1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            if (hasShiftDown()) {
                jump(1);
            } else {
                step(1);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Opens the item editor on one slot of the page.
     *
     * <p>A page is a file on this client, so the edit is applied here rather than sent, and Apply puts
     * the chest back on screen on the page it was on. Space is what opens the editor everywhere else in
     * this mod; a chest slot is the one place it opens a screen whose write does not leave the client.
     *
     * <p>No operator is asked for, and none is needed: nothing here reaches a server, and what an
     * edited item could do is what the chest already allows — a page is taken from in creative, where
     * the game lets a player make any item anyway.
     */
    private void edit(int slot) {
        ItemStack item = this.pages.page(this.page).items()[slot].copy();
        int index = this.page;
        ItemEditorScreen.openLocal(item,
                edited -> {
                    ChestPage target = this.pages.page(index);
                    target.items()[slot] = edited;
                    target.unlock(slot);
                    this.pages.save(index);
                },
                () -> ChestScreen.screenFor(index));
    }

    /**
     * Asks once, then empties the page.
     *
     * <p>Two presses because it is the one control here that throws something away and cannot undo it:
     * the first turns the button into an exclamation mark and says what the next press does, and the
     * second empties every slot — including the unread ones, whose tags are the last copy of those
     * items — and writes the page, which for a page with nothing in it means deleting the file. The
     * first press is reset by anything that looks like changing one's mind: another page, a reload, a
     * clear that happened.
     */
    private void askToClear() {
        if (!this.clearing) {
            this.clearing = true;
            this.clearPage.setMessage(Component.literal(CLEAR_AGAIN));
            this.clearPage.setTooltip(Tooltip.create(
                    Component.translatable("backutils.client_chest.clear_again")));
            return;
        }
        this.clearing = false;
        this.clearPage.setMessage(Component.literal(CLEAR));
        this.clearPage.setTooltip(Tooltip.create(Component.translatable("backutils.client_chest.clear")));
        ChestPage page = this.pages.page(this.page);
        // A page this game cannot use is not this screen's to empty: its file would keep what the
        // screen has just forgotten it held.
        if (!page.usable()) return;
        for (int slot : List.copyOf(page.lockedSlots())) page.unlock(slot);
        for (int slot = 0; slot < ChestPage.SLOTS; slot++) this.container.setItem(slot, ItemStack.EMPTY);
        // Capture writes the empty page out, and an empty page is a page with no file.
        this.capture();
    }

    /**
     * Reads the page from disk again, throwing away what has not been written.
     *
     * <p>The file is the truth: an edit made outside the game is only visible once the page has been
     * read again, and that is what this is for. The names are read again with it, because a name can be
     * edited in the same file's neighbour.
     */
    private void reload() {
        this.clearing = false;
        this.clearPage.setMessage(Component.literal(CLEAR));
        this.clearPage.setTooltip(Tooltip.create(Component.translatable("backutils.client_chest.clear")));
        this.pages.names().load();
        this.pages.forget(this.page);
        this.load();
    }

    /** {@return where the strip goes}, moving the panel down when its centred place leaves no room */
    private int roomForStrip() {
        int strip = STRIP_HEIGHT + STRIP_GAP;
        if (this.topPos < strip) {
            this.topPos = Math.min(strip, Math.max(0, this.height - this.imageHeight));
        }
        this.stripOverTitle = this.topPos < strip;
        return this.stripOverTitle ? Math.max(2, this.topPos) : this.topPos - strip;
    }

    /**
     * Puts the strip where the panel now is.
     *
     * <p>A window resize moves the panel without rebuilding the screen — the base class repositions
     * rather than re-initialises — so a strip placed once in {@code init} would stay where the panel
     * used to be. The room above the panel is decided again here for the same reason: a panel that is
     * centred in a different height may have room above it that it did not have before.
     */
    @Override
    protected void repositionElements() {
        super.repositionElements();
        int stripY = this.roomForStrip();
        if (this.nameBox != null) this.nameBox.setPosition(this.leftPos + 8, stripY + 1);
        if (this.previousInteresting != null) {
            this.previousInteresting.setPosition(this.leftPos + 62, stripY);
        }
        if (this.previousPage != null) this.previousPage.setPosition(this.leftPos + 80, stripY);
        if (this.nextPage != null) this.nextPage.setPosition(this.leftPos + 98, stripY);
        if (this.nextInteresting != null) {
            this.nextInteresting.setPosition(this.leftPos + 116, stripY);
        }
        if (this.clearPage != null) this.clearPage.setPosition(this.leftPos + 134, stripY);
        if (this.reloadPage != null) this.reloadPage.setPosition(this.leftPos + 152, stripY);
    }

    /** Names the page after what is in the name box, or takes its name away when that is empty. */
    private void rename() {
        if (this.nameBox == null) return;
        if (!this.pages.names().set(this.page, this.nameBox.getValue())) return;
        this.pages.names().save();
        this.nameBox.setValue(this.pages.names().nameOf(this.page));
        this.pageTitle = titleFor(this.pages, this.page);
        this.refreshButtons();
    }

    /**
     * Names the page and lets the box go when the click was anywhere else.
     *
     * <p>The base class leaves the focused widget alone: a box that has been clicked into keeps the
     * keyboard for the rest of the visit, so every click on a slot would also edit a name and there
     * would be no way to stop naming the page except by leaving the chest. This is also where a name
     * typed and then clicked away from is kept, which is what makes the box behave the way typing
     * into a field is expected to.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.nameBox != null && this.nameBox.isFocused()
                && !this.nameBox.isMouseOver(mouseX, mouseY)) {
            this.rename();
            this.setFocused(null);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Watches the name box for losing the focus.
     *
     * <p>A name is committed when the box is left as well as when Enter is pressed, because typing a
     * name and clicking back into the chest is what anybody does: a page that still had no name after
     * that is a page whose title goes on saying "Page (3)" while its owner believes otherwise.
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        boolean focused = this.nameBox != null && this.nameBox.isFocused();
        if (this.nameBoxHadFocus && !focused) this.rename();
        this.nameBoxHadFocus = focused;
    }

    @Override
    public void removed() {
        // Typing a name and pressing Escape is a name too, and this is the last moment it can be kept.
        this.rename();
        // The base class is what puts a stack left on the cursor back into the menu`s container, so it
        // has to run before the container is written into the page: captured first, that stack would
        // never reach the file and would go away with the screen.
        super.removed();
        this.capture();
        this.sync.push(this.minecraft.player);
    }
}
