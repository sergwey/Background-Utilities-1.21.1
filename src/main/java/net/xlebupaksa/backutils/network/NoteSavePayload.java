package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.NoteData;

import java.util.ArrayList;
import java.util.List;

/** A whole-document save; the server checks the named version and the lease before writing it. */
public record NoteSavePayload(String player, int baseVersion, List<String> pages)
        implements CustomPacketPayload {

    public NoteSavePayload {
        player = player == null ? "" : player;
        pages = List.copyOf(pages);
    }

    public static final CustomPacketPayload.Type<NoteSavePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_note_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NoteSavePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public NoteSavePayload decode(RegistryFriendlyByteBuf buffer) {
                    String player = buffer.readUtf(128);
                    int version = buffer.readVarInt();
                    int count = buffer.readVarInt();
                    if (count < 1 || count > NoteData.MAX_PAGES) {
                        throw new IllegalArgumentException("A note save has " + count + " pages");
                    }
                    List<String> pages = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        pages.add(buffer.readUtf(NoteData.MAX_PAGE_BYTES));
                    }
                    return new NoteSavePayload(player, version, pages);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, NoteSavePayload payload) {
                    buffer.writeUtf(payload.player(), 128);
                    buffer.writeVarInt(payload.baseVersion());
                    int count = Math.min(payload.pages().size(), NoteData.MAX_PAGES);
                    buffer.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        buffer.writeUtf(payload.pages().get(i), NoteData.MAX_PAGE_BYTES);
                    }
                }
            };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
