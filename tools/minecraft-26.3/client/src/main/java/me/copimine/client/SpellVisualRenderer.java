package me.copimine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.copimine.endevent.domain.SpellCastPacket;
import me.copimine.endevent.domain.SpellCastPacketCodec;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Draws authored End Rift meshes and signed hazard outlines in world space. */
public final class SpellVisualRenderer {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "copimineclient", "textures/entity/end_rift_spell_arc.png");

    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final double[][] SECTIONS_6 = section(6);
    private static final double[][] SECTIONS_8 = section(8);
    private static final double[][] SECTIONS_12 = section(12);
    private static volatile SpellVisualGeometry.Quality quality =
            SpellVisualGeometry.Quality.BALANCED;
    private static volatile boolean reducedMotion;

    private SpellVisualRenderer() {
    }

    /** FULL / BALANCED / REDUCED only change sample density, never the footprint. */
    public static void setQuality(SpellVisualGeometry.Quality value) {
        quality = value == null ? SpellVisualGeometry.Quality.BALANCED : value;
    }

    public static SpellVisualGeometry.Quality quality() {
        return quality;
    }

    /**
     * Section 9A.04: reduced motion stills the travelling pulse and the
     * brightness throb while every warning and hit silhouette stays exactly
     * the same size. Independent of the quality preset.
     */
    public static void setReducedMotion(boolean value) {
        reducedMotion = value;
    }

    public static boolean reducedMotion() {
        return reducedMotion;
    }

    /** Parse the delivered spell cue from an event world instance. */
    public static SpellVisualGeometry.Spell spellOf(String instance) {
        return SpellVisualGeometry.spellCue(instance);
    }

    public static long seedOf(String instance) {
        return SpellVisualGeometry.cueSeed(instance);
    }

    /** The render type used for every authored stroke. */
    public static RenderType layer() {
        return RenderTypes.entityTranslucentEmissive(TEXTURE);
    }

    public static void draw(PoseStack matrices, VertexConsumer buffer,
                            EndEventWorldVfxManager.BeamSnapshot beam, long now) {
        drawLegacy(matrices, buffer, beam, now, 22_000);
    }

    static int drawLegacy(PoseStack matrices, VertexConsumer buffer,
                          EndEventWorldVfxManager.BeamSnapshot beam, long now, int budget) {
        if (matrices == null || buffer == null || beam == null || beam.start() == null || beam.end() == null) {
            return 0;
        }
        SpellVisualGeometry.Spell spell = spellOf(beam.instanceId());
        return spell == null ? 0 : drawAuthored(matrices, buffer, spell, seedOf(beam.instanceId()),
                beam.start(), beam.end(), beam.color(), beam.startedAtMillis(), beam.expiresAtMillis(),
                now, quality, budget);
    }

    static int drawCast(PoseStack matrices, VertexConsumer buffer, SpellCastPacket cast,
                        long start, long expires, long now, int budget) {
        if (now < start || now >= expires || budget <= 0) return 0;
        SpellVisualGeometry.Quality tier = SpellVisualGeometry.Quality.values()[
                Math.max(quality.ordinal(), cast.vfxLod())];
        SpellContactMesh impact = SpellContactMesh.find(cast.spellId());
        if (impact != null) {
            return impact.draw(matrices.last(), buffer, cast, (now - start) / (double) (expires - start),
                    tier, reducedMotion, budget);
        }
        if (("geyser_square".equals(cast.spellId()) || "geyser_super".equals(cast.spellId()))
                && cast.lanes().size() == 1)
            return drawGeyser(matrices.last(), buffer, cast, now - start, expires - now, tier, budget);
        SpellVisualGeometry.Spell spell = SpellVisualGeometry.Spell.fromToken(cast.spellId());
        if (spell == null) return 0;
        if (spell == SpellVisualGeometry.Spell.BEAM && cast.lanes().size() == 1) {
            return drawChestBeam(matrices.last(), buffer, cast, now - start, expires - now, tier, budget);
        }
        if (spell == SpellVisualGeometry.Spell.GEYSER && cast.lanes().size() == 1
                && cast.lanes().getFirst().radius() >= 1F) {
            return drawGeyser(matrices.last(), buffer, cast, now - start, expires - now, tier, budget);
        }
        if (SpellCastPacketCodec.isFangGeometry(cast.spellId())) {
            return drawFangs(matrices.last(), buffer, cast, (now - start) / (double) (expires - start), tier, budget);
        }
        if (spell == SpellVisualGeometry.Spell.GRAVITY && cast.lanes().size() == 3) {
            return drawGravity(matrices.last(), buffer, cast, now - start, tier, budget);
        }
        int drawn = 0;
        for (SpellCastPacket.Lane lane : cast.lanes()) {
            Vec3 from = new Vec3(lane.x(), lane.y(), lane.z());
            Vec3 to = from.add(lane.dx() * lane.length(), lane.dy() * lane.length(), lane.dz() * lane.length());
            drawn += drawAuthored(matrices, buffer, spell, cast.seed(), from, to, color(spell),
                    start, expires, now, tier, budget - drawn);
        }
        return drawn;
    }

    private static int drawChestBeam(PoseStack.Pose pose, VertexConsumer buffer, SpellCastPacket cast,
                                     long elapsed, long remaining, SpellVisualGeometry.Quality tier, int budget) {
        SpellCastPacket.Lane lane = cast.lanes().getFirst();
        Vec3 origin = new Vec3(lane.x(), lane.y(), lane.z());
        Vec3 forward = new Vec3(lane.dx(), lane.dy(), lane.dz()).normalize();
        if (lane.length() <= 0 || lane.radius() <= 0 || forward.lengthSqr() == 0) return 0;
        Vec3 normal = initialNormal(forward);
        Vec3 binormal = forward.cross(normal);
        double[][] section = profile(tier, true);
        int nodes = 2 * tier.samples();
        int vertices = (nodes + 1) * section.length * 4;
        double visibility = Math.min(1, remaining / 100.0);
        boolean charging = cast.stage() == 0;
        boolean cooling = cast.stage() == 2;
        double cool = 1D - Math.min(1D, elapsed / 720D);
        double clock = reducedMotion ? 0 : elapsed * 0.009;
        int passes = charging ? 2 : cooling ? 2 : 5;
        int count = 0;
        if (charging) {
            count += drawCollar(pose, buffer, origin, normal, binormal, lane.radius(), 0.035F,
                    0xe9ded1ff, section, budget - count);
            count += drawCollar(pose, buffer, origin.add(forward.scale(lane.length())), normal, binormal,
                    lane.radius(), 0.035F, 0xebded1ff, section, budget - count);
            if (budget - count >= 32) {
                for (int side = 0; side < 4; side++) {
                    double angle = side * Math.PI / 2;
                    Vec3 radial = normal.scale(Math.cos(angle)).add(binormal.scale(Math.sin(angle)));
                    Vec3 from = origin.add(radial.scale(lane.radius()));
                    ribbon(buffer, pose, from, from.add(forward.scale(lane.length())),
                            forward.cross(radial), .028F, .028F, 0xa7d683ff, 0xa7f9ddff);
                    count += 8;
                }
            }
        }
        for (int pass = 0; pass < passes && count + vertices <= budget; pass++) {
            var path = new Vec3[nodes];
            var radii = new java.util.ArrayList<Float>(nodes);
            var colors = new int[nodes];
            for (int i = 0; i < nodes; i++) {
                double t = i / (double) (nodes - 1);
                double edge = 0.2 + 0.8 * Math.sin(Math.PI * Math.pow(t, 0.55));
                double angle = t * (pass == 4 ? 29 : 14) - clock * (pass == 3 ? -1 : 1) + pass * 2.0944;
                double offset = pass < 2 ? 0 : lane.radius() * (0.43 + 0.32 * edge);
                path[i] = origin.add(forward.scale(lane.length() * t))
                        .add(normal.scale(offset * Math.cos(angle))).add(binormal.scale(offset * Math.sin(angle)));
                double flow = 0.82 + 0.18 * Math.cos(angle);
                double radius = switch (pass) {
                    case 0 -> charging ? 0.12 : cooling ? .15 * cool : 0.46;
                    case 1 -> charging ? 0.32 : cooling ? .56 * cool : 1.0;
                    default -> pass == 4 ? .042 : .10;
                };
                radii.add((float) Math.max(.012, lane.radius() * radius * (pass == 1 && !charging ? 1 : edge)));
                int alpha = (int) ((pass == 0 ? 243 : pass == 1 ? 82 : 182) * visibility * flow);
                if (charging) alpha /= 2;
                if (cooling) alpha = (int) (alpha * cool * .55);
                int color = pass == 0 ? 0xffefed : pass == 1 ? 0x9e32f4 : pass == 3 ? 0xd7bdff : 0xff7ad9;
                colors[i] = alpha << 24 | color;
            }
            count += tube(buffer, pose, path, radii, colors, section);
        }
        return count;
    }

    private static int drawCollar(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, Vec3 a, Vec3 b,
                                  double radius, float thickness, int tint, double[][] section, int budget) {
        if (budget < section.length * 8) return 0;
        int count = 0;
        for (int i = 0; i < section.length; i++) {
            double[] p = section[i], q = section[(i + 1) % section.length];
            Vec3 from = center.add(a.scale(radius * p[0])).add(b.scale(radius * p[1]));
            Vec3 to = center.add(a.scale(radius * q[0])).add(b.scale(radius * q[1]));
            if (budget - count < 8) break;
            ribbon(buffer, pose, from, to, from.add(to).scale(.5).subtract(center).normalize(),
                    thickness, thickness, tint, tint);
            count += 8;
        }
        return count;
    }

    private static int drawGeyser(PoseStack.Pose pose, VertexConsumer buffer, SpellCastPacket cast,
                                  long age, long remaining, SpellVisualGeometry.Quality tier, int budget) {
        var lane = cast.lanes().getFirst();
        Vec3 root = new Vec3(lane.x(), lane.y() + .07, lane.z());
        double radius = lane.radius();
        double[][] section = profile(tier, true);
        boolean warning = cast.stage() == 0;
        boolean collapsing = cast.stage() == 2;
        boolean square = "geyser_square".equals(cast.spellId());
        boolean superGeyser = "geyser_super".equals(cast.spellId());
        int edge = superGeyser ? 0xf3ffd279 : warning ? 0xeaff7d3b : 0xd5ffb26b;
        int emitted = 0;
        if (square && budget >= 32) {
            Vec3[] corners = {root.add(-radius, 0, -radius), root.add(radius, 0, -radius),
                    root.add(radius, 0, radius), root.add(-radius, 0, radius)};
            for (int i = 0; i < 4; i++) {
                Vec3 from = corners[i], to = corners[(i + 1) & 3];
                ribbon(buffer, pose, from, to, to.subtract(from).cross(UP).normalize(), .05F, .05F, edge, edge);
                emitted += 8;
            }
        } else if (!square) {
            emitted = drawCollar(pose, buffer, root, new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                    radius, superGeyser ? .095F : .045F, edge, section, budget);
        }
        int knots = tier.samples() + 2;
        int tubeCost = (knots + 1) * section.length * 4;
        int tongues = switch (tier) {
            case FULL -> 6;
            case BALANCED -> 4;
            case REDUCED -> 3;
        };
        double phase = reducedMotion ? 0 : age * .009;
        if (superGeyser && tier != SpellVisualGeometry.Quality.REDUCED) tongues += 2;
        double pressure = Math.min(1D, age / (superGeyser ? 1050D : 660D));
        double height = warning ? .18 + 1.3 * Math.pow(pressure, 3) : lane.length();
        double fade = Math.min(1D, remaining / 100D) * (collapsing ? Math.max(.2D, 1 - age / 650D) : 1D);
        for (int strand = 0; strand < tongues && budget - emitted >= tubeCost; strand++) {
            var centers = new Vec3[knots];
            var radii = new java.util.ArrayList<Float>(knots);
            var colors = new int[knots];
            double heading = strand * Math.PI * 2 / tongues;
            for (int n = 0; n < knots; n++) {
                double t = n / (double) (knots - 1);
                double angle = heading + (warning ? t * .24 : t * 2.7 - phase);
                double radial = warning ? radius * (.91 - .83 * t) :
                        strand == 0 ? radius * .09 * t : radius * (.48 + .25 * t);
                double lift = warning ? height * Math.pow(t, 2.4) : height * t;
                double x = Math.cos(angle), z = Math.sin(angle);
                double boundary = square ? Math.max(Math.abs(x), Math.abs(z)) : 1D;
                centers[n] = root.add(x * radial / boundary, lift, z * radial / boundary);
                double taper = Math.pow(1 - t, .7);
                radii.add((float) (.018 + radius * taper * (warning ? .045 : strand == 0 ? .38 : .105)));
                int hue = warning ? (superGeyser ? 0xffc74a : 0xf58054)
                        : strand == 0 ? (t < .65 ? 0xffedbb : 0xff7541)
                        : (strand & 1) == 0 ? 0x702451 : 0xff6d28;
                int alpha = (int) ((warning ? 150 + 70 * pressure : strand == 0 ? 238 : 162) * fade * (.24 + .76 * taper));
                colors[n] = alpha << 24 | hue;
            }
            emitted += tube(buffer, pose, centers, radii, colors, section);
        }
        return emitted;
    }

    private static int drawAuthored(PoseStack matrices, VertexConsumer buffer, SpellVisualGeometry.Spell spell,
                                    long seed, Vec3 origin, Vec3 end, int color, long start, long expires,
                                    long now, SpellVisualGeometry.Quality tier, int budget) {
        if (now < start || now >= expires || budget <= 0) return 0;
        long elapsed = now - start;
        Vec3 axis = end.subtract(origin);
        double length = axis.length();
        Vec3 forward = length < 0.05 ? new Vec3(0, 0, 1) : axis.scale(1 / length);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        right = right.lengthSqr() < 1.0e-10 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(forward);
        double reach = Math.max(1.0D, Math.min(SpellVisualGeometry.maxReach(spell), length));
        List<SpellVisualGeometry.Stroke> strokes = SpellVisualGeometry.build(spell, elapsed, seed, reach, tier);
        float fade = (float) (Math.min(1.0D, elapsed / 130.0D) * Math.min(1.0D, (expires - now) / 180.0D));
        if (fade <= 0.01F) return 0;
        PoseStack.Pose pose = matrices.last();
        boolean calm = reducedMotion;
        double flowPhase = SpellVisualMotionPolicy.flowPhase(elapsed, calm);
        int count = 0;
        for (SpellVisualGeometry.Stroke stroke : strokes) {
            List<SpellVisualGeometry.Point> points = stroke.points();
            if (points.size() < 2) continue;
            int tint = SpellVisualToneMap.tone(color, stroke.brightness());
            int alpha = Math.max(0, Math.min(255, Math.round(255.0F * stroke.alpha() * fade)));
            if (alpha == 0) continue;
            double stylePhase = flowPhase - stroke.style().ordinal() * 0.9D;
            Vec3[] curve = new Vec3[points.size()];
            int[] colors = new int[points.size()];
            for (int i = 0; i < curve.length; i++) {
                SpellVisualGeometry.Point point = points.get(i);
                curve[i] = origin.add(forward.scale(point.x())).add(up.scale(point.y())).add(right.scale(point.z()));
                colors[i] = (scaleAlpha(alpha, SpellVisualMotionPolicy.alphaScale(i, stylePhase, calm)) << 24) | tint;
            }
            if (stroke.style().volumetric()) {
                double[][] section = profile(tier, spell == SpellVisualGeometry.Spell.BEAM
                        || spell == SpellVisualGeometry.Spell.GEYSER);
                int required = curve.length * section.length * 4 + section.length * 4;
                if (count + required <= budget) {
                    count += tube(buffer, pose, curve, stroke.halfWidths(), colors, section);
                    continue;
                }
            }
            // Flat ground outlines are inexpensive and retain their complete footprint at every tier.
            for (int i = 0; i + 1 < curve.length && count + 8 <= budget; i++) {
                Vec3 tangent = curve[i + 1].subtract(curve[i]).normalize();
                if (tangent.lengthSqr() < 1.0e-8) continue;
                ribbon(buffer, pose, curve[i], curve[i + 1], initialNormal(tangent),
                        stroke.halfWidths().get(i), stroke.halfWidths().get(i + 1), colors[i], colors[i + 1]);
                count += 8;
            }
        }
        return count;
    }

    private static int drawFangs(PoseStack.Pose pose, VertexConsumer buffer, SpellCastPacket cast, double p,
                                 SpellVisualGeometry.Quality tier, int budget) {
        // Spend the budget on the exact ground marks before decorative spokes.
        int count = 0;
        for (SpellCastPacket.Lane lane : cast.lanes()) {
            Vec3 root = new Vec3(lane.x(), lane.y() + 0.06, lane.z());
            int tint = cast.spellId().equals("crown") ? 0xC289FF : 0xE0BBFF;
            if (budget - count < 12 * 8) break;
            for (int i = 0; i < SECTIONS_12.length; i++) {
                double[] a = SECTIONS_12[i];
                double[] b = SECTIONS_12[(i + 1) % SECTIONS_12.length];
                double r = lane.radius();
                Vec3 from = root.add(r * a[0], 0, r * a[1]);
                Vec3 to = root.add(r * b[0], 0, r * b[1]);
                Vec3 side = to.subtract(from).cross(UP).normalize();
                ribbon(buffer, pose, from, to, side, 0.035f, 0.035f, 0xe8000000 | tint, 0xe8000000 | tint);
                count += 8;
            }
            if (cast.stage() == 0 || budget - count < 400) continue;
            double height = Math.min(1, p * 5) * Math.min(1, (1 - p) * 4) * 1.5;
            int[] colors = {0xa06b2ba6, 0xe0000000 | tint, 0xfff8edff};
            for (int sign : new int[]{-1, 1}) {
                Vec3[] curve = {root.add(sign * 0.35, 0, 0), root.add(sign * 0.24, height * 0.52, 0.13),
                        root.add(sign * 0.09, height, 0.04)};
                count += tube(buffer, pose, curve, List.of(0.18f, 0.12f, 0.015f), colors, profile(tier, true));
            }
        }
        if (cast.spellId().equals("crown")) {
            double[][] section = profile(tier, true);
            Vec3 base = new Vec3(cast.x(), cast.y(), cast.z());
            double yaw = cast.yaw() + 90.0;
            int samples = tier == SpellVisualGeometry.Quality.REDUCED ? 5 : 9;
            double[] angles = me.copimine.endevent.domain.ArchmageV5CombatPolicy.crownSpokeAnglesDegrees(0, 10, yaw);
            for (double angle : angles) {
                if (budget - count < (samples + 2) * section.length * 4) break;
                var points = me.copimine.endevent.domain.ArchmageV5CombatPolicy.crownArmPoints(angle,
                        me.copimine.endevent.domain.ArchmageV5CombatPolicy.CROWN_LENGTH_MAX, samples);
                Vec3[] centers = new Vec3[points.size()];
                int[] colors = new int[points.size()];
                var radii = new java.util.ArrayList<Float>(points.size());
                for (int i = 0; i < points.size(); i++) {
                    double[] pt = points.get(i);
                    double t = i / (double) samples;
                    centers[i] = base.add(pt[0], Math.max(0.06, pt[1]), pt[2]);
                    radii.add((float) (0.06 + 0.14 * Math.sin(Math.PI * t)));
                    colors[i] = cast.stage() == 0 ? 0x88704bbd : 0xa8753bbe;
                }
                count += tube(buffer, pose, centers, radii, colors, section);
            }
        }
        return count;
    }

    private static int drawGravity(PoseStack.Pose pose, VertexConsumer buffer, SpellCastPacket cast,
                                   long tickMillis, SpellVisualGeometry.Quality tier, int budget) {
        if (budget < 128) return 0;
        double rx = cast.lanes().get(0).length();
        double rz = cast.lanes().get(1).length();
        double height = cast.lanes().get(2).length();
        double x = cast.x(), y = cast.y(), z = cast.z();
        boolean circular = cast.lanes().get(0).radius() == 0;
        int ink = cast.stage() == 1 ? 0xc8ba9cff : 0xb48468e8;
        int count = 0;
        if (circular) {
            count += drawCollar(pose, buffer, new Vec3(x, y, z), new Vec3(1, 0, 0),
                    new Vec3(0, 0, 1), rx, .08F, ink, SECTIONS_12, budget);
        } else {
            Vec3[] floor = {new Vec3(x - rx, y, z - rz), new Vec3(x + rx, y, z - rz),
                    new Vec3(x + rx, y, z + rz), new Vec3(x - rx, y, z + rz)};
            for (int i = 0; i < floor.length; i++) {
                Vec3 from = floor[i], to = floor[(i + 1) & 3];
                Vec3 side = to.subtract(from).cross(UP).normalize();
                ribbon(buffer, pose, from, to, side, 0.08f, 0.08f, ink, ink);
                count += 8;
            }
        }
        if (height < 0.2) return count;
        int columns = switch (tier) {
            case FULL -> 16;
            case BALANCED -> 12;
            case REDUCED -> 8;
        };
        double[][] section = profile(tier, false);
        if (budget - count < section.length * 24) return count;
        boolean warning = cast.stage() == 0;
        boolean collapse = cast.stage() == 2;
        double progress = Math.clamp(tickMillis / (warning ? 1900.0 : 1500.0), 0, 1);
        double flow = reducedMotion ? .48 : (tickMillis / (warning ? 2200.0 : 1520.0)) % 1;
        double fade = collapse ? (1 - progress) * (1 - progress) : 1;
        List<Float> radii = List.of(.025F, .10F, .22F, .16F, .09F, .022F);
        int cost = (radii.size() + 1) * section.length * 4;
        int alpha = (int) (fade * (warning ? 128 : 202));
        int[] shades = {(alpha / 5) << 24 | 0x5b4bad, (alpha / 2) << 24 | 0xa28bda,
                alpha << 24 | 0xe0d2ff, (alpha * 3 / 4) << 24 | 0xa170ea,
                (alpha / 2) << 24 | 0x7254ca, 0x00472b89};
        for (int column = 0; column < columns && budget - count >= cost; column++) {
            double angle = column * 2.39996323;
            double radial = .13 + .74 * Math.sqrt((column + 1) / (double) columns);
            double drift = (flow + column * .618034) % 1;
            double top = height * (1.0 - drift * (warning ? .24 : .77));
            if (collapse) top *= 1 - .76 * progress;
            double tail = height * (warning ? .17 + .26 * progress : .24 + .31 * (1 - drift));
            double bottom = Math.max(.12D, top - tail);
            if (top - bottom < .12D) continue;
            Vec3[] path = new Vec3[radii.size()];
            for (int i = 0; i < path.length; i++) {
                double t = i / (double) (path.length - 1);
                double turn = angle + .19 * t * t;
                double inward = radial * (1 - .22 * t * t);
                path[i] = new Vec3(x + Math.cos(turn) * inward * rx,
                        y + top + (bottom - top) * t,
                        z + Math.sin(turn) * inward * rz);
            }
            count += tube(buffer, pose, path, radii, shades, section);
        }
        return count;
    }

    private static int color(SpellVisualGeometry.Spell spell) {
        return switch (spell) {
            case CROWN, CIRCLE, FANG_LINE -> 0xB070FF;
            case WARDEN_SWEEP, ECHO_SPHERE, SCULK_TRACE -> 0x62FFE0;
            case GEYSER -> 0xFF8A2B;
            case GRAVITY -> 0x9677F7;
            case GUARDIAN_FAN, GUARDIAN_HEAVY, SEPARATION -> 0x88DDFC;
            case BEAM -> 0xE58BFF;
        };
    }

    static double[][] profile(SpellVisualGeometry.Quality tier, boolean hero) {
        return switch (tier) {
            case FULL -> hero ? SECTIONS_12 : SECTIONS_8;
            case BALANCED -> SECTIONS_8;
            case REDUCED -> SECTIONS_6;
        };
    }

    private static double[][] section(int sides) {
        double[][] values = new double[sides][2];
        for (int i = 0; i < sides; i++) {
            double angle = 2 * Math.PI * i / sides;
            values[i][0] = Math.cos(angle);
            values[i][1] = Math.sin(angle);
        }
        return values;
    }

    private static Vec3 initialNormal(Vec3 tangent) {
        Vec3 normal = tangent.cross(UP);
        return (normal.lengthSqr() > 1.0e-8 ? normal : tangent.cross(new Vec3(1, 0, 0))).normalize();
    }

    private static Vec3 transport(Vec3 normal, Vec3 from, Vec3 to) {
        Vec3 rotationAxis = from.cross(to);
        double step = 1.0 + from.dot(to);
        if (step > 1.0e-6) {
            Vec3 first = rotationAxis.cross(normal);
            normal = normal.add(first).add(rotationAxis.cross(first).scale(1.0 / step));
        }
        Vec3 projected = normal.subtract(to.scale(normal.dot(to)));
        return projected.lengthSqr() > 1.0e-8 ? projected.normalize() : initialNormal(to);
    }

    static int tube(VertexConsumer buffer, PoseStack.Pose pose, Vec3[] centers,
                            List<Float> radii, int[] colors, double[][] section) {
        int sides = section.length;
        Vec3[] previousRing = new Vec3[sides];
        Vec3[] previousNormals = new Vec3[sides];
        Vec3[] ring = new Vec3[sides];
        Vec3[] normals = new Vec3[sides];
        Vec3 oldTangent = null;
        Vec3 normal = null;
        int emitted = 0;
        for (int i = 0; i < centers.length; i++) {
            int before = Math.max(0, i - 1);
            int after = Math.min(centers.length - 1, i + 1);
            Vec3 tangent = centers[after].subtract(centers[before]).normalize();
            if (tangent.lengthSqr() < 1.0e-8) tangent = oldTangent == null ? UP : oldTangent;
            normal = normal == null ? initialNormal(tangent) : transport(normal, oldTangent, tangent);
            Vec3 binormal = tangent.cross(normal);
            double path = centers[after].distanceTo(centers[before]);
            double slope = path > 1.0e-5 ? (radii.get(after) - radii.get(before)) / path : 0;
            double radius = Math.max(0.008, radii.get(i));
            for (int j = 0; j < sides; j++) {
                Vec3 radial = normal.scale(section[j][0]).add(binormal.scale(section[j][1]));
                ring[j] = centers[i].add(radial.scale(radius));
                normals[j] = radial.subtract(tangent.scale(slope)).normalize();
            }
            if (i > 0) {
                float u0 = (float) (i - 1) / (centers.length - 1);
                float u1 = (float) i / (centers.length - 1);
                for (int j = 0; j < sides; j++) {
                    int next = (j + 1) % sides;
                    float v0 = (float) j / sides;
                    float v1 = (float) (j + 1) / sides;
                    vertex(buffer, pose, previousRing[j], u0, v0, previousNormals[j], colors[i - 1]);
                    vertex(buffer, pose, previousRing[next], u0, v1, previousNormals[next], colors[i - 1]);
                    vertex(buffer, pose, ring[next], u1, v1, normals[next], colors[i]);
                    vertex(buffer, pose, ring[j], u1, v0, normals[j], colors[i]);
                    emitted += 4;
                }
            }
            if (i == 0 || i == centers.length - 1) {
                cap(buffer, pose, centers[i], ring, i == 0 ? tangent.scale(-1) : tangent,
                        colors[i], i == 0);
                emitted += sides * 4;
            }
            Vec3[] swap = previousRing; previousRing = ring; ring = swap;
            swap = previousNormals; previousNormals = normals; normals = swap;
            oldTangent = tangent;
        }
        return emitted;
    }

    private static void cap(VertexConsumer buffer, PoseStack.Pose pose, Vec3 center, Vec3[] ring,
                            Vec3 normal, int color, boolean reverse) {
        for (int i = 0; i < ring.length; i++) {
            int next = (i + 1) % ring.length;
            vertex(buffer, pose, center, 0.5f, 0.5f, normal, color);
            vertex(buffer, pose, ring[reverse ? next : i], 0.5f, 0.15f, normal, color);
            vertex(buffer, pose, ring[reverse ? i : next], 0.5f, 0.85f, normal, color);
            vertex(buffer, pose, center, 0.5f, 0.5f, normal, color);
        }
    }

    private static int scaleAlpha(int alpha, float pulse) {
        int scaled = Math.round(alpha * Math.max(0.0F, Math.min(1.0F, pulse)));
        return Math.max(0, Math.min(255, scaled));
    }
    private static void ribbon(VertexConsumer buffer, PoseStack.Pose pose, Vec3 from, Vec3 to,
                               Vec3 side, float wa, float wb, int argbFrom, int argbTo) {
        Vec3 normal = side.cross(to.subtract(from).normalize()).normalize();
        Vec3 aLeft = from.subtract(side.scale(wa));
        Vec3 aRight = from.add(side.scale(wa));
        Vec3 bRight = to.add(side.scale(wb));
        Vec3 bLeft = to.subtract(side.scale(wb));
        quad(buffer, pose, aLeft, aRight, bRight, bLeft, normal, argbFrom, argbTo);
        quad(buffer, pose, bLeft, bRight, aRight, aLeft, normal.scale(-1.0D), argbTo, argbFrom);
    }

    /**
     * Emit a double-sided quad whose {@code u} runs along the stroke length and
     * {@code v} spans the ribbon width. The authored texture is a bright core
     * band with feathered edges, so this mapping draws a clean glowing ribbon.
     */
    static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b,
                             Vec3 c, Vec3 d, Vec3 normal, int argbStart, int argbEnd) {
        vertex(buffer, pose, a, 0.02F, 0.02F, normal, argbStart);
        vertex(buffer, pose, b, 0.02F, 0.98F, normal, argbStart);
        vertex(buffer, pose, c, 0.98F, 0.98F, normal, argbEnd);
        vertex(buffer, pose, d, 0.98F, 0.02F, normal, argbEnd);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 point,
                               float u, float v, Vec3 normal, int argb) {
        buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(argb).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
