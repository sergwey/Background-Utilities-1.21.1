package net.xlebupaksa.backutils.profile;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.xlebupaksa.backutils.data.ModAttachments;
import net.xlebupaksa.backutils.data.ProfileSnapshot;

/**
 * Gives a player's tab-list entry the name their profile says. The attachment is registered with a default, so it always
 * answers — a player without a name profile carries {@link ProfileSnapshot#DEFAULT}.
 */
@SuppressWarnings("unused") // called by the event bus
public class TabListNameHandler {

    @SubscribeEvent
    public void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ProfileSnapshot snap = player.getData(ModAttachments.PROFILE.get());
        event.setDisplayName(ProfileText.display(snap, player.getName().getString()));
    }
}
