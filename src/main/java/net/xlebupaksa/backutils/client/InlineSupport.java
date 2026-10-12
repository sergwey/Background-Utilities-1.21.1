package net.xlebupaksa.backutils.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Inline, reached reflectively, for the text this mod draws with the game's own font.
 *
 * <p><b>Why the text has to be handed over at all.</b> Inline's matchers - {@code [item:diamond_sword]},
 * {@code [entity:pig]} and the rest - are applied where a plain {@code String} is decomposed, which is
 * where Inline hooks {@code StringDecomposer}. A {@code Component} never goes that way: its visual
 * order is built from its own contents, and Ember parses a <em>literal</em> component as it draws it,
 * so the banner has to be drawn as a component for markup to work. Inline's component route is the
 * other half of its API, and the matches it finds carry their data on {@code Style}s.
 *
 * <p><b>Assembled here rather than asked for.</b> {@code MatchContext#getFinalStyledText} is the call
 * that would do this, and on Inline 1.21.1-1.2.2 it throws a {@code StringIndexOutOfBoundsException}
 * for a text holding more than one match. The pieces it would have assembled from are public API -
 * {@code getFinalText}, {@code getFinalMatches}, {@code InlineMatch#charLength} and the {@code data}
 * field of {@code InlineMatch$DataMatch} - so the assembly happens here, where a failure is a text
 * drawn without symbols rather than a frame that throws.
 *
 * <p><b>Reached reflectively, and that is a decision rather than laziness.</b> Inline is LGPL-3.0,
 * compatible with this mod's GPL-3.0, but bundling it would carry obligations there is no reason to
 * take on, and a compile-time reference would make it a dependency of a mod that has to load without
 * it. So the seam is reflection, {@link #styled(String)} is all a caller needs, and a text with no
 * inline syntax in it is drawn exactly as it was before Inline was installed.
 */
@OnlyIn(Dist.CLIENT)
public final class InlineSupport {

    private static final String MOD_ID = "inline";
    private static final String CLIENT_API = "com.samsthenerd.inline.api.client.InlineClientAPI";
    private static final String MATCH_CONTEXT = "com.samsthenerd.inline.api.matching.MatchContext";
    private static final String INLINE_MATCH = "com.samsthenerd.inline.api.matching.InlineMatch";
    private static final String DATA_MATCH = "com.samsthenerd.inline.api.matching.InlineMatch$DataMatch";
    private static final String INLINE_DATA = "com.samsthenerd.inline.api.InlineData";

    /** Looked up once: the shape of Inline cannot change while the game is running. */
    private static boolean looked;
    private static Object clientApi;
    private static Method getMatched;
    private static Method freeze;
    private static Method getFinalMatches;
    private static Method getFinalText;
    private static Method charLength;
    private static Method asStyle;
    private static Field matchData;
    private static Field matchStyle;

    private InlineSupport() {}

    /** {@return true when Inline is installed and its parse could be reached} */
    public static boolean present() {
        resolve();
        return clientApi != null;
    }

    /**
     * {@return the text with Inline's matches carried on its styles}
     *
     * <p>The plain literal when Inline is absent, when it has changed shape, when nothing in the text
     * matched, or when the assembly failed. That last case is the one that matters for how this is
     * built: a text with no inline symbols in it, which is most of them, comes back byte for byte the
     * component this mod drew before the seam existed.
     */
    public static Component styled(String text) {
        Component plain = Component.literal(text);
        resolve();
        if (clientApi == null || getFinalText == null || asStyle == null || matchData == null) {
            return plain;
        }
        try {
            Object context = getMatched.invoke(clientApi, text);
            if (context == null) return plain;
            // Frozen before it is read: a context is matches being added to, and its finished form is
            // only meaningful once it has been closed.
            freeze.invoke(context);

            Object matches = getFinalMatches.invoke(context);
            if (!(matches instanceof Map<?, ?> map) || map.isEmpty()) return plain;
            String finalText = (String) getFinalText.invoke(context);
            if (finalText == null || finalText.isEmpty()) return plain;

            return assemble(finalText, map);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // A different Inline, or one that failed at this text. Nothing here is worth an error in
            // the log: the fallback is a working banner whose symbols are missing, not a broken one.
            return plain;
        }
    }

    /**
     * {@return the final text, with each matched run carrying the style that says what to draw there}
     *
     * <p>The matches are keyed by where they start in the final text and know how long they are, so
     * the text is walked in runs: everything up to the next match is one literal, and a match is one
     * literal carrying its data. Runs rather than characters because a component per character would
     * be a component per character, and because Ember parses a literal as a whole.
     */
    private static Component assemble(String finalText, Map<?, ?> matches) throws ReflectiveOperationException {
        MutableComponent assembled = Component.empty();
        int index = 0;
        while (index < finalText.length()) {
            if (matches.get(index) == null) {
                int next = index + 1;
                while (next < finalText.length() && matches.get(next) == null) next++;
                assembled.append(Component.literal(finalText.substring(index, next)));
                index = next;
                continue;
            }

            Object match = matches.get(index);
            int length = Math.max(1, ((Number) charLength.invoke(match)).intValue());
            length = Math.min(length, finalText.length() - index);

            MutableComponent piece = Component.literal(finalText.substring(index, index + length));
            Object data = matchData.get(match);
            if (data != null) {
                // asStyle(false): the style that draws the data, without Inline's extra styling - its
                // hover and click events are what the flag is for.
                Style style = (Style) asStyle.invoke(data, false);
                // The match's own style is applied on top, which is where the size modifier lives: a
                // separator other than ':' - ',' for smaller, '!' for larger, '+' for largest - is
                // carried there rather than in the data. Inline does the same in InlineMatch.accept,
                // and a match drawn without it is a match at the default size, which is what made
                // '+' look as though it had done nothing but move the text.
                Object extra = matchStyle.get(match);
                piece.withStyle(extra instanceof Style matchStyle ? matchStyle.applyTo(style) : style);
            }
            assembled.append(piece);
            index += length;
        }
        return assembled;
    }

    /** Resolves the API instance, the two methods and the field, once, if Inline is loaded at all. */
    private static void resolve() {
        if (looked) return;
        looked = true;
        if (ModList.get() == null || !ModList.get().isLoaded(MOD_ID)) return;
        try {
            Class<?> api = Class.forName(CLIENT_API);
            Class<?> context = Class.forName(MATCH_CONTEXT);
            Class<?> match = Class.forName(INLINE_MATCH);

            clientApi = api.getField("INSTANCE").get(null);
            getMatched = api.getMethod("getMatched", String.class);
            freeze = context.getMethod("freeze");
            getFinalMatches = context.getMethod("getFinalMatches");
            getFinalText = context.getMethod("getFinalText");
            charLength = match.getMethod("charLength");
            asStyle = Class.forName(INLINE_DATA).getMethod("asStyle", boolean.class);
            matchData = Class.forName(DATA_MATCH).getField("data");
            matchStyle = Class.forName(DATA_MATCH).getField("style");
        } catch (ReflectiveOperationException | RuntimeException e) {
            clientApi = null;
        }
    }
}
