package net.xlebupaksa.backutils.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.xlebupaksa.backutils.data.EffectPlacement;
import net.xlebupaksa.backutils.item.DeleteToolItem;
import net.xlebupaksa.backutils.item.EffectAttachment;
import net.xlebupaksa.backutils.item.EffectToolConfig;
import net.xlebupaksa.backutils.item.EffectToolItem;
import net.xlebupaksa.backutils.item.EffectToolPlacement;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Cube;
import net.xlebupaksa.backutils.item.EffectToolPlacement.EntityBox;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Face;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Point;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Segment;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a held tool shows in the world: the effect tool's preview of where its effect would go, and
 * the delete tool's view of the effects that are already there.
 *
 * <p>The effect tool's preview is a small green cube at the point the effect would be attached at
 * with a thin green line from the hand, the green outline of the entity an entity-mode tool would
 * attach to, the green outline of the holder for a self-mode tool, or the same small cube in the
 * accurate mode's own colour at the exact point it would use. The delete tool's view is that small
 * cube at every effect this client is drawing, pink, the same cube in red under the crosshair, the
 * outline of an entity an effect hangs off, and the red a deletion leaves behind while it blinks.
 * Blue wherever a face or a corner is buried in a block, in both of them.
 *
 * <p>The cube is the size of a dropped item's hitbox rather than of a block, because that is what an
 * effect is: the owner asked for it in as many words, and it is also what makes an effect in open
 * air drawable at all. The placement it stands for still snaps to the block grid — the drawing is
 * small, the effect is not.
 *
 * <p>The two tools are one class because they are one drawing: both are a translucent cube whose
 * faces are shaded by their direction and whose buried faces are drawn through the block that hides
 * them, and a second copy of that would be a second place for the shading, the probes and the two
 * render types to be got wrong. What differs between them is which colour is passed and what the
 * cube is put around, which is why the colour is a parameter of the drawing rather than a constant
 * inside it.
 *
 * <p>Drawn inside the level's own frame, at the stage after the translucent blocks, so it takes its
 * place among the things in the world rather than being painted over the finished picture. It is
 * the client of the player holding the tool that draws it, in the first person view of that player
 * alone: no part of this reaches a server or another player, and the third person view is left
 * alone because the tool is aimed from an eye the holder has stepped away from there.
 *
 * <p>Where the effect goes is decided by {@link EffectToolPlacement}, which knows nothing of the
 * game; what the crosshair is on by {@link EffectToolAim}; and which effect the delete tool would
 * remove by {@link EffectPick}, which the click reads too so that the highlight and the click cannot
 * disagree. What is already placed comes from {@link PlacedEffects}, which is the client's own list
 * of it. What is here is only the drawing: a pose, six quads or twelve lines, and one line per
 * frame.
 */
@OnlyIn(Dist.CLIENT)
public final class EffectToolPreviewRenderer {

    /** How far the hand sits in front of the eye, in blocks, which is where the line starts. */
    private static final double HAND_FORWARD = 0.4D;
    /** How far to the right of the eye the held hand is, in blocks. */
    private static final double HAND_RIGHT = 0.3D;
    /** How far below the eye the held hand is, in blocks. */
    private static final double HAND_DOWN = 0.25D;

    private static final float ALPHA = 0.35F;

    /**
     * How thickly a face that is inside a block is laid on.
     *
     * <p>Much heavier than the open faces, and deliberately: a buried face is read <em>through</em> a
     * block, so what is compared is a translucent colour against a block texture rather than against
     * the sky. At the alpha the open faces use it was reported as not there at all, which is the one
     * reading this colour must never have — the whole point of it is to say "the effect is in here".
     */
    private static final float BURIED_ALPHA = 0.75F;

    private static final float LINE_ALPHA = 0.9F;

    /**
     * How wide the outline of an entity is drawn, in pixels.
     *
     * <p>A little heavier than a block outline: an entity's box is the only thing saying which of
     * several entities standing together would receive the effect, and at one pixel that is hard to
     * tell apart from the block outlines the game already draws.
     */
    private static final float OUTLINE_WIDTH = 2.0F;

    /** The cube and its visible faces: green, the colour of where an effect would go. */
    private static final Rgb GREEN = new Rgb(0.30F, 0.85F, 0.35F);

    /**
     * A cube an effect is already in: pink, the spec's own colour for what is placed.
     *
     * <p>A colour of its own rather than the green of a preview, because the two say opposite
     * things: green is where an effect <em>would</em> go if the button were pressed, and pink is
     * where one already is. A player who saw one colour for both would have no way to tell an empty
     * block from an occupied one while aiming.
     */
    private static final Rgb PINK = new Rgb(1.00F, 0.42F, 0.78F);

