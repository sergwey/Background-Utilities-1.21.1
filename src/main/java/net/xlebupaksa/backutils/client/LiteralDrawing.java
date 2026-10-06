package net.xlebupaksa.backutils.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.lang.reflect.Method;

/**
 * Ember's "draw this text as it is" switch, borrowed while the item editor's two fields are drawn.
 *
 * <p>The fields are widgets that draw what they hold, and what they hold is markup. The library builds a
 * literal component out of the text of a field, and Ember parses <em>any</em> literal component that
 * contains markup as it draws it — so without this the tags are consumed as they are typed and a field
 * shows the formatted words rather than the words themselves, which is exactly what an operator typing
 * markup cannot have.
 *
 * <p><b>Reached reflectively, deliberately.</b> The switch lives in Ember's Patchouli compatibility package,
 * which is not an API and may be moved by a release that has nothing to do with this mod. This mod requires
 * Ember, so the class is not expected to be missing — but a field that cannot be drawn is a screen that
 * cannot be opened, and a compat class that has moved must cost the operator nothing more than seeing the
 * tags rendered. With it absent, every call here answers nothing and the editor works as it did before.
 *
 * <p>Ember's switch counts entries rather than setting a flag, so nesting is safe and the pair of calls
 * below has to be balanced — which is why they are used from a finally block.
 */
@OnlyIn(Dist.CLIENT)
public final class LiteralDrawing {

    /** Ember's switch, by the name a later release is most likely to keep the behaviour under. */
    private static final String BYPASS = "net.tysontheember.emberstextapi.compat.patchouli.PatchouliBypass";

    /** Resolved once: this runs twice for every frame the editor is open. */
    private static Method enter;
    private static Method exit;
    private static boolean looked;

    private LiteralDrawing() {}

    /** Draws text as it is written until {@link #end}, rather than as markup. */
    public static void begin() {
        invoke(enter());
    }

    /** Undoes one {@link #begin}. */
    public static void end() {
        invoke(exit());
    }

    private static Method enter() {
        look();
        return enter;
    }

    private static Method exit() {
        look();
        return exit;
    }

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> bypass = Class.forName(BYPASS);
            enter = bypass.getMethod("enter");
            exit = bypass.getMethod("exit");
        } catch (Throwable absentOrMoved) {
            enter = null;
            exit = null;
        }
    }

    private static void invoke(Method method) {
        if (method == null) return;
        try {
            method.invoke(null);
        } catch (Throwable refused) {
            // Nothing to do: the text is drawn the way it was before, which is a screen that still works.
        }
    }
}
