package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * Asks a client to open the administrator menu. Sent in answer to a command, so the permission check
 * happens where permissions live — on the server.
 */
public record AdminMenuOpenPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminMenuOpenPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_menu_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminMenuOpenPayload> STREAM_CODEC =
            StreamCodec.unit(new AdminMenuOpenPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
