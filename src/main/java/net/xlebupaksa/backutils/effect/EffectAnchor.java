package net.xlebupaksa.backutils.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.xlebupaksa.backutils.data.EffectPlacement;
import net.xlebupaksa.backutils.data.PlacedEffect;
import net.xlebupaksa.backutils.item.EffectAttachment;
import net.xlebupaksa.backutils.item.EffectToolPlacement;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The blank item-display an accurate placement hangs off, which the server owns.
 *
 * <p>The spec asks for one in as many words — "for this purpose creates a blank item-display that
 * the effect is then attached to" — and it has to be a real entity in the world rather than
 * something this mod makes on each client that hears about the effect:
 *
 * <ul>
 *   <li>a display made on one client is that client's alone, so two players watching the same effect
 *       would be watching it hang off two different things;
 *   <li>it would not survive a reload, and an accurate effect is stored to be put back afterwards;
 *   <li>and it could not be found, selected or moved by anything else — an operator who wants a prop
 *       at that spot, for roleplay or for a tool like Axiom, needs an entity the world has.
 * </ul>
 *
 * <p>The display carries no item and no model of its own: it is a place for the effect to be and
 * nothing else, and an empty one renders nothing while still being an entity that exists, is
 * tracked, and can be picked up by anything that looks for entities.
 *
 * <p>The row remembers the display by {@link UUID} rather than by entity id, because an id is handed
 * out per session and means nothing after a restart; a display is found again by asking the level
 * for its UUID.
 */
public final class EffectAnchor {

    /**
     * The tag every anchor carries, so one can be found by something that knows to look.
     *
     * <p>The row that names a display is the ordinary way back to it, and it is not a reliable one:
     * a row written by an older build names nothing, a row can be deleted while its display is in a
     * chunk nobody has loaded, and the two writes are not one transaction. A display that can say
     * what it is survives all of that — it can be found, and taken away, by anything sweeping for
     * the leftovers, which is the only way an orphan is ever cleaned up.
     */
    private static final String ANCHOR_TAG = "backutils_effect_anchor";

    /**
     * Where each display was last seen, by identity.
     *
     * <p>What this is for: a display is not nailed to the place its row was written at. It is an
     * entity, anything that moves entities can move it, and the operator who placed the effect is the
     * one most likely to — so "where is this display" is a question whose answer changes, and the
     * row's own coordinates are only the answer it had at the start. Looking where it was last seen is
     * what stops a moved display growing a second one beside it every time its ground is reloaded.
     *
     * <p>Kept in memory rather than written down, which is a deliberate limit: it is this session's
     * knowledge, and a restart begins again from the row — where a replacement is made only if the
     * display really is gone. Held by identity, dropped when a display is taken away, and cleared when
     * the server stops.
     */
    private static final Map<String, Vec3> SEEN = new HashMap<>();

    private EffectAnchor() {}

    /**
     * Puts a blank display at a point and returns its identity.
     *
     * <p>The position is set before the entity is added to the level, and its previous position is
     * set with it. An entity is drawn between where it was last tick and where it is now, and one
     * added with no previous position is drawn between the world's origin and its own place — which
     * for an effect attached to it is a single frame of the effect flying in from nowhere. Setting
     * both is what stops that frame being drawn at all.
     *
     * @return the new display's UUID, or null when the level could not make one
     */
    public static UUID place(ServerLevel level, double x, double y, double z) {
        Display.ItemDisplay display = EntityType.ITEM_DISPLAY.create(level);
        if (display == null) return null;

        display.setPos(x, y, z);
        display.setOldPosAndRot();
        // Nothing else in the world should be able to move it: an anchor is a place, and an anchor
        // that drifted would take every effect attached to it along with it.
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.addTag(ANCHOR_TAG);
        level.addFreshEntity(display);
        return display.getUUID();
    }

    /**
     * {@return the display a stored effect hangs off}, or null when this level has not got it
     *
     * <p>Looking is also what remembers: a display that has been found is one whose position is worth
     * knowing, and every caller that can see one is a caller that can say where it is — see
     * {@link #ensure}, which is the one that has to look in the right place.
     */
    public static Entity find(ServerLevel level, String uuid) {
        UUID id = parse(uuid);
        if (id == null) return null;
        Entity found = level.getEntity(id);
        if (found != null) SEEN.put(id.toString(), found.position());
        return found;
    }

    /**
     * {@return the display a stored effect hangs off, loading the ground it stands on to be sure}
     *
     * <p><b>A lookup by identity only answers for what is loaded.</b> An entity in a chunk nobody has
     * loaded is not in the level's entity list at all, so asking for it answers nothing — and taking a
     * display away turns that nothing into damage: an expired effect whose display cannot be reached
     * leaves the display standing in the world for the chunk to bring back.
     *
     * <p>So ground is loaded before the answer is believed, and it is the ground the display was last
     * seen on rather than the row's own coordinates: a display that has been moved is where it was
     * moved to, and loading the place it used to be would answer nothing about it. It is the one cost
     * this class has, paid only when a display is actually taken away.
     */
    public static Entity reach(ServerLevel level, String uuid, double x, double y, double z) {
        Entity found = find(level, uuid);
        if (found != null) return found;

        Vec3 seen = lastSeen(uuid);
        level.getChunkAt(seen != null
                ? BlockPos.containing(seen.x, seen.y, seen.z)
                : BlockPos.containing(x, y, z));
        return find(level, uuid);
    }

