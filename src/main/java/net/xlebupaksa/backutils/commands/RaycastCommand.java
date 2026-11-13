package net.xlebupaksa.backutils.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * {@code /execute raycast ...} — turning a player's view into the source of the next command.
 *
 * <p>Vanilla's execute chain has no raycast step, so this is added as a child of the existing
 * {@code execute} node rather than as a second {@code execute} root. The three modes differ in what
 * they put into the source: {@code entity} puts the entity the player is looking at there,
 * {@code pos} puts the exact hit point, and {@code block} puts the centre of the block that was hit.
 *
 * <p>The filter is the same vocabulary in every mode:
 * <ul>
 *   <li>{@code ignoreAll} — blocks do not stop the ray at all;</li>
 *   <li>{@code hitAny} — any block that is not air stops it;</li>
 *   <li>{@code hitSolid} — only a block whose collision shape fills its whole cube stops it.</li>
 * </ul>
 *
 * <p>A distance of {@code -1} means the command's own generous default, not an infinite ray: a
 * command that iterates an unbounded set of chunks while somebody holds the button is not useful.
 * The tail is optional, so the examples in the request parse without a {@code run}; with no tail
 * the ray is still evaluated and reports whether it found what was asked for.
 */
public final class RaycastCommand {

    private static final double DEFAULT_DISTANCE = 256.0D;
    private static final double MAX_DISTANCE = 256.0D;
    private static final int MAX_SOLID_STEPS = 256;

    private static final List<String> FILTERS = List.of("ignoreAll", "hitAny", "hitSolid");
    private static final SuggestionProvider<CommandSourceStack> FILTER_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(FILTERS, builder);

    private RaycastCommand() {}

    /** Adds the command under the vanilla {@code execute} node. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> execute = dispatcher.getRoot().getChild("execute");
        if (execute == null || execute.getChild("raycast") != null) return;

        LiteralCommandNode<CommandSourceStack> raycast = Commands.literal("raycast")
                .requires(source -> source.hasPermission(2))
                .build();
        for (String mode : List.of("entity", "pos", "block")) {
            raycast.addChild(Commands.literal(mode)
                    .then(Commands.argument("distance", DoubleArgumentType.doubleArg(-1.0D))
                            .then(Commands.argument("filter", StringArgumentType.word())
                                    .suggests(FILTER_SUGGESTIONS)
                                    .executes(context -> run(context, mode))
                                    .then(Commands.literal("run")
                                            .then(Commands.argument("command",
                                                            StringArgumentType.greedyString())
                                                    .executes(context -> run(context, mode))))))
                    .build());
        }
        execute.addChild(raycast);
    }

    private static int run(CommandContext<CommandSourceStack> context, String mode) {
        CommandSourceStack source = context.getSource();
        Entity looker = source.getEntity();
        if (looker == null) {
            source.sendFailure(Component.translatable("backutils.raycast.no_viewer"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        String filter = StringArgumentType.getString(context, "filter");
        if (!FILTERS.contains(filter)) {
            source.sendFailure(Component.translatable("backutils.raycast.bad_filter", filter));
            return 0;
        }

        double requested = DoubleArgumentType.getDouble(context, "distance");
        double distance = requested < 0.0D ? DEFAULT_DISTANCE : Math.min(requested, MAX_DISTANCE);
        Vec3 eye = looker.getEyePosition();
        Vec3 direction = looker.getViewVector(1.0F).normalize();
        Vec3 end = eye.add(direction.scale(distance));

        BlockHitResult blockHit = filter.equals("ignoreAll") ? null
                : clipBlocks(level, looker, eye, end, filter);
        Vec3 reachableEnd = blockHit == null ? end : blockHit.getLocation();

        CommandSourceStack next;
        if (mode.equals("entity")) {
            Entity target = findEntity(level, looker, eye, reachableEnd);
            if (target == null) {
                source.sendFailure(Component.translatable("backutils.raycast.no_entity"));
                return 0;
            }
            next = source.withEntity(target);
        } else {
            if (blockHit == null) {
                source.sendFailure(Component.translatable("backutils.raycast.no_block"));
                return 0;
            }
            next = source.withPosition(mode.equals("pos")
                    ? blockHit.getLocation()
                    : Vec3.atCenterOf(blockHit.getBlockPos()));
        }

        String tail = tail(context);
        if (tail != null && !tail.isBlank()) {
            source.getServer().getCommands().performPrefixedCommand(next, tail);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String tail(CommandContext<CommandSourceStack> context) {
        try {
            return StringArgumentType.getString(context, "command");
        } catch (IllegalArgumentException ignored) {
            // The tail is optional: the examples in the request end after the filter.
            return null;
        }
    }

    /**
     * {@return the first block matching the filter, or null}
     *
     * <p>{@code hitSolid} cannot use a plain clip: a fence or a plant has a collision shape and would
     * win. The loop advances just past each non-full block and clips again, which is bounded and
     * keeps the same shape test the game itself uses for a full cube.
     */
    private static BlockHitResult clipBlocks(ServerLevel level, Entity looker, Vec3 eye, Vec3 end,
            String filter) {
        if (filter.equals("hitAny")) {
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, looker));
            return hit.getType() == HitResult.Type.MISS ? null : hit;
        }

        Vec3 start = eye;
        for (int step = 0; step < MAX_SOLID_STEPS; step++) {
            BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, looker));
            if (hit.getType() == HitResult.Type.MISS) return null;
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (state.isCollisionShapeFullBlock(level, hit.getBlockPos())) return hit;
            if (hit.getLocation().distanceToSqr(end) < 1.0E-6D) return null;
            start = hit.getLocation().add(end.subtract(eye).normalize().scale(0.01D));
        }
        return null;
    }

    /**
     * {@return the closest entity whose hitbox the ray meets}, excluding the viewer
     *
     * <p>The hitbox is inflated by the game's own entity-pick margin, and the result is chosen by
     * distance rather than by iteration order, so two entities on the same line answer with the one
     * nearer the eye.
     */
    private static Entity findEntity(ServerLevel level, Entity looker, Vec3 eye, Vec3 end) {
        Vec3 direction = end.subtract(eye);
        if (direction.lengthSqr() < 1.0E-6D) return null;
        AABB search = new AABB(eye, end).inflate(1.0D);
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity candidate : level.getEntities(looker, search,
                entity -> entity != looker && entity.isPickable() && !entity.isSpectator())) {
            Optional<Vec3> hit = candidate.getBoundingBox().inflate(candidate.getPickRadius())
                    .clip(eye, end);
            if (hit.isEmpty()) continue;
            double at = hit.get().distanceToSqr(eye);
            if (at < bestDistance) {
                bestDistance = at;
                best = candidate;
            }
        }
        return best;
    }
}
