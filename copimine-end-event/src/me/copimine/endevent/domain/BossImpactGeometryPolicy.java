package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /**
     * N23-P1-11: one authored world-space shape per phase-transition signature.
     *
     * <p>Every Guardian phase used to open on the same {@link #sample PHASE}
     * geometry, so six different rules of the fight drew the identical ring.
     * Each signature named by
     * {@code BossPhaseTransitionCinematicPolicy} maps here to its own rig:
     * a rising helix (ascension), a forward claw pair (stalker), jagged ground
     * cracks (fracture), ground vents (ember), an expanding shock disc with a
     * chest burst (roar), and a contracting seal (last-stand). An unknown
     * signature draws nothing rather than silently reusing a generic ring.</p>
     */
    public static List<Point> phaseTransition(String signature, double radius, double progress) {
        if (signature == null || signature.isBlank() || !Double.isFinite(radius) || radius <= 0
                || !Double.isFinite(progress)) return List.of();
        double r = Math.min(32, radius);
        double t = Math.max(0, Math.min(1, progress));
        List<Point> points = new ArrayList<>(144);
        switch (signature.trim().toLowerCase(Locale.ROOT)) {
            case "ascension" -> {
                for (int strand = 0; strand < 2; strand++) for (int i = 0; i < 22; i++) {
                    double h = i / 21.0;
                    double angle = strand * Math.PI + h * Math.PI * 3 - t * Math.PI * 2;
                    double distance = r * (0.95 - 0.8 * h);
                    points.add(new Point(Math.sin(angle) * distance, 0.15 + h * 5.2,
                            Math.cos(angle) * distance, h < 0.5 ? 0 : 1));
                }
                for (int i = 0; i < 24; i++) {
                    double angle = i * Math.PI / 12;
                    double distance = r * (0.35 + 0.65 * t);
                    points.add(new Point(Math.sin(angle) * distance, 0.08,
                            Math.cos(angle) * distance, 2));
                }
            }
            case "stalker" -> {
                double reach = r * (0.5 + 0.5 * t);
                for (int chevron = 0; chevron < 2; chevron++) {
                    double z = reach - chevron * r * 0.35;
                    for (int i = 0; i < 10; i++) {
                        double s = i / 9.0;
                        double half = (0.9 - 0.9 * s) * r * 0.5;
                        points.add(new Point(half, 0.6 + s * 0.2, z - s * r * 0.45, chevron));
                        points.add(new Point(-half, 0.6 + s * 0.2, z - s * r * 0.45, chevron));
                    }
                }
                for (int i = 0; i < 6; i++) {
                    double s = i / 5.0;
                    points.add(new Point(0.0, 0.7 + s * 0.4, reach + s * r * 0.15, 2));
                }
            }
            case "fracture" -> {
                for (int spoke = 0; spoke < 7; spoke++) {
                    double angle = spoke * Math.PI * 2 / 7 + (spoke % 2 == 0 ? 0.08 : -0.08);
                    for (int step = 1; step <= 6; step++) {
                        double distance = r * step / 6.0 * (0.6 + 0.4 * t);
                        double lateral = Math.sin(step * 1.7 + spoke) * 0.35;
                        double a = angle + lateral * 0.1;
                        points.add(new Point(Math.sin(a) * distance + Math.cos(a) * lateral,
                                0.06 + (step % 2) * 0.05,
                                Math.cos(a) * distance - Math.sin(a) * lateral, step % 2));
                    }
                }
                points.add(new Point(0.0, 0.12, 0.0, 2));
            }
            case "ember" -> {
                for (int i = 0; i < 28; i++) {
                    double angle = i * Math.PI / 14;
                    points.add(new Point(Math.sin(angle) * r * 0.9, 0.08,
                            Math.cos(angle) * r * 0.9, 2));
                }
                for (int vent = 0; vent < 9; vent++) {
                    double angle = vent * Math.PI * 2 / 9 + 0.2;
                    double distance = r * (0.25 + 0.6 * ((vent % 3) / 2.0));
                    for (int i = 0; i < 7; i++) {
                        double h = i / 6.0;
                        double rise = t >= 0.35 ? h : h * (t / 0.35);
                        points.add(new Point(Math.sin(angle) * distance, 0.1 + rise * 3.2,
                                Math.cos(angle) * distance, vent % 2));
                    }
                }
            }
            case "roar" -> {
                for (int ring = 0; ring < 3; ring++) {
                    double distance = r * (0.35 + 0.65 * t) * (1 - ring * 0.16);
                    for (int i = 0; i < 24; i++) {
                        double angle = i * Math.PI / 12 + ring * 0.2;
                        points.add(new Point(Math.sin(angle) * distance, 0.12 + ring * 0.08,
                                Math.cos(angle) * distance, ring % 2));
                    }
                }
                for (int i = 0; i < 10; i++) {
                    double h = i / 9.0;
                    points.add(new Point(0.0, 0.4 + h * 4.5, 0.0, 2));
                }
            }
            case "last-stand" -> {
                for (int ring = 0; ring < 2; ring++) {
                    double distance = r * (1.0 - 0.75 * t) * (ring == 0 ? 1.0 : 0.7);
                    int dir = ring == 0 ? 1 : -1;
                    for (int i = 0; i < 24; i++) {
                        double angle = dir * i * Math.PI / 12 + t * Math.PI * dir;
                        points.add(new Point(Math.sin(angle) * distance, 0.1 + ring * 0.06,
                                Math.cos(angle) * distance, ring));
                    }
                }
                for (int i = 0; i < 14; i++) {
                    double h = i / 13.0;
                    points.add(new Point(0.0, 0.2 + h * 6.0, 0.0, 2));
                }
            }
            default -> {
                // Unknown signature: draw nothing. A generic fallback ring would
                // reintroduce exactly the sameness this method removes.
            }
        }
        return List.copyOf(points);
    }
}
