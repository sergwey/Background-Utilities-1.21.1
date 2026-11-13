package net.xlebupaksa.backutils.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
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
 * {@code /execute raycast ...} — a source modifier in the middle of an execute chain.
 *
 * <p>It is a fork back to the execute root, not a command of its own: everything after it is the
 * ordinary execute grammar again, so {@code as}, {@code at}, {@code positioned}, {@code if} and
 * {@code run} can all follow it. The {@code entity}, {@code pos} and {@code block} modes differ only
 * in what they put into the source before the fork.
 *
 * <p>The filters are shared by all three modes:
 * <ul>
 *   <li>{@code ignoreAll} — blocks do not stop the ray at all;</li>
 *   <li>{@code hitAny} — any block that is not air stops it;</li>
 *   <li>{@code hitSolid} — only a block whose collision shape fills its whole cube stops it.</li>
 * </ul>
 *
 * <p>A distance of {@code -1} uses the 256-block maximum. The redirect modifier runs when the chain
 * executes, after the left-hand execute modifiers have had their say, so the ray starts from the
 * source that reaches this node rather than always from the original caller.
 */
public final class RaycastCommand {

    private static final double DEFAULT_DISTANCE = 256.0D;
    private static final double MAX_DISTANCE = 256.0D;
    private static final int MAX_SOLID_STEPS = 256;

    private static final List<String> FILTERS = List.of("ignoreAll", "hitAny", "hitSolid");
    private static final SuggestionProvider<CommandSourceStack> FILTER_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(FILTERS, builder);

    private static final SimpleCommandExceptionType ERROR_NO_VIEWER =
            new SimpleCommandExceptionType(
                    Component.translatable("backutils.raycast.no_viewer"));
    private static final SimpleCommandExceptionType ERROR_NO_ENTITY =
            new SimpleCommandExceptionType(
                    Component.translatable("backutils.raycast.no_entity"));
    private static final SimpleCommandExceptionType ERROR_NO_BLOCK =
            new SimpleCommandExceptionType(
                    Component.translatable("backutils.raycast.no_block"));
    private static final DynamicCommandExceptionType ERROR_BAD_FILTER =
            new DynamicCommandExceptionType(filter -> Component.translatable(
                    "backutils.raycast.bad_filter", filter));

    private RaycastCommand() {}

    /** Adds the modifier under the vanilla {@code execute} node. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> execute = dispatcher.getRoot().getChild("execute");
        if (execute == null || execute.getChild("raycast") != null) return;

        LiteralCommandNode<CommandSourceStack> raycast = Commands.literal("raycast").build();
        for (String mode : List.of("entity", "pos", "block")) {
            raycast.addChild(Commands.literal(mode)
                    .then(Commands.argument("distance", DoubleArgumentType.doubleArg(-1.0D))
                            .then(Commands.argument("filter", StringArgumentType.word())
                                    .suggests(FILTER_SUGGESTIONS)
                                    .fork(execute, modifier(mode))))
                    .build());
        }
        execute.addChild(raycast);
    }

    /**
     * {@return the redirect modifier for one mode}
     *
     * <p>A fork rather than a single redirect because the execute grammar uses forks for its own
     * {@code as} and {@code at}: it is the shape that lets the parser come back to the execute root
     * and read whatever modifier follows. One source is returned, or an exception is thrown so the
     * chain stops with the same message a user would have seen from the old standalone command.
     */
    private static RedirectModifier<CommandSourceStack> modifier(String mode) {
        return context -> {
            CommandSourceStack next = resolve(context, mode);
            return List.of(next);
        };
    }

    private static CommandSourceStack resolve(CommandContext<CommandSourceStack> context, String mode)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity looker = source.getEntity();
        if (looker == null) throw ERROR_NO_VIEWER.create();

        String filter = StringArgumentType.getString(context, "filter");
        if (!FILTERS.contains(filter)) throw ERROR_BAD_FILTER.create(filter);

        double requested = DoubleArgumentType.getDouble(context, "distance");
        double distance = requested < 0.0D ? DEFAULT_DISTANCE : Math.min(requested, MAX_DISTANCE);
        Vec3 eye = looker.getEyePosition();
        Vec3 direction = looker.getViewVector(1.0F).normalize();
        Vec3 end = eye.add(direction.scale(distance));

        ServerLevel level = source.getLevel();
        BlockHitResult blockHit = filter.equals("ignoreAll")
                ? null : clipBlocks(level, looker, eye, end, filter);
        Vec3 reachableEnd = blockHit == null ? end : blockHit.getLocation();

        if (mode.equals("entity")) {
            Entity target = findEntity(level, looker, eye, reachableEnd);
            if (target == null) throw ERROR_NO_ENTITY.create();
            return source.withEntity(target);
        }
        if (blockHit == null) throw ERROR_NO_BLOCK.create();
        return source.withPosition(mode.equals("pos")
                ? blockHit.getLocation()
                : Vec3.atCenterOf(blockHit.getBlockPos()));
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