    /**
     * The same cube, or an entity's outline, while the delete tool is pointed at it.
     *
     * <p>The spec's own signal: the box under the crosshair turns red, and the red blinks. Deep
     * enough not to be read as the pink at a glance, and the one colour in this drawing that means
     * "this is what a click would take away".
     */
    private static final Rgb RED = new Rgb(0.95F, 0.13F, 0.13F);

    /** A face with a block against it: drawn through that block rather than hidden by it. */
    private static final Rgb BLUE = new Rgb(0.35F, 0.50F, 1.00F);

    /**
     * The same cube for an accurate placement: its own colour rather than the green of the block
     * modes.
     *
     * <p>Accurate mode ignores blocks and lands off the grid, so the one thing the drawing has to
     * say beyond where the point is that this is not the other modes' block-aligned placement. A
     * colour of its own says it at a glance, and the shape is now the same small cube as theirs
     * because the owner asked for one cube everywhere.
     */
    private static final Rgb ACCURATE = new Rgb(0.85F, 0.55F, 0.95F);

    /**
     * What the cube is drawn with: a flat colour with no texture, no light and no culling, blended,
     * drawn through the world and writing no depth.
     *
     * <p>Not writing depth is what lets the faces of one cube blend into a volume instead of hiding
     * one another; no culling means a face is drawn whichever side of it the player is standing on;
     * and no depth <em>test</em> is what makes the cube a report rather than a picture of one — an
     * effect inside a wall is exactly when where it is matters most, so the cube is drawn whatever
     * stands in front of it and the faces that are inside something are overlaid blue.
     *
     * <p>One pass serves both, because there is no longer a difference between them: the cube's own
     * colours go down first for every face, and the blue goes over the faces that are buried.
     */
    private static final RenderType CUBE = preview("backutils_effect_tool_cube");

