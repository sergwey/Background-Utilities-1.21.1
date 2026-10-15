package net.xlebupaksa.backutils.chat;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.SilenceKind;
import net.xlebupaksa.backutils.data.SilenceState;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The gate on private messages: what one player sends to named players, rather than what everyone
 * nearby hears.
 *
 * <p>Private messages are not chat, so there is no chat event to refuse them at. They are commands —
 * "/msg", "/tell" and "/w", which are one command registered under three names — and the refusal
 * happens where every other command's does: after the line has been parsed and before it runs, in
 * {@link CommandEvent}, which carries the parse and can be cancelled. Nothing vanilla is edited, and
 * nothing is re-implemented: the command either runs as vanilla wrote it, or it does not run at all.
 *
 * <p>Both ends of a message are gated, and told differently. A sender silenced in {@code msg} is told
 * so, in the words the other silences are told in: it is their own state, and they are entitled to it.
 * A recipient silenced in {@code msg} is not told anything — and neither is the sender told why:
 * {@link ChatHandler#notifyRecipientNotReceiving} says only that the message was not received, because
 * one player's moderation state is not the other's business. A message no recipient may receive is
 * therefore refused whole rather than delivered to the rest of its list: the alternative is composing
 * the message a second time, signed chat included, which is vanilla's to do and not this mod's.
 *
 * <p>The command is looked for by name anywhere in the parse chain rather than at its head, because
 * "/execute run tell ..." redirects to the root and parses the real command again as a child of the
 * context the event hands over; a gate that only read the first context would be one "/execute" away
 * from being bypassed. A side effect of that, worth knowing: a recipient selector that names the
 * command's own executor — {@code @s}, {@code @p}, or a relative one — is read against the source the
 * line was parsed with, because the "/execute as" that would change it runs during execution, after
 * this event. Names, {@code @a} and absolute selectors read the same either way.
 */
public class PrivateMessageGate {

    /**
     * The commands that send one message to the players named after them. One command, three names:
     * "/tell" and "/w" are redirects onto "/msg", which is why all three appear as a node of the same
     * parse.
     */
    private static final Set<String> COMMANDS = Set.of("msg", "tell", "w");

    /** The argument each of them names its recipients with. */
    private static final String TARGETS = "targets";

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onCommand(CommandEvent event) {
        try {
            ParseResults<CommandSourceStack> parse = event.getParseResults();
            CommandContextBuilder<CommandSourceStack> message = privateMessage(parse.getContext());
            if (message == null) return;

            CommandSourceStack source = parse.getContext().getSource();
            ServerPlayer sender = source.getEntity() instanceof ServerPlayer player ? player : null;

            // The sender first, because it is their own silence: a message they may not send at all
            // is not a question about who it was addressed to.
            if (sender != null && silenced(sender, SilenceKind.MSG)) {
                event.setCanceled(true);
                ChatHandler.notifyMessagesMuted(source);
                return;
            }

            // A console or a command block has no silence of its own, but the players it writes to
            // have: what the gate protects is the recipient's choice, whoever is asking.
            for (ServerPlayer recipient : recipients(message)) {
                if (silenced(recipient, SilenceKind.MSG)) {
                    event.setCanceled(true);
                    ChatHandler.notifyRecipientNotReceiving(source);
                    return;
                }
            }
        } catch (Exception e) {
            // Deliberately not cancelled: a gate that cannot read a command must not stop the server
            // obeying it, whatever the reason was. The error is logged because this is the one place
            // where a fault would be invisible - the message would simply arrive.
            BackUtils.LOGGER.error("Could not check a private message for silences", e);
        }
    }

    /**
     * {@return the context of the private message being run, or null when this is not one}
     *
     * <p>The deepest context naming one of the three commands, because "/execute run tell ..." parses
     * the message inside a context of its own; the arguments come off that context or one of its
     * children, as "/tell" and "/w" arrive through a redirect and keep their recipients a level down.
     */
    private static CommandContextBuilder<CommandSourceStack> privateMessage(
            CommandContextBuilder<CommandSourceStack> parse) {
        CommandContextBuilder<CommandSourceStack> message = null;
        for (CommandContextBuilder<CommandSourceStack> context = parse; context != null;
                context = context.getChild()) {
            for (ParsedCommandNode<CommandSourceStack> node : context.getNodes()) {
                if (COMMANDS.contains(node.getNode().getName())) message = context;
            }
        }
        return message;
    }

    /**
     * {@return the players the message names, empty when it names nobody}
     *
     * <p>A selector the parser accepted but that cannot be resolved here is left to the command: it
     * reports that itself a moment later, and answering in its place would put this mod's wording on
     * a vanilla mistake.
     */
    private static Collection<ServerPlayer> recipients(
            CommandContextBuilder<CommandSourceStack> message) {
        EntitySelector selector = null;
        for (CommandContextBuilder<CommandSourceStack> context = message; context != null;
                context = context.getChild()) {
            ParsedArgument<CommandSourceStack, ?> argument = context.getArguments().get(TARGETS);
            if (argument != null && argument.getResult() instanceof EntitySelector found) {
                selector = found;
            }
        }
        if (selector == null) return List.of();

        try {
            return selector.findPlayers(message.getSource());
        } catch (CommandSyntaxException e) {
            return List.of();
        }
    }

    /** {@return true when this player carries a silence of this kind} */
    private static boolean silenced(ServerPlayer player, SilenceKind kind) {
        SilenceState state = ChatHandler.silenceOf(player);
        return state != null && state.silenced(kind);
    }
}
