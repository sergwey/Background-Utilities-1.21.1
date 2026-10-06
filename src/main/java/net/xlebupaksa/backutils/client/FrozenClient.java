package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * What this client does about its own player being held.
 *
 * <p>Nothing about it is what holds them: the server has their position and refuses what they do, and
 * a client that ignores all of this is still held. What it is for is the client's own prediction —
 * the half of a player's movement that happens at home, before the server is asked. Two of the three
 * parts of that are already answered for: walking and jumping come from attributes, and the attributes
 * this client holds are the ones the server sent it. <b>Falling does not.</b> Whether an entity is
 * subject to gravity is not part of an entity's synced data, so a player held in the air would fall
 * on this side, be put back by the server, and fall again — the visible shake that says a plugin is
 * holding you.
 *
 * <p>Applied after the player's tick rather than before: the tick that has just run was computed from
 * the state from before the message, and there is nothing to salvage there. From the next tick on,
 * gravity is off and the velocity is zero, which is a player who does not move.
 */
public final class FrozenClient {

    private static boolean held;

    private FrozenClient() {}

    /** Takes the server's word for it, and lets the player fall again the moment it is withdrawn. */
    public static void held(boolean frozen) {
        held = frozen;
        Player player = Minecraft.getInstance().player;
        if (player != null && !frozen) player.setNoGravity(false);
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!held) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    /**
     * Forgets the hold when the client leaves the server that ordered it.
     *
     * <p>A client that kept this would walk into the next server — or into a single-player world —
     * unable to fall, with nobody there who ever held it and no command that could let it go.
     */
    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        held = false;
    }
}
