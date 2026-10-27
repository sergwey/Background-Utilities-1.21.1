package net.xlebupaksa.backutils.mixin;

import net.minecraft.client.gui.screens.social.SocialInteractionsPlayerList;
import net.xlebupaksa.backutils.client.HiddenPlayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Keeps a hidden name out of the social interactions screen's list.
 *
 * <p>The list is built by being handed every online player at once — when the screen opens, and again
 * whenever a page is switched or the search changes — so the set that is handed over is where a name
 * has to be dropped. {@link SocialInteractionsScreenMixin} covers the other way in, a player joining
 * while the screen is open; without this one the whole list would arrive unfiltered and the screen
 * would look as though nothing had been hidden at all.
 */
@Mixin(SocialInteractionsPlayerList.class)
public abstract class SocialInteractionsPlayerListMixin {

    @ModifyVariable(method = "updatePlayerList", at = @At("HEAD"), argsOnly = true)
    private Collection<UUID> backutils$withoutHiddenNames(Collection<UUID> players) {
        if (players.isEmpty() || HiddenPlayers.none()) return players;

        List<UUID> shown = new ArrayList<>(players.size());
        for (UUID player : players) {
            if (!HiddenPlayers.hidden(player)) shown.add(player);
        }
        return shown;
    }
}
