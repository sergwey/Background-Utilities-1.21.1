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
 */
public record RoleplayLogPayload(long id, String text) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RoleplayLogPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "roleplay_log"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoleplayLogPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, RoleplayLogPayload::id,
                    ByteBufCodecs.STRING_UTF8, RoleplayLogPayload::text,
                    RoleplayLogPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
