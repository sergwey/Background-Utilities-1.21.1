package net.xlebupaksa.backutils.clientchest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /clientchest} and its short name, registered on the client rather than the server.
 *
 * <p>Which side registers it is the whole point: the library is the player's own files and has to work
 * on a server that has never heard of this mod, so the command cannot be one the server answers. A
 * client command is answered by the client, and the server is not asked anything.
 */
public final class ChestCommand {

    private ChestCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(tree());
        // Redirected rather than duplicated: the two names have to keep answering the same way.
        dispatcher.register(Commands.literal("cc").redirect(node));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("clientchest")
                .executes(context -> {
                    ChestScreen.open();
                    return 1;
                })
                .then(Commands.literal("import").executes(context -> importLibrary(context.getSource())))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            // Pages are counted from one on the command line: a player typing a page
                            // number means the ninth page, not the tenth.
                            ChestScreen.open(IntegerArgumentType.getInteger(context, "page") - 1);
                            return 1;
                        }))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            if (ChestScreen.openNamed(name)) return 1;
                            context.getSource().sendFailure(
                                    Component.translatable("backutils.client_chest.no_such_page", name));
                            return 0;
                        }));
    }

    /**
     * Moves a library left behind by the tool this feature is modelled on into this one.
     *
     * <p>A move, not a second copy: that tool runs on Fabric and cannot be installed beside this one on
     * this version of the game, so what is being imported is an abandoned library, and reading it in
     * place would leave two libraries that are no longer the same after the first change.
     */
    private static int importLibrary(CommandSourceStack source) {
        ChestPages pages = ChestPages.ofRunningGame();
        if (!pages.hasOriginalLibrary()) {
            source.sendFailure(Component.translatable("backutils.client_chest.import.none"));
            return 0;
        }
        int moved = pages.migrateFromOriginal();
        if (moved == 0) {
            source.sendFailure(Component.translatable("backutils.client_chest.import.nothing"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("backutils.client_chest.import.done", moved),
                false);
        return moved;
    }
}
