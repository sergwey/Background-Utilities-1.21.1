package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

import java.util.List;

/**
 * Every player an administrator can open in the profile tab, with what they have: everyone who
 * already has a profile of the kind being looked at, plus everyone online, since a first name
 * profile cannot be given from anywhere else. Both kinds and the counts travel with every row.
 *
 * @param names   true for name profiles, false for chat profiles
 * @param players the names, online first, then alphabetically
 */
public record AdminPlayerListPayload(boolean names, List<Player> players)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminPlayerListPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_player_list"));

    /**
     * One player in the list.
     *
     * @param name       the account name, spelled the way the database or the server spells it
     * @param online     whether they are connected right now
     * @param chatCount  how many chat profiles they have
     * @param nameCount  how many name profiles they have
     */
    public record Player(String name, boolean online, int chatCount, int nameCount) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Player> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, Player::name,
                        ByteBufCodecs.BOOL, Player::online,
                        ByteBufCodecs.VAR_INT, Player::chatCount,
                        ByteBufCodecs.VAR_INT, Player::nameCount,
                        Player::new
                );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminPlayerListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AdminPlayerListPayload::names,
                    Player.STREAM_CODEC.apply(ByteBufCodecs.list()), AdminPlayerListPayload::players,
                    AdminPlayerListPayload::new
            );

    public AdminPlayerListPayload {
        players = List.copyOf(players);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
