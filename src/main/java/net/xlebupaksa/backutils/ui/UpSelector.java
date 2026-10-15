package net.xlebupaksa.backutils.ui;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import net.xlebupaksa.backutils.BackUtils;
import org.joml.Vector2f;

import java.lang.reflect.Field;

/** A Selector whose candidate list opens above the display bar, since the chat input is below. */
public class UpSelector<T> extends Selector<T> {

    private static final Field ANCHOR_X = findField("dialogAnchorX");
    private static final Field ANCHOR_Y = findField("dialogAnchorY");

    static {
        if (ANCHOR_X == null || ANCHOR_Y == null) {
            BackUtils.LOGGER.warn("ldlib2's Selector no longer exposes dialogAnchorX/dialogAnchorY; "
                    + "the profile dropdown may open in the wrong place. Check the bundled ldlib2 version.");
        }
    }

    private static Field findField(String name) {
        try {
            Field f = Selector.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    @Override
    protected void updateDialogPosition() {
        var mui = getModularUI();
        if (mui == null) return;

        var root = mui.ui.rootElement;
        var worldPos = this.localToWorld(new Vector2f(getPositionX(), getPositionY()));
        var pos = root.worldToLocalLayoutOffset(worldPos);

        float dialogHeight = this.dialog.getSizeHeight();
        // Kept on the screen here rather than left to the library's own adaptPositionToScreen, which
        // answers the same layout change this does: a list hung over an edge was pulled back on screen
        // by that one and put back over the edge by this one, pass after pass, which is a tree dirtied
        // inside the frame's own layout - the library's "dirty for more than 10 times per frame".
        // Landing where adaptPositionToScreen would leave it is what makes the two agree.
        final float top = Math.max(0,
                Math.min(pos.y - dialogHeight, mui.getScreenHeight() - dialogHeight));
        final float left = pos.x;
        final float width = Math.max(this.getSizeWidth(), 50);

        this.dialog.layout(layout -> {
            layout.left(left);
            layout.top(top);
            layout.width(width);
        });

        try {
            if (ANCHOR_X != null) ANCHOR_X.setFloat(this, getPositionX());
            if (ANCHOR_Y != null) ANCHOR_Y.setFloat(this, getPositionY());
        } catch (IllegalAccessException ignored) {}
    }
}