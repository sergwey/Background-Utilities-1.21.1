package net.xlebupaksa.backutils.mixin;

import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.xlebupaksa.backutils.client.HiddenPlayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a hidden name out of the social interactions screen.
 *
 * <p>Refused where the screen takes a player in, which is both when it is opened and whenever somebody
 * else joins while it is open: the screen builds its list by being handed each player in turn, so a
 * row that is never handed over is a row that is never there — in any of its pages, and however it is
 * searched.
 *
 * <p>The screen's own Hide button is a different thing and is left alone. That one is the player's
 * choice about somebody's chat, held on this client by {@code PlayerSocialManager}; this is the
 * server's choice about a name, and it should not also silence anybody.
 */
@Mixin(SocialInteractionsScreen.class)
public abstract class SocialInteractionsScreenMixin {

    @Inject(method = "onAddPlayer", at = @At("HEAD"), cancellable = true)
    private void backutils$withoutHiddenNames(PlayerInfo player, CallbackInfo info) {
        if (HiddenPlayers.hidden(player.getProfile().getId())) info.cancel();
    }
}
