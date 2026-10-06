package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.item.EffectToolConfig;

/**
 * One edit from the effect tool's screen, sent to the server to be stored.
 *
 * <p>The screen holds the client's own copy of a stack, so an edit written there is one the next
 * sync of that slot takes away again. The stack belongs to the server, and this is how a change
 * reaches it.
 *
 * <p>Everything in the payload is a claim rather than a value. The server finds the slot in its own
 * copy of the menu and writes through the item's own clamp, so a client cannot put a tool's field
 * out of range, and one that names a slot holding something else has its edit dropped.
 *
 * <p>The menu travels with the slot because an index alone means nothing: it is only meaningful
 * against the container it was read from, and the server refuses when the player has a different
 * one open than the edit was made against.
 *
 * <p>The configuration goes through the component's own stream codec, so a payload that decodes is
 * of the shape the component can hold; a field outside its range still goes through
 * {@link EffectToolConfig#of} on the way in, and is clamped there.
 *
 * @param where       which menu {@code slot} counts in, and which side owns it
 * @param containerId the menu the index was read from, checked against the server's own
 */
public record EffectToolConfigPayload(Where where, int containerId, int slot,
                                      EffectToolConfig config)
        implements CustomPacketPayload {

    /**
     * Which menu a slot index counts in.
     *
     * <p>Needed because the id alone does not say. A player in creative mode opens their own
     * inventory as the creative screen, whose menu is built on the client and never exists on the
     * server — and it takes the same id as the player's own inventory menu, which does. A slot index
     * from one means something else in the other, so the two are told apart by name rather than by
     * a number that happens to be shared.
     */
    public enum Where {

        /**
         * The player's own inventory menu.
         *
         * <p>Both sides always have this one, whatever else is open, so a tool in the player's own
         * inventory can be addressed from any screen — including one the server does not have.
         */
        PLAYER,

        /** The menu the player has open, which a chest or a machine owns. */
        OPEN_MENU;

        /**
         * {@return the menu a number stands for}, the player's own for one that means nothing
         *
         * <p>A claim naming no menu is read as the one every side has and the slot is then checked
         * against: a menu that does not exist is not a menu to act on, and the refusal belongs to the
         * check rather than to the reading.
         */
        public static Where byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : PLAYER;
        }
    }

    public static final CustomPacketPayload.Type<EffectToolConfigPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "effect_tool_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EffectToolConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(Where::byId, Where::ordinal),
                    EffectToolConfigPayload::where,
                    ByteBufCodecs.VAR_INT, EffectToolConfigPayload::containerId,
                    ByteBufCodecs.VAR_INT, EffectToolConfigPayload::slot,
                    EffectToolConfig.STREAM_CODEC, EffectToolConfigPayload::config,
                    EffectToolConfigPayload::new
            );

    /** A configuration built by hand is corrected here rather than at every use of the payload. */
    public EffectToolConfigPayload {
        config = config == null ? EffectToolConfig.BLANK : config.sanitised();
        where = where == null ? Where.PLAYER : where;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
