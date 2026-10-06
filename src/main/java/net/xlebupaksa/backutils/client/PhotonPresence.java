package net.xlebupaksa.backutils.client;

import net.neoforged.fml.ModList;

/**
 * Whether Photon is here, asked through the loader rather than by touching a Photon type.
 *
 * <p>Separate from {@link PhotonFx} so that the question can be asked without loading the class that
 * names Photon's executors. That matters on a client without Photon: the executor class refers to
 * types that are not present, and a check that lived inside it would answer the question by failing
 * to load — which is a {@code NoClassDefFoundError} at the worst possible moment rather than a
 * boolean.
 *
 * <p>The answer is cached because it cannot change while the game runs: mods are loaded once, and a
 * preview asks this every frame.
 */
public final class PhotonPresence {

    private static Boolean loaded;

    private PhotonPresence() {}

    /** {@return true when the Photon mod is loaded} */
    public static boolean loaded() {
        if (loaded == null) {
            boolean present;
            try {
                present = ModList.get() != null && ModList.get().isLoaded("photon");
            } catch (Throwable e) {
                // Asked too early or too late in the load to be answered: treat as absent, because
                // the only cost is an effect not being placed.
                present = false;
            }
            loaded = present;
        }
        return loaded;
    }
}