    /**
     * Takes a display away, with any effect still attached to it.
     *
     * <p>Asked for by identity rather than by the entity, because the caller has a row and not an
     * entity. This is the half of the timed sweep that has to be right: a row that is deleted while
     * its display cannot be reached leaves the display in the world with nothing left to say it
     * should not be there, and it comes back the next time that chunk is loaded.
     */
    public static void remove(ServerLevel level, String uuid, double x, double y, double z) {
        Entity display = reach(level, uuid, x, y, z);
        if (display != null) display.discard();
        // What was remembered about it is worthless once it is gone, and keeping it would answer a
        // later question about this identity with a position nothing stands at.
        forget(uuid);
    }

    /**
     * {@return the display an effect hangs off, putting a new one there when the old one has gone}
     *
     * <p>A display is an entity, so it is gone after a restart and can be removed by anything that
     * removes entities — and an accurate effect with no display has nothing to be attached to. This is
     * how a stored effect is put back after a restart: the same display when it is still there, and a
     * new one standing where the effect is when it is not.
     *
     * <p><b>A lookup that finds nothing is not the same as a display that has gone</b>, and believing
     * otherwise cost two bugs of one kind. The first: a chunk's entities are added to the level a tick
     * or more <em>after</em> the chunk itself is loaded — read out of
     * {@code PersistentEntitySectionManager}, which queues the entities of a chunk that has become
     * visible and adds them in the next {@code processPendingLoads} — so a lookup running while the
     * ground is arriving answers nothing about a display that is on its way, and a display made there
     * was a second one inside the first, once per unload and load. The second: a display can be
     * <em>moved</em>, and the row still holds where the effect was aimed, so a display an operator has
     * taken elsewhere is neither at the coordinates a lookup uses nor at the ones a replacement would
     * be made at.
     *
     * <p>Two things are therefore asked before one is made. The ground it should be standing on has to
     * have its entities in the level — {@code areEntitiesLoaded}, which is the game's own answer to
     * exactly that question — and the place looked in is where the display was <b>last seen</b> rather
     * than where the row was written, because a display that has been moved is where it was moved to.
     * What is left after both is a display whose own ground is populated and which is not in it: that
     * is one that has gone, and its replacement stands where it did.
     *
     * <p>A move made while this server is running is followed, and one made before it is not: after a
     * restart the row's own coordinates are all there is, and a replacement is made there. Nothing is
     * lost by that — a row naming a display that is really gone is exactly the case this is for.
     *
     * <p>The new identity is written back to the row by the caller, which is the side that has one: an
     * anchor nobody wrote down is an anchor the next restart looks for again, and every restart would
     * leave another display standing in the world.
     *
     * @param level the level the effect is in, which is the one its row names
     * @param uuid  the identity the row remembers, which may name nothing
     * @param x     where the effect is, for a display that has to be made again
     * @return the entity to attach to, or null when there is nothing to attach to yet
     */
    public static Entity ensure(ServerLevel level, String uuid, double x, double y, double z) {
        Entity existing = find(level, uuid);
        if (existing != null) return existing;

        Vec3 want = lastSeen(uuid);
        if (want == null) want = new Vec3(x, y, z);

        // Asked of the ground the display should be standing on, before anything is concluded from not
        // finding it: a chunk whose entities are not in the level yet answers nothing, and nothing is
        // what is returned. The effect is not lost by that — it arrives with the display, which is
        // tracked the moment it exists and re-sent then.
        if (!level.areEntitiesLoaded(new ChunkPos(BlockPos.containing(want.x, want.y, want.z))
                .toLong())) {
            return null;
        }

        UUID placed = place(level, want.x, want.y, want.z);
        return placed == null ? null : find(level, placed.toString());
    }

    /** {@return where a display was last seen}, or null when this session has not seen it */
    private static Vec3 lastSeen(String uuid) {
        UUID id = parse(uuid);
        return id == null ? null : SEEN.get(id.toString());
    }

    /** Drops what is remembered about one display, which taking it away makes worthless. */
    private static void forget(String uuid) {
        UUID id = parse(uuid);
        if (id != null) SEEN.remove(id.toString());
    }

    /** Drops everything remembered, which a server that is stopping requires. */
    public static void clear() {
        SEEN.clear();
    }

