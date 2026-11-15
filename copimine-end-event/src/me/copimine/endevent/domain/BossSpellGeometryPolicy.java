package me.copimine.endevent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded local-space geometry. Presentation never authorizes collision or damage. */
public final class BossSpellGeometryPolicy {
    public enum Shape {
        VOID_SPHERE, RIFT_LANCE, ARROW_RACK, VOID_RUNE, SUMMON_ARCH,
        INFERNO_CROWN, FIREBALL_CAGE, FINAL_SEAL, SWIPE, CHEST_IRIS,
        SLAM_FAULT, AMBUSH_TALONS, KAGUNE_KNOT, PHASE_SHELL, DEFEAT, GEYSER,
        /*
         * The four miniboss signatures: a rift dash, a binding snare, a sonic
         * bloom and the euphoria helix. They previously had neither a shape nor
         * a mesh, so their cast could only ever be drawn as a flat camera-facing
         * ring -- the "particle-only substitute" the specification forbids for a
         * player-facing spell. Each now has both a silhouette in sample() and a
         * client mesh in modelData().
         */
        RIFT_GATE, SNARE_CHAINS, ECHO_BLOOM, EUPHORIA_HELIX
    }
    public enum Anchor { CHEST, FEET, LOCKED_FLOOR }
    public enum Beat { CHARGE, RELEASE, IMPACT }
    public record Point(double side, double up, double forward, int layer) { }
    public static final int MAX_POINTS = 96;
    private static final double TAU = Math.PI * 2;
    private BossSpellGeometryPolicy() { }

    public static Shape shape(String id) {
        if (id == null) return null;
        return switch (id.trim().toLowerCase(Locale.ROOT)) {
            case "void_blast" -> Shape.VOID_SPHERE;
            case "rift_projectile" -> Shape.RIFT_LANCE;
            case "rift_arrows", "arrow_salvo" -> Shape.ARROW_RACK;
            case "void_mark" -> Shape.VOID_RUNE;
            case "summon_servants", "rift_obelisks" -> Shape.SUMMON_ARCH;
            case "arena_inferno" -> Shape.INFERNO_CROWN;
            case "boss_fireball" -> Shape.FIREBALL_CAGE;
            case "final_strike" -> Shape.FINAL_SEAL;
            case "melee_swipe" -> Shape.SWIPE;
            case "chest_strike" -> Shape.CHEST_IRIS;
            case "ground_slam" -> Shape.SLAM_FAULT;
            case "ambush_grab" -> Shape.AMBUSH_TALONS;
            case "small_kagune" -> Shape.KAGUNE_KNOT;
            case "phase_shift" -> Shape.PHASE_SHELL;
            case "defeat_collapse" -> Shape.DEFEAT;
            case "rift_fracture", "inferno_geyser" -> Shape.GEYSER;
            case "rift_step" -> Shape.RIFT_GATE;
            case "void_snare" -> Shape.SNARE_CHAINS;
            case "echo_pulse" -> Shape.ECHO_BLOOM;
            case "rift_euphoria" -> Shape.EUPHORIA_HELIX;
            // The five ids the miniboss rotation really emits. Without these
            // `shape()` returned null, so `telegraphMiniBossSpell` had no
            // silhouette to sample and no mesh to spawn: the only live
            // miniboss spells were the only ones with no geometry at all.
            // Each reuses an authored body from the boss kit whose form
            // matches the ability, rather than inventing a second family that
            // would have to be verified separately.
            case "arrow_rain" -> Shape.ARROW_RACK;
            case "rift_bolt" -> Shape.RIFT_LANCE;
            case "dragon_breath" -> Shape.INFERNO_CROWN;
            case "void_burst" -> Shape.VOID_SPHERE;
            case "web_shot" -> Shape.SNARE_CHAINS;
            default -> null;
        };
    }

    /** Private client mesh selectors; displays are cosmetic and never collision owners. */
    public static int modelData(Shape shape) {
        if (shape == null) return 0;
        // SWIPE and DEFEAT are the only player-facing shapes with no mesh, and
        // both are deliberate: a contact arc has to track the boss's own swing
        // rather than a spawned body, and DEFEAT is the boss dissolving, which
        // a mesh would freeze mid-collapse. Every other shape has real geometry.
        return switch (shape) {
            case VOID_SPHERE -> 830200;
            case FIREBALL_CAGE -> 830201;
            case RIFT_LANCE -> 830202;
            case PHASE_SHELL -> 830203;
            case VOID_RUNE -> 830208;
            case INFERNO_CROWN -> 830209;
            case SUMMON_ARCH -> 830210;
            case ARROW_RACK -> 830211;
            case FINAL_SEAL -> 830204;
            case CHEST_IRIS -> 830205;
            case AMBUSH_TALONS, KAGUNE_KNOT -> 830206;
            case SLAM_FAULT, GEYSER -> 830207;
            // The four miniboss signatures used to return 0, which meant the
            // only thing ever drawn for them was the sampled particle frame:
            // the spell had an authored silhouette list but no body. Each now
            // resolves to its own client mesh, the counterpart of the shape in
            // sample() below.
            case RIFT_GATE -> 830212;
            case SNARE_CHAINS -> 830213;
            case ECHO_BLOOM -> 830214;
            case EUPHORIA_HELIX -> 830215;
            default -> 0;
        };
    }

