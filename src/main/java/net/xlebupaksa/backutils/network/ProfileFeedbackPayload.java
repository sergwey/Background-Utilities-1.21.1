package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * The server's answer to a profile change: what happened, and to which profile.
 *
 * <p>Sent for refusals as well as successes, because the checks live on the server. The name
 * travels with it so the window that asked is the one that reacts.
 */
public record ProfileFeedbackPayload(boolean ok, String name, String message)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfileFeedbackPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "profile_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileFeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ProfileFeedbackPayload::ok,
                    ByteBufCodecs.STRING_UTF8, ProfileFeedbackPayload::name,
                    ByteBufCodecs.STRING_UTF8, ProfileFeedbackPayload::message,
                    ProfileFeedbackPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
