package net.xlebupaksa.backutils.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Whether a client negotiated one of this mod's optional channels.
 *
 * <p>The payloads are registered with {@code registrar.optional()}, so a player without the
 * channel can still join, but sending on a channel that was never negotiated is an error rather
 * than a no-op. {@link NetworkRegistry#hasChannel} is the only way to ask, and NeoForge marks it
 * {@code @ApiStatus.Internal}: the call and its suppression live here, once.
 */
public final class OptionalChannels {

    private OptionalChannels() {}

    @SuppressWarnings("UnstableApiUsage")
    public static boolean negotiated(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && NetworkRegistry.hasChannel(player.connection, type.id());
    }
}
