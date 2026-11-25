package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.copimine.endevent.domain.SpellCastPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Short meshes that start on the server's committed swing, landing or collision tick. */
enum SpellContactMesh {
    GUARDIAN_BASIC("guardian_basic_contact", 0x398d82, 0xb0fff2),
    GUARDIAN_LANCE("guardian_lance_contact", 0x3750f4, 0xcffbff),
    GUARDIAN_FIREBALL("guardian_fireball_contact", 0xba210f, 0xffde73),
    GUARDIAN_FAN("guardian_cleave_contact", 0x86eaff, 0xe3faff),
    GUARDIAN_HEAVY("guardian_heavy_contact", 0xfe8439, 0xffe7a0),
    WARDEN_ECHO("warden_echo_contact", 0x16aeba, 0xa4fff7),
    MINI_PULSE("mini_pulse_contact", 0x3d61ca, 0xb8deff),
    SNARE("snare_contact", 0x423292, 0xcc9bff),
    WITHER("wither_contact", 0x518abd, 0xcbeaff),
    MAGMA("magma_contact", 0xb3230b, 0xffbf39),
    VOID("void_contact", 0x8428d9, 0xf1c9ff),
    BOLT("bolt_contact", 0x5474ff, 0xf5e9ff),
    BREATH("breath_contact", 0xb12d80, 0xffa9e3),
    WEB("web_contact", 0x786ce7, 0xf2ebff);

    private static final double TAU = Math.PI * 2;
    private static final List<List<Face>> SHELLS = List.of(
            sphereMesh(32, 14), sphereMesh(24, 10), sphereMesh(16, 7));

    private record Face(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, boolean accent) { }
    private static final SpellContactMesh[] FAMILIES = values();
    private final String token;
    private final int body;
    private final int core;

    SpellContactMesh(String token, int body, int core) {
        this.token = token;
        this.body = body;
        this.core = core;
    }

    static SpellContactMesh find(String token) {
        for (SpellContactMesh family : FAMILIES) {
            if (family.token.equals(token)) return family;
        }
        return null;
    }

    int draw(PoseStack.Pose pose, VertexConsumer buffer, SpellCastPacket cast, double time,
             SpellVisualGeometry.Quality quality, boolean reducedMotion, int budget) {
        if (cast.lanes().size() != 1 || cast.stage() != 2) return 0;
        SpellCastPacket.Lane lane = cast.lanes().getFirst();
        Vec3 origin = new Vec3(lane.x(), lane.y(), lane.z());
        double[] progress = {Math.min(1, time * 3.1), Math.pow(1 - time, 1.35)};
        return switch (this) {
            case WARDEN_ECHO, MINI_PULSE, VOID -> drawShell(pose, buffer, origin, lane.radius(), progress,
                    quality, budget);
            case GUARDIAN_BASIC, GUARDIAN_FAN, GUARDIAN_HEAVY, WITHER -> drawCleave(pose, buffer, origin, lane,
                    progress, quality, budget);
            case GUARDIAN_LANCE, GUARDIAN_FIREBALL, MAGMA, BOLT, BREATH, WEB, SNARE -> drawFilaments(pose, buffer, origin, lane.radius(),
                    cast.seed(), time, progress, quality, reducedMotion, budget);
        };
    }

    private int drawShell(PoseStack.Pose pose, VertexConsumer buffer, Vec3 origin, double radius,
                          double[] progress, SpellVisualGeometry.Quality quality, int budget) {
        int tier = quality.ordinal();
        while (tier + 1 < SHELLS.size() && SHELLS.get(tier).size() * 4 > budget) {
            tier++;
        }
        List<Face> mesh = SHELLS.get(tier);
        if (mesh.size() * 4 > budget || radius <= 0) return 0;
        double shellRadius = radius * (0.13 + 0.87 * progress[0]);
        int tint = argb(body, (this == WARDEN_ECHO ? 70 : 93) * progress[1]);
        int edge = argb(core, (this == WARDEN_ECHO ? 170 : 128) * progress[1]);
        for (Face face : mesh) {
            SpellVisualRenderer.quad(buffer, pose, origin.add(face.a.scale(shellRadius)),
                    origin.add(face.b.scale(shellRadius)), origin.add(face.c.scale(shellRadius)),
                    origin.add(face.d.scale(shellRadius)), face.normal, face.accent ? edge : tint, tint);
        }
        return mesh.size() * 4;
    }

    private static List<Face> sphereMesh(int slices, int bands) {
        var faces = new ArrayList<Face>(slices * bands);
        for (int row = 0; row < bands; row++) {
            double bottom = Math.PI * row / bands - Math.PI / 2;
            double top = Math.PI * (row + 1) / bands - Math.PI / 2;
            for (int i = 0; i < slices; i++) {
                double left = TAU * i / slices;
                double right = TAU * (i + 1) / slices;
                Vec3 a = spherePoint(left, bottom);
                Vec3 b = spherePoint(left, top);
                Vec3 c = spherePoint(right, top);
                Vec3 d = spherePoint(right, bottom);
                faces.add(new Face(a, b, c, d, a.add(b).add(c).add(d).normalize(),
                        (row + (i & 1)) % 4 == 0));
            }
        }
        return List.copyOf(faces);
    }

