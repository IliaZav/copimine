package me.copimine.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Deterministic world positions for the client-only Wave 2 phantom presentation. */
final class WaveTwoPhantomLayout {
    static final int TARGET_PHANTOM_COUNT = 32;
    private static final int PHANTOMS_PER_RING = 16;
    private static final double[] RING_RADII = {6.0D, 8.0D, 10.0D, 12.0D};

    private WaveTwoPhantomLayout() { }

    static List<Position> candidatesAround(Position anchor, long seed) {
        if (!finite(anchor)) return List.of();
        double phase = phase(seed);
        List<Position> candidates = new ArrayList<>(PHANTOMS_PER_RING * RING_RADII.length);
        for (int ring = 0; ring < RING_RADII.length; ring++) {
            double radius = RING_RADII[ring];
            double ringOffset = (ring & 1) == 0 ? 0.0D : Math.PI / PHANTOMS_PER_RING;
            for (int index = 0; index < PHANTOMS_PER_RING; index++) {
                double angle = phase + ringOffset + (Math.PI * 2.0D * index / PHANTOMS_PER_RING);
                candidates.add(new Position(
                        anchor.x() + StrictMath.cos(angle) * radius,
                        anchor.y(),
                        anchor.z() + StrictMath.sin(angle) * radius));
            }
        }
        return List.copyOf(candidates);
    }

    /** Resolve candidates to safe standing locations and keep at most 32. */
    static List<Position> selectSafe(List<Position> candidates,
                                     Function<Position, Position> safeResolver) {
        if (candidates == null || candidates.isEmpty() || safeResolver == null) return List.of();
        Set<Position> selected = new LinkedHashSet<>(TARGET_PHANTOM_COUNT);
        for (Position candidate : candidates) {
            if (!finite(candidate)) continue;
            Position resolved = safeResolver.apply(candidate);
            if (finite(resolved)) selected.add(resolved);
            if (selected.size() >= TARGET_PHANTOM_COUNT) break;
        }
        return selected.isEmpty() ? List.of() : List.copyOf(selected);
    }

    static float targetYaw(Position origin, Position target) {
        if (!finite(origin) || !finite(target)) return 0.0F;
        double dx = target.x() - origin.x();
        double dz = target.z() - origin.z();
        if (dx * dx + dz * dz < 1.0E-8D) return 0.0F;
        return (float) StrictMath.atan2(dx, dz);
    }

    /** Keep the phantom's body facing forward and aim only its head at the marked player. */
    static LookPose lookAt(Position phantom, Position markedPlayer) {
        if (!finite(phantom) || !finite(markedPlayer)) return new LookPose(0.0F, 0.0F);
        return new LookPose(0.0F, (float) -Math.toDegrees(targetYaw(phantom, markedPlayer)));
    }

    static boolean finite(Position position) {
        return position != null
                && Double.isFinite(position.x())
                && Double.isFinite(position.y())
                && Double.isFinite(position.z());
    }

    /** Returns the layout index only for the exact server-authored position of an existing phantom. */
    static int indexOfExactPosition(List<Position> positions, Position serverPosition) {
        if (positions == null || !finite(serverPosition)) return -1;
        for (int index = 0; index < positions.size(); index++) {
            Position candidate = positions.get(index);
            if (finite(candidate) && candidate.x() == serverPosition.x()
                    && candidate.y() == serverPosition.y() && candidate.z() == serverPosition.z()) {
                return index;
            }
        }
        return -1;
    }

    static long mixSeed(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double phase(long seed) {
        long mixed = mixSeed(seed);
        double unit = (mixed >>> 11) * 0x1.0p-53;
        return unit * (StrictMath.PI * 2.0D);
    }

    record LookPose(float bodyYawDegrees, float headYawDegrees) { }

    record Position(double x, double y, double z) { }
}
