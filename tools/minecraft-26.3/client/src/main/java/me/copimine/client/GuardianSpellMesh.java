package me.copimine.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable, bounded meshes for the standalone spell atlas; no terrain-atlas stitching. */
public final class GuardianSpellMesh {
    private static final String[] NAMES = {"void_core", "inferno_core", "rift_lance", "echo_shard",
            "final_seal", "chest_iris", "kagune_splinter", "fault_fragment"};
    private static final Map<String, List<Vertex>> MESHES = loadAll();
    public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) { }
    private GuardianSpellMesh() { }

    public static List<Vertex> vertices(String name) { return MESHES.getOrDefault(name, List.of()); }
    public static java.util.Set<String> names() { return MESHES.keySet(); }

    private static Map<String, List<Vertex>> loadAll() {
        Map<String, List<Vertex>> meshes = new LinkedHashMap<>();
        for (String name : NAMES) {
            String path = "/assets/copimineclient/geometry/guardian_spells/" + name + ".json";
            try (var stream = GuardianSpellMesh.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing spell mesh: " + path);
                meshes.put(name, parse(JsonParser.parseReader(new InputStreamReader(stream,
                        StandardCharsets.UTF_8)).getAsJsonObject()));
            } catch (java.io.IOException error) { throw new IllegalStateException(path, error); }
        }
        return Map.copyOf(meshes);
    }

    static List<Vertex> parse(JsonObject model) {
        JsonArray elements = model.getAsJsonArray("elements");
        if (elements == null || elements.isEmpty() || elements.size() > 32)
            throw new IllegalArgumentException("Spell mesh needs 1..32 cuboids");
        List<Vertex> vertices = new ArrayList<>(elements.size() * 24);
        for (var raw : elements) {
            JsonObject element = raw.getAsJsonObject();
            float[] from = vector(element.getAsJsonArray("from"), 3, -16, 32);
            float[] to = vector(element.getAsJsonArray("to"), 3, -16, 32);
            for (int i = 0; i < 3; i++) if (from[i] >= to[i]) throw new IllegalArgumentException("Empty spell cuboid");
            float[] pivot = {8, 8, 8};
            String axis = "y";
            float angle = 0;
            if (element.has("rotation")) {
                JsonObject rotation = element.getAsJsonObject("rotation");
                pivot = vector(rotation.getAsJsonArray("origin"), 3, -16, 32);
                axis = rotation.get("axis").getAsString();
                if (!List.of("x", "y", "z").contains(axis)) throw new IllegalArgumentException("Invalid rotation axis");
                angle = rotation.get("angle").getAsFloat();
                if (!Float.isFinite(angle) || Math.abs(angle) > 45) throw new IllegalArgumentException("Invalid rotation angle");
            }
            float x0=from[0], y0=from[1], z0=from[2], x1=to[0], y1=to[1], z1=to[2];
            float[][][] faces = {
                {{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}},
                {{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}},
                {{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}},
                {{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}},
                {{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}},
                {{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}}
            };
            String[] names = {"north","south","west","east","down","up"};
            float[][] normals = {{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
            JsonObject authoredFaces = element.getAsJsonObject("faces");
            for (int f=0; f<6; f++) {
                float[] uv = vector(authoredFaces.getAsJsonObject(names[f]).getAsJsonArray("uv"), 4, 0, 16);
                float[][] coords = {{uv[0],uv[3]},{uv[2],uv[3]},{uv[2],uv[1]},{uv[0],uv[1]}};
                float[] normal = rotate(normals[f], new float[]{0,0,0}, axis, angle);
                for (int i=0; i<4; i++) {
                    float[] point=rotate(faces[f][i], pivot, axis, angle);
                    vertices.add(new Vertex((point[0]-8)/16, (point[1]-8)/16, (point[2]-8)/16,
                            coords[i][0]/16, coords[i][1]/16, normal[0],normal[1],normal[2]));
                }
            }
        }
        return List.copyOf(vertices);
    }

    private static float[] vector(JsonArray values, int count, float min, float max) {
        if (values == null || values.size()!=count) throw new IllegalArgumentException("Invalid mesh vector");
        float[] result = new float[count];
        for (int i=0; i<count; i++) {
            float value=values.get(i).getAsFloat();
            if (!Float.isFinite(value) || value<min || value>max) throw new IllegalArgumentException("Mesh vector out of bounds");
            result[i]=value;
        }
        return result;
    }

    private static float[] rotate(float[] point, float[] pivot, String axis, float degrees) {
        double angle=Math.toRadians(degrees), c=Math.cos(angle), s=Math.sin(angle);
        float x=point[0]-pivot[0], y=point[1]-pivot[1], z=point[2]-pivot[2];
        return switch(axis) {
            case "x" -> new float[]{x+pivot[0],(float)(y*c-z*s)+pivot[1],(float)(y*s+z*c)+pivot[2]};
            case "y" -> new float[]{(float)(x*c+z*s)+pivot[0],y+pivot[1],(float)(-x*s+z*c)+pivot[2]};
            default -> new float[]{(float)(x*c-y*s)+pivot[0],(float)(x*s+y*c)+pivot[1],z+pivot[2]};
        };
    }
}
