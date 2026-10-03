package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * Tells an administrator that an action just happened. Carries the actor and nothing else, so
 * pushing the text does not type every action across an operator's screen.
 */
public record AdminAlertPayload(long id, String actor) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminAlertPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_alert"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminAlertPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, AdminAlertPayload::id,
                    ByteBufCodecs.STRING_UTF8, AdminAlertPayload::actor,
                    AdminAlertPayload::new);
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
