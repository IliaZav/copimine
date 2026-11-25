package me.copimine.endevent.domain;

/**
 * V6 §6.1 / §7: the Last Seal reveal may demolish only the cells an approved
 * site plan authorises, and only inside the geometry those cells are allowed to
 * occupy. A concealed corridor must really be inside the configured corridor and
 * strictly above the arena floor; the collapsing outer wall exists only when no
 * concealed corridor is configured, on the outer-hall boundary ring, above the
 * floor and no higher than the hall; an outer floor fissure opens only outside
 * the hall at exactly floor level. Anything else is refused.
 *
 * <p>This is kept as a pure, Bukkit-free decision so a regression cannot quietly
 * start removing a real block outside the approved corridor: the live reveal
 * delegates its per-cell geometry test here and this class is unit tested.
 */
public final class LastSealStructuralGeometryPolicy {

    private LastSealStructuralGeometryPolicy() {
    }

    /** Inclusive axis-aligned block box; {@code minY} is only used by containment. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean horizontal(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public boolean contains(int x, int y, int z) {
            return horizontal(x, z) && y >= minY && y <= maxY;
        }
    }

    /** True only on the horizontal outer-hall boundary ring (never the interior). */
    public static boolean onOuterHallBoundary(int x, int z, Box square) {
        if (square == null || !square.horizontal(x, z)) {
            return false;
        }
        return x == square.minX() || x == square.maxX()
                || z == square.minZ() || z == square.maxZ();
    }

    /**
     * Approves one authored cell kind for demolition at {@code (x, y, z)}.
     *
     * @param corridor the configured concealed corridor, or {@code null} when no
     *                 concealed corridor is configured
     */
    public static boolean cellKindApproved(String kind, int x, int y, int z,
                                           Box square, int floor, Box corridor) {
        if (kind == null || square == null) {
            return false;
        }
        return switch (kind) {
            case "corridor" -> corridor != null && corridor.contains(x, y, z) && y > floor;
            case "wall" -> corridor == null && onOuterHallBoundary(x, z, square)
                    && y > floor && y <= square.maxY();
            case "fissure" -> !square.horizontal(x, z) && y == floor;
            default -> false;
        };
    }
}