    public static Anchor anchor(Shape shape, Beat beat) {
        if (shape == null || beat == null) return Anchor.FEET;
        return switch (shape) {
            case VOID_RUNE, FINAL_SEAL, AMBUSH_TALONS, GEYSER, SNARE_CHAINS -> Anchor.LOCKED_FLOOR;
            case VOID_SPHERE -> Anchor.LOCKED_FLOOR;
            case RIFT_LANCE, ARROW_RACK, FIREBALL_CAGE, CHEST_IRIS -> Anchor.CHEST;
            default -> Anchor.FEET;
        };
    }

    /** One generated frame is shared by viewers; distance LOD selects a stride afterwards. */
    public static List<Point> sample(Shape shape, Beat beat, double progress, double radius) {
        if (shape == null || beat == null || !Double.isFinite(progress)
                || !Double.isFinite(radius) || radius <= 0) return List.of();
        double p = Math.clamp(progress, 0, 1), r = Math.min(16, radius);
        boolean charge = beat == Beat.CHARGE;
        double size = charge ? 0.35 + 0.65 * p : 0.18 + 0.82 * easeOut(p);
        double spin = p * TAU;
        List<Point> out = new ArrayList<>(MAX_POINTS);
        switch (shape) {
            case VOID_SPHERE -> {
                double s = r * (charge ? 0.8 + p * 0.2 : 1.0 - p * 0.25);
                for (int plane = 0; plane < 3; plane++) for (int i = 0; i < 24; i++) {
                    double a = TAU * i / 24 + spin * (plane % 2 == 0 ? 0.3 : -0.3);
                    double x = Math.cos(a) * s, y = Math.sin(a) * s;
                    add(out, plane == 1 ? 0 : x, 0.8 + (plane == 0 ? 0 : y), plane == 0 ? y : plane == 1 ? x : 0, plane);
                }
            }
            case RIFT_LANCE -> {
                for (int strand = 0; strand < 2; strand++) for (int i = 0; i < 20; i++) {
                    double u = i / 19.0, a = u * TAU * 1.5 + spin + strand * Math.PI;
                    double s = Math.sin(Math.PI * u) * 0.30 * size;
                    add(out, Math.cos(a) * s, Math.sin(a) * s, u * 2.4 * size, strand);
                }
                for (int i = 0; i < 10; i++) add(out, 0, 0, i / 9.0 * 2.7 * size, 2);
                for (int fin = 0; fin < 3; fin++) line(out,
                        new Point(0, 0, 2.7 * size, 2),
                        new Point(Math.cos(fin * TAU / 3) * 0.4, Math.sin(fin * TAU / 3) * 0.4, 1.8 * size, 1), 5, 1);
            }
            case ARROW_RACK -> {
                // Three lanes that fan apart as they travel. The rack used to be
                // perfectly horizontal, so every sample sat on one plane and the
                // volley read as a flat ruler rather than as shots in the air.
                for (int lane = -1; lane <= 1; lane++) {
                    double x = lane * (charge ? 0.65 - p * 0.15 : 0.5 + p * 0.5);
                    double lift = lane * 0.28 * size;
                    line(out, new Point(x, 0, 0, 0), new Point(x, lift, 2.0 * size, 0), 10, 0);
                    for (int side : new int[]{-1, 1}) line(out,
                            new Point(x, lift, 2.0 * size, 2),
                            new Point(x + side * 0.25, lift + side * 0.12 * size, 1.55 * size, 1), 4, 1);
                }
            }
            case VOID_RUNE -> {
                polygon(out, 6, r, 0.08, spin * 0.08, 4, 0);
                polygon(out, 3, r * 0.68, 0.14, -spin * 0.1, 5, 1);
                for (int i = 0; i < 6; i++) {
                    double a = i * TAU / 6;
                    line(out, new Point(Math.cos(a) * r, 0.1, Math.sin(a) * r, 1),
                            new Point(Math.cos(a) * r * 0.8, 0.5 + size * 0.5, Math.sin(a) * r * 0.8, 2), 4, 1);
                }
            }
            case SUMMON_ARCH, AMBUSH_TALONS, KAGUNE_KNOT -> {
                int ribs = shape == Shape.KAGUNE_KNOT ? 4 : 3;
                for (int rib = 0; rib < ribs; rib++) for (int i = 0; i < 18; i++) {
                    double u = i / 17.0, a = rib * TAU / ribs + u * (shape == Shape.KAGUNE_KNOT ? TAU : shape == Shape.AMBUSH_TALONS ? -1.4 : 0.7);
                    double s = r * (1 - 0.7 * u) * size;
                    add(out, Math.cos(a) * s, Math.sin(u * Math.PI / 2) * 2.2 * size, Math.sin(a) * s, rib % 2);
                }
                polygon(out, 6, r * size, 0.05, 0, 3, 2);
            }
            case INFERNO_CROWN -> {
                for (int rib = 0; rib < 12; rib++) {
                    double a = rib * TAU / 12, s = r * size;
                    line(out, new Point(Math.cos(a) * s * 0.68, 0.08, Math.sin(a) * s * 0.68, 0),
                            new Point(Math.cos(a) * s, 0.7 + (rib % 3) * 0.25, Math.sin(a) * s, 1), 5, rib % 2);
                }
                polygon(out, 12, r * size * 0.8, 0.08, 0, 2, 2);
            }
            case FIREBALL_CAGE -> {
                // Three offset diamond cages give a volume, not a camera-facing disk.
                for (int plane = 0; plane < 3; plane++) for (int edge = 0; edge < 4; edge++) {
                    double a = edge * Math.PI / 2 + spin * 0.2;
                    double b = a + Math.PI / 2, s = Math.min(1.2, r) * size;
                    for (int i = 0; i < 5; i++) {
                        double u = i / 4.0, x = (Math.cos(a) * (1 - u) + Math.cos(b) * u) * s;
                        double y = (Math.sin(a) * (1 - u) + Math.sin(b) * u) * s;
                        add(out, plane == 1 ? 0 : x, plane == 0 ? 0 : y, plane == 0 ? y : plane == 1 ? x : 0, plane % 2);
                    }
                }
            }
            case FINAL_SEAL, PHASE_SHELL -> {
                int ribs = shape == Shape.FINAL_SEAL ? 4 : 6;
                for (int rib = 0; rib < ribs; rib++) for (int i = 0; i < 12; i++) {
                    double u = i / 11.0, a = rib * TAU / ribs + (shape == Shape.PHASE_SHELL ? spin * 0.12 : 0);
                    double s = r * Math.sin(u * Math.PI) * (charge ? 1 - p * 0.25 : size);
                    add(out, Math.cos(a) * s, u * 3.7, Math.sin(a) * s, rib % 2);
                }
                polygon(out, ribs, r * size, 0.08, 0, 3, 2);
            }
            case SWIPE -> {
                double halfArc = Math.acos(0.1), head = -halfArc + 2 * halfArc * p;
                for (int band = 0; band < 3; band++) for (int i = 0; i < 20; i++) {
                    double u = i / 19.0, a = head - u * Math.max(0.08, Math.min(1.0, p * 2));
                    double s = r * (1 - band * 0.07);
                    add(out, Math.sin(a) * s, 1.1 + 0.25 * Math.sin(a) - band * 0.07, Math.cos(a) * s, band);
                }
            }
            case CHEST_IRIS -> {
                for (int blade = 0; blade < 6; blade++) for (int i = 0; i < 10; i++) {
                    double u = i / 9.0, a = blade * TAU / 6 + u * 0.6 + spin * 0.1;
                    double s = (0.25 + u * 0.8) * (charge ? 1 - p * 0.45 : size);
                    add(out, Math.cos(a) * s, Math.sin(a) * s, u * 0.25, blade % 2);
                }
            }
            case SLAM_FAULT -> {
                for (int spoke = 0; spoke < 8; spoke++) for (int i = 0; i < 8; i++) {
                    double u = i / 7.0, a = spoke * TAU / 8 + (i % 2 == 0 ? -0.03 : 0.03);
                    add(out, Math.cos(a) * r * u * size, 0.06 + Math.sin(u * Math.PI) * p * 0.15,
                            Math.sin(a) * r * u * size, spoke % 2);
                }
                polygon(out, 12, r * size, 0.1 + p * 0.12, 0, 2, 2);
            }
            case DEFEAT -> {
                double s = r * (1 - easeOut(p)) + 0.08;
                for (int ring = 0; ring < 3; ring++) for (int i = 0; i < 24; i++) {
                    double a = i * TAU / 24 + spin * (ring % 2 == 0 ? 1 : -1);
                    add(out, Math.cos(a) * s, 0.3 + ring * (1 - p) * 1.3, Math.sin(a) * s, ring);
                }
            }
            case GEYSER -> {
                for (int plume = 0; plume < 3; plume++) for (int i = 0; i < 24; i++) {
                    double u = i / 23.0, a = plume * TAU / 3 + u * TAU * 1.5 - spin;
                    double s = r * (0.2 + u * 0.6) * Math.sin(p * Math.PI);
                    add(out, Math.cos(a) * s, u * 5 * size, Math.sin(a) * s, plume % 2);
                }
            }
            case RIFT_GATE -> {
                // A standing door the caster steps through: two posts, a lintel,
                // and a second frame set back so the gate has real depth. Every
                // other shape is radial, so this one is recognisable from its
                // straight verticals alone -- and a doorway with no thickness
                // was a picture of a door, not a door.
                double halfWidth = 0.85 * size, height = 2.4 * size, depth = 0.45 * size;
                for (int plane = 0; plane < 2; plane++) {
                    double front = plane == 0 ? 0 : depth;
                    for (int post = -1; post <= 1; post += 2) {
                        line(out, new Point(post * halfWidth, 0, front, 0),
                                new Point(post * halfWidth, height, front, 0), 8, 0);
                    }
                    line(out, new Point(-halfWidth, height, front, 1),
                            new Point(halfWidth, height, front, 1), 8, 1);
                }
                for (int post = -1; post <= 1; post += 2) {
                    line(out, new Point(post * halfWidth, height, 0, 2),
                            new Point(post * halfWidth, height, depth, 2), 3, 2);
                }
            }
            case SNARE_CHAINS -> {
                // Six chains pulled from a wide ring down to a tight one: the
                // motion itself is the read, because the shape closes as the
                // telegraph runs out.
                for (int chain = 0; chain < 6; chain++) {
                    double a = chain * TAU / 6 + spin * 0.08;
                    line(out, new Point(Math.cos(a) * r * size, 1.1, Math.sin(a) * r * size, 0),
                            new Point(Math.cos(a) * r * 0.16, 0.05, Math.sin(a) * r * 0.16, 0), 8, chain % 2);
                }
                polygon(out, 6, r * size * 0.55, 0.04, spin * 0.12, 4, 1);
            }
            case ECHO_BLOOM -> {
                // A volume rather than a disk: three latitudes of a bloom plus
                // the column the shriek leaves through.
                for (int latitude = 1; latitude <= 3; latitude++) {
                    double phi = Math.PI * latitude / 4;
                    double ring = Math.sin(phi), lift = Math.cos(phi);
                    for (int i = 0; i < 12; i++) {
                        double a = i * TAU / 12 + spin * (latitude % 2 == 0 ? 0.5 : -0.5);
                        double s = r * size * ring;
                        add(out, Math.cos(a) * s, 0.7 + lift * r * size * 0.55, Math.sin(a) * s, latitude % 2);
                    }
                }
                line(out, new Point(0, 0.05, 0, 2), new Point(0, 3.2 * size, 0, 2), 8, 2);
            }
            case EUPHORIA_HELIX -> {
                // Two strands of constant radius climbing the caster; the lance
                // tapers, this does not, so the two never read alike.
                for (int strand = 0; strand < 2; strand++) for (int i = 0; i < 14; i++) {
                    double u = i / 13.0, a = u * TAU * 1.6 + spin + strand * Math.PI;
                    double s = r * 0.45 * size;
                    add(out, Math.cos(a) * s, u * 2.6 * size, Math.sin(a) * s, strand);
                }
                polygon(out, 8, r * 0.45 * size, 0.05, spin * 0.2, 3, 2);
            }
        }
        return List.copyOf(out);
    }

