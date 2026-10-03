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
        final float top = Math.max(0, pos.y - dialogHeight);
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