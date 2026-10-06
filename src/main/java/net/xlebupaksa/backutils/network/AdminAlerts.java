package net.xlebupaksa.backutils.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsConfig;

/** Tells operators that an action just happened. */
public final class AdminAlerts {

    private AdminAlerts() {}

    /**
     * Sends the alert for one entry to every operator who should hear about it.
     *
     * <p>The actor is included unless the server's {@code alertSelfActions} says otherwise. The
     * alert is what says the menu has something new, and an operator testing that alert is the
     * actor, so excluding them by default would leave the feature unobservable to whoever set it up.
     *
     * @param actor the player who performed the action, left out when self-alerts are off
     */
    public static void notifyOperators(MinecraftServer server, long logId, ServerPlayer actor) {
        if (server == null || actor == null) return;

        String actorName = actor.getName().getString();
        boolean selfAlerts = BackUtilsConfig.isSelfAlertEnabled();
        int eligible = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.hasPermissions(AdminNetwork.REQUIRED_LEVEL)) continue;
            if (!AdminNetwork.canReceive(player)) continue;
            eligible++;
            if (!selfAlerts && player.getUUID().equals(actor.getUUID())) continue;

            PacketDistributor.sendToPlayer(player, new AdminAlertPayload(logId, actorName));
        }

        if (eligible == 0) {
            // Said out loud, because an alert nobody receives looks exactly like a broken one. Not
            // raised when the actor was the only candidate and chose not to hear about their own.
            BackUtils.LOGGER.warn("No operator could be alerted about {}'s action: none online"
                    + " with permission level {} and the alert channel.", actorName,
                    AdminNetwork.REQUIRED_LEVEL);
        }
    }

    /** Asks one player's client to open the menu. */
    public static void openMenu(ServerPlayer player) {
        if (!AdminNetwork.canReceive(player)) {
            // Told, not ignored: the command exists, so the client is told why nothing happened.
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "backutils.admin.no_channel"), false);
            return;
        }
        PacketDistributor.sendToPlayer(player, new AdminMenuOpenPayload());
    }
}
