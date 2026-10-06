package net.xlebupaksa.backutils.client;

import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXEffectExecutor;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.lowdragmc.photon.client.fx.FXRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.xlebupaksa.backutils.item.EffectAttachment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The one place this mod calls Photon, and the only file that names its classes.
 *
 * <p>Photon is compiled against and never bundled, and is absent from most worlds: the mod loads and
 * every other feature works without it, and the effect tool simply places nothing. That is why
 * <b>nothing here may be reached from a dedicated server</b> — not the class, not one signature in
 * it. It lives in the client package for that reason, and its caller sits behind the client-only
 * gate alongside the preview.
 *
 * <p>Everything a failure could do is swallowed and answered with nothing. That is the owner's
 * decision and the right one here: an effect path is typed by hand, so a typo, an effect this client
 * does not have, and a library that changed under us are all ordinary events rather than crashes, and
 * the operator sees "nothing appeared" instead of a stack trace in someone's world.
 *
 * <p>The three kinds of attachment are the three executors Photon has. A block effect hangs off a
 * block position, and an entity effect — the holder's own included — off an entity. A free point has
 * no executor of its own: it hangs off the blank display the <em>server</em> put there, which is what
 * the spec asks for in as many words and what makes it the same display for every player.
 */
public final class PhotonFx {

    private PhotonFx() {}

    /**
     * Places one effect, if Photon is here and has that effect.
     *
     * @param level  the level to place into
     * @param holder the player placing it, which a self effect attaches to
     * @param target the entity aimed at, or the display a free point hangs off, or null for neither
     * @param anchor the block a block effect hangs off, or null when this is not a block effect
     * @return the effect that was started, or null when nothing was
     */
    public static FXRuntime place(EffectAttachment attachment, Level level, Player holder,
                                  Entity target, BlockPos anchor) {
        if (!available() || attachment == null || !attachment.placeable()) return null;

        ResourceLocation id = attachment.location();
        if (id == null) return null;

        try {
            FX effect = FXHelper.getFX(id);
            // A path naming nothing is the ordinary case for a typo, and is silent by decision.
            if (effect == null) return null;

            Entity anchorEntity = target;
            FXEffectExecutor executor;
            switch (attachment.kind()) {
                case BLOCK -> executor = new BlockEffectExecutor(effect, level, anchor);
                case ENTITY, SELF -> {
                    if (anchorEntity == null) anchorEntity = holder;
                    executor = new EntityEffectExecutor(effect, level, anchorEntity,
                            photonAutoRotate(attachment));
                }
                default -> {
                    // A free point hangs off the display the server put there, and off nothing this
                    // client invents: an effect attached to a display of its own would be one only
                    // this player could see, would not survive a reload, and could not be found or
                    // moved by anything else. A display that has not arrived yet is not a failure —
                    // the caller waits for it rather than drawing the effect somewhere else.
                    if (anchorEntity == null) return null;
                    executor = new EntityEffectExecutor(effect, level, anchorEntity,
                            photonAutoRotate(attachment));
                }
            }

            executor.setScale(attachment.scale());
            executor.setRotation(attachment.rotation());
            executor.setOffset(attachment.offset());
            executor.setDelay(attachment.delayTicks());
            executor.setForcedDeath(attachment.forceDeath());
            executor.setAllowMulti(attachment.allowMulti());
            executor.start();
            return executor.getRuntime();
        } catch (Throwable e) {
            // Deliberately all of it: a library that throws in its own setup must not be able to
            // take this mod's placing down with it, and there is nothing useful to say about it.
            return null;
        }
    }

    /**
     * {@return true when something other than the effect's own end has stopped it}
     *
     * <p>The library destroys an effect it can no longer draw — walking out of its range is the
     * ordinary case — and that reads exactly like an effect that has played out if only
     * {@link #finished} is asked. The two are opposites for this mod: one means the row describes
     * nothing and should go, and the other means the row describes an effect that is merely out of
     * sight and must be left alone, because it is sent again when the player comes back.
     */
    public static boolean destroyed(FXRuntime runtime) {
        if (runtime == null) return true;
        try {
            return runtime.isDestroyed();
        } catch (Throwable e) {
            return true;
        }
    }

    /**
     * {@return true when an effect has run to its own end}
     *
     * <p>How an effect that carries its own lifetime is noticed. Photon's answer is the only one
     * there is: {@code isFinished} is true when the effect's timeline has run out <b>and</b> nothing
     * of it is still alive, which is exactly "this effect is over" rather than "the effect was told
     * to stop". A runtime that is gone, or that throws when asked, is over as well — there is
     * nothing left to draw either way, and a caller that kept it would be keeping a row for an
     * effect nobody can see.
     */
    public static boolean finished(FXRuntime runtime) {
        if (runtime == null) return true;
        try {
            return runtime.isFinished() || !runtime.isAlive();
        } catch (Throwable e) {
            return true;
        }
    }

    /**
     * Takes an effect away, which is how a placement that has run out of time ends.
     *
     * <p>{@code true} asks for the effect's objects to be removed outright rather than faded: the
     * lifetime belongs to the tool, and an effect that lingers for a fade after its time is an
     * effect outliving what the operator set.
     */
    public static void destroy(FXRuntime runtime) {
        if (runtime == null) return;
        try {
            runtime.destroy(true);
        } catch (Throwable e) {
            // As with placing: a library that throws while being told to stop must not take the
            // caller down with it, and there is nothing useful to say about it.
        }
    }

    /**
     * {@return this mod's auto-rotation as Photon's}
     *
     * <p>Mapped by name because the two are separate types: ours exists so the tool, its codec and
     * its screen can be built and tested without Photon on the compile path at all, and Photon's
     * belongs to its executor. A value Photon does not have falls back to none, which attaches the
     * effect without auto-rotation rather than failing to attach it — a version that dropped a mode
     * should cost the operator that mode, not the whole effect.
     */
    private static EntityEffectExecutor.AutoRotate photonAutoRotate(EffectAttachment attachment) {
        try {
            return EntityEffectExecutor.AutoRotate.valueOf(attachment.autoRotate().name());
        } catch (IllegalArgumentException | NullPointerException e) {
            return EntityEffectExecutor.AutoRotate.NONE;
        }
    }

    /** {@return true when Photon is loaded}, asked through FML rather than by touching a type */
    public static boolean available() {
        return PhotonPresence.loaded();
    }

    /**
     * {@return every effect this client has, or nothing when Photon is not here}, sorted by name
     *
     * <p>What the configuration screen offers beside the path box. The list comes from the client's
     * own resources, through the same call Photon's own command lists them with, so what an operator
     * can pick is exactly what this client could draw — a path another player's client has and this
     * one does not would be an effect that never appears.
     *
     * <p>Sorted so the dropdown does not reorder itself between openings: the library's own order is
     * the order a resource pack happened to load in.
     *
     * <p>Everything a listing could go wrong with is answered with nothing, as placing is: a client
     * that cannot list its effects should be offered a path box that is still typable rather than a
     * screen that fails to open.
     */
    public static List<ResourceLocation> listEffects() {
        if (!available()) return List.of();
        try {
            List<ResourceLocation> found = new ArrayList<>();
            for (ResourceLocation id : FXHelper.listAllFX()) {
                if (id != null) found.add(id);
            }
            found.sort(Comparator.comparing(ResourceLocation::toString));
            return found;
        } catch (Throwable e) {
            return List.of();
        }
    }
}
