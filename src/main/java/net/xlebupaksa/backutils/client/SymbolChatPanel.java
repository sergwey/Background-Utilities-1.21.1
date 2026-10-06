package net.xlebupaksa.backutils.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * SymbolChat's own symbol menu, borrowed for the item editor when that mod is installed.
 *
 * <p><b>SymbolChat is a Fabric mod.</b> For 1.21.1 it is published for Fabric alone, so on this mod's
 * NeoForge client it can only ever be present through Sinytra Connector and the Forgified Fabric API.
 * That is why everything below is optional and silent: with none of those installed, every call here
 * answers nothing and the editor has one button fewer. Nothing outside this class may reach for
 * SymbolChat directly — {@link #present()} is the single question "could the menu be built", and it
 * is asked before the button is offered rather than when it is pressed.
 *
 * <p><b>Reached reflectively, and that is a decision rather than laziness.</b> Compiling against the
 * mod would mean adding a Fabric mod and its Fabric dependencies to a NeoForge build to type-check a
 * constructor and one interface method — and it would not make the call any more likely to work,
 * because a compile-time reference is linked by this mod's own class loader while SymbolChat's
 * classes are loaded by whichever loader Connector and the Fabric loader ended up using.
 * {@link #find} asks every loader that could hold them. Reflection is not a shortcut here; it is the
 * only mechanism that survives not knowing which loader has the mod.
 *
 * <p><b>Checked against the mod's source, not guessed.</b> In the 1.21.1 build (1.2.8) the panel is
 * {@code SymbolSelectionPanel}, its only public constructor is {@code (int x, int y, int height,
 * SymbolInsertable)} — exactly what {@link #panel} calls — and it descends from Minecraft's
 * {@code AbstractWidget} through SymbolChat's own container widget, which is what lets a screen hold
 * it like any other widget. {@code SymbolInsertable} in that build declares only
 * {@code insertSymbol(String)}; later builds add {@code focusTextbox()}, which the bridge below
 * answers with nothing — correct either way, because the symbol goes into the field the operator was
 * already in and there is no separate text box to focus.
 */
@OnlyIn(Dist.CLIENT)
public final class SymbolChatPanel {

    /** The panel class, from the mod's own source: it has a public four-argument constructor. */
    private static final String PANEL = "net.replaceitem.symbolchat.gui.SymbolSelectionPanel";

    /** The interface the panel inserts through, which is what makes this integration possible. */
    private static final String INSERTABLE = "net.replaceitem.symbolchat.SymbolInsertable";

    private SymbolChatPanel() {}

    /**
     * {@return true when the symbol menu could be offered}
     *
     * <p>Asked of the class rather than of the mod list, because the class is what the menu is built
     * from: an id in the mod list with no reachable class would offer a button that does nothing.
     */
    public static boolean present() {
        return find(PANEL) != null;
    }

    /**
     * {@return SymbolChat's panel, or null when it is not installed or has changed shape}
     *
     * <p>Null is the ordinary answer on a client without the mod, and the caller treats it as "no
     * button" rather than as a failure. A version of SymbolChat whose constructor differs answers null
     * as well, which is the honest thing for a reflective call to do: the menu is a convenience, and a
     * convenience that throws would take the editor with it.
     *
     * @param insert what to do with a symbol the operator picks
     */
    public static AbstractWidget panel(int x, int y, int height, Consumer<String> insert) {
        Class<?> insertable = find(INSERTABLE);
        if (insertable == null) return null;
        Class<?> panel = find(PANEL);
        if (panel == null) return null;

        try {
            Object bridge = Proxy.newProxyInstance(insertable.getClassLoader(), new Class<?>[]{insertable},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            // The panel may compare, print or hash the bridge; a proxy that answered
                            // those with nothing would throw inside SymbolChat rather than here.
                            return switch (method.getName()) {
                                case "equals" -> proxy == args[0];
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "toString" -> "Background Utils symbol bridge";
                                default -> null;
                            };
                        }
                        if (method.getName().equals("insertSymbol") && args != null && args.length == 1) {
                            insert.accept(args[0] == null ? "" : String.valueOf(args[0]));
                        }
                        return null;
                    });

            Object built = panel.getConstructor(int.class, int.class, int.class, insertable)
                    .newInstance(x, y, height, bridge);
            // It is added to this screen as an ordinary widget, so it has to be one. The check is what
            // makes the reflection safe to hand to a screen rather than a guess at its supertype.
            return built instanceof AbstractWidget widget ? widget : null;
        } catch (Throwable changedOrAbsent) {
            return null;
        }
    }

    /**
     * Makes a panel visible, if it is not already.
     *
     * <p>SymbolChat decides for itself whether its panel starts open — it has a setting for keeping it
     * open, and its default is closed — so this asks rather than assumes, and only ever turns it on.
     */
    public static void show(AbstractWidget panel) {
        turn(panel, true);
    }

    /**
     * Puts a panel away, if it is not already.
     *
     * <p>This and {@link #show} are what make one button open the menu and close it again, which is what a
     * button on a menu is expected to do.
     */
    public static void hide(AbstractWidget panel) {
        turn(panel, false);
    }

    /**
     * Turns a panel on or off, whichever it is not.
     *
     * <p>Both ends go through the panel's own {@code toggleVisible} rather than through a field or a
     * setter, because that method is what keeps the panel's own state in step with the rest of the mod: it
     * is what writes SymbolChat's remembered "the panel was open" setting.
     *
     * @param wanted the state being asked for, so that asking for the one it is already in does nothing
     */
    private static void turn(AbstractWidget panel, boolean wanted) {
        if (panel == null) return;
        try {
            Object visible = panel.getClass().getMethod("isVisible").invoke(panel);
            if (Boolean.TRUE.equals(visible) != wanted) {
                panel.getClass().getMethod("toggleVisible").invoke(panel);
            }
        } catch (Throwable changedOrAbsent) {
            // Nothing to do: the panel is there and stays as it is.
        }
    }

    /**
     * {@return the first loader that has the named class, or null when none does}
     *
     * <p>Three loaders are asked, in the order that is cheapest to be sure about. This mod's own
     * loader answers when Connector loads the Fabric mods on the game layer, which is also the case
     * where the lookup is nothing more than an ordinary one. The thread's context loader is asked next,
     * because switching it is how a mod loader runs another loader's code. The Fabric launcher is asked
     * last: its target loader is the one Fabric loads mod classes with, so it is the answer when the
     * Fabric mods really are on a layer of their own.
     */
    private static Class<?> find(String name) {
        for (ClassLoader loader : loaders()) {
            if (loader == null) continue;
            try {
                return Class.forName(name, false, loader);
            } catch (Throwable absent) {
                // Ask the next loader.
            }
        }
        return null;
    }

    private static List<ClassLoader> loaders() {
        List<ClassLoader> loaders = new ArrayList<>(3);
        loaders.add(SymbolChatPanel.class.getClassLoader());
        loaders.add(Thread.currentThread().getContextClassLoader());
        loaders.add(fabricTargetLoader());
        return loaders;
    }

    /**
     * {@return the loader Fabric loads mod classes with, or null when no Fabric loader is installed}
     *
     * <p>Named as strings because these are Fabric loader implementation classes, not API. They are
     * present exactly when SymbolChat can be, since the loader is what loads it.
     */
    private static ClassLoader fabricTargetLoader() {
        try {
            String base = "net.fabricmc.loader.impl.launch.FabricLauncherBase";
            Object launcher = Class.forName(base).getMethod("getLauncher").invoke(null);
            String api = "net.fabricmc.loader.impl.launch.FabricLauncher";
            Object loader = Class.forName(api).getMethod("getTargetClassLoader").invoke(launcher);
            return loader instanceof ClassLoader found ? found : null;
        } catch (Throwable absent) {
            return null;
        }
    }
}
