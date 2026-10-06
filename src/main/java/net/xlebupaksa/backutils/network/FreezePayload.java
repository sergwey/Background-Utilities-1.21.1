package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * One client being told that its player is being held, or that they have been let go.
 *
 * <p>The server already holds the player and already refuses everything they try to do, so nothing
 * here is what makes a suspension work — a client that never receives this is still held. What it
 * fixes is the two ends disagreeing about a player who is in the air: the server stops the fall and
 * the client does not, because whether an entity falls is not something the client is told, so it
 * would predict a fall of its own and be put back, every tick, until the operator let go. The message
 * is what lets the client agree.
 */
public record FreezePayload(boolean held) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FreezePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "freeze"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreezePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, FreezePayload::held,
                    FreezePayload::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
