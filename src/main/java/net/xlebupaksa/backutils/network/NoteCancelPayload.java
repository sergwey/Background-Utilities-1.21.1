package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/** Release the edit lease on one player's notes, discarding any unsaved draft. */
public record NoteCancelPayload(String player) implements CustomPacketPayload {

    public NoteCancelPayload {
        player = player == null ? "" : player;
    }

    public static final CustomPacketPayload.Type<NoteCancelPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_note_cancel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoteCancelPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(128), NoteCancelPayload::player,
                    NoteCancelPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
