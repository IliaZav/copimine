package me.copimine.protocol;

import java.util.ArrayList;
import java.util.List;

/** Shared kinematics for temporary spell rigs; components never authorize damage. */
public record KaguneSpellFrame(int kind, int stage, double progress, double previousProgress,
                               double heading, List<Point> points) {
    public static final int MODEL = 830017, MARKER = 830118;
    public static final int STAB = 0, ERUPTION = 1;
    public static final int WARNING = 0, EMERGING = 1, STRIKING = 2, RETRACTING = 3;
    public static final double SCALE = .65D;
    // Same imported 7.8681-unit rig and length conversion used by the client.
    public static final double HEIGHT = 7.8681D * .62D * SCALE;
    public record Point(double x, double y, double z) { }

    public KaguneSpellFrame {
        if (kind < STAB || kind > ERUPTION || stage < WARNING || stage > RETRACTING
                || !bounded(progress, 0, 1) || !bounded(previousProgress, 0, 1)
                || !bounded(heading, -Math.PI - 1E-6, Math.PI + 1E-6)
                || points == null || points.isEmpty() || points.size() > 5
                || kind == STAB && points.size() != 1) throw new IllegalArgumentException("spell-frame");
        points = List.copyOf(points);
        for (Point point : points) {
            if (point == null || !bounded(point.x, -8, 8) || !bounded(point.y, -3, 3)
                    || !bounded(point.z, -8, 8)) throw new IllegalArgumentException("spell-position");
        }
    }

    private static boolean bounded(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    public double rise(double p) {
        return switch (stage) {
            case WARNING -> 0D;
            case EMERGING -> smooth(p);
            case RETRACTING -> 1D - smooth(p);
            default -> 1D;
        };
    }

    /** A visible backward preparation, forward thrust, then recovery. */
    public double pitch(double p) {
        if (kind != STAB) return 0D;
        return Math.toRadians(switch (stage) {
            case EMERGING -> -22D * smooth(p);
            case STRIKING -> -22D + 96D * smooth(Math.min(1D, p / .6D));
            case RETRACTING -> 74D * (1D - smooth(p));
            default -> 0D;
        });
    }

    public boolean canDamage() { return stage == STRIKING && (kind == ERUPTION || progress >= .25D); }

    /** Swept stem/player AABBs, shared with the exact root transform drawn by the client. */
    public boolean contacts(Point before, Point now, double halfWidth, double height) {
        if (!canDamage()) return false;
        for (Point base : points) for (int step = 0; step <= 8; step++) {
            double t = step / 8D, p = previousProgress + (progress - previousProgress) * t;
            double theta = pitch(p), length = HEIGHT;
            double px = before.x + (now.x - before.x) * t;
            double py = before.y + (now.y - before.y) * t;
            double pz = before.z + (now.z - before.z) * t;
            double rootY = base.y - HEIGHT * (1D - rise(p));
            if (segmentBox(base.x, rootY, base.z,
                    base.x + Math.cos(heading) * Math.sin(theta) * length,
                    rootY + Math.cos(theta) * length,
                    base.z + Math.sin(heading) * Math.sin(theta) * length,
                    px - halfWidth - .28D, py - .15D, pz - halfWidth - .28D,
                    px + halfWidth + .28D, py + height + .15D, pz + halfWidth + .28D)) return true;
        }
        return false;
    }

    private static boolean segmentBox(double ax, double ay, double az, double bx, double by, double bz,
                                      double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double lo = 0D, hi = 1D;
        // No per-contact arrays in the 20-player, five-rig inner loop.
        for (int axis = 0; axis < 3; axis++) {
            double a = axis == 0 ? ax : axis == 1 ? ay : az;
            double d = axis == 0 ? bx-ax : axis == 1 ? by-ay : bz-az;
            double min = axis == 0 ? minX : axis == 1 ? minY : minZ;
            double max = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
            if (Math.abs(d) < 1E-9D) { if (a < min || a > max) return false; }
            else {
                double enter = (min - a) / d, exit = (max - a) / d;
                lo = Math.max(lo, Math.min(enter, exit)); hi = Math.min(hi, Math.max(enter, exit));
                if (lo > hi) return false;
            }
        }
        return true;
    }

    private static double smooth(double value) {
        double t = Math.max(0D, Math.min(1D, value)); return t * t * (3D - 2D * t);
    }

    public List<Float> encode() {
        List<Float> values = new ArrayList<>();
        values.add((float) MODEL); values.add((float) MARKER); values.add((float) kind);
        values.add((float) stage); values.add((float) progress); values.add((float) previousProgress);
        values.add((float) heading); values.add((float) points.size());
        for (Point point : points) { values.add((float) point.x); values.add((float) point.y); values.add((float) point.z); }
        return List.copyOf(values);
    }

    public static KaguneSpellFrame decode(List<Float> values) {
        try {
            if (values == null || values.size() < 11 || values.get(0) != MODEL || values.get(1) != MARKER) return null;
            int count = integer(values.get(7));
            if (count < 1 || count > 5 || values.size() != 8 + count * 3) return null;
            List<Point> points = new ArrayList<>();
            for (int i = 0; i < count; i++) points.add(new Point(values.get(8+i*3), values.get(9+i*3), values.get(10+i*3)));
            return new KaguneSpellFrame(integer(values.get(2)), integer(values.get(3)),
                    values.get(4), values.get(5), values.get(6), points);
        } catch (RuntimeException invalid) { return null; }
    }

    private static int integer(float value) {
        if (!Float.isFinite(value) || value != Math.rint(value)) throw new IllegalArgumentException("integer");
        return (int) value;
    }
}
