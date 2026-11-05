package net.xlebupaksa.backutils.compat;

import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.UUID;

/**
 * The one place this mod asks Sable anything, and the only file that names its classes.
 *
 * <p>It asks <b>Sable Companion</b> and never Sable. The companion is the MIT-licensed library Sable's
 * own wiki tells a third-party mod to build against; Sable itself is under PolyForm Shield, which is
 * not a licence this project may take code or a build dependency from, and nothing here needs it. The
 * companion also answers when Sable is not installed at all — every call that would name a sub-level
 * answers nothing — so the rest of the mod has no "is Sable here" question to ask: a placement on a
 * sub-level is simply one that never resolves, exactly as a placement over unloaded ground is.
 *
 * <p><b>A sub-level is found by where it is, not by an identity.</b> The companion has no lookup by
 * {@code UUID}: it answers which sub-level contains a position, and that is the same question this mod
 * asks — the stored point is a position inside the sub-level's own plot, so it resolves to the
 * sub-level that owns that plot. The stored identity is then a <em>check</em> rather than a key: a plot
 * whose sub-level is no longer the one the effect was placed on has been taken apart and put back
 * together, and an effect on the old one must not be drawn on the new one.
 *
 * <p>Positions here are in the sub-level's own space, which is the space its blocks are at — the "plot"
 * of Sable's own vocabulary, thousands of blocks away from anything a player can walk to. A world
 * position is what {@link #worldPoint} answers with, and the two must never be compared directly: the
 * distance between a world point and a local one is the distance to the edge of the world, which is a
 * reach check that refuses every effect on a moving ship. Every caller therefore projects first, and
 * measures afterwards.
 */
public final class SableSubLevels {

    private SableSubLevels() {}

    /**
     * {@return the sub-level whose plot holds this point, or null when it holds none}
     *
     * <p>The point is read in the sub-level's own space, which is where a hit inside a plot already is
     * — Sable rewrites the game's own block clip to answer in plot coordinates — so a caller holding a
     * block position it was given by that clip can ask this without transforming anything.
     *
     * <p>A library that cannot answer is a library that names no sub-level, rather than an error in
     * the tool. This runs on the placement path every time a block is aimed at, so an exception here
     * would take placing anywhere — including in the world, where Sable has nothing to do with it —
     * down with it.
     */
    public static SubLevelAccess at(Level level, double x, double y, double z) {
        if (level == null) return null;
        try {
            return SableCompanion.INSTANCE.getContaining(level, new Vec3(x, y, z));
        } catch (Throwable notThere) {
            return null;
        }
    }

    /**
     * {@return the sub-level a stored placement names}, or null when this level has not got it
     *
     * <p>Both halves matter and neither is optional. The position says which sub-level owns that plot
     * now; the identity says whether it is still the one the effect was placed on. A level that is not
     * this client's, a plot that has not arrived, and a ship that has been taken apart and rebuilt all
     * answer null, which is the caller's signal to wait rather than to draw the effect somewhere else.
     */
    public static SubLevelAccess named(Level level, String subLevel, double x, double y, double z) {
        if (subLevel == null || subLevel.isBlank()) return null;
        SubLevelAccess found = at(level, x, y, z);
        if (found == null) return null;
        UUID id = found.getUniqueId();
        return id != null && id.toString().equals(subLevel.trim()) ? found : null;
    }

    /**
     * {@return where a point in a sub-level's own space is in the world}
     *
     * <p>The pose is the <b>rendered</b> one wherever there is a screen: Sable interpolates a ship's
     * last pose into its current one for the frame being drawn, and reading the tick pose instead
     * leaves the effect a tick behind the hull it is bolted to, which reads as jitter rather than as lag.
     * A server has no partial tick and no renderer, so it gets the logical pose, which is what it
     * reasons about — see {@link ClientSubLevelAccess}.
     *
     * @param partialTick the fraction of the tick being drawn, and nothing on a server
     */
    public static Vec3 worldPoint(SubLevelAccess subLevel, float partialTick,
                                  double x, double y, double z) {
        return poseOf(subLevel, partialTick).transformPosition(new Vec3(x, y, z));
    }

    /**
     * {@return a rotation in a sub-level's own space as a rotation in the world}
     *
     * <p>A rotation composes the other way round from a position: a heading the operator set on a hull
     * is applied first and the hull's own turn after it, so the effect turns with the ship instead of
     * staying pointed where the world's axes point.
     */
    public static Quaternionf worldRotation(SubLevelAccess subLevel, float partialTick,
                                            Quaternionf local) {
        return new Quaternionf(poseOf(subLevel, partialTick).orientation()).mul(local);
    }

    /**
     * {@return the pose to place an effect with}, which is interpolated on a client and not on a server
     */
    private static Pose3dc poseOf(SubLevelAccess subLevel, float partialTick) {
        return subLevel instanceof ClientSubLevelAccess client
                ? client.renderPose(partialTick)
                : subLevel.logicalPose();
    }
}
