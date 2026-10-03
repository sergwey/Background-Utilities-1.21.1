package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * The server's answer to an administrator's profile change, kept apart from
 * {@link ProfileFeedbackPayload} so a refusal meant for one window is not shown, or acted on, by
 * another.
 */
public record AdminProfileFeedbackPayload(boolean ok, boolean names, String player, String message)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminProfileFeedbackPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_profile_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminProfileFeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AdminProfileFeedbackPayload::ok,
                    ByteBufCodecs.BOOL, AdminProfileFeedbackPayload::names,
                    ByteBufCodecs.STRING_UTF8, AdminProfileFeedbackPayload::player,
                    ByteBufCodecs.STRING_UTF8, AdminProfileFeedbackPayload::message,
                    AdminProfileFeedbackPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
