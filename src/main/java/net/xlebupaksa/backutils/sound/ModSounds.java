package net.xlebupaksa.backutils.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.xlebupaksa.backutils.BackUtils;

/**
 * The sounds this mod ships of its own.
 *
 * <p>One event with two recordings behind it, listed with equal weight in {@code sounds.json}: the
 * game chooses between them per play, so a third recording is a line in that file rather than a
 * change here, and nothing in the mod has to pick.
 *
 * <p>Kept as the instance that was registered rather than looked up again when it is played. The one
 * sound here is fired by the client that made it and never travels, so the two can only ever be the
 * same object; the registration is what lets {@code /playsound backutils:tool_shot players} name it,
 * which is how the sound is heard without holding a tool to fire.
 */
public final class ModSounds {

    /** The shot both tools make. See {@link net.xlebupaksa.backutils.client.ToolShot}. */
    public static final SoundEvent TOOL_SHOT = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "tool_shot"));

    private ModSounds() {}
}
