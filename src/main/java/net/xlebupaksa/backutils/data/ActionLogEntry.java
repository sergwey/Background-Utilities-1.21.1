package net.xlebupaksa.backutils.data;

import java.util.List;
import java.util.UUID;

/**
 * One roleplay action, as stored in the action log.
 *
 * @param dimension where it happened, or null for rows written before positions were stored
 * @param adminNote something only an administrator may read, such as the result behind a hidden
 *                  roll, or empty
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
        List<UUID> hiddenFrom,
        String adminNote
) {

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
        return resolve(template, actorName);
    }

    /**
     * {@return the template with both name placeholders filled in under this name}
     *
     * <p>Public because the settings tab previews a format through the same substitution the log
     * renders it with, so the preview cannot show something the log will not.
     */
    public static String resolve(String template, String name) {
        return fill(template, name, false);
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
        return fill(template, shown, viewerIsActor);
    }

    /** Fills both name placeholders, which always resolve to the same player. */
    private static String fill(String template, String name, boolean underlined) {
        String shown = ProfileText.underlined(name == null ? "" : name, underlined);
        String possessive = ProfileText.underlined(ProfileText.possessive(name), underlined);
        String text = template
                .replace(ProfileText.POSSESSIVE_PLACEHOLDER, possessive)
                .replace(ProfileText.PLAYER_PLACEHOLDER, shown);
        // A placeholder that resolved to nothing must not leave a gap behind it: one roll format
        // has to read both "Dev's dice rolled 42/100!" and "dice rolled 42/100!".
        return text.replaceAll("[ \\t]{2,}", " ").trim();
    }
}
