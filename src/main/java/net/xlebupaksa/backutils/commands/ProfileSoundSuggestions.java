package net.xlebupaksa.backutils.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.profile.ProfileSound;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Tab-completion for {@code /backutils profiles sound}. Adding offers Ember's presets followed by
 * every registered sound, the same browse-everything behaviour {@code /playsound} has; removing
 * offers only the palette the player is choosing from. A preset is suggested bare ({@code click})
 * because Ember resolves it against its own table, and {@link BackUtilsCommands} maps it back from
 * {@code minecraft:click}.
 */
public final class ProfileSoundSuggestions implements SuggestionProvider<CommandSourceStack> {

    /** Ember's presets, then every registered sound. */
    public static final ProfileSoundSuggestions AVAILABLE = new ProfileSoundSuggestions(false);

    /** Only what is already in the palette. */
    public static final ProfileSoundSuggestions PALETTE = new ProfileSoundSuggestions(true);

    private final boolean paletteOnly;

    private ProfileSoundSuggestions(boolean paletteOnly) {
        this.paletteOnly = paletteOnly;
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context,
                                                         SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();

        if (paletteOnly) {
            for (String sound : BackUtilsConfig.getProfileSounds()) {
                if (remaining.isEmpty() || sound.toLowerCase(Locale.ROOT).contains(remaining)) {
                    builder.suggest(sound);
                }
            }
            return builder.buildFuture();
        }

        for (String preset : ProfileSound.PRESETS) {
            if (remaining.isEmpty() || preset.contains(remaining)) builder.suggest(preset);
        }
        for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            String text = id.toString();
            if (remaining.isEmpty() || text.toLowerCase(Locale.ROOT).contains(remaining)) {
                builder.suggest(text);
            }
        }
        return builder.buildFuture();
    }
}
