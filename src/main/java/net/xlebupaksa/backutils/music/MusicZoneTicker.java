package net.xlebupaksa.backutils.music;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives the music zone sweep: half a second between passes, short enough that walking into a room
 * feels immediate under the fade-in.
 */
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public class MusicZoneTicker {

    private static final int SWEEP_INTERVAL_TICKS = 10;

    private int ticks;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (++ticks < SWEEP_INTERVAL_TICKS) return;
        ticks = 0;
        MusicZoneManager.sweep(server);
    }

    /**
     * Forgets what a leaving player was standing in, so a player who logs out inside a zone and
     * back in is still sent the play message that starts the music again.
     */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MusicZoneManager.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        MusicZoneManager.clear();
    }
}
