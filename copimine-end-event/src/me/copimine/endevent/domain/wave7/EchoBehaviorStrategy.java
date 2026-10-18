package me.copimine.endevent.domain.wave7;

/** Bounded, non-identifying tactical weights derived from one participant's event profile. */
public record EchoBehaviorStrategy(int meleePermille, int rangedPermille,
                                   int mobilityPermille, int defensePermille,
                                   int sustainPermille, int confidencePermille,
                                   int observedWaveMask, boolean fromEventStart) {
    public static final long MAX_PROFILE_COUNT = 1_000_000_000L;

    public EchoBehaviorStrategy {
        if (meleePermille < 0 || meleePermille > 1_000
                || rangedPermille < 0 || rangedPermille > 1_000
                || meleePermille + rangedPermille != 1_000
                || mobilityPermille < 0 || mobilityPermille > 1_000
                || defensePermille < 0 || defensePermille > 1_000
                || sustainPermille < 0 || sustainPermille > 1_000
                || confidencePermille < 0 || confidencePermille > 1_000
                || observedWaveMask < 0 || observedWaveMask > 0b11_1111) {
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
        long sustainEvidence = attackCount + counts.healUses();
        return new EchoBehaviorStrategy(
                share(counts.meleeHits(), attackCount), share(counts.rangedHits(), attackCount),
                share(counts.sprintSamples(), counts.movementSamples()),
                share(counts.shieldSamples(), counts.movementSamples()),
                share(counts.healUses(), sustainEvidence),
                (int) Math.round(counts.confidence() * 1_000.0D),
                counts.observedWaveMask(), counts.fromEventStart());
    }

    /** Canonical nine-field representation; it contains weights, never raw observations. */
    public String encode() {
        return String.join(",", "1", Integer.toString(meleePermille), Integer.toString(rangedPermille),
                Integer.toString(mobilityPermille), Integer.toString(defensePermille),
                Integer.toString(sustainPermille), Integer.toString(confidencePermille),
                Integer.toString(observedWaveMask), fromEventStart ? "1" : "0");
    }

    public static EchoBehaviorStrategy decode(String encoded) {
        if (encoded == null || encoded.length() > 128) {
            throw new IllegalArgumentException("missing or oversized Echo behavior strategy");
        }
        String[] fields = encoded.split(",", -1);
        if (fields.length != 9 || !"1".equals(fields[0])
                || !"0".equals(fields[8]) && !"1".equals(fields[8])) {
            throw new IllegalArgumentException("malformed Echo behavior strategy");
        }
        EchoBehaviorStrategy strategy = new EchoBehaviorStrategy(
                parseCanonicalInt(fields[1]), parseCanonicalInt(fields[2]), parseCanonicalInt(fields[3]),
                parseCanonicalInt(fields[4]), parseCanonicalInt(fields[5]), parseCanonicalInt(fields[6]),
                parseCanonicalInt(fields[7]), "1".equals(fields[8]));
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
                                int observedWaveMask, boolean fromEventStart, double confidence) {
        public ProfileCounts {
            bounded(meleeHits, "melee hits");
            bounded(rangedHits, "ranged hits");
            bounded(movementSamples, "movement samples");
            bounded(sprintSamples, "sprint samples");
            bounded(shieldSamples, "shield samples");
            bounded(healUses, "heal uses");
            if (sprintSamples > movementSamples || shieldSamples > movementSamples
                    || observedWaveMask < 0 || observedWaveMask > 0b11_1111
                    || !Double.isFinite(confidence) || confidence < 0.0D || confidence > 1.0D) {
                throw new IllegalArgumentException("invalid bounded Echo profile counts");
            }
        }

        private static void bounded(long value, String label) {
            if (value < 0L || value > MAX_PROFILE_COUNT) {
                throw new IllegalArgumentException("invalid Echo profile " + label);
            }
        }
    }
}
