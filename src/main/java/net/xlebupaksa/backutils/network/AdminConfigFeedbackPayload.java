package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * The server's answer to a settings change: whether it happened and, when it did not, why. The menu
 * shows it under the button that sent it, and the values themselves come back in
 * {@link AdminConfigPayload}.
 */
public record AdminConfigFeedbackPayload(boolean ok, String message)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminConfigFeedbackPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_config_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminConfigFeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AdminConfigFeedbackPayload::ok,
                    ByteBufCodecs.STRING_UTF8, AdminConfigFeedbackPayload::message,
                    AdminConfigFeedbackPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
