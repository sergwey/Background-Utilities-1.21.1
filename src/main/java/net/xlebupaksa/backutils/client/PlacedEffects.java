package net.xlebupaksa.backutils.client;

import com.lowdragmc.photon.client.fx.FXRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.xlebupaksa.backutils.BackUtils;
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
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
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
 *   <li><b>waiting</b> — the effect is wanted and could not be attached, either because the display it
 *       hangs off has not reached this client yet — effect and entity arrive on different channels and
 *       either can be first, so this is a race rather than a fault — or because this client cannot
 *       start it at all. The wait is what tells the two apart: an anchor that has not arrived is
 *       dropped when its budget runs out, and an effect whose anchor <em>is</em> here is kept and
 *       retried slowly, because the row is still there and forgetting it would take the boxes and the
 *       delete tool away from something the server still holds.
 *   <li><b>running</b> — the effect is attached, and is polled for having ended on its own. An
 *       effect with its own lifetime ends without anybody asking it to, and the server is told, so
 *       the row and the display go with it.
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class PlacedEffects {

    /**
     * How long an entry is retried at the poll's own rate before its anchor is asked about.
     *
     * <p>Five seconds: an anchor is one entity in a chunk the player is standing in, so it arrives
     * within a tick or two of the effect, and anything still missing after five seconds is missing
     * because it is not coming — a display somebody deleted, or a chunk that never loaded. What the
     * budget decides is only whether the <em>anchor</em> has run out; an effect whose anchor is here
     * and which still cannot be started is not forgotten, and drops to {@link #STUCK_RETRY_TICKS}.
     */
    private static final int WAITING_TICKS = 100;

    /**
     * How many times one entry is started again quickly after its drawing was taken away.
     *
     * <p>Four: three more than a level change or a mod reaching into the particle engine ever needs,
     * and few enough that an effect this client cannot keep started costs a handful of attempts rather
     * than one every other tick for as long as the player stands there. It is a bound on the fast
     * attempts and not on the entry — what comes after them is {@link #STUCK_RETRY_TICKS}, because an
     * effect that will not keep running while its anchor is here is not a row to be forgotten.
     */
    private static final int MAX_REVIVALS = 4;

    /**
     * How long an effect whose anchor is here waits between attempts once the fast ones are spent.
     *
     * <p>Forty ticks: an effect that will not keep running is one this client cannot fix by trying
     * harder, and the cost of an attempt is a definition lookup and a walk over one anchor's effects.
     * Short enough that anything the resources settle into on their own is picked up within a couple
     * of seconds.
     */
    private static final int STUCK_RETRY_TICKS = 40;

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

        /** How many times this effect has been started again after its drawing was taken away. */
        private int revivals;

        /** Whether this effect has ever been attached, which is what makes a failure worth saying. */
        private boolean drawn;

        /** Ticks left before the next attempt is made, which is what holds an attempt back. */
        private int retryIn;

        /** Whether the log has already been told that this effect will not start. */
        private boolean complained;

        /** Why the last attempt did not start it, when that reason is not an ordinary one. */
        private String fault;

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

        /**
         * {@return what this effect was attached with}, which its own anchor is read from
         *
         * <p>Reached for by {@link EffectPick} rather than kept there, so that the offset an effect is
         * drawn with is the offset it was placed with: the arithmetic is the attachment's own, and an
         * entry that carried a second copy of it could be drawn as something else.
         */
        EffectAttachment attachment() {
            return attachment;
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
         *
         * <p>Answered for a moment rather than once, because one mode's anchor moves: an accurate
         * effect hangs off the blank display the server put in the world, and the effect follows that
         * display wherever anything takes it. {@link EffectPick#anchorAt} is where the live reading
         * and the point the row holds as its fallback are decided.
         */
        public EffectToolPlacement.Point anchor(float partialTick) {
            // The one sum, shared with the preview that promised it and with the server that removes
            // the place: EffectAttachment.at holds it, and says why the middle of the block is what an
            // offset is measured from.
            return EffectPick.anchorAt(this, partialTick);
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

        /**
         * {@return true when this effect hangs off a display the world holds}
         *
         * <p>An accurate placement is the only one. The server puts a blank item-display where the
         * effect was aimed and every client attaches the effect to it, which is what makes it the same
         * effect for everybody; a block effect hangs off a place, and an entity effect off the entity
         * it is on, which are the two other questions — see {@link #hangsOffEntity()}.
         *
         * <p>Kept apart from those two rather than folded into either, because what it decides is
         * whether an effect's position has to be read from an entity as it is <em>now</em> instead of
         * from the point the row was written at: a display can be moved, and the effect moves with it.
         */
        public boolean hangsOffDisplay() {
            return attachment.kind() == EffectAttachment.Kind.POINT
                    && anchorEntityId != EffectPlacedPayload.NO_ENTITY;
        }

        /**
         * {@return true when the thing this effect hangs off is still here}
         *
         * <p>What decides whether an effect whose drawing was taken away can be started again as it
         * stands: ground that is loaded for a block effect, and a living entity for the three others —
         * the mob an entity effect is on, the display an accurate one hangs off, and the player a self
         * one is attached to. A missing entity and unloaded ground are deliberately the same answer,
         * because they are the same state: the effect is not lost, it is waiting for something that has
         * not arrived, and starting it against nothing is what the library refuses to do.
         */
        boolean anchorHere(ClientLevel level) {
            if (attachment.kind() == EffectAttachment.Kind.BLOCK) {
                return level.isLoaded(BlockPos.containing(placement.x(), placement.y(),
                        placement.z()));
            }
            if (anchorEntityId == EffectPlacedPayload.NO_ENTITY) return false;
            Entity entity = level.getEntity(anchorEntityId);
            return entity != null && entity.isAlive();
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
     * Stops drawing what this client holds when its resources are reloaded.
     *
     * <p>A reload is a {@code ResourceManager} reload, so F3+T and a resource pack being switched on
     * are the same event — and everything an effect was built from comes out of those resources: its
     * definition, the materials of the emitter, the shaders and samplers those materials resolve. The
     * library's own reload listener drops the definition cache and nothing else, and the runtime this
     * client has already started is left exactly as it was, together with the executor the library
     * still holds for it. It is then stale rather than absent, and nothing here can tell: the
     * library's answers about a runtime are about the particle engine (a level change, a wipe, the
     * engine's tick) and say nothing about the resources, so an effect built from packs that have
     * gone still reads as alive. Hence this, which is the one thing the resources cannot take away —
     * the rows this client holds.
     *
     * <p>Registered on the mod bus by {@code ClientSetup}, like the client config; the event is one
     * of the loader's, not the game's. It reaches this by construction rather than by luck: the mod
     * bus carries it while the client is still being built, after every mod's constructor has run —
     * mod construction is the loader's {@code gatherAndInitializeMods}, which the client reaches from
     * its own constructor before the event is posted — so a listener added here is in time for it.
     */
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> stopDrawing());
    }

    /**
     * Takes the drawing away from everything held, and starts nothing: what replaces it is the poll's.
     *
     * <p>Nothing is built here, and that is the point of the split: the poll is the only place that
     * reads a definition and starts an effect from it, and a reload is not over when its listeners
     * run — they run once every listener has prepared, while other listeners' own passes, the particle
     * engine's sprites and the model manager among them, are still to come, and the game swaps the
     * resource manager for the new one only at the very end. The library's own loaders refuse to read
     * a model in that window and retry once the reload is over, so an effect built in it is built
     * against a half-replaced state. That is why the poll asks the loading overlay that stands for
     * that window before it does anything at all — see {@link #tick} — and why what is left here is
     * the rows rather than the drawing: an entry with no runtime is an entry the poll will start.
     *
     * <p>The drawing already started is taken away outright rather than left to the poll: a runtime
     * left in the engine would go on ticking beside the one started in its place, and would also keep
     * the library's executor for this anchor occupied, so the replacement could not be attached at
     * all — the library skips starting an effect it believes is already running, and a runtime is
     * only believed to be over once it has been destroyed or dropped by the engine.
     *
     * <p>An entry left without a runtime is the state {@link #tick}'s waiting half starts from, and
     * that is the path on purpose: nothing about an anchor is in question here, and the anchor check
     * {@link #revive} makes would forget every self effect, whose anchor is the player and which
     * holds no entity id at all. Both budgets a start can run out of are reset, because a reload is
     * this client's own act on its own resources rather than an effect it could not keep started:
     * the attempt it now gets is a whole fresh one, and several reloads in one session are not
     * several failures.
     */
    private static void stopDrawing() {
        for (Entry entry : ENTRIES.values()) {
            // Walked directly: destroying a runtime reaches the particle engine, never this list,
            // which is private to this class and changed only by the methods below.
            PhotonFx.destroy(entry.runtime);
            entry.runtime = null;
            entry.waited = 0;
            entry.revivals = 0;
        }
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
        // Started here rather than left to the poll, because the answer to an operator using the tool
        // is the effect appearing and not appearing a tick later — except while the game is loading
        // resources, where the poll is the only safe place to start from: an effect built in that
        // window is built from meshes and shaders that are being replaced, which is the same reason
        // the reload listener above does not build anything. The entry waits a poll at most.
        if (!(minecraft.getOverlay() instanceof LoadingOverlay)) start(entry, level, holder);
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
     * player who placed it, and a client that is drawing one is the only side that can take it away.
     * An answer is therefore a set of rows of two kinds — the row ids a place or an entity effect was
     * stored under, and the ids this client invented for the self effects it is drawing — and the
     * caller tells them apart by sign, because an id of zero or less is one no row can have.
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

        // Nothing is decided while the game is loading resources. A reload keeps a loading overlay up
        // for the whole of it — F3+T and a resource pack being switched on are that same overlay — and
        // the game swaps the resource manager for the new one only when it is over, so while it stands
        // every answer about a definition, a mesh or a shader is an answer about the packs that are
        // being replaced. It is the test the library's own model loaders make before refusing to read
        // a mesh and asking again once the reload is done, and it is exact where a number of ticks
        // would be a guess: the first poll after the overlay goes starts the effects again, which is
        // at most two ticks after the reload. The window is not counted against anything either — not
        // the wait an entry is spending on its anchor, and not the fast attempts one that was taken
        // down gets — because a reload is this client's act on its own resources and not a failure of
        // the effect's.
        if (minecraft.getOverlay() instanceof LoadingOverlay) return;

        // Copied before walking: a pass can remove entries, and a map that is being changed under
        // its own iterator is the one way this can fail outright.
        for (Entry entry : List.copyOf(ENTRIES.values())) {
            if (entry.running()) {
                if (!PhotonFx.finished(entry.runtime)) continue;
                // Three ways to be over, and the third is the one that means the row goes. An effect
                // that played its own timeline out describes nothing any more, so the server is told
                // and the row is removed with its display. An effect that was *destroyed* was stopped
                // by something other than its own end — the library gives up on an effect whose anchor
                // has gone, which is walking out of its range, unloaded ground, or a mob that died —
                // and an effect the engine has quietly dropped is that same state seen from another
                // angle: nothing is drawn, and the row still describes an effect that is there.
                // Neither of those is reported, because reporting one would delete a row whenever a
                // player walked away from its effect; both are revived instead — started again where
                // the anchor is still here, and forgotten where it is not, in which case the server
                // sends the effect again when that ground or that entity arrives. The destruction is
                // asked first because a destroyed runtime is finished as well.
                if (!PhotonFx.destroyed(entry.runtime) && PhotonFx.playedOut(entry.runtime)) {
                    end(entry);
                    continue;
                }
                revive(entry, level);
                continue;
            }
            entry.waited += POLL_INTERVAL_TICKS;
            if (entry.retryIn > 0) {
                entry.retryIn -= POLL_INTERVAL_TICKS;
                continue;
            }
            if (start(entry, level, holder)) continue;
            if (entry.waited <= WAITING_TICKS) continue;
            // Past the wait, the anchor is the only question that can still be answered. An anchor
            // that is not here is what the budget above is for, and forgetting the entry is right:
            // the row stays with the server and comes back with that ground or that entity. An anchor
            // that *is* here is the opposite case — the server says an effect is placed, this client
            // can see what it hangs off, and it cannot be started — and forgetting it would take the
            // boxes and the delete tool away from a row that is still there, and would leave it
            // undrawn for the rest of the session, because nothing is sent again while the player
            // stands still. It is kept and retried slowly instead.
            if (!entry.anchorHere(level)) {
                remove(entry.id());
                continue;
            }
            complain(entry);
            entry.retryIn = STUCK_RETRY_TICKS;
        }
    }

    /**
     * Says once that an effect this client was drawing cannot be started again, and stops the fast
     * attempts at it.
     *
     * <p>Logged rather than silent: a row the server holds, an anchor this client can see, and an
     * effect that will not be drawn is a fault, which is the one thing this mod writes to the log
     * about besides its record. An entry that was never drawn says nothing — a definition this
     * client's packs do not have is another player's effect and an ordinary event, and the owner's
     * decision that a bad effect path stays quiet is about exactly that case. An entry that was drawn
     * and is now refused says which of the two it is, because the library's own reason is the only
     * thing that can tell a library holding the anchor from a pack that has lost the effect.
     */
    private static void complain(Entry entry) {
        if (entry.complained || !entry.drawn) return;
        entry.complained = true;
        BackUtils.LOGGER.warn("the effect of row {} ({}) is placed and its anchor is here, but it can no "
                        + "longer be started{}, so it is not being drawn; it will be retried every {} ticks",
                entry.id, entry.attachment.location(),
                entry.fault == null ? "" : " — " + entry.fault, STUCK_RETRY_TICKS);
    }

    /**
     * Starts an effect again whose drawing was taken away, or forgets it when it cannot be drawn.
     *
     * <p>What this is for: the server has said this client is drawing an effect, and it is not. Two
     * things take one away without the server being told, and neither is the effect ending. The
     * library stops an effect whose anchor has gone, and the particle engine discards an effect's
     * particles — on a level change, when another mod reaches into the engine, or whenever the
     * library's own root stops being ticked — which leaves a runtime that looks alive for ever and
     * draws nothing (see {@link PhotonFx#finished}).
     *
     * <p>An anchor that is still here is one this client can attach to again, and attaching again is
     * not inventing anything: the effect, the anchor and the settings are the ones the server sent,
     * so what is drawn is what the server believes is drawn. A display that has gone, ground that is
     * not loaded, or a mob that died answers the other way, and then the entry is forgotten — the row
     * is left alone and the effect comes back from the server when that entity is tracked or that
     * ground is sent again, which is what {@link net.xlebupaksa.backutils.effect.EffectReplay} is for.
     *
     * <p>Bounded by {@link #MAX_REVIVALS} for the fast attempts, and by nothing at all afterwards
     * while the anchor is here: an effect this client has had to start again four times is one it
     * cannot keep started, and the honest answer then is not to forget the row — which is still
     * there, and whose boxes and delete tool would go with it — but to stop trying every other tick
     * and start again every {@link #STUCK_RETRY_TICKS} instead. Only an anchor that is not here ends
     * the entry, because only then is the effect somewhere this client cannot reach it.
     */
    private static void revive(Entry entry, ClientLevel level) {
        if (!entry.anchorHere(level)) {
            remove(entry.id());
            return;
        }
        entry.revivals++;
        // Forgetting the runtime is what puts the entry back in the waiting state the next poll
        // starts from, and the wait is reset so a fresh five seconds is spent on the new attempt.
        entry.runtime = null;
        entry.waited = 0;
        if (entry.revivals > MAX_REVIVALS) {
            // The fast attempts are spent. The wait is put past its budget and the retry held back, so
            // the next poll reads this as the case it is — an effect whose anchor is here and which
            // will not keep running — and says so once instead of trying again every other tick.
            entry.waited = WAITING_TICKS;
            entry.retryIn = STUCK_RETRY_TICKS;
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

        PhotonFx.Placement placement = PhotonFx.place(entry.attachment, level, holder, target, anchor);
        if (placement.runtime() == null) {
            // Kept on the entry rather than said here: this is asked again every few ticks, and the
            // reason is worth saying once, when the entry is known to be stuck.
            entry.fault = placement.fault();
            return false;
        }

        entry.runtime = placement.runtime();
        entry.drawn = true;
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
