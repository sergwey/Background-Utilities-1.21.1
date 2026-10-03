package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * An administrator asking to see the server's settings. Nothing is carried: the answer is the whole
 * table, since a menu that asked setting by setting would show half a window while it did.
 */
public record AdminConfigRequestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminConfigRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_config_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminConfigRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new AdminConfigRequestPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
