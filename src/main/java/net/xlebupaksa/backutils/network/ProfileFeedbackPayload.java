package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * The server's answer to a profile change: what happened.
 *
 * <p>Sent for refusals as well as successes, because the checks live on the server. Nothing names the
 * profile it was about: it goes to the player who asked, and that player's editor is one window, so
 * the connection is what says which window reacts.
 */
public record ProfileFeedbackPayload(boolean ok, String message)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfileFeedbackPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "profile_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileFeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ProfileFeedbackPayload::ok,
                    ByteBufCodecs.STRING_UTF8, ProfileFeedbackPayload::message,
                    ProfileFeedbackPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
