package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * Asks the server for the whole log this player is allowed to see, which the live overlay cannot
 * supply: it only ever receives entries as they happen.
 */
public record LogHistoryRequestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LogHistoryRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "log_history_request"));

    /** Carries no data at all; the player it belongs to is implied by the connection. */
    public static final StreamCodec<RegistryFriendlyByteBuf, LogHistoryRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new LogHistoryRequestPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
