package net.xlebupaksa.backutils.data;

import net.xlebupaksa.backutils.item.EffectToolConfig.Mode;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Face;

/**
 * Where one placed effect sits, in the terms the database and the world agree on.
 *
 * <p>Self placements are never stored, and the type does not represent them. A self effect is
 * attached to the player who made it, so the player's own absence is what ends it: a row would
 * outlive the thing it described and could never be shown again. Placing a self effect therefore
 * leaves nothing behind, which is the honest behaviour rather than a gap.
 *
 * <p>Accurate placements are stored, at the exact point and with the direction they were aimed in.
 * The blank item-display they hang off does not survive a restart, so the point and the direction
 * are what let the effect be put back where the operator put it.
 *
 * <p>The face is carried because a block-side effect is attached to a surface rather than to the
 * world: an effect on a wall stands on its side, and a row that remembered only the position would
 * put it back upright after a restart. It is {@link Face#UP} for every other mode, which is the
 * direction none of them turns away from.
 */
public record EffectPlacement(
        Mode mode,
        String dimension,
        double x, double y, double z,
        float yaw, float pitch,
        Face face
) {

    /** A placement that names no face is taken to hang off the world's own up. */
    public EffectPlacement {
        face = face == null ? Face.UP : face;
    }

    /** {@return a placement at a block, stored in the block grid's own terms} */
    public static EffectPlacement atBlock(String dimension, double x, double y, double z) {
        return new EffectPlacement(Mode.BLOCK, dimension, x, y, z, 0.0F, 0.0F, Face.UP);
    }

    /** {@return a placement on one face of a block, which the rotation is read against} */
    public static EffectPlacement atBlockSide(String dimension, double x, double y, double z,
                                              Face face) {
        return new EffectPlacement(Mode.BLOCK_SIDE, dimension, x, y, z, 0.0F, 0.0F, face);
    }

    /** {@return a placement attached to an entity, stored at where that entity stood} */
    public static EffectPlacement atEntity(String dimension, double x, double y, double z) {
        return new EffectPlacement(Mode.ENTITY, dimension, x, y, z, 0.0F, 0.0F, Face.UP);
    }

    /** {@return a placement in open air, which keeps the direction it was aimed in} */
    public static EffectPlacement atPoint(String dimension, double x, double y, double z,
                                          float yaw, float pitch) {
        return new EffectPlacement(Mode.ACCURATE, dimension, x, y, z, yaw, pitch, Face.UP);
    }

    /** {@return true when this placement is one the database is allowed to hold} */
    public static boolean storable(Mode mode) {
        return mode == Mode.BLOCK || mode == Mode.BLOCK_SIDE || mode == Mode.ENTITY
                || mode == Mode.ACCURATE;
    }

    /** {@return true when this placement sits on the block grid rather than in open air} */
    public boolean onGrid() {
        return mode != Mode.ACCURATE;
    }

    /**
     * {@return the same position as an exact point}, which the renderer and the effect both need
     *
     * <p>A block placement is stored as the block's low corner, so the middle of the block is what a
     * cube is drawn around and what an effect is offset from. An accurate placement is already the
     * point itself.
     */
    public double centreX() {
        return onGrid() ? x + 0.5D : x;
    }

    public double centreY() {
        return onGrid() ? y + 0.5D : y;
    }

    public double centreZ() {
        return onGrid() ? z + 0.5D : z;
    }

    /**
     * {@return the block this placement occupies}
     *
     * <p>Flooring rather than rounding: a placement is stored at a block's low corner, and a
     * negative coordinate must floor to the block that contains it, which rounding would get wrong.
     */
    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    /** {@return a short description, for command output} */
    public String describe() {
        return mode + " at " + trim(centreX()) + " " + trim(centreY()) + " " + trim(centreZ())
                + " in " + dimension;
    }

    private static String trim(double value) {
        return value == Math.floor(value)
                ? String.valueOf((long) value)
                : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
