package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/** Ask for the edit lease on one player's notes. */
public record NoteEditRequestPayload(String player) implements CustomPacketPayload {

    public NoteEditRequestPayload {
        player = player == null ? "" : player;
    }

    public static final CustomPacketPayload.Type<NoteEditRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_note_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoteEditRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(128), NoteEditRequestPayload::player,
                    NoteEditRequestPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
