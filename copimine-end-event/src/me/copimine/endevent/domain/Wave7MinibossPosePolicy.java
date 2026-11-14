package me.copimine.endevent.domain;

/**
 * Which presentation pose a Wave 7 miniboss is in, derived from the same
 * authoritative action state that already drives its damage.
 *
 * <p>The three scripted minibosses never trigger a vanilla animation state:
 * their AI is switched off and their attacks are resolved by the plugin, so
 * {@code attackAnimationState}, {@code sonicBoomAnimationState} and the
 * Evoker's {@code isCastingSpell} flag stay false for the entire trial. The
 * client therefore rendered three statues that slid around the arena, and the
 * Archmage's sigil ring -- its one "a spell is coming" cue -- never became
 * visible.</p>
 *
 * <p>The fix is to drive the pose from the state the fight is already in: the
 * durable cast receipt for the Archer and the Archmage, the action field for
 * the Warden. That means the pose cannot disagree with the attack, because
 * both are read from the same record.</p>
 *
 * <p>Pure: no Bukkit, no clock, no entity. The plugin decides which of these
 * predicates is true; this class only maps that to a wire pose and a lease.</p>
 */
public final class Wave7MinibossPosePolicy {
    private Wave7MinibossPosePolicy() {
    }

    /** The only poses the client accepts for a wave actor. */
    public enum Pose {
        /** Walking, idling, waiting for the next attack slot. */
        COMBAT("WAVE_COMBAT"),
        /** The telegraph window: the attack is committed but has not landed. */
        WINDUP("WAVE_WINDUP"),
        /** The single frame on which the attack resolves. */
        RELEASE("WAVE_RELEASE"),
        /** The attack has landed and the miniboss is openly recovering. */
        RECOVER("WAVE_RECOVER");

        private final String wire;

        Pose(String wire) {
            this.wire = wire;
        }

        /** Exact token the client parses; never localised. */
        public String wire() {
            return wire;
        }
    }

    /**
     * Pose of a named trial actor from its cast receipt.
     *
     * @param attacking      the receipt names an attack that has not resolved
     * @param telegraphSpent the receipt's deadline has passed, so this is the
     *                       tick the attack resolves on
     * @param coolingDown    the receipt is inside its post-attack cooldown
     */
    public static Pose castPose(boolean attacking, boolean telegraphSpent, boolean coolingDown) {
        if (attacking) {
            return telegraphSpent ? Pose.RELEASE : Pose.WINDUP;
        }
        return coolingDown ? Pose.RECOVER : Pose.COMBAT;
    }

    /**
     * Pose of the Warden from its action field.
     *
     * <p>{@code action} is the runtime's own enum: 0 is neutral, 1 is the
     * telegraphed swing or slam, 2 is the committed sonic lunge. A lunge is its
     * own release because it covers several ticks.</p>
     *
     * @param action     the runtime action field
     * @param recovering the Warden is inside the recovery window that
     *                   {@code finishTrialAttack} opened
     */
    public static Pose wardenPose(int action, boolean recovering) {
        if (action == 2) {
            return Pose.RELEASE;
        }
        if (action == 1) {
            return Pose.WINDUP;
        }
        return recovering ? Pose.RECOVER : Pose.COMBAT;
    }

    /**
     * Insert the missing release frame.
     *
     * <p>An attack can resolve on the very tick its telegraph ends, so a naive
     * "read the state, send the pose" would jump straight from WINDUP to
     * RECOVER and the player would never see the swing land. When the last pose
     * was a windup and the new one is not a release, emit one short release
     * frame first. The next call then applies the real target.</p>
     */
    public static Pose insertRelease(Pose previous, Pose target) {
        if (previous == Pose.WINDUP && target != Pose.WINDUP && target != Pose.RELEASE) {
            return Pose.RELEASE;
        }
        return target;
    }

    /** How long a release frame is held; long enough to be seen at 20 TPS. */
    public static final long RELEASE_MILLIS = 250L;

    /** Lease floor and ceiling, so a pose can never be leased for zero forever. */
    public static final long MIN_LEASE_MILLIS = 50L;
    public static final long MAX_LEASE_MILLIS = 60_000L;

    /**
     * Lease for a pose.
     *
     * <p>A windup lease must end exactly when its telegraph does and a recovery
     * lease exactly when the cooldown does, otherwise the client holds a stale
     * pose past the point the server has moved on. Everything else gets a
     * bounded default; the client falls back to {@code WAVE_COMBAT} on expiry,
     * which is the correct resting state.</p>
     */
    public static long leaseMillis(Pose pose, long endsAtMillis, long nowMillis) {
        if (pose == Pose.RELEASE) {
            return RELEASE_MILLIS;
        }
        long remaining = endsAtMillis > 0L ? endsAtMillis - nowMillis : 0L;
        if (pose == Pose.WINDUP || pose == Pose.RECOVER) {
            return clamp(remaining);
        }
        return clamp(remaining > 0L ? remaining : 1_000L);
    }

    private static long clamp(long value) {
        if (value < MIN_LEASE_MILLIS) {
            return MIN_LEASE_MILLIS;
        }
        return Math.min(value, MAX_LEASE_MILLIS);
    }

    /** True when the token is one the client will accept for a wave actor. */
    public static boolean isKnown(String pose) {
        if (pose == null) {
            return false;
        }
        for (Pose value : Pose.values()) {
            if (value.wire.equals(pose)) {
                return true;
            }
        }
        return false;
    }
}