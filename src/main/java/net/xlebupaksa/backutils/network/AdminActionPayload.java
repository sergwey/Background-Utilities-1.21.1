package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * One thing an administrator asked to do to one entry. Every action runs on the server, including
 * the teleports: the client says which entry it means, never where to go.
 */
public record AdminActionPayload(Kind action, long id, String target) implements CustomPacketPayload {

    /** The things the options panel can ask for. */
    public enum Kind {
        HIDE_FOR_EVERYONE,
        /** Give it back to everyone who witnessed it. */
        UNHIDE_FOR_EVERYONE,
        HIDE_FROM_PLAYER,
        UNHIDE_FROM_PLAYER,
        /**
         * Withholds it from a set of players. The target is a Minecraft selector such as
         * {@code @a[distance=..10]} or a {@code ;}-separated list of names.
         */
        HIDE_FROM_MANY,
        /** Give it back to a set of players, in the same two forms. */
        UNHIDE_FROM_MANY,
        /** Remove the row entirely. Irreversible. */
        DELETE_RECORD,
        TELEPORT_TO_ACTION,
        /** Falls back to the action's location when the actor is offline. */
        TELEPORT_TO_ACTOR,
        /**
         * Nothing at all, and never sent by this build: what a number this one has not got is read as,
         * so that a message from another build cannot throw inside the decoder.
         */
        NONE
    }

    public static final CustomPacketPayload.Type<AdminActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(AdminActionPayload::kindOf, Kind::ordinal),
                    AdminActionPayload::action,
                    ByteBufCodecs.VAR_LONG, AdminActionPayload::id,
                    ByteBufCodecs.STRING_UTF8, AdminActionPayload::target,
                    AdminActionPayload::new);

    /**
     * {@return the kind a number on the wire means}, or {@link Kind#NONE} for one this build has not got
     *
     * <p>A number rather than the name, because every action is sent by this mod and to this mod. An
     * unknown one is read as doing nothing instead of being indexed straight into the enum: the number
     * is a claim from a client, and a message that threw where it was decoded would be a disconnect
     * rather than a refusal. Reading it this way also means a constant inserted above an existing one
     * cannot silently turn one action into another.
     */
    private static Kind kindOf(int id) {
        Kind[] kinds = Kind.values();
        return id >= 0 && id < kinds.length ? kinds[id] : Kind.NONE;
    }

    public static AdminActionPayload of(Kind action, long id) {
        return new AdminActionPayload(action, id, "");
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
