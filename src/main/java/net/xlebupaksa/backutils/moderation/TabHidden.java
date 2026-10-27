package net.xlebupaksa.backutils.moderation;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.xlebupaksa.backutils.network.TabHiddenNetwork;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * The players whose name is kept out of the player lists.
 *
 * <p>The lists are drawn on the client from the client's own records of who is online, so what this
 * class does is keep the set and have it sent: {@link TabHiddenNetwork} carries it to every client,
 * and two client mixins leave a hidden player out of the tab list and out of the social interactions
 * screen. Nothing about the player's entry is touched, which is the point — the entry is also what the
 * client builds a player's body from, so removing it would hide somebody from the world and not merely
 * from a list.
 *
 * <p>Kept by identity and not written down, for the reason a suspension is not written down either:
 * it is a decision about a player who is on the server now, and a restart that outlived the operator
 * would leave a name hidden with nobody to unhide it. What it does outlive is the player's own
 * session — logging out and back in does not put the name back, because a client that joins is sent
 * the set.
 */
public final class TabHidden {

    /** Who is hidden, with the name to answer a listing with, in the order they were hidden. */
    private static final Map<UUID, String> HIDDEN = new LinkedHashMap<>();

    /** {@return true when this player's name is kept out of the player lists} */
    public static boolean hidden(UUID player) {
        return player != null && HIDDEN.containsKey(player);
    }

    /** {@return the hidden names, in the order they were hidden}, separated by commas */
    public static String names() {
        StringJoiner out = new StringJoiner(", ");
        for (String name : HIDDEN.values()) out.add(name);
        return out.toString();
    }

    /**
     * Hides a player's name, and has every client told.
     *
     * @return {@code false} when the name was already hidden, which is not an event
     */
    public static boolean hide(ServerPlayer player) {
        if (HIDDEN.putIfAbsent(player.getUUID(), player.getName().getString()) != null) return false;
        TabHiddenNetwork.broadcast(player.server, HIDDEN.keySet());
        return true;
    }

    /**
     * Puts a player's name back.
     *
     * @return {@code false} when it was not hidden, which is not an event either
     */
    public static boolean show(ServerPlayer player) {
        if (HIDDEN.remove(player.getUUID()) == null) return false;
        TabHiddenNetwork.broadcast(player.server, HIDDEN.keySet());
        return true;
    }

    /**
     * Tells a player who has just joined what is hidden.
     *
     * <p>A client starts with an empty set and forgets the last server's when it disconnects, so the
     * one moment the set has to be sent to somebody in particular is the moment they arrive. The name
     * is taken again from the player rather than trusted from the map, because an account renamed
     * while it was hidden would otherwise be listed under a name it no longer has.
     */
    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (hidden(player.getUUID())) HIDDEN.put(player.getUUID(), player.getName().getString());
        TabHiddenNetwork.send(player, List.copyOf(HIDDEN.keySet()));
    }
}

