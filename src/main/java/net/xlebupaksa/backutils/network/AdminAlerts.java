package net.xlebupaksa.backutils.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.xlebupaksa.backutils.BackUtils;

/** Tells operators that an action just happened: every online operator, whoever performed it. */
public final class AdminAlerts {

    private AdminAlerts() {}

    /**
     * Sends the alert for one entry to every operator who should hear about it.
     *
     * <p>The actor is included. The alert is what says the menu has something new and plays the
     * operator's own alert sound, and an operator testing that alert is the actor — the one client
     * that would otherwise never see it. Operators who do not want a bell for their own actions
     * turn the alert off in their client config, which is the per-player choice the server cannot
     * make for them.
     */
    public static void notifyOperators(MinecraftServer server, long logId, String actorName) {
        if (server == null) return;

        int told = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.hasPermissions(AdminNetwork.REQUIRED_LEVEL)) continue;
            if (!AdminNetwork.canReceive(player)) continue;

            PacketDistributor.sendToPlayer(player, new AdminAlertPayload(logId, actorName));
            told++;
        }

        if (told == 0) {
            // Said out loud, because an alert nobody receives looks exactly like a broken one.
            BackUtils.LOGGER.warn("No operator could be alerted about {}'s action: none online"
                    + " with permission level {} and the alert channel.", actorName,
                    AdminNetwork.REQUIRED_LEVEL);
        }
    }

    /** Asks one player's client to open the menu. */
    public static void openMenu(ServerPlayer player) {
        if (!AdminNetwork.canReceive(player)) {
            // Told, not ignored: the command exists, so the client is told why nothing happened.
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Your client cannot open the administrator menu; it is missing the channel."),
                    false);
            return;
        }
        PacketDistributor.sendToPlayer(player, new AdminMenuOpenPayload());
    }
}
