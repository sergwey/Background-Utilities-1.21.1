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
 * Tells a client to put an effect into the world.
 *
 * <p>This is the message Photon does not send on its own behalf. Photon relays a command to whoever
 * is watching at the time, which is why an effect is invisible to a player who arrives afterwards;
 * the server keeps the list, and this is how one entry of it reaches a client — both when it is
 * first placed and again when a player arrives somewhere the effect already exists.
 *
 * <p>It carries a position and a configuration rather than an id into a table, because the client
 * has no such table and must not need one. Nothing about it names Photon: a client without the mod
 * receives it, ignores it, and is no worse off.
 *
 * @param targetEntityId the entity to attach to, or {@link #NO_ENTITY} for a placement in the world
 * @param face           the block face the effect hangs off, and up for every other mode
 * @param effectId       the row this effect came from, so a client can tell two of them apart
 */
public record EffectPlacedPayload(
        long effectId,
        double x, double y, double z,
        float yaw, float pitch,
        int targetEntityId,
        Face face,
        EffectToolConfig config
) implements CustomPacketPayload {

    /** Written in place of an entity id when the effect attaches to nothing. */
    public static final int NO_ENTITY = -1;

    /** The longest face name this message may carry, matching the claim a client may send. */
    private static final int MAX_FACE_NAME = 16;

    public static final CustomPacketPayload.Type<EffectPlacedPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "effect_placed"));

    /**
     * Written out rather than composed: {@code StreamCodec.composite} takes at most six pairs, and
     * this record has eight values.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, EffectPlacedPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public EffectPlacedPayload decode(RegistryFriendlyByteBuf buf) {
                    return new EffectPlacedPayload(
                            buf.readVarLong(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readFloat(), buf.readFloat(),
                            buf.readVarInt(),
                            Face.byName(buf.readUtf(MAX_FACE_NAME)),
                            EffectToolConfig.STREAM_CODEC.decode(buf));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, EffectPlacedPayload payload) {
                    buf.writeVarLong(payload.effectId());
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

    /** A payload built by hand is corrected here rather than at every use. */
    public EffectPlacedPayload {
        config = config == null ? EffectToolConfig.BLANK : config.sanitised();
        face = face == null ? Face.UP : face;
    }

    /** {@return the mode the effect this describes should be placed with} */
    public Mode mode() {
        return config.mode();
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
