package net.xlebupaksa.backutils.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.ModAttachments;
import net.xlebupaksa.backutils.data.ProfileSnapshot;
import net.xlebupaksa.backutils.data.SilenceKind;
import net.xlebupaksa.backutils.data.SilenceState;
import net.xlebupaksa.backutils.log.ActionText;
import net.xlebupaksa.backutils.log.RoleplayLog;
import net.xlebupaksa.backutils.profile.ProfileText;

/**
 * {@code /me} and {@code /sme}, routed through this mod's own formatting: both obey the display name
 * and reach the roleplay log, and {@code /sme} is the silent twin.
 */
@SuppressWarnings("unused") // entry points: the game bus and the loader call these
public class MeCommand {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        replaceVanillaMe(dispatcher);

        dispatcher.register(Commands.literal("me")
                .then(Commands.argument("action", StringArgumentType.greedyString())
                        .executes(ctx -> execute(ctx, false))));

        dispatcher.register(Commands.literal("sme")
                .then(Commands.argument("action", StringArgumentType.greedyString())
                        .executes(ctx -> execute(ctx, true))));
    }

    /**
     * Removes the existing {@code /me} node so this mod's can take its place: Brigadier merges onto a
     * child of the same name rather than replacing it, which would leave the new executor attached to
     * vanilla's argument node.
     */
    private static void replaceVanillaMe(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> existing = dispatcher.getRoot().getChild("me");
        if (existing == null) return;
        try {
            if (dispatcher.getRoot().getChildren().remove(existing)) {
                BackUtils.LOGGER.debug("Replaced the vanilla /me command");
            }
        } catch (UnsupportedOperationException e) {
            BackUtils.LOGGER.error("Could not replace the vanilla /me command; "
                    + "/me actions will not reach the roleplay log.", e);
        }
    }

    private int execute(CommandContext<CommandSourceStack> ctx, boolean silent) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String body = ActionText.stripLeadingAsterisks(
                    StringArgumentType.getString(ctx, "action"));
            if (body.isBlank()) {
                ctx.getSource().sendFailure(Component.literal("Say what you are doing."));
                return 0;
            }

            SilenceState silence = BackUtils.data() == null ? null
                    : BackUtils.data().silences().find(player.getName().getString());
            if (silence != null && silence.silenced(SilenceKind.ACTIONS)) {
                ctx.getSource().sendFailure(Component.literal("Your actions are silenced."));
                return 0;
            }

            ProfileSnapshot snapshot = player.getData(ModAttachments.PROFILE.get());
            if (snapshot == null) snapshot = ProfileSnapshot.DEFAULT;
            String displayed = ProfileText.resolve(
                    snapshot.displayedName(), player.getName().getString());

            RoleplayLog.recordAction(player, ActionText.template(body), silent);

            if (silent) {
                // Silent means silent: the server log keeps a record for administration, but no
                // player is told anything, including the actor.
                BackUtils.LOGGER.info("[silent] * {} {}", player.getName().getString(), body);
                return 1;
            }

            // The server log keeps the vanilla-shaped record; players get the display name.
            player.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("* " + player.getName().getString() + " " + body),
                    recipient -> Component.literal(ActionText.emoteLine(displayed, body)),
                    false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}
