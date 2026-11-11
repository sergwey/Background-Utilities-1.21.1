package net.xlebupaksa.backutils.log;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives the hidden rolls whose delay is up.
 *
 * <p>Checked once a second rather than on a task per roll: the deadlines live in the database, so
 * there is nothing to schedule and nothing to lose when the server stops — the first check after a
 * restart reveals everything that came due while it was down.
 */
@SuppressWarnings("unused") // entry point: the game bus calls the event
public final class HiddenRollTicker {

    /** Twenty ticks: a second is close enough to the configured seconds and cheap to check. */
    private static final int CHECK_INTERVAL_TICKS = 20;

    private int ticks;

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < CHECK_INTERVAL_TICKS) return;
        ticks = 0;
        Rolls.revealDue(event.getServer());
    }
}
