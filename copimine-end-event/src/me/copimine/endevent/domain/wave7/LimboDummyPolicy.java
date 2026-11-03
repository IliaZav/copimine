package me.copimine.endevent.domain.wave7;

/**
 * Seating and lifecycle for the Limbo rehearsal dummies.
 *
 * <p>A real Limbo rehearsal needs fifteen bodies on fifteen runes, and the user
 * does not have fifteen friends at hand. This policy decides where those
 * bodies stand, who counts as present, and what the party must visibly notice
 * about itself. It is deliberately dumb about Minecraft: it only works with
 * positions and counts so it can be tested without a server.</p>
 *
 * <p>The check it exists for is the one the user asked about: "чтобы я проверил
 * что не вижу некоторых". In Limbo the split party must lose sight of the
 * ordinary party; the policy therefore also produces the expectation line the
 * command prints, so the operator compares what the server did against what the
 * arrangement says should have happened.</p>
 */
public final class LimboDummyPolicy {
    /** Mirrors the authored Limbo threshold: below this no split happens. */
    public static final int LIMBO_THRESHOLD = EchoRealmAssignmentPolicy.LIMBO_THRESHOLD;

    /** The user does not have fifteen friends, so a rehearsal tops out here. */
    public static final int MIN_DUMMIES = 1;
    public static final int MAX_DUMMIES = limboCount();

    /** Dummies are cheap but not free; refuse to spawn an absurd crowd. */
    public static final double SPACING_BLOCKS = 1.6D;

    /** Lifetime, long enough to walk the whole start ritual and the split. */
    public static final long LIFETIME_TICKS = 20L * 60L * 10L;

    private LimboDummyPolicy() {
    }

    /**
     * Party size at which a rehearsal actually exercises Limbo.
     *
     * <p>Returns the authored threshold so the command can tell an operator
     * "fifteen dummies plus you is sixteen bodies, the split happens".</p>
     */
    public static int limboCount() {
        return LIMBO_THRESHOLD;
    }

    /** Clamp a requested number of dummies into the rehearsal range. */
    public static int clampCount(int requested) {
        return Math.max(MIN_DUMMIES, Math.min(MAX_DUMMIES, requested));
    }

    /**
     * How many bodies are needed for the split to trigger, given the real
     * players already standing in the arena.
     *
     * <p>This is what makes the command useful for a two-player night: the
     * operator asks for a rehearsal and the policy says "you need thirteen,
     * you have two, here are thirteen". Never negative.</p>
     */
    public static int dummiesNeeded(int realPlayersInArena) {
        int real = Math.max(0, realPlayersInArena);
        return Math.max(0, LIMBO_THRESHOLD - real);
    }

    /**
     * True when the combined party reaches the authored threshold, i.e. the
     * rehearsal will really split into ordinary and Limbo realms.
     */
    public static boolean reachesLimbo(int realPlayersInArena, int dummies) {
        int real = Math.max(0, realPlayersInArena);
        int fake = Math.max(0, dummies);
        return real + fake >= LIMBO_THRESHOLD;
    }

    /**
     * Where the i-th dummy stands relative to an anchor: a ring, so bodies never
     * overlap and a rune absorbs exactly one of them.
     *
     * <p>Deterministic, so the operator can re-run a rehearsal and get the same
     * picture, and so a test can assert the ring without a server.</p>
     */
    public static double[] offsetOf(int index, int total) {
        int safeTotal = Math.max(1, total);
        int slot = Math.max(0, Math.min(safeTotal - 1, index));
        // A single body has no ring to sit on: placing it at radius*cos(0)
        // would offset it by a whole rune spacing from the anchor for no
        // reason, so it stands exactly on the anchor instead.
        if (safeTotal == 1) {
            return new double[]{0.0D, 0.0D};
        }
        double angle = (2.0D * Math.PI * slot) / safeTotal;
        double radius = SPACING_BLOCKS * Math.max(1.0D, safeTotal / 6.0D);
        return new double[]{Math.cos(angle) * radius, Math.sin(angle) * radius};
    }

    /**
     * Expiry check, kept here so the session adapter has no magic numbers.
     *
     * <p>A dummy that outlives the rehearsal is a ghost body on the arena that
     * later blocks rune seating, so the caller must always pass the live tick.</p>
     */
    public static boolean expired(long startTick, long nowTick) {
        return nowTick - startTick >= LIFETIME_TICKS;
    }

    /**
     * What the operator should see once the rehearsal splits.
     *
     * <p>Deliberately phrased as a checklist rather than a claim: the command
     * cannot know what a client renders, so it states the expectation and lets
     * the human confirm it.</p>
     */
    public static String expectation() {
        return "Ожидается: группа делится (" + EchoRealmAssignmentPolicy.LIMBO_THRESHOLD
                + " игроков), обычная арена видит ТОЛЬКО своих, а лимбо-группа — только своих."
                + " Если видите всех — деления нет, если не видите никого — сломан отбор,"
                + " а не видимость.";
    }

    /** Short summary for the command output. */
    public static String describe(int realPlayersInArena, int dummies) {
        int real = Math.max(0, realPlayersInArena);
        int fake = Math.max(0, dummies);
        return "игроков=" + real + " болванчиков=" + fake + " всего=" + (real + fake)
                + (reachesLimbo(real, fake) ? " (лимбо включится)" : " (мало для лимбо)");
    }
}
