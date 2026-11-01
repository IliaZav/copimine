package me.copimine.endevent.domain.wave7;

/** Bounded, non-identifying tactical weights derived from one participant's event profile. */
public record EchoBehaviorStrategy(int meleePermille, int rangedPermille,
                                   int mobilityPermille, int defensePermille,
                                   int sustainPermille, int confidencePermille,
                                   int observedWaveMask, boolean fromEventStart,
                                   int jumpAttackPermille, int holdDistancePermille,
                                   int kitePermille, int shieldPermille,
                                   int weaponSwitchPermille) {
    public static final long MAX_PROFILE_COUNT = 1_000_000_000L;
    /** Encoded field count; kept in one place so old records fail closed. */
    public static final int ENCODED_FIELDS = 14;
    /** Strategy schema revision understood by {@link #decode(String)}. */
    public static final String SCHEMA = "2";

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
                || weaponSwitchPermille < 0 || weaponSwitchPermille > 1_000) {
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
                share(counts.itemSwitches(), Math.max(1L, attackCount)));
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
                Integer.toString(weaponSwitchPermille));
    }

    public static EchoBehaviorStrategy decode(String encoded) {
        if (encoded == null || encoded.length() > 192) {
            throw new IllegalArgumentException("missing or oversized Echo behavior strategy");
        }
        String[] fields = encoded.split(",", -1);
        if (fields.length != ENCODED_FIELDS || !SCHEMA.equals(fields[0])
                || !"0".equals(fields[8]) && !"1".equals(fields[8])) {
            throw new IllegalArgumentException("malformed Echo behavior strategy");
        }
        EchoBehaviorStrategy strategy = new EchoBehaviorStrategy(
                parseCanonicalInt(fields[1]), parseCanonicalInt(fields[2]), parseCanonicalInt(fields[3]),
                parseCanonicalInt(fields[4]), parseCanonicalInt(fields[5]), parseCanonicalInt(fields[6]),
                parseCanonicalInt(fields[7]), "1".equals(fields[8]),
                parseCanonicalInt(fields[9]), parseCanonicalInt(fields[10]),
                parseCanonicalInt(fields[11]), parseCanonicalInt(fields[12]),
                parseCanonicalInt(fields[13]));
        if (!strategy.encode().equals(encoded)) {
            throw new IllegalArgumentException("non-canonical Echo behavior strategy");
        }
        return strategy;
    }

    private static int share(long numerator, long denominator) {
        return denominator <= 0L ? 500 : (int) Math.round(numerator * 1_000.0D / denominator);
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
                                int holdDistancePermille, int kitePermille) {
        /** Baseline used when a caller only recorded the five original features. */
        public ProfileCounts(long meleeHits, long rangedHits, long movementSamples,
                             long sprintSamples, long shieldSamples, long healUses,
                             int observedWaveMask, boolean fromEventStart, double confidence) {
            this(meleeHits, rangedHits, movementSamples, sprintSamples, shieldSamples, healUses,
                    observedWaveMask, fromEventStart, confidence, 0L, 0L, -1, -1);
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
