package me.copimine.endevent.domain;

/**
 * V6 §6.1 / §7: the Last Seal reveal may demolish only the cells an approved
 * site plan authorises, and only inside the geometry those cells are allowed to
 * occupy. A concealed corridor must really be inside the configured corridor and
 * strictly above the arena floor; an approved inner wall panel is confined to the
 * outer-hall boundary ring, above the floor and no higher than the hall, and may
 * coexist with a configured corridor (V7 A-01); an outer floor fissure opens only
 * outside the hall at exactly floor level. Anything else is refused.
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
            // V7 A-01: the inner partition panels that reveal the prebuilt
            // round hall are a separate authored mask from the concealed
            // corridor. A legitimate plan carries BOTH: corridor cells inside
            // the corridor box and wall cells on the outer-hall boundary ring.
            // Requiring `corridor == null` here rejected every combined plan, so
            // the reveal could never open the round arena through its approved
            // panels. A wall cell is still confined to the boundary ring, above
            // the floor and no higher than the hall.
            case "wall" -> onOuterHallBoundary(x, z, square) && y > floor && y <= square.maxY();
            case "fissure" -> !square.horizontal(x, z) && y == floor;
            default -> false;
        };
    }

    /**
     * Decides whether a player body must be evacuated while an unfinished reveal
     * is restored (V7 A-02).
     *
     * <p>The broad sweep is a horizontal radius test, so it must never run for a
     * journal whose true vertical volume is unknown (a legacy V1 journal) and
     * must never run for a body outside the arena's own {@code minY..maxY} band.
     * Otherwise a player standing on the surface above an underground complex, or
     * on a stacked neighbouring floor, shares the arena's X/Z and would be
     * teleported into the rescue landing by a horizontal distance alone. A body
     * that actually intersects a restored cell is always displaced, in every
     * journal version, because restoring that cell would otherwise entomb it.
     *
     * @param bodyMinX,bodyMinY,bodyMinZ,bodyMaxX,bodyMaxY,bodyMaxZ the player's
     *                                              bounding box
     * @param feetX,feetY,feetZ                    the player's block-centred
     *                                              location
     * @param changedCells                         the authored cells being
     *                                              restored, as unit boxes
     * @param original                             the arena square
     * @param verticalVolumeKnown                  true only for a V2 journal that
     *                                              persisted the arena's Y bounds
     * @param wasRevealed                          true when at least one cell is
     *                                              currently air (mid-reveal)
     */
    public static boolean recoveryEvacuation(
            double bodyMinX, double bodyMinY, double bodyMinZ,
            double bodyMaxX, double bodyMaxY, double bodyMaxZ,
            double feetX, double feetY, double feetZ,
            java.util.List<Box> changedCells, Box original,
            int centerX, int centerZ, int radius,
            boolean verticalVolumeKnown, boolean wasRevealed) {
        if (original == null || changedCells == null) {
            return false;
        }
        boolean inClosingCell = changedCells.stream().anyMatch(cell ->
                bodyMinX < cell.maxX() + 1.0 && bodyMaxX > cell.minX()
                        && bodyMinY < cell.maxY() + 1.0 && bodyMaxY > cell.minY()
                        && bodyMinZ < cell.maxZ() + 1.0 && bodyMaxZ > cell.minZ());
        double dx = feetX - centerX - .5, dz = feetZ - centerZ - .5;
        boolean withinArenaVolume = verticalVolumeKnown
                && feetY >= original.minY() - 1 && feetY <= original.maxY() + 2;
        boolean broad = verticalVolumeKnown && wasRevealed && withinArenaVolume
                && dx * dx + dz * dz <= (double) radius * radius
                && !original.horizontal((int) Math.floor(feetX), (int) Math.floor(feetZ));
        return inClosingCell || broad;
    }
}
