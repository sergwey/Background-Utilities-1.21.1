package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * An administrator asking to see one player's memory.
 *
 * <p>The player is named rather than identified because that is what the administrator picked from
 * the list; the server resolves the name to an identity before it reads anything. Answered only for a
 * permission level of 2 or above, checked on the connection.
 *
 * @param player whose memory is wanted, or "" for nothing
 */
public record AdminMemoryRequestPayload(String player) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminMemoryRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_memory_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminMemoryRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AdminMemoryRequestPayload::player,
                    AdminMemoryRequestPayload::new
            );

    public AdminMemoryRequestPayload {
        player = player == null ? "" : player;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
