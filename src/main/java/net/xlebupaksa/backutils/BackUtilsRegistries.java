package net.xlebupaksa.backutils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.xlebupaksa.backutils.commands.arguments.TolerantDoubleArgument;
import net.xlebupaksa.backutils.item.ModDataComponents;
import net.xlebupaksa.backutils.item.ModItems;

/**
 * The mod-bus part of the mod's setup, kept off {@link BackUtils} because {@link RegisterEvent} and
 * {@link FMLCommonSetupEvent} are both {@code IModBusEvent} handlers, which the game bus refuses.
 *
 * <p>Attached with {@code modEventBus.addListener} rather than as annotated methods, so the bus a
 * handler belongs to is decided here rather than inferred from the event type.
 */
public final class BackUtilsRegistries {

    private BackUtilsRegistries() {}

    /**
     * The tab the two tools are found in.
     *
     * <p>Named here rather than inside the registration because the contents are filled by a different
     * event from the one that makes the tab: the two have to agree about which tab they mean, and a
     * key written out twice is a tab that quietly stays empty.
     */
    private static final ResourceKey<CreativeModeTab> TOOLS_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "tools"));

    /** Gives the tolerant number argument the registry id that goes on the wire. */
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.COMMAND_ARGUMENT_TYPE,
                ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "tolerant_double"),
                () -> TolerantDoubleArgument.Info.INSTANCE);
        event.register(Registries.CREATIVE_MODE_TAB, TOOLS_TAB.location(), () -> CreativeModeTab.builder()
                .title(Component.translatable("backutils.tab.tools"))
                .icon(() -> new ItemStack(ModItems.EFFECT_TOOL))
                .build());
        event.register(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "effect_tool"),
                () -> ModItems.EFFECT_TOOL);
        event.register(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "delete_tool"),
                () -> ModItems.DELETE_TOOL);
        event.register(Registries.DATA_COMPONENT_TYPE,
                ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "effect_tool_config"),
                () -> ModDataComponents.EFFECT_TOOL_CONFIG);
    }

    /**
     * Puts the two tools where a creative player can find them.
     *
     * <p>In a tab of this mod's own. The tools are the whole of what this mod adds that a creative
     * player handles directly, and a tab is how they are found; the game's own Tools and Utilities tab
     * was the first home for them and it buried them among everything else in it.
     *
     * <p>The effect tool is set up by opening an inventory and holding W over it, and the creative
     * screen cannot do that: its item picker does not line up index for index with the server's menu,
     * so an edit made there names a slot the server reads as something else and is refused. A creative
     * player sets the tool up from an ordinary container, which lines up, or from the tooltip's own
     * instruction once it is in the hotbar.
     */
    public static void onBuildTabContents(BuildCreativeModeTabContentsEvent event) {
        if (TOOLS_TAB.equals(event.getTabKey())) {
            event.accept(ModItems.EFFECT_TOOL);
            event.accept(ModItems.DELETE_TOOL);
        }
    }

    /** Verifies the argument type registered, since a missing id corrupts the command packet. */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        if (BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getId(TolerantDoubleArgument.Info.INSTANCE) < 0) {
            BackUtils.LOGGER.error("The tolerant double argument type did not register. "
                    + "/playradius and /playbox will not work, and joining a world will fail "
                    + "with 'VarInt too big'. Do not ship this build.");
        }
    }
}
