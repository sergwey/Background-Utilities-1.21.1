package net.xlebupaksa.backutils.network;

/**
 * The banner text the server last sent, and the only source the banner draws from.
 *
 * <p>Client-side state with no Minecraft imports, like {@link AdminConfigCache}, so the class the
 * banner reads cannot drag a screen or a renderer into whatever asks.
 *
 * <p>The revision moves once per real change rather than once per payload: the server sends this on
 * every join and after every batch of settings, most of which have nothing to do with the banner,
 * and the components the rows are drawn from carry the effects other mods hang off them - so a text
 * that is already drawn must not be rebuilt for being resent.
 */
public final class BannerTextCache {

    private static volatile String text = "";
    private static volatile int revision;

    private BannerTextCache() {}

    /** {@return the text the server last sent}, empty when the banner is switched off */
    public static String text() {
        return text;
    }

    /** {@return how many changes have arrived}, which is what a rebuild is keyed on */
    public static int revision() {
        return revision;
    }

    public static void set(String value) {
        String clean = value == null ? "" : value;
        if (clean.equals(text)) return;
        text = clean;
        revision++;
    }

    /** Forgets the server just left, so its banner is not drawn over the next one's. */
    public static void clear() {
        text = "";
        revision++;
    }
}
