package net.xlebupaksa.backutils.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * The look the administrator's lists share: the row colours, the tick box and the sprite that says a
 * line is withheld.
 *
 * <p>One place because three lists now exist — the action log, a player's memory and the client's own
 * memory — and a marker drawn in one of them at a different size, or a copied row lit in another
 * colour, would read as a different state rather than the same one.
 */
final class AdminListStyle {

    /**
     * The marker drawn in front of a line an administrator would not normally be shown, and beside
     * each witness it is withheld from. A sprite rather than a glyph, because Minecraft's fonts carry
     * no emoji and the obvious ones rendered as an empty box.
     */
    static final ResourceLocation HIDDEN_MARKER =
            ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "menu/hidden_action");
    /** Drawn at the sprite's own size, which is the height a row of these lists has room for. */
    static final int HIDDEN_MARKER_SIZE = 10;

    static final int ROW_IDLE = 0x44101016;
    static final int ROW_HOVER = 0x883A4658;
    static final int ROW_SELECTED = 0xAA4E7FB8;
    /** What a row is lit with while its copied acknowledgement is playing. */
    static final int ROW_COPIED = 0xAA4E7FB8;

    static final int MUTED = 0xFF9AA3B2;
    static final int PLAIN = 0xFFE2E8F0;
    static final int MARKER_GREY = 0xFF8A8A8A;

    /** How long a copied row stays lit, and how quickly it alternates. */
    static final long BLINK_NANOS = 420_000_000L;
    static final long BLINK_PHASE_NANOS = 70_000_000L;

    /** {@return the tick box in front of a row}, the same in every list that has one */
    static Component checkbox(boolean ticked) {
        return Component.literal(ticked ? "[x] " : "[ ] ");
    }

    private AdminListStyle() {}
}
