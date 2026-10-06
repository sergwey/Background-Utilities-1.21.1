package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * The server's answer to an effect tool edit: stored, or refused and why.
 *
 * <p>Sent for a refusal as well as for a success, because every check lives on the server. A screen
 * that hears nothing would look exactly like one whose edit was stored, which is the one thing it
 * must not do.
 *
 * <p>The answer is one of a fixed set of reasons rather than a sentence, so the words are chosen by
 * the client that draws them and a server cannot put text of its own into a player's screen.
 */
public record EffectToolFeedbackPayload(Reason reason) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EffectToolFeedbackPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID,
                            "effect_tool_config_feedback"));

    /** What became of an edit, named by the translation key the screen shows for it. */
    public enum Reason {

        STORED("backutils.effect_tool.status.applied", true),
        NOT_PERMITTED("backutils.effect_tool.feedback.not_permitted", false),
        SLOT_CHANGED("backutils.effect_tool.feedback.slot_changed", false);

        private final String key;
        private final boolean stored;

        Reason(String key, boolean stored) {
            this.key = key;
            this.stored = stored;
        }

        /** {@return the translation key this answer is shown by} */
        public String key() {
            return key;
        }

        /** {@return true when the edit was written}, rather than dropped with a reason */
        public boolean stored() {
            return stored;
        }

        /**
         * {@return the answer a number stands for}, the refusal for one that means nothing
         *
         * <p>An unknown number is answered as a refusal: a screen told nothing was stored says
         * something true either way, where a screen told the edit was stored would not.
         */
        public static Reason byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : SLOT_CHANGED;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, EffectToolFeedbackPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(Reason::byId, Reason::ordinal),
                    EffectToolFeedbackPayload::reason,
                    EffectToolFeedbackPayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
