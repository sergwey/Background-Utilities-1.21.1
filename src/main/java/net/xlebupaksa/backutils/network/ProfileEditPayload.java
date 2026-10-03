package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * A player asking to change one of their own chat profiles.
 *
 * <p>The sender is read from the connection, so a client cannot name a victim. The advanced
 * field is raw markup, which is why this is a payload rather than a command.
 *
 * @param name    the profile's current name, which identifies it within one player's profiles
 * @param newName the name it should have afterwards — {@link #CREATE} and {@link #UPDATE} only,
 *                and the same as {@code name} when it is not being renamed
 * @param inner   the text inside the formatting ({@code {m}} belongs there), or the raw format
 *                when {@link #FLAG_ADVANCED} is set
 */
public record ProfileEditPayload(int kind, String name, String newName, String colour, int flags,
                                 String inner, String sound) implements CustomPacketPayload {

    /** Store a new profile. */
    public static final int CREATE = 0;
    /** Change the format and sound of one that exists. */
    public static final int UPDATE = 1;
    /** Remove one, and stop using it if it was the active one. */
    public static final int DELETE = 2;
    /** Start using one, or "default" to go back to the plain format. */
    public static final int USE = 3;

    public static final int FLAG_BOLD = 1;
    public static final int FLAG_ITALIC = 2;
    public static final int FLAG_UNDERLINE = 4;
    public static final int FLAG_STRIKE = 8;

    /** Set when {@link #inner} holds the raw format instead of the text inside the formatting. */
    public static final int FLAG_ADVANCED = 16;

    public static final CustomPacketPayload.Type<ProfileEditPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "profile_edit"));

    /** Written out by hand: seven fields, and {@code StreamCodec.composite} takes six. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileEditPayload> STREAM_CODEC =
            StreamCodec.of(ProfileEditPayload::write, ProfileEditPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ProfileEditPayload value) {
        buf.writeVarInt(value.kind);
        buf.writeUtf(value.name);
        buf.writeUtf(value.newName);
        buf.writeUtf(value.colour);
        buf.writeVarInt(value.flags);
        buf.writeUtf(value.inner);
        buf.writeUtf(value.sound);
    }

    private static ProfileEditPayload read(RegistryFriendlyByteBuf buf) {
        return new ProfileEditPayload(
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf());
    }

    public static ProfileEditPayload create(String name, String colour, int flags, String inner,
                                            String sound) {
        return new ProfileEditPayload(CREATE, name, name, colour, flags, inner, sound);
    }

    public static ProfileEditPayload update(String name, String newName, String colour, int flags,
                                            String inner, String sound) {
        return new ProfileEditPayload(UPDATE, name, newName, colour, flags, inner, sound);
    }

    public static ProfileEditPayload delete(String name) {
        return new ProfileEditPayload(DELETE, name, name, "", 0, "", "");
    }

    public static ProfileEditPayload use(String name) {
        return new ProfileEditPayload(USE, name, name, "", 0, "", "");
    }

    public boolean renames() {
        return (kind == CREATE || kind == UPDATE)
                && newName != null && !newName.equals(name);
    }

    public boolean advanced() {
        return (flags & FLAG_ADVANCED) != 0;
    }

    public boolean bold() {
        return (flags & FLAG_BOLD) != 0;
    }

    public boolean italic() {
        return (flags & FLAG_ITALIC) != 0;
    }

    public boolean underline() {
        return (flags & FLAG_UNDERLINE) != 0;
    }

    public boolean strikethrough() {
        return (flags & FLAG_STRIKE) != 0;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
