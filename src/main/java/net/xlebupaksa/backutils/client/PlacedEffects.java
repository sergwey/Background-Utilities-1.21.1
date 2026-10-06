package net.xlebupaksa.backutils.client;

import com.lowdragmc.photon.client.fx.FXRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.xlebupaksa.backutils.data.EffectPlacement;
import net.xlebupaksa.backutils.item.EffectAttachment;
import net.xlebupaksa.backutils.item.EffectToolPlacement;
import net.xlebupaksa.backutils.network.EffectEndedPayload;
import net.xlebupaksa.backutils.network.EffectPlacedPayload;
import net.xlebupaksa.backutils.network.EffectToolNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The effects this client is drawing, and what it knows about each of them.
 *
 * <p>Photon keeps no list of what it has running that a caller can ask about, and the mod needs one
 * for everything the placed effect is more than a picture: the boxes drawn where effects already
 * are, the delete tool that removes them, the effects that run out of time, and the ones that end by
 * themselves. This is that list.
 *
 * <p>It is deliberately not a cache of what the server holds. It is what <em>this</em> client has
 * actually started, which is the only thing that can be destroyed, drawn or counted — a row whose
 * effect this client could not start because its anchor has not arrived yet is an entry that is
 * still waiting, not a missing one.
 *
 * <p>An entry has two states, and both are ordinary:
 *
 * <ul>
 *   <li><b>waiting</b> — the effect is wanted and could not be attached, because the display it hangs
 *       off has not reached this client yet. Effect and entity arrive on different channels and
 *       either can be first, so this is a race rather than a fault; the entry is retried for a while
 *       and then given up on.
 *   <li><b>running</b> — the effect is attached, and is polled for having ended on its own. An
 *       effect with its own lifetime ends without anybody asking it to, and the server is told, so
 *       the row and the display go with it.
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class PlacedEffects {

    /**
     * How long an entry is retried before it is given up on.
     *
     * <p>Five seconds: an anchor is one entity in a chunk the player is standing in, so it arrives
     * within a tick or two of the effect, and anything still missing after five seconds is missing
     * because it is not coming — a display somebody deleted, or a chunk that never loaded.
     */
    private static final int WAITING_TICKS = 100;

    /**
     * How often the list is walked.
     *
     * <p>Every other tick: an effect's own ending is not an emergency, and this is a walk over a
     * handful of entries rather than over the world.
     */
    private static final int POLL_INTERVAL_TICKS = 2;

    /** One effect this client is drawing, or is waiting to draw. */
    public static final class Entry {

        private final long id;
        private final EffectPlacement placement;
        private final EffectAttachment attachment;
        private final int anchorEntityId;
        private FXRuntime runtime;
        private int waited;

        private Entry(long id, EffectPlacement placement, EffectAttachment attachment,
                      int anchorEntityId) {
            this.id = id;
            this.placement = placement;
            this.attachment = attachment;
            this.anchorEntityId = anchorEntityId;
        }

        /** {@return the row this effect came from}, which is what removes it again */
        public long id() {
            return id;
        }

        /** {@return where this effect sits}, which is what the boxes are drawn at */
        public EffectPlacement placement() {
            return placement;
        }

        /** {@return true once this effect is attached and running} */
        public boolean running() {
            return runtime != null;
        }

        /**
         * {@return where the effect actually is, in the world}
         *
         * <p>The planning point plus the offset the effect was attached with, because that is the sum
         * the library itself works from: a block effect's offset is added to the middle of the block,
         * and an accurate one is the point with its offset. What is drawn, picked and clicked has to
         * be where the effect is rather than where its row points, or the box would sit in the middle
         * of a block an effect is hanging off the side of.
         */
        public EffectToolPlacement.Point anchor() {
            // The one sum, shared with the preview that promised it: EffectPick.effectAt holds it, and
            // says why the middle of the block is what an offset is measured from.
            return EffectPick.effectAt(attachment);
        }

        /**
         * {@return true when this effect hangs off a place in the world rather than off an entity}
         *
         * <p>Which is what decides whether it is drawn as a cube at a point or as an outline around
         * something that moves, and which of the two the delete tool offers: an effect attached to an
         * entity is not in the block the entity was standing in when it was placed.
         */
        public boolean placed() {
            return attachment.kind() != EffectAttachment.Kind.ENTITY
                    && attachment.kind() != EffectAttachment.Kind.SELF;
        }

        /**
         * {@return true when this effect hangs off an entity rather than off a place}
         *
         * <p>The one kind that is drawn as an outline around something and picked by aiming at it. A
         * self effect is not one of these: its anchor is the player wearing it, and the effect itself
         * is the indication rather than a box drawn on the player.
         *
         * <p>Separate from {@link #placed()} rather than its negation, because the third case is the
         * one that matters: an accurate effect is a place, and the display it hangs off is an anchor
         * the world holds rather than an entity the effect is on. Treating that display as the target
         * — which is what reading one of these two as the other does — puts a box around an invisible
         * prop and takes the cube away from the point the effect is actually at.
         */
        public boolean hangsOffEntity() {
            return attachment.kind() == EffectAttachment.Kind.ENTITY;
        }

        /** {@return the entity this effect hangs off}, or {@link EffectPlacedPayload#NO_ENTITY} */
        public int anchorEntityId() {
            return anchorEntityId;
        }
    }

    private static final Map<Long, Entry> ENTRIES = new LinkedHashMap<>();

    /**
     * The next id for a placement that was never written down, counted downwards.
     *
     * <p>Below every row id and never reaching one: a row is what the server removes an effect by,
     * and an id this client invented must not be able to name one. Zero is never used either — that
     * is the id the server sends for an effect it did not store.
     */
    private static long nextLocalId = -1L;

    private static int ticks;

    private PlacedEffects() {}

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onClientTick(ClientTickEvent.Post event) {
        tick();
    }

    /**
     * Forgets everything when the level goes away.
     *
     * <p>An effect is a picture of something in one level, and a runtime belongs to the level it was
     * started in: keeping either across a level change would be drawing the last world's effects in
     * the next one. The server's rows are untouched — they are what a player is brought up to date
     * from when they arrive somewhere.
     */
    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    /**
     * Takes on one effect the server has said is placed.
     *
     * <p>Nothing here decides whether a placement is allowed: that was settled before the message
     * was sent. What is left is turning a position and a configuration into the attachment the
     * one Photon-facing class takes, which is the same planning the preview uses, so what is drawn
     * is what the operator was shown.
     */
    public static void accept(EffectPlacedPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player holder = minecraft.player;
        if (level == null || holder == null) return;

        // A placement that was written down is named by its row, which is what the server removes it
        // by; one that was not — a self effect — is named by this client alone, and the id the
        // message carries is the same nothing for all of them. Naming each of those here is what lets
        // two self effects be drawn at once instead of the second replacing the first.
        long id = payload.effectId();
        if (id <= 0L) {
            id = nextLocalId--;
        } else {
            Entry existing = ENTRIES.get(id);
            // The server re-sends an effect when it believes this client has lost it, and this client
            // is the side that knows — but only when it knows *both* things. An effect that is still
            // running against the same anchor is not lost, and restarting it would begin its animation
            // again in front of somebody watching it. An effect whose anchor is now a different entity
            // is one that must be rebuilt whatever the runtime says: a display that was unloaded and
            // loaded again is a new entity, and the library's executor still holds the old one — which
            // is why a re-sent effect can look alive and never appear.
            if (existing != null && existing.running()
                    && !PhotonFx.destroyed(existing.runtime)
                    && existing.anchorEntityId() == payload.targetEntityId()) {
                return;
            }
            remove(id);
        }

        EffectPlacement where = new EffectPlacement(payload.mode(), dimension(level),
                payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch(),
                payload.face());
        EffectAttachment attachment = EffectAttachment.of(payload.config(), where);
        if (!attachment.placeable() || attachment.location() == null) return;

        Entry entry = new Entry(id, where, attachment, payload.targetEntityId());
        ENTRIES.put(entry.id(), entry);
        start(entry, level, holder);
    }

    /**
     * Stops drawing one effect and forgets it.
     *
     * <p>Asked for by the server when an effect runs out of its time or is deleted, and by this
     * client when it notices the effect ended on its own before starting:
     *
     * <p>An id nobody here knows is not a failure. A client that never managed to start an effect
     * has nothing to stop, and the answer to being told about it is the same as the answer to
     * anything else here.
     */
    public static void remove(long effectId) {
        Entry gone = ENTRIES.remove(effectId);
        if (gone != null) PhotonFx.destroy(gone.runtime);
    }

    /** {@return every effect this client is drawing or waiting for}, for the boxes and the tool */
    public static Collection<Entry> all() {
        return List.copyOf(ENTRIES.values());
    }

    /**
     * {@return the effects that are placed in one block}
     *
     * <p>What the delete tool asks: it removes a place rather than an effect, so the tool has to be
     * able to find the effects that share one.
     *
     * <p><b>Only effects that hang off a place.</b> An effect attached to an entity records where
     * that entity was standing when it was placed, and that block is not a place the effect is in —
     * offering it would highlight a block for an effect that follows a mob around, and a click there
     * would empty a block that has nothing to do with what is on screen.
     */
    public static List<Entry> at(ClientLevel level, BlockPos pos) {
        List<Entry> found = new ArrayList<>();
        String dimension = dimension(level);
        for (Entry entry : ENTRIES.values()) {
            EffectPlacement where = entry.placement();
            if (entry.placed() && where.dimension().equals(dimension)
                    && where.blockX() == pos.getX() && where.blockY() == pos.getY()
                    && where.blockZ() == pos.getZ()) {
                found.add(entry);
            }
        }
        return found;
    }

    /** {@return every effect that hangs off a place in the world}, for the boxes and the picking */
    public static List<Entry> places() {
        List<Entry> found = new ArrayList<>();
        for (Entry entry : ENTRIES.values()) {
            if (entry.placed()) found.add(entry);
        }
        return found;
    }

    /**
     * {@return the rows of every effect this client is drawing that hangs off one entity}
     *
     * <p>What the delete tool asks when the crosshair is on an entity, and the one question about a
     * placement that a place cannot answer: a row records where an entity stood and not which entity
     * it was, so an effect attached to one is only ever known by an entity id from the message that
     * placed it. The rows are answered rather than a yes or no because the caller that outlines the
     * entity only has to know whether there are any, and the caller that deletes them has to know
     * which ones they are — one walk of the list serves both.
     *
     * <p>A self effect answers here as well, with the row the server never wrote: its anchor is the
     * player who placed it. That is the honest answer, and the deletion of such a row is the
     * server's own business to refuse.
     */
    public static List<Long> anchoredTo(int entityId) {
        List<Long> found = new ArrayList<>();
        for (Entry entry : ENTRIES.values()) {
            if (entry.anchorEntityId == entityId) found.add(entry.id());
        }
        return found;
    }

    /** Forgets everything, which a level that is going away requires: nothing would be drawable. */
    public static void clear() {
        for (Entry entry : ENTRIES.values()) PhotonFx.destroy(entry.runtime);
        ENTRIES.clear();
        ticks = 0;
    }

    /**
     * Attaches what is waiting and notices what has ended.
     *
     * <p>Called every tick by the client setup and walked every other one.
     */
    public static void tick() {
        if (ENTRIES.isEmpty()) return;
        if (++ticks < POLL_INTERVAL_TICKS) return;
        ticks = 0;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player holder = minecraft.player;
        if (level == null || holder == null) {
            clear();
            return;
        }

        // Copied before walking: a pass can remove entries, and a map that is being changed under
        // its own iterator is the one way this can fail outright.
        for (Entry entry : List.copyOf(ENTRIES.values())) {
            if (entry.running()) {
                if (!PhotonFx.finished(entry.runtime)) continue;
                // Two ways to be over, and they mean opposite things. An effect that was *destroyed*
                // was stopped by something other than its own end — walking out of its range is the
                // ordinary case, because the library gives up on an effect it can no longer draw —
                // and it is only forgotten here: the row still describes an effect that is there, and
                // it is sent again when the player comes back. An effect that finished and was not
                // destroyed played itself out, and the row describes nothing, so the server is told
                // and the row goes. Reporting the first as the second would delete a row whenever a
                // player walked away from it.
                if (PhotonFx.destroyed(entry.runtime)) remove(entry.id());
                else end(entry);
                continue;
            }
            entry.waited += POLL_INTERVAL_TICKS;
            if (start(entry, level, holder) || entry.waited > WAITING_TICKS) remove(entry.id());
        }
    }

    /**
     * {@return true when the effect was attached}
     *
     * <p>A self or entity effect hangs off an entity the client already has, and a free point off
     * the display the server made. An anchor that is not here yet is the ordinary reason this
     * answers false, and the caller retries rather than drawing the effect somewhere else.
     *
     * <p>An effect that hangs off an entity starts its outline blinking pink here, because this is
     * the one moment the attachment is news: the spec asks for the outline to blink for a second
     * when an effect is attached, and the effect exists from the line below onwards. It is started
     * once per entry — a waiting entry that fails to attach returns before it — so a mob does not
     * blink again on every retry. A place is not flashed: the pink cube that stands in it is already
     * the statement that something was placed there, and the spec asks for the blink of an attached
     * entity alone.
     */
    private static boolean start(Entry entry, ClientLevel level, Player holder) {
        Entity target = entry.anchorEntityId == EffectPlacedPayload.NO_ENTITY
                ? null : level.getEntity(entry.anchorEntityId);
        BlockPos anchor = BlockPos.containing(entry.placement().x(), entry.placement().y(),
                entry.placement().z());

        FXRuntime runtime = PhotonFx.place(entry.attachment, level, holder, target, anchor);
        if (runtime == null) return false;

        entry.runtime = runtime;
        if (entry.attachment.kind() == EffectAttachment.Kind.ENTITY
                && entry.anchorEntityId != EffectPlacedPayload.NO_ENTITY) {
            EffectBlink.flashAttached(entry.anchorEntityId);
        }
        return true;
    }

    /**
     * Forgets an effect that ended on its own, and tells the server.
     *
     * <p>The effect carried its own lifetime and has run out of it, so the row that described it
     * describes nothing: the display it hung off is the server's to remove, and only the server can
     * remove it. Every client watching will notice the same ending, and the server is asked once per
     * client — an effect already gone is gone, which is what makes that harmless.
     */
    private static void end(Entry entry) {
        remove(entry.id());
        EffectToolNetwork.sendEnded(new EffectEndedPayload(entry.id()));
    }

    /** {@return the dimension a placement here would be written with} */
    private static String dimension(ClientLevel level) {
        return level.dimension().location().toString();
    }
}
