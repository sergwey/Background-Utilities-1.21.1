package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

import java.util.List;

/**
 * An administrator changing the server's settings. A batch, so one press of Save is one round trip
 * and only what changed is sent; the server answers with the whole table, accepted or not.
 */
public record AdminConfigEditPayload(List<Change> changes) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminConfigEditPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_config_edit"));

    /**
     * One setting's new value. The value is text; the server checks it before writing anything.
     */
    public record Change(String key, String value) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Change> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, Change::key,
                        ByteBufCodecs.STRING_UTF8, Change::value,
                        Change::new
                );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminConfigEditPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Change.STREAM_CODEC.apply(ByteBufCodecs.list()), AdminConfigEditPayload::changes,
                    AdminConfigEditPayload::new
            );

    public AdminConfigEditPayload {
        changes = List.copyOf(changes);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
