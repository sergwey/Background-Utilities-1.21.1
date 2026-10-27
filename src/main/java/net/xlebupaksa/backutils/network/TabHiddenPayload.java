package net.xlebupaksa.backutils.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

import java.util.List;
import java.util.UUID;

/**
 * The names a client is to keep out of its player lists.
 *
 * <p>The whole set rather than a change to it. It is small, it travels when an operator changes it
 * and once when a player joins, and a client that missed one message is put right by the next instead
 * of being left half told — which, for a set that decides who a player can see, is worth the bytes.
 */
public record TabHiddenPayload(List<UUID> hidden) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TabHiddenPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "tab_hidden"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TabHiddenPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), TabHiddenPayload::hidden,
                    TabHiddenPayload::new
            );

    public TabHiddenPayload {
        hidden = List.copyOf(hidden);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
