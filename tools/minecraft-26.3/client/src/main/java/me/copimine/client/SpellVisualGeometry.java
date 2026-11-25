package me.copimine.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Authored, deterministic 3D geometry for the V5.1 signature casts.
 *
 * <p>This is a presentation-only description: it never chooses a target, moves
 * a player, applies damage or owns a hit volume. The server keeps the real
 * hitboxes; the client reconstructs the visible arms, jaws, wedges and shells
 * from a compact {@code v51-<spell>-<seed>} world cue plus the single anchor /
 * direction pair the existing bounded beam transport already carries.</p>
 *
 * <p>Geometry is expressed in a local frame: {@code +x} runs along the first
 * spoke / the cast direction, {@code +z} is lateral and {@code +y} is up. The
 * renderer maps that frame onto the server anchors, so no authored point is a
 * world coordinate and no stale packet can leave geometry after cleanup.</p>
 *
 * <p>Every family is continuously animated from the cast clock: arms ripple,
 * teeth open and close, sigils counter-rotate, motes spiral, shells breathe and
 * pressure rings expand. The animation is derived only from {@code elapsedMillis}
 * and the server-authored seed, so the same instant always rebuilds the exact
 * same mesh and no client state can drift away from the cue lifetime.</p>
 */
public final class SpellVisualGeometry {

    /** The authored signature casts that have a bespoke local mesh. */
    public enum Spell {
        CROWN("crown"),
        CIRCLE("circle"),
        FANG_LINE("fangline"),
        WARDEN_SWEEP("sweep"),
        ECHO_SPHERE("sphere"),
        SCULK_TRACE("sculk"),
        GRAVITY("gravity"),
        GUARDIAN_FAN("fan"),
        GUARDIAN_HEAVY("heavy"),
        SEPARATION("separation"),
        // N23-P1-05: the signature chest beam's own full-length cross-section.
        BEAM("beam"),
        // N23-P1-08: the Phase 3 rock/obsidian eruption column.
        GEYSER("geyser");

        private final String token;

        Spell(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        /** Exact, case-sensitive token lookup used by the cue parser. */
        public static Spell fromToken(String candidate) {
            if (candidate == null) {
                return null;
            }
            for (Spell spell : values()) {
                if (spell.token.equals(candidate)) {
                    return spell;
                }
            }
            return null;
        }
    }

    /** Client quality tier; it only changes sample density, never the footprint. */
    public enum Quality {
        FULL(12),
        BALANCED(8),
        REDUCED(5);

        private final int samples;

        Quality(int samples) {
            this.samples = samples;
        }

        public int samples() {
            return samples;
        }
    }

    /**
     * Readability role of a stroke; the renderer picks alpha / brightness.
     *
     * <p>{@code volumetric} marks the hero strokes - arms, jaws, wedges, veins
     * and force fields - that the renderer draws as a crossed pair of ribbons so
     * they keep real volume from every camera angle. Ground lanes, shell rings
     * and pressure rings stay single-sided because they already read as 3D
     * through their own stacked geometry, and a second ribbon there would only
     * add fill cost. Neither choice changes the authored half-width, so the
     * visible hazard silhouette is identical on every tier.</p>
     */
    public enum Style {
        ARM(true),
        JAW(true),
        LANE(false),
        WEDGE(true),
        SHELL(false),
        TRACE(true),
        FIELD(true),
        RING(false);

        private final boolean volumetric;

        Style(boolean volumetric) {
            this.volumetric = volumetric;
        }

        /** True when this role is drawn as a crossed pair of ribbons. */
        public boolean volumetric() {
            return volumetric;
        }
    }

    public record Point(double x, double y, double z) {
        public double horizontalLength() {
            return Math.sqrt(x * x + z * z);
        }
    }

    /** One polyline with a tapered half-width and a readable brightness/alpha. */
    public record Stroke(Style style, List<Point> points, List<Float> halfWidths,
                         float brightness, float alpha) {
        public Stroke {
            points = List.copyOf(points);
            halfWidths = List.copyOf(halfWidths);
            if (points.size() != halfWidths.size() || points.size() < 2) {
                throw new IllegalArgumentException("stroke needs one width per point");
            }
        }
    }

    public static final int CROWN_SPOKES = 5;
    public static final double CROWN_CURVE_DEGREES = 26.0D;
    public static final double CROWN_LENGTH_MIN = 7.0D;
    public static final double CROWN_LENGTH_MAX = 9.0D;
    public static final double CROWN_TEETH_SPACING_BLOCKS = 1.25D;
    public static final double CROWN_ROTATION_DEG_PER_SECOND = 10.0D;

    public static final double CIRCLE_RADIUS = 2.5D;
    public static final int CIRCLE_JAWS_MIN = 7;
    public static final int CIRCLE_JAWS_MAX = 9;
    public static final double CIRCLE_GAP_BLOCKS_MIN = 2.0D;

    public static final double FANG_LINE_PATH_BLOCKS = 3.0D;
    public static final int FANG_LINE_TEETH = 3;

    public static final double WARDEN_SWEEP_FORWARD = 4.0D;
    public static final double WARDEN_SWEEP_WIDTH = 5.0D;

    public static final double ECHO_SPHERE_RADIUS = 6.0D;

    public static final double GUARDIAN_FAN_RADIUS = 4.8D;
    public static final double GUARDIAN_FAN_HALF_ANGLE = 62.5D;
    public static final double GUARDIAN_HEAVY_FORWARD = 4.6D;
    public static final double GUARDIAN_HEAVY_HALF_WIDTH = 2.4D;
    public static final double SEPARATION_RADIUS = 4.8D;

    // N23-P1-05: the chest beam is the signature ultimate. The server damages a
    // real capsule of radius ChestBeamPolicy.BEAM_RADIUS either side of the
    // locked lane, up to ChestBeamPolicy.BEAM_RANGE long. The authored visual
    // must read as a substantial shaft that HUGS that exact capsule from the
    // inside, so the outside of the drawn silhouette never promises more than
    // the hit volume and never reads as a hairline narrower than the damage.
    public static final double BEAM_RADIUS = 1.7D;
    public static final double BEAM_VISUAL_RADIUS = 1.62D;
    public static final double BEAM_MIN_LENGTH = 2.0D;
    public static final double BEAM_MAX_LENGTH = 26.0D;

    // N23-P1-08: the Phase 3 eruption column. The server stages real reversible
    // blocks under an 8..12-block high damage column of radius 1.9 and restores
    // every block afterwards. The authored visual is a rock/obsidian throat that
    // opens fissures, chokes with volcanic flame sheets, throws ash and debris,
    // then collapses -- all rebuilt from the cast clock so it stays animated and
    // stays inside the real hit cylinder.
    public static final double GEYSER_MAX_HEIGHT = 12.0D;
    public static final double GEYSER_MIN_HEIGHT = 6.0D;
    public static final double GEYSER_VISUAL_RADIUS = 1.58D;
    /** One authored eruption cycle; the clock loops so the column keeps living. */
    public static final long GEYSER_PERIOD_MILLIS = 2400L;

    /** Prefix of the compact authored-cast cue carried by the beam transport. */
    public static final String CUE_PREFIX = "v51-";

    /**
     * Parse the authored spell from an event world instance. The suffix must be
     * exactly {@code v51-<token>-<seed>} so a malformed or truncated key cannot
     * guess a mesh into existence.
     */
    public static Spell spellCue(String instance) {
        String[] parts = cueParts(instance);
        return parts == null ? null : Spell.fromToken(parts[0]);
    }

