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
     * Scale fireball duration and effect strength with participants. The
     * two-player values stay exact; every value is bounded by ten seconds.
     */
    public static EffectProfile scaledFireballEffects(double configuredDamage,
                                                       int blindnessTicks,
                                                       int debuffTicks,
                                                       int livingParticipants) {
        int players = boundedParticipants(livingParticipants);
        int tier = participantTier(players);
        int scaledBlindness = scaledDuration(blindnessTicks, players);
        int scaledDebuff = scaledDuration(debuffTicks, players);
        return new EffectProfile(
                scaledFireballDamage(configuredDamage, players),
                scaledBlindness,
                scaledDebuff,
                Math.min(2, tier),
                scaledDebuff,
                Math.min(2, 1 + tier),
                scaledDebuff,
                Math.min(1, Math.max(0, tier - 1)));
    }

    private static int boundedParticipants(int participants) {
        return Math.max(MIN_PARTICIPANTS, Math.min(MAX_PARTICIPANTS, participants));
    }

    private static double participantProgress(int participants) {
        return (participants - MIN_PARTICIPANTS)
                / (double) (MAX_PARTICIPANTS - MIN_PARTICIPANTS);
    }

    private static int participantTier(int participants) {
        if (participants <= 5) {
            return 0;
        }
        if (participants <= 10) {
            return 1;
        }
        if (participants <= 15) {
            return 2;
        }
        return 3;
    }

    private static int scaledDuration(int configuredTicks, int participants) {
        int baseTicks = Math.max(0, Math.min(MAX_EFFECT_TICKS, configuredTicks));
        double progress = participantProgress(participants);
        return Math.min(MAX_EFFECT_TICKS,
                baseTicks + (int) Math.round((MAX_EFFECT_TICKS - baseTicks) * progress));
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
