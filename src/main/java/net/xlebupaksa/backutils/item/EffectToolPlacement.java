package net.xlebupaksa.backutils.item;

import net.xlebupaksa.backutils.item.EffectToolConfig.AutoRotate;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Where an effect tool would put its effect, as arithmetic on plain numbers.
 *
 * <p>Kept free of Minecraft types on purpose. The screen, the preview and the placement itself all
 * ask the same questions — which block, how far, which way — and an answer built out of a
 * {@code Vec3} cannot be checked without a running client. Everything here takes and returns
 * doubles and records of them, so the arithmetic can be pinned down by a harness that has no world,
 * no renderer and no game at all.
 *
 * <p>The grid is the block grid: a placement is the low corner of a block, and a block is one block
 * wide, so a placement sits exactly in the block an effect would be attached to. What is <em>drawn</em>
 * for it is a small cube the size of an item's hitbox, centred on the point the effect would be
 * attached at — that is the renderer's business and the client's, and this class is about where the
 * effect goes rather than how large a picture of it is.
 *
 * <p>JOML is the one exception to the plain-numbers rule: it is arithmetic in a jar rather than a
 * live client, and a rotation can be composed with it as honestly as with three hand-written
 * cosines, in far less room.
 */
public final class EffectToolPlacement {

    /**
     * The width of a block, in blocks: one, which is the step between one placement and the next.
     *
     * <p>A placement is the low corner of a block and the effect hangs off the middle of it, so this
     * is the half-block {@link EffectAttachment#offsetOf} reads to put a block-side effect on the
     * surface it belongs to. The cube a preview draws is smaller than this and is not built from it.
     */
    public static final double CUBE_SIZE = 1.0D;

    /**
     * How far outside a face the world is asked about it, in blocks. The face itself is a surface
     * two blocks share, so the question has to be put to the block beyond it, and a little way past
     * the surface is enough to name that neighbour unambiguously.
     */
    public static final double PROBE = 0.02D;

    /** How far the entity preview may reach when nothing narrows it, in blocks. */
    public static final double MAX_ENTITY_PREVIEW_RANGE = 8.0D;
    /** How near a look has to be for its entity to be outlined, in blocks. */
    public static final double MIN_ENTITY_PREVIEW_RANGE = 0.5D;

    /**
     * How long the up-direction line is, in blocks.
     *
     * <p>About half a block, so it reads as an arrow leaving the cube rather than as a second edge
     * of it.
     */
    public static final double UP_LINE_LENGTH = 0.5D;

    /** The world's own up, which is the base direction of any placement not against a face. */
    public static final Point UP = new Point(0.0D, 1.0D, 0.0D);

    private EffectToolPlacement() {}

    /**
     * {@return the low corner of the block the effect would be placed in}
     *
     * <p>A look that met a block is placed in that block, whichever distance the tool is set to: a
     * placement distance is about reaching into the air, and a block within reach is a block the
     * operator picked. A look that met nothing is carried out along its own direction by
     * {@link #midAirDistance} and then dropped onto the grid, so even a placement in open air
     * occupies a block rather than a spot between four of them.
     */
    public static Point placement(Aim aim, EffectToolConfig config, double reach) {
        return switch (aim) {
            case Aim.AtBlock at -> at.block().onGrid();
            // A block-side placement goes in the block that was met, as a block placement does: the
            // face decides its direction, not its position, and a space beside the block is not a
            // block anything can be attached to.
            case Aim.AtFace at -> at.block().onGrid();
            case Aim.IntoAir air -> air.eye()
                    .plus(air.direction().scaled(midAirDistance(config, reach)))
                    .onGrid();
        };
    }

    /**
     * {@return how far into the air an effect reaches}, in blocks
     *
     * <p>The tool asks for a distance or for the player's own reach, and either answer is brought
     * into the range a distance may have: an attribute a server has widened to hundreds of blocks is
     * still a placement, not a shot across the world.
     */
    public static double midAirDistance(EffectToolConfig config, double reach) {
        double wanted = config.usesPlayerReach() ? reach : config.maxDistance();
        return Math.max(EffectToolConfig.MIN_DISTANCE,
                Math.min(EffectToolConfig.MAX_DISTANCE, wanted));
    }

