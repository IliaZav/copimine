package me.copimine.endevent.domain;

import java.util.Collection;
import java.util.function.BiPredicate;

/** Full-body shelter exclusion; the runtime supplies collision checks and owns movement. */
public final class BlackFogShelterMobPolicy {
    private BlackFogShelterMobPolicy() { }
    public record Cell(int x, int z) { }

    public static boolean overlaps(double x, double z, double width, Collection<Cell> cells) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(width) || width <= 0) return true;
        double radius = width * .5 + .25;
        for (Cell cell : cells) {
            if (x + radius > cell.x() && x - radius < cell.x() + 1
                    && z + radius > cell.z() && z - radius < cell.z() + 1) return true;
        }
        return false;
    }

    public static Cell exit(double x, double z, double width, Collection<Cell> cells,
                            BiPredicate<Integer, Integer> walkable) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(width) || width <= 0) return null;
        if (!overlaps(x, z, width, cells)) return null;
        int originX = (int) Math.floor(x), originZ = (int) Math.floor(z);
        for (int radius = 1; radius <= 8; radius++) {
            Cell best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                int cx = originX + dx, cz = originZ + dz;
                if (overlaps(cx + .5, cz + .5, width, cells) || !walkable.test(cx, cz)) continue;
                double distance = Math.pow(cx + .5 - x, 2) + Math.pow(cz + .5 - z, 2);
                if (distance < bestDistance) { bestDistance = distance; best = new Cell(cx, cz); }
            }
            if (best != null) return best;
        }
        return null;
    }
}
