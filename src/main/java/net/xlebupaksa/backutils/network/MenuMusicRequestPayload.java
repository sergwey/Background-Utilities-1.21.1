package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * Asks the server for the music the menus play, sent when a player opens the corner menu.
 *
 * <p>Carries nothing: the track is the server's setting, and a client that could name its own would
 * be able to play anything at any volume. The answer is a {@link ZoneMusicPayload} under the menu's
 * handle, which is the same loop the zones use.
 */
public record MenuMusicRequestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MenuMusicRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "menu_music_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuMusicRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new MenuMusicRequestPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
