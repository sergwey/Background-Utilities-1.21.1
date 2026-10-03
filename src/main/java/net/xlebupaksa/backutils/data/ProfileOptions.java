package net.xlebupaksa.backutils.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * The profile choices a player may pick from, plus what they may do with them.
 *
 * <p>It exists so the chat overlay can list profiles without the client touching
 * {@link BackUtilsData}: those databases only exist on the server, and reading them from the
 * render thread was both wrong and slow.
 *
 * <p>The two permissions are separate because they answer different questions: a name profile is
 * what everyone else reads the player as, so choosing between them stays with staff, while a chat
 * profile is the player's own formatting.
 *
 * <p>Synced but not serialized, because it is re-derived from the databases on every login and
 * after every profile change.
 */
public record ProfileOptions(List<String> nameProfiles, List<String> chatProfiles,
                             boolean canManageNames, boolean canManageChat) {

    /** The reserved profile name that always means "no profile". */
    public static final String DEFAULT_PROFILE = "default";

    public static final ProfileOptions EMPTY = new ProfileOptions(
            List.of(DEFAULT_PROFILE),
            List.of(DEFAULT_PROFILE),
            false,
            false
    );

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileOptions> STREAM_CODEC =
            StreamCodec.composite(
                    STRING_LIST, ProfileOptions::nameProfiles,
                    STRING_LIST, ProfileOptions::chatProfiles,
                    ByteBufCodecs.BOOL, ProfileOptions::canManageNames,
                    ByteBufCodecs.BOOL, ProfileOptions::canManageChat,
                    ProfileOptions::new
            );

    public ProfileOptions {
        nameProfiles = List.copyOf(nameProfiles);
        chatProfiles = List.copyOf(chatProfiles);
    }
}
