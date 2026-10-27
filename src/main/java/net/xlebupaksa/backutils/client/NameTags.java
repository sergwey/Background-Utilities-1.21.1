package net.xlebupaksa.backutils.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.xlebupaksa.backutils.BackUtils;

/**
 * What this client last heard about drawing the tags above players' heads.
 *
 * <p>The setting is the server's — see {@code BackUtilsConfig.HIDE_PLAYER_NAME_TAGS} — and this is
 * only the client's copy of the answer, kept because the renderer asks on every frame for every
 * player and the answer arrives from the network thread. Volatile for the same reason: the value is
 * replaced by the payload's handler and read by the renderer, and a half-updated boolean is the one
 * thing that could make a tag flicker for a frame.
 *
 * <p>False until told otherwise, and false again once the connection goes: a client that has not been
 * told, or that has left the server that told it, draws what the game draws by default.
 */
@EventBusSubscriber(modid = BackUtils.MOD_ID, value = Dist.CLIENT)
public final class NameTags {

    private static volatile boolean hidden;

    private NameTags() {}

    /** {@return true when the server has asked for the tags above players' heads not to be drawn} */
    public static boolean hidden() {
        return hidden;
    }

    /** Takes the server's answer. */
    public static void setHidden(boolean value) {
        hidden = value;
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        hidden = false;
    }
}
