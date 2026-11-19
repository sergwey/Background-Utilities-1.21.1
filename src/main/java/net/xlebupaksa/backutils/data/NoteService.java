package net.xlebupaksa.backutils.data;

import net.minecraft.server.level.ServerPlayer;
import net.xlebupaksa.backutils.BackUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The edit lock and read/save operations for administrator notes.
 *
 * <p>One lease per target player covers the whole document, so adding, deleting and saving pages is
 * one atomic act from the point of view of every other administrator. The lease is intentionally in
 * memory: a server restart drops it, and the document's version still prevents an old draft from
 * overwriting a newer save. Every method that reads or changes a lease is synchronized; the server
 * thread is normally the only caller, but a shutdown hook or a test may not be.
 */
public final class NoteService {

    /** How long an editor keeps the lock without a heartbeat. */
    public static final long LEASE_MILLIS = 30_000L;

    public record Lease(String holderUuid, String holderName, long expiresAtMillis) {
        public boolean heldBy(ServerPlayer player) {
            return player != null && sameUuid(player.getStringUUID(), holderUuid);
        }

        public boolean heldBy(UUID player) {
            return player != null && sameUuid(player.toString(), holderUuid);
        }
    }

    public record State(String playerName, NoteData.Document document, Lease lease) {
        public List<String> pages() {
            return document.texts();
        }
    }

    public record LockResult(boolean granted, State state, String holderName) {
    }

    public record SaveAttempt(NoteData.SaveResult result, State state) {
        public boolean saved() {
            return result == NoteData.SaveResult.SAVED;
        }
    }

    private static final Map<UUID, Lease> LEASES = new HashMap<>();

    private NoteService() {}

    /** {@return the current state, including an active lease when there is one} */
    public static synchronized State state(UUID target, String fallbackName) {
        NoteData data = notes();
        NoteData.Document document = data == null
                ? NoteData.Document.blank(fallbackName) : data.read(target, fallbackName);
        return new State(document.playerName(), document, active(target));
    }

    /** {@return true when the player now holds the lease; a competing holder is returned in the state} */
    public static synchronized LockResult acquire(ServerPlayer admin, UUID target, String fallbackName) {
        return acquireFor(admin.getUUID(), admin.getName().getString(), target, fallbackName);
    }

    /** The holder-taking form used by the lease tests; the server passes its player's identity. */
    public static synchronized LockResult acquireFor(UUID holderUuid, String holderName, UUID target,
            String fallbackName) {
        Lease current = active(target);
        if (current != null && !current.heldBy(holderUuid)) {
            return new LockResult(false, state(target, fallbackName), current.holderName());
        }
        Lease lease = new Lease(holderUuid.toString(), holderName,
                System.currentTimeMillis() + LEASE_MILLIS);
        LEASES.put(target, lease);
        return new LockResult(true, state(target, fallbackName), lease.holderName());
    }

    /** Renews the caller's lease. Returns the current state, whose lease may belong to somebody else. */
    public static synchronized State heartbeat(ServerPlayer admin, UUID target, String fallbackName) {
        return heartbeatFor(admin.getUUID(), target, fallbackName);
    }

    public static synchronized State heartbeatFor(UUID holderUuid, UUID target, String fallbackName) {
        Lease current = active(target);
        if (current != null && current.heldBy(holderUuid)) {
            LEASES.put(target, new Lease(current.holderUuid(), current.holderName(),
                    System.currentTimeMillis() + LEASE_MILLIS));
        }
        return state(target, fallbackName);
    }

    /** Releases the caller's lease, leaving somebody else's alone. */
    public static synchronized State release(ServerPlayer admin, UUID target, String fallbackName) {
        return releaseFor(admin.getUUID(), target, fallbackName);
    }

    public static synchronized State releaseFor(UUID holderUuid, UUID target, String fallbackName) {
        Lease current = active(target);
        if (current != null && current.heldBy(holderUuid)) LEASES.remove(target);
        return state(target, fallbackName);
    }

    /** Drops every lease held by a player who left; a stale lock is worse than an expired one. */
    public static synchronized void releaseAll(ServerPlayer admin) {
        LEASES.entrySet().removeIf(entry -> entry.getValue().heldBy(admin));
    }

    /** Clears all leases when the server stops; a lease belongs to one server session. */
    public static synchronized void clear() {
        LEASES.clear();
    }

    /**
     * Saves the whole document when the caller holds the lease and names the current version.
     *
     * <p>The lease is released only after a successful save. A version conflict or invalid content
     * leaves the lease in place so the editor can refresh and try again, rather than silently
     * losing the lock to a third administrator in the middle of the warning.
     */
    public static synchronized SaveAttempt save(ServerPlayer admin, UUID target, String fallbackName,
            int baseVersion, List<String> pages) {
        return saveFor(admin.getUUID(), admin.getName().getString(), target, fallbackName,
                baseVersion, pages);
    }

    public static synchronized SaveAttempt saveFor(UUID holderUuid, String holderName, UUID target,
            String fallbackName, int baseVersion, List<String> pages) {
        Lease current = active(target);
        if (current == null || !current.heldBy(holderUuid)) {
            return new SaveAttempt(NoteData.SaveResult.VERSION_MISMATCH,
                    state(target, fallbackName));
        }
        NoteData data = notes();
        if (data == null) {
            return new SaveAttempt(NoteData.SaveResult.INVALID, state(target, fallbackName));
        }
        NoteData.SaveOutcome outcome = data.save(target, fallbackName, baseVersion, pages,
                holderUuid.toString(), holderName);
        if (outcome.saved()) LEASES.remove(target);
        return new SaveAttempt(outcome.result(), new State(outcome.document().playerName(),
                outcome.document(), active(target)));
    }

    /** {@return the active lease, or null when none is live} */
    public static synchronized Lease lease(UUID target) {
        return active(target);
    }

    private static Lease active(UUID target) {
        Lease lease = LEASES.get(target);
        if (lease != null && lease.expiresAtMillis() <= System.currentTimeMillis()) {
            LEASES.remove(target);
            return null;
        }
        return lease;
    }

    private static NoteData notes() {
        return BackUtils.data() == null ? null : BackUtils.data().notes();
    }

    private static boolean sameUuid(String left, String right) {
        if (left == null || right == null) return false;
        return left.replace("-", "").equalsIgnoreCase(right.replace("-", ""));
    }
}
