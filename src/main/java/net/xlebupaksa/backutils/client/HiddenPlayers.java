package net.xlebupaksa.backutils.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.xlebupaksa.backutils.BackUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The names this client keeps out of its player lists.
 *
 * <p>A player list is drawn on the client from the client's own records of who is online, so what the
 * server can do about one is tell the client which of those records not to draw. That is all this is:
 * a set, replaced whole whenever the answer changes, and read by the two mixins that draw the lists —
 * the tab list and the social interactions screen. Nothing about the player is touched, so a hidden
 * player is still a player in the world, with a body and a name above it.
 *
 * <p>Client-only, and cleared when the connection goes: the set belongs to a server, and a client that
 * kept it would carry one server's decisions into the next.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID, value = Dist.CLIENT)
public final class HiddenPlayers {

    private static final Set<UUID> HIDDEN = new HashSet<>();

    private HiddenPlayers() {}

    /** Replaces the set with the one the server last sent. */
    public static void apply(List<UUID> hidden) {
        HIDDEN.clear();
        HIDDEN.addAll(hidden);
    }

    /** {@return true when nothing is hidden}, which is the common case and costs one test a frame */
    public static boolean none() {
        return HIDDEN.isEmpty();
    }

    /** {@return true when this player is to be left out of a player list} */
    public static boolean hidden(UUID player) {
        return player != null && HIDDEN.contains(player);
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        HIDDEN.clear();
    }
}
