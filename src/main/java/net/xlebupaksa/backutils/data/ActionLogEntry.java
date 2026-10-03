package net.xlebupaksa.backutils.data;

import java.util.List;
import java.util.UUID;

/**
 * One roleplay action, as stored in the action log.
 *
 * @param dimension where it happened, or null for rows written before positions were stored
 */
public record ActionLogEntry(
        long id,
        String actorId,
        String actorName,
        String template,
        String createdAt,
        List<UUID> accessList,
        String dimension,
        double x,
        double y,
        double z,
        boolean hiddenAll,
        List<UUID> hiddenFrom
) {

    public boolean hasPosition() {
        return dimension != null && !dimension.isBlank();
    }

    /** {@return true when this player may be shown the entry: witnessing is not enough on its
     * own, since an entry can also be withheld from everyone, or from this witness. */
    public boolean visibleTo(UUID viewer) {
        if (viewer == null || hiddenAll) return false;
        return accessList.contains(viewer) && !hiddenFrom.contains(viewer);
    }

    public UUID actorUuid() {
        if (actorId == null || actorId.isBlank()) return null;
        try {
            return UUID.fromString(actorId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@return the text with the actor's name substituted and nothing highlighted} */
    public String text() {
        return template.replace(ProfileText.PLAYER_PLACEHOLDER, actorName);
    }

    /**
     * {@return the text as this viewer should read it}
     *
     * <p>The actor's name is underlined only for the player who performed the action.
     */
    public String textFor(UUID viewerId) {
        return textFor(viewerId, actorName);
    }

    /**
     * {@return the text as this viewer should read it, under the given name}
     *
     * <p>The name is passed in rather than taken from the entry because the log shows the actor's
     * roleplay name, resolved from their profile at the moment the line is sent.
     */
    public String textFor(UUID viewerId, String name) {
        String shown = name == null || name.isBlank() ? actorName : name;
        boolean viewerIsActor = viewerId != null && actorId != null
                && actorId.equals(viewerId.toString());
        return template.replace(ProfileText.PLAYER_PLACEHOLDER,
                ProfileText.underlined(shown, viewerIsActor));
    }
}
