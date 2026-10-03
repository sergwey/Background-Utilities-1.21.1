package net.xlebupaksa.backutils.data;

/**
 * One music zone: an area where a looping sound plays.
 *
 * <p>Zones are <b>anonymous</b> — created, played and removed by area rather than by name, so
 * there is no naming step between an operator wanting music in a room and hearing it there. The
 * database id exists only so the client can tell one zone's loop from another's.
 *
 * @param expiresAtMillis when the zone ends, or {@link #PERMANENT}
 */
public record MusicZone(
        long id,
        String dimension,
        boolean radius,
        double x1, double y1, double z1,
        double x2, double y2, double z2,
        double radiusValue,
        String sound,
        String source,
        float volume,
        float pitch,
        long expiresAtMillis
) {

    /** Sentinel for "this zone never expires on its own". */
    public static final long PERMANENT = 0L;

    public boolean permanent() {
        return expiresAtMillis <= 0L;
    }

    public boolean expired(long nowMillis) {
        return !permanent() && nowMillis >= expiresAtMillis;
    }

    /**
     * {@return true when the given position is inside this zone}
     *
     * <p>Dimension is checked first because the same x/y/z in the Nether and the Overworld are
     * nowhere near each other.
     */
    public boolean contains(String dimensionKey, double x, double y, double z) {
        if (dimensionKey == null || !dimensionKey.equals(dimension)) return false;

        if (radius) {
            double dx = x - x1;
            double dy = y - y1;
            double dz = z - z1;
            return dx * dx + dy * dy + dz * dz <= radiusValue * radiusValue;
        }

        // Corners are stored as given, so min/max rather than assuming an order.
        return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
    }

    /** {@return a short description, for command output} */
    public String describe() {
        String where = radius
                ? "radius " + trim(radiusValue) + " at " + trim(x1) + " " + trim(y1) + " " + trim(z1)
                : "box " + trim(x1) + " " + trim(y1) + " " + trim(z1)
                        + " to " + trim(x2) + " " + trim(y2) + " " + trim(z2);
        return "#" + id + " " + dimension + " " + where + " -> " + sound + " [" + source + "]";
    }

    private static String trim(double value) {
        return value == Math.floor(value)
                ? String.valueOf((long) value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
