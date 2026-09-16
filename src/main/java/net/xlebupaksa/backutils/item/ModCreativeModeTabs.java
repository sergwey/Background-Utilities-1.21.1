package net.xlebupaksa.backutils.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.xlebupaksa.backutils.BackUtils;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BackUtils.MOD_ID);

    public static final Supplier<CreativeModeTab> BACKGROUND_UTILITIES_TAB = CREATIVE_MODE_TAB.register("background_utilities",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.WANDERLITE.get()))
                    .title(Component.translatable("creativetab.backutils.background_utilities"))
                    .displayItems((itemDisplayParameters, output) -> {
                        // Здесь будут кастомные предметы
                        output.accept(ModItems.WANDERLITE);

                    })
                    .build());

    public static void register(IEventBus eventBus){
        CREATIVE_MODE_TAB.register(eventBus);
    }

}
