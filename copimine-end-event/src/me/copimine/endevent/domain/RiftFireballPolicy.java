package me.copimine.endevent.domain;

/** Pure source identity, target and effect contract for Rift Fireballs. */
public final class RiftFireballPolicy {
    public static final double BASE_FIREBALL_DAMAGE = 6.0D;
    /** Keep one impact below a normal full-health player's one-shot range. */
    public static final double MAX_SCALED_DAMAGE = 9.0D;
    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 20;
    public static final int MAX_EFFECT_TICKS = 200;
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

    /**
     * Scale only the event fireball's direct player damage from the active
     * participant count. The two-player baseline remains the configured
     * ghast-like 6.0 damage, while the hard cap prevents a one-hit kill.
     */
    public static double scaledFireballDamage(double configuredDamage, int livingParticipants) {
        int players = boundedParticipants(livingParticipants);
        double baseDamage = Double.isFinite(configuredDamage)
                ? Math.max(BASE_FIREBALL_DAMAGE, configuredDamage)
                : BASE_FIREBALL_DAMAGE;
        baseDamage = Math.min(MAX_SCALED_DAMAGE, baseDamage);
        double progress = participantProgress(players);
        return Math.min(MAX_SCALED_DAMAGE,
                baseDamage + (MAX_SCALED_DAMAGE - BASE_FIREBALL_DAMAGE) * progress);
    }

    /**
     * Resolve the impact profile. Participant count changes only the bounded
     * direct damage; crowd-control timing stays deterministic so a larger
     * party is not punished by compounded, unbounded debuff duration.
     */
    public static EffectProfile scaledFireballEffects(double configuredDamage,
                                                       int blindnessTicks,
                                                       int debuffTicks,
                                                       int livingParticipants) {
        int players = boundedParticipants(livingParticipants);
        // The event contract is exact: Blindness I lasts 40 ticks and the
        // three secondary effects last 60 ticks. Ignore stale config values
        // rather than silently creating a different gameplay contract.
        return new EffectProfile(
                scaledFireballDamage(configuredDamage, players),
                40,
                60,
                0,
                60,
                1,
                60,
                0);
    }

    private static int boundedParticipants(int participants) {
        return Math.max(MIN_PARTICIPANTS, Math.min(MAX_PARTICIPANTS, participants));
    }

    private static double participantProgress(int participants) {
        return (participants - MIN_PARTICIPANTS)
                / (double) (MAX_PARTICIPANTS - MIN_PARTICIPANTS);
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
