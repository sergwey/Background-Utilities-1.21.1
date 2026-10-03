package net.xlebupaksa.backutils.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.xlebupaksa.backutils.network.AdminLogPayload;

import java.util.List;

/**
 * The unfiltered log as last sent by the server, held client-side so the menu can rebuild its list
 * without asking again, and revisioned so the screen knows when it has gone stale.
 */
@OnlyIn(Dist.CLIENT)
public final class AdminMenuCache {

    private static volatile List<AdminLogPayload.Row> rows = List.of();
    private static volatile int revision;

    private AdminMenuCache() {}

    public static void set(List<AdminLogPayload.Row> newRows) {
        rows = List.copyOf(newRows);
        revision++;
    }

    public static List<AdminLogPayload.Row> rows() {
        return rows;
    }

    public static int revision() {
        return revision;
    }

    public static void clear() {
        rows = List.of();
        revision++;
    }

    public static void refresh() {
        if (Minecraft.getInstance().player == null) return;
        net.xlebupaksa.backutils.network.AdminNetwork.requestLog();
    }
}
