package net.xlebupaksa.backutils.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * What clicking, ctrl-clicking and shift-clicking a row mean, in one place.
 *
 * <p>Written as plain values rather than inside a screen because three lists now select the same way
 * and the rule is easy to get subtly wrong. A shift-click is a range in the order the list is
 * <em>drawn</em>, which after a filter is not the order the rows were loaded in; the anchor is the
 * last row clicked without a shift rather than the last row touched; and a ctrl-click that removes
 * the last selected row leaves nothing selected rather than an id pointing at nothing.
 *
 * <p>Ids are kept as values and never as indexes. A refresh can remove a row, and an index kept
 * across one would silently move the selection to the row below it.
 */
final class MultiSelection {

    /** Selected ids, oldest first. A list rather than a set so a range keeps its drawn order. */
    private final List<Long> selected = new ArrayList<>();

    /** The row the options panel is about: the last one clicked, while it is still selected. */
    private long primary;
    private boolean hasPrimary;

    /** Where a shift-click starts, which a shift-click does not move. */
    private long anchor;
    private boolean hasAnchor;

    /** {@return true when the row with this id is selected} */
    boolean contains(long id) {
        return selected.contains(id);
    }

    /** {@return the selected ids in the order they were added} */
    List<Long> ids() {
        return List.copyOf(selected);
    }

    int size() {
        return selected.size();
    }

    boolean isEmpty() {
        return selected.isEmpty();
    }

    /** {@return the row the options are about}, or 0 when nothing is selected */
    long primary() {
        return hasPrimary ? primary : 0L;
    }

    /**
     * Applies one click.
     *
     * @param id        the clicked row
     * @param ctrl      whether ctrl is held, which toggles the row instead of replacing the selection
     * @param shift     whether shift is held, which selects from the anchor to this row
     * @param displayed every id the list is drawing, in drawing order
     */
    void click(long id, boolean ctrl, boolean shift, List<Long> displayed) {
        if (shift && hasAnchor) {
            int from = displayed.indexOf(anchor);
            int to = displayed.indexOf(id);
            if (from >= 0 && to >= 0) {
                int low = Math.min(from, to);
                int high = Math.max(from, to);
                // Ctrl + shift adds the range to what is already selected rather than replacing it,
                // which is the other half of the convention every list in a desktop program follows.
                if (!ctrl) selected.clear();
                for (int i = low; i <= high; i++) {
                    long candidate = displayed.get(i);
                    if (!selected.contains(candidate)) selected.add(candidate);
                }
                primary = id;
                hasPrimary = true;
                // The anchor stays put: a second shift-click extends from where the first one began.
                return;
            }
        }

        if (ctrl) {
            if (!selected.remove(Long.valueOf(id))) selected.add(id);
            if (selected.contains(id)) {
                primary = id;
                hasPrimary = true;
            } else if (!selected.isEmpty()) {
                primary = selected.getLast();
                hasPrimary = true;
            } else {
                hasPrimary = false;
            }
            anchor = id;
            hasAnchor = true;
            return;
        }

        selected.clear();
        selected.add(id);
        primary = id;
        hasPrimary = true;
        anchor = id;
        hasAnchor = true;
    }

    /** Forgets everything, which a list that moved to another player or was closed requires. */
    void clear() {
        selected.clear();
        hasPrimary = false;
        hasAnchor = false;
    }

    /**
     * Re-resolves the selection after a refresh.
     *
     * <p>Rows that are still drawn stay selected and rows that are gone drop out. An anchor that has
     * gone is forgotten too: a shift-click from a row that is nowhere to be seen would read as the
     * range starting at random.
     */
    void retainAll(Collection<Long> drawn) {
        selected.retainAll(drawn);
        if (hasPrimary && !drawn.contains(primary)) {
            if (selected.isEmpty()) {
                hasPrimary = false;
            } else {
                primary = selected.getLast();
            }
        }
        if (hasAnchor && !drawn.contains(anchor)) hasAnchor = false;
    }
}