    /**
     * {@return true when this tool's mode has a preview yet}
     *
     * <p>The two block modes are previewed as a cube. The two entity modes are previewed as the
     * outline of the entity they would attach to, which is a different question — whether a look
     * met an entity — and is asked through {@link #previewsEntity}. The two self modes are
     * previewed as the outline of the holder, and accurate as a mark at the point it would use.
     */
    public static boolean previews(EffectToolConfig config) {
        return previewsCube(config) || previewsEntity(config) || previewsSelf(config)
                || previewsAccurate(config);
    }

    /**
     * {@return true when this tool would place something}
     *
     * <p>Two things stop a tool placing: no mode chosen, and no effect path. Neither is an error —
     * a tool that has just been given out is meant to do nothing until it is configured — and both
     * are the client's own test before it sends anything, because a message about nothing is a
     * message the server would only have to refuse.
     *
     * <p>Separate from {@link #previews}, which the renderer asks: a mode can be placeable and have
     * no preview of its own, and a preview is drawn for one mode that is not placeable at all.
     */
    public static boolean places(EffectToolConfig config) {
        return config.mode() != EffectToolConfig.Mode.NONE && !config.effectPath().isBlank();
    }

    /** {@return true when this mode is previewed as a block-grid cube} */
    public static boolean previewsCube(EffectToolConfig config) {
        return config.mode() == EffectToolConfig.Mode.BLOCK
                || config.mode() == EffectToolConfig.Mode.BLOCK_SIDE;
    }

    /**
     * {@return true when this mode is previewed as the outline of an entity}
     *
     * <p>{@code NONE} is deliberately not an entity mode: a tool that has just been given out has
     * chosen no target, and drawing an outline for it would say it had.
     */
    public static boolean previewsEntity(EffectToolConfig config) {
        return config.mode() == EffectToolConfig.Mode.ENTITY
                || config.mode() == EffectToolConfig.Mode.ENTITY_AUTO_ROTATE;
    }

    /**
     * {@return true when this mode attaches to the holder rather than to something aimed at}
     *
     * <p>Both self modes answer yes: the only difference between them is whether the effect
     * auto-rotates, which is a property of the attachment rather than of where it goes.
     */
    public static boolean previewsSelf(EffectToolConfig config) {
        return config.mode() == EffectToolConfig.Mode.SELF
                || config.mode() == EffectToolConfig.Mode.SELF_AUTO_ROTATE;
    }

    /** {@return true when this mode is previewed as a mark at one exact point} */
    public static boolean previewsAccurate(EffectToolConfig config) {
        return config.mode() == EffectToolConfig.Mode.ACCURATE;
    }

    /**
     * {@return the exact point an accurate placement would use}, in world coordinates
     *
     * <p>Accurate mode ignores blocks altogether, so this is not a re-aiming of something the look
     * met: it is the eye carried along its own direction by the configured distance, which is why
     * it needs no {@link Aim} and takes the look directly. The result is deliberately off the block
     * grid — that is the whole of what the mode is for — and a blank item-display is what the effect
     * is attached to there.
     *
     * <p>The direction is brought to a length of one first. A look arrives as a unit vector, but a
     * direction that is not one would otherwise carry the placement further than the distance asked
     * for — the distance is a number of blocks, and scaling by it only means blocks if the direction
     * is a block long.
     */
    public static Point accuratePoint(Point eye, Point direction, EffectToolConfig config,
                                      double reach) {
        return eye.plus(direction.normalised().scaled(midAirDistance(config, reach)));
    }

