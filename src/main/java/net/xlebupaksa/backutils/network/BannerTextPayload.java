package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * Carries the banner's text from the server to a client.
 *
 * <p>The whole text travels rather than a change to it: the client holds no copy to patch, and a
 * player who has just joined has nothing to patch anyway. It is small enough that sending it whole
 * is cheaper than deciding what part of it moved.
 *
 * <p>Empty is a real payload rather than a stop: it is what takes a banner off a client that is
 * showing one, so an operator who clears the text does not have to wait for a relog.
 *
 * @param text the banner's text, in the markup the client draws it in
 */
public record BannerTextPayload(String text) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BannerTextPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "banner_text"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BannerTextPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, BannerTextPayload::text,
                    BannerTextPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
