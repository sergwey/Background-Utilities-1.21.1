package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.data.EffectPlacement;
import net.xlebupaksa.backutils.item.EffectAttachment;
import net.xlebupaksa.backutils.item.EffectToolConfig;
import net.xlebupaksa.backutils.item.EffectToolPlacement;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Cube;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Point;
import net.xlebupaksa.backutils.network.EffectPlacedPayload;
import net.xlebupaksa.backutils.network.EffectToolNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * What the delete tool's crosshair is on: the placed effect a click would remove, or the entity an
 * effect hangs off.
 *
 * <p>Picking is by the effect's own small cube rather than by the block it floored into. A placed
 * effect is drawn as a cube the size of an item's hitbox and an accurate one sits in open air, so a
 * pick that asked the world for a block would name a block nothing is in and leave a mid-air effect
 * impossible to remove — which is the bug this exists to fix. The ray is the player's own eye and
 * look, and the boxes are the anchors of {@link PlacedEffects#places()}.
 *
 * <p>The renderer and the click both take a {@link Scene} from here, and neither decides the target
 * for itself. What a frame highlights and what a click removes are the same question asked twice,
 * and two answers would be a cube drawn red that a click does not take away — the worst kind of lie
 * for a tool whose whole job is removing things.
 *
 * <p>The ray-versus-box arithmetic is plain numbers on purpose, as the rest of the placement
 * arithmetic is: it is the part of picking that can be wrong silently — an axis left out of the
 * test, the farthest hit taken instead of the nearest — and a harness with no world can pin it down.
 * JOML is not needed here; the sums are a dozen lines and read better written out.
 *
 * <p>An entity is a target only when it is what the ray meets first, and a place is measured against
 * it by the same ray, so an entity behind a wall — or behind the cube being aimed at — cannot be
 * deleted through it. The distance to an entity is the entry point of its own hitbox, which is the
 * same surface the game's own pick uses, so the two agree about which of two overlapping things is
 * in front.
 */
@OnlyIn(Dist.CLIENT)
final class EffectPick {

    /**
     * How wide the small cube is, in blocks: the shape every preview and every marker is drawn as.
     *
     * <p>A quarter of a block, which is the side of a dropped item's hitbox. The owner asked for it in
     * as many words — the placed-effect cubes and the preview cube are the size of an item rather than
     * of the block they sit in, because an effect is a small thing in a place and a block-sized cube
     * is a claim about the whole block. The placement itself still snaps to the block grid; it is the
     * drawing that is small.
     *
     * <p>It lives here rather than in the renderer because the drawn box and the picked box have to be
     * one box: a cube the crosshair is on but a ray misses, or the other way round, is a delete tool
     * that does not remove what it highlights.
     */
    static final double ITEM_SIZE = 0.25D;

    /**
     * How far past a surface a block-side cube is pushed, over and above its own half.
     *
     * <p>Half the cube is exactly enough for it to stand clear of the block, and exactly enough for
     * its inner face to lie in the same plane as the block's own surface — two surfaces at the same
     * depth, which the depth buffer resolves differently from one pixel to the next. That is the
     * shimmer that was reported on the cube, and a hundredth of a block is far too small to see and
     * far too large to fight over.
     */
    private static final double SURFACE_CLEARANCE = 0.01D;

    private EffectPick() {}

    /**
     * The box one entry is drawn as, which is also the box it is picked by.
     *
     * <p>A place is a small cube centred on its anchor, and an entity is its own hitbox. The two are
     * the same {@link Cube} because they are the same shape for this purpose — a box in the world
     * that a ray is tested against — and one type means the picking walk does not have to know which
     * kind it is holding.
     */
    static Cube boxOf(PlacedEffects.Entry entry, float partialTick) {
        Entity anchor = anchorOf(entry);
        if (anchor == null) {
            // Every placed effect's box is a small cube centred on the point the effect is at rather
            // than on the middle of the block that point falls in, and a block-side effect's is
            // pushed out of the block as well — see markerCentre, which is the one place that is
            // decided, so the drawn cube and the picked one cannot be two different boxes. The point
            // is read for a moment rather than taken from the row, because an accurate effect follows
            // the display it hangs off and that display can be moved — see anchorAt.
            Point at = markerCentre(entry.anchor(partialTick), entry.placement().mode(),
                    entry.placement().face());
            return new Cube(at.plus(-ITEM_SIZE / 2.0D, -ITEM_SIZE / 2.0D, -ITEM_SIZE / 2.0D),
                    ITEM_SIZE);
        }
        return new Cube(EffectToolPlacement.entityBox(pointOf(anchor.getPosition(partialTick)),
                anchor.getBbWidth(), anchor.getBbHeight()));
    }

    /**
     * {@return the point a placed effect's cube is centred on}
     *
     * <p>{@code anchor} is where the effect is, and for every mode but one that is where the cube is
     * drawn. A block-side effect is attached to the middle of the face it hangs off, so a small cube
     * centred exactly there would be half inside the block it belongs to — and with the buried pass
     * drawn in blue it would say the effect were in the wall, which is the opposite of what the mode
     * is for. It is therefore pushed out by half its own size along the face's normal, so the whole
     * of it stands in front of the surface: a nudge of the drawn cube's size and not of the old
     * block-sized half, which would move it far more than the surface it is meant to clear.
     *
     * <p>The normal is the face's own, in world axes, read from the same place
     * {@link net.xlebupaksa.backutils.item.EffectAttachment#offsetOf} reads the half-block that puts
     * the effect on the surface. The effect's own rotation is deliberately not applied: this is where
     * a cube is drawn, not where the effect points, and a rotated effect must not drag the box off
     * the surface it hangs from.
     *
     * <p>The nudge is applied for every face, including an up one. Up is out of the block rather than
     * into it, so an effect on a block's top is in no need of being pushed out — but the preview is
     * built from the aim and the marker from the stored face, and a rule that applied here and not
     * there would be the one place the two could disagree: aiming a block-side tool at a block's top
     * and placing it would move the cube a quarter of a block between the preview and the marker.
     * One rule for both, and the whole cube stays outside the block in every case.
     */
    static Point markerCentre(Point anchor, EffectToolConfig.Mode mode, EffectToolPlacement.Face face) {
        if (mode != EffectToolConfig.Mode.BLOCK_SIDE) return anchor;
        Point normal = face == null ? EffectToolPlacement.Face.UP.normal() : face.normal();
        double out = ITEM_SIZE / 2.0D + SURFACE_CLEARANCE;
        return anchor.plus(normal.x() * out, normal.y() * out, normal.z() * out);
    }

    /**
     * {@return the entity an entry hangs off and that this client can see}, or null when there is
     * none
     *
     * <p>Null is the ordinary answer for three separate reasons: an entry that hangs off a place
     * rather than off an entity, an entity in another level or out of view, and a self effect, whose
     * anchor is the player wearing it. A self effect answers null although its anchor exists, because
     * the effect follows a player rather than sitting in a place and the effect itself is what shows
     * it — a box drawn around them would be a target offered for something nothing can pick.
     *
     * <p>An accurate effect answers null as well, and it is the case worth naming: it hangs off a
     * blank display the server put in the world, so there <em>is</em> an entity at its id. That
     * display is a prop with a box of nothing, not the thing the effect is on — the effect is at the
     * point the display stands at, which is the point it was aimed at only for as long as nobody
     * moves it. Reading the display as the anchor is what put a box around an invisible prop instead
     * of a cube at the effect; {@link #displayOf} is the reading that is wanted instead.
     */
    static Entity anchorOf(PlacedEffects.Entry entry) {
        if (!entry.hangsOffEntity() || entry.anchorEntityId() < 0) return null;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        return level == null ? null : level.getEntity(entry.anchorEntityId());
    }

    /**
     * {@return the display an accurate effect hangs off and that this client can see}, or null
     *
     * <p>Null for every other kind of entry, and that is the whole of the difference from
     * {@link #anchorOf}: an effect attached to an entity is <em>on</em> that entity and is drawn as
     * its own box, where an accurate effect hangs off a prop standing in a place and is drawn as a
     * cube at the effect's point — so the prop is wanted here only in order to know where that point
     * is now.
     *
     * <p>A display in another level, or one that has not reached this client yet, answers null, which
     * is the ordinary state of an effect that is still waiting to be attached.
     */
    static Entity displayOf(PlacedEffects.Entry entry) {
        if (!entry.hangsOffDisplay()) return null;
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(entry.anchorEntityId());
    }

    /**
     * {@return where an effect is, read from the thing it hangs off as that thing is now}
     *
     * <p>An accurate effect hangs off the blank display the server put in the world rather than off
     * the point it was aimed at, and a display is an entity: whatever moves entities — a prop set up
     * for roleplay, a tool like Axiom, a command — moves the effect with it, because the library
     * re-reads the display's own position every frame. A cube left at the planned point would
     * therefore stand where the effect no longer is, and a click on it would name a block the server
     * finds no effect in.
     *
     * <p>The point is the library's own: {@code EntityEffectExecutor} puts an entity-anchored effect
     * at the entity's eye plus the effect's offset, so that is what is read here, through
     * {@link EffectAttachment#at} — the one place that sum is written. An item display is an entity
     * of no size, so its eye is its position; it is asked for as the eye rather than as the position
     * because that is the accessor the effect itself is placed through, and reading it any other way
     * would be the coincidence this is meant not to depend on.
     *
     * <p>An entry whose display this client cannot see answers with the point the row holds, which is
     * where the effect was planned: there is nothing to follow yet, and that point is where the
     * effect will be once the display arrives.
     */
    static Point anchorAt(PlacedEffects.Entry entry, float partialTick) {
        Entity display = displayOf(entry);
        if (display == null) return effectAt(entry.attachment());
        Vec3 at = display.getEyePosition(partialTick);
        return entry.attachment().at(at.x, at.y, at.z);
    }

    /**
     * {@return where an effect actually is, in the world}
     *
     * <p>The one sum this feature has to get right in two places: the planning point plus the offset
     * the effect was attached with, because that is what the library itself works from. A block
     * effect's offset is added <b>to the middle of the block</b> — read out of
     * {@code BlockEffectExecutor.start} — and the attachment already carries that middle, so adding
     * it to the block's low corner instead puts the answer half a block off on every axis. That is
     * not a rounding difference: it is a block-side cube landing at the block's own down-left corner
     * rather than on the face it was aimed at.
     *
     * <p>Kept here as this package's name for the sum, and answered by
     * {@link EffectAttachment#at()}, which is where it is written: the server asks the same question
     * of a row — where is this effect now — and a second copy of the arithmetic here would be a
     * second answer.
     */
    static Point effectAt(EffectAttachment attachment) {
        return attachment.at();
    }

    /**
     * {@return what the crosshair is on, as one decision the renderer and the click both take}
     *
     * <p>{@code crosshair} is the game's own pick, which is only read for whether it met an entity:
     * the entity itself comes from {@link Minecraft#crosshairPickEntity}, which the game fills from
     * that same pick and leaves null whenever the crosshair is not on one. The block the pick met is
     * deliberately not read — that is the reading that made a placed effect reachable only through
     * the block it floored into.
     *
     * <p>An entity out of this client's level, or an entry whose anchor has not arrived, is skipped
     * rather than refused: a ray that met one has not met anything else either, so the answer is
     * nothing.
     */
    static Scene scene(Player player, float partialTick, HitResult crosshair) {
        Point eye = pointOf(player.getEyePosition(partialTick));
        Point direction = pointOf(player.getViewVector(partialTick)).normalised();
        // The range is the same allowance the server checks a deletion against, read from the class
        // that decides it rather than repeated here: a client that offered a cube the server would
        // refuse would be drawing a target that a click cannot take away.
        double range = player.blockInteractionRange() + EffectToolNetwork.REACH_SLACK;
        Point[] ray = eyeTo(eye, direction);

        List<PlacedEffects.Entry> places = PlacedEffects.places();
        List<Cube> boxes = new ArrayList<>();
        for (PlacedEffects.Entry entry : places) {
            boxes.add(boxOf(entry, partialTick));
        }
        int at = nearest(ray, boxes, range);
        double nearestAt = at < 0 ? Double.MAX_VALUE : pick(ray, boxes.get(at), range);
        PlacedEffects.Entry near = at < 0 ? null : places.get(at);
        Cube nearBox = at < 0 ? null : boxes.get(at);

        // The entity rule, in one place: an entity is a target only when it is the thing the ray
        // meets first. Its entry point is compared with the place's own, so a mob standing behind a
        // cube the operator is aiming at is not the target and an effect behind a mob is not either.
        // A tie leaves it to the place, because a place is what a click empties and a cube the
        // operator can see in front of them is the thing they aimed at.
        EntityHitResult met = crosshair instanceof EntityHitResult hit ? hit : null;
        Entity entity = met == null ? null : Minecraft.getInstance().crosshairPickEntity;
        if (entity != null) {
            double entityAt = pick(ray, boxOf(entity, partialTick), range);
            if (entityAt < nearestAt) return new Scene(null, null, entity, ray, partialTick);
        }
        return new Scene(near, nearBox, null, ray, partialTick);
    }

    /**
     * {@return the number of the box a ray meets first, or -1 when it meets none in range}
     *
     * <p>Separate from {@link #pick} because the two are separate questions: how far along a ray one
     * box is, and which of several boxes is the nearest. Keeping the second here rather than inside a
     * walk that also builds boxes is what lets a harness check that the <em>nearest</em> of two is
     * the one taken — the mistake this was written for, since taking the last hit of a walk is a
     * plausible thing to write and is wrong in exactly the case of one effect in front of another.
     *
     * <p>The distance the answer stands for is not carried alongside it, so a caller that has to
     * compare it with something else asks {@link #pick} for that one box again. That is a single sum
     * rather than a walk, and it is the price of answering with a number: a pair would have to be a
     * type, and a type that only this file and its test read is one the tree's own review tool
     * reports as referenced nowhere.
     *
     * <p>A box the ray misses answers {@link Double#MAX_VALUE} and cannot win; a box the eye is
     * inside answers no distance at all and wins against everything. An empty list answers -1, which
     * is the ordinary case of a client drawing no placed effects at all.
     */
    static int nearest(Point[] ray, List<Cube> boxes, double range) {
        int nearest = -1;
        double nearestAt = Double.MAX_VALUE;
        for (int index = 0; index < boxes.size(); index++) {
            double distance = pick(ray, boxes.get(index), range);
            if (distance < nearestAt) {
                nearestAt = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    /** {@return the box an entity's own hitbox makes}, which is the shape the game picks it by */
    static Cube boxOf(Entity entity, float partialTick) {
        return new Cube(EffectToolPlacement.entityBox(pointOf(entity.getPosition(partialTick)),
                entity.getBbWidth(), entity.getBbHeight()));
    }

    /**
     * {@return how far along a ray a box is first met}, or {@link Double#MAX_VALUE} for no hit
     *
     * <p>A slab test: the ray is inside the box's volume along each axis for one stretch of its
     * length, and where those three stretches overlap is where it is inside the box. The entry is
     * the last of the three beginnings. An axis the ray does not travel along is either inside the
     * slab for its whole length or outside it for its whole length, and there is nothing to compare.
     *
     * <p>Both ends of the slab are ordered with {@link Math#min} and {@link Math#max}: a ray moving
     * towards the lower corner meets the low side first and one moving away from it the high side,
     * and the direction being negative is what swaps them.
     *
     * <p>A box further away than the range is no hit, and a box the eye is already inside is met at
     * no distance at all — which is a hit, not a miss: an effect the player is standing in is one
     * they can see on screen.
     */
    static double pick(Point[] ray, Cube box, double range) {
        Point origin = ray[0];
        Point direction = ray[1];
        // A ray with no direction travels nowhere, and there is no distance along it to divide by:
        // answered as a miss rather than as a box met at no distance at all, which would make the
        // eye's own position a target whatever it was standing in.
        if (direction.x() == 0.0D && direction.y() == 0.0D && direction.z() == 0.0D) {
            return Double.MAX_VALUE;
        }

        double[] originAt = {origin.x(), origin.y(), origin.z()};
        double[] way = {direction.x(), direction.y(), direction.z()};
        double[] low = {box.min().x(), box.min().y(), box.min().z()};
        double[] high = {box.max().x(), box.max().y(), box.max().z()};

        double entry = 0.0D;
        double exit = Double.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            if (way[axis] == 0.0D) {
                if (originAt[axis] < low[axis] || originAt[axis] > high[axis]) {
                    return Double.MAX_VALUE;
                }
                continue;
            }
            double first = (low[axis] - originAt[axis]) / way[axis];
            double second = (high[axis] - originAt[axis]) / way[axis];
            entry = Math.max(entry, Math.min(first, second));
            exit = Math.min(exit, Math.max(first, second));
            if (entry > exit) return Double.MAX_VALUE;
        }

        return entry <= range ? entry : Double.MAX_VALUE;
    }

    /** {@return a ray as the two points the picking sums take}, the second brought to length one */
    static Point[] eyeTo(Point eye, Point direction) {
        return new Point[] {eye, direction.normalised()};
    }

    /** {@return a position as a point} */
    static Point pointOf(Vec3 pos) {
        return new Point(pos.x(), pos.y(), pos.z());
    }

    /** {@return the block a point is in}, by flooring as the placement arithmetic floors */
    static BlockPos blockOf(Point at) {
        return BlockPos.containing(at.x(), at.y(), at.z());
    }

    /**
     * {@return the block a click on a placed effect names}
     *
     * <p>Two answers, and the mode is what decides which. An effect placed <b>in a block</b> is named
     * by the block its row holds, because the anchor such an effect is drawn at is not a point inside
     * it: a block-side effect hangs off the middle of a face, which is the boundary between two
     * blocks, and flooring a boundary point gives whichever block lies on the far side of it — the
     * block <em>above</em> an up-face effect, the one beyond an east or a south face. Naming that is a
     * click that removes nothing, which is what happened on those three faces; the cube's own nudge
     * clear of the surface is the same kind of drawing decision and must not decide this either.
     *
     * <p>An effect <b>in open air</b> is the other answer: it is off the grid on purpose and hangs off
     * a display the world holds, so the block named is the one its anchor is in now — a display that
     * has been moved takes the place it empties with it.
     *
     * <p>Pinned in the harness, face by face, because the trap is invisible: see
     * {@code EffectPickTest.namedBlockTest}.
     */
    static BlockPos namedBlock(EffectPlacement where, Point anchor) {
        if (where == null) return null;
        if (where.onGrid()) return new BlockPos(where.blockX(), where.blockY(), where.blockZ());
        return anchor == null ? null : blockOf(anchor);
    }

    /**
     * {@return the block an effect is in}, which is the block a click on its cube empties
     *
     * <p>See {@link #namedBlock}, which is where the answer is decided: this is the same question asked
     * of an entry, with the anchor read as it is now so that an accurate effect follows its display.
     */
    static BlockPos blockOf(PlacedEffects.Entry entry, float partialTick) {
        return namedBlock(entry.placement(), entry.anchor(partialTick));
    }

    /**
     * One look at what is placed, as whichever of the two targets the crosshair is on.
     *
     * <p>Exactly one of the two is ever answered: {@link #entity} for an entity an effect hangs off,
     * and {@link #hit} for a placed effect. Both being null is an ordinary answer — the crosshair is
     * on nothing this tool can act on — and the caller draws no red and sends nothing for it.
     *
     * @param hit the placed effect the ray met first, or null when it met none in range
     * @param box the box that effect is drawn as, which is the box the ray was tested against
     * @param entity the entity an effect hangs off that the ray met first, or null
     * @param ray the eye and the unit direction, as {@link EffectPick#pick} takes them: kept so a
     *            caller can rebuild the same look, which is what the hand-to-point line needs
     * @param partialTick the moment this look was taken at, which is what the box was built for: the
     *                    block a click names is the anchor's block at that same moment, so a display
     *                    that is moving cannot be drawn in one block and removed in another
     */
    record Scene(PlacedEffects.Entry hit, Cube box, Entity entity, Point[] ray, float partialTick) {

        /** {@return true when the crosshair is on an entity an effect hangs off} */
        boolean onEntity() {
            return entity != null;
        }

        /** {@return true when the crosshair is on a placed effect} */
        boolean onPlace() {
            return hit != null;
        }

        /** {@return the entity id the crosshair is on}, or nothing when it is on a place */
        int entityId() {
            return entity == null ? EffectPlacedPayload.NO_ENTITY : entity.getId();
        }

        /**
         * {@return the block a click on this place empties}
         *
         * <p>See {@link EffectPick#blockOf(PlacedEffects.Entry, float)}: the anchor's own block, read
         * once for the look rather than from the box, because the box has been nudged to keep it out of
         * the wall it hangs on and the place to empty has not.
         */
        BlockPos placeBlock() {
            return hit == null ? null : blockOf(hit, partialTick);
        }
    }
}
