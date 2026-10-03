package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * An administrator asking to see somebody else's profiles.
 *
 * <p>Two questions in one payload: no player named asks who has profiles, one named asks for that
 * player's profiles. Unlike {@link ProfileEditPayload} it names the player it is about, so it is
 * answered only for a permission level of 2 or above, checked on the connection.
 *
 * @param player whose profiles are wanted, or "" for the list of players
 */
public record AdminProfileRequestPayload(boolean names, String player)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminProfileRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_profile_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminProfileRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AdminProfileRequestPayload::names,
                    ByteBufCodecs.STRING_UTF8, AdminProfileRequestPayload::player,
                    AdminProfileRequestPayload::new
            );

    public AdminProfileRequestPayload {
        player = player == null ? "" : player;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
