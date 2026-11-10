package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

import java.util.List;

/**
 * One thing an administrator asked to do to several entries at once.
 *
 * <p>A separate payload from {@link AdminActionPayload} rather than a list on it: the single-entry
 * payload is what every existing control sends and its shape is worth leaving alone, while a bulk
 * action is a different act — one permission check, one target resolved, one report for the set.
 *
 * <p>{@link Kind#HIDE_FROM} and {@link Kind#UNHIDE_FROM} name one player, resolved on the server from
 * the same cache the single-entry action uses. Hiding skips entries the player never witnessed:
 * withholding a line from somebody who never had it is not a change, and counting it as one would
 * report work that did not happen.
 *
 * <p>Ids are read as claims and every one is looked up on the server; an id that no longer exists is
 * skipped rather than treated as a failure, because a list on a client is always a moment older than
 * the table.
 */
public record AdminLogBatchPayload(Kind action, List<Long> ids, String target)
        implements CustomPacketPayload {

    /** The things a list of entries can be asked to do. */
    public enum Kind {
        DELETE_RECORD,
        HIDE_FROM_PLAYER,
        UNHIDE_FROM_PLAYER,
        HIDE_FOR_EVERYONE,
        UNHIDE_FOR_EVERYONE,
        /** Nothing at all, which is how a number from another build is read. */
        NONE
    }

    public static final CustomPacketPayload.Type<AdminLogBatchPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_log_batch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminLogBatchPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(AdminLogBatchPayload::kindOf, Kind::ordinal),
                    AdminLogBatchPayload::action,
                    ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), AdminLogBatchPayload::ids,
                    ByteBufCodecs.STRING_UTF8, AdminLogBatchPayload::target,
                    AdminLogBatchPayload::new);

    public AdminLogBatchPayload {
        ids = ids == null ? List.of() : List.copyOf(ids);
        target = target == null ? "" : target;
    }

    /** {@return the kind a number on the wire means}, or {@link Kind#NONE} for one this build lacks */
    private static Kind kindOf(int id) {
        Kind[] kinds = Kind.values();
        return id >= 0 && id < kinds.length ? kinds[id] : Kind.NONE;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
