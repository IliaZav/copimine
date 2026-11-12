package me.copimine.protocol;

import java.util.ArrayList;
import java.util.List;

/** One authoritative frame of a batched ring, carried by one ItemDisplay. */
public record KaguneRingFrame(int row, int stage, double radius, double previousRadius,
                              double rise, double strike, List<Double> gaps) {
    public static final int MODEL = 830017;
    public static final int MARKER = 830117;
    public static final int EMERGE = 0, TRAVEL = 1, RETRACT = 2;
    public static final double GAP_WIDTH = 2.0D;
    public static final double RIG_SCALE = .65D;
    public static final double CONTACT_RADIUS = .62D;
    public static final int MAX_INSTANCES = 192;

    public KaguneRingFrame {
        if (row < 0 || row > 2 || stage < EMERGE || stage > RETRACT
                || !bounded(radius, .5D, 64D) || !bounded(previousRadius, .5D, 64D)
                || !bounded(rise, 0D, 1D) || !bounded(strike, 0D, 1D)
                || gaps == null || gaps.size() < 2 || gaps.size() > 7)
            throw new IllegalArgumentException("Invalid Kagune ring frame");
        List<Double> normalized = new ArrayList<>(gaps.size());
        for (Double angle : gaps) {
            // Angles cross a float component; preserve valid +/-PI endpoints.
            if (angle == null || !bounded(angle, -Math.PI - 1E-6D, Math.PI + 1E-6D))
                throw new IllegalArgumentException("Invalid Kagune gap");
            normalized.add(wrap(angle));
        }
        gaps = List.copyOf(normalized);
    }

    private static boolean bounded(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    public int instances() {
        return Math.min(MAX_INSTANCES, Math.max(24, (int) Math.ceil(Math.PI * 2D * (12.5D + row * 3.5D) / .70D)));
    }

    public double direction(int index) { return wrap(Math.PI * 2D * index / instances()); }

    public boolean insideGap(double direction) {
        for (double gap : gaps) if (Math.abs(wrap(direction - gap)) * radius <= GAP_WIDTH / 2D) return true;
        return false;
    }

    /** Keep the authored mesh's width out of the actual escape opening. */
    public boolean drawsInstance(int index) {
        double direction = direction(index);
        for (double gap : gaps)
            if (Math.abs(wrap(direction - gap)) * radius <= GAP_WIDTH / 2D + .25D) return false;
        return true;
    }

    public boolean canDamage() { return stage == TRAVEL && rise >= .99D; }

    public List<Float> encode() {
        List<Float> values = new ArrayList<>(9 + gaps.size());
        values.add((float) MODEL); values.add((float) MARKER);
        values.add((float) row); values.add((float) stage);
        values.add((float) radius); values.add((float) previousRadius);
        values.add((float) rise); values.add((float) strike); values.add((float) gaps.size());
        for (double gap : gaps) values.add((float) gap);
        return List.copyOf(values);
    }

    /** Rendering data never authorizes damage; malformed/old data renders no ring. */
    public static KaguneRingFrame decode(List<Float> values) {
        try {
            if (values == null || values.size() < 11 || values.get(0) != MODEL
                    || values.get(1) != MARKER) return null;
            int row = integer(values.get(2)), stage = integer(values.get(3)), count = integer(values.get(8));
            if (count < 2 || count > 7 || values.size() != 9 + count) return null;
            List<Double> gaps = new ArrayList<>(count);
            for (int i = 0; i < count; i++) gaps.add((double) values.get(9 + i));
            return new KaguneRingFrame(row, stage, values.get(4), values.get(5),
                    values.get(6), values.get(7), gaps);
        } catch (RuntimeException invalid) { return null; }
    }

    private static int integer(float value) {
        if (!Float.isFinite(value) || value != Math.rint(value)) throw new IllegalArgumentException();
        return (int) value;
    }

    public static double wrap(double angle) { return Math.atan2(Math.sin(angle), Math.cos(angle)); }
}
