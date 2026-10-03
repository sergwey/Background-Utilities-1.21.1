package net.xlebupaksa.backutils.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * What one player's profiles currently are, synced to their own client.
 *
 * <p>The stream codec is written out by hand rather than composed: {@code StreamCodec.composite}
 * takes at most six arguments and this record has seven.
 *
 * <p>{@code chat_sound} is an optional field in the saved-data codec, so an attachment written by
 * an earlier build still loads, without a sound.
 */
public record ProfileSnapshot(
        long chatProfileId,
        String chatProfileName,
        String chatFormat,
        String chatSound,
        long nameProfileId,
        String nameProfileName,
        String displayedName
) {
    public static final ProfileSnapshot DEFAULT = new ProfileSnapshot(
            0L,
            "default",
            "{m}",
            "",
            0L,
            "default",
            "{player}"
    );

    public static final Codec<ProfileSnapshot> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.LONG.fieldOf("chat_id").forGetter(ProfileSnapshot::chatProfileId),
            Codec.STRING.fieldOf("chat_name").forGetter(ProfileSnapshot::chatProfileName),
            Codec.STRING.fieldOf("chat_format").forGetter(ProfileSnapshot::chatFormat),
            Codec.STRING.optionalFieldOf("chat_sound", "").forGetter(ProfileSnapshot::chatSound),
            Codec.LONG.fieldOf("name_id").forGetter(ProfileSnapshot::nameProfileId),
            Codec.STRING.fieldOf("name_name").forGetter(ProfileSnapshot::nameProfileName),
            Codec.STRING.fieldOf("displayed").forGetter(ProfileSnapshot::displayedName)
    ).apply(inst, ProfileSnapshot::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileSnapshot> STREAM_CODEC =
            StreamCodec.of(ProfileSnapshot::write, ProfileSnapshot::read);

    private static void write(RegistryFriendlyByteBuf buf, ProfileSnapshot value) {
        buf.writeVarLong(value.chatProfileId);
        buf.writeUtf(value.chatProfileName);
        buf.writeUtf(value.chatFormat);
        buf.writeUtf(value.chatSound);
        buf.writeVarLong(value.nameProfileId);
        buf.writeUtf(value.nameProfileName);
        buf.writeUtf(value.displayedName);
    }

    private static ProfileSnapshot read(RegistryFriendlyByteBuf buf) {
        return new ProfileSnapshot(
                buf.readVarLong(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarLong(),
                buf.readUtf(),
                buf.readUtf());
    }

    public boolean matches(ProfileSnapshot other) {
        return chatProfileId == other.chatProfileId
                && chatProfileName.equals(other.chatProfileName)
                && chatFormat.equals(other.chatFormat)
                && chatSound.equals(other.chatSound)
                && nameProfileId == other.nameProfileId
                && nameProfileName.equals(other.nameProfileName)
                && displayedName.equals(other.displayedName);
    }
}
