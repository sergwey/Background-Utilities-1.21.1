package net.xlebupaksa.backutils.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.xlebupaksa.backutils.BackUtils;

/** Tells operators that an action just happened: every online operator, except the actor. */
public final class AdminAlerts {

    private AdminAlerts() {}

    /** Sends the alert for one entry to every operator who should hear about it. */
    public static void notifyOperators(MinecraftServer server, long logId, String actorName) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.hasPermissions(AdminNetwork.REQUIRED_LEVEL)) continue;
            if (player.getName().getString().equals(actorName)) continue;
            if (!AdminNetwork.canReceive(player)) continue;

            PacketDistributor.sendToPlayer(player, new AdminAlertPayload(logId, actorName));
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
