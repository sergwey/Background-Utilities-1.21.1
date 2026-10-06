package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * Asks the server for the unfiltered action log. Carries nothing: the server decides whether the
 * sender may have it and what it contains, so there is nothing here worth forging.
 */
public record AdminLogRequestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminLogRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_log_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminLogRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new AdminLogRequestPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