    /** Stable cosmetic seed from the same cue, or {@code 0} when malformed. */
    public static long cueSeed(String instance) {
        String[] parts = cueParts(instance);
        if (parts == null) {
            return 0L;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String[] cueParts(String instance) {
        if (instance == null || instance.isBlank() || instance.length() > 160) {
            return null;
        }
        int marker = instance.indexOf(":world:");
        if (marker <= 0 || instance.indexOf(":world:", marker + 7) >= 0) {
            return null;
        }
        String key = instance.substring(marker + 7);
        if (!key.startsWith(CUE_PREFIX) || key.length() > CUE_PREFIX.length() + 32) {
            return null;
        }
        String rest = key.substring(CUE_PREFIX.length());
        int dash = rest.lastIndexOf('-');
        if (dash <= 0 || dash >= rest.length() - 1 || rest.length() - dash - 1 > 12) {
            return null;
        }
        String token = rest.substring(0, dash);
        String seed = rest.substring(dash + 1);
        for (int index = 0; index < seed.length(); index++) {
            char digit = seed.charAt(index);
            if (digit < '0' || digit > '9') {
                return null;
            }
        }
        if (Spell.fromToken(token) == null) {
            return null;
        }
        return new String[]{token, seed};
    }

    /** Hard caps: a malformed cue can never become an unbounded mesh. */
    public static final int MAX_STROKES = 96;
    public static final int MAX_POINTS_PER_STROKE = 48;
    public static final int MAX_SEGMENTS = 1024;
    public static final double MAX_REACH_BLOCKS = 10.5D;

    /** Per-spell footprint bound: the arena gravity field, the 26-block beam and the 12-block column exceed the local reach. */
    public static double maxReach(Spell spell) {
        if (spell == Spell.GRAVITY) {
            return 48.0D;
        }
        if (spell == Spell.BEAM) {
            return BEAM_MAX_LENGTH + 1.0D;
        }
        return spell == Spell.GEYSER ? GEYSER_MAX_HEIGHT + 1.0D : MAX_REACH_BLOCKS;
    }

    private SpellVisualGeometry() {
    }

    /**
     * Build the local strokes for one cast instant.
     *
     * <p>Section 9A.03(C): the family builders own the shapes, while
     * {@link SpellVisualEnvelope} owns the cast arc. The authored clock is
     * re-timed so each family winds up, releases and settles on its own tempo,
     * and the authored heat curve scales only material brightness - never a
     * stroke point or half-width - so the announced silhouette is unchanged at
     * every beat of the cast.</p>
     *
     * @param spell        authored spell family
     * @param elapsedMillis monotonic time since the cast's first authored frame
     * @param seed         server-authored stable cast seed (cosmetic only)
     * @param param        authored reach / radius in blocks (clamped per spell)
     * @param quality      sample density
     */
    public static List<Stroke> build(Spell spell, long elapsedMillis, long seed,
                                     double param, Quality quality) {
        if (spell == null) {
            return List.of();
        }
        long elapsed = Math.max(0L, Math.min(120_000L, elapsedMillis));
        List<Stroke> strokes = authored(spell, SpellVisualEnvelope.tempoMillis(spell, elapsed),
                seed, param, quality);
        double heat = SpellVisualEnvelope.heat(spell, elapsed);
        if (heat < 0.999D) {
            float factor = (float) heat;
            List<Stroke> ignited = new ArrayList<>(strokes.size());
            for (Stroke stroke : strokes) {
                ignited.add(new Stroke(stroke.style(), stroke.points(), stroke.halfWidths(),
                        Math.max(0.0F, Math.min(1.0F, stroke.brightness() * factor)),
                        stroke.alpha()));
            }
            strokes = ignited;
        }
        return strokes;
    }

    /**
     * Shape-only build at an already-authored cast clock, without the ignition
     * curve. The heat pass in {@link #build} may only rescale material
     * brightness, so this is what the tests compare against to prove the
     * silhouette never depends on the ignition.
     */
    static List<Stroke> authored(Spell spell, long authoredMillis, long seed,
                                 double param, Quality quality) {
        if (spell == null) {
            return List.of();
        }
        long elapsed = Math.max(0L, Math.min(120_000L, authoredMillis));
        Quality tier = quality == null ? Quality.BALANCED : quality;
        double reach = Double.isFinite(param) ? Math.abs(param) : 0.0D;
        List<Stroke> strokes = switch (spell) {
            case CROWN -> crown(elapsed, seed, reach, tier);
            case CIRCLE -> circle(elapsed, seed, reach, tier);
            case FANG_LINE -> fangLine(elapsed, reach, tier);
            case WARDEN_SWEEP -> wardenSweep(elapsed, tier);
            case ECHO_SPHERE -> echoSphere(elapsed, reach, tier);
            case SCULK_TRACE -> sculkTrace(elapsed, seed, reach, tier);
            case GRAVITY -> gravity(elapsed, reach, tier);
            case GUARDIAN_FAN -> guardianFan(elapsed, reach, tier);
            case GUARDIAN_HEAVY -> guardianHeavy(elapsed, tier);
            case SEPARATION -> separation(elapsed, tier);
            case BEAM -> beam(elapsed, seed, reach, tier);
            case GEYSER -> geyser(elapsed, seed, reach, tier);
        };
        return bounded(strokes, maxReach(spell));
    }

    /** A traversable escape sector must stay at least {@code gapBlocks} wide. */
    public static double requiredGapDegrees(double radius, double gapBlocks) {
        double r = Math.max(0.5D, Math.min(MAX_REACH_BLOCKS, radius));
        double g = Math.max(0.5D, Math.min(2.0D * r - 0.01D, gapBlocks));
        return Math.toDegrees(2.0D * Math.asin(g / (2.0D * r)));
    }

    /** Deterministic jaw ring; never returns a closed prison. */
    public static List<Point[]> circleJaws(double radius, int jawCount, double gapDegrees) {
        double r = Math.max(1.2D, Math.min(MAX_REACH_BLOCKS, radius));
        int count = Math.max(CIRCLE_JAWS_MIN, Math.min(CIRCLE_JAWS_MAX, jawCount));
        double required = requiredGapDegrees(r, CIRCLE_GAP_BLOCKS_MIN);
        double gap = Math.max(required, Math.min(180.0D, gapDegrees));
        double start = gap / 2.0D;
        double step = (360.0D - gap) / count;
        List<Point[]> jaws = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double angle = Math.toRadians(start + index * step);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double root = r;
            double tip = r - 0.7D;
            Point left = new Point(cos * root - sin * 0.12D, 0.06D, sin * root + cos * 0.12D);
            Point point = new Point(cos * tip, 0.06D, sin * tip);
            Point right = new Point(cos * root + sin * 0.12D, 0.06D, sin * root - cos * 0.12D);
            jaws.add(new Point[]{left, point, right});
        }
        return jaws;
    }

    // ------------------------------------------------------------------
    // CROWN: five rippling arms, breathing fangs, a counter-rotating inner
    // crown, orbiting shard ghosts and a rising spiral of embers.
    // ------------------------------------------------------------------
    private static List<Stroke> crown(long elapsedMillis, long seed, double requested,
                                      Quality quality) {
        double length = clamp(requested > 0.0D ? requested : CROWN_LENGTH_MAX,
                CROWN_LENGTH_MIN, CROWN_LENGTH_MAX);
        double rotation = spinDegrees(elapsedMillis, CROWN_ROTATION_DEG_PER_SECOND,
                splitUnit(seed, 0L) * 12.0D);
        double rotateRadians = Math.toRadians(rotation);
        double breathe = 0.5D + 0.5D * Math.sin(rotateRadians * 1.4D);
        int samples = quality.samples();
        int teethPerArm = Math.min(4, Math.max(1, (int) Math.floor(length / CROWN_TEETH_SPACING_BLOCKS)));
        List<Stroke> strokes = new ArrayList<>(72);
        for (int spoke = 0; spoke < CROWN_SPOKES; spoke++) {
            double angle = rotation + spoke * (360.0D / CROWN_SPOKES);
            List<Point> arm = new ArrayList<>(samples + 1);
            List<Float> widths = new ArrayList<>(samples + 1);
            for (int index = 0; index <= samples; index++) {
                double t = index / (double) samples;
                double curved = angle + CROWN_CURVE_DEGREES * (t * t - t * 0.5D);
                double radians = Math.toRadians(curved);
                double distance = length * t;
                // The vertical ripple is zero at both ends, so the authored arm
                // length and the verified rotation delta never drift.
                double lift = Math.sin(Math.PI * t) * 0.55D + t * 0.25D
                        + Math.sin(Math.PI * t) * 0.16D
                        * Math.sin(2.0D * Math.PI * 1.15D * t - rotateRadians * 3.0D);
                arm.add(new Point(Math.cos(radians) * distance, lift, Math.sin(radians) * distance));
                double taper = 0.115D - 0.075D * t;
                taper *= 1.0D + 0.12D * Math.sin(Math.PI * t) * Math.sin(rotateRadians * 2.2D);
                widths.add((float) Math.max(0.03D, taper));
            }
            strokes.add(new Stroke(Style.ARM, arm, widths, 1.0F, 0.88F));
            for (int tooth = 1; tooth <= teethPerArm; tooth++) {
                double t = Math.min(0.94D, 0.35D + tooth * (0.55D / Math.max(1, teethPerArm)));
                double open = 1.0D + 0.10D * Math.sin(rotateRadians * 2.5D + tooth * 0.7D);
                strokes.add(armTooth(angle, length, t, open));
            }
        }
        strokes.add(ring(1.6D, 18, 0.075D, Style.RING, 0.55F, 0.50F));
        strokes.add(ring(2.1D, 18, 0.040D, Style.RING, 0.40F, 0.35F));
        // Counter-rotating inner halos turn the cast into a living sigil: one
        // band spins against the arms, one rides above the base.
        strokes.add(rotatedRing(length * 0.42D, 0.90D, 20, 0.028D, Style.RING,
                0.55F, 0.45F, -rotation * 1.6D));
        strokes.add(rotatedRing(length * 0.30D, 1.55D, 16, 0.022D, Style.RING,
                0.45F, 0.38F, rotation * 2.2D));
        // Vertically rising crest blades ride the arm tips, so the rotation is
        // legible from the side as well as from above.
        for (int spoke = 0; spoke < CROWN_SPOKES; spoke++) {
            double angle = rotation + spoke * (360.0D / CROWN_SPOKES);
            double tipRadians = Math.toRadians(angle + CROWN_CURVE_DEGREES * 0.5D);
            double tipX = Math.cos(tipRadians) * length;
            double tipZ = Math.sin(tipRadians) * length;
            double height = 1.15D + 0.55D * breathe;
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(tipX, 0.35D, tipZ), new Point(tipX, height, tipZ)),
                    List.of(0.07F, 0.012F), 1.0F, 0.90F));
        }
        // One ghost chevron per arm, orbiting faster than the arms themselves.
        for (int spoke = 0; spoke < CROWN_SPOKES; spoke++) {
            double angle = rotation * 1.35D + spoke * (360.0D / CROWN_SPOKES);
            strokes.add(orbitChevron(angle, length * 0.78D, 1.0D + 0.5D * breathe,
                    0.42D, 0.90F, 0.78F));
        }
        int motes = quality == Quality.REDUCED ? 4 : quality == Quality.BALANCED ? 6 : 8;
        for (int mote = 0; mote < motes; mote++) {
            double phase = frac(elapsedMillis / 1800.0D + mote / (double) motes);
            double angle = Math.toRadians(rotation * 0.8D + mote * 137.5D);
            double radius = length * (0.30D + 0.50D * phase);
            double y = 0.25D + 2.2D * phase;
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(Math.cos(angle) * radius, y, Math.sin(angle) * radius),
                            new Point(Math.cos(angle) * radius, y + 0.26D,
                                    Math.sin(angle) * radius)),
                    List.of(0.020F, 0.004F), 0.85F, 0.75F));
        }
        strokes.add(rotatedRing(1.15D, 0.14D, 3, 0.035D, Style.LANE, 0.70F, 0.60F,
                rotation * 2.6D));
        strokes.add(rotatedRing(1.15D, 0.14D, 3, 0.035D, Style.LANE, 0.70F, 0.60F,
                rotation * 2.6D + 180.0D));
        burst(strokes, length * 0.24D, 8, elapsedMillis, 900.0D, 1.0F, 0.80F);
        return strokes;
    }

    private static Stroke armTooth(double angleDegrees, double length, double t, double open) {
        double curved = angleDegrees + CROWN_CURVE_DEGREES * (t * t - t * 0.5D);
        double radians = Math.toRadians(curved);
        double distance = length * t;
        double lift = Math.sin(Math.PI * t) * 0.55D + t * 0.25D;
        double cx = Math.cos(radians) * distance;
        double cz = Math.sin(radians) * distance;
        double tangentX = Math.cos(radians + Math.PI / 2.0D);
        double tangentZ = Math.sin(radians + Math.PI / 2.0D);
        double inwardX = -Math.cos(radians);
        double inwardZ = -Math.sin(radians);
        double tangent = 0.30D * open;
        double inward = 0.52D * open;
        List<Point> points = List.of(
                new Point(cx + tangentX * tangent, lift - 0.02D, cz + tangentZ * tangent),
                new Point(cx + inwardX * inward, lift + 0.10D, cz + inwardZ * inward),
                new Point(cx - tangentX * tangent, lift - 0.02D, cz - tangentZ * tangent));
        return new Stroke(Style.JAW, points, List.of(0.05F, 0.09F, 0.05F), 1.0F, 0.90F);
    }

    // ------------------------------------------------------------------
    // CIRCLE: breathing jaw ring, expanding pressure pulses, orbiting wisps,
    // a counter-rotating rune dial and vertical blades at the jaw roots.
    // ------------------------------------------------------------------
    private static List<Stroke> circle(long elapsedMillis, long seed, double requested,
                                       Quality quality) {
        double radius = clamp(requested > 0.0D ? requested : CIRCLE_RADIUS, 1.8D, 4.0D);
        int jaws = quality == Quality.REDUCED ? CIRCLE_JAWS_MIN : CIRCLE_JAWS_MAX;
        double gap = requiredGapDegrees(radius, CIRCLE_GAP_BLOCKS_MIN) + 4.0D;
        double spin = spinDegrees(elapsedMillis, 6.0D, splitUnit(seed, 1L) * 360.0D);
        double open = 1.0D + 0.035D * Math.sin(Math.toRadians(spin * 2.4D));
        List<Stroke> strokes = new ArrayList<>(56);
        for (Point[] jaw : circleJaws(radius, jaws, gap)) {
            strokes.add(new Stroke(Style.JAW,
                    List.of(scaleRadial(jaw[0], open), scaleRadial(jaw[1], open),
                            scaleRadial(jaw[2], open)),
                    List.of(0.05F, 0.09F, 0.05F), 1.0F, 0.92F));
        }
        // The visible gap arc is drawn thinner so the escape sector reads clearly.
        strokes.add(gapArc(radius, gap, 0.035D));
        strokes.add(ring(radius * 0.55D, 16, 0.03D, Style.RING, 0.45F, 0.35F));
        strokes.add(rotatedRing(radius * 0.72D, 0.09D, 20, 0.028D, Style.RING,
                0.50F, 0.42F, -spin));
        int spokes = quality == Quality.REDUCED ? 4 : 8;
        for (int spoke = 0; spoke < spokes; spoke++) {
            double angle = Math.toRadians(spin + spoke * (360.0D / spokes));
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(cos * radius * 0.55D, 0.08D, sin * radius * 0.55D),
                            new Point(cos * radius * 0.96D, 0.08D, sin * radius * 0.96D)),
                    List.of(0.03F, 0.012F), 0.70F, 0.55F));
        }
        // Three outrushing pressure rings, phase-shifted so one is always moving.
        for (int pulse = 0; pulse < 3; pulse++) {
            double phase = frac(elapsedMillis / 1400.0D + pulse / 3.0D);
            double pulseRadius = radius * (0.32D + 0.64D * phase);
            double halfWidth = 0.020D * (1.0D - phase) + 0.006D;
            strokes.add(ringAt(pulseRadius, 0.07D, 22, halfWidth, Style.RING,
                    0.90F, (float) (0.55D * (1.0D - phase))));
        }
        // Counter-spinning wisps hug the inner ring like a rune crown.
        int wisps = quality == Quality.REDUCED ? 4 : 8;
        for (int wisp = 0; wisp < wisps; wisp++) {
            double angle = -spin * 1.4D + wisp * (360.0D / wisps);
            double radiusWisp = radius * (0.80D
                    + 0.10D * Math.sin(Math.toRadians(spin * 3.0D + wisp * 45.0D)));
            strokes.add(orbitChevron(angle, radiusWisp, 0.50D, 0.22D, 0.85F, 0.70F));
        }
        // Radial rune ticks rotate with the dial but never cross the jaw ring.
        int ticks = quality == Quality.REDUCED ? 6 : 12;
        for (int tick = 0; tick < ticks; tick++) {
            double angle = Math.toRadians(spin * 1.1D + tick * (360.0D / ticks));
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            strokes.add(new Stroke(Style.LANE,
                    List.of(new Point(cos * radius * 0.42D, 0.10D, sin * radius * 0.42D),
                            new Point(cos * radius * 0.54D, 0.10D, sin * radius * 0.54D)),
                    List.of(0.022F, 0.010F), 0.65F, 0.50F));
        }
        // Four short walls rise from the dial so the seal reads in 3D.
        for (int blade = 0; blade < 4; blade++) {
            double angle = Math.toRadians(spin * 0.7D + 90.0D + blade * 90.0D);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double base = radius * 0.88D;
            double height = 1.25D + 0.45D * Math.sin(Math.toRadians(spin * 2.0D + blade * 70.0D));
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(cos * base, 0.08D, sin * base),
                            new Point(cos * base, height, sin * base)),
                    List.of(0.030F, 0.006F), 0.85F, 0.70F));
        }
        return strokes;
    }

    // ------------------------------------------------------------------
    // FANG LINE: a travelling channel, three breathing fangs, side rakes
    // and a forward-moving pulse.
    // ------------------------------------------------------------------
    private static List<Stroke> fangLine(long elapsedMillis, double requested, Quality quality) {
        double path = clamp(requested > 0.0D ? requested : FANG_LINE_PATH_BLOCKS, 1.5D, 4.5D);
        List<Stroke> strokes = new ArrayList<>(16);
        int samples = Math.max(3, quality.samples());
        List<Point> lane = new ArrayList<>(samples + 1);
        List<Float> widths = new ArrayList<>(samples + 1);
        double flow = frac(elapsedMillis / 620.0D);
        for (int index = 0; index <= samples; index++) {
            double t = index / (double) samples;
            double x = 0.4D + path * t;
            lane.add(new Point(x, 0.05D, 0.0D));
            double swell = 0.030D + 0.020D * Math.abs(Math.sin(Math.PI * (t - flow)));
            widths.add((float) swell);
        }
        strokes.add(new Stroke(Style.LANE, lane, widths, 0.60F, 0.50F));
        for (int tooth = 0; tooth < FANG_LINE_TEETH; tooth++) {
            double x = 0.7D + tooth * (path / FANG_LINE_TEETH);
            double open = 1.0D + 0.10D * Math.sin(Math.toRadians(elapsedMillis * 0.30D + tooth * 60.0D));
            List<Point> jaw = List.of(new Point(x - 0.22D, 0.06D, -0.36D * open),
                    new Point(x + 0.30D, 0.10D, 0.0D),
                    new Point(x - 0.22D, 0.06D, 0.36D * open));
            strokes.add(new Stroke(Style.JAW, jaw, List.of(0.05F, 0.085F, 0.05F), 1.0F, 0.92F));
        }
        double endX = 0.4D + path;
        for (int side = -1; side <= 1; side += 2) {
            strokes.add(new Stroke(Style.LANE,
                    List.of(new Point(0.45D, 0.05D, side * 0.40D),
                            new Point(endX, 0.05D, side * 0.55D)),
                    List.of(0.020F, 0.008F), 0.55F, 0.42F));
        }
        // Two chevrons race down the lane; they are decoration only and stop
        // exactly at the authored end of the path.
        for (int pulse = 0; pulse < 2; pulse++) {
            double t = frac(elapsedMillis / 780.0D + pulse * 0.5D);
            double x = 0.4D + path * t;
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(x - 0.14D, 0.07D, -0.24D),
                            new Point(x, 0.11D, 0.0D),
                            new Point(x - 0.14D, 0.07D, 0.24D)),
                    List.of(0.020F, 0.045F, 0.020F), 0.90F, 0.75F));
        }
        return strokes;
    }

    // ------------------------------------------------------------------
    // WARDEN SWEEP: the committed 5x4 box plus vertical edge curtains,
    // rolling chevrons and forward-tumbling dust.
    // ------------------------------------------------------------------
    private static List<Stroke> wardenSweep(long elapsedMillis, Quality quality) {
        double forward = WARDEN_SWEEP_FORWARD;
        double halfWidth = WARDEN_SWEEP_WIDTH / 2.0D;
        List<Stroke> strokes = new ArrayList<>(16);
        strokes.add(new Stroke(Style.WEDGE,
                List.of(new Point(0.6D, 0.06D, -halfWidth), new Point(forward, 0.06D, -halfWidth),
                        new Point(forward, 0.10D, halfWidth), new Point(0.6D, 0.06D, halfWidth),
                        new Point(0.6D, 0.06D, -halfWidth)),
                List.of(0.05F, 0.06F, 0.06F, 0.05F, 0.05F), 1.0F, 0.92F));
        int samples = Math.max(4, quality.samples());
        List<Point> crush = new ArrayList<>(samples + 1);
        List<Float> widths = new ArrayList<>(samples + 1);
        for (int index = 0; index <= samples; index++) {
            double t = index / (double) samples;
            double z = -halfWidth + WARDEN_SWEEP_WIDTH * t;
            double arch = Math.sin(Math.PI * t) * 0.45D;
            crush.add(new Point(forward, 0.10D + arch, z));
            widths.add(0.06F);
        }
        strokes.add(new Stroke(Style.WEDGE, crush, widths, 1.0F, 0.95F));
        // Vertical curtains mark the exact lateral limits without widening them.
        for (int side = -1; side <= 1; side += 2) {
            double curtain = 1.9D + 0.5D * Math.sin(Math.toRadians(elapsedMillis * 0.18D + side * 40.0D));
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(forward, 0.10D, side * halfWidth),
                            new Point(forward, curtain, side * halfWidth)),
                    List.of(0.050F, 0.010F), 0.90F, 0.75F));
        }
        for (int chevron = 0; chevron < 4; chevron++) {
            double x = 1.3D + chevron * 0.8D;
            double wave = Math.sin(Math.toRadians(elapsedMillis * 0.22D + chevron * 55.0D));
            // The tip stops at the authored 4.0-block contact edge: a decorative
            // chevron may never draw past the committed sweep volume.
            List<Point> line = List.of(new Point(x, 0.07D, -halfWidth * 0.72D),
                    new Point(Math.min(forward, x + 0.30D), 0.07D + 0.02D * wave, 0.0D),
                    new Point(x, 0.07D, halfWidth * 0.72D));
            strokes.add(new Stroke(Style.LANE, line, List.of(0.03F, 0.045F, 0.03F), 0.70F, 0.45F));
        }
        strokes.add(new Stroke(Style.LANE,
                List.of(new Point(0.6D, 0.07D, 0.0D), new Point(forward, 0.11D, 0.0D)),
                List.of(0.02F, 0.01F), 0.65F, 0.50F));
        int dust = quality == Quality.REDUCED ? 3 : 5;
        for (int mote = 0; mote < dust; mote++) {
            double t = frac(elapsedMillis / 900.0D + mote / (double) dust);
            double x = 0.6D + (forward - 0.8D) * t;
            double z = halfWidth * 0.82D * Math.sin(Math.PI * (mote / (double) dust));
            double y = 0.10D + 0.55D * Math.sin(Math.PI * t);
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(x, y, z), new Point(Math.min(forward, x + 0.20D), y + 0.05D, z)),
                    List.of(0.018F, 0.005F), 0.80F, 0.65F));
        }
        return strokes;
    }

    // ------------------------------------------------------------------
    // ECHO SPHERE: seven breathing latitude shells, six meridians, an inner
    // lattice, orbiting motes and a vertical axis.
    // ------------------------------------------------------------------
    private static List<Stroke> echoSphere(long elapsedMillis, double requested, Quality quality) {
        double radius = clamp(requested > 0.0D ? requested : ECHO_SPHERE_RADIUS,
                3.0D, ECHO_SPHERE_RADIUS);
        double progress = Math.min(1.0D, (elapsedMillis % 2400L) / 2400.0D);
        double compression = 1.0D - 0.30D * progress;
        double effective = radius * compression;
        double centreY = radius * 0.55D;
        List<Stroke> strokes = new ArrayList<>(34);
        double[] offsets = {-4.8D, -3.2D, -1.6D, 0.0D, 1.6D, 3.2D, 4.8D};
        int rings = quality == Quality.REDUCED ? 3 : 7;
        for (int ring = 0; ring < rings; ring++) {
            int slot = rings == 3 ? (ring == 0 ? 1 : ring == 1 ? 3 : 5) : ring;
            double y = Math.max(0.05D, centreY + offsets[slot] * compression);
            double dy = y - centreY;
            double horizontal = Math.sqrt(Math.max(0.25D, effective * effective - dy * dy));
            strokes.add(ringAt(horizontal, y, 20, 0.045D, Style.SHELL, 0.90F, 0.75F));
        }
        int meridians = quality == Quality.REDUCED ? 2 : 6;
        int samples = Math.max(6, quality.samples());
        double meridianSpin = Math.toRadians(elapsedMillis * 0.06D);
        for (int meridian = 0; meridian < meridians; meridian++) {
            double angle = Math.PI * meridian / meridians + meridianSpin;
            List<Point> arc = new ArrayList<>(samples + 1);
            List<Float> widths = new ArrayList<>(samples + 1);
            for (int index = 0; index <= samples; index++) {
                double t = index / (double) samples;
                double phi = -Math.PI / 2.0D + Math.PI * t;
                double y = Math.max(0.05D, centreY + Math.sin(phi) * effective);
                double r = Math.cos(phi) * effective;
                arc.add(new Point(Math.cos(angle) * r, y, Math.sin(angle) * r));
                widths.add(0.040F);
            }
            strokes.add(new Stroke(Style.SHELL, arc, widths, 0.85F, 0.65F));
        }
        // Inner tetrahedral lattice (decoration, strictly inside the shell).
        double inner = effective * 0.52D;
        for (int axis = 0; axis < 3; axis++) {
            double angle = meridianSpin * 1.5D + axis * (Math.PI / 3.0D);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            strokes.add(new Stroke(Style.LANE,
                    List.of(new Point(-cos * inner, centreY - inner * 0.65D, -sin * inner),
                            new Point(cos * inner, centreY + inner * 0.65D, sin * inner)),
                    List.of(0.02F, 0.02F), 0.55F, 0.40F));
        }
        int motes = quality == Quality.REDUCED ? 3 : 6;
        for (int mote = 0; mote < motes; mote++) {
            double angle = meridianSpin * 2.0D + mote * (Math.PI * 2.0D / motes);
            double lift = Math.sin(elapsedMillis * 0.0016D + mote * 1.7D) * 0.55D;
            double r = effective * 0.92D;
            strokes.add(orbitChevron(Math.toDegrees(angle), r,
                    centreY + lift * effective * 0.5D, 0.30D, 0.95F, 0.80F));
        }
        strokes.add(new Stroke(Style.FIELD,
                List.of(new Point(0.0D, Math.max(0.10D, centreY - effective), 0.0D),
                        new Point(0.0D, centreY + effective, 0.0D)),
                List.of(0.030F, 0.010F), 0.80F, 0.55F));
        for (int pulse = 0; pulse < 2; pulse++) {
            double phase = frac(elapsedMillis / 1200.0D + pulse * 0.5D);
            strokes.add(ringAt(effective * (0.20D + 0.45D * phase), centreY + 0.02D, 16,
                    0.018D, Style.RING, 0.70F, (float) (0.45D * (1.0D - phase))));
        }
        return strokes;
    }

    // ------------------------------------------------------------------
    // SCULK TRACE: a seeded crawling vein with branching filaments and a
    // locked jaw marker at the end.
    // ------------------------------------------------------------------
    private static List<Stroke> sculkTrace(long elapsedMillis, long seed, double requested,
                                           Quality quality) {
        double path = clamp(requested > 0.0D ? requested : 4.0D, 1.5D, 5.0D);
        int samples = Math.max(5, quality.samples() / 2 + 3);
        List<Point> trace = new ArrayList<>(samples + 1);
        List<Float> widths = new ArrayList<>(samples + 1);
        double crawl = elapsedMillis * 0.0009D;
        for (int index = 0; index <= samples; index++) {
            double t = index / (double) samples;
            double jitter = (splitUnit(seed, index) - 0.5D) * 0.55D * Math.sin(Math.PI * t);
            double lift = 0.06D + 0.05D * Math.sin(2.0D * Math.PI * (t - crawl));
            trace.add(new Point(0.4D + path * t, lift, jitter));
            widths.add(0.035F + 0.045F * (float) Math.sin(Math.PI * t));
        }
        List<Stroke> strokes = new ArrayList<>(14);
        strokes.add(new Stroke(Style.TRACE, trace, widths, 0.90F, 0.80F));
        double endX = 0.4D + path;
        strokes.add(new Stroke(Style.JAW, List.of(new Point(endX, 0.06D, -0.45D),
                new Point(endX + 0.25D, 0.10D, 0.0D), new Point(endX, 0.06D, 0.45D)),
                List.of(0.04F, 0.09F, 0.04F), 1.0F, 0.95F));
        strokes.add(new Stroke(Style.LANE, List.of(new Point(endX, 0.06D, -0.60D),
                new Point(endX, 0.06D, 0.60D)), List.of(0.03F, 0.03F), 0.60F, 0.50F));
        // Seeded side filaments branch off the main crawl; they stop short of
        // the authored tip so the locked marker stays legible.
        int branches = quality == Quality.REDUCED ? 2 : 4;
        for (int branch = 0; branch < branches; branch++) {
            double t = 0.25D + branch * (0.55D / branches);
            double x = 0.4D + path * t;
            double z = (splitUnit(seed, 20L + branch) - 0.5D) * 0.7D;
            double reach = 0.35D + 0.30D * splitUnit(seed, 40L + branch);
            strokes.add(new Stroke(Style.LANE,
                    List.of(new Point(x, 0.06D, z),
                            new Point(x + reach, 0.08D, z + reach * 0.6D)),
                    List.of(0.020F, 0.005F), 0.70F, 0.55F));
        }
        for (int mote = 0; mote < 3; mote++) {
            double t = frac(crawl * 2.0D + mote / 3.0D);
            double x = 0.4D + path * t;
            double z = (splitUnit(seed, 60L + mote) - 0.5D) * 0.4D;
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(x, 0.10D, z), new Point(x, 0.34D, z)),
                    List.of(0.014F, 0.004F), 0.80F, 0.65F));
        }
        return strokes;
    }

    // ------------------------------------------------------------------
    // GRAVITY: nine descending vortex columns, a depressed atmosphere,
    // floating debris and a top and bottom capture ring.
    // ------------------------------------------------------------------
    private static List<Stroke> gravity(long elapsedMillis, double requested, Quality quality) {
        double radius = clamp(requested > 0.0D ? requested : 8.0D, 4.0D, maxReach(Spell.GRAVITY));
        double height = clamp(radius * 0.6D, 10.0D, 18.0D);
        double progress = Math.min(1.0D, (elapsedMillis % 6000L) / 6000.0D);
        int columns = quality == Quality.REDUCED ? 3 : quality == Quality.BALANCED ? 5 : 9;
        List<Stroke> strokes = new ArrayList<>(columns + 16);
        int samples = Math.max(5, quality.samples() / 2 + 3);
        for (int column = 0; column < columns; column++) {
            double baseAngle = Math.PI * 2.0D * column / columns + 0.4D
                    + progress * 0.55D + column * 0.18D;
            double ring = radius * (column % 2 == 0 ? 0.55D : 0.85D);
            List<Point> line = new ArrayList<>(samples + 1);
            List<Float> widths = new ArrayList<>(samples + 1);
            for (int index = 0; index <= samples; index++) {
                double t = index / (double) samples;
                double y = height * (1.0D - t);
                double curl = ring * (1.0D - 0.45D * t) * (1.0D - 0.25D * progress);
                double twist = baseAngle + t * 1.5D;
                line.add(new Point(Math.cos(twist) * curl, y, Math.sin(twist) * curl));
                widths.add(0.035F + 0.03F * (float) t);
            }
            strokes.add(new Stroke(Style.FIELD, line, widths, 0.75F, 0.60F));
        }
        int ringPoints = radius > 12.0D ? 32 : 20;
        strokes.add(ringAt(radius, 0.08D, ringPoints, 0.05D, Style.RING, 0.90F, 0.70F));
        strokes.add(ringAt(radius, height, ringPoints, 0.05D, Style.RING, 0.70F, 0.50F));
        strokes.add(ringAt(radius * 0.5D, 0.09D, 14, 0.03D, Style.RING, 0.50F, 0.40F));
        strokes.add(ringAt(radius * 0.25D, 0.10D, 10, 0.03D, Style.RING, 0.40F, 0.35F));
        // Inner helix tightens as the field matures.
        int helixSamples = Math.max(6, samples);
        List<Point> helix = new ArrayList<>(helixSamples + 1);
        List<Float> helixWidths = new ArrayList<>(helixSamples + 1);
        double helixRadius = radius * 0.32D * (1.0D - 0.30D * progress);
        for (int index = 0; index <= helixSamples; index++) {
            double t = index / (double) helixSamples;
            double angle = Math.PI * 2.0D * 1.8D * t + progress * Math.PI * 2.0D;
            helix.add(new Point(Math.cos(angle) * helixRadius, height * (1.0D - t), Math.sin(angle) * helixRadius));
            helixWidths.add(0.020F);
        }
        strokes.add(new Stroke(Style.FIELD, helix, helixWidths, 0.70F, 0.55F));
        int debris = quality == Quality.REDUCED ? 4 : 8;
        for (int mote = 0; mote < debris; mote++) {
            double angle = Math.PI * 2.0D * mote / debris + progress * 3.0D;
            double r = radius * 0.90D * (1.0D - 0.30D * progress)
                    * (0.55D + 0.40D * splitUnit(mote, 1L));
            double y = 0.4D + (height - 1.0D) * splitUnit(mote, 2L);
            double size = 0.10D + 0.10D * splitUnit(mote, 3L);
            strokes.add(orbitChevron(Math.toDegrees(angle), r, y, size, 0.80F, 0.65F));
        }
        return strokes;
    }

    // ------------------------------------------------------------------
    // GUARDIAN FAN: the committed cone, layered inner bands, edge walls,
    // an outward ripple wave and scattered sparks.
    // ------------------------------------------------------------------
    private static List<Stroke> guardianFan(long elapsedMillis, double requested, Quality quality) {
        double radius = clamp(requested > 0.0D ? requested : GUARDIAN_FAN_RADIUS, 3.5D, 5.5D);
        int samples = Math.max(6, quality.samples());
        double ripple = Math.toRadians(elapsedMillis * 0.22D);
        List<Point> arc = new ArrayList<>(samples + 1);
        List<Float> widths = new ArrayList<>(samples + 1);
        for (int index = 0; index <= samples; index++) {
            double t = index / (double) samples;
            double angle = Math.toRadians(-GUARDIAN_FAN_HALF_ANGLE + 2.0D * GUARDIAN_FAN_HALF_ANGLE * t);
            double lift = 0.08D + Math.sin(Math.PI * t) * 0.35D
                    + 0.10D * Math.sin(Math.PI * t) * Math.sin(ripple + t * Math.PI);
            arc.add(new Point(Math.cos(angle) * radius, lift, Math.sin(angle) * radius));
            widths.add(0.05F + 0.03F * (float) Math.sin(Math.PI * t));
        }
        List<Stroke> strokes = new ArrayList<>(24);
        strokes.add(new Stroke(Style.WEDGE, arc, widths, 1.0F, 0.92F));
        double edge = Math.toRadians(GUARDIAN_FAN_HALF_ANGLE);
        for (int side = -1; side <= 1; side += 2) {
            strokes.add(new Stroke(Style.WEDGE, List.of(new Point(0.7D, 0.06D, 0.0D),
                    new Point(Math.cos(edge) * radius, 0.08D, side * Math.sin(edge) * radius)),
                    List.of(0.05F, 0.04F), 0.80F, 0.60F));
        }
        strokes.add(ringAt(radius * 0.5D, 0.07D, 10, 0.03D, Style.LANE, 0.50F, 0.40F));
        // Nested inner bands give the cone depth without ever reaching past the
        // committed fan radius, so the visible wedge still stops at 4.8 blocks.
        for (int band = 1; band <= 4; band++) {
            double shrink = 1.0D - band * 0.13D;
            List<Point> inner = new ArrayList<>(samples + 1);
            List<Float> innerWidths = new ArrayList<>(samples + 1);
            for (int index = 0; index <= samples; index++) {
                double t = index / (double) samples;
                double angle = Math.toRadians(-GUARDIAN_FAN_HALF_ANGLE + 2.0D * GUARDIAN_FAN_HALF_ANGLE * t);
                inner.add(new Point(Math.cos(angle) * radius * shrink, 0.07D,
                        Math.sin(angle) * radius * shrink));
                innerWidths.add(0.020F);
            }
            strokes.add(new Stroke(Style.WEDGE, inner, innerWidths, 0.60F, 0.35F));
        }
        // Vertical edge walls trace the two outer rays upward.
        for (int side = -1; side <= 1; side += 2) {
            double wall = 1.7D + 0.5D * Math.sin(ripple * 1.3D + side * 0.7D);
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(Math.cos(edge) * radius, 0.08D, side * Math.sin(edge) * radius),
                            new Point(Math.cos(edge) * radius, wall, side * Math.sin(edge) * radius)),
                    List.of(0.04F, 0.008F), 0.85F, 0.65F));
        }
        // Ripple wave travels outward inside the cone; it never exceeds it.
        for (int wave = 0; wave < 3; wave++) {
            double phase = frac(elapsedMillis / 1200.0D + wave / 3.0D);
            double shrink = 0.20D + 0.78D * phase;
            List<Point> rippleArc = new ArrayList<>(samples + 1);
            List<Float> rippleWidths = new ArrayList<>(samples + 1);
            for (int index = 0; index <= samples; index++) {
                double t = index / (double) samples;
                double angle = Math.toRadians(-GUARDIAN_FAN_HALF_ANGLE + 2.0D * GUARDIAN_FAN_HALF_ANGLE * t);
                rippleArc.add(new Point(Math.cos(angle) * radius * shrink, 0.09D,
                        Math.sin(angle) * radius * shrink));
                rippleWidths.add(0.014F);
            }
            strokes.add(new Stroke(Style.FIELD, rippleArc, rippleWidths, 0.80F,
                    (float) (0.50D * (1.0D - phase))));
        }
        int sparks = quality == Quality.REDUCED ? 3 : 6;
        for (int spark = 0; spark < sparks; spark++) {
            double t = (spark + 0.5D) / sparks;
            double angle = Math.toRadians(-GUARDIAN_FAN_HALF_ANGLE + 2.0D * GUARDIAN_FAN_HALF_ANGLE * t);
            double r = radius * (0.55D + 0.30D * splitUnit(spark, 7L));
            strokes.add(orbitChevron(Math.toDegrees(angle), r, 0.14D, 0.18D, 0.90F, 0.70F));
        }
        return strokes;
    }

    // ------------------------------------------------------------------
    // GUARDIAN HEAVY: the short wedge, a raised striker arm and impact
    // rings that race outward along the ground.
    // ------------------------------------------------------------------
    private static List<Stroke> guardianHeavy(long elapsedMillis, Quality quality) {
        double forward = GUARDIAN_HEAVY_FORWARD;
        double half = GUARDIAN_HEAVY_HALF_WIDTH;
        int samples = Math.max(4, quality.samples() / 2 + 2);
        List<Point> wedge = new ArrayList<>(samples + 3);
        List<Float> widths = new ArrayList<>(samples + 3);
        wedge.add(new Point(0.5D, 0.06D, 0.0D));
        widths.add(0.05F);
        for (int index = 0; index <= samples; index++) {
            double t = index / (double) samples;
            double angle = Math.toRadians(-28.0D + 56.0D * t);
            wedge.add(new Point(Math.cos(angle) * forward, 0.09D, Math.sin(angle) * forward));
            widths.add(0.055F);
        }
        wedge.add(new Point(0.5D, 0.06D, 0.0D));
        widths.add(0.05F);
        List<Stroke> strokes = new ArrayList<>(10);
        strokes.add(new Stroke(Style.WEDGE, wedge, widths, 1.0F, 0.94F));
        double lift = 0.9D + 0.35D * Math.sin(Math.toRadians(elapsedMillis * 0.25D));
        strokes.add(new Stroke(Style.ARM, List.of(new Point(0.2D, 1.4D, -half * 0.4D),
                new Point(forward * 0.72D, lift, half * 0.35D),
                new Point(forward, 0.25D, half * 0.2D)),
                List.of(0.10F, 0.08F, 0.05F), 1.0F, 0.90F));
        for (int side = -1; side <= 1; side += 2) {
            strokes.add(new Stroke(Style.LANE,
                    List.of(new Point(0.6D, 0.07D, side * 0.10D),
                            new Point(forward * 0.92D, 0.09D, side * half * 0.55D)),
                    List.of(0.018F, 0.006F), 0.65F, 0.48F));
        }
        for (int ring = 0; ring < 3; ring++) {
            double phase = frac(elapsedMillis / 780.0D + ring / 3.0D);
            double r = forward * (0.25D + 0.62D * phase);
            strokes.add(ringAt(r, 0.08D, 18, 0.02D, Style.FIELD, 0.85F,
                    (float) (0.45D * (1.0D - phase))));
        }
        return strokes;
    }

    // ------------------------------------------------------------------
    // SEPARATION: a planted pressure ring that expands, with secondary
    // rings, radial ticks and sparks riding the wave.
    // ------------------------------------------------------------------
    private static List<Stroke> separation(long elapsedMillis, Quality quality) {
        double radius = SEPARATION_RADIUS;
        double progress = Math.min(1.0D, (elapsedMillis % 1600L) / 1600.0D);
        double expanding = radius * (0.55D + 0.45D * progress);
        List<Stroke> strokes = new ArrayList<>(26);
        strokes.add(ringAt(expanding, 0.08D, 18, 0.05D, Style.RING, 1.0F, 0.85F));
        strokes.add(ringAt(radius * 0.4D, 0.09D, 10, 0.03D, Style.RING, 0.60F, 0.45F));
        strokes.add(ringAt(expanding * 0.55D, 0.07D, 14, 0.02D, Style.RING, 0.55F, 0.35F));
        strokes.add(ringAt(expanding * 0.25D, 0.07D, 10, 0.02D, Style.RING, 0.45F, 0.28F));
        int ticks = quality == Quality.REDUCED ? 8 : 12;
        for (int tick = 0; tick < ticks; tick++) {
            double angle = Math.PI * 2.0D * tick / ticks + progress * 0.6D;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            strokes.add(new Stroke(Style.FIELD, List.of(
                    new Point(cos * expanding, 0.07D, sin * expanding),
                    new Point(cos * (expanding + 0.75D), 0.07D, sin * (expanding + 0.75D))),
                    List.of(0.035F, 0.020F), 0.80F, 0.70F));
        }
        int sparks = quality == Quality.REDUCED ? 4 : 8;
        for (int spark = 0; spark < sparks; spark++) {
            double angle = Math.PI * 2.0D * spark / sparks + Math.PI / sparks;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double inner = expanding * 0.92D;
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(cos * inner, 0.10D, sin * inner),
                            new Point(cos * (inner + 0.28D), 0.10D, sin * (inner + 0.28D))),
                    List.of(0.018F, 0.004F), 0.90F, 0.75F));
        }
        return strokes;
    }

    // ------------------------------------------------------------------
    // BEAM (N23-P1-05): the signature chest beam's own full-length,
    // layered cross-section. A stack of horizontal chord ribbons fills a
    // circular tube; the core is bright, the outer wake is translucent and
    // hugs the real damage capsule from inside. Two counter-rotating inner
    // helices, cross-section hoops, a flared muzzle plate with concentric
    // opening rings, seeded forged side branches and an expanding tip
    // detonation give it depth from every camera angle.
    // ------------------------------------------------------------------
    private static List<Stroke> beam(long elapsedMillis, long seed, double requested,
                                     Quality quality) {
        double length = clamp(requested > 0.0D ? requested : BEAM_MAX_LENGTH,
                BEAM_MIN_LENGTH, BEAM_MAX_LENGTH);
        int samples = Math.max(6, quality.samples());
        double spin = spinDegrees(elapsedMillis, 9.0D, splitUnit(seed, 3L) * 360.0D);
        List<Stroke> strokes = new ArrayList<>(48);

        // Energized broad core and translucent outer wake, both built from the
        // same chord stack so the visible shaft is a genuine cross-section.
        strokes.addAll(chordSheets(length, samples, 0.52D, 4, Style.ARM, 1.0F, 0.94F));
        strokes.addAll(chordSheets(length, samples, BEAM_VISUAL_RADIUS * 0.62D, 6,
                Style.SHELL, 0.70F, 0.42F));
        strokes.addAll(chordSheets(length, samples, BEAM_VISUAL_RADIUS, 9,
                Style.SHELL, 0.58F, 0.30F));

        // Two counter-rotating inner helices read the flow of energy along the
        // shaft; they stay strictly inside the core.
        int helixSamples = Math.max(8, samples * 2);
        int helixTurns = Math.max(1, (int) Math.round(length / 6.0D));
        for (int direction = -1; direction <= 1; direction += 2) {
            List<Point> helix = new ArrayList<>(helixSamples + 1);
            List<Float> helixWidths = new ArrayList<>(helixSamples + 1);
            for (int index = 0; index <= helixSamples; index++) {
                double t = index / (double) helixSamples;
                double angle = Math.toRadians(spin * direction)
                        + direction * Math.PI * 2.0D * helixTurns * t;
                double r = 0.95D * (0.55D + 0.45D * t);
                helix.add(new Point(length * t, Math.cos(angle) * r, Math.sin(angle) * r));
                helixWidths.add(0.030F);
            }
            strokes.add(new Stroke(Style.FIELD, helix, helixWidths, 0.95F, 0.74F));
        }

        // Cross-section hoops make the tube legible as a cylinder from the side.
        int hoops = quality == Quality.REDUCED ? 2 : 4;
        for (int hoop = 0; hoop < hoops; hoop++) {
            double station = hoops == 2 ? (hoop == 0 ? 0.32D : 0.72D)
                    : 0.10D + 0.78D * hoop / (hoops - 1.0D);
            strokes.add(axisRing(length * station, BEAM_VISUAL_RADIUS * 0.94D, 18, 0.030D,
                    Style.RING, 0.78F, 0.50F, spin * 0.5D + hoop * 30.0D));
        }

        // Flared muzzle plate: a short cone plus concentric opening rings at the
        // core, so the shaft is visibly fired from an opened socket.
        int collarSamples = Math.max(4, samples / 2);
        int collarSheets = quality == Quality.REDUCED ? 3 : 5;
        for (int sheet = 0; sheet < collarSheets; sheet++) {
            double y = collarSheets == 1 ? 0.0D
                    : -0.9D + 1.8D * sheet / (collarSheets - 1.0D);
            double shell = Math.sqrt(Math.max(0.02D, 1.0D - (y * y) / (1.30D * 1.30D)));
            List<Point> collar = new ArrayList<>(collarSamples + 1);
            List<Float> collarWidths = new ArrayList<>(collarSamples + 1);
            for (int index = 0; index <= collarSamples; index++) {
                double t = index / (double) collarSamples;
                collar.add(new Point(0.05D + 0.80D * t, y, 0.0D));
                collarWidths.add((float) ((0.35D + 0.85D * t) * shell));
            }
            strokes.add(new Stroke(Style.WEDGE, collar, collarWidths, 1.0F, 0.85F));
        }
        for (double plate : new double[]{0.38D, 0.82D, 1.26D, 1.56D}) {
            strokes.add(axisRing(0.06D, plate, 16, 0.032D, Style.RING, 1.0F, 0.72F, spin * 0.2D));
        }

        // Seeded forged side branches crack off the shell, always inside it.
        int branches = quality == Quality.REDUCED ? 3 : 6;
        for (int branch = 0; branch < branches; branch++) {
            double t = 0.34D + 0.52D * (branch / (double) Math.max(1, branches - 1));
            double angle = Math.toRadians(spin * 0.6D + branch * (360.0D / branches));
            double startX = length * t;
            double inner = BEAM_VISUAL_RADIUS * 0.34D;
            double outer = BEAM_VISUAL_RADIUS * 0.94D;
            strokes.add(new Stroke(Style.JAW, List.of(
                    new Point(startX, Math.cos(angle) * inner, Math.sin(angle) * inner),
                    new Point(startX + length * 0.05D, Math.cos(angle) * outer,
                            Math.sin(angle) * outer),
                    new Point(startX + length * 0.10D, Math.cos(angle) * outer * 0.82D,
                            Math.sin(angle) * outer * 0.82D)),
                    List.of(0.050F, 0.034F, 0.012F), 1.0F, 0.90F));
        }

        // Expanding tip detonation plus a bright contact lip at the real end.
        for (int ring = 0; ring < 3; ring++) {
            double phase = frac(elapsedMillis / 640.0D + ring / 3.0D);
            strokes.add(axisRing(length, BEAM_VISUAL_RADIUS * (0.30D + 0.66D * phase), 16, 0.028D,
                    Style.RING, 0.95F, (float) (0.55D * (1.0D - phase)), spin + ring * 40.0D));
        }
        strokes.add(axisRing(length, BEAM_VISUAL_RADIUS, 22, 0.048D, Style.RING, 1.0F, 0.85F, 0.0D));
        return strokes;
    }

    /**
     * Build a tube cross-section as a stack of horizontal chord ribbons. A
     * ribbon along local {@code x} spans local {@code z}; the half-width of a
     * sheet at height {@code y} is the circle chord at that height, so the
     * stacked ribbons fill a genuine circular shaft and the widest sheet sits
     * exactly at the requested radius.
     */
    // ------------------------------------------------------------------
    // GEYSER (N23-P1-08): the Phase 3 rock/obsidian eruption column. A
    // seeded ring of growing floor fissures feeds a jagged throat of rock
    // ribs; volumetric flame sheets choke upward through it, volcanic ash
    // and debris tumble out, and the whole column collapses and settles on
    // the cast clock. Every radial feature stays inside the real 1.58-block
    // visual radius (under the 1.9-block damage radius), and the height is
    // driven by the authored reach so it matches the staged 8..12 blocks.
    // ------------------------------------------------------------------
    private static List<Stroke> geyser(long elapsedMillis, long seed, double requested,
                                       Quality quality) {
        double height = clamp(requested > 0.0D ? requested : GEYSER_MAX_HEIGHT,
                GEYSER_MIN_HEIGHT, GEYSER_MAX_HEIGHT);
        double t = frac(elapsedMillis / (double) GEYSER_PERIOD_MILLIS);
        // Envelope: fissures and pressure lead, the column blooms, then it
        // collapses and the ash settles. All phases overlap so motion never
        // stops mid-cycle.
        double swell = t < 0.22D ? smooth(t / 0.22D) : 1.0D;
        double erupt = clamp((t - 0.18D) / 0.18D, 0.0D, 1.0D);
        double collapse = clamp((t - 0.72D) / 0.28D, 0.0D, 1.0D);
        double column = clamp(0.12D + 0.88D * erupt - collapse, 0.06D, 1.0D);
        double spin = spinDegrees(elapsedMillis, 26.0D, splitUnit(seed, 5L) * 360.0D);
        int samples = quality.samples();
        List<Stroke> strokes = new ArrayList<>(64);

        // 1. Growing floor fissures: seeded radial jags in the base plane.
        int fissures = quality == Quality.REDUCED ? 5 : 8;
        for (int fissure = 0; fissure < fissures; fissure++) {
            double angle = Math.toRadians(splitUnit(seed, fissure) * 360.0D + spin * 0.20D);
            double reach = GEYSER_VISUAL_RADIUS * (0.50D + 0.40D * swell)
                    * (0.72D + 0.28D * splitUnit(seed, 40L + fissure));
            List<Point> crack = new ArrayList<>(5);
            List<Float> widths = new ArrayList<>(5);
            for (int index = 0; index <= 4; index++) {
                double u = index / 4.0D;
                double jag = (index % 2 == 0 ? 1.0D : -1.0D) * 0.18D * u;
                double r = reach * u;
                crack.add(new Point(0.05D + jag * 0.06D,
                        Math.cos(angle) * r - Math.sin(angle) * jag,
                        Math.sin(angle) * r + Math.cos(angle) * jag));
                widths.add((float) (0.080D * (1.0D - 0.70D * u)));
            }
            strokes.add(new Stroke(Style.LANE, crack, widths, 0.85F, 0.55F));
        }

        // 2. Locked ground breach: a rugged, closed ring of cracked black
        // ground whose inner edge glows. The jitter is seeded by the cue only,
        // so once the server freezes the target position the marked footprint
        // can never drift or shrink; it simply grows into place through the
        // warning and then holds, which is what lets a player read the exact
        // edge of the real damage cylinder even under tall grass or other fog.
        int breachPoints = quality == Quality.REDUCED ? 14 : 22;
        double breachScale = GEYSER_VISUAL_RADIUS * 0.92D * (0.62D + 0.38D * swell);
        for (int edge = 0; edge < 2; edge++) {
            double radius = breachScale * (edge == 0 ? 1.0D : 0.74D);
            List<Point> breach = new ArrayList<>(breachPoints + 1);
            List<Float> breachWidths = new ArrayList<>(breachPoints + 1);
            for (int index = 0; index <= breachPoints; index++) {
                int knot = index % breachPoints;
                double angle = Math.PI * 2.0D * index / breachPoints;
                double jitter = 0.84D + 0.24D * splitUnit(seed, 300L + edge * 40L + knot);
                double r = radius * jitter;
                breach.add(new Point(0.06D + 0.02D * edge,
                        Math.cos(angle) * r, Math.sin(angle) * r));
                breachWidths.add((float) (0.07D * (edge == 0 ? 1.0D : 0.60D)));
            }
            strokes.add(new Stroke(edge == 0 ? Style.LANE : Style.RING, breach, breachWidths,
                    edge == 0 ? 0.62F : 1.0F, edge == 0 ? 0.55F : 0.86F));
        }

        // 3. Pre-blast pressure: a handful of ash motes are visibly pulled
        // INWARD before the blow, so the warning reads from motion alone even
        // with sound off. It is entirely local and disappears by the eruption.
        if (swell < 1.0D) {
            int pulled = quality == Quality.REDUCED ? 4 : 6;
            for (int mote = 0; mote < pulled; mote++) {
                double angle = splitUnit(seed, 420L + mote) * Math.PI * 2.0D + spin * 0.25D;
                double outer = GEYSER_VISUAL_RADIUS * (0.90D + 0.06D * splitUnit(seed, 460L + mote));
                double inner = GEYSER_VISUAL_RADIUS * 0.30D;
                double r = outer + (inner - outer) * swell;
                double lift = 0.10D + 0.24D * (1.0D - swell) * splitUnit(seed, 500L + mote);
                strokes.add(new Stroke(Style.TRACE, List.of(
                        new Point(lift, Math.cos(angle) * outer * 0.92D,
                                Math.sin(angle) * outer * 0.92D),
                        new Point(lift + 0.06D, Math.cos(angle) * r, Math.sin(angle) * r)),
                        List.of(0.05F, 0.02F), 0.92F,
                        (float) (0.55D + 0.35D * (1.0D - swell))));
            }
        }

        // 4. Pressure glow: pulsing rings hugging the throat at the base.
        for (int ring = 0; ring < 3; ring++) {
            double phase = frac(t + ring / 3.0D);
            strokes.add(axisRing(0.10D + ring * 0.08D,
                    GEYSER_VISUAL_RADIUS * (0.34D + 0.50D * phase), 18, 0.05D,
                    Style.RING, 0.90F, (float) (0.55D * (1.0D - phase)), spin * 0.3D));
        }

        // 5. Rock/obsidian throat: jagged vertical ribs and stepped ledges.
        int ribs = quality == Quality.REDUCED ? 5 : 7;
        for (int rib = 0; rib < ribs; rib++) {
            double angle = rib * (Math.PI * 2.0D) / ribs + spin * 0.10D;
            double baseR = GEYSER_VISUAL_RADIUS * (0.55D + 0.10D * (rib % 3));
            double topR = baseR * (0.72D + 0.18D * splitUnit(seed, 60L + rib));
            double top = height * (0.52D + 0.16D * (rib % 3)) * column;
            List<Point> throat = new ArrayList<>(samples + 1);
            List<Float> widths = new ArrayList<>(samples + 1);
            for (int index = 0; index <= samples; index++) {
                double u = index / (double) samples;
                double jag = Math.sin(u * Math.PI * 3.0D + rib) * 0.10D * GEYSER_VISUAL_RADIUS
                        * Math.sin(Math.PI * u);
                double r = baseR + (topR - baseR) * u + jag;
                throat.add(new Point(top * u, Math.cos(angle) * r, Math.sin(angle) * r));
                widths.add((float) (0.16D * (1.0D - 0.55D * u)));
            }
            strokes.add(new Stroke(Style.JAW, throat, widths, 0.60F, 0.90F));
        }

        // 6. Volumetric flame sheets: tall staggered, leaning, travelling wave.
        int sheets = quality == Quality.REDUCED ? 4 : 6;
        for (int sheet = 0; sheet < sheets; sheet++) {
            double angle = sheet * (Math.PI * 2.0D) / sheets + spin * 0.16D;
            double baseR = GEYSER_VISUAL_RADIUS * (0.30D + 0.06D * (sheet % 3));
            double lean = GEYSER_VISUAL_RADIUS * 0.28D * (sheet % 2 == 0 ? 1.0D : -1.0D);
            double top = height * (0.82D + 0.16D * splitUnit(seed, 80L + sheet)) * column;
            int segments = Math.max(4, samples);
            List<Point> flame = new ArrayList<>(segments + 1);
            List<Float> widths = new ArrayList<>(segments + 1);
            for (int index = 0; index <= segments; index++) {
                double u = index / (double) segments;
                double lash = Math.sin(u * Math.PI * 2.0D - spin * 0.60D + sheet)
                        * 0.12D * GEYSER_VISUAL_RADIUS * u;
                double r = baseR + lean * u * u;
                flame.add(new Point(top * u,
                        Math.cos(angle) * r - Math.sin(angle) * lash,
                        Math.sin(angle) * r + Math.cos(angle) * lash));
                double wave = 0.55D + 0.45D * (0.5D + 0.5D * Math.sin(u * 6.0D - spin * 0.90D));
                widths.add((float) (0.20D * (1.0D - 0.45D * u) * wave));
            }
            strokes.add(new Stroke(Style.FIELD, flame, widths, 1.0F, 0.62F));
        }

        // 7. Ash and debris: motes flung out, then falling as it collapses.
        int motes = quality == Quality.REDUCED ? 6 : 10;
        for (int mote = 0; mote < motes; mote++) {
            double angle = splitUnit(seed, 100L + mote) * (Math.PI * 2.0D) + spin * 0.40D;
            double u = frac(t * (0.90D + 0.50D * splitUnit(seed, 140L + mote))
                    + splitUnit(seed, 180L + mote));
            double r = GEYSER_VISUAL_RADIUS * Math.min(1.0D,
                    (0.50D + 0.90D * u) * (0.70D + 0.50D * collapse));
            double x = Math.max(0.05D, Math.min(height,
                    height * (0.35D + 0.75D * u) * column - collapse * height * 0.50D * u));
            double tailX = Math.max(0.05D, Math.min(height, x - 0.16D - 0.08D * (mote % 3)));
            double tailR = Math.min(GEYSER_VISUAL_RADIUS, r * 1.12D);
            strokes.add(new Stroke(Style.TRACE, List.of(
                    new Point(x, Math.cos(angle) * r, Math.sin(angle) * r),
                    new Point(tailX, Math.cos(angle) * tailR, Math.sin(angle) * tailR)),
                    List.of(0.10F, 0.03F), 0.70F, (float) (0.35D + 0.35D * (1.0D - u))));
        }

        // 8. Real rock fragmentation: readable local shards leave the throat on
        // a ballistic arc, peak under their own momentum and fall back as the
        // column collapses. Each is a chipped three-point slab. They stay
        // inside the marked breach edge, so no shard can ever hide the actual
        // danger boundary, and they are cosmetic only -- the hit receipt stays
        // server-authoritative.
        int shards = quality == Quality.REDUCED ? 4 : 8;
        for (int shard = 0; shard < shards; shard++) {
            double launch = 0.20D + 0.03D * (shard % 4)
                    + 0.02D * splitUnit(seed, 540L + shard);
            double life = clamp((t - launch) / 0.55D, 0.0D, 1.0D);
            if (life <= 0.0D || life >= 1.0D || column <= 0.10D) {
                continue;
            }
            double arc = Math.sin(Math.PI * life);
            double angle = splitUnit(seed, 580L + shard) * Math.PI * 2.0D + spin * 0.12D;
            double radial = GEYSER_VISUAL_RADIUS * (0.38D + 0.34D * life)
                    * (0.80D + 0.20D * splitUnit(seed, 620L + shard));
            double top = height * (0.14D + 0.66D * arc);
            double tilt = 0.10D + 0.12D * ((shard % 3) - 1);
            double side = radial * 0.22D;
            Point root = new Point(Math.max(0.06D, top - height * 0.16D),
                    Math.cos(angle) * radial - Math.sin(angle) * side,
                    Math.sin(angle) * radial + Math.cos(angle) * side);
            Point tip = new Point(Math.min(height, top + height * 0.05D),
                    Math.cos(angle) * (radial + tilt),
                    Math.sin(angle) * (radial + tilt));
            Point wing = new Point(Math.max(0.06D, top - height * 0.10D),
                    Math.cos(angle + 0.18D) * radial,
                    Math.sin(angle + 0.18D) * radial);
            strokes.add(new Stroke(Style.JAW, List.of(root, wing, tip),
                    List.of(0.15F, 0.10F, 0.03F), 0.92F,
                    (float) (0.55D + 0.35D * arc)));
        }

        // 9. Crown flare: short blades at the top of the living column.
        if (column > 0.35D) {
            double top = height * column;
            for (int blade = 0; blade < 5; blade++) {
                double angle = blade * (Math.PI * 2.0D) / 5.0D + spin * 0.30D;
                strokes.add(new Stroke(Style.ARM, List.of(
                        new Point(top * 0.84D, Math.cos(angle) * GEYSER_VISUAL_RADIUS * 0.30D,
                                Math.sin(angle) * GEYSER_VISUAL_RADIUS * 0.30D),
                        new Point(top * (0.90D + 0.08D * splitUnit(seed, 200L + blade)),
                                Math.cos(angle) * GEYSER_VISUAL_RADIUS * 0.85D,
                                Math.sin(angle) * GEYSER_VISUAL_RADIUS * 0.85D)),
                        List.of(0.09F, 0.02F), 1.0F, (float) (0.50D + 0.50D * column)));
            }
        }

        // 10. Detached crest lobes: the top breaks into separated tongues
        // instead of ending in a hard flat cap, so the column keeps reading as
        // fire from every angle and never as a cylinder with a lid.
        if (column > 0.50D) {
            double crest = height * column;
            int lobes = quality == Quality.REDUCED ? 3 : 5;
            for (int lobe = 0; lobe < lobes; lobe++) {
                double angle = lobe * (Math.PI * 2.0D) / lobes + spin * 0.34D + 0.40D;
                double spread = GEYSER_VISUAL_RADIUS * (0.34D + 0.10D * (lobe % 3));
                double startX = Math.max(0.06D, Math.min(crest, height - height * 0.10D));
                double rise = Math.min(height, startX
                        + height * (0.06D + 0.05D * splitUnit(seed, 660L + lobe)));
                double drift = Math.cos(angle + 0.30D);
                double driftZ = Math.sin(angle + 0.30D);
                strokes.add(new Stroke(Style.ARM, List.of(
                        new Point(startX, Math.cos(angle) * spread * 0.55D,
                                Math.sin(angle) * spread * 0.55D),
                        new Point(startX + (rise - startX) * 0.55D,
                                Math.cos(angle) * spread,
                                Math.sin(angle) * spread),
                        new Point(rise, drift * spread * 1.05D, driftZ * spread * 1.05D)),
                        List.of(0.10F, 0.07F, 0.02F), 1.0F,
                        (float) (0.50D + 0.50D * column)));
            }
        }

        // 11. Impact exit: fast concentric dust/air rings leave the base as the
        // column cools, then fade with it. They stay inside the real radius.
        if (collapse > 0.0D) {
            int rings = quality == Quality.REDUCED ? 1 : 2;
            for (int ring = 0; ring < rings; ring++) {
                double u = clamp(collapse - ring * 0.18D, 0.0D, 1.0D);
                if (u <= 0.0D) {
                    continue;
                }
                double radius = GEYSER_VISUAL_RADIUS * (0.42D + 0.54D * u);
                strokes.add(axisRing(0.12D + 0.02D * ring, radius, 18, 0.05D,
                        Style.RING, (float) (0.85D * (1.0D - u)),
                        (float) (0.50D * (1.0D - u)), spin * 0.40D));
            }
        }
        return strokes;
    }

    private static List<Stroke> chordSheets(double length, int samples, double radius,
                                            int sheets, Style style, float brightness,
                                            float alpha) {
        int count = Math.max(1, sheets);
        List<Stroke> result = new ArrayList<>(count);
        for (int sheet = 0; sheet < count; sheet++) {
            double y = count == 1 ? 0.0D : -radius + 2.0D * radius * sheet / (count - 1.0D);
            double chord = Math.max(0.04D, Math.sqrt(Math.max(0.0D, radius * radius - y * y)));
            List<Point> line = new ArrayList<>(samples + 1);
            List<Float> widths = new ArrayList<>(samples + 1);
            for (int index = 0; index <= samples; index++) {
                double t = index / (double) samples;
                line.add(new Point(length * t, y, 0.0D));
                widths.add((float) chord);
            }
            result.add(new Stroke(style, line, widths, brightness, alpha));
        }
        return result;
    }

    /**
     * A ring in the plane perpendicular to a beam's local {@code x} axis: the
     * cross-section marker at one station along the shaft.
     */
    private static Stroke axisRing(double x, double radius, int points, double halfWidth,
                                   Style style, float brightness, float alpha, double phaseDegrees) {
        int count = Math.max(6, Math.min(MAX_POINTS_PER_STROKE - 1, points));
        List<Point> ring = new ArrayList<>(count + 1);
        List<Float> widths = new ArrayList<>(count + 1);
        double offset = Math.toRadians(phaseDegrees);
        for (int index = 0; index <= count; index++) {
            double angle = offset + Math.PI * 2.0D * index / count;
            ring.add(new Point(x, Math.cos(angle) * radius, Math.sin(angle) * radius));
            widths.add((float) halfWidth);
        }
        return new Stroke(style, ring, widths, brightness, alpha);
    }

    private static Stroke rotatedRing(double radius, double y, int points, double halfWidth,
                                      Style style, float brightness, float alpha, double rotationDegrees) {
        int count = Math.max(3, Math.min(MAX_POINTS_PER_STROKE - 1, points));
        List<Point> ring = new ArrayList<>(count + 1);
        List<Float> widths = new ArrayList<>(count + 1);
        double offset = Math.toRadians(rotationDegrees);
        for (int index = 0; index <= count; index++) {
            double angle = offset + Math.PI * 2.0D * index / count;
            ring.add(new Point(Math.cos(angle) * radius, y, Math.sin(angle) * radius));
            widths.add((float) halfWidth);
        }
        return new Stroke(style, ring, widths, brightness, alpha);
    }

    /**
     * A short angled chevron orbiting the cast origin. It is decoration: the
     * caller keeps {@code radius + size} inside the spell's authored bound, so
     * a mote can never pretend to be a hit volume.
     */
    private static Stroke orbitChevron(double angleDegrees, double radius, double y, double size,
                                       float brightness, float alpha) {
        double angle = Math.toRadians(angleDegrees);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double tangentX = -sin;
        double tangentZ = cos;
        List<Point> points = List.of(
                new Point(cos * radius + tangentX * size, y - 0.05D, sin * radius + tangentZ * size),
                new Point(cos * radius, y + 0.04D, sin * radius),
                new Point(cos * radius - tangentX * size, y - 0.05D, sin * radius - tangentZ * size));
        return new Stroke(Style.LANE, points, List.of(0.030F, 0.050F, 0.030F),
                brightness, alpha);
    }

    /**
     * A short deterministic radial flare that pulses with the cast clock. It is
     * pure decoration: every ray is far shorter than the spell's own footprint,
     * so it can never widen the visible hit volume or escape the reach bound.
     */
    private static void burst(List<Stroke> strokes, double radius, int arms, long elapsedMillis,
                              double periodMillis, float brightness, float alpha) {
        if (arms <= 0 || radius <= 0.0D) {
            return;
        }
        double period = Math.max(1.0D, periodMillis);
        double phase = (elapsedMillis % (long) period) / period;
        double scale = 0.35D + 0.65D * phase;
        for (int arm = 0; arm < arms; arm++) {
            double angle = Math.PI * 2.0D * arm / arms;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double inner = radius * 0.35D * scale;
            double outer = radius * scale;
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(cos * inner, 0.12D, sin * inner),
                            new Point(cos * outer, 0.12D, sin * outer)),
                    List.of(0.03F, 0.008F), brightness, alpha));
        }
    }

    private static Stroke ring(double radius, int points, double halfWidth, Style style,
                               float brightness, float alpha) {
        return ringAt(radius, 0.06D, points, halfWidth, style, brightness, alpha);
    }

    private static Stroke ringAt(double radius, double y, int points, double halfWidth, Style style,
                                 float brightness, float alpha) {
        int count = Math.max(6, Math.min(MAX_POINTS_PER_STROKE - 1, points));
        List<Point> ring = new ArrayList<>(count + 1);
        List<Float> widths = new ArrayList<>(count + 1);
        for (int index = 0; index <= count; index++) {
            double angle = Math.PI * 2.0D * index / count;
            ring.add(new Point(Math.cos(angle) * radius, y, Math.sin(angle) * radius));
            widths.add((float) halfWidth);
        }
        return new Stroke(style, ring, widths, brightness, alpha);
    }

    private static Stroke gapArc(double radius, double gapDegrees, double halfWidth) {
        double gap = Math.min(180.0D, Math.max(1.0D, gapDegrees));
        double start = -gap / 2.0D;
        int samples = 10;
        List<Point> arc = new ArrayList<>(samples + 1);
        List<Float> widths = new ArrayList<>(samples + 1);
        for (int index = 0; index <= samples; index++) {
            double angle = Math.toRadians(start + gap * index / (double) samples);
            arc.add(new Point(Math.cos(angle) * radius, 0.05D, Math.sin(angle) * radius));
            widths.add((float) halfWidth);
        }
        return new Stroke(Style.LANE, arc, widths, 0.60F, 0.55F);
    }

    private static Point scaleRadial(Point point, double factor) {
        return new Point(point.x() * factor, point.y(), point.z() * factor);
    }

    private static double spinDegrees(long elapsedMillis, double degreesPerSecond, double phaseDegrees) {
        return (elapsedMillis / 1000.0D) * degreesPerSecond + phaseDegrees;
    }

    private static double frac(double value) {
        return value - Math.floor(value);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Smoothstep ease, so authored growth never snaps. */
    private static double smooth(double value) {
        double u = clamp(value, 0.0D, 1.0D);
        return u * u * (3.0D - 2.0D * u);
    }

    /** Hard-bound the mesh so no cue can request an unbounded submission. */
    private static List<Stroke> bounded(List<Stroke> strokes, double maxReach) {
        List<Stroke> result = new ArrayList<>(Math.min(MAX_STROKES, strokes.size()));
        int segments = 0;
        for (Stroke stroke : strokes) {
            if (result.size() >= MAX_STROKES || segments >= MAX_SEGMENTS) {
                break;
            }
            List<Point> points = new ArrayList<>(Math.min(MAX_POINTS_PER_STROKE, stroke.points().size()));
            List<Float> widths = new ArrayList<>(Math.min(MAX_POINTS_PER_STROKE, stroke.points().size()));
            boolean valid = true;
            for (int index = 0; index < stroke.points().size() && index < MAX_POINTS_PER_STROKE; index++) {
                Point point = stroke.points().get(index);
                if (!finite(point) || point.horizontalLength() > maxReach
                        || point.y() < -2.0D || point.y() > 20.0D) {
                    // Reject the whole stroke: concatenating the surviving points
                    // would draw a long un-authored line across the arena.
                    valid = false;
                    break;
                }
                points.add(point);
                widths.add(stroke.halfWidths().get(index));
            }
            if (!valid || points.size() < 2) {
                continue;
            }
            segments += points.size() - 1;
            result.add(new Stroke(stroke.style(), points, widths, stroke.brightness(), stroke.alpha()));
        }
        return List.copyOf(result);
    }

    private static boolean finite(Point point) {
        return point != null && Double.isFinite(point.x()) && Double.isFinite(point.y())
                && Double.isFinite(point.z());
    }

    /** SplitMix-style deterministic unit value; no shared RNG state. */
    static double splitUnit(long seed, long index) {
        long z = seed + 0x9E3779B97F4A7C15L * (index + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }
}
