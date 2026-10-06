package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.sound.ModSounds;

/**
 * The shot a tool makes when it is used.
 *
 * <p>Heard by the player who used it and by nobody else: it is played on this client's own sound
 * manager, so nothing about it goes to the server and no other client can be sent it. That is the
 * whole of what the tools' sound is for — an operator's own feedback that the click was taken — and
 * it is also why the server cannot be the one to play it, since it would have to play it for
 * everyone in range.
 *
 * <p>On the Players channel, which is the one the noises players make belong to: a player who wants
 * the tools quieter turns that slider down rather than the whole game, and a player who has already
 * turned the sounds of other players down has said something about this one too.
 *
 * <p>Relative, and with no attenuation. The shot is the player's own, so it is placed with them
 * rather than at the spot they fired from: a sound left behind would soften as they walked away from
 * it, and a shot fired while running would come out quieter than one fired standing still.
 *
 * <p>Which of the two recordings is heard is the game's choice, made per play, from the list on
 * {@link ModSounds#TOOL_SHOT}: equal weights, so each comes up half the time.
 */
@OnlyIn(Dist.CLIENT)
public final class ToolShot {

    /** The fraction of the Players slider both tools fire at. */
    static final float VOLUME = 0.3F;

    /** The effect tool's shot. */
    static final float EFFECT_TOOL_PITCH = 1.0F;

    /** The delete tool's, the deeper of the two. */
    static final float DELETE_TOOL_PITCH = 0.8F;

    private ToolShot() {}

    /** Fires the shot the effect tool makes when it places what it is aimed at. */
    public static void effectTool() {
        play(effectToolShot());
    }

    /** Fires the shot the delete tool makes when it takes a click. */
    public static void deleteTool() {
        play(deleteToolShot());
    }

    /** {@return the shot the effect tool fires} */
    static SimpleSoundInstance effectToolShot() {
        return shot(EFFECT_TOOL_PITCH);
    }

    /** {@return the shot the delete tool fires} */
    static SimpleSoundInstance deleteToolShot() {
        return shot(DELETE_TOOL_PITCH);
    }

    /**
     * {@return one shot at the given pitch, as the sound engine is handed it}
     *
     * <p>Built away from playing it so what each tool fires can be read without a sound engine to
     * fire it on — the channel, the volume and the pitch are then checked rather than listened to.
     */
    static SimpleSoundInstance shot(float pitch) {
        return new SimpleSoundInstance(ModSounds.TOOL_SHOT.getLocation(), SoundSource.PLAYERS, VOLUME, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE,
                0.0, 0.0, 0.0, true);
    }

    private static void play(SimpleSoundInstance shot) {
        Minecraft minecraft = Minecraft.getInstance();
        // A shot needs a player to have fired it, and a level for the sound engine to be running in.
        if (minecraft.player == null || minecraft.level == null) return;

        minecraft.getSoundManager().play(shot);
    }
}
