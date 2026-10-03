package net.xlebupaksa.backutils.profile;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.xlebupaksa.backutils.data.ProfileSnapshot;

/**
 * Builds the components shown for a player from the placeholder text in their profiles: {@code {player}} is the real
 * game profile name, {@code {m}} the message body that a chat format is required to contain.
 *
 * <p>The text reaches {@link Component#literal}, which Ember's Text API parses on the client, per literal and per line, so
 * a tag and the content it applies to must never be split across components.
 */
public final class ProfileText {

    /** Replaced with the acting player's name. Matches the data-layer constant. */
    public static final String PLAYER = net.xlebupaksa.backutils.data.ProfileText.PLAYER_PLACEHOLDER;

    public static final String MESSAGE = "{m}";

    private ProfileText() {}

    /** {@return the display name from the snapshot with {@code {player}} filled in} */
    public static MutableComponent display(ProfileSnapshot snapshot, String playerName) {
        return Component.literal(resolve(snapshot.displayedName(), playerName));
    }

    public static String resolve(String text, String playerName) {
        return text == null ? "" : text.replace(PLAYER, playerName);
    }
}
