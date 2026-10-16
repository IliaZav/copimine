package me.copimine.client;

import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/** Cosmetic geometry only; all endpoints, lifetime and ownership come from the server. */
public final class RitualBeamMesh {
    private static final int FILAMENT_SEGMENTS = 24;
    public enum Layer { GLOW, BODY, CORE, FLOW }
    public record Vertex(Vec3d position, float u, float v) { }
    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d, Layer layer) { }
    private RitualBeamMesh() { }

    public static List<Quad> quads(Vec3d from, Vec3d to, float width, long elapsedMillis) {
        if (!finite(from) || !finite(to) || !Float.isFinite(width) || width < .02F || width > .45F) {
            return List.of();
        }
        Vec3d delta = to.subtract(from);
        double length = delta.length();
        if (!Double.isFinite(length) || length < .01D || length > 64.0D) return List.of();
        Vec3d axis = delta.multiply(1.0D / length);
        Vec3d reference = Math.abs(axis.y) < .92D ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        Vec3d side = axis.crossProduct(reference).normalize();
        Vec3d other = axis.crossProduct(side).normalize();
        double seconds = Math.floorMod(elapsedMillis, 60_000L) / 1000.0D;
        float scroll = (float) (-seconds * .8D);
        var result = new ArrayList<Quad>(112);
        for (Layer layer : new Layer[]{Layer.GLOW, Layer.BODY, Layer.CORE}) {
            double radius = width * switch (layer) { case GLOW -> 1.0D; case BODY -> .62D; default -> .22D; };
            strip(result, from, to, side.multiply(radius), 0, length, scroll, layer);
            strip(result, from, to, other.multiply(radius), 0, length, scroll, layer);
        }
        for (int strand = 0; strand < 2; strand++) {
            for (int segment = 0; segment < FILAMENT_SEGMENTS; segment++) {
                double a = segment * length / FILAMENT_SEGMENTS;
                double b = (segment + 1) * length / FILAMENT_SEGMENTS;
                double angleA = a * 2.8D - seconds * 4.0D + strand * Math.PI;
                double angleB = b * 2.8D - seconds * 4.0D + strand * Math.PI;
                Vec3d first = from.add(axis.multiply(a)).add(side.multiply(Math.cos(angleA) * width * .86D))
                        .add(other.multiply(Math.sin(angleA) * width * .86D));
                Vec3d second = from.add(axis.multiply(b)).add(side.multiply(Math.cos(angleB) * width * .86D))
                        .add(other.multiply(Math.sin(angleB) * width * .86D));
                strip(result, first, second, side.multiply(width * .06D), a, b, scroll, Layer.FLOW);
            }
        }
        double phase = Math.floorMod(elapsedMillis, 1800L) / 1800.0D;
        double center = phase * length;
        double a = Math.max(0, center - .4D), b = Math.min(length, center + .4D);
        strip(result, from.add(axis.multiply(a)), from.add(axis.multiply(b)), side.multiply(width * .48D),
                a, b, scroll, Layer.FLOW);
        strip(result, from.add(axis.multiply(a)), from.add(axis.multiply(b)), other.multiply(width * .48D),
                a, b, scroll, Layer.FLOW);
        return result;
    }

    private static void strip(List<Quad> out, Vec3d from, Vec3d to, Vec3d side,
                              double a, double b, float scroll, Layer layer) {
        Vertex v1 = new Vertex(from.subtract(side), 0, (float) (a / 1.5D) + scroll);
        Vertex v2 = new Vertex(from.add(side), 1, (float) (a / 1.5D) + scroll);
        Vertex v3 = new Vertex(to.add(side), 1, (float) (b / 1.5D) + scroll);
        Vertex v4 = new Vertex(to.subtract(side), 0, (float) (b / 1.5D) + scroll);
        out.add(new Quad(v1, v2, v3, v4, layer));
        out.add(new Quad(v4, v3, v2, v1, layer));
    }

    private static boolean finite(Vec3d point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
}
