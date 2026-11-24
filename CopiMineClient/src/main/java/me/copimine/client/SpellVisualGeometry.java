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
        SEPARATION("separation");

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

    /** Readability role of a stroke; the renderer picks alpha / brightness. */
    public enum Style {
        ARM,
        JAW,
        LANE,
        WEDGE,
        SHELL,
        TRACE,
        FIELD,
        RING
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

    /** Per-spell footprint bound; only the arena-wide gravity field exceeds the local reach. */
    public static double maxReach(Spell spell) {
        return spell == Spell.GRAVITY ? 48.0D : MAX_REACH_BLOCKS;
    }

    private SpellVisualGeometry() {
    }

    /**
     * Build the local strokes for one cast instant.
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
        Quality tier = quality == null ? Quality.BALANCED : quality;
        double reach = Double.isFinite(param) ? Math.abs(param) : 0.0D;
        List<Stroke> strokes = switch (spell) {
            case CROWN -> crown(elapsed, seed, reach, tier);
            case CIRCLE -> circle(elapsed, seed, reach, tier);
            case FANG_LINE -> fangLine(reach, tier);
            case WARDEN_SWEEP -> wardenSweep(tier);
            case ECHO_SPHERE -> echoSphere(elapsed, reach, tier);
            case SCULK_TRACE -> sculkTrace(seed, reach, tier);
            case GRAVITY -> gravity(elapsed, reach, tier);
            case GUARDIAN_FAN -> guardianFan(reach, tier);
            case GUARDIAN_HEAVY -> guardianHeavy(tier);
            case SEPARATION -> separation(elapsed, tier);
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

    private static List<Stroke> crown(long elapsedMillis, long seed, double requested,
                                      Quality quality) {
        double length = Double.isFinite(requested) && requested > 0
                ? Math.max(CROWN_LENGTH_MIN, Math.min(CROWN_LENGTH_MAX, requested))
                : CROWN_LENGTH_MAX;
        double rotation = (elapsedMillis / 1000.0D) * CROWN_ROTATION_DEG_PER_SECOND
                + splitUnit(seed, 0L) * 12.0D;
        int samples = quality.samples();
        int teethPerArm = Math.max(1, (int) Math.floor(length / CROWN_TEETH_SPACING_BLOCKS));
        teethPerArm = Math.min(4, teethPerArm);
        List<Stroke> strokes = new ArrayList<>(CROWN_SPOKES * (1 + teethPerArm) + 2);
        for (int spoke = 0; spoke < CROWN_SPOKES; spoke++) {
            double angle = rotation + spoke * (360.0D / CROWN_SPOKES);
            List<Point> arm = new ArrayList<>(samples + 1);
            List<Float> widths = new ArrayList<>(samples + 1);
            for (int index = 0; index <= samples; index++) {
                double t = index / (double) samples;
                double curved = angle + CROWN_CURVE_DEGREES * (t * t - t * 0.5D);
                double radians = Math.toRadians(curved);
                double distance = length * t;
                double lift = Math.sin(Math.PI * t) * 0.55D + t * 0.25D;
                arm.add(new Point(Math.cos(radians) * distance, lift, Math.sin(radians) * distance));
                double taper = 0.115D - 0.075D * t;
                widths.add((float) Math.max(0.03D, taper));
            }
            strokes.add(new Stroke(Style.ARM, arm, widths, 1.0F, 0.86F));
            for (int tooth = 1; tooth <= teethPerArm; tooth++) {
                double t = Math.min(0.94D, 0.35D + tooth * (0.55D / Math.max(1, teethPerArm)));
                strokes.add(armTooth(angle, length, t));
            }
        }
        strokes.add(ring(1.6D, 18, 0.075D, Style.RING, 0.55F, 0.5F));
        strokes.add(ring(2.1D, 18, 0.04D, Style.RING, 0.4F, 0.35F));
        // Vertically rising crest blades ride the arm tips, so the rotation is
        // legible from the side as well as from above.
        for (int spoke = 0; spoke < CROWN_SPOKES; spoke++) {
            double angle = rotation + spoke * (360.0D / CROWN_SPOKES);
            double tipRadians = Math.toRadians(angle + CROWN_CURVE_DEGREES * 0.5D);
            double tipX = Math.cos(tipRadians) * length;
            double tipZ = Math.sin(tipRadians) * length;
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(tipX, 0.35D, tipZ), new Point(tipX, 1.55D, tipZ)),
                    List.of(0.07F, 0.012F), 1.0F, 0.9F));
        }
        burst(strokes, length * 0.24D, 8, elapsedMillis, 900.0D, 1.0F, 0.8F);
        return strokes;
    }

    private static Stroke armTooth(double angleDegrees, double length, double t) {
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
        List<Point> points = List.of(
                new Point(cx + tangentX * 0.30D, lift - 0.02D, cz + tangentZ * 0.30D),
                new Point(cx + inwardX * 0.52D, lift + 0.10D, cz + inwardZ * 0.52D),
                new Point(cx - tangentX * 0.30D, lift - 0.02D, cz - tangentZ * 0.30D));
        return new Stroke(Style.JAW, points, List.of(0.05F, 0.09F, 0.05F), 1.0F, 0.9F);
    }

    private static List<Stroke> circle(long elapsedMillis, long seed, double requested, Quality quality) {
        double radius = Double.isFinite(requested) && requested > 0
                ? Math.max(1.8D, Math.min(4.0D, requested))
                : CIRCLE_RADIUS;
        int jaws = quality == Quality.REDUCED ? CIRCLE_JAWS_MIN : CIRCLE_JAWS_MAX;
        double gap = requiredGapDegrees(radius, CIRCLE_GAP_BLOCKS_MIN) + 4.0D;
        double spin = (elapsedMillis / 1000.0D) * 6.0D + splitUnit(seed, 1L) * 360.0D;
        List<Stroke> strokes = new ArrayList<>(jaws + 12);
        for (Point[] jaw : circleJaws(radius, jaws, gap)) {
            strokes.add(new Stroke(Style.JAW, List.of(jaw[0], jaw[1], jaw[2]),
                    List.of(0.05F, 0.09F, 0.05F), 1.0F, 0.92F));
        }
        // The visible gap arc is drawn thinner so the escape sector reads clearly.
        strokes.add(gapArc(radius, gap, 0.035D));
        strokes.add(ring(radius * 0.55D, 16, 0.03D, Style.RING, 0.45F, 0.35F));
        // A slow counter-spinning inner sigil turns the ritual circle into an
        // active seal instead of a static decal, and radial ley-spokes tie the
        // sigil to the jaw ring so the cast never reads as a flat floor sticker.
        strokes.add(rotatedRing(radius * 0.72D, 0.09D, 20, 0.028D, Style.RING, 0.5F, 0.42F, -spin));
        int spokes = quality == Quality.REDUCED ? 4 : 8;
        for (int spoke = 0; spoke < spokes; spoke++) {
            double angle = Math.toRadians(spin + spoke * (360.0D / spokes));
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            strokes.add(new Stroke(Style.FIELD,
                    List.of(new Point(cos * radius * 0.55D, 0.08D, sin * radius * 0.55D),
                            new Point(cos * radius * 0.96D, 0.08D, sin * radius * 0.96D)),
                    List.of(0.03F, 0.012F), 0.7F, 0.55F));
        }
        return strokes;
    }

    private static List<Stroke> fangLine(double requested, Quality quality) {
        double path = Double.isFinite(requested) && requested > 0
                ? Math.max(1.5D, Math.min(4.5D, requested))
                : FANG_LINE_PATH_BLOCKS;
        List<Stroke> strokes = new ArrayList<>(FANG_LINE_TEETH + 1);
        int samples = Math.max(3, quality.samples());
        List<Point> lane = new ArrayList<>(samples + 1);
        List<Float> widths = new ArrayList<>(samples + 1);
        for (int index = 0; index <= samples; index++) {
            double x = 0.4D + path * index / (double) samples;
            lane.add(new Point(x, 0.05D, 0.0D));
            widths.add(0.035F);
        }
        strokes.add(new Stroke(Style.LANE, lane, widths, 0.6F, 0.5F));
        for (int tooth = 0; tooth < FANG_LINE_TEETH; tooth++) {
            double x = 0.7D + tooth * (path / FANG_LINE_TEETH);
            List<Point> jaw = List.of(new Point(x - 0.22D, 0.06D, -0.36D),
                    new Point(x + 0.30D, 0.10D, 0.0D),
                    new Point(x - 0.22D, 0.06D, 0.36D));
            strokes.add(new Stroke(Style.JAW, jaw, List.of(0.05F, 0.085F, 0.05F), 1.0F, 0.92F));
        }
        return strokes;
    }

    private static List<Stroke> wardenSweep(Quality quality) {
        double forward = WARDEN_SWEEP_FORWARD;
        double halfWidth = WARDEN_SWEEP_WIDTH / 2.0D;
        List<Stroke> strokes = new ArrayList<>(5);
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
        for (int chevron = 0; chevron < 3; chevron++) {
            double x = 1.4D + chevron * 0.9D;
            List<Point> line = List.of(new Point(x, 0.07D, -halfWidth * 0.7D),
                    new Point(x + 0.35D, 0.07D, 0.0D),
                    new Point(x, 0.07D, halfWidth * 0.7D));
            strokes.add(new Stroke(Style.LANE, line, List.of(0.03F, 0.045F, 0.03F), 0.7F, 0.45F));
        }
        return strokes;
    }

    private static List<Stroke> echoSphere(long elapsedMillis, double requested, Quality quality) {
        double radius = Double.isFinite(requested) && requested > 0
                ? Math.max(3.0D, Math.min(ECHO_SPHERE_RADIUS, requested))
                : ECHO_SPHERE_RADIUS;
        double progress = Math.min(1.0D, (elapsedMillis % 2400L) / 2400.0D);
        double compression = 1.0D - 0.30D * progress;
        double effective = radius * compression;
        double centreY = radius * 0.55D;
        List<Stroke> strokes = new ArrayList<>(9);
        double[] offsets = {-4.8D, -2.4D, 0.0D, 2.4D, 4.8D};
        int rings = quality == Quality.REDUCED ? 2 : offsets.length;
        for (int ring = 0; ring < rings; ring++) {
            double y = Math.max(0.05D, centreY + offsets[ring] * compression);
            double dy = y - centreY;
            double horizontal = Math.sqrt(Math.max(0.25D, effective * effective - dy * dy));
            strokes.add(ringAt(horizontal, y, 16, 0.045D, Style.SHELL, 0.9F, 0.75F));
        }
        int meridians = quality == Quality.REDUCED ? 2 : 4;
        int samples = Math.max(6, quality.samples());
        for (int meridian = 0; meridian < meridians; meridian++) {
            double angle = Math.PI * meridian / meridians;
            List<Point> arc = new ArrayList<>(samples + 1);
            List<Float> widths = new ArrayList<>(samples + 1);
            for (int index = 0; index <= samples; index++) {
                double t = index / (double) samples;
                double phi = -Math.PI / 2.0D + Math.PI * t;
                double y = Math.max(0.05D, centreY + Math.sin(phi) * effective);
                double r = Math.cos(phi) * effective;
                arc.add(new Point(Math.cos(angle) * r, y, Math.sin(angle) * r));
                widths.add(0.04F);
            }
            strokes.add(new Stroke(Style.SHELL, arc, widths, 0.85F, 0.65F));
        }
        return strokes;
    }

    private static List<Stroke> sculkTrace(long seed, double requested, Quality quality) {
        double path = Double.isFinite(requested) && requested > 0
                ? Math.max(1.5D, Math.min(5.0D, requested))
                : 4.0D;
        int samples = Math.max(5, quality.samples() / 2 + 3);
        List<Point> trace = new ArrayList<>(samples + 1);
        List<Float> widths = new ArrayList<>(samples + 1);
        for (int index = 0; index <= samples; index++) {
            double t = index / (double) samples;
            double jitter = (splitUnit(seed, index) - 0.5D) * 0.55D * Math.sin(Math.PI * t);
            trace.add(new Point(0.4D + path * t, 0.06D, jitter));
            widths.add(0.035F + 0.045F * (float) Math.sin(Math.PI * t));
        }
        List<Stroke> strokes = new ArrayList<>(4);
        strokes.add(new Stroke(Style.TRACE, trace, widths, 0.9F, 0.8F));
        double endX = 0.4D + path;
        strokes.add(new Stroke(Style.JAW, List.of(new Point(endX, 0.06D, -0.45D),
                new Point(endX + 0.25D, 0.10D, 0.0D), new Point(endX, 0.06D, 0.45D)),
                List.of(0.04F, 0.09F, 0.04F), 1.0F, 0.95F));
        strokes.add(new Stroke(Style.LANE, List.of(new Point(endX, 0.06D, -0.6D),
                new Point(endX, 0.06D, 0.6D)), List.of(0.03F, 0.03F), 0.6F, 0.5F));
        return strokes;
    }

    private static List<Stroke> gravity(long elapsedMillis, double requested, Quality quality) {
        double radius = Double.isFinite(requested) && requested > 0
                ? Math.max(4.0D, Math.min(maxReach(Spell.GRAVITY), requested))
                : 8.0D;
        double height = Math.max(10.0D, Math.min(18.0D, radius * 0.6D));
        double progress = Math.min(1.0D, (elapsedMillis % 6000L) / 6000.0D);
        int columns = quality == Quality.REDUCED ? 3 : quality == Quality.BALANCED ? 5 : 9;
        List<Stroke> strokes = new ArrayList<>(columns + 4);
        int samples = Math.max(5, quality.samples() / 2 + 3);
        for (int column = 0; column < columns; column++) {
            double angle = Math.PI * 2.0D * column / columns + 0.4D;
            double ring = radius * (column % 2 == 0 ? 0.55D : 0.85D);
            List<Point> line = new ArrayList<>(samples + 1);
            List<Float> widths = new ArrayList<>(samples + 1);
            for (int index = 0; index <= samples; index++) {
                double t = index / (double) samples;
                double y = height * (1.0D - t);
                double curl = ring * (1.0D - 0.45D * t) * (1.0D - 0.25D * progress);
                line.add(new Point(Math.cos(angle) * curl, y, Math.sin(angle) * curl));
                widths.add(0.035F + 0.03F * (float) t);
            }
            strokes.add(new Stroke(Style.FIELD, line, widths, 0.75F, 0.6F));
        }
        int ringPoints = radius > 12.0D ? 32 : 20;
        strokes.add(ringAt(radius, 0.08D, ringPoints, 0.05D, Style.RING, 0.9F, 0.7F));
        strokes.add(ringAt(radius, height, ringPoints, 0.05D, Style.RING, 0.7F, 0.5F));
        strokes.add(ringAt(radius * 0.5D, 0.09D, 14, 0.03D, Style.RING, 0.5F, 0.4F));
        strokes.add(ringAt(radius * 0.25D, 0.10D, 10, 0.03D, Style.RING, 0.4F, 0.35F));
        return strokes;
    }

    private static List<Stroke> guardianFan(double requested, Quality quality) {
        double radius = Double.isFinite(requested) && requested > 0
                ? Math.max(3.5D, Math.min(5.5D, requested))
                : GUARDIAN_FAN_RADIUS;
        int samples = Math.max(6, quality.samples());
        List<Point> arc = new ArrayList<>(samples + 1);
        List<Float> widths = new ArrayList<>(samples + 1);
        for (int index = 0; index <= samples; index++) {
            double t = index / (double) samples;
            double angle = Math.toRadians(-GUARDIAN_FAN_HALF_ANGLE + 2.0D * GUARDIAN_FAN_HALF_ANGLE * t);
            arc.add(new Point(Math.cos(angle) * radius, 0.08D + Math.sin(Math.PI * t) * 0.35D,
                    Math.sin(angle) * radius));
            widths.add(0.05F + 0.03F * (float) Math.sin(Math.PI * t));
        }
        List<Stroke> strokes = new ArrayList<>(4);
        strokes.add(new Stroke(Style.WEDGE, arc, widths, 1.0F, 0.92F));
        double edge = Math.toRadians(GUARDIAN_FAN_HALF_ANGLE);
        strokes.add(new Stroke(Style.WEDGE, List.of(new Point(0.7D, 0.06D, 0.0D),
                new Point(Math.cos(edge) * radius, 0.08D, Math.sin(edge) * radius)),
                List.of(0.05F, 0.04F), 0.8F, 0.6F));
        strokes.add(new Stroke(Style.WEDGE, List.of(new Point(0.7D, 0.06D, 0.0D),
                new Point(Math.cos(edge) * radius, 0.08D, -Math.sin(edge) * radius)),
                List.of(0.05F, 0.04F), 0.8F, 0.6F));
        strokes.add(ringAt(radius * 0.5D, 0.07D, 10, 0.03D, Style.LANE, 0.5F, 0.4F));
        // Nested inner bands give the cone depth without ever reaching past the
        // committed fan radius, so the visible wedge still stops at 4.8 blocks.
        for (int band = 1; band <= 3; band++) {
            double shrink = 1.0D - band * 0.15D;
            List<Point> inner = new ArrayList<>(samples + 1);
            List<Float> innerWidths = new ArrayList<>(samples + 1);
            for (int index = 0; index <= samples; index++) {
                double t = index / (double) samples;
                double angle = Math.toRadians(-GUARDIAN_FAN_HALF_ANGLE + 2.0D * GUARDIAN_FAN_HALF_ANGLE * t);
                inner.add(new Point(Math.cos(angle) * radius * shrink, 0.07D,
                        Math.sin(angle) * radius * shrink));
                innerWidths.add(0.02F);
            }
            strokes.add(new Stroke(Style.WEDGE, inner, innerWidths, 0.6F, 0.35F));
        }
        return strokes;
    }

    private static List<Stroke> guardianHeavy(Quality quality) {
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
        List<Stroke> strokes = new ArrayList<>(3);
        strokes.add(new Stroke(Style.WEDGE, wedge, widths, 1.0F, 0.94F));
        strokes.add(new Stroke(Style.ARM, List.of(new Point(0.2D, 1.4D, -half * 0.4D),
                new Point(forward * 0.72D, 0.9D, half * 0.35D),
                new Point(forward, 0.25D, half * 0.2D)),
                List.of(0.10F, 0.08F, 0.05F), 1.0F, 0.9F));
        return strokes;
    }

    private static List<Stroke> separation(long elapsedMillis, Quality quality) {
        double radius = SEPARATION_RADIUS;
        double progress = Math.min(1.0D, (elapsedMillis % 1600L) / 1600.0D);
        double expanding = radius * (0.55D + 0.45D * progress);
        List<Stroke> strokes = new ArrayList<>(2 + 12);
        strokes.add(ringAt(expanding, 0.08D, 18, 0.05D, Style.RING, 1.0F, 0.85F));
        strokes.add(ringAt(radius * 0.4D, 0.09D, 10, 0.03D, Style.RING, 0.6F, 0.45F));
        int ticks = quality == Quality.REDUCED ? 8 : 12;
        for (int tick = 0; tick < ticks; tick++) {
            double angle = Math.PI * 2.0D * tick / ticks;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            strokes.add(new Stroke(Style.FIELD, List.of(
                    new Point(cos * expanding, 0.07D, sin * expanding),
                    new Point(cos * (expanding + 0.75D), 0.07D, sin * (expanding + 0.75D))),
                    List.of(0.035F, 0.02F), 0.8F, 0.7F));
        }
        return strokes;
    }

    private static Stroke rotatedRing(double radius, double y, int points, double halfWidth,
                                      Style style, float brightness, float alpha, double rotationDegrees) {
        int count = Math.max(6, Math.min(MAX_POINTS_PER_STROKE - 1, points));
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
        return new Stroke(Style.LANE, arc, widths, 0.6F, 0.55F);
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
