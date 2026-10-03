package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * An empty request for this player's chat profiles and the sound palette, answered with
 * {@link ProfileListPayload}.
 */
public record ProfileListRequestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfileListRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "profile_list_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileListRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new ProfileListRequestPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
