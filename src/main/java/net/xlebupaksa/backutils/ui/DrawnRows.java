package net.xlebupaksa.backutils.ui;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One line of markup text, and the components its rows are drawn with.
 *
 * <p>A row's text does not change between frames, so its component is built once and handed back
 * from then on. Ember parses a literal's markup as that literal is drawn, and hangs state off what
 * the parse produced — a random id on every {@code LiteralContents}, and animation tracks in
 * {@code TypewriterTracks} and {@code ObfuscateTracks} keyed on objects from the parse — so a
 * component rebuilt inside the draw call re-parses the same text sixty times a second and gives that
 * state a new identity each time. Kept, the text is parsed once and the state has somewhere to live.
 *
 * <p>Built on first use rather than up front, so that an effect starts when the row is first shown
 * rather than when the line arrived: the log reveals its rows one at a time, and a row waiting its
 * turn must not spend that time typing itself out unwatched.
 */
final class DrawnRows {

    private final List<String> rows;
    private final Component[] drawn;

    DrawnRows(List<String> rows) {
        this.rows = List.copyOf(rows);
        this.drawn = new Component[this.rows.size()];
    }

    /** {@return how many rows this line wraps into} */
    int size() {
        return rows.size();
    }

    /** {@return the markup of one row, which is what it is measured and stripped as} */
    String row(int index) {
        return rows.get(index);
    }

    /** {@return the component for one row, built on first use and the same one from then on} */
    Component component(int index) {
        Component kept = drawn[index];
        if (kept == null) {
            kept = Component.literal(rows.get(index));
            drawn[index] = kept;
        }
        return kept;
    }
}
