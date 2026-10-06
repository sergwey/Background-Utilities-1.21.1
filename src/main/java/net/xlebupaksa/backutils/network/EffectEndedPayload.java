package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * One client saying that an effect is over.
 *
 * <p>An effect may carry a lifetime of its own, and when that runs out it ends without anybody
 * having asked: the timeline is finished and nothing of the effect is still alive. That is a fact
 * only a client can see, and it is the client's to report — the row that described the effect then
 * describes nothing, and the display the effect hung off is the server's to take away.
 *
 * <p>Several clients will report the same ending at about the same time, because they are all
 * drawing the same effect. Nothing here is a claim about the world that could be wrong: the worst a
 * mistaken report can do is remove an effect the server was asked to remove anyway, and an effect
 * that is already gone answers the request the same way an effect that was never there does.
 */
public record EffectEndedPayload(long effectId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EffectEndedPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "effect_ended"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EffectEndedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, EffectEndedPayload::effectId,
                    EffectEndedPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
