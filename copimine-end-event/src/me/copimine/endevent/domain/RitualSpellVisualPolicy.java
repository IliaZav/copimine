package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Bounded world geometry for the four accumulating Wave 6 spells. */
public final class RitualSpellVisualPolicy {
    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("finite visual point required");
            }
        }
    }

    public record Segment(Point from, Point to) { }

    private RitualSpellVisualPolicy() { }

    public static List<Segment> ring(double radius, double height, double phase) {
        if (!Double.isFinite(radius) || radius <= 0 || radius > 4.5
                || !Double.isFinite(height) || Math.abs(height) > 4
                || !Double.isFinite(phase)) {
            throw new IllegalArgumentException("bounded rune required");
        }
        List<Point> points = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            double angle = phase + i * Math.PI / 4;
            points.add(new Point(Math.cos(angle) * radius, height, Math.sin(angle) * radius));
        }
        List<Segment> segments = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) segments.add(new Segment(points.get(i), points.get((i + 1) % 8)));
        return List.copyOf(segments);
    }

    /** Two ground runes and four streams moving toward the real well centre. */
    public static List<Segment> gravity(double phase) {
        if (!Double.isFinite(phase)) throw new IllegalArgumentException("finite phase required");
        List<Segment> segments = new ArrayList<>(20);
        segments.addAll(ring(RitualZoneEffectPolicy.RADIUS_BLOCKS, 0.12, phase * 0.08));
        segments.addAll(ring(1.15, 0.22, -phase * 0.35));
        double progress = phase - Math.floor(phase);
        double outer = RitualZoneEffectPolicy.RADIUS_BLOCKS - progress * 2.5;
        double inner = Math.max(0.35, outer - 0.85);
        for (int i = 0; i < 4; i++) {
            double angle = phase * 0.12 + i * Math.PI / 2;
            segments.add(new Segment(new Point(Math.cos(angle) * outer, 0.28, Math.sin(angle) * outer),
                    new Point(Math.cos(angle) * inner, 0.28, Math.sin(angle) * inner)));
        }
        return List.copyOf(segments);
    }

    public static List<Segment> chain(Point start, Point end, double phase) {
        if (start == null || end == null || !Double.isFinite(phase)) {
            throw new IllegalArgumentException("finite chain required");
        }
        double dx = end.x - start.x, dy = end.y - start.y, dz = end.z - start.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.01 || length > 64) throw new IllegalArgumentException("bounded chain required");
        double horizontal = Math.hypot(dx, dz);
        double sideX = horizontal > 0.001 ? -dz / horizontal : 1;
        double sideZ = horizontal > 0.001 ? dx / horizontal : 0;
        List<Segment> segments = new ArrayList<>(6);
        Point previous = start;
        for (int i = 1; i <= 6; i++) {
            double t = i / 6.0;
            double wobble = Math.sin(t * Math.PI) * Math.sin(phase * 3 + t * Math.PI * 4) * 0.18;
            Point next = i == 6 ? end : new Point(start.x + dx * t + sideX * wobble,
                    start.y + dy * t - Math.sin(t * Math.PI) * 0.28, start.z + dz * t + sideZ * wobble);
            segments.add(new Segment(previous, next));
            previous = next;
        }
        return List.copyOf(segments);
    }
}
