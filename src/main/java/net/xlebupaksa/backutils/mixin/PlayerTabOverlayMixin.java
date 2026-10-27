package net.xlebupaksa.backutils.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.xlebupaksa.backutils.client.HiddenPlayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps a hidden name out of the tab list.
 *
 * <p>Asked of the list the overlay draws rather than of the drawing: {@code getPlayerInfos} is the one
 * place the overlay decides who it is about to show, and everything else it does — columns, ping,
 * headers — is worked out from what that returns. So the rows simply are not there, rather than being
 * drawn and then covered up, and no other part of the overlay has to be told about it.
 *
 * <p>Client-only, and it asks {@link HiddenPlayers} first: nothing is hidden on a server that has no
 * use for the command, and the answer is one test of an empty set per frame.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

    @Inject(method = "getPlayerInfos", at = @At("RETURN"), cancellable = true)
    private void backutils$withoutHiddenNames(CallbackInfoReturnable<List<PlayerInfo>> info) {
        List<PlayerInfo> players = info.getReturnValue();
        if (players == null || HiddenPlayers.none()) return;

        List<PlayerInfo> shown = new ArrayList<>(players.size());
        for (PlayerInfo player : players) {
            if (!HiddenPlayers.hidden(player.getProfile().getId())) shown.add(player);
        }
        info.setReturnValue(shown);
    }
}
