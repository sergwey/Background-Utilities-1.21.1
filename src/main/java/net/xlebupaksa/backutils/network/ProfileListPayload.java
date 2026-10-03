package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

import java.util.List;

/**
 * The player's chat profiles, and the sounds they may choose from.
 *
 * <p>Sent on request, and again after every change, so the list on screen is the list in the
 * database. The palette travels with it because the editor's sound dropdown is the server's.
 */
public record ProfileListPayload(List<Row> rows, List<String> sounds) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProfileListPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "profile_list"));

    /**
     * One profile, as the editor shows it.
     *
     * @param name   the player's name for the profile
     * @param format the markup, containing {@code {m}}
     */
    public record Row(String name, String format, String sound, boolean active) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, Row::name,
                        ByteBufCodecs.STRING_UTF8, Row::format,
                        ByteBufCodecs.STRING_UTF8, Row::sound,
                        ByteBufCodecs.BOOL, Row::active,
                        Row::new
                );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()), ProfileListPayload::rows,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ProfileListPayload::sounds,
                    ProfileListPayload::new
            );

    public ProfileListPayload {
        rows = List.copyOf(rows);
        sounds = List.copyOf(sounds);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
