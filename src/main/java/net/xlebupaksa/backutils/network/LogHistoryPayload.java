package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

import java.util.List;

/**
 * The log entries a player may see, sent in answer to {@link LogHistoryRequestPayload}.
 *
 * <p>Each row is already rendered for its recipient, so the actor's own name is underlined on their
 * screen and plain on everyone else's. Oldest first, so the menu reads top to bottom.
 *
 * <p>The newest {@link net.xlebupaksa.backutils.data.LogData#SCREEN_ROWS} rows and no more, so
 * <b>older entries are no longer shown in the menu</b>; nothing is deleted, and the live log still
 * arrives as it happens. Only the order the rows are read in is newest-first, which is what lets the
 * database stop at the limit: what is sent here is oldest-first, as the menu expects.
 */
public record LogHistoryPayload(List<Row> rows) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LogHistoryPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "log_history"));

    /**
     * One displayable line: {@code id} de-duplicates, {@code createdAt} is shown when the row is hovered.
     */
    public record Row(long id, String createdAt, String text) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, Row::id,
                        ByteBufCodecs.STRING_UTF8, Row::createdAt,
                        ByteBufCodecs.STRING_UTF8, Row::text,
                        Row::new
                );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, LogHistoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()), LogHistoryPayload::rows,
                    LogHistoryPayload::new
            );

    public LogHistoryPayload {
        rows = List.copyOf(rows);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
