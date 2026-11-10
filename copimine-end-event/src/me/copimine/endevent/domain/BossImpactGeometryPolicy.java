package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;

/** Local side/up/forward offsets; cosmetics never decide damage or collision. */
public final class BossImpactGeometryPolicy {
    public enum Kind { SWIPE, SLAM, CHEST, PHASE, DEFEAT }
    public record Point(double x, double y, double z, int layer) { }
    private BossImpactGeometryPolicy() { }

    public static List<Point> sample(Kind kind, double radius, double progress) {
        if (kind == null || !Double.isFinite(radius) || radius <= 0
                || !Double.isFinite(progress)) return List.of();
        double r = Math.min(32, radius);
        double t = Math.max(0, Math.min(1, progress));
        List<Point> points = new ArrayList<>(96);
        switch (kind) {
            case SWIPE -> {
                double halfArc = Math.acos(0.1);
                for (int band = 0; band < 2; band++) for (int i = 0; i < 28; i++) {
                    double angle = -halfArc + 2 * halfArc * i / 27.0;
                    double distance = r * (band == 0 ? 1 : 0.76);
                    points.add(new Point(Math.sin(angle) * distance,
                            1.0 + 0.35 * Math.sin(angle + t * 0.8),
                            Math.cos(angle) * distance, band));
                }
            }
            case SLAM -> {
                for (int band = 0; band < 2; band++) for (int i = 0; i < 32; i++) {
                    double angle = i * Math.PI / 16 + band * 0.13;
                    double distance = r * (band == 0 ? 1 : 0.56 + t * 0.16);
                    points.add(new Point(Math.sin(angle) * distance,
                            0.08 + band * 0.15 + t * 0.48, Math.cos(angle) * distance, band));
                }
                for (int spoke = 0; spoke < 8; spoke++) for (int step = 1; step <= 3; step++) {
                    double angle = spoke * Math.PI / 4;
                    double distance = r * step / 3.0;
                    points.add(new Point(Math.sin(angle) * distance, 0.10 + t * 0.18,
                            Math.cos(angle) * distance, 2));
                }
            }
            case CHEST -> {
                for (int band = 0; band < 2; band++) for (int i = 0; i < 24; i++) {
                    double angle = i * Math.PI / 12 + t * Math.PI * 2 + band * Math.PI;
                    double distance = r * (band == 0 ? 1 : 0.42);
                    points.add(new Point(Math.cos(angle) * distance * 0.45, Math.sin(angle) * distance,
                            band * 0.12, band));
                }
            }
            case PHASE -> {
                for (int strand = 0; strand < 3; strand++) for (int i = 0; i < 20; i++) {
                    double height = i / 19.0;
                    double angle = strand * Math.PI * 2 / 3 + height * Math.PI * 2 - t * Math.PI;
                    double distance = r * (0.35 + 0.65 * height) * (1 - t * 0.35);
                    points.add(new Point(Math.sin(angle) * distance, 0.15 + height * 5,
                            Math.cos(angle) * distance, strand % 2));
                }
                for (int i = 0; i < 16; i++) {
                    double angle = i * Math.PI / 8;
                    points.add(new Point(Math.sin(angle) * r, 0.1, Math.cos(angle) * r, 2));
                }
            }
            case DEFEAT -> {
                for (int ring = 0; ring < 3; ring++) for (int i = 0; i < 24; i++) {
                    double angle = i * Math.PI / 12 + ring * 0.35 + t * 0.7;
                    double distance = r * (0.35 + ring * 0.3) * (0.55 + t * 0.45);
                    points.add(new Point(Math.sin(angle) * distance,
                            0.2 + ring * 1.4 + Math.sin(angle * 3) * 0.15,
                            Math.cos(angle) * distance, ring));
                }
            }
        }
        return List.copyOf(points);
    }
}
