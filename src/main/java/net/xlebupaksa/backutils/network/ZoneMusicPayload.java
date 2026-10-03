package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * Tells one client to start or stop one zone's music.
 *
 * <p>Sent only when a player crosses a zone boundary, never per tick. A stop carries only the
 * id: the client already knows which sound it is holding.
 *
 * <p>The fade lengths are deliberately not on the wire: how a loop ramps is a client
 * presentation detail, and leaving them out keeps the payload inside
 * {@code StreamCodec.composite}'s six-field limit.
 *
 * @param zoneId which zone, used by the client as the handle for its loop
 * @param stop   true to fade the loop out, false to start or retune it
 * @param sound  the sound id, resolved on the client
 * @param source the sound channel, which decides the volume slider it obeys
 */
public record ZoneMusicPayload(
        long zoneId,
        boolean stop,
        String sound,
        String source,
        float volume,
        float pitch
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ZoneMusicPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "zone_music"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ZoneMusicPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, ZoneMusicPayload::zoneId,
                    ByteBufCodecs.BOOL, ZoneMusicPayload::stop,
                    ByteBufCodecs.STRING_UTF8, ZoneMusicPayload::sound,
                    ByteBufCodecs.STRING_UTF8, ZoneMusicPayload::source,
                    ByteBufCodecs.FLOAT, ZoneMusicPayload::volume,
                    ByteBufCodecs.FLOAT, ZoneMusicPayload::pitch,
                    ZoneMusicPayload::new
            );

    public static ZoneMusicPayload play(long zoneId, String sound, String source,
                                        float volume, float pitch) {
        return new ZoneMusicPayload(zoneId, false, sound, source, volume, pitch);
    }

    public static ZoneMusicPayload stop(long zoneId) {
        return new ZoneMusicPayload(zoneId, true, "", "", 0f, 1f);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Fade lengths, in ticks: stopping dead at a zone boundary makes the boundary audible. */
    public static final class Fade {
        public static final int IN_TICKS = 30;
        public static final int OUT_TICKS = 40;

        private Fade() {}
    }
}
