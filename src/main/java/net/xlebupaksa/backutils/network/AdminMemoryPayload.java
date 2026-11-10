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
 * The lines one player has in memory, for an administrator: newest first, hidden ones included.
 *
 * <p>{@link net.xlebupaksa.backutils.data.LogData#rememberedBy} is the read: the access list, not the
 * visibility rule, decides what is here, because a line withheld from a player is still a line in
 * their memory. Each row says whether that player can see it now, so the same list can both show what
 * was withheld and offer it back.
 *
 * @param player the player whose memory this is, as the request named them
 */
public record AdminMemoryPayload(String player, List<Row> rows) implements CustomPacketPayload {

    /**
     * One line of a player's memory.
     *
     * @param text the stored line rendered as <em>that player</em> sees it, markup intact
     * @param hiddenAll whether the line is withheld from everyone
     * @param hiddenFromPlayer whether it is withheld from this player in particular
     * @param note what the line keeps from players, such as the number behind a hidden roll
     */
    public record Row(long id, String createdAt, String text, String actor, String dimension,
                      double x, double y, double z, boolean hiddenAll,
                      boolean hiddenFromPlayer, String note) {

        /** Written by hand: {@code StreamCodec.composite} stops at six fields. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public Row decode(RegistryFriendlyByteBuf buffer) {
                        return new Row(
                                buffer.readVarLong(),
                                buffer.readUtf(),
                                buffer.readUtf(),
                                buffer.readUtf(),
                                buffer.readUtf(),
                                buffer.readDouble(),
                                buffer.readDouble(),
                                buffer.readDouble(),
                                buffer.readBoolean(),
                                buffer.readBoolean(),
                                buffer.readUtf());
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
                        buffer.writeBoolean(row.hiddenAll());
                        buffer.writeBoolean(row.hiddenFromPlayer());
                        buffer.writeUtf(row.note());
                    }
                };
    }

    public static final CustomPacketPayload.Type<AdminMemoryPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_memory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminMemoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AdminMemoryPayload::player,
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()), AdminMemoryPayload::rows,
                    AdminMemoryPayload::new
            );

    public AdminMemoryPayload {
        player = player == null ? "" : player;
        rows = List.copyOf(rows);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
