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
 * Tab-completion for {@code /backutils typing_sounds}. Removing offers the names already in the palette, which
 * are what a profile stores; adding offers every registered sound, the same browse-everything behaviour
 * {@code /playsound} has.
 *
 * <p><b>Only ids are offered for the id an added name should play</b>, and no bare word. Ember's presets are
 * still <em>accepted</em> there — a bare entry's name is its own id, and {@code click} parses as one — but
 * offering them put two names that are not ids into a list of ids, which reads as sound names among the sound
 * ids and is what the owner asked not to see. An operator who knows a preset's name can still type it: the
 * argument's rule is untouched, and only the list changed.
 *
 * <p>The registry is a convenience here and never a check: an id from a resourcepack is typed, and accepted,
 * exactly the same. The mod's own {@code backutils:tool_shot} is in the list because it is registered like any
 * other sound.
 *
 * <p>Nothing a palette holds can reach this list, because the palette is not read here at all: what is listed
 * is the registry's own keys. A name the palette command accepts is a bare word and can never be one of them —
 * see {@link ProfileSound#checkName} — and a preset's name is a bare word too, which is why dropping the
 * presets from the list costs the palette nothing.
 */
public final class ProfileSoundSuggestions implements SuggestionProvider<CommandSourceStack> {

    /** Every registered sound, for the id an added name should play. */
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

        for (ResourceLocation id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            String text = id.toString();
            if (remaining.isEmpty() || text.toLowerCase(Locale.ROOT).contains(remaining)) {
                builder.suggest(text);
            }
        }
        return builder.buildFuture();
    }
}
