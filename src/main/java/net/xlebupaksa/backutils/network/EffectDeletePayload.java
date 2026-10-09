package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

import java.util.List;

/**
 * One deletion the delete tool asks for: a place to empty or a set of effects the client is drawing.
 *
 * <p>Sent to the server rather than acted on where the click was made, for the reason a placement is:
 * the server is the side that owns the table, and the display an effect hung off is an entity in the
 * world that only the server may take away. A client that deleted its own copy would stop drawing one
 * effect, would leave the row and the display behind, and would have every other client still
 * drawing it.
 *
 * <p>The kinds are the things a click can name, and each is a claim the server checks rather than a
 * command it obeys:
 *
 * <ul>
 *   <li><b>{@link Kind#BLOCK}</b> — the place under the crosshair, as the block's low corner. The
 *       spec calls it "all the placed effects in this place": a block is what the pink cube is
 *       drawn around, so a block is what a click on one asks to empty.
 *   <li><b>{@link Kind#EFFECTS}</b> — the rows of effects attached to an entity, which the client is
 *       the only side that can name. A row records where an entity stood and not which entity it was,
 *       so "the effects on this entity" is not a question the server can be asked; the client's own
 *       registry is what knows an effect hangs off one, and it sends the rows it holds. An id the
 *       server does not know is not an error — a client that has stopped drawing an effect already
 *       has nothing to stop. This is also what alt-click sends, for the effects attached to the
 *       clicking player: the same question about themselves.
 *   <li><b>{@link Kind#MINE}</b> — retired, and deliberately still here: it emptied every effect the
 *       clicking player had ever placed, which one stray alt-click could do to a whole build, so
 *       nothing sends it and the server acts on nothing for it. It is kept as a value because the
 *       kinds are written by ordinal, and removing it would renumber the one after it.
 * </ul>
 *
 * @param x the block's low corner, for {@link Kind#BLOCK} and meaningless otherwise
 * @param effectIds the rows to remove, for {@link Kind#EFFECTS} and empty otherwise
 */
public record EffectDeletePayload(Kind kind, double x, double y, double z, List<Long> effectIds)
        implements CustomPacketPayload {

    /**
     * What a delete click names.
     *
     * <p>Written to the wire by ordinal rather than by name, which is the opposite of what the
     * effect tool's stored settings do: nothing here is written down, both ends of one connection
     * are one build of the mod, and a kind is meaningless outside the click that made it.
     */
    public enum Kind {

        /**
         * No kind at all, which nothing sends.
         *
         * <p>An id outside this list is read as this rather than refused, so a payload from a build
         * that had a kind this one does not is ignored instead of disconnecting its sender. It is the
         * one value that cannot be mistaken for an instruction: every other kind deletes something.
         */
        NONE,

        /** Everything in one block. */
        BLOCK,

        /**
         * Retired: everything the clicking player had placed.
         *
         * <p>Nothing sends it and the server acts on nothing for it — see the class comment for why it
         * was retired and why the value itself is still here. It is the one kind that names no target at
         * all, so a build that did send it would empty a whole footprint with one click.
         */
        MINE,

        /** The given rows, which are the effects a client is drawing on one entity. */
        EFFECTS;

        /** {@return the kind an id stands for}, answering {@link #NONE} for one it does not know */
        public static Kind byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : NONE;
        }
    }

    /**
     * How many rows one claim may name.
     *
     * <p>Two hundred and fifty-six: an entity with that many effects attached to it is already past
     * anything the tool can produce, and the cap is what keeps a claim from being a document. It is
     * enforced on the way in, so a longer list is refused rather than read.
     */
    private static final int MAX_EFFECTS = 256;

    public static final CustomPacketPayload.Type<EffectDeletePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "effect_delete"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EffectDeletePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(Kind::byId, Kind::ordinal), EffectDeletePayload::kind,
                    ByteBufCodecs.DOUBLE, EffectDeletePayload::x,
                    ByteBufCodecs.DOUBLE, EffectDeletePayload::y,
                    ByteBufCodecs.DOUBLE, EffectDeletePayload::z,
                    ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(MAX_EFFECTS)),
                    EffectDeletePayload::effectIds,
                    EffectDeletePayload::new);

    /** A payload built by hand is corrected here rather than at every use of it. */
    public EffectDeletePayload {
        kind = kind == null ? Kind.NONE : kind;
        effectIds = effectIds == null ? List.of() : List.copyOf(effectIds);
    }

    /** {@return a claim that one block be emptied} */
    public static EffectDeletePayload atBlock(double x, double y, double z) {
        return new EffectDeletePayload(Kind.BLOCK, x, y, z, List.of());
    }

    /** {@return a claim that the given rows be removed} */
    public static EffectDeletePayload effects(List<Long> effectIds) {
        return new EffectDeletePayload(Kind.EFFECTS, 0.0D, 0.0D, 0.0D, effectIds);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
