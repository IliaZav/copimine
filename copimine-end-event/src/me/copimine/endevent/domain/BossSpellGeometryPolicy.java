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
         * N23-P1-06/N23-P1-08 used to leave three different hazards sharing the
         * single fault_fragment body: kagune_eruption, rift_fracture and
         * inferno_geyser all resolved to GEYSER. That made a rock eruption, a
         * floor cut and a fire column the same visible object, which the spec
         * forbids. Each now has its own silhouette in sample() and its own
         * client mesh below, so no two major mechanics share the main body.
         */
        RIFT_FRACTURE, INFERNO_GEYSER,
        /*
         * The four miniboss signatures: a rift dash, a binding snare, a sonic
         * bloom and the euphoria helix. They previously had neither a shape nor
         * a mesh, so their cast could only ever be drawn as a flat camera-facing
         * ring -- the "particle-only substitute" the specification forbids for a
         * player-facing spell. Each now has both a silhouette in sample() and a
         * client mesh in modelData().
         */
        RIFT_GATE, SNARE_CHAINS, ECHO_BLOOM, EUPHORIA_HELIX,
        /*
         * The owner's signature ultimate: a finite chest ray with an exposed
         * socket. It used to reuse RIFT_LANCE, which meant the "visual
         * benchmark" of the whole encounter shared its silhouette with an
         * ordinary projectile; 9H.02 requires the beam to read as its own
         * authored family (core, turbulent shell, helices, travelling rings,
         * impact crown).
         */
        CHEST_BEAM,
        /*
         * N23-P1-06 residual body-sharing. `kagune_stab` still resolved to
         * RIFT_LANCE (the ordinary rift projectile), `kagune_eruption` to the
         * rock GEYSER, and `small_kagune` to KAGUNE_KNOT / 830206 (the ambush
         * talon body). Three Kagune roles therefore shared the main visible
         * object of three unrelated mechanics. Each now has its own silhouette
         * in sample(), its own client mesh and its own renderer profile.
         */
        KAGUNE_STAB, KAGUNE_ERUPTION, KAGUNE_SMALL,
        /*
         * V7 phase-signature portfolio (specification section 6). The authored
         * client meshes 830223-830236 existed without a server shape, so they
         * were unreachable: no cast could ever emit their CustomModelData and
         * the "every mesh has a body" contract was broken. Each phase now owns
         * a distinct silhouette here, and therefore a distinct client body.
         *
         * Phase 1 AWAKENING: the broken ground shock front (VOID_CRUSH) and the
         *   sweeping claw (CLAW_CHAIN).
         * Phase 2 HUNT: torn-space doorway (DIMENSIONAL_CARVE), the dash wake
         *   (HUNT_DASH) and the close grapple (GRAB_SHOVE).
         * Phase 4/5 OVERLOAD/RAGE: the wall of fire (INFERNAL_SURGE), the
         *   hanging flame curtain (INFERNO_CURTAIN) and the shattered mirror
         *   planes (MIRROR_CARNAGE).
         * Phase 6 LAST_SEAL: the spear volley (SEAL_LANCE), the rising Kagune
         *   ring (RING_OF_KAGUNE) and the under-player tendrils (AMBUSH_GRAB).
         * Wave 6: the expanding shock ring (EARTHQUAKE_WAVE).
         * Wave 6 harbinger: the stacked skeleton spire (SPIRE_HARBINGER).
         */
        VOID_CRUSH, CLAW_CHAIN, DIMENSIONAL_CARVE, HUNT_DASH, GRAB_SHOVE,
        INFERNAL_SURGE, INFERNO_CURTAIN, MIRROR_CARNAGE, SEAL_LANCE,
        RING_OF_KAGUNE, AMBUSH_GRAB, EARTHQUAKE_WAVE, SPIRE_HARBINGER,
        SHOULDER_SLAM
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
            case "ambush_grab" -> Shape.AMBUSH_GRAB;
            case "small_kagune" -> Shape.KAGUNE_SMALL;
            // The Kagune roles are real boss mechanics, not decorative effects,
            // so each needs a body of its own rather than borrowing the lance,
            // the rock geyser or the ambush talon knot.
            case "kagune_stab" -> Shape.KAGUNE_STAB;
            case "kagune_eruption" -> Shape.KAGUNE_ERUPTION;
            case "phase_shift" -> Shape.PHASE_SHELL;
            case "defeat_collapse" -> Shape.DEFEAT;
            case "rift_fracture" -> Shape.RIFT_FRACTURE;
            case "inferno_geyser" -> Shape.INFERNO_GEYSER;
            // The owner's approved chest beam: a straight, finite ray leaving the
            // chest toward a locked lane, with its own authored body family.
            case "chest_beam" -> Shape.CHEST_BEAM;
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
            // V7 phase-signature portfolio (specification section 6). These ids
            // are the real abilities of the six phases and the Wave 6 harbinger.
            case "void_crush" -> Shape.VOID_CRUSH;
            case "claw_chain" -> Shape.CLAW_CHAIN;
            case "shoulder_slam" -> Shape.SHOULDER_SLAM;
            case "dimensional_carve" -> Shape.DIMENSIONAL_CARVE;
            case "hunt_dash" -> Shape.HUNT_DASH;
            case "grab_shove" -> Shape.GRAB_SHOVE;
            case "infernal_surge" -> Shape.INFERNAL_SURGE;
            case "inferno_curtain" -> Shape.INFERNO_CURTAIN;
            case "mirror_carnage" -> Shape.MIRROR_CARNAGE;
            case "seal_lance" -> Shape.SEAL_LANCE;
            case "ring_of_kagune" -> Shape.RING_OF_KAGUNE;
            case "earthquake_wave" -> Shape.EARTHQUAKE_WAVE;
            case "spire_harbinger" -> Shape.SPIRE_HARBINGER;
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
            // The ground slam keeps the fault fragment; the geyser, the floor
            // rift and the inferno column each get a body of their own so the
            // three hazards are never mistaken for one another.
            case SLAM_FAULT -> 830207;
            case GEYSER -> 830217;
            case RIFT_FRACTURE -> 830218;
            case INFERNO_GEYSER -> 830219;
            // The four miniboss signatures used to return 0, which meant the
            // only thing ever drawn for them was the sampled particle frame:
            // the spell had an authored silhouette list but no body. Each now
            // resolves to its own client mesh, the counterpart of the shape in
            // sample() below.
            case RIFT_GATE -> 830212;
            case SNARE_CHAINS -> 830213;
            case ECHO_BLOOM -> 830214;
            case EUPHORIA_HELIX -> 830215;
            // The signature chest beam's own exposed-socket body. A distinct id
            // is the whole point: the beam must not be the lance mesh.
            case CHEST_BEAM -> 830216;
            // N23-P1-06 residual: three Kagune roles, three bodies. The stab is
            // not the rift lance, the eruption is not the rock geyser, and the
            // small melee summon is not the ambush talon knot.
            case KAGUNE_STAB -> 830220;
            case KAGUNE_ERUPTION -> 830221;
            case KAGUNE_SMALL -> 830222;
            // V7 phase-signature bodies, one CustomModelData per authored mesh.
            case VOID_CRUSH -> 830223;
            case CLAW_CHAIN -> 830224;
            case DIMENSIONAL_CARVE -> 830225;
            case SHOULDER_SLAM -> 830226;
            case HUNT_DASH -> 830227;
            case GRAB_SHOVE -> 830228;
            case SEAL_LANCE -> 830229;
            case RING_OF_KAGUNE -> 830230;
            case AMBUSH_GRAB -> 830231;
            case INFERNAL_SURGE -> 830232;
            case MIRROR_CARNAGE -> 830233;
            case INFERNO_CURTAIN -> 830234;
            case EARTHQUAKE_WAVE -> 830235;
            case SPIRE_HARBINGER -> 830236;
            default -> 0;
        };
    }

    /** Mesh assigned to the actual authored cue, with historical shape IDs as fallback. */
    public static int cueModelData(String cue, Shape shape) {
        if (cue == null) return modelData(shape);
        return switch (cue.toLowerCase(Locale.ROOT)) {
            case "void_blast" -> 830223;
            case "melee_swipe", "guardian_fan" -> 830224;
            case "rift_step" -> 830225;
            case "guardian_heavy" -> 830226;
            case "hunt_dash" -> 830227;
            case "guardian_separation" -> 830228;
            case "final_strike" -> 830229;
            case "ring_of_kagune" -> 830230;
            case "ambush_grab" -> 830231;
            case "infernal_surge" -> 830232;
            case "mirror_carnage" -> 830233;
            case "arena_inferno" -> 830234;
            case "ram_stomp" -> 830235;
            case "spire_harbinger" -> 830236;
            default -> modelData(shape);
        };
    }

    public static Anchor anchor(Shape shape, Beat beat) {
        if (shape == null || beat == null) return Anchor.FEET;
        return switch (shape) {
            case VOID_RUNE, FINAL_SEAL, AMBUSH_TALONS, GEYSER,
                    RIFT_FRACTURE, INFERNO_GEYSER, SNARE_CHAINS,
                    KAGUNE_ERUPTION -> Anchor.LOCKED_FLOOR;
            case VOID_SPHERE -> Anchor.LOCKED_FLOOR;
            // V7 phase-signature floors: the shock front, the Kagune ring, the
            // under-player tendrils, the ground shock ring, the fire wall/curtain
            // and the harbinger spire all grow out of a locked floor point.
            case VOID_CRUSH, RING_OF_KAGUNE, AMBUSH_GRAB, EARTHQUAKE_WAVE,
                    INFERNAL_SURGE, INFERNO_CURTAIN, SPIRE_HARBINGER -> Anchor.LOCKED_FLOOR;
            case RIFT_LANCE, ARROW_RACK, FIREBALL_CAGE, CHEST_IRIS, CHEST_BEAM,
                    SEAL_LANCE, DIMENSIONAL_CARVE -> Anchor.CHEST;
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
                // N23-P1-08: an original rock/obsidian eruption body, not the
                // floor-fault fragment it used to share with the ground slam.
                // A cluster of jagged shards climbs out of a pressure ring and
                // flares at the crown. The charge beat is the same cluster still
                // buried, so the tell and the eruption read as one object.
                double lift = charge ? 0.35 + 0.65 * p : 0.55 + 0.45 * easeOut(p);
                for (int shard = 0; shard < 5; shard++) {
                    double a = shard * TAU / 5 + 0.4;
                    double baseR = r * (0.18 + 0.05 * (shard % 3));
                    double tipR = baseR * (charge ? 0.9 : 1.7);
                    double height = (charge ? 1.6 + p : 3.2 + 1.1 * (shard % 3)) * size * lift;
                    line(out, new Point(Math.cos(a) * baseR, 0.05, Math.sin(a) * baseR, 0),
                            new Point(Math.cos(a) * tipR, height, Math.sin(a) * tipR, 0), 6, shard % 2);
                    // A jagged side facet gives the shard width instead of a pencil line.
                    line(out, new Point(Math.cos(a) * tipR, height, Math.sin(a) * tipR, 1),
                            new Point(Math.cos(a + 1.1) * (tipR + 0.3), height * 0.55,
                                    Math.sin(a + 1.1) * (tipR + 0.3), 1), 3, 1);
                }
                polygon(out, 10, r * (0.30 + 0.18 * (charge ? p : easeOut(p))), 0.08, spin * 0.3, 2, 2);
                if (!charge) for (int i = 0; i < 8; i++) {
                    double a = i * TAU / 8 + spin;
                    double s = r * 0.22 * size;
                    add(out, Math.cos(a) * s, 4.2 * size, Math.sin(a) * s, 2);
                }
            }
            case RIFT_FRACTURE -> {
                // 9D.03: a floor rift slash, not a radial geyser. A jagged crack
                // opens along one axis and thin fire sheets rise out of it, so
                // the hazard reads as a cut in the ground rather than a column.
                double widen = charge ? 0.15 + 0.35 * p : 0.30 + 0.70 * easeOut(p);
                double length = r * (charge ? 0.7 : 1.0);
                double prevX = -length, prevZ = 0;
                for (int i = 0; i <= 7; i++) {
                    double u = i / 7.0;
                    double x = -length + 2 * length * u;
                    double zig = (i % 2 == 0 ? 1 : -1) * 0.18 * r * widen;
                    double gap = 0.10 + widen * 0.22;
                    line(out, new Point(prevX, 0.05, prevZ, 0),
                            new Point(x, 0.05, zig, 0), 1, 0);
                    line(out, new Point(x, 0.05, zig - gap, 0),
                            new Point(x, 0.05, zig + gap, 0), 2, 0);
                    line(out, new Point(x, 0.05, zig, 1),
                            new Point(x, r * (0.35 + 0.75 * widen), zig, 1), 3, 1);
                    prevX = x;
                    prevZ = zig;
                }
                for (int i = 0; i < 5; i++) {
                    double x = -length + 2 * length * (i / 4.0);
                    double h = r * (0.25 + 0.65 * widen);
                    line(out, new Point(x, 0.06, -0.06, 2), new Point(x, h, 0.06, 2), 2, 2);
                }
            }
            case INFERNO_GEYSER -> {
                // N23-P1-08's vertical volumetric-style flame sheets: several
                // tall staggered ribbons around a hot base, so the column reads
                // as fire under pressure rather than as a rock shard cluster.
                double rise = charge ? 0.4 + 0.6 * p : 0.6 + 0.4 * easeOut(p);
                for (int sheet = 0; sheet < 6; sheet++) {
                    double a = sheet * TAU / 6 + spin * 0.06;
                    double baseR = r * 0.42;
                    double lean = r * 0.10 * (sheet % 2 == 0 ? 1 : -1);
                    double h = r * (0.55 + 0.12 * (sheet % 3)) * rise;
                    line(out, new Point(Math.cos(a) * baseR, 0.05, Math.sin(a) * baseR, 0),
                            new Point(Math.cos(a) * (baseR + lean), h,
                                    Math.sin(a) * (baseR + lean), 0), 5, sheet % 2);
                    line(out, new Point(Math.cos(a) * baseR * 0.55, 0.05, Math.sin(a) * baseR * 0.55, 1),
                            new Point(Math.cos(a) * baseR * 0.62, h * 0.82,
                                    Math.sin(a) * baseR * 0.62, 1), 4, 1);
                }
                polygon(out, 8, r * 0.36, 0.08, spin * 0.4, 2, 2);
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
                double closure = charge ? 1.0D - .65D * p : size;
                for (int chain = 0; chain < 6; chain++) {
                    double a = chain * TAU / 6 + spin * 0.08;
                    line(out, new Point(Math.cos(a) * r * closure, 1.1, Math.sin(a) * r * closure, 0),
                            new Point(Math.cos(a) * r * 0.16, 0.05, Math.sin(a) * r * 0.16, 0), 8, chain % 2);
                }
                polygon(out, 6, r * closure * 0.55, 0.04, spin * 0.12, 4, 1);
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
            case CHEST_BEAM -> {
                // 9H.02 shot 4, one authored silhouette: (a) a dense inner core
                // along the ray, (b) two counter-twisting outer helices giving the
                // shaft a real cross-section from the side, (c) rings that march
                // from the chest toward the tip so the shaft reads as travelling
                // energy rather than a clear rod, and (d) a separate expanding
                // impact crown at the far end on release/impact. The charge beat
                // compresses the same axis into a short hot socket, so the tell
                // and the shot belong to one family without looking identical.
                //
                // The shaft must span the real 26-block finite lane. `size` is
                // the authored unit scale (the same convention RIFT_LANCE uses
                // for its 2.4-block body), so the release reach is expressed in
                // blocks and peaks exactly on ChestBeamPolicy.BEAM_RANGE. The
                // charge beat stays a compressed socket: a short hot core with
                // the same rings, which is the tell, not the shot.
                double reach = charge ? 1.6 * size : 26.0 * size;
                int corePoints = charge ? 12 : 26;
                for (int i = 0; i < corePoints; i++) {
                    add(out, 0, 0, 0.12 + (reach - 0.12) * i / (corePoints - 1.0), 2);
                }
                for (int strand = 0; strand < 2; strand++) for (int i = 0; i < 14; i++) {
                    double u = i / 13.0;
                    double a = u * TAU * 1.35 + spin * (strand == 0 ? 1 : -1) + strand * Math.PI;
                    double s = (charge ? 0.30 : 0.24 + 0.10 * Math.sin(Math.PI * u)) * size;
                    add(out, Math.cos(a) * s, Math.sin(a) * s, 0.10 + reach * u, strand);
                }
                for (int ring = 0; ring < 3; ring++) for (int i = 0; i < 8; i++) {
                    double u = Math.min(1.0, (ring + p * 0.9) / 3.0);
                    double a = i * TAU / 8 + spin * 0.5;
                    double s = (0.34 + 0.12 * Math.sin(Math.PI * u)) * size;
                    add(out, Math.cos(a) * s, Math.sin(a) * s, 0.15 + reach * u, 1);
                }
                if (!charge) for (int i = 0; i < 12; i++) {
                    double a = i * TAU / 12;
                    double s = (0.35 + 0.55 * easeOut(p)) * size;
                    add(out, Math.cos(a) * s, Math.sin(a) * s, reach, 1);
                }
            }
            case KAGUNE_STAB -> {
                // N23-P1-06 residual: the stab used to be the RIFT_LANCE, so a
                // Kagune thrust and an ordinary rift projectile were drawn as
                // the same object. This is a single curved, base-heavy impaling
                // spine: it leaves the caster low, bows sideways and drives
                // forward to a barbed point -- the opposite taper and axis of
                // the straight, chest-anchored lance.
                double reach = (charge ? 1.7 : 2.5) * size;
                double lift = (charge ? 0.95 : 1.35) * size;
                for (int spine = 0; spine < 3; spine++) {
                    double fan = (spine - 1) * 0.22;
                    for (int i = 0; i < 14; i++) {
                        double u = i / 13.0;
                        double bow = Math.sin(u * Math.PI) * 0.60 * size;
                        add(out,
                                fan * (0.4 + u) + bow * 0.75,
                                lift * u * u + Math.sin(Math.PI * u) * 0.30 * size,
                                reach * u,
                                spine % 2);
                    }
                }
                for (int barb = -1; barb <= 1; barb += 2) {
                    line(out, new Point(0, lift, reach, 1),
                            new Point(barb * 0.55 * size, lift * 0.55,
                                    reach * 0.62, 1), 5, 1);
                    line(out, new Point(barb * 0.55 * size, lift * 0.55,
                                    reach * 0.62, 1),
                            new Point(barb * 0.30 * size, lift * 0.30,
                                    reach * 0.34, 2), 3, 2);
                }
                polygon(out, 5, r * 0.22 * size, 0.05, spin * 0.2, 2, 2);
            }
            case KAGUNE_ERUPTION -> {
                // N23-P1-06 residual: a genuine Kagune eruption, not the rock
                // geyser body. Four sinuous tentacles climb out of the floor in
                // an S, each ending in an inward fang, over a low thorned ring.
                // The S-curve is what separates it from the straight rock
                // shards and the vertical fire ribbons of the other hazards.
                double rise = (charge ? 1.1 + 0.9 * p : 1.7 + 0.7 * easeOut(p)) * size;
                for (int arm = 0; arm < 4; arm++) {
                    double a = arm * TAU / 4 + 0.25;
                    double swayDir = arm % 2 == 0 ? 1 : -1;
                    for (int i = 0; i < 12; i++) {
                        double u = i / 11.0;
                        double ring = r * (0.16 + 0.46 * u);
                        double sway = Math.sin(u * Math.PI * 1.5) * 0.42 * size * swayDir;
                        add(out,
                                Math.cos(a) * ring - Math.sin(a) * sway,
                                rise * u,
                                Math.sin(a) * ring + Math.cos(a) * sway,
                                arm % 2);
                    }
                    double tipRadius = r * 0.62;
                    double tipSide = Math.cos(a) * tipRadius;
                    double tipForward = Math.sin(a) * tipRadius;
                    line(out, new Point(tipSide, rise, tipForward, 1),
                            new Point(tipSide - Math.cos(a) * 0.55 * size,
                                    rise + 0.55 * size,
                                    tipForward - Math.sin(a) * 0.55 * size, 1), 4, 1);
                }
                polygon(out, 8, r * 0.34, 0.06, spin * 0.15, 3, 2);
            }
            case KAGUNE_SMALL -> {
                // The small attacking Kagune is a compact claw-knot: three short
                // tendrils curl out and hook back over a low thorn ring. It is
                // smaller and wider than the guardian splinter and carries no
                // grab volume, matching its melee-only role.
                double compact = (charge ? 0.72 + 0.28 * p : 1.05 - 0.10 * p) * size;
                for (int tendril = 0; tendril < 3; tendril++) {
                    double a = tendril * TAU / 3 + 0.2;
                    for (int i = 0; i < 10; i++) {
                        double u = i / 9.0;
                        double outward = r * (0.12 + 0.52 * Math.sin(u * Math.PI * 0.85));
                        double curl = r * 0.34 * u * u;
                        add(out,
                                Math.cos(a) * outward - Math.sin(a) * curl,
                                (0.20 + 1.15 * u - 0.28 * u * u) * compact,
                                Math.sin(a) * outward + Math.cos(a) * curl,
                                tendril % 2);
                    }
                }
                for (int thorn = 0; thorn < 6; thorn++) {
                    double a = thorn * TAU / 6 + spin * 0.08;
                    line(out, new Point(Math.cos(a) * r * 0.18, 0.05, Math.sin(a) * r * 0.18, 2),
                            new Point(Math.cos(a) * r * 0.46, 0.42 * compact,
                                    Math.sin(a) * r * 0.46, 2), 3, 2);
                }
            }
            case VOID_CRUSH -> {
                // A broken ground shock front: three concentric rings with
                // alternating gaps, so a grounded or jumping player can read a
                // lane out of it. It is deliberately a front, not a solid disk.
                double reach = (charge ? 0.35 + 0.40 * p : 0.45 + 0.75 * easeOut(p)) * r;
                for (int ring = 0; ring < 3; ring++) {
                    double ringD = reach * (0.45 + 0.28 * ring);
                    int segments = 10 + ring * 2;
                    for (int i = 0; i < segments; i += 2) {
                        double a0 = i * TAU / segments + ring * 0.25;
                        double a1 = (i + 1) * TAU / segments + ring * 0.25;
                        line(out, new Point(Math.cos(a0) * ringD, 0.06, Math.sin(a0) * ringD, ring % 2),
                                new Point(Math.cos(a1) * ringD, 0.06, Math.sin(a1) * ringD, ring % 2), 3, ring % 2);
                    }
                }
                // Falling shards above the front give the release real height.
                if (!charge) for (int shard = 0; shard < 6; shard++) {
                    double a = shard * TAU / 6 + spin * 0.4;
                    double s = reach * (0.20 + 0.10 * (shard % 3));
                    add(out, Math.cos(a) * s, 1.2 + 0.5 * (shard % 3), Math.sin(a) * s, 2);
                }
            }
            case CLAW_CHAIN -> {
                // Three steel claw arcs sweeping across the caster's front.
                for (int arc = 0; arc < 3; arc++) {
                    double half = 0.75 + 0.15 * arc, lift = 0.7 + 0.35 * arc;
                    for (int i = 0; i < 12; i++) {
                        double u = i / 11.0;
                        double a = -half + 2 * half * u;
                        add(out, Math.sin(a) * r * size, lift + Math.cos(a) * 0.35 * size,
                                Math.cos(a) * r * size * 0.55, arc % 2);
                    }
                }
                line(out, new Point(0, 0.05, 0, 2), new Point(0, 1.8 * size, 0, 2), 5, 2);
            }
            case DIMENSIONAL_CARVE -> {
                // A torn-space doorway plus the diagonal cut that leaves it.
                double halfWidth = 0.7 * size, height = 2.2 * size;
                for (int plane = 0; plane < 2; plane++) {
                    double z = plane * 0.4 * size;
                    line(out, new Point(-halfWidth, 0, z, 0), new Point(-halfWidth, height, z, 0), 6, 0);
                    line(out, new Point(halfWidth, 0, z, 0), new Point(halfWidth, height, z, 0), 6, 0);
                    line(out, new Point(-halfWidth, height, z, 1), new Point(halfWidth, height, z, 1), 6, 1);
                }
                for (int i = 0; i < 10; i++) {
                    double u = i / 9.0;
                    add(out, -halfWidth + 2 * halfWidth * u, height * (1 - u) + 0.1,
                            0.2 * size + u * 0.6 * size, 2);
                }
            }
            case SHOULDER_SLAM -> {
                // A compact shoulder burst: short radial spikes from one point.
                double burst = (charge ? 0.25 + 0.35 * p : 0.55 + 0.45 * easeOut(p)) * r;
                for (int spoke = 0; spoke < 8; spoke++) {
                    double a = spoke * TAU / 8 + spin * 0.2;
                    line(out, new Point(0, 0.7 * size, 0, 0),
                            new Point(Math.cos(a) * burst, 0.7 * size + Math.sin(a) * burst * 0.6,
                                    Math.sin(a) * burst, spoke % 2), 4, spoke % 2);
                }
            }
            case HUNT_DASH -> {
                // A long wake trail: shrinking rings behind the moving caster.
                double length = (charge ? 1.2 : 3.4) * size;
                for (int ring = 0; ring < 5; ring++) {
                    double z = -length * ring / 4.0;
                    double wakeR = r * (0.40 - 0.06 * ring) * (charge ? 0.7 : 1.0);
                    polygonAt(out, 8, wakeR, 0.7 * size, z, spin * 0.15, 4, ring % 2);
                }
            }
            case GRAB_SHOVE -> {
                // A close grapple: three short barbs snapped out and hooked back.
                double reach = (charge ? 0.8 : 1.4) * size;
                for (int barb = 0; barb < 3; barb++) {
                    double a = barb * TAU / 3 + 0.3;
                    for (int i = 0; i < 6; i++) {
                        double u = i / 5.0;
                        double s = reach * Math.sin(u * Math.PI * 0.8);
                        add(out, Math.cos(a) * s, 0.8 + u * 0.9 * size - u * u * 0.5 * size,
                                Math.sin(a) * s, barb % 2);
                    }
                }
            }
            case SEAL_LANCE -> {
                // Three tall spear lances fanned from the seal, each with a barb.
                for (int lane = -1; lane <= 1; lane++) {
                    double x = lane * 0.55 * size;
                    double height = (charge ? 1.6 : 3.0) * size;
                    line(out, new Point(x, 0.05, 0, 0), new Point(x * 0.6, height, 0.4 * size, 0), 8, 0);
                    line(out, new Point(x * 0.6, height, 0.4 * size, 1),
                            new Point(x * 0.9, height * 0.7, 0.9 * size, 1), 3, 1);
                    line(out, new Point(x * 0.6, height, 0.4 * size, 1),
                            new Point(x * 0.3, height * 0.7, 0.9 * size, 1), 3, 1);
                }
            }
            case RING_OF_KAGUNE -> {
                // An expanding ring of rising tentacle spikes in validated gaps.
                double ringRadius = (charge ? 0.45 + 0.25 * p : 0.6 + 0.5 * easeOut(p)) * r;
                int spikes = 10;
                for (int i = 0; i < spikes; i++) {
                    double a = i * TAU / spikes + spin * 0.12;
                    double h = (charge ? 0.8 : 1.9) * size * (0.7 + 0.3 * (i % 3));
                    line(out, new Point(Math.cos(a) * ringRadius, 0.05, Math.sin(a) * ringRadius, 0),
                            new Point(Math.cos(a) * (ringRadius + 0.12), h,
                                    Math.sin(a) * (ringRadius + 0.12), 0), 5, i % 2);
                }
                polygon(out, 12, ringRadius, 0.05, spin * 0.2, 3, 2);
            }
            case AMBUSH_GRAB -> {
                // Under-player tendrils: four reaching roots that curl inward.
                double rise = (charge ? 0.7 + 0.5 * p : 1.4 + 0.6 * easeOut(p)) * size;
                for (int root = 0; root < 4; root++) {
                    double a = root * TAU / 4 + 0.4;
                    for (int i = 0; i < 8; i++) {
                        double u = i / 7.0;
                        double s = r * (0.10 + 0.30 * Math.sin(u * Math.PI * 0.7));
                        add(out, Math.cos(a) * s - Math.sin(a) * 0.25 * size * u,
                                rise * u, Math.sin(a) * s + Math.cos(a) * 0.25 * size * u, root % 2);
                    }
                }
                polygon(out, 8, r * 0.22, 0.04, spin * 0.1, 3, 2);
            }
            case INFERNAL_SURGE -> {
                // A moving wall of fire: stacked, staggered flame panels.
                double wallHeight = (charge ? 1.0 : 2.4) * size;
                int panels = 7;
                for (int panel = 0; panel < panels; panel++) {
                    double x = (panel / (double) (panels - 1) - 0.5) * 2 * r * 0.7;
                    double lean = (panel % 2 == 0 ? 1 : -1) * 0.18 * size;
                    line(out, new Point(x, 0.05, 0, 0),
                            new Point(x + lean, wallHeight * (0.7 + 0.3 * (panel % 3)), 0, 0), 5, panel % 2);
                }
            }
            case INFERNO_CURTAIN -> {
                // Hanging flame panels falling from above the arena.
                double drop = (charge ? 1.0 : 2.6) * size;
                for (int panel = 0; panel < 6; panel++) {
                    double a = panel * TAU / 6 + spin * 0.1;
                    double s = r * 0.65;
                    line(out, new Point(Math.cos(a) * s, drop, Math.sin(a) * s, 1),
                            new Point(Math.cos(a) * s, Math.max(0.05, drop - 2.2 * size),
                                    Math.sin(a) * s, 1), 6, panel % 2);
                }
            }
            case MIRROR_CARNAGE -> {
                // Shattered mirror planes: four offset facets, none camera-locked.
                for (int plane = 0; plane < 4; plane++) {
                    double a = plane * TAU / 4 + spin * 0.15;
                    double s = r * (0.35 + 0.25 * (plane % 2));
                    for (int i = 0; i < 5; i++) {
                        double u = i / 4.0;
                        add(out, Math.cos(a) * s + (u - 0.5) * 0.5 * size,
                                0.6 + u * 1.4 * size, Math.sin(a) * s + (u - 0.5) * 0.5 * size,
                                plane % 2);
                    }
                }
            }
            case EARTHQUAKE_WAVE -> {
                // An expanding ground shock ring with radial cracks.
                double reach = (charge ? 0.4 + 0.4 * p : 0.5 + 0.9 * easeOut(p)) * r;
                polygon(out, 16, reach, 0.04, spin * 0.2, 3, 0);
                for (int crack = 0; crack < 8; crack++) {
                    double a = crack * TAU / 8 + 0.2;
                    line(out, new Point(Math.cos(a) * reach * 0.3, 0.05, Math.sin(a) * reach * 0.3, 1),
                            new Point(Math.cos(a) * reach, 0.05, Math.sin(a) * reach, 1), 4, crack % 2);
                }
            }
            case SPIRE_HARBINGER -> {
                // A stacked skeleton spire: three narrowing tiers.
                double base = r * 0.8, height = (charge ? 1.8 : 3.6) * size;
                for (int tier = 0; tier < 3; tier++) {
                    double tierBase = height * tier / 3.0;
                    double tierRadius = base * (1.0 - 0.22 * tier);
                    polygonAt(out, 6, tierRadius, tierBase, 0, spin * (tier % 2 == 0 ? 0.1 : -0.1), 4, tier % 2);
                }
                line(out, new Point(0, 0.05, 0, 2), new Point(0, height, 0, 2), 6, 2);
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
    /** A horizontal ring lifted to {@code height} and pushed forward by {@code depth}. */
    private static void polygonAt(List<Point> out, int sides, double radius, double height,
                                  double depth, double spin, int samples, int layer) {
        for (int edge = 0; edge < sides; edge++) {
            double a = edge * TAU / sides + spin, b = (edge + 1) * TAU / sides + spin;
            line(out, new Point(Math.cos(a) * radius, height, depth + Math.sin(a) * radius, layer),
                    new Point(Math.cos(b) * radius, height, depth + Math.sin(b) * radius, layer), samples, layer);
        }
    }
}
