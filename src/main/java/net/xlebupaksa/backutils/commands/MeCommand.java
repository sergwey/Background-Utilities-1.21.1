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
import net.xlebupaksa.backutils.data.SilenceKind;
import net.xlebupaksa.backutils.data.SilenceState;
import net.xlebupaksa.backutils.log.ActionText;
import net.xlebupaksa.backutils.log.RoleplayLog;

/**
 * {@code /me} and {@code /sme}, routed through this mod's own formatting: both reach the roleplay
 * log and the console record, and neither posts anything to chat. {@code /sme} is the silent twin:
 * nobody but the actor is shown the entry, and the console record says so.
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
                ctx.getSource().sendFailure(Component.translatable("backutils.command.me.empty"));
                return 0;
            }

            SilenceState silence = BackUtils.data() == null ? null
                    : BackUtils.data().silences().find(player.getName().getString());
            if (silence != null && silence.silenced(SilenceKind.ACTIONS)) {
                ctx.getSource().sendFailure(Component.translatable("backutils.command.me.silenced"));
                return 0;
            }

            RoleplayLog.recordAction(player, ActionText.template(body), silent);
            // Nothing is posted to chat: an action is read in the log, and repeating it in chat
            // would say it twice.
            RoleplayLog.logAction(player, body, silent);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("backutils.command.error",
                    String.valueOf(e.getMessage())));
            return 0;
        }
    }
}