    /**
     * {@return the auto-rotation the effect of this mode would be attached with}
     *
     * <p>The one place a mode and a stored value become an {@link AutoRotate}, so the value the
     * selector shows and the value the effect will be attached with cannot drift apart.
     *
     * <p><b>Only the two modes whose names say "auto-rotate" rotate.</b> Every other mode attaches
     * with {@link AutoRotate#NONE}, whatever the tool stores: the spec gives auto-rotation to those
     * two and to nothing else, and an effect that turned because a selector was left where it was
     * would be a setting the operator never made. That is not a small matter for the modes it
     * affects — a block effect that spins, or an accurate one, is a bug report rather than a
     * feature — and it was one, because every mode but the plain entity one used to read the stored
     * value and the stored default is a rotation.
     *
     * <p>The auto-rotating modes answer the stored value, except that a stored {@code NONE} is
     * promoted to {@link AutoRotate#DEFAULT}. The spec gives those modes no none: resolving one to
     * none would silently drop the feature the mode exists for. Promoting keeps {@code NONE} a legal
     * stored value — a tool that has chosen no mode yet holds it, and the plain mode resolves to it.
     */
    public static AutoRotate autoRotateFor(EffectToolConfig config) {
        return switch (config.mode()) {
            case ENTITY_AUTO_ROTATE, SELF_AUTO_ROTATE -> {
                AutoRotate stored = config.autoRotate();
                yield stored == null || stored == AutoRotate.NONE ? AutoRotate.DEFAULT : stored;
            }
            default -> AutoRotate.NONE;
        };
    }

    /**
     * {@return how far the entity preview reaches with this tool}, in blocks
     *
     * <p>The player's own entity interaction range is the figure the game already aims an attack
     * with, so it is what the preview uses rather than a reach of its own. It is clamped at both
     * ends because an attribute a server has widened to hundreds of blocks would otherwise outline
     * an entity across the world, and a value too short to reach anything would leave the mode with
     * no preview at all.
     *
     * <p>A range that is not a distance at all — negative, or not a number — is missing rather than
     * short, and falls back to the longest the preview may reach: an attribute that failed to reach
     * the client is not a reason to stop drawing outlines.
     */
    public static double entityRange(EffectToolConfig config, double interactionRange) {
        double wanted = Double.isFinite(interactionRange) && interactionRange > 0.0D
                ? interactionRange : MAX_ENTITY_PREVIEW_RANGE;
        return Math.max(MIN_ENTITY_PREVIEW_RANGE,
                Math.min(MAX_ENTITY_PREVIEW_RANGE, wanted));
    }

