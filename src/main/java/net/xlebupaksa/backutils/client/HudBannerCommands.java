package net.xlebupaksa.backutils.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.xlebupaksa.backutils.data.MarkupUtil;

/**
 * {@code /backutils banner} — the text drawn beside the log's menu button.
 *
 * <p>A <b>client</b> command, registered on {@link RegisterClientCommandsEvent} rather than with the
 * server's {@code /backutils} tree, because the setting it writes is the client's own: the banner is
 * drawn by this client, from this client's config file, and it has to work on a server that does not
 * have this mod at all — which every optional channel in this mod already assumes.
 *
 * <p>It sits under the server's own root because that is where a player looks for it. The two trees
 * are separate, and this one holds {@code banner} alone: the client is asked first, so a command that
 * parses here is answered here, and every other {@code /backutils ...} path - the bare command, and
 * {@code radius}, {@code freeze}, {@code typing} and the rest - fails to parse against this tree and
 * is sent to the server as it always was. The one thing to keep true is therefore that no subcommand
 * here may share a name with one there, or the client would answer it and the server would never see
 * it.
 *
 * <p>The command sets a string and nothing else. Everything about how the text looks is markup
 * inside that string, so there is no second way here to set a colour, a size or a sound: a command
 * that offered them would be offering a poorer version of what the text already carries.
 *
 * <p>Replies are written into this client's chat directly. A client command is answered by the
 * client, and its source has no connection to reply through, so {@code sendSuccess} would be
 * written into a source that goes nowhere.
 */
@OnlyIn(Dist.CLIENT)
public final class HudBannerCommands {

    private HudBannerCommands() {}

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();

        d.register(Commands.literal("backutils")
                .then(Commands.literal("banner")
                        .executes(ctx -> status())
                        .then(Commands.literal("set")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> set(StringArgumentType.getString(ctx, "text")))))
                        .then(Commands.literal("clear").executes(ctx -> set("")))));
    }

    /** Reports the text, drawn as it was typed so its markup is readable rather than applied. */
    private static int status() {
        String text = BackUtilsClientConfig.getBannerText();
        if (text.isBlank()) {
            reply(Component.literal("No banner text. Set one with "
                    + "/backutils banner set <text>; \\n starts a new line."));
            return 1;
        }
        reply(Component.literal("Banner: ").append(MarkupUtil.asLiteral(text)));
        return 1;
    }

    private static int set(String text) {
        BackUtilsClientConfig.setBannerText(text);
        if (text.isEmpty()) {
            reply(Component.literal("Banner text cleared."));
            return 1;
        }
        reply(Component.literal("Banner set: ").append(MarkupUtil.asLiteral(text)));
        return 1;
    }

    /** Writes a line into this client's own chat; see the class comment for why not sendSuccess. */
    private static void reply(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) return;
        minecraft.gui.getChat().addMessage(message);
    }
}
