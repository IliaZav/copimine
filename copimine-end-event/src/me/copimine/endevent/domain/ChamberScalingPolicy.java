package me.copimine.endevent.domain;

/** Bounded Wave 6 scaling based on players inside one chamber. */
public final class ChamberScalingPolicy {
    private ChamberScalingPolicy() {
    }

    public static Profile forPlayers(int players) {
        int count = Math.max(0, Math.min(5, players));
        return switch (count) {
            case 0 -> new Profile(0, 0.0D, 0.0D, 0.0D);
            case 1 -> new Profile(1, 0.75D, 0.80D, 1.00D);
            case 2 -> new Profile(2, 1.00D, 1.00D, 1.00D);
            case 3 -> new Profile(3, 1.25D, 1.10D, 1.08D);
            case 4 -> new Profile(4, 1.45D, 1.15D, 1.15D);
            default -> new Profile(5, 1.60D, 1.20D, 1.22D);
        };
    }

    public record Profile(int players, double healthMultiplier,
                           double cadenceMultiplier, double effectMultiplier) {
        public Profile {
            players = Math.max(0, players);
            healthMultiplier = bounded(healthMultiplier);
            cadenceMultiplier = bounded(cadenceMultiplier);
            effectMultiplier = bounded(effectMultiplier);
        }

        private static double bounded(double value) {
            return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.60D, value)) : 0.0D;
        }
    }
}
