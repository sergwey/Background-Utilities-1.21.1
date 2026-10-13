package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.BackUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Plays one named sound for this client.
 *
 * <p>Shared by the two places the mod asks for one — the operator alert and a roll line arriving —
 * so an id that names nothing is reported the same way, and once each.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSounds {

    private ClientSounds() {}

    /**
     * The ids already reported as missing, so a typo is said once rather than every time it is played.
     *
     * <p>A sound a client does not have is now an ordinary thing rather than a mistake this end can
     * refuse at save time: the id is resolved here, so a track an operator added in their resourcepack
     * exists for the players who have that pack and names nothing for the players who do not. Those
     * players are told once; a roll that warns on every roll is a log nobody reads.
     */
    private static final Set<ResourceLocation> REPORTED = new HashSet<>();

    /**
     * Plays a sound from the registry, if it is there.
     *
     * @param soundId a full sound id, or empty for silence
     */
    public static void play(String soundId, float volume, float pitch) {
        if (soundId == null || soundId.isBlank()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;

        ResourceLocation location = ResourceLocation.tryParse(soundId);
        if (location == null) return;

        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(location);
        if (event == null) {
            // Warned rather than silent: a sound nobody hears is worse than no sound at all.
            if (REPORTED.add(location)) {
                BackUtils.LOGGER.warn("Sound '{}' does not exist; nothing will be heard.", location);
            }
            return;
        }

        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }
}
