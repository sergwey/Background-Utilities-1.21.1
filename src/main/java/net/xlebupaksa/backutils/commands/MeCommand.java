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
import net.xlebupaksa.backutils.BackUtilsConfig;
import net.xlebupaksa.backutils.chat.ChatHandler;

/**
 * {@code /me} and {@code /sme}: the two action forms this mod reads out of chat, written as commands
 * instead.
 *
 * <p>Everything after the body has been cut out is {@link ChatHandler}'s, so {@code /me} is the
 * {@code *text*} route and {@code /sme} is the {@code ^text^} one in every respect — the same silencing
 * gate and the same refusal for it, the same row with the same witnesses, the same alert to operators,
 * the same console record, and nothing posted to chat in either case. They are not two implementations
 * that have to be kept in step: the command hands the body over and the chat path does the rest.
 * {@code /sme} is the silent twin, so nobody but the actor is shown the entry and the console record
 * says so.
 *
 * <p>The body itself is the one thing a command and a marker in chat cannot share: an argument arrives
 * already cut out, where a chat line has to be recognised as an action before anything can be cut from
 * it. So {@code /me *waves*} records {@code *waves*} — the asterisks are part of what was typed — while
 * the same text in chat is ordinary chat, because {@code ActionText} refuses a body that still holds an
 * asterisk rather than guess where the action ends.
 *
 * <p>While the action log is switched off neither of them records anything, no operator is alerted and
 * nothing is said: the actor is told the actions are disabled and that is the whole of the answer, the
 * same for {@code /me} as for {@code /sme}. The two do not part company there, and {@code /me} does not
 * fall back to saying its words as ordinary local chat: the chat route is the same route, and an action
 * nobody is recording is not an action, so publishing its words as a line would be a second way of saying
 * something the player wrote as an action — the words marked as not for everyone among them. Both tell the
 * actor that the action was not recorded, and tell them once. That state is the whole point of the
 * switch — an operator turned it off so that actions would stop filling the log — so a command that kept
 * writing rows, pushing them to the witnesses and nudging every operator would be the one thing the switch
 * could not stop.
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
     *
     * <p>Success is deliberately not logged: this runs on every world load, and the mod writes
     * nothing to the log in normal operation. A node that cannot be removed is the one case worth an
     * error, because then this mod's {@code /me} never took effect at all.
     */
    private static void replaceVanillaMe(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandNode<CommandSourceStack> existing = dispatcher.getRoot().getChild("me");
        if (existing == null) return;
        try {
            dispatcher.getRoot().getChildren().remove(existing);
        } catch (UnsupportedOperationException e) {
            BackUtils.LOGGER.error("Could not replace the vanilla /me command; "
                    + "/me actions will not reach the roleplay log.", e);
        }
    }

    private int execute(CommandContext<CommandSourceStack> ctx, boolean silent) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            // The argument is the body as typed, asterisks and all: a player who writes /me *waves*
            // said those asterisks, so the log keeps them rather than the command editing what was
            // said. Only the surrounding whitespace comes off, which is all the chat forms do to a
            // body they cut out of a line, and all this did before the asterisks stopped coming off.
            String body = StringArgumentType.getString(ctx, "action").trim();
            if (body.isBlank()) {
                // The chat forms have no equivalent: an empty asterisk pair is not an action at all,
                // where a command with nothing to act out has to say so rather than record a blank.
                ctx.getSource().sendFailure(Component.translatable("backutils.command.me.empty"));
                return 0;
            }

            // Both things that can stop an action, asked in the one order that is right: a silence of the
            // action kind is a moderation state and comes first, and the recording switch decides only
            // whether an action allowed to happen is recorded. See ChatHandler.actionOutcome.
            switch (ChatHandler.actionOutcome(ChatHandler.silencedInActions(player),
                    BackUtilsConfig.isActionLoggingEnabled())) {
                case REFUSED -> {
                    ChatHandler.notifyActionsMuted(player);
                    return 0;
                }
                case DISABLED -> {
                    // Nothing is said and nothing is recorded. The action did not happen, so 0 is returned
                    // — not because the command refused it — and the notice is the whole of the answer: the
                    // same for /me as for /sme, because a server that is not recording actions is not
                    // recording either of them.
                    ChatHandler.notifyActionsDisabled(player);
                    return 0;
                }
                case RECORDED -> {
                    return ChatHandler.recordAction(player, body, silent) ? 1 : 0;
                }
            }
            // Kept, though every case above returns: javac does not count an arrow switch statement over
            // an enum as covering the method's paths, so this is the value the method needs to compile.
            return 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("backutils.command.error",
                    String.valueOf(e.getMessage())));
            return 0;
        }
    }
}
