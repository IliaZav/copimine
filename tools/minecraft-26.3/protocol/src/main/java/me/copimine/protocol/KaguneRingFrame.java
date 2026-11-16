package me.copimine.protocol;

import java.util.ArrayList;
import java.util.List;

/** One authoritative frame of a batched ring, carried by one ItemDisplay. */
public record KaguneRingFrame(int row, int stage, double radius, double previousRadius,
                              double rise, double strike, List<Double> gaps) {
    public static final int MODEL = 830017;
    public static final int MARKER = 830117;
    public static final int EMERGE = 0, TRAVEL = 1, RETRACT = 2;
    public static final int EMERGE_TICKS = 20, TRAVEL_TICKS = 40, RETRACT_TICKS = 18, GAP_TICKS = 8;
    public static final double GAP_WIDTH = 2.0D;
    public static final double RIG_SCALE = .65D;
    public static final double HEIGHT = 7.8681D * .62D * RIG_SCALE;
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

    public boolean canDamage() { return stage == TRAVEL && rise >= .99D && strike > 0D; }

    public double renderRise(double partialTick) {
        double partial=Double.isFinite(partialTick) ? Math.max(0D,Math.min(1D,partialTick)) : 1D;
        double previous=stage==EMERGE ? Math.max(0D,rise-1D/EMERGE_TICKS)
                : stage==RETRACT ? Math.min(1D,rise+1D/RETRACT_TICKS)
                : strike==0D ? 1D-1D/EMERGE_TICKS : rise;
        return previous+(rise-previous)*partial;
    }

    /** Moving player against moving upright stems, all coordinates relative to the ring origin. */
    public boolean contacts(double bx,double by,double bz,double x,double y,double z,
                            double halfWidth,double playerHeight) {
        if (!canDamage() || !Double.isFinite(bx+by+bz+x+y+z+halfWidth+playerHeight)
                || halfWidth <= 0D || playerHeight <= 0D) return false;
        double dy=y-by, low=0D, high=1D;
        double bottom=-playerHeight-.15D, top=HEIGHT+.15D;
        if (Math.abs(dy)<1E-9D) { if (by<bottom || by>top) return false; }
        else {
            double a=(bottom-by)/dy,b=(top-by)/dy;
            low=Math.max(low,Math.min(a,b)); high=Math.min(high,Math.max(a,b));
            if (low>high) return false;
        }
        double contact=.32D+halfWidth;
        int count=instances();
        for (int i=0;i<count;i++) {
            if (!drawsInstance(i)) continue;
            double angle=direction(i),cos=Math.cos(angle),sin=Math.sin(angle);
            double sx=bx-cos*previousRadius,sz=bz-sin*previousRadius;
            double vx=x-cos*radius-sx,vz=z-sin*radius-sz;
            double a=vx*vx+vz*vz,b=2D*(sx*vx+sz*vz),c=sx*sx+sz*sz-contact*contact;
            double lo=low,hi=high;
            if (a<1E-9D) { if (c>0D) continue; }
            else {
                double d=b*b-4D*a*c;
                if (d<0D) continue;
                double root=Math.sqrt(d);
                lo=Math.max(lo,(-b-root)/(2D*a)); hi=Math.min(hi,(-b+root)/(2D*a));
                if (lo>hi) continue;
            }
            double closest=a<1E-9D ? lo : Math.max(lo,Math.min(hi,-b/(2D*a)));
            // Evaluate gaps at contact time, not at the player's final position.
            // Newly exposed boundary stems cannot sweep an earlier invisible interval.
            for (int sample=0;sample<3;sample++) {
                double t=sample==0 ? closest : sample==1 ? lo : hi;
                double atRadius=previousRadius+(radius-previousRadius)*t;
                boolean visible=true,safe=false;
                double playerAngle=Math.atan2(bz+(z-bz)*t,bx+(x-bx)*t);
                for (double gap:gaps) {
                    if (Math.abs(wrap(angle-gap))*atRadius<=GAP_WIDTH/2D+.25D) visible=false;
                    if (Math.abs(wrap(playerAngle-gap))*atRadius<=GAP_WIDTH/2D) safe=true;
                }
                if (visible && !safe) return true;
            }
        }
        return false;
    }

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
