package me.copimine.endevent.domain.wave7;

/** Bounded, non-identifying tactical weights derived from one participant's event profile. */
public record EchoBehaviorStrategy(int meleePermille, int rangedPermille,
                                   int mobilityPermille, int defensePermille,
                                   int sustainPermille, int confidencePermille,
                                   int observedWaveMask, boolean fromEventStart,
                                   int jumpAttackPermille, int holdDistancePermille,
                                   int kitePermille, int shieldPermille,
                                   int weaponSwitchPermille,
                                   // E-04: weapon-specific, cadence and sustain evidence that
                                   // the profile service already measured but the strategy used
                                   // to discard. Each has a neutral baseline so a sparse or
                                   // low-confidence sample cannot lock a style.
                                   int healThresholdPermille, int swingCadenceTicks,
                                   int weaponPreferencePermille, int bowDrawTicks,
                                   int crossbowChargeTicks) {
    public static final long MAX_PROFILE_COUNT = 1_000_000_000L;
    /** Encoded field count; kept in one place so old records fail closed. */
    public static final int ENCODED_FIELDS = 19;
    /** Strategy schema revision understood by {@link #decode(String)}. */
    public static final String SCHEMA = "3";
    /** Prior schema still accepted so an in-progress attempt migrates safely. */
    private static final String SCHEMA_V2 = "2";
    private static final int V2_FIELDS = 14;

    /** Competent neutral values a sparse feature blends towards (E-04). */
    public static final int NEUTRAL_HEAL_THRESHOLD_PERMILLE = 550;
    public static final int NEUTRAL_SWING_CADENCE_TICKS = 20;
    public static final int NEUTRAL_WEAPON_PREFERENCE_PERMILLE = 500;
    public static final int NEUTRAL_BOW_DRAW_TICKS = 20;
    public static final int NEUTRAL_CROSSBOW_CHARGE_TICKS = 25;

    /**
     * Legacy 13-feature shape, retained for callers that only know the base
     * profile. The E-04 evidence features default to their neutral baselines, so
     * a caller built from old data keeps the exact previous behaviour.
     */
    public EchoBehaviorStrategy(int meleePermille, int rangedPermille,
                                int mobilityPermille, int defensePermille,
                                int sustainPermille, int confidencePermille,
                                int observedWaveMask, boolean fromEventStart,
                                int jumpAttackPermille, int holdDistancePermille,
                                int kitePermille, int shieldPermille, int weaponSwitchPermille) {
        this(meleePermille, rangedPermille, mobilityPermille, defensePermille, sustainPermille,
                confidencePermille, observedWaveMask, fromEventStart, jumpAttackPermille,
                holdDistancePermille, kitePermille, shieldPermille, weaponSwitchPermille,
                NEUTRAL_HEAL_THRESHOLD_PERMILLE, NEUTRAL_SWING_CADENCE_TICKS,
                NEUTRAL_WEAPON_PREFERENCE_PERMILLE, NEUTRAL_BOW_DRAW_TICKS,
                NEUTRAL_CROSSBOW_CHARGE_TICKS);
    }

    /** Legacy five-feature shape, retained for callers that only know the base profile. */
    public EchoBehaviorStrategy(int meleePermille, int rangedPermille,
                                int mobilityPermille, int defensePermille,
                                int sustainPermille, int confidencePermille,
                                int observedWaveMask, boolean fromEventStart) {
        this(meleePermille, rangedPermille, mobilityPermille, defensePermille, sustainPermille,
                confidencePermille, observedWaveMask, fromEventStart,
                250, 500, 300, defensePermille, 250);
    }

    public EchoBehaviorStrategy {
        if (meleePermille < 0 || meleePermille > 1_000
                || rangedPermille < 0 || rangedPermille > 1_000
                || meleePermille + rangedPermille != 1_000
                || mobilityPermille < 0 || mobilityPermille > 1_000
                || defensePermille < 0 || defensePermille > 1_000
                || sustainPermille < 0 || sustainPermille > 1_000
                || confidencePermille < 0 || confidencePermille > 1_000
                || observedWaveMask < 0 || observedWaveMask > 0b111_1111
                || jumpAttackPermille < 0 || jumpAttackPermille > 1_000
                || holdDistancePermille < 0 || holdDistancePermille > 1_000
                || kitePermille < 0 || kitePermille > 1_000
                || shieldPermille < 0 || shieldPermille > 1_000
                || weaponSwitchPermille < 0 || weaponSwitchPermille > 1_000
                || healThresholdPermille < 0 || healThresholdPermille > 1_000
                || swingCadenceTicks < 1 || swingCadenceTicks > 60
                || weaponPreferencePermille < 0 || weaponPreferencePermille > 1_000
                || bowDrawTicks < 0 || bowDrawTicks > 60
                || crossbowChargeTicks < 0 || crossbowChargeTicks > 60) {
            throw new IllegalArgumentException("invalid bounded Echo behavior strategy");
        }
    }

    /**
     * Raw event-profile facts are consumed here and deliberately not retained by the strategy.
     * A present but unobserved feature receives a neutral value; a missing profile cannot be
     * represented by this input and must be rejected by its runtime adapter.
     */
    public static EchoBehaviorStrategy from(ProfileCounts counts) {
        if (counts == null) throw new IllegalArgumentException("owner combat profile is required");
        long attackCount = counts.meleeHits() + counts.rangedHits();
        int meleeShare = share(counts.meleeHits(), attackCount);
        int rangedShare = attackCount <= 0L ? 500 : 1_000 - meleeShare;
        // Sustain is a share of this participant's own sustained actions, not of
        // their attacks. Dividing by attacks made a busier attacker look like a
        // non-healer: three heals scored 500 alongside three hits but only 10
        // alongside three hundred, even though the healing never changed.
        long sustainedActions = counts.healUses() + attackCount;
        // BRAIN-013: a melee/crit, a ranged/kite and a shield/heal original must
        // differ observably. These style terms carry that difference; each one
        // falls back to the documented baseline when its evidence is missing,
        // so a sparse sample cannot erase a strong neighbouring signal.
        int holdDistance = counts.holdDistancePermille();
        int kite = counts.kitePermille();
        return new EchoBehaviorStrategy(
                meleeShare, rangedShare,
                share(counts.sprintSamples(), counts.movementSamples()),
                share(counts.shieldSamples(), counts.movementSamples()),
                share(counts.healUses(), sustainedActions),
                (int) Math.round(counts.confidence() * 1_000.0D),
                counts.observedWaveMask(), counts.fromEventStart(),
                counts.jumpAttackPermille(),
                holdDistance < 0 ? 500 : holdDistance,
                kite < 0 ? rangedShare : kite,
                share(counts.shieldSamples(), counts.movementSamples()),
                // Audit ECHO error: switches are counted against recorded attacks, but a
                // player can switch weapons more often than they land hits. The share
                // saturates at 1000 per mille instead of rejecting the profile.
                share(counts.itemSwitches(), Math.max(1L, attackCount)),
                // E-04: the pre-blended weapon/cadence/sustain evidence. A negative
                // value means "no evidence" and falls back to the neutral baseline;
                // the blend itself happens in the profile service, where each
                // feature's own confidence is known.
                counts.healHealthPermille() < 0
                        ? NEUTRAL_HEAL_THRESHOLD_PERMILLE : counts.healHealthPermille(),
                counts.attackIntervalTicks() < 0
                        ? NEUTRAL_SWING_CADENCE_TICKS : counts.attackIntervalTicks(),
                counts.meleeAxePermille() < 0
                        ? NEUTRAL_WEAPON_PREFERENCE_PERMILLE : counts.meleeAxePermille(),
                counts.bowChargeTicks() < 0
                        ? NEUTRAL_BOW_DRAW_TICKS : counts.bowChargeTicks(),
                counts.crossbowChargeTicks() < 0
                        ? NEUTRAL_CROSSBOW_CHARGE_TICKS : counts.crossbowChargeTicks());
    }

    /** Canonical representation; it contains weights, never raw observations. */
    public String encode() {
        return String.join(",", SCHEMA,
                Integer.toString(meleePermille), Integer.toString(rangedPermille),
                Integer.toString(mobilityPermille), Integer.toString(defensePermille),
                Integer.toString(sustainPermille), Integer.toString(confidencePermille),
                Integer.toString(observedWaveMask), fromEventStart ? "1" : "0",
                Integer.toString(jumpAttackPermille), Integer.toString(holdDistancePermille),
                Integer.toString(kitePermille), Integer.toString(shieldPermille),
                Integer.toString(weaponSwitchPermille),
                Integer.toString(healThresholdPermille), Integer.toString(swingCadenceTicks),
                Integer.toString(weaponPreferencePermille), Integer.toString(bowDrawTicks),
                Integer.toString(crossbowChargeTicks));
    }

    public static EchoBehaviorStrategy decode(String encoded) {
        if (encoded == null || encoded.length() > 256) {
            throw new IllegalArgumentException("missing or oversized Echo behavior strategy");
        }
        String[] fields = encoded.split(",", -1);
        if (SCHEMA.equals(fields[0])) {
            if (fields.length != ENCODED_FIELDS
                    || !"0".equals(fields[8]) && !"1".equals(fields[8])) {
                throw new IllegalArgumentException("malformed Echo behavior strategy");
            }
            EchoBehaviorStrategy strategy = new EchoBehaviorStrategy(
                    parseCanonicalInt(fields[1]), parseCanonicalInt(fields[2]),
                    parseCanonicalInt(fields[3]), parseCanonicalInt(fields[4]),
                    parseCanonicalInt(fields[5]), parseCanonicalInt(fields[6]),
                    parseCanonicalInt(fields[7]), "1".equals(fields[8]),
                    parseCanonicalInt(fields[9]), parseCanonicalInt(fields[10]),
                    parseCanonicalInt(fields[11]), parseCanonicalInt(fields[12]),
                    parseCanonicalInt(fields[13]), parseCanonicalInt(fields[14]),
                    parseCanonicalInt(fields[15]), parseCanonicalInt(fields[16]),
                    parseCanonicalInt(fields[17]), parseCanonicalInt(fields[18]));
            if (!strategy.encode().equals(encoded)) {
                throw new IllegalArgumentException("non-canonical Echo behavior strategy");
            }
            return strategy;
        }
        // E-04 safe migration: a schema-2 record predates the weapon-specific,
        // cadence and sustain features. It restores with the neutral baselines;
        // raw observations were never persisted, so there is nothing to recover.
        if (SCHEMA_V2.equals(fields[0])) {
            if (fields.length != V2_FIELDS
                    || !"0".equals(fields[8]) && !"1".equals(fields[8])) {
                throw new IllegalArgumentException("malformed Echo behavior strategy");
            }
            return new EchoBehaviorStrategy(
                    parseCanonicalInt(fields[1]), parseCanonicalInt(fields[2]),
                    parseCanonicalInt(fields[3]), parseCanonicalInt(fields[4]),
                    parseCanonicalInt(fields[5]), parseCanonicalInt(fields[6]),
                    parseCanonicalInt(fields[7]), "1".equals(fields[8]),
                    parseCanonicalInt(fields[9]), parseCanonicalInt(fields[10]),
                    parseCanonicalInt(fields[11]), parseCanonicalInt(fields[12]),
                    parseCanonicalInt(fields[13]));
        }
        throw new IllegalArgumentException("malformed Echo behavior strategy");
    }

    /**
     * A thousandth share, clamped to the closed interval [0, 1000].
     *
     * <p>Audit ECHO error: a single observation category can legitimately exceed
     * its denominator. Twelve item switches across four recorded hits produced
     * 3000 and the strict strategy constructor threw, which silently dropped a
     * whole real player's signature. A share above one simply means "always",
     * so it saturates instead of failing.</p>
     */
    private static int share(long numerator, long denominator) {
        if (denominator <= 0L) return 500;
        double ratio = numerator * 1_000.0D / denominator;
        if (!Double.isFinite(ratio)) return 1_000;
        return (int) Math.max(0L, Math.min(1_000L, Math.round(ratio)));
    }

    private static int parseCanonicalInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (!Integer.toString(parsed).equals(value)) {
                throw new IllegalArgumentException("non-canonical Echo strategy integer");
            }
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid Echo strategy integer", invalid);
        }
    }

    public record ProfileCounts(long meleeHits, long rangedHits, long movementSamples,
                                long sprintSamples, long shieldSamples, long healUses,
                                int observedWaveMask, boolean fromEventStart, double confidence,
                                long jumpAttacks, long itemSwitches,
                                int holdDistancePermille, int kitePermille,
                                // E-04 evidence, already blended by per-feature confidence;
                                // a negative value means "no evidence".
                                int healHealthPermille, int attackIntervalTicks,
                                int meleeAxePermille, int bowChargeTicks,
                                int crossbowChargeTicks) {
        /** Baseline used when a caller only recorded the five original features. */
        public ProfileCounts(long meleeHits, long rangedHits, long movementSamples,
                             long sprintSamples, long shieldSamples, long healUses,
                             int observedWaveMask, boolean fromEventStart, double confidence) {
            this(meleeHits, rangedHits, movementSamples, sprintSamples, shieldSamples, healUses,
                    observedWaveMask, fromEventStart, confidence, 0L, 0L, -1, -1,
                    -1, -1, -1, -1, -1);
        }

        /** Extended shape without the E-04 evidence features; they stay neutral. */
        public ProfileCounts(long meleeHits, long rangedHits, long movementSamples,
                             long sprintSamples, long shieldSamples, long healUses,
                             int observedWaveMask, boolean fromEventStart, double confidence,
                             long jumpAttacks, long itemSwitches,
                             int holdDistancePermille, int kitePermille) {
            this(meleeHits, rangedHits, movementSamples, sprintSamples, shieldSamples, healUses,
                    observedWaveMask, fromEventStart, confidence, jumpAttacks, itemSwitches,
                    holdDistancePermille, kitePermille, -1, -1, -1, -1, -1);
        }

        public ProfileCounts {
            bounded(meleeHits, "melee hits");
            bounded(rangedHits, "ranged hits");
            bounded(movementSamples, "movement samples");
            bounded(sprintSamples, "sprint samples");
            bounded(shieldSamples, "shield samples");
            bounded(healUses, "heal uses");
            bounded(jumpAttacks, "jump attacks");
            bounded(itemSwitches, "item switches");
            if (sprintSamples > movementSamples || shieldSamples > movementSamples
                    || observedWaveMask < 0 || observedWaveMask > 0b111_1111
                    || holdDistancePermille < -1 || holdDistancePermille > 1_000
                    || kitePermille < -1 || kitePermille > 1_000
                    || healHealthPermille < -1 || healHealthPermille > 1_000
                    || attackIntervalTicks < -1 || attackIntervalTicks > 60
                    || meleeAxePermille < -1 || meleeAxePermille > 1_000
                    || bowChargeTicks < -1 || bowChargeTicks > 60
                    || crossbowChargeTicks < -1 || crossbowChargeTicks > 60
                    || !Double.isFinite(confidence) || confidence < 0.0D || confidence > 1.0D) {
                throw new IllegalArgumentException("invalid bounded Echo profile counts");
            }
        }

        /** Jump-attack share of all attacks, with the documented 25% baseline. */
        int jumpAttackPermille() {
            long attacks = meleeHits + rangedHits;
            return attacks <= 0L ? 250 : (int) Math.min(1_000L, Math.round(jumpAttacks * 1_000.0D / attacks));
        }

        private static void bounded(long value, String label) {
            if (value < 0L || value > MAX_PROFILE_COUNT) {
                throw new IllegalArgumentException("invalid Echo profile " + label);
            }
        }
    }
}