    /**
     * Takes away every anchor that nothing points at any more.
     *
     * <p>What this is for: a display whose effect has gone but which is still standing there. The
     * ordinary path removes one when its effect ends, and the cases it cannot cover are the ones that
     * leave litter — a row from a build that recorded no anchor, a row deleted while its display was
     * in a chunk nobody had loaded, and a crash between the two writes. None of those can be found by
     * looking at the rows, because the rows are exactly what has gone.
     *
     * <p>The tag is what makes it findable, and <b>only tagged displays are touched</b>. The tag is
     * written in exactly one place — {@link #place}, where an anchor is made — so an entity carrying
     * it is one this mod created; an operator's own display, whatever it holds and wherever it stands,
     * was made by something else and is not marked. The tag is also asked for sparingly and in two
     * places only, so the invariant is one a reader can check.
     *
     * <p>Loaded entities only, which is the honest limit of the question: an entity in a chunk nobody
     * has loaded cannot be listed, and sweeping would mean loading the world a chunk at a time. Run it
     * again after walking about, or from the region an effect was placed in.
     *
     * @param named   the anchors some row still names, which are the ones to leave standing
     * @param confirm false to count what would go and change nothing, which is what a destructive
     *                command should ask for first
     * @return how many were found, and how many were taken away when asked to
     */
    public static int sweepOrphans(MinecraftServer server, Set<String> named, boolean confirm) {
        if (server == null) return 0;
        int found = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                // Three tests, and every one of them has to hold. The tag is this mod's own mark and
                // is written in exactly one place — where an anchor is made — so an entity carrying it
                // is one this mod created; nothing an operator or another mod puts in the world
                // carries it, which is what makes this safe to run at all. The type is asked as well,
                // so that a mark that somehow reached an ordinary entity still could not take one
                // away. And a row that still names it is a row that still wants it.
                if (!entity.getTags().contains(ANCHOR_TAG)) continue;
                if (entity.getType() != EntityType.ITEM_DISPLAY) continue;
                if (named.contains(entity.getUUID().toString())) continue;
                found++;
                if (confirm) entity.discard();
            }
        }
        return found;
    }

    /**
     * {@return the level a stored effect's anchor should be in}, or null when it is not loaded
     *
     * <p>An effect is stored with the dimension it was placed in, and a dimension that is not loaded
     * has no level to put a display into: this is the answer for both a world that has never had
     * that dimension and one whose chunks are not loaded yet, and either way there is nothing to do
     * rather than something to get wrong.
     */
    public static ServerLevel levelOf(net.minecraft.server.MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimension)) return level;
        }
        return null;
    }

    /**
     * {@return the level a display was placed in by its own record}
     *
     * <p>Used where a caller has a display and needs the level it belongs to rather than the level a
     * player happens to be standing in.
     */
    public static Level levelOf(Entity display) {
        return display.level();
    }

    /**
     * {@return where a stored effect is now, which is where the display it hangs off is}
     *
     * <p>A row records the point an accurate effect was aimed at, and that is where the effect
     * <em>was</em>: the blank display the server put there is an entity anything in the world may
     * move — a prop set up for roleplay, a tool like Axiom, a command — and the effect follows it,
     * because the library re-reads the entity's own position every frame. So "where is this effect"
     * is a question whose answer is not the one the row holds, and every caller that cares asks it
     * here rather than reading the coordinates out of the row: the delete tool, which removes by
     * place, the replay, which sends what a player can see, and the ending a client reports, which is
     * bounded by how near the reporter is.
     *
     * <p>The point is the library's own sum, read through {@link EffectAttachment#at}: the display's
     * eye plus the offset the effect was attached with. An item display is an entity of no size, so
     * its eye is its position — asked for as the eye because that is the accessor the effect is placed
     * through, rather than because the two agree here.
     *
     * <p>A row that names no display answers with the point it holds, and so does one whose display
     * this server cannot see: the row is then the only thing that knows where the effect was asked
     * for, which is a better answer than a point nobody can check. <b>Loaded entities only</b>, as
     * {@link #find} is: a caller asking about a place the player is looking at is asking about ground
     * that is loaded, and a display that is not loaded cannot be standing in it.
     *
     * @return the point the effect is at, or null when the row names no place at all
     */
    public static EffectToolPlacement.Point whereIs(ServerLevel level, PlacedEffect effect) {
        EffectPlacement where = effect.placement();
        if (where == null) return null;

        EffectToolPlacement.Point planned =
                new EffectToolPlacement.Point(where.centreX(), where.centreY(), where.centreZ());
        if (effect.anchorUuid() == null || effect.anchorUuid().isBlank()) return planned;

        Entity display = find(level, effect.anchorUuid());
        if (display == null) return planned;

        Vec3 eye = display.getEyePosition();
        return EffectAttachment.of(effect.config(), where).at(eye.x, eye.y, eye.z);
    }

    /** {@return the identity a stored string names}, or null when it names none */
    private static UUID parse(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        try {
            return UUID.fromString(uuid.trim());
        } catch (IllegalArgumentException notAnIdentity) {
            return null;
        }
    }
}