    /**
     * {@return true when an entity the look met is inside the range the preview may draw at}
     *
     * <p>Measured from the eye to the entity's own position: what is being drawn is one outline
     * around one entity, and its position is the point that outline is drawn about.
     */
    public static boolean entityWithinRange(Point eye, Point entityCentre, double range) {
        double dx = entityCentre.x() - eye.x();
        double dy = entityCentre.y() - eye.y();
        double dz = entityCentre.z() - eye.z();
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    /**
     * {@return the box the entity outline is drawn around}
     *
     * <p>The entity's own position and the size of its hitbox rather than the shape of its model:
     * what the outline has to say is <em>this</em> entity, and a hitbox is the game's own answer to
     * where an entity begins and ends. It is also one box for every entity in the game, where a
     * model would be a different drawing per mob.
     */
    public static EntityBox entityBox(Point position, double width, double height) {
        double half = Math.max(0.0D, width) / 2.0D;
        return new EntityBox(
                new Point(position.x() - half, position.y(), position.z() - half),
                new Point(position.x() + half, position.y() + Math.max(0.0D, height),
                        position.z() + half));
    }

    /**
     * {@return true when the line from the hand to the placement is worth drawing}
     *
     * <p>A rotation is a direction the operator chose, and it matters wherever the mode puts the
     * effect, so the line from the hand is drawn to show it. A placement at no rotation has no
     * direction to state, and a line from the hand to the cube would say nothing.
     *
     * <p>Separate from {@link #showsUpLine}, which answers for the other line: the two say
     * different things, and the block-side mode wants the up line while having no reason for this
     * one.
     */
    public static boolean showsDirection(EffectToolConfig config) {
        return config.isRotated();
    }

    /**
     * {@return true when the way the effect's up points is worth drawing}
     *
     * <p>The block-side mode attaches to a block face, so up has to be stated — perpendicular to
     * that face, and turned by the configured rotation. A rotation chosen in any other mode is a
     * direction as well, and the spec asks for the line wherever the rotation is changed.
     */
    public static boolean showsUpLine(EffectToolConfig config) {
        return config.mode() == EffectToolConfig.Mode.BLOCK_SIDE || config.isRotated();
    }

    /** {@return the line from the player's hand to the cube}, which is the way the tool points */
    public static Segment directionLine(Point hand, Cube cube) {
        return new Segment(hand, cube.centre());
    }

    /**
     * Where the effect's own up points, as the line the preview draws for it.
     *
     * <p>How far the line is off the local up is {@link EffectToolConfig#rotation()}, read as a
     * rotation about each of the three local axes in turn — x, then y, then z — with the first
     * carrying the base direction and each later one turning what the ones before it left. That is
     * the composition the effect itself will be given, so the line cannot disagree with it.
     *
     * <p>Two things carry a base direction. A block-side placement is attached to one face of a
     * block, so its up starts out of that face and a rotation is read against the face: a
     * quarter-turn takes the line across the block rather than away from it. A look that met no
     * block has no face to be relative to, and the spec says such a placement assumes up is up.
     * Every other mode is placed in the world rather than against a surface, which is the same
     * assumption.
     *
     * <p>The middle of the effect is where the line is drawn from, because that is where the
     * effect would sit. It is pushed half a {@link #UP_LINE_LENGTH} along its own base direction
     * first, so that the near half is not buried inside the shape being drawn, and then runs a full
     * {@link #UP_LINE_LENGTH} along whichever way up has been turned to.
     */
    public static Segment upLine(Aim aim, EffectToolConfig config, Point origin) {
        return upLine(aim instanceof Aim.AtFace atFace ? atFace.face() : null, config, origin);
    }

    /**
     * The same line, for a caller that knows the face without having an {@link Aim}.
     *
     * <p>Accurate mode has no aim: blocks take no part in deciding where its effect goes, so the
     * point is the eye carried along its own direction and there is no block for an {@code Aim} to
     * name. The face the look met is still what the placement's rotation is read against — the same
     * rule {@link #upLine(Aim, EffectToolConfig, Point)} follows for a block-side effect, and the
     * same one {@link EffectAttachment#offsetOf} uses — so it is handed in directly rather than
     * derived a second way.
     */
    public static Segment upLine(Face face, EffectToolConfig config, Point origin) {
        Point base = UP;
        if (config.mode() == EffectToolConfig.Mode.BLOCK_SIDE && face != null) {
            base = face.normal();
        }

        double halfway = UP_LINE_LENGTH / 2.0D;
        Point start = origin.plus(base.x() * halfway, base.y() * halfway, base.z() * halfway);
        Point step = rotate(base, config.rotation()).scaled(UP_LINE_LENGTH);
        return new Segment(start, start.plus(step));
    }

    /**
     * {@return a direction with a rotation applied about each of the three local axes in turn}
     *
     * <p>The rotation is in degrees, and x, y and z are the rotation about each of those axes rather
     * than the three parts of one direction: the spec asks for three text boxes per row, so a
     * half-turn in x and a quarter-turn in y is a rotation the operator asked for rather than a
     * mistake to be corrected.
     */
    public static Point rotate(Point direction, EffectToolConfig.Triplet rotation) {
        Vector3f turned = new Vector3f((float) direction.x(), (float) direction.y(),
                (float) direction.z()).rotate(quaternion(rotation));
        return new Point(turned.x(), turned.y(), turned.z());
    }

    /**
     * {@return a rotation as the quaternion an effect is attached with}
     *
     * <p>The same rotation the up-direction line is drawn from, built once here so the line and the
     * effect cannot disagree about which way up the effect is. The composition is x, then y, then z,
     * each about the result of the one before it, which is what makes the line move the way the
     * operator typed rather than the way the axes happen to be listed.
     */
    public static Quaternionf quaternion(EffectToolConfig.Triplet rotation) {
        Quaternionf turned = new Quaternionf();
        if (rotation.x() != 0.0D) turned.rotateX(radians(rotation.x()));
        if (rotation.y() != 0.0D) turned.rotateY(radians(rotation.y()));
        if (rotation.z() != 0.0D) turned.rotateZ(radians(rotation.z()));
        return turned;
    }

    /** {@return an angle in degrees as the radians a rotation is built from} */
    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    /**
     * {@return how far an effect hanging off one face of a block is turned}
     *
     * <p>A block-side placement is attached to a surface, so the effect's own up starts out of that
     * surface: one on a ceiling hangs upside down and one on a wall stands on its side. This is that
     * difference on its own, before the operator's own rotation is read against it.
     *
     * <p>An up face turns nothing, and so does a missing one. The spec says a block-side placement
     * that met no block assumes up is up, and a rotation about the vertical would turn the effect
     * without moving the line that shows the rotation — a difference nobody could see, which is the
     * definition of a difference not worth inventing.
     */
    public static Quaternionf faceQuaternion(Face face) {
        return switch (face == null ? Face.UP : face) {
            case UP -> new Quaternionf();
            case DOWN -> new Quaternionf().rotateX((float) Math.PI);
            case NORTH -> new Quaternionf().rotateX((float) -Math.PI / 2.0F);
            case SOUTH -> new Quaternionf().rotateX((float) Math.PI / 2.0F);
            case WEST -> new Quaternionf().rotateZ((float) Math.PI / 2.0F);
            case EAST -> new Quaternionf().rotateZ((float) -Math.PI / 2.0F);
        };
    }

    /**
     * {@return the rotation an effect attached to one face of a block is given}
     *
     * <p>The face first and the operator's rotation after it, which is what makes the three angles
     * relative to the surface they were typed against rather than to the world. This is the sum
     * behind {@link #upLine}: that line is this direction drawn, so the up an operator sees and the
     * up the effect is attached with cannot come apart — they are one composition read twice.
     */
    public static Quaternionf attachmentQuaternion(Face face, EffectToolConfig.Triplet rotation) {
        return quaternion(rotation).mul(faceQuaternion(face));
    }

    /**
     * Where a player's look is aimed, in the two shapes the arithmetic cares about.
     *
     * <p>A hit and a miss are different sums rather than one sum with a flag: a hit names a block
     * outright, where a miss has to be carried out along a direction to become one.
     */
    public sealed interface Aim {

        /** Aimed at a block: the low corner of the block that was met. */
        record AtBlock(Point block) implements Aim {}

        /**
         * Aimed at one face of a block: where the block is, and which of its sides was met.
         *
         * <p>Only the block-side mode is placed by the face, so the other aim cases carry none: a
         * face that nothing reads is a face that would have to be invented for them.
         */
        record AtFace(Point block, Face face) implements Aim {}

        /** Aimed at nothing: the eye it started from, and the unit direction it travels in. */
        record IntoAir(Point eye, Point direction) implements Aim {}
    }

    /** A point in the world, in blocks, measured from the world's own corner. */
    public record Point(double x, double y, double z) {

        public Point plus(Point other) {
            return plus(other.x, other.y, other.z);
        }

        public Point plus(double dx, double dy, double dz) {
            return new Point(x + dx, y + dy, z + dz);
        }

        public Point scaled(double factor) {
            return new Point(x * factor, y * factor, z * factor);
        }

        /**
         * {@return this point as a direction of length one}
         *
         * <p>A point with no length at all has no direction to be brought to, and is returned as it
         * stands rather than divided by zero: the caller asked for a direction along a look that is
         * not one, and a point at the eye is a better answer than a point that is not a number.
         */
        public Point normalised() {
            double length = Math.sqrt(x * x + y * y + z * z);
            return length < 1.0E-9D ? this : scaled(1.0D / length);
        }

        /** {@return this point moved to the low corner of the block that contains it} */
        public Point onGrid() {
            return new Point(Math.floor(x), Math.floor(y), Math.floor(z));
        }
    }

    /**
     * One of the six block faces, as the step from a block to the neighbour beyond that face.
     *
     * <p>Written as a step rather than as a Minecraft {@code Direction} so the arithmetic stays
     * free of the game; the renderer is where a step becomes a direction again.
     */
    public enum Face {

        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        /** The order the faces are drawn and asked about in, which is the six sides of a block. */
        public static final List<Face> ALL = List.of(DOWN, UP, NORTH, SOUTH, WEST, EAST);

        private final int stepX;
        private final int stepY;
        private final int stepZ;

        Face(int stepX, int stepY, int stepZ) {
            this.stepX = stepX;
            this.stepY = stepY;
            this.stepZ = stepZ;
        }

        public int stepX() {
            return stepX;
        }

        public int stepY() {
            return stepY;
        }

        public int stepZ() {
            return stepZ;
        }

        /** {@return the outward normal of this face}, which is its step as a length of one */
        public Point normal() {
            return new Point(stepX, stepY, stepZ);
        }

        /**
         * {@return the face a stored name stands for}, answering {@link #UP} for a name it does not
         * know
         *
         * <p>Stored by name rather than by ordinal, as the tool's own settings are: an ordinal would
         * silently change meaning the day a face is inserted in the middle of this list. A name from
         * another build is read as up rather than refused, which puts the effect where it would have
         * gone anyway and keeps one odd row from stopping the rest of a dimension being drawn.
         */
        public static Face byName(String name) {
            for (Face face : ALL) {
                if (face.name().equalsIgnoreCase(name)) return face;
            }
            return UP;
        }
    }

    /**
     * A box in the world, which is the shape every drawing here puts around something.
     *
     * <p>Named for the cube it usually is: a placement's cube is one size on every axis, and
     * {@code size} is how far it reaches from its low corner on each of them. An {@link EntityBox} is
     * the other way to make one, and it is not a cube — a hitbox is as wide as the entity and as tall
     * as it stands — so the two extents are held rather than the one.
     */
    public record Cube(Point min, double size, double height, double depth) {

        /** {@return a cube of one size}, which is what every placement's own box is */
        public Cube(Point min, double size) {
            this(min, size, size, size);
        }

        /** {@return an entity's own hitbox as a box}, which is what the outline is drawn around */
        public Cube(EntityBox box) {
            this(box.min(), box.max().x() - box.min().x(), box.max().y() - box.min().y(),
                    box.max().z() - box.min().z());
        }

        /** {@return the corner opposite {@link #min()}, a size further along every axis} */
        public Point max() {
            return min.plus(size, height, depth);
        }

        /**
         * {@return the middle of the box}, which is where a line to the placement ends
         *
         * <p>Each extent's own half, not {@code size} on every axis: a box made from an entity's
         * hitbox is as tall as the entity stands and no wider than it is, and reading the one extent
         * as all three would put its middle somewhere the entity is not — which is where the buried
         * test would then look.
         */
        public Point centre() {
            return min.plus(size / 2.0D, height / 2.0D, depth / 2.0D);
        }

        /** {@return the middle of one face}, measured along that face's own axis */
        public Point faceCentre(Face face) {
            return centre().plus(face.stepX() * size / 2.0D, face.stepY() * height / 2.0D,
                    face.stepZ() * depth / 2.0D);
        }

        /**
         * {@return a point just outside one face}
         *
         * <p>What the world is asked about to learn whether a face is buried: {@link #PROBE} beyond
         * the middle of it lands in the block next to the cube rather than on the surface itself.
         */
        public Point probe(Face face) {
            return faceCentre(face).plus(face.normal().scaled(PROBE));
        }
    }

    /**
     * The box an entity occupies: the shape the outline is drawn around.
     *
     * <p>Its eight corners are offered rather than its twelve edges, because which corners the world
     * hides is the question the preview asks, and every edge ends at two of them.
     */
    public record EntityBox(Point min, Point max) {

        /** How many corners the box has, which is what a renderer walks to draw its edges. */
        public static final int CORNERS = 8;

        /** {@return the corner opposite the low one along a given side}, one square of the box */
        public Point corner(boolean highX, boolean highY, boolean highZ) {
            return new Point(highX ? max.x() : min.x(), highY ? max.y() : min.y(),
                    highZ ? max.z() : min.z());
        }

        /** {@return the middle of the box}, which is where a line or a label would sit */
        public Point centre() {
            return new Point((min.x() + max.x()) / 2.0D, (min.y() + max.y()) / 2.0D,
                    (min.z() + max.z()) / 2.0D);
        }
    }

    /** A line from the player's hand to where the effect lands. */
    public record Segment(Point from, Point to) {}
}
