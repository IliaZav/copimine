package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic bounded geometry for End Rift boss set pieces.
 */
public final class BossArenaSetPiecePolicy {
    private BossArenaSetPiecePolicy() {
    }

    public static Frame frame(Scene scene, Bounds bounds, Point core, int elapsedTicks) {
        if (scene == null || bounds == null || core == null) {
            throw new IllegalArgumentException("scene, bounds and core are required");
        }
        int ticks = Math.max(0, elapsedTicks);
        return switch (scene) {
            case FINAL_DRAIN -> buildFinalDrain(bounds, core, ticks);
            case FINAL_RITUAL -> buildFinalRitual(bounds, core, ticks);
            case FINAL_WAVE -> buildFinalWave(bounds, core, ticks);
            case BOSS_FINISH -> buildBossFinish(bounds, core, ticks);
        };
    }

    private static Frame buildFinalDrain(Bounds bounds, Point core, int ticks) {
        int pulse = ticks / 20;
        List<Ring> rings = List.of(
                ring("drain-outer", bounds, core, 3 + pulse % 2, 10),
                ring("drain-middle", bounds, core, 2, 8));
        List<Pillar> pillars = List.of(
                pillar("drain-north", bounds, core, 3, 0, 2),
                pillar("drain-south", bounds, core, -3, 0, 2),
                pillar("drain-east", bounds, core, 0, 3, 2),
                pillar("drain-west", bounds, core, 0, -3, 2));
        List<Cell> cells = List.of(
                cell("drain-core", bounds, core, 0, 0),
                cell("drain-arc", bounds, core, 1, 1));
        return new Frame(Scene.FINAL_DRAIN, rings, pillars, cells, false);
    }

    private static Frame buildFinalRitual(Bounds bounds, Point core, int ticks) {
        int pulse = ticks / 30;
        List<Ring> rings = List.of(
                ring("ritual-inner", bounds, core, 4 + pulse % 2, 12),
                ring("ritual-outer", bounds, core, 6, 16));
        List<Pillar> pillars = List.of(
                pillar("ritual-north", bounds, core, 5, 0, 3),
                pillar("ritual-south", bounds, core, -5, 0, 3),
                pillar("ritual-east", bounds, core, 0, 5, 3),
                pillar("ritual-west", bounds, core, 0, -5, 3));
        List<Cell> cells = List.of(
                cell("ritual-safe-1", bounds, core, 2, 2),
                cell("ritual-safe-2", bounds, core, -2, 2),
                cell("ritual-safe-3", bounds, core, 2, -2),
                cell("ritual-safe-4", bounds, core, -2, -2));
        return new Frame(Scene.FINAL_RITUAL, rings, pillars, cells, false);
    }

    private static Frame buildFinalWave(Bounds bounds, Point core, int ticks) {
        int pulse = ticks / 10;
        List<Ring> rings = List.of(
                ring("wave-outer", bounds, core, 5 + pulse % 2, 16),
                ring("wave-middle", bounds, core, 3, 12),
                ring("wave-inner", bounds, core, 2, 8));
        List<Pillar> pillars = List.of(
                pillar("wave-north", bounds, core, 4, 0, 2),
                pillar("wave-south", bounds, core, -4, 0, 2),
                pillar("wave-east", bounds, core, 0, 4, 2));
        List<Cell> cells = List.of(
                cell("wave-cell-1", bounds, core, 1, 0),
                cell("wave-cell-2", bounds, core, -1, 0),
                cell("wave-cell-3", bounds, core, 0, 1),
                cell("wave-cell-4", bounds, core, 0, -1));
        return new Frame(Scene.FINAL_WAVE, rings, pillars, cells, false);
    }

    private static Frame buildBossFinish(Bounds bounds, Point core, int ticks) {
        int pulse = ticks / 15;
        List<Ring> rings = List.of(
                ring("finish-collapse", bounds, core, 3 + pulse % 3, 20));
        List<Pillar> pillars = List.of(
                pillar("finish-north", bounds, core, 2, 0, 1),
                pillar("finish-south", bounds, core, -2, 0, 1),
                pillar("finish-east", bounds, core, 0, 2, 1),
                pillar("finish-west", bounds, core, 0, -2, 1));
        List<Cell> cells = List.of(
                cell("finish-core", bounds, core, 0, 0),
                cell("finish-signal", bounds, core, 1, 1));
        return new Frame(Scene.BOSS_FINISH, rings, pillars, cells, true);
    }

