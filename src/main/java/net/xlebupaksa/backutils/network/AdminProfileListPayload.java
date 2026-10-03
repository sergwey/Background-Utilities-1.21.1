package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

import java.util.List;

/**
 * One player's profiles, as the background menu shows them.
 *
 * <p>The rows are the ones the player's own Profiles tab uses, field for field, because the editor is
 * the same window, and the sound palette rides along because the editor's dropdown is the server's
 * palette. A name profile has no sound, so its menu control is hidden rather than left empty.
 *
 * @param player the player these belong to, resolved to the name the database uses
 * @param rows   the profiles, in the order the database lists them
 */
public record AdminProfileListPayload(boolean names, String player,
                                      List<ProfileListPayload.Row> rows, List<String> sounds)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminProfileListPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_profile_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminProfileListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, AdminProfileListPayload::names,
                    ByteBufCodecs.STRING_UTF8, AdminProfileListPayload::player,
                    ProfileListPayload.Row.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    AdminProfileListPayload::rows,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    AdminProfileListPayload::sounds,
                    AdminProfileListPayload::new
            );

    public AdminProfileListPayload {
        rows = List.copyOf(rows);
        sounds = List.copyOf(sounds);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
