package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.xlebupaksa.backutils.BackUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Puts RP Renames' creative tab back on the page, on the 1.21.1 build of it that has no NeoForge
 * release of its own and therefore runs here as a Fabric mod through Sinytra Connector.
 *
 * <p><b>Why the tab is missing.</b> Not because anything about it is wrong: RP Renames registers it,
 * it is in the registry, and the mod works. It is missing because the tab is empty. In RP Renames
 * 0.9.2 the tab's generator fills RP Renames' own list and offers the tab nothing —
 * {@code entries((displayContext, entries) -> update())}, with the entries it is handed unused — so the
 * worked-out set of items a creative tab carries is empty for it.
 *
 * <p>NeoForge, assembling the creative menu's tab pages, keeps only the tabs that have something in
 * them ({@code CreativeModeTabRegistry.getSortedCreativeModeTabs()} filtered by
 * {@link CreativeModeTab#hasAnyItems()}), so an empty tab is left in no page at all: never drawn,
 * never clickable, and with nothing logged to say so. Fabric's creative screen, by contrast, filters
 * on {@code shouldDisplay()}, which permits a search tab with no items — which is why the tab is
 * visible on Fabric and not here, and why this is a NeoForge symptom rather than a broken mod. RP
 * Renames later fixed it upstream for its own NeoForge line by giving the tab one item, with the
 * comment "some loaders may not show tab if it's 'empty'"; that fix is not in the build this mod is
 * used with.
 *
 * <p><b>What this does about it.</b> The same thing, from outside, for the version that lacks it: when
 * the tab's contents are built, the item RP Renames uses as its own icon is offered to it, which is
 * enough for the tab to be kept. A fallback covers the case where those contents are never built at
 * all, and asks RP Renames to refill the list it draws its own contents from, so that opening the tab
 * shows the renames rather than the one item. Nothing is added to any other tab, and nothing happens
 * when RP Renames is absent.
 *
 * <p>RP Renames' class is reached reflectively, as this mod reaches every optional integration: there
 * is no compile-time dependency on it, and a build of RP Renames that has changed shape is answered by
 * leaving the tab with its icon rather than by throwing.
 *
 * <p>Note for whoever tests this: NeoForge fits ten tabs to a page and vanilla's come first, so the
 * tab is on the second page of the creative menu, behind the {@code >} button.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = BackUtils.MOD_ID, value = Dist.CLIENT)
@SuppressWarnings("unused") // entry points: the game bus calls this
public final class RpRenamesTab {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpRenamesTab.class);

    /** The tab RP Renames registers, under the same name in every build of it. */
    private static final ResourceLocation TAB = ResourceLocation.fromNamespaceAndPath("rprenames", "item_group");

    /**
     * Where the list behind the tab lives, in the build this is written for and in the line that
     * followed it: 0.9.2 is {@code com.HiWord9.RPRenames}, later builds moved it.
     */
    private static final String[] ITEM_GROUP = {
            "com.HiWord9.RPRenames.RPRenamesItemGroup",
            "com.hiword9.rprenames.mod.item_group.RPRenamesItemGroup",
    };

    /** Whether RP Renames has already been asked to refill its list: once is enough, and it is not free. */
    private static boolean asked;

    /** Set when the game refuses the item, which would otherwise be attempted on every tick. */
    private static boolean refused;

    private RpRenamesTab() {}

    /**
     * The clean half: a tab's contents are built, for every registered tab, when the creative menu
     * works out what each one holds, and this is where RP Renames' own later builds add the same item.
     * Which bus this arrives on is not declared here: a mod-bus event is routed to the mod bus whatever
     * the annotation says, and the parameter that used to say so is deprecated for removal.
     */
    @SubscribeEvent
    public static void onBuildTabContents(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(TAB)) return;
        event.accept(icon());
        asked = true;
    }

    /**
     * The fallback, every tick, for a build of RP Renames whose tab contents never reach that event. It
     * costs a registry lookup and an emptiness check until the tab has something in it, and a tick is
     * what it has to be: the menu's pages are assembled inside the screen's own {@code init}, which runs
     * before any screen event this could listen for.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (refused || asked || Minecraft.getInstance().level == null) return;

        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(TAB);
        if (tab == null) return; // RP Renames is not installed, which is the ordinary case
        if (tab.hasAnyItems()) return;

        // Registered and empty: exactly the state the creative menu leaves off its pages. One item is
        // the difference.
        try {
            tab.getDisplayItems().add(icon());
        } catch (Throwable immutable) {
            refused = true;
            LOGGER.error("RP Renames' creative tab is empty and its contents cannot be added to from here, "
                    + "so the tab will stay hidden; this is a compatibility problem, not a fault in this mod", immutable);
            return;
        }

        asked = true;
        refillRenamesTab();
    }

    /**
     * RP Renames' own icon, copied rather than invented: a knowledge book with the glint forced on and
     * its own marker in the item's custom data, which is how that build recognises the tab as its own.
     */
    private static ItemStack icon() {
        ItemStack stack = new ItemStack(Items.KNOWLEDGE_BOOK);
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        CompoundTag marker = new CompoundTag();
        marker.putString("rprenames", "");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        return stack;
    }

    /**
     * Asks RP Renames to rebuild the list behind its tab, which is what it would have done itself while
     * building the tab's contents had it been given something to build. Its {@code update} already
     * returns early when there is no level, so calling it here at the wrong moment is harmless.
     */
    private static void refillRenamesTab() {
        for (String name : ITEM_GROUP) {
            try {
                Class.forName(name).getMethod("update").invoke(null);
                return;
            } catch (Throwable changedOrAbsent) {
                // Try the next name, or give up: the tab still appears, which is the part that broke.
            }
        }
    }
}
