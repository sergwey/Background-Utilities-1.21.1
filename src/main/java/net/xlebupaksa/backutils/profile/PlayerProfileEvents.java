package net.xlebupaksa.backutils.profile;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.xlebupaksa.backutils.BackUtils;

@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public class PlayerProfileEvents {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (BackUtils.data() == null) return;

        ProfileLoader.push(player, ProfileLoader.load(player.getName().getString()));
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (BackUtils.data() == null) return;

        // Respawning builds a fresh ServerPlayer, so the synced attachments are re-pushed: ProfileOptions is neither persisted nor copied on death.
        ProfileLoader.reload(player);
    }
}
