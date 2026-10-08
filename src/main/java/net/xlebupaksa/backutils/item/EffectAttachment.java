package net.xlebupaksa.backutils.item;

import net.xlebupaksa.backutils.data.EffectPlacement;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Everything one effect is placed with, as plain values, worked out before anything is spawned.
 *
 * <p>Kept apart from the effect library on purpose. Which executor an effect needs, what its
 * auto-rotation resolves to, and what rotation it hangs at are decisions about <em>our</em>
 * configuration, and they are worth being able to check without a renderer, a world or a running
 * game. The class that talks to Photon takes one of these and does nothing but pass the values on.
 *
 * <p>The kind is stated rather than derived at the far end: an effect hangs off a block, an entity,
 * or a free point, and a caller that had to work that out from the mode again would be a second
 * place for the two to disagree.
 *
 * <p>The rotation is a quaternion rather than the three angles the screen takes, because the angles
 * are the operator's way of saying it and this is the library's way of being told. It is the same
 * rotation {@link EffectToolPlacement#quaternion} builds for the up-direction line, so the line the
 * operator sees and the effect they get cannot point different ways.
 *
 * @param allowMulti whether the effect may be started where the same effect already runs; true for
 *                   every tool, so that two placements are two effects rather than one — see
 *                   {@link #of} for why the library's own answer is not taken
 * @param lifetimeTicks the configured life of the effect, which the caller schedules the ending of:
 *                      the library has no lifetime of its own, so a placement that is to be removed
 *                      on time is removed by this mod
 */
public record EffectAttachment(
        String effectPath,
        Kind kind,
        EffectToolConfig.AutoRotate autoRotate,
        double x, double y, double z,
        Vector3f scale,
        Quaternionf rotation,
        Vector3f offset,
        int delayTicks,
        boolean forceDeath,
        boolean allowMulti,
        int lifetimeTicks
) {

    /** What an effect hangs off, which is what decides the executor it needs. */
    public enum Kind {

        /** A block, named by the block's low corner. */
        BLOCK,
        /**
         * The holder, attached to the player rather than to a place.
         *
         * <p>Carried rather than refused: a self effect is a real thing to place, and it is only
         * the <em>storing</em> of one that is refused, because the player's absence ends it.
         */
        SELF,
        /** An entity, named by the entity it was aimed at rather than by a position. */
        ENTITY,
        /** A free point in the world, which is what an accurate placement uses. */
        POINT
    }

    /**
     * The scale an effect is given when nothing was chosen.
     *
     * <p>One, not zero: a zero scale is a real and useless setting an operator can type, so it
     * cannot double as "unset", and the plainest effect is one at its own size.
     */
    public static final Vector3f DEFAULT_SCALE = new Vector3f(1.0F, 1.0F, 1.0F);

    /**
     * Ensures the mutable JOML values are this record's own.
     *
     * <p>A quaternion and a vector are handed to the effect library, which is entitled to turn them
     * about; copying on the way in keeps a stored configuration from being rotated by the act of
     * placing it.
     */
    public EffectAttachment {
        scale = scale == null ? new Vector3f(DEFAULT_SCALE) : new Vector3f(scale);
        offset = offset == null ? new Vector3f() : new Vector3f(offset);
        rotation = rotation == null ? new Quaternionf() : new Quaternionf(rotation);
    }

    /**
     * {@return where the effect is, given where the thing it hangs off is}
     *
     * <p>The library's own sum, and the one every drawing of an effect has to make: it adds this
     * offset to the anchor it was handed — the middle of a block for a block effect, the entity's own
     * eye for one attached to an entity — so an effect's position is its anchor plus this offset and
     * nothing else.
     *
     * <p>The anchor is taken as three numbers rather than read from {@link #x}, {@link #y} and
     * {@link #z}, because those are only where the effect was <em>planned</em>. An accurate effect
     * hangs off a blank display the world holds, and a display is an entity that anything may move:
     * the effect follows it, so "where is this effect" is a question to be asked of the display as it
     * is now. Both ends ask it here — the client that draws the cube and the server that removes the
     * place — so that the two cannot be answers that disagree.
     *
     * @param x the anchor's own x: the display's position for a moved accurate effect, and this
     *          attachment's own for everything else
     */
    public EffectToolPlacement.Point at(double x, double y, double z) {
        return new EffectToolPlacement.Point(x + offset.x(), y + offset.y(), z + offset.z());
    }

    /** {@return where the effect was planned to be}, which is the anchor it was attached at */
    public EffectToolPlacement.Point at() {
        return at(x, y, z);
    }

    /**
     * {@return what to place for a configuration and the point it goes to}
     *
     * <p>{@code placement} is null for a self effect and only for one: every other mode names a
     * place, and one that did not would be a placement with nowhere to go.
     */
    public static EffectAttachment of(EffectToolConfig config, EffectPlacement placement) {
        EffectToolConfig.Mode mode = config.mode();
        Kind kind = switch (mode) {
            case BLOCK, BLOCK_SIDE -> Kind.BLOCK;
            case ENTITY, ENTITY_AUTO_ROTATE -> Kind.ENTITY;
            case SELF, SELF_AUTO_ROTATE -> Kind.SELF;
            default -> Kind.POINT;
        };

        double x = placement == null ? 0.0D : placement.centreX();
        double y = placement == null ? 0.0D : placement.centreY();
        double z = placement == null ? 0.0D : placement.centreZ();

        return new EffectAttachment(
                config.effectPath(),
                kind,
                // Resolved through the one place a mode and a stored value become an auto-rotation,
                // so a mode that is not an auto-rotating one cannot quietly acquire a turn.
                EffectToolPlacement.autoRotateFor(config),
                x, y, z,
                vectorOf(config.scale()),
                // The face first and the settings' own rotation after it, which is the composition
                // the up-direction line is drawn from: an effect on a wall stands on its side, and
                // the angles are read against the surface rather than against the world.
                EffectToolPlacement.attachmentQuaternion(
                        placement == null ? null : placement.face(), config.rotation()),
                offsetOf(config, placement, mode),
                config.delayTicks(),
                config.forceDeath(),
                // Every effect may be started more than once, which is a decision rather than the
                // library's default. Without it the library drops a start when the same effect is
                // already running on the same block or the same entity — and it decides that by
                // which effect it is, not by how it was set up. Two flames a block apart in size and
                // rotation are two flames to an operator and one to the library, so the second
                // simply would not appear, which reads as the tool having stopped working. Nothing
                // is lost by allowing them: the delete tool removes by place, and every placement is
                // a row of its own either way.
                true,
                config.lifetimeTicks());
    }

    /**
     * {@return true when this attachment can be acted on}
     *
     * <p>A tool whose effect path is empty describes no effect, and a placement for one would be a
     * request to place nothing. The screen already refuses to apply such a tool, so this is the guard
     * for everything that reaches placing another way.
     */
    public boolean placeable() {
        return effectPath != null && !effectPath.isBlank() && kind != null;
    }

    /**
     * {@return the effect path as an id, or null when it is not one}
     *
     * <p>{@code photon:fire} is a path; {@code photon fire} and {@code :} are not. The operator types
     * this by hand, so a malformed one is an ordinary mistake rather than an exceptional state, and
     * answering with nothing is how it is reported — the effect is simply not placed.
     */
    public net.minecraft.resources.ResourceLocation location() {
        if (!placeable()) return null;
        return net.minecraft.resources.ResourceLocation.tryParse(effectPath.trim().toLowerCase(Locale.ROOT));
    }

    /** {@return a configuration's three values as the vector the effect library takes} */
    private static Vector3f vectorOf(EffectToolConfig.Triplet triplet) {
        return new Vector3f((float) triplet.x(), (float) triplet.y(), (float) triplet.z());
    }

    /**
     * {@return how far the effect is moved from the middle of the block it hangs on}
     *
     * <p>A block-side placement is attached to a surface rather than to the block, and the library
     * measures an offset <b>from the middle of the block</b> — read out of
     * {@code BlockEffectExecutor.start}, which adds the offset to the block position plus a half on
     * every axis.
     *
     * <p>Both the offset the operator typed and the half block that puts the effect on the surface
     * are read <b>in the surface's own frame</b>, where <b>+y is straight out of the face</b> towards
     * whoever is looking at it, +z runs along the face upwards and +x across it. That is what makes
     * an offset of {@code 0 1 0} mean "a block out of the wall, towards me" on every face rather than
     * "a block up the world", which is the difference between an effect hanging off a wall and one
     * floating beside it. The frame is the one the effect's own rotation is read against — the same
     * {@link EffectToolPlacement#faceQuaternion} — so the surface, the offset and the effect's up all
     * agree.
     *
     * <p>The offset is turned by the face and <b>not</b> by the effect's own rotation: a nudge is a
     * place on the surface, and spinning the effect about that place must not spin the place with it.
     *
     * <p>Public because the preview is drawn from it. What the small cube is centred on is this
     * offset added to the point the placement was planned at, which is the same sum the effect
     * itself is handed — so the cube an operator sees before placing and the marker left behind
     * afterwards cannot land in different spots, because they are one arithmetic read twice.
     */
    public static Vector3f offsetOf(EffectToolConfig config, EffectPlacement placement,
                                    EffectToolConfig.Mode mode) {
        Vector3f offset = vectorOf(config.offset());
        if (mode != EffectToolConfig.Mode.BLOCK_SIDE) return offset;

        EffectToolPlacement.Face face =
                placement == null ? EffectToolPlacement.Face.UP : placement.face();
        float half = (float) (EffectToolPlacement.CUBE_SIZE / 2.0D);
        return offset.add(0.0F, half, 0.0F).rotate(EffectToolPlacement.faceQuaternion(face));
    }
}
