package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.NoteData;

import java.util.ArrayList;
import java.util.List;

/** The full readable state of one player's notes, including who currently holds the edit lease. */
public record NoteStatePayload(String player, String playerName, int version, List<String> pages,
                               String updatedAt, String updatedBy,
                               String holderUuid, String holderName, long leaseUntilMillis)
        implements CustomPacketPayload {

    public NoteStatePayload {
        player = player == null ? "" : player;
        playerName = playerName == null ? "" : playerName;
        pages = List.copyOf(pages);
        updatedAt = updatedAt == null ? "" : updatedAt;
        updatedBy = updatedBy == null ? "" : updatedBy;
        holderUuid = holderUuid == null ? "" : holderUuid;
        holderName = holderName == null ? "" : holderName;
    }

    public boolean lockedByOther(String selfUuid) {
        return !holderUuid.isBlank() && !holderUuid.equals(selfUuid) && leaseUntilMillis > 0L;
    }

    public static final CustomPacketPayload.Type<NoteStatePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_note_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoteStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public NoteStatePayload decode(RegistryFriendlyByteBuf buffer) {
                    String player = buffer.readUtf(128);
                    String playerName = buffer.readUtf(128);
                    int version = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > NoteData.MAX_PAGES) {
                        throw new IllegalArgumentException("A note state has " + count + " pages");
                    }
                    List<String> pages = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        pages.add(buffer.readUtf(NoteData.MAX_PAGE_BYTES));
                    }
                    return new NoteStatePayload(player, playerName, version, pages,
                            buffer.readUtf(32), buffer.readUtf(128),
                            buffer.readUtf(128), buffer.readUtf(128), buffer.readVarLong());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, NoteStatePayload payload) {
                    buffer.writeUtf(payload.player(), 128);
                    buffer.writeUtf(payload.playerName(), 128);
                    buffer.writeVarInt(payload.version());
                    int count = Math.min(payload.pages().size(), NoteData.MAX_PAGES);
                    buffer.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        buffer.writeUtf(payload.pages().get(i), NoteData.MAX_PAGE_BYTES);
                    }
                    buffer.writeUtf(payload.updatedAt(), 32);
                    buffer.writeUtf(payload.updatedBy(), 128);
                    buffer.writeUtf(payload.holderUuid(), 128);
                    buffer.writeUtf(payload.holderName(), 128);
                    buffer.writeVarLong(payload.leaseUntilMillis());
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
