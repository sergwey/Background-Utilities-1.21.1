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
 *       has nothing to stop. This is also what a sneak-click sends, for the effects attached to the
 *       clicking player: the same question about themselves.
 *   <li><b>{@link Kind#MINE}</b> — retired, and deliberately still here: it emptied every effect the
 *       clicking player had ever placed, which one stray sneak-click could do to a whole build, so
 *       nothing sends it and the server acts on nothing for it. It is kept as a value because the
 *       kinds are written by ordinal, and removing it would renumber the one after it.
 * </ul>
 *
 * @param x the block's low corner, for {@link Kind#BLOCK} and meaningless otherwise
 * @param subLevel the moving structure the block is on, or empty for the world; the coordinates above
 *                 are then the structure's own, and a block of the same numbers on another structure
 *                 is another place
 * @param face the surface a block-side effect hangs off, or empty when the whole block is meant; a
 *             block-side effect's place is that surface and not the block, so two of them on two
 *             faces are two places
 * @param effectIds the rows to remove, for {@link Kind#EFFECTS} and empty otherwise
 */
public record EffectDeletePayload(Kind kind, double x, double y, double z, String subLevel,
                                  String face, List<Long> effectIds)
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

    /** The longest sub-level identity a claim may carry, which is the width of one with room to spare. */
    private static final int MAX_SUB_LEVEL = 64;

    /** The longest face name a claim may carry, which is the width of the name of a face. */
    private static final int MAX_FACE_NAME = 16;

    public static final CustomPacketPayload.Type<EffectDeletePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "effect_delete"));

    /**
     * Written out rather than composed: {@code StreamCodec.composite} takes at most six pairs and this
     * record has seven values, and nesting two of them to reach it would need a holder type that exists
     * only to be taken apart again.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, EffectDeletePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public EffectDeletePayload decode(RegistryFriendlyByteBuf buf) {
                    return new EffectDeletePayload(
                            Kind.byId(buf.readVarInt()),
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readUtf(MAX_SUB_LEVEL),
                            buf.readUtf(MAX_FACE_NAME),
                            // Read through the capped list codec rather than straight off the buffer,
                            // so a claim cannot carry more rows than one click can name.
                            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(MAX_EFFECTS)).decode(buf));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, EffectDeletePayload payload) {
                    buf.writeVarInt(payload.kind().ordinal());
                    buf.writeDouble(payload.x());
                    buf.writeDouble(payload.y());
                    buf.writeDouble(payload.z());
                    buf.writeUtf(payload.subLevel(), MAX_SUB_LEVEL);
                    buf.writeUtf(payload.face(), MAX_FACE_NAME);
                    ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(MAX_EFFECTS))
                            .encode(buf, payload.effectIds());
                }
            };

    /** A payload built by hand is corrected here rather than at every use of it. */
    public EffectDeletePayload {
        kind = kind == null ? Kind.NONE : kind;
        subLevel = subLevel == null ? "" : subLevel.trim();
        face = face == null ? "" : face.trim();
        effectIds = effectIds == null ? List.of() : List.copyOf(effectIds);
    }

    /** {@return a claim that one block be emptied} */
    public static EffectDeletePayload atBlock(double x, double y, double z) {
        return new EffectDeletePayload(Kind.BLOCK, x, y, z, "", "", List.of());
    }

    /**
     * {@return a claim that one surface of a block be emptied}
     *
     * <p>A block-side effect's place is the face it hangs off, so a click on its cube names that face:
     * three effects on three faces of one block are three places, and each is taken away on its own.
     * The face is left empty for a whole block, which is what a block placement's cube means — the
     * coordinates and the structure are the ones every block claim carries.
     */
    public static EffectDeletePayload atBlock(double x, double y, double z, String subLevel,
                                              String face) {
        return new EffectDeletePayload(Kind.BLOCK, x, y, z, subLevel, face, List.of());
    }

    /** {@return a claim that the given rows be removed} */
    public static EffectDeletePayload effects(List<Long> effectIds) {
        return new EffectDeletePayload(Kind.EFFECTS, 0.0D, 0.0D, 0.0D, "", "", effectIds);
    }

    /** {@return true when this claim names a place on a moving structure rather than in the world} */
    public boolean onSubLevel() {
        return !subLevel.isEmpty();
    }

    /** {@return true when this claim names one surface rather than a whole block} */
    public boolean onSurface() {
        return !face.isEmpty();
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
