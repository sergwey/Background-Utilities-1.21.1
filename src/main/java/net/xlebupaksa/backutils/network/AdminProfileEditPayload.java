package net.xlebupaksa.backutils.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtils;

/**
 * An administrator changing a profile that belongs to somebody else.
 *
 * <p>{@link ProfileEditPayload} carries no target and reads the sender from the connection, which makes
 * it safe for every player; here the target is the request, so the handler refuses it for anyone below
 * permission level 2 before it reads anything.
 *
 * @param name    the profile's current name, which identifies it within that player's profiles
 * @param newName the name it should have afterwards, or {@code name}
 * @param flags   {@code ProfileEditPayload.FLAG_*} bits
 * @param inner   the text inside the formatting, or the raw format when the advanced flag is set
 * @param sound   the chosen sound, or "" for none; chat profiles only
 */
public record AdminProfileEditPayload(int operation, boolean names, String player, String name,
                                      String newName, String colour, int flags, String inner,
                                      String sound) implements CustomPacketPayload {

    /** Stores a new profile for this player. */
    public static final int CREATE = 0;
    /** Changes an existing profile's format, and its name if it is being renamed. */
    public static final int UPDATE = 1;
    /** Removes one, and stops that player using it if it was the active one. */
    public static final int DELETE = 2;
    /** Makes it the player's active profile, or "default" to go back to the plain one. */
    public static final int USE = 3;

    public static final CustomPacketPayload.Type<AdminProfileEditPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "admin_profile_edit"));

    /** Written out by hand: nine fields, and {@code StreamCodec.composite} takes six. */
    public static final StreamCodec<RegistryFriendlyByteBuf, AdminProfileEditPayload> STREAM_CODEC =
            StreamCodec.of(AdminProfileEditPayload::write, AdminProfileEditPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, AdminProfileEditPayload value) {
        buf.writeVarInt(value.operation);
        buf.writeBoolean(value.names);
        buf.writeUtf(value.player);
        buf.writeUtf(value.name);
        buf.writeUtf(value.newName);
        buf.writeUtf(value.colour);
        buf.writeVarInt(value.flags);
        buf.writeUtf(value.inner);
        buf.writeUtf(value.sound);
    }

    private static AdminProfileEditPayload read(RegistryFriendlyByteBuf buf) {
        return new AdminProfileEditPayload(
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf());
    }

    public static AdminProfileEditPayload create(boolean names, String player, String name,
                                                 String colour, int flags, String inner,
                                                 String sound) {
        return new AdminProfileEditPayload(CREATE, names, player, name, name, colour, flags,
                inner, sound);
    }

    public static AdminProfileEditPayload update(boolean names, String player, String name,
                                                 String newName, String colour, int flags,
                                                 String inner, String sound) {
        return new AdminProfileEditPayload(UPDATE, names, player, name, newName, colour, flags,
                inner, sound);
    }

    public static AdminProfileEditPayload delete(boolean names, String player, String name) {
        return new AdminProfileEditPayload(DELETE, names, player, name, name, "", 0, "", "");
    }

    public static AdminProfileEditPayload use(boolean names, String player, String name) {
        return new AdminProfileEditPayload(USE, names, player, name, name, "", 0, "", "");
    }

    public boolean advanced() {
        return (flags & ProfileEditPayload.FLAG_ADVANCED) != 0;
    }

    public boolean bold() {
        return (flags & ProfileEditPayload.FLAG_BOLD) != 0;
    }

    public boolean italic() {
        return (flags & ProfileEditPayload.FLAG_ITALIC) != 0;
    }

    public boolean underline() {
        return (flags & ProfileEditPayload.FLAG_UNDERLINE) != 0;
    }

    public boolean strikethrough() {
        return (flags & ProfileEditPayload.FLAG_STRIKE) != 0;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