    /**
     * The line from the hand, as the line shader takes it: a colour and a direction per vertex,
     * blended, depth-tested and writing no depth.
     *
     * <p>Built rather than taken from {@code RenderType.lines()}, which belongs to the block
     * outline: that one writes depth, offsets itself towards the camera to sit on the block it
     * outlines, and draws into the item entity buffer whenever the graphics are set to fabulous,
     * where a line through open air would be lost.
     */
    private static final RenderType DIRECTION = RenderType.create("backutils_effect_tool_direction",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(RenderStateShard.DEFAULT_LINE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * The box around an entity, drawn as the same kind of line the direction line is.
     *
     * <p>Its own type rather than {@code RenderType.lines()}, for the reason {@link #DIRECTION} is:
     * the level's buffer source hands out one buffer per type and flushes the open one when the
     * next is asked for, and the lines type belongs to the block outlines the game draws out of its
     * own buffer. Sharing it would leave whichever of the two asked second with a buffer that was
     * never drawn.
     */
    private static final RenderType OUTLINE = lines("backutils_effect_tool_outline",
            RenderStateShard.LEQUAL_DEPTH_TEST);

    /**
     * The same with the depth test off: the part of the outline that a block stands in front of,
     * drawn through that block in blue, exactly as a buried face of the cube is.
     */
    private static final RenderType BURIED_OUTLINE = lines("backutils_effect_tool_outline_buried",
            RenderStateShard.NO_DEPTH_TEST);

    private EffectToolPreviewRenderer() {}

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) return;

        // Only the holder, and only from behind their own eyes. Another player's client never runs
        // this for a tool it is not holding, and in the third person view the crosshair belongs to
        // a camera the placement does not follow, so nothing is drawn there either.
        if (!minecraft.options.getCameraType().isFirstPerson()) return;
        if (event.getCamera().getEntity() != player) return;

        Camera camera = event.getCamera();
        // The camera the drawing is laid out around, and the eye the placement is worked out from.
        // They are not the same point: bobbing moves one without moving the other.
        Vec3 cameraPos = camera.getPosition();
        PoseStack poses = event.getPoseStack();

        ItemStack tool = player.getMainHandItem();

        // The delete tool shows what is already placed rather than what would be, and it is read
        // first because the two tools are different items: exactly one of the two drawings is ever
        // made, and neither is ever drawn for the other's item.
        if (tool.getItem() instanceof DeleteToolItem) {
            minecraft.getMainRenderTarget().bindWrite(false);
            drawPlacedEffects(poses, minecraft, level, cameraPos,
                    event.getPartialTick().getGameTimeDeltaPartialTick(false));
            return;
        }

        // Only with the tool in hand, and only for a mode that says where its effect would go.
        if (!(tool.getItem() instanceof EffectToolItem)) return;
        EffectToolConfig config = EffectToolItem.configOf(tool);
        if (!EffectToolPlacement.previews(config)) return;

        double reach = player.blockInteractionRange();

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        // The aim is read once for every mode, from the game's own crosshair pick, which is the only
        // pick that finds entities as well as blocks. A block-mode tool reads the block it met, an
        // entity-mode tool the entity, and a self or accurate tool neither — none of them needs a
        // pick of its own, so one look serves the whole frame.
        EffectToolAim.Look look = EffectToolAim.of(player, partialTick, config, minecraft.hitResult);

        // The preview is drawn on the main target whatever the graphics setting: a render type of
        // its own carries no output state, so it would otherwise land in whichever framebuffer the
        // pass before it left bound, which is not the one the player is looking at.
        minecraft.getMainRenderTarget().bindWrite(false);

        if (EffectToolPlacement.previewsCube(config)) {
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

            // The preview cube is small and centred on the point the effect would be attached at,
            // while the placement itself still snaps to the block grid: the owner asked for a cube
            // the size of an item's hitbox, aimed at where the effect goes rather than at the whole
            // block it goes into.
            Cube cube = previewCube(look.aim(), config, reach);
            drawCube(poses, buffers, cube, level, cameraPos, GREEN);
            if (EffectToolPlacement.showsDirection(config)) {
                Segment toHand = EffectToolPlacement.directionLine(
                        handOf(look.eye(), look.direction()), cube);
                drawLine(poses, buffers, toHand, cameraPos, GREEN);
            }
            if (EffectToolPlacement.showsUpLine(config)) {
                // Read against the face that was met when there is one, and against the world's own
                // up when the placement is in open air. It starts at the small cube's own middle,
                // which is where the effect would be.
                Segment up = EffectToolPlacement.upLine(look.aim(), config, cube.centre());
                drawLine(poses, buffers, up, cameraPos, GREEN);
            }
        } else if (EffectToolPlacement.previewsEntity(config)) {
            Entity entity = look.target();
            if (entity == null) return;
            EntityBox box = EffectToolPlacement.entityBox(
                    pointOf(entity.getPosition(partialTick)), entity.getBbWidth(),
                    entity.getBbHeight());
            drawOutline(poses, minecraft.renderBuffers().bufferSource(), box, level, cameraPos,
                    GREEN);
        } else if (EffectToolPlacement.previewsSelf(config)) {
            // The holder's own box, the same outline an entity-mode tool draws: what the effect
            // attaches to is this player, and their box is the shape that says so. An armed tool
            // aimed at the holder is not read as anything else, so no special case is needed.
            EntityBox box = EffectToolPlacement.entityBox(
                    pointOf(player.getPosition(partialTick)), player.getBbWidth(),
                    player.getBbHeight());
            drawOutline(poses, minecraft.renderBuffers().bufferSource(), box, level, cameraPos,
                    GREEN);
        } else {
            // Accurate: blocks are ignored outright, so there is nothing to aim at and nothing to
            // snap to — only the point the eye's own direction reaches, marked off the grid. The mark
            // is the same small cube as the other modes', for the same reason: an effect is a small
            // thing, and a block-sized cube would claim the whole block it happens to sit in.
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            Cube cube = cubeAt(look.accurate(config, reach));

            drawCube(poses, buffers, cube, level, cameraPos, ACCURATE);
            // The line from the hand is the base visual of the tool, not the extra one a rotation
            // asks for: an accurate placement is a point rather than a block, so the line is what
            // says where in the open air the point is. The cube modes draw it only when a rotation
            // has something to state, because there the cube already sits in a block the operator
            // can see.
            drawLine(poses, buffers, EffectToolPlacement.directionLine(
                    handOf(look.eye(), look.direction()), cube), cameraPos, ACCURATE);
            if (EffectToolPlacement.showsUpLine(config)) {
                // The face the look met rather than the aim: this mode keeps no aim, and the rule
                // for reading a rotation against a surface is the one the block-side mode uses.
                Segment up = EffectToolPlacement.upLine(look.face(), config, cube.centre());
                drawLine(poses, buffers, up, cameraPos, ACCURATE);
            }
        }
    }

    // ------------------------------------------------------------------
    // What is already placed
    // ------------------------------------------------------------------

    /**
     * Draws the effects that are already placed, which is what the delete tool is held for.
     *
     * <p>One small cube per effect rather than one per block, centred on
     * {@link PlacedEffects.Entry#anchor(float)} — where the effect actually is, which is the planning
     * point plus the offset it was attached with. A cube on the block a placement floors to would
     * name the block rather than the effect, and an accurate placement is deliberately off the grid,
     * so there would be nothing on the grid to draw at all.
     *
     * <p>The cube is a quarter of a block wide, {@link EffectPick#ITEM_SIZE}, and picking is by that
     * same box — see {@link EffectPick} for why the two are one question.
     *
     * <p>A cube the crosshair is on is <b>steadily</b> red: the red is what says a click would take
     * this one away, and a highlight that blinked while the crosshair sat still would be describing
     * something other than the crosshair. The blinking is the post-deletion flash alone, for the
     * second the spec asks for, and it is drawn for a place whose effects have just been removed by
     * {@link EffectBlink#places()} — the entry itself is gone by then, so there would otherwise be
     * nothing left to draw the red around. The lit half of the blink is read once for the frame, so
     * every cube and outline in it blinks together.
     *
     * <p>An entity an effect hangs off is drawn as the outline of its own box — the shape
     * {@link EffectToolPlacement#entityBox} gives the effect tool — because that is what says
     * <em>this</em> entity: a cube on the grid would be a fixed place where the entity happened to be
     * standing, which is not what an effect attached to a mob is attached to. The outline is pink
     * while the effect is simply there, red when the crosshair is on it, and pink <em>again</em> for
     * the second after the effect was attached, which is the spec's "when effect is attached, blinks
     * a pink outline on the entity for a second". An entity whose effects have just been deleted
     * keeps a red outline for the same second, so the outline does not simply vanish under the click.
     *
     * <p><b>No cube and no outline for a self effect.</b> It follows the player wearing it, so the
     * position its entry carries is only where they were standing when they asked for it: a cube
     * there would offer a click at a block holding nothing the server can remove, and an outline
     * around the holder would be a target drawn on themselves. The effect itself is the indication,
     * and alt-click is what takes these away.
     */
    private static void drawPlacedEffects(PoseStack poses, Minecraft minecraft, ClientLevel level,
                                          Vec3 camera, float partialTick) {
        // The holder is read for the ray the picking is done with. A frame with no player is one
        // where the callback was handed a level that is already going away, and there is nothing to
        // be aimed at in it.
        Player player = minecraft.player;
        if (player == null) return;

        EffectPick.Scene scene = EffectPick.scene(player, partialTick, minecraft.hitResult);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        boolean lit = EffectBlink.lit();

        // A deletion takes the effect away and the flash is what is left where it was, so the places
        // it names are read as the packed block positions they were written as.
        Set<Long> flashing = EffectBlink.places();

        // One cube per effect, drawn at the anchor the effect is actually at. A block holding two of
        // them is therefore two cubes rather than one darker one, which is what the old one-per-place
        // drawing had to accept: the preview render type is translucent and writes no depth.
        for (PlacedEffects.Entry entry : PlacedEffects.places()) {
            Cube cube = EffectPick.boxOf(entry, partialTick);
            boolean hovered = entry == scene.hit();
            // The block the effect is in, by the rule a click names it by, so that the cube a click
            // removes is the cube the flash is drawn around: an accurate effect follows the display it
            // hangs off, and a block-side cube is drawn outside the block it belongs to.
            boolean deleted = flashing.contains(EffectPick.blockOf(entry, partialTick).asLong());
            if (deleted && !lit) continue;
            drawCube(poses, buffers, cube, level, camera, hovered || deleted ? RED : PINK);
        }

        // A place whose effects have all gone has nothing left to draw a cube from, so the flash is
        // drawn from the position it was written as. Drawn after the effects that are still there,
        // and in the one colour a deletion leaves behind.
        for (long place : flashing) {
            if (!lit || anchoring(level, BlockPos.of(place), partialTick)) continue;
            drawCube(poses, buffers, cubeAt(middleOf(BlockPos.of(place))), level, camera, RED);
        }

        // The outlines: every entity this client is drawing an effect on, every entity an effect was
        // just deleted from. Read from the registry rather than from the game's pick, because the
        // pick is null the moment the crosshair falls on a cube in front of the entity, and the pink
        // a fresh attachment blinks is exactly the case where the operator stands in front of the mob.
        //
        // Only entity-anchored effects: a place is drawn as its own cube, and an accurate effect's
        // display is a prop with a box of nothing — outlining it would draw a box around the invisible
        // thing rather than at the point the effect is at.
        Set<Integer> anchors = new LinkedHashSet<>();
        for (PlacedEffects.Entry entry : PlacedEffects.all()) {
            if (entry.hangsOffEntity()) anchors.add(entry.anchorEntityId());
        }

        for (int id : anchors) {
            // A self effect is skipped: its anchor is the player wearing it, and the effect itself is
            // the indication — an outline around the holder would be a target drawn on themselves.
            if (id < 0 || id == player.getId()) continue;
            Entity entity = level.getEntity(id);
            if (entity == null) continue;

            boolean deleted = EffectBlink.entityFlashing(id);
            boolean attached = EffectBlink.attachmentFlashing(id);
            if (deleted && !lit) continue;
            if (!deleted && !attached && !(scene.onEntity() && id == scene.entityId())) continue;

            // Red for the two things a deletion says, pink for the two the effect itself says: it is
            // there, or it has just arrived. The hover and the deletion blink are the same red, which
            // is what makes a click read as having done something even where the highlight was
            // already on.
            EntityBox box = EffectToolPlacement.entityBox(pointOf(entity.getPosition(partialTick)),
                    entity.getBbWidth(), entity.getBbHeight());
            drawOutline(poses, buffers, box, level, camera, deleted ? RED : (attached ? PINK : RED));
        }
    }

    /**
     * {@return true when this client is drawing an effect in a block}
     *
     * <p>Asked of the effects this frame is drawing rather than of the rows, because the two are the
     * same question only while nothing moves: an accurate effect follows the display it hangs off, and
     * a display can be picked up and carried. The flash is keyed by the block a click names — by the
     * rule {@link EffectPick#blockOf(PlacedEffects.Entry, float)} gives — so what has to be known here
     * is whether an effect is already drawn in that block: a second cube at the middle of the block
     * beside it would be the same deletion said twice.
     */
    private static boolean anchoring(ClientLevel level, BlockPos pos, float partialTick) {
        String dimension = level.dimension().location().toString();
        for (PlacedEffects.Entry entry : PlacedEffects.places()) {
            if (!entry.placement().dimension().equals(dimension)) continue;
            if (EffectPick.blockOf(entry, partialTick).equals(pos)) return true;
        }
        return false;
    }

    /** {@return the middle of a block}, which is where a block-mode effect sits */
    private static Point middleOf(BlockPos pos) {
        return new Point(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    // ------------------------------------------------------------------
    // What is being aimed at
    // ------------------------------------------------------------------

    /** {@return a position as a point} */
    private static Point pointOf(Vec3 pos) {
        return new Point(pos.x(), pos.y(), pos.z());
    }

    /**
     * {@return where the tool is held}, for the near end of the direction line
     *
     * <p>An approximation of the hand rather than its model: the line is only read as the way the
     * tool is pointing, and the first person hand sits a little in front of, beside and below the
     * eye. Looking straight up or down leaves the sideways step with no direction to take, so it
     * falls back to the world's own x axis rather than to a point that is not a number.
     */
    private static Point handOf(Point eye, Point direction) {
        Vec3 look = new Vec3(direction.x(), direction.y(), direction.z());
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 1.0E-6D) right = new Vec3(1.0D, 0.0D, 0.0D);

        Vec3 hand = new Vec3(eye.x(), eye.y(), eye.z())
                .add(look.scale(HAND_FORWARD))
                .add(right.normalize().scale(HAND_RIGHT))
                .add(0.0D, -HAND_DOWN, 0.0D);
        return new Point(hand.x(), hand.y(), hand.z());
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /**
     * Draws the six faces of the cube: its own colour where a face is out in the open, blue where it
     * is inside something.
     *
     * <p>A face is buried when the block beyond it is one that occludes, and every face is buried
     * when the block the cube stands in is: a cube inside a wall is hidden from all six sides
     * whichever way the blocks around it are arranged.
     *
     * <p><b>Both passes are drawn through the world</b>, with no depth test at all. A cube is a report
     * of where an effect is, and an effect inside a wall is exactly when that report matters most —
     * so nothing the world stands in front of it may take it away. What being buried changes is a
     * face's colour and not whether the face is drawn.
     *
     * <p>Each face is drawn once, into the pass it belongs to, rather than the blue being laid over
     * the cube's colour on the buried faces: two translucent colours over one another blend, and a
     * third of a blue over a third of a green is a colder green. The blue has to be blue.
     *
     * <p>The two passes are filled and ended one after the other rather than face by face. A buffer
     * source hands out one shared buffer at a time and flushes the open one when the next type is
     * asked for, so a consumer fetched before its turn would be one whose vertices are never drawn.
     *
     * <p>The visible colour is a parameter rather than a constant: the cube is green where an effect
     * would go, pink where one already is, and red where the delete tool is pointed at it, and all
     * three are drawn by this one method. Blue stays blue in all three, because what it says — that
     * this face is inside something — is about the world and not about the tool.
     */
    private static void drawCube(PoseStack poses, MultiBufferSource.BufferSource buffers, Cube cube,
                                 Level level, Vec3 camera, Rgb colour) {
        boolean insideBlock = occludes(level, cube.centre());
        boolean[] buried = new boolean[Face.ALL.size()];
        for (int i = 0; i < Face.ALL.size(); i++) {
            buried[i] = insideBlock || occludes(level, cube.probe(Face.ALL.get(i)));
        }

        poses.pushPose();
        poses.translate(cube.min().x() - camera.x(), cube.min().y() - camera.y(),
                cube.min().z() - camera.z());
        PoseStack.Pose pose = poses.last();

        // The cube's own colour on the faces that are not inside anything, and blue on the faces that
        // are — each drawn once, into its own pass. The buried ones are laid on more thickly: they are
        // read *through* a block, against a texture rather than against the sky, and the alpha the
        // open faces use is not enough to be seen through one.
        drawFaces(pose, buffers, CUBE, colour, cube.size(), buried, false, ALPHA);
        drawFaces(pose, buffers, CUBE, BLUE, cube.size(), buried, true, BURIED_ALPHA);

        poses.popPose();
    }

    /**
     * Fills one pass with the faces of the cube that belong in it, and draws them.
     *
     * <p>A pass with nothing in it still asks for its buffer and ends it, which is a build of
     * nothing and leaves the buffer source as it stands.
     *
     * <p>The alpha comes in rather than being the one constant: a face inside a block is read
     * <em>through</em> that block, so it has to be laid on thickly enough to be seen against a texture
     * rather than against the sky, and a third of a colour is not.
     */
    private static void drawFaces(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers,
                                  RenderType type, Rgb colour, double size, boolean[] buried,
                                  boolean wanted, float alpha) {
        VertexConsumer into = buffers.getBuffer(type);
        for (int i = 0; i < Face.ALL.size(); i++) {
            if (buried[i] == wanted) drawFace(pose, into, size, Face.ALL.get(i), colour, alpha);
        }
        buffers.endBatch(type);
    }

    /** Draws one face of a cube whose low corner is the pose's origin, shaded by its direction. */
    private static void drawFace(PoseStack.Pose pose, VertexConsumer into, double size, Face face,
                                 Rgb colour, float alpha) {
        float shade = shadeOf(face);
        // The face lies on one of the cube's six sides: two axes span it and the third is fixed at
        // the near or the far side, which is the whole of the difference between the six.
        double fixed = face.stepX() + face.stepY() + face.stepZ() > 0 ? size : 0.0D;

        corner(pose, into, face, fixed, 0.0D, 0.0D, size, colour, shade, alpha);
        corner(pose, into, face, fixed, size, 0.0D, size, colour, shade, alpha);
        corner(pose, into, face, fixed, size, size, size, colour, shade, alpha);
        corner(pose, into, face, fixed, 0.0D, size, size, colour, shade, alpha);
    }

    /** Puts one corner of a face: two lengths across it, on the side the face's step runs to. */
    private static void corner(PoseStack.Pose pose, VertexConsumer into, Face face, double fixed,
                               double acrossFirst, double acrossSecond, double size, Rgb colour,
                               float shade, float alpha) {
        into.addVertex(pose,
                        (float) coordinate(face, 0, fixed, acrossFirst, acrossSecond),
                        (float) coordinate(face, 1, fixed, acrossFirst, acrossSecond),
                        (float) coordinate(face, 2, fixed, acrossFirst, acrossSecond))
                .setColor(colour.red() * shade, colour.green() * shade, colour.blue() * shade,
                        alpha);
    }

    /**
     * {@return one coordinate of a corner on a face}
     *
     * <p>The face's own axis is the fixed side of the cube. Of the two that are left, the one nearer
     * x carries the first length across the face and the one after it the second, which turns the
     * four corners of every face into the same four pairs.
     */
    private static double coordinate(Face face, int axis, double fixed, double acrossFirst,
                                     double acrossSecond) {
        int fixedAxis = axisOf(face);
        if (axis == fixedAxis) return fixed;
        return axis == (fixedAxis + 1) % 3 ? acrossFirst : acrossSecond;
    }

    /** {@return the axis a face's step moves along}: 0 for x, 1 for y and 2 for z */
    private static int axisOf(Face face) {
        if (face.stepX() != 0) return 0;
        return face.stepY() != 0 ? 1 : 2;
    }

    /** {@return how much of a face's colour is left after the light}, one share per side */
    private static float shadeOf(Face face) {
        return switch (face) {
            case UP -> 1.0F;
            case DOWN -> 0.45F;
            case NORTH, SOUTH -> 0.75F;
            case WEST, EAST -> 0.6F;
        };
    }

    /**
     * Draws the outline of an entity: the one an entity-mode tool would attach its effect to, the
     * holder for a self-mode tool, or the one the delete tool is pointed at.
     *
     * <p>The twelve edges of the entity's own box, in the colour of the reading, and in blue with the
     * depth test off where the box is buried in a block — the same reading as the cube's buried
     * faces, so the two drawings say "here, but inside something" the same way.
     *
     * <p>The whole box is drawn through the world when any part of it is buried rather than each
     * edge on its own: an outline is read as one shape, and half of it in one colour and half in the
     * other reads as two. The buried pass goes first for the same reason the cube's does.
     */
    private static void drawOutline(PoseStack poses, MultiBufferSource.BufferSource buffers,
                                    EntityBox box, Level level, Vec3 camera, Rgb colour) {
        boolean buried = occludes(level, box.centre());
        for (int corner = 0; corner < EntityBox.CORNERS && !buried; corner++) {
            buried = occludes(level, cornerOf(box, corner));
        }

        float keep = RenderSystem.getShaderLineWidth();
        RenderSystem.lineWidth(OUTLINE_WIDTH);
        if (buried) drawEdges(poses, buffers, box, camera, BLUE, BURIED_OUTLINE);
        drawEdges(poses, buffers, box, camera, colour, OUTLINE);
        RenderSystem.lineWidth(keep);
    }

    /**
     * Draws the twelve edges of a box into one pass.
     *
     * <p>The pose is moved to the low corner once and every edge is then a step from a corner to
     * the one beside it, which is the cube's own arrangement: one pose, and coordinates that are
     * the size of the box rather than the position of it.
     */
    private static void drawEdges(PoseStack poses, MultiBufferSource.BufferSource buffers,
                                  EntityBox box, Vec3 camera, Rgb colour, RenderType type) {
        poses.pushPose();
        poses.translate(box.min().x() - camera.x(), box.min().y() - camera.y(),
                box.min().z() - camera.z());
        PoseStack.Pose pose = poses.last();
        VertexConsumer into = buffers.getBuffer(type);

        for (int from = 0; from < EntityBox.CORNERS; from++) {
            for (int axis = 0; axis < 3; axis++) {
                int to = from ^ (1 << axis);
                // Each edge is drawn by the corner it starts at, which is the one whose bit for
                // this axis is clear: the other end draws the same edge the other way round.
                if ((from & (1 << axis)) != 0) continue;
                edge(pose, into, cornerOf(box, from), cornerOf(box, to), box.min(), colour);
            }
        }
        buffers.endBatch(type);
        poses.popPose();
    }

    /** Puts one edge of a box, whose ends are named relative to a pose already at the low corner. */
    private static void edge(PoseStack.Pose pose, VertexConsumer into, Point from, Point to,
                             Point origin, Rgb colour) {
        float dx = (float) (to.x() - from.x());
        float dy = (float) (to.y() - from.y());
        float dz = (float) (to.z() - from.z());
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        // A line shader expands each vertex along the normal it is given, which for an edge is the
        // direction the edge runs in. A box with no height has one such normal undefined.
        float nx = length < 1.0E-4F ? 0.0F : dx / length;
        float ny = length < 1.0E-4F ? 0.0F : dy / length;
        float nz = length < 1.0E-4F ? 1.0F : dz / length;

        into.addVertex(pose, (float) (from.x() - origin.x()), (float) (from.y() - origin.y()),
                        (float) (from.z() - origin.z()))
                .setColor(colour.red(), colour.green(), colour.blue(), LINE_ALPHA)
                .setNormal(pose, nx, ny, nz);
        into.addVertex(pose, (float) (to.x() - origin.x()), (float) (to.y() - origin.y()),
                        (float) (to.z() - origin.z()))
                .setColor(colour.red(), colour.green(), colour.blue(), LINE_ALPHA)
                .setNormal(pose, nx, ny, nz);
    }

    /** {@return one of a box's eight corners, by the number whose bits name its sides} */
    private static Point cornerOf(EntityBox box, int corner) {
        return box.corner((corner & 1) != 0, (corner & 2) != 0, (corner & 4) != 0);
    }

    /**
     * Draws a line from one point to another, in one colour.
     *
     * <p>A line rather than a thin box, so it stays a line at any distance and costs two vertices.
     * Its normal is the direction it runs in, which is what the line shader expands the width with.
     */
    private static void drawLine(PoseStack poses, MultiBufferSource.BufferSource buffers,
                                 Segment line, Vec3 camera, Rgb colour) {
        double dx = line.to().x() - line.from().x();
        double dy = line.to().y() - line.from().y();
        double dz = line.to().z() - line.from().z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // A hand already inside the cube it is pointing at has no line to draw.
        if (length < 1.0E-4D) return;

        poses.pushPose();
        poses.translate(line.from().x() - camera.x(), line.from().y() - camera.y(),
                line.from().z() - camera.z());
        PoseStack.Pose pose = poses.last();

        VertexConsumer into = buffers.getBuffer(DIRECTION);
        float nx = (float) (dx / length);
        float ny = (float) (dy / length);
        float nz = (float) (dz / length);
        into.addVertex(pose, 0.0F, 0.0F, 0.0F)
                .setColor(colour.red(), colour.green(), colour.blue(), LINE_ALPHA)
                .setNormal(pose, nx, ny, nz);
        into.addVertex(pose, (float) dx, (float) dy, (float) dz)
                .setColor(colour.red(), colour.green(), colour.blue(), LINE_ALPHA)
                .setNormal(pose, nx, ny, nz);
        buffers.endBatch(DIRECTION);

        poses.popPose();
    }

    // ------------------------------------------------------------------
    // Where a drawn cube goes
    // ------------------------------------------------------------------

    /**
     * {@return the small cube the preview of a block-mode tool is drawn as}
     *
     * <p>Small, because the owner asked for a cube the size of an item's hitbox rather than one the
     * size of the block, and centred on the point the effect would be attached at rather than on the
     * middle of the block it lands in. The placement itself still snaps to the block grid: it is the
     * drawing that is small, not where the effect goes.
     *
     * <p>The centre is built exactly as the placement is, through
     * {@link EffectAttachment#offsetOf(EffectToolConfig, EffectPlacement, EffectToolConfig.Mode)} —
     * the same sum the effect will be handed, and the same one
     * {@link PlacedEffects.Entry#anchor(float)} gives for a placed effect once its offset has been
     * added — read there for a moment rather than once, because an accurate effect follows the display
     * it hangs off rather than sitting where the row was written.
     * That is the whole point of a preview: the cube seen before the click and the marker left behind
     * by it are one arithmetic read twice, so they cannot land in different places.
     *
     * <p>A block-side placement is pushed out of the block it is attached to — see
     * {@link EffectPick#markerCentre} for why, and read it from there so that the two drawings agree.
     */
    private static Cube previewCube(EffectToolPlacement.Aim aim, EffectToolConfig config,
                                    double reach) {
        Point planned = EffectToolPlacement.placement(aim, config, reach);
        // The placement the real one will be written as, built here rather than remembered: the tool's
        // own mode, the block that was aimed at, and the face when there was one. Only the face and
        // the point are read — an attachment's offset is measured from the middle of the block and
        // turned by the surface — and the dimension is the only thing a preview has no business
        // naming.
        EffectPlacement where = new EffectPlacement(config.mode(), "",
                planned.x(), planned.y(), planned.z(), 0.0F, 0.0F,
                aim instanceof EffectToolPlacement.Aim.AtFace atFace
                        ? atFace.face() : EffectToolPlacement.Face.UP);

        // The attachment's own position, which is the middle of the block for a placement on the grid
        // and the point itself for an accurate one — the same sum the library is handed and the same
        // one a placed effect's entry answers with. Reading the offset against the *planning point*
        // instead, which is the block's low corner, puts a block-side cube half a block off on every
        // axis: it landed at the block's own down-left corner rather than on the face aimed at.
        EffectAttachment plannedAttachment = EffectAttachment.of(config, where);
        return cubeAt(EffectPick.markerCentre(EffectPick.effectAt(plannedAttachment), config.mode(),
                where.face()));
    }

    /** {@return a small cube centred on a point}, which is how every marker is drawn */
    private static Cube cubeAt(Point centre) {
        double half = EffectPick.ITEM_SIZE / 2.0D;
        return new Cube(centre.plus(-half, -half, -half), EffectPick.ITEM_SIZE);
    }

    /** {@return true when the block at a probe point is one that hides what is behind it} */
    private static boolean occludes(Level level, Point probe) {
        BlockPos pos = BlockPos.containing(probe.x(), probe.y(), probe.z());
        BlockState state = level.getBlockState(pos);
        return state.canOcclude();
    }

    /**
     * {@return the pass a cube is drawn with}: a flat colour, blended, and drawn through the world
     *
     * <p>Not sorting and not crumbling, so both are created off: one cube of one colour has nothing
     * to sort, and a cube is not part of any block's destruction. The depth test is off because the
     * cube is a report of where an effect is rather than a picture of one — a cube inside a wall is
     * still drawn, and the faces that are inside something are overlaid blue on top of it.
     */
    private static RenderType preview(String name) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                1536, false, false, RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                        .setOverlayState(RenderStateShard.NO_OVERLAY)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }

    /**
     * {@return a line pass for an outline}, depth-tested or drawn through the world
     *
     * <p>Neither pass is sorting or crumbling geometry, so both are created with those off, and
     * both write a colour without writing depth: an outline is a shape drawn over the world rather
     * than part of it.
     */
    private static RenderType lines(String name, RenderStateShard.DepthTestStateShard depthTest) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 1536, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                        .setLineState(RenderStateShard.DEFAULT_LINE)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(depthTest)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .createCompositeState(false));
    }

    /** A colour the preview is drawn in, as the three parts the colour shader takes. */
    private record Rgb(float red, float green, float blue) {}
}
