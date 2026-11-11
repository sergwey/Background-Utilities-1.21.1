package net.xlebupaksa.backutils.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.xlebupaksa.backutils.chat.ChatHandler;

/**
 * {@code /adm <message>} — the operators' own chat channel.
 *
 * <p>It is ordinary chat in every respect except the audience: the sender's profile format and
 * separator, markup, item triggers, typing sound and console record all go through
 * {@link ChatHandler}, which is what makes it the same line a player would have said in local chat.
 * The only thing that differs is that the command is gated at staff level, and the same level is the
 * audience, so nobody below it can write here and nobody below it is sent what is written.
 *
 * <p>The command is hidden from console and command blocks rather than explained to them: the line
 * starts with a player's displayed name and chat profile, and there is no player behind those. A
 * command source that is not a player therefore does not see it at all.
 */
public class AdminChatCommand {

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("adm")
                .requires(source -> source.getEntity() instanceof ServerPlayer
                        && source.hasPermission(ChatHandler.STAFF_LEVEL))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(this::execute)));
    }

    private int execute(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String message = StringArgumentType.getString(ctx, "message").trim();
            if (message.isEmpty()) {
                ctx.getSource().sendFailure(Component.translatable("backutils.command.adm.empty"));
                return 0;
            }

            return ChatHandler.sayInAdminChannel(player, message) ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("backutils.command.error",
                    String.valueOf(e.getMessage())));
            return 0;
        }
    }
}
