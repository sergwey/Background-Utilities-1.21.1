package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.item.EffectToolConfig;
import net.xlebupaksa.backutils.item.EffectToolPlacement;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Aim;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Face;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Point;

import java.util.Locale;

/**
 * What the crosshair is on, read from the game's own pick.
 *
 * <p>The preview and the placement both ask this, and they have to be given the same answer: what an
 * operator sees is a promise about what they will get. Reading the aim twice would be two chances to
 * disagree, so it is read here once and both callers take a {@link Look}.
 *
 * <p>The pick is the game's rather than one of ours. {@code GameRenderer#pick} runs every frame and
 * leaves its result on {@link Minecraft#hitResult}, and it is the only thing that finds entities: a
 * pick made from {@code Entity#pick}, which is a block clip and nothing else, can never return an
 * entity however the crosshair is aimed. Entity modes making their own would outline nothing at all.
 * The delete tool reads the same pick, but through {@link EffectPick} rather than here: it removes
 * effects that sit off the block grid, so what it is aimed at is decided by the effects' own boxes
 * and not by the block the pick happened to meet.
 *
 * <p>The eye and the direction are the player's own rather than the camera's, because the player is
 * what the game aims its pick from. View bobbing moves the camera without moving the placement, so a
 * preview built from the camera would promise a point one the placement does not use.
 */
@OnlyIn(Dist.CLIENT)
public final class EffectToolAim {

    private EffectToolAim() {}

    /**
     * One look at the world, in the shapes the placement arithmetic has.
     *
     * @param aim       where the look is aimed; a look that met nothing is {@link Aim.IntoAir}
     * @param target    the entity the crosshair is on and within reach of, or null when there is
     *                  none
     * @param eye       the eye the look started from
     * @param direction the unit direction the look travels in
     */
    public record Look(Aim aim, Entity target, Point eye, Point direction) {

        /** {@return the low corner of the block this look places a block-mode effect in} */
        public Point placement(EffectToolConfig config, double reach) {
            return EffectToolPlacement.placement(aim, config, reach);
        }

        /** {@return the exact point an accurate tool would use} */
        public Point accurate(EffectToolConfig config, double reach) {
            return EffectToolPlacement.accuratePoint(eye, direction, config, reach);
        }

        /**
         * {@return the face a block-side placement hangs off}
         *
         * <p>Up when the look met no block, which is the spec's own answer for a block-side
         * placement in open air: there is no surface for the rotation to be read against, so up is
         * up.
         */
        public Face face() {
            return aim instanceof Aim.AtFace atFace ? atFace.face() : Face.UP;
        }
    }

    /**
     * {@return what the player is aiming at}
     *
     * <p>{@code crosshair} is the game's own pick, {@link Minecraft#hitResult}. A look that met
     * nothing arrives as a block hit of type {@code MISS} rather than as null, so it is the type
     * that decides and not the kind of result.
     */
    public static Look of(Player player, float partialTick, EffectToolConfig config,
                          HitResult crosshair) {
        Point eye = pointOf(player.getEyePosition(partialTick));
        Point direction = pointOf(player.getViewVector(partialTick));

        BlockHitResult block = crosshair instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK ? hit : null;

        Aim aim;
        if (block == null) {
            aim = new Aim.IntoAir(eye, direction);
        } else {
            Point at = pointOf(block.getBlockPos());
            // Only the block-side mode is placed by the face, so only it is told which one was met:
            // a face nothing reads is a face that would have to be invented for the others.
            aim = config.mode() == EffectToolConfig.Mode.BLOCK_SIDE
                    ? new Aim.AtFace(at, faceOf(block.getDirection()))
                    : new Aim.AtBlock(at);
        }

        return new Look(aim, targetOf(player, partialTick, config, crosshair), eye, direction);
    }

    /**
     * {@return the entity the crosshair is on inside the range an entity mode may attach at}, or
     * null for none
     *
     * <p>The range is the player's own entity interaction range, brought into the band
     * {@link EffectToolPlacement#entityRange} allows, so an entity the game would never let the
     * holder touch is not one this mod offers to attach to either. The second test is the same range
     * measured from the eye to the entity's own middle, which is the point an outline is drawn
     * about: an entity whose box the crosshair only grazes at its far corner is not one that has
     * been picked out.
     */
    private static Entity targetOf(Player player, float partialTick, EffectToolConfig config,
                                   HitResult crosshair) {
        if (!(crosshair instanceof EntityHitResult found)) return null;

        double range = EffectToolPlacement.entityRange(config, player.entityInteractionRange());
        Point at = pointOf(found.getEntity().getPosition(partialTick));
        boolean within = EffectToolPlacement.entityWithinRange(
                pointOf(player.getEyePosition(partialTick)), at, range);
        return within ? found.getEntity() : null;
    }

    /** {@return the low corner of a block as a point} */
    private static Point pointOf(BlockPos pos) {
        return new Point(pos.getX(), pos.getY(), pos.getZ());
    }

    /** {@return a position as a point} */
    private static Point pointOf(Vec3 pos) {
        return new Point(pos.x(), pos.y(), pos.z());
    }

    /** {@return the aim's face as the step the arithmetic uses} */
    private static Face faceOf(Direction direction) {
        return Face.valueOf(direction.getName().toUpperCase(Locale.ROOT));
    }
}
