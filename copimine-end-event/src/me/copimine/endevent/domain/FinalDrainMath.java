package me.copimine.endevent.domain;

public final class FinalDrainMath {
    private FinalDrainMath() {
    }

    public static double healthAfterDrain(double before, double maximum,
                                          double fraction, double minimum) {
        if (!Double.isFinite(before) || !Double.isFinite(maximum)
                || maximum <= 0.0D || before <= 0.0D) {
            return 0.0D;
        }
        double safeMaximum = maximum;
        double safeBefore = Math.min(before, safeMaximum);
        double safeFraction = Double.isFinite(fraction) ? Math.max(0.0D, Math.min(1.0D, fraction)) : 0.0D;
        double safeMinimum = Double.isFinite(minimum) ? Math.max(0.0D, minimum) : 0.0D;
        return Math.min(safeMaximum, Math.max(safeMinimum, safeBefore * (1.0D - safeFraction)));
    }
}
