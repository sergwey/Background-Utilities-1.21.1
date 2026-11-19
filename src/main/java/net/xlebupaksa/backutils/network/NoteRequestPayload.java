package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/** Ask for one player's notes; this is a read, not a lock. */
public record NoteRequestPayload(String player) implements CustomPacketPayload {

    public NoteRequestPayload {
        player = player == null ? "" : player;
    }

    public static final CustomPacketPayload.Type<NoteRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_note_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoteRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(128), NoteRequestPayload::player,
                    NoteRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
