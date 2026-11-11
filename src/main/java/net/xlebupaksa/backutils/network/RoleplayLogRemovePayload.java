package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * Takes the roller's private early copy back off their log when the remembered line arrives.
 *
 * <p>The private copy has no stored row of its own, and leaving it beside the remembered line would
 * show the same roll twice in the same corner. The id is the negative one that line arrived with;
 * removing it is all the client has to do, and a client that no longer holds it simply does nothing.
 */
public record RoleplayLogRemovePayload(long id) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RoleplayLogRemovePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "roleplay_log_remove"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoleplayLogRemovePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, RoleplayLogRemovePayload::id,
                    RoleplayLogRemovePayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends RoleplayLogRemovePayload> type() {
        return TYPE;
    }
}
