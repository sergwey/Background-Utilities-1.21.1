package net.xlebupaksa.backutils.effect;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.xlebupaksa.backutils.BackUtils;
import net.xlebupaksa.backutils.data.EffectData;
import net.xlebupaksa.backutils.data.PlacedEffect;
import net.xlebupaksa.backutils.network.EffectToolNetwork;

import java.util.List;

/**
 * Takes away the effects that have run out of time.
 *
 * <p>The spec's "when to stop the effect" is a lifetime the tool sets, and this is what gives it
 * meaning: an effect placed for five hundred ticks is removed at the end of them, from every client
 * drawing it and from the world the display it hung off was put in. Nothing else would do it — the
 * effect library has no concept of a placement outliving a time, and a lifetime that nothing acts on
 * is a setting that does nothing.
 *
 * <p>The sweep is once a second. An effect ending a second late is not a thing anybody can see, and
 * a sweep that ran every tick would read the whole table sixty times a second for a change that
 * happens once in a while.
 *
 * <p>An effect that ends <em>by itself</em> is the other half of the same thing and is not here: only
 * a client can see that an effect's own timeline has finished, so those are reported by the clients
 * drawing them and settled by {@link EffectToolNetwork}.
 */
public class EffectTicker {

    /**
     * How many ticks between sweeps.
     *
     * <p>Twenty, which is the second the lifetimes are measured in: a lifetime is a number of ticks,
     * so checking once per twenty of them is checking as finely as the setting can mean.
     */
    private static final int SWEEP_INTERVAL_TICKS = 20;

    private int ticks;

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < SWEEP_INTERVAL_TICKS) return;
        ticks = 0;
        sweep(event.getServer());
    }

    /** Removes everything whose time is up, which is what a restart also has to do at its start. */
    public static void sweep(MinecraftServer server) {
        if (server == null || BackUtils.data() == null) return;
        EffectData store = BackUtils.data().effects();
        if (store == null) return;

        List<PlacedEffect> expired = store.expired(System.currentTimeMillis());
        for (PlacedEffect effect : expired) {
            EffectToolNetwork.forget(server, store, effect.id());
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused") // called by the event bus
    public void onServerStopping(ServerStoppingEvent event) {
        ticks = 0;
    }
}
