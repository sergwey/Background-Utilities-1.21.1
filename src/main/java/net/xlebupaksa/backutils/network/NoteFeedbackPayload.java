package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/** One localised result for the note screen. */
public record NoteFeedbackPayload(String player, Reason reason, String holderName, int seconds)
        implements CustomPacketPayload {

    public enum Reason {
        SAVED,
        LOCKED,
        LOCK_LOST,
        VERSION_CONFLICT,
        INVALID,
        UNKNOWN_PLAYER,
        NOT_ALLOWED,
        ERROR;

        public static Reason byId(int id) {
            Reason[] values = values();
            return id < 0 || id >= values.length ? ERROR : values[id];
        }
    }

    public NoteFeedbackPayload {
        player = player == null ? "" : player;
        reason = reason == null ? Reason.ERROR : reason;
        holderName = holderName == null ? "" : holderName;
        seconds = Math.max(0, seconds);
    }

    public static final CustomPacketPayload.Type<NoteFeedbackPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_note_feedback"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoteFeedbackPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public NoteFeedbackPayload decode(RegistryFriendlyByteBuf buffer) {
                    return new NoteFeedbackPayload(buffer.readUtf(128),
                            Reason.byId(buffer.readVarInt()), buffer.readUtf(128),
                            buffer.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, NoteFeedbackPayload payload) {
                    buffer.writeUtf(payload.player(), 128);
                    buffer.writeVarInt(payload.reason().ordinal());
                    buffer.writeUtf(payload.holderName(), 128);
                    buffer.writeVarInt(payload.seconds());
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
