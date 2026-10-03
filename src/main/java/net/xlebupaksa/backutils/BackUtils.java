package net.xlebupaksa.backutils;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.xlebupaksa.backutils.chat.ChatHandler;
import net.xlebupaksa.backutils.client.BackUtilsClientConfig;
import net.xlebupaksa.backutils.client.ClientSetup;
import net.xlebupaksa.backutils.commands.arguments.TolerantDoubleArgument;
import net.xlebupaksa.backutils.commands.BackUtilsCommands;
import net.xlebupaksa.backutils.commands.ChatCommands;
import net.xlebupaksa.backutils.commands.MeCommand;
import net.xlebupaksa.backutils.commands.MusicCommands;
import net.xlebupaksa.backutils.commands.ProfileCommands;
import net.xlebupaksa.backutils.data.BackUtilsData;
import net.xlebupaksa.backutils.data.ModAttachments;
import net.xlebupaksa.backutils.log.LogCommands;
import net.xlebupaksa.backutils.log.RoleplayLogTicker;
import net.xlebupaksa.backutils.music.MusicZoneTicker;
import net.xlebupaksa.backutils.profile.PlayerProfileEvents;
import net.xlebupaksa.backutils.profile.TabListNameHandler;
import org.slf4j.Logger;

@Mod(BackUtils.MOD_ID)
public class BackUtils {

    public static final String MOD_ID = "backutils";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static BackUtilsData data;

    public BackUtils(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.REGISTRY.register(modEventBus);

        // Fills ArgumentTypeInfos' class map only, which is what lets the server find an
        // argument's serialiser; the registry entry that gives the type its id comes from the
        // RegisterEvent handler below. Both steps must use the same Info instance.
        ArgumentTypeInfos.registerByClass(TolerantDoubleArgument.class,
                TolerantDoubleArgument.Info.INSTANCE);

        // The mod-bus half of setup, attached explicitly rather than as @SubscribeEvent methods on
        // this class: RegisterEvent and FMLCommonSetupEvent are both IModBusEvent, which NeoForge
        // refuses to register on the game bus that takes `this` below.
        modEventBus.addListener(BackUtilsRegistries::onRegister);
        modEventBus.addListener(BackUtilsRegistries::onCommonSetup);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new ChatHandler());
        NeoForge.EVENT_BUS.register(new MeCommand());
        NeoForge.EVENT_BUS.register(new BackUtilsCommands());
        NeoForge.EVENT_BUS.register(new ChatCommands());
        NeoForge.EVENT_BUS.register(new MusicCommands());
        NeoForge.EVENT_BUS.register(new ProfileCommands());
        NeoForge.EVENT_BUS.register(new LogCommands());
        NeoForge.EVENT_BUS.register(new PlayerProfileEvents());
        NeoForge.EVENT_BUS.register(new TabListNameHandler());
        NeoForge.EVENT_BUS.register(new RoleplayLogTicker());
        NeoForge.EVENT_BUS.register(new MusicZoneTicker());

        modContainer.registerConfig(ModConfig.Type.SERVER, BackUtilsConfig.SPEC);

        // Client-only: the classes this reaches into do not exist on a dedicated server.
        if (FMLEnvironment.dist.isClient()) {
            modContainer.registerConfig(ModConfig.Type.CLIENT, BackUtilsClientConfig.SPEC);
            ClientSetup.register(modEventBus);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onServerStarted(ServerStartedEvent event) {
        try {
            data = new BackUtilsData(event.getServer().getServerDirectory());
        } catch (RuntimeException e) {
            // A database that cannot be opened must not take the server down with it; every
            // entry point null-checks data(), so the profile features stay unavailable.
            LOGGER.error("Background Utils could not open its databases; "
                    + "profile features are disabled until this is fixed.", e);
            data = null;
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onServerStopping(ServerStoppingEvent event) {
        if (data != null) {
            data.closeAll();
            data = null;
        }
    }

    public static BackUtilsData data() {
        return data;
    }
}
