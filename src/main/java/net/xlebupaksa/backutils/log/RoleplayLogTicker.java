package net.xlebupaksa.backutils.log;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.xlebupaksa.backutils.network.RoleplayLogBroadcaster;
import net.xlebupaksa.backutils.network.SentLogWatermark;

/**
 * Drives delivery of the roleplay log: lines are pushed when an action happens, and the sweep covers the entries a push
 * could not reach, such as one written while the recipient was mid-connection.
 */
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public class RoleplayLogTicker {

    /** Ten seconds: the push path already handles anything that needs to be instant. */
    private static final int SWEEP_INTERVAL_TICKS = 200;

    private int ticks;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (++ticks < SWEEP_INTERVAL_TICKS) return;
        ticks = 0;
        RoleplayLogBroadcaster.flushPending(server);
    }

    /**
     * Starts a joining player's log at the current end: without a watermark every entry still visible to them would count
     * as unsent, and the next sweep would replay the backlog at them.
     */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RoleplayLogBroadcaster.seed(player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SentLogWatermark.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        SentLogWatermark.clear();
    }
}
