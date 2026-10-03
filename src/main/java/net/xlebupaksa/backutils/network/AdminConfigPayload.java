package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.BackUtilsSettings;

import java.util.List;

/**
 * The server's settings, as the menu needs to draw them: labels, tabs, control kinds, bounds and
 * comments all come from here, and the client keeps no list of its own ({@link BackUtilsSettings}).
 */
public record AdminConfigPayload(List<Row> settings) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_config"));

    /** One setting. */
    public record Row(String key, String label, BackUtilsSettings.Group group,
                      BackUtilsSettings.Kind kind, String value, double min, double max,
                      List<String> comment) {

        /**
         * Written out rather than composed: eight fields, and {@code StreamCodec.composite} takes
         * six. The enums go as enums, since both ends are this same jar.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                StreamCodec.of(Row::write, Row::read);

        private static void write(RegistryFriendlyByteBuf buf, Row value) {
            buf.writeUtf(value.key);
            buf.writeUtf(value.label);
            buf.writeEnum(value.group);
            buf.writeEnum(value.kind);
            buf.writeUtf(value.value);
            buf.writeDouble(value.min);
            buf.writeDouble(value.max);
            buf.writeCollection(value.comment, (out, line) -> out.writeUtf(line));
        }

        private static Row read(RegistryFriendlyByteBuf buf) {
            return new Row(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readEnum(BackUtilsSettings.Group.class),
                    buf.readEnum(BackUtilsSettings.Kind.class),
                    buf.readUtf(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readList(in -> in.readUtf()));
        }

        public Row {
            comment = List.copyOf(comment);
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()), AdminConfigPayload::settings,
                    AdminConfigPayload::new
            );

    public AdminConfigPayload {
        settings = List.copyOf(settings);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
