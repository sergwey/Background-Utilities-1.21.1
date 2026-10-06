package net.xlebupaksa.backutils.ui;

import net.minecraft.network.chat.Component;

/**
 * Looks up a localised string for the widgets that take plain text.
 *
 * <p>ldlib2's labels and buttons are handed a {@code String}, which is resolved when the widget is
 * built rather than when it is drawn, so a translated component has to be flattened here. That is
 * still the language of the client that is about to draw it: every one of these menus is built
 * client-side, from what the server sent, and never on the server.
 *
 * <p>Where a widget or a drawing call takes a component, use {@code Component.translatable}
 * directly instead, so nothing is flattened a moment before it is needed.
 */
public final class Text {

    private Text() {}

    /**
     * {@return the text for a key, in the language this client is set to}
     *
     * @param args values for the {@code %s} placeholders in the translation
     */
    public static String of(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
