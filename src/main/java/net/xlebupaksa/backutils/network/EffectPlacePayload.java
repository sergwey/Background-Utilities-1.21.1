package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.item.EffectToolConfig;
import net.xlebupaksa.backutils.item.EffectToolConfig.Mode;
import net.xlebupaksa.backutils.item.EffectToolPlacement.Face;

/**
 * One placement: where an operator pointed the tool, and what they pointed it with.
 *
 * <p>Sent to the server rather than acted on where it was made, because the server is the side that
 * owns the world. A client that placed its own effects would place them for itself alone, would
 * survive nothing, and could not be stopped from placing them anywhere at all.
 *
 * <p>Every field is a claim, checked on the way in. The position, the mode and the target are what
 * the client says it aimed at; the server re-derives what it can, refuses what it cannot, and is the
 * only side that writes a row.
 *
 * <p>The configuration is carried rather than read back off the tool, so the effect placed is the
 * one the operator was looking at when they placed it, even if they edited the tool in the same
 * tick. The mode is read from that configuration rather than sent beside it: the two could disagree,
 * and the one the effect would actually be placed with is the configuration's.
 *
 * @param targetEntityId the entity aimed at, or {@link #NO_ENTITY} when the mode names no entity
 * @param face           the block face a block-side placement hangs off, and up for every other
 *                       mode; the surface the configured rotation is read against
 */
public record EffectPlacePayload(
        double x, double y, double z,
        float yaw, float pitch,
        int targetEntityId,
        Face face,
        EffectToolConfig config
) implements CustomPacketPayload {

    /** Written in place of an entity id when the placement names no entity. */
    public static final int NO_ENTITY = -1;

    /**
     * The longest face name a client may send. A face name is at most five characters, so anything
     * longer is not one, and the cap is what keeps a claim from being a document.
     */
    private static final int MAX_FACE_NAME = 16;

    public static final CustomPacketPayload.Type<EffectPlacePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "effect_place"));

    /**
     * Written out rather than composed: {@code StreamCodec.composite} takes at most six pairs, and
     * nesting two of them to reach eight would need a holder type that exists only to be taken apart
     * again.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, EffectPlacePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public EffectPlacePayload decode(RegistryFriendlyByteBuf buf) {
                    return new EffectPlacePayload(
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readFloat(), buf.readFloat(),
                            buf.readVarInt(),
                            Face.byName(buf.readUtf(MAX_FACE_NAME)),
                            EffectToolConfig.STREAM_CODEC.decode(buf));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, EffectPlacePayload payload) {
                    buf.writeDouble(payload.x());
                    buf.writeDouble(payload.y());
                    buf.writeDouble(payload.z());
                    buf.writeFloat(payload.yaw());
                    buf.writeFloat(payload.pitch());
                    buf.writeVarInt(payload.targetEntityId());
                    buf.writeUtf(payload.face().name(), MAX_FACE_NAME);
                    EffectToolConfig.STREAM_CODEC.encode(buf, payload.config());
                }
            };

    /** A placement built by hand is corrected here rather than at every use of the payload. */
    public EffectPlacePayload {
        config = config == null ? EffectToolConfig.BLANK : config.sanitised();
        face = face == null ? Face.UP : face;
    }

    /** {@return the mode this placement is for}, which is the configuration's own */
    public Mode mode() {
        return config.mode();
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
