package net.xlebupaksa.backutils.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.xlebupaksa.backutils.commands.arguments.TolerantDoubleArgument;
import net.xlebupaksa.backutils.data.MusicZone;
import net.xlebupaksa.backutils.music.MusicZoneManager;
import net.xlebupaksa.backutils.profile.ProfileLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Music zones: {@code /playradius}, {@code /playbox} and {@code /stopzone}.
 *
 * <pre>
 * /playradius &lt;sound&gt; &lt;radius&gt; &lt;volume&gt; &lt;pitch&gt; [seconds] [channel]
 * /playbox &lt;sound&gt; &lt;pos1&gt; &lt;pos2&gt; &lt;volume&gt; &lt;pitch&gt; [seconds] [channel]
 * /stopzone [current|list]
 * </pre>
 *
 * <p>The sound comes first and is a {@link ResourceLocationArgument} with
 * {@link SuggestionProviders#AVAILABLE_SOUNDS} attached, the same as vanilla's {@code /playsound}.
 *
 * <p>{@code seconds} and {@code channel} are both optional and independent, which needs four
 * branches: Brigadier has only paths, so each combination has to exist. The channel decides which
 * of the player's volume sliders the music obeys.
 *
 * <p>Zones have no names: {@code /playradius} centres on whoever runs it, and
 * {@code /stopzone current} removes whatever they are standing in.
 */
public class MusicCommands {

    private static final double MAX_RADIUS = 512.0D;
    private static final double MAX_SECONDS = 86400.0D;
    private static final String DEFAULT_CHANNEL = "music";

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("playradius")
                .requires(s -> s.hasPermission(ProfileLoader.PROFILE_PERMISSION_LEVEL))
                .then(Commands.argument("sound", ResourceLocationArgument.id())
                        .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
                        .then(Commands.argument("radius", TolerantDoubleArgument.tolerantDouble(0.5D, MAX_RADIUS))
                                .then(Commands.argument("volume", TolerantDoubleArgument.tolerantDouble(0.0D, 2.0D))
                                        .then(Commands.argument("pitch", TolerantDoubleArgument.tolerantDouble(0.5D, 2.0D))
                                                .executes(ctx -> radius(ctx, DEFAULT_CHANNEL, MusicZone.PERMANENT))
                                                .then(secondsTail(ctx -> radius(ctx, DEFAULT_CHANNEL, expiresAt(ctx))))
                                                .then(channelTail(
                                                        ctx -> radius(ctx, channel(ctx), MusicZone.PERMANENT),
                                                        ctx -> radius(ctx, channel(ctx), expiresAt(ctx))))
                                        )
                                )
                        )
                )
        );

        d.register(Commands.literal("playbox")
                .requires(s -> s.hasPermission(ProfileLoader.PROFILE_PERMISSION_LEVEL))
                .then(Commands.argument("sound", ResourceLocationArgument.id())
                        .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                        .then(Commands.argument("volume", TolerantDoubleArgument.tolerantDouble(0.0D, 2.0D))
                                                .then(Commands.argument("pitch", TolerantDoubleArgument.tolerantDouble(0.5D, 2.0D))
                                                        .executes(ctx -> box(ctx, DEFAULT_CHANNEL, MusicZone.PERMANENT))
                                                        .then(secondsTail(ctx -> box(ctx, DEFAULT_CHANNEL, expiresAt(ctx))))
                                                        .then(channelTail(
                                                                ctx -> box(ctx, channel(ctx), MusicZone.PERMANENT),
                                                                ctx -> box(ctx, channel(ctx), expiresAt(ctx))))
                                                )
                                        )
                                )
                        )
                )
        );

        d.register(Commands.literal("stopzone")
                .requires(s -> s.hasPermission(ProfileLoader.PROFILE_PERMISSION_LEVEL))
                .executes(this::list)
                .then(Commands.literal("current").executes(this::stopCurrent))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(this::stopById)));
    }

    // ------------------------------------------------------------------
    // The optional tail: [seconds] [channel]
    // ------------------------------------------------------------------

    /** {@return a builder for the optional {@code seconds} argument, with its own tail} */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Double>
            secondsTail(java.util.function.Function<CommandContext<CommandSourceStack>, Integer> timed) {
        return Commands.argument("seconds", TolerantDoubleArgument.tolerantDouble(0.1D, MAX_SECONDS))
                .executes(ctx -> timed.apply(ctx))
                .then(channelTail(timed, timed));
    }

    /**
     * {@return a builder for the channel argument}; {@code withoutTime} runs when no {@code seconds}
     * was given, {@code withTime} when one was.
     */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
            channelTail(java.util.function.Function<CommandContext<CommandSourceStack>, Integer> withoutTime,
                        java.util.function.Function<CommandContext<CommandSourceStack>, Integer> withTime) {
        return Commands.argument("channel", StringArgumentType.word())
                .suggests(MusicCommands::suggestChannels)
                .executes(ctx -> withoutTime.apply(ctx))
                .then(Commands.argument("seconds", TolerantDoubleArgument.tolerantDouble(0.1D, MAX_SECONDS))
                        .executes(ctx -> withTime.apply(ctx)));
    }

    /** {@return the channel the command was given, or the default when it was omitted}, throwing for
     * a name that is not a sound channel. */
    private static String channel(CommandContext<CommandSourceStack> ctx) {
        String raw;
        try {
            raw = StringArgumentType.getString(ctx, "channel");
        } catch (IllegalArgumentException absent) {
            // The argument is simply not on this path: no channel was typed.
            return DEFAULT_CHANNEL;
        }

        for (SoundSource source : SoundSource.values()) {
            if (source.getName().equalsIgnoreCase(raw)) return source.getName();
        }
        throw new IllegalArgumentException("'" + raw + "' is not a sound channel. Use one of: "
                + channelNames() + ".");
    }

    private static String channelNames() {
        StringBuilder sb = new StringBuilder();
        for (SoundSource source : SoundSource.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(source.getName());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Starting
    // ------------------------------------------------------------------

    private int radius(CommandContext<CommandSourceStack> ctx, String channel, long expiresAt) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String sound = ResourceLocationArgument.getId(ctx, "sound").toString();
            double radius = TolerantDoubleArgument.getTolerantDouble(ctx, "radius");
            float volume = (float) TolerantDoubleArgument.getTolerantDouble(ctx, "volume");
            float pitch = (float) TolerantDoubleArgument.getTolerantDouble(ctx, "pitch");

            MusicZone zone = MusicZoneManager.addRadius(
                    player, radius, sound, channel, volume, pitch, expiresAt);
            if (zone == null) return failed(ctx, "The music zone database is not available.");

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Playing " + sound + " [" + channel + "] in a " + trim(radius)
                            + " block radius around you" + duration(expiresAt) + "."), true);
            return 1;
        } catch (Exception e) {
            return failed(ctx, e.getMessage());
        }
    }

    private int box(CommandContext<CommandSourceStack> ctx, String channel, long expiresAt) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String sound = ResourceLocationArgument.getId(ctx, "sound").toString();
            BlockPos first = BlockPosArgument.getLoadedBlockPos(ctx, "pos1");
            BlockPos second = BlockPosArgument.getLoadedBlockPos(ctx, "pos2");
            float volume = (float) TolerantDoubleArgument.getTolerantDouble(ctx, "volume");
            float pitch = (float) TolerantDoubleArgument.getTolerantDouble(ctx, "pitch");

            MusicZone zone = MusicZoneManager.addBox(player,
                    first.getX(), first.getY(), first.getZ(),
                    second.getX(), second.getY(), second.getZ(),
                    sound, channel, volume, pitch, expiresAt);
            if (zone == null) return failed(ctx, "The music zone database is not available.");

            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Playing " + sound + " [" + channel + "] in the box "
                            + first.toShortString() + " to " + second.toShortString()
                            + duration(expiresAt) + "."), true);
            return 1;
        } catch (Exception e) {
            return failed(ctx, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Stopping
    // ------------------------------------------------------------------

    private int stopCurrent(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            List<MusicZone> removed = MusicZoneManager.stopContaining(player);
            if (removed.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal(
                        "You are not standing in a music zone."));
                return 0;
            }
            for (MusicZone zone : removed) {
                ctx.getSource().sendSuccess(() -> Component.literal("Stopped " + zone.describe()), false);
            }
            return removed.size();
        } catch (Exception e) {
            return failed(ctx, e.getMessage());
        }
    }

    /** Removes one zone by its id: the only way to address a zone the operator is not standing in. */
    private int stopById(CommandContext<CommandSourceStack> ctx) {
        try {
            int id = IntegerArgumentType.getInteger(ctx, "id");
            MusicZone removed = MusicZoneManager.stopById(ctx.getSource().getServer(), id);
            if (removed == null) {
                ctx.getSource().sendFailure(Component.literal("No music zone with id " + id + "."));
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Stopped and removed " + removed.describe()), true);
            return 1;
        } catch (Exception e) {
            return failed(ctx, e.getMessage());
        }
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        try {
            List<MusicZone> zones = MusicZoneManager.all();
            if (zones.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("No music zones."), false);
                return 0;
            }
            long now = System.currentTimeMillis();
            for (MusicZone zone : zones) {
                ctx.getSource().sendSuccess(() -> Component.literal("  " + zone.describe()
                        + (zone.permanent() ? "" : " (" + remaining(zone, now) + ")")), false);
            }
            int count = zones.size();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    count + " music zone(s). Remove one with /stopzone <id>."), false);
            return count;
        } catch (Exception e) {
            return failed(ctx, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** {@return every sound channel name, for tab completion} */
    private static CompletableFuture<Suggestions> suggestChannels(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> names = new ArrayList<>();
        for (SoundSource source : SoundSource.values()) names.add(source.getName());
        return SharedSuggestionProvider.suggest(names, builder);
    }

    /** {@return when this zone should end, or {@link MusicZone#PERMANENT} when no time was given} */
    private static long expiresAt(CommandContext<CommandSourceStack> ctx) {
        double seconds = TolerantDoubleArgument.getTolerantDouble(ctx, "seconds");
        return System.currentTimeMillis() + Math.round(seconds * 1000.0D);
    }

    private static String duration(long expiresAt) {
        if (expiresAt == MusicZone.PERMANENT) return ", until stopped";
        long seconds = Math.max(0L, (expiresAt - System.currentTimeMillis()) / 1000L);
        return " for " + seconds + "s";
    }

    private static String remaining(MusicZone zone, long now) {
        long seconds = Math.max(0L, (zone.expiresAtMillis() - now) / 1000L);
        return seconds + "s left";
    }

    private static int failed(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal("Error: " + message));
        return 0;
    }

    private static String trim(double value) {
        return value == Math.floor(value)
                ? String.valueOf((long) value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