    private int drawCleave(PoseStack.Pose pose, VertexConsumer buffer, Vec3 origin,
                           SpellCastPacket.Lane lane, double[] progress,
                           SpellVisualGeometry.Quality quality, int budget) {
        double[][] section = SpellVisualRenderer.profile(quality, this == GUARDIAN_FAN || this == GUARDIAN_HEAVY);
        int samples = quality.samples() * 2;
        int required = section.length * (samples + 2) * 4;
        if (required > budget || lane.length() == 0) return 0;
        double heading = Math.atan2(lane.dz(), lane.dx());
        double sweep = lane.radius();
        double reach = lane.length() * (0.35 + 0.65 * progress[0]);
        int count = 0;
        int passes = this == GUARDIAN_BASIC ? 1 : 2;
        for (int pass = 0; pass < passes && budget - count >= required; pass++) {
            Vec3[] path = new Vec3[samples + 1];
            var radii = new ArrayList<Float>(path.length);
            int[] tones = new int[path.length];
            for (int i = 0; i < path.length; i++) {
                double t = i / (double) samples;
                double shoulder = Math.sin(Math.PI * t);
                double angle = heading + sweep * (2 * t - 1);
                double radial = reach * (0.78 + 0.22 * shoulder) * (1 - pass * 0.20);
                double lift = this == GUARDIAN_HEAVY ? 2.5 * (1 - progress[0]) * shoulder : 0.5 * shoulder;
                path[i] = origin.add(Math.cos(angle) * radial, 0.21 + lift + 0.19 * pass,
                        Math.sin(angle) * radial);
                radii.add((float) ((pass == 0 ? 0.27 : 0.085) * (0.14 + 0.86 * shoulder)));
                tones[i] = argb(pass == 0 ? body : core,
                        (pass == 0 ? 155 : 230) * progress[1] * (0.35 + 0.65 * shoulder));
            }
            count += SpellVisualRenderer.tube(buffer, pose, path, radii, tones, section);
        }
        return count;
    }

    private int drawFilaments(PoseStack.Pose pose, VertexConsumer buffer, Vec3 origin,
                              double radius, long seed, double time, double[] progress,
                              SpellVisualGeometry.Quality quality, boolean reducedMotion, int budget) {
        double[][] section = SpellVisualRenderer.profile(quality, false);
        int count = 0;
        int arms = switch (this) {
            case WEB, SNARE -> 8;
            case BREATH -> 5;
            case MAGMA -> 7;
            case GUARDIAN_LANCE -> 9;
            case GUARDIAN_FIREBALL -> 12;
            default -> 6;
        };
        int knots = this == WEB || this == SNARE ? 5 : 4;
        int required = (knots + 1) * section.length * 4;
        int[] nodes = new int[knots];
        for (int i = 0; i < arms && budget - count >= required; i++) {
            double noise = SpellVisualGeometry.splitUnit(seed, i + 403L);
            double angle = TAU * (i + noise * 0.26) / arms;
            Vec3[] path = new Vec3[knots];
            var radii = new ArrayList<Float>(knots);
            for (int j = 0; j < knots; j++) {
                double t = j / (double) (knots - 1);
                double turn = angle + switch (this) {
                    case WEB -> 0.15 * Math.sin(3 * Math.PI * t);
                    case BREATH -> 1.3 * t;
                    case SNARE -> -0.7 * t;
                    default -> (noise - 0.5) * 0.6 * (j & 1);
                };
                double spread = radius * t * (this == SNARE ? 1 - 0.72 * progress[0]
                        : 0.3 + 0.7 * progress[0]);
                double rise = switch (this) {
                    case WEB -> 0.26 * Math.sin(Math.PI * t);
                    case SNARE -> 1.2 * (1 - t) * progress[1];
                    case MAGMA -> 0.13 + 0.68 * Math.sin(Math.PI * t) * progress[1];
                    case GUARDIAN_LANCE -> t * (0.4 + noise * 1.8) * progress[0];
                    case GUARDIAN_FIREBALL -> 0.17 + (1.4 + noise) * Math.sin(Math.PI * t) * progress[1];
                    case BREATH -> t * (1.2 + noise * 0.8) * progress[0];
                    default -> t * (0.65 + noise) * progress[0];
                };
                if (!reducedMotion && this == BREATH) turn += time * 1.2;
                path[j] = origin.add(Math.cos(turn) * spread, rise, Math.sin(turn) * spread);
                radii.add((float) ((this == WEB ? 0.045 : 0.21) * (0.08 + 0.92 * (1 - t))));
                nodes[j] = argb(j == 1 ? core : body, (235 - j * 28) * progress[1]);
            }
            count += SpellVisualRenderer.tube(buffer, pose, path, radii, nodes, section);
        }
        return count;
    }

    private static Vec3 spherePoint(double longitude, double latitude) {
        double band = Math.cos(latitude);
        return new Vec3(Math.cos(longitude) * band, Math.sin(latitude), Math.sin(longitude) * band);
    }

    private static int argb(int color, double alpha) {
        return (int) Math.clamp(alpha, 0, 255) << 24 | color;
    }
}
