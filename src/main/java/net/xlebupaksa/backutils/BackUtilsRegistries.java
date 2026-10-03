package net.xlebupaksa.backutils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.xlebupaksa.backutils.commands.arguments.TolerantDoubleArgument;

/**
 * The mod-bus part of the mod's setup, kept off {@link BackUtils} because {@link RegisterEvent} and
 * {@link FMLCommonSetupEvent} are both {@code IModBusEvent} handlers, which the game bus refuses.
 *
 * <p>Attached with {@code modEventBus.addListener} rather than as annotated methods, so the bus a
 * handler belongs to is decided here rather than inferred from the event type.
 */
public final class BackUtilsRegistries {

    private BackUtilsRegistries() {}

    /** Gives the tolerant number argument the registry id that goes on the wire. */
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.COMMAND_ARGUMENT_TYPE,
                ResourceLocation.fromNamespaceAndPath(BackUtils.MOD_ID, "tolerant_double"),
                () -> TolerantDoubleArgument.Info.INSTANCE);
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
