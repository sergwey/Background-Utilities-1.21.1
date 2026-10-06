package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * Tells a client to stop drawing one effect.
 *
 * <p>Sent when an effect's time is up, and when a delete tool removes one. Both are the same thing
 * to a client: the effect is over, and the client that is drawing it is the only side that can stop
 * it. The row is already gone on the server by the time this is sent, so nothing needs an answer.
 *
 * <p>An id this client does not know is not an error. A client that never started the effect has
 * nothing to stop, and a client that stopped it already has stopped it.
 */
public record EffectRemovedPayload(long effectId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EffectRemovedPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "effect_removed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EffectRemovedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, EffectRemovedPayload::effectId,
                    EffectRemovedPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
