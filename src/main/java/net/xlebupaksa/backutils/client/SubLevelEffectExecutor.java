package net.xlebupaksa.backutils.client;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXEffectExecutor;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.compat.SableSubLevels;
import org.joml.Vector3f;

/**
 * Plays an effect bolted to a moving structure, which is the one anchor Photon has no executor for.
 *
 * <p>Photon's own two executors follow a block and an entity. Both are places the library can look up
 * for itself, and neither is a ship: Sable keeps a ship's blocks in a plot of its own and moves a pose
 * over them, so there is no block position and no entity whose position is the ship's. What there is,
 * is a transform — which is why this executor is a transform and not an anchor lookup, and why it is
 * written the way Photon's documentation says to write one: the runtime root is updated every frame,
 * from {@code updateFXObjectFrame}, which exists for exactly this.
 *
 * <p><b>Every frame rather than every tick.</b> A hull moves continuously and a renderer draws between
 * ticks, so a tick-only update is a whole tick of lag drawn as a ship sailing out from under its own
 * smoke. The pose read here is Sable's <em>rendered</em> one, which is the last tick's pose interpolated
 * towards this one by the fraction of the tick being drawn — the same pose the hull itself is drawn
 * with, so the effect and the hull cannot disagree.
 *
 * <p><b>The point and the rotation are the operator's own.</b> They were typed and aimed against the
 * hull, so they are read in the hull's space and transformed out of it: the offset is a nudge along the
 * surface the operator was looking at, and a heading is applied before the hull's own turn rather than
 * after it. Nothing here reads the world's axes.
 *
 * <p><b>The hull's scale is not the effect's.</b> A sub-level's pose carries a scale, which positions
 * apply — a point on a hull that has been scaled up has to move out with the hull — but the effect's
 * own scale is the operator's setting and is left alone, because a ship twice the size is still lit by
 * a lantern of the size it was placed at.
 */
@OnlyIn(Dist.CLIENT)
final class SubLevelEffectExecutor extends FXEffectExecutor {

    /** The hull's identity, kept as text because that is what a row and a payload carry. */
    private final String subLevelId;

    /** Where on the hull the effect is, in the hull's own space and without the effect's offset. */
    private final Vector3f local;

    SubLevelEffectExecutor(FX fx, Level level, String subLevelId, double x, double y, double z) {
        super(fx, level);
        this.subLevelId = subLevelId;
        this.local = new Vector3f((float) x, (float) y, (float) z);
    }

    /**
     * Starts the effect at the hull's pose as it is now, so the first frame drawn is already in place.
     *
     * <p>The pose is asked for with a partial tick of one, which is the end of the current tick: a
     * start happens on the game thread between frames, and there is no frame being drawn to
     * interpolate towards.
     */
    @Override
    public void start() {
        SubLevelAccess subLevel = subLevel();
        // PhotonFx asks this before building an executor, so this is the hull going away in the tick
        // between the two. Starting an effect with nowhere to put it would draw it at the origin.
        if (subLevel == null) return;

        resetFinishedNotification();
        this.runtime = fx.createRuntime();
        rootAt(subLevel, 1.0F);
        this.runtime.getRoot().updateScale(new Vector3f(scale));
        this.runtime.emit(this, delay);
    }

    /**
     * Puts the root back on the hull, every frame.
     *
     * <p>A hull that cannot be resolved is a hull that is not here — unloaded, taken apart, or a plot
     * that now holds another one — and the honest answer is to hold the pose rather than to invent
     * one. The entry that owns this effect will notice within its own poll and take the effect down,
     * which is what stops a ship's last pose becoming a plume of smoke in the sky.
     */
    @Override
    public void updateFXObjectFrame(IFXObject fxObject, float partialTicks) {
        if (runtime == null || fxObject != runtime.root) return;
        SubLevelAccess subLevel = subLevel();
        if (subLevel == null) return;
        rootAt(subLevel, partialTicks);
    }

    /** {@return the hull this effect is on}, or null when it is not here */
    private SubLevelAccess subLevel() {
        return SableSubLevels.named(level, subLevelId, local.x, local.y, local.z);
    }

    /**
     * Writes the hull's pose into the runtime root.
     *
     * <p>The offset is added in the hull's space rather than to the transformed point, because it is a
     * nudge along the surface the operator aimed at: adding it after the transform would push the
     * effect along the world's axes instead of along the hull.
     */
    private void rootAt(SubLevelAccess subLevel, float partialTicks) {
        Vector3f at = new Vector3f(local).add(offset);
        Vec3 world = SableSubLevels.worldPoint(subLevel, partialTicks, at.x, at.y, at.z);
        IFXObject root = runtime.getRoot();
        root.updatePos(new Vector3f((float) world.x, (float) world.y, (float) world.z));
        root.updateRotation(SableSubLevels.worldRotation(subLevel, partialTicks, rotation));
    }
}
