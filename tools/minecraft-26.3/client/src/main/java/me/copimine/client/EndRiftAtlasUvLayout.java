package me.copimine.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.geom.ModelPart;

/** Fits solid mesh faces into painted islands of the unchanged authored event atlases. */
final class EndRiftAtlasUvLayout {
    private static final Map<String, Atlas> ATLASES = new ConcurrentHashMap<>();
    private record Rect(int x, int y, int width, int height) { }
    private record Face(int x, int y, int width, int height) { }
    private EndRiftAtlasUvLayout() { }

    static void fit(ModelPart root, String texture) {
        Atlas atlas = ATLASES.computeIfAbsent(texture, Atlas::load);
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            // Rune planes and sigils intentionally retain transparency; solid bodies do not.
            if (path.contains("rune") || path.contains("sigil")) return;
            for (int i = 0; i < cube.polygons.length; i++) {
                var polygon = cube.polygons[i];
                float u0 = Float.POSITIVE_INFINITY, v0 = u0, u1 = Float.NEGATIVE_INFINITY, v1 = u1;
                for (var vertex : polygon.vertices()) {
                    u0 = Math.min(u0, vertex.u()); u1 = Math.max(u1, vertex.u());
                    v0 = Math.min(v0, vertex.v()); v1 = Math.max(v1, vertex.v());
                }
                if (u1 <= u0 || v1 <= v0) continue;
                Face face = new Face((int) Math.floor(u0 * atlas.width), (int) Math.floor(v0 * atlas.height),
                        Math.max(1, Math.round((u1 - u0) * atlas.width)), Math.max(1, Math.round((v1 - v0) * atlas.height)));
                if (atlas.painted(face.x, face.y, face.width, face.height) >= face.width * face.height * .95) continue;
                Rect patch = atlas.patches.computeIfAbsent(face, atlas::patch);
                var vertices = new ModelPart.Vertex[polygon.vertices().length];
                for (int j = 0; j < vertices.length; j++) {
                    var vertex = polygon.vertices()[j];
                    float u = (patch.x + .5F + (vertex.u() - u0) / (u1 - u0) * (patch.width - 1)) / atlas.width;
                    float v = (patch.y + .5F + (vertex.v() - v0) / (v1 - v0) * (patch.height - 1)) / atlas.height;
                    vertices[j] = vertex.remap(u, v);
                }
                cube.polygons[i] = new ModelPart.Polygon(vertices, polygon.normal());
            }
        });
    }

    private static final class Atlas {
        final int width, height;
        final int[] prefix;
        final Map<Face, Rect> patches = new ConcurrentHashMap<>();
        Atlas(NativeImage image) {
            width = image.getWidth(); height = image.getHeight(); prefix = new int[(width + 1) * (height + 1)];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int at = (y + 1) * (width + 1) + x + 1;
                prefix[at] = ((image.getPixel(x, y) >>> 24) >= 128 ? 1 : 0)
                        + prefix[at - 1] + prefix[at - width - 1] - prefix[at - width - 2];
            }
        }
        static Atlas load(String name) {
            String path = "/assets/copimineclient/textures/entity/" + name;
            try (var stream = EndRiftAtlasUvLayout.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing event atlas: " + name);
                try (NativeImage image = NativeImage.read(stream)) { return new Atlas(image); }
            } catch (java.io.IOException error) { throw new IllegalStateException("Invalid event atlas: " + name, error); }
        }
        int painted(int x, int y, int w, int h) {
            int x0 = Math.max(0, Math.min(width, x)), y0 = Math.max(0, Math.min(height, y));
            int x1 = Math.max(0, Math.min(width, x + w)), y1 = Math.max(0, Math.min(height, y + h));
            return prefix[y1 * (width + 1) + x1] - prefix[y0 * (width + 1) + x1]
                    - prefix[y1 * (width + 1) + x0] + prefix[y0 * (width + 1) + x0];
        }
        Rect patch(Face face) {
            for (double scale = 1; scale >= .015; scale *= .5) {
                int w = Math.max(2, (int) Math.ceil(face.width * scale));
                int h = Math.max(2, (int) Math.ceil(face.height * scale));
                Rect best = null; double score = Double.POSITIVE_INFINITY;
                for (int y = 0; y + h <= height; y++) for (int x = 0; x + w <= width; x++) {
                    if (painted(x, y, w, h) != w * h) continue;
                    double distance = Math.pow(x + w * .5 - face.x - face.width * .5, 2)
                            + Math.pow(y + h * .5 - face.y - face.height * .5, 2);
                    if (distance < score) { score = distance; best = new Rect(x, y, w, h); }
                }
                if (best != null) return best;
            }
            throw new IllegalStateException("Event atlas has no painted material island");
        }
    }
}
