package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.network.AdminNetwork;

/**
 * Whether this client has been told it may act as an operator.
 *
 * <p>One place, rather than a copy per caller: two copies of this question are exactly how the
 * operator alert's settings came to be offered to players who cannot be sent an alert at all — the
 * corner asked it and kept its icon to itself, and the menu's configuration tab never asked.
 *
 * <p>The answer is the server's, not a guess: a client is told its own permission level when it
 * joins and again whenever that changes, and this is the same level the server checks before it
 * sends an alert. Hiding what a player cannot use is not what protects an action — the server
 * protects that, and would refuse anyway — but a screen that offers what the server will not do is a
 * lie about what the player can.
 */
@OnlyIn(Dist.CLIENT)
public final class Operators {

    private Operators() {}

    /** {@return true when this client holds the operator level}, false before it has been told */
    public static boolean isOperator() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && minecraft.player.hasPermissions(AdminNetwork.REQUIRED_LEVEL);
    }
}
