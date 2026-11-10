package me.copimine.client;

/** Bounded render-only feedback at the authored contact markers of signed boss clips. */
public final class BossCameraShakePolicy {
    private BossCameraShakePolicy() { }

    public record Sample(double pitchDegrees, double yawDegrees, double rollDegrees) {
        public static final Sample ZERO = new Sample(0, 0, 0);
        public boolean isZero() { return pitchDegrees == 0 && yawDegrees == 0 && rollDegrees == 0; }
    }

    private record Profile(long contactMillis, long durationMillis, double amplitude, double radius) { }

    public static Sample sample(String animation, long elapsedMillis, double distance, double strength) {
        if (animation == null || elapsedMillis < 0 || !Double.isFinite(distance) || distance < 0
                || !Double.isFinite(strength) || strength <= 0) return Sample.ZERO;
        Profile profile = switch (animation) {
            case "GROUND_SLAM" -> new Profile(5_500, 650, 1.1, 24);
            case "CHEST_STRIKE" -> new Profile(2_000, 300, 0.35, 14);
            case "MELEE_SWIPE" -> new Profile(1_300, 220, 0.18, 6);
            case "PHASE_TRANSITION" -> new Profile(800, 700, 0.55, 32);
            case "DYING" -> new Profile(1_400, 600, 1.1, 32);
            case "CAST_IMPACT" -> new Profile(0, 300, 0.25, 12);
            default -> null;
        };
        if (profile == null || distance >= profile.radius()) return Sample.ZERO;
        long age = elapsedMillis - profile.contactMillis();
        if (age < 0 || age >= profile.durationMillis()) return Sample.ZERO;
        double t = age / (double) profile.durationMillis();
        double attenuation = 1.0 - distance / profile.radius();
        double envelope = Math.min(1.0, age / 30.0) * (1.0 - t) * (1.0 - t);
        double amplitude = profile.amplitude() * Math.min(1.0, strength)
                * attenuation * attenuation * envelope;
        return new Sample(Math.sin(age * 0.085) * amplitude,
                Math.sin(age * 0.11 + 1.2) * amplitude * 0.45,
                Math.sin(age * 0.07 + 0.7) * amplitude * 0.3);
    }
}
