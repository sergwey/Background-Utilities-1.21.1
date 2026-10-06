package net.xlebupaksa.backutils.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.network.AdminAlertPayload;

/**
 * The client half of the operator alert: a sound, and a marker that stays until the menu has
 * actually been opened rather than clearing itself on a timer.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = BackUtils.MOD_ID, value = Dist.CLIENT)
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class AdminAlerts {

    /** The newest unseen alert, or null when there is nothing pending. */
    private static AdminAlertPayload pending;

    private AdminAlerts() {}

    /** Client-side handler for {@link AdminAlertPayload}. */
    public static void receive(AdminAlertPayload payload) {
        // Kept even when the marker is switched off: switching the marker off is about drawing the
        // icon, and the menu still has to be told that there is something new.
        pending = payload;

        if (BackUtilsClientConfig.isAdminAlertEnabled()) {
            playSound();
        }
    }

    public static boolean hasPending() {
        return pending != null;
    }

    /**
     * Plays the configured sound once, so an operator can hear what they have chosen in the settings
     * tab. An arriving alert is the only other time this client makes the sound.
     */
    public static void preview() {
        playSound();
    }

    /** Called when the menu opens: the alert has been delivered, so it stops asking. */
    public static void clear() {
        pending = null;
    }

    private static void playSound() {
        ClientSounds.play(BackUtilsClientConfig.getAdminAlertSound(),
                (float) BackUtilsClientConfig.getAdminAlertVolume(),
                (float) BackUtilsClientConfig.getAdminAlertPitch());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // Leaving the server must not carry an alert into the next one.
        pending = null;
    }
}
