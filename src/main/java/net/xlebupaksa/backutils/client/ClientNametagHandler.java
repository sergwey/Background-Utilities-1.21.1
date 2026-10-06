package net.xlebupaksa.backutils.client;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.xlebupaksa.backutils.data.ModAttachments;
import net.xlebupaksa.backutils.data.ProfileSnapshot;
import net.xlebupaksa.backutils.profile.ProfileText;

/**
 * Renders the roleplay name above a player's head at render time, which leaves the real game
 * profile untouched. Client-only: this class must never be loaded on a dedicated server.
 */
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public final class ClientNametagHandler {

    private ClientNametagHandler() {}

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ProfileSnapshot snapshot = player.getData(ModAttachments.PROFILE.get());
        if (snapshot == null || snapshot.nameProfileId() == 0L) return;

        MutableComponent name = ProfileText.display(snapshot, player.getName().getString());

        // Keep the scoreboard team prefix, suffix and colour, which a plain literal would drop.
        PlayerTeam team = player.getTeam();
        event.setContent(team != null ? team.getFormattedName(name) : name);
    }
}