    public static int stride(double distanceSquared, int viewers) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0 || distanceSquared > 48 * 48) return 0;
        return distanceSquared > 28 * 28 ? 4 : distanceSquared > 14 * 14 || viewers > 10 ? 2 : 1;
    }

    private static double easeOut(double p) { return 1 - Math.pow(1 - p, 3); }
    private static void add(List<Point> out, double x, double y, double z, int layer) {
        if (out.size() < MAX_POINTS) out.add(new Point(x, y, z, layer));
    }
    private static void line(List<Point> out, Point a, Point b, int count, int layer) {
        for (int i = 0; i < count; i++) {
            double p = i / (double) Math.max(1, count - 1);
            add(out, a.side + (b.side - a.side) * p, a.up + (b.up - a.up) * p,
                    a.forward + (b.forward - a.forward) * p, layer);
        }
    }
    private static void polygon(List<Point> out, int sides, double radius, double height, double spin, int samples, int layer) {
        for (int edge = 0; edge < sides; edge++) {
            double a = edge * TAU / sides + spin, b = (edge + 1) * TAU / sides + spin;
            line(out, new Point(Math.cos(a) * radius, height, Math.sin(a) * radius, layer),
                    new Point(Math.cos(b) * radius, height, Math.sin(b) * radius, layer), samples, layer);
        }
    }
}
