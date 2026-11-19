package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/** Keep the edit lease alive while the editor is open. */
public record NoteHeartbeatPayload(String player) implements CustomPacketPayload {

    public NoteHeartbeatPayload {
        player = player == null ? "" : player;
    }

    public static final CustomPacketPayload.Type<NoteHeartbeatPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_note_heartbeat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoteHeartbeatPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(128), NoteHeartbeatPayload::player,
                    NoteHeartbeatPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
