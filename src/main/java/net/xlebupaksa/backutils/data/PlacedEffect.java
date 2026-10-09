package net.xlebupaksa.backutils.data;

import net.xlebupaksa.backutils.item.EffectToolConfig;

/**
 * One effect an operator has placed: where it is, who put it there, and what it was set to.
 *
 * <p>This is the mod's own record, not Photon's. Photon keeps no server-side state at all — it
 * relays a command to the clients that happen to be watching — so an effect placed before a player
 * arrived is invisible to them, and an effect outlives its chunk only as long as somebody remembers
 * it. The list of what was placed is therefore ours to hold, and this is one line of it.
 *
 * <p>The lifetime is a wall-clock instant rather than a tick count, for the reason the music zones
 * use one: a count of ticks means nothing across a restart, and a placed effect is meant to survive
 * one.
 *
 * @param configNbt the tool's settings as stored text, which is how they survive a restart; the
 *                  effect's kind, scale, rotation, offset, delay and forced-death flag are all in
 *                  there rather than in columns of their own, so adding a setting to the tool does
 *                  not need a database migration
 * @param anchorUuid the display an accurate placement hangs off, empty when it hangs off something
 *                  the world already had; kept by identity rather than by entity id, which is handed
 *                  out per session and means nothing after a restart
 * @param targetUuid the entity an entity-attached placement hangs off, empty for every other mode;
 *                   an identity for the same reason, and the only way to answer "which effects are on
 *                   this entity" — a placement records where an entity stood, not which entity it was
 * @param expiresAtMillis when the effect is removed, or {@link #PERMANENT}
 * @param durationTicks the lifetime as it was configured, kept for display
 */
public record PlacedEffect(
        long id,
        EffectPlacement placement,
        String ownerUuid,
        String ownerName,
        String effectPath,
        String configNbt,
        String anchorUuid,
        String targetUuid,
        long createdAtMillis,
        long expiresAtMillis,
        int durationTicks
) {

    /**
     * An effect that hangs off a place rather than off an entity.
     *
     * <p>The shorter way in for the many rows that name no entity, so a caller with a block or a point
     * to describe does not have to say so twice.
     */
    public PlacedEffect(long id, EffectPlacement placement, String ownerUuid, String ownerName,
                        String effectPath, String configNbt, String anchorUuid,
                        long createdAtMillis, long expiresAtMillis, int durationTicks) {
        this(id, placement, ownerUuid, ownerName, effectPath, configNbt, anchorUuid, "",
                createdAtMillis, expiresAtMillis, durationTicks);
    }

    /** Sentinel for "this effect never expires on its own". */
    public static final long PERMANENT = 0L;

    public boolean permanent() {
        return expiresAtMillis <= 0L;
    }

    public boolean expired(long nowMillis) {
        return !permanent() && nowMillis >= expiresAtMillis;
    }

    /** {@return the tool settings this effect was placed with} */
    public EffectToolConfig config() {
        return EffectToolConfig.fromStoredText(configNbt);
    }

    /**
     * {@return true when this effect sits in the same block as the given one}
     *
     * <p>What the delete tool asks: clicking a pink cube removes everything in that place, so two
     * effects that share a block are neighbours however differently they were configured.
     */
    public boolean sameBlockAs(EffectPlacement other) {
        return placement != null && other != null
                && placement.dimension().equals(other.dimension())
                && placement.blockX() == other.blockX()
                && placement.blockY() == other.blockY()
                && placement.blockZ() == other.blockZ();
    }

    /** {@return a short description, for command output} */
    public String describe() {
        String life = permanent() ? "permanent" : "ends at " + expiresAtMillis;
        return "#" + id + " " + placement.describe() + " -> " + effectPath
                + " by " + ownerName + " (" + life + ")";
    }
}
