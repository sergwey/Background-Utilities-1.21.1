package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * One roleplay log line, on its way to a single player: the server renders the line for that viewer,
 * underlining the actor's name only on the actor's own screen, which a single broadcast payload could
 * not do. Optional, so a client without it is not refused entry; vanilla ignores custom payloads it has
 * no handler for and simply sees no roleplay log.
 *
 * @param sound  a sound to play as the line arrives, or empty. It travels with the line rather than
 *               living in the stored text, because the players who hear a roll are exactly the
 *               players the line is sent to, and a stored tag would be played again every time the
 *               menu drew the row
 * @param volume played at this volume, and
 * @param pitch  at this pitch
 * @param roll   whether this line is a roll of the dice rather than an action, which the corner
 *               button draws a different animation for while the line types itself out
 */
public record RoleplayLogPayload(long id, String text, String sound, float volume, float pitch,
                                 boolean roll) implements CustomPacketPayload {

    /** Keeps the codec from ever being handed a null, which it would write as a crash. */
    public RoleplayLogPayload {
        text = text == null ? "" : text;
        sound = sound == null ? "" : sound;
    }

    public static final CustomPacketPayload.Type<RoleplayLogPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "roleplay_log"));

    /**
     * Six fields, which is as many as {@code StreamCodec.composite} takes: a seventh would mean
     * writing this one by hand, as the administrator's log row already is.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, RoleplayLogPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, RoleplayLogPayload::id,
                    ByteBufCodecs.STRING_UTF8, RoleplayLogPayload::text,
                    ByteBufCodecs.STRING_UTF8, RoleplayLogPayload::sound,
                    ByteBufCodecs.FLOAT, RoleplayLogPayload::volume,
                    ByteBufCodecs.FLOAT, RoleplayLogPayload::pitch,
                    ByteBufCodecs.BOOL, RoleplayLogPayload::roll,
                    RoleplayLogPayload::new
            );

    /** {@return true when this line brings a sound with it} */
    public boolean hasSound() {
        return !sound.isBlank();
    }

    @Override
    public CustomPacketPayload.Type<? extends RoleplayLogPayload> type() {
        return TYPE;
    }
}
