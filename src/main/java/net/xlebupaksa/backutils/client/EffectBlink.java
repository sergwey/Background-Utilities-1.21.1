package net.xlebupaksa.backutils.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The two blinks the effect tool draws with: the red one a deletion leaves behind, and the pink one
 * a newly attached entity effect arrives with.
 *
 * <p>The spec asks for both. *"When effect is attached, blinks a pink outline on the entity for a
 * second"* is the attachment blink, and the red a deletion flashes for a second is its counterpart.
 * They are one idea — a short-lived mark, on for half of a short cycle and off for the other half,
 * read from the client's own tick count so that every cube and outline drawn in one frame blinks
 * together. Two halves of a blink in one frame read as a flicker; one reads as a signal.
 *
 * <p>What each is keyed by follows from what it is about. A deletion is about a <em>place</em> or an
 * entity: the effect is gone, so the thing the click named is what the red has to be drawn on until
 * the answer comes back. An attachment is only ever about an <em>entity</em>, and the spec asks for
 * it for entities alone: a place already has the pink cube, which <em>is</em> the indication that
 * something is there, so there is nothing for a second pink cube to add.
 *
 * <p>The state is kept here rather than in the drawing code because a blink has to survive between
 * frames. A renderer is handed the fraction of a tick that has passed and draws when it is asked,
 * so what it can see by itself is only "now" and never "a second ago" — the deletion had to have
 * been written down by whoever saw the click, and the attachment by whoever saw the effect start.
 *
 * <p>It is deliberately free of Minecraft types, places included: a place is kept as the packed long
 * a {@code BlockPos} is made of, which is a position in exactly the form this needs and one that a
 * harness can hand in without a world, and an entity is kept as the id the registry was handed. The
 * arithmetic that decides which half of the blink is being drawn is a plain function of a tick
 * count, so it can be pinned down without a client.
 *
 * <p>Nothing here is persisted and nothing is sent: a blink is this client's own acknowledgement of
 * something it saw happen, the server has already been told what to remove or has already told this
 * client what it placed, and neither colour is something another player is meant to see.
 */
final class EffectBlink {

    /** How long a deletion or an attachment stays lit, in ticks: the second the spec asks for. */
    static final int FLASH_TICKS = 20;

    /**
     * How long one half of a blink lasts, in ticks.
     *
     * <p>Five, so a second of blinking is two pulses of colour and two gaps. A quarter of a second a
     * side is long enough to be read as blinking rather than as a flicker, and short enough that the
     * colour appears within a moment of the click whichever half the cycle happens to be in.
     */
    static final int HALF_TICKS = 5;

    /** The places deleted a moment ago, by packed position, with the ticks each has left. */
    private static final Map<Long, Integer> PLACES = new HashMap<>();

    /** The entities whose effects were deleted a moment ago, by entity id, and the ticks left. */
    private static final Map<Integer, Integer> ENTITIES = new HashMap<>();

    /**
     * The entities an effect was attached to a moment ago, by entity id, and the ticks left.
     *
     * <p>Kept apart from the deletions an entity carries although both are counted down the same
     * way, because they say different things: a deleted entity is marked red for what has gone, and
     * an attached one pink for what has just arrived.
     */
    private static final Map<Integer, Integer> ATTACHED = new HashMap<>();

    /** Ticks since the level was loaded, which is what the blink's phase is read from. */
    private static int ticks;

    private EffectBlink() {}

    /**
     * Remembers a place as just deleted.
     *
     * <p>Needed because a deletion takes the effect away: the cube a click was aimed at is drawn from
     * the effect that is there, and once the removal reaches this client there is nothing left to
     * draw one around. This is what is drawn instead, so that the place itself is what blinks.
     */
    static void flashPlace(long place) {
        PLACES.put(place, FLASH_TICKS);
    }

    /** Remembers an entity as just deleted from, for the reason a place is remembered. */
    static void flashEntity(int entity) {
        ENTITIES.put(entity, FLASH_TICKS);
    }

    /**
     * Remembers an entity as one an effect has just been attached to.
     *
     * <p>Called by {@link PlacedEffects} at the moment an entity-anchored effect starts for the
     * first time, which is the only moment the attachment is news: the effect is spawned by that
     * call, so a client that drew the outline before it would be announcing something that had not
     * happened yet. A place is not remembered this way — the spec asks for the blink for an attached
     * effect, and a place is already announced by the pink cube that stands where it is.
     */
    static void flashAttached(int entity) {
        ATTACHED.put(entity, FLASH_TICKS);
    }

    /**
     * {@return the places still blinking}, whether or not an effect is left in them
     *
     * <p>A copy, because the renderer walks it while the client tick may be ageing it.
     */
    static Set<Long> places() {
        return Set.copyOf(PLACES.keySet());
    }

    /** {@return true while an entity is still blinking from a deletion} */
    static boolean entityFlashing(int entity) {
        return ENTITIES.containsKey(entity);
    }

    /** {@return true while an entity is still blinking from an effect having been attached} */
    static boolean attachmentFlashing(int entity) {
        return ATTACHED.containsKey(entity);
    }

    /**
     * {@return true while the lit half of the blink is the one to draw}
     *
     * <p>Asked of the client's own tick count rather than of a deletion's remaining time, so that
     * everything blinking in one frame is in the same half of the cycle: a hover and a deletion
     * beside it that disagreed would look like two different things being said at once.
     *
     * <p>Read once per frame by the renderer, whether or not anything is blinking: a frame with
     * nothing to flash asks the same question and answers it the same way, which is cheaper to read
     * than a third state saying "nothing is happening".
     */
    static boolean lit() {
        return lit(ticks);
    }

    /**
     * {@return true while the colour is on, for a tick count}, which is the whole of the blink
     *
     * <p>The count is taken as a parameter rather than read from the field so that the arithmetic can
     * be handed any tick of the cycle: it is the one part of this class that is worth pinning down
     * without a client, and a caller that reads the field is the one line above.
     */
    static boolean lit(int tickCount) {
        return (tickCount / HALF_TICKS) % 2 == 0;
    }

    /** Ages every blink by one tick and gives up on the ones whose second has passed. */
    static void tick() {
        ticks++;
        age(PLACES);
        age(ENTITIES);
        age(ATTACHED);
    }

    /** Forgets every blink, which a level that is going away requires: nothing would be drawable. */
    static void clear() {
        PLACES.clear();
        ENTITIES.clear();
        ATTACHED.clear();
        ticks = 0;
    }

    /**
     * Takes a tick off every remaining blink and drops the ones that have run out.
     *
     * <p>Written once for all three kinds of blink: they differ in what they are keyed by and in
     * nothing else, and a second copy of a countdown is a second place for it to be wrong.
     */
    private static void age(Map<?, Integer> blinking) {
        Iterator<? extends Map.Entry<?, Integer>> entries = blinking.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<?, Integer> entry = entries.next();
            int left = entry.getValue() - 1;
            if (left <= 0) {
                entries.remove();
            } else {
                entry.setValue(left);
            }
        }
    }
}
