package me.copimine.endevent.domain;

/** Pure source identity, target and effect contract for Rift Fireballs. */
public final class RiftFireballPolicy {
    public static final double PULSE_RADIUS = 5.0D;
    public static final int PULSE_INTERVAL_TICKS = 40;
    public static final int FIRE_INTERVAL_TICKS = 80;
    public static final int MAX_ACTIVE_FIREBALLS = 8;

    private RiftFireballPolicy() {
    }

    public static boolean validReflectedProjectile(boolean eventOwned, boolean reflected,
                                                   boolean generationMatches,
                                                   boolean reflectedByPlayer) {
        return eventOwned && reflected && generationMatches && reflectedByPlayer;
    }

    /** Event-owned Rift Fireballs are filtered; ordinary player damage is not. */
    public static boolean blocksBossDamage(boolean eventOwned, boolean reflected) {
        return eventOwned;
    }

    /**
     * The impact controller applies the configured player damage exactly once.
     * Cancel the LargeFireball's vanilla player damage event, while leaving
     * ordinary fireballs and ordinary player attacks untouched.
     */
    public static boolean blocksVanillaPlayerDamage(boolean eventOwned) {
        return eventOwned;
    }

    public static EffectProfile pulseEffects() {
        return new EffectProfile(0.0D, 0, 60, 0, 60, 1, 60, 0);
    }

    public static EffectProfile fireballEffects(double damage, int blindnessTicks, int debuffTicks) {
        return new EffectProfile(damage, blindnessTicks, debuffTicks,
                0, debuffTicks, 1, debuffTicks, 0);
    }

    public record EffectProfile(double damage,
                                int blindnessTicks,
                                int weaknessTicks,
                                int weaknessAmplifier,
                                int nauseaTicks,
                                int nauseaAmplifier,
                                int slownessTicks,
                                int slownessAmplifier) {
        public EffectProfile {
            if (Double.isNaN(damage) || Double.isInfinite(damage) || damage < 0.0D
                    || blindnessTicks < 0 || weaknessTicks < 0 || nauseaTicks < 0
                    || slownessTicks < 0 || weaknessAmplifier < 0 || nauseaAmplifier < 0
                    || slownessAmplifier < 0) {
                throw new IllegalArgumentException("invalid Rift Fireball effect profile");
            }
        }
    }
}
