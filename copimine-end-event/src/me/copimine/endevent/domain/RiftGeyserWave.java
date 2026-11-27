package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Locked ground geometry used by warning, terrain admission, collision and presentation. */
public record RiftGeyserWave(double alongX, double alongZ, double size, List<Section> sections) {
    public record Section(double x, double z, double alongX, double alongZ, double half) {
        public boolean contains(double blockX, double blockZ) {
            double dx = blockX - x, dz = blockZ - z;
            return Math.abs(dx * alongX + dz * alongZ) <= half + 1.0E-8
                    && Math.abs(dz * alongX - dx * alongZ) <= half + 1.0E-8;
        }

        public boolean overlapsTile(int blockX, int blockZ) {
            double dx = blockX + .5 - x, dz = blockZ + .5 - z;
            double extent = Math.abs(alongX) + Math.abs(alongZ);
            return Math.abs(dx) < half * extent + .5 && Math.abs(dz) < half * extent + .5
                    && Math.abs(dx * alongX + dz * alongZ) < half + .5 * extent
                    && Math.abs(dz * alongX - dx * alongZ) < half + .5 * extent;
        }

        public double xAt(double forward, double side) { return x + forward * alongX - side * alongZ; }
        public double zAt(double forward, double side) { return z + forward * alongZ + side * alongX; }
    }

    public RiftGeyserWave {
        sections = List.copyOf(sections);
    }

    public static RiftGeyserWave between(double startX, double startZ, double targetX, double targetZ,
            double sectionSize, double maxDistance) {
        double dx = targetX - startX, dz = targetZ - startZ;
        double distance = Math.hypot(dx, dz);
        if (!Double.isFinite(distance) || !Double.isFinite(sectionSize) || !Double.isFinite(maxDistance)
                || distance < 4.0 || distance > maxDistance || sectionSize < 1.0 || sectionSize > 5.0) return null;
        double forwardX = dx / distance, forwardZ = dz / distance;
        int count = (int) Math.ceil(distance / sectionSize - 1.0E-9);
        if (count > 20) return null;
        var sections = new ArrayList<Section>(count);
        for (int index = 0; index < count; index++) {
            double offset = (index + .5) * sectionSize;
            sections.add(new Section(startX + offset * forwardX, startZ + offset * forwardZ,
                    forwardX, forwardZ, sectionSize / 2));
        }
        return new RiftGeyserWave(forwardX, forwardZ, sectionSize, sections);
    }
}
