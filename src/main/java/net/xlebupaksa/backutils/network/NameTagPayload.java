package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * Whether this client is to draw the tags above players' heads.
 *
 * <p>One bit, because the setting is the server's and the drawing is the client's: there is nothing
 * else for the two of them to agree on, and the client cannot work it out for itself — a server that
 * wants tags hidden and one that has never heard of the setting look identical from inside a client.
 *
 * @param hidden true when the tag above a player's head is not to be drawn
 */
public record NameTagPayload(boolean hidden) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NameTagPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "name_tags"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NameTagPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, NameTagPayload::hidden,
                    NameTagPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
