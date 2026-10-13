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
 * Tab-completion for {@code /backutils sounds}. Removing offers the names already in the palette, which
 * are what a profile stores; adding offers Ember's presets followed by every registered sound, the same
 * browse-everything behaviour {@code /playsound} has. A preset is suggested bare ({@code click}) because
 * Ember resolves it against its own table. The registry is a convenience here and never a check: an id
 * from a resourcepack is typed, and accepted, exactly the same.
 */
public final class ProfileSoundSuggestions implements SuggestionProvider<CommandSourceStack> {

    /** Ember's presets, then every registered sound, for the id an added name should play. */
    public static final ProfileSoundSuggestions AVAILABLE = new ProfileSoundSuggestions(false);

    /** Only the names already in the palette. */
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
            for (String name : ProfileSound.names(BackUtilsConfig.getProfileSounds())) {
                if (remaining.isEmpty() || name.toLowerCase(Locale.ROOT).contains(remaining)) {
                    builder.suggest(name);
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
