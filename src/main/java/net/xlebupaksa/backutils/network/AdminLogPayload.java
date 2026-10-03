package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole action log, for an administrator, newest first. It ignores {@code access_list}, so it
 * is only sent to somebody whose permission has been checked on the server; reusing the player
 * payload would put the visibility rule in two places.
 */
public record AdminLogPayload(List<Row> rows) implements CustomPacketPayload {

    /**
     * One row.
     *
     * @param id         the {@code log_id} that admin actions address
     * @param text       the stored text, markup intact and the actor's name filled in
     * @param dimension  where it happened, or empty when the row predates positions
     * @param visible    whether this viewer would normally be allowed to see it, which the marker
     *                   in front of hidden entries reports
     * @param hiddenFrom witness names this entry is currently withheld from
     */
    public record Row(long id, String createdAt, String text, String actor,
                      String dimension, double x, double y, double z, boolean visible,
                      boolean hiddenAll, List<String> witnesses, List<String> hiddenFrom) {

        /**
         * Written by hand rather than with {@code StreamCodec.composite}, which stops at six fields
         * and this row has nine.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public Row decode(RegistryFriendlyByteBuf buffer) {
                        long id = buffer.readVarLong();
                        String createdAt = buffer.readUtf();
                        String text = buffer.readUtf();
                        String actor = buffer.readUtf();
                        String dimension = buffer.readUtf();
                        double x = buffer.readDouble();
                        double y = buffer.readDouble();
                        double z = buffer.readDouble();
                        boolean visible = buffer.readBoolean();
                        boolean hiddenAll = buffer.readBoolean();
                        List<String> witnesses = readStrings(buffer);
                        List<String> hiddenFrom = readStrings(buffer);
                        return new Row(id, createdAt, text, actor, dimension, x, y, z,
                                visible, hiddenAll, witnesses, hiddenFrom);
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, Row row) {
                        buffer.writeVarLong(row.id());
                        buffer.writeUtf(row.createdAt());
                        buffer.writeUtf(row.text());
                        buffer.writeUtf(row.actor());
                        buffer.writeUtf(row.dimension());
                        buffer.writeDouble(row.x());
                        buffer.writeDouble(row.y());
                        buffer.writeDouble(row.z());
                        buffer.writeBoolean(row.visible());
                        buffer.writeBoolean(row.hiddenAll());
                        writeStrings(buffer, row.witnesses());
                        writeStrings(buffer, row.hiddenFrom());
                    }
                };

        /** Count first, then the entries, so the two sides cannot disagree. */
        private static void writeStrings(RegistryFriendlyByteBuf buffer, List<String> values) {
            buffer.writeVarInt(values.size());
            for (String value : values) buffer.writeUtf(value);
        }

        private static List<String> readStrings(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            List<String> values = new ArrayList<>(Math.max(0, count));
            for (int i = 0; i < count; i++) values.add(buffer.readUtf());
            return values;
        }
    }

    public static final CustomPacketPayload.Type<AdminLogPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_log"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminLogPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()), AdminLogPayload::rows,
                    AdminLogPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
