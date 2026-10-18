package net.xlebupaksa.backutils.clientchest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What a page from another game version is offered instead of its items.
 *
 * <p>Item data is version specific, so a page that was written by a different version is a file this
 * game must not read as items. It is not a file to delete either — a library is a player's work — so
 * the page is opened here and one decision is asked for: stamp it as this version and use it, or throw
 * it away. Discarding takes two clicks, because it is the only button on the screen that loses
 * something.
 *
 * <p>A page from a <em>newer</em> version is never offered the stamp. Reading data a newer game wrote
 * is the corruption the gate exists to prevent, and stamping it would be that corruption with a
 * receipt.
 */
public final class ChestVersionScreen extends Screen {

    private final ChestPages pages;
    private final int page;
    private final ChestPage refused;

    private boolean confirmingDiscard;

    public ChestVersionScreen(ChestPages pages, int page, ChestPage refused) {
        super(Component.translatable("backutils.client_chest.version.title"));
        this.pages = pages;
        this.page = page;
        this.refused = refused;
    }

    @Override
    protected void init() {
        int centre = this.width / 2;
        int top = this.height / 2 + 24;
        Button importPage = Button.builder(
                        Component.translatable("backutils.client_chest.version.import"),
                        button -> this.importPage())
                .bounds(centre - 154, top, 150, 20).build();
        importPage.active = this.refused.status() != ChestPage.Status.NEWER_VERSION;
        this.addRenderableWidget(importPage);
        this.addRenderableWidget(Button.builder(
                        Component.translatable("backutils.client_chest.version.discard"),
                        button -> this.discard())
                .bounds(centre + 4, top, 150, 20).build());
        this.explanation = buildExplanation();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The base class draws the background itself; drawing it here as well darkened the screen twice.
        int centre = this.width / 2;
        int line = this.height / 2 - 40;
        graphics.drawCenteredString(this.font,
                Component.translatable("backutils.client_chest.version.heading", this.page + 1),
                centre, line, 0xFFFFFF);
        for (Component paragraph : this.explanation == null
                ? java.util.List.<Component>of() : this.explanation) {
            graphics.drawCenteredString(this.font, paragraph, centre, line + 20, 0xA0A0A0);
            line += 12;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** What to say about this page: built once, because it is the same every frame. */
    private java.util.List<Component> explanation;

    /** {@return what to say about this page}, a line at a time so long sentences can be split */
    private java.util.List<Component> buildExplanation() {
        int written = this.refused.dataVersion();
        int current = ChestPage.currentDataVersion();
        return switch (this.refused.status()) {
            case MISSING_VERSION -> java.util.List.of(
                    Component.translatable("backutils.client_chest.version.missing.one"),
                    Component.translatable("backutils.client_chest.version.missing.two"));
            case OLDER_VERSION -> java.util.List.of(
                    Component.translatable("backutils.client_chest.version.older.one", written, current),
                    Component.translatable("backutils.client_chest.version.older.two"));
            case NEWER_VERSION -> java.util.List.of(
                    Component.translatable("backutils.client_chest.version.newer.one", written, current),
                    Component.translatable("backutils.client_chest.version.newer.two"));
            case CURRENT -> java.util.List.of();
        };
    }

    /** Takes the page on as this version's, and opens it. */
    private void importPage() {
        if (!this.pages.importVersion(this.page)) return;
        this.back();
    }

    private void discard() {
        if (!this.confirmingDiscard) {
            this.confirmingDiscard = true;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("backutils.client_chest.version.discard_again"), false);
            }
            return;
        }
        this.pages.discard(this.page);
        this.back();
    }

    private void back() {
        Minecraft minecraft = Minecraft.getInstance();
        int page = this.page;
        minecraft.setScreen(null);
        ChestScreen.open(page);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