    private static Ring ring(String id, Bounds bounds, Point core, int radius, int points) {
        return new Ring(id, circle(bounds, core, radius, points, 0));
    }

    private static Pillar pillar(String id, Bounds bounds, Point core, int dx, int dz, int height) {
        List<Point> points = new ArrayList<>();
        for (int y = 0; y <= height; y++) {
            points.add(clamp(bounds, core.x() + dx, core.y() + y, core.z() + dz));
        }
        return new Pillar(id, points);
    }

    private static Cell cell(String id, Bounds bounds, Point core, int dx, int dz) {
        return new Cell(id, List.of(clamp(bounds, core.x() + dx, core.y(), core.z() + dz)));
    }

    private static List<Point> circle(Bounds bounds, Point core, int radius, int points, int yOffset) {
        List<Point> result = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D * i) / points;
            int x = core.x() + (int) Math.round(Math.cos(angle) * radius);
            int z = core.z() + (int) Math.round(Math.sin(angle) * radius);
            result.add(clamp(bounds, x, core.y() + yOffset, z));
        }
        return result;
    }

    private static Point clamp(Bounds bounds, int x, int y, int z) {
        return new Point(
                clamp(x, bounds.minX(), bounds.maxX()),
                clamp(y, bounds.minY(), bounds.maxY()),
                clamp(z, bounds.minZ(), bounds.maxZ()));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum Scene {
        FINAL_DRAIN,
        FINAL_RITUAL,
        FINAL_WAVE,
        BOSS_FINISH
    }

    public record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("invalid bounds");
            }
        }
    }

    public record Point(int x, int y, int z) {
    }

    public record Ring(String id, List<Point> points) {
        public Ring {
            id = normalize(id);
            points = List.copyOf(points == null ? List.of() : points);
            if (id.isBlank() || points.isEmpty()) {
                throw new IllegalArgumentException("invalid ring");
            }
        }
    }

    public record Pillar(String id, List<Point> points) {
        public Pillar {
            id = normalize(id);
            points = List.copyOf(points == null ? List.of() : points);
            if (id.isBlank() || points.isEmpty()) {
                throw new IllegalArgumentException("invalid pillar");
            }
        }
    }

    public record Cell(String id, List<Point> points) {
        public Cell {
            id = normalize(id);
            points = List.copyOf(points == null ? List.of() : points);
            if (id.isBlank() || points.isEmpty()) {
                throw new IllegalArgumentException("invalid cell");
            }
        }
    }

    public record Frame(Scene scene, List<Ring> rings, List<Pillar> pillars, List<Cell> cells, boolean finished) {
        public Frame {
            scene = scene;
            rings = List.copyOf(rings == null ? List.of() : rings);
            pillars = List.copyOf(pillars == null ? List.of() : pillars);
            cells = List.copyOf(cells == null ? List.of() : cells);
            if (scene == null) {
                throw new IllegalArgumentException("scene is required");
            }
        }

        public boolean allPointsInside(Bounds bounds) {
            if (bounds == null) {
                return false;
            }
            return rings.stream().flatMap(ring -> ring.points().stream())
                    .allMatch(point -> inside(bounds, point))
                    && pillars.stream().flatMap(pillar -> pillar.points().stream())
                    .allMatch(point -> inside(bounds, point))
                    && cells.stream().flatMap(cell -> cell.points().stream())
                    .allMatch(point -> inside(bounds, point));
        }

        private static boolean inside(Bounds bounds, Point point) {
            return point.x() >= bounds.minX() && point.x() <= bounds.maxX()
                    && point.y() >= bounds.minY() && point.y() <= bounds.maxY()
                    && point.z() >= bounds.minZ() && point.z() <= bounds.maxZ();
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
