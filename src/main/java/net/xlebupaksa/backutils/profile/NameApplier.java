package net.xlebupaksa.backutils.profile;

import net.minecraft.server.level.ServerPlayer;

/**
 * Refreshes the server-side state derived from a player's profile.
 *
 * <p>The display name is not written onto the entity, since that leaks the roleplay name into death messages, kill credit
 * and other mods' output, and fights scoreboard team prefixes and colours: the nameplate is drawn on the client from the
 * synced {@link net.xlebupaksa.backutils.data.ProfileSnapshot} instead. The tab list name is resolved server-side through
 * {@code PlayerEvent.TabListNameFormat}, and {@link ServerPlayer} caches it, so the cache is invalidated by hand.
 */
public final class NameApplier {

    private NameApplier() {}

    public static void apply(ServerPlayer player) {
        player.refreshTabListName();
    }
}
